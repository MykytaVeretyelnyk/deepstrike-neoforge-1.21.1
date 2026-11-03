package dev.warmode.game;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent state of the war: teams, banks, scores, settings, headquarters and the running round. */
public final class WarData extends SavedData {
   private static final String NAME = "warmode";

   /** Every tunable value, with its default. Changed with /war set. */
   public static final Map<String, Double> DEFAULTS = new LinkedHashMap<>();

   static {
      DEFAULTS.put("start_balance", 1000.0);
      DEFAULTS.put("balance_cap", 5000.0);
      DEFAULTS.put("regen_base", 4.0);
      DEFAULTS.put("regen_per_player", 0.5);
      DEFAULTS.put("time_limit_min", 30.0);
      DEFAULTS.put("score_limit", 50.0);
      DEFAULTS.put("hq_health", 1000.0);
      DEFAULTS.put("friendly_fire", 0.0);
      DEFAULTS.put("reward_kill_player", 100.0);
      DEFAULTS.put("reward_kill_vehicle", 250.0);
      DEFAULTS.put("reward_intercept_missile", 300.0);
      DEFAULTS.put("reward_intercept_aircraft", 200.0);
      DEFAULTS.put("reward_intercept_ordnance", 400.0);
      DEFAULTS.put("points_kill_player", 1.0);
      DEFAULTS.put("points_kill_vehicle", 3.0);
      DEFAULTS.put("points_intercept_missile", 3.0);
      DEFAULTS.put("points_intercept_aircraft", 2.0);
      DEFAULTS.put("points_intercept_ordnance", 4.0);
   }

   public final Map<UUID, WarTeam> members = new HashMap<>();
   public final double[] bank = new double[2];
   public final int[] score = new int[2];
   public final double[] hqHealth = new double[2];
   public final BlockPos[] hqPos = new BlockPos[2];
   public final ResourceLocation[] hqDimension = new ResourceLocation[2];
   public final Map<String, Double> settings = new HashMap<>();
   public GameMode mode = GameMode.SCORE;
   public boolean running;
   /** Game time at which the timer ends the round; -1 when there is no timer. */
   public long endTick = -1L;

   public double setting(String key) {
      return this.settings.getOrDefault(key, DEFAULTS.getOrDefault(key, 0.0));
   }

   public int settingInt(String key) {
      return (int)Math.round(this.setting(key));
   }

   public WarTeam teamOf(UUID player) {
      return this.members.get(player);
   }

   public int count(WarTeam team) {
      int n = 0;

      for (WarTeam t : this.members.values()) {
         if (t == team) {
            n++;
         }
      }

      return n;
   }

   public ResourceKey<Level> hqLevel(WarTeam team) {
      ResourceLocation id = this.hqDimension[team.ordinal()];
      return id == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, id);
   }

   public static WarData get(MinecraftServer server) {
      ServerLevel overworld = server.overworld();
      return overworld.getDataStorage().computeIfAbsent(new SavedData.Factory<>(WarData::new, WarData::load, null), NAME);
   }

   private static WarData load(CompoundTag tag, HolderLookup.Provider registries) {
      WarData data = new WarData();
      GameMode mode = GameMode.byId(tag.getString("Mode"));
      if (mode != null) {
         data.mode = mode;
      }

      data.running = tag.getBoolean("Running");
      data.endTick = tag.contains("EndTick") ? tag.getLong("EndTick") : -1L;

      for (int i = 0; i < 2; i++) {
         data.bank[i] = tag.getDouble("Bank" + i);
         data.score[i] = tag.getInt("Score" + i);
         data.hqHealth[i] = tag.getDouble("HqHealth" + i);
         if (tag.contains("HqPos" + i)) {
            data.hqPos[i] = NbtUtils.readBlockPos(tag, "HqPos" + i).orElse(null);
            data.hqDimension[i] = ResourceLocation.tryParse(tag.getString("HqDim" + i));
         }
      }

      CompoundTag settings = tag.getCompound("Settings");

      for (String key : settings.getAllKeys()) {
         if (DEFAULTS.containsKey(key)) {
            data.settings.put(key, settings.getDouble(key));
         }
      }

      ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);

      for (int i = 0; i < members.size(); i++) {
         CompoundTag entry = members.getCompound(i);
         WarTeam team = WarTeam.byId(entry.getString("Team"));
         if (team != null && entry.hasUUID("Id")) {
            data.members.put(entry.getUUID("Id"), team);
         }
      }

      return data;
   }

   @Override
   public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
      tag.putString("Mode", this.mode.id);
      tag.putBoolean("Running", this.running);
      if (this.endTick >= 0L) {
         tag.putLong("EndTick", this.endTick);
      }

      for (int i = 0; i < 2; i++) {
         tag.putDouble("Bank" + i, this.bank[i]);
         tag.putInt("Score" + i, this.score[i]);
         tag.putDouble("HqHealth" + i, this.hqHealth[i]);
         if (this.hqPos[i] != null) {
            tag.put("HqPos" + i, NbtUtils.writeBlockPos(this.hqPos[i]));
            tag.putString("HqDim" + i, (this.hqDimension[i] == null ? Level.OVERWORLD.location() : this.hqDimension[i]).toString());
         }
      }

      CompoundTag settings = new CompoundTag();
      this.settings.forEach(settings::putDouble);
      tag.put("Settings", settings);
      ListTag members = new ListTag();
      this.members.forEach((id, team) -> {
         CompoundTag entry = new CompoundTag();
         entry.putUUID("Id", id);
         entry.putString("Team", team.id);
         members.add(entry);
      });
      tag.put("Members", members);
      return tag;
   }
}
