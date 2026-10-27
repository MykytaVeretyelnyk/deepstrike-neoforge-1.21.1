package dev.sbw.deepstrike.game;

public final class FlightUnits {
   public static final double TICKS_PER_SECOND = 20.0;
   public static final double TICK_SECONDS = 0.05;
   public static final double FLIGHT_TIME_SCALE = 1.0;
   public static final double PHYSICS_TICK_SECONDS = 0.05;
   public static final double PHYSICS_TICKS_PER_SECOND = 20.0;

   public static double metresPerSecond(double kmh) {
      return kmh / 3.6;
   }

   public static double blocksPerTick(double kmh) {
      return metresPerSecond(kmh) * 0.05;
   }

   public static double kilometresPerHour(double blocksPerTick) {
      return blocksPerTick * 20.0 * 3.6;
   }

   public static double tickAccelerationToMetresPerSecondSquared(double acceleration) {
      return acceleration * 20.0 * 20.0;
   }

   private FlightUnits() {
   }
}
