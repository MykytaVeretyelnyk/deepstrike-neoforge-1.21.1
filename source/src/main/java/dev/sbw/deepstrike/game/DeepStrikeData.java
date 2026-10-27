package dev.sbw.deepstrike.game;

import net.minecraft.nbt.CompoundTag;

public final class DeepStrikeData {
   public static final int VERSION = 1;
   public static final String KEY = "DeepStrikeDataVersion";

   public static void stamp(CompoundTag tag) {
      tag.putInt("DeepStrikeDataVersion", Math.max(1, tag.getInt("DeepStrikeDataVersion")));
   }

   public static CompoundTag migrateLauncher(CompoundTag source) {
      CompoundTag tag = source.copy();
      if (tag.getInt("DeepStrikeDataVersion") < 1) {
         int slot = tag.contains("FiringSlot") ? tag.getInt("FiringSlot") : -1;
         long started = tag.contains("LaunchStarted") ? tag.getLong("LaunchStarted") : -1L;
         if (slot >= 0 && started >= 0L) {
            tag.putLong("LaunchAtTick", started + 100L);
         }
      }

      stamp(tag);
      return tag;
   }

   private DeepStrikeData() {
   }
}
