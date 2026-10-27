package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.IronDomeCanisterLayout;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

final class IronDomeCanisterRenderer {
   static void render(PoseStack pose, MultiBufferSource buffers, GeoBone elevation, IronDomeEntity dome, float partial, int light, int overlay) {
      pose.pushPose();

      try {
         RenderUtil.prepMatrixForBone(pose, elevation);

         for (int cell = 0; cell < 20; cell++) {
            Vec3 mouth = IronDomeCanisterLayout.mouth(cell);
            if (dome.loadedCell(cell)) {
               pose.pushPose();

               try {
                  pose.translate(-mouth.x, mouth.y - IronDomeCanisterLayout.setback(), mouth.z);
                  pose.mulPose(ModelGeometry.flight(180.0F, -90.0F));
                  pose.scale(InterceptorGeometry.axialScale(3), InterceptorGeometry.radialScale(3), InterceptorGeometry.radialScale(3));
                  ModelRenderer.render("tamir", "tamir", pose, buffers, light, overlay, 0.0F, 0.0F, b -> true);
               } finally {
                  pose.popPose();
               }
            }
         }
      } finally {
         pose.popPose();
      }
   }

   private IronDomeCanisterRenderer() {
   }
}
