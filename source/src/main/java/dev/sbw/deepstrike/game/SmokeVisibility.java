package dev.sbw.deepstrike.game;

public final class SmokeVisibility {
   public static final double MAX_SMOKE_DISTANCE = 3000.0;
   // Full opacity out to this range, rising with altitude (thin air, clean backdrop).
   private static final double NEAR_LOW = 400.0;
   private static final double NEAR_HIGH = 900.0;

   public static float opacity(double distance, double heightAboveGround) {
      if (!(distance >= MAX_SMOKE_DISTANCE) && !(distance < 0.0)) {
         double height = Math.max(0.0, Math.min(1.0, (heightAboveGround - 30.0) / 470.0));
         height = height * height * (3.0 - 2.0 * height);
         double start = NEAR_LOW + (NEAR_HIGH - NEAR_LOW) * height;
         double end = MAX_SMOKE_DISTANCE;
         if (distance <= start) {
            return 1.0F;
         } else {
            double t = (distance - start) / (end - start);
            return (float)((1.0 - t) * (1.0 - t));
         }
      } else {
         return 0.0F;
      }
   }

   // Apparent size multiplier that keeps far smoke from shrinking to a hairline; 1.0 inside 250 blocks.
   public static double distanceScale(double distance) {
      return distance <= 250.0 ? 1.0 : 1.0 + Math.min(3.0, (distance - 250.0) / 450.0);
   }

   private SmokeVisibility() {
   }
}
