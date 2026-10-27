package dev.sbw.deepstrike.client;

import java.util.function.BiFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

final class InstrumentField extends EditBox {
   InstrumentField(Font font, int x, int y, int width, int height, Component label) {
      super(font, x, y, width, height, label);
      this.setBordered(false);
      this.setTextColor(-1182997);
      this.setTextColorUneditable(-5196890);
      this.setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
   }

   public void setFormatter(BiFunction<String, Integer, FormattedCharSequence> formatter) {
      super.setFormatter(
         (value, cursor) -> sink -> formatter.apply(value, cursor)
            .accept((index, style, point) -> sink.accept(index, style.withFont(Style.DEFAULT_FONT), point))
      );
   }

   public int getInnerWidth() {
      return Math.max(1, super.getInnerWidth() - 10);
   }

   public boolean mouseClicked(double x, double y, int button) {
      return !this.isMouseOver(x, y)
         ? super.mouseClicked(x, y, button)
         : super.mouseClicked(Math.max((double)this.getX(), Math.min((double)(this.getX() + this.getWidth() - 1), x - 5.0)), y, button);
   }

   public void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      FlightUi.field(g, this.getX(), this.getY(), this.getWidth(), this.getHeight(), this.isFocused(), this.active);
      g.pose().pushPose();
      g.pose().translate(5.0F, Math.max(0, (this.getHeight() - 9) / 2), 0.0F);
      super.renderWidget(g, mx, my, partial);
      g.pose().popPose();
   }
}
