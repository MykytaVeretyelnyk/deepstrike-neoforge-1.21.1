package dev.sbw.deepstrike.game;

import net.minecraft.world.phys.Vec3;

public final class FlightVisualMotion {
   public static double packetPredictionWeight(int age) {
      return Math.max(0.0, Math.min(1.0, (6 - age) / 4.0));
   }

   public static FlightVisualMotion.Step step(Vec3 position, Vec3 velocity, Vec3 target, Vec3 reference, double seconds, double convergence) {
      if (seconds <= 0.0) {
         return new FlightVisualMotion.Step(position, velocity);
      } else {
         double dt = Math.min(0.1, seconds);
         double omega = 2.0 / Math.max(0.025, convergence);
         Vec3 error = position.subtract(target);
         Vec3 relativeVelocity = velocity.subtract(reference);
         Vec3 impulse = relativeVelocity.add(error.scale(omega));
         double decay = Math.exp(-omega * dt);
         Vec3 nextError = error.add(impulse.scale(dt)).scale(decay);
         Vec3 nextVelocity = reference.add(relativeVelocity.subtract(impulse.scale(omega * dt)).scale(decay));
         Vec3 destination = target.add(reference.scale(dt)).add(nextError);
         Vec3 visible = constrain(destination.subtract(position).scale(1.0 / dt), reference);
         Vec3 desiredVelocity = constrain(nextVelocity, reference);
         double acceleration = 20.0 + 2.0 * Math.max(velocity.length(), reference.length());
         double change = acceleration * dt;
         Vec3 meanChange = visible.subtract(velocity);
         if (!(desiredVelocity.distanceTo(velocity) > change) && !(meanChange.length() > change * 0.5)) {
            return new FlightVisualMotion.Step(position.add(visible.scale(dt)), desiredVelocity);
         } else {
            Vec3 correction = meanChange.scale(2.0 / dt);
            if (correction.length() > acceleration) {
               correction = correction.normalize().scale(acceleration);
            }

            Vec3 endVelocity = velocity.add(correction.scale(dt));
            return new FlightVisualMotion.Step(position.add(velocity.add(endVelocity).scale(dt * 0.5)), endVelocity);
         }
      }
   }

   private static Vec3 constrain(Vec3 velocity, Vec3 reference) {
      double speed = reference.length();
      if (speed < 1.0E-6) {
         return Vec3.ZERO;
      } else {
         Vec3 forward = reference.scale(1.0 / speed);
         double longitudinal = velocity.dot(forward);
         Vec3 lateral = velocity.subtract(forward.scale(longitudinal));
         double lateralLimit = speed * 0.35;
         if (lateral.lengthSqr() > lateralLimit * lateralLimit) {
            lateral = lateral.normalize().scale(lateralLimit);
         }

         longitudinal = Math.max(speed * 0.2, Math.min(speed * 2.0, longitudinal));
         return forward.scale(longitudinal).add(lateral);
      }
   }

   private FlightVisualMotion() {
   }

   public record Step(Vec3 position, Vec3 velocity) {
   }
}
