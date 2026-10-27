package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.game.AirRaidSirenBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class AirRaidSirenRenderer implements BlockEntityRenderer<AirRaidSirenBlockEntity> {
   public AirRaidSirenRenderer(Context context) {
   }

   public void render(AirRaidSirenBlockEntity siren, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
      String model = "siren_tower";
      pose.pushPose();
      pose.translate(0.5, 0.0, 0.5);
      ModelRenderer.render(model, model, pose, buffers, light, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, bone -> true);
      pose.popPose();
   }

   public boolean shouldRenderOffScreen(AirRaidSirenBlockEntity siren) {
      return true;
   }
}
