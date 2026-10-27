package dev.sbw.deepstrike.game;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class ContactSurface {
   static Vec3 find(AABB obstacle, AABB movedBody, Vec3 movement) {
      double x = (Math.max(obstacle.minX, movedBody.minX) + Math.min(obstacle.maxX, movedBody.maxX)) * 0.5;
      double y = (Math.max(obstacle.minY, movedBody.minY) + Math.min(obstacle.maxY, movedBody.maxY)) * 0.5;
      double z = (Math.max(obstacle.minZ, movedBody.minZ) + Math.min(obstacle.maxZ, movedBody.maxZ)) * 0.5;
      double[] gaps = new double[]{
         Math.abs(movedBody.maxX - obstacle.minX),
         Math.abs(movedBody.minX - obstacle.maxX),
         Math.abs(movedBody.maxY - obstacle.minY),
         Math.abs(movedBody.minY - obstacle.maxY),
         Math.abs(movedBody.maxZ - obstacle.minZ),
         Math.abs(movedBody.minZ - obstacle.maxZ)
      };
      int face = 0;

      for (int i = 1; i < 6; i++) {
         if (gaps[i] < gaps[face]) {
            face = i;
         }
      }

      switch (face) {
         case 0:
            x = obstacle.minX;
            break;
         case 1:
            x = obstacle.maxX;
            break;
         case 2:
            y = obstacle.minY;
            break;
         case 3:
            y = obstacle.maxY;
            break;
         case 4:
            z = obstacle.minZ;
            break;
         case 5:
            z = obstacle.maxZ;
      }

      return new Vec3(
         Mth.clamp(x, obstacle.minX, obstacle.maxX),
         Mth.clamp(y, obstacle.minY, obstacle.maxY),
         Mth.clamp(z, obstacle.minZ, obstacle.maxZ)
      );
   }

   private ContactSurface() {
   }
}
