package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

final class JourneyTerrainMap implements MapSurface {
   private Object renderer;
   private Object state;
   private Object properties;
   private boolean modern;
   private int tileGridSize;
   private TextureTarget target;
   private double x;
   private double z;
   private double spanX = 1000.0;
   private double spanZ = 1000.0;
   private double pixelsPerBlock;
   private long nextFrame;
   private final HashMap<String, Method> methods = new HashMap<>();

   private static Class<?> type(String name) {
      try {
         return Class.forName(name);
      } catch (ClassNotFoundException var2) {
         throw new IllegalStateException("JourneyMap integration class: " + name, var2);
      }
   }

   private Object call(Object owner, String name, Object... args) {
      Class<?> clazz = owner instanceof Class<?> c ? c : owner.getClass();
      String key = clazz.getName() + "#" + name + Arrays.toString(Arrays.stream(args).map(a -> a == null ? null : a.getClass()).toArray());
      Method method = this.methods.get(key);
      if (method == null) {
         label70:
         for (Method candidate : clazz.getMethods()) {
            if (candidate.getName().equals(name) && candidate.getParameterCount() == args.length) {
               Class<?>[] parameters = candidate.getParameterTypes();

               for (int i = 0; i < args.length; i++) {
                  if (args[i] != null) {
                     Class<?> parameter = parameters[i];
                     if (parameter.isPrimitive()) {
                        parameter = parameter == int.class
                           ? Integer.class
                           : (
                              parameter == float.class
                                 ? Float.class
                                 : (parameter == double.class ? Double.class : (parameter == boolean.class ? Boolean.class : parameter))
                           );
                     }

                     if (!parameter.isInstance(args[i])) {
                        continue label70;
                     }
                  }
               }

               method = candidate;
               this.methods.put(key, candidate);
               break;
            }
         }

         if (method == null) {
            throw new IllegalStateException("JourneyMap method unavailable: " + key);
         }
      }

      try {
         return method.invoke(owner instanceof Class ? null : owner, args);
      } catch (ReflectiveOperationException var14) {
         throw new IllegalStateException("JourneyMap call: " + key, var14);
      }
   }

   private void initialize() {
      try {
         try {
            Class.forName("journeymap.client.model.map.MapState");
            this.modern = true;
         } catch (ClassNotFoundException var4) {
            this.modern = false;
         }

         String model = this.modern ? "journeymap.client.model.map." : "journeymap.client.model.";
         Class<?> context = type(this.modern ? "journeymap.api.v2.common.Context$UI" : "journeymap.client.api.display.Context$UI");
         this.renderer = type("journeymap.client.render.map." + (this.modern ? "MapRenderer" : "GridRenderer"))
            .getConstructor(context)
            .newInstance(Enum.valueOf((Class)context, "Fullscreen"));
         this.state = type(model + "MapState").getConstructor().newInstance();
         Object instance = this.call(type("journeymap.client.JourneymapClient"), "getInstance");
         this.properties = this.call(instance, "getFullMapProperties");
      } catch (ReflectiveOperationException var5) {
         throw new IllegalStateException("JourneyMap renderer initialization", var5);
      }
   }

   @Override
   public void refresh(double x, double z, int sx, int sz) {
      if (this.x != x || this.z != z || this.spanX != sx || this.spanZ != sz) {
         this.nextFrame = 0L;
      }

      this.x = x;
      this.z = z;
      this.spanX = Math.max(1, sx);
      this.spanZ = Math.max(1, sz);
   }

