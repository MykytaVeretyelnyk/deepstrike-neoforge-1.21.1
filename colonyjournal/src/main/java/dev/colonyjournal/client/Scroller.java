package dev.colonyjournal.client;

import net.minecraft.client.gui.GuiGraphics;

/** Vertical scroll state of a list with a thin scroll bar. */
final class Scroller {
   int offset;
   private int content;
   private int view;

   void setSize(int contentHeight, int viewHeight) {
      this.content = contentHeight;
      this.view = viewHeight;
      this.offset = Ui.clamp(this.offset, 0, this.max());
   }

   int max() {
      return Math.max(0, this.content - this.view);
   }

   void wheel(double amount) {
      this.offset = Ui.clamp(this.offset - (int)(amount * 18.0), 0, this.max());
   }

   void reset() {
      this.offset = 0;
   }

   /** Draws the bar at the right edge of the view starting at (x, y), the view being {@code height} pixels high. */
   void renderBar(GuiGraphics g, int x, int y, int height) {
      if (this.max() <= 0) {
         return;
      }

      g.fill(x, y, x + 4, y + height, 0x55000000);
      int thumb = Math.max(10, height * this.view / this.content);
      int ty = y + (height - thumb) * this.offset / this.max();
      g.fill(x, ty, x + 4, ty + thumb, 0xFFB0A090);
      g.fill(x + 3, ty, x + 4, ty + thumb, 0xFF605848);
   }
}
