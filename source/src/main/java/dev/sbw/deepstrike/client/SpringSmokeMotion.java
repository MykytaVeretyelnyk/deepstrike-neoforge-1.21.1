package dev.sbw.deepstrike.client;

final class SpringSmokeMotion {
   static final double AIR_DRAG = 0.92;
   static final double SOURCE_HZ = 30.0;

   static SpringSmokeMotion.Emission emit(double scale, int seed) {
      double az = unit(seed, 0) * Math.PI * 2.0;
      double ay = Math.toRadians(-180.0 + 17.0 * unit(seed, 1));
      double speed = (1.5 + 2.5 * unit(seed, 2)) * scale;
      return new SpringSmokeMotion.Emission(Math.cos(ay) * speed, -Math.cos(az) * Math.sin(ay) * speed, Math.sin(az) * Math.sin(ay) * speed);
   }

   static double velocityDistance(double elapsed, double drag) {
      double n = Math.floor(Math.max(0.0, elapsed));
      double partial = Math.max(0.0, elapsed) - n;
      if (drag == 1.0) {
         return n + partial;
      } else {
         double power = Math.pow(drag, n);
         return (1.0 - power) / (1.0 - drag) + partial * power;
      }
   }

   static double gravityDistance(double elapsed, double drag) {
      double n = Math.floor(Math.max(0.0, elapsed));
      double partial = Math.max(0.0, elapsed) - n;
      if (drag == 1.0) {
         return n * (n - 1.0) * 0.5 + partial * n;
      } else {
         double power = Math.pow(drag, n);
         double sum = (1.0 - power) / (1.0 - drag);
         return drag / (1.0 - drag) * (n - sum) + partial * drag * (1.0 - power) / (1.0 - drag);
      }
   }

   static double angle(int seed, double seconds) {
      return -10.0 + 20.0 * unit(seed, 3) + (-0.05 + 0.1 * unit(seed, 4)) * seconds;
   }

   static int seed(float distance, int trail, int strand) {
      return Float.floatToIntBits(distance) ^ trail ^ strand * -1640531527;
   }

   static double unit(int seed, int channel) {
      int x = seed + channel * -1640531527;
      x ^= x >>> 16;
      x *= 2146121005;
      x ^= x >>> 15;
      x *= -2073254261;
      x ^= x >>> 16;
      return (x & 4294967295L) * 2.3283064E-10F;
   }

   private SpringSmokeMotion() {
   }

   record Emission(double axial, double across, double up) {
   }
}
