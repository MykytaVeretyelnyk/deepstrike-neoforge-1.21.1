package dev.sbw.deepstrike.validation;

import dev.sbw.deepstrike.game.AcousticWave;
import dev.sbw.deepstrike.game.Atmosphere;
import dev.sbw.deepstrike.game.CruiseDynamics;
import dev.sbw.deepstrike.game.CruiseRouteDynamics;
import dev.sbw.deepstrike.game.FlightDynamics;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PeacekeeperDynamics;
import dev.sbw.deepstrike.game.RouteDynamics;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.game.SimpleFlightPlan;
import dev.sbw.deepstrike.game.SpikeDynamics;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.MissileGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FlightRealismChecks {
   private static void require(boolean ok, String message) {
      if (!ok) {
         throw new IllegalStateException(message);
      }
   }

   public static void main(String[] args) {
      run();
   }

   private static void checkPhysicalIntegration() {
      for (double dt : new double[]{0.05, 0.0125}) {
         FlightDynamics.State state = new FlightDynamics.State(new Vec3(0.0, 1000.0, 0.0), new Vec3(100.0, 0.0, 0.0), 20.0);
         double initialEnergy = 296200.0;

         for (int i = 0; i < (int)Math.round(10.0 / dt); i++) {
            state = FlightDynamics.integrate(state, Vec3.ZERO, new Vec3(0.0, -9.81, 0.0), dt).state();
         }

         require(state.position().distanceTo(new Vec3(1000.0, 509.5, 0.0)) < 1.0E-7, "Vacuum ballistic displacement depends on tick rate: " + state);
         double energy = 10.0 * state.velocity().lengthSqr() + 196.20000000000002 * state.position().y;
         require(Math.abs(energy - initialEnergy) < 1.0E-6, "Vacuum ballistic energy drift: " + (energy - initialEnergy));
      }

      require(Math.abs(Atmosphere.density(63.0) - 1.225) < 0.001, "Sea-level density");
      require(Math.abs(Atmosphere.density(11063.0) - 0.3639) < 0.001, "Tropopause density");
      require(Math.abs(Atmosphere.soundSpeed(63.0) - 340.3) < 0.1, "Sea-level speed of sound");
      require(Atmosphere.density(10063.0) < Atmosphere.density(1063.0), "Density must decrease with altitude");
      System.out.println("FLIGHT_CHECK physical vacuum energy/time-step invariance and standard atmosphere PASS");
   }

   private static void checkAcousticArrival() {
      double sound = 343.0;
      double distance = 3000.0;
      int fixed = AcousticWave.delayTicks(distance, sound);
      require(fixed > 140, "Distant boom must outlive FP5 visual flash");
      require(!AcousticWave.arrived(fixed - 1, distance, sound) && AcousticWave.arrived(fixed, distance, sound), "Sound arrived before its physical wavefront");

      for (double listenerSpeed : new double[]{-50.0, 50.0}) {
         double expected = distance / (sound - listenerSpeed);
         int arrival = -1;

         for (int tick = 0; tick < 1000; tick++) {
            if (AcousticWave.arrived(tick, distance + listenerSpeed * tick / 20.0, sound)) {
               arrival = tick;
               break;
            }
         }

         require(arrival >= 0 && Math.abs(arrival / 20.0 - expected) < 0.051, "Moving listener acoustic interception: " + listenerSpeed);
      }

      System.out.println("FLIGHT_CHECK acoustic arrival after visual expiry and moving listener PASS fixedTicks=" + fixed);
   }

   private static void checkTakeoffLean() {
      Vec3 origin = Vec3.ZERO;
      PeacekeeperDynamics.Plan plan = new PeacekeeperDynamics.Plan(origin, new Vec3(3000.0, 0.0, 0.0), 5500.0, Math.PI / 2, 200);
      PeacekeeperDynamics.State state = new PeacekeeperDynamics.State(origin, Vec3.ZERO, 0.0);
      double peakAngle = 0.0;
      double end = PeacekeeperDynamics.CLEARANCE_EXIT_PHYSICS_SECONDS + 1.65;

      while (state.seconds() < end) {
         PeacekeeperDynamics.State previous = state;
         state = PeacekeeperDynamics.step(plan, state);
         if (previous.position().y < PeacekeeperDynamics.CLEARANCE) {
            require(state.velocity().horizontalDistance() < 1.0E-8, "Peacekeeper leaned before clearing its platform");
         }

         double angle = Math.toDegrees(Math.atan2(state.velocity().horizontalDistance(), state.velocity().y));
         peakAngle = Math.max(peakAngle, angle);
         double age = (state.seconds() - PeacekeeperDynamics.CLEARANCE_EXIT_PHYSICS_SECONDS) / 1.0;
         if (age >= 1.55) {
            require(angle < 0.05, "Peacekeeper did not straighten after its takeoff pulse: " + angle);
         }
      }

      require(peakAngle > 0.5 && peakAngle <= 1.01, "Peacekeeper takeoff lean angle: " + peakAngle);
      require(
         state.position().horizontalDistance() > 0.01 && state.position().horizontalDistance() < 2.0,
         "Peacekeeper takeoff did not produce a small displacement: " + state.position()
      );
      System.out
         .println(
            "FLIGHT_CHECK Peacekeeper takeoff PASS maxDegrees=" + peakAngle + " lateralMetres=" + state.position().horizontalDistance() + " settleWorldSeconds=1.5"
         );
   }

   private static void checkDescendingLeg() {
      Vec3 origin = new Vec3(1000.0, 300.0, 0.0);
      Vec3 end = new Vec3(3000.0, 0.0, 0.0);

      for (MissileKind kind : List.of(MissileKind.FP5, MissileKind.STORM_SHADOW, MissileKind.JAGM)) {
         List<RoutePoint> route = List.of(new RoutePoint(end.x, end.y, end.z, kind.defaultSpeed));
         CruiseRouteDynamics.State state = new CruiseRouteDynamics.State(
            new CruiseDynamics.State(origin, new Vec3(FlightUnits.metresPerSecond(kind.defaultSpeed), 0.0, 0.0)), 0, 1000.0, origin
         );
         boolean arrived = false;
         double lowBeforeHalf = 300.0;

         for (int i = 0; i < 12000; i++) {
            CruiseRouteDynamics.Step step = CruiseRouteDynamics.step(state, route, kind, new Vec3(1.0, 0.0, 0.0), false, false);
            state = step.state();
            if (state.motion().position().x < 2000.0) {
               lowBeforeHalf = Math.min(lowBeforeHalf, state.motion().position().y);
            }

            require(state.motion().position().y >= -1.0, "Premature ground strike on descending leg: " + kind);
            if (step.finished()) {
               arrived = true;
               break;
            }
         }

         require(arrived, "Descending leg failed to arrive: " + kind);
         require(lowBeforeHalf > 130.0, "Descending leg dives toward endpoint altitude too early: " + kind + " y=" + lowBeforeHalf);
         require(state.motion().position().distanceTo(end) < 2.0, "Descending leg misses endpoint: " + kind);
         System.out.println("FLIGHT_CHECK " + kind + " 300m-to-ground over2km PASS firstHalfMinY=" + lowBeforeHalf);
      }
   }

   private static void checkSimplePlans() {
      for (MissileKind kind : List.of(MissileKind.FP5, MissileKind.STORM_SHADOW, MissileKind.JAGM, MissileKind.SPIKE_NLOS)) {
         double distance = Math.min(2400.0, kind.maxRange * 0.8);
         Vec3 start = new Vec3(0.0, 100.0, 0.0);
         Vec3 end = new Vec3(distance, 100.0, 0.0);
         Vec3 east = new Vec3(1.0, 0.0, 0.0);
         List<RoutePoint> mission = List.of(
            new RoutePoint(distance * 0.3, 100.0, 0.0, kind.defaultSpeed),
            new RoutePoint(distance * 0.65, 100.0, 0.0, kind.defaultSpeed),
            new RoutePoint(end.x, end.y, end.z, kind.defaultSpeed)
         );
         List<RoutePoint> plan = SimpleFlightPlan.build(start, east, kind, mission);
         require(plan.size() < 1600, "Simple plan expanded excessively");
         if (kind != MissileKind.FP5 && kind != MissileKind.STORM_SHADOW) {
            require(plan.get(1).y() == 100.0, "Intermediate altitude was rewritten");
         } else {
            require(Math.abs(plan.get(16).y() - 132.0) < 0.001, "Launch did not climb 32 metres");
            require(plan.subList(0, 17).stream().allMatch(p -> Math.abs(p.z()) < 0.001), "Launch turned before climbing");
         }

         RouteDynamics.State state = new RouteDynamics.State(new CruiseDynamics.State(start, Vec3.ZERO), 0, 0.0);
         boolean arrived = false;

         for (int i = 0; i < 48000; i++) {
            RouteDynamics.Step step = RouteDynamics.step(state, plan, kind, east);
            state = step.state();
            if (step.finished()) {
               arrived = true;
               break;
            }
         }

         require(arrived, "Simple plan failed arrival: " + kind + " " + state);
         require(state.motion().position().distanceTo(end) < 0.1, "Simple plan lost final altitude/position");
         System.out.println("FLIGHT_CHECK simple plan PASS " + kind + " points=" + plan.size());
      }
   }

   private static void checkMandatoryAltitude() {
      Vec3 origin = new Vec3(0.0, 10.0, 0.0);
      Vec3 east = new Vec3(1.0, 0.0, 0.0);
      List<RoutePoint> mission = List.of(
         new RoutePoint(600.0, 100.0, 0.0, 600.0), new RoutePoint(1600.0, 100.0, 0.0, 600.0), new RoutePoint(2400.0, 10.0, 0.0, 600.0)
      );
      List<RoutePoint> plan = SimpleFlightPlan.build(origin, east, MissileKind.FP5, mission);
      require(plan.stream().anyMatch(p -> p.x() == 600.0 && p.y() == 100.0), "Lost first 100m waypoint");
      RouteDynamics.State state = new RouteDynamics.State(new CruiseDynamics.State(origin, Vec3.ZERO), 0, 0.0);
      double firstError = Double.NaN;
      double secondError = Double.NaN;
      double holdError = 0.0;
      boolean arrived = false;

      for (int i = 0; i < 48000; i++) {
         Vec3 before = state.motion().position();
         RouteDynamics.Step step = RouteDynamics.step(state, plan, MissileKind.FP5, east);
         state = step.state();
         Vec3 after = state.motion().position();
         if (before.x < 600.0 && after.x >= 600.0) {
            firstError = Math.abs(before.lerp(after, (600.0 - before.x) / (after.x - before.x)).y - 100.0);
         }

         if (before.x < 1600.0 && after.x >= 1600.0) {
            secondError = Math.abs(before.lerp(after, (1600.0 - before.x) / (after.x - before.x)).y - 100.0);
         }

         if (after.x >= 650.0 && after.x <= 1500.0) {
            holdError = Math.max(holdError, Math.abs(after.y - 100.0));
         }

         if (step.finished()) {
            arrived = true;
            break;
         }
      }

      require(arrived, "100m route failed final arrival: " + state);
      require(
         firstError <= 2.0 && secondError <= 2.0 && holdError <= 2.0,
         "Mandatory altitude missed: first=" + firstError + " second=" + secondError + " hold=" + holdError
      );
      System.out.println("FLIGHT_CHECK mandatory100m PASS firstError=" + firstError + " secondError=" + secondError + " holdError=" + holdError);
   }

   private static void checkSharpMissions() {
      Vec3 origin = new Vec3(0.0, 100.0, 0.0);
      Vec3 east = new Vec3(1.0, 0.0, 0.0);

      for (double angle : new double[]{90.0, 150.0, 175.0, 180.0}) {
         for (MissileKind kind : List.of(MissileKind.FP5, MissileKind.STORM_SHADOW, MissileKind.JAGM, MissileKind.SPIKE_NLOS)) {
            double leg = kind == MissileKind.JAGM ? 100.0 : 500.0;
            double radians = Math.toRadians(angle);
            Vec3 corner = origin.add(leg, 0.0, 0.0);
            Vec3 end = corner.add(Math.cos(radians) * leg, 0.0, Math.sin(radians) * leg);
            List<RoutePoint> plan = SimpleFlightPlan.build(
               origin,
               east,
               kind,
               List.of(new RoutePoint(corner.x, 100.0, corner.z, kind.maxSpeed), new RoutePoint(end.x, 100.0, end.z, kind.maxSpeed))
            );
            RouteDynamics.State state = new RouteDynamics.State(new CruiseDynamics.State(origin, Vec3.ZERO), 0, 0.0);
            int cornerIndex = 0;

            for (int n = 0; n < plan.size(); n++) {
               if (new Vec3(plan.get(n).x(), plan.get(n).y(), plan.get(n).z()).distanceTo(corner) < 0.001) {
                  cornerIndex = n;
                  break;
               }
            }

            boolean arrived = false;
            double altitudeError = 0.0;
            double maxTurn = 0.0;
            int lastCursor = 0;

            for (int i = 0; i < 24000; i++) {
               RouteDynamics.State before = state;
               RouteDynamics.Step step = RouteDynamics.step(state, plan, kind, east);
               state = step.state();
               require(state.cursor() >= lastCursor, "Route progress went backwards");
               lastCursor = state.cursor();
               if (state.cursor() > cornerIndex) {
                  altitudeError = Math.max(altitudeError, Math.abs(state.motion().position().y - 100.0));
               }

               if (before.motion().velocity().length() > 1.0) {
                  double dot = before.motion().velocity().normalize().dot(state.motion().velocity().normalize());
                  maxTurn = Math.max(maxTurn, Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
               }

               if (step.finished()) {
                  arrived = true;
                  break;
               }

               require(state.launchDistance() < leg * 4.0, "Circling on " + kind + " turn=" + angle + " " + state);
            }

            require(arrived, "Sharp mission failed " + kind + " turn=" + angle + " " + state);
            require(altitudeError < 1.0, "Turn changed mandatory altitude " + altitudeError);
            require(maxTurn <= FlightDynamics.angularRate(kind, false) * 0.0125 + 1.0E-6, "Sharp mission instantaneous turn");
            System.out.println("FLIGHT_CHECK sharp mission PASS " + kind + " degrees=" + angle + " path=" + state.launchDistance());
         }
      }
   }

   private static void checkZigzagMission() {
      Vec3 origin = new Vec3(0.0, 100.0, 0.0);
      Vec3 east = new Vec3(1.0, 0.0, 0.0);
      List<RoutePoint> mission = List.of(
         new RoutePoint(450.0, 200.0, 0.0, 1650.0),
         new RoutePoint(450.0, 200.0, 450.0, 1650.0),
         new RoutePoint(100.0, 200.0, 450.0, 1650.0),
         new RoutePoint(100.0, 100.0, 100.0, 1650.0),
         new RoutePoint(700.0, 100.0, 100.0, 1650.0)
      );
      List<RoutePoint> plan = SimpleFlightPlan.build(origin, east, MissileKind.FP5, mission);
      RouteDynamics.State state = new RouteDynamics.State(new CruiseDynamics.State(origin, Vec3.ZERO), 0, 0.0);
      boolean arrived = false;
      double[] closest = new double[]{1.0E9, 1.0E9, 1.0E9, 1.0E9, 1.0E9};
      double[] heightErrors = new double[5];

      for (int i = 0; i < 24000; i++) {
         RouteDynamics.Step step = RouteDynamics.step(state, plan, MissileKind.FP5, east);
         state = step.state();

         for (int j = 0; j < mission.size(); j++) {
            RoutePoint point = mission.get(j);
            double d = state.motion().position().subtract(new Vec3(point.x(), point.y(), point.z())).horizontalDistance();
            if (d < closest[j]) {
               closest[j] = d;
               heightErrors[j] = Math.abs(state.motion().position().y - point.y());
            }
         }

         if (step.finished()) {
            arrived = true;
            break;
         }

         require(state.launchDistance() < 3500.0, "Mixed-altitude zigzag started circling " + state);
      }

      require(arrived, "Mixed-altitude zigzag failed " + state);

      for (int i = 0; i < mission.size(); i++) {
         require(closest[i] < 12.0 && heightErrors[i] < 2.0, "Zigzag waypoint missed " + i + " distance=" + closest[i] + " heightError=" + heightErrors[i]);
      }

      System.out.println("FLIGHT_CHECK mixed-altitude zigzag PASS path=" + state.launchDistance());
   }

   private static void checkLongFastApproach() {
      Vec3 origin = new Vec3(0.0, 100.0, 0.0);
      Vec3 east = new Vec3(1.0, 0.0, 0.0);
      List<RoutePoint> plan = SimpleFlightPlan.build(
         origin,
         east,
         MissileKind.FP5,
         List.of(new RoutePoint(2000.0, 100.0, 0.0, 1650.0), new RoutePoint(2000.0, 100.0, 400.0, 1650.0), new RoutePoint(2300.0, 100.0, 400.0, 1650.0))
      );
      RouteDynamics.State state = new RouteDynamics.State(new CruiseDynamics.State(origin, Vec3.ZERO), 0, 0.0);
      boolean arrived = false;

      for (int i = 0; i < 24000; i++) {
         RouteDynamics.Step step = RouteDynamics.step(state, plan, MissileKind.FP5, east);
         state = step.state();
         if (step.finished()) {
            arrived = true;
            break;
         }

         require(state.launchDistance() < 3500.0, "Long fast approach circled " + state);
      }

      require(arrived, "Long fast approach failed " + state);
      System.out.println("FLIGHT_CHECK long fast approach PASS path=" + state.launchDistance());
   }

   private static void checkReportedSalvoRoute() {
      Vec3 forward = new Vec3(0.0, 0.12, -1.0).normalize();
      List<RoutePoint> mission = List.of(
         new RoutePoint(709.5, 64.0, -1245.5, 850.0), new RoutePoint(1395.5, 32.0, 706.5, 1650.0), new RoutePoint(1644.5, 0.0, 1220.5, 850.0)
      );

      for (int x : new int[]{728, 718, 713, 707, 703, 699, 693, 683}) {
         Vec3 origin = new Vec3(x + 0.5, 2.0, 400.5);
         List<RoutePoint> plan = SimpleFlightPlan.build(origin, forward, MissileKind.FP5, mission);
         RouteDynamics.State state = new RouteDynamics.State(new CruiseDynamics.State(origin, Vec3.ZERO), 0, 0.0);
         boolean arrived = false;

         for (int i = 0; i < 64000; i++) {
            RouteDynamics.Step step = RouteDynamics.step(state, plan, MissileKind.FP5, forward);
            state = step.state();
            if (step.finished()) {
               arrived = true;
               break;
            }

            require(state.launchDistance() < 8000.0, "Reported salvo circling x=" + x + " " + state + " target=" + plan.get(state.cursor()));
         }

         require(arrived, "Reported salvo did not arrive x=" + x + " " + state);
         System.out.println("FLIGHT_CHECK reported salvo PASS x=" + x + " distance=" + state.launchDistance());
      }
   }

   private static void checkBallisticArcs() {
      Vec3 origin = new Vec3(0.0, 120.0, 0.0);

      for (double range : new double[]{100.0, 500.0, 1500.0, 2000.0, 3000.0, 6000.0}) {
         for (double speed : new double[]{2000.0, 5500.0}) {
            PeacekeeperDynamics.Selection selection = PeacekeeperDynamics.select(origin, origin.add(range, 0.0, 0.0), speed);
            require(selection.valid(), "No ballistic solution range=" + range + " speed=" + speed + " " + selection.reason());
            PeacekeeperDynamics.Plan plan = selection.plan();
            PeacekeeperDynamics.State state = new PeacekeeperDynamics.State(origin, Vec3.ZERO, 0.0);
            double apexX = 0.0;
            double apexY = origin.y;
            double maxTurn = 0.0;
            boolean descending = false;

            for (int i = 0; i < 24000; i++) {
               PeacekeeperDynamics.State previous = state;
               state = PeacekeeperDynamics.step(plan, state);
               if (state.position().y > apexY) {
                  apexY = state.position().y;
                  apexX = state.position().x;
               }

               if (previous.velocity().length() > 10.0) {
                  double dot = previous.velocity().normalize().dot(state.velocity().normalize());
                  maxTurn = Math.max(maxTurn, Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
               }

               if (state.seconds() > plan.boostTicks() * 0.05) {
                  require(!PeacekeeperDynamics.engineRunning(plan, state), "Powered coast");
                  require(state.velocity().x >= 0.0, "Ballistic arc flew backwards");
               }

               if (state.velocity().y < 0.0) {
                  descending = true;
               }

               if (descending && state.position().y <= origin.y) {
                  break;
               }
            }

            PeacekeeperDynamics.Prediction prediction = PeacekeeperDynamics.predict(plan);
            require(prediction != null && prediction.impact().distanceTo(plan.target()) < 1.0, "Ballistic arc missed target");
            require(apexX > range * 0.1 && apexX < range * 0.9, "Ballistic arc is effectively vertical " + apexX + " / " + range);
            require(maxTurn < Math.toRadians(2.0), "Ballistic heading jumped " + Math.toDegrees(maxTurn));
            System.out
               .println(
                  "FLIGHT_CHECK ballistic arc PASS range="
                     + range
                     + " speed="
                     + speed
                     + " apex="
                     + apexY
                     + " apexX="
                     + apexX
                     + " maxSubstepDegrees="
                     + Math.toDegrees(maxTurn)
               );
         }
      }
   }

   private static void checkSkyTarget() {
      Vec3 origin = new Vec3(0.0, 100.0, 0.0);
      Vec3 target = new Vec3(1200.0, 700.0, 0.0);
      Vec3 east = new Vec3(1.0, 0.0, 0.0);
      SpikeDynamics.State state = new SpikeDynamics.State(new CruiseDynamics.State(origin, east.scale(10.0)), 0.0);
      boolean arrived = false;
      double maxSpeed = 0.0;

      for (int i = 0; i < 20000; i++) {
         SpikeDynamics.Step step = SpikeDynamics.step(state, target, east, 450.0, 0.0, true);
         state = step.state();
         maxSpeed = Math.max(maxSpeed, state.motion().velocity().length());
         if (step.finished()) {
            arrived = true;
            break;
         }
      }

      require(arrived && state.motion().position().distanceTo(target) < 0.5, "SPIKE did not reach sky target " + state);
      require(maxSpeed <= 83.43333333333332, "SPIKE slowdown not applied");
      System.out.println("FLIGHT_CHECK SPIKE sky target PASS maxPhysicalKmh=" + maxSpeed * 3.6);
   }

   public static void run() {
      checkPhysicalIntegration();
      checkAcousticArrival();
      checkReportedSalvoRoute();
      checkBallisticArcs();
      checkSkyTarget();
      checkLongFastApproach();
      checkZigzagMission();
      checkSharpMissions();
      checkMandatoryAltitude();
      checkSimplePlans();
      checkTakeoffLean();
      checkDescendingLeg();
      Vec3 east = new Vec3(1.0, 0.0, 0.0);
      Vec3 origin = new Vec3(0.0, 1000.0, 0.0);

      for (MissileKind kind : MissileKind.values()) {
         if (kind != MissileKind.PEACEKEEPER) {
            CruiseDynamics.State motion = new CruiseDynamics.State(origin, east.scale(10.0));
            double maxTurn = 0.0;
            double minSpeed = Double.POSITIVE_INFINITY;
            double cut = -1.0;
            double time = 0.0;
            Vec3 target = origin.add(2000.0, 0.0, 0.0);

            for (int i = 0; i < 40000; i++) {
               CruiseDynamics.State previous = motion;
               motion = CruiseDynamics.step(motion, new CruiseDynamics.Command(target, east, kind.defaultSpeed, false, true, kind, false, true));
               time += 0.0125;
               if (motion.propulsion().terminalCoast() && cut < 0.0) {
                  cut = time;
               }

               double angle = Math.acos(Math.max(-1.0, Math.min(1.0, previous.velocity().normalize().dot(motion.velocity().normalize()))));
               maxTurn = Math.max(maxTurn, angle);
               if (time > 8.0) {
                  minSpeed = Math.min(minSpeed, motion.velocity().length() * 3.6);
               }

               if (motion.position().x >= target.x) {
                  require(cut >= 0.0 && time - cut >= 1.5 && time - cut <= 2.5, kind + " cutoff lead " + (time - cut));
                  require(minSpeed > 30.0, kind + " crawled " + minSpeed);
                  require(maxTurn <= FlightDynamics.angularRate(kind, false) * 0.0125 + 1.0E-6, kind + " angular jump");
                  System.out.println("FLIGHT_CHECK " + kind + " cutoff=" + (time - cut) + " minKmh=" + minSpeed);
                  break;
               }

               require(i < 39999, kind + " failed to arrive");
            }
         }
      }

      List<RoutePoint> route = List.of(
         new RoutePoint(650.0, 1030.0, 0.0, 600.0), new RoutePoint(650.0, 1030.0, 650.0, 600.0), new RoutePoint(1200.0, 1030.0, 650.0, 600.0)
      );
      CruiseRouteDynamics.State state = new CruiseRouteDynamics.State(
         new CruiseDynamics.State(origin, new Vec3(Math.cos(Math.toRadians(18.0)), Math.sin(Math.toRadians(18.0)), 0.0).scale(3.2)), 0, 0.0, origin
      );
      boolean arrived = false;
      double maxTurn = 0.0;

      for (int i = 0; i < 50000; i++) {
         CruiseRouteDynamics.State before = state;
         CruiseRouteDynamics.Step step = CruiseRouteDynamics.step(state, route, MissileKind.FP5, east, false, false);
         state = step.state();
         double dot = before.motion().velocity().normalize().dot(state.motion().velocity().normalize());
         maxTurn = Math.max(maxTurn, Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
         if (step.finished()) {
            arrived = true;
            break;
         }
      }

      require(state.launchDistance() < 2200.0, "FP5 made an orbit instead of anticipated turns: " + state.launchDistance());
      require(arrived, "FP5 failed two 90 degree route bends: " + state);
      require(maxTurn <= FlightDynamics.angularRate(MissileKind.FP5, false) * 0.0125 + 1.0E-6, "FP5 discontinuous turn");
      System.out.println("FLIGHT_CHECK FP5 two bends PASS maxSubstepDegrees=" + Math.toDegrees(maxTurn));
      ArrayList<RoutePoint> dense = new ArrayList<>();

      for (int ix = 1; ix <= 130; ix++) {
         dense.add(new RoutePoint(ix * 5, 1000.0, 0.0, 600.0));
      }

      for (int ix = 1; ix <= 130; ix++) {
         dense.add(new RoutePoint(650.0, 1000.0, ix * 5, 600.0));
      }

      for (int ix = 1; ix <= 130; ix++) {
         dense.add(new RoutePoint(650 + ix * 5, 1000.0, 650.0, 600.0));
      }

      RouteDynamics.State planned = new RouteDynamics.State(new CruiseDynamics.State(origin, east.scale(10.0)), 0, 0.0);
      arrived = false;

      for (int ix = 0; ix < 50000; ix++) {
         RouteDynamics.Step step = RouteDynamics.step(planned, dense, MissileKind.FP5, east);
         planned = step.state();
         if (step.finished()) {
            arrived = true;
            break;
         }
      }

      require(arrived, "FP5 dense planned route failed " + planned);
      System.out.println("FLIGHT_CHECK FP5 dense planned bends PASS");
      SpikeDynamics.State spike = new SpikeDynamics.State(new CruiseDynamics.State(origin, east.scale(10.0)), 0.0);
      arrived = false;
      double spikeCut = -1.0;
      double spikeTime = 0.0;

      for (int ixx = 0; ixx < 50000; ixx++) {
         SpikeDynamics.Step step = SpikeDynamics.step(spike, origin.add(1000.0, -100.0, 0.0), east, 450.0, origin.y + 60.0);
         spike = step.state();
         spikeTime += 0.0125;
         if (spike.motion().propulsion().terminalCoast() && spikeCut < 0.0) {
            spikeCut = spikeTime;
         }

         if (step.finished()) {
            arrived = true;
            break;
         }
      }

      require(
         arrived && spikeTime - spikeCut >= 1.5 && spikeTime - spikeCut <= 2.5, "SPIKE loft/terminal failed " + spike + " cutoff=" + (spikeTime - spikeCut)
      );
      System.out.println("FLIGHT_CHECK SPIKE loft PASS cutoff=" + (spikeTime - spikeCut));

      for (MissileKind kindx : List.of(MissileKind.JAGM, MissileKind.STORM_SHADOW)) {
         CruiseRouteDynamics.State lofted = new CruiseRouteDynamics.State(new CruiseDynamics.State(origin, east.scale(10.0)), 0, 0.0, origin);
         List<RoutePoint> loftRoute = List.of(new RoutePoint(1800.0, 900.0, 0.0, kindx.defaultSpeed));
         boolean landed = false;
         double time = 0.0;
         double cut = -1.0;

         for (int ixx = 0; ixx < 50000; ixx++) {
            CruiseRouteDynamics.Step stepx = CruiseRouteDynamics.step(lofted, loftRoute, kindx, east, true, false);
            lofted = stepx.state();
            time += 0.0125;
            if (lofted.motion().propulsion().terminalCoast() && cut < 0.0) {
               cut = time;
            }

            if (stepx.finished()) {
               landed = true;
               break;
            }
         }

         require(
            landed && lofted.launchDistance() < 2500.0 && time - cut >= 1.5 && time - cut <= 2.5,
            kindx + " lofted terminal miss/orbit/cutoff " + lofted + " lead=" + (time - cut)
         );
         System.out.println("FLIGHT_CHECK " + kindx + " loft PASS cutoff=" + (time - cut));
      }

      origin = new Vec3(0.5, 121.9F, 0.5);
      PeacekeeperDynamics.Selection selected = PeacekeeperDynamics.select(origin, origin.add(3000.0, 0.0, 0.0), 5500.0);
      require(selected.valid(), "Peacekeeper solve " + selected.reason());
      PeacekeeperDynamics.State pk = new PeacekeeperDynamics.State(origin, Vec3.ZERO, 0.0);
      double cutoff = -1.0;
      double impact = -1.0;
      Vec3 observer = origin.add(0.0, 4.0, 18.0);
      int oldObserverContact = -1;
      float yaw = 0.0F;
      float pitch = -90.0F;
      double earlySway = 0.0;
      int steps = 0;

      for (int ixx = 0; ixx < 48000; ixx++) {
         PeacekeeperDynamics.State before = pk;
         pk = PeacekeeperDynamics.step(selected.plan(), pk);
         int age = ixx / 4 + 1;
         if (age % 20 == 0 && ixx % 4 == 3) {
            observer = pk.position().add(0.0, 4.0, 18.0);
         }

         AABB bounds = MissileGeometry.substepBounds(
            MissileKind.PEACEKEEPER, before.position(), FlightOrientation.direction(yaw, pitch), pk.position().subtract(before.position()), yaw, pitch
         );
         AABB spectatorBox = new AABB(
               observer.x - 0.3, observer.y, observer.z - 0.3, observer.x + 0.3, observer.y + 1.8, observer.z + 0.3
            )
            .inflate(0.1);
         if (oldObserverContact < 0 && bounds.expandTowards(pk.position().subtract(before.position())).intersects(spectatorBox)) {
            oldObserverContact = age;
         }

         FlightOrientation.Angles angles = FlightOrientation.along(pk.velocity(), yaw, pitch);
         yaw = angles.yaw();
         pitch = angles.pitch();
         if (pk.seconds() < 4.0) {
            earlySway = Math.max(earlySway, Math.abs(pk.velocity().x));
         }

         steps = ixx + 1;
         if (PeacekeeperDynamics.engineRunning(selected.plan(), before) && !PeacekeeperDynamics.engineRunning(selected.plan(), pk)) {
            cutoff = pk.seconds() / 1.0;
         }

         if (pk.velocity().y < 0.0 && pk.position().y <= origin.y) {
            impact = pk.seconds() / 1.0;
            break;
         }
      }

      require(cutoff > 0.0 && impact - cutoff > 10.0, "Peacekeeper must coast along its ballistic arc");
      require(!PeacekeeperDynamics.engineRunning(selected.plan(), pk), "Peacekeeper restarted its engine on descent");
      require(PeacekeeperDynamics.predict(selected.plan()).impact().distanceTo(selected.plan().target()) <= 1.0, "Peacekeeper miss");
      require(earlySway > 0.0 && Double.isFinite(earlySway), "Peacekeeper launch sway missing or invalid " + earlySway);
      System.out
         .println(
            "FLIGHT_CHECK Peacekeeper PASS cutoff="
               + (impact - cutoff)
               + " worldSeconds="
               + impact
               + " oldSpectatorContactAge="
               + oldObserverContact
               + " swayMaxMps="
               + earlySway
               + " substeps="
               + steps
         );
   }
}
