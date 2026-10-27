package dev.sbw.deepstrike.client.smoke;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SmokeFlow {
   public static final double SKIN = 0.00390625;
   private static ClientLevel world;
   private static long revision;
   private static final Long2ObjectOpenHashMap<SmokeFlow.Tile> TILES = new Long2ObjectOpenHashMap();
   private static final Long2ObjectOpenHashMap<SmokeFlow.SectionSnapshot> SECTIONS = new Long2ObjectOpenHashMap();

   public static void invalidateBlock(ClientLevel level, BlockPos pos) {
      if (world == level) {
         revision++;

         for (int x = pos.getX() - 1; x <= pos.getX() + 1; x++) {
            for (int y = pos.getY() - 1; y <= pos.getY() + 1; y++) {
               for (int z = pos.getZ() - 1; z <= pos.getZ() + 1; z++) {
                  SECTIONS.remove(BlockPos.asLong(x >> 4, y >> 4, z >> 4));
                  TILES.remove(BlockPos.asLong(x >> 2, y >> 2, z >> 2));
               }
            }
         }
      }
   }

   public static void invalidateChunk(int x, int z) {
      if (world != null) {
         revision++;

         for (int y = world.getMinSection(); y < world.getMaxSection(); y++) {
            SECTIONS.remove(BlockPos.asLong(x, y, z));
         }

         TILES.clear();
      }
   }

   public static void prewarmTile(ClientLevel level, int x, int y, int z) {
      if (y * 4 >= level.getMinBuildHeight() && y * 4 < level.getMaxBuildHeight()) {
         if (world != level) {
            clear();
            world = level;
         }

         if (level.getChunkSource().getChunk(x >> 2, z >> 2, ChunkStatus.FULL, false) != null) {
            tile(level, x, y, z);
         }
      }
   }

   public static void prewarmSection(ClientLevel level, int x, int y, int z) {
      if (y * 16 >= level.getMinBuildHeight() && y * 16 < level.getMaxBuildHeight()) {
         if (world != level) {
            clear();
            world = level;
         }

         if (level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null) {
            section(level, x, y, z);
         }
      }
   }

   public static void clear() {
      TILES.clear();
      SECTIONS.clear();
      world = null;
      revision++;
      SmokeGeometry.clear();
   }

   private static List<AABB> parcelObstacles(ClientLevel level, SmokeFlow.Parcel parcel, AABB required) {
      if (parcel.cachedBoxes == null
         || parcel.cachedRevision != revision
         || world != level
         || level.getGameTime() - parcel.cachedTick >= 4L
         || !parcel.cachedBounds.contains(required.minX, required.minY, required.minZ)
         || !parcel.cachedBounds.contains(required.maxX, required.maxY, required.maxZ)) {
         parcel.cachedBounds = required.inflate(0.75);
         parcel.cachedBoxes = obstacles(level, parcel.cachedBounds);
         parcel.cachedRevision = revision;
         parcel.cachedTick = level.getGameTime();
      }

      return parcel.cachedBoxes;
   }

   public static List<AABB> obstacles(ClientLevel level, AABB bounds) {
      if (world != level) {
         clear();
         world = level;
      }

      if (TILES.size() > 65536) {
         TILES.clear();
      }

      double lower = Math.max(bounds.minY, (double)level.getMinBuildHeight());
      double upper = Math.min(bounds.maxY, (double)level.getMaxBuildHeight());
      if (upper <= lower) {
         return List.of();
      } else {
         int tx0 = Math.floorDiv((int)Math.floor(bounds.minX), 4);
         int tx1 = Math.floorDiv((int)Math.floor(bounds.maxX), 4);
         int ty0 = Math.floorDiv((int)Math.floor(lower), 4);
         int ty1 = Math.floorDiv((int)Math.floor(Math.nextDown(upper)), 4);
         int tz0 = Math.floorDiv((int)Math.floor(bounds.minZ), 4);
         int tz1 = Math.floorDiv((int)Math.floor(bounds.maxZ), 4);
         long tiles = (long)(tx1 - tx0 + 1) * (ty1 - ty0 + 1) * (tz1 - tz0 + 1);
         if (tiles <= 64L) {
            return tileObstacles(level, bounds, tx0, tx1, ty0, ty1, tz0, tz1);
         } else {
            if (SECTIONS.size() > 8192) {
               SECTIONS.clear();
            }

            ArrayList<AABB> result = null;
            int x0 = Math.floorDiv((int)Math.floor(bounds.minX), 16);
            int x1 = Math.floorDiv((int)Math.floor(bounds.maxX), 16);
            int y0 = Math.floorDiv((int)Math.floor(lower), 16);
            int y1 = Math.floorDiv((int)Math.floor(Math.nextDown(upper)), 16);
            int z0 = Math.floorDiv((int)Math.floor(bounds.minZ), 16);
            int z1 = Math.floorDiv((int)Math.floor(bounds.maxZ), 16);

            for (int x = x0; x <= x1; x++) {
               for (int y = y0; y <= y1; y++) {
                  for (int z = z0; z <= z1; z++) {
                     for (AABB box : section(level, x, y, z).boxes) {
                        if (box.intersects(bounds)) {
                           if (result == null) {
                              result = new ArrayList<>();
                           }

                           result.add(box);
                        }
                     }
                  }
               }
            }

            return (List<AABB>)(result == null ? List.of() : (result.size() < 2 ? result : mergeFullBoxes(result)));
         }
      }
   }

   private static List<AABB> tileObstacles(ClientLevel level, AABB bounds, int x0, int x1, int y0, int y1, int z0, int z1) {
      ArrayList<AABB> result = null;

      for (int x = x0; x <= x1; x++) {
         for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
               for (AABB box : tile(level, x, y, z).boxes) {
                  if (box.intersects(bounds)) {
                     if (result == null) {
                        result = new ArrayList<>();
                     }

                     result.add(box);
                  }
               }
            }
         }
      }

      return (List<AABB>)(result == null ? List.of() : result);
   }

   private static boolean distantUnknown(int chunkX, int chunkZ) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      } else {
         ChunkPos position = mc.player.chunkPosition();
         int reach = (Integer)mc.options.renderDistance().get() + 2;
         return Math.abs(chunkX - position.x) > reach || Math.abs(chunkZ - position.z) > reach;
      }
   }

   private static SmokeFlow.Tile section(ClientLevel level, int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      SmokeFlow.SectionSnapshot cached = (SmokeFlow.SectionSnapshot)SECTIONS.get(key);
      long now = level.getGameTime();
      LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
      LevelChunkSection currentSection = chunk == null ? null : chunk.getSection(level.getSectionIndex(y * 16));
      if (cached == null || cached.chunk != chunk || cached.section != currentSection || chunk == null && cached.tile.tick != now) {
         ArrayList<AABB> boxes = new ArrayList<>();
         ArrayList<AABB> full = new ArrayList<>();
         if (chunk == null) {
            if (!distantUnknown(x, z)) {
               boxes.add(new AABB(x * 16, y * 16, z * 16, x * 16 + 16, y * 16 + 16, z * 16 + 16));
            }
         } else if (!chunk.getSection(level.getSectionIndex(y * 16)).hasOnlyAir()) {
            for (int xx = x * 4; xx < x * 4 + 4; xx++) {
               for (int yy = y * 4; yy < y * 4 + 4; yy++) {
                  for (int zz = z * 4; zz < z * 4 + 4; zz++) {
                     SmokeFlow.Tile source = tile(level, xx, yy, zz);

                     for (AABB box : source.boxes) {
                        if (integralBox(box)) {
                           full.add(box);
                        } else {
                           boxes.add(box);
                        }
                     }
                  }
               }
            }

            boxes.addAll(mergeFullBoxes(full));
         }

         SmokeFlow.Tile tile = new SmokeFlow.Tile(now, chunk, List.copyOf(boxes));
         SECTIONS.put(key, new SmokeFlow.SectionSnapshot(chunk, currentSection, tile));
         return tile;
      } else {
         return cached.tile;
      }
   }

   private static boolean integralBox(AABB b) {
      return b.minX == Math.rint(b.minX)
         && b.minY == Math.rint(b.minY)
         && b.minZ == Math.rint(b.minZ)
         && b.maxX == Math.rint(b.maxX)
         && b.maxY == Math.rint(b.maxY)
         && b.maxZ == Math.rint(b.maxZ);
   }

   public static List<AABB> mergeFullBoxes(List<AABB> source) {
      if (source.size() < 2) {
         return source;
      } else {
         ArrayList<AABB> boxes = new ArrayList<>(source);

         boolean changed;
         do {
            changed = false;

            for (int i = 0; i < boxes.size(); i++) {
               for (int j = i + 1; j < boxes.size(); j++) {
                  AABB a = boxes.get(i);
                  AABB b = boxes.get(j);
                  if (a.minX <= b.minX
                     && a.minY <= b.minY
                     && a.minZ <= b.minZ
                     && a.maxX >= b.maxX
                     && a.maxY >= b.maxY
                     && a.maxZ >= b.maxZ) {
                     boxes.remove(j);
                     j--;
                     changed = true;
                  } else if (b.minX <= a.minX
                     && b.minY <= a.minY
                     && b.minZ <= a.minZ
                     && b.maxX >= a.maxX
                     && b.maxY >= a.maxY
                     && b.maxZ >= a.maxZ) {
                     boxes.set(i, b);
                     boxes.remove(j);
                     j--;
                     changed = true;
                  } else {
                     boolean adjacent = a.minY == b.minY
                           && a.maxY == b.maxY
                           && a.minZ == b.minZ
                           && a.maxZ == b.maxZ
                           && a.maxX >= b.minX
                           && b.maxX >= a.minX
                        || a.minX == b.minX
                           && a.maxX == b.maxX
                           && a.minZ == b.minZ
                           && a.maxZ == b.maxZ
                           && a.maxY >= b.minY
                           && b.maxY >= a.minY
                        || a.minX == b.minX
                           && a.maxX == b.maxX
                           && a.minY == b.minY
                           && a.maxY == b.maxY
                           && a.maxZ >= b.minZ
                           && b.maxZ >= a.minZ;
                     if (adjacent) {
                        boxes.set(i, a.minmax(b));
                        boxes.remove(j);
                        j--;
                        changed = true;
                     }
                  }
               }
            }
         } while (changed);

         return boxes;
      }
   }

   private static SmokeFlow.Tile tile(ClientLevel level, int x, int y, int z) {
      long key = BlockPos.asLong(x, y, z);
      SmokeFlow.Tile tile = (SmokeFlow.Tile)TILES.get(key);
      LevelChunk chunk = level.getChunkSource().getChunk(x >> 2, z >> 2, ChunkStatus.FULL, false);
      if (tile == null || tile.chunk != chunk || chunk == null && tile.tick != level.getGameTime()) {
         ArrayList<AABB> boxes = new ArrayList<>();
         int bottom = y * 4;
         if (bottom >= level.getMinBuildHeight() && bottom < level.getMaxBuildHeight()) {
            if (chunk == null) {
               if (!distantUnknown(x >> 2, z >> 2)) {
                  boxes.add(new AABB(x * 4, bottom, z * 4, x * 4 + 4, bottom + 4, z * 4 + 4));
               }
            } else if (!chunk.getSection(level.getSectionIndex(bottom)).hasOnlyAir()) {
               long fullBlocks = 0L;
               MutableBlockPos pos = new MutableBlockPos();

               for (int xx = x * 4; xx < x * 4 + 4; xx++) {
                  for (int yy = bottom; yy < bottom + 4; yy++) {
                     for (int zz = z * 4; zz < z * 4 + 4; zz++) {
                        pos.set(xx, yy, zz);
                        BlockState state = chunk.getBlockState(pos);
                        if (!state.isAir()) {
                           if (state.isCollisionShapeFullBlock(level, pos)) {
                              fullBlocks |= 1L << xx - x * 4 + 4 * (zz - z * 4) + 16 * (yy - bottom);
                           } else {
                              List<AABB> geometry = SmokeGeometry.local(level, pos);
                              if (geometry.size() == 1 && isFullCube(geometry.get(0))) {
                                 fullBlocks |= 1L << xx - x * 4 + 4 * (zz - z * 4) + 16 * (yy - bottom);
                              } else {
                                 for (AABB local : geometry) {
                                    boxes.add(local.move(xx, yy, zz));
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               for (AABB solid : solidCuboids(fullBlocks)) {
                  AABB moved = solid.move(x * 4, bottom, z * 4);
                  boxes.add(moved);
               }
            }
         }

         tile = new SmokeFlow.Tile(level.getGameTime(), chunk, List.copyOf(boxes));
         TILES.put(key, tile);
      }

      return tile;
   }

   private static boolean isFullCube(AABB b) {
      return b.minX == 0.0 && b.minY == 0.0 && b.minZ == 0.0 && b.maxX == 1.0 && b.maxY == 1.0 && b.maxZ == 1.0;
   }

   public static List<AABB> solidCuboids(long occupied) {
      ArrayList<AABB> boxes = new ArrayList<>();

      while (occupied != 0L) {
         int index = Long.numberOfTrailingZeros(occupied);
         int x = index & 3;
         int z = index >> 2 & 3;
         int y = index >> 4;
         int width = 1;
         int depth = 1;
         int height = 1;

         while (x + width < 4 && filled(occupied, x + width, z, y)) {
            width++;
         }

         while (z + depth < 4 && filledRectangle(occupied, x, z + depth, y, width, 1)) {
            depth++;
         }

         while (y + height < 4 && filledRectangle(occupied, x, z, y + height, width, depth)) {
            height++;
         }

         for (int yy = y; yy < y + height; yy++) {
            for (int zz = z; zz < z + depth; zz++) {
               for (int xx = x; xx < x + width; xx++) {
                  occupied &= ~(1L << xx + 4 * zz + 16 * yy);
               }
            }
         }

         boxes.add(new AABB(x, y, z, x + width, y + height, z + depth));
      }

      return boxes;
   }

   private static boolean filled(long occupied, int x, int z, int y) {
      return (occupied & 1L << x + 4 * z + 16 * y) != 0L;
   }

   private static boolean filledRectangle(long occupied, int x, int z, int y, int width, int depth) {
      for (int zz = z; zz < z + depth; zz++) {
         for (int xx = x; xx < x + width; xx++) {
            if (!filled(occupied, xx, zz, y)) {
               return false;
            }
         }
      }

      return true;
   }

   public static Vec3 constrainDrift(ClientLevel level, Vec3 origin, Vec3 target) {
      Vec3 delta = target.subtract(origin);
      Vec3 position = origin;
      List<AABB> boxes = obstacles(level, new AABB(origin, target).inflate(0.00390625));

      for (int i = 0; i < 3; i++) {
         SmokeFlow.Hit first = null;

         for (AABB b : boxes) {
            SmokeFlow.Hit h = hit(position, delta, b);
            if (h != null && (first == null || h.fraction < first.fraction)) {
               first = h;
            }
         }

         if (first == null) {
            return position.add(delta);
         }

         position = position.add(delta.scale(Math.max(0.0, first.fraction - 1.0E-5)));
         delta = delta.scale(1.0 - first.fraction);
         delta = delta.subtract(first.normal.scale(delta.dot(first.normal)));
      }

      return position;
   }

   public static SmokeFlow.Hit hit(Vec3 origin, Vec3 delta, AABB solid) {
      AABB b = solid.inflate(0.00390625);
      double enter = 0.0;
      double exit = 1.0;
      Vec3 normal = Vec3.ZERO;

      for (int axis = 0; axis < 3; axis++) {
         double p = SmokeGeometry.coordinate(origin, axis);
         double v = SmokeGeometry.coordinate(delta, axis);
         double low = SmokeGeometry.min(b, axis);
         double high = SmokeGeometry.max(b, axis);
         if (Math.abs(v) < 1.0E-12) {
            if (p <= low || p >= high) {
               return null;
            }
         } else {
            double a = (low - p) / v;
            double c = (high - p) / v;
            double near = Math.min(a, c);
            double far = Math.max(a, c);
            if (near >= enter) {
               enter = near;
               double sign = v > 0.0 ? -1.0 : 1.0;
               normal = new Vec3(axis == 0 ? sign : 0.0, axis == 1 ? sign : 0.0, axis == 2 ? sign : 0.0);
            }

            exit = Math.min(exit, far);
            if (enter > exit) {
               return null;
            }
         }
      }

      return !(enter < 0.0) && !(enter > 1.0) && normal.lengthSqr() != 0.0 ? new SmokeFlow.Hit(enter, normal) : null;
   }

   public static void advance(ClientLevel level, SmokeFlow.Parcel parcel, double age, int seed) {
      double remaining = Math.max(0.0, age - parcel.seconds);

      while (remaining > 1.0E-7) {
         double dt = Math.min(0.03333333333333333, remaining);
         boolean enclosed = false;

         for (AABB box : parcelObstacles(level, parcel, new AABB(parcel.position, parcel.position).inflate(0.00390625))) {
            if (box.contains(parcel.position)) {
               enclosed = true;
               break;
            }
         }

         if (enclosed) {
            parcel.velocity = Vec3.ZERO;
            parcel.seconds += remaining;
            return;
         }

         Vec3 trial = parcel.position.add(parcel.velocity.scale(dt));
         boolean constricted = false;

         for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(parcel.position), BlockPos.containing(trial))) {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.isCollisionShapeFullBlock(level, pos) && !SmokeGeometry.local(level, pos).isEmpty()) {
               constricted = true;
               break;
            }
         }

         if (constricted && parcel.velocity.lengthSqr() > 1.0E-6) {
            parcel.velocity = parcel.velocity.normalize().scale(0.45);
         }

         double drag = parcel.touched ? 0.38 : 1.8;
         Vec3 displacement = parcel.velocity.scale((1.0 - Math.exp(-drag * dt)) / drag);

         for (int contact = 0; contact < 3 && displacement.lengthSqr() > 1.0E-14; contact++) {
            List<AABB> boxes = parcelObstacles(level, parcel, new AABB(parcel.position, parcel.position.add(displacement)).inflate(0.08));
            SmokeFlow.Hit first = null;

            for (AABB boxx : boxes) {
               SmokeFlow.Hit h = hit(parcel.position, displacement, boxx);
               if (h != null && (first == null || h.fraction < first.fraction)) {
                  first = h;
               }
            }

            if (first == null) {
               parcel.position = parcel.position.add(displacement);
               break;
            }

            parcel.position = parcel.position.add(displacement.scale(Math.max(0.0, first.fraction - 1.0E-5)));
            Vec3 n = first.normal;
            double impact = Math.max(0.0, -parcel.velocity.dot(n));
            Vec3 tangent = parcel.velocity.subtract(n.scale(parcel.velocity.dot(n)));
            if (tangent.lengthSqr() < 0.1) {
               double angle = seed * 2.399963229728653;
               tangent = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
               tangent = tangent.subtract(n.scale(tangent.dot(n)));
               if (tangent.lengthSqr() < 0.01) {
                  tangent = new Vec3(0.0, 1.0, 0.0);
               }
            }

            double spread = Math.min(10.0, impact * 0.65);
            double swirl = seed * 2.399963229728653;
            Vec3 radial = new Vec3(Math.cos(swirl), 0.25 * Math.sin(swirl * 1.7), Math.sin(swirl));
            radial = radial.subtract(n.scale(radial.dot(n))).normalize();
            tangent = tangent.add(radial.scale(spread));
            parcel.velocity = tangent.normalize().scale(Math.min(12.0, tangent.length() + spread)).add(n.scale(n.y > 0.5 ? 0.65 : 0.05));
            parcel.touched = true;
            displacement = parcel.velocity.scale(dt * (1.0 - first.fraction));
         }

         parcel.velocity = parcel.velocity.scale(Math.exp(-drag * dt)).add(0.025 * dt, (parcel.touched ? 0.28 : 0.12) * dt, 0.008 * dt);
         if (parcel.touched) {
            parcel.velocity = parcel.velocity.add(parcel.pressure.scale(dt));
         }

         parcel.seconds += dt;
         remaining -= dt;
      }
   }

   public static double clearance(Vec3 center, List<AABB> boxes, double maximum) {
      double squared = maximum * maximum;

      for (AABB b : boxes) {
         double x = Math.max(Math.max(b.minX - center.x, 0.0), center.x - b.maxX);
         double y = Math.max(Math.max(b.minY - center.y, 0.0), center.y - b.maxY);
         double z = Math.max(Math.max(b.minZ - center.z, 0.0), center.z - b.maxZ);
         squared = Math.min(squared, x * x + y * y + z * z);
      }

      return Math.sqrt(squared);
   }

   public static float safeRadius(ClientLevel level, Vec3 center, float requested) {
      if (world != level) {
         clear();
         world = level;
      }

      if (TILES.size() > 65536) {
         TILES.clear();
      }

      double envelope = requested * 1.75;
      double nearest = envelope;
      if (!(center.y - envelope > level.getMaxBuildHeight() + 0.00390625) && !(center.y + envelope < level.getMinBuildHeight() - 0.00390625)) {
         int tx = Math.floorDiv((int)Math.floor(center.x), 4);
         int ty = Math.floorDiv((int)Math.floor(center.y), 4);
         int tz = Math.floorDiv((int)Math.floor(center.z), 4);
         int shells = (int)Math.ceil(envelope / 4.0) + 1;

         for (int shell = 0; shell <= shells; shell++) {
            for (int x = tx - shell; x <= tx + shell; x++) {
               for (int y = ty - shell; y <= ty + shell; y++) {
                  for (int z = tz - shell; z <= tz + shell; z++) {
                     if (Math.max(Math.max(Math.abs(x - tx), Math.abs(y - ty)), Math.abs(z - tz)) == shell) {
                        double dx = Math.max(Math.max(x * 4 - center.x, 0.0), center.x - (x * 4 + 4));
                        double dy = Math.max(Math.max(y * 4 - center.y, 0.0), center.y - (y * 4 + 4));
                        double dz = Math.max(Math.max(z * 4 - center.z, 0.0), center.z - (z * 4 + 4));
                        if (!(dx * dx + dy * dy + dz * dz >= nearest * nearest)) {
                           nearest = clearance(center, tile(level, x, y, z).boxes, nearest);
                        }
                     }
                  }
               }
            }
         }

         return (float)Math.min((double)requested, Math.max(0.0, nearest - 1.0E-4) / 1.75);
      } else {
         return requested;
      }
   }

   private SmokeFlow() {
   }

   public record Hit(double fraction, Vec3 normal) {
   }

   public static final class Parcel {
      public Vec3 position;
      public Vec3 velocity;
      public final Vec3 pressure;
      public double seconds;
      public boolean touched;
      private AABB cachedBounds;
      private List<AABB> cachedBoxes;
      private long cachedRevision = -1L;
      private long cachedTick = -1L;

      public Parcel(Vec3 position, Vec3 velocity) {
         this.position = position;
         this.velocity = velocity;
         this.pressure = velocity.normalize().scale(0.35);
      }
   }

   private record SectionSnapshot(LevelChunk chunk, LevelChunkSection section, SmokeFlow.Tile tile) {
   }

   private record Tile(long tick, LevelChunk chunk, List<AABB> boxes) {
   }
}
