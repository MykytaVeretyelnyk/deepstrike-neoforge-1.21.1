package dev.sbw.deepstrike.validation;

import dev.sbw.deepstrike.game.SmokeVisibility;

public final class SmokeVisibilityChecks {
   public static void main(String[] args) {
      for (double height : new double[]{0.0, 30.0, 100.0, 250.0, 500.0, 1500.0}) {
         float previous = 1.0F;

         for (int distance = 0; distance <= 3000; distance += 10) {
            float value = SmokeVisibility.opacity(distance, height);
            if (value < 0.0F || value > previous) {
               throw new AssertionError("Non-monotonic distance fade: d=" + distance + " h=" + height + " v=" + value + " prev=" + previous);
            }

            previous = value;
         }
      }

      if (SmokeVisibility.opacity(SmokeVisibility.MAX_SMOKE_DISTANCE, 20.0) != 0.0F) {
         throw new AssertionError("Low launch smoke visible at the cutoff");
      } else if (SmokeVisibility.opacity(SmokeVisibility.MAX_SMOKE_DISTANCE, 500.0) != 0.0F) {
         throw new AssertionError("High wake visible at the cutoff");
      } else if (SmokeVisibility.opacity(4500.0, 500.0) != 0.0F) {
         throw new AssertionError("High wake visible far past the cutoff");
      } else if (SmokeVisibility.opacity(1000.0, 500.0) < 0.5F) {
         throw new AssertionError("Mid-range smoke too faint");
      } else if (SmokeVisibility.opacity(300.0, 0.0) != 1.0F) {
         throw new AssertionError("Near smoke should be fully opaque");
      } else if (SmokeVisibility.distanceScale(100.0) != 1.0 || SmokeVisibility.distanceScale(2000.0) <= 1.0 || SmokeVisibility.distanceScale(9000.0) > 4.0) {
         throw new AssertionError("Distance scale out of range");
      } else {
         float previous = 0.0F;

         for (int height = 0; height <= 1000; height++) {
            float value = SmokeVisibility.opacity(800.0, height);
            if (value < previous) {
               throw new AssertionError("Altitude reduces visibility");
            }

            previous = value;
         }

         System.out.println("SMOKE_VISIBILITY_PASS distanceMonotonic=true heightMonotonic=true cutoff3000=true");
      }
   }
}
