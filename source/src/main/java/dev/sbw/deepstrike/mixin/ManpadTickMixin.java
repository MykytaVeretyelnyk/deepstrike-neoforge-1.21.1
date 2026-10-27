package dev.sbw.deepstrike.mixin;

import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.tools.RangeTool;
import dev.sbw.deepstrike.client.PatriotRemoteEffects;
import dev.sbw.deepstrike.game.ManpadFlight;
import dev.sbw.deepstrike.game.ManpadPresentation;
import dev.sbw.deepstrike.model.FlightOrientation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"com.atsuishio.superbwarfare.entity.projectile.Ru9m336MissileEntity", "com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity"},
   remap = false
)
public abstract class ManpadTickMixin {
   @Redirect(
      method = {"tick"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/atsuishio/superbwarfare/tools/RangeTool;calculateFiringSolution(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;DD)Lnet/minecraft/world/phys/Vec3;"
      ),
      require = 1
   )
   private Vec3 deepstrike$singleGuidance(Vec3 from, Vec3 target, Vec3 velocity, double speed, double gravity) {
      return (Object)this instanceof MissileProjectile missile && ManpadFlight.controlsNativeCollision(missile)
         ? missile.getDeltaMovement()
         : RangeTool.calculateFiringSolution(from, target, velocity, speed, gravity);
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 1
   )
   private void deepstrike$reconcilePosition(CallbackInfo ci) {
      if ((Object)this instanceof MissileProjectile missile && !missile.level().isClientSide && !ManpadFlight.beginTick(missile)) {
         ci.cancel();
      } else if ((Object)this instanceof MissileProjectile missile && missile.level().isClientSide && PatriotRemoteEffects.hidesNativeBody(missile)) {
         missile.setDeltaMovement(Vec3.ZERO);
         ci.cancel();
      } else {
         if ((Object)this instanceof MissileProjectile missile && ManpadPresentation.controls(missile)) {
            ManpadPresentation.prepare(missile);
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/atsuishio/superbwarfare/entity/projectile/MissileProjectile;tick()V",
         shift = Shift.AFTER
      ), @At(
         value = "INVOKE",
         target = "Lcom/atsuishio/superbwarfare/entity/projectile/MissileProjectile;tick()V",
         shift = Shift.AFTER
      )},
      cancellable = true,
      require = 1
   )
   private void deepstrike$clientAuthoritativeMotion(CallbackInfo ci) {
      if ((Object)this instanceof MissileProjectile missile
         && (missile instanceof Fim92MissileEntity || missile instanceof IglaMissileEntity)
         && missile.level().isClientSide) {
         if (missile.isAlive()) {
            FlightOrientation.Angles angles = FlightOrientation.along(missile.getDeltaMovement(), missile.getYRot(), missile.getXRot());
            missile.setYRot(angles.yaw());
            missile.setXRot(angles.pitch());
         }

         ci.cancel();
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("RETURN")},
      require = 1
   )
   private void deepstrike$restoreDynamicsBeforeTracking(CallbackInfo ci) {
      if ((Object)this instanceof MissileProjectile missile
         && (missile instanceof Fim92MissileEntity || missile instanceof IglaMissileEntity)
         && !missile.level().isClientSide) {
         ManpadFlight.finish(missile);
      }
   }
}