   private void update() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int w = mc.getWindow().getWidth();
         int h = mc.getWindow().getHeight();
         boolean resized = this.target == null || this.target.width != w || this.target.height != h;
         if (resized || System.nanoTime() >= this.nextFrame) {
            if (this.renderer == null) {
               this.initialize();
            }

            RenderTarget original = mc.getMainRenderTarget();
            int read = GL11.glGetInteger(36010);
            int draw = GL11.glGetInteger(36006);
            int[] viewport = new int[4];
            GL11.glGetIntegerv(2978, viewport);
            Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            VertexSorting sorting = RenderSystem.getVertexSorting();
            org.joml.Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();

            try {
               if (resized) {
                  if (this.target != null) {
                     this.target.destroyBuffers();
                  }

                  this.target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
               }

               TerrainMap.target(mc, this.target);
               this.target.setClearColor(0.055F, 0.055F, 0.055F, 1.0F);
               this.target.clear(Minecraft.ON_OSX);
               this.target.bindWrite(true);
               modelView.identity();
               RenderSystem.applyModelViewMatrix();
               RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0F, w, h, 0.0F, 1000.0F, 21000.0F), VertexSorting.ORTHOGRAPHIC_Z);
               GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
               sizeDisplay(graphics.pose(), w, h);
               RenderSystem.applyModelViewMatrix();
               this.call(this.state, "refresh", mc, mc.player, this.properties);
               String model = this.modern ? "journeymap.client.model.map." : "journeymap.client.model.";
               Object day = Enum.valueOf((Class)type(model + "MapType$Name"), "day");
               this.call(this.state, "setMapType", day);
               Object mapType = this.call(this.state, "getMapType");
               Object world = this.call(this.state, "getWorldDir");
               this.pixelsPerBlock = Math.min(w / this.spanX, h / this.spanZ);
               if (this.modern) {
                  int zoom = Math.max(2, Math.min(16384, (int)Math.round(this.pixelsPerBlock * 512.0)));
                  this.pixelsPerBlock = zoom / 512.0;
                  this.call(this.renderer, "setContext", this.state);
                  this.call(this.renderer, "setZoom", (double)zoom);
                  this.call(this.renderer, "setViewPortBounds", new java.awt.geom.Rectangle2D.Double(0.0, 0.0, w, h));
                  this.call(this.renderer, "center", world, mapType, this.x, this.z, zoom);
                  this.call(this.renderer, "updateTiles", mapType, zoom, false);
                  double actualX = ((Number)this.call(this.renderer, "getCenterBlockX")).doubleValue();
                  double actualZ = ((Number)this.call(this.renderer, "getCenterBlockZ")).doubleValue();
                  graphics.pose()
                     .translate(
                        (w - mc.getWindow().getScreenWidth()) * 0.5 + (actualX - this.x) * this.pixelsPerBlock,
                        (h - mc.getWindow().getScreenHeight()) * 0.5 + (actualZ - this.z) * this.pixelsPerBlock,
                        0.0
                     );
                  this.updateRotation(this.renderer, graphics, 0.0);
                  this.call(this.renderer, "render", graphics, mc.renderBuffers().bufferSource(), 0.0, 0.0, 1.0F, false);
               } else {
                  int zoom = Math.max(0, Math.min(5, (int)Math.ceil(Math.log(this.pixelsPerBlock) / Math.log(2.0))));
                  double nativeScale = Math.pow(2.0, zoom);
                  double ratio = this.pixelsPerBlock / nativeScale;
                  int vw = (int)Math.ceil(w / ratio);
                  int vh = (int)Math.ceil(h / ratio);
                  this.call(this.renderer, "setContext", world, mapType);
                  int grid = Math.min(65, Math.max(3, (int)Math.ceil(Math.max(vw, vh) / 512.0) + 3) | 1);
                  if (grid != this.tileGridSize) {
                     this.call(this.renderer, "setGridSize", grid);
                     this.call(this.renderer, "clear");
                     this.tileGridSize = grid;
                  }

                  this.call(this.renderer, "setViewPort", new java.awt.geom.Rectangle2D.Double(0.0, 0.0, vw, vh));
                  this.call(this.renderer, "center", world, mapType, this.x, this.z, zoom);
                  this.call(this.renderer, "updateTiles", mapType, zoom, true, vw, vh, false, 0.0, 0.0);
                  graphics.pose().pushPose();
                  graphics.pose().scale((float)ratio, (float)ratio, 1.0F);
                  this.call(this.renderer, "draw", graphics, 1.0F, 1.0F, 0.0, 0.0, false);
                  graphics.pose().popPose();
               }

