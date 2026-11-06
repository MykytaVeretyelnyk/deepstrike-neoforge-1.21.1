package dev.sbw.deepstrike.client.terminal;

import dev.krutlexik.skyfront.client.gui.ConsoleStyle;
import dev.krutlexik.skyfront.client.gui.TerminalButton;
import dev.sbw.deepstrike.game.MissileKind;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small dialog to edit X, Y, Z and speed of one route point; styled after Skyfront's ShahedWaypointEditScreen. */
class MissileRoutePointScreen extends Screen {
   private static final String[] LABELS = new String[]{"x", "y", "z", "speed"};
   private final MissileRouteScreen parent;
   private final int index;
   private final EditBox[] fields = new EditBox[4];
   private Component error = Component.empty();

   MissileRoutePointScreen(MissileRouteScreen parent, int index) {
      super(Component.translatable("gui.deepstrike.terminal.point.title", new Object[]{index + 1}));
      this.parent = parent;
      this.index = index;
   }

   /**
    * Same rules as the DeepStrike map's RoutePointScreen: finite numbers, |x| and |z| inside the world, Y inside the build height,
    * speed inside the missile's range. Returns the translation key of the error, or null when the values are valid.
    */
   static String validate(double[] v, MissileKind kind, int minY, int maxY) {
      for (double d : v) {
         if (!Double.isFinite(d)) {
            return "gui.deepstrike.terminal.point.error.number";
         }
      }

      if (Math.abs(v[0]) > 2.9999984E7 || Math.abs(v[2]) > 2.9999984E7) {
         return "gui.deepstrike.terminal.point.error.world";
      } else if (v[1] < (double)minY || v[1] > (double)maxY) {
         return "gui.deepstrike.terminal.point.error.height";
      } else {
         return v[3] >= (double)kind.minSpeed && v[3] <= (double)kind.maxSpeed ? null : "gui.deepstrike.terminal.point.error.speed";
      }
   }

   private int panelLeft() {
      return this.width / 2 - 100;
   }

   private int panelTop() {
      return this.height / 2 - 82;
   }

   protected void init() {
      int left = this.panelLeft();
      int top = this.panelTop();
      double[] values = this.parent.pointValues(this.index);

      for (int i = 0; i < 4; i++) {
         EditBox box = new EditBox(this.font, left + 70 + 3, top + 24 + i * 20 + 3, 118, 12, Component.empty());
         box.setBordered(false);
         box.setMaxLength(14);
         box.setTextColor(-4653109);
         box.setValue(i == 3 ? String.format(Locale.ROOT, "%.0f", values[i]) : String.format(Locale.ROOT, "%.2f", values[i]));
         box.setFilter(s -> s.matches("[-0-9.,]*"));
         this.fields[i] = box;
         this.addRenderableWidget(box);
      }

      int y = top + 24 + 80 + 14;
      this.addRenderableWidget(new TerminalButton(left + 8, y, 90, 18, Component.translatable("gui.deepstrike.terminal.point.ok"), b -> this.confirm()));
      this.addRenderableWidget(new TerminalButton(left + 102, y, 90, 18, Component.translatable("gui.deepstrike.terminal.point.cancel"), b -> this.onClose()));
      this.setInitialFocus(this.fields[0]);
   }

   private void confirm() {
      double[] v = new double[4];

      for (int i = 0; i < 4; i++) {
         try {
            v[i] = Double.parseDouble(this.fields[i].getValue().trim().replace(',', '.'));
         } catch (NumberFormatException var5) {
            this.error = Component.translatable("gui.deepstrike.terminal.point.error.number");
            return;
         }
      }

      int minY = this.minecraft.level.getMinBuildHeight();
      int maxY = this.minecraft.level.getMaxBuildHeight() - 1;
      String key = validate(v, this.parent.kind(), minY, maxY);
      if (key == null) {
         this.parent.applyPoint(this.index, v[0], v[1], v[2], v[3]);
         this.minecraft.setScreen(this.parent);
      } else {
         MissileKind kind = this.parent.kind();
         this.error = switch (key) {
            case "gui.deepstrike.terminal.point.error.height" -> Component.translatable(key, new Object[]{minY, maxY});
            case "gui.deepstrike.terminal.point.error.speed" -> Component.translatable(key, new Object[]{kind.minSpeed, kind.maxSpeed});
            default -> Component.translatable(key);
         };
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBlurredBackground(partialTick);
      int left = this.panelLeft();
      int top = this.panelTop();
      int bottom = top + 24 + 80 + 14 + 18 + 8;
      ConsoleStyle.panel(graphics, left, top, left + 200, bottom);
      ConsoleStyle.plate(graphics, this.font, this.width / 2, top + 5, this.title);

      for (int i = 0; i < 4; i++) {
         int y = top + 24 + i * 20;
         graphics.drawString(this.font, Component.translatable("gui.deepstrike.terminal.point." + LABELS[i]), left + 10, y + 5, -3226218);
         int x0 = left + 70;
         int x1 = left + 200 - 10;
         graphics.fill(x0, y, x1, y + 16, -16575478);
         graphics.fill(x0, y, x1, y + 1, -14779852);
         graphics.fill(x0, y + 15, x1, y + 16, -14779852);
         graphics.fill(x0, y, x0 + 1, y + 16, -14779852);
         graphics.fill(x1 - 1, y, x1, y + 16, -14779852);
      }

      graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.error.getString(), 184), this.width / 2, top + 24 + 80 + 3, -43691);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key != 257 && key != 335) {
         return super.keyPressed(key, scan, mods);
      } else {
         this.confirm();
         return true;
      }
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
   }

   public boolean isPauseScreen() {
      return false;
   }
}
