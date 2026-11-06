package dev.sbw.deepstrike.client.terminal;

import dev.krutlexik.skyfront.client.gui.TerminalButton;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Two tabs, "Shahed" and "Missiles", in the header strip of the terminal. The same strip is used on Skyfront's own screen and on
 * {@link MissileTerminalScreen}, so switching looks like flipping a tab of one console. The current tab is lit and does nothing.
 */
final class TerminalTabs {
   private static final int GAP = 2;
   private static final int HEIGHT = 14;

   private TerminalTabs() {
   }

   /**
    * Header geometry shared with ShahedTerminalScreen: the panel spans width/2-180 .. width/2+180, the header strip starts at
    * height/2-92 and holds the title plate in the middle. The tabs take the space left of the plate.
    */
   static void add(Screen screen, boolean missilesActive, Consumer<TerminalButton> adder, Runnable toShahed, Runnable toMissiles) {
      Font font = Minecraft.getInstance().font;
      int centre = screen.width / 2;
      Component plateTitle = Component.translatable("gui.skyfront.terminal.title");
      int plateLeft = centre - (font.width(plateTitle) + 10) / 2;
      int left = centre - 168;
      int room = Math.max(2 * 28 + GAP, plateLeft - 4 - left);
      int each = Mth.clamp((room - GAP) / 2, 28, 54);
      int y = screen.height / 2 - 92 + 5;

      adder.accept(tab(left, y, each, Component.translatable("gui.deepstrike.terminal.tab.shahed"), !missilesActive, toShahed));
      adder.accept(tab(left + each + GAP, y, each, Component.translatable("gui.deepstrike.terminal.open"), missilesActive, toMissiles));
   }

   private static TerminalButton tab(int x, int y, int width, Component label, boolean current, Runnable action) {
      TerminalButton button = new TerminalButton(x, y, width, HEIGHT, label, b -> {
         if (!current) {
            action.run();
         }
      });
      return current ? button.style(TerminalButton.Style.LIT) : button;
   }
}
