package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.MissileGeometry;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FlightTerrain {
   private static final Map<FlightTerrain, Boolean> LIVE = Collections.synchronizedMap(new WeakHashMap<>());
   private static final TicketType<UUID> TICKET = TicketType.create("deepstrike_flight_terrain", Comparator.<UUID>naturalOrder());
   private final ServerLevel level;
   private final UUID owner = UUID.randomUUID();
   private final ArrayDeque<ChunkPos> queued = new ArrayDeque<>();
   private final Map<ChunkPos, Integer> held = new HashMap<>();
   private final Map<ChunkPos, CompletableFuture<?>> futures = new HashMap<>();
   private long stepped = -1L;
   private long ballisticForecastAt = Long.MIN_VALUE;
   private boolean closed;
   private int peakRetained;
   private long retentionUpdatedAt = Long.MIN_VALUE;
   private Set<ChunkPos> remainingCorridor;
   private Set<ChunkPos> currentSweep = Set.of();
   private Set<ChunkPos> plannedForecast = Set.of();
   private long plannedForecastAt = Long.MIN_VALUE;

   public static FlightTerrain.Diagnostics diagnostics(ServerLevel level) {
      int instances = 0;
      int retained = 0;
      int queued = 0;
      int requests = 0;
      int peak = 0;
      synchronized (LIVE) {
         for (FlightTerrain terrain : LIVE.keySet()) {
            if (!terrain.closed && terrain.level == level) {
               instances++;
               retained += terrain.held.size();
               queued += terrain.queued.size();
               requests += terrain.futures.size();
               peak += terrain.peakRetained;
            }
         }
      }

      return new FlightTerrain.Diagnostics(instances, retained, queued, requests, peak);
   }

   public FlightTerrain(ServerLevel level, Vec3 origin, List<RoutePoint> route, MissileKind kind) {
      this(level, origin, route, kind, null);
   }

   public FlightTerrain(ServerLevel level, Vec3 origin, List<RoutePoint> route, MissileKind kind, PeacekeeperDynamics.Plan ballisticPlan) {
      this.level = level;
      Set<ChunkPos> needed = new LinkedHashSet<>();
      Set<ChunkPos> blastArea = new LinkedHashSet<>();
      if (origin.y <= level.getMaxBuildHeight() + MissileGeometry.clearance(kind) && origin.y >= level.getMinBuildHeight()) {
         add(needed, origin, 1);
      }

      if (!route.isEmpty()) {
         RoutePoint last = route.get(route.size() - 1);
         Vec3 target = new Vec3(last.x(), last.y(), last.z());
         if (target.y <= level.getMaxBuildHeight() + kind.damageRadius && target.y >= level.getMinBuildHeight() - kind.damageRadius) {
            add(needed, target, 1);
            add(blastArea, target, Math.min(8, (kind.damageRadius + 15) / 16));
         }

         if (kind == MissileKind.PEACEKEEPER) {
            if (ballisticPlan == null) {
               RoutePoint first = route.get(0);
               ballisticPlan = PeacekeeperDynamics.select(origin, new Vec3(first.x(), first.y(), first.z()), first.speed()).plan();
            }

            if (ballisticPlan != null) {
               PeacekeeperDynamics.State state = new PeacekeeperDynamics.State(ballisticPlan.origin(), Vec3.ZERO, 0.0);
               Vec3 previous = state.position();

               for (int i = 0; i < 24000; i++) {
                  state = PeacekeeperDynamics.step(ballisticPlan, state);
                  Vec3 point = state.position();
                  if (point.y <= level.getMaxBuildHeight() + PeacekeeperDynamics.CLEARANCE) {
                     int samples = Math.max(1, (int)Math.ceil(Math.hypot(point.x - previous.x, point.z - previous.z) / 8.0));

                     for (int j = 1; j <= samples; j++) {
                        add(needed, previous.lerp(point, (double)j / samples), 1);
                     }
                  }

                  previous = point;
                  if (state.seconds() > ballisticPlan.boostTicks() * 0.05
                     && state.velocity().y < 0.0
                     && point.y <= ballisticPlan.target().y) {
                     break;
                  }
               }
            }
         } else {
            Vec3 from = origin;

            for (RoutePoint waypoint : route) {
               Vec3 to = new Vec3(waypoint.x(), waypoint.y(), waypoint.z());
               int samples = Math.max(1, (int)Math.ceil(Math.hypot(to.x - from.x, to.z - from.z) / 8.0));

               for (int i = 0; i <= samples; i++) {
                  Vec3 pointx = from.lerp(to, (double)i / samples);
                  if (pointx.y <= level.getMaxBuildHeight() + 20) {
                     add(needed, pointx, 1);
                  }
               }

               from = to;
            }
         }
      }

      needed.addAll(blastArea);
      this.queued.addAll(needed);
      ChunkPos pad = new ChunkPos(BlockPos.containing(origin));
      this.held.put(pad, 2);
      level.getChunkSource().addRegionTicket(TICKET, pad, 2, this.owner);
      this.peakRetained = this.held.size();
      LIVE.put(this, Boolean.TRUE);
   }

   private static void add(Set<ChunkPos> set, Vec3 point, int radius) {
      int cx = Mth.floor(point.x) >> 4;
      int cz = Mth.floor(point.z) >> 4;

      for (int ring = 0; ring <= radius; ring++) {
         for (int x = cx - ring; x <= cx + ring; x++) {
            for (int z = cz - ring; z <= cz + ring; z++) {
               if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) == ring) {
                  set.add(new ChunkPos(x, z));
               }
            }
         }
      }
   }

   private void hold(ChunkPos chunk) {
      if (!this.held.containsKey(chunk)) {
         this.held.put(chunk, 0);
         this.peakRetained = Math.max(this.peakRetained, this.held.size());
         this.level.getChunkSource().addRegionTicket(TICKET, chunk, 0, this.owner);
      }
   }

   public boolean retainValidatedCorridor(Vec3 origin, List<RoutePoint> route, MissileKind kind) {
      if (this.closed) {
         return false;
      } else {
         Set<ChunkPos> corridor = new LinkedHashSet<>();
         Vec3 from = origin;

         for (RoutePoint waypoint : route) {
            Vec3 to = new Vec3(waypoint.x(), waypoint.y(), waypoint.z());
            Vec3 movement = to.subtract(from);
            AABB body = MissileGeometry.worldBounds(kind, from, movement, 0.0F, 0.0F).inflate(1.0);
            this.collectBodyCorridor(corridor, body, movement);
            from = to;
         }

         boolean available = true;
         ArrayList<ChunkPos> order = new ArrayList<>(corridor);

         for (int i = order.size() - 1; i >= 0; i--) {
            ChunkPos chunk = order.get(i);
            if (FlightCollision.availableChunk(this.level, chunk.x, chunk.z) != null) {
               this.hold(chunk);
               this.queued.remove(chunk);
            } else {
               available = false;
               if (!this.futures.containsKey(chunk)) {
                  this.queued.remove(chunk);
                  this.queued.addFirst(chunk);
               }
            }
         }

         return available;
      }
   }

   public void prioritizeFlight(Vec3 position, Vec3 tickVelocity, MissileKind kind) {
      if (!this.closed) {
         AABB body = MissileGeometry.worldBounds(kind, position, tickVelocity, 0.0F, 0.0F).inflate(1.0);
         Set<ChunkPos> urgent = new LinkedHashSet<>();
         Vec3 ahead = tickVelocity.scale(4.0);
         this.collectBodyCorridor(urgent, body, ahead);
         this.currentSweep = Set.copyOf(urgent);
         this.prioritizeChunks(urgent);
      }
   }

   public void prioritizePlanned(RouteDynamics.State state, List<RoutePoint> route, MissileKind kind, Vec3 launchDirection) {
      if (!this.closed && !route.isEmpty() && this.level.getGameTime() >= this.plannedForecastAt) {
         LinkedHashSet<ChunkPos> urgent = new LinkedHashSet<>();

         for (int i = 0; i < 320; i++) {
            RouteDynamics.Step next = RouteDynamics.step(state, route, kind, launchDirection);
            Vec3 movement = next.state().motion().position().subtract(state.motion().position());
            if (!Double.isFinite(movement.lengthSqr()) || !Double.isFinite(next.state().motion().velocity().lengthSqr())) {
               return;
            }

            AABB body = MissileGeometry.substepBounds(kind, state.motion().position(), state.motion().velocity(), movement, 0.0F, 0.0F).inflate(1.0);
            this.collectBodyCorridor(urgent, body, movement);
            state = next.state();
            if (next.finished()) {
               break;
            }
         }

         this.plannedForecastAt = this.level.getGameTime() + 4L;
         this.plannedForecast = Set.copyOf(urgent);
         this.prioritizeChunks(urgent);
         return;
      }
   }

   public void prioritizeCruise(CruiseRouteDynamics.State state, List<RoutePoint> route, MissileKind kind, Vec3 launchDirection, boolean loft, boolean fallback) {
      if (!this.closed && !route.isEmpty() && this.level.getGameTime() >= this.plannedForecastAt) {
         LinkedHashSet<ChunkPos> urgent = new LinkedHashSet<>();

         for (int i = 0; i < 320; i++) {
            CruiseRouteDynamics.Step next = CruiseRouteDynamics.step(state, route, kind, launchDirection, loft, fallback);
            Vec3 movement = next.state().motion().position().subtract(state.motion().position());
            if (!Double.isFinite(movement.lengthSqr()) || !Double.isFinite(next.state().motion().velocity().lengthSqr())) {
               return;
            }

            FlightOrientation.Angles angles = FlightOrientation.along(launchDirection, 0.0F, 0.0F);
            AABB body = MissileGeometry.substepBounds(kind, state.motion().position(), state.motion().velocity(), movement, angles.yaw(), angles.pitch())
               .inflate(1.0);
            this.collectBodyCorridor(urgent, body, movement);
            state = next.state();
            if (next.finished()) {
               break;
            }
         }

         this.plannedForecastAt = this.level.getGameTime() + 4L;
         this.plannedForecast = Set.copyOf(urgent);
         this.prioritizeChunks(urgent);
         return;
      }
   }

   public void prioritizeSpike(SpikeDynamics.State state, Vec3 target, Vec3 launchDirection, double speedKmh, double height) {
      if (!this.closed && this.level.getGameTime() >= this.plannedForecastAt) {
         LinkedHashSet<ChunkPos> urgent = new LinkedHashSet<>();
         FlightOrientation.Angles angles = FlightOrientation.along(launchDirection, 0.0F, 0.0F);

         for (int i = 0; i < 320; i++) {
            SpikeDynamics.Step next = SpikeDynamics.step(state, target, launchDirection, speedKmh, height);
            Vec3 movement = next.state().motion().position().subtract(state.motion().position());
            if (!Double.isFinite(movement.lengthSqr()) || !Double.isFinite(next.state().motion().velocity().lengthSqr())) {
               return;
            }

            AABB body = MissileGeometry.substepBounds(
                  MissileKind.SPIKE_NLOS, state.motion().position(), state.motion().velocity(), movement, angles.yaw(), angles.pitch()
               )
               .inflate(1.0);
            this.collectBodyCorridor(urgent, body, movement);
            state = next.state();
            if (next.finished()) {
               break;
            }
         }

         this.plannedForecastAt = this.level.getGameTime() + 4L;
         this.plannedForecast = Set.copyOf(urgent);
         this.prioritizeChunks(urgent);
         return;
      }
   }

   public int releasePassedTerrain(Vec3 position, List<RoutePoint> remaining, MissileKind kind) {
      if (!this.closed && kind != MissileKind.PEACEKEEPER && this.level.getGameTime() >= this.retentionUpdatedAt) {
         this.retentionUpdatedAt = this.level.getGameTime() + 20L;
         Set<ChunkPos> needed = new HashSet<>(this.currentSweep);
         needed.addAll(this.plannedForecast);
         Vec3 from = position;

         for (RoutePoint point : remaining) {
            Vec3 to = new Vec3(point.x(), point.y(), point.z());
            Vec3 movement = to.subtract(from);
            AABB body = MissileGeometry.worldBounds(kind, from, movement, 0.0F, 0.0F).inflate(1.0);
            this.collectBodyCorridor(needed, body, movement);
            from = to;
         }

         if (!remaining.isEmpty()) {
            RoutePoint last = remaining.get(remaining.size() - 1);
            Vec3 target = new Vec3(last.x(), last.y(), last.z());
            if (target.y <= this.level.getMaxBuildHeight() + kind.damageRadius && target.y >= this.level.getMinBuildHeight() - kind.damageRadius) {
               add(needed, target, Math.min(8, (kind.damageRadius + 15) / 16));
            }
         }

         return this.applyRemainingCorridor(needed);
      } else {
         return 0;
      }
   }

   public int releasePassedBallistic(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state) {
      if (!this.closed && plan != null && this.level.getGameTime() >= this.retentionUpdatedAt) {
         this.retentionUpdatedAt = this.level.getGameTime() + 20L;
         Set<ChunkPos> needed = new HashSet<>(this.currentSweep);
         if (!this.traceBallisticCorridor(plan, state, 24000, needed)) {
            return 0;
         } else {
            Vec3 target = plan.target();
            MissileKind kind = MissileKind.PEACEKEEPER;
            if (target.y <= this.level.getMaxBuildHeight() + kind.damageRadius && target.y >= this.level.getMinBuildHeight() - kind.damageRadius) {
               add(needed, target, Math.min(8, (kind.damageRadius + 15) / 16));
            }

            return this.applyRemainingCorridor(needed);
         }
      } else {
         return 0;
      }
   }

   private int applyRemainingCorridor(Set<ChunkPos> needed) {
      this.remainingCorridor = Set.copyOf(needed);
      this.queued.removeIf(chunk -> !needed.contains(chunk));
      int released = 0;
      Iterator<Entry<ChunkPos, Integer>> iterator = this.held.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<ChunkPos, Integer> entry = iterator.next();
         if (!needed.contains(entry.getKey()) && !this.futures.containsKey(entry.getKey())) {
            this.level.getChunkSource().removeRegionTicket(TICKET, entry.getKey(), entry.getValue(), this.owner);
            iterator.remove();
            released++;
         }
      }

      return released;
   }

   public void prioritizeBallistic(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state) {
      if (!this.closed && plan != null && this.level.getGameTime() >= this.ballisticForecastAt) {
         this.ballisticForecastAt = this.level.getGameTime() + 4L;
         Set<ChunkPos> urgent = new LinkedHashSet<>();
         this.traceBallisticCorridor(plan, state, 320, urgent);
         this.prioritizeChunks(urgent);
      }
   }

   private boolean traceBallisticCorridor(PeacekeeperDynamics.Plan plan, PeacekeeperDynamics.State state, int steps, Set<ChunkPos> chunks) {
      AABB bounds = MissileGeometry.canonicalBounds(MissileKind.PEACEKEEPER);
      double x = Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX));
      double y = Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY));
      double z = Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ));
      double reach = Math.sqrt(x * x + y * y + z * z) + 1.0;

      for (int step = 0; step < steps; step++) {
         PeacekeeperDynamics.State next = PeacekeeperDynamics.step(plan, state);
         if (!Double.isFinite(next.position().lengthSqr()) || !Double.isFinite(next.velocity().lengthSqr())) {
            return false;
         }

         Vec3 movement = next.position().subtract(state.position());
         if (Math.min(state.position().y, next.position().y) - reach < this.level.getMaxBuildHeight()
            && Math.max(state.position().y, next.position().y) + reach >= this.level.getMinBuildHeight()) {
            AABB body = MissileGeometry.substepBounds(MissileKind.PEACEKEEPER, state.position(), state.velocity(), movement, 0.0F, 0.0F).inflate(1.0);
            this.collectBodyCorridor(chunks, body, movement);
         }

         state = next;
         if (next.seconds() > plan.boostTicks() * 0.05
            && next.velocity().y < 0.0
            && next.position().y < plan.target().y - PeacekeeperDynamics.CLEARANCE) {
            return true;
         }
      }

      return false;
   }

   private void prioritizeChunks(Set<ChunkPos> urgent) {
      ArrayList<ChunkPos> order = new ArrayList<>(urgent);

      for (int i = order.size() - 1; i >= 0; i--) {
         ChunkPos chunk = order.get(i);
         if (!this.futures.containsKey(chunk)) {
            if (FlightCollision.availableChunk(this.level, chunk.x, chunk.z) != null) {
               this.hold(chunk);
               this.queued.remove(chunk);
            } else {
               this.queued.remove(chunk);
               this.queued.addFirst(chunk);
            }
         }
      }
   }

   private void collectBodyCorridor(Set<ChunkPos> chunks, AABB body, Vec3 movement) {
      int samples = Math.max(1, (int)Math.ceil(movement.length() / 8.0));
      Vec3 step = movement.scale(1.0 / samples);

      for (int i = 0; i < samples; i++) {
         AABB swept = body.move(step.scale(i)).expandTowards(step);
         if (!(swept.minY >= this.level.getMaxBuildHeight()) && !(swept.maxY < this.level.getMinBuildHeight())) {
            int x0 = Mth.floor(swept.minX) - 1 >> 4;
            int x1 = Mth.floor(swept.maxX) + 1 >> 4;
            int z0 = Mth.floor(swept.minZ) - 1 >> 4;
            int z1 = Mth.floor(swept.maxZ) + 1 >> 4;

            for (int x = x0; x <= x1; x++) {
               for (int z = z0; z <= z1; z++) {
                  chunks.add(new ChunkPos(x, z));
               }
            }
         }
      }
   }

   public boolean ready() {
      if (this.closed) {
         return false;
      } else {
         if (this.stepped != this.level.getGameTime()) {
            this.stepped = this.level.getGameTime();
            Iterator<Entry<ChunkPos, CompletableFuture<?>>> iterator = this.futures.entrySet().iterator();

            while (iterator.hasNext()) {
               Entry<ChunkPos, CompletableFuture<?>> entry = iterator.next();
               if (entry.getValue().isDone()) {
                  if (FlightCollision.availableChunk(this.level, entry.getKey().x, entry.getKey().z) == null
                     && (
                        this.remainingCorridor == null
                           || this.remainingCorridor.contains(entry.getKey())
                           || this.currentSweep.contains(entry.getKey())
                           || this.plannedForecast.contains(entry.getKey())
                     )) {
                     this.queued.addLast(entry.getKey());
                  }

                  iterator.remove();
               }
            }

            int i = 0;

            for (int inspected = 0; i < 2 && inspected < 64 && !this.queued.isEmpty() && this.futures.size() < 8; inspected++) {
               ChunkPos chunk = this.queued.remove();
               this.hold(chunk);
               if (FlightCollision.availableChunk(this.level, chunk.x, chunk.z) == null) {
                  i++;
                  this.futures
                     .put(
                        chunk,
                        CompletableFuture.<CompletableFuture>supplyAsync(
                              () -> this.level.getChunkSource().getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true)
                           )
                           .thenCompose(future -> future)
                     );
               }
            }
         }

         return this.queued.isEmpty() && this.futures.isEmpty();
      }
   }

   public void close() {
      this.closed = true;

      for (Entry<ChunkPos, Integer> entry : this.held.entrySet()) {
         this.level.getChunkSource().removeRegionTicket(TICKET, entry.getKey(), entry.getValue(), this.owner);
      }

      this.held.clear();
      this.queued.clear();
      this.futures.clear();
      this.remainingCorridor = null;
      this.currentSweep = Set.of();
      this.plannedForecast = Set.of();
      LIVE.remove(this);
   }

   public record Diagnostics(int instances, int retained, int queued, int requests, int summedPeakRetained) {
   }
}
