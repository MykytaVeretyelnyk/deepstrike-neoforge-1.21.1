package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance;
import com.atsuishio.superbwarfare.client.renderer.entity.GeoVehicleRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

public final class Su24mRenderer extends GeoVehicleRenderer<Su24mEntity> {
   private static final String[] GEAR_BONES = new String[]{"gear_nose", "gear_main_r", "gear_main_l"};
   private Matrix4f reviewBase;

   public Su24mRenderer(Context context) {
      super(context);
   }

   public void transformCustomModelPart(Su24mEntity plane, VehicleModelInstance instance, PoseStack pose, float age, float partial) {
      super.transformCustomModelPart(plane, instance, pose, age, partial);
      float sweep = plane.wingSweepProgress(partial);
      BoneState wingR = instance.getBone("wing_r");
      BoneState wingL = instance.getBone("wing_l");
      if (wingR != null) {
         wingR.rotation.rotateY((float)Math.toRadians(-53.0) * sweep);
      }

      if (wingL != null) {
         wingL.rotation.rotateY((float)Math.toRadians(53.0) * sweep);
      }

      float progress = plane.landingGearProgress(partial);
      float fold = progress * progress * (3.0F - 2.0F * progress);

      for (String name : GEAR_BONES) {
         BoneState bone = instance.getBone(name);
         if (bone != null) {
            bone.visible = progress < 0.999F;
            if (name.equals("gear_nose")) {
               bone.rotation.rotateX((float)Math.toRadians(-88.0) * fold);
            } else {
               bone.rotation.rotateX((float)Math.toRadians(82.0) * fold);
            }
         }
      }

      float doorProgress = Math.min(1.0F, Math.max(0.0F, (progress - 0.15F) / 0.85F));
      float doorFold = doorProgress * doorProgress * (3.0F - 2.0F * doorProgress);
      BoneState doorR = instance.getBone("door_main_r");
      if (doorR != null) {
         doorR.visible = true;
         doorR.rotation.rotateZ((float)Math.toRadians(-90.0) * doorFold);
      }

      BoneState doorL = instance.getBone("door_main_l");
      if (doorL != null) {
         doorL.visible = true;
         doorL.rotation.rotateZ((float)Math.toRadians(90.0) * doorFold);
      }

      BoneState noseR = instance.getBone("door_nose_r");
      if (noseR != null) {
         noseR.visible = true;
         noseR.rotation.rotateZ((float)Math.toRadians(-80.0) * doorFold);
      }

      BoneState noseL = instance.getBone("door_nose_l");
      if (noseL != null) {
         noseL.visible = true;
         noseL.rotation.rotateZ((float)Math.toRadians(80.0) * doorFold);
      }

      if (System.getProperty("deepstrike.reviewCase", "").startsWith("model-seams-design")) {
         VehicleDesignReview.observeGear(plane, instance, progress);
      }
   }

   public void render(Su24mEntity plane, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
      if (Boolean.getBoolean("deepstrike.su24mReview")) {
         this.reviewBase = new Matrix4f(pose.last().pose());
      }

      try {
         super.render(plane, yaw, partial, pose, buffers, light);
      } finally {
         this.reviewBase = null;
      }
   }

   public void renderCustomPart(
      Su24mEntity plane, VehicleModelInstance instance, PoseStack pose, float yaw, float partial, MultiBufferSource buffers, int light
   ) {
      super.renderCustomPart(plane, instance, pose, yaw, partial, buffers, light);
      if (this.reviewBase != null && Su24mExhaust.running(plane)) {
         Su24mExhaust.verifyAnchor(plane, partial, new Matrix4f(this.reviewBase).invert().mul(pose.last().pose()));
      }

      if (Su24mExhaust.running(plane)) {
         double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(plane.position());
         if (distance < 262144.0) {
            for (int i = 0; i < 2; i++) {
               if (!((i == 0 ? plane.getMainEngineHealth() : plane.getSubEngineHealth()) <= 0.0F)) {
                  pose.pushPose();
                  pose.translate(i == 0 ? -0.7525 : 0.7525, 2.205, 8.83);
                  pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
                  Fp5BoosterFire.renderAircraft(pose, buffers, plane.tickCount + partial, Su24mExhaust.power(plane, partial), distance);
                  pose.popPose();
               }
            }
         }
      }

      for (int ix = 0; ix < 2; ix++) {
         if (plane.loaded(ix)) {
            pose.pushPose();
            pose.translate(ix == 0 ? -2.1 : 2.1, 2.2, -0.2);
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            ModelRenderer.render(
               ModelAssets.missile(MissileKind.STORM_SHADOW),
               ModelAssets.texture(MissileKind.STORM_SHADOW, false),
               pose,
               buffers,
               light,
               OverlayTexture.NO_OVERLAY,
               0.0F,
               0.0F,
               b -> !b.startsWith("wing_") && !b.startsWith("fin_")
            );
            pose.popPose();
         }
      }
   }
}
