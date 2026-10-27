package dev.sbw.deepstrike.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.sbw.deepstrike.client.UiViewport;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   value = {MouseHandler.class},
   remap = false
)
public abstract class ResponsiveMouseMixin {
   @Redirect(
      method = {"onPress", "onScroll", "onMove"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"
      ),
      require = 0
   )
   @Group(
      name = "dimensions",
      min = 2
   )
   private int deepstrike$width(Window window) {
      return UiViewport.inputWidth();
   }

   @Redirect(
      method = {"onPress", "onScroll", "onMove"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"
      ),
      require = 0
   )
   @Group(
      name = "dimensions",
      min = 2
   )
   private int deepstrike$height(Window window) {
      return UiViewport.inputHeight();
   }
}
