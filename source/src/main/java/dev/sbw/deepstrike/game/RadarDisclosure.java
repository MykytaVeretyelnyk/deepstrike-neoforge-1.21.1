package dev.sbw.deepstrike.game;

import java.util.UUID;

public final class RadarDisclosure {
   private static long mix(long value) {
      value = (value ^ value >>> 30) * -4658895280553007687L;
      value = (value ^ value >>> 27) * -7723592293110705685L;
      return value ^ value >>> 31;
   }

   private static boolean roll(UUID id, long salt, int percent) {
      return Long.remainderUnsigned(mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23) ^ salt), 100L) < percent;
   }

   public static boolean modelKnown(UUID id) {
      return roll(id, 7507247309642731089L, 2);
   }

   public static boolean impactKnown(UUID id) {
      return roll(id, 3569917864818837436L, 30);
   }

   public static int model(UUID id, int actual) {
      return modelKnown(id) ? actual : -1;
   }

   private RadarDisclosure() {
   }
}
