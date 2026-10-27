package dev.sbw.deepstrike.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

abstract class TacticalXaeroMap extends Screen {
   private final Screen returnTo;
   private final TerrainMap terrain = new TerrainMap();
   private double zoom = 1.0;
   private double centerX;
   private double centerZ;
   private double pressX;
   private double pressY;
   private boolean dragging;
   private boolean moved;
   protected boolean chromeHidden;

   TacticalXaeroMap(Screen parent, double x, double z) {
      super(Component.literal("DeepStrike"));
      this.returnTo = parent;
      this.centerX = x;
      this.centerZ = z;
   }

   private double dpi() {
      return Math.max(1.0, Math.min(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight()) / 1080.0);
   }

   protected double effectiveScale() {
      return this.zoom * this.dpi() / this.minecraft.getWindow().getGuiScale() / UiViewport.scale();
   }

   protected int mapX(double world) {
      return (int)Math.round(this.width / 2.0 + (world - this.centerX) * this.effectiveScale());
   }

   protected int mapZ(double world) {
      return (int)Math.round(this.height / 2.0 + (world - this.centerZ) * this.effectiveScale());
   }

   protected double worldX(double x) {
      return this.centerX + (x - this.width / 2.0) / this.effectiveScale();
   }

   protected double worldZ(double y) {
      return this.centerZ + (y - this.height / 2.0) / this.effectiveScale();
   }

   protected void mapCenter(double x, double z) {
      this.centerX = x;
      this.centerZ = z;
   }

   protected void mapSpan(double diameter) {
      this.zoom = Math.max(
         0.125,
         Math.min(
            8.0,
            Math.max(1, Math.min(this.width - 300, this.height - 110)) * this.minecraft.getWindow().getGuiScale() * UiViewport.scale() / diameter / this.dpi()
         )
      );
   }

   public void init() {
      super.init();
      this.clearWidgets();
   }

   public void removed() {
      this.terrain.close();
      super.removed();
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      this.terrain
         .refresh(
            this.centerX,
            this.centerZ,
            Math.max(1, (int)Math.round(this.width / this.effectiveScale())),
            Math.max(1, (int)Math.round(this.height / this.effectiveScale()))
         );
      this.terrain.render(g, 0, 0, this.width, this.height);
      g.flush();
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 1100.0F);
      this.drawOverlay(g, mx, my, partial);
      if (!this.chromeHidden) {
         for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) {
               widget.render(g, mx, my, partial);
            }
         }
      }

      int toggleX = this.chromeHidden ? this.width - 180 : this.width - 330;
      boolean hover = mx >= toggleX && mx < toggleX + 166 && my >= 14 && my < 40;
      FlightUi.control(
         g,
         toggleX,
         14,
         166,
         26,
         FlightUi.localized(this.chromeHidden ? "screen.deepstrike.map.show_gui" : "screen.deepstrike.map.hide_gui"),
         hover,
         true,
         false,
         hover && this.minecraft.mouseHandler.isLeftPressed()
      );
      g.flush();
      g.pose().popPose();
   }

   protected abstract void drawOverlay(GuiGraphics var1, int var2, int var3, float var4);

   protected abstract boolean mapArea(double var1, double var3);

   protected boolean choose(double x, double y, int button) {
      return false;
   }

   public boolean mouseClicked(double x, double y, int button) {
      int toggleX = this.chromeHidden ? this.width - 180 : this.width - 330;
      if (x >= toggleX && x < toggleX + 166 && y >= 14.0 && y < 40.0) {
         if (button == 0) {
            this.toggleChrome();
         }

         return true;
      } else {
         if (!this.chromeHidden) {
            for (GuiEventListener child : this.children()) {
               if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(x, y)) {
                  this.setFocused(widget);
                  return widget.mouseClicked(x, y, button);
               }
            }
         }

         this.setFocused(null);
         if (!this.chromeHidden && !this.mapArea(x, y)) {
            return true;
         } else if (this.choose(x, y, button)) {
            return true;
         } else if (button == 0) {
            this.pressX = x;
            this.pressY = y;
            this.moved = false;
            this.dragging = true;
            return true;
         } else {
            return false;
         }
      }
   }

   public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
      if (b == 0 && this.dragging) {
         this.moved = this.moved | Math.hypot(x - this.pressX, y - this.pressY) > 3.0;
         if (this.moved) {
            this.centerX = this.centerX - dx / this.effectiveScale();
            this.centerZ = this.centerZ - dy / this.effectiveScale();
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double x, double y, int b) {
      if (b == 0 && this.dragging) {
         this.dragging = false;
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
      if (!this.chromeHidden && !this.mapArea(x, y)) {
         return true;
      } else {
         double wx = this.worldX(x);
         double wz = this.worldZ(y);
         this.zoom = Math.max(0.125, Math.min(8.0, this.zoom * Math.pow(1.25, delta)));
         this.centerX = this.centerX + (wx - this.worldX(x));
         this.centerZ = this.centerZ + (wz - this.worldZ(y));
         return true;
      }
   }

   private void toggleChrome() {
      this.chromeHidden = !this.chromeHidden;
      this.setFocused(null);

      for (GuiEventListener child : this.children()) {
         if (child instanceof EditBox box) {
            box.setFocused(false);
         }
      }
   }

   public boolean charTyped(char c, int mods) {
      return !this.chromeHidden && this.getFocused() != null && this.getFocused().charTyped(c, mods);
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 72 && !(this.getFocused() instanceof EditBox)) {
         this.toggleChrome();
         return true;
      } else if (key == 256) {
         this.onClose();
         return true;
      } else {
         return !this.chromeHidden && this.getFocused() != null && this.getFocused().keyPressed(key, scan, mods);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.minecraft.setScreen(this.returnTo);
   }
}
