package dev.sbw.deepstrike.vehicle;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.init.ModTags.Items;
import com.atsuishio.superbwarfare.item.weapon.CrowbarItem;
import dev.sbw.deepstrike.client.ClientSetup;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.FlightCollision;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LaunchJournal;
import dev.sbw.deepstrike.game.MissileLaunchEffects;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.game.RadarDisclosure;
import dev.sbw.deepstrike.game.RadarHorizon;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.model.IronDomeCanisterLayout;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;
import org.joml.Vector3d;
import org.joml.Vector4d;

public class IronDomeEntity extends DeepStrikeVehicleEntity {
   public static final int MAX_CELLS = 20;
   public static final int RELOAD_MAX_TICKS = 800;
   public static final double RADAR_RANGE = 8000.0;
   public static final double INTERCEPT_RANGE = 4500.0;
   private static final EntityDataAccessor<Integer> READY_TAMIR = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> RESERVE_TAMIR = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> RELOAD_TICKS = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> ZONE_X = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> ZONE_Z = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> ZONE_RADIUS = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Boolean> AUTO_DEFENSE = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> TORN = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> LAST_CELL = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Long> LAST_LAUNCH_TICK = SynchedEntityData.defineId(IronDomeEntity.class, EntityDataSerializers.LONG);
   private final Map<UUID, IronDomeNetwork.Contact> cachedContacts = new HashMap<>();
   private long nextScanTick = 0L;
   private boolean aircraftTargets;
   private List<String> friendlyFrequencies = List.of();
   private ChunkPos defenseChunk;

   public boolean aircraftTargets() {
      return this.aircraftTargets;
   }

   public void setAircraftTargets(boolean value) {
      this.aircraftTargets = value;
      this.nextScanTick = 0L;
   }

   public List<String> friendlyFrequencies() {
      return this.friendlyFrequencies;
   }

   public void setFriendlyFrequencies(List<String> values) {
      this.friendlyFrequencies = List.copyOf(values);
      this.nextScanTick = 0L;
      this.cachedContacts.clear();
   }

   public boolean isFriendly(GuidedMissileEntity missile) {
      return !missile.frequency().isEmpty() && this.friendlyFrequencies.contains(missile.frequency());
   }

