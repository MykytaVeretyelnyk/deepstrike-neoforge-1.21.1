package dev.sbw.deepstrike.game;

public enum FlightMode {
   CRUISE,
   BALLISTIC;

   public static FlightMode forMissile(MissileKind kind, int index) {
      return index == 1 && kind != MissileKind.FP5 ? BALLISTIC : CRUISE;
   }
}
