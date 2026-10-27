package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.lang.reflect.Field;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.WaypointWorld;
import xaero.common.minimap.waypoints.WaypointsManager;
import xaero.map.WorldMapSession;
import xaero.map.graphics.ImprovedFramebuffer;
import xaero.map.gui.GuiMap;

final class XaeroTerrainMap implements MapSurface {
   private static final HashMap<String, Field> FIELDS = new HashMap<>();
   private static Field mainTargetField;
   private GuiMap map;
   private TextureTarget target;
   private double centerX;
   private double centerZ;
   private double spanX = 1000.0;
   private double spanZ = 1000.0;
   private double pixelsPerBlock;
   private long nextFrame;

   static void target(Minecraft mc, RenderTarget value) {
      try {
         if (mainTargetField == null) {
            for (Field f : Minecraft.class.getDeclaredFields()) {
               if (RenderTarget.class.isAssignableFrom(f.getType())) {
                  f.setAccessible(true);
                  if (f.get(mc) == mc.getMainRenderTarget()) {
                     mainTargetField = f;
                     break;
                  }
               }
            }
         }

         if (mainTargetField == null) {
            throw new IllegalStateException("Minecraft render target field unavailable");
         } else {
            mainTargetField.set(mc, value);
         }
      } catch (ReflectiveOperationException var6) {
         throw new IllegalStateException(var6);
      }
   }

   private static Field framebufferBackup() {
      try {
         Field f = ImprovedFramebuffer.class.getDeclaredField("mainRenderTargetBackup");
         f.setAccessible(true);
         return f;
      } catch (Exception var1) {
         throw new IllegalStateException(var1);
      }
   }

   private static Object backup() {
      try {
         return framebufferBackup().get(null);
      } catch (Exception var1) {
         throw new IllegalStateException(var1);
      }
   }

   private static void backup(Object value) {
      try {
         framebufferBackup().set(null, value);
      } catch (Exception var2) {
         throw new IllegalStateException(var2);
      }
   }

   public XaeroTerrainMap() {
   }

   public XaeroTerrainMap(int ignoredResolution) {
   }

   private static Field field(String name) {
      return FIELDS.computeIfAbsent(name, key -> {
         try {
            Field f = GuiMap.class.getDeclaredField(key);
            f.setAccessible(true);
            return f;
         } catch (Exception var2) {
            throw new IllegalStateException(var2);
         }
      });
   }

   private static Object read(String name, Object owner) {
      try {
         return field(name).get(owner);
      } catch (Exception var3) {
         throw new IllegalStateException(var3);
      }
   }

   private static void write(String name, Object owner, Object value) {
      try {
         field(name).set(owner, value);
      } catch (Exception var4) {
         throw new IllegalStateException(var4);
      }
   }

   @Override
   public void refresh(double x, double z, int sx, int sz) {
      if (x != this.centerX || z != this.centerZ || sx != this.spanX || sz != this.spanZ) {
         this.nextFrame = 0L;
      }

      this.centerX = x;
      this.centerZ = z;
      this.spanX = Math.max(1, sx);
      this.spanZ = Math.max(1, sz);
   }

