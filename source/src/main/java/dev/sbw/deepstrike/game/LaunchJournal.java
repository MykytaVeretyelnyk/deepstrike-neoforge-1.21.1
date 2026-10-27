package dev.sbw.deepstrike.game;

import com.google.gson.JsonParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public final class LaunchJournal extends SavedData {
   private final Map<UUID, List<String>> units = new HashMap<>();
   private final Set<String> recordedEvents = new HashSet<>();
   private final Map<UUID, CompoundTag> blockUnits = new HashMap<>();
   private static final String COMPONENT_PREFIX = "DSM_COMPONENT:";

   public static LaunchJournal get(ServerLevel level) {
      return (LaunchJournal)level.getServer().overworld().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(LaunchJournal::new, (tag, registries) -> LaunchJournal.load(tag)), "deepstrike_unit_journals");
   }

   public static LaunchJournal load(CompoundTag tag) {
      LaunchJournal data = new LaunchJournal();

      for (Tag raw : tag.getList("BlockUnits", 10)) {
         CompoundTag entry = (CompoundTag)raw;
         if (entry.hasUUID("Id")) {
            data.blockUnits.put(entry.getUUID("Id"), entry.copy());
         }
      }

      for (Tag event : tag.getList("RecordedEvents", 8)) {
         data.recordedEvents.add(event.getAsString());
      }

      for (Tag rawx : tag.getList("Units", 10)) {
         CompoundTag t = (CompoundTag)rawx;
         if (t.hasUUID("Id")) {
            List<String> entries = new ArrayList<>();

            for (Tag entry : t.getList("Entries", 8)) {
               entries.add(entry.getAsString());
            }

            data.units.put(t.getUUID("Id"), entries);
         }
      }

      return data;
   }

   public static UUID id(Entity unit) {
      CompoundTag tag = unit.getPersistentData();
      if (!tag.hasUUID("LaunchJournalId")) {
         tag.putUUID("LaunchJournalId", UUID.randomUUID());
      }

      return tag.getUUID("LaunchJournalId");
   }

   public static Component display(String entry) {
      if (entry.startsWith("DSM_COMPONENT:")) {
         try {
            Component value = Serializer.fromJson(entry.substring("DSM_COMPONENT:".length()), net.minecraft.core.RegistryAccess.EMPTY);
            if (value != null) {
               return value;
            }
         } catch (JsonParseException var2) {
         }
      }

      return Component.literal(entry);
   }

   private static Component roundName(String round) {
      for (MissileKind kind : MissileKind.values()) {
         if (kind.id.equalsIgnoreCase(round)) {
            return kind.title();
         }
      }
      return (Component)(switch (round) {
         case "SPIKE NLOS" -> MissileKind.SPIKE_NLOS.title();
         case "Tamir" -> Component.translatable("item.deepstrike.tamir");
         case "PAC-2" -> Component.translatable("item.deepstrike.pac_2");
         case "PAC-3" -> Component.translatable("item.deepstrike.pac_3");
         default -> Component.literal(round);
      });
   }

   public static void record(Entity unit, String round, Vec3 target, String details) {
      record(unit, round, target, Component.literal(details));
   }

   public static void record(Entity unit, String round, Vec3 target, Component details) {
      if (unit.level() instanceof ServerLevel level) {
         record(level, id(unit), unit.position(), round, target, details);
      }
   }

   public static void record(ServerLevel level, UUID id, Vec3 origin, String round, Vec3 target, String details) {
      record(level, id, origin, round, target, Component.literal(details));
   }

   public static void record(ServerLevel level, UUID id, Vec3 origin, String round, Vec3 target, Component details) {
      LaunchJournal data = get(level);
      Component entry = Component.translatable(
         "journal.deepstrike.entry", new Object[]{Instant.now().toString(), roundName(round), coords(origin), coords(target), details}
      );
      data.units.computeIfAbsent(id, key -> new ArrayList<>()).add("DSM_COMPONENT:" + Serializer.toJson(entry, net.minecraft.core.RegistryAccess.EMPTY));
      data.setDirty();
   }

   public static void recordOnce(ServerLevel level, UUID unit, UUID mission, String event, Vec3 origin, String round, Vec3 target, String details) {
      recordOnce(level, unit, mission, event, origin, round, target, Component.literal(details));
   }

   public static void recordOnce(ServerLevel level, UUID unit, UUID mission, String event, Vec3 origin, String round, Vec3 target, Component details) {
      LaunchJournal data = get(level);
      if (data.recordedEvents.add(mission + ":" + event)) {
         record(level, unit, origin, round, target, details);
      }
   }

   public UUID packBlock(UUID id) {
      CompoundTag record = this.blockUnits.computeIfAbsent(id, key -> new CompoundTag());
      UUID token = UUID.randomUUID();
      record.putUUID("Id", id);
      record.putUUID("Transfer", token);
      this.setDirty();
      return token;
   }

   public UUID placeBlock(ServerLevel level, UUID requested, BlockPos pos, UUID token) {
      CompoundTag previous = this.blockUnits.get(requested);
      boolean transferred = previous != null && token != null && previous.hasUUID("Transfer") && previous.getUUID("Transfer").equals(token);
      UUID id = previous != null && !transferred ? UUID.randomUUID() : requested;
      CompoundTag record = new CompoundTag();
      record.putUUID("Id", id);
      record.putString("Dimension", level.dimension().location().toString());
      record.putLong("Position", pos.asLong());
      this.blockUnits.put(id, record);
      this.setDirty();
      return id;
   }

   public void rememberBlock(ServerLevel level, UUID id, BlockPos pos) {
      if (!this.blockUnits.containsKey(id)) {
         CompoundTag record = new CompoundTag();
         record.putUUID("Id", id);
         record.putString("Dimension", level.dimension().location().toString());
         record.putLong("Position", pos.asLong());
         this.blockUnits.put(id, record);
         this.setDirty();
      }
   }

   private static String coords(Vec3 pos) {
      return (int)pos.x + ", " + (int)pos.y + ", " + (int)pos.z;
   }

   public List<String> page(UUID id, int page) {
      List<String> all = this.units.getOrDefault(id, List.of());
      int end = Math.max(0, all.size() - Math.max(0, page) * 50);
      int start = Math.max(0, end - 50);
      List<String> entries = new ArrayList<>(all.subList(start, end));
      Collections.reverse(entries);
      return entries;
   }

   public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      return this.save(tag);
   }

   public CompoundTag save(CompoundTag tag) {
      DeepStrikeData.stamp(tag);
      ListTag blocks = new ListTag();
      this.blockUnits.values().forEach(value -> blocks.add(value.copy()));
      tag.put("BlockUnits", blocks);
      ListTag events = new ListTag();
      this.recordedEvents.forEach(event -> events.add(StringTag.valueOf(event)));
      tag.put("RecordedEvents", events);
      ListTag list = new ListTag();
      this.units.forEach((id, entries) -> {
         CompoundTag t = new CompoundTag();
         t.putUUID("Id", id);
         ListTag values = new ListTag();
         entries.forEach(value -> values.add(StringTag.valueOf(value)));
         t.put("Entries", values);
         list.add(t);
      });
      tag.put("Units", list);
      return tag;
   }
}
