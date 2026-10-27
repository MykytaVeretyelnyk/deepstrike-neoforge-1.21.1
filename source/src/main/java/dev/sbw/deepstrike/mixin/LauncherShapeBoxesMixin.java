package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.game.LauncherShapeCache;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {VoxelShape.class},
   remap = false
)
public abstract class LauncherShapeBoxesMixin {
   @Inject(
      method = {"toAabbs"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void deepstrike$launcherBoxes(CallbackInfoReturnable<List<AABB>> result) {
      List<AABB> boxes = LauncherShapeCache.boxes((VoxelShape)(Object)this);
      if (boxes != null) {
         result.setReturnValue(boxes);
      }
   }
}
