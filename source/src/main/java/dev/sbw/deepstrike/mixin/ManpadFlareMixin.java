package dev.sbw.deepstrike.mixin;

import com.atsuishio.superbwarfare.entity.projectile.BasicGeoProjectileEntity;
import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.client.PatriotRemoteEffects;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"com.atsuishio.superbwarfare.client.renderer.projectile.BasicProjectileRenderer"},
   remap = false
)
public abstract class ManpadFlareMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 1
   )
   private void deepstrike$finalContactPose(Entity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
      if (entity instanceof MissileProjectile missile && PatriotRemoteEffects.hidesNativeBody(missile)) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"render"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/atsuishio/superbwarfare/entity/projectile/BasicGeoProjectileEntity;getFlareHiddenTicks()I"
      ),
      require = 1
   )
   private int deepstrike$sharedFlame(BasicGeoProjectileEntity projectile) {
      return !(projectile instanceof Fim92MissileEntity) && !(projectile instanceof IglaMissileEntity) ? projectile.getFlareHiddenTicks() : Integer.MAX_VALUE;
   }
}
