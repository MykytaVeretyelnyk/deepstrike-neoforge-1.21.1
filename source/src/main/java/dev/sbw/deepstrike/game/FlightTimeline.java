package dev.sbw.deepstrike.game;

import java.util.ArrayDeque;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class FlightTimeline {
   private final ArrayDeque<FlightTimeline.Sample> samples = new ArrayDeque<>();

   public boolean add(long tick, Vec3 position, Vec3 velocity, float yaw, float pitch) {
      if (!Double.isFinite(position.lengthSqr()) || !Double.isFinite(velocity.lengthSqr()) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
         return false;
      } else if (!this.samples.isEmpty() && tick <= this.samples.getLast().tick()) {
         return false;
      } else {
         this.samples.addLast(new FlightTimeline.Sample(tick, new FlightTimeline.Pose(position, velocity, yaw, pitch)));

         while (this.samples.size() > 32) {
            this.samples.removeFirst();
         }

         return true;
      }
   }

   public FlightTimeline.Pose sample(double time) {
      if (this.samples.isEmpty()) {
         return null;
      } else {
         FlightTimeline.Sample before = this.samples.getFirst();

         for (FlightTimeline.Sample after : this.samples) {
            if (after.tick() > time) {
               if (after == before) {
                  return stopped(before.pose());
               }

               double duration = after.tick() - before.tick();
               double t = Mth.clamp((time - before.tick()) / duration, 0.0, 1.0);
               FlightTimeline.Pose a = before.pose();
               FlightTimeline.Pose b = after.pose();
               return new FlightTimeline.Pose(
                  a.position().lerp(b.position(), t),
                  b.position().subtract(a.position()).scale(1.0 / duration),
                  Mth.rotLerp((float)t, a.yaw(), b.yaw()),
                  Mth.lerp((float)t, a.pitch(), b.pitch())
               );
            }

            before = after;
         }

         return stopped(before.pose());
      }
   }

   private static FlightTimeline.Pose stopped(FlightTimeline.Pose p) {
      return new FlightTimeline.Pose(p.position(), Vec3.ZERO, p.yaw(), p.pitch());
   }

   public double distanceToPath(Vec3 position) {
      if (this.samples.isEmpty()) {
         return Double.POSITIVE_INFINITY;
      } else {
         Vec3 before = this.samples.getFirst().pose().position();
         double nearest = position.distanceTo(before);

         for (FlightTimeline.Sample sample : this.samples) {
            Vec3 after = sample.pose().position();
            Vec3 leg = after.subtract(before);
            double t = leg.lengthSqr() < 1.0E-15 ? 0.0 : Mth.clamp(position.subtract(before).dot(leg) / leg.lengthSqr(), 0.0, 1.0);
            nearest = Math.min(nearest, position.distanceTo(before.add(leg.scale(t))));
            before = after;
         }

         return nearest;
      }
   }

   public boolean isEmpty() {
      return this.samples.isEmpty();
   }

   public long newestTick() {
      return this.samples.isEmpty() ? Long.MIN_VALUE : this.samples.getLast().tick();
   }

   public record Pose(Vec3 position, Vec3 velocity, float yaw, float pitch) {
   }

   private record Sample(long tick, FlightTimeline.Pose pose) {
   }
}
