package dev.sbw.deepstrike.client.smoke;

import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SmokeGeometry {
   private static final List<AABB> BLOCK = List.of(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));

   public static List<AABB> local(ClientLevel level, BlockPos pos) {
      BlockState state = level.getBlockState(pos);
      return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty() ? BLOCK : List.of();
   }

   public static void clear() {
   }

   public static double coordinate(Vec3 p, int axis) {
      return axis == 0 ? p.x : (axis == 1 ? p.y : p.z);
   }

   public static double min(AABB b, int axis) {
      return axis == 0 ? b.minX : (axis == 1 ? b.minY : b.minZ);
   }

   public static double max(AABB b, int axis) {
      return axis == 0 ? b.maxX : (axis == 1 ? b.maxY : b.maxZ);
   }

   private SmokeGeometry() {
   }
}
