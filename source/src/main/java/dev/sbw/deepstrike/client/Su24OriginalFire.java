package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.joml.Vector3f;

final class Su24OriginalFire {
   private static ShaderInstance shader;
   private static ShaderInstance depthShader;
   private static final float[][] PROFILE = new float[6][33];
   private static final float[][] SLOPE = new float[6][33];
   private static final float[] COS = new float[25];
   private static final float[] SIN = new float[25];
   private static final float[] ANGLE = new float[25];
   private static final float[] JET_GRID;

   static void register(RegisterShadersEvent event) {
      try {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "su24_original_fire"), DefaultVertexFormat.NEW_ENTITY),
            s -> shader = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "su24_original_fire_depth"), DefaultVertexFormat.NEW_ENTITY),
            s -> depthShader = s
         );
      } catch (IOException var2) {
         throw new IllegalStateException(var2);
      }
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style) {
      render(pose, buffers, age, style, true);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style, boolean boosterCutoff) {
      render(pose, buffers, age, style, boosterCutoff, false);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style, boolean boosterCutoff, boolean depth) {
      render(pose, buffers, age, style, boosterCutoff ? 90 : 0, depth);
   }

   static void render(PoseStack pose, MultiBufferSource buffers, float age, int style, int cutoffTicks, boolean depth) {
      if (shader != null && (!Boolean.getBoolean("deepstrike.installedSmokeReview") || !"smoke".equals(System.getProperty("deepstrike.reviewEffectIsolation")))
         )
       {
         float ignition = Math.min(1.0F, age / 3.0F);
         float cutoff = cutoffTicks > 0 ? Math.max(0.0F, Math.min(1.0F, (cutoffTicks - age) / 3.0F)) : 1.0F;
         cutoff = cutoff * cutoff * (3.0F - 2.0F * cutoff);
         if (!(cutoff <= 0.0F)) {
            float length = new float[]{0.0F, 7.2F, 8.0F, 9.3F, 7.8F, 8.6F}[style];
            float radius = new float[]{0.0F, 0.54F, 0.66F, 0.49F, 0.72F, 0.61F}[style];
            float pulse = 1.0F + 0.025F * (float)Math.sin(age * 2.8) + 0.015F * (float)Math.sin(age * 5.3);
            length *= pulse;
            radius *= Math.max(0.1F, ignition);
            VertexConsumer out = buffers.getBuffer(depth ? Su24OriginalFire.Types.DEPTH : Su24OriginalFire.Types.MATERIAL);

            for (int layer = 0; layer < 1; layer++) {
               float scale = 1.0F + layer * 0.42F;

               float shellOpacity = switch (layer) {
                  case 0 -> 0.98F;
                  case 1 -> 0.24F;
                  case 2 -> 0.09F;
                  case 3 -> 0.032F;
                  case 4 -> 0.012F;
                  default -> 0.0F;
               };
               float opacity = Math.min(1.0F, shellOpacity * cutoff * 1.16F * (layer == 0 ? 1.0F : 1.65F));

               for (int i = 0; i < 32; i++) {
                  for (int j = 0; j < 24; j++) {
                     emit(pose, out, i / 32.0F, j / 24.0F, length, radius, scale, opacity, age, style, layer);
                     emit(pose, out, (i + 1) / 32.0F, j / 24.0F, length, radius, scale, opacity, age, style, layer);
                     emit(pose, out, (i + 1) / 32.0F, (j + 1) / 24.0F, length, radius, scale, opacity, age, style, layer);
                     emit(pose, out, i / 32.0F, (j + 1) / 24.0F, length, radius, scale, opacity, age, style, layer);
                  }
               }
            }
         }
      }
   }

   static void renderAircraft(PoseStack pose, MultiBufferSource buffers, float age, float rawPower, double distanceSquared) {
      float power = rawPower;
      if (shader != null && !(distanceSquared >= 262144.0)) {
         if (!EngineFlamePostPass.shaders()) {
            if (!EngineFlamePostPass.defer(pose, (p, b) -> renderAircraft(p, b, age, rawPower, distanceSquared))) {
               power = Mth.clamp(power, 0.0F, 1.0F);
               float length = aircraftLength(power) * (1.0F + 0.018F * (float)Math.sin(age * 1.7));
               float radius = 0.29F + 0.16F * power;
               int step = distanceSquared > 9216.0 ? 2 : 1;

               for (int i = 0; i <= 16; i += step) {
                  for (int j = 0; j <= 8; j += step) {
                     int row = i * 2;
                     int col = j * 3;
                     int k = (i * 9 + j) * 6;
                     float u = i / 16.0F;
                     float r = radius * PROFILE[2][row] * (1.0F + 0.025F * (float)Math.sin(u * 24.0F - ANGLE[col] * 3.0F - age * 1.2F));
                     float derivative = SLOPE[2][row] * radius / length;
                     float inv = 1.0F / (float)Math.sqrt(1.0F + derivative * derivative);
                     JET_GRID[k] = u * length;
                     JET_GRID[k + 1] = COS[col] * r;
                     JET_GRID[k + 2] = SIN[col] * r;
                     JET_GRID[k + 3] = -derivative * inv;
                     JET_GRID[k + 4] = COS[col] * inv;
                     JET_GRID[k + 5] = SIN[col] * inv;
                  }
               }

               VertexConsumer out = buffers.getBuffer(Su24OriginalFire.Types.MATERIAL);
               float brightness = 0.54F + 0.46F * power;

               for (int i = 0; i < 16; i += step) {
                  for (int j = 0; j < 8; j += step) {
                     jetVertex(pose, out, i, j, brightness);
                     jetVertex(pose, out, i + step, j, brightness);
                     jetVertex(pose, out, i + step, j + step, brightness);
                     jetVertex(pose, out, i, j + step, brightness);
                  }
               }
            }
         }
      }
   }

   static float aircraftLength(float power) {
      return 0.28F + 3.5F * power * power;
   }

   private static void jetVertex(PoseStack pose, VertexConsumer out, int i, int j, float brightness) {
      int k = (i * 9 + j) * 6;
      out.addVertex(pose.last().pose(), JET_GRID[k], JET_GRID[k + 1], JET_GRID[k + 2])
         .setColor(brightness, brightness, brightness, 1.0F)
         .setUv(i / 16.0F, j / 8.0F)
         .setUv1(0, 0)
         .setLight(15728880)
         .setNormal(pose.last(), JET_GRID[k + 3], JET_GRID[k + 4], JET_GRID[k + 5])
         ;
   }

   private static float profile(float u, int style) {
      float rise = (float)Math.pow(Math.max(0.0F, Math.min(1.0F, u / 0.1F)), 0.54F);
      float taper = (float)Math.pow(Math.max(0.0F, 1.0F - Math.max(0.0F, u - 0.12F) / 0.88F), 0.88F);
      float amcwExpand = (0.68F + 0.64F * u) * (1.0F - (float)Math.pow(Math.max(0.0F, (u - 0.58F) / 0.42F), 1.35F));
      float base = rise * (0.54F * taper + 0.46F * amcwExpand);

      return base * switch (style) {
         case 1 -> 1.0F - 0.32F * u;
         case 2 -> 0.94F + 0.08F * (float)Math.sin(Math.PI * u);
         case 3 -> (0.96F + 0.04F * (float)Math.sin(u * 28.0F)) * (1.0F - 0.28F * u);
         case 4 -> 0.84F + 0.16F * (float)Math.cos(u * 13.0F);
         default -> (1.0F - 0.16F * u) * (0.92F + 0.08F * (float)Math.sin(u * 7.0F));
      };
   }

   private static void emit(
      PoseStack pose, VertexConsumer out, float u, float v, float length, float radius, float scale, float alpha, float age, int style, int layer
   ) {
      float angle = v * (float) Math.PI * 2.0F;
      int row = Math.round(u * 32.0F);
      int column = Math.round(v * 24.0F);
      float shape = PROFILE[style][row];
      float r = radius * shape * scale;
      float ripple = 1.0F + (layer < 2 ? 0.028F : 0.048F) * (float)Math.sin(u * 24.0F - angle * 3.0F - age * 1.2F);
      float x = u * length * (1.0F + layer * 0.08F) - layer * 0.035F;
      float y = COS[column] * r * ripple;
      float z = SIN[column] * r * ripple;
      float derivative = SLOPE[style][row] * radius * scale / length;
      Vector3f normal = new Vector3f(-derivative, COS[column], SIN[column]).normalize();
      out.addVertex(pose.last().pose(), x, y, z)
         .setColor(1.0F, 1.0F, 1.0F, alpha)
         .setUv(u, v)
         .setUv1(0, 0)
         .setLight(15728880)
         .setNormal(pose.last(), normal.x, normal.y, normal.z)
         ;
   }

   private Su24OriginalFire() {
   }

   static {
      for (int style = 1; style <= 5; style++) {
         for (int i = 0; i <= 32; i++) {
            float u = i / 32.0F;
            float du = 0.002F;
            PROFILE[style][i] = profile(u, style);
            SLOPE[style][i] = (profile(Math.min(1.0F, u + du), style) - profile(Math.max(0.0F, u - du), style)) / (2.0F * du);
         }
      }

      for (int j = 0; j <= 24; j++) {
         ANGLE[j] = j / 24.0F * (float) Math.PI * 2.0F;
         COS[j] = (float)Math.cos(ANGLE[j]);
         SIN[j] = (float)Math.sin(ANGLE[j]);
      }

      JET_GRID = new float[918];
   }

   private static class Types extends RenderType {
      private static final RenderType MATERIAL = create(
         "su24_original_fire",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         262144,
         false,
         false,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> Su24OriginalFire.shader))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(COLOR_DEPTH_WRITE)
            .createCompositeState(false)
      );
      private static final RenderType DEPTH = create(
         "su24_original_fire_depth",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         262144,
         false,
         false,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(() -> Su24OriginalFire.depthShader))
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
