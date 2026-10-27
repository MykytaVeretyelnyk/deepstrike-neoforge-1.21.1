package dev.sbw.deepstrike.game;

public final class PeacekeeperFlightProfile {
   public static final double ASCENT_FRACTION = 0.45;

   public static double assignedKmh(double requested) {
      return Math.max((double)MissileKind.PEACEKEEPER.minSpeed, Math.min((double)MissileKind.PEACEKEEPER.maxSpeed, requested));
   }

   public static double boostSpeedKmh(double seconds, double assigned) {
      double ceiling = assignedKmh(assigned) * 0.45;
      if (seconds <= 1.0) {
         return blend(0.0, 50.0, seconds);
      } else if (seconds <= 2.0) {
         return blend(50.0, 100.0, seconds - 1.0);
      } else {
         return seconds <= 3.0 ? blend(100.0, 300.0, seconds - 2.0) : blend(300.0, ceiling, (seconds - 3.0) / 7.0);
      }
   }

   private static double blend(double from, double to, double fraction) {
      double t = Math.max(0.0, Math.min(1.0, fraction));
      return from + (to - from) * t * t * t * (10.0 + t * (-15.0 + 6.0 * t));
   }

   private PeacekeeperFlightProfile() {
   }
}
