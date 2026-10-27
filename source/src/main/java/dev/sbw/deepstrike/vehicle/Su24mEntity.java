package dev.sbw.deepstrike.vehicle;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.client.Su24mScreen;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.DeepStrikeCameraTabletItem;
import dev.sbw.deepstrike.game.DeepStrikeLaptopItem;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Vector3d;

public final class Su24mEntity extends VehicleEntity {
   public static final int CANNON_CAPACITY = 500;
   private static final EntityDataAccessor<Integer> RACKS = SynchedEntityData.defineId(Su24mEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> ROUNDS = SynchedEntityData.defineId(Su24mEntity.class, EntityDataSerializers.INT);
   private final ItemStack[] stores = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY};
   private final List<RoutePoint>[] routes = new List[]{List.of(), List.of()};
   private final Su24mEntity.Order[] orders = new Su24mEntity.Order[2];
   private final String[] messages = new String[]{"empty", "empty"};
   private final UUID[] lastFlight = new UUID[2];
   private double commandedThrottle = -1.0;
   private float gearVisual;
   private float gearVisualOld;
   private boolean gearVisualReady;
   private float wingSweep;
   private float wingSweepOld;
   private boolean wingSweepReady;

   public float landingGearProgress(float partial) {
      return this.gearVisualReady ? Mth.lerp(partial, this.gearVisualOld, this.gearVisual) : (this.getGearUp() ? 1 : 0);
   }

   public float wingSweepProgress(float partial) {
      return this.wingSweepReady ? Mth.lerp(partial, this.wingSweepOld, this.wingSweep) : 0.0F;
   }

   public Su24mEntity(EntityType<Su24mEntity> type, Level level) {
      super(type, level);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(RACKS, 0);
      builder.define(ROUNDS, 500);
   }

   public boolean loaded(int slot) {
      return slot >= 0 && slot < 2 && ((Integer)this.entityData.get(RACKS) & 1 << slot) != 0;
   }

   public int cannonRounds() {
      return (Integer)this.entityData.get(ROUNDS);
   }

   public List<RoutePoint> missionRoute(int slot) {
      return this.routes[slot];
   }

   public String storeStatus(int slot) {
      return this.messages[slot];
   }

   public Su24mEntity.Order order(int slot) {
      return this.orders[slot];
   }

   public UUID lastFlight(int slot) {
      return this.lastFlight[slot];
   }

   public double speedKmh() {
      return FlightUnits.kilometresPerHour(this.getDeltaMovement().length());
   }

   public double throttleSetting() {
      return this.commandedThrottle;
   }

   public boolean parked() {
      return this.speedKmh() < 5.0
         && (this.onGround() || !this.level().getBlockState(this.blockPosition().below()).getCollisionShape(this.level(), this.blockPosition().below()).isEmpty());
   }

   public boolean canOperate(ServerPlayer p, boolean airborne) {
      if (p == null || p.level() != this.level() || !this.isAlive()) {
         return false;
      } else if (p.getVehicle() == this) {
         return this.getSeatIndex(p) >= 0 && this.getSeatIndex(p) < 2;
      } else {
         return !airborne && this.parked() && !(this.distanceToSqr(p) > 144.0)
            ? !this.getPersistentData().hasUUID("DeepStrikeOwner")
               || this.getPersistentData().getUUID("DeepStrikeOwner").equals(p.getUUID())
               || p.hasPermissions(2)
            : false;
      }
   }

   public Vec3 pylonPosition(int slot) {
      Vector3d p = this.getTransformFromString("Vehicle").transformPosition(new Vector3d(slot == 0 ? 2.1 : -2.1, 2.2, 0.2));
      return new Vec3(p.x, p.y, p.z);
   }

   public Vec3 noseDirection() {
      Vector3d p = this.getTransformFromString("Vehicle").transformDirection(new Vector3d(0.0, 0.0, 1.0));
      return new Vec3(p.x, p.y, p.z).normalize();
   }

   private void syncRacks() {
      this.entityData.set(RACKS, (this.stores[0].isEmpty() ? 0 : 1) | (this.stores[1].isEmpty() ? 0 : 2));
   }

