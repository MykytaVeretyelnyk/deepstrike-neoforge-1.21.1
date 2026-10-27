package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;
import org.slf4j.LoggerFactory;

final class DistantFlightRenderer extends RenderType {
   private static ShaderInstance meshShader;
   private static ShaderInstance compositeShader;
   private static ShaderInstance hotShader;
   private static TextureTarget body;
   private static final Map<String, RenderType> TYPES = new HashMap<>();
   private static final float RADIUS = 24.0F;

   private DistantFlightRenderer() {
      super("deepstrike_distant", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 256, false, false, () -> {}, () -> {});
   }

   static void close() {
      if (body != null) {
         body.destroyBuffers();
         body = null;
      }
   }

   static void registerShaders(RegisterShadersEvent event) {
      try {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "distant_mesh"), DefaultVertexFormat.NEW_ENTITY), s -> {
               meshShader = s;
               TYPES.clear();
            }
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "distant_composite"), DefaultVertexFormat.POSITION_TEX),
            s -> compositeShader = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "distant_hot"), DefaultVertexFormat.NEW_ENTITY),
            s -> hotShader = s
         );
      } catch (IOException var2) {
         throw new IllegalStateException("Cannot load distant flight shaders", var2);
      }
   }

   static RenderType type(String atlas, boolean thermal) {
      return type(ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/" + atlas + ".png"), thermal);
   }

   static RenderType type(ResourceLocation texture, boolean thermal) {
      return type(texture, thermal, Mode.QUADS);
   }

   static RenderType type(ResourceLocation texture, boolean thermal, Mode mode) {
      return TYPES.computeIfAbsent(
         texture + (thermal ? ":hot" : "") + ":" + mode,
         key -> create(
            "deepstrike_distant_mesh",
            DefaultVertexFormat.NEW_ENTITY,
            mode,
            256,
            false,
            false,
            CompositeState.builder()
               .setShaderState(new ShaderStateShard(() -> thermal ? hotShader : meshShader))
               .setTextureState(new TextureStateShard(texture, false, false))
               .setCullState(NO_CULL)
               .setLightmapState(LIGHTMAP)
               .setOverlayState(OVERLAY)
               .setDepthTestState(LEQUAL_DEPTH_TEST)
               .setWriteMaskState(COLOR_DEPTH_WRITE)
               .createCompositeState(false)
         )
      );
   }

   static boolean render(GuidedMissileEntity missile, float partial, PoseStack pose, BufferSource buffers, double x, double y, double z, double distance) {
      if (meshShader != null && compositeShader != null && MissileEffectsRenderTypes.fireReady() && hotShader != null && !(distance < 0.25)) {
         if (Boolean.getBoolean("deepstrike.fp5Review") && missile.tickCount % 20 == 0) {
            LoggerFactory.getLogger(DistantFlightRenderer.class)
               .info(
                  "OPAQUE_DISTANT_PASS kind={} age={} distance={} far={}",
                  new Object[]{missile.kind(), missile.tickCount, distance, Minecraft.getInstance().gameRenderer.getDepthFar()}
               );
         }

         AABB bounds = MissileGeometry.canonicalBounds(missile.kind());
         double radius = Math.sqrt(
            Math.pow(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)), 2.0)
               + Math.pow(Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY)), 2.0)
               + Math.pow(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)), 2.0)
         );
         double plume = missile.kind() == MissileKind.PEACEKEEPER
            ? 48.0
            : (missile.kind() == MissileKind.FP5 ? 10.5 : (missile.kind() == MissileKind.STORM_SHADOW ? 9.5 : 6.2));
         return renderMesh(
            pose,
            buffers,
            x,
            y,
            z,
            distance,
            radius + plume,
            PatriotThermalCamera.isTracked(missile)
               ? MissileVisibility.sensorOpacity(distance)
               : (
                  missile.kind() == MissileKind.PEACEKEEPER && missile.exhaustVisible()
                     ? MissileVisibility.engineOpacity(distance)
                     : MissileVisibility.opacity(distance)
               ),
            (modelPose, modelBuffers) -> {
               modelPose.mulPose(FlightOrientation.interpolate(missile.yRotO, missile.xRotO, missile.getYRot(), missile.getXRot(), partial));
               modelPose.pushPose();

               try {
                  modelPose.mulPose(MissileGeometry.exportRotation(missile.kind()));
                  String atlas = ModelAssets.texture(missile.kind(), missile.camo(), missile.graphite());
                  boolean thermal = PatriotThermalCamera.isThermalTarget(missile);
                  if (thermal) {
                     atlas = ThermalAtlas.forTexture(atlas);
                  }

                  VertexConsumer vertices = modelBuffers.getBuffer(type(atlas, thermal));
                  ModelRenderer.render(
                     ModelAssets.missile(missile.kind()),
                     atlas,
                     modelPose,
                     ignored -> vertices,
                     15728880,
                     OverlayTexture.NO_OVERLAY,
                     0.0F,
                     0.0F,
                     missile::visibleFlightBone
                  );
               } finally {
                  modelPose.popPose();
               }

               modelBuffers.endBatch();
               ClientSetup.renderExhaust(missile, partial, modelPose, modelBuffers);
               modelBuffers.endBatch();
               ClientSetup.renderExhaust(missile, partial, modelPose, modelBuffers, true);
               modelBuffers.endBatch();
            }
         );
      } else {
         return false;
      }
   }

   static boolean renderMesh(
      PoseStack pose, BufferSource buffers, double x, double y, double z, double distance, double extent, float visibility, DistantFlightRenderer.Mesh mesh
   ) {
      if (meshShader != null && compositeShader != null && hotShader != null && !(distance < 0.25)) {
         Minecraft mc = Minecraft.getInstance();
         RenderTarget scene = mc.getMainRenderTarget();
         Matrix4f original = new Matrix4f(RenderSystem.getProjectionMatrix());
         if (Math.abs(original.m23() + 1.0F) > 0.001) {
            return false;
         } else {
            int[] viewport = new int[4];
            GL30.glGetIntegerv(2978, viewport);
            int previousDraw = GL30.glGetInteger(36006);
            int previousRead = GL30.glGetInteger(36010);
            if (body == null) {
               body = new TextureTarget(scene.width, scene.height, true, Minecraft.ON_OSX);
            }

            if (body.width != scene.width || body.height != scene.height) {
               body.resize(scene.width, scene.height, Minecraft.ON_OSX);
            }

            VertexSorting sorting = RenderSystem.getVertexSorting();
            float scale = (float)(24.0 / distance);
            Vector3f center = new Matrix4f(RenderSystem.getModelViewMatrix())
               .mul(pose.last().pose())
               .transformPosition(new Vector3f((float)(x * scale), (float)(y * scale), (float)(z * scale)));
            if (Float.isFinite(center.z) && !(center.z >= 0.0F)) {
               float margin = (float)(extent * scale * 1.1 + 0.01);
               float near = Math.max(0.01F, -center.z - margin);
               float far = Math.max(near + 0.02F, -center.z + margin);
               Matrix4f local = new Matrix4f(original).m22(-(far + near) / (far - near)).m32(-2.0F * far * near / (far - near));
               int oldTexture0 = RenderSystem.getShaderTexture(0);
               int oldTexture1 = RenderSystem.getShaderTexture(1);
               buffers.endBatch();

               boolean var30;
               try {
                  RenderSystem.colorMask(true, true, true, true);
                  RenderSystem.depthMask(true);
                  body.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                  body.clear(Minecraft.ON_OSX);
                  body.bindWrite(true);
                  RenderSystem.setProjectionMatrix(local, sorting);
                  pose.pushPose();

                  try {
                     pose.translate(x * scale, y * scale, z * scale);
                     pose.scale(scale, scale, scale);
                     mesh.draw(pose, buffers);
                  } finally {
                     pose.popPose();
                  }

                  buffers.endBatch();
                  RenderSystem.setProjectionMatrix(original, sorting);
                  GL30.glBindFramebuffer(36009, previousDraw);
                  RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                  compositeShader.safeGetUniform("LocalDepth").set(local.m22(), local.m32());
                  compositeShader.safeGetUniform("WorldDepth").set(original.m22(), original.m32());
                  compositeShader.safeGetUniform("DistanceScale").set(scale);
                  compositeShader.safeGetUniform("Visibility").set(visibility);
                  RenderSystem.setShader(() -> compositeShader);
                  RenderSystem.setShaderTexture(0, body.getColorTextureId());
                  RenderSystem.setShaderTexture(1, body.getDepthTextureId());
                  RenderSystem.enableDepthTest();
                  RenderSystem.depthMask(true);
                  RenderSystem.depthFunc(515);
                  RenderSystem.disableCull();
                  RenderSystem.enableBlend();
                  RenderSystem.blendFuncSeparate(1, 771, 1, 771);
                  BufferBuilder builder = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                  builder.addVertex(-1.0F, -1.0F, 0.0F).setUv(0.0F, 0.0F);
                  builder.addVertex(1.0F, -1.0F, 0.0F).setUv(1.0F, 0.0F);
                  builder.addVertex(1.0F, 1.0F, 0.0F).setUv(1.0F, 1.0F);
                  builder.addVertex(-1.0F, 1.0F, 0.0F).setUv(0.0F, 1.0F);
                  dev.sbw.deepstrike.compat.RenderCompat.draw(builder);
                  RenderSystem.disableBlend();
                  RenderSystem.defaultBlendFunc();
                  RenderSystem.enableCull();
                  var30 = true;
               } finally {
                  RenderSystem.setShaderTexture(0, oldTexture0);
                  RenderSystem.setShaderTexture(1, oldTexture1);
                  RenderSystem.setProjectionMatrix(original, sorting);
                  GL30.glBindFramebuffer(36009, previousDraw);
                  GL30.glBindFramebuffer(36008, previousRead);
                  RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                  RenderSystem.colorMask(true, true, true, true);
                  RenderSystem.depthMask(true);
                  RenderSystem.depthFunc(515);
                  RenderSystem.defaultBlendFunc();
                  RenderSystem.disableBlend();
               }

               return var30;
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   @FunctionalInterface
   interface Mesh {
      void draw(PoseStack var1, BufferSource var2);
   }
}
