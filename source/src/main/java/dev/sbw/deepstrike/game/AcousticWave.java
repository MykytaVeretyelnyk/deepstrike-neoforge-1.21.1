package dev.sbw.deepstrike.game;

public final class AcousticWave {
   public static boolean arrived(int ageTicks, double listenerDistance, double soundSpeed) {
      return ageTicks * 0.05 * soundSpeed >= listenerDistance;
   }

   public static int delayTicks(double distance, double soundSpeed) {
      return (int)Math.ceil(Math.max(0.0, distance) / (soundSpeed * 0.05));
   }

   private AcousticWave() {
   }
}
