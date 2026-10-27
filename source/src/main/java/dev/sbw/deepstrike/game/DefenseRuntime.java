package dev.sbw.deepstrike.game;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import dev.sbw.deepstrike.vehicle.AnMpq65Entity;
import dev.sbw.deepstrike.vehicle.DeepStrikeVehicleEntity;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;
import dev.sbw.deepstrike.compat.ForgeChunkManager;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import dev.sbw.deepstrike.compat.TickEvent.LevelTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.core.registries.BuiltInRegistries;

public final class DefenseRuntime extends SavedData {
   public static final ModConfigSpec SPEC;
   private static final IntValue MAX_ACTIVE;
   public static final IntValue MAX_COMMAND_UNITS;
   public static final IntValue MAX_COMMAND_DUTY;
   public static final IntValue MAX_COMMAND_SALVO;
   public static final IntValue MAX_COMMAND_CHUNKS;
   private final Map<UUID, CompoundTag> units = new LinkedHashMap<>();
   private final Map<String, Map<UUID, ChunkPos>> leases = new HashMap<>();
   private final Map<UUID, Long> absentSince = new HashMap<>();

   public static DefenseRuntime get(ServerLevel level) {
      return (DefenseRuntime)(level.dimension() == Level.OVERWORLD ? level : level.getServer().overworld())
         .getDataStorage()
         .computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(DefenseRuntime::new, (tag, registries) -> DefenseRuntime.load(tag)), "deepstrike_units");
   }

   public static DefenseRuntime load(CompoundTag tag) {
      DefenseRuntime data = new DefenseRuntime();

      for (Tag raw : tag.getList("Units", 10)) {
         CompoundTag unit = (CompoundTag)raw;
         if (unit.hasUUID("Entity")) {
            data.units.put(unit.getUUID("Entity"), unit.copy());
         }
      }

      return data;
   }

   public static void join(EntityJoinLevelEvent event) {
      if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof DeepStrikeVehicleEntity vehicle) {
         get(level).remember(vehicle);
      }
   }

   public void remember(DeepStrikeVehicleEntity vehicle) {
      if (vehicle.level() instanceof ServerLevel level) {
         UUID var8 = vehicle.getUUID();
         UUID journal = LaunchJournal.id(vehicle);

         for (Entry<UUID, CompoundTag> other : this.units.entrySet()) {
            if (!other.getKey().equals(var8) && other.getValue().hasUUID("Journal") && other.getValue().getUUID("Journal").equals(journal)) {
               journal = UUID.randomUUID();
               vehicle.getPersistentData().putUUID("LaunchJournalId", journal);
               break;
            }
         }

         CompoundTag record = this.units.computeIfAbsent(var8, key -> new CompoundTag());
         DeepStrikeData.stamp(record);
         record.putUUID("Entity", var8);
         record.putUUID("Journal", journal);
         record.putString("Dimension", level.dimension().location().toString());
         record.putLong("Position", vehicle.blockPosition().asLong());
         record.putString("Type", BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString());
         record.putString("State", "LIVE");
         record.remove("TransferToken");
         record.remove("NextWakeTick");
         record.putBoolean(
            "KeepActive",
            vehicle instanceof IronDomeEntity
               || vehicle instanceof AnMpq65Entity
               || vehicle instanceof PatriotTruckEntity patriot && patriot.hasRadarBinding()
               || vehicle instanceof SpikeNlosEntity spike && spike.hasPendingLaunch()
         );
         this.absentSince.remove(var8);
         this.setDirty();
      }
   }

   public void departed(DeepStrikeVehicleEntity entity, RemovalReason reason) {
      if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
         CompoundTag entry = this.units.get(entity.getUUID());
         if (entry != null && !"PACKED".equals(entry.getString("State"))) {
            entry.putString("State", "DESTROYED");
            this.setDirty();
         }

         this.release((ServerLevel)entity.level(), entity.getUUID());
      }
   }

   public UUID pack(DeepStrikeVehicleEntity entity) {
      this.remember(entity);
      CompoundTag record = this.units.get(entity.getUUID());
      UUID token = UUID.randomUUID();
      record.putString("State", "PACKED");
      record.putUUID("TransferToken", token);
      this.setDirty();
      return token;
   }

   public boolean canTransfer(UUID entity, UUID token) {
      CompoundTag record = this.units.get(entity);
      return record != null
         && "PACKED".equals(record.getString("State"))
         && record.hasUUID("TransferToken")
         && record.getUUID("TransferToken").equals(token);
   }

   public void restoreFailedTransfer(ServerLevel level, UUID entity, UUID token) {
      if (level.getEntity(entity) == null) {
         CompoundTag record = this.units.get(entity);
         if (record != null) {
            record.putString("State", "PACKED");
            record.putUUID("TransferToken", token);
            record.remove("NextWakeTick");
            this.absentSince.remove(entity);
            this.release(level, entity);
            this.setDirty();
         }
      }
   }

   public boolean destroyed(UUID id) {
      CompoundTag record = this.units.get(id);
      return record != null && "DESTROYED".equals(record.getString("State"));
   }

   public boolean packed(UUID id) {
      CompoundTag record = this.units.get(id);
      return record != null && "PACKED".equals(record.getString("State"));
   }

   public boolean known(UUID id) {
      return this.units.containsKey(id);
   }

   public BlockPos position(UUID id) {
      CompoundTag record = this.units.get(id);
      return record == null ? null : BlockPos.of(record.getLong("Position"));
   }

   public void wake(ServerLevel level, UUID id, BlockPos fallback) {
      CompoundTag entry = this.units.get(id);
      if (entry == null) {
         entry = new CompoundTag();
         entry.putUUID("Entity", id);
         entry.putLong("Position", fallback.asLong());
         entry.putString("Dimension", level.dimension().location().toString());
         entry.putString("State", "MISSING");
         this.units.put(id, entry);
      }

      if (!this.destroyed(id) && !this.packed(id) && level.dimension().location().toString().equals(entry.getString("Dimension"))) {
         entry.putLong("RequestedUntil", level.getGameTime() + 100L);
         this.setDirty();
      }
   }

   private void release(ServerLevel level, UUID id) {
      Map<UUID, ChunkPos> active = this.leases.get(level.dimension().location().toString());
      if (active != null) {
         ChunkPos chunk = active.remove(id);
         if (chunk != null) {
            ForgeChunkManager.forceChunk(level, "deepstrike", id, chunk.x, chunk.z, false, true);
         }
      }
   }

   public static void validateTickets(ServerLevel level, TicketHelper helper) {
      DefenseRuntime data = get(level);
      ActiveFlights flights = ActiveFlights.get(level);

      for (BlockPos origin : helper.getBlockTickets().keySet()) {
         if (flights.hasQueuedLaunch(level, origin)) {
            level.getChunk(origin.getX() >> 4, origin.getZ() >> 4);
         }
      }

      for (Entry<UUID, net.neoforged.neoforge.common.world.chunk.TicketSet> ticket : helper.getEntityTickets().entrySet()) {
         UUID id = ticket.getKey();
         if (!data.units.containsKey(id) && !ActiveFlights.get(level).containsFlight(id)) {
            LongSet chunks = ticket.getValue().ticking();
            if (!chunks.isEmpty()) {
               ChunkPos pos = new ChunkPos(chunks.iterator().nextLong());
               CompoundTag entry = new CompoundTag();
               entry.putUUID("Entity", id);
               entry.putString("Dimension", level.dimension().location().toString());
               entry.putLong("Position", pos.getMiddleBlockPosition(64).asLong());
               entry.putString("State", "LIVE");
               entry.putBoolean("KeepActive", true);
               data.units.put(id, entry);
               data.setDirty();
            }
         }

         helper.removeAllTickets(id);
      }
   }

   public static void tick(LevelTickEvent event) {
      if (event.phase == Phase.START && event.level instanceof ServerLevel level && level.getGameTime() % 20L == 0L) {
         DefenseRuntime data = get(level);
         String dimension = level.dimension().location().toString();
         long now = level.getGameTime();
         Map<UUID, ChunkPos> active = data.leases.computeIfAbsent(dimension, key -> new LinkedHashMap<>());
         Map<UUID, ChunkPos> wanted = new LinkedHashMap<>();

         for (Entry<UUID, CompoundTag> unit : data.units.entrySet()) {
            CompoundTag record = unit.getValue();
            if (dimension.equals(record.getString("Dimension")) && !data.destroyed(unit.getKey()) && !data.packed(unit.getKey())) {
               boolean requested = record.getLong("RequestedUntil") > now;
               boolean retry = record.getBoolean("KeepActive") && "MISSING".equals(record.getString("State")) && now >= record.getLong("NextWakeTick");
               if (requested || retry || "LIVE".equals(record.getString("State")) && record.getBoolean("KeepActive")) {
                  Entity entity = level.getEntity(unit.getKey());
                  BlockPos pos = entity != null ? entity.blockPosition() : BlockPos.of(record.getLong("Position"));
                  if (wanted.size() < (Integer)MAX_ACTIVE.get()) {
                     wanted.put(unit.getKey(), new ChunkPos(pos));
                  }
               }
            }
         }

         for (UUID id : List.copyOf(active.keySet())) {
            if (!Objects.equals(active.get(id), wanted.get(id))) {
               data.release(level, id);
            }
         }

         for (Entry<UUID, ChunkPos> entry : wanted.entrySet()) {
            UUID idx = entry.getKey();
            ChunkPos chunk = entry.getValue();
            if (!chunk.equals(active.get(idx))) {
               ForgeChunkManager.forceChunk(level, "deepstrike", idx, chunk.x, chunk.z, true, true);
               active.put(idx, chunk);
            }

            if (level.getEntity(idx) instanceof DeepStrikeVehicleEntity vehicle) {
               data.remember(vehicle);
            } else if (now - data.absentSince.computeIfAbsent(idx, key -> now) > 200L) {
               CompoundTag record = data.units.get(idx);
               record.putString("State", "MISSING");
               record.putLong("RequestedUntil", 0L);
               record.putLong("NextWakeTick", now + 200L);
               data.release(level, idx);
               data.absentSince.remove(idx);
               data.setDirty();
            }
         }
      }
   }

   public static void commands(RegisterCommandsEvent event) {
      event.getDispatcher()
         .register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("dsm").requires(source -> source.hasPermission(2)))
               .then(
                  Commands.literal("status")
                     .executes(
                        context -> {
                           ServerLevel level = ((CommandSourceStack)context.getSource()).getLevel();
                           DefenseRuntime data = get(level);
                           String dim = level.dimension().location().toString();
                           long requested = data.units
                              .values()
                              .stream()
                              .filter(
                                 record -> dim.equals(record.getString("Dimension"))
                                    && "LIVE".equals(record.getString("State"))
                                    && record.getBoolean("KeepActive")
                              )
                              .count();
                           int active = data.leases.getOrDefault(dim, Map.of()).size();
                           long unavailable = data.units
                              .values()
                              .stream()
                              .filter(
                                 record -> dim.equals(record.getString("Dimension"))
                                    && "MISSING".equals(record.getString("State"))
                                    && record.getBoolean("KeepActive")
                              )
                              .count();
                           ((CommandSourceStack)context.getSource())
                              .sendSuccess(
                                 () -> Component.translatable(
                                    "message.deepstrike.runtime.status",
                                    new Object[]{dim, active, MAX_ACTIVE.get(), Math.max(0L, requested - active), unavailable}
                                 ),
                                 false
                              );
                           return active;
                        }
                     )
               )
         );
   }

   public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      return this.save(tag);
   }

   public CompoundTag save(CompoundTag tag) {
      DeepStrikeData.stamp(tag);
      ListTag list = new ListTag();
      this.units.values().forEach(record -> list.add(record.copy()));
      tag.put("Units", list);
      return tag;
   }

   static {
      Builder config = new Builder();
      MAX_ACTIVE = config.comment(
            new String[]{
               "Maximum autonomous DSM units holding a ticking chunk in each dimension. Extra units keep their data and work when naturally loaded.",
               "Use /dsm status to inspect the active and waiting counts. Owners need not be online."
            }
         )
         .defineInRange("maxActiveDefenseSystemsPerDimension", 64, 0, 1024);
      MAX_COMMAND_UNITS = config.defineInRange("maxCommandUnitsPerNetwork", 32, 1, 256);
      MAX_COMMAND_DUTY = config.defineInRange("maxCommandDutyPerNetwork", 8, 1, 64);
      MAX_COMMAND_SALVO = config.defineInRange("maxCommandMissilesPerSalvo", 8, 1, 64);
      MAX_COMMAND_CHUNKS = config.defineInRange("maxCommandChunksPerServer", 64, 1, 256);
      SPEC = config.build();
   }
}
