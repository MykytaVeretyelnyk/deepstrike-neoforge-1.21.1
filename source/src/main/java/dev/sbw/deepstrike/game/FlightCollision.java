package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.model.MissileGeometry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.LoggerFactory;

public final class FlightCollision {
   public static Optional<FlightCollision.PathContact> sweepPath(
      ServerLevel level, Entity entity, List<FlightCollision.Segment> segments, Predicate<Entity> filter
   ) {
      return sweepPathDetailed(level, entity, segments, filter).contact();
   }

   public static Optional<FlightCollision.PathContact> sweepPath(
      ServerLevel level, Entity entity, List<FlightCollision.Segment> segments, Predicate<Entity> filter, Entity observedTarget
   ) {
      return sweepPathDetailed(level, entity, segments, filter, observedTarget).contact();
   }

   public static FlightCollision.PathSweepResult sweepPathDetailed(
      ServerLevel level, Entity entity, List<FlightCollision.Segment> segments, Predicate<Entity> filter
   ) {
      return sweepPathDetailed(level, entity, segments, filter, null);
   }

   private static FlightCollision.PathSweepResult sweepPathDetailed(
      ServerLevel level, Entity entity, List<FlightCollision.Segment> segments, Predicate<Entity> filter, Entity observedTarget
   ) {
      HashSet<ChunkPos> missing = new HashSet<>();
      int firstUnknownSegment = Integer.MAX_VALUE;
      double firstUnknownFraction = Double.POSITIVE_INFINITY;

      for (int i = 0; i < segments.size(); i++) {
         FlightCollision.Segment segment = segments.get(i);
         FlightCollision.SweepResult contact = sweepSegment(
            level, entity, segment.from(), segment.to(), segment.body(), filter, (double)i / segments.size(), 1.0 / segments.size(), observedTarget
         );
         missing.addAll(contact.missingTerrain());
         if (firstUnknownSegment == Integer.MAX_VALUE && Double.isFinite(contact.firstUnknownFraction())) {
            firstUnknownSegment = i;
            firstUnknownFraction = contact.firstUnknownFraction();
         }

         if (contact.hit().getType() != Type.MISS) {
            return new FlightCollision.PathSweepResult(
               Optional.of(new FlightCollision.PathContact(i, contact)), missing, firstUnknownSegment, firstUnknownFraction
            );
         }
      }

      return new FlightCollision.PathSweepResult(Optional.empty(), missing, firstUnknownSegment, firstUnknownFraction);
   }

   public static HitResult sweep(ServerLevel level, Entity entity, Predicate<Entity> filter) {
      return sweepDetailed(level, entity, filter).hit();
   }

   public static FlightCollision.SweepResult sweepDetailed(ServerLevel level, Entity entity, Predicate<Entity> filter) {
      Vec3 from = entity.position();
      Vec3 movement = entity.getDeltaMovement();
      Vec3 to = from.add(movement);
      AABB body = entity instanceof GuidedMissileEntity missile
         ? MissileGeometry.worldBounds(missile.kind(), from, movement, entity.getYRot(), entity.getXRot())
         : (
            entity instanceof PatriotInterceptorEntity interceptor
               ? InterceptorGeometry.worldBounds(interceptor.warhead(), from, movement, interceptor.yaw(), interceptor.pitch())
               : entity.getBoundingBox()
         );
      return sweepSegment(level, entity, from, to, body, filter);
   }

   public static FlightCollision.SweepResult sweepSegment(ServerLevel level, Entity entity, Vec3 from, Vec3 to, AABB body, Predicate<Entity> filter) {
      return sweepSegment(level, entity, from, to, body, filter, 0.0, 1.0);
   }

   private static FlightCollision.SweepResult sweepSegment(
      ServerLevel level, Entity entity, Vec3 from, Vec3 to, AABB body, Predicate<Entity> filter, double targetStart, double targetSpan
   ) {
      return sweepSegment(level, entity, from, to, body, filter, targetStart, targetSpan, null);
   }

