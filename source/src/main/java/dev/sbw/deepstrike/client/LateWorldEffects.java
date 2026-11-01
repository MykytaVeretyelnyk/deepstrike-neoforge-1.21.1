package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

final class LateWorldEffects {
   // AFTER_LEVEL fires after LevelRenderer popped the view off RenderSystem's model-view stack and passes a null PoseStack.
   // Camera-relative geometry therefore needs the view rotation (event.getModelViewMatrix()) baked into the pose here.
   static RenderLevelStageEvent worldView(RenderLevelStageEvent event) {
      if (event.getStage() != Stage.AFTER_LEVEL) {
         return event;
      } else {
         PoseStack pose = new PoseStack();
         pose.mulPose(event.getModelViewMatrix());
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
