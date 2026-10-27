package dev.sbw.deepstrike.client;

import net.minecraft.client.gui.GuiGraphics;

interface MapSurface extends AutoCloseable {
   void refresh(double var1, double var3, int var5, int var6);

   void render(GuiGraphics var1, int var2, int var3, int var4, int var5);

   @Override
   void close();
}
