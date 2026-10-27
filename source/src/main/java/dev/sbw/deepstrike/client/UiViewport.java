package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class UiViewport {
   private static float drawingScale = 1.0F;

   public static boolean applies(Object screen) {
      return screen instanceof Screen && screen.getClass().getPackageName().equals("dev.sbw.deepstrike.client");
   }

   public static float scale() {
      Window window = Minecraft.getInstance().getWindow();
      double physicalUnit = Math.max(1.0, Math.min(3.0, window.getHeight() / 600.0));
      return (float)Math.min(physicalUnit / window.getGuiScale(), Math.min(window.getGuiScaledWidth() / 1000.0, window.getGuiScaledHeight() / 600.0));
   }

   public static int width() {
      return (int)Math.ceil(Minecraft.getInstance().getWindow().getGuiScaledWidth() / scale());
   }

   public static int height() {
      return (int)Math.ceil(Minecraft.getInstance().getWindow().getGuiScaledHeight() / scale());
   }

   public static int inputWidth() {
      Minecraft mc = Minecraft.getInstance();
      return applies(mc.screen) ? mc.screen.width : mc.getWindow().getGuiScaledWidth();
   }

   public static int inputHeight() {
      Minecraft mc = Minecraft.getInstance();
      return applies(mc.screen) ? mc.screen.height : mc.getWindow().getGuiScaledHeight();
   }

   public static void begin(GuiGraphics g) {
      g.flush();
      g.pose().pushPose();
      drawingScale = scale();
      g.pose().scale(drawingScale, drawingScale, 1.0F);
   }

   static void nativeMapBegin(GuiGraphics g) {
      g.flush();
      g.pose().pushPose();
      float s = scale();
      g.pose().scale(1.0F / s, 1.0F / s, 1.0F);
      drawingScale = 1.0F;
   }

   static void nativeMapEnd(GuiGraphics g) {
      g.flush();
      g.pose().popPose();
      drawingScale = scale();
   }

   public static void end(GuiGraphics g) {
      g.flush();
      g.pose().popPose();
      drawingScale = 1.0F;
   }

   public static int scissor(int coordinate) {
      return Math.round(coordinate * drawingScale);
   }

   private UiViewport() {
   }
}
