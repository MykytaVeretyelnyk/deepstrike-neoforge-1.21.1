package dev.sbw.deepstrike.game;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RouteDynamics {
   public static RouteDynamics.Step step(RouteDynamics.State state, List<RoutePoint> route, MissileKind kind, Vec3 launchDirection) {
      if (state.cursor >= route.size()) {
         return new RouteDynamics.Step(state, true);
      } else {
         Vec3 from = state.motion.position();
         RoutePoint point = route.get(state.cursor);
         Vec3 waypoint = position(point);
         boolean terminal = state.cursor == route.size() - 1;
         boolean clearing = state.launchDistance < (kind == MissileKind.JAGM ? 2.5 : 6.5);
         double speed = cornerSpeed(route, state.cursor, from, state.motion.velocity(), kind, FlightUnits.blocksPerTick(point.speed()));
         double velocity = state.motion.velocity().length();
         double lookAhead = Math.max(2.0, Math.min(160.0, velocity * velocity / FlightDynamics.lateralAcceleration(kind, false) * 0.65 + velocity * 0.25));

         for (int i = state.cursor; i < Math.min(route.size(), state.cursor + 96); i++) {
            if (Math.abs(route.get(i).y() - from.y) > 0.5) {
               lookAhead = Math.min(lookAhead, 48.0);
               break;
            }
         }

         Vec3 target = terminal ? waypoint : lookAhead(route, state.cursor, from, lookAhead);
         Vec3 aim = target.subtract(from);
         if (!clearing && velocity > 1.0E-6 && aim.lengthSqr() > 1.0E-10) {
            double alignment = Math.max(-1.0, Math.min(1.0, state.motion.velocity().dot(aim) / (velocity * aim.length())));
            double sine = alignment < 0.0 ? 1.0 : Math.sqrt(Math.max(0.0, 1.0 - alignment * alignment));
            if (sine > 0.001) {
               double radius = aim.length() / (2.0 * sine);
               double captureSpeed = 0.75
                  * Math.min(Math.sqrt(FlightDynamics.lateralAcceleration(kind, false) * radius), FlightDynamics.angularRate(kind, false) * radius);
               speed = Math.min(speed, Math.max(0.0025, captureSpeed * 0.05));
            }
         }

         CruiseDynamics.Command command = new CruiseDynamics.Command(
            target, launchDirection, FlightUnits.kilometresPerHour(speed), clearing, terminal, kind, false, true
         );
         double remaining = from.distanceTo(waypoint);

         for (int ix = state.cursor + 1; ix < route.size(); ix++) {
            remaining += position(route.get(ix - 1)).distanceTo(position(route.get(ix)));
         }

         CruiseDynamics.State motion = state.motion;
         if (!clearing && motion.propulsion() != null && remaining <= velocity * 1.0 * 2.0) {
            motion = new CruiseDynamics.State(from, motion.velocity(), motion.propulsion().cutOff());
         }

         CruiseDynamics.State next = CruiseDynamics.step(motion, command);
         AABB region = new AABB(waypoint, waypoint).inflate(terminal ? 0.05 : 0.25);
         Optional<Vec3> contact = region.clip(from, next.position());
         if (!terminal || !region.contains(from) && !contact.isPresent()) {
            int cursor;
            for (cursor = state.cursor; cursor < route.size() - 1; cursor++) {
               Vec3 node = position(route.get(cursor));
               AABB passage = new AABB(node, node).inflate(0.25);
               Vec3 incoming = cursor == 0 ? launchDirection : node.subtract(position(route.get(cursor - 1))).normalize();
               boolean passed = next.position().subtract(node).dot(incoming) >= 0.0 && next.position().distanceTo(node) <= lookAhead * 1.5;
               if (!passed && !passage.contains(next.position()) && passage.clip(from, next.position()).isEmpty()) {
                  break;
               }
            }

            return new RouteDynamics.Step(new RouteDynamics.State(next, cursor, state.launchDistance + next.position().distanceTo(from)), false);
         } else {
            Vec3 end = contact.orElse(from);
            return new RouteDynamics.Step(
               new RouteDynamics.State(
                  new CruiseDynamics.State(end, next.velocity(), next.propulsion()), state.cursor, state.launchDistance + end.distanceTo(from)
               ),
               true
            );
         }
      }
   }

   private static Vec3 position(RoutePoint p) {
      return new Vec3(p.x(), p.y(), p.z());
   }

   private static Vec3 lookAhead(List<RoutePoint> route, int cursor, Vec3 previous, double distance) {
      if (cursor > 0) {
         Vec3 a = position(route.get(cursor - 1));
         Vec3 leg = position(route.get(cursor)).subtract(a);
         double fraction = leg.lengthSqr() < 1.0E-8 ? 1.0 : Math.max(0.0, Math.min(1.0, previous.subtract(a).dot(leg) / leg.lengthSqr()));
         previous = a.add(leg.scale(fraction));
      }

      for (int i = cursor; i < route.size(); i++) {
         Vec3 next = position(route.get(i));
         double length = previous.distanceTo(next);
         if (length >= distance && length > 1.0E-8) {
            return previous.lerp(next, distance / length);
         }

         distance -= length;
         previous = next;
      }

      return previous;
   }

   private static double cornerSpeed(List<RoutePoint> route, int cursor, Vec3 previous, Vec3 velocity, MissileKind kind, double requested) {
      if (cursor > 0) {
         previous = position(route.get(cursor - 1));
      }

      Vec3 incoming = position(route.get(cursor)).subtract(previous).normalize();
      double ahead = 0.0;
      double incomingLength = Double.POSITIVE_INFINITY;
      double brakingHorizon = Math.max(96.0, velocity.lengthSqr() / 56.0 + velocity.length() * 0.5);
      double lateral = FlightDynamics.lateralAcceleration(kind, false);

      for (int i = cursor; i < Math.min(route.size(), cursor + 512); i++) {
         Vec3 next = position(route.get(i));
         Vec3 delta = next.subtract(previous);
         double length = delta.length();
         if (length < 1.0E-6) {
            previous = next;
         } else {
            Vec3 outgoing = delta.scale(1.0 / length);
            double angle = incoming.lengthSqr() < 0.5 ? 0.0 : Math.acos(Math.max(-1.0, Math.min(1.0, incoming.dot(outgoing))));
            if (angle > 1.0E-4) {
               double radius = Math.min(incomingLength, length) / (2.0 * Math.sin(angle * 0.5));
               double limit = Math.min(Math.sqrt(lateral * Math.max(0.001, radius)), FlightDynamics.angularRate(kind, false) * radius) * 0.05;
               requested = Math.min(requested, Math.sqrt(limit * limit + 0.14 * ahead));
            }

            ahead += length;
            if (ahead > brakingHorizon) {
               break;
            }

            incoming = outgoing;
            incomingLength = length;
            previous = next;
         }
      }

      return Math.max(FlightUnits.blocksPerTick(30.0), requested);
   }

   private RouteDynamics() {
   }

   public record State(CruiseDynamics.State motion, int cursor, double launchDistance) {
   }

   public record Step(RouteDynamics.State state, boolean finished) {
   }
}
