package dev.sbw.deepstrike.validation;

import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.init.ModEntities;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.FlightCollision;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.ManpadFlight;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.MissileModelContact;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.game.PeacekeeperDynamics;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.LevelTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AirDefenseReview {
   private static final Logger LOG = LoggerFactory.getLogger(AirDefenseReview.class);
   private static final Vec3 FIXTURE = new Vec3(0.0, 140.0, 140.0);
   private static final IronDomeEntity[] batteries = new IronDomeEntity[3];
   private static GuidedMissileEntity target;
   private static ServerLevel world;
   private static long started;
   private static long seenAt = -1L;
   private static long removedAt = -1L;
   private static long phaseAt;
   private static int expectedWave = 1;
   private static Set<UUID> previousSources = new HashSet<>();
   private static Set<UUID> observedSources = new HashSet<>();
   private static int ammoAfterHit;
   private static boolean clearExitVerified;
   private static MissileProjectile pursuit;
   private static int pursuitType;
   private static int stabilityTrial;
   private static long nativeContactAt = -1L;
   private static PatriotInterceptorEntity pacPursuit;
   private static int pacType;
   private static long pacContactAt = -1L;
   public static volatile int contactCapture;
   public static volatile int contactWarhead;
   public static volatile int nativeScene;
   public static volatile int nativeExplosionType;
   public static volatile int nativeExplosionVisuals;
   private static final List<Entity> additional = new ArrayList<>();
   private static final Map<UUID, Vec3> launchAxes = new HashMap<>();
   public static volatile List<Integer> additionalIds = List.of();
   public static volatile int phase = -1;
   public static volatile int age;
   public static volatile boolean finished;
   public static volatile String failure;
   public static volatile Vec3 focus = FIXTURE;
   public static volatile int liveInterceptorId = -1;

   private static boolean stabilityOnly() {
      return System.getProperty("deepstrike.reviewCase", "").startsWith("manpad-stability");
   }

   public static void start(ServerLevel level) {
      world = level;
      started = level.getGameTime();
      phaseAt = started;
      if (stabilityOnly()) {
         verifyAimPoints(level);
         pursuitType = 0;
         startPursuit(level);
         phase = 5;
         age = 0;
         LOG.info("MANPAD_STABILITY_START trials=16 targetKinds=SPIKE,FP5,STORM_SHADOW,JAGM");
      } else if (glowOnly()) {
         additional.clear();
         spawnAllTypes(level);
         phase = 4;
         age = 0;

         for (MissileProjectile missile : new MissileProjectile[]{
            new Fim92MissileEntity((EntityType)ModEntities.FIM_92_MISSILE.get(), level),
            new IglaMissileEntity((EntityType)ModEntities.IGLA_MISSILE.get(), level)
         }) {
            missile.setPos(0.0, 1000.0, -5000.0);
            missile.setDeltaMovement(0.0, 1.0, 0.0);
            missile.getPersistentData().putInt("DSMManpadAge", 499);
            ManpadFlight.prepare(missile, null);
            check(missile.isAlive(), "MANPADS expired before 25 seconds");
            missile.getPersistentData().putInt("DSMManpadAge", 500);
            ManpadFlight.prepare(missile, null);
            check(!missile.isAlive(), "MANPADS survived 25 seconds without a target");
            LOG.info("AIR_DEFENSE_LIFETIME_BOUNDARY_PASS type={} aliveAt499=true airburstAt500=true", missile.getClass().getSimpleName());
         }

         LOG.info("AIR_DEFENSE_GLOW_ONLY_START realFlights=PAC2,PAC3,FIM92,IGLA");
      } else {
         for (int i = 0; i < 3; i++) {
            IronDomeEntity battery = (IronDomeEntity)((EntityType)BFEntities.IRON_DOME.get()).create(level);
            check(battery != null, "Cannot create Iron Dome");
            battery.setAutoDefense(false);
            battery.setPos(i == 0 ? 0.0 : (i == 1 ? 6.0 : -6.0), 101.0, 10.0);
            battery.setYRot(0.0F);
            battery.setReadyTamir(20);
            battery.setDefendedZone(600.0, 140.0, 1500.0);
            check(level.addFreshEntity(battery), "Cannot add Iron Dome");
            batteries[i] = battery;
         }

         target = makeTarget(level, FIXTURE);

         try {
            verifyApproachRules(level);
            verifyNativeContacts(level);
         } catch (Throwable var5) {
            failure = var5.toString();
            LOG.error("AIR_DEFENSE_REVIEW_FAILED", var5);
            return;
         }

         for (IronDomeEntity battery : batteries) {
            battery.setAutoDefense(true);
         }

         phase = 0;
         LOG.info("AIR_DEFENSE_REVIEW_START batteries=3 target={}", target.getUUID());
      }
   }

   private static boolean pacOnly() {
      return System.getProperty("deepstrike.reviewCase", "").startsWith("pvo-pac-realism");
   }

   private static boolean glowOnly() {
      return System.getProperty("deepstrike.reviewCase", "").startsWith("pvo-glow-only");
   }

   private static void spawnAllTypes(ServerLevel level) {
      target = makeTarget(level, new Vec3(300.0, 145.0, 400.0));

      for (int warhead = 0; warhead <= 1; warhead++) {
         PatriotInterceptorEntity flight = new PatriotInterceptorEntity(
            level, new Vec3(0.0, 140 + warhead * 5, 120.0), new Vec3(0.0, 0.0, 1.0), target, warhead, false
         );
         check(level.addFreshEntity(flight), "Cannot add PAC fixture");
         additional.add(flight);
      }

      MissileProjectile[] nativeMissiles = new MissileProjectile[]{
         new Fim92MissileEntity((EntityType)ModEntities.FIM_92_MISSILE.get(), level), new IglaMissileEntity((EntityType)ModEntities.IGLA_MISSILE.get(), level)
      };

      for (int i = 0; i < nativeMissiles.length; i++) {
         MissileProjectile flight = nativeMissiles[i];
         flight.setPos(0.0, 150 + i * 5, 120.0);
         flight.setDeltaMovement(new Vec3(0.0, 0.0, 2.0));
         flight.setTargetUuid(target.getUUID().toString());
         flight.setLost(false);
         flight.setLostTarget(false);
         check(level.addFreshEntity(flight), "Cannot add native MANPADS fixture");
         additional.add(flight);
      }

      additionalIds = additional.stream().<Integer>map(Entity::getId).toList();
   }

   private static void verifyApproachRules(ServerLevel level) throws ReflectiveOperationException {
      Method eligible = IronDomeEntity.class.getDeclaredMethod("canAutoEngage", ServerLevel.class, Entity.class);
      eligible.setAccessible(true);
      Field ageField = GuidedMissileEntity.class.getDeclaredField("launchAge");
      ageField.setAccessible(true);
      IronDomeEntity battery = batteries[0];
      battery.setAutoDefense(true);
      Vec3 origin = new Vec3(-3000.0, 140.0, 140.0);
      Vec3 impact = new Vec3(1000.0, 140.0, 140.0);

      for (MissileKind kind : MissileKind.values()) {
         GuidedMissileEntity missile = new GuidedMissileEntity(level, kind);
         missile.setPos(origin);
         missile.setMission(List.of(new RoutePoint(impact.x, impact.y, impact.z, kind.defaultSpeed)), kind == MissileKind.PEACEKEEPER);
         missile.setDeltaMovement(new Vec3(0.5, 0.0, 0.0));
         Vec3 first = MissileModelContact.aimOffset(missile, 0.25F);
         Vec3 last = MissileModelContact.aimOffset(missile, 0.75F);
         check(first.distanceTo(last) > 0.01, kind + " has no aim-point spread");

         for (float fraction : new float[]{0.25F, 0.5F, 0.75F}) {
            Vec3 inside = missile.position().add(MissileModelContact.aimOffset(missile, fraction));
            check(MissileModelContact.fraction(missile, inside, inside, Vec3.ZERO) == 0.0, kind + " aim is outside solid geometry");
         }

         LOG.info("AIR_DEFENSE_BODY_AIM_SPREAD_VERIFIED kind={} spread={}", kind, first.distanceTo(last));
         check(!(Boolean)eligible.invoke(battery, level, missile), kind + " eligible at launch");
         terminalFixture(missile);
         missile.setPos(0.0, 140.0, 140.0);
         if (kind == MissileKind.PEACEKEEPER) {
            PeacekeeperDynamics.Selection plan = PeacekeeperDynamics.select(origin, impact, 5500.0);
            check(plan.valid(), "Invalid approach fixture plan");
            missile.setPeacekeeperPlan(plan.plan());
         } else {
            missile.tickCount = 20;
         }

         check(!(Boolean)eligible.invoke(battery, level, missile), kind + " eligible during boost");
         ageField.setInt(missile, 10000);
         missile.tickCount = 10000;
         missile.setPos(-2500.0, 140.0, 140.0);
         check(!(Boolean)eligible.invoke(battery, level, missile), kind + " eligible far from destination");
         missile.setPos(-2000.0, 140.0, 140.0);
         check((Boolean)eligible.invoke(battery, level, missile), kind + " not acquired on early inbound approach");
         missile.setPos(0.0, 140.0, 140.0);
         check((Boolean)eligible.invoke(battery, level, missile), kind + " not eligible while closing near destination");
         missile.setDeltaMovement(new Vec3(-0.5, 0.0, 0.0));
         check(!(Boolean)eligible.invoke(battery, level, missile), kind + " eligible while receding");
         LOG.info("AIR_DEFENSE_APPROACH_KIND_VERIFIED kind={} launch=false boost=false distant=false closing=true receding=false", kind);
      }

      battery.setAutoDefense(false);
   }

   private static void verifyNativeContacts(ServerLevel level) {
      MissileProjectile[] nativeMissiles = new MissileProjectile[]{
         new Fim92MissileEntity((EntityType)ModEntities.FIM_92_MISSILE.get(), level), new IglaMissileEntity((EntityType)ModEntities.IGLA_MISSILE.get(), level)
      };
      ModelGeometry geometry = ModelGeometry.get(ModelAssets.missile(target.kind()));
      ModelGeometry.Bone bone = geometry.bones.stream().filter(value -> !value.cubes().isEmpty()).findFirst().orElseThrow();
      ModelGeometry.Cube cube = bone.cubes().get(0);
      Vector3f local = new Vector3f(cube.min()).add(new Vector3f(cube.size()).mul(0.5F));
      Matrix4f matrix = MissileGeometry.flight(target.kind(), target.getYRot(), target.getXRot())
         .mul((Matrix4fc)geometry.defaultTransforms().get(bone.name()))
         .mul(cube.transform());
      Vec3 center = target.position().add(new Vec3(matrix.transformPosition(local)));

      for (int x = -3; x <= 3; x++) {
         for (int z = 7; z <= 10; z++) {
            level.getChunk(x, z);
         }
      }

      Vec3 direction = new Vec3(1.0, 0.0, 0.0);
      Vec3 movement = direction.scale(60.0);
      target.setDeltaMovement(Vec3.ZERO);
      GuidedMissileEntity detached = new GuidedMissileEntity(level, target.kind());
      detached.setPos(target.position());
      detached.setYRot(target.getYRot());
      detached.setXRot(target.getXRot());

      for (MissileProjectile missile : nativeMissiles) {
         Vec3 noseOffset = ManpadGeometry.nose(missile, Vec3.ZERO, direction);

         for (int pass = 0; pass < 2; pass++) {
            Vec3 from = center.subtract(direction.scale(30.0)).subtract(noseOffset).add(0.0, pass * 3, 0.0);
            AABB body = ManpadGeometry.worldBounds(missile, from, direction);
            FlightCollision.SweepResult hit = FlightCollision.sweepSegment(
               level, missile, from, from.add(movement), body, candidate -> candidate == target
            );
            boolean touching = hit.hit() instanceof EntityHitResult entityHit && entityHit.getEntity() == target;
            check(
               touching == (pass == 0),
               "Native model contact failed pass="
                  + pass
                  + " type="
                  + missile.getClass().getSimpleName()
                  + " hit="
                  + hit.hit()
                  + " terrainKnown="
                  + hit.terrainComplete()
            );
            Optional<FlightCollision.PathContact> known = FlightCollision.sweepPath(
               level, missile, List.of(new FlightCollision.Segment(from, from.add(movement), body)), candidate -> candidate == detached, detached
            );
            boolean knownTouch = known.isPresent() && known.get().contact().hit() instanceof EntityHitResult entityHitx && entityHitx.getEntity() == detached;
            check(knownTouch == (pass == 0), "Unindexed target contact failed pass=" + pass + " type=" + missile.getClass().getSimpleName());
         }

         LOG.info("AIR_DEFENSE_NATIVE_CONTACT_VERIFIED type={} modelHit=true threeMetreMiss=true unindexedTarget=true", missile.getClass().getSimpleName());
      }
   }

   private static GuidedMissileEntity makeTarget(ServerLevel level, Vec3 position) {
      GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.SPIKE_NLOS);
      missile.setPos(position);
      missile.setMission(List.of(new RoutePoint(1000.0, 140.0, position.z, 450.0)), false);
      missile.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 0);
      missile.setYRot(-90.0F);
      missile.configureSpike(position.y + 6.0, 450.0);
      check(level.addFreshEntity(missile), "Cannot add incoming missile");
      return missile;
   }

   private static void terminalFixture(GuidedMissileEntity missile) throws ReflectiveOperationException {
      for (String field : new String[]{"launchSource", "launchAge", "launchDistance"}) {
         Field value = GuidedMissileEntity.class.getDeclaredField(field);
         value.setAccessible(true);
         if (field.equals("launchSource")) {
            value.set(missile, new Vec3(-3000.0, 140.0, missile.getZ()));
         } else if (field.equals("launchAge")) {
            value.setInt(missile, 100);
         } else {
            value.setDouble(missile, 3000.0);
         }
      }

      missile.tickCount = 100;
      missile.setDeltaMovement(new Vec3(FlightUnits.blocksPerTick(450.0), 0.0, 0.0));
   }

   public static void tick(LevelTickEvent event) {
      if (Boolean.getBoolean("deepstrike.airDefenseReview") && event.phase == Phase.END && event.level == world && phase >= 0 && !finished && failure == null) {
         try {
            step(world);
         } catch (Throwable var2) {
            failure = var2.toString();
            LOG.error("AIR_DEFENSE_REVIEW_FAILED", var2);
         }
      }
   }

   private static void step(ServerLevel level) throws ReflectiveOperationException {
      long now = level.getGameTime();
      age = (int)(now - phaseAt);

      for (Entity entity : level.getAllEntities()) {
         if (entity instanceof PatriotInterceptorEntity flight
            && flight.isAlive()
            && flight.tickCount >= 1
            && flight.tickCount <= 20
            && flight.getDeltaMovement().lengthSqr() > 1.0E-10) {
            Vec3 direction = flight.getDeltaMovement().normalize();
            Vec3 axis = launchAxes.computeIfAbsent(flight.getUUID(), ignored -> direction);
            check(axis.dot(direction) > 0.999999, "Interceptor turned before clearing launch phase");
            if (flight.tickCount == 20) {
               LOG.info("AIR_DEFENSE_AXIAL_LAUNCH_VERIFIED warhead={} ticks=20", flight.warhead());
            }
         }
      }

      check(now - started < 2200L, "Review timed out");
      if (phase == 0) {
         target.setPos(FIXTURE);
         target.setDeltaMovement(new Vec3(0.1, 0.0, 0.0));
         if (age < 60) {
            check(ammo() == 60 && flights(level).isEmpty(), "Iron Dome fired during launch / distant approach");
            return;
         }

         if (age == 60) {
            terminalFixture(target);
            LOG.info("AIR_DEFENSE_LAUNCH_HOLD_VERIFIED ticks=60 ammo=60");
         }

         check(!target.isIntercepted(), "Fixture was hit before simulated miss");
         List<PatriotInterceptorEntity> flights = flights(level);
         check(flights.size() <= 1, "Duplicate initial allocation or excess salvo: " + flights.size());
         if (flights.size() == 1) {
            check(removedAt < 0L || now - removedAt >= 10L, "Retry launched before the post-miss interval");
            Set<UUID> sources = new HashSet<>();

            for (PatriotInterceptorEntity flightx : flights) {
               check(flightx.getPersistentData().getInt("IronDomeVolley") == expectedWave, "Wrong volley index");
               check(sources.add(flightx.defenseSource()), "One battery fired twice in a volley");
            }

            check(sources.size() == 1, "Retry allocated multiple simultaneous interceptors");
            check(ammo() == 60 - expectedWave, "Incorrect ammunition accounting");
            if (expectedWave == 1) {
               check(sources.contains(batteries[0].getUUID()), "Closest aligned battery was not selected");
            }

            if (seenAt < 0L) {
               seenAt = now;
               observedSources = sources;
               LOG.info("AIR_DEFENSE_VOLLEY_VERIFIED wave={} shots={} ammo={} tick={}", new Object[]{expectedWave, flights.size(), ammo(), now});
            }
         }

         if (seenAt >= 0L && now - seenAt >= 10L) {
            previousSources = observedSources;
            flights.forEach(Entity::discard);
            removedAt = now;
            seenAt = -1L;
            if (expectedWave == 3) {
               target.setFrequency("123");

               for (IronDomeEntity battery : batteries) {
                  battery.setFriendlyFrequencies(List.of("123"));
               }

               phase = 1;
               phaseAt = now;
               age = 0;
            } else {
               expectedWave++;
            }
         }
      } else if (phase == 1) {
         target.setPos(FIXTURE);
         target.setDeltaMovement(Vec3.ZERO);
         check(ammo() == 57 && flights(level).isEmpty(), "Friendly target consumed ammunition");
         if (age >= 60) {
            LOG.info("AIR_DEFENSE_FRIENDLY_VERIFIED ammo=57");
            target.discard();

            for (IronDomeEntity battery : batteries) {
               battery.setAutoDefense(false);
               battery.setFriendlyFrequencies(List.of());
               battery.setReadyTamir(20);
            }

            target = makeTarget(level, new Vec3(0.0, 140.0, 180.0));
            terminalFixture(target);
            target.setDeltaMovement(new Vec3(FlightUnits.blocksPerTick(450.0), 0.0, 0.0));

            for (int x = -1; x <= 0; x++) {
               for (int y = 102; y <= 106; y++) {
                  level.setBlock(new BlockPos(x, y, 12), Blocks.GRAY_CONCRETE.defaultBlockState(), 3);
               }
            }

            for (IronDomeEntity battery : batteries) {
               battery.setAutoDefense(true);
            }

            phase = 2;
            phaseAt = now;
            age = 0;
         }
      } else if (phase == 2) {
         if (age % 20 == 0) {
            LOG.info(
               "AIR_DEFENSE_CONTACT_TRACE age={} targetAlive={} target={} velocity={} interceptors={}",
               new Object[]{
                  age,
                  target.isAlive(),
                  target.position(),
                  target.getDeltaMovement(),
                  flights(level).stream().map(f -> f.position() + " v=" + f.getDeltaMovement() + " separation=" + f.distanceTo(target)).toList()
               }
            );
         }

         Field accuracy = PatriotInterceptorEntity.class.getDeclaredField("plannedHit");
         accuracy.setAccessible(true);
         Field offset = PatriotInterceptorEntity.class.getDeclaredField("missOffset");
         offset.setAccessible(true);

         for (PatriotInterceptorEntity flightx : flights(level)) {
            if (!clearExitVerified) {
               check(!batteries[0].getUUID().equals(flightx.defenseSource()), "Blocked closest battery was chosen over a clear launcher");
               clearExitVerified = true;
               LOG.info("AIR_DEFENSE_CLEAR_EXIT_VERIFIED blockedClosestSkipped=true");
            }

            accuracy.setBoolean(flightx, true);
            offset.set(flightx, Vec3.ZERO);
            liveInterceptorId = flightx.getId();
            focus = flightx.position().subtract(flightx.getDeltaMovement().normalize().scale(12.0));
         }

         if (target.isIntercepted()) {
            check(!ActiveFlights.resolve(target, "INTERCEPTED"), "A target accepted a duplicate interception result");
            boolean recorded = false;

            for (Tag raw : ActiveFlights.get(level).save(new CompoundTag()).getList("Missions", 10)) {
               CompoundTag record = (CompoundTag)raw;
               if (record.hasUUID("FlightId") && record.getUUID("FlightId").equals(target.getUUID())) {
                  recorded = "INTERCEPTED".equals(record.getString("Outcome"));
               }
            }

            check(recorded, "Interception result missing from the flight directory");
            focus = target.position();
            ammoAfterHit = ammo();
            phase = 3;
            phaseAt = now;
            age = 0;
            LOG.info(
               "AIR_DEFENSE_ACTUAL_INTERCEPT_VERIFIED movingTarget=true point={} flightAge={} ammo={}", new Object[]{focus, target.tickCount, ammoAfterHit}
            );
         } else {
            check(age < 600, "Actual Tamir never intercepted the moving missile");
         }
      } else if (phase == 3) {
         check(ammo() == ammoAfterHit, "Battery kept firing after a successful interception");
         if (age >= 80) {
            for (IronDomeEntity battery : batteries) {
               battery.discard();
            }

            target.discard();
            flights(level).forEach(Entity::discard);
            LOG.info("AIR_DEFENSE_STOP_AFTER_HIT_VERIFIED");

            for (ServerPlayer player : level.players()) {
               if (player.isSpectator()) {
                  player.connection
                     .teleport(
                        Boolean.getBoolean("deepstrike.reviewFar") ? Double.parseDouble(System.getProperty("deepstrike.reviewDistance", "8000")) : 0.0,
                        140.0,
                        170.0,
                        0.0F,
                        0.0F
                     );
               }
            }

            spawnAllTypes(level);
            phase = 4;
            phaseAt = now;
            age = 0;
         }
      } else if (phase == 4) {
         Vec3 sum = Vec3.ZERO;
         int count = 0;

         for (Entity flightx : additional) {
            if (flightx.isAlive()) {
               sum = sum.add(flightx.position());
               count++;
            }
         }

         if (count > 0) {
            focus = sum.scale(1.0 / count);
         }

         if (glowOnly()) {
            int view = age < 30 ? 2 : (age < 55 ? 0 : (age < 80 ? 3 : 1));
            if (additional.get(view).isAlive()) {
               focus = additional.get(view).position();
            }
         }

         if (age >= 100) {
            additional.forEach(Entity::discard);
            target.discard();
            LOG.info("AIR_DEFENSE_ALL_TYPES_FINISHED types=PAC2,PAC3,FIM92,IGLA");
            if (glowOnly()) {
               finished = true;
               return;
            }

            if (pacOnly()) {
               pacType = 0;
               startPacPursuit(level);
               phase = 6;
            } else {
               pursuitType = 0;
               startPursuit(level);
               phase = 5;
            }

            phaseAt = now;
            age = 0;
         }
      } else if (phase == 5) {
         if (nativeContactAt >= 0L) {
            if (now - nativeContactAt >= 12L) {
               target.discard();
               if (pursuitType == 0) {
                  pursuitType = 1;
                  startPursuit(level);
                  phaseAt = now;
                  age = 0;
               } else if (stabilityOnly()) {
                  stabilityTrial++;
                  if (stabilityTrial < 8) {
                     pursuitType = 0;
                     startPursuit(level);
                     phaseAt = now;
                     age = 0;
                  } else {
                     finished = true;
                     LOG.info("MANPAD_STABILITY_PASS pursuits=16");
                  }
               } else {
                  LOG.info("AIR_DEFENSE_NATIVE_PURSUIT_FINISHED");
                  pacType = 0;
                  startPacPursuit(level);
                  phase = 6;
                  phaseAt = now;
                  age = 0;
               }
            }

            return;
         }

         focus = target.position();
         if (stabilityOnly() && (age == 5 || age == 30) && pursuit.isAlive()) {
            Vec3 before = pursuit.position();
            int ticks = pursuit.tickCount;
            pursuit.tick();
            pursuit.tick();
            check(pursuit.position().equals(before) && pursuit.tickCount == ticks, "Duplicate server tick moved MANPADS twice");
            LOG.info("MANPAD_DUPLICATE_TICK_PASS type={} trial={} age={}", new Object[]{pursuitType, stabilityTrial, age});
         }

         if (Boolean.getBoolean("deepstrike.reviewNativeContact")) {
            if (age % 10 == 0) {
               Vec3 midpoint = target.position().add(pursuit.position()).scale(0.5);

               for (ServerPlayer playerx : level.players()) {
                  if (playerx.isSpectator()) {
                     playerx.connection.teleport(midpoint.x, midpoint.y, midpoint.z - 10.0, 0.0F, 0.0F);
                  }
               }
            }

            if (pursuit.distanceToSqr(target) < 400.0) {
               contactWarhead = 4 + pursuitType;
               contactCapture++;
            }
         }

         if (age % 40 == 0 || pursuit.distanceToSqr(target) < 144.0) {
            LOG.info(
               "AIR_DEFENSE_PURSUIT_TRACE type={} age={} targetAlive={} interceptorAlive={} separation={} target={} interceptor={} velocity={} lost={} lostTarget={} targetUUID={}",
               new Object[]{
                  pursuitType,
                  age,
                  target.isAlive(),
                  pursuit.isAlive(),
                  pursuit.distanceTo(target),
                  target.position(),
                  pursuit.position(),
                  pursuit.getDeltaMovement(),
                  pursuit.isLost(),
                  pursuit.isLostTarget(),
                  pursuit.getTargetUUID()
               }
            );
         }

         if (target.isIntercepted()) {
            check(!pursuit.isAlive(), "Native missile remained after contact");
            LOG.info("AIR_DEFENSE_NATIVE_PURSUIT_VERIFIED type={} ticks={} point={}", new Object[]{pursuitType == 0 ? "FIM92" : "IGLA", age, target.position()});
            nativeContactAt = now;
            contactCapture++;
         } else {
            check(
               age < 650 && target.isAlive() && pursuit.isAlive(),
               "Native pursuit failed type="
                  + pursuitType
                  + " age="
                  + age
                  + " targetAlive="
                  + target.isAlive()
                  + " interceptorAlive="
                  + pursuit.isAlive()
                  + " lost="
                  + pursuit.isLost()
                  + " lostTarget="
                  + pursuit.isLostTarget()
                  + " targetUUID="
                  + pursuit.getTargetUUID()
                  + " exploded="
                  + pursuit.getExploded()
                  + " removal="
                  + pursuit.getRemovalReason()
                  + " separation="
                  + pursuit.distanceTo(target)
            );
         }
      } else if (phase == 6) {
         if (pacContactAt < 0L) {
            focus = target.position();
            if (pacPursuit.distanceToSqr(target) < 400.0) {
               contactWarhead = pacType;
               contactCapture++;
            }

            if (target.isIntercepted()) {
               check(!pacPursuit.isAlive(), "PAC remained after model contact");
               pacContactAt = now;
               contactCapture++;
               LOG.info(
                  "AIR_DEFENSE_PAC_PURSUIT_VERIFIED warhead={} ticks={} target={} interceptor={}",
                  new Object[]{pacType, age, target.position(), pacPursuit.position()}
               );
            } else {
               check(
                  age < 650 && target.isAlive() && pacPursuit.isAlive(),
                  "PAC pursuit failed warhead=" + pacType + " age=" + age + " gap=" + pacPursuit.distanceTo(target)
               );
            }
         } else if (now - pacContactAt >= 12L) {
            target.discard();
            if (pacType == 0) {
               pacType = 1;
               startPacPursuit(level);
               phaseAt = now;
               age = 0;
            } else if (pacOnly()) {
               finished = true;
               LOG.info("AIR_DEFENSE_PAC_REALISM_FINISHED scope=Tamir,PAC2,PAC3,allFourWakes");
            } else {
               LOG.info("AIR_DEFENSE_PAC_PURSUIT_FINISHED");
               nativeExplosionType = 0;
               startNativeExplosion(level);
               phase = 7;
               phaseAt = now;
               age = 0;
            }
         }
      } else if (phase == 7 && age >= 80) {
         if (nativeExplosionType == 0) {
            nativeExplosionType = 1;
            startNativeExplosion(level);
            phaseAt = now;
            age = 0;
         } else {
            finished = true;
            LOG.info("AIR_DEFENSE_NATIVE_EXPLOSIONS_FINISHED");
         }
      }
   }

   private static void startNativeExplosion(ServerLevel level) {
      Vec3 point = new Vec3(80.0, 141.0, 180.0);
      focus = point;

      for (int x = 76; x <= 84; x++) {
         for (int z = 176; z <= 184; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState());
         }
      }

      Cow dummy = new Cow(EntityType.COW, level);
      dummy.setPos(point.add(0.8, 0.0, 0.0));
      dummy.setNoAi(true);
      dummy.setNoGravity(true);
      check(level.addFreshEntity(dummy), "Cannot add native explosion damage fixture");
      MissileProjectile missile = (MissileProjectile)(nativeExplosionType == 0
         ? new Fim92MissileEntity((EntityType)ModEntities.FIM_92_MISSILE.get(), level)
         : new IglaMissileEntity((EntityType)ModEntities.IGLA_MISSILE.get(), level));
      missile.setExplosionDamage(100.0F);
      missile.setExplosionRadius(4.0F);
      missile.setPos(point);
      check(level.addFreshEntity(missile), "Cannot add native explosion fixture");

      for (ServerPlayer player : level.players()) {
         if (player.isSpectator()) {
            player.connection.teleport(point.x, point.y + 3.0, point.z - 8.0, 0.0F, 0.0F);
         }
      }

      float before = dummy.getHealth();
      int visuals = nativeExplosionVisuals;
      missile.causeExplode(point);
      check(nativeExplosionVisuals == visuals + 1, "Native explosion did not route common visuals");
      check(dummy.getHealth() < before || !dummy.isAlive(), "Native explosion lost its damage");
      LOG.info(
         "AIR_DEFENSE_NATIVE_EXPLOSION_VERIFIED type={} healthBefore={} healthAfter={} sharedVisuals=true",
         new Object[]{nativeExplosionType, before, dummy.getHealth()}
      );
      dummy.discard();
      missile.discard();
   }

   private static void startPacPursuit(ServerLevel level) {
      target = makeTarget(level, new Vec3(60.0, 170.0, 180.0));
      target.setDeltaMovement(new Vec3(FlightUnits.blocksPerTick(450.0), 0.0, 0.0));
      pacPursuit = new PatriotInterceptorEntity(level, new Vec3(0.0, 170.0, 180.0), new Vec3(1.0, 0.0, 0.0), target, pacType, true);
      check(level.addFreshEntity(pacPursuit), "Cannot add PAC pursuit interceptor");
      pacContactAt = -1L;

      for (ServerPlayer player : level.players()) {
         if (player.isSpectator()) {
            player.connection.teleport(60.0, 170.0, 170.0, 0.0F, 0.0F);
         }
      }
   }

   private static void startPursuit(ServerLevel level) {
      nativeScene = pursuitType;
      nativeContactAt = -1L;
      double offset = Double.parseDouble(System.getProperty("deepstrike.reviewPursuitOffset", "0"));
      if (stabilityOnly() && stabilityTrial % 2 == 1) {
         pursuit = newNativePursuit(level);
      }

      if (stabilityOnly() && stabilityTrial >= 2) {
         MissileKind kind = stabilityTrial < 4 ? MissileKind.FP5 : (stabilityTrial < 6 ? MissileKind.STORM_SHADOW : MissileKind.JAGM);
         boolean incoming = kind != MissileKind.FP5;
         double startX = incoming ? (kind == MissileKind.JAGM ? 300 : 600) : 60.0;
         double speed = incoming ? kind.minSpeed : 450.0;
         target = new GuidedMissileEntity(level, kind);
         target.setPos(startX + offset, 170.0, 180.0);
         target.setWarhead("none");
         target.setMission(List.of(new RoutePoint(incoming ? 0.0 : 1000.0, 170.0, 180.0, speed)), false);
         target.setLaunchVector(new Vec3(incoming ? -1.0 : 1.0, 0.0, 0.0), 0);

         try {
            terminalFixture(target);
         } catch (ReflectiveOperationException var10) {
            throw new IllegalStateException(var10);
         }

         target.setYRot(incoming ? 90.0F : -90.0F);
         target.setDeltaMovement(new Vec3(FlightUnits.blocksPerTick(speed) * (incoming ? -1 : 1), 0.0, 0.0));
         check(level.addFreshEntity(target), "Cannot add varied pursuit target");
      } else {
         target = makeTarget(level, new Vec3(60.0 + offset, 170.0, 180.0));
         target.setDeltaMovement(new Vec3(FlightUnits.blocksPerTick(450.0), 0.0, 0.0));
      }

      LOG.info(
         "MANPAD_PURSUIT_CASE trial={} type={} targetKind={} nativeCreatedFirst={}",
         new Object[]{stabilityTrial, pursuitType, target.kind(), stabilityOnly() && stabilityTrial % 2 == 1}
      );
      if (!stabilityOnly() || stabilityTrial % 2 == 0) {
         pursuit = newNativePursuit(level);
      }

      pursuit.setPos(0.0, 170.0, 180.0);
      pursuit.setDeltaMovement(new Vec3(0.25, 0.0, 0.0));
      pursuit.setYRot(-90.0F);
      pursuit.setTargetUuid(target.getUUID().toString());
      pursuit.setLost(false);
      pursuit.setLostTarget(false);
      check(level.addFreshEntity(pursuit), "Cannot add pursuit interceptor");
   }

   private static MissileProjectile newNativePursuit(ServerLevel level) {
      return (MissileProjectile)(pursuitType == 0
         ? new Fim92MissileEntity((EntityType)ModEntities.FIM_92_MISSILE.get(), level)
         : new IglaMissileEntity((EntityType)ModEntities.IGLA_MISSILE.get(), level));
   }

   private static void verifyAimPoints(ServerLevel level) {
      for (int type = 0; type < 2; type++) {
         pursuitType = type;
         MissileProjectile probe = newNativePursuit(level);
         probe.setPos(0.0, 1000.0, -5000.0);
         probe.setDeltaMovement(0.25, 0.0, 0.0);
         probe.setYRot(-90.0F);
         int entityTick = probe.tickCount;
         int synced = probe.getSyncedTick();
         probe.tick();
         check(probe.tickCount == entityTick && probe.getSyncedTick() == synced + 1, "Manual tick fixture failed");
         check(probe.getPersistentData().getInt("DSMManpadAge") == 1, "Manual tick did not commit integrated dynamics");
         check(probe.getDeltaMovement().length() < 1.0, "Native guidance overwrote manual tick velocity");
         Vec3 position = probe.position();
         probe.tick();
         check(probe.position().equals(position) && probe.getSyncedTick() == synced + 1, "Manual tick ran twice in one server frame");
         probe.getPersistentData().putInt("DSMManpadAge", 500);
         ManpadFlight.prepare(probe, null);
         check(!probe.isAlive(), "MANPADS survived 25-second limit");
         ManpadFlight.release(probe);
         LOG.info("MANPAD_MANUAL_TICK_PASS type={} committed=true duplicateBlocked=true expiry500=true", type);
      }

      int checked = 0;

      for (MissileKind kind : MissileKind.values()) {
         for (float yaw : new float[]{0.0F, 90.0F}) {
            for (float pitch : new float[]{-30.0F, 20.0F}) {
               GuidedMissileEntity probe = new GuidedMissileEntity(level, kind);
               probe.setPos(800.0, 220.0, 180.0);
               probe.setYRot(yaw);
               probe.setXRot(pitch);
               Vec3 point = probe.position().add(MissileModelContact.aimOffset(probe));
               check(MissileModelContact.fraction(probe, point, point, Vec3.ZERO) == 0.0, "Aim point outside solid geometry: " + kind);
               checked++;
            }
         }
      }

      LOG.info("MANPAD_AIM_GEOMETRY_PASS cases={} kinds=5", checked);
   }

   private static List<PatriotInterceptorEntity> flights(ServerLevel level) {
      ArrayList<PatriotInterceptorEntity> result = new ArrayList<>();

      for (Entity entity : level.getAllEntities()) {
         if (entity instanceof PatriotInterceptorEntity flight && flight.isAlive() && target.getUUID().equals(flight.targetId())) {
            for (IronDomeEntity battery : batteries) {
               if (battery.getUUID().equals(flight.defenseSource())) {
                  result.add(flight);
                  break;
               }
            }
         }
      }

      return result;
   }

   private static int ammo() {
      return Arrays.stream(batteries).mapToInt(IronDomeEntity::getReadyTamir).sum();
   }

   private static void check(boolean value, String message) {
      if (!value) {
         throw new IllegalStateException(message);
      }
   }

   private AirDefenseReview() {
   }
}
