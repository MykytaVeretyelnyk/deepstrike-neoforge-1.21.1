package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.client.ContrailRenderer;
import dev.sbw.deepstrike.client.PatriotRemoteEffects;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.IronDomeCanisterLayout;
import dev.sbw.deepstrike.model.PatriotCanisterLayout;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.slf4j.LoggerFactory;

public final class PatriotInterceptorEntity extends Entity {
   private static final EntityDataAccessor<Integer> WARHEAD = SynchedEntityData.defineId(PatriotInterceptorEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> ROT_YAW = SynchedEntityData.defineId(PatriotInterceptorEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> ROT_PITCH = SynchedEntityData.defineId(PatriotInterceptorEntity.class, EntityDataSerializers.FLOAT);
   private final Set<ChunkPos> flightChunks = new HashSet<>();
   private static final TicketType<UUID> FLIGHT_TICKET = TicketType.create("deepstrike_interceptor", Comparator.<UUID>naturalOrder());
   private long lastFlightTick = Long.MIN_VALUE;
   private Vec3 filteredDomeAim;
   private Vec3 clientTarget;
   private int interpolationSteps;
   private double distanceTravelled;
   public float previousYaw;
   public float previousPitch;
   private UUID targetId;
   private UUID sourceDefense;
   private boolean forcedManual = true;
   private boolean plannedHit;
   private Vec3 missOffset = Vec3.ZERO;
   private int age;
   private Vec3 lastDirection = new Vec3(0.0, 0.0, 1.0);
   private Vec3 launchDirection = new Vec3(0.0, 0.0, 1.0);
   private boolean canisterLaunch;
   private boolean domeLaunch;
   private double canisterSetback;
   private Vec3 previousTrailPos;
   private Vec3 impactSurface;

   public boolean tickedAt(long tick) {
      return this.lastFlightTick == tick;
   }

   public boolean shouldBeSaved() {
      return false;
   }

   public boolean isAlwaysTicking() {
      return true;
   }

   public void setDefenseSource(UUID source, boolean manual) {
      this.sourceDefense = source;
      this.forcedManual = manual;
   }

   public UUID defenseSource() {
      return this.sourceDefense;
   }

   public boolean isDomeLaunch() {
      return this.domeLaunch;
   }

   public UUID targetId() {
      return this.targetId;
   }

   public PatriotInterceptorEntity(EntityType<? extends PatriotInterceptorEntity> type, Level level) {
      super(type, level);
   }

   public PatriotInterceptorEntity(Level level, Vec3 spawnPos, Vec3 initialDirection, Entity target, int warhead, boolean plannedHit) {
      this((EntityType<? extends PatriotInterceptorEntity>)BFEntities.PATRIOT_INTERCEPTOR.get(), level);
      this.targetId = target.getUUID();
      this.plannedHit = plannedHit;
      this.entityData.set(WARHEAD, warhead);
      this.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
      this.lastDirection = initialDirection.lengthSqr() > 1.0E-6 ? initialDirection.normalize() : new Vec3(0.0, 1.0, 0.0);
      if ((warhead == 1 || warhead == 3) && !plannedHit) {
         Vec3 approach = AirDefenseTargets.aim(target).subtract(this.position());
         Vec3 side = new Vec3(-approach.z, 0.0, approach.x);
         this.missOffset = (side.lengthSqr() > 1.0E-8 ? side.normalize() : new Vec3(1.0, 0.0, 0.0)).scale(level.random.nextBoolean() ? 6.5 : -6.5);
      }

      this.launchDirection = this.lastDirection;
      this.updateRotation();
      this.previousYaw = this.yaw();
      this.previousPitch = this.pitch();
   }

   public PatriotInterceptorEntity(Level level, PatriotTruckEntity launcher, Entity target, int warhead, boolean plannedHit) {
      this((EntityType<? extends PatriotInterceptorEntity>)BFEntities.PATRIOT_INTERCEPTOR.get(), level);
      this.setDefenseSource(launcher.getUUID(), true);
      this.targetId = target.getUUID();
      this.plannedHit = plannedHit;
      this.entityData.set(WARHEAD, warhead);
      int tube = launcher.launchCell(warhead);
      if (tube < 0) {
         throw new IllegalArgumentException("No loaded PAC cell for warhead " + warhead);
      } else {
         Vec3 local = PatriotCanisterLayout.elevatedMouth(tube);
         Matrix4d transform = launcher.getVehicleTransform(1.0F);
         Vector4d muzzle = launcher.transformPosition(transform, local.x, local.y, local.z);
         Vec3 axis = PatriotCanisterLayout.axis();
         Vector3d direction = transform.transformDirection(new Vector3d(axis.x, axis.y, axis.z));
         this.lastDirection = new Vec3(direction.x, direction.y, direction.z).normalize();
         this.canisterLaunch = true;
         this.canisterSetback = PatriotCanisterLayout.nose(warhead) + 0.08;
         this.setPos(new Vec3(muzzle.x, muzzle.y, muzzle.z).subtract(this.lastDirection.scale(this.canisterSetback)));
         if ((warhead == 1 || warhead == 3) && !plannedHit) {
            Vec3 approach = AirDefenseTargets.aim(target).subtract(this.position());
            Vec3 side = new Vec3(-approach.z, 0.0, approach.x);
            this.missOffset = (side.lengthSqr() > 1.0E-8 ? side.normalize() : new Vec3(1.0, 0.0, 0.0)).scale(level.random.nextBoolean() ? 6.5 : -6.5);
         }

         this.launchDirection = this.lastDirection;
         this.updateRotation();
         this.previousYaw = this.yaw();
         this.previousPitch = this.pitch();
      }
   }

   public PatriotInterceptorEntity(Level level, IronDomeEntity launcher, Entity target, boolean plannedHit) {
      this((EntityType<? extends PatriotInterceptorEntity>)BFEntities.PATRIOT_INTERCEPTOR.get(), level);
      this.setDefenseSource(launcher.getUUID(), false);
      this.targetId = target.getUUID();
      this.plannedHit = plannedHit;
      this.entityData.set(WARHEAD, 3);
      int cell = launcher.spendCell();
      if (cell < 0) {
         throw new IllegalArgumentException("No loaded Tamir cell");
      } else {
         Vec3 local = IronDomeCanisterLayout.elevatedMouth(cell);
         Matrix4d transform = launcher.getVehicleTransform(1.0F);
         Vector4d muzzle = launcher.transformPosition(transform, local.x, local.y, local.z);
         Vec3 axis = IronDomeCanisterLayout.axis();
         Vector3d direction = transform.transformDirection(new Vector3d(axis.x, axis.y, axis.z));
         this.lastDirection = new Vec3(direction.x, direction.y, direction.z).normalize();
         this.domeLaunch = true;
         this.canisterSetback = IronDomeCanisterLayout.setback();
         this.setPos(new Vec3(muzzle.x, muzzle.y, muzzle.z).subtract(this.lastDirection.scale(this.canisterSetback)));
         if (!plannedHit) {
            Vec3 approach = AirDefenseTargets.aim(target).subtract(this.position());
            Vec3 side = new Vec3(-approach.z, 0.0, approach.x);
            this.missOffset = (side.lengthSqr() > 1.0E-8 ? side.normalize() : new Vec3(1.0, 0.0, 0.0)).scale(level.random.nextBoolean() ? 6.5 : -6.5);
         }

         this.launchDirection = this.lastDirection;
         this.updateRotation();
         this.previousYaw = this.yaw();
         this.previousPitch = this.pitch();
      }
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      builder.define(WARHEAD, 0);
      builder.define(ROT_YAW, 0.0F);
      builder.define(ROT_PITCH, 0.0F);
   }

   public int warhead() {
      return (Integer)this.entityData.get(WARHEAD);
   }

   public float yaw() {
      return (Float)this.entityData.get(ROT_YAW);
   }

   public float pitch() {
      return (Float)this.entityData.get(ROT_PITCH);
   }


   // 1.21 re-sends these as the target when a move packet carries only rotation (or only position).
   @Override
   public double lerpTargetX() {
      return this.interpolationSteps > 0 && this.clientTarget != null ? this.clientTarget.x : this.getX();
   }

   @Override
   public double lerpTargetY() {
      return this.interpolationSteps > 0 && this.clientTarget != null ? this.clientTarget.y : this.getY();
   }

   @Override
   public double lerpTargetZ() {
      return this.interpolationSteps > 0 && this.clientTarget != null ? this.clientTarget.z : this.getZ();
   }

   public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
      boolean teleport = false;
      this.clientTarget = new Vec3(x, y, z);
      this.interpolationSteps = teleport ? 1 : Math.max(1, Math.min(3, steps));
   }

   private void updateRotation() {
      this.entityData.set(ROT_YAW, (float)Math.toDegrees(Math.atan2(this.lastDirection.z, this.lastDirection.x)) - 90.0F);
      this.entityData.set(ROT_PITCH, (float)(-Math.toDegrees(Math.atan2(this.lastDirection.y, this.lastDirection.horizontalDistance()))));
   }

   private void emitContrail() {
      Vec3 cur = this.position();
      if (PatriotRemoteEffects.hidesTracked(this)) {
         this.previousTrailPos = cur;
      } else if (this.previousTrailPos == null) {
         this.previousTrailPos = cur;
      } else {
         double dist = this.previousTrailPos.distanceTo(cur);
         if (dist > 0.005 && dist < 50.0) {
            ContrailRenderer.recordInterceptor(this);
         }

         this.previousTrailPos = cur;
      }
   }

   public void tick() {
      if (this.level() instanceof ServerLevel server) {
         if (this.lastFlightTick == server.getGameTime()) {
            return;
         }

         this.lastFlightTick = server.getGameTime();
      }

      this.previousYaw = this.yaw();
      this.previousPitch = this.pitch();
      super.tick();
      this.age++;
      if (this.level().isClientSide) {
         if (this.interpolationSteps > 0 && this.clientTarget != null) {
            this.setPos(this.position().lerp(this.clientTarget, 1.0 / this.interpolationSteps--));
         }

         this.emitContrail();
      } else if (this.level() instanceof ServerLevel server && this.targetId != null && this.age <= 1200) {
         this.updateChunkTickets(server);
         Entity candidate = server.getEntity(this.targetId);
         if (!AirDefenseTargets.target(candidate)) {
            this.burst(server, false);
         } else if (!this.forcedManual
            && this.sourceDefense != null
            && server.getEntity(this.sourceDefense) instanceof IronDomeEntity dome
            && candidate instanceof GuidedMissileEntity missile
            && dome.isFriendly(missile)) {
            this.discard();
         } else {
            double maxSpeed = FlightUnits.blocksPerTick(this.warhead() == 3 ? 1008.0 : (this.warhead() == 0 ? 1296.0 : 1584.0));
            double speed = this.poweredSpeed(this.getDeltaMovement().length(), this.age, maxSpeed);
            if (this.canisterLaunch) {
               double previous = this.getDeltaMovement().length();
               speed = Math.min(speed, Math.max(0.45, previous + 0.22));
               if (this.distanceTravelled - this.canisterSetback < 8.0) {
                  speed = Math.min(speed, 1.25);
               }
            } else if (this.domeLaunch) {
               speed = Math.min(IronDomeLaunchPath.speed(this.age, maxSpeed), this.getDeltaMovement().length() + 0.24);
            }

            Vec3 relative = AirDefenseTargets.aim(candidate).subtract(this.position());
            Vec3 targetVelocity = candidate.getDeltaMovement();
            Vec3 aim = this.predictedAim(candidate, maxSpeed).add(this.missOffset);
            if (this.domeLaunch) {
               this.filteredDomeAim = this.filteredDomeAim == null ? aim : this.filteredDomeAim.add(targetVelocity).lerp(aim, 0.32);
               aim = this.filteredDomeAim;
            }

            Vec3 pursuitDirection = aim.subtract(this.position()).normalize();
            double acquisition = Mth.clamp((this.age - 20.0) / 20.0, 0.0, 1.0);
            acquisition = acquisition * acquisition * (3.0 - 2.0 * acquisition);
            Vec3 blended = this.launchDirection.scale(1.0 - acquisition).add(pursuitDirection.scale(acquisition));
            Vec3 desired = blended.lengthSqr() > 1.0E-10 ? blended.normalize() : this.lastDirection;
            if (this.canisterLaunch) {
               double clearance = Math.max(0.0, this.distanceTravelled - this.canisterSetback);
               acquisition = PatriotLaunchPath.acquisition(clearance);
               desired = PatriotLaunchPath.direction(this.launchDirection, pursuitDirection, clearance);
            } else if (this.domeLaunch) {
               acquisition = IronDomeLaunchPath.acquisition(this.age);
               desired = IronDomeLaunchPath.direction(this.launchDirection, pursuitDirection, this.age);
            }

            double angle = Math.acos(Mth.clamp(this.lastDirection.dot(desired), -1.0, 1.0));
            double lateral = this.warhead() == 3 ? 350.0 : (this.warhead() == 0 ? 300.0 : 500.0);
            double angular = Math.toRadians(this.warhead() == 3 ? 16.0 : (this.warhead() == 0 ? 8.0 : 12.0));
            if (acquisition > 0.0 && angle > 0.015) {
               double sine = angle > Math.PI / 2 ? 1.0 : Math.sin(angle);
               double radius = Math.max(1.0, this.position().distanceTo(aim) / (2.0 * Math.max(0.001, sine)));
               double captureSpeed = 0.85 * Math.min(Math.sqrt(lateral * radius), angular / 0.05 * radius) * 0.05;
               double requested = Math.min(speed, captureSpeed);
               double current = this.getDeltaMovement().length();
               speed = current > 0.0 ? Math.max(requested, current - (this.warhead() == 0 ? 0.2 : 0.25)) : requested;
            }

            double maxTurn = FlightDynamics.turnLimit(
               speed,
               this.warhead() == 3 ? 350.0 : (this.warhead() == 0 ? 300.0 : 500.0),
               Math.toRadians(this.warhead() == 3 ? 16.0 : (this.warhead() == 0 ? 8.0 : 12.0))
            );
            if (angle > maxTurn) {
               Vec3 tangent = desired.subtract(this.lastDirection.scale(this.lastDirection.dot(desired)));
               if (tangent.lengthSqr() < 1.0E-10) {
                  tangent = this.lastDirection.cross(new Vec3(0.0, 1.0, 0.0));
               }

               if (tangent.lengthSqr() < 1.0E-10) {
                  tangent = new Vec3(1.0, 0.0, 0.0);
               }

               desired = this.lastDirection.scale(Math.cos(maxTurn)).add(tangent.normalize().scale(Math.sin(maxTurn)));
            }

            Vec3 previousDirection = this.lastDirection;
            this.lastDirection = desired.normalize();
            Vec3 movement = this.lastDirection.scale(speed);
            Vec3 start = this.position();
            Vec3 end = start.add(movement);
            this.setDeltaMovement(movement);
            AABB body = InterceptorGeometry.substepBounds(this.warhead(), start, previousDirection, movement, this.yaw(), this.pitch());
            FlightCollision.SweepResult blockContact = FlightCollision.sweepSegment(server, this, start, end, body, e -> false);
            HitResult blockHit = blockContact.hit();
            double blockFraction = blockContact.fraction();
            Vec3 relativeStep = movement.subtract(targetVelocity);
            Vec3 offset = start.subtract(AirDefenseTargets.aim(candidate));
            double radius = (this.warhead() == 3 ? 4.5 : (this.warhead() == 0 ? 13.0 : 0.55))
               + (candidate instanceof GuidedMissileEntity ? 0.0 : Math.max(candidate.getBbWidth(), candidate.getBbHeight()) * 0.4);
            double hitFraction = !(candidate instanceof GuidedMissileEntity missile && (this.warhead() == 1 || this.warhead() == 2))
               ? contactTime(offset, relativeStep, radius)
               : this.missileContact(missile, start, movement, previousDirection, this.lastDirection, targetVelocity);
            if (hitFraction >= 0.0 && hitFraction <= blockFraction) {
               this.setPos(start.add(movement.scale(hitFraction)));
               boolean clearPath = this.warhead() == 1
                  || FlightCollision.knownClear(server, this.position(), AirDefenseTargets.aim(candidate).add(targetVelocity.scale(hitFraction)), this);
               boolean hit = this.plannedHit && clearPath;
               if (hit) {
                  if (candidate instanceof GuidedMissileEntity) {
                     candidate.setPos(candidate.position().add(targetVelocity.scale(hitFraction)));
                  }

                  this.updateRotation();
                  PatriotDefenseNetwork.flight(this, false);
                  AirDefenseTargets.hit(candidate, this, this.warhead());
               }

               this.burst(server, hit);
            } else {
               if (!this.plannedHit && this.missOffset.lengthSqr() > 0.0 && this.age > 40 && relativeStep.lengthSqr() > 1.0E-8) {
                  double nearest = Mth.clamp(-offset.dot(relativeStep) / relativeStep.lengthSqr(), 0.0, 1.0);
                  double missEnvelope = radius + this.missOffset.length() + 1.0;
                  if (nearest < 1.0 && nearest <= blockFraction && offset.add(relativeStep.scale(nearest)).lengthSqr() <= missEnvelope * missEnvelope) {
                     this.setPos(start.add(movement.scale(nearest)));
                     if (Boolean.getBoolean("deepstrike.airDefenseReview")) {
                        LoggerFactory.getLogger(PatriotInterceptorEntity.class)
                           .info("AIR_DEFENSE_MISS_RETRY_RELEASED warhead={} point={}", this.warhead(), this.position());
                     }

                     this.burst(server, false);
                     return;
                  }
               }

               if (blockHit.getType() != Type.MISS) {
                  this.impactSurface = blockContact.surface();
                  this.setPos(blockHit.getLocation());
                  this.burst(server, false);
               } else {
                  this.setDeltaMovement(movement);
                  this.setPos(end);
                  this.distanceTravelled += speed;
                  this.updateRotation();
                  if (this.tickCount < 500 && !(this.distanceTravelled > (this.warhead() == 3 ? 4500 : (this.warhead() == 0 ? 2200 : 4000)))) {
                     PatriotDefenseNetwork.flight(this, true);
                  } else {
                     this.burst(server, false);
                  }
               }
            }
         }
      } else {
         this.discard();
      }
   }

   private double missileContact(GuidedMissileEntity target, Vec3 start, Vec3 movement, Vec3 before, Vec3 after, Vec3 targetVelocity) {
      double nose = Math.max(0.0, -InterceptorGeometry.canonicalBounds(this.warhead()).minX);
      return MissileModelContact.fraction(
         target, start.add(before.scale(nose)), start.add(movement).add(after.scale(nose)), targetVelocity
      );
   }

   private double poweredSpeed(double previous, int atAge, double maximum) {
      double envelope = this.domeLaunch ? IronDomeLaunchPath.speed(atAge, maximum) : maximum * Math.min(1.0, 0.025 + atAge * 1.0 / 55.0);
      double acceleration = this.warhead() == 3 ? 0.24 : (this.warhead() == 0 ? 0.22 : 0.3);
      return Math.min(envelope, Math.max(0.35, previous + acceleration));
   }

   private Vec3 predictedAim(Entity target, double maxSpeed) {
      List<Vec3> path = target instanceof GuidedMissileEntity missile ? missile.interceptOriginForecast() : List.of();
      Vec3 bodyOffset = target instanceof GuidedMissileEntity missilex
         ? MissileModelContact.aimOffset(missilex, 0.25F + 0.5F * ((this.getUUID().hashCode() & 65535) / 65535.0F))
         : Vec3.ZERO;
      Vec3 aim = AirDefenseTargets.aim(target).add(bodyOffset);
      double distance = Math.max(0.0, -InterceptorGeometry.canonicalBounds(this.warhead()).minX);
      double previousGap = -this.position().distanceTo(aim);
      Vec3 previousAim = aim;
      int horizon = path.isEmpty() ? 160 : path.size() - 1;
      double futureSpeed = this.getDeltaMovement().length();

      for (int future = 1; future <= horizon; future++) {
         futureSpeed = this.poweredSpeed(futureSpeed, this.age + future - 1, maxSpeed);
         distance += futureSpeed;
         aim = path.isEmpty() ? AirDefenseTargets.aim(target).add(target.getDeltaMovement().scale(future)) : path.get(future).add(bodyOffset);
         Vec3 delta = aim.subtract(this.position());
         double angle = Math.acos(Mth.clamp(this.lastDirection.dot(delta.normalize()), -1.0, 1.0));
         double turn = FlightDynamics.turnLimit(
            futureSpeed,
            this.warhead() == 3 ? 350.0 : (this.warhead() == 0 ? 300.0 : 500.0),
            Math.toRadians(this.warhead() == 3 ? 16.0 : (this.warhead() == 0 ? 8.0 : 12.0))
         );
         double turningLoss = futureSpeed * angle / Math.max(0.001, turn) * 0.35;
         double gap = distance - turningLoss - delta.length();
         if (gap >= 0.0) {
            double fraction = Mth.clamp(-previousGap / (gap - previousGap), 0.0, 1.0);
            return previousAim.lerp(aim, fraction);
         }

         previousGap = gap;
         previousAim = aim;
      }

      double lead = Math.min(80.0, this.position().distanceTo(AirDefenseTargets.aim(target)) / Math.max(0.25, maxSpeed));
      return AirDefenseTargets.aim(target).add(bodyOffset).add(target.getDeltaMovement().scale(lead));
   }

   private static double contactTime(Vec3 offset, Vec3 step, double radius) {
      double c = offset.lengthSqr() - radius * radius;
      if (c <= 0.0) {
         return 0.0;
      } else {
         double a = step.lengthSqr();
         double b = 2.0 * offset.dot(step);
         double d = b * b - 4.0 * a * c;
         if (!(a < 1.0E-10) && !(d < 0.0)) {
            double t = (-b - Math.sqrt(d)) / (2.0 * a);
            return t >= 0.0 && t <= 1.0 ? t : -1.0;
         } else {
            return -1.0;
         }
      }
   }

   private void updateChunkTickets(ServerLevel server) {
      if (this.getY() > server.getMaxBuildHeight() + 32) {
         for (ChunkPos chunk : this.flightChunks) {
            server.getChunkSource().removeRegionTicket(FLIGHT_TICKET, chunk, 2, this.getUUID());
         }

         this.flightChunks.clear();
      } else {
         ChunkPos center = this.chunkPosition();
         HashSet<ChunkPos> next = new HashSet<>();
         Vec3 ahead = this.position().add(this.lastDirection.scale(this.warhead() == 0 ? 40.0 : 48.0));
         ChunkPos destination = new ChunkPos(BlockPos.containing(ahead));

         for (int x = Math.min(center.x, destination.x) - 1; x <= Math.max(center.x, destination.x) + 1; x++) {
            for (int z = Math.min(center.z, destination.z) - 1; z <= Math.max(center.z, destination.z) + 1; z++) {
               ChunkPos chunk = new ChunkPos(x, z);
               next.add(chunk);
               if (!this.flightChunks.contains(chunk)) {
                  server.getChunkSource().addRegionTicket(FLIGHT_TICKET, chunk, 2, this.getUUID());
                  CompletableFuture.<CompletableFuture>supplyAsync(() -> server.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true))
                     .thenCompose(future -> future);
               }
            }
         }

         for (ChunkPos chunk : this.flightChunks) {
            if (!next.contains(chunk)) {
               server.getChunkSource().removeRegionTicket(FLIGHT_TICKET, chunk, 2, this.getUUID());
            }
         }

         this.flightChunks.clear();
         this.flightChunks.addAll(next);
      }
   }

   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) && ActiveFlights.isAuthoritative(this)) {
         PatriotDefenseNetwork.flight(this, false);
      }

      if (this.level() instanceof ServerLevel server) {
         for (ChunkPos chunk : this.flightChunks) {
            server.getChunkSource().removeRegionTicket(FLIGHT_TICKET, chunk, 2, this.getUUID());
         }
      }

      if (!this.level().isClientSide && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
         ActiveFlights.removed(this);
      }

      this.flightChunks.clear();
      super.remove(reason);
   }

   private void burst(ServerLevel server, boolean hit) {
      if (ActiveFlights.resolve(this, hit ? "INTERCEPT" : "MISSED")) {
         if (!hit) {
            InterceptionEffects.emit(server, this.impactSurface == null ? this.position() : this.impactSurface, this.warhead());
         }

         this.discard();
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      return false;
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
      FlightMotion.save(this, tag);
      DeepStrikeData.stamp(tag);
      if (this.targetId != null) {
         tag.putUUID("Target", this.targetId);
      }

      if (this.sourceDefense != null) {
         tag.putUUID("DefenseSource", this.sourceDefense);
      }

      tag.putBoolean("ForcedManual", this.forcedManual);
      tag.putBoolean("PlannedHit", this.plannedHit);
      tag.putBoolean("DomeLaunch", this.domeLaunch);
      tag.putBoolean("CanisterLaunch", this.canisterLaunch);
      tag.putDouble("CanisterSetback", this.canisterSetback);
      tag.putDouble("MissOffsetX", this.missOffset.x);
      tag.putDouble("MissOffsetZ", this.missOffset.z);
      tag.putInt("Warhead", this.warhead());
      tag.putInt("Age", this.age);
      tag.putDouble("Travelled", this.distanceTravelled);
      tag.putDouble("LaunchDirectionX", this.launchDirection.x);
      tag.putDouble("LaunchDirectionY", this.launchDirection.y);
      tag.putDouble("LaunchDirectionZ", this.launchDirection.z);
      tag.putDouble("DirectionX", this.lastDirection.x);
      tag.putDouble("DirectionY", this.lastDirection.y);
      tag.putDouble("DirectionZ", this.lastDirection.z);
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      if (tag.hasUUID("Target")) {
         this.targetId = tag.getUUID("Target");
      }

      this.sourceDefense = tag.hasUUID("DefenseSource") ? tag.getUUID("DefenseSource") : null;
      this.forcedManual = !tag.contains("ForcedManual") || tag.getBoolean("ForcedManual");
      this.plannedHit = tag.getBoolean("PlannedHit");
      this.domeLaunch = tag.getBoolean("DomeLaunch");
      this.canisterLaunch = tag.getBoolean("CanisterLaunch");
      this.canisterSetback = Math.max(0.0, tag.getDouble("CanisterSetback"));
      this.entityData.set(WARHEAD, Math.max(0, Math.min(3, tag.getInt("Warhead"))));
      this.age = tag.getInt("Age");
      this.distanceTravelled = tag.getDouble("Travelled");
      Vec3 savedDirection = new Vec3(tag.getDouble("DirectionX"), tag.getDouble("DirectionY"), tag.getDouble("DirectionZ"));
      if (savedDirection.lengthSqr() > 1.0E-8) {
         this.lastDirection = savedDirection.normalize();
      }

      Vec3 savedLaunch = new Vec3(tag.getDouble("LaunchDirectionX"), tag.getDouble("LaunchDirectionY"), tag.getDouble("LaunchDirectionZ"));
      this.launchDirection = savedLaunch.lengthSqr() > 1.0E-8 ? savedLaunch.normalize() : this.lastDirection;
      if (tag.contains("MissOffsetX") && tag.contains("MissOffsetZ")) {
         this.missOffset = new Vec3(tag.getDouble("MissOffsetX"), 0.0, tag.getDouble("MissOffsetZ"));
      } else if (this.warhead() == 1 && !this.plannedHit) {
         Vec3 side = new Vec3(-this.lastDirection.z, 0.0, this.lastDirection.x);
         this.missOffset = (side.lengthSqr() > 1.0E-8 ? side.normalize() : new Vec3(1.0, 0.0, 0.0)).scale(6.0);
      }

      this.updateRotation();
      FlightMotion.restore(this, tag, 1584.0);
   }
}
