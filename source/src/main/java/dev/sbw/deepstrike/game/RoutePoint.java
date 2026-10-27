package dev.sbw.deepstrike.game;

public record RoutePoint(double x, double y, double z, double speed) {
   public RoutePoint clamped() {
      return new RoutePoint(this.x, this.y, this.z, Math.max(10.0, Math.min(15000.0, this.speed)));
   }
}
