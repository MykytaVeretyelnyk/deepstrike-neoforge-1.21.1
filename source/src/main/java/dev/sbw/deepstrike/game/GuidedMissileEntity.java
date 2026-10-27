package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.client.ContrailRenderer;
import dev.sbw.deepstrike.client.RemoteFlights;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFParticles;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import dev.sbw.deepstrike.compat.ForgeChunkManager;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class GuidedMissileEntity extends Entity implements GeoEntity {
   private static final Map<ServerLevel, Map<UUID, GuidedMissileEntity.PeacekeeperClaim>> ACTIVE_PEACEKEEPERS = new WeakHashMap<>();
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   private static final EntityDataAccessor<Integer> FP5_BOOST_AGE = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Integer> CAMERA = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Boolean> GRAPHITE = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> INTERCEPTED = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> INTERCEPT_AGE = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> WARHEAD = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<String> CAMO = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Boolean> HAS_CAMERA = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> HAS_ECM = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> ENGINE_RUNNING = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> EXHAUST_VISIBLE = SynchedEntityData.defineId(GuidedMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private final List<RoutePoint> route = new ArrayList<>();
   private final Set<UUID> evaluatedThreats = new HashSet<>();
   private UUID ownerId;
   private int routeIndex;
   private boolean ballistic;
   private boolean terrainPlanned;
   private boolean forceFp5Flight;
   private double peacekeeperImpactEta = Double.NaN;
   private Vec3 previousChunk;
   private final Set<ChunkPos> flightChunks = new HashSet<>();
   private Vec3 flightOrigin;
   private Vec3 launchDirection = Vec3.ZERO;
   private int launchAge;
   public static final int STORM_SHADOW_BOOST_TICKS = 100;
   public static final int FP5_BOOSTER_TICKS = 90;
   public static final int FP5_BOOSTER_RELEASE_TICKS = 130;
   private boolean fp5BoosterReleased;
   private double traveled;
   private double launchDistance;
   private double flightSpeed = 0.045;
   private BlockPos launchMaster;
   private boolean sonicBoomFired;
   private boolean rangeUpgrade;
   private boolean boosterUpgrade;
   private double ballisticProgress;
   private PeacekeeperDynamics.Plan peacekeeperPlan;
   private double peacekeeperTerminalSeconds;
   private double spikeCruiseHeight;
   private boolean spikeClimbing;
   private boolean spikeDirect;
   private double commandedSpeed;
   private int throttleInput;
   private int throttleUntil;
   private UUID tvTarget;
   private UUID launchVehicle;
   private boolean aircraftReleased;
   private int separationTicks;
   private UUID missionId;
   private FlightTerrain terrain;
   private boolean originTicketAsserted;
   private Vec3 launchSource;
   private boolean detonationResolved;
   private Vec3 impactPosition;
   private boolean dudCoasting;
   private int dudCoastAge;
   private int launchFuel;
   private int launchMaxFuel;
   private CruisePropulsion.State cruisePropulsion;
   private long launchTick = -1L;
   private long lastFlightTick = Long.MIN_VALUE;
   private String frequency = "";
   private int interpolationSteps;
   private double targetX;
   private double targetY;
   private double targetZ;
   private float targetYaw;
   private float targetPitch;
   private int narrowPlumeStart = -1;
   private long forecastTick = Long.MIN_VALUE;
   private Vec3 forecastOrigin;
   private List<Vec3> forecast = List.of();
   private List<Vec3> originForecast = List.of();

   public boolean fp5BoosterAttached() {
      return this.kind() == MissileKind.FP5
         && Math.max(this.tickCount, (Integer)this.entityData.get(FP5_BOOST_AGE)) * 1.0 < 130.0
         && !this.fp5BoosterReleased;
   }

   public boolean fp5BoosterBurning() {
      return this.fp5BoosterAttached() && Math.max(this.tickCount, (Integer)this.entityData.get(FP5_BOOST_AGE)) * 1.0 < 90.0 && this.engineRunning();
   }

   public boolean stormShadowLaunchBurning() {
      return false;
   }

   public boolean exhaustSmokeReady() {
      return this.kind() != MissileKind.FP5 && this.kind() != MissileKind.STORM_SHADOW
         || Math.max(this.tickCount, (Integer)this.entityData.get(FP5_BOOST_AGE)) >= 10;
   }

   public boolean launchBoostBurning() {
      return this.fp5BoosterBurning() || this.stormShadowLaunchBurning();
   }

   public boolean visibleFlightBone(String bone) {
      return this.kind() != MissileKind.STORM_SHADOW || this.tickCount >= 12 || !bone.startsWith("wing_") && !bone.startsWith("fin_")
         ? !bone.equals("booster") || this.kind() != MissileKind.FP5 || this.fp5BoosterAttached()
         : false;
   }

   public void setPeacekeeperPlan(PeacekeeperDynamics.Plan plan) {
      this.peacekeeperPlan = plan;
      this.cachePeacekeeperImpactEta();
      this.setExhaustVisible(true);
   }

   private void cachePeacekeeperImpactEta() {
      PeacekeeperDynamics.Prediction prediction = this.peacekeeperPlan == null ? null : PeacekeeperDynamics.predict(this.peacekeeperPlan);
      this.peacekeeperImpactEta = prediction == null ? Double.POSITIVE_INFINITY : prediction.seconds();
   }

   public void releaseFromAircraft(Vec3 inheritedVelocity) {
      this.aircraftReleased = true;
      this.separationTicks = 12;
      this.launchDistance = 0.0;
      this.setDeltaMovement(inheritedVelocity.add(0.0, -0.035, 0.0));
      this.setEngineRunning(false);
   }

   public Vec3 impactPosition() {
      return this.impactPosition == null ? this.position() : this.impactPosition;
   }

   public boolean hasImpactPosition() {
      return this.impactPosition != null;
   }

   public void setPreparedTerrain(FlightTerrain value) {
      this.terrain = value;
   }

   public boolean ignoresLaunchBlock(BlockPos pos) {
      return this.launchMaster != null
         && this.launchDistance < MissileGeometry.clearance(this.kind())
         && this.level().getBlockEntity(this.launchMaster) instanceof LauncherBlockEntity
         && LauncherCollision.belongsTo(this.level(), pos, this.launchMaster);
   }

   public boolean tickedAt(long tick) {
      return this.lastFlightTick == tick;
   }

   public boolean shouldBeSaved() {
      return false;
   }

   public boolean isAlwaysTicking() {
      return true;
   }

   public String frequency() {
      return this.frequency;
   }

   public void setFrequency(String value) {
      if (RadioFrequency.valid(value)) {
         this.frequency = value;
      }
   }

   public void setMissionId(UUID id) {
      this.missionId = id;
   }

   public UUID missionId() {
      return this.missionId;
   }

   public boolean approachingImpact(Vec3 impact) {
      if (this.launchSource != null && this.launchAge >= 20 && !this.isIntercepted() && !this.detonationResolved) {
         String phase = this.flightPhase();
         if (!phase.equals("LAUNCHING") && !phase.equals("BOOST")) {
            Vec3 remaining = impact.subtract(this.position());
            double total = this.launchSource.distanceTo(impact);
            double window = Math.min(3600.0, total * 0.8);
            return remaining.lengthSqr() <= window * window && remaining.dot(this.getDeltaMovement()) > 0.0;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public String flightPhase() {
      if (this.isIntercepted()) {
         return "INTERCEPTED";
      } else if (this.detonationResolved) {
         return "FINISHED";
      } else if (this.kind() != MissileKind.PEACEKEEPER && this.cruisePropulsion != null && !this.cruisePropulsion.powered()) {
         return "COAST";
      } else if (this.launchDistance
         < (this.kind() == MissileKind.PEACEKEEPER ? MissileGeometry.clearance(this.kind()) : (this.kind() == MissileKind.JAGM ? 2.5 : 6.5))) {
         return "LAUNCHING";
      } else if (this.kind() == MissileKind.PEACEKEEPER) {
         if (this.peacekeeperPlan != null && this.launchAge * 1.0 <= this.peacekeeperPlan.boostTicks()) {
            return "BOOST";
         } else {
            return this.getDeltaMovement().y < 0.0 ? "TERMINAL" : "COAST";
         }
      } else if (this.tickCount < 60) {
         return "BOOST";
      } else {
         return this.routeIndex >= this.route.size() - 1 ? "TERMINAL" : "CRUISE";
      }
   }

   public boolean claimPeacekeeperFlight(ServerLevel server) {
      if (this.kind() != MissileKind.PEACEKEEPER) {
         return true;
      } else {
         if (this.missionId == null) {
            if (this.launchMaster != null && !this.route.isEmpty()) {
               RoutePoint target = this.route.get(0);
               String key = "peacekeeper:"
                  + server.dimension().location()
                  + ":"
                  + this.launchMaster.asLong()
                  + ":"
                  + target.x()
                  + ":"
                  + target.y()
                  + ":"
                  + target.z()
                  + ":"
                  + this.ownerId;
               this.missionId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            } else {
               this.missionId = this.getUUID();
            }
         }

         Map<UUID, GuidedMissileEntity.PeacekeeperClaim> flights = ACTIVE_PEACEKEEPERS.computeIfAbsent(server, ignored -> new HashMap<>());
         GuidedMissileEntity.PeacekeeperClaim existing = flights.get(this.missionId);
         if (existing != null && !existing.missileId().equals(this.getUUID())) {
            GuidedMissileEntity other = existing.entity().get();
            if (other != null && other != this && other.isAlive()) {
               return false;
            }
         }

         flights.put(this.missionId, new GuidedMissileEntity.PeacekeeperClaim(this.getUUID(), new WeakReference<>(this)));
         return true;
      }
   }

   public void releasePeacekeeperFlight(ServerLevel server) {
      if (this.missionId != null) {
         Map<UUID, GuidedMissileEntity.PeacekeeperClaim> flights = ACTIVE_PEACEKEEPERS.get(server);
         if (flights != null) {
            GuidedMissileEntity.PeacekeeperClaim claim = flights.get(this.missionId);
            if (claim != null
               && (claim.missileId().equals(this.getUUID()) || claim.entity().get() == this || claim.entity().get() == null || !claim.entity().get().isAlive())
               )
             {
               flights.remove(this.missionId);
            }
         }
      }
   }

   public void setLaunchVehicle(UUID vehicle) {
      this.launchVehicle = vehicle;
   }

   private boolean canFlightHit(Entity e) {
      if (e.getUUID().equals(this.getUUID())) {
         return false;
      } else if (this.kind() == MissileKind.STORM_SHADOW
         && e instanceof GuidedMissileEntity sibling
         && sibling.kind() == MissileKind.STORM_SHADOW
         && this.launchVehicle != null
         && this.launchVehicle.equals(sibling.launchVehicle)) {
         return false;
      } else if (e instanceof GuidedMissileEntity other
         && this.getPersistentData().hasUUID("DeepStrikeCommandNetwork")
         && other.getPersistentData().hasUUID("DeepStrikeCommandNetwork")
         && this.getPersistentData().getUUID("DeepStrikeCommandNetwork").equals(other.getPersistentData().getUUID("DeepStrikeCommandNetwork"))) {
         return false;
      } else if (e.isSpectator() || !e.isPickable()) {
         return false;
      } else if (e != this.getOwner() && (this.ownerId == null || !this.ownerId.equals(e.getUUID()))) {
         double clearance = MissileGeometry.clearance(this.kind());
         return !(this.launchDistance < clearance) && (!this.aircraftReleased || this.tickCount >= 40)
            || this.launchVehicle == null
            || !this.launchVehicle.equals(e.getUUID()) && (e.getVehicle() == null || !this.launchVehicle.equals(e.getVehicle().getUUID()));
      } else {
         return false;
      }
   }

   public void configureSpike(double height, double speed) {
      this.spikeCruiseHeight = Math.max(this.getY() + 6.0, height);
      this.spikeClimbing = true;
      this.commandedSpeed = Mth.clamp(speed, this.kind().minSpeed, this.kind().maxSpeed);
      this.setHasCamera(true);
   }

   public void tvThrottle(int input) {
      this.throttleInput = Math.max(-1, Math.min(1, input));
      this.throttleUntil = this.tickCount + 8;
   }

   public boolean retargetSpike(Vec3 target, UUID targetId) {
      if (this.kind() == MissileKind.SPIKE_NLOS
         && Double.isFinite(target.x)
         && Double.isFinite(target.y)
         && Double.isFinite(target.z)
         && this.flightOrigin != null
         && !(target.distanceTo(this.flightOrigin) > this.kind().maxRange)) {
         double speed = this.commandedSpeed > 0.0 ? this.commandedSpeed : this.kind().defaultSpeed;
         this.route.clear();
         this.route.add(new RoutePoint(target.x, target.y, target.z, speed));
         this.routeIndex = 0;
         this.tvTarget = targetId;
         this.spikeDirect = true;
         this.terrainPlanned = false;
         this.ballistic = false;
         return true;
      } else {
         return false;
      }
   }

   public double peacekeeperInterceptPenalty() {
      if (this.kind() != MissileKind.PEACEKEEPER) {
         return 0.0;
      } else {
         double kinematic = 0.1
            + 0.025 * Mth.clamp(FlightUnits.kilometresPerHour(this.getDeltaMovement().length()) / this.kind().maxSpeed, 0.0, 1.0)
            + 0.025 * Mth.clamp((this.getY() - 100.0) / 900.0, 0.0, 1.0);
         return Math.min(0.25, kinematic + (this.hasEcm() ? 0.05 : 0.0) + (this.boosterUpgrade ? 0.05 : 0.0));
      }
   }

   private double hitChance(double baseChance, int defense, boolean manpad) {
      if (this.kind() == MissileKind.PEACEKEEPER) {
         return manpad ? 0.01 : Math.max(0.01, baseChance - this.peacekeeperInterceptPenalty());
      } else {
         return Math.max(0.08, baseChance - (this.hasEcm() ? 0.2 : 0.0));
      }
   }

   public void setLaunchMaster(BlockPos pos) {
      this.launchMaster = pos.immutable();
   }

   // 1.21 re-sends these as the target when a move packet carries only rotation (or only position).
   @Override
   public double lerpTargetX() {
      return this.interpolationSteps > 0 ? this.targetX : this.getX();
   }

   @Override
   public double lerpTargetY() {
      return this.interpolationSteps > 0 ? this.targetY : this.getY();
   }

   @Override
   public double lerpTargetZ() {
      return this.interpolationSteps > 0 ? this.targetZ : this.getZ();
   }

   @Override
   public float lerpTargetYRot() {
      return this.tickCount > 1 ? this.targetYaw : this.getYRot();
   }

   @Override
   public float lerpTargetXRot() {
      return this.tickCount > 1 ? this.targetPitch : this.getXRot();
   }

   @Override
   public boolean shouldRenderAtSqrDistance(double distance) {
      return distance < RemoteFlights.NATIVE_RENDER_DISTANCE * RemoteFlights.NATIVE_RENDER_DISTANCE;
   }

   public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
      boolean teleport = false;
      this.targetX = x;
      this.targetY = y;
      this.targetZ = z;
      this.targetYaw = yaw;
      this.targetPitch = pitch;
      this.interpolationSteps = 2;
   }

   public void setRemotePose(Vec3 position, float yaw, float pitch) {
      this.interpolationSteps = 0;
      this.setPos(position);
      this.setYRot(yaw);
      this.setXRot(pitch);
      this.targetYaw = yaw;
      this.targetPitch = pitch;
   }


   public GuidedMissileEntity(EntityType<? extends GuidedMissileEntity> type, Level level) {
      super(type, level);
   }

   public GuidedMissileEntity(Level level, MissileKind kind) {
      this(level, kind, false, MissileData.defaultCamo(kind));
   }

   public GuidedMissileEntity(Level level, MissileKind kind, boolean graphite) {
      this(level, kind, graphite, MissileData.defaultCamo(kind));
   }

   public GuidedMissileEntity(Level level, MissileKind kind, boolean graphite, String camo) {
      this((EntityType<? extends GuidedMissileEntity>)BFEntities.GUIDED_MISSILE.get(), level);
      this.entityData.set(KIND, kind.ordinal());
      this.entityData.set(GRAPHITE, graphite);
      this.entityData.set(CAMO, camo != null ? camo : MissileData.defaultCamo(kind));
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      builder.define(FP5_BOOST_AGE, 0);
      builder.define(KIND, 0);
      builder.define(CAMERA, 0);
      builder.define(GRAPHITE, false);
      builder.define(INTERCEPTED, false);
      builder.define(INTERCEPT_AGE, 0);
      builder.define(WARHEAD, "he");
      builder.define(CAMO, "olive");
      builder.define(HAS_CAMERA, false);
      builder.define(HAS_ECM, false);
      builder.define(ENGINE_RUNNING, true);
      builder.define(EXHAUST_VISIBLE, true);
   }

   public boolean isIntercepted() {
      return (Boolean)this.entityData.get(INTERCEPTED);
   }

   public void setIntercepted(boolean val) {
      this.entityData.set(INTERCEPTED, val);
   }

   public int interceptAge() {
      return (Integer)this.entityData.get(INTERCEPT_AGE);
   }

   public List<RoutePoint> getRoute() {
      return Collections.unmodifiableList(this.route);
   }

   public int getRouteIndex() {
      return this.routeIndex;
   }

   public void intercept(int warheadType) {
      if (!(Boolean)this.entityData.get(INTERCEPTED) && !this.detonationResolved) {
         if (!(this.level() instanceof ServerLevel) || ActiveFlights.resolve(this, "INTERCEPTED")) {
            this.entityData.set(INTERCEPTED, true);
            this.setExhaustVisible(false);
            this.entityData.set(INTERCEPT_AGE, 0);
            this.route.clear();
            this.terrainPlanned = false;
            this.ballistic = false;
            Vec3 cur = this.getDeltaMovement();
            double forwardMult = warheadType == 1 ? 0.18 : 0.3;
            this.setDeltaMovement(new Vec3(cur.x * forwardMult, cur.y * 0.2 - 0.22, cur.z * forwardMult));
            if (this.level() instanceof ServerLevel server) {
               DeepStrikeNetwork.flight(this, true);
               InterceptionEffects.emit(server, this.position(), warheadType);
               DeepStrikeNetwork.intercepted(this);
            }
         }
      }
   }

   public static double calculateIglaHitChance(double altitude, double speedKmh) {
      double chance = 0.35;
      if (altitude < 80.0) {
         chance += 0.05;
      } else if (altitude > 150.0) {
         chance -= 0.05;
      }

      if (speedKmh < 900.0) {
         chance += 0.05;
      } else if (speedKmh > 1400.0) {
         chance -= 0.05;
      }

      return Math.max(0.25, Math.min(0.45, chance));
   }

   public MissileKind kind() {
      return MissileKind.byId((Integer)this.entityData.get(KIND));
   }

   public boolean graphite() {
      return (Boolean)this.entityData.get(GRAPHITE);
   }

   public String warhead() {
      return (String)this.entityData.get(WARHEAD);
   }

   public void setWarhead(String w) {
      this.entityData.set(WARHEAD, w != null ? w : "he");
   }

   public String camo() {
      return (String)this.entityData.get(CAMO);
   }

   public void setCamo(String c) {
      this.entityData.set(CAMO, c != null ? c : MissileData.defaultCamo(this.kind()));
   }

   public boolean hasCamera() {
      return (Boolean)this.entityData.get(HAS_CAMERA);
   }

   public void setHasCamera(boolean v) {
      this.entityData.set(HAS_CAMERA, v);
   }

   public boolean hasEcm() {
      return (Boolean)this.entityData.get(HAS_ECM);
   }

   public void setHasEcm(boolean v) {
      this.entityData.set(HAS_ECM, v);
   }

   public boolean propulsionBurning() {
      return (Boolean)this.entityData.get(ENGINE_RUNNING);
   }

   public boolean engineRunning() {
      return !this.isIntercepted() && this.propulsionBurning();
   }

   public boolean exhaustVisible() {
      return !this.isIntercepted() && (Boolean)this.entityData.get(EXHAUST_VISIBLE);
   }

   public void setExhaustVisible(boolean visible) {
      this.entityData.set(EXHAUST_VISIBLE, visible);
   }

   public float peacekeeperNarrowPlume(float partial) {
      if (this.kind() != MissileKind.PEACEKEEPER) {
         return 0.0F;
      } else {
         if (this.narrowPlumeStart < 0 && !this.exhaustVisible()) {
            this.narrowPlumeStart = this.tickCount;
         }

         float t = this.narrowPlumeStart < 0 ? 0.0F : Mth.clamp((this.tickCount + partial - this.narrowPlumeStart) / 30.0F, 0.0F, 1.0F);
         return t * t * (3.0F - 2.0F * t);
      }
   }

   public void setEngineRunning(boolean running) {
      this.entityData.set(ENGINE_RUNNING, running);
      if (this.kind() != MissileKind.PEACEKEEPER) {
         this.setExhaustVisible(running);
      }
   }

   private void setCruisePropulsion(CruisePropulsion.State state) {
      this.cruisePropulsion = state;
      this.setEngineRunning(state == null || state.powered());
   }

   public void applyMissileData(MissileData data) {
      if (data != null) {
         this.launchFuel = data.fuel();
         this.launchMaxFuel = data.maxFuel();
         if (this.kind() != MissileKind.PEACEKEEPER) {
            this.setCruisePropulsion(CruisePropulsion.create(this.kind(), data.fuel(), data.maxFuel(), data.calculateMaxRange()));
         }

         this.setWarhead(data.warhead());
         this.setCamo(data.camo());
         this.setHasCamera(data.hasCamera());
         this.setHasEcm(data.hasEcm());
         this.rangeUpgrade = data.hasRangeModule();
         this.boosterUpgrade = data.hasBooster();
         if ("graphite".equals(data.warhead())) {
            this.entityData.set(GRAPHITE, true);
         }
      }
   }

   public int cameraToken() {
      return (Integer)this.entityData.get(CAMERA);
   }

   public void setCameraToken(int token) {
      this.entityData.set(CAMERA, token);
   }

   public void setOwnerId(UUID id) {
      this.ownerId = id;
   }

   public UUID ownerId() {
      return this.ownerId;
   }

   public boolean cameraAvailable() {
      return (this.kind() == MissileKind.JAGM || this.kind() == MissileKind.SPIKE_NLOS || this.hasCamera()) && this.isAlive();
   }

   public boolean isPickable() {
      return this.isAlive();
   }

   public AABB getBoundingBoxForCulling() {
      return super.getBoundingBoxForCulling().inflate(4.0);
   }

   public void registerControllers(ControllerRegistrar controllers) {
      controllers.add(new AnimationController[]{new AnimationController(this, "flight", 0, state -> PlayState.STOP)});
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   public void setMission(List<RoutePoint> points, boolean useBallistic) {
      this.terrainPlanned = false;
      this.route.clear();
      points.stream()
         .limit(this.kind() != MissileKind.PEACEKEEPER && this.kind() != MissileKind.SPIKE_NLOS ? 32L : 1L)
         .map(RoutePoint::clamped)
         .forEach(this.route::add);
      this.ballistic = this.kind() == MissileKind.PEACEKEEPER || useBallistic && this.kind() != MissileKind.FP5;
      this.routeIndex = 0;
      this.flightOrigin = this.position();
      this.ballisticProgress = 0.0;
   }

   public void setPlannedRoute(List<RoutePoint> points) {
      if (!points.isEmpty()) {
         this.route.clear();
         this.route.addAll(points);
         this.routeIndex = 0;
         this.terrainPlanned = true;
      }
   }

   public void forceFp5Fallback() {
      if (this.kind() != MissileKind.FP5) {
         this.terrainPlanned = false;
         this.ballistic = false;
         this.forceFp5Flight = true;
      }
   }

   public void setLaunchVector(Vec3 direction, int ticks) {
      this.launchDirection = direction.normalize();
      this.launchAge = 0;
   }

   public void tick() {
      if (this.level() instanceof ServerLevel server) {
         if (this.lastFlightTick == server.getGameTime()) {
            return;
         }

         this.lastFlightTick = server.getGameTime();
         if (this.launchTick < 0L) {
            this.launchTick = server.getGameTime() - this.tickCount;
         }

         this.updateChunkTicket(server);
         if (this.terrain == null) {
            this.terrain = new FlightTerrain(
               server,
               this.kind() == MissileKind.PEACEKEEPER && this.flightOrigin != null ? this.flightOrigin : this.position(),
               this.route,
               this.kind(),
               this.peacekeeperPlan
            );
         }

         this.terrain.prioritizeFlight(this.position(), this.getDeltaMovement(), this.kind());
         if (this.terrainPlanned && this.kind() != MissileKind.PEACEKEEPER) {
            this.terrain
               .prioritizePlanned(
                  new RouteDynamics.State(this.cruiseState(), this.routeIndex, this.launchDistance), this.route, this.kind(), this.launchDirection
               );
         } else if (this.kind() != MissileKind.PEACEKEEPER && this.kind() != MissileKind.SPIKE_NLOS) {
            this.terrain
               .prioritizeCruise(
                  new CruiseRouteDynamics.State(
                     this.cruiseState(), this.routeIndex, this.launchDistance, this.flightOrigin == null ? this.position() : this.flightOrigin
                  ),
                  this.route,
                  this.kind(),
                  this.launchDirection,
                  this.ballistic,
                  this.forceFp5Flight
               );
         }

         this.terrain.releasePassedTerrain(this.position(), this.route.subList(Math.min(this.routeIndex, this.route.size()), this.route.size()), this.kind());
         if (this.kind() == MissileKind.PEACEKEEPER && this.peacekeeperPlan != null) {
            PeacekeeperDynamics.State state = new PeacekeeperDynamics.State(
               this.position(), this.launchAge == 0 ? Vec3.ZERO : this.getDeltaMovement().scale(20.0), this.launchAge * 0.05, this.peacekeeperTerminalSeconds
            );
            this.terrain.prioritizeBallistic(this.peacekeeperPlan, state);
            this.terrain.releasePassedBallistic(this.peacekeeperPlan, state);
         }

         this.terrain.ready();
      }

      if (this.launchSource == null) {
         this.launchSource = this.position();
      }

      if (this.flightOrigin == null) {
         this.flightOrigin = this.position();
      }

      super.tick();
      if (!this.level().isClientSide && (this.kind() == MissileKind.FP5 || this.kind() == MissileKind.STORM_SHADOW)) {
         this.entityData.set(FP5_BOOST_AGE, Math.min(130, this.tickCount));
      }

      if (!this.level().isClientSide && this.kind() == MissileKind.FP5 && this.tickCount * 1.0 >= 130.0 && !this.fp5BoosterReleased) {
         this.fp5BoosterReleased = true;
         Fp5BoosterEntity.release(this);
      }

      if (this.level().isClientSide) {
         if (this.interpolationSteps > 0) {
            this.setPos(
               this.getX() + (this.targetX - this.getX()) / this.interpolationSteps,
               this.getY() + (this.targetY - this.getY()) / this.interpolationSteps,
               this.getZ() + (this.targetZ - this.getZ()) / this.interpolationSteps
            );
            this.interpolationSteps--;
         }

         if (this.tickCount > 1) {
            this.setYRot(this.getYRot() + Mth.wrapDegrees(this.targetYaw - this.getYRot()) * 0.42F);
            this.setXRot(this.getXRot() + (this.targetPitch - this.getXRot()) * 0.42F);
         }

         if (RemoteFlights.ownsEffects(this)) {
            if (this.isIntercepted()) {
               this.emitInterceptTrail();
            } else if (this.isAlive()) {
               this.emitTrail();
            }
         }
      } else if (this.level() instanceof ServerLevel server) {
         try {
            if (this.dudCoasting) {
               this.setEngineRunning(false);
               Vec3 velocity = this.getDeltaMovement();
               Vec3 next = velocity.scale(this.kind() == MissileKind.PEACEKEEPER ? 0.999 : 0.997).add(0.0, -0.0245, 0.0);
               this.advanceFlight(next);
               if (this.isAlive() && (this.getY() < server.getMinBuildHeight() - 32 || ++this.dudCoastAge > 2400) && ActiveFlights.resolve(this, "DUD_LOST")) {
                  this.discard();
               }

               return;
            }

            if (this.kind() == MissileKind.PEACEKEEPER && !this.claimPeacekeeperFlight(server)) {
               this.discard();
               return;
            }

            if (this.isIntercepted()) {
               this.handleInterceptedFlight(server);
               return;
            }

            if (this.isIntercepted()) {
               this.handleInterceptedFlight(server);
               return;
            }

            if (this.route.isEmpty()) {
               if (this.tickCount > 200) {
                  this.discard();
               }

               return;
            }

            if (this.kind() != MissileKind.PEACEKEEPER
               && this.cruisePropulsion != null
               && !this.cruisePropulsion.powered()
               && (
                  this.getY() < server.getMinBuildHeight() - MissileGeometry.clearance(this.kind())
                     || this.cruisePropulsion.seconds() > this.cruisePropulsion.endurance() + 60.0
               )) {
               if (ActiveFlights.resolve(this, "FUEL_EXHAUSTED")) {
                  this.discard();
               }

               return;
            }

            if (!this.aircraftReleased || this.separationTicks <= 0) {
               if (this.kind() == MissileKind.PEACEKEEPER) {
                  this.followPeacekeeper();
                  return;
               }

               if (this.kind() != MissileKind.SPIKE_NLOS || this.terrainPlanned) {
                  if (!this.terrainPlanned) {
                     this.followCruiseRoute();
                     return;
                  }

                  this.followPlannedRoute();
                  return;
               }

               this.followSpike();
               return;
            }

            Vec3 motion = this.getDeltaMovement().add(0.0, -0.0245, 0.0);
            this.separationTicks--;
            if (this.advanceFlight(motion)) {
               if (this.separationTicks == 0) {
                  this.launchDistance = Math.max(this.launchDistance, 7.0);
                  this.flightOrigin = this.position();
                  this.setEngineRunning(true);
               }

               return;
            }
         } finally {
            if (this.isAlive()) {
               DeepStrikeNetwork.flight(this, true);
            }
         }
      }
   }

   public List<Vec3> interceptOriginForecast() {
      this.interceptForecast();
      return this.originForecast;
   }

   public List<Vec3> interceptForecast() {
      long now = this.level().getGameTime();
      if (this.forecastTick == now && this.position().equals(this.forecastOrigin)) {
         return this.forecast;
      } else {
         ArrayList<Vec3> points = new ArrayList<>(161);
         ArrayList<Vec3> origins = new ArrayList<>(161);
         points.add(AirDefenseTargets.aim(this));
         origins.add(this.position());
         CruisePropulsion.State fuel = this.cruisePropulsion != null
            ? this.cruisePropulsion
            : CruisePropulsion.legacy(
               this.kind(), this.launchFuel, this.launchMaxFuel, this.kind().maxRange * (this.rangeUpgrade ? 1.4 : 1.0), this.launchAge * 0.05
            );
         CruiseDynamics.State motion = new CruiseDynamics.State(this.position(), this.getDeltaMovement().scale(20.0), fuel);
         CruiseRouteDynamics.State cruise = new CruiseRouteDynamics.State(
            motion, this.routeIndex, this.launchDistance, this.flightOrigin == null ? this.position() : this.flightOrigin
         );
         RouteDynamics.State planned = new RouteDynamics.State(motion, this.routeIndex, this.launchDistance);
         SpikeDynamics.State spike = new SpikeDynamics.State(motion, this.launchDistance);
         PeacekeeperDynamics.State ballisticState = new PeacekeeperDynamics.State(
            this.position(), motion.velocity(), this.launchAge * 0.05, this.peacekeeperTerminalSeconds
         );
         boolean finished = this.route.isEmpty();
         double spikeSpeed = this.commandedSpeed > 0.0 ? this.commandedSpeed : (this.route.isEmpty() ? this.kind().minSpeed : this.route.get(0).speed());

         for (int ahead = 1; ahead <= 160 && !finished; ahead++) {
            for (int sub = 0; sub < 4 && !finished; sub++) {
               if (this.kind() == MissileKind.PEACEKEEPER) {
                  if (this.peacekeeperPlan == null) {
                     finished = true;
                     FlightOrientation.Angles orientation = FlightOrientation.along(motion.velocity(), this.getYRot(), this.getXRot());
                     points.add(MissileGeometry.worldCenter(this.kind(), motion.position(), orientation.yaw(), orientation.pitch()));
                     origins.add(motion.position());
                     break;
                  }

                  ballisticState = PeacekeeperDynamics.step(this.peacekeeperPlan, ballisticState);
                  motion = new CruiseDynamics.State(ballisticState.position(), ballisticState.velocity(), fuel);
                  finished = motion.position().y < this.peacekeeperPlan.target().y && motion.velocity().y < 0.0;
               } else if (this.kind() == MissileKind.SPIKE_NLOS && !this.terrainPlanned) {
                  RoutePoint end = this.route.get(0);
                  SpikeDynamics.Step step = SpikeDynamics.step(
                     spike, new Vec3(end.x(), end.y(), end.z()), this.launchDirection, spikeSpeed, this.spikeCruiseHeight, this.spikeDirect
                  );
                  spike = step.state();
                  motion = spike.motion();
                  finished = step.finished();
               } else if (this.terrainPlanned) {
                  RouteDynamics.Step step = RouteDynamics.step(planned, this.route, this.kind(), this.launchDirection);
                  planned = step.state();
                  motion = planned.motion();
                  finished = step.finished();
               } else {
                  CruiseRouteDynamics.Step step = CruiseRouteDynamics.step(
                     cruise, this.route, this.kind(), this.launchDirection, this.ballistic, this.forceFp5Flight
                  );
                  cruise = step.state();
                  motion = cruise.motion();
                  finished = step.finished();
               }
            }
            break;
         }

         this.forecastTick = now;
         this.forecastOrigin = this.position();
         this.forecast = List.copyOf(points);
         this.originForecast = List.copyOf(origins);
         return this.forecast;
      }
   }

   private CruiseDynamics.State cruiseState() {
      if (this.cruisePropulsion == null) {
         this.setCruisePropulsion(
            CruisePropulsion.legacy(
               this.kind(), this.launchFuel, this.launchMaxFuel, this.kind().maxRange * (this.rangeUpgrade ? 1.4 : 1.0), this.launchAge * 0.05
            )
         );
      }

      this.setEngineRunning(this.cruisePropulsion.powered() && this.separationTicks == 0);
      return new CruiseDynamics.State(this.position(), this.getDeltaMovement().scale(20.0), this.cruisePropulsion);
   }

   private void followCruiseRoute() {
      CruiseRouteDynamics.State state = new CruiseRouteDynamics.State(
         this.cruiseState(), this.routeIndex, this.launchDistance, this.flightOrigin == null ? this.position() : this.flightOrigin
      );

      for (int step = 0; step < 4 && this.routeIndex < this.route.size(); step++) {
         CruiseRouteDynamics.Step result = CruiseRouteDynamics.step(state, this.route, this.kind(), this.launchDirection, this.ballistic, this.forceFp5Flight);
         CruiseRouteDynamics.State next = result.state();
         if (!this.advanceFlight(next.motion().position().subtract(this.position()))) {
            return;
         }

         this.setCruisePropulsion(next.motion().propulsion());
         if (result.finished()) {
            this.setDeltaMovement(next.motion().velocity().scale(0.05));
            this.detonate();
            return;
         }

         if (next.cursor() != this.routeIndex) {
            this.routeIndex = next.cursor();
            this.flightOrigin = next.origin();
            this.traveled = 0.0;
         }

         state = next;
      }

      this.setDeltaMovement(state.motion().velocity().scale(0.05));
      this.flightSpeed = this.getDeltaMovement().length();
      this.launchAge++;
      if (this.routeIndex >= this.route.size()) {
         this.detonate();
      }
   }

   private void followPeacekeeper() {
      if (this.launchSource == null) {
         this.launchSource = this.position();
      }

      if (this.flightOrigin == null) {
         this.flightOrigin = this.position();
      }

      Vec3 target = new Vec3(this.route.get(0).x(), this.route.get(0).y(), this.route.get(0).z());
      if (this.peacekeeperPlan == null) {
         PeacekeeperDynamics.Selection selection = PeacekeeperDynamics.select(this.flightOrigin, target, this.route.get(0).speed());
         if (!selection.valid()) {
            if (ActiveFlights.resolve(this, "INVALID_BALLISTIC_PROFILE")) {
               this.discard();
            }

            return;
         }

         this.setPeacekeeperPlan(selection.plan());
      }

      Vec3 tickStart = this.position();
      boolean airTarget = this.level() instanceof ServerLevel server && FlightCollision.airTarget(server, target);
      Vec3 velocity = this.launchAge == 0 ? Vec3.ZERO : this.getDeltaMovement().scale(20.0);
      PeacekeeperDynamics.State state = new PeacekeeperDynamics.State(this.position(), velocity, this.launchAge * 0.05, this.peacekeeperTerminalSeconds);

      for (int i = 0; i < 4; i++) {
         PeacekeeperDynamics.State next = PeacekeeperDynamics.step(this.peacekeeperPlan, state);
         if (airTarget && next.velocity().y < 0.0) {
            AABB region = new AABB(target, target).inflate(1.0);
            Optional<Vec3> contact = region.clip(this.position(), next.position());
            if (region.contains(this.position()) || contact.isPresent()) {
               Vec3 end = contact.orElse(this.position());
               if (!this.advanceFlight(end.subtract(this.position()))) {
                  return;
               } else {
                  this.setDeltaMovement(next.velocity().scale(0.05));
                  this.detonate();
                  return;
               }
            }
         }

         if (!this.advanceFlight(next.position().subtract(this.position()))) {
            return;
         }

         this.peacekeeperTerminalSeconds = next.terminalSeconds();
         this.setEngineRunning(PeacekeeperDynamics.engineRunning(this.peacekeeperPlan, next));
         this.setExhaustVisible(this.peacekeeperImpactEta - next.seconds() > 2.25);
         state = next;
      }

      this.setDeltaMovement(state.velocity().scale(0.05));
      this.flightSpeed = this.getDeltaMovement().length();
      this.launchAge++;
      Vec3 actual = this.position().subtract(tickStart);
      if (actual.lengthSqr() > 1.0E-12) {
         this.applyFlightOrientation(state.velocity());
      }
   }

   private void followSpike() {
      if (this.tickCount > this.throttleUntil) {
         this.throttleInput = 0;
      }

      this.commandedSpeed = Mth.clamp(
         (this.commandedSpeed > 0.0 ? this.commandedSpeed : this.route.get(0).speed()) + this.throttleInput * 5.0, this.kind().minSpeed, this.kind().maxSpeed
      );
      if (this.tvTarget != null && this.level() instanceof ServerLevel server) {
         Entity tracked = server.getEntity(this.tvTarget);
         if (tracked != null && tracked.isAlive() && tracked.distanceToSqr(this.flightOrigin) < (double)this.kind().maxRange * this.kind().maxRange) {
            this.route.set(0, new RoutePoint(tracked.getX(), tracked.getY() + tracked.getBbHeight() * 0.45, tracked.getZ(), this.commandedSpeed));
         } else {
            this.tvTarget = null;
         }
      }

      RoutePoint p = this.route.get(0);
      Vec3 finalTarget = new Vec3(p.x(), p.y(), p.z());
      SpikeDynamics.State state = new SpikeDynamics.State(this.cruiseState(), this.launchDistance);
      if (this.terrain != null && !this.spikeDirect) {
         this.terrain.prioritizeSpike(state, finalTarget, this.launchDirection, this.commandedSpeed, this.spikeCruiseHeight);
      }

      for (int substep = 0; substep < 4; substep++) {
         SpikeDynamics.Step result = SpikeDynamics.step(state, finalTarget, this.launchDirection, this.commandedSpeed, this.spikeCruiseHeight, this.spikeDirect);
         SpikeDynamics.State next = result.state();
         this.spikeClimbing = result.climbing();
         if (!this.advanceFlight(next.motion().position().subtract(this.position()))) {
            return;
         }

         this.setCruisePropulsion(next.motion().propulsion());
         if (result.finished()) {
            this.setDeltaMovement(next.motion().velocity().scale(0.05));
            this.detonate();
            return;
         }

         state = next;
      }

      this.setDeltaMovement(state.motion().velocity().scale(0.05));
      this.flightSpeed = this.getDeltaMovement().length();
      this.launchAge++;
      if (this.traveled > this.kind().maxRange * 1.2) {
         this.detonate();
      }
   }

   private boolean advanceFlight(Vec3 movement) {
      Vec3 destination = this.position().add(movement);
      this.setDeltaMovement(movement);
      AABB body = MissileGeometry.substepBounds(
         this.kind(), this.position(), FlightOrientation.direction(this.getYRot(), this.getXRot()), movement, this.getYRot(), this.getXRot()
      );
      FlightCollision.SweepResult collision = FlightCollision.sweepSegment(
         (ServerLevel)this.level(), this, this.position(), destination, body, e -> !this.dudCoasting && this.canFlightHit(e)
      );
      HitResult hit = collision.hit();
      double clearance = MissileGeometry.clearance(this.kind());
      boolean own = this.launchDistance < clearance
         && (
            hit instanceof BlockHitResult bh && this.ignoresLaunchBlock(bh.getBlockPos())
               || this.launchVehicle != null && hit instanceof EntityHitResult eh && this.launchVehicle.equals(eh.getEntity().getUUID())
         );
      if (hit.getType() != Type.MISS && !own) {
         this.impactPosition = hit instanceof BlockHitResult ? collision.surface() : null;
         this.setPos(hit.getLocation());
         this.applyFlightOrientation(movement);
         this.detonate();
         return false;
      } else {
         this.setPos(destination);
         this.launchDistance = this.launchDistance + movement.length();
         this.traveled = this.traveled + movement.length();
         if (movement.lengthSqr() > 1.0E-10) {
            this.applyFlightOrientation(movement);
         }

         this.checkSonicBoom();
         return true;
      }
   }

   private void applyFlightOrientation(Vec3 velocity) {
      FlightOrientation.Angles angles = FlightOrientation.along(velocity, this.getYRot(), this.getXRot());
      this.setYRot(angles.yaw());
      this.setXRot(angles.pitch());
   }

   private void followPlannedRoute() {
      RouteDynamics.State state = new RouteDynamics.State(this.cruiseState(), this.routeIndex, this.launchDistance);

      for (int i = 0; i < 4; i++) {
         RouteDynamics.Step result = RouteDynamics.step(state, this.route, this.kind(), this.launchDirection);
         RouteDynamics.State next = result.state();
         if (!this.advanceFlight(next.motion().position().subtract(this.position()))) {
            return;
         }

         this.setCruisePropulsion(next.motion().propulsion());
         if (next.cursor() != this.routeIndex) {
            this.routeIndex = next.cursor();
            this.traveled = 0.0;
         }

         state = next;
         if (result.finished()) {
            this.setDeltaMovement(next.motion().velocity().scale(0.05));
            this.detonate();
            return;
         }
      }

      this.setDeltaMovement(state.motion().velocity().scale(0.05));
      this.flightSpeed = this.getDeltaMovement().length();
      this.launchAge++;
   }

   private void checkSonicBoom() {
      double speedKmh = FlightUnits.kilometresPerHour(Math.max(this.getDeltaMovement().length(), this.flightSpeed));
      double mach = speedKmh / 3.6 / Atmosphere.soundSpeed(this.getY());
      if (mach >= 1.0) {
         if (!this.sonicBoomFired) {
            this.sonicBoomFired = true;
            boolean doubleClap = this.level().random.nextBoolean();
            DeepStrikeNetwork.sonicBoom(this, doubleClap, (float)speedKmh);
         }
      } else if (mach < 0.92) {
         this.sonicBoomFired = false;
      }
   }

   private void emitTrail() {
      if (this.engineRunning()) {
         ContrailRenderer.record(this);
      }
   }

   private void emitInterceptTrail() {
      Vec3 p = this.position();
      ContrailRenderer.recordDamaged(this);
      this.level()
         .addAlwaysVisibleParticle(
            ParticleTypes.FLAME,
            true,
            p.x + (this.random.nextDouble() - 0.5) * 0.35,
            p.y + (this.random.nextDouble() - 0.5) * 0.35,
            p.z + (this.random.nextDouble() - 0.5) * 0.35,
            (this.random.nextDouble() - 0.5) * 0.02,
            0.02,
            (this.random.nextDouble() - 0.5) * 0.02
         );
      if (this.random.nextInt(3) == 0) {
         this.level().addAlwaysVisibleParticle(ParticleTypes.LAVA, true, p.x, p.y, p.z, 0.0, 0.0, 0.0);
      }
   }

   private void handleInterceptedFlight(ServerLevel server) {
      int age = (Integer)this.entityData.get(INTERCEPT_AGE) + 1;
      this.entityData.set(INTERCEPT_AGE, age);
      Vec3 mov = this.getDeltaMovement();
      mov = new Vec3(mov.x * 0.965, Math.max(-2.2, mov.y - 0.065), mov.z * 0.965);
      this.setDeltaMovement(mov);
      Vec3 start = this.position();
      Vec3 end = start.add(mov);
      BlockHitResult blockHit = FlightCollision.clip(server, start, end, this);
      boolean hitGround = blockHit.getType() != Type.MISS || end.y <= server.getMinBuildHeight() || age >= 100;
      if (hitGround) {
         Vec3 impact = blockHit.getType() != Type.MISS ? blockHit.getLocation() : end;
         this.setPos(impact);
         this.applyGroundDebrisEffects(server, impact);
         this.discard();
      } else {
         this.setPos(end);
         this.setXRot(Math.min(85.0F, this.getXRot() + 2.8F));
         this.setYRot(this.getYRot() + 22.0F);
      }
   }

   private void applyGroundDebrisEffects(ServerLevel server, Vec3 impact) {
      BlockPos center = server.getHeightmapPos(Types.MOTION_BLOCKING, BlockPos.containing(impact));
      if (server.random.nextDouble() < 0.3) {
         int breakCount = 1 + server.random.nextInt(2);
         int broken = 0;

         for (int attempts = 0; attempts < 16 && broken < breakCount; attempts++) {
            int ox = server.random.nextInt(3) - 1;
            int oz = server.random.nextInt(3) - 1;
            BlockPos target = server.getHeightmapPos(Types.MOTION_BLOCKING, center.offset(ox, 0, oz)).below();
            BlockState state = server.getBlockState(target);
            if (!state.isAir() && state.getDestroySpeed(server, target) >= 0.0F && state.getDestroySpeed(server, target) < 10.0F) {
               server.destroyBlock(target, true);
               broken++;
            }
         }
      }

      server.playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.2F, 0.75F);
      server.playSound(null, impact.x, impact.y, impact.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.6F, 1.6F);
      server.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 2, 0.4, 0.4, 0.4, 0.05);
      server.sendParticles(
         new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()),
         impact.x,
         impact.y,
         impact.z,
         30,
         0.6,
         0.4,
         0.6,
         0.5
      );
      server.sendParticles((SimpleParticleType)BFParticles.BLAST_SMOKE.get(), impact.x, impact.y, impact.z, 16, 0.6, 0.4, 0.6, 0.06);
   }

   private void updateChunkTicket(ServerLevel server) {
      if (!this.originTicketAsserted) {
         this.originTicketAsserted = true;
         ChunkPos origin = this.previousChunk == null ? this.chunkPosition() : new ChunkPos((int)this.previousChunk.x, (int)this.previousChunk.z);
         this.previousChunk = new Vec3(origin.x, 0.0, origin.z);
         this.flightChunks.add(origin);
         ForgeChunkManager.forceChunk(server, "deepstrike", this, origin.x, origin.z, true, true);
      }
   }

   private void detonate() {
      if (!this.detonationResolved && !this.isIntercepted()) {
         if (this.level() instanceof ServerLevel server) {
            double failure = switch (this.kind()) {
               case FP5 -> 0.1;
               case STORM_SHADOW, SPIKE_NLOS -> 0.05;
               case JAGM -> 0.02;
               case PEACEKEEPER -> 0.1 + this.random.nextDouble() * 0.05;
            };
            boolean dud = this.dudCoasting || !"none".equals(this.warhead()) && this.random.nextDouble() < failure;
            if (dud && !this.hasImpactPosition()) {
               this.dudCoasting = true;
               this.setEngineRunning(false);
               this.setExhaustVisible(false);
               return;
            }

            if (!ActiveFlights.resolve(this, dud ? "DUD" : "IMPACT")) {
               return;
            }

            this.detonationResolved = true;
            if (dud) {
               DudMissileEntity.plant(this, this.launchSource != null ? this.launchSource : (this.flightOrigin != null ? this.flightOrigin : this.position()));
            } else {
               MissileExplosion.detonate(server, this, this.warhead());
            }

            this.discard();
         }
      }
   }

   private Entity getOwner() {
      return this.ownerId != null && this.level() instanceof ServerLevel s ? s.getEntity(this.ownerId) : null;
   }

   public boolean hurt(DamageSource source, float amount) {
      if (!this.level().isClientSide && !this.isIntercepted()) {
         Entity direct = source.getDirectEntity();
         Entity attacker = source.getEntity();
         int defense = InterceptionEffects.defenseOf(direct, attacker);
         if (defense >= 0 && this.level() instanceof ServerLevel server) {
            double speedKmh = FlightUnits.kilometresPerHour(this.getDeltaMovement().length());
            double chance = calculateIglaHitChance(this.getY(), speedKmh);
            if (defense == 2) {
               chance = Math.min(0.65, chance + 0.08);
            }

            if (direct != null && !this.evaluatedThreats.add(direct.getUUID())) {
               return false;
            } else {
               chance = this.hitChance(chance, defense, InterceptionEffects.isManpad(direct));
               if (server.random.nextDouble() < chance) {
                  if (direct != null) {
                     direct.discard();
                  }

                  this.intercept(defense);
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      this.route.clear();
      this.dudCoasting = tag.getBoolean("DudCoasting");
      this.dudCoastAge = tag.getInt("DudCoastAge");
      this.cruisePropulsion = tag.contains("CruisePropulsion") ? CruisePropulsion.load(tag.getCompound("CruisePropulsion")) : null;
      this.fp5BoosterReleased = tag.getBoolean("Fp5BoosterReleased");
      this.launchFuel = tag.getInt("LaunchFuel");
      this.launchMaxFuel = tag.getInt("LaunchMaxFuel");
      this.launchTick = tag.contains("LaunchTick") ? tag.getLong("LaunchTick") : -1L;
      this.detonationResolved = tag.getBoolean("DetonationResolved");
      this.impactPosition = tag.contains("ImpactX") ? new Vec3(tag.getDouble("ImpactX"), tag.getDouble("ImpactY"), tag.getDouble("ImpactZ")) : null;
      if (this.impactPosition != null && !Double.isFinite(this.impactPosition.lengthSqr())) {
         this.impactPosition = null;
      }

      this.entityData.set(KIND, tag.getInt("Kind"));
      this.entityData.set(GRAPHITE, tag.getBoolean("Graphite"));
      if (tag.contains("Warhead")) {
         this.setWarhead(tag.getString("Warhead"));
      }

      if (tag.contains("Camo")) {
         this.setCamo(tag.getString("Camo"));
      }

      this.setHasCamera(tag.getBoolean("HasCamera"));
      this.setHasEcm(tag.getBoolean("HasEcm"));
      this.rangeUpgrade = tag.getBoolean("RangeUpgrade");
      this.boosterUpgrade = tag.getBoolean("BoosterUpgrade");
      this.ballisticProgress = tag.getDouble("BallisticProgress");
      this.spikeCruiseHeight = tag.getDouble("SpikeCruiseHeight");
      this.peacekeeperPlan = tag.contains("PeacekeeperPlan") ? PeacekeeperDynamics.load(tag.getCompound("PeacekeeperPlan")) : null;
      if (this.peacekeeperPlan != null) {
         this.cachePeacekeeperImpactEta();
      }

      this.spikeDirect = tag.getBoolean("SpikeDirect");
      this.spikeClimbing = tag.getBoolean("SpikeClimbing");
      this.commandedSpeed = tag.getDouble("CommandedSpeed");
      this.tvTarget = tag.hasUUID("TvTarget") ? tag.getUUID("TvTarget") : null;
      if (tag.hasUUID("Owner")) {
         this.ownerId = tag.getUUID("Owner");
      }

      if (tag.hasUUID("LaunchVehicle")) {
         this.launchVehicle = tag.getUUID("LaunchVehicle");
      }

      this.aircraftReleased = tag.getBoolean("AircraftReleased");
      this.separationTicks = Math.max(0, Math.min(12, tag.getInt("AircraftSeparationTicks")));
      if (tag.hasUUID("MissionId")) {
         this.missionId = tag.getUUID("MissionId");
      }

      if (tag.contains("OriginTicket")) {
         ChunkPos chunk = new ChunkPos(tag.getLong("OriginTicket"));
         this.previousChunk = new Vec3(chunk.x, 0.0, chunk.z);
         this.flightChunks.add(chunk);
      }

      if (tag.contains("FlightAge")) {
         this.tickCount = tag.getInt("FlightAge");
      }

      this.entityData.set(INTERCEPTED, tag.getBoolean("Intercepted"));
      this.entityData.set(INTERCEPT_AGE, tag.getInt("InterceptAge"));
      this.setFrequency(tag.getString("RadioFrequency"));
      if (tag.contains("SourceX")) {
         this.launchSource = new Vec3(tag.getDouble("SourceX"), tag.getDouble("SourceY"), tag.getDouble("SourceZ"));
      }

      this.flightSpeed = tag.contains("FlightSpeed") ? tag.getDouble("FlightSpeed") : 0.045;
      this.launchDistance = tag.getDouble("LaunchDistance");
      if (tag.contains("LaunchMaster")) {
         this.launchMaster = BlockPos.of(tag.getLong("LaunchMaster"));
      }

      this.forceFp5Flight = tag.getBoolean("ForceFp5Flight");
      this.ballistic = tag.getBoolean("Ballistic");
      this.routeIndex = tag.getInt("RouteIndex");
      this.traveled = tag.getDouble("Traveled");
      this.launchAge = tag.getInt("LaunchAge");
      this.terrainPlanned = tag.getBoolean("TerrainPlanned");
      this.sonicBoomFired = tag.getBoolean("SonicBoomFired");
      if (tag.contains("LaunchX")) {
         this.launchDirection = new Vec3(tag.getDouble("LaunchX"), tag.getDouble("LaunchY"), tag.getDouble("LaunchZ"));
      }

      for (CompoundTag p : tag.getList("Route", 10).stream().map(t -> (CompoundTag)t).toList()) {
         this.route.add(new RoutePoint(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"), p.getDouble("Speed")));
      }

      if (tag.contains("Origin")) {
         this.flightOrigin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
      }

      FlightMotion.restore(this, tag, this.kind().maxSpeed);
      if (this.kind() != MissileKind.PEACEKEEPER) {
         this.cruiseState();
      } else {
         double reserve = tag.contains("PeacekeeperTerminalSeconds")
            ? tag.getDouble("PeacekeeperTerminalSeconds")
            : (this.peacekeeperPlan == null ? 0.0 : PeacekeeperDynamics.terminalSecondsAt(this.peacekeeperPlan, this.launchAge * 0.05));
         this.peacekeeperTerminalSeconds = Double.isFinite(reserve) ? Math.max(0.0, Math.min(30.0, reserve)) : 0.0;
         this.setEngineRunning(
            this.peacekeeperPlan == null
               || PeacekeeperDynamics.engineRunning(
                  this.peacekeeperPlan,
                  new PeacekeeperDynamics.State(this.position(), this.getDeltaMovement().scale(20.0), this.launchAge * 0.05, this.peacekeeperTerminalSeconds)
               )
         );
      }
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putBoolean("DudCoasting", this.dudCoasting);
      tag.putInt("DudCoastAge", this.dudCoastAge);
      DeepStrikeData.stamp(tag);
      FlightMotion.save(this, tag);
      if (this.kind() != MissileKind.PEACEKEEPER) {
         this.cruiseState();
         tag.put("CruisePropulsion", CruisePropulsion.save(this.cruisePropulsion));
      }

      tag.putBoolean("Fp5BoosterReleased", this.fp5BoosterReleased);
      tag.putInt("LaunchFuel", this.launchFuel);
      tag.putInt("LaunchMaxFuel", this.launchMaxFuel);
      tag.putLong("LaunchTick", this.launchTick);
      tag.putBoolean("DetonationResolved", this.detonationResolved);
      if (this.impactPosition != null) {
         tag.putDouble("ImpactX", this.impactPosition.x);
         tag.putDouble("ImpactY", this.impactPosition.y);
         tag.putDouble("ImpactZ", this.impactPosition.z);
      }

      tag.putString("FlightPhase", this.flightPhase());
      tag.putDouble("FlightSpeed", this.flightSpeed);
      tag.putDouble("LaunchDistance", this.launchDistance);
      if (this.launchMaster != null) {
         tag.putLong("LaunchMaster", this.launchMaster.asLong());
      }

      tag.putInt("Kind", this.kind().ordinal());
      tag.putBoolean("Graphite", this.graphite());
      tag.putString("Warhead", this.warhead());
      tag.putString("Camo", this.camo());
      tag.putBoolean("HasCamera", this.hasCamera());
      tag.putBoolean("HasEcm", this.hasEcm());
      tag.putBoolean("RangeUpgrade", this.rangeUpgrade);
      tag.putBoolean("BoosterUpgrade", this.boosterUpgrade);
      tag.putDouble("BallisticProgress", this.ballisticProgress);
      tag.putDouble("SpikeCruiseHeight", this.spikeCruiseHeight);
      if (this.peacekeeperPlan != null) {
         tag.put("PeacekeeperPlan", PeacekeeperDynamics.save(this.peacekeeperPlan));
         tag.putDouble("PeacekeeperTerminalSeconds", this.peacekeeperTerminalSeconds);
      }

      tag.putBoolean("SpikeDirect", this.spikeDirect);
      tag.putBoolean("SpikeClimbing", this.spikeClimbing);
      tag.putDouble("CommandedSpeed", this.commandedSpeed);
      if (this.tvTarget != null) {
         tag.putUUID("TvTarget", this.tvTarget);
      }

      if (this.ownerId != null) {
         tag.putUUID("Owner", this.ownerId);
      }

      if (this.launchVehicle != null) {
         tag.putUUID("LaunchVehicle", this.launchVehicle);
      }

      tag.putBoolean("AircraftReleased", this.aircraftReleased);
      tag.putInt("AircraftSeparationTicks", this.separationTicks);
      if (this.missionId != null) {
         tag.putUUID("MissionId", this.missionId);
      }

      if (this.previousChunk != null) {
         tag.putLong("OriginTicket", ChunkPos.asLong((int)this.previousChunk.x, (int)this.previousChunk.z));
      }

      tag.putInt("FlightAge", this.tickCount);
      tag.putBoolean("Intercepted", this.isIntercepted());
      tag.putInt("InterceptAge", this.interceptAge());
      tag.putString("RadioFrequency", this.frequency);
      if (this.launchSource != null) {
         tag.putDouble("SourceX", this.launchSource.x);
         tag.putDouble("SourceY", this.launchSource.y);
         tag.putDouble("SourceZ", this.launchSource.z);
      }

      tag.putBoolean("Ballistic", this.ballistic);
      tag.putInt("RouteIndex", this.routeIndex);
      tag.putDouble("Traveled", this.traveled);
      tag.putInt("LaunchAge", this.launchAge);
      tag.putBoolean("TerrainPlanned", this.terrainPlanned);
      tag.putBoolean("ForceFp5Flight", this.forceFp5Flight);
      tag.putBoolean("SonicBoomFired", this.sonicBoomFired);
      tag.putDouble("LaunchX", this.launchDirection.x);
      tag.putDouble("LaunchY", this.launchDirection.y);
      tag.putDouble("LaunchZ", this.launchDirection.z);
      ListTag list = new ListTag();
      this.route.forEach(p -> {
         CompoundTag t = new CompoundTag();
         t.putDouble("X", p.x());
         t.putDouble("Y", p.y());
         t.putDouble("Z", p.z());
         t.putDouble("Speed", p.speed());
         list.add(t);
      });
      tag.put("Route", list);
      if (this.flightOrigin != null) {
         tag.putBoolean("Origin", true);
         tag.putDouble("OriginX", this.flightOrigin.x);
         tag.putDouble("OriginY", this.flightOrigin.y);
         tag.putDouble("OriginZ", this.flightOrigin.z);
      }
   }

   public void remove(RemovalReason reason) {
      if (!this.level().isClientSide && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) && ActiveFlights.isAuthoritative(this)) {
         DeepStrikeNetwork.flight(this, false);
      }

      if (this.terrain != null) {
         this.terrain.close();
         this.terrain = null;
      }

      if (!this.level().isClientSide && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)) {
         ActiveFlights.removed(this);
      }

      if (this.level() instanceof ServerLevel s) {
         this.releasePeacekeeperFlight(s);

         for (ChunkPos chunk : this.flightChunks) {
            ForgeChunkManager.forceChunk(s, "deepstrike", this, chunk.x, chunk.z, false, true);
         }
      }

      this.flightChunks.clear();
      super.remove(reason);
   }

   private record PeacekeeperClaim(UUID missileId, WeakReference<GuidedMissileEntity> entity) {
   }
}
