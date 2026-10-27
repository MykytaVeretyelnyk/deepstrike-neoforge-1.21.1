package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.Fp5BoosterEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.ModelAssets;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

final class Fp5BoosterRenderer extends EntityRenderer<Fp5BoosterEntity> {
   Fp5BoosterRenderer(Context c) {
      super(c);
      this.shadowRadius = 0.0F;
   }

   public ResourceLocation getTextureLocation(Fp5BoosterEntity e) {
      return ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/" + ModelAssets.texture(MissileKind.FP5, e.camo(), false) + ".png");
   }

   public boolean shouldRender(Fp5BoosterEntity entity, Frustum frustum, double x, double y, double z) {
      return super.shouldRender(entity, frustum, x, y, z) || frustum.isVisible(entity.getBoundingBox().inflate(16.0));
   }

   public void render(Fp5BoosterEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
      pose.pushPose();
      pose.mulPose(FlightOrientation.interpolate(e.yRotO, e.xRotO, e.getYRot(), e.getXRot(), partial));
      float roll = e.getRoll(partial);
      float pitchWobble = e.getPitchWobble(partial);
      pose.mulPose(Axis.XP.rotationDegrees(roll));
      pose.mulPose(Axis.ZP.rotationDegrees(pitchWobble));
      pose.translate(-Fp5BoosterEntity.CENTER.x, -Fp5BoosterEntity.CENTER.y, -Fp5BoosterEntity.CENTER.z);
      ModelRenderer.render(
         "fp5_flamingo",
         ModelAssets.texture(MissileKind.FP5, e.camo(), false),
         pose,
         buffers,
         light,
         OverlayTexture.NO_OVERLAY,
         0.0F,
         0.0F,
         b -> b.equals("booster")
      );
      pose.popPose();
   }
}
