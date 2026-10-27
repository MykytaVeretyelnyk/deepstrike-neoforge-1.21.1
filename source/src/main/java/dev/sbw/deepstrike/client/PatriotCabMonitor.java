package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@EventBusSubscriber(
   modid = "deepstrike",
   value = {Dist.CLIENT}
)
public final class PatriotCabMonitor {
   private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("deepstrike", "patriot_cab_live");
   private static PatriotDefenseScreen console;
   private static PatriotTruckEntity vehicle;
   private static TextureTarget frame;
   private static long nextFrame;
   private static boolean valid;
   static int reviewFrames;

   static void tick() {
      Minecraft mc = Minecraft.getInstance();
      PatriotTruckEntity truck = mc.player != null && mc.player.getVehicle() instanceof PatriotTruckEntity p ? p : null;
      if (truck != vehicle) {
         close();
         vehicle = truck;
      }

      if (truck != null && mc.level != null && truck.isAlive()) {
         if (console == null) {
            console = PatriotDefenseScreen.monitor(truck.getId());
         }

         if (mc.screen == null) {
            console.tick();
         }
      } else {
         close();
      }
   }

   @SubscribeEvent
   public static void renderGui(RenderTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (console != null
            && vehicle != null
            && mc.player != null
            && mc.player.getVehicle() == vehicle
            && mc.screen == null
            && mc.level == vehicle.level()) {
            long now = System.nanoTime();
            if (now >= nextFrame) {
               nextFrame = now + 250000000L;
               mc.renderBuffers().bufferSource().endBatch();
               RenderTarget originalTarget = mc.getMainRenderTarget();
               int oldReadFbo = GL11.glGetInteger(36010);
               int oldDrawFbo = GL11.glGetInteger(36006);
               boolean oldBlend = GL11.glIsEnabled(3042);
               boolean oldDepth = GL11.glIsEnabled(2929);
               boolean oldCull = GL11.glIsEnabled(2884);
               boolean oldScissor = GL11.glIsEnabled(3089);
               boolean oldDepthMask = GL11.glGetBoolean(2930);
               int[] scissor = new int[4];
               GL11.glGetIntegerv(3088, scissor);
               ShaderInstance oldShader = RenderSystem.getShader();
               boolean review = System.getProperty("deepstrike.reviewCase", "").startsWith("model-seams-render-radar");
               int[] before = !review || reviewFrames != 3 && reviewFrames != 9 ? null : reviewPixels(originalTarget);
               int[] viewport = new int[4];
               GL11.glGetIntegerv(2978, viewport);
               Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
               VertexSorting sorting = RenderSystem.getVertexSorting();
               org.joml.Matrix4fStack modelView = RenderSystem.getModelViewStack();
               modelView.pushMatrix();

               try {
                  if (frame == null) {
                     frame = new TextureTarget(512, 320, true, Minecraft.ON_OSX);
                     mc.getTextureManager().register(TEXTURE, new PatriotCabMonitor.BorrowedTexture(frame.getColorTextureId()));
                  }

                  TerrainMap.target(mc, frame);
                  frame.setClearColor(0.015F, 0.02F, 0.015F, 1.0F);
                  frame.clear(Minecraft.ON_OSX);
                  frame.bindWrite(true);
                  RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, 1016.0F, 636.0F, 0.0F, 1000.0F, 21000.0F), VertexSorting.ORTHOGRAPHIC_Z);
                  modelView.identity();
                  modelView.translate(0.0F, 0.0F, -11000.0F);
                  RenderSystem.applyModelViewMatrix();
                  GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                  console.render(graphics, -100, -100, event.renderTickTime);
                  graphics.flush();
                  valid = true;
               } finally {
                  modelView.popMatrix();
                  RenderSystem.applyModelViewMatrix();
                  RenderSystem.setProjectionMatrix(projection, sorting);
                  TerrainMap.target(mc, originalTarget);
                  GL30.glBindFramebuffer(36008, oldReadFbo);
                  GL30.glBindFramebuffer(36009, oldDrawFbo);
                  RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
                  RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                  RenderSystem.defaultBlendFunc();
                  if (oldShader != null) {
                     RenderSystem.setShader(() -> oldShader);
                  }

                  if (oldBlend) {
                     RenderSystem.enableBlend();
                  } else {
                     RenderSystem.disableBlend();
                  }

                  if (oldDepth) {
                     RenderSystem.enableDepthTest();
                  } else {
                     RenderSystem.disableDepthTest();
                  }

                  if (oldCull) {
                     RenderSystem.enableCull();
                  } else {
                     RenderSystem.disableCull();
                  }

                  RenderSystem.depthMask(oldDepthMask);
                  if (oldScissor) {
                     RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
                  } else {
                     RenderSystem.disableScissor();
                  }
               }

               if (review) {
                  if (mc.getMainRenderTarget() != originalTarget
                     || GL11.glGetInteger(36006) != oldDrawFbo
                     || !RenderSystem.getProjectionMatrix().equals(projection)
                     || before != null && !Arrays.equals(before, reviewPixels(originalTarget))) {
                     throw new IllegalStateException("Cab display changed the main frame or leaked its render target");
                  }

                  reviewFrames++;
               }
            }
         }
      }
   }

   private static int[] reviewPixels(RenderTarget source) {
      NativeImage image = Screenshot.takeScreenshot(source);

      int[] var8;
      try {
         int[] pixels = new int[25];
         int k = 0;

         for (int y = 1; y <= 5; y++) {
            for (int x = 1; x <= 5; x++) {
               pixels[k++] = image.getPixelRGBA(image.getWidth() * x / 6, image.getHeight() * y / 6);
            }
         }

         var8 = pixels;
      } catch (Throwable var7) {
         if (image != null) {
            try {
               image.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (image != null) {
         image.close();
      }

      return var8;
   }

   static void render(PoseStack pose, MultiBufferSource buffers, PatriotTruckEntity truck, int overlay) {
      Minecraft mc = Minecraft.getInstance();
      if (valid && vehicle == truck && mc.player != null && mc.player.getVehicle() == truck) {
         VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
         float l = 0.31875F;
         float r = 0.8375F;
         float b = 1.9125F;
         float t = 2.225F;
         float z = -7.171875F;
         vertex(out, pose, l, b, z, 0.0F, 0.0F, overlay);
         vertex(out, pose, r, b, z, 1.0F, 0.0F, overlay);
         vertex(out, pose, r, t, z, 1.0F, 1.0F, overlay);
         vertex(out, pose, l, t, z, 0.0F, 1.0F, overlay);
      }
   }

   private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z, float u, float v, int overlay) {
      out.addVertex(pose.last().pose(), x, y, z)
         .setColor(255, 255, 255, 255)
         .setUv(u, v)
         .setOverlay(overlay)
         .setLight(15728880)
         .setNormal(pose.last(), 0.0F, 0.0F, 1.0F)
         ;
   }

   static void close() {
      valid = false;
      nextFrame = 0L;
      if (console != null) {
         console.removed();
         console = null;
      }

      if (frame != null) {
         Minecraft.getInstance().getTextureManager().release(TEXTURE);
         frame.destroyBuffers();
         frame = null;
      }

      vehicle = null;
   }

   private PatriotCabMonitor() {
   }

   private static final class BorrowedTexture extends AbstractTexture {
      BorrowedTexture(int texture) {
         this.id = texture;
      }

      public void load(ResourceManager resources) {
      }

      public void releaseId() {
      }
   }
}
