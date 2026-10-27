package dev.sbw.deepstrike.compat.mixin;

import dev.sbw.deepstrike.game.GuidedMissileEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Optional: only transformed when SBW Advanced CIWS is installed. require = 0 keeps a CIWS update from crashing the game.
@Mixin(targets = "com.sbwadvancedciws.integration.SBWDroneDetector", remap = false)
public abstract class CiwsDetectorMixin {
   @Inject(method = {"isSBWDrone"}, at = {@At("RETURN")}, cancellable = true, require = 0)
   private static void deepstrike$missilesAreTargets(Entity entity, CallbackInfoReturnable<Boolean> cir) {
      if (!cir.getReturnValueZ() && entity instanceof GuidedMissileEntity missile && missile.isAlive() && !missile.isIntercepted()) {
         cir.setReturnValue(true);
      }
   }
}
