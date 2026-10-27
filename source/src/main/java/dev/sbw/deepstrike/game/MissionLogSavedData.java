package dev.sbw.deepstrike.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class MissionLogSavedData extends SavedData {
   private final List<String> entries = new ArrayList<>();

   public static MissionLogSavedData get(ServerLevel level) {
      return (MissionLogSavedData)level.getServer()
         .overworld()
         .getDataStorage()
         .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(MissionLogSavedData::new, (tag, registries) -> MissionLogSavedData.load(tag)), "deepstrike_mission_log");
   }

   public static MissionLogSavedData load(CompoundTag tag) {
      MissionLogSavedData data = new MissionLogSavedData();
      ListTag list = tag.getList("Entries", 8);

      for (int i = 0; i < list.size(); i++) {
         data.entries.add(list.getString(i));
      }

      return data;
   }

   public static void record(ServerLevel level, MissileKind kind, BlockPos pos, String pilot, int points, boolean ballistic) {
      MissionLogSavedData data = get(level);
      data.entries
         .add(
            0,
            Instant.now().toString()
               + "  |  "
               + pilot
               + "  |  "
               + kind.id.toUpperCase()
               + "  |  "
               + pos.getX()
               + ", "
               + pos.getY()
               + ", "
               + pos.getZ()
               + "  |  "
               + points
               + " WP  |  "
               + (ballistic ? "BALLISTIC" : "CRUISE")
         );

      while (data.entries.size() > 40) {
         data.entries.remove(data.entries.size() - 1);
      }

      data.setDirty();
   }

   public List<String> entries() {
      return List.copyOf(this.entries);
   }

   public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      return this.save(tag);
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag list = new ListTag();
      this.entries.forEach(s -> list.add(StringTag.valueOf(s)));
      tag.put("Entries", list);
      return tag;
   }
}
