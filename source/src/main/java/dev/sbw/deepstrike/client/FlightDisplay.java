package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.FlightPresentationClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;

final class FlightDisplay {
   private static final FlightPresentationClock CLOCK = new FlightPresentationClock();
   private static ClientLevel level;
   private static boolean frame;
   private static double frameTime = Double.NaN;

   private static void sync() {
      if (level != Minecraft.getInstance().level) {
         level = Minecraft.getInstance().level;
         CLOCK.clear();
         frameTime = Double.NaN;
         frame = false;
      }
   }

   static void observe(long tick) {
      sync();
      CLOCK.observe(tick, System.nanoTime());
   }

   static double time() {
      sync();
      return frame ? frameTime : CLOCK.sample(System.nanoTime());
   }

   static void renderTick(RenderTickEvent event) {
      sync();
      frame = event.phase == Phase.START;
      if (frame) {
         frameTime = CLOCK.sample(System.nanoTime());
      }
   }

   private FlightDisplay() {
   }
}