   public IronDomeEntity(EntityType<? extends IronDomeEntity> type, Level level) {
      super(type, level);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(READY_TAMIR, 20);
      builder.define(RESERVE_TAMIR, 0);
      builder.define(RELOAD_TICKS, 0);
      builder.define(ZONE_X, (float)this.getX());
      builder.define(ZONE_Z, (float)this.getZ());
      builder.define(ZONE_RADIUS, 1500.0F);
      builder.define(AUTO_DEFENSE, true);
      builder.define(TORN, 0);
      builder.define(LAST_CELL, -1);
      builder.define(LAST_LAUNCH_TICK, 0L);
   }

   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(6.0, 4.0, 6.0);
   }

   public int getReadyTamir() {
      return (Integer)this.entityData.get(READY_TAMIR);
   }

   public void setReadyTamir(int count) {
      int clamped = Math.max(0, Math.min(20, count));
      this.entityData.set(READY_TAMIR, clamped);
      int spentCount = 20 - clamped;
      int spentMask = spentCount <= 0 ? 0 : (1 << spentCount) - 1;
      this.entityData.set(TORN, (Integer)this.entityData.get(TORN) & spentMask);
   }

   public boolean loadedCell(int cell) {
      return cell >= 20 - this.getReadyTamir() && cell < 20;
   }

   public boolean tornCell(int cell) {
      return !this.loadedCell(cell) && (((Integer)this.entityData.get(TORN) & 1 << cell) != 0 || cell < 20 - this.getReadyTamir());
   }

   public float cellOpening(int cell, float partial) {
      if (!this.tornCell(cell)) {
         return 0.0F;
      } else {
         return cell == this.entityData.get(LAST_CELL)
            ? Mth.clamp(((float)(this.level().getGameTime() - (Long)this.entityData.get(LAST_LAUNCH_TICK)) + partial) / 4.0F, 0.0F, 1.0F)
            : 1.0F;
      }
   }

   public int nextLaunchCell() {
      int ready = this.getReadyTamir();
      return ready <= 0 ? -1 : 20 - ready;
   }

   public int spendCell() {
      int cell = this.nextLaunchCell();
      if (cell < 0) {
         return -1;
      } else {
         this.setReadyTamir(this.getReadyTamir() - 1);
         this.entityData.set(TORN, (Integer)this.entityData.get(TORN) | 1 << cell);
         this.entityData.set(LAST_CELL, cell);
         this.entityData.set(LAST_LAUNCH_TICK, this.level().getGameTime());
         return cell;
      }
   }

   public int getReserveTamir() {
      return (Integer)this.entityData.get(RESERVE_TAMIR);
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

   public double getZoneX() {
      return ((Float)this.entityData.get(ZONE_X)).floatValue();
   }

   public double getZoneZ() {
      return ((Float)this.entityData.get(ZONE_Z)).floatValue();
   }

   public double getZoneRadius() {
      return ((Float)this.entityData.get(ZONE_RADIUS)).floatValue();
   }

   public boolean isAutoDefense() {
      return (Boolean)this.entityData.get(AUTO_DEFENSE);
   }

   public void setAutoDefense(boolean val) {
      this.entityData.set(AUTO_DEFENSE, val);
   }

   boolean canAutoEngage(ServerLevel server, Entity target) {
      if (this.isAlive()
         && this.isAutoDefense()
         && this.getReadyTamir() > 0
         && !this.isReloading()
         && AirDefenseTargets.target(target)
         && !(target.distanceToSqr(this) > 2.025E7)
         && (!AirDefenseTargets.aircraft(target) || this.aircraftTargets)
         && !(target instanceof GuidedMissileEntity missile && this.isFriendly(missile))) {
         Vec3 impact = target instanceof GuidedMissileEntity missilex
            ? predictImpactPoint(missilex)
            : target.position().add(target.getDeltaMovement().scale(50.0));
         return target instanceof GuidedMissileEntity missilexx && !missilexx.approachingImpact(impact)
            ? false
            : Math.hypot(impact.x - this.getZoneX(), impact.z - this.getZoneZ()) <= this.getZoneRadius()
               || AirDefenseTargets.aircraft(target)
                  && Math.hypot(target.getX() - this.getZoneX(), target.getZ() - this.getZoneZ()) <= this.getZoneRadius();
      } else {
         return false;
      }
   }

   double engagementScore(ServerLevel server, Vec3 aim) {
      Vec3 muzzle = this.calculateCellMuzzle(20 - this.getReadyTamir());
      Vec3 launchDirection = this.calculateCellDirection();
      Vec3 relative = aim.subtract(muzzle);
      double distance = relative.length();
      if (distance < 1.0E-6) {
         return 0.0;
      } else {
         double headingError = Math.acos(Mth.clamp(launchDirection.dot(relative.scale(1.0 / distance)), -1.0, 1.0));
         boolean clearExit = FlightCollision.knownClear(server, muzzle, muzzle.add(launchDirection.scale(6.0)), this);
         return distance + headingError * 180.0 + (clearExit ? 0.0 : 9000.0);
      }
   }

   PatriotInterceptorEntity launchCoordinated(ServerLevel server, Entity target, int wave, boolean manual) {
      PatriotInterceptorEntity interceptor = this.launchTamir(server, target, manual);
      if (interceptor != null) {
         interceptor.getPersistentData().putInt("IronDomeVolley", Math.max(1, wave));
      }

      return interceptor;
   }

   public void setDefendedZone(double x, double z, double radius) {
      if (Double.isFinite(x) && Double.isFinite(z) && Double.isFinite(radius)) {
         double dx = x - this.getX();
         double dz = z - this.getZ();
         double dist = Math.hypot(dx, dz);
         if (dist > 4500.0) {
            double angle = Math.atan2(dz, dx);
            x = this.getX() + Math.cos(angle) * 4500.0;
            z = this.getZ() + Math.sin(angle) * 4500.0;
         }

         this.entityData.set(ZONE_X, (float)x);
         this.entityData.set(ZONE_Z, (float)z);
         this.entityData.set(ZONE_RADIUS, (float)Math.max(100.0, Math.min(4500.0, radius)));
      }
   }

   public void startManualReload() {
      if (!this.isReloading() && this.getReadyTamir() < 20 && this.getReserveTamir() > 0) {
         this.entityData.set(RELOAD_TICKS, 1);
      }
   }

   public void refreshInventory() {
      if (this.level() instanceof ServerLevel) {
         int reserve = 0;

         for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack stack = this.getItem(i);
            if (stack.is((Item)BFItems.TAMIR.get())) {
               reserve += stack.getCount();
            }
         }

         this.entityData.set(RESERVE_TAMIR, reserve);
      }
   }

   private void handleReloading() {
      if (!this.isReloading()) {
         if (this.getReadyTamir() == 0 && this.getReserveTamir() > 0) {
            this.entityData.set(RELOAD_TICKS, 1);
         }
      } else {
         int ticks = this.getReloadTicks() + 1;
         if (ticks >= 800) {
            int needed = 20 - this.getReadyTamir();
            int reloaded = 0;

            for (int i = 0; i < this.getContainerSize() && needed > 0; i++) {
               ItemStack stack = this.getItem(i);
               if (stack.is((Item)BFItems.TAMIR.get())) {
                  int take = Math.min(needed, stack.getCount());
                  stack.shrink(take);
                  needed -= take;
                  reloaded += take;
               }
            }

            this.setReadyTamir(this.getReadyTamir() + reloaded);
            this.entityData.set(RELOAD_TICKS, 0);
            this.refreshInventory();
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 2.0F, 0.9F);
         } else {
            this.entityData.set(RELOAD_TICKS, ticks);
         }
      }
   }

   public static Vec3 predictImpactPoint(GuidedMissileEntity missile) {
      List<RoutePoint> route = missile.getRoute();
      if (route != null && !route.isEmpty()) {
         RoutePoint dest = route.get(route.size() - 1);
         return new Vec3(dest.x(), dest.y(), dest.z());
      } else {
         Vec3 pos = missile.position();
         Vec3 vel = missile.getDeltaMovement();
         if (vel.lengthSqr() < 1.0E-4) {
            return pos;
         } else {
            double t = (pos.y - 64.0) / Math.max(0.04, -vel.y);
            if (t < 0.0 || t > 800.0) {
               t = 120.0;
            }

            return pos.add(vel.scale(t));
         }
      }
   }

   public List<IronDomeNetwork.Contact> scanContacts(ServerLevel server) {
      long now = server.getGameTime();
      if (now < this.nextScanTick && !this.cachedContacts.isEmpty()) {
         return new ArrayList<>(this.cachedContacts.values());
      } else {
         this.nextScanTick = now + 10L;
         this.cachedContacts.clear();

         for (Entity e : server.getAllEntities()) {
            if (AirDefenseTargets.target(e)) {
               double distSqr = e.distanceToSqr(this);
               if (!(distSqr > 6.4E7) && RadarHorizon.visible(e, this)) {
                  Vec3 impact = e instanceof GuidedMissileEntity guided ? predictImpactPoint(guided) : e.position().add(e.getDeltaMovement().scale(50.0));
                  double distToZone = Math.hypot(impact.x - this.getZoneX(), impact.z - this.getZoneZ());
                  boolean threatInZone = distToZone <= this.getZoneRadius()
                     || AirDefenseTargets.aircraft(e) && Math.hypot(e.getX() - this.getZoneX(), e.getZ() - this.getZoneZ()) <= this.getZoneRadius();
                  int status = 0;
                  if (e instanceof GuidedMissileEntity guidedx && guidedx.isIntercepted()) {
                     status = 2;
                  } else if (IronDomeDefenseCoordinator.engaging(server, e.getUUID())) {
                     status = 1;
                  } else if (!threatInZone) {
                     status = 3;
                  }

                  double speedKmh = FlightUnits.kilometresPerHour(e.getDeltaMovement().length());
                  IronDomeNetwork.Contact contact = new IronDomeNetwork.Contact(
                     e.getUUID(),
                     e.getId(),
                     e.getX(),
                     e.getY(),
                     e.getZ(),
                     (float)speedKmh,
                     (float)e.getY(),
                     RadarDisclosure.model(e.getUUID(), AirDefenseTargets.kind(e)),
                     RadarDisclosure.impactKnown(e.getUUID()) ? impact.x : e.getX(),
                     RadarDisclosure.impactKnown(e.getUUID()) ? impact.z : e.getZ(),
                     RadarDisclosure.impactKnown(e.getUUID()) && threatInZone,
                     status == 3 && !RadarDisclosure.impactKnown(e.getUUID()) ? 0 : status,
                     e instanceof GuidedMissileEntity guidedx && this.isFriendly(guidedx)
                  );
                  this.cachedContacts.put(e.getUUID(), contact);
               }
            }
         }

         return new ArrayList<>(this.cachedContacts.values());
      }
   }

   public Vec3 calculateCellMuzzle(int cellIndex) {
      Vec3 local = IronDomeCanisterLayout.elevatedMouth(cellIndex);
      Vector4d p = this.transformPosition(this.getVehicleTransform(1.0F), local.x, local.y, local.z);
      return new Vec3(p.x, p.y, p.z);
   }

   public Vec3 calculateCellDirection() {
      Vec3 axis = IronDomeCanisterLayout.axis();
      Vector3d d = this.getVehicleTransform(1.0F).transformDirection(new Vector3d(axis.x, axis.y, axis.z));
      return new Vec3(d.x, d.y, d.z).normalize();
   }

   private PatriotInterceptorEntity launchTamir(ServerLevel server, Entity target, boolean manual) {
      if (this.getReadyTamir() > 0 && !this.isReloading()) {
         double chance = manual ? 0.9 + server.random.nextDouble() * 0.07 : 0.75 + server.random.nextDouble() * 0.05;
         chance = Math.max(0.2, chance);
         boolean hit = server.random.nextDouble() < chance;
         int previousReady = this.getReadyTamir();
         int previousTorn = (Integer)this.entityData.get(TORN);
         PatriotInterceptorEntity interceptor = new PatriotInterceptorEntity(server, this, target, hit);
         interceptor.setDefenseSource(this.getUUID(), manual);
         if (!server.addFreshEntity(interceptor)) {
            this.setReadyTamir(previousReady);
            this.entityData.set(TORN, previousTorn);
            return null;
         } else {
            int cell = Math.max(0, (Integer)this.entityData.get(LAST_CELL));
            Vec3 muzzle = this.calculateCellMuzzle(cell);
            Vec3 forward = this.calculateCellDirection();
            LaunchJournal.recordOnce(
               server,
               LaunchJournal.id(this),
               interceptor.getUUID(),
               "launch",
               this.position(),
               "Tamir",
               AirDefenseTargets.aim(target),
               Component.translatable(manual ? "journal.deepstrike.manual_chance" : "journal.deepstrike.auto_chance", new Object[]{manual ? "90–97" : "75–80"})
            );
            MissileLaunchEffects.spawnLaunchPlume(server, muzzle, forward);
            server.playSound(null, muzzle.x, muzzle.y, muzzle.z, (SoundEvent)BFSounds.PATRIOT_LAUNCH.get(), SoundSource.HOSTILE, 3.8F, 1.15F);
            return interceptor;
         }
      } else {
         return null;
      }
   }

   public void fireManual(UUID targetId, ServerPlayer player) {
      if (this.level() instanceof ServerLevel server) {
         Entity target = server.getEntity(targetId);
         if (!AirDefenseTargets.target(target)) {
            player.sendSystemMessage(Component.translatable("message.deepstrike.iron_dome.lost"));
         } else if (AirDefenseTargets.aircraft(target) && !this.aircraftTargets) {
            player.sendSystemMessage(Component.translatable("message.deepstrike.iron_dome.aircraft"));
         } else if (target.distanceToSqr(this) > 2.025E7) {
            player.sendSystemMessage(Component.translatable("message.deepstrike.iron_dome.range"));
         } else if (this.getReadyTamir() <= 0) {
            player.sendSystemMessage(Component.translatable("message.deepstrike.iron_dome.empty"));
         } else {
            if (IronDomeDefenseCoordinator.fireManual(server, this, target)) {
               player.sendSystemMessage(Component.translatable("message.deepstrike.iron_dome.manual_launch"));
            }
         }
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level() instanceof ServerLevel server) {
         this.refreshInventory();
         this.handleReloading();
         IronDomeDefenseCoordinator.tick(server);
      }
   }

   public InteractionResult interact(Player player, InteractionHand hand) {
      ItemStack held = player.getItemInHand(hand);
      if (held.is(Items.TOOLS_CROWBAR) || held.getItem() instanceof CrowbarItem) {
         return super.interact(player, hand);
      } else if (player.isShiftKeyDown()) {
         if (!this.level().isClientSide && player instanceof ServerPlayer) {
            this.openMenu(player);
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else if (held.is((Item)BFItems.TAMIR.get())) {
         if (!this.level().isClientSide) {
            int loaded = this.insertTamir(held, player.getAbilities().instabuild);
            player.sendSystemMessage(
               loaded > 0
                  ? Component.translatable("message.deepstrike.iron_dome.loaded", new Object[]{loaded, this.getReserveTamir(), this.getReadyTamir()})
                  : Component.translatable("message.deepstrike.iron_dome.inventory_full")
            );
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else if (held.is((Item)BFItems.AIR_DEFENSE_TERMINAL.get())) {
         if (!this.level().isClientSide && player instanceof ServerPlayer) {
            CompoundTag tag = NbtCompat.getOrCreateTag(held);
            tag.putUUID("TargetUUID", this.getUUID());
            tag.putInt("TargetEntityId", this.getId());
            tag.putString("TargetName", "Iron Dome");
            tag.putString("TargetDimension", this.level().dimension().location().toString());
            tag.putLong("TargetPosition", this.blockPosition().asLong());
            tag.putInt("TargetChunkX", this.chunkPosition().x);
            tag.putInt("TargetChunkZ", this.chunkPosition().z);
            NbtCompat.setTag(held, tag);
            player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.iron_dome_bound"));
         }

         if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.openIronDomeScreen(this.getId()));
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else {
         if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.openIronDomeScreen(this.getId()));
         }

         if (!this.level().isClientSide) {
            player.sendSystemMessage(
               Component.translatable(
                  "message.deepstrike.iron_dome.status",
                  new Object[]{
                     Component.translatable(this.isAutoDefense() ? "message.deepstrike.defense.auto" : "message.deepstrike.defense.manual"),
                     this.getReadyTamir(),
                     String.format(Locale.ROOT, "%.0f", this.getZoneX()),
                     String.format(Locale.ROOT, "%.0f", this.getZoneZ()),
                     String.format(Locale.ROOT, "%.0f", this.getZoneRadius())
                  }
               )
            );
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      }
   }

   private int insertTamir(ItemStack held, boolean creative) {
      int remaining = held.getCount();
      int limit = Math.min(this.getMaxStackSize(), held.getMaxStackSize());

      for (int slot = 0; slot < this.getContainerSize() && remaining > 0; slot++) {
         ItemStack current = this.getItem(slot);
         if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, held)) {
            int capacity = current.isEmpty() ? limit : limit - current.getCount();
            if (capacity > 0) {
               int moved = Math.min(remaining, capacity);
               ItemStack stored = held.copy();
               stored.setCount((current.isEmpty() ? 0 : current.getCount()) + moved);
               this.setItem(slot, stored);
               remaining -= moved;
            }
         }
      }

      int inserted = held.getCount() - remaining;
      if (inserted > 0) {
         if (!creative) {
            held.shrink(inserted);
         }

         this.setChanged();
         this.refreshInventory();
      }

      return inserted;
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("ReadyTamir", this.getReadyTamir());
      tag.putInt("ReloadTicks", this.getReloadTicks());
      tag.putFloat("ZoneX", (float)this.getZoneX());
      tag.putFloat("ZoneZ", (float)this.getZoneZ());
      tag.putFloat("ZoneRadius", (float)this.getZoneRadius());
      tag.putBoolean("AutoDefense", this.isAutoDefense());
      tag.putBoolean("AircraftTargets", this.aircraftTargets);
      RadioFrequency.save(tag, "FriendlyFrequencies", this.friendlyFrequencies);
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.contains("ReadyTamir")) {
         this.setReadyTamir(tag.getInt("ReadyTamir"));
      }

      if (tag.contains("ReloadTicks")) {
         this.entityData.set(RELOAD_TICKS, tag.getInt("ReloadTicks"));
      }

      if (tag.contains("ZoneX")) {
         this.entityData.set(ZONE_X, tag.getFloat("ZoneX"));
      }

      if (tag.contains("ZoneZ")) {
         this.entityData.set(ZONE_Z, tag.getFloat("ZoneZ"));
      }

      if (tag.contains("ZoneRadius")) {
         this.entityData.set(ZONE_RADIUS, tag.getFloat("ZoneRadius"));
      }

      if (tag.contains("AutoDefense")) {
         this.entityData.set(AUTO_DEFENSE, tag.getBoolean("AutoDefense"));
      }

      this.friendlyFrequencies = RadioFrequency.load(tag, "FriendlyFrequencies", 16);
      this.aircraftTargets = tag.getBoolean("AircraftTargets");
   }
}
