package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.client.UiViewport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Screen.class},
   remap = false
)
public abstract class ResponsiveScreenMixin {
   @ModifyVariable(
      method = {"init(Lnet/minecraft/client/Minecraft;II)V", "resize"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int deepstrike$width(int value) {
      return UiViewport.applies(this) ? UiViewport.width() : value;
   }

   @ModifyVariable(
      method = {"init(Lnet/minecraft/client/Minecraft;II)V", "resize"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int deepstrike$height(int value) {
      return UiViewport.applies(this) ? UiViewport.height() : value;
   }

   @ModifyVariable(
      method = {"renderWithTooltip"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int deepstrike$mouseX(int value) {
      return UiViewport.applies(this) ? (int)(value / UiViewport.scale()) : value;
   }

   @ModifyVariable(
      method = {"renderWithTooltip"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int deepstrike$mouseY(int value) {
      return UiViewport.applies(this) ? (int)(value / UiViewport.scale()) : value;
   }

   @Inject(
      method = {"renderWithTooltip"},
      at = {@At("HEAD")}
   )
   private void deepstrike$begin(GuiGraphics g, int x, int y, float partial, CallbackInfo ci) {
      if (UiViewport.applies(this)) {
         UiViewport.begin(g);
      }
   }

   @Inject(
      method = {"renderWithTooltip"},
      at = {@At("RETURN")}
   )
   private void deepstrike$end(GuiGraphics g, int x, int y, float partial, CallbackInfo ci) {
      if (UiViewport.applies(this)) {
         UiViewport.end(g);
      }
   }
}
