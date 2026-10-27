package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.game.MissileKind;
import net.minecraft.client.renderer.MultiBufferSource;

final class ExhaustPlume {
   static void render(PoseStack pose, MultiBufferSource buffers, float age) {
      render(pose, buffers, age, 1.0F, 1.0F);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, float nozzle, float size) {
      render(pose, buffers, age, nozzle, size >= 4.0F ? 1.95F : 0.2F * size, size >= 4.0F ? 24.0F : 3.8F * size);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, float nozzle, float radius, float length) {
      draw(
         pose,
         buffers,
         age,
         nozzle,
         radius * (0.22F + 0.78F * ExhaustStyles.ignition(age)),
         length * (0.08F + 0.92F * ExhaustStyles.ignition(age)),
         ExhaustStyles.motorBrightness(age)
      );
   }

   static void renderSam(PoseStack pose, MultiBufferSource buffers, float time, float nozzle, int warhead, float age) {
      draw(
         pose,
         buffers,
         time,
         nozzle,
         ExhaustStyles.samMotorRadius(warhead, age),
         ExhaustStyles.samMotorLength(warhead, age),
         ExhaustStyles.motorBrightness(age)
      );
   }

   static void renderVariant(PoseStack pose, MultiBufferSource buffers, float age, float nozzle, MissileKind kind, int variant) {
      renderVariant(pose, buffers, age, nozzle, kind, variant, 0.38F);
   }

   static void renderVariant(PoseStack pose, MultiBufferSource buffers, float age, float nozzle, MissileKind kind, int variant, float burnStage) {
      float motorAge = ExhaustStyles.motorAge(kind, age);
      draw(
         pose,
         buffers,
         age,
         nozzle,
         ExhaustStyles.motorRadius(kind, motorAge),
         ExhaustStyles.motorLength(kind, motorAge),
         ExhaustStyles.motorBrightness(kind, motorAge)
      );
   }

   private static void draw(PoseStack pose, MultiBufferSource buffers, float age, float nozzle, float radius, float length, float brightness) {
      pose.pushPose();
      pose.translate(nozzle, 0.0F, 0.0F);
      Su24MotorFlame.render(pose, buffers, age, radius, length, brightness, 0.0, false);
      pose.popPose();
   }

   private ExhaustPlume() {
   }
}
