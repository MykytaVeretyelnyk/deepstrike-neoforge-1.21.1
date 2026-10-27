package dev.sbw.deepstrike.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;

final class FlightButton extends Button {
   FlightButton(int x, int y, int w, int h, String title, OnPress action) {
      super(x, y, w, h, FlightUi.text(title), action, DEFAULT_NARRATION);
   }

   public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
      FlightUi.control(
         g,
         this.getX(),
         this.getY(),
         this.width,
         this.height,
         this.getMessage().getString(),
         this.isHoveredOrFocused(),
         this.active,
         false,
         this.isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed()
      );
   }
}
