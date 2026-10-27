package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

final class EngineLightRenderer extends RenderType {
   private static ShaderInstance volumeShader;
   private static final int SLICES = 28;
   private static final RenderType VOLUME = create(
      "deepstrike_engine_volume",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      4096,
      false,
      false,
      CompositeState.builder()
         .setShaderState(new ShaderStateShard(() -> volumeShader))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );
   private static final RenderType ENGINE_VOLUME = create(
      "deepstrike_engine_halo",
      DefaultVertexFormat.POSITION_TEX_COLOR,
      Mode.QUADS,
      4096,
      false,
      false,
      CompositeState.builder()
         .setShaderState(new ShaderStateShard(() -> volumeShader))
         .setTransparencyState(ADDITIVE_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(LEQUAL_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .createCompositeState(false)
   );
   static final RenderType EXHAUST = create(
      "deepstrike_engine_emission",
      DefaultVertexFormat.POSITION_COLOR,
      Mode.QUADS,
      4096,
      false,
      false,
      CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false)
   );

   static void registerShaders(RegisterShadersEvent event) {
      volumeShader = null;

      try {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "engine_volume"), DefaultVertexFormat.POSITION_TEX_COLOR),
            shader -> volumeShader = shader
         );
      } catch (IOException var2) {
         LogUtils.getLogger().error("Unable to load engine volume shader", var2);
      }
   }

   static void render(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 direction, Vec3 camera, float far, float radius, float intensity) {
      render(pose, buffers, center, direction, camera, far, radius, intensity, false);
   }

   static void renderAircraftMotor(PoseStack pose, MultiBufferSource buffers, FlightLighting.Glow glow, Vec3 camera, float far) {
      Vec3 end = glow.end() == null ? null : glow.position().lerp(glow.end(), 0.65);
      FlightLighting.Glow original = new FlightLighting.Glow(
         glow.id(), glow.position(), glow.radius(), glow.strength() * 0.42F, glow.halo() * 0.74F, false, glow.direction(), end, glow.rgb(), 0.0F
      );
      renderLit(pose, buffers, original, camera, far);
   }

   static void renderLit(PoseStack pose, MultiBufferSource buffers, FlightLighting.Glow glow, Vec3 camera, float far) {
      Vec3 direction = glow.direction().normalize();
      double jetLength = glow.end() == null ? glow.halo() : glow.position().distanceTo(glow.end());
      Vec3 center = glow.position().subtract(direction.scale(Math.min(jetLength * 0.34, glow.halo() * 0.78)));
      double distance = center.distanceTo(camera);
      float activeHalo = glow.halo() * 0.52F;
      float scattering = glow.strength() * 0.48F;
      render(pose, buffers, center, direction, camera, far, activeHalo, scattering, false, glow, glow.burnStage());
   }

   static void renderFlash(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 camera, float far, float radius, float intensity) {
      render(pose, buffers, center, new Vec3(0.0, 1.0, 0.0), camera, far, radius, intensity, true);
   }

   static void renderAtmosphere(PoseStack pose, MultiBufferSource buffers, FlightLighting.Glow glow, Vec3 camera, float far) {
      render(pose, buffers, glow.position(), new Vec3(0.0, 1.0, 0.0), camera, far, glow.radius() * 4.0F, glow.strength() * 0.8F, false);
   }

   private static void render(
      PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 direction, Vec3 camera, float far, float radius, float intensity, boolean flash
   ) {
      render(pose, buffers, center, direction, camera, far, radius, intensity, flash, null);
   }

   private static void render(
      PoseStack pose,
      MultiBufferSource buffers,
      Vec3 center,
      Vec3 direction,
      Vec3 camera,
      float far,
      float radius,
      float intensity,
      boolean flash,
      FlightLighting.Glow light
   ) {
      render(pose, buffers, center, direction, camera, far, radius, intensity, flash, light, 0.0F);
   }

   private static void render(
      PoseStack pose,
      MultiBufferSource buffers,
      Vec3 center,
      Vec3 direction,
      Vec3 camera,
      float far,
      float radius,
      float intensity,
      boolean flash,
      FlightLighting.Glow light,
      float burnStage
   ) {
      if (volumeShader != null && !(radius <= 0.0F) && !(intensity <= 0.005F)) {
         Vec3 relative = center.subtract(camera);
         double distance = relative.length();
         if (!(distance < 0.01)) {
            Vec3 axis = direction.lengthSqr() > 1.0E-8 ? direction.normalize() : new Vec3(0.0, 1.0, 0.0);
            Vec3 seed = Math.abs(axis.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 right = axis.cross(seed).normalize();
            Vec3 up = right.cross(axis).normalize();
            double width = radius * (flash ? 1.0 : 0.92);
            double jetSpan = light != null && light.end() != null ? light.position().distanceTo(light.end()) : width;
            double length = flash ? width : Math.max(width, Math.min(width * 5.0, jetSpan * 0.8));
            float jetAspect = !flash && light != null ? (float)Mth.clamp(length / Math.max(0.1, width) - 0.85, 0.0, 1.25) : 0.0F;
            if (!flash) {
               intensity *= MissileVisibility.engineOpacity(distance);
               if (intensity <= 0.005F) {
                  return;
               }
            }

            Vec3 view = camera.subtract(center).normalize();
            Vec3 scaledView = new Vec3(view.dot(right) * width, view.dot(up) * width, view.dot(axis) * length);
            double planeSpacing = scaledView.length();
            Vec3 localView = scaledView.normalize();
            Vec3 localSeed = Math.abs(localView.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 sliceRight = localView.cross(localSeed).normalize();
            Vec3 sliceUp = sliceRight.cross(localView).normalize();
            float projection = (float)Math.min(1.0, far / distance);
            pose.pushPose();
            pose.translate(relative.x * projection, relative.y * projection, relative.z * projection);
            Matrix4f matrix = pose.last().pose();
            int samples = light != null && Su24mExhaust.aircraftLight(light.id())
               ? 12
               : (flash ? 28 : (distance > radius * 55.0F ? 10 : (distance > radius * 20.0F ? 14 : 20)));
            Vec3 shadowTarget = distance > 32.0 ? center.add(camera.subtract(center).normalize().scale(32.0)) : camera;
            float lightVisibility = light == null ? 1.0F : FlightLighting.transmission(light, shadowTarget);
            if (lightVisibility <= 0.0F) {
               pose.popPose();
            } else {
               BufferBuilder out = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
               float opticalStep = (float)(28.0 / samples * planeSpacing / width);

               for (int slice = 0; slice < samples; slice++) {
                  float depth = -1.0F + (slice + 0.5F) * 2.0F / samples;
                  int cells = 1;

                  for (int cx = 0; cx < cells; cx++) {
                     for (int cy = 0; cy < cells; cy++) {
                        for (int corner = 0; corner < 4; corner++) {
                           float x = -1.0F + 2.0F * (cx + (corner != 0 && corner != 1 ? 1 : 0)) / cells;
                           float y = -1.0F + 2.0F * (cy + (corner != 0 && corner != 3 ? 1 : 0)) / cells;
                           Vec3 local = sliceRight.scale(x).add(sliceUp.scale(y)).add(localView.scale(depth));
                           Vec3 p = right.scale(local.x * width)
                              .add(up.scale(local.y * width))
                              .add(axis.scale(local.z * length));
                           out.addVertex(matrix, (float)p.x * projection, (float)p.y * projection, (float)p.z * projection)
                              .setUv((float)local.x, (float)local.y)
                              .setColor(
                                 (float)local.z * 0.25F + 0.5F, Math.min(1.0F, intensity) * lightVisibility, opticalStep / 8.0F, flash ? 1.0F : 0.0F
                              )
                              ;
                        }
                     }
                  }
               }

               ShaderInstance previousShader = RenderSystem.getShader();
               RenderSystem.setShader(() -> volumeShader);
               volumeShader.safeGetUniform("BurnStage").set(burnStage);
               volumeShader.safeGetUniform("JetAspect").set(jetAspect);
               RenderSystem.enableBlend();
               RenderSystem.blendFunc(770, flash ? 771 : 1);
               RenderSystem.enableDepthTest();
               RenderSystem.depthMask(false);
               RenderSystem.disableCull();
               dev.sbw.deepstrike.compat.RenderCompat.draw(out);
               RenderSystem.depthMask(true);
               RenderSystem.enableCull();
               RenderSystem.defaultBlendFunc();
               RenderSystem.disableBlend();
               RenderSystem.setShader(() -> previousShader);
               pose.popPose();
            }
         }
      }
   }

   private EngineLightRenderer() {
      super("unused", DefaultVertexFormat.POSITION_COLOR, Mode.QUADS, 256, false, false, () -> {}, () -> {});
   }
}
