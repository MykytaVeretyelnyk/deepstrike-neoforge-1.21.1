package dev.sbw.deepstrike.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

final class FlightInstruments {
   static String tr(String key) {
      return FlightUi.localized("hud.deepstrike.instrument." + key);
   }

   static void type(GuiGraphics g, String text, int x, int y, int color) {
      FlightUi.label(g, text, x, y, color);
   }

   static void card(GuiGraphics g, int x, int y, int w, int h) {
      FlightUi.panel(g, x, y, w, h, EquipmentUiTheme.current().surface() & 16777215 | -536870912, EquipmentUiTheme.current().edge());
   }

   static void metric(GuiGraphics g, int x, int y, int w, String label, String value, String unit) {
      card(g, x, y, w, 48);
      type(g, label, x + 10, y + 7, -5196890);
      g.pose().pushPose();
      g.pose().translate(x + 10, y + 23, 1.0F);
      g.pose().scale(1.35F, 1.35F, 1.0F);
      type(g, value, 0, 0, label.equals(tr("speed")) ? -1000096 : -4668269);
      g.pose().popPose();
      int uw = Minecraft.getInstance().font.width(FlightUi.text(unit));
      type(g, unit, x + w - uw - 10, y + 28, -5196890);
   }

   static void heading(GuiGraphics g, int cx, int y, int width, float yaw) {
      int heading = Math.floorMod(Math.round(yaw), 360);
      card(g, cx - width / 2 - 8, y - 5, width + 16, 35);
      g.enableScissor(cx - width / 2, y - 3, cx + width / 2, y + 29);

      for (int delta = -60; delta <= 60; delta++) {
         int value = Math.floorMod(heading + delta, 360);
         if (value % 5 == 0) {
            int x = cx + delta * 3;
            if (Math.abs(x - cx) <= width / 2) {
               int len = value % 30 == 0 ? 8 : 4;
               g.fill(x, y + 15, x + 1, y + 15 + len, value % 30 == 0 ? -1000096 : -4668269);
               if (value % 30 == 0 && Math.abs(x - cx) > 25) {
                  type(g, String.format(Locale.ROOT, "%03d", value), x - 9, y + 2, -5196890);
               }
            }
         }
      }

      g.disableScissor();
      g.fill(cx - 1, y + 19, cx + 2, y + 27, -1000096);
      card(g, cx - 20, y - 9, 40, 20);
      g.fill(cx - 18, y - 7, cx + 18, y + 8, -12368075);
      FlightUi.centered(g, String.format(Locale.ROOT, "%03d", heading), cx, y - 3, -1000096);
   }

   static void reticle(GuiGraphics g, int cx, int cy, int size, boolean command) {
      int ink = -4668269;
      int shadow = -1072754676;

      for (int pass = 0; pass < 2; pass++) {
         int offset = pass == 0 ? 1 : 0;
         int color = pass == 0 ? shadow : ink;

         for (int side : new int[]{-1, 1}) {
            int x = cx + side * size + offset;
            g.fill(x, cy - 9 + offset, x + 1, cy + 10 + offset, color);
            FlightUi.line(g, x, cy - 9 + offset, x - side * 6, cy - 9 + offset, color);
            FlightUi.line(g, x, cy + 9 + offset, x - side * 6, cy + 9 + offset, color);
            FlightUi.line(g, cx - 5 + offset, cy + side * (size + 5) + offset, cx + 5 + offset, cy + side * (size + 5) + offset, color);
         }

         g.fill(cx + offset, cy + offset, cx + 1 + offset, cy + 1 + offset, color);
      }

      if (command) {
         int yy = cy + size + 23;
         type(g, tr("aim"), cx - 25, yy, -1182997);
      }
   }

   static void horizon(GuiGraphics g, int cx, int cy, float pitch, int width) {
      g.enableScissor(cx - width / 2, cy - 68, cx + width / 2, cy + 68);

      for (int angle = -80; angle <= 80; angle += 10) {
         int y = cy + Math.round((angle - pitch) * 3.0F);
         if (Math.abs(y - cy) <= 64) {
            int length = angle == 0 ? 34 : 20;
            g.fill(cx - length - 16, y, cx - 16, y + 1, -1110257985);
            g.fill(cx + 17, y, cx + length + 17, y + 1, -1110257985);
            if (angle != 0) {
               type(g, Integer.toString(Math.abs(angle)), cx + length + 23, y - 4, -5196890);
            }
         }
      }

      g.disableScissor();
   }

   static void footer(GuiGraphics g, String hint, int width, int height) {
      Font font = Minecraft.getInstance().font;
      int max = Math.max(100, width - 32);
      int y = height - 18 - font.split(FlightUi.text(hint), max).size() * 11;

      for (FormattedCharSequence line : font.split(FlightUi.text(hint), max)) {
         int w = font.width(line);
         card(g, width / 2 - w / 2 - 9, y - 3, w + 18, 17);
         g.drawString(font, line, width / 2 - w / 2, y, -1182997, false);
         y += 17;
      }
   }

   private FlightInstruments() {
   }
}
