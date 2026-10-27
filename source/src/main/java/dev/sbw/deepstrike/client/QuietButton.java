package dev.sbw.deepstrike.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.Builder;

final class QuietButton extends Button {
   private boolean selected;
   private FlightUi.Tone tone;

   QuietButton(Builder builder) {
      super(builder);
      this.setMessage(this.getMessage().copy().withStyle(FlightUi.STYLE));
   }

   QuietButton selected(boolean value) {
      this.selected = value;
      return this;
   }

   QuietButton tone(FlightUi.Tone value) {
      this.tone = value;
      return this;
   }

   public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
      String caption = this.getMessage().getString();
      FlightUi.control(
         g,
         this.getX(),
         this.getY(),
         this.width,
         this.height,
         caption,
         this.isHoveredOrFocused(),
         this.active,
         this.selected,
         this.isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed(),
         this.tone == null ? FlightUi.tone(caption) : this.tone
      );
   }
}
