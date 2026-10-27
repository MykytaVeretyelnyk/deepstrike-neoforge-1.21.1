package dev.sbw.deepstrike.client;

import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ZoneXaeroMapScreen extends TacticalXaeroMap implements EquipmentUiTheme {
   private final Screen parent;
   private final BiConsumer<Double, Double> onCenter;
   private final double radius;
   private double zoneX;
   private double zoneZ;
   private boolean selecting;

   ZoneXaeroMapScreen(Screen parent, double x, double z, double radius, BiConsumer<Double, Double> onCenter) {
      super(parent, x, z);
      this.parent = parent;
      this.zoneX = x;
      this.zoneZ = z;
      this.radius = radius;
      this.onCenter = onCenter;
   }

   @Override
   public String equipmentSkin() {
      return EquipmentUiTheme.inherited(this.parent);
   }

   @Override
   public void init() {
      super.init();
      this.mapSpan(Math.max(1000.0, this.radius * 3.0));
      if (this.onCenter != null) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("screen.deepstrike.siren.select_map"), b -> this.selecting = true)
               .bounds(24, 18, 240, 26)
               .build(QuietButton::new)
         );
      }

      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.done"), b -> this.onClose())
            .bounds(this.width - 148, this.height - 48, 124, 26)
            .build(QuietButton::new)
      );
   }

   @Override
   protected boolean mapArea(double x, double y) {
      return y > 58.0 && y < this.height - 58;
   }

   @Override
   protected boolean choose(double x, double y, int button) {
      if (button == 0 && this.selecting && this.onCenter != null) {
         this.zoneX = this.worldX(x);
         this.zoneZ = this.worldZ(y);
         this.onCenter.accept(this.zoneX, this.zoneZ);
         this.selecting = false;
         return true;
      } else {
         return false;
      }
   }

   @Override
   protected void drawOverlay(GuiGraphics g, int mx, int my, float pt) {
      int x = this.mapX(this.zoneX);
      int y = this.mapZ(this.zoneZ);
      double r = this.radius * this.effectiveScale();
      g.enableScissor(0, 0, this.width, this.height);

      for (int i = 0; i < 180; i++) {
         double a = i * Math.PI / 90.0;
         int px = x + (int)Math.round(r * Math.cos(a));
         int py = y + (int)Math.round(r * Math.sin(a));
         g.fill(px, py, px + 2, py + 2, -1000096);
      }

      g.renderOutline(x - 5, y - 5, 11, 11, -1000096);
      g.disableScissor();
      if (!this.chromeHidden) {
         g.fill(0, this.height - 58, this.width, this.height, -182838503);
         FlightUi.label(
            g,
            this.selecting ? Component.translatable("screen.deepstrike.siren.click_map").getString() : "X " + (int)this.zoneX + "  Z " + (int)this.zoneZ,
            48,
            this.height - 38,
            -1182997
         );
      }
   }
}
