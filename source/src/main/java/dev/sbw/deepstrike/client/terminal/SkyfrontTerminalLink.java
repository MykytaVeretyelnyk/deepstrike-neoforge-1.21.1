package dev.sbw.deepstrike.client.terminal;

import dev.krutlexik.skyfront.client.gui.ShahedTerminalScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** The only place that hooks into Skyfront's terminal screen. */
final class SkyfrontTerminalLink {
   private SkyfrontTerminalLink() {
   }

   /** Adds the Shahed | Missiles tabs to the header of Skyfront's terminal. Re-run on every rebuildWidgets of that screen. */
   static void addTabs(ScreenEvent.Init.Post event) {
      if (event.getScreen() instanceof ShahedTerminalScreen screen) {
         TerminalTabs.add(screen, false, event::addListener, () -> {
         }, () -> MissileTerminalScreen.open(screen, screen.getTerminalPos()));
      }
   }
}