   private void update() {
      Minecraft mc = Minecraft.getInstance();
      WorldMapSession session = WorldMapSession.getCurrentSession();
      if (session != null && mc.player != null) {
         int w = mc.getWindow().getWidth();
         int h = mc.getWindow().getHeight();
         boolean resized = this.target == null || this.target.width != w || this.target.height != h;
         if (resized || System.nanoTime() >= this.nextFrame) {
            RenderTarget original = mc.getMainRenderTarget();
            Object originalBackup = backup();
            boolean hidden = GuiMap.hiddenUI;
            int readFbo = GL11.glGetInteger(36010);
            int drawFbo = GL11.glGetInteger(36006);
            int[] viewport = new int[4];
            GL11.glGetIntegerv(2978, viewport);
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorting sorting = RenderSystem.getVertexSorting();
            RenderSystem.getModelViewStack().pushMatrix();
            Object attached = read("attachedCamera", null);
            Object destination = read("destScale", null);

            try {
               if (resized) {
                  if (this.target != null) {
                     this.target.destroyBuffers();
                  }

                  this.target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
                  this.map = null;
               }

               target(mc, this.target);
               backup(this.target);
               this.target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
               this.target.clear(Minecraft.ON_OSX);
               this.target.bindWrite(true);
               if (this.map == null) {
                  this.map = new GuiMap(null, null, session.getMapProcessor(), mc.player);
                  this.map.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
               }

               this.map.tick();
               this.pixelsPerBlock = Math.min(w / this.spanX, h / this.spanZ);
               write("shouldResetCameraPos", this.map, false);
               write("cameraX", this.map, this.centerX);
               write("cameraZ", this.map, this.centerZ);
               double dpi = Math.max(1.0, Math.min(w, h) / 1080.0);
               double zoom = this.pixelsPerBlock / dpi;
               write("scale", this.map, this.pixelsPerBlock);
               write("userScale", this.map, zoom);
               write("destScale", null, zoom);
               write("zoomAnim", this.map, null);
               write("cameraDestination", this.map, null);
               write("cameraDestinationAnimX", this.map, null);
               write("cameraDestinationAnimZ", this.map, null);
               write("attachedCamera", null, false);
               GuiMap.hiddenUI = true;
               GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
               this.map.render(graphics, -10000, -10000, mc.getTimer().getGameTimeDeltaPartialTick(false));
               graphics.flush();
               this.nextFrame = System.nanoTime() + 250000000L;
            } finally {
               GuiMap.hiddenUI = hidden;
               write("attachedCamera", null, attached);
               write("destScale", null, destination);
               backup(originalBackup);
               target(mc, original);
               RenderSystem.getModelViewStack().popMatrix();
               RenderSystem.applyModelViewMatrix();
               RenderSystem.setProjectionMatrix(projection, sorting);
               GL30.glBindFramebuffer(36008, readFbo);
               GL30.glBindFramebuffer(36009, drawFbo);
               RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      }
   }

   @Override
   public void render(GuiGraphics g, int x, int y, int w, int h) {
      g.flush();
      UiViewport.nativeMapBegin(g);

      try {
         this.update();
      } finally {
         UiViewport.nativeMapEnd(g);
      }

      if (this.target != null) {
         float du = (float)(this.spanX * this.pixelsPerBlock / this.target.width / 2.0);
         float dv = (float)(this.spanZ * this.pixelsPerBlock / this.target.height / 2.0);
         RenderSystem.setShader(GameRenderer::getPositionTexShader);
         RenderSystem.setShaderTexture(0, this.target.getColorTextureId());
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         Matrix4f matrix = g.pose().last().pose();
         BufferBuilder buffer = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         buffer.addVertex(matrix, x, y + h, 0.0F).setUv(0.5F - du, 0.5F - dv);
         buffer.addVertex(matrix, x + w, y + h, 0.0F).setUv(0.5F + du, 0.5F - dv);
         buffer.addVertex(matrix, x + w, y, 0.0F).setUv(0.5F + du, 0.5F + dv);
         buffer.addVertex(matrix, x, y, 0.0F).setUv(0.5F - du, 0.5F + dv);
         dev.sbw.deepstrike.compat.RenderCompat.draw(buffer);
      }
   }

   static void openNativeMap() {
      Minecraft mc = Minecraft.getInstance();
      mc.setScreen(new GuiMap(null, null, WorldMapSession.getCurrentSession().getMapProcessor(), mc.player));
   }

   static boolean hiddenUI() {
      return GuiMap.hiddenUI;
   }

   static boolean ready() {
      return WorldMapSession.getCurrentSession() != null;
   }

   static MapProviders.View nativeView(Screen screen) {
      try {
         return new MapProviders.View((Double)read("cameraX", screen), (Double)read("cameraZ", screen), (Double)read("scale", screen));
      } catch (RuntimeException var2) {
         return null;
      }
   }

   static void cleanupWaypoints() {
      XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
      if (session != null && session.getWaypointsManager() != null) {
         WaypointsManager manager = session.getWaypointsManager();
         WaypointWorld world = manager.getCurrentWorld();
         if (world != null && world.getCurrentSet() != null && world.getCurrentSet().getList().removeIf(wp -> wp.getName().startsWith("[DeepStrike"))) {
            manager.updateWaypoints();
         }
      }
   }

   @Override
   public void close() {
      if (this.target != null) {
         this.target.destroyBuffers();
         this.target = null;
      }

      this.map = null;
   }
}
