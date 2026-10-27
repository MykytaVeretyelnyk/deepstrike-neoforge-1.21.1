package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.model.MissileGeometry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

public final class PeacekeeperDynamics {
   public static final double GRAVITY = 9.81;
   public static final int SUBSTEPS = 4;
   public static final double STEP_SECONDS = 0.0125;
   public static final double CLEARANCE = MissileGeometry.clearance(MissileKind.PEACEKEEPER);
   public static final double TAKEOFF_LEAN_WORLD_SECONDS = 1.5;
   public static final double CLEARANCE_EXIT_PHYSICS_SECONDS = verticalClearanceSeconds();
   private static final double DRY_MASS = 4000.0;
   private static final double PROPELLANT_MASS = 1000.0;
   private static final double TERMINAL_PROPELLANT = 200.0;
   public static final double TERMINAL_BURN_SECONDS = 30.0;

   public static double propellant(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state) {
      double boostRemaining = Math.max(0.0, 1.0 - state.seconds / (plan.boostTicks * 0.05));
      double terminalRemaining = Math.max(0.0, 1.0 - state.terminalSeconds / 30.0);
      return 800.0 * boostRemaining + 200.0 * terminalRemaining;
   }

   public static double mass(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state) {
      return 4000.0 + propellant(plan, state);
   }

   public static double impactSeconds(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state) {
      double height = Math.max(0.0, state.position.y - plan.target.y);
      double down = -state.velocity.y;
      return 2.0 * height / (Math.sqrt(down * down + 19.62 * height) + down + 1.0E-9) / 1.0;
   }

   public static boolean engineRunning(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state) {
      return state.seconds < plan.boostTicks * 0.05;
   }

   public static double terminalSecondsAt(PeacekeeperDynamics.Plan plan, double age) {
      return 0.0;
   }

   public static CompoundTag save(PeacekeeperDynamics.Plan plan) {
      CompoundTag tag = new CompoundTag();
      tag.putDouble("OriginX", plan.origin.x);
      tag.putDouble("OriginY", plan.origin.y);
      tag.putDouble("OriginZ", plan.origin.z);
      tag.putDouble("TargetX", plan.target.x);
      tag.putDouble("TargetY", plan.target.y);
      tag.putDouble("TargetZ", plan.target.z);
      tag.putDouble("AssignedKmh", plan.assignedKmh);
      tag.putDouble("Pitch", plan.pitch);
      tag.putInt("BoostTicks", plan.boostTicks);
      tag.putInt("Version", 2);
      return tag;
   }

