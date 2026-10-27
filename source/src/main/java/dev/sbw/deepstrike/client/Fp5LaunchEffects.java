package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

final class Fp5LaunchEffects {
   static int previewVariant;

   static int selected() {
      return previewVariant > 0 ? previewVariant : 2;
   }

   static void render(float age, PoseStack pose, MultiBufferSource buffers) {
      if (!(age >= 90.0F)) {
         pose.pushPose();
         pose.translate(0.75, -0.478125, 0.0);
         Fp5BoosterFire.render(pose, buffers, age, selected());
         pose.popPose();
      }
   }

   private Fp5LaunchEffects() {
   }
}
