package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.game.ManpadPresentation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Entity.class},
   remap = false
)
public abstract class ManpadPositionMixin {
   @Inject(
      method = {"lerpTo"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 1
   )
   private void deepstrike$position(double x, double y, double z, float yaw, float pitch, int steps, CallbackInfo ci) {
      Entity entity = ((Entity)(Object)this);
      if (ManpadPresentation.controls(entity)) {
         ManpadPresentation.position(entity, new Vec3(x, y, z));
         ci.cancel();
      }
   }
}
