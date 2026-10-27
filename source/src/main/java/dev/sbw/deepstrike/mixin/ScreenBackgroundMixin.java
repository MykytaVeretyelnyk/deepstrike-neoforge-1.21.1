package dev.sbw.deepstrike.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21 draws a blurred menu background from inside Screen.render, after DeepStrike screens
 * have already painted their panels. DeepStrike screens draw their own backdrop (as in 1.20),
 * so the vanilla background is skipped for them.
 */
@Mixin(value = Screen.class, remap = false)
public abstract class ScreenBackgroundMixin {
   @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
   private void deepstrike$skipVanillaBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (this.getClass().getName().startsWith("dev.sbw.deepstrike.")) {
         ci.cancel();
      }
   }
}
