package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class TerrainMap implements AutoCloseable {
   private final MapSurface surface = MapProviders.create();
   private static Field mainTargetField;

   public TerrainMap() {
   }

   public TerrainMap(int ignoredResolution) {
      this();
   }

   public void refresh(double x, double z, int sx, int sz) {
      this.surface.refresh(x, z, sx, sz);
   }

   public void render(GuiGraphics g, int x, int y, int w, int h) {
      this.surface.render(g, x, y, w, h);
   }

   @Override
   public void close() {
      this.surface.close();
   }

   static void target(Minecraft mc, RenderTarget value) {
      try {
         if (mainTargetField == null) {
            for (Field field : Minecraft.class.getDeclaredFields()) {
               if (RenderTarget.class.isAssignableFrom(field.getType())) {
                  field.setAccessible(true);
                  if (field.get(mc) == mc.getMainRenderTarget()) {
                     mainTargetField = field;
                     break;
                  }
               }
            }
         }

         if (mainTargetField == null) {
            throw new IllegalStateException("Minecraft render target unavailable");
         } else {
            mainTargetField.set(mc, value);
         }
      } catch (ReflectiveOperationException var6) {
         throw new IllegalStateException(var6);
      }
   }
}
