package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.client.renderer.entity.VehicleRenderer;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.util.RenderUtil;

public class AirDefenseVehicleRenderer<T extends VehicleEntity & GeoAnimatable> extends VehicleRenderer<T> {
   public AirDefenseVehicleRenderer(Context context) {
      super(context, new AirDefenseVehicleModel());
   }

   public RenderType getRenderType(T animatable, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
      return RenderType.entityCutoutNoCull(this.getTextureLocation(animatable));
   }

   public void renderRecursively(
      PoseStack poseStack,
      T animatable,
      GeoBone bone,
      RenderType renderType,
      MultiBufferSource bufferSource,
      VertexConsumer bufferIn,
      boolean isReRender,
      float partialTick,
      int packedLight,
      int packedOverlay,
      int colour
   ) {
      if (animatable instanceof PatriotTruckEntity truck && bone.getName().startsWith("support_")) {
         applySupportPose(bone, truck, partialTick);
      }

      if (bone.getName().startsWith("glass_")) {
         RenderType glass = RenderType.entityCutoutNoCull(this.getTextureLocation(animatable));
         super.renderRecursively(
            poseStack,
            animatable,
            bone,
            glass,
            bufferSource,
            bufferSource.getBuffer(glass),
            isReRender,
            partialTick,
            packedLight,
            packedOverlay,
            colour
         );
      } else {
         super.renderRecursively(
            poseStack, animatable, bone, renderType, bufferSource, bufferIn, isReRender, partialTick, packedLight, packedOverlay, colour
         );
      }

      if (bone.getName().equals("tractor") && animatable instanceof PatriotTruckEntity truck) {
         poseStack.pushPose();

         try {
            RenderUtil.prepMatrixForBone(poseStack, bone);
            PatriotCabMonitor.render(poseStack, bufferSource, truck, packedOverlay);
         } finally {
            poseStack.popPose();
         }
      }

      if (bone.getName().equals("launcher") && animatable instanceof PatriotTruckEntity truck) {
         PatriotCanisterRenderer.render(poseStack, bufferSource, bone, truck, partialTick, packedLight, packedOverlay);
      } else if (bone.getName().equals("elevation") && animatable instanceof IronDomeEntity ironDome) {
         IronDomeCanisterRenderer.render(poseStack, bufferSource, bone, ironDome, partialTick, packedLight, packedOverlay);
      }
   }

   private static void applySupportPose(GeoBone bone, PatriotTruckEntity truck, float partial) {
      float ticks = truck.getDeployTicks() + partial;

      float timeline = switch (truck.getDeployState()) {
         case 1 -> ticks;
         case 2 -> 160.0F;
         case 3 -> 160.0F - ticks;
         default -> 0.0F;
      };
      float slide = smooth(timeline / 40.0F);
      float lower = smooth((timeline - 40.0F) / 40.0F);
      String name = bone.getName();
      int side = name.startsWith("support_-1_") ? -1 : 1;
      if (name.endsWith("_slide")) {
         bone.setPosX(-side * 11.2F * slide);
      } else if (name.endsWith("_foot")) {
         bone.setPosY(-8.8F * lower);
      } else if (name.endsWith("_piston")) {
         bone.setScaleY(1.0F + 0.92436975F * lower);
      } else if (name.endsWith("_beam")) {
         bone.setScaleX(1.0F + 1.4736842F * slide);
      }
   }

   private static float smooth(float value) {
      float t = Mth.clamp(value, 0.0F, 1.0F);
      return t * t * (3.0F - 2.0F * t);
   }
}
