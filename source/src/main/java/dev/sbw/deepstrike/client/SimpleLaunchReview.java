package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.mojang.authlib.GameProfile;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.LoggerFactory;

final class SimpleLaunchReview {
   private static final List<SimpleLaunchReview.Case> cases = new ArrayList<>();
   private static volatile boolean done;
   private static volatile String failure;
   private static long start;
   private static final Map<UUID, Vec3> previousPositions = new HashMap<>();
   private static final Map<UUID, Double> plateauErrors = new HashMap<>();
   private static final Set<String> altitudeChecks = new HashSet<>();
   private static final Map<UUID, GuidedMissileEntity> missiles = new HashMap<>();
   private static final Set<UUID> launched = new HashSet<>();
   private static final Set<UUID> arrived = new HashSet<>();

   static void tick(Minecraft mc) {
      if (failure != null) {
         throw new IllegalStateException(failure);
      } else {
         if (done) {
            LoggerFactory.getLogger(SimpleLaunchReview.class).info("FP5_OPTIONS_FINISHED simpleLaunch=true");
            mc.stop();
         }
      }
   }

   static void serverTick(Minecraft mc) {
      if (!done && failure == null) {
         try {
            ServerLevel level = mc.getSingleplayerServer().overworld();
            if (cases.isEmpty()) {
               start = level.getGameTime();
               FakePlayer pilot = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SimpleLaunch"));
               int i = 0;

               for (MissileKind kind : List.of(MissileKind.FP5, MissileKind.STORM_SHADOW, MissileKind.JAGM)) {
                  BlockPos pad = new BlockPos(i++ * 128, 250, 0);
                  LauncherBlock block = kind == MissileKind.JAGM ? (LauncherBlock)BFBlocks.JAGM_LAUNCHER.get() : (LauncherBlock)BFBlocks.FP5_LAUNCHER.get();
                  level.setBlockAndUpdate(pad, block.defaultBlockState());
                  LauncherBlockEntity launcher = (LauncherBlockEntity)level.getBlockEntity(pad);
                  Item item = kind == MissileKind.JAGM
                     ? (Item)BFItems.JAGM.get()
                     : (kind == MissileKind.FP5 ? (Item)BFItems.FP5.get() : (Item)BFItems.STORM_SHADOW.get());
                  ItemStack stack = new ItemStack(item);
                  new MissileData(kind, "none", "rocket", List.of(), MissileData.defaultCamo(kind), 1000, 1000).writeToItemStack(stack);
                  pilot.setPos(Vec3.atCenterOf(pad).add(0.0, 1.0, 4.0));
                  require(launcher.load(stack, pilot), "Load rejected " + kind);
                  double distance = kind == MissileKind.JAGM ? 240.0 : (kind == MissileKind.FP5 ? 2200.0 : 800.0);
                  Vec3 target = new Vec3(pad.getX() + 0.5, 270.0, distance);
                  double cruiseY = kind == MissileKind.JAGM ? 270.0 : 310.0;
                  List<RoutePoint> route = List.of(
                     new RoutePoint(target.x, cruiseY, distance * 0.4, kind.defaultSpeed),
                     new RoutePoint(target.x, cruiseY, distance * 0.7, kind.defaultSpeed),
                     new RoutePoint(target.x, target.y, target.z, kind.defaultSpeed)
                  );
                  UUID mission = UUID.randomUUID();
                  require(launcher.fire(pilot, route, false, "002", mission), "Fire rejected " + kind + " " + launcher.getLastFireError());
                  cases.add(new SimpleLaunchReview.Case(kind, launcher, mission, NbtCompat.saveBE(launcher).getLong("LaunchAtTick"), target, cruiseY));
               }
            }

            for (SimpleLaunchReview.Case c : cases) {
               ActiveFlights flights = ActiveFlights.get(level);
               if (!flights.wasLaunched(c.mission)) {
                  require(level.getGameTime() <= c.deadline, "Missed 100tick ignition deadline " + c.kind);
               } else {
                  require(level.getGameTime() >= c.deadline, "Early ignition " + c.kind);
                  if (launched.add(c.mission)) {
                     long ignition = -1L;

                     for (Tag raw : flights.save(new CompoundTag()).getList("Missions", 10)) {
                        CompoundTag record = (CompoundTag)raw;
                        if (record.hasUUID("MissionId") && record.getUUID("MissionId").equals(c.mission)) {
                           ignition = record.getLong("LaunchTick");
                        }
                     }

                     require(ignition == c.deadline, "Deadline shifted " + c.kind + " " + ignition + " != " + c.deadline);
                     LoggerFactory.getLogger(SimpleLaunchReview.class).info("SIMPLE_LAUNCH_PASS kind={} ignitionDelay={}", c.kind, ignition - start);
                  }

                  if (!arrived.contains(c.mission)) {
                     if (level.getEntity(flights.launchedFlight(c.mission)) instanceof GuidedMissileEntity live) {
                        missiles.put(c.mission, live);
                     }

                     GuidedMissileEntity missile = missiles.get(c.mission);
                     require(missile != null, "Launched missile missing " + c.kind);
                     Vec3 now = missile.position();
                     Vec3 before = previousPositions.put(c.mission, now);
                     if (before != null) {
                        for (double fraction : new double[]{0.4, 0.7}) {
                           double z = c.target.z * fraction;
                           String key = c.mission + ":" + fraction;
                           if (before.z < z && now.z >= z && altitudeChecks.add(key)) {
                              double y = before.lerp(now, (z - before.z) / (now.z - before.z)).y;
                              require(
                                 Math.abs(y - c.cruiseY) < 2.0,
                                 "Waypoint altitude missed " + c.kind + " fraction=" + fraction + " expected=" + c.cruiseY + " actual=" + y
                              );
                              LoggerFactory.getLogger(SimpleLaunchReview.class)
                                 .info("MANDATORY_ALTITUDE_PASS kind={} fraction={} expected={} actual={}", new Object[]{c.kind, fraction, c.cruiseY, y});
                           }
                        }

                        if (now.z > c.target.z * 0.45 && now.z < c.target.z * 0.65) {
                           plateauErrors.merge(c.mission, Math.abs(now.y - c.cruiseY), Math::max);
                        }
                     }

                     if (!missile.isAlive()) {
                        double error = missile.impactPosition().distanceTo(c.target);
                        require(error < 2.0, "Missile missed final altitude/position " + c.kind + " error=" + error);
                        require(
                           altitudeChecks.contains(c.mission + ":0.4") && altitudeChecks.contains(c.mission + ":0.7"), "Missing altitude crossings " + c.kind
                        );
                        require(plateauErrors.getOrDefault(c.mission, Double.POSITIVE_INFINITY) < 2.0, "Level-flight altitude drift " + c.kind);
                        arrived.add(c.mission);
                        LoggerFactory.getLogger(SimpleLaunchReview.class).info("SIMPLE_ARRIVAL_PASS kind={} error={}", c.kind, error);
                     }
                  }
               }
            }

            require(level.getGameTime() - start < 1500L, "Flight deadline exceeded");
            done = SharpTurnReview.tick(level) && arrived.size() == cases.size();
         } catch (Throwable var19) {
            failure = var19.toString();
            LoggerFactory.getLogger(SimpleLaunchReview.class).error("SIMPLE_LAUNCH_FAILED", var19);
         }
      }
   }

   private static void require(boolean ok, String message) {
      if (!ok) {
         throw new IllegalStateException(message);
      }
   }

   private SimpleLaunchReview() {
   }

   private record Case(MissileKind kind, LauncherBlockEntity launcher, UUID mission, long deadline, Vec3 target, double cruiseY) {
   }
}
