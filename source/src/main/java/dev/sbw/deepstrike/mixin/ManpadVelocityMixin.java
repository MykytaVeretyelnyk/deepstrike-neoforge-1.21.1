package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.game.ManpadPresentation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Projectile.class},
   remap = false
)
public abstract class ManpadVelocityMixin {
   @Inject(
      method = {"lerpMotion"},
      at = {@At("HEAD")},
      require = 1
   )
   private void deepstrike$velocity(double x, double y, double z, CallbackInfo ci) {
      Entity entity = ((Entity)(Object)this);
      if (ManpadPresentation.controls(entity)) {
         ManpadPresentation.velocity(entity, new Vec3(x, y, z));
      }
   }
}
