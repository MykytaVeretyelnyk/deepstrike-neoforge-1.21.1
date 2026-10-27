package dev.sbw.deepstrike.game;

public final class FlightPresentationClock {
   private double offset = Double.NaN;
   private double lastTime = Double.NaN;
   private long newestTick = Long.MIN_VALUE;

   public void clear() {
      this.offset = Double.NaN;
      this.lastTime = Double.NaN;
      this.newestTick = Long.MIN_VALUE;
   }

   public void observe(long serverTick, long nowNanos) {
      if (serverTick > this.newestTick) {
         this.newestTick = serverTick;
         double observed = nowNanos / 5.0E7 - serverTick;
         if (Double.isNaN(this.offset)) {
            this.offset = observed;
         } else {
            this.offset = this.offset + Math.max(-2.0, Math.min(2.0, observed - this.offset)) * 0.12;
         }
      }
   }

   public double sample(long nowNanos) {
      if (Double.isNaN(this.offset)) {
         return Double.NaN;
      } else {
         double desired = Math.min((double)this.newestTick, nowNanos / 5.0E7 - this.offset - 3.0);
         this.lastTime = Double.isNaN(this.lastTime) ? desired : Math.max(this.lastTime, desired);
         return this.lastTime;
      }
   }
}
