package dev.sbw.deepstrike.game;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class FlightRoutePlanner {
   private final Vec3 start;
   private final Vec3 forward;
   private final MissileKind kind;
   private final List<RoutePoint> mission;
   private List<RoutePoint> result;
   private boolean done;
   private FlightRoutePlanner.FailureReason failureReason = FlightRoutePlanner.FailureReason.NONE;

   public FlightRoutePlanner(ServerLevel level, BlockPos launcher, Vec3 start, Vec3 forward, MissileKind kind, List<RoutePoint> mission) {
      this.start = start;
      this.forward = forward;
      this.kind = kind;
      this.mission = List.copyOf(mission);
   }

   public void step() {
      if (!this.done) {
         this.result = SimpleFlightPlan.build(this.start, this.forward, this.kind, this.mission);
         if (this.result.isEmpty()) {
            this.fail(FlightRoutePlanner.FailureReason.NO_CORRIDOR);
         } else {
            this.done = true;
         }
      }
   }

   public boolean done() {
      return this.done;
   }

   public List<RoutePoint> result() {
      return this.done && this.failureReason == FlightRoutePlanner.FailureReason.NONE ? this.result : null;
   }

   public FlightRoutePlanner.FailureReason failureReason() {
      return this.failureReason;
   }

   public String diagnostics() {
      return "simple=true, done=" + this.done + ", points=" + (this.result == null ? 0 : this.result.size());
   }

   public void cancel() {
      if (!this.done) {
         this.fail(FlightRoutePlanner.FailureReason.TIME_LIMIT);
      }
   }

   private void fail(FlightRoutePlanner.FailureReason reason) {
      this.failureReason = reason;
      this.result = null;
      this.done = true;
   }

   public static enum FailureReason {
      NONE,
      TIME_LIMIT,
      TERRAIN_UNAVAILABLE,
      NO_CORRIDOR,
      WORK_BUDGET,
      MANEUVER_LIMIT;
   }
}