   public static PeacekeeperDynamics.Plan load(CompoundTag tag) {
      if (tag.getInt("Version") >= 1 && tag.getInt("Version") <= 2) {
         Vec3 origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
         Vec3 target = new Vec3(tag.getDouble("TargetX"), tag.getDouble("TargetY"), tag.getDouble("TargetZ"));
         double speed = tag.getDouble("AssignedKmh");
         double pitch = tag.getDouble("Pitch");
         int boost = tag.getInt("BoostTicks");
         if (Double.isFinite(origin.lengthSqr())
            && Double.isFinite(target.lengthSqr())
            && Double.isFinite(speed)
            && Double.isFinite(pitch)
            && !(pitch < 0.0)
            && !(pitch > Math.PI / 2)
            && boost >= 1
            && boost <= 400) {
            return tag.getInt("Version") == 1
               ? select(origin, target, speed).plan()
               : new PeacekeeperDynamics.Plan(origin, target, PeacekeeperFlightProfile.assignedKmh(speed), pitch, boost);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static PeacekeeperDynamics.State step(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state) {
      double nextTime = Math.rint((state.seconds + 0.0125) / 0.0125) * 0.0125;
      Vec3 gravity = new Vec3(0.0, -9.81, 0.0);
      Vec3 thrust = Vec3.ZERO;
      boolean boost = nextTime <= plan.boostTicks * 0.05;
      double terminalSeconds = state.terminalSeconds;
      double mass = mass(plan, new PeacekeeperDynamics.State(state.position, state.velocity, nextTime, terminalSeconds));
      double density = Atmosphere.density(state.position.y);
      Vec3 drag = FlightDynamics.drag(state.velocity, density, 0.06, 3.14);
      if (boost) {
         Vec3 horizontal = new Vec3(plan.target.x - plan.origin.x, 0.0, plan.target.z - plan.origin.z).normalize();
         double leanEnd = CLEARANCE_EXIT_PHYSICS_SECONDS + 1.5;
         Vec3 wanted = !(state.position.y - plan.origin.y < CLEARANCE) && !(nextTime <= leanEnd)
            ? horizontal.scale(Math.cos((Math.PI / 2) + (plan.pitch - (Math.PI / 2)) * smoothstep((nextTime - leanEnd) / 5.0)))
               .add(0.0, Math.sin((Math.PI / 2) + (plan.pitch - (Math.PI / 2)) * smoothstep((nextTime - leanEnd) / 5.0)), 0.0)
            : new Vec3(0.0, 1.0, 0.0);
         if (state.position.y - plan.origin.y >= CLEARANCE) {
            wanted = wanted.add(horizontal.scale(Math.tan(takeoffLeanAngle(nextTime)))).normalize();
         }

         Vec3 current = state.velocity.lengthSqr() > 1.0E-12 ? state.velocity.normalize() : new Vec3(0.0, 1.0, 0.0);
         double turn = Math.min(Math.toRadians(6.0) * 1.0 / 4.0, 0.5625 / Math.max(0.1, state.velocity.length()));
         Vec3 direction = turn(current, wanted, turn);
         double range = plan.target.subtract(plan.origin).horizontalDistance();
         double arcSpeed = Math.min(FlightUnits.metresPerSecond(plan.assignedKmh) * 0.45, Math.max(45.0, Math.sqrt(9.81 * range) * 1.15));
         double speed = nextTime <= 3.0
            ? FlightUnits.metresPerSecond(PeacekeeperFlightProfile.boostSpeedKmh(nextTime, plan.assignedKmh))
            : FlightUnits.metresPerSecond(300.0) + (arcSpeed - FlightUnits.metresPerSecond(300.0)) * smoothstep((nextTime - 3.0) / 7.0);
         Vec3 commandedVelocity = direction.scale(speed);
         thrust = commandedVelocity.subtract(state.velocity).scale(mass / 0.0125).subtract(gravity.scale(mass)).subtract(drag);
      }

      FlightDynamics.State result = FlightDynamics.integrate(
            new FlightDynamics.State(state.position, state.velocity, mass), thrust.add(drag), gravity, 0.0125
         )
         .state();
      Vec3 velocity = result.velocity();
      double ceiling = FlightUnits.metresPerSecond(plan.assignedKmh) * (velocity.y >= 0.0 ? 0.45 : 1.0);
      if (velocity.length() > ceiling) {
         velocity = velocity.normalize().scale(ceiling);
      }

      return new PeacekeeperDynamics.State(state.position.add(state.velocity.add(velocity).scale(0.00625)), velocity, nextTime, terminalSeconds);
   }

   private static Vec3 turn(Vec3 from, Vec3 to, double limit) {
      double angle = Math.acos(Math.max(-1.0, Math.min(1.0, from.dot(to))));
      if (angle <= limit) {
         return to;
      } else {
         Vec3 tangent = to.subtract(from.scale(from.dot(to)));
         if (tangent.lengthSqr() < 1.0E-12) {
            tangent = new Vec3(1.0, 0.0, 0.0);
         }

         return from.scale(Math.cos(limit)).add(tangent.normalize().scale(Math.sin(limit))).normalize();
      }
   }

   private static double smoothstep(double value) {
      double t = Math.max(0.0, Math.min(1.0, value));
      return t * t * (3.0 - 2.0 * t);
   }

   private static double verticalClearanceSeconds() {
      double height = 0.0;
      double speed = 0.0;

      for (int i = 1; i <= 480; i++) {
         double seconds = i * 0.0125;
         double nextSpeed = FlightUnits.metresPerSecond(PeacekeeperFlightProfile.boostSpeedKmh(seconds, MissileKind.PEACEKEEPER.minSpeed));
         height += (speed + nextSpeed) * 0.5 * 0.0125;
         speed = nextSpeed;
         if (height >= CLEARANCE) {
            return seconds;
         }
      }

      throw new IllegalStateException("Peacekeeper must clear its platform within the shared initial speed envelope");
   }

   public static double takeoffLeanAngle(double physicsSeconds) {
      double age = (physicsSeconds - CLEARANCE_EXIT_PHYSICS_SECONDS) / 1.0;
      double leanIn = smoothstep(age / 0.3);
      double leanOut = 1.0 - smoothstep((age - 0.35) / 1.15);
      return Math.toRadians(1.0) * leanIn * leanOut;
   }

   public static PeacekeeperDynamics.Prediction predict(PeacekeeperDynamics.Plan plan) {
      PeacekeeperDynamics.State state = new PeacekeeperDynamics.State(plan.origin, Vec3.ZERO, 0.0);
      double apex = plan.origin.y;

      for (int i = 0; i < 24000.0; i++) {
         PeacekeeperDynamics.State previous = state;
         state = step(plan, state);
         apex = Math.max(apex, state.position.y);
         if (state.seconds > plan.boostTicks * 0.05 && state.velocity.y < 0.0 && state.position.y <= plan.target.y) {
            double fraction = (previous.position.y - plan.target.y) / (previous.position.y - state.position.y);
            return new PeacekeeperDynamics.Prediction(previous.position.lerp(state.position, Math.max(0.0, Math.min(1.0, fraction))), apex, state.seconds);
         }
      }

      return null;
   }

   public static PeacekeeperDynamics.Selection select(Vec3 origin, Vec3 target, double requested) {
      double assigned = PeacekeeperFlightProfile.assignedKmh(requested);
      double range = Math.hypot(target.x - origin.x, target.z - origin.z);
      if (Double.isFinite(range) && !(range < 1.0)) {
         double minimumApex = range >= 2000.0 ? origin.y + 1000.0 : Math.max(origin.y + CLEARANCE, target.y + 10.0);

         for (int duration : new int[]{200, 240, 280}) {
            double low = Math.toRadians(15.0);
            double high = Math.PI / 2;
            PeacekeeperDynamics.Plan best = null;
            PeacekeeperDynamics.Prediction prediction = null;

            for (int iteration = 0; iteration < 32; iteration++) {
               double pitch = (low + high) * 0.5;
               PeacekeeperDynamics.Plan candidate = new PeacekeeperDynamics.Plan(origin, target, assigned, pitch, duration);
               PeacekeeperDynamics.Prediction result = predict(candidate);
               if (result == null) {
                  return new PeacekeeperDynamics.Selection(null, "TARGET_ABOVE_REACHABLE_ARC");
               }

               double actual = Math.hypot(result.impact.x - origin.x, result.impact.z - origin.z);
               if (actual > range) {
                  low = pitch;
               } else {
                  high = pitch;
               }

               if (best == null || result.impact.distanceTo(target) < prediction.impact.distanceTo(target)) {
                  best = candidate;
                  prediction = result;
               }
            }

            if (prediction != null && prediction.impact.distanceTo(target) <= 1.0 && prediction.apex >= minimumApex) {
               return new PeacekeeperDynamics.Selection(best, "");
            }
         }

         return new PeacekeeperDynamics.Selection(null, "NO_REACHABLE_BALLISTIC_PROFILE");
      } else {
         return new PeacekeeperDynamics.Selection(null, "INVALID_RANGE");
      }
   }

   private PeacekeeperDynamics() {
   }

   public record Plan(Vec3 origin, Vec3 target, double assignedKmh, double pitch, int boostTicks) {
   }

   public record Prediction(Vec3 impact, double apex, double seconds) {
   }

   public record Selection(PeacekeeperDynamics.Plan plan, String reason) {
      public boolean valid() {
         return this.plan != null;
      }
   }

   public record State(Vec3 position, Vec3 velocity, double seconds, double terminalSeconds) {
      public State(Vec3 position, Vec3 velocity, double seconds) {
         this(position, velocity, seconds, 0.0);
      }

      public State(Vec3 position, Vec3 velocity, double seconds, double terminalSeconds) {
         if (Double.isFinite(seconds) && !(seconds < 0.0) && Double.isFinite(terminalSeconds) && !(terminalSeconds < 0.0) && !(terminalSeconds > 30.0)) {
            this.position = position;
            this.velocity = velocity;
            this.seconds = seconds;
            this.terminalSeconds = terminalSeconds;
         } else {
            throw new IllegalArgumentException("Invalid ballistic engine age");
         }
      }
   }
}