               graphics.flush();
               this.nextFrame = System.nanoTime() + 250000000L;
            } finally {
               sizeDisplay(new PoseStack(), mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
               TerrainMap.target(mc, original);
               modelView.popMatrix();
               RenderSystem.applyModelViewMatrix();
               RenderSystem.setProjectionMatrix(projection, sorting);
               GL30.glBindFramebuffer(36008, read);
               GL30.glBindFramebuffer(36009, draw);
               RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
         }
      }
   }

   @Override
   public void render(GuiGraphics g, int left, int top, int width, int height) {
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
         BufferBuilder bb = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         bb.addVertex(matrix, left, top + height, 0.0F).setUv(0.5F - du, 0.5F - dv);
         bb.addVertex(matrix, left + width, top + height, 0.0F).setUv(0.5F + du, 0.5F - dv);
         bb.addVertex(matrix, left + width, top, 0.0F).setUv(0.5F + du, 0.5F + dv);
         bb.addVertex(matrix, left, top, 0.0F).setUv(0.5F - du, 0.5F + dv);
         dev.sbw.deepstrike.compat.RenderCompat.draw(bb);
      }
   }

   private static void sizeDisplay(PoseStack pose, double w, double h) {
      try {
         Class<?> drawUtil = type("journeymap.client.render.draw.DrawUtil");

         try {
            Method m = drawUtil.getMethod("sizeDisplay", PoseStack.class, double.class, double.class);
            m.invoke(null, pose, w, h);
         } catch (NoSuchMethodException var8) {
            Method mx = drawUtil.getMethod("sizeDisplay", double.class, double.class);
            mx.invoke(null, w, h);
         }
      } catch (ReflectiveOperationException var9) {
         throw new IllegalStateException("JourneyMap display projection", var9);
      }
   }

   private void updateRotation(Object targetRenderer, GuiGraphics graphics, double rotation) {
      if (this.modern) {
         this.call(targetRenderer, "updateRotation", graphics, rotation);
      } else {
         this.call(targetRenderer, "updateRotation", graphics.pose(), rotation);
      }
   }

   static boolean ready() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      } else {
         try {
            return Class.forName("journeymap.client.JourneymapClient").getMethod("getInstance").invoke(null) != null;
         } catch (Exception var2) {
            return true;
         }
      }
   }

   static MapProviders.View nativeView(Screen screen) {
      try {
         Field field = findField(screen.getClass(), "mapRenderer", "gridRenderer");
         if (field == null) {
            return null;
         } else {
            boolean modern = "mapRenderer".equals(field.getName());
            Object targetRenderer = field.get(screen);
            if (targetRenderer == null) {
               return null;
            } else {
               Class<?> type = targetRenderer.getClass();
               double x = ((Number)type.getMethod("getCenterBlockX").invoke(targetRenderer)).doubleValue();
               double z = ((Number)type.getMethod("getCenterBlockZ").invoke(targetRenderer)).doubleValue();
               double zoom = ((Number)type.getMethod("getZoom").invoke(targetRenderer)).doubleValue();
               Minecraft mc = Minecraft.getInstance();
               double dpi = mc != null && mc.getWindow() != null ? (double)mc.getWindow().getWidth() / mc.getWindow().getScreenWidth() : 1.0;
               return new MapProviders.View(x, z, (modern ? zoom / 512.0 : Math.pow(2.0, zoom)) * dpi);
            }
         }
      } catch (Exception var14) {
         return null;
      }
   }

   private static Field findField(Class<?> clazz, String... names) {
      for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
         for (String name : names) {
            try {
               Field f = c.getDeclaredField(name);
               f.setAccessible(true);
               return f;
            } catch (NoSuchFieldException var8) {
            }
         }
      }

      return null;
   }

   @Override
   public void close() {
      if (this.target != null) {
         this.target.destroyBuffers();
         this.target = null;
      }

      if (this.renderer != null) {
         this.call(this.renderer, "clear");
      }

      this.renderer = null;
      this.state = null;
      this.methods.clear();
   }
}
