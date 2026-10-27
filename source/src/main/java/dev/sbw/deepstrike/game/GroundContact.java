package dev.sbw.deepstrike.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class GroundContact {
   // ClipContext rejects a null entity in 1.21 (CollisionContext.of(null) throws), so trace by hand.
   public static BlockHitResult below(Level level, Vec3 point) {
      Vec3 from = point.add(0.0, 0.02, 0.0);
      Vec3 to = new Vec3(point.x, level.getMinBuildHeight() - 0.5, point.z);
      return BlockGetter.traverseBlocks(
         from,
         to,
         level,
         (world, pos) -> {
            var state = world.getBlockState(pos);
            return world.clipWithInteractionOverride(from, to, pos, state.getCollisionShape(world, pos, CollisionContext.empty()), state);
         },
         world -> BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to))
      );
   }

   private GroundContact() {
   }
}