   private static FlightCollision.SweepResult sweepSegment(
      ServerLevel level, Entity entity, Vec3 from, Vec3 to, AABB body, Predicate<Entity> filter, double targetStart, double targetSpan, Entity observedTarget
   ) {
      Vec3 movement = to.subtract(from);
      FlightCollision.SweepResult blockContact = sweepBlocks(level, entity, from, to, body);
      HitResult block = blockContact.hit();
      FlightCollision.SweepResult earliest = blockContact;
      double nearest = block.getType() == Type.MISS ? Double.POSITIVE_INFINITY : from.distanceToSqr(block.getLocation());
      boolean nativeAirDefense = entity instanceof MissileProjectile nativeMissile && InterceptionEffects.isManpad(nativeMissile);
      List<Entity> candidates = level.getEntities(entity, body.expandTowards(movement).inflate(nativeAirDefense ? 32.0 : 1.0), filter);
      boolean recoveredTarget = false;
      if (observedTarget != null
         && observedTarget != entity
         && observedTarget.level() == level
         && observedTarget.isAlive()
         && filter.test(observedTarget)
         && !candidates.contains(observedTarget)) {
         AABB targetBounds = observedTarget instanceof GuidedMissileEntity guided
            ? MissileGeometry.worldBounds(guided.kind(), guided.position(), guided.getDeltaMovement(), guided.getYRot(), guided.getXRot())
            : observedTarget.getBoundingBox();
         if (targetBounds.expandTowards(observedTarget.getDeltaMovement()).inflate(0.05).intersects(body.expandTowards(movement).inflate(0.05))) {
            candidates = new ArrayList<>(candidates);
            candidates.add(observedTarget);
            recoveredTarget = true;
         }
      }

      for (Entity candidate : candidates) {
         AABB expanded = expandForBody(candidate.getBoundingBox().inflate(candidate.getPickRadius()), body, from);
         Vec3 contact;
         if (entity instanceof MissileProjectile nativeMissilex
            && InterceptionEffects.isManpad(nativeMissilex)
            && candidate instanceof GuidedMissileEntity missile) {
            Vec3 noseFrom = ManpadGeometry.nose(nativeMissilex, from, movement);
            Vec3 noseTo = ManpadGeometry.nose(nativeMissilex, to, movement);
            Vec3 priorTargetMovement = missile.getDeltaMovement().scale(targetStart);
            double hit = MissileModelContact.fraction(
               missile, noseFrom.subtract(priorTargetMovement), noseTo.subtract(priorTargetMovement), missile.getDeltaMovement().scale(targetSpan)
            );
            contact = hit < 0.0 ? null : from.lerp(to, hit);
         } else {
            contact = contact(expanded, from, to);
         }

         if (contact != null && from.distanceToSqr(contact) < nearest) {
            if (recoveredTarget && candidate == observedTarget && Boolean.getBoolean("deepstrike.airDefenseReview")) {
               LoggerFactory.getLogger(FlightCollision.class).info("MANPAD_OBSERVED_TARGET_CONTACT target={}", candidate.getUUID());
            }

            nearest = from.distanceToSqr(contact);
            Vec3 surface = surface(candidate.getBoundingBox(), body.move(contact.subtract(from)), movement);
            earliest = new FlightCollision.SweepResult(
               new EntityHitResult(candidate, contact),
               surface,
               fraction(from, to, contact),
               blockContact.missingTerrain(),
               blockContact.firstUnknownFraction()
            );
         }
      }

      return earliest;
   }

   private static AABB expandForBody(AABB obstacle, AABB body, Vec3 origin) {
      return new AABB(
         obstacle.minX - (body.maxX - origin.x),
         obstacle.minY - (body.maxY - origin.y),
         obstacle.minZ - (body.maxZ - origin.z),
         obstacle.maxX - (body.minX - origin.x),
         obstacle.maxY - (body.minY - origin.y),
         obstacle.maxZ - (body.minZ - origin.z)
      );
   }

   private static Vec3 contact(AABB obstacle, Vec3 from, Vec3 to) {
      return obstacle.contains(from) ? from : (Vec3)obstacle.clip(from, to).orElse(null);
   }

