package dev.sbw.deepstrike.game;

import java.util.List;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CruiseRouteDynamics {
   public static CruiseRouteDynamics.Step step(
      CruiseRouteDynamics.State state, List<RoutePoint> route, MissileKind kind, Vec3 launchDirection, boolean loft, boolean fallback
   ) {
      if (state.cursor() >= route.size()) {
         return new CruiseRouteDynamics.Step(state, true);
      } else {
         Vec3 from = state.motion().position();
         RoutePoint point = route.get(state.cursor());
         Vec3 target = new Vec3(point.x(), point.y(), point.z());
         boolean terminal = state.cursor() == route.size() - 1;
         boolean clearing = state.launchDistance() < (kind == MissileKind.JAGM ? 2.5 : 6.5);
         Vec3 navigation = target;
         boolean terminalCommand = terminal;
         if (!clearing && !loft) {
            Vec3 leg = target.subtract(state.origin());
            double horizontalSquared = leg.horizontalDistanceSqr();
            if (horizontalSquared > 1.0) {
               double progress = ((from.x - state.origin().x) * leg.x + (from.z - state.origin().z) * leg.z)
                  / horizontalSquared;
               double lead = Math.max(12.0, Math.min(120.0, state.motion().velocity().length() * 0.8));
               double fraction = Mth.clamp(progress + lead / Math.sqrt(horizontalSquared), 0.0, 1.0);
               navigation = new Vec3(target.x, Mth.lerp(fraction, state.origin().y, target.y), target.z);
            }
         }

         if (loft && !fallback && !clearing) {
            double horizontal = target.subtract(from).horizontalDistance();
            double velocity = state.motion().velocity().length();
            double lookAhead = Math.max(24.0, Math.min(500.0, velocity * velocity / FlightDynamics.lateralAcceleration(kind, fallback) * 1.5 + velocity * 0.5));
            if (horizontal > lookAhead) {
               double height = Math.max(state.origin().y, target.y) + Math.min(48.0, target.distanceTo(state.origin()) * 0.22);
               height = Math.min(height, target.y + horizontal * 0.15);
               double fraction = lookAhead / horizontal;
               navigation = new Vec3(Mth.lerp(fraction, from.x, target.x), height, Mth.lerp(fraction, from.z, target.z));
               terminalCommand = false;
            }
         }

         double turnLead = Math.max(6.0, Math.min(1500.0, state.motion().velocity().lengthSqr() / FlightDynamics.lateralAcceleration(kind, fallback) * 1.4));
         if (!terminal && !clearing) {
            RoutePoint following = route.get(state.cursor() + 1);
            Vec3 outgoing = new Vec3(following.x(), following.y(), following.z()).subtract(target);
            double distance = from.distanceTo(target);
            if (distance < turnLead) {
               double blend = 1.0 - distance / turnLead;
               blend = blend * blend * (3.0 - 2.0 * blend);
               navigation = target.add(outgoing.normalize().scale(Math.min(turnLead, outgoing.length() * 0.45) * blend));
            }
         }

         CruiseDynamics.Command command = new CruiseDynamics.Command(
            navigation, launchDirection, point.speed(), clearing, terminalCommand, kind, fallback, false
         );
         CruiseDynamics.State next = CruiseDynamics.step(state.motion(), command);
         AABB region = new AABB(target, target).inflate(terminal ? 1.0 : 2.0);
         Optional<Vec3> contact = clearing ? Optional.empty() : region.clip(from, next.position());
         boolean reached = !clearing && (region.contains(from) || contact.isPresent());
         if (!terminal && !clearing && from.distanceTo(target) < turnLead) {
            new Vec3(route.get(state.cursor() + 1).x(), route.get(state.cursor() + 1).y(), route.get(state.cursor() + 1).z());
            reached |= next.position().distanceTo(target) <= turnLead * 0.65;
         }

         if (terminal && reached) {
            Vec3 end = contact.orElse(from);
            return new CruiseRouteDynamics.Step(
               new CruiseRouteDynamics.State(
                  new CruiseDynamics.State(end, next.velocity(), next.propulsion()),
                  state.cursor(),
                  state.launchDistance() + end.distanceTo(from),
                  state.origin()
               ),
               true
            );
         } else {
            return new CruiseRouteDynamics.Step(
               new CruiseRouteDynamics.State(
                  next, state.cursor() + (reached ? 1 : 0), state.launchDistance() + next.position().distanceTo(from), reached ? target : state.origin()
               ),
               false
            );
         }
      }
   }

   private CruiseRouteDynamics() {
   }

   public record State(CruiseDynamics.State motion, int cursor, double launchDistance, Vec3 origin) {
   }

   public record Step(CruiseRouteDynamics.State state, boolean finished) {
   }
}
