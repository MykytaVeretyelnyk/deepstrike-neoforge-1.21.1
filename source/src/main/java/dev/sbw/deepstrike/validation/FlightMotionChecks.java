package dev.sbw.deepstrike.validation;

import dev.sbw.deepstrike.game.FlightVisualMotion;
import net.minecraft.world.phys.Vec3;

public final class FlightMotionChecks {
   private static Vec3 simulate(int fps) {
      Vec3 position = new Vec3(-2.0, 0.0, 0.0);
      Vec3 velocity = new Vec3(100.0, 0.0, 0.0);

      for (int frame = 0; frame < fps * 2; frame++) {
         FlightVisualMotion.Step next = FlightVisualMotion.step(
            position, velocity, new Vec3((double)(100 * frame) / fps, 0.0, 0.0), new Vec3(100.0, 0.0, 0.0), 1.0 / fps, 0.13
         );
         if (next.position().x < position.x) {
            throw new AssertionError("Backward motion");
         }

         position = next.position();
         velocity = next.velocity();
      }

      return position;
   }

   public static void main(String[] args) {
      Vec3 sixty = simulate(60);

      for (int fps : new int[]{30, 60, 120, 144, 240}) {
         double difference = sixty.distanceTo(simulate(fps));
         if (difference > 0.002) {
            throw new AssertionError("Frame rate dependent position: " + fps + " " + difference);
         }

         System.out.println("MOTION_FRAME_RATE fps=" + fps + " differenceMetres=" + difference);
      }

      Vec3 position = Vec3.ZERO;
      Vec3 velocity = new Vec3(100.0, 0.0, 0.0);
      double previousSpeed = 100.0;
      double maxDelta = 0.0;

      for (int frame = 0; frame < 1200; frame++) {
         double time = frame / 120.0;
         double jitter = frame / 6 % 2 == 0 ? 0.3 : -0.3;
         Vec3 target = new Vec3(time * 100.0 + jitter, Math.sin(time) * 0.2, 0.0);
         FlightVisualMotion.Step next = FlightVisualMotion.step(position, velocity, target, new Vec3(100.0, 0.0, 0.0), 0.008333333333333333, 0.13);
         double speed = next.velocity().length();
         maxDelta = Math.max(maxDelta, Math.abs(speed - previousSpeed));
         if (next.position().x <= position.x || next.position().distanceTo(position) > 1.1) {
            throw new AssertionError("Discontinuous presentation");
         }

         position = next.position();
         velocity = next.velocity();
         previousSpeed = speed;
      }

      if (maxDelta > 1.5) {
         throw new AssertionError("Packet jitter acceleration spike: " + maxDelta);
      } else {
         System.out.println("MOTION_PACKET_JITTER maxSpeedDeltaPerFrame=" + maxDelta + " frames=1200");
         position = Vec3.ZERO;
         velocity = new Vec3(83.0, 0.0, 0.0);
         double elapsed = 0.0;
         double maxAcceleration = 0.0;

         for (int frame = 0; frame < 600; frame++) {
            double dt = frame % 80 == 40 ? 0.096 : 0.027;
            double offset = frame % 80 < 40 ? 1.5 : -1.5;
            Vec3 target = new Vec3(elapsed * 83.0 + offset, Math.sin(elapsed) * 2.0, 0.0);
            FlightVisualMotion.Step next = FlightVisualMotion.step(position, velocity, target, new Vec3(83.0, 0.0, 0.0), dt, 0.13);
            double acceleration = next.velocity().distanceTo(velocity) / dt;
            maxAcceleration = Math.max(maxAcceleration, acceleration);
            if (acceleration > 260.0 || next.position().x <= position.x) {
               throw new AssertionError("Frame hitch caused abrupt correction: " + acceleration);
            }

            elapsed += dt;
            position = next.position();
            velocity = next.velocity();
         }

         System.out.println("MOTION_FRAME_HITCH maxAccelerationMps2=" + maxAcceleration + " frames=600");
      }
   }
}
