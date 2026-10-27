package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SmokeVisualReview {
   private static boolean opening;
   private static volatile boolean prepared;
   private static volatile boolean spawned;
   private static volatile int missileId = -1;
   private static int ticks;
   private static GuidedMissileEntity observer;
   private static String pending;
   private static Vec3 lastHead;
   private static final Logger LOG = LoggerFactory.getLogger(SmokeVisualReview.class);

   private static boolean enabled(Minecraft mc) {
      return Boolean.getBoolean("deepstrike.smokeVisual") && mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("smoke-visual");
   }

   static void tick(ClientTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase == Phase.END && enabled(mc)) {
         mc.options.pauseOnLostFocus = false;
         if (mc.level == null) {
            if (!opening && mc.screen instanceof TitleScreen) {
               opening = true;
               mc.createWorldOpenFlows().openWorld("New World", () -> {});
            }
         } else if (mc.player != null && mc.getSingleplayerServer() != null) {
            if (ticks == 0) {
               ticks = 1;
               mc.setScreen(null);
               mc.options.hideGui = true;
               mc.options.fov().set(55);
               mc.getSingleplayerServer().execute(() -> {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  level.setDayTime(6000L);
                  level.setWeatherParameters(6000, 0, false, false);
                  player.setGameMode(GameType.SPECTATOR);
                  player.teleportTo(0.0, 1000.0, 70.0);
                  ArrayList<Entity> remove = new ArrayList<>();

                  for (Entity e : level.getAllEntities()) {
                     if (e instanceof GuidedMissileEntity) {
                        remove.add(e);
                     }
                  }

                  remove.forEach(Entity::discard);
                  prepared = true;
               });
            } else if (prepared) {
               if (ticks < 40 || spawned) {
                  if (ticks != 40 || mc.level.getEntity(missileId) != null || RemoteFlights.get(missileId) != null) {
                     int t = ++ticks;
                     if (t == 40) {
                        mc.getSingleplayerServer().execute(() -> {
                           ServerLevel level = mc.getSingleplayerServer().overworld();
                           GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.JAGM);
                           missile.setPos(0.0, 1000.0, 0.0);
                           missile.setMissionId(UUID.randomUUID());
                           missile.setWarhead("none");
                           missile.setMission(List.of(new RoutePoint(6000.0, 1000.0, 0.0, 450.0)), false);
                           missile.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 12);
                           missile.setDeltaMovement(new Vec3(4.0, 0.0, 0.0));
                           level.addFreshEntity(missile);
                           missileId = missile.getId();
                           spawned = true;
                           LOG.info("SMOKE_REVIEW_SPAWN actualServerEntity={} pass={}", missileId, System.getProperty("deepstrike.smokePass"));
                        });
                     }

                     if (t == 620) {
                        mc.getSingleplayerServer().execute(() -> {
                           Entity entity = mc.getSingleplayerServer().overworld().getEntity(missileId);
                           if (entity != null) {
                              entity.discard();
                           }

                           LOG.info("SMOKE_REVIEW_MOTOR_REMOVED {}");
                        });
                     }

                     if (t == 880 && Boolean.getBoolean("deepstrike.smokeLighting")) {
                        mc.getSingleplayerServer().execute(() -> {
                           mc.getSingleplayerServer().overworld().setDayTime(18000L);
                           LOG.info("SMOKE_REVIEW_NIGHT_SET");
                        });
                     }

                     if (t == 120
                        || t == 200
                        || t == 300
                        || t == 420
                        || t == 560
                        || t == 680
                        || t == 820
                        || t == 1020
                        || t == 510
                        || t == 610
                        || Boolean.getBoolean("deepstrike.smokeLighting") && (t == 900 || t == 940)) {
                        pending = System.getProperty("deepstrike.smokePass", "review") + "-" + t;
                     }

                     if (t == 1060) {
                        LOG.info("SMOKE_REVIEW_FINISHED");
                        mc.stop();
                     }
                  }
               }
            }
         }
      }
   }

   static void render(RenderTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (enabled(mc) && mc.level != null && mc.player != null) {
         if (event.phase == Phase.START) {
            GuidedMissileEntity missile = RemoteFlights.get(missileId);
            if (missile == null && mc.level.getEntity(missileId) instanceof GuidedMissileEntity tracked) {
               missile = tracked;
            }

            if (missile == null && lastHead == null) {
               return;
            }

            if (observer == null) {
               observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            }

            Vec3 head = missile != null ? missile.position() : lastHead;
            lastHead = head;
            Vec3 position = ticks < 240 ? head.add(-65.0, 5.0, 65.0) : (ticks < 480 ? head.add(-95.0, 10.0, 30.0) : head.add(-160.0, 4.0, 15.0));
            Vec3 target = head.add(ticks < 480 ? -55.0 : -100.0, 0.0, 0.0);
            observer.setPos(position);
            Vec3 d = target.subtract(observer.getEyePosition());
            observer.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            observer.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
            observer.xo = position.x;
            observer.yo = position.y;
            observer.zo = position.z;
            observer.yRotO = observer.getYRot();
            observer.xRotO = observer.getXRot();
            mc.setCameraEntity(observer);
         } else if (pending != null) {
            String name = pending;
            pending = null;

            try {
               NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

               try {
                  Path dir = mc.gameDirectory.toPath().resolve("screenshots");
                  Files.createDirectories(dir);
                  shot.writeToFile(dir.resolve(name + ".png"));
                  LOG.info("SMOKE_REVIEW_CAPTURE {} fps={} day={}", new Object[]{name, mc.getFps(), mc.level.getDayTime()});
               } catch (Throwable var8) {
                  if (shot != null) {
                     try {
                        shot.close();
                     } catch (Throwable var7) {
                        var8.addSuppressed(var7);
                     }
                  }

                  throw var8;
               }

               if (shot != null) {
                  shot.close();
               }
            } catch (Exception var9) {
               throw new RuntimeException(var9);
            }
         }
      }
   }

   private SmokeVisualReview() {
   }
}
