package dev.sbw.deepstrike.game;

public final class SmokeVisibility {
   public static final double MAX_SMOKE_DISTANCE = 1500.0;

   public static float opacity(double distance, double heightAboveGround) {
      if (!(distance >= 1500.0) && !(distance < 0.0)) {
         double height = Math.max(0.0, Math.min(1.0, (heightAboveGround - 30.0) / 470.0));
         height = height * height * (3.0 - 2.0 * height);
         double start = 150.0 + 150.0 * height;
         double end = 1500.0;
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

   private SmokeVisibility() {
   }
}
