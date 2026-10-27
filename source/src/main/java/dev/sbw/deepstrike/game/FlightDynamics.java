package dev.sbw.deepstrike.game;

import net.minecraft.world.phys.Vec3;

public final class FlightDynamics {
   public static FlightDynamics.Step integrate(FlightDynamics.State state, Vec3 force, Vec3 gravity, double seconds) {
      if (Double.isFinite(seconds) && !(seconds <= 0.0) && !(seconds > 0.05)) {
         Vec3 acceleration = force.scale(1.0 / state.mass()).add(gravity);
         Vec3 velocity = state.velocity().add(acceleration.scale(seconds));
         Vec3 position = state.position().add(state.velocity().scale(seconds)).add(acceleration.scale(0.5 * seconds * seconds));
         return new FlightDynamics.Step(new FlightDynamics.State(position, velocity, state.mass()), acceleration);
      } else {
         throw new IllegalArgumentException("Physics step must be within one server tick");
      }
   }

   public static Vec3 drag(Vec3 relativeVelocity, double density, double coefficient, double area) {
      double speed = relativeVelocity.length();
      return relativeVelocity.scale(-0.5 * density * coefficient * area * speed);
   }

   public static double approachTickSpeed(double current, double target, double acceleration, double braking) {
      double velocity = current * 20.0;
      double desiredAcceleration = (target - current) * 20.0 / 0.05;
      double limited = Math.max(
         -FlightUnits.tickAccelerationToMetresPerSecondSquared(braking),
         Math.min(FlightUnits.tickAccelerationToMetresPerSecondSquared(acceleration), desiredAcceleration)
      );
      FlightDynamics.State state = new FlightDynamics.State(Vec3.ZERO, new Vec3(velocity, 0.0, 0.0), 1.0);
      return Math.max(0.0, integrate(state, new Vec3(limited, 0.0, 0.0), Vec3.ZERO, 0.05).state().velocity().x / 20.0);
   }

   public static double lateralAcceleration(MissileKind kind, boolean fallback) {
      if (fallback) {
         return 80.0;
      } else {
         return switch (kind) {
            case FP5 -> 80.0;
            case STORM_SHADOW -> 100.0;
            case JAGM -> 160.0;
            case SPIKE_NLOS -> 65.0;
            case PEACEKEEPER -> 45.0;
         };
      }
   }

   public static double angularRate(MissileKind kind, boolean fallback) {
      double degreesPerTick = kind == MissileKind.SPIKE_NLOS ? 5.5 : (kind == MissileKind.JAGM && !fallback ? 6.0 : 3.5);
      return Math.toRadians(degreesPerTick) / 0.05;
   }

   public static double turnLimit(double speedPerTick, double acceleration, double angularLimitRadians) {
      double speed = Math.abs(speedPerTick) * 20.0;
      return Math.min(angularLimitRadians * 1.0, acceleration * 0.05 / Math.max(0.1, speed));
   }

   public static double turnRadius(double speedMetresPerSecond, double acceleration) {
      if (acceleration <= 0.0) {
         throw new IllegalArgumentException("Positive lateral acceleration required");
      } else {
         return speedMetresPerSecond * speedMetresPerSecond / acceleration;
      }
   }

   private FlightDynamics() {
   }

   public record State(Vec3 position, Vec3 velocity, double mass) {
      public State(Vec3 position, Vec3 velocity, double mass) {
         if (Double.isFinite(mass) && !(mass <= 0.0)) {
            this.position = position;
            this.velocity = velocity;
            this.mass = mass;
         } else {
            throw new IllegalArgumentException("Positive finite mass required");
         }
      }
   }

   public record Step(FlightDynamics.State state, Vec3 acceleration) {
   }
}
