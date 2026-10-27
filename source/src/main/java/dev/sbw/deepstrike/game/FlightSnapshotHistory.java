package dev.sbw.deepstrike.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class FlightSnapshotHistory {
   private final Map<UUID, FlightSnapshotHistory.Entry> flights = new HashMap<>();
   private final Map<UUID, FlightSnapshotHistory.Entry> missions = new HashMap<>();
   private final long retentionTicks;
   private long newest = Long.MIN_VALUE;
   private int updates;

   public FlightSnapshotHistory(long retentionTicks) {
      if (retentionTicks < 1L) {
         throw new IllegalArgumentException("Positive retention required");
      } else {
         this.retentionTicks = retentionTicks;
      }
   }

   public boolean finished(UUID flight) {
      FlightSnapshotHistory.Entry entry = this.flights.get(flight);
      return entry != null && entry.finished();
   }

   public boolean accept(UUID flight, UUID mission, long tick, boolean alive) {
      return this.accept(flight, mission, tick, alive, false);
   }

   public boolean accept(UUID flight, UUID mission, long tick, boolean alive, boolean discontinuity) {
      this.newest = Math.max(this.newest, tick);
      if (++this.updates % 64 == 0) {
         this.prune();
      }

      FlightSnapshotHistory.Entry previous = this.flights.get(flight);
      FlightSnapshotHistory.Entry previousMission = this.missions.get(mission);
      if (alive) {
         if (tick < this.oldest() || rejects(previous, tick, discontinuity) || rejects(previousMission, tick, discontinuity)) {
            return false;
         }
      } else if (previous != null && previous.finished() && (mission == null || previousMission != null && previousMission.finished())) {
         return false;
      }

      FlightSnapshotHistory.Entry entry = new FlightSnapshotHistory.Entry(alive ? tick : this.newest, !alive, discontinuity);
      this.flights.put(flight, entry);
      if (mission != null) {
         this.missions.put(mission, entry);
      }

      return true;
   }

   private static boolean rejects(FlightSnapshotHistory.Entry previous, long tick, boolean discontinuity) {
      return previous != null && (previous.finished() || tick < previous.tick() || tick == previous.tick() && (!discontinuity || previous.discontinuity()));
   }

   private long oldest() {
      return this.newest < Long.MIN_VALUE + this.retentionTicks ? Long.MIN_VALUE : this.newest - this.retentionTicks;
   }

   public void prune() {
      long oldest = this.oldest();
      this.flights.values().removeIf(entry -> entry.tick() < oldest);
      this.missions.values().removeIf(entry -> entry.tick() < oldest);
   }

   public int retainedFlights() {
      return this.flights.size();
   }

   public void clear() {
      this.flights.clear();
      this.missions.clear();
      this.newest = Long.MIN_VALUE;
      this.updates = 0;
   }

   private record Entry(long tick, boolean finished, boolean discontinuity) {
   }
}
