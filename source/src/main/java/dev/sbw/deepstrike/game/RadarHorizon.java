package dev.sbw.deepstrike.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class RadarHorizon {
   public static double height(Entity target, Entity radar) {
      BlockPos pos = target.blockPosition();
      ChunkAccess chunk = target.level().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
      double ground = chunk == null ? radar.getY() : chunk.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, pos.getX() & 15, pos.getZ() & 15) + 1;
      return target.getY() - ground;
   }

   public static boolean visible(Entity target, Entity radar) {
      return !(target instanceof GuidedMissileEntity) || height(target, radar) >= 2.0;
   }

   private RadarHorizon() {
   }
}
