package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import org.joml.Matrix4f;

final class LateWorldEffects {
   private static final Matrix4f view = new Matrix4f();

   static void capture(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_PARTICLES) {
         view.set(event.getPoseStack().last().pose());
      }
   }

   static RenderLevelStageEvent worldView(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_LEVEL) {
         return event;
      } else {
         PoseStack pose = new PoseStack();
         pose.mulPose(view);
         return new RenderLevelStageEvent(
            event.getStage(),
            event.getLevelRenderer(),
            pose,
            event.getModelViewMatrix(),
            event.getProjectionMatrix(),
            event.getRenderTick(),
            event.getPartialTick(),
            event.getCamera(),
            event.getFrustum()
         );
      }
   }

   private LateWorldEffects() {
   }
}
