package dev.sbw.deepstrike.vehicle;

import com.atsuishio.superbwarfare.config.server.VehicleConfig;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.AirframeItem;
import dev.sbw.deepstrike.game.DeepStrikeData;
import dev.sbw.deepstrike.game.DefenseRuntime;
import dev.sbw.deepstrike.game.FlightRoutePlanner;
import dev.sbw.deepstrike.game.FlightTerrain;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LaunchJournal;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.MissileLaunchEffects;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.game.SimpleFlightPlan;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.network.MissileCameraStream;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.MoveFunction;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SpikeNlosEntity extends DeepStrikeVehicleEntity {
   private static final EntityDataAccessor<Integer> AMMO = SynchedEntityData.defineId(SpikeNlosEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> RADIO_FREQUENCY = SynchedEntityData.defineId(SpikeNlosEntity.class, EntityDataSerializers.STRING);
   private UUID activeMissile;
   private int activeMissileMissingTicks;
   private UUID driverPilot;
   private long driverInputUntil;
   private FlightTerrain launchTerrain;
   private FlightRoutePlanner launchPlanner;
   private List<RoutePoint> launchRoute;
   private SpikeNlosEntity.PendingLaunch pendingLaunch;

   public String transmitFrequency() {
      return (String)this.entityData.get(RADIO_FREQUENCY);
   }

   public boolean hasPendingLaunch() {
      return this.pendingLaunch != null;
   }

   public boolean queueLaunch(ServerPlayer player, Vec3 target, double cruiseY, double speed, String frequency) {
      return this.queueLaunch(player, target, cruiseY, speed, frequency, UUID.randomUUID());
   }

   public boolean queueLaunch(ServerPlayer player, Vec3 target, double cruiseY, double speed, String frequency, UUID commandId) {
      ActiveFlights flights = ActiveFlights.get(player.serverLevel());
      if (flights.knowsMission(commandId)) {
         if (!flights.acceptedBy(commandId, LaunchJournal.id(this), player.getUUID())) {
            return false;
         } else {
            if (this.pendingLaunch != null && this.pendingLaunch.mission().equals(commandId)) {
               this.sendCountdown(player, true);
            }

            return true;
         }
      } else if (this.pendingLaunch == null
         && player.getVehicle() == this
         && this.isAlive()
         && this.activeMissile == null
         && this.readyMissiles() != 0
         && RadioFrequency.valid(frequency)
         && Double.isFinite(target.x)
         && Double.isFinite(target.y)
         && Double.isFinite(target.z)
         && Double.isFinite(cruiseY)
         && Double.isFinite(speed)
         && !(target.y < this.level().getMinBuildHeight() + 1)
         && !(target.y > this.level().getMaxBuildHeight() - 2)
         && !(speed < MissileKind.SPIKE_NLOS.minSpeed)
         && !(speed > MissileKind.SPIKE_NLOS.maxSpeed)
         && !(target.distanceTo(this.position()) < MissileKind.SPIKE_NLOS.minRange)
         && !(target.distanceTo(this.position()) > MissileKind.SPIKE_NLOS.maxRange)) {
         Vec3 spawn = this.launchPosition();
         long launchAt = this.level().getGameTime() + 100L;
         if (!flights.acceptLaunch(
            player.serverLevel(), commandId, LaunchJournal.id(this), player.getUUID(), this.blockPosition(), target, MissileKind.SPIKE_NLOS.id, launchAt
         )) {
            return false;
         } else {
            this.launchTerrain = new FlightTerrain(
               player.serverLevel(), spawn, List.of(new RoutePoint(target.x, target.y, target.z, speed)), MissileKind.SPIKE_NLOS
            );
            this.pendingLaunch = new SpikeNlosEntity.PendingLaunch(player.getUUID(), target, cruiseY, speed, frequency, commandId, launchAt, this.blockPosition());
            this.sendCountdown(player, true);
            this.holdLaunchChunk(true);
            this.entityData.set(RADIO_FREQUENCY, frequency);
            return true;
         }
      } else {
         return false;
      }
   }

   public void lockDriverControls(ServerPlayer p, boolean active) {
      if (active && this.getSeatIndex(p) == 0) {
         this.driverPilot = p.getUUID();
         this.driverInputUntil = this.level().getGameTime() + 40L;
      } else if (!active && p.getUUID().equals(this.driverPilot)) {
         this.driverPilot = null;
         this.driverInputUntil = 0L;
      }
   }

   public void processInput(short input) {
      if (this.pendingLaunch != null || this.driverPilot != null && this.level().getGameTime() < this.driverInputUntil) {
         input = 0;
      }

      super.processInput(input);
   }

   public SpikeNlosEntity(EntityType<? extends SpikeNlosEntity> type, Level level) {
      super(type, level);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      super.defineSynchedData(builder);
      builder.define(AMMO, 0);
      builder.define(RADIO_FREQUENCY, "");
   }

   public int readyMissiles() {
      if (this.level().isClientSide) {
         return (Integer)this.entityData.get(AMMO);
      } else {
         int n = 0;

         for (int i = 0; i < this.getContainerSize(); i++) {
            MissileData data = MissileData.fromItemStack(this.getItem(i));
            if (data != null
               && data.kind() == MissileKind.SPIKE_NLOS
               && data.isFueled()
               && data.supportsWarhead()
               && !(this.getItem(i).getItem() instanceof AirframeItem)) {
               n += this.getItem(i).getCount();
            }
         }

         return Math.min(4, n);
      }
   }

   @Override
   public void tick() {
      if (this.pendingLaunch != null || this.driverPilot != null && this.level().getGameTime() < this.driverInputUntil) {
         this.setForwardInputDown(false);
         this.setBackInputDown(false);
         this.setLeftInputDown(false);
         this.setRightInputDown(false);
      }

      super.tick();
      if (!this.level().isClientSide) {
         if (this.pendingLaunch != null && this.level() instanceof ServerLevel server) {
            SpikeNlosEntity.PendingLaunch pending = this.pendingLaunch;
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(pending.player());
            UUID restoredFlight = ActiveFlights.get(server).launchedFlight(pending.mission());
            if (restoredFlight == null) {
               if (this.launchTerrain == null) {
                  this.launchTerrain = new FlightTerrain(
                     server,
                     this.position(),
                     List.of(new RoutePoint(pending.target().x, pending.target().y, pending.target().z, pending.speed())),
                     MissileKind.SPIKE_NLOS
                  );
               }

               this.holdLaunchChunk(true);
               this.launchTerrain.ready();
               if (this.launchPlanner == null) {
                  Vec3 spawn = this.launchPosition();
                  double height = Mth.clamp(pending.cruiseY(), spawn.y + 6.0, this.level().getMaxBuildHeight() + 256);
                  Vec3 cruiseStart = spawn.lerp(pending.target(), 0.25);
                  Vec3 cruiseEnd = spawn.lerp(pending.target(), 0.75);
                  this.launchPlanner = new FlightRoutePlanner(
                     server,
                     this.blockPosition(),
                     spawn,
                     new Vec3(0.0, 1.0, 0.0),
                     MissileKind.SPIKE_NLOS,
                     List.of(
                        new RoutePoint(cruiseStart.x, height, cruiseStart.z, pending.speed()),
                        new RoutePoint(cruiseEnd.x, height, cruiseEnd.z, pending.speed()),
                        new RoutePoint(pending.target().x, pending.target().y, pending.target().z, pending.speed())
                     )
                  );
               }

               if (!this.launchPlanner.done()) {
                  this.launchPlanner.step();
               }

               if (this.launchPlanner.done()) {
                  this.launchRoute = this.launchPlanner.result();
               }

               if (this.launchRoute == null && server.getGameTime() >= pending.launchAtTick()) {
                  this.launchRoute = SimpleFlightPlan.build(
                     this.launchPosition(),
                     new Vec3(0.0, 1.0, 0.0),
                     MissileKind.SPIKE_NLOS,
                     List.of(new RoutePoint(pending.target().x, pending.target().y, pending.target().z, pending.speed()))
                  );
               }

               if (server.getGameTime() % 5L == 0L && player != null) {
                  this.sendCountdown(player, true);
               }

               if (server.getGameTime() >= pending.launchAtTick()) {
                  GuidedMissileEntity launched = this.launchRoute == null
                     ? null
                     : this.launchPrepared(pending.player(), pending.target(), pending.cruiseY(), pending.speed(), pending.frequency(), pending.mission());
                  if (launched != null) {
                     launched.setPreparedTerrain(this.launchTerrain);
                     this.launchTerrain = null;
                  } else {
                     this.launchTerrain.close();
                     this.launchTerrain = null;
                     ActiveFlights.get(server).cancelLaunch(pending.mission());
                  }

                  if (player != null) {
                     this.sendCountdown(player, false);
                     if (launched == null) {
                        player.displayClientMessage(Component.translatable("message.deepstrike.spike.not_ready"), true);
                     }
                  }

                  this.clearLaunchPlan();
                  this.pendingLaunch = null;
                  this.holdLaunchChunk(false);
               }
            } else {
               if (!restoredFlight.equals(this.activeMissile)) {
                  for (int i = 0; i < this.getContainerSize(); i++) {
                     ItemStack stack = this.getItem(i);
                     MissileData data = MissileData.fromItemStack(stack);
                     if (data != null
                        && data.kind() == MissileKind.SPIKE_NLOS
                        && data.isFueled()
                        && data.supportsWarhead()
                        && !(stack.getItem() instanceof AirframeItem)) {
                        stack.shrink(1);
                        this.setChanged();
                        break;
                     }
                  }
               }

               this.activeMissile = ActiveFlights.get(server).hasActiveFlight(restoredFlight) ? restoredFlight : null;
               this.activeMissileMissingTicks = 0;
               if (this.launchTerrain != null) {
                  this.launchTerrain.close();
                  this.launchTerrain = null;
               }

               if (player != null) {
                  this.sendCountdown(player, false);
               }

               this.clearLaunchPlan();
               this.pendingLaunch = null;
               this.holdLaunchChunk(false);
            }
         }

         this.entityData.set(AMMO, this.readyMissiles());
         if (!(this.activeMissile != null && this.level() instanceof ServerLevel s)
            || s.getEntity(this.activeMissile) instanceof GuidedMissileEntity m && m.isAlive()
            || ActiveFlights.get(s).hasActiveFlight(this.activeMissile)) {
            this.activeMissileMissingTicks = 0;
         } else if (++this.activeMissileMissingTicks > 100) {
            this.activeMissile = null;
            this.activeMissileMissingTicks = 0;
         }
      }
   }

   private Vec3 launchPosition() {
      return this.position().add(new Vec3(0.0, 4.7, -1.15).yRot((float)(-Math.toRadians(this.getYRot()))));
   }

   private void clearLaunchPlan() {
      if (this.launchPlanner != null) {
         this.launchPlanner.cancel();
      }

      this.launchPlanner = null;
      this.launchRoute = null;
   }

   private void holdLaunchChunk(boolean active) {
      if (this.level() instanceof ServerLevel server) {
         DefenseRuntime.get(server).remember(this);
      }
   }

   private void sendCountdown(ServerPlayer player, boolean active) {
      if (this.pendingLaunch != null && player.serverLevel() == this.level()) {
         DeepStrikeNetwork.sendLaunchCountdown(
            player, this.pendingLaunch.hudPos(), this.pendingLaunch.mission(), this.pendingLaunch.launchAtTick(), active, "READY"
         );
      }
   }

   @Override
   public void remove(RemovalReason reason) {
      this.clearLaunchPlan();
      if (this.launchTerrain != null) {
         this.launchTerrain.close();
         this.launchTerrain = null;
      }

      if ((reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) && this.pendingLaunch != null && this.level() instanceof ServerLevel server) {
         ActiveFlights.get(server).cancelLaunch(this.pendingLaunch.mission());
         ServerPlayer player = server.getServer().getPlayerList().getPlayer(this.pendingLaunch.player());
         if (player != null) {
            this.sendCountdown(player, false);
         }

         this.pendingLaunch = null;
      }

      super.remove(reason);
   }

   @Override
   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      DeepStrikeData.stamp(tag);
      if (this.activeMissile != null) {
         tag.putUUID("SpikeActiveMissile", this.activeMissile);
      }

      tag.putString("RadioFrequency", this.transmitFrequency());
      if (this.pendingLaunch != null) {
         SpikeNlosEntity.PendingLaunch p = this.pendingLaunch;
         CompoundTag queued = new CompoundTag();
         queued.putUUID("Owner", p.player());
         queued.putUUID("Mission", p.mission());
         queued.putLong("LaunchAtTick", p.launchAtTick());
         queued.putDouble("X", p.target().x);
         queued.putDouble("Y", p.target().y);
         queued.putDouble("Z", p.target().z);
         queued.putDouble("CruiseY", p.cruiseY());
         queued.putDouble("Speed", p.speed());
         queued.putString("Frequency", p.frequency());
         queued.putLong("HudPos", p.hudPos().asLong());
         tag.put("PendingLaunch", queued);
      }
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      this.activeMissile = tag.hasUUID("SpikeActiveMissile") ? tag.getUUID("SpikeActiveMissile") : null;
      if (RadioFrequency.valid(tag.getString("RadioFrequency"))) {
         this.entityData.set(RADIO_FREQUENCY, tag.getString("RadioFrequency"));
      }

      this.pendingLaunch = null;
      CompoundTag queued = tag.getCompound("PendingLaunch");
      if (queued.hasUUID("Owner") && queued.hasUUID("Mission")) {
         this.pendingLaunch = new SpikeNlosEntity.PendingLaunch(
            queued.getUUID("Owner"),
            new Vec3(queued.getDouble("X"), queued.getDouble("Y"), queued.getDouble("Z")),
            queued.getDouble("CruiseY"),
            queued.getDouble("Speed"),
            queued.getString("Frequency"),
            queued.getUUID("Mission"),
            queued.getLong("LaunchAtTick"),
            BlockPos.of(queued.getLong("HudPos"))
         );
      }
   }

   public void positionRider(Entity passenger, MoveFunction callback) {
      int index = this.getSeatIndex(passenger);
      if (index >= 0 && index <= 1 && this.hasPassenger(passenger)) {
         this.passengerPos(
            passenger, callback, new Vec3(index == 0 ? -0.5 : 0.5, 0.65625 - VehicleConfig.getPassengerYOffset(passenger.getType()), 0.0), "Vehicle"
         );
      }
   }

   public GuidedMissileEntity launch(ServerPlayer player, Vec3 target, double cruiseY, double speed, String frequency) {
      if (player.getVehicle() != this) {
         return null;
      } else {
         this.queueLaunch(player, target, cruiseY, speed, frequency);
         return null;
      }
   }

   private GuidedMissileEntity launchPrepared(UUID owner, Vec3 target, double cruiseY, double speed, String frequency, UUID mission) {
      if (!(this.level() instanceof ServerLevel server && !ActiveFlights.get(server).wasLaunched(mission))) {
         return null;
      } else if (this.isAlive()
         && this.activeMissile == null
         && this.readyMissiles() != 0
         && RadioFrequency.valid(frequency)
         && Double.isFinite(target.x)
         && Double.isFinite(target.y)
         && Double.isFinite(target.z)
         && Double.isFinite(cruiseY)
         && Double.isFinite(speed)
         && !(target.y < this.level().getMinBuildHeight() + 1)
         && !(target.y > this.level().getMaxBuildHeight() - 2)
         && !(speed < MissileKind.SPIKE_NLOS.minSpeed)
         && !(speed > MissileKind.SPIKE_NLOS.maxSpeed)
         && !(target.distanceTo(this.position()) < MissileKind.SPIKE_NLOS.minRange)
         && !(target.distanceTo(this.position()) > MissileKind.SPIKE_NLOS.maxRange)) {
         for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack stack = this.getItem(i);
            MissileData data = MissileData.fromItemStack(stack);
            if (data != null
               && data.kind() == MissileKind.SPIKE_NLOS
               && data.isFueled()
               && data.supportsWarhead()
               && !(stack.getItem() instanceof AirframeItem)) {
               GuidedMissileEntity missile = new GuidedMissileEntity(this.level(), MissileKind.SPIKE_NLOS, false, data.camo());
               missile.setUUID(mission);
               Vec3 spawn = this.launchPosition();
               missile.moveTo(spawn.x, spawn.y, spawn.z, this.getYRot(), -90.0F);
               missile.applyMissileData(data);
               missile.setOwnerId(owner);
               missile.setMissionId(mission);
               missile.setFrequency(frequency);
               missile.setLaunchVehicle(this.getUUID());
               missile.setMission(List.of(new RoutePoint(target.x, target.y, target.z, speed)), false);
               if (this.launchRoute != null) {
                  missile.setPlannedRoute(this.launchRoute);
               }

               missile.configureSpike(Mth.clamp(cruiseY, spawn.y + 6.0, this.level().getMaxBuildHeight() + 256), speed);
               missile.setLaunchVector(new Vec3(0.0, 1.0, 0.0), 20);
               if (!this.level().addFreshEntity(missile)) {
                  return null;
               }

               LaunchJournal.recordOnce(
                  (ServerLevel)this.level(),
                  LaunchJournal.id(this),
                  mission,
                  "launch",
                  this.position(),
                  "SPIKE NLOS",
                  target,
                  Component.translatable("journal.deepstrike.launch_frequency", new Object[]{frequency})
               );
               stack.shrink(1);
               this.setChanged();
               this.activeMissile = missile.getUUID();
               this.activeMissileMissingTicks = 0;
               MissileLaunchEffects.spawnLaunchPlume(this.level(), spawn, new Vec3(0.0, 1.0, 0.0));
               this.level()
                  .playSound(
                     null, spawn.x, spawn.y, spawn.z, BFSounds.launchForKind(MissileKind.SPIKE_NLOS), SoundSource.BLOCKS, 3.0F, 1.08F
                  );
               DeepStrikeNetwork.flight(missile, true);
               ServerPlayer player = ((ServerLevel)this.level()).getServer().getPlayerList().getPlayer(owner);
               if (player != null && player.getVehicle() == this) {
                  DeepStrikeNetwork.sendCameraTarget(player, missile.getId());
                  MissileCameraStream.begin(player, missile);
               }

               return missile;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private record PendingLaunch(UUID player, Vec3 target, double cruiseY, double speed, String frequency, UUID mission, long launchAtTick, BlockPos hudPos) {
   }
}