   public boolean loadStore(ServerPlayer p, int slot) {
      if (slot >= 0 && slot <= 1 && this.canOperate(p, false) && this.parked() && this.orders[slot] == null && this.stores[slot].isEmpty()) {
         for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack stack = p.getInventory().getItem(i);
            if (stack.getItem() instanceof MissileItem item && item.kind() == MissileKind.STORM_SHADOW && !item.isAirframe()) {
               MissileData data = MissileData.fromItemStack(stack);
               if (data != null && data.isFueled() && data.supportsWarhead()) {
                  this.stores[slot] = stack.copy();
                  this.stores[slot].setCount(1);
                  stack.shrink(1);
                  this.syncRacks();
                  this.messages[slot] = this.routes[slot].isEmpty() ? "loaded" : "ready";
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean unloadStore(ServerPlayer p, int slot) {
      if (slot >= 0 && slot <= 1 && this.canOperate(p, false) && this.parked() && this.orders[slot] == null && !this.stores[slot].isEmpty()) {
         ItemStack copy = this.stores[slot].copy();
         if (!p.getInventory().add(copy) && !copy.isEmpty()) {
            p.drop(copy, false);
         }

         this.stores[slot] = ItemStack.EMPTY;
         this.syncRacks();
         this.messages[slot] = "empty";
         return true;
      } else {
         return false;
      }
   }

   public boolean prepare(ServerPlayer p, int slot, List<RoutePoint> route) {
      if (slot >= 0 && slot <= 1 && this.canOperate(p, false) && this.orders[slot] == null && this.validRoute(route)) {
         this.routes[slot] = List.copyOf(route);
         this.messages[slot] = this.loaded(slot) ? "ready" : "empty";
         return true;
      } else {
         return false;
      }
   }

   private boolean validRoute(List<RoutePoint> route) {
      if (route != null && !route.isEmpty() && route.size() <= 32) {
         for (RoutePoint point : route) {
            if (!Double.isFinite(point.x())
               || !Double.isFinite(point.y())
               || !Double.isFinite(point.z())
               || !Double.isFinite(point.speed())
               || Math.abs(point.x()) > 2.9999E7
               || Math.abs(point.z()) > 2.9999E7
               || point.y() < this.level().getMinBuildHeight() + 1
               || point.y() > this.level().getMaxBuildHeight() + 4096
               || point.speed() < MissileKind.STORM_SHADOW.minSpeed
               || point.speed() > MissileKind.STORM_SHADOW.maxSpeed) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean arm(ServerPlayer p, int slot, int mode, int delaySeconds, double speed) {
      if (slot >= 0
         && slot <= 1
         && this.canOperate(p, true)
         && this.loaded(slot)
         && this.orders[slot] == null
         && this.validRoute(this.routes[slot])
         && mode >= 0
         && mode <= 3
         && delaySeconds >= 0
         && delaySeconds <= 3600
         && Double.isFinite(speed)
         && !(speed < 0.0)
         && !(speed > 3000.0)) {
         long now = this.level().getGameTime();
         this.orders[slot] = new Su24mEntity.Order(
            UUID.randomUUID(), p.getUUID(), mode, now + (mode != 1 && mode != 3 ? 0L : delaySeconds * 20L), now + 72000L, speed
         );
         this.messages[slot] = "armed";
         return true;
      } else {
         return false;
      }
   }

   public boolean cancel(ServerPlayer p, int slot) {
      if (slot >= 0 && slot <= 1 && this.canOperate(p, false)) {
         this.orders[slot] = null;
         this.messages[slot] = this.loaded(slot) ? (this.routes[slot].isEmpty() ? "loaded" : "ready") : "empty";
         return true;
      } else {
         return false;
      }
   }

   public boolean setAvionicsThrottle(ServerPlayer p, double throttle) {
      if (this.canOperate(p, true) && Double.isFinite(throttle) && !(throttle < -1.0) && !(throttle > 1.0)) {
         this.commandedThrottle = throttle;
         return true;
      } else {
         return false;
      }
   }

   public boolean reloadCannon(ServerPlayer p) {
      if (this.canOperate(p, false) && this.parked()) {
         Item shell = (Item)BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("superbwarfare", "small_shell_ap"));
         int need = 500 - this.cannonRounds();
         int loaded = 0;

         for (int i = 0; i < p.getInventory().getContainerSize() && loaded < need; i++) {
            ItemStack stack = p.getInventory().getItem(i);
            if (stack.is(shell)) {
               int n = Math.min(need - loaded, stack.getCount());
               stack.shrink(n);
               loaded += n;
            }
         }

         this.entityData.set(ROUNDS, this.cannonRounds() + loaded);
         return loaded > 0;
      } else {
         return false;
      }
   }

   public boolean canShoot(LivingEntity shooter) {
      return this.cannonRounds() > 0 && super.canShoot(shooter);
   }

   public void vehicleShoot(LivingEntity shooter, String weapon, Vec3 aim) {
      if ("Cannon".equals(weapon) && this.cannonRounds() > 0) {
         super.vehicleShoot(shooter, weapon, aim);
      }
   }

   public void vehicleShoot(LivingEntity shooter, String weapon, UUID target, Vec3 aim) {
      if ("Cannon".equals(weapon) && this.cannonRounds() > 0) {
         super.vehicleShoot(shooter, weapon, target, aim);
      }
   }

   public void vehicleShoot(LivingEntity shooter, UUID target, Vec3 aim) {
      if (this.cannonRounds() > 0) {
         super.vehicleShoot(shooter, target, aim);
      }
   }

   public void afterShoot(GunData gun, Vec3 aim) {
      super.afterShoot(gun, aim);
      if (!this.level().isClientSide && gun != null && gun == this.getGunData("Cannon")) {
         this.entityData.set(ROUNDS, Math.max(0, this.cannonRounds() - 1));
      }
   }

   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         float target = this.getGearUp() ? 1.0F : 0.0F;
         if (!this.gearVisualReady) {
            this.gearVisual = target;
            this.gearVisualOld = target;
            this.gearVisualReady = true;
         } else {
            this.gearVisualOld = this.gearVisual;
            this.gearVisual = Mth.approach(this.gearVisual, target, 0.025F);
         }

         float speed = (float)this.speedKmh();
         float sweepTarget = this.onGround() ? 0.0F : Mth.clamp((speed - 80.0F) / 100.0F, 0.0F, 1.0F);
         sweepTarget = sweepTarget * sweepTarget * (3.0F - 2.0F * sweepTarget);
         if (!this.wingSweepReady) {
            this.wingSweep = 0.0F;
            this.wingSweepOld = 0.0F;
            this.wingSweepReady = true;
         } else {
            this.wingSweepOld = this.wingSweep;
            this.wingSweep = Mth.approach(this.wingSweep, sweepTarget, 0.035F);
         }
      } else if (this.level() instanceof ServerLevel server) {
         GunData gun = this.getGunData("Cannon");
         if (gun != null) {
            gun.ammo.set(this.cannonRounds());
         }

         if (this.commandedThrottle >= 0.0) {
            if (!this.forwardInputDown() && !this.backInputDown()) {
               this.setPower((float)this.commandedThrottle);
            } else {
               this.commandedThrottle = -1.0;
            }
         }

         for (int i = 0; i < 2; i++) {
            Su24mEntity.Order order = this.orders[i];
            if (order != null) {
               if (ActiveFlights.get(server).wasLaunched(order.mission())) {
                  this.stores[i] = ItemStack.EMPTY;
                  this.lastFlight[i] = ActiveFlights.get(server).launchedFlight(order.mission());
                  this.orders[i] = null;
                  this.messages[i] = "launched";
                  this.syncRacks();
               } else {
                  long now = this.level().getGameTime();
                  if (now > order.expires()) {
                     this.orders[i] = null;
                     this.messages[i] = "expired";
                  } else if (now < order.earliest()) {
                     this.messages[i] = "timer";
                  } else if ((order.mode() == 2 || order.mode() == 3) && this.speedKmh() < order.speed()) {
                     this.messages[i] = "speed";
                  } else if (this.getPassengers().isEmpty()) {
                     this.messages[i] = "crew";
                  } else if (!this.onGround() && !(this.speedKmh() < 40.0)) {
                     this.launch(server, i, order);
                  } else {
                     this.messages[i] = "flight";
                  }
               }
            }
         }
      }
   }

   private void launch(ServerLevel server, int slot, Su24mEntity.Order order) {
      MissileData data = MissileData.fromItemStack(this.stores[slot]);
      if (data != null && data.isFueled()) {
         Vec3 from = this.pylonPosition(slot);
         Vec3 previous = from;
         double distance = 0.0;

         for (RoutePoint point : this.routes[slot]) {
            Vec3 next = new Vec3(point.x(), point.y(), point.z());
            distance += previous.distanceTo(next);
            previous = next;
         }

         if (distance > data.calculateMaxRange()) {
            this.orders[slot] = null;
            this.messages[slot] = "range";
         } else {
            ActiveFlights flights = ActiveFlights.get(server);
            boolean known = flights.knowsMission(order.mission());
            if ((!known || flights.acceptedBy(order.mission(), this.getUUID(), order.operator()) && !flights.report(order.mission()).contains("Outcome"))
               && (
                  known
                     || flights.acceptLaunch(
                        server, order.mission(), this.getUUID(), order.operator(), this.blockPosition(), previous, MissileKind.STORM_SHADOW.id, server.getGameTime()
                     )
               )) {
               GuidedMissileEntity missile = new GuidedMissileEntity(server, MissileKind.STORM_SHADOW, false, data.camo());
               missile.setUUID(order.mission());
               missile.setMissionId(order.mission());
               missile.applyMissileData(data);
               missile.setOwnerId(order.operator());
               missile.setFrequency("0002400");
               missile.setLaunchVehicle(this.getUUID());
               Vec3 direction = this.noseDirection();
               missile.moveTo(
                  from.x,
                  from.y,
                  from.z,
                  (float)Math.toDegrees(Math.atan2(-direction.x, direction.z)),
                  (float)(-Math.toDegrees(Math.asin(direction.y)))
               );
               missile.setLaunchVector(direction, 0);
               missile.setMission(this.routes[slot], false);
               missile.releaseFromAircraft(this.getDeltaMovement());
               if (server.addFreshEntity(missile)) {
                  this.stores[slot] = ItemStack.EMPTY;
                  this.lastFlight[slot] = missile.getUUID();
                  this.orders[slot] = null;
                  this.messages[slot] = "launched";
                  this.syncRacks();
               } else {
                  flights.cancelLaunch(order.mission());
                  this.orders[slot] = null;
                  this.messages[slot] = "rejected";
               }
            } else {
               this.orders[slot] = null;
               this.messages[slot] = "rejected";
            }
         }
      } else {
         this.orders[slot] = null;
         this.messages[slot] = "empty";
      }
   }

   public InteractionResult interact(Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      boolean tablet = stack.getItem() instanceof DeepStrikeCameraTabletItem || stack.getItem() instanceof DeepStrikeLaptopItem;
      if (tablet) {
         if (this.level().isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Su24mScreen.open(this.getId()));
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else if (stack.getItem() instanceof MissileItem item && item.kind() == MissileKind.STORM_SHADOW) {
         if (player instanceof ServerPlayer p) {
            boolean done = this.loadStore(p, this.loaded(0) ? 1 : 0);
            p.displayClientMessage(Component.translatable(done ? "su24.loaded" : "su24.load_failed"), true);
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else {
         return super.interact(player, hand);
      }
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      tag.putInt("Su24Rounds", this.cannonRounds());
      tag.putDouble("Su24Throttle", this.commandedThrottle);
      ListTag pylons = new ListTag();

      for (int i = 0; i < 2; i++) {
         CompoundTag t = new CompoundTag();
         t.put("Store", this.stores[i].saveOptional(this.registryAccess()));
         ListTag route = new ListTag();

         for (RoutePoint p : this.routes[i]) {
            CompoundTag n = new CompoundTag();
            n.putDouble("X", p.x());
            n.putDouble("Y", p.y());
            n.putDouble("Z", p.z());
            n.putDouble("Speed", p.speed());
            route.add(n);
         }

         t.put("Route", route);
         t.putString("Status", this.messages[i]);
         if (this.lastFlight[i] != null) {
            t.putUUID("Flight", this.lastFlight[i]);
         }

         Su24mEntity.Order o = this.orders[i];
         if (o != null) {
            t.putUUID("Mission", o.mission());
            t.putUUID("Operator", o.operator());
            t.putInt("Mode", o.mode());
            t.putLong("Earliest", o.earliest());
            t.putLong("Expires", o.expires());
            t.putDouble("Speed", o.speed());
         }

         pylons.add(t);
      }

      tag.put("Su24Pylons", pylons);
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.entityData.set(ROUNDS, tag.contains("Su24Rounds") ? Math.max(0, Math.min(500, tag.getInt("Su24Rounds"))) : 500);
      this.commandedThrottle = tag.contains("Su24Throttle") ? tag.getDouble("Su24Throttle") : -1.0;
      ListTag pylons = tag.getList("Su24Pylons", 10);

      for (int i = 0; i < Math.min(2, pylons.size()); i++) {
         CompoundTag t = pylons.getCompound(i);
         this.stores[i] = ItemStack.parseOptional(this.registryAccess(), t.getCompound("Store"));
         ArrayList<RoutePoint> route = new ArrayList<>();

         for (Tag n : t.getList("Route", 10)) {
            CompoundTag p = (CompoundTag)n;
            route.add(new RoutePoint(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"), p.getDouble("Speed")));
         }

         this.routes[i] = List.copyOf(route);
         this.messages[i] = t.getString("Status");
         this.lastFlight[i] = t.hasUUID("Flight") ? t.getUUID("Flight") : null;
         this.orders[i] = t.hasUUID("Mission") && t.hasUUID("Operator")
            ? new Su24mEntity.Order(
               t.getUUID("Mission"), t.getUUID("Operator"), t.getInt("Mode"), t.getLong("Earliest"), t.getLong("Expires"), t.getDouble("Speed")
            )
            : null;
      }

      this.syncRacks();
   }

   public record Order(UUID mission, UUID operator, int mode, long earliest, long expires, double speed) {
   }
}
