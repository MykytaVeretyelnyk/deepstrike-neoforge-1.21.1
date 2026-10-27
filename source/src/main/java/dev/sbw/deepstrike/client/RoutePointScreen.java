package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.FormattedCharSequence;

final class RoutePointScreen extends Screen {
   private final Screen parent;
   private final RoutePoint initial;
   private final boolean terminal;
   private final MissileKind kind;
   private final Consumer<RoutePoint> save;
   private final EditBox[] fields = new EditBox[4];
   private String error = "";

   RoutePointScreen(Screen parent, RoutePoint point, boolean terminal, MissileKind kind, Consumer<RoutePoint> save) {
      super(
         FlightUi.text(
            terminal ? FlightUi.localized("ui.deepstrike.route_point_screen.dda436a572") : FlightUi.localized("ui.deepstrike.route_point_screen.f1c0e82588")
         )
      );
      this.parent = parent;
      this.initial = point;
      this.terminal = terminal;
      this.kind = kind;
      this.save = save;
   }

   protected void init() {
      double[] values = new double[]{this.initial.x(), this.initial.y(), this.initial.z(), this.initial.speed()};
      int x = this.width / 2 - 130;
      int y = this.height / 2 - 108;

      for (int i = 0; i < 4; i++) {
         this.fields[i] = new InstrumentField(this.font, x + 112, y + 30 + i * 30, 140, 20, FlightUi.text(this.label(i)));
         this.fields[i].setBordered(false);
         this.fields[i].setTextColor(-1182997);
         this.fields[i].setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
         this.fields[i].setMaxLength(18);
         this.fields[i].setValue(String.format(Locale.ROOT, "%.2f", values[i]));
         this.addRenderableWidget(this.fields[i]);
      }

      this.addRenderableWidget(new FlightButton(x, y + 178, 124, 24, FlightUi.localized("screen.deepstrike.save"), b -> this.commit()));
      this.addRenderableWidget(new FlightButton(x + 136, y + 178, 124, 24, FlightUi.localized("ui.deepstrike.route_point_screen.8fbe9b75cb"), b -> this.onClose()));
      this.setInitialFocus(this.fields[0]);
   }

   private String label(int i) {
      return switch (i) {
         case 0 -> "X";
         case 1 -> this.terminal
            ? FlightUi.localized("ui.deepstrike.route_point_screen.3143974e60")
            : FlightUi.localized("ui.deepstrike.route_point_screen.a660f6492a");
         case 2 -> "Z";
         default -> FlightUi.localized("ui.deepstrike.route_point_screen.fb3b3e8af0");
      };
   }

   private void commit() {
      try {
         double[] v = new double[4];

         for (int i = 0; i < 4; i++) {
            v[i] = Double.parseDouble(this.fields[i].getValue().replace(',', '.'));
            if (!Double.isFinite(v[i])) {
               throw new NumberFormatException();
            }
         }

         int min = this.minecraft.level.getMinBuildHeight();
         int max = this.minecraft.level.getMaxBuildHeight() - 1;
         if (Math.abs(v[0]) > 2.9999984E7 || Math.abs(v[2]) > 2.9999984E7) {
            this.error = FlightUi.localized("ui.deepstrike.route_point_screen.38d6074b2c");
            return;
         }

         if (v[1] < min || v[1] > max) {
            this.error = FlightUi.localized("ui.deepstrike.route_point_screen.a81f766411")
               + min
               + FlightUi.localized("ui.deepstrike.route_point_screen.473a94c3ed")
               + max;
            return;
         }

         if (v[3] < this.kind.minSpeed || v[3] > this.kind.maxSpeed) {
            this.error = FlightUi.localized("ui.deepstrike.route_point_screen.bf5d4f6078")
               + this.kind.minSpeed
               + FlightUi.localized("ui.deepstrike.route_point_screen.473a94c3ed")
               + this.kind.maxSpeed
               + FlightUi.localized("ui.deepstrike.route_point_screen.ea49235b0a");
            return;
         }

         this.save.accept(new RoutePoint(v[0], v[1], v[2], v[3]));
         this.onClose();
      } catch (NumberFormatException var4) {
         this.error = FlightUi.localized("ui.deepstrike.route_point_screen.e18026d23b");
      }
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.renderTransparentBackground(g);
      int x = this.width / 2 - 130;
      int y = this.height / 2 - 108;
      FlightUi.panel(g, x - 20, y - 22, 300, 250, -182838503, -10854328);
      FlightUi.label(g, this.title.getString(), x, y - 5, -1182997);
      g.fill(x, y + 14, x + 260, y + 15, -10854328);

      for (int i = 0; i < 4; i++) {
         FlightUi.label(g, this.label(i), x, y + 36 + i * 30, -5196890);
         FlightUi.rounded(g, x + 104, y + 26 + i * 30, 154, 25, -14078937);
      }

      int yy = y + 151;

      for (FormattedCharSequence line : this.font.split(FlightUi.text(this.error), 260)) {
         g.drawString(this.font, line, x, yy, -1076610, false);
         yy += 11;
      }

      super.render(g, mx, my, pt);
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key != 257 && key != 335) {
         return super.keyPressed(key, scan, mods);
      } else {
         this.commit();
         return true;
      }
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
