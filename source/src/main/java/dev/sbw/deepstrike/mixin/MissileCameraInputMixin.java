package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.client.ClientCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Entity.class},
   remap = false
)
public abstract class MissileCameraInputMixin {
   @Inject(
      method = {"turn"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void deepstrike$freeLook(double dx, double dy, CallbackInfo ci) {
      if ((Object)this == Minecraft.getInstance().player && ClientCamera.turn(dx, dy)) {
         ci.cancel();
      }
   }
}
