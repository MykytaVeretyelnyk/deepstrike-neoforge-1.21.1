package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.client.UiViewport;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(
   value = {GuiGraphics.class},
   remap = false
)
public abstract class ResponsiveScissorMixin {
   @ModifyVariable(
      method = {"enableScissor"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private int deepstrike$x1(int v) {
      return UiViewport.scissor(v);
   }

   @ModifyVariable(
      method = {"enableScissor"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private int deepstrike$y1(int v) {
      return UiViewport.scissor(v);
   }

   @ModifyVariable(
      method = {"enableScissor"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 2
   )
   private int deepstrike$x2(int v) {
      return UiViewport.scissor(v);
   }

   @ModifyVariable(
      method = {"enableScissor"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 3
   )
   private int deepstrike$y2(int v) {
      return UiViewport.scissor(v);
   }
}
