package dev.sbw.deepstrike.game;

import net.minecraft.world.phys.Vec3;

public final class ManpadDynamics {
   public static final int SUBSTEPS = 4;
   public static final double DT = 0.0125;

   public static ManpadDynamics.State step(ManpadDynamics.Profile profile, ManpadDynamics.State state, Vec3 aim, boolean powered) {
      double speed = state.velocity.length();
      Vec3 current = speed > 1.0E-8 ? state.velocity.scale(1.0 / speed) : aim.subtract(state.position).normalize();
      Vec3 wanted = powered ? aim.subtract(state.position).normalize() : current;
      if (wanted.lengthSqr() < 1.0E-12) {
         wanted = current;
      }

      double nextSpeed = powered ? Math.min(168.42105263157893, speed + profile.acceleration * 0.0125) : speed;
      double angle = Math.acos(Math.max(-1.0, Math.min(1.0, current.dot(wanted))));
      double limit = Math.min(profile.angularRate * 0.0125, profile.lateral * 0.0125 / Math.max(0.1, Math.max(speed, nextSpeed)));
      Vec3 direction = wanted;
      if (angle > limit) {
         Vec3 tangent = wanted.subtract(current.scale(current.dot(wanted)));
         if (tangent.lengthSqr() < 1.0E-12) {
            tangent = current.cross(Math.abs(current.y) < 0.9 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0));
         }

         direction = current.scale(Math.cos(limit)).add(tangent.normalize().scale(Math.sin(limit))).normalize();
      }

      Vec3 gravity = new Vec3(0.0, -9.81, 0.0);
      Vec3 drag = FlightDynamics.drag(state.velocity, Atmosphere.density(state.position.y), 0.05, 0.03);
      Vec3 thrust = powered
         ? direction.scale(nextSpeed).subtract(state.velocity).scale(profile.mass / 0.0125).subtract(gravity.scale(profile.mass)).subtract(drag)
         : Vec3.ZERO;
      FlightDynamics.State result = FlightDynamics.integrate(
            new FlightDynamics.State(state.position, state.velocity, profile.mass), thrust.add(drag), gravity, 0.0125
         )
         .state();
      return new ManpadDynamics.State(result.position(), result.velocity());
   }

   private ManpadDynamics() {
   }

   public static enum Profile {
      FIM92(75.0, 95.0, 60.0, 16.0),
      IGLA(60.0, 75.0, 45.0, 18.0);

      public final double acceleration;
      public final double lateral;
      public final double angularRate;
      public final double mass;
      public final double maximumMps = 168.42105263157893;

      private Profile(double acceleration, double lateral, double degreesPerSecond, double mass) {
         this.acceleration = acceleration;
         this.lateral = lateral;
         this.angularRate = Math.toRadians(degreesPerSecond);
         this.mass = mass;
      }
   }

   public record State(Vec3 position, Vec3 velocity) {
   }
}
