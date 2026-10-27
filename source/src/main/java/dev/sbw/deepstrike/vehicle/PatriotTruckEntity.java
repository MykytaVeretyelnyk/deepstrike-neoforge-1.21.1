package dev.sbw.deepstrike.vehicle;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.DefenseRuntime;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.RadarHorizon;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PatriotTruckEntity extends DeepStrikeVehicleEntity {
   public static final int DEPLOY_STOWED = 0;
   public static final int DEPLOY_DEPLOYING = 1;
   public static final int DEPLOY_DEPLOYED = 2;
   public static final int DEPLOY_STOWING = 3;
   public static final int SUPPORTS_TICKS = 80;
   public static final int LAUNCHER_TICKS = 80;
   public static final int TOTAL_DEPLOY_TICKS = 160;
   public static final int RELOAD_MAX_TICKS = 800;
   private static final EntityDataAccessor<Boolean> RADAR = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> PAC2 = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> PAC3 = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> READY_PAC2 = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> READY_PAC3 = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> RELOAD_TICKS = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DEPLOY_STATE = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> DEPLOY_TICKS = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> CELLS = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> TORN = SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.INT);
   private static final List<EntityDataAccessor<Long>> CELL_LAUNCH = IntStream.range(0, 4)
      .mapToObj(i -> SynchedEntityData.defineId(PatriotTruckEntity.class, EntityDataSerializers.LONG))
      .toList();
   private final int[] localLaunchTicks = new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
   private final Map<UUID, PatriotTruckEntity.RadarContact> contacts = new HashMap<>();
   private long nextScan;
   private UUID linkedIronDome;
   private ChunkPos defenseChunk;
   private List<String> friendlyFrequencies = List.of();

   public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
      super.onSyncedDataUpdated(accessor);
      if (this.level().isClientSide && this.localLaunchTicks != null) {
         int cell = CELL_LAUNCH.indexOf(accessor);
         if (cell >= 0 && this.tickCount > 1) {
            this.localLaunchTicks[cell] = this.tickCount;
         }
      }
   }

   public int loadedCellType(int cell) {
      return ((Integer)this.entityData.get(CELLS) >> cell * 2 & 3) - 1;
   }

   public boolean tornCell(int cell) {
      return ((Integer)this.entityData.get(TORN) & 1 << cell) != 0;
   }

   public float cellOpening(int cell, float partial) {
      if (!this.tornCell(cell)) {
         return 0.0F;
      } else if (this.level().isClientSide) {
         return this.localLaunchTicks[cell] == Integer.MIN_VALUE
            ? 1.0F
            : Mth.clamp((this.tickCount - this.localLaunchTicks[cell] + partial) / 4.0F, 0.0F, 1.0F);
      } else {
         return Mth.clamp(((float)(this.level().getGameTime() - (Long)this.entityData.get(CELL_LAUNCH.get(cell))) + partial) / 4.0F, 0.0F, 1.0F);
      }
   }

   public int launchCell(int type) {
      for (int i = 0; i < 4; i++) {
         if (this.loadedCellType(i) == type) {
            return i;
         }
      }

      return -1;
   }

   private void reconcileCells() {
      int cells = (Integer)this.entityData.get(CELLS);
      int torn = (Integer)this.entityData.get(TORN);
      int[] remaining = new int[]{Math.max(0, this.readyPac2()), Math.max(0, this.readyPac3())};

      for (int i = 0; i < 4; i++) {
         int type = (cells >> i * 2 & 3) - 1;
         if (type >= 0 && type < 2 && remaining[type] > 0) {
            remaining[type]--;
         } else {
            cells &= ~(3 << i * 2);
         }
      }

      for (int type = 0; type < 2; type++) {
         for (int ix = 0; ix < 4 && remaining[type] > 0; ix++) {
            if ((cells >> ix * 2 & 3) == 0) {
               cells |= type + 1 << ix * 2;
               torn &= ~(1 << ix);
               remaining[type]--;
            }
         }
      }

      this.entityData.set(CELLS, cells);
      this.entityData.set(TORN, torn);
   }

   public double radarRange() {
      return this.linkedIronDome == null ? 4500.0 : 8000.0;
   }

   public List<String> friendlyFrequencies() {
      return this.friendlyFrequencies;
   }

   public void setFriendlyFrequencies(List<String> values) {
      this.friendlyFrequencies = List.copyOf(values);
      this.nextScan = 0L;
      this.contacts.clear();
   }

   public boolean isFriendly(GuidedMissileEntity missile) {
      String frequency = missile.frequency();
      if (frequency.isEmpty()) {
         return false;
      } else {
         return this.friendlyFrequencies.contains(frequency)
            ? true
            : this.level() instanceof ServerLevel server
               && this.linkedIronDome != null
               && server.getEntity(this.linkedIronDome) instanceof IronDomeEntity dome
               && dome.isFriendly(missile);
      }
   }

   public PatriotTruckEntity(EntityType<? extends PatriotTruckEntity> type, Level level) {
      super(type, level);
   }

   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(6.0, 0.5, 6.0);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(CELLS, 0);
      builder.define(TORN, 0);

      for (EntityDataAccessor<Long> accessor : CELL_LAUNCH) {
         builder.define(accessor, 0L);
      }

      builder.define(RADAR, false);
      builder.define(PAC2, 0);
      builder.define(PAC3, 0);
      builder.define(READY_PAC2, 0);
      builder.define(READY_PAC3, 0);
      builder.define(RELOAD_TICKS, 0);
      builder.define(DEPLOY_STATE, 0);
      builder.define(DEPLOY_TICKS, 0);
   }

   public boolean hasRadarBinding() {
      for (int i = 0; i < this.getContainerSize(); i++) {
         ItemStack stack = this.getItem(i);
         CompoundTag tag = NbtCompat.getTag(stack);
         if (stack.is((Item)BFItems.AIR_DEFENSE_TERMINAL.get()) && tag != null && tag.hasUUID("TargetUUID")) {
            return true;
         }
      }

      return false;
   }

   public boolean radarConnected() {
      return (Boolean)this.entityData.get(RADAR);
   }

   public int pac2Count() {
      return (Integer)this.entityData.get(PAC2);
   }

   public int pac3Count() {
      return (Integer)this.entityData.get(PAC3);
   }

   public int readyPac2() {
      return (Integer)this.entityData.get(READY_PAC2);
   }

   public int readyPac3() {
      return (Integer)this.entityData.get(READY_PAC3);
   }

   public int totalReady() {
      return this.readyPac2() + this.readyPac3();
   }

   public int getReloadTicks() {
      return (Integer)this.entityData.get(RELOAD_TICKS);
   }

   public boolean isReloading() {
      return this.getReloadTicks() > 0;
   }

   public float getReloadProgress() {
      return Math.min(1.0F, this.getReloadTicks() / 800.0F);
   }

   public void startManualReload() {
      if (!this.isReloading() && this.totalReady() < 4 && this.pac2Count() + this.pac3Count() > 0) {
         this.entityData.set(RELOAD_TICKS, 1);
      }
   }

   public int getDeployState() {
      return (Integer)this.entityData.get(DEPLOY_STATE);
   }

   public int getDeployTicks() {
      return (Integer)this.entityData.get(DEPLOY_TICKS);
   }

   public boolean isDeployed() {
      return this.getDeployState() == 2;
   }

   public boolean isDeploying() {
      return this.getDeployState() == 1;
   }

   public boolean isStowing() {
      return this.getDeployState() == 3;
   }

   public boolean isStowed() {
      return this.getDeployState() == 0;
   }

   public void toggleDeployment() {
      int state = this.getDeployState();
      if (state == 0) {
         this.entityData.set(DEPLOY_STATE, 1);
         this.entityData.set(DEPLOY_TICKS, 0);
      } else if (state == 2) {
         this.entityData.set(DEPLOY_STATE, 3);
         this.entityData.set(DEPLOY_TICKS, 0);
      } else if (state == 1) {
         this.entityData.set(DEPLOY_STATE, 3);
         int current = this.getDeployTicks();
         this.entityData.set(DEPLOY_TICKS, Math.max(0, 160 - current));
      } else if (state == 3) {
         this.entityData.set(DEPLOY_STATE, 1);
         int current = this.getDeployTicks();
         this.entityData.set(DEPLOY_TICKS, Math.max(0, 160 - current));
      }
   }

   private void handleDeployment() {
      int state = this.getDeployState();
      int ticks = this.getDeployTicks();
      if (state == 1) {
         if (++ticks >= 160) {
            ticks = 160;
            this.entityData.set(DEPLOY_STATE, 2);
         }

         this.entityData.set(DEPLOY_TICKS, ticks);
      } else if (state == 3) {
         if (++ticks >= 160) {
            ticks = 0;
            this.entityData.set(DEPLOY_STATE, 0);
         }

         this.entityData.set(DEPLOY_TICKS, ticks);
      }
   }

   public void refreshDefenseInventory() {
      if (this.level() instanceof ServerLevel server) {
         int var12 = 0;
         int pac3 = 0;
         boolean connected = false;
         UUID domeId = null;

         for (int slot = 0; slot < this.getContainerSize(); slot++) {
            ItemStack stack = this.getItem(slot);
            if (stack.is((Item)BFItems.PAC_2.get())) {
               var12 += stack.getCount();
            }

            if (stack.is((Item)BFItems.PAC_3.get())) {
               pac3 += stack.getCount();
            }

            if (stack.is((Item)BFItems.AIR_DEFENSE_TERMINAL.get())) {
               CompoundTag tag = NbtCompat.getTag(stack);
               if (tag != null
                  && tag.hasUUID("TargetUUID")
                  && (!tag.contains("TargetDimension") || tag.getString("TargetDimension").equals(server.dimension().location().toString()))) {
                  Entity radar = server.getEntity(tag.getUUID("TargetUUID"));
                  DefenseRuntime runtime = DefenseRuntime.get(server);
                  if (radar == null && tag.contains("TargetChunkX") && tag.contains("TargetChunkZ") && this.tickCount % 20 == 0) {
                     runtime.wake(
                        server,
                        tag.getUUID("TargetUUID"),
                        tag.contains("TargetPosition")
                           ? BlockPos.of(tag.getLong("TargetPosition"))
                           : new ChunkPos(tag.getInt("TargetChunkX"), tag.getInt("TargetChunkZ")).getMiddleBlockPosition(64)
                     );
                  }

                  if (radar != null) {
                     tag.putLong("TargetPosition", radar.blockPosition().asLong());
                     tag.putInt("TargetChunkX", radar.chunkPosition().x);
                     tag.putInt("TargetChunkZ", radar.chunkPosition().z);
                  }

                  if ("Iron Dome".equals(tag.getString("TargetName")) && !runtime.destroyed(tag.getUUID("TargetUUID"))) {
                     domeId = tag.getUUID("TargetUUID");
                  }

                  if (radar instanceof AnMpq65Entity && radar.isAlive()) {
                     connected = true;
                  }

                  if (radar instanceof IronDomeEntity dome && dome.isAlive()) {
                     connected = true;
                     domeId = dome.getUUID();
                  }
               }
            }
         }

         this.entityData.set(PAC2, var12);
         this.entityData.set(PAC3, pac3);
         this.entityData.set(RADAR, connected);
         if (!Objects.equals(this.linkedIronDome, domeId)) {
            this.contacts.clear();
            this.nextScan = 0L;
         }

         this.linkedIronDome = domeId;
         if (!connected) {
            this.contacts.clear();
         }
      }
   }

   public boolean spendMissile(int type) {
      if (type >= 0 && type <= 1) {
         this.reconcileCells();
         int cell = this.launchCell(type);
         if (cell < 0) {
            return false;
         } else {
            if (type == 0) {
               int r = this.readyPac2();
               if (r <= 0) {
                  return false;
               }

               this.entityData.set(READY_PAC2, r - 1);
            } else {
               int r = this.readyPac3();
               if (r <= 0) {
                  return false;
               }

               this.entityData.set(READY_PAC3, r - 1);
            }

            this.entityData.set(CELLS, (Integer)this.entityData.get(CELLS) & ~(3 << cell * 2));
            this.entityData.set(TORN, (Integer)this.entityData.get(TORN) | 1 << cell);
            this.entityData.set(CELL_LAUNCH.get(cell), this.level().getGameTime());
            if (this.totalReady() == 0 && (this.pac2Count() > 0 || this.pac3Count() > 0)) {
               this.entityData.set(RELOAD_TICKS, 1);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private void handleReload() {
      int ready = this.totalReady();
      int reserve = this.pac2Count() + this.pac3Count();
      if (ready == 0 && reserve > 0 && !this.isReloading()) {
         this.entityData.set(RELOAD_TICKS, 1);
      }

      if (this.isReloading()) {
         if (ready >= 4 || reserve <= 0) {
            this.entityData.set(RELOAD_TICKS, 0);
            return;
         }

         int ticks = this.getReloadTicks() + 1;
         if (ticks >= 800) {
            this.performReloadFromInventory();
            this.entityData.set(RELOAD_TICKS, 0);
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 1.8F, 0.85F);
         } else {
            this.entityData.set(RELOAD_TICKS, ticks);
         }
      }
   }

   private void performReloadFromInventory() {
      int needed = 4 - this.totalReady();
      if (needed > 0) {
         int loaded2 = this.readyPac2();
         int loaded3 = this.readyPac3();

         for (int slot = 0; slot < this.getContainerSize() && needed > 0; slot++) {
            ItemStack stack = this.getItem(slot);
            if (stack.is((Item)BFItems.PAC_2.get())) {
               int take = Math.min(needed, stack.getCount());
               stack.shrink(take);
               loaded2 += take;
               needed -= take;
            } else if (stack.is((Item)BFItems.PAC_3.get())) {
               int take = Math.min(needed, stack.getCount());
               stack.shrink(take);
               loaded3 += take;
               needed -= take;
            }
         }

         this.entityData.set(READY_PAC2, loaded2);
         this.entityData.set(READY_PAC3, loaded3);
         this.reconcileCells();
         this.refreshDefenseInventory();
      }
   }

   public List<PatriotTruckEntity.RadarContact> scan(ServerLevel level) {
      long now = level.getGameTime();
      if (!this.radarConnected()) {
         this.contacts.clear();
         return List.of();
      } else if (now < this.nextScan) {
         return this.sortedContacts();
      } else {
         this.nextScan = now + 10L;
         if (this.linkedIronDome != null && level.getEntity(this.linkedIronDome) instanceof IronDomeEntity dome) {
            this.contacts.clear();

            for (IronDomeNetwork.Contact contact : dome.scanContacts(level)) {
               boolean friendly = level.getEntity(contact.id()) instanceof GuidedMissileEntity missile && this.isFriendly(missile);
               this.contacts
                  .put(
                     contact.id(),
                     new PatriotTruckEntity.RadarContact(
                        contact.id(), contact.x(), contact.y(), contact.z(), contact.speedKmh(), contact.altitude(), contact.kind(), now, friendly
                     )
                  );
            }

            return this.sortedContacts();
         } else {
            Set<UUID> seen = new HashSet<>();

            for (Entity entity : level.getAllEntities()) {
               if (AirDefenseTargets.target(entity)) {
                  double distSqr = entity.distanceToSqr(this);
                  if (!(distSqr > this.radarRange() * this.radarRange())) {
                     double altitude = entity.getY();
                     if (RadarHorizon.visible(entity, this)) {
                        UUID id = entity.getUUID();
                        seen.add(id);
                        if (this.contacts.containsKey(id)) {
                           Vec3 velocity = entity.getDeltaMovement();
                           this.contacts
                              .put(
                                 id,
                                 new PatriotTruckEntity.RadarContact(
                                    id,
                                    entity.getX(),
                                    entity.getY(),
                                    entity.getZ(),
                                    (float)FlightUnits.kilometresPerHour(velocity.length()),
                                    (float)altitude,
                                    AirDefenseTargets.kind(entity),
                                    now,
                                    entity instanceof GuidedMissileEntity guided && this.isFriendly(guided)
                                 )
                              );
                        } else {
                           double chance = RadarHorizon.height(entity, this) < 12.0 ? 0.7 : 0.95;
                           if (level.random.nextDouble() < chance) {
                              Vec3 velocity = entity.getDeltaMovement();
                              this.contacts
                                 .put(
                                    id,
                                    new PatriotTruckEntity.RadarContact(
                                       id,
                                       entity.getX(),
                                       entity.getY(),
                                       entity.getZ(),
                                       (float)FlightUnits.kilometresPerHour(velocity.length()),
                                       (float)altitude,
                                       AirDefenseTargets.kind(entity),
                                       now,
                                       entity instanceof GuidedMissileEntity guided && this.isFriendly(guided)
                                    )
                                 );
                           }
                        }
                     }
                  }
               }
            }

            this.contacts.entrySet().removeIf(entry -> !seen.contains(entry.getKey()) || now - entry.getValue().lastSeen() > 100L);
            return this.sortedContacts();
         }
      }
   }

   private List<PatriotTruckEntity.RadarContact> sortedContacts() {
      return this.contacts.values().stream().sorted(Comparator.comparingDouble(c -> this.position().distanceToSqr(c.x(), c.y(), c.z()))).toList();
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         this.refreshDefenseInventory();
         this.handleDeployment();
         this.handleReload();
         if (this.radarConnected() && this.level() instanceof ServerLevel server) {
            this.scan(server);
         }
      }

      if (!this.isStowed()) {
         this.setPower(0.0F);
         Vec3 dm = this.getDeltaMovement();
         this.setDeltaMovement(0.0, dm.y, 0.0);
      }
   }

   public void vMove(MoverType type, Vec3 pos) {
      if (!this.isStowed()) {
         super.vMove(type, new Vec3(0.0, pos.y, 0.0));
      } else {
         super.vMove(type, pos);
      }
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.linkedIronDome != null) {
         tag.putUUID("LinkedIronDome", this.linkedIronDome);
      }

      tag.putInt("DeployState", this.getDeployState());
      tag.putInt("DeployTicks", this.getDeployTicks());
      tag.putInt("CanisterCells", (Integer)this.entityData.get(CELLS));
      tag.putInt("CanisterTorn", (Integer)this.entityData.get(TORN));

      for (int i = 0; i < 4; i++) {
         tag.putLong("CanisterLaunch" + i, (Long)this.entityData.get(CELL_LAUNCH.get(i)));
      }

      tag.putInt("ReadyPAC2", this.readyPac2());
      tag.putInt("ReadyPAC3", this.readyPac3());
      tag.putInt("ReloadTicks", this.getReloadTicks());
      RadioFrequency.save(tag, "FriendlyFrequencies", this.friendlyFrequencies);
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.linkedIronDome = tag.hasUUID("LinkedIronDome") ? tag.getUUID("LinkedIronDome") : null;
      if (tag.contains("DeployState")) {
         this.entityData.set(DEPLOY_STATE, tag.getInt("DeployState"));
      }

      if (tag.contains("DeployTicks")) {
         this.entityData.set(DEPLOY_TICKS, tag.getInt("DeployTicks"));
      }

      if (tag.contains("ReadyPAC2")) {
         this.entityData.set(READY_PAC2, tag.getInt("ReadyPAC2"));
      }

      if (tag.contains("ReadyPAC3")) {
         this.entityData.set(READY_PAC3, tag.getInt("ReadyPAC3"));
      }

      if (tag.contains("ReloadTicks")) {
         this.entityData.set(RELOAD_TICKS, tag.getInt("ReloadTicks"));
      }

      if (tag.contains("CanisterCells")) {
         this.entityData.set(CELLS, tag.getInt("CanisterCells"));
      }

      this.entityData.set(TORN, tag.getInt("CanisterTorn"));

      for (int i = 0; i < 4; i++) {
         this.entityData.set(CELL_LAUNCH.get(i), tag.getLong("CanisterLaunch" + i));
      }

      this.reconcileCells();
      this.friendlyFrequencies = RadioFrequency.load(tag, "FriendlyFrequencies", 16);
   }

   public record RadarContact(UUID id, double x, double y, double z, float speedKmh, float altitude, int kind, long lastSeen, boolean friendly) {
   }
}
