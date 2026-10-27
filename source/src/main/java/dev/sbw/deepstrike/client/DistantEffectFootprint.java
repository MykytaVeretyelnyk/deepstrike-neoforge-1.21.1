package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

final class DistantEffectFootprint {
   static double radius(double distance, double pixels) {
      Minecraft mc = Minecraft.getInstance();
      double projection = Math.abs(RenderSystem.getProjectionMatrix().m11());
      double height = mc.getMainRenderTarget().height;
      if (!(height < 1.0) && !(projection < 0.01)) {
         double transition = Mth.clamp((distance - 512.0) / 1536.0, 0.0, 1.0);
         transition = transition * transition * (3.0 - 2.0 * transition);
         return distance * pixels * 2.0 / (height * projection) * transition;
      } else {
         return 0.0;
      }
   }

   private DistantEffectFootprint() {
   }
}
