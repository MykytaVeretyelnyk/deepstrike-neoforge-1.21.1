package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderStateShard.MultiTextureStateShard;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

final class MissileEffectsRenderTypes extends RenderType {
   private static ShaderInstance ribbon;
   private static ShaderInstance cloud;
   private static ShaderInstance detail;
   private static ShaderInstance blastCore;
   private static ShaderInstance flame;
   private static ShaderInstance flameFar;
   private static ShaderInstance flameDepth;
   static final RenderType RIBBON = smoke("wake_ribbon", () -> ribbon);
   static final RenderType CLOUD = smoke("wake_cloud", () -> cloud);
   static final RenderType BLAST_CORE = smoke("blast_core", () -> blastCore);
   static final RenderType DETAIL = smoke("smoke_0ad", () -> detail);
   static final RenderType FLAME = fire("nozzle_flame", () -> flame, false);
   static final RenderType FLAME_FAR = fire("nozzle_flame_far", () -> flameFar, false);
   static final RenderType FLAME_DEPTH = fire("nozzle_flame_depth", () -> flameDepth, true);

   private static RenderType smoke(String name, Supplier<ShaderInstance> shader) {
      return create(
         "deepstrike_" + name,
         DefaultVertexFormat.NEW_ENTITY,
         Mode.QUADS,
         262144,
         false,
         true,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(shader))
            .setTextureState(
               MultiTextureStateShard.builder()
                  .add(
                     ResourceLocation.fromNamespaceAndPath("deepstrike", name.equals("wake_ribbon") ? "textures/particle/zerok/trail.png" : "textures/particle/ported/cloud_1_512.png"
                     ),
                     true,
                     false
                  )
                  .add(ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/ported/smoke_light.png"), true, false)
                  .add(ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/ported/smoke_light.png"), true, false)
                  .add(ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/explosion_billow.png"), true, false)
                  .build()
            )
            .setLightmapState(LIGHTMAP)
            .setTransparencyState(name.equals("blast_core") ? NO_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(name.equals("blast_core") ? COLOR_DEPTH_WRITE : COLOR_WRITE)
            .createCompositeState(false)
      );
   }

   private static RenderType fire(String name, Supplier<ShaderInstance> shader, boolean depth) {
      return create(
         "deepstrike_" + name,
         DefaultVertexFormat.POSITION_TEX_COLOR,
         Mode.QUADS,
         8192,
         false,
         !depth,
         CompositeState.builder()
            .setShaderState(new ShaderStateShard(shader))
            .setTransparencyState(depth ? NO_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(depth ? DEPTH_WRITE : COLOR_DEPTH_WRITE)
            .createCompositeState(false)
      );
   }

   static boolean smokeReady() {
      return ribbon != null && cloud != null && detail != null && blastCore != null;
   }

   static void updateSmokeLighting(float skyBrightness) {
      Minecraft mc = Minecraft.getInstance();
      Vec3 cam = mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null ? mc.gameRenderer.getMainCamera().getPosition() : Vec3.ZERO;
      updateSmokeLighting(skyBrightness, cam, mc.getTimer().getGameTimeDeltaPartialTick(false));
   }

   static void updateSmokeLighting(float skyBrightness, Vec3 camera, float partialTick) {
      float sun = 1.34F * Math.max(0.2F, Math.min(1.0F, skyBrightness));
      cloud.getUniform("SunColor").set(sun, sun, sun);
      detail.getUniform("SunColor").set(sun, sun, sun);
      blastCore.getUniform("SunColor").set(sun, sun, sun);
      List<FlightLighting.Glow> lights = new ArrayList<>();
      FlightLighting.collectSmokeLights(lights, partialTick);
      lights.sort(Comparator.comparingDouble(gx -> gx.position().distanceToSqr(camera) / Math.max(1.0, (double)(gx.strength() * gx.radius()))));
      float[] posA = new float[16];
      float[] jetA = new float[16];
      float[] colA = new float[16];
      float[] posB = new float[16];
      float[] jetB = new float[16];
      float[] colB = new float[16];
      Minecraft mc = Minecraft.getInstance();
      double far = mc.gameRenderer != null ? Math.max(1.0, mc.gameRenderer.getDepthFar() * 0.72) : 256.0;
      int count = Math.min(8, lights.size());

      for (int i = 0; i < count; i++) {
         FlightLighting.Glow g = lights.get(i);
         Vec3 rel = g.position().subtract(camera);
         double dist = rel.length();
         float proj = dist > far ? (float)(far / dist) : 1.0F;
         Vec3 p = rel.scale(proj);
         Vec3 jet = (g.end() != null ? g.end().subtract(g.position()) : Vec3.ZERO).scale(proj);
         float rad = (g.flash() ? g.radius() : Math.max(g.halo() * 2.45F, g.radius() * 0.68F)) * proj;
         float str = g.strength() * (g.flash() ? 1.55F : 1.38F);
         float[] rgb = g.rgb() != null ? g.rgb() : new float[]{1.0F, 0.56F, 0.16F};
         float r = Math.min(1.65F, rgb[0] * 1.35F + 0.12F);
         float gr = Math.min(1.35F, rgb[1] * 0.92F + 0.06F);
         float b = Math.min(1.1F, rgb[2] * 0.65F);
         float[] pArr = i < 4 ? posA : posB;
         float[] jArr = i < 4 ? jetA : jetB;
         float[] cArr = i < 4 ? colA : colB;
         int base = (i & 3) * 4;
         pArr[base] = (float)p.x;
         pArr[base + 1] = (float)p.y;
         pArr[base + 2] = (float)p.z;
         pArr[base + 3] = rad;
         jArr[base] = (float)jet.x;
         jArr[base + 1] = (float)jet.y;
         jArr[base + 2] = (float)jet.z;
         jArr[base + 3] = str;
         cArr[base] = r;
         cArr[base + 1] = gr;
         cArr[base + 2] = b;
         cArr[base + 3] = g.flash() ? 2.0F : g.burnStage();
      }

      for (ShaderInstance s : new ShaderInstance[]{ribbon, cloud, detail, blastCore}) {
         if (s != null) {
            s.safeGetUniform("LightPosMatA").set(posA);
            s.safeGetUniform("LightJetMatA").set(jetA);
            s.safeGetUniform("LightColMatA").set(colA);
            s.safeGetUniform("LightPosMatB").set(posB);
            s.safeGetUniform("LightJetMatB").set(jetB);
            s.safeGetUniform("LightColMatB").set(colB);
         }
      }
   }

   static boolean fireReady() {
      return flame != null && flameFar != null && flameDepth != null;
   }

   static void registerShaders(RegisterShadersEvent event) {
      Fp5BoosterFire.register(event);

      try {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "wake_ribbon"), DefaultVertexFormat.NEW_ENTITY), s -> ribbon = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "blast_core"), DefaultVertexFormat.NEW_ENTITY), s -> blastCore = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "wake_cloud"), DefaultVertexFormat.NEW_ENTITY), s -> cloud = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "smoke_0ad"), DefaultVertexFormat.NEW_ENTITY), s -> detail = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "nozzle_flame"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> flame = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "nozzle_flame_far"), DefaultVertexFormat.POSITION_TEX_COLOR),
            s -> flameFar = s
         );
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "nozzle_flame_depth"), DefaultVertexFormat.POSITION_TEX_COLOR),
            s -> flameDepth = s
         );
      } catch (IOException var2) {
         throw new IllegalStateException("Cannot load missile smoke/flame materials", var2);
      }
   }

   private MissileEffectsRenderTypes() {
      super("unused", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 256, false, false, () -> {}, () -> {});
   }
}
