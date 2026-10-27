package dev.sbw.deepstrike.client;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class SpotlightTour {
   private List<SpotlightTour.Step> steps = List.of();
   private int index = -1;
   private int nextX;
   private int nextY;
   private int closeX;

   void start(List<SpotlightTour.Step> value) {
      this.steps = List.copyOf(value);
      this.index = this.steps.isEmpty() ? -1 : 0;
   }

   boolean active() {
      return this.index >= 0 && this.index < this.steps.size();
   }

   void close() {
      this.index = -1;
   }

   boolean key(int key) {
      if (!this.active()) {
         return false;
      } else {
         if (key == 256) {
            this.close();
         } else if (key == 257 || key == 262 || key == 32) {
            this.advance();
         } else if (key == 263) {
            this.index = Math.max(0, this.index - 1);
         }

         return true;
      }
   }

   private void advance() {
      if (++this.index >= this.steps.size()) {
         this.close();
      }
   }

   boolean click(double x, double y, int button) {
      if (!this.active()) {
         return false;
      } else {
         if (button == 0 && y >= this.nextY && y < this.nextY + 26) {
            if (x >= this.nextX && x < this.nextX + 110) {
               this.advance();
            } else if (x >= this.closeX && x < this.closeX + 30) {
               this.close();
            }
         }

         return true;
      }
   }

   void render(GuiGraphics g, int width, int height, int mx, int my) {
      if (this.active()) {
         SpotlightTour.Step step = this.steps.get(this.index);
         SpotlightTour.Box box = step.target.get();
         int x = Math.max(0, box.x - 5);
         int y = Math.max(0, box.y - 5);
         int right = Math.min(width, box.x + box.width + 5);
         int bottom = Math.min(height, box.y + box.height + 5);
         g.flush();
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 3000.0F);
         int shade = -939524096;
         g.fill(0, 0, width, y, shade);
         g.fill(0, bottom, width, height, shade);
         g.fill(0, y, x, bottom, shade);
         g.fill(right, y, width, bottom, shade);
         int alpha = 150 + (int)(80.0 * (0.5 + 0.5 * Math.sin(System.nanoTime() / 3.5E8)));
         g.renderOutline(x, y, Math.max(1, right - x), Math.max(1, bottom - y), alpha << 24 | 15777120);
         int cardW = Math.min(360, width - 24);
         Font font = Minecraft.getInstance().font;
         List<FormattedCharSequence> lines = font.split(step.explanation.copy().withStyle(FlightUi.STYLE), cardW - 28);
         int cardH = 54 + lines.size() * 13;
         int cardX = Math.min(width - cardW - 12, Math.max(12, x));
         int cardY = bottom + 12 + cardH <= height ? bottom + 12 : Math.max(12, y - cardH - 12);
         FlightUi.panel(g, cardX, cardY, cardW, cardH, -182838503, -1000096);
         int lineY = cardY + 12;

         for (FormattedCharSequence line : lines) {
            g.drawString(font, line, cardX + 14, lineY, -1182997, false);
            lineY += 13;
         }

         this.nextX = cardX + cardW - 154;
         this.closeX = cardX + cardW - 38;
         this.nextY = cardY + cardH - 34;
         FlightUi.label(g, this.index + 1 + " / " + this.steps.size(), cardX + 14, this.nextY + 8, -5196890);
         FlightUi.control(
            g,
            this.nextX,
            this.nextY,
            110,
            26,
            Component.translatable(this.index == this.steps.size() - 1 ? "gui.done" : "spectatorMenu.next_page").getString(),
            mx >= this.nextX && mx < this.nextX + 110 && my >= this.nextY && my < this.nextY + 26,
            true,
            false,
            false
         );
         FlightUi.control(
            g, this.closeX, this.nextY, 30, 26, "×", mx >= this.closeX && mx < this.closeX + 30 && my >= this.nextY && my < this.nextY + 26, true, false, false
         );
         g.flush();
         g.pose().popPose();
      }
   }

   record Box(int x, int y, int width, int height) {
   }

   record Step(Supplier<SpotlightTour.Box> target, Component explanation) {
   }
}
