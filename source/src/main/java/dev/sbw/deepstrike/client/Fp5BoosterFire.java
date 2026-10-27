package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

final class Fp5BoosterFire {
   private static ShaderInstance shader;
   private static ShaderInstance depthShader;

   static void register(RegisterShadersEvent event) {
      Su24OriginalFire.register(event);

      try {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "fp5_booster_volume"), DefaultVertexFormat.NEW_ENTITY),
            s -> shader = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "fp5_booster_depth"), DefaultVertexFormat.NEW_ENTITY),
            s -> depthShader = s
         );
      } catch (IOException var2) {
         throw new IllegalStateException(var2);
      }
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style) {
      render(pose, buffers, age, style, true);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style, boolean cutoff) {
      render(pose, buffers, age, style, cutoff, false);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style, boolean cutoff, boolean depth) {
      render(pose, buffers, age, style, cutoff ? 90 : 0, depth);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style, int cutoffTicks, boolean depth) {
      float ramp = ExhaustStyles.ignition(age);
      float fade = cutoffTicks > 0 ? Mth.clamp((cutoffTicks - age) / 3.0F, 0.0F, 1.0F) : 1.0F;
      renderMotor(pose, buffers, age, 0.66F * (0.22F + 0.78F * ramp) * fade, 8.0F * (0.08F + 0.92F * ramp) * fade, 0.7F + 0.3F * ramp, 0.0, depth);
   }

   static void renderMotor(
      PoseStack pose, MultiBufferSource buffers, float age, float radius, float length, float brightness, double distanceSquared, boolean depth
   ) {
      Su24MotorFlame.render(pose, buffers, age, radius, length, brightness, distanceSquared, depth);
   }

   static void renderAircraft(PoseStack pose, MultiBufferSource buffers, float age, float power, double distanceSquared) {
      Su24OriginalFire.renderAircraft(pose, buffers, age, power, distanceSquared);
   }

   static float aircraftLength(float power) {
      return 0.28F + 3.5F * power * power;
   }

   static float aircraftRadius(float power) {
      return 0.29F + 0.16F * Mth.clamp(power, 0.0F, 1.0F);
   }

   private Fp5BoosterFire() {
   }

   private static class Types extends RenderType {
      private static final RenderType MATERIAL = create(
         "fp5_booster_volume",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         262144,
         false,
         false,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> Fp5BoosterFire.shader))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_DEPTH_WRITE)
            .createCompositeState(false)
      );
      private static final RenderType DEPTH = create(
         "fp5_booster_depth",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         262144,
         false,
         false,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> Fp5BoosterFire.depthShader))
            .setTransparencyState(NO_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(DEPTH_WRITE)
            .createCompositeState(false)
      );

      private Types() {
         super("unused", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 256, false, false, () -> {}, () -> {});
      }
   }
}
