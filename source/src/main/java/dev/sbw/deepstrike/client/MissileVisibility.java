package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.SmokeVisibility;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

final class MissileVisibility {
   static final double SENSOR_RANGE = 8000.0;
   private static ClientLevel terrainWorld;
   private static final Map<Long, MissileVisibility.Ground> ground = new HashMap<>();

   static float opacity(double distance) {
      double t = Mth.clamp((distance - 2500.0) / 2500.0, 0.0, 1.0);
      return (float)((1.0 - t) * (1.0 - t));
   }

   static float engineOpacity(double distance) {
      double t = Mth.clamp((distance - 4000.0) / 1000.0, 0.0, 1.0);
      return (float)(1.0 - t * t * (3.0 - 2.0 * t));
   }

   static float sensorOpacity(double distance) {
      double t = Mth.clamp((distance - 7500.0) / 500.0, 0.0, 1.0);
      return (float)(1.0 - t * t * (3.0 - 2.0 * t));
   }

   static float trailOpacity(Vec3 position, double distance, double fallbackGround) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         return 0.0F;
      } else {
         if (terrainWorld != level) {
            ground.clear();
            terrainWorld = level;
         }

         int x = Mth.floor(position.x);
         int z = Mth.floor(position.z);
         long key = BlockPos.asLong(x >> 4, 0, z >> 4);
         long now = level.getGameTime();
         MissileVisibility.Ground sample = ground.get(key);
         if (sample == null || now - sample.tick > 20L) {
            ChunkAccess chunk = level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false);
            if (chunk != null) {
               sample = new MissileVisibility.Ground((double)chunk.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15), now);
               if (ground.size() >= 4096) {
                  ground.clear();
               }

               ground.put(key, sample);
            } else {
               sample = null;
            }
         }

         double terrain = sample == null ? fallbackGround : sample.height;
         return SmokeVisibility.opacity(distance, position.y - terrain);
      }
   }

   static float trailOpacity(Vec3 position, double distance) {
      ClientLevel level = Minecraft.getInstance().level;
      return trailOpacity(position, distance, level == null ? 63.0 : level.getSeaLevel());
   }

   private MissileVisibility() {
   }

   private record Ground(double height, long tick) {
   }
}
