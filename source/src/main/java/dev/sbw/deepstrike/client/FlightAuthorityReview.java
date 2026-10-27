package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.FlightTimeline;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.game.SimpleFlightPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import org.slf4j.LoggerFactory;

final class FlightAuthorityReview {
   private static final Map<String, Integer> COUNTS = new HashMap<>();
   private static final List<FlightAuthorityReview.Trial> TRIALS = new ArrayList<>();
   private static volatile boolean started;
   private static volatile boolean complete;

   static void inspect(String kind, FlightTimeline track, Vec3 rendered) {
      double error = track.distanceToPath(rendered);
      if (error > 1.0E-6) {
         throw new IllegalStateException("Rendered missile departed confirmed path: " + kind + " error=" + error);
      } else {
         int count = COUNTS.merge(kind, 1, Integer::sum);
         if (count == 60 || count % 1200 == 0) {
            LoggerFactory.getLogger(FlightAuthorityReview.class)
               .info("FLIGHT_AUTHORITY_RENDER_PASS kind={} frames={} pathError={}", new Object[]{kind, count, error});
         }
      }
   }

   static void tick(ClientTickEvent event) {
      if (event.phase == Phase.END
         && Boolean.getBoolean("deepstrike.authorityReview")
         && Boolean.getBoolean("deepstrike.flightRealismReview")
         && Boolean.getBoolean("deepstrike.largeExhaustReview")) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && mc.player.tickCount >= 60 && mc.gameDirectory.toString().contains("/build/")) {
            if (!started) {
               started = true;
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        int index = 0;

                        for (MissileKind kind : MissileKind.values()) {
                           if (kind != MissileKind.PEACEKEEPER) {
                              double leg = kind == MissileKind.JAGM ? 100.0 : 350.0;
                              int lane = index++;
                              Vec3 origin = new Vec3(500 + lane * 600, 1000.0, -1500 - lane * 600);
                              Vec3 corner = origin.add(leg, 0.0, 0.0);
                              Vec3 end = corner.add(0.0, 0.0, leg);
                              List<RoutePoint> route = List.of(
                                 new RoutePoint(corner.x, corner.y, corner.z, kind.defaultSpeed),
                                 new RoutePoint(end.x, end.y, end.z, kind.defaultSpeed)
                              );
                              GuidedMissileEntity missile = new GuidedMissileEntity(level, kind);
                              missile.setPos(origin);
                              missile.setMissionId(UUID.randomUUID());
                              missile.setWarhead("none");
                              missile.setMission(route, false);
                              missile.setPlannedRoute(SimpleFlightPlan.build(origin, new Vec3(1.0, 0.0, 0.0), kind, route));
                              missile.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 12);
                              missile.setDeltaMovement(new Vec3(0.5, 0.0, 0.0));
                              missile.setYRot(-90.0F);
                              missile.setXRot(0.0F);
                              if (!level.addFreshEntity(missile)) {
                                 throw new IllegalStateException("Could not spawn flight authority trial");
                              }

                              TRIALS.add(new FlightAuthorityReview.Trial(missile, end));
                           }
                        }
                     }
                  );
            }

            if (mc.player.tickCount % 20 == 0 && !complete) {
               mc.getSingleplayerServer().execute(() -> {
                  if (TRIALS.size() == 4) {
                     int done = 0;

                     for (FlightAuthorityReview.Trial trial : TRIALS) {
                        if (!trial.missile.isAlive()) {
                           double error = trial.missile.impactPosition().distanceTo(trial.target);
                           if (error > 1.8) {
                              throw new IllegalStateException("Authority trial missed target: " + trial.missile.kind() + " error=" + error);
                           }

                           done++;
                        }
                     }

                     if (done == 4) {
                        complete = true;
                        LoggerFactory.getLogger(FlightAuthorityReview.class).info("FLIGHT_AUTHORITY_ALL_CRUISE_ARRIVED kinds=FP5,STORM_SHADOW,JAGM,SPIKE_NLOS");
                     }
                  }
               });
            }
         }
      }
   }

   static void requireComplete() {
      if (Boolean.getBoolean("deepstrike.authorityReview") && Boolean.getBoolean("deepstrike.largeExhaustReview") && !complete) {
         throw new IllegalStateException("Not all flight authority route trials completed");
      }
   }

   private FlightAuthorityReview() {
   }

   private record Trial(GuidedMissileEntity missile, Vec3 target) {
   }
}