   private static FlightCollision.SweepResult sweepBlocks(ServerLevel level, Entity entity, Vec3 from, Vec3 to, AABB body) {
      Vec3 movement = to.subtract(from);
      int samples = Math.max(1, (int)Math.ceil(Math.max(Math.abs(movement.x), Math.max(Math.abs(movement.y), Math.abs(movement.z))) * 2.0));
      HashSet<BlockPos> visited = new HashSet<>();
      HashSet<ChunkPos> missing = new HashSet<>();
      double firstUnknown = Double.POSITIVE_INFINITY;
      FlightCollision.TerrainView loaded = terrainView(level);
      MissileShapeSweep.Mesh mesh = entity instanceof GuidedMissileEntity missile
         ? MissileShapeSweep.motion(
            missile.kind(),
            FlightOrientation.direction(missile.getYRot(), missile.getXRot()),
            movement,
            missile.getYRot(),
            missile.getXRot(),
            0.0,
            missile::visibleFlightBone
         )
         : null;
      double nearest = Double.POSITIVE_INFINITY;
      Vec3 best = null;
      Vec3 bestSurface = null;
      BlockPos bestBlock = null;

      for (int i = 0; i <= samples; i++) {
         AABB at = body.move(movement.scale((double)i / samples)).inflate(0.5);
         int minY = Math.max(level.getMinBuildHeight(), Mth.floor(at.minY));
         int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(at.maxY));

         for (int x = Mth.floor(at.minX); x <= Mth.floor(at.maxX); x++) {
            for (int y = minY; y <= maxY; y++) {
               for (int z = Mth.floor(at.minZ); z <= Mth.floor(at.maxZ); z++) {
                  BlockPos pos = new BlockPos(x, y, z);
                  if (visited.add(pos) && !(entity instanceof GuidedMissileEntity missilex && missilex.ignoresLaunchBlock(pos))) {
                     ChunkAccess chunk = availableChunk(level, x >> 4, z >> 4);
                     if (chunk == null) {
                        ChunkPos key = new ChunkPos(x >> 4, z >> 4);
                        if (missing.add(key)) {
                           AABB unknown = new AABB(key.getMinBlockX(), level.getMinBuildHeight(), key.getMinBlockZ(), key.getMaxBlockX() + 1, level.getMaxBuildHeight(), key.getMaxBlockZ() + 1);
                           Vec3 entry = contact(expandForBody(unknown, body.inflate(0.5), from), from, to);
                           if (entry != null) {
                              firstUnknown = Math.min(firstUnknown, fraction(from, to, entry));
                           }
                        }
                     } else {
                        BlockState state = chunk.getBlockState(pos);
                        if (!state.isAir()) {
                           loaded.missing.clear();
                           VoxelShape shape = state.getCollisionShape(loaded, pos, CollisionContext.of(entity));
                           if (!loaded.missing.isEmpty()) {
                              missing.addAll(loaded.missing);
                              firstUnknown = 0.0;
                           }

                           for (AABB box : shape.toAabbs()) {
                              AABB obstacle = box.move(x, y, z);
                              MissileShapeSweep.Contact precise = mesh == null ? null : mesh.contact(obstacle, from, to);
                              Vec3 hit = mesh == null ? contact(expandForBody(obstacle, body, from), from, to) : (precise == null ? null : precise.origin());
                              if (hit != null && from.distanceToSqr(hit) < nearest) {
                                 nearest = from.distanceToSqr(hit);
                                 best = hit;
                                 bestBlock = pos;
                                 bestSurface = precise == null ? surface(obstacle, body.move(hit.subtract(from)), movement) : precise.surface();
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      Direction face = Direction.getNearest(-movement.x, -movement.y, -movement.z);
      return best == null
         ? new FlightCollision.SweepResult(BlockHitResult.miss(to, face, BlockPos.containing(to)), to, 1.0, missing, firstUnknown)
         : new FlightCollision.SweepResult(new BlockHitResult(best, face, bestBlock, false), bestSurface, fraction(from, to, best), missing, firstUnknown);
   }

   private static double fraction(Vec3 from, Vec3 to, Vec3 hit) {
      return to.distanceToSqr(from) < 1.0E-18 ? 0.0 : Math.min(1.0, from.distanceTo(hit) / from.distanceTo(to));
   }

   static Vec3 surface(AABB obstacle, AABB movedBody, Vec3 movement) {
      return ContactSurface.find(obstacle, movedBody, movement);
   }

   public static FlightCollision.ClipResult clipDetailed(ServerLevel level, Vec3 from, Vec3 to, Entity entity) {
      FlightCollision.TerrainView view = terrainView(level);
      BlockHitResult hit = view.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, entity));
      return new FlightCollision.ClipResult(hit, view.missingTerrain());
   }

   public static boolean knownClear(ServerLevel level, Vec3 from, Vec3 to, Entity entity) {
      return clipDetailed(level, from, to, entity).knownClear();
   }

   public static BlockHitResult clip(ServerLevel level, Vec3 from, Vec3 to, Entity entity) {
      return clipDetailed(level, from, to, entity).hit();
   }

   public static boolean airTarget(ServerLevel level, Vec3 target) {
      BlockPos pos = BlockPos.containing(target);
      if (pos.getY() >= level.getMinBuildHeight() && pos.getY() < level.getMaxBuildHeight()) {
         ChunkAccess chunk = availableChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
         if (chunk == null) {
            return false;
         } else {
            FlightCollision.TerrainView view = terrainView(level);
            boolean empty = chunk.getBlockState(pos).getCollisionShape(view, pos).isEmpty();
            return empty && view.missingTerrain().isEmpty();
         }
      } else {
         return true;
      }
   }

   public static ChunkAccess availableChunk(ServerLevel level, int x, int z) {
      LevelChunk loaded = level.getChunkSource().getChunkNow(x, z);
      if (loaded != null) {
         return loaded;
      } else {
         try {
            return level.getChunkSource().getChunkForLighting(x, z) instanceof ChunkAccess chunk && chunk.getPersistedStatus().isOrAfter(ChunkStatus.FULL) ? chunk : null;
         } catch (CancellationException | CompletionException var6) {
            return null;
         }
      }
   }

   public static FlightCollision.TerrainView terrainView(ServerLevel level) {
      return new FlightCollision.TerrainView(level);
   }

   static BlockGetter loadedTerrain(ServerLevel level) {
      return terrainView(level);
   }

   private FlightCollision() {
   }

   public record ClipResult(BlockHitResult hit, Set<ChunkPos> missingTerrain) {
      public ClipResult(BlockHitResult hit, Set<ChunkPos> missingTerrain) {
         missingTerrain = Set.copyOf(missingTerrain);
         this.hit = hit;
         this.missingTerrain = missingTerrain;
      }

      public boolean knownClear() {
         return this.hit.getType() == Type.MISS && this.missingTerrain.isEmpty();
      }
   }

   public record PathContact(int segment, FlightCollision.SweepResult contact) {
   }

   public record PathSweepResult(
      Optional<FlightCollision.PathContact> contact, Set<ChunkPos> missingTerrain, int firstUnknownSegment, double firstUnknownFraction
   ) {
      public PathSweepResult(Optional<FlightCollision.PathContact> contact, Set<ChunkPos> missingTerrain, int firstUnknownSegment, double firstUnknownFraction) {
         missingTerrain = Set.copyOf(missingTerrain);
         this.contact = contact;
         this.missingTerrain = missingTerrain;
         this.firstUnknownSegment = firstUnknownSegment;
         this.firstUnknownFraction = firstUnknownFraction;
      }

      public boolean terrainComplete() {
         return this.missingTerrain.isEmpty();
      }

      public boolean terrainKnownThroughContact() {
         return this.contact.isPresent()
            ? this.firstUnknownSegment > this.contact.get().segment()
               || this.firstUnknownSegment == this.contact.get().segment() && this.firstUnknownFraction > this.contact.get().contact().fraction()
            : this.terrainComplete();
      }
   }

   public record Segment(Vec3 from, Vec3 to, AABB body) {
   }

   public record SweepResult(HitResult hit, Vec3 surface, double fraction, Set<ChunkPos> missingTerrain, double firstUnknownFraction) {
      public SweepResult(HitResult hit, Vec3 surface, double fraction, Set<ChunkPos> missingTerrain, double firstUnknownFraction) {
         missingTerrain = Set.copyOf(missingTerrain);
         this.hit = hit;
         this.surface = surface;
         this.fraction = fraction;
         this.missingTerrain = missingTerrain;
         this.firstUnknownFraction = firstUnknownFraction;
      }

      public SweepResult(HitResult hit, Vec3 surface, double fraction) {
         this(hit, surface, fraction, Set.of(), Double.POSITIVE_INFINITY);
      }

      public boolean terrainComplete() {
         return this.missingTerrain.isEmpty();
      }

      public boolean terrainKnownThroughContact() {
         return this.firstUnknownFraction > this.fraction;
      }
   }

   public static final class TerrainView implements BlockGetter {
      private final ServerLevel level;
      private final Set<ChunkPos> missing = new HashSet<>();

      private TerrainView(ServerLevel level) {
         this.level = level;
      }

      public Set<ChunkPos> missingTerrain() {
         return Set.copyOf(this.missing);
      }

      private boolean outside(BlockPos pos) {
         return pos.getY() < this.level.getMinBuildHeight() || pos.getY() >= this.level.getMaxBuildHeight();
      }

      private ChunkAccess chunk(BlockPos pos) {
         if (this.outside(pos)) {
            return null;
         } else {
            ChunkAccess chunk = FlightCollision.availableChunk(this.level, pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
               this.missing.add(new ChunkPos(pos));
            }

            return chunk;
         }
      }

      public BlockEntity getBlockEntity(BlockPos pos) {
         ChunkAccess chunk = this.chunk(pos);
         return chunk == null ? null : chunk.getBlockEntity(pos);
      }

      public BlockState getBlockState(BlockPos pos) {
         ChunkAccess chunk = this.chunk(pos);
         return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
      }

      public FluidState getFluidState(BlockPos pos) {
         return this.getBlockState(pos).getFluidState();
      }

      public int getHeight() {
         return this.level.getHeight();
      }

      public int getMinBuildHeight() {
         return this.level.getMinBuildHeight();
      }
   }
}
