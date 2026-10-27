package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.vehicle.DeepStrikeVehicleEntity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.LevelTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class ActiveFlights extends SavedData {
   private final Map<UUID, Entity> flights = new LinkedHashMap<>();
   private final Map<UUID, CompoundTag> pending = new LinkedHashMap<>();
   private final Map<UUID, CompoundTag> missions = new LinkedHashMap<>();
   private final Set<UUID> countdowns = new LinkedHashSet<>();
   private final Set<UUID> completed = new LinkedHashSet<>();
   private long retryAt = Long.MIN_VALUE;

   public Collection<Entity> radarEntities() {
      return List.copyOf(this.flights.values());
   }

   public static ActiveFlights get(ServerLevel level) {
      return (ActiveFlights)level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(ActiveFlights::new, (tag, registries) -> ActiveFlights.load(tag)), "deepstrike_active_flights");
   }

   public static ActiveFlights load(CompoundTag tag) {
      ActiveFlights data = new ActiveFlights();

      for (Tag raw : tag.getList("Flights", 10)) {
         CompoundTag flight = (CompoundTag)raw;
         if (flight.hasUUID("UUID")) {
            data.pending.put(flight.getUUID("UUID"), flight.copy());
         }
      }

      for (Tag rawx : tag.getList("Completed", 10)) {
         CompoundTag entry = (CompoundTag)rawx;
         if (entry.hasUUID("UUID")) {
            data.completed.add(entry.getUUID("UUID"));
         }
      }

      for (Tag rawxx : tag.getList("Missions", 10)) {
         CompoundTag entry = (CompoundTag)rawxx;
         if (entry.hasUUID("MissionId")) {
            UUID id = entry.getUUID("MissionId");
            data.missions.put(id, entry.copy());
            if (!entry.hasUUID("FlightId") && !entry.contains("Outcome") && entry.contains("Origin")) {
               data.countdowns.add(id);
            }
         }
      }

      data.pending.keySet().removeAll(data.completed);
      return data;
   }

   public static boolean isAuthoritative(Entity entity) {
      return entity.level() instanceof ServerLevel server && get(server).flights.get(entity.getUUID()) == entity;
   }

   public boolean containsFlight(UUID id) {
      return this.flights.containsKey(id) || this.pending.containsKey(id) || this.completed.contains(id);
   }

   public boolean hasActiveFlight(UUID id) {
      return !this.completed.contains(id) && (this.flights.containsKey(id) || this.pending.containsKey(id));
   }

   public CompoundTag report(UUID id) {
      CompoundTag record = this.missions.get(id);
      return record == null ? new CompoundTag() : record.copy();
   }

   public boolean wasLaunched(UUID id) {
      CompoundTag record = this.missions.get(id);
      return record != null && record.hasUUID("FlightId");
   }

   public UUID launchedFlight(UUID mission) {
      CompoundTag record = this.missions.get(mission);
      return record != null && record.hasUUID("FlightId") ? record.getUUID("FlightId") : null;
   }

   public boolean knowsMission(UUID id) {
      return id != null && this.missions.containsKey(id);
   }

   public boolean hasQueuedLaunch(ServerLevel level, BlockPos origin) {
      String dimension = level.dimension().location().toString();
      return this.countdowns
         .stream()
         .map(this.missions::get)
         .filter(Objects::nonNull)
         .anyMatch(
            record -> !record.hasUUID("FlightId")
               && !record.contains("Outcome")
               && record.contains("Origin")
               && record.getLong("Origin") == origin.asLong()
               && dimension.equals(record.getString("Dimension"))
         );
   }

   public boolean acceptedBy(UUID id, UUID unit, UUID owner) {
      CompoundTag entry = this.missions.get(id);
      return entry != null
         && entry.hasUUID("UnitId")
         && entry.getUUID("UnitId").equals(unit)
         && entry.hasUUID("Owner")
         && entry.getUUID("Owner").equals(owner);
   }

   public boolean acceptLaunch(ServerLevel level, UUID id, UUID unit, UUID owner, BlockPos origin, Vec3 target, String kind, long launchAt) {
      if (id != null && !this.missions.containsKey(id)) {
         CompoundTag entry = new CompoundTag();
         DeepStrikeData.stamp(entry);
         entry.putUUID("MissionId", id);
         entry.putUUID("UnitId", unit);
         entry.putUUID("Owner", owner);
         entry.putString("Dimension", level.dimension().location().toString());
         entry.putLong("Origin", origin.asLong());
         entry.putString("Kind", kind);
         entry.putDouble("TargetX", target.x);
         entry.putDouble("TargetY", target.y);
         entry.putDouble("TargetZ", target.z);
         entry.putLong("AcceptedAtTick", level.getGameTime());
         entry.putLong("LaunchAtTick", launchAt);
         entry.putString("Phase", "COUNTDOWN");
         this.missions.put(id, entry);
         this.countdowns.add(id);
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public void cancelLaunch(UUID id) {
      CompoundTag entry = this.missions.get(id);
      if (entry != null && !entry.hasUUID("FlightId") && !entry.contains("Outcome")) {
         entry.putString("Outcome", "CANCELLED");
         entry.putString("Phase", "FINISHED");
         this.setDirty();
         this.countdowns.remove(id);
      }
   }

   private static UUID missionId(Entity entity) {
      if (entity instanceof GuidedMissileEntity missile) {
         if (missile.missionId() == null) {
            missile.setMissionId(entity.getUUID());
         }

         return missile.missionId();
      } else {
         return entity.getUUID();
      }
   }

   public static void onJoin(EntityJoinLevelEvent event) {
      if (event.getLevel() instanceof ServerLevel level) {
         Entity missile = event.getEntity();
         if (missile instanceof GuidedMissileEntity || missile instanceof PatriotInterceptorEntity) {
            ActiveFlights data = get(level);
            UUID id = missile.getUUID();
            Entity existing = data.flights.get(id);
            if (!data.completed.contains(id) && (existing == null || existing == missile || !existing.isAlive())) {
               CompoundTag authoritative = data.pending.get(id);
               if (authoritative != null) {
                  missile.load(authoritative.copy());
               }

               if (missile instanceof GuidedMissileEntity guided
                  && guided.kind() == MissileKind.PEACEKEEPER
                  && guided.missionId() == null
                  && !guided.claimPeacekeeperFlight(level)) {
                  event.setCanceled(true);
               } else {
                  UUID mission = missionId(missile);
                  CompoundTag record = data.missions.computeIfAbsent(mission, key -> {
                     CompoundTag entry = new CompoundTag();
                     entry.putUUID("MissionId", key);
                     return entry;
                  });
                  boolean sameFlight = record.hasUUID("FlightId") && id.equals(record.getUUID("FlightId"));
                  boolean wreck = missile instanceof GuidedMissileEntity guided
                     && guided.isIntercepted()
                     && "INTERCEPTED".equals(record.getString("Outcome"))
                     && sameFlight;
                  if ((!record.hasUUID("FlightId") || sameFlight) && (!record.contains("Outcome") || wreck)) {
                     record.putUUID("FlightId", id);
                     data.countdowns.remove(mission);
                     if (!record.hasUUID("UnitId")
                        && missile instanceof PatriotInterceptorEntity interceptor
                        && interceptor.defenseSource() != null
                        && level.getEntity(interceptor.defenseSource()) instanceof DeepStrikeVehicleEntity launcher) {
                        record.putUUID("UnitId", LaunchJournal.id(launcher));
                        record.putLong("Origin", launcher.blockPosition().asLong());
                        record.putString("Kind", interceptor.warhead() == 3 ? "Tamir" : (interceptor.warhead() == 0 ? "PAC-2" : "PAC-3"));
                     }

                     record.putString("Dimension", level.dimension().location().toString());
                     if (!record.contains("LaunchTick")) {
                        record.putLong("LaunchTick", level.getGameTime());
                     }

                     if (!record.contains("Outcome")) {
                        record.putString("Phase", "LAUNCHING");
                     }

                     DeepStrikeData.stamp(record);
                     data.pending.remove(id);
                     data.flights.put(id, missile);
                     data.setDirty();
                  } else {
                     if (authoritative != null) {
                        data.pending.remove(id);
                        data.completed.add(id);
                        data.setDirty();
                     }

                     if (missile instanceof GuidedMissileEntity guidedx) {
                        guidedx.releasePeacekeeperFlight(level);
                     }

                     event.setCanceled(true);
                  }
               }
            } else {
               event.setCanceled(true);
            }
         }
      }
   }

   public static boolean resolve(Entity missile, String outcome) {
      if (missile.level() instanceof ServerLevel level) {
         ActiveFlights data = get(level);
         if (data.flights.get(missile.getUUID()) == missile && !data.completed.contains(missile.getUUID())) {
            CompoundTag record = data.missions.get(missionId(missile));
            if (record != null && !record.contains("Outcome")) {
               record.putString("Outcome", outcome);
               record.putString("Phase", outcome);
               record.putLong("ResolvedAtTick", level.getGameTime());
               Vec3 contact = missile instanceof GuidedMissileEntity guided ? guided.impactPosition() : missile.position();
               record.putDouble("ResultX", contact.x);
               record.putDouble("ResultY", contact.y);
               record.putDouble("ResultZ", contact.z);
               if (record.hasUUID("UnitId")) {
                  LaunchJournal.recordOnce(
                     level,
                     record.getUUID("UnitId"),
                     missionId(missile),
                     "result",
                     Vec3.atCenterOf(BlockPos.of(record.getLong("Origin"))),
                     record.getString("Kind"),
                     contact,
                     Component.translatable("journal.deepstrike.outcome." + outcome.toLowerCase(Locale.ROOT))
                  );
               }

               data.setDirty();
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void tick(LevelTickEvent event) {
      if (event.phase == Phase.END && event.level instanceof ServerLevel level) {
         ActiveFlights data = get(level);

         for (UUID id : List.copyOf(data.countdowns)) {
            CompoundTag record = data.missions.get(id);
            if (record != null && !record.hasUUID("FlightId") && !record.contains("Outcome")) {
               if (level.dimension().location().toString().equals(record.getString("Dimension"))) {
                  BlockPos origin = BlockPos.of(record.getLong("Origin"));
                  LevelChunk chunk = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
                  if (chunk != null && chunk.getBlockEntity(origin) instanceof LauncherBlockEntity launcher) {
                     LauncherBlockEntity.serverTick(level, origin, launcher.getBlockState(), launcher);
                  }
               }
            } else {
               data.countdowns.remove(id);
            }
         }

         if (!data.pending.isEmpty() && level.getGameTime() >= data.retryAt) {
            data.retryAt = level.getGameTime() + 20L;

            for (CompoundTag tag : List.copyOf(data.pending.values())) {
               UUID idx = tag.getUUID("UUID");
               if (data.completed.contains(idx)) {
                  data.pending.remove(idx);
                  data.setDirty();
               } else if (level.getEntity(idx) == null) {
                  Entity missile = tag.getBoolean("ManagedInterceptor")
                     ? ((EntityType)BFEntities.PATRIOT_INTERCEPTOR.get()).create(level)
                     : ((EntityType)BFEntities.GUIDED_MISSILE.get()).create(level);
                  if (missile != null) {
                     missile.load(tag.copy());
                     level.addFreshEntity(missile);
                  }
               }
            }
         }

         for (Entity missile : List.copyOf(data.flights.values())) {
            if (!missile.isAlive()) {
               if (missile.getRemovalReason() != RemovalReason.UNLOADED_TO_CHUNK && missile.getRemovalReason() != RemovalReason.UNLOADED_WITH_PLAYER) {
                  removed(missile);
               } else {
                  data.pending.put(missile.getUUID(), snapshot(missile));
                  data.flights.remove(missile.getUUID());
                  data.setDirty();
               }
            } else {
               boolean ticked = missile instanceof GuidedMissileEntity guided
                  ? guided.tickedAt(level.getGameTime())
                  : ((PatriotInterceptorEntity)missile).tickedAt(level.getGameTime());
               if (!ticked) {
                  level.tickNonPassenger(missile);
               }

               if (missile.isAlive()) {
                  CompoundTag record = data.missions.get(missionId(missile));
                  if (record != null && !record.contains("Outcome")) {
                     record.putString("Phase", missile instanceof GuidedMissileEntity guidedx ? guidedx.flightPhase() : "BOOST");
                  }
               }
            }
         }

         if (!data.flights.isEmpty()) {
            data.setDirty();
         }
      }
   }

   public static void removed(Entity missile) {
      if (missile.level() instanceof ServerLevel level) {
         ActiveFlights data = get(level);
         if (data.flights.get(missile.getUUID()) == missile) {
            resolve(missile, "REMOVED");
            data.flights.remove(missile.getUUID());
            data.pending.remove(missile.getUUID());
            data.completed.add(missile.getUUID());
            CompoundTag record = data.missions.get(missionId(missile));
            if (record != null) {
               record.putString("Phase", "FINISHED");
            }

            data.setDirty();
         }
      }
   }

   private static CompoundTag snapshot(Entity missile) {
      CompoundTag tag = missile.saveWithoutId(new CompoundTag());
      tag.putBoolean("ManagedInterceptor", missile instanceof PatriotInterceptorEntity);
      DeepStrikeData.stamp(tag);
      return tag;
   }

   public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      return this.save(tag);
   }

   public CompoundTag save(CompoundTag tag) {
      DeepStrikeData.stamp(tag);
      ListTag list = new ListTag();

      for (Entity missile : this.flights.values()) {
         RemovalReason reason = missile.getRemovalReason();
         if (missile.isAlive() || reason == RemovalReason.UNLOADED_TO_CHUNK || reason == RemovalReason.UNLOADED_WITH_PLAYER) {
            list.add(snapshot(missile));
         }
      }

      this.pending.forEach((idx, value) -> {
         if (!this.flights.containsKey(idx)) {
            list.add(value.copy());
         }
      });
      tag.put("Flights", list);
      ListTag finished = new ListTag();

      for (UUID id : this.completed) {
         CompoundTag entry = new CompoundTag();
         entry.putUUID("UUID", id);
         finished.add(entry);
      }

      tag.put("Completed", finished);
      ListTag records = new ListTag();
      this.missions.values().forEach(value -> records.add(value.copy()));
      tag.put("Missions", records);
      return tag;
   }
}
