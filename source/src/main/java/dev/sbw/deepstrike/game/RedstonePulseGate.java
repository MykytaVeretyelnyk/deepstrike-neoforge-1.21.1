package dev.sbw.deepstrike.game;

public final class RedstonePulseGate {
   private boolean initialized;
   private boolean high;
   private long lowSince;

   public boolean sample(boolean powered, long tick) {
      if (!this.initialized) {
         this.initialized = true;
         this.high = powered;
         this.lowSince = powered ? Long.MAX_VALUE : tick;
         return false;
      } else if (!powered) {
         if (this.high) {
            this.lowSince = tick;
         }

         this.high = false;
         return false;
      } else {
         boolean trigger = !this.high && this.lowSince != Long.MAX_VALUE && tick - this.lowSince >= 4L;
         this.high = true;
         return trigger;
      }
   }
}
