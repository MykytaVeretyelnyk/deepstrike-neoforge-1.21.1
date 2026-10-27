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

      if (SmokeVisibility.opacity(1500.0, 20.0) != 0.0F) {
         throw new AssertionError("Low launch smoke visible at 1.5km cutoff");
      } else if (SmokeVisibility.opacity(1500.0, 500.0) != 0.0F) {
         throw new AssertionError("High wake visible at 1.5km cutoff");
      } else if (SmokeVisibility.opacity(3000.0, 500.0) != 0.0F) {
         throw new AssertionError("High wake visible at 3km");
      } else if (SmokeVisibility.opacity(3000.0, 20.0) != 0.0F) {
         throw new AssertionError("Low launch smoke visible at 3km");
      } else if (SmokeVisibility.opacity(500.0, 500.0) < 0.3F) {
         throw new AssertionError("Mid-range smoke too faint");
      } else {
         float previous = 0.0F;

         for (int height = 0; height <= 1000; height++) {
            float value = SmokeVisibility.opacity(800.0, height);
            if (value < previous) {
               throw new AssertionError("Altitude reduces visibility");
            }

            previous = value;
         }

         System.out.println("SMOKE_VISIBILITY_PASS distanceMonotonic=true heightMonotonic=true cutoff1500=true zero3km=true");
      }
   }
}
