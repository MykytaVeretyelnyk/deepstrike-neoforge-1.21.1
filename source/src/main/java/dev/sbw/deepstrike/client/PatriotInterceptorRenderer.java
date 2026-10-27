package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.ModelGeometry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class PatriotInterceptorRenderer extends EntityRenderer<PatriotInterceptorEntity> {
   public PatriotInterceptorRenderer(Context context) {
      super(context);
      this.shadowRadius = 0.0F;
   }

   public boolean shouldRender(PatriotInterceptorEntity entity, Frustum frustum, double x, double y, double z) {
      return !PatriotRemoteEffects.hidesTracked(entity) && super.shouldRender(entity, frustum, x, y, z);
   }

   public ResourceLocation getTextureLocation(PatriotInterceptorEntity entity) {
      return ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/" + (entity.warhead() == 3 ? "tamir" : (entity.warhead() == 0 ? "pac_2" : "pac_3")) + ".png");
   }

   public void render(PatriotInterceptorEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
      pose.pushPose();
      pose.mulPose(FlightOrientation.interpolate(entity.previousYaw, entity.previousPitch, entity.yaw(), entity.pitch(), partial));
      float axial = InterceptorGeometry.axialScale(entity.warhead());
      float radial = InterceptorGeometry.radialScale(entity.warhead());
      String model = InterceptorGeometry.model(entity.warhead());
      pose.pushPose();
      pose.scale(axial, radial, radial);
      ModelRenderer.render(model, model, pose, buffers, light, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, b -> true);
      pose.popPose();
      float age = entity.tickCount + partial;
      if (!PatriotRemoteEffects.renderingNativeContact()) {
         ExhaustPlume.renderSam(pose, buffers, age, ModelGeometry.get(model).tailX * axial, entity.warhead(), age);
      }

      pose.popPose();
      super.render(entity, yaw, partial, pose, buffers, light);
   }
}
