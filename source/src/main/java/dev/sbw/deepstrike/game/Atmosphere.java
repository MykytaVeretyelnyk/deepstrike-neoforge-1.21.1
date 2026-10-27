package dev.sbw.deepstrike.game;

public final class Atmosphere {
   public static final double SEA_LEVEL_Y = 63.0;
   private static final double GAS_CONSTANT = 287.05;
   private static final double GRAVITY = 9.80665;

   private static double altitude(double worldY) {
      return Math.max(-1000.0, Math.min(86000.0, worldY - 63.0));
   }

   public static double temperature(double worldY) {
      return 288.15 - 0.0065 * Math.min(11000.0, altitude(worldY));
   }

   public static double density(double worldY) {
      double h = altitude(worldY);
      double temperature = temperature(worldY);
      double pressure = h <= 11000.0
         ? 101325.0 * Math.pow(temperature / 288.15, 5.255932362359814)
         : 22632.04 * Math.exp(-9.80665 * (h - 11000.0) / 62189.38250000001);
      return pressure / (287.05 * temperature);
   }

   public static double soundSpeed(double worldY) {
      return Math.sqrt(401.87 * temperature(worldY));
   }

   private Atmosphere() {
   }
}
