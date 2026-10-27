package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.game.SimpleFlightPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

final class SharpTurnReview {
   private static final List<SharpTurnReview.Flight> flights = new ArrayList<>();
   private static final Set<String> finished = new HashSet<>();
   private static final Map<String, Vec3> previous = new HashMap<>();
   private static final Map<String, Double> distances = new HashMap<>();
   private static long started;

   static boolean tick(ServerLevel level) {
      if (flights.isEmpty()) {
         started = level.getGameTime();
         int index = 0;

         for (double angle : new double[]{90.0, 150.0, 175.0, 180.0}) {
            Vec3 origin = new Vec3(512 + index++ * 512, 1000.0, -800.0);
            Vec3 corner = origin.add(400.0, 0.0, 0.0);
            Vec3 end = corner.add(Math.cos(Math.toRadians(angle)) * 400.0, 0.0, Math.sin(Math.toRadians(angle)) * 400.0);
            List<RoutePoint> mission = List.of(
               new RoutePoint(corner.x, corner.y, corner.z, 1650.0), new RoutePoint(end.x, end.y, end.z, 1650.0)
            );
            GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.FP5);
            missile.setPos(origin);
            missile.setMissionId(UUID.randomUUID());
            missile.setWarhead("none");
            missile.setMission(mission, false);
            missile.setPlannedRoute(SimpleFlightPlan.build(origin, new Vec3(1.0, 0.0, 0.0), MissileKind.FP5, mission));
            missile.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 12);
            missile.setYRot(-90.0F);
            missile.setXRot(0.0F);
            if (!level.addFreshEntity(missile)) {
               throw new IllegalStateException("Sharp review spawn failed");
            }

            flights.add(new SharpTurnReview.Flight("turn-" + angle, missile, end, 800.0));
            previous.put("turn-" + angle, origin);
         }

         Vec3 origin = new Vec3(2560.0, 1000.0, -2000.0);
         Vec3 end = origin.add(2300.0, 0.0, 400.0);
         List<RoutePoint> mission = List.of(
            new RoutePoint(origin.x + 2000.0, 1000.0, origin.z, 1650.0),
            new RoutePoint(origin.x + 2000.0, 1000.0, origin.z + 400.0, 1650.0),
            new RoutePoint(end.x, 1000.0, end.z, 1650.0)
         );
         GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.FP5);
         missile.setPos(origin);
         missile.setMissionId(UUID.randomUUID());
         missile.setWarhead("none");
         missile.setMission(mission, false);
         missile.setPlannedRoute(SimpleFlightPlan.build(origin, new Vec3(1.0, 0.0, 0.0), MissileKind.FP5, mission));
         missile.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 12);
         missile.setYRot(-90.0F);
         missile.setXRot(0.0F);
         if (!level.addFreshEntity(missile)) {
            throw new IllegalStateException("Long sharp review spawn failed");
         }

         flights.add(new SharpTurnReview.Flight("long-fast-approach", missile, end, 2700.0));
         previous.put("long-fast-approach", origin);
      }

      for (SharpTurnReview.Flight flight : flights) {
         if (!finished.contains(flight.label)) {
            GuidedMissileEntity missile = flight.missile;
            Vec3 now = missile.position();
            Vec3 before = previous.put(flight.label, now);
            double distance = distances.merge(flight.label, now.distanceTo(before), Double::sum);
            if (distance > flight.length * 1.7) {
               throw new IllegalStateException("Circling on actual entity " + flight.label + " distance=" + distance);
            }

            if (Math.abs(now.y - 1000.0) > (distance < 400.0 ? 34 : 1)) {
               throw new IllegalStateException("Sharp turn changed altitude " + flight.label + " y=" + now.y);
            }

            if (!missile.isAlive()) {
               double error = missile.impactPosition().distanceTo(flight.end);
               if (error > 1.0) {
                  throw new IllegalStateException("Sharp entity missed endpoint " + flight.label + " error=" + error);
               }

               finished.add(flight.label);
               LoggerFactory.getLogger(SharpTurnReview.class)
                  .info("SHARP_ENTITY_PASS label={} distance={} error={} ticks={}", new Object[]{flight.label, distance, error, level.getGameTime() - started});
            }
         }
      }

      if (level.getGameTime() - started > 1600L) {
         throw new IllegalStateException("Sharp route flight timed out");
      } else {
         return finished.size() == flights.size();
      }
   }

   private SharpTurnReview() {
   }

   private record Flight(String label, GuidedMissileEntity missile, Vec3 end, double length) {
   }
}
