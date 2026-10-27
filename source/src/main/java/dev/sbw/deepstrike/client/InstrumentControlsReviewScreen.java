package dev.sbw.deepstrike.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

final class InstrumentControlsReviewScreen extends Screen {
   InstrumentControlsReviewScreen() {
      super(FlightUi.text("Instrument controls"));
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      FlightUi.desktop(g, this.width, this.height);
      FlightUi.label(g, "DEEPSTRIKE / CONTROL MATERIALS", 40, 30, -1182997);
      String[] columns = new String[]{"IDLE", "HOVER", "PRESSED", "DISABLED"};
      int start = 190;
      int step = (this.width - 230) / 4;
      int buttonWidth = step - 18;

      for (int col = 0; col < 4; col++) {
         FlightUi.label(g, columns[col], start + col * step, 100, -5196890);
      }

      String[] keys = new String[]{"units", "edit_route", "launch", "delete", "tasks"};
      String[] rows = new String[]{"NAVIGATION", "PRIMARY", "LAUNCH", "DESTRUCTIVE", "SELECTED"};

      for (int row = 0; row < 5; row++) {
         int y = 140 + row * 72;
         FlightUi.label(g, rows[row], 32, y + 8, row == 2 ? -1000096 : (row == 3 ? -1076610 : -4668269));
         String caption = CommandTabletScreen.tr(keys[row]).getString();

         for (int col = 0; col < 4; col++) {
            FlightUi.control(g, start + col * step, y, buttonWidth, 30, caption, col == 1 || col == 2, col != 3, row == 4, col == 2);
         }
      }
   }
}
