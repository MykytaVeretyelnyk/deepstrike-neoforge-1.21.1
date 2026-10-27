package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.client.smoke.SmokeFlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {LevelChunk.class},
   remap = false
)
public abstract class SmokeChunkChangeMixin {
   @Inject(
      method = {"setBlockState"},
      at = {@At("RETURN")}
   )
   private void deepstrike$smokeChanged(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> result) {
      LevelChunk chunk = ((LevelChunk)(Object)this);
      if (chunk.getLevel() instanceof ClientLevel level && result.getReturnValue() != null && result.getReturnValue() != state) {
         SmokeFlow.invalidateBlock(level, pos);
      }
   }
}
