package dev.sbw.deepstrike.game;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class SimpleFlightPlan {
   public static List<RoutePoint> build(Vec3 start, Vec3 forward, MissileKind kind, List<RoutePoint> requested) {
      if (requested.isEmpty()) {
         return List.of();
      } else {
         ArrayList<RoutePoint> mission = new ArrayList<>();

         for (int i = 0; i < requested.size(); i++) {
            RoutePoint p = requested.get(i);
            double y = p.y();
            double speed = Math.max((double)kind.minSpeed, Math.min((double)kind.defaultSpeed, p.speed()));
            mission.add(new RoutePoint(p.x(), y, p.z(), speed));
         }

         ArrayList<RoutePoint> result = new ArrayList<>();
         Vec3 exit = start.add(forward.normalize().scale(kind == MissileKind.JAGM ? 2.5 : 6.5));
         result.add(new RoutePoint(exit.x, exit.y, exit.z, mission.get(0).speed()));
         if (kind == MissileKind.FP5) {
            Vec3 heading = new Vec3(forward.x, 0.0, forward.z).normalize();
            if (heading.lengthSqr() < 1.0E-8) {
               heading = new Vec3(0.0, 0.0, 1.0);
            }

            Vec3 railExit = exit;

            for (int i = 1; i <= 16; i++) {
               double t = i / 16.0;
               exit = railExit.add(heading.scale(80.0 * t)).add(0.0, 32.0 * t, 0.0);
               result.add(new RoutePoint(exit.x, exit.y, exit.z, mission.get(0).speed()));
            }

            RoutePoint first = mission.get(0);
            double settle = Math.min(160.0, exit.distanceTo(new Vec3(first.x(), first.y(), first.z())) * 0.35);
            Vec3 level = exit.add(heading.scale(settle));
            mission.add(0, new RoutePoint(level.x, first.y(), level.z, first.speed()));
         }

         ArrayList<Vec3> anchors = new ArrayList<>();
         anchors.add(exit);

         for (RoutePoint p : mission) {
            anchors.add(new Vec3(p.x(), p.y(), p.z()));
         }

         ArrayList<Vec3> tangents = new ArrayList<>();

         for (int i = 0; i < anchors.size(); i++) {
            Vec3 incoming = i == 0 ? forward : anchors.get(i).subtract(anchors.get(i - 1));
            Vec3 outgoing = i == anchors.size() - 1 ? incoming : anchors.get(i + 1).subtract(anchors.get(i));
            Vec3 in = new Vec3(incoming.x, 0.0, incoming.z).normalize();
            Vec3 out = new Vec3(outgoing.x, 0.0, outgoing.z).normalize();
            Vec3 direction = i == 0 ? in : (i == anchors.size() - 1 ? in : in.add(out).normalize());
            if (direction.lengthSqr() < 1.0E-8) {
               direction = new Vec3(-in.z, 0.0, in.x);
            }

            tangents.add(direction.scale(Math.min(incoming.horizontalDistance(), outgoing.horizontalDistance()) * 0.9));
         }

         for (int leg = 0; leg < mission.size(); leg++) {
            RoutePoint p = mission.get(leg);
            Vec3 previous = anchors.get(leg);
            Vec3 end = anchors.get(leg + 1);
            Vec3 startTangent = tangents.get(leg);
            Vec3 endTangent = tangents.get(leg + 1);
            int samples = Math.max(2, Math.min(512, (int)Math.ceil(previous.distanceTo(end) / 6.0)));

            for (int sample = 1; sample <= samples; sample++) {
               double t = (double)sample / samples;
               double t2 = t * t;
               double t3 = t2 * t;
               double h0 = 2.0 * t3 - 3.0 * t2 + 1.0;
               double h1 = t3 - 2.0 * t2 + t;
               double h2 = -2.0 * t3 + 3.0 * t2;
               double h3 = t3 - t2;
               double vertical = leg == mission.size() - 1 ? t2 * (2.0 - t) : t2 * (3.0 - 2.0 * t);
               result.add(
                  new RoutePoint(
                     previous.x * h0 + startTangent.x * h1 + end.x * h2 + endTangent.x * h3,
                     previous.y + (end.y - previous.y) * vertical,
                     previous.z * h0 + startTangent.z * h1 + end.z * h2 + endTangent.z * h3,
                     p.speed()
                  )
               );
            }
         }

         return List.copyOf(result);
      }
   }

   private SimpleFlightPlan() {
   }
}
