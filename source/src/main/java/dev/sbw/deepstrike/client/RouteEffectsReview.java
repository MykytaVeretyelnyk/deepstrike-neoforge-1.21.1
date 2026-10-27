package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.BurningDebris;
import dev.sbw.deepstrike.game.FlightRoutePlanner;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class RouteEffectsReview {
   private static final Logger LOG = LoggerFactory.getLogger(RouteEffectsReview.class);
   private static final Vec3 START = new Vec3(0.5, 140.0, 0.5);
   private static final Vec3 END = new Vec3(2400.5, 100.0, 0.5);
   private static final Vec3 FIRE = new Vec3(16.5, 141.0, 16.5);
   private static volatile boolean initialized;
   private static volatile boolean done;
   private static volatile boolean flightDone;
   private static volatile long fireStarted;
   private static volatile int flightAge;
   private static volatile Vec3 flightPosition = START;
   private static volatile String failure;
   private static FlightRoutePlanner planner;
   private static GuidedMissileEntity missile;
   private static GuidedMissileEntity observer;
   private static int ticks;
   private static int launchTick;
   private static int lastFireCheck;
   private static int flashAge = -1;
   private static boolean sentFlash;
   private static final Set<String> captures = new HashSet<>();

   static void tick(Minecraft mc) {
      if (Boolean.getBoolean("deepstrike.simpleLaunchReview")) {
         SimpleLaunchReview.tick(mc);
      } else if (failure != null) {
         throw new IllegalStateException(failure);
      } else if (initialized) {
         if (observer == null) {
            observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
         }

         if (flightDone && !sentFlash) {
            sentFlash = true;
            flashAge = 0;
         } else if (flashAge >= 0) {
            flashAge++;
         }

         setCamera(mc);
         if (flashAge == 8) {
            MissileExplosionEffects.accept(
               new DeepStrikeNetwork.BlastEffect(MissileKind.FP5.ordinal(), FIRE.x, FIRE.y + 8.0, FIRE.z, 42L, "he")
            );
         }

         if (done) {
            LOG.info("FP5_OPTIONS_FINISHED routeEffects=true");
            mc.stop();
         }
      }
   }

   static void serverTick(Minecraft mc) {
      if (Boolean.getBoolean("deepstrike.simpleLaunchReview")) {
         SimpleLaunchReview.serverTick(mc);
      } else if (failure == null && !done) {
         try {
            ServerLevel level = mc.getSingleplayerServer().overworld();
            if (!initialized) {
               level.setWeatherParameters(100000, 0, false, false);

               for (int x = 1198; x <= 1202; x++) {
                  for (int y = 70; y <= 310; y++) {
                     for (int z = -20; z <= 20; z++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                     }
                  }
               }

               for (int x = 2394; x <= 2406; x++) {
                  for (int z = -6; z <= 6; z++) {
                     level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 2);

                     for (int y = 100; y < level.getMaxBuildHeight(); y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                     }
                  }
               }

               for (int x = 10; x <= 22; x++) {
                  for (int z = 10; z <= 22; z++) {
                     level.setBlock(new BlockPos(x, 139, z), Blocks.STONE.defaultBlockState(), 2);
                     level.setBlock(new BlockPos(x, 140, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                     level.setBlock(new BlockPos(x, 141, z), Blocks.AIR.defaultBlockState(), 2);
                  }
               }

               for (ServerPlayer player : level.players()) {
                  player.teleportTo(FIRE.x, FIRE.y + 3.0, FIRE.z + 24.0);
               }

               List<BlockPos> fuel = BurningDebris.fuelNear(level, FIRE);
               require(!fuel.isEmpty(), "Fuel detection failed");
               int attempts = 0;

               while (fires(level) == 0 && attempts++ < 40) {
                  BurningDebris.ignite(level, FIRE, fuel);
               }

               require(fires(level) > 0, "No residual fire in40 production ignition attempts");
               fireStarted = level.getGameTime();
               LOG.info("ROUTE_EFFECTS_FIRE_START attempts={} fuel={} fires={} tick={}", new Object[]{attempts, fuel.size(), fires(level), fireStarted});
               LOG.info("ROUTE_EFFECTS_TARGET_HEIGHT top={} target={}", level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(END)), END);
               List<RoutePoint> route = List.of(new RoutePoint(400.5, 300.0, 0.5, 600.0), new RoutePoint(END.x, END.y, END.z, 600.0));
               planner = new FlightRoutePlanner(level, BlockPos.containing(START), START, new Vec3(1.0, 0.0, 0.0), MissileKind.FP5, route);
               initialized = true;
            }

            ticks++;
            if (missile == null) {
               if (!planner.done()) {
                  planner.step();
               }

               if (ticks % 200 == 0) {
                  LOG.info("ROUTE_EFFECTS_PLANNING {}", planner.diagnostics());
               }

               if (planner.done()) {
                  List<RoutePoint> route = planner.result();
                  require(route != null, "Planner rejected fixture corridor: " + planner.failureReason() + " " + planner.diagnostics());
                  double maxZ = route.stream().mapToDouble(p -> Math.abs(p.z() - 0.5)).max().orElse(0.0);
                  require(maxZ > 15.0, "Planner did not detour around the tall wall");
                  LOG.info("ROUTE_EFFECTS_PLAN_PASS points={} maxZ={} ticks={}", new Object[]{route.size(), maxZ, ticks});
                  missile = new GuidedMissileEntity(level, MissileKind.FP5);
                  missile.setPos(START);
                  missile.setMissionId(UUID.randomUUID());
                  missile.setWarhead("none");
                  missile.setMission(List.of(new RoutePoint(END.x, END.y, END.z, 600.0)), false);
                  missile.setPlannedRoute(route);
                  missile.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 12);
                  missile.setDeltaMovement(Vec3.ZERO);
                  missile.setYRot(-90.0F);
                  missile.setXRot(0.0F);
                  level.addFreshEntity(missile);
                  launchTick = ticks;
               }
            } else if (!flightDone) {
               flightAge = ticks - launchTick;
               flightPosition = missile.position();
               if (!missile.isAlive()) {
                  double error = missile.impactPosition().distanceTo(END);
                  require(error < 3.0, "Actual planned flight ended early: error=" + error + " position=" + missile.position());
                  flightDone = true;
                  LOG.info("ROUTE_EFFECTS_FLIGHT_PASS age={} impact={} error={}", new Object[]{flightAge, missile.impactPosition(), error});
               }

               require(flightAge < 1600, "Planned flight did not finish");
            }

            long age = level.getGameTime() - fireStarted;
            if (age >= lastFireCheck + 400 && age < 2400L) {
               lastFireCheck = (int)age;
               require(fires(level) > 0, "Residual fire died before2minutes at age=" + age);
               LOG.info("ROUTE_EFFECTS_FIRE_ALIVE age={} fires={}", age, fires(level));
            }

            if (age >= 2440L && flightDone) {
               LOG.info("ROUTE_EFFECTS_FIRE_DURATION_PASS elapsedTicks={} (production maintenance expires2400)", age);
               done = true;
            }

            require(ticks < 4400, "Combined scene timed out");
         } catch (Throwable var5) {
            failure = var5.toString();
            LOG.error("ROUTE_EFFECTS_FAILURE", var5);
         }
      }
   }

   private static int fires(ServerLevel level) {
      int count = 0;

      for (BlockPos p : BlockPos.betweenClosed(10, 140, 10, 22, 142, 22)) {
         if (level.getBlockState(p).is(Blocks.FIRE)) {
            count++;
         }
      }

      return count;
   }

   private static void require(boolean condition, String message) {
      if (!condition) {
         throw new IllegalStateException(message);
      }
   }

   private static void setCamera(Minecraft mc) {
      Vec3 target = missile != null && !flightDone ? flightPosition : FIRE.add(0.0, 5.0, 0.0);
      Vec3 position = target.add(0.0, 3.0, missile != null && !flightDone ? 10.0 : 24.0);
      observer.setPos(position);
      observer.xo = position.x;
      observer.yo = position.y;
      observer.zo = position.z;
      Vec3 direction = target.subtract(observer.getEyePosition());
      observer.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
      observer.setXRot((float)(-Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()))));
      observer.yRotO = observer.getYRot();
      observer.xRotO = observer.getXRot();
      mc.setCameraEntity(observer);
      mc.options.hideGui = true;
      mc.options.fov().set(55);
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (initialized && observer != null) {
         if (event.phase == Phase.START) {
            setCamera(mc);
         } else {
            if (missile != null && !flightDone && flightAge >= 40 && flightAge <= 45) {
               capture(mc, "close-opaque-hull");
            }

            if (flashAge == 4) {
               capture(mc, "flash-baseline");
            }

            if (flashAge == 9) {
               capture(mc, "flash-peak");
            }

            if (flashAge == 14) {
               capture(mc, "flash-afterglow");
            }

            long age = mc.level.getGameTime() - fireStarted;
            if (flightDone && age >= 1600L && age <= 1620L) {
               capture(mc, "residual-black-smoke-80s");
            }

            if (flightDone && age >= 2370L && age <= 2390L) {
               capture(mc, "residual-fire-119s");
            }
         }
      }
   }

   private static void capture(Minecraft mc, String name) {
      if (captures.add(name)) {
         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(dir);
               shot.writeToFile(dir.resolve(name + ".png"));
               LOG.info("ROUTE_EFFECTS_CAPTURE {}", name);
            } catch (Throwable var6) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var7) {
            throw new IllegalStateException(var7);
         }
      }
   }

   private RouteEffectsReview() {
   }
}
