package dev.sbw.deepstrike.validation;

import dev.sbw.deepstrike.game.FlightPresentationClock;
import dev.sbw.deepstrike.game.FlightTimeline;
import net.minecraft.world.phys.Vec3;

public final class FlightTimelineChecks {
   private static void require(boolean condition, String message) {
      if (!condition) {
         throw new AssertionError(message);
      }
   }

   public static void main(String[] args) {
      run();
   }

   public static void run() {
      for (double speed : new double[]{0.05, 1.0, 8.0, 22.0, 120.0}) {
         for (int fps : new int[]{24, 30, 60, 144, 240}) {
            FlightTimeline timeline = new FlightTimeline();

            for (int i = 0; i <= 20; i++) {
               timeline.add(i, new Vec3(i * speed, 100.0, 0.0), new Vec3(speed, 0.0, 0.0), -90.0F, 0.0F);
            }

            for (int frame = 0; frame <= fps; frame++) {
               double time = frame * 20.0 / fps;
               FlightTimeline.Pose pose = timeline.sample(time);
               require(pose.position().distanceTo(new Vec3(time * speed, 100.0, 0.0)) < 1.0E-7, "Frame-rate dependent confirmed position");
            }

            require(timeline.sample(1000.0).position().distanceTo(new Vec3(20.0 * speed, 100.0, 0.0)) < 1.0E-9, "Stalled packet extrapolated past endpoint");
            require(timeline.sample(1000.0).velocity().lengthSqr() == 0.0, "Stalled packet retained phantom velocity");
            require(!timeline.add(19L, Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F), "Stale packet rewound path");
         }
      }

      for (Vec3[] points : new Vec3[][]{
         {new Vec3(0.0, 100.0, 0.0), new Vec3(10.0, 100.0, 0.0), new Vec3(10.0, 100.0, 10.0)},
         {new Vec3(0.0, 100.0, 0.0), new Vec3(0.0, 200.0, 0.0), new Vec3(100.0, 0.0, 0.0)},
         {new Vec3(0.0, 100.0, 0.0), new Vec3(20.0, 100.0, 0.0), new Vec3(0.0, 100.0, 0.0)}
      }) {
         FlightTimeline t = new FlightTimeline();

         for (int i = 0; i < points.length; i++) {
            t.add(i * 4, points[i], new Vec3(120.0, 120.0, 120.0), 179 - i * 179, 0.0F);
         }

         for (int i = 0; i <= 800; i++) {
            double time = i * 0.01;
            FlightTimeline.Pose pose = t.sample(time);
            int leg = Math.min(1, (int)(time / 4.0));
            Vec3 a = points[leg];
            Vec3 b = points[leg + 1];
            Vec3 d = b.subtract(a);
            double fraction = pose.position().subtract(a).dot(d) / d.lengthSqr();
            require(fraction >= -1.0E-8 && fraction <= 1.00000001, "Corner extrapolation");
            require(pose.position().distanceTo(a.add(d.scale(fraction))) < 1.0E-8, "Visual spring left authoritative path");
         }
      }

      FlightTimeline wrap = new FlightTimeline();
      wrap.add(10L, Vec3.ZERO, Vec3.ZERO, 179.0F, 0.0F);
      wrap.add(11L, new Vec3(1.0, 0.0, 0.0), Vec3.ZERO, -179.0F, 0.0F);
      require(Math.abs(Math.abs(wrap.sample(10.5).yaw()) - 180.0F) < 1.0E-6, "Yaw crossed long arc");
      FlightPresentationClock clock = new FlightPresentationClock();
      double before = -Double.MAX_VALUE;

      for (int tick = 1; tick < 300; tick++) {
         long nanos = 5000000000L + tick * 50000000L + tick % 5 * 9000000L;
         clock.observe(tick, nanos);
         double now = clock.sample(nanos);
         require(now >= before, "Presentation clock rewound under jitter");
         before = now;
      }

      double held = clock.sample(100000000000L);
      require(held <= 299.0, "Paused presentation ran ahead of server");
      clock.observe(300L, 100050000000L);
      require(clock.sample(100050000000L) <= 300.0, "Resume blocked by future presentation time");
      System.out.println("FLIGHT_TIMELINE PASS speeds=5 frameRates=5 corners/ascent/descent/reversal packet-loss stale-packets yaw-wrap shared-clock");
   }
}
