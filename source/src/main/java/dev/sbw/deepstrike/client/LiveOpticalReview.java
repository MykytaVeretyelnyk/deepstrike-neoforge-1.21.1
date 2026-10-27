package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.init.ModEntities;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LiveOpticalReview {
   private static final Logger LOG = LoggerFactory.getLogger(LiveOpticalReview.class);
   private static volatile int domeId = -1;
   private static volatile int truckId = -1;
   private static volatile int missileId = -1;
   private static volatile UUID missileUuid;
   private static volatile boolean prepared;
   private static boolean started;
   private static boolean capture;
   private static boolean noseViewPrimed;
   private static int age;
   private static int index = -1;
   private static int mountWait;
   private static int failures;
   private static long lastWorldTime = Long.MIN_VALUE;
   private static Screen screen;
   private static final int[] RANGES = new int[]{3000, 6500, 6500, 3000, 6500};

   static void tick(Minecraft mc) {
      if (!started) {
         started = true;
         mc.options.guiScale().set(2);
         mc.resizeDisplay();
         mc.getSingleplayerServer().execute(() -> {
            ServerLevel level = mc.getSingleplayerServer().overworld();
            IronDomeEntity domex = (IronDomeEntity)((EntityType)BFEntities.IRON_DOME.get()).create(level);
            domex.setPos(0.0, 101.0, 0.0);
            domex.setNoGravity(true);
            domex.setAutoDefense(false);
            level.addFreshEntity(domex);
            domeId = domex.getId();
            PatriotTruckEntity truck = (PatriotTruckEntity)((EntityType)BFEntities.PATRIOT_TRUCK.get()).create(level);
            truck.setPos(12.0, 101.0, 0.0);
            truck.setNoGravity(true);
            level.addFreshEntity(truck);
            truckId = truck.getId();
            ItemStack terminal = new ItemStack((ItemLike)BFItems.AIR_DEFENSE_TERMINAL.get());
            NbtCompat.update(terminal, t -> t.putUUID("TargetUUID", domex.getUUID()));
            NbtCompat.update(terminal, t -> t.putString("TargetName", "Iron Dome"));
            truck.setItem(0, terminal);
            truck.refreshDefenseInventory();
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            player.setGameMode(GameType.CREATIVE);
            if (!player.startRiding(truck, true)) {
               throw new IllegalStateException("Could not mount real Patriot");
            } else {
               prepared = true;
            }
         });
      } else if (prepared) {
         if (index < 2 && !(mc.player.getVehicle() instanceof PatriotTruckEntity)) {
            if (++mountWait > 1200) {
               throw new IllegalStateException("Client Patriot mount did not arrive");
            } else {
               if (mc.level.getEntity(truckId) != null && mountWait % 20 == 0) {
                  mc.getSingleplayerServer().execute(() -> {
                     ServerLevel level = mc.getSingleplayerServer().overworld();
                     Entity truck = level.getEntity(truckId);
                     ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                     if (truck != null && player != null) {
                        player.startRiding(truck, true);
                        player.connection.send(new ClientboundSetPassengersPacket(truck));
                     }
                  });
               }
            }
         } else if (age == 0) {
            if (++index >= RANGES.length) {
               mc.setScreen(null);
               LOG.info("LIVE_OPTICAL_FINISHED failures={} {}", failures, failures == 0 ? "FP5_OPTIONS_FINISHED" : "FAILED");
               mc.stop();
            } else {
               prepared = false;
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        if (index == 2) {
                           mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).stopRiding();
                        }

                        if (missileUuid != null) {
                           Entity old = level.getEntity(missileUuid);
                           if (old != null) {
                              old.discard();
                           }
                        }

                        Vec3 start = new Vec3(RANGES[index], 110.0, 0.0);
                        level.getChunkAt(BlockPos.containing(start));
                        Entity missile;
                        if (index < 3) {
                           GuidedMissileEntity guided = new GuidedMissileEntity(level, MissileKind.JAGM);
                           guided.setPos(start);
                           guided.setMissionId(UUID.randomUUID());
                           guided.setWarhead("none");
                           guided.setOwnerId(mc.player.getUUID());
                           guided.setMission(List.of(new RoutePoint(start.x, 110.0, 12000.0, 450.0)), false);
                           guided.setLaunchVector(new Vec3(0.0, 0.0, 1.0), 12);
                           guided.setDeltaMovement(0.0, 0.0, 0.08);
                           missile = guided;
                        } else {
                           MissileProjectile nativeMissile = (MissileProjectile)(index == 3
                              ? new Fim92MissileEntity((EntityType)ModEntities.FIM_92_MISSILE.get(), level)
                              : new IglaMissileEntity((EntityType)ModEntities.IGLA_MISSILE.get(), level));
                           nativeMissile.setPos(start);
                           nativeMissile.setDeltaMovement(0.0, 0.0, 0.5);
                           nativeMissile.setLost(false);
                           nativeMissile.setLostTarget(false);
                           missile = nativeMissile;
                        }

                        level.addFreshEntity(missile);
                        missileId = missile.getId();
                        missileUuid = missile.getUUID();
                        prepared = true;
                        LOG.info("LIVE_OPTICAL_SERVER_LAUNCH id={} uuid={} range={}", new Object[]{missileId, missileUuid, RANGES[index]});
                     }
                  );
               age = 1;
            }
         } else {
            if (index == 0 && !noseViewPrimed && RemoteFlights.get(missileId) != null) {
               ClientCamera.view(missileId);
               noseViewPrimed = true;
               screen = null;
            }

            if (screen == null || mc.screen != screen) {
               screen = (Screen)(index < 2
                  ? new PatriotDefenseScreen(truckId)
                  : (index == 2 ? new IronDomeDefenseScreen(domeId) : new LiveOpticalReview.NativeOpticalScreen()));
               mc.setScreen(screen);

               try {
                  Field field = screen.getClass().getDeclaredField("thermalCamera");
                  field.setAccessible(true);
                  PatriotThermalCamera camera = (PatriotThermalCamera)field.get(screen);
                  camera.setMode(PatriotThermalCamera.CameraMode.NORMAL);
                  camera.adjustZoom(index == 0 ? 0.0F : 5.0F);
               } catch (ReflectiveOperationException var9) {
                  throw new IllegalStateException(var9);
               }
            }

            if (screen instanceof PatriotDefenseScreen patriot) {
               patriot.mapSelect(missileUuid);
            } else if (screen instanceof IronDomeDefenseScreen dome) {
               dome.mapSelect(missileUuid);
            } else if (screen instanceof LiveOpticalReview.NativeOpticalScreen nativeScreen) {
               Vec3 point = PatriotRemoteEffects.opticalPoint(missileUuid, missileId);
               if (point != null) {
                  nativeScreen.thermalCamera.track(new Vec3(0.0, 106.0, 0.0), point.x, point.y, point.z, missileId, missileUuid);
               }
            }

            long worldTime = mc.getSingleplayerServer().overworld().getGameTime();
            if (worldTime != lastWorldTime) {
               lastWorldTime = worldTime;
               if (age == 65 || age == 100) {
                  GuidedMissileEntity replica = RemoteFlights.get(missileId);
                  Vec3 point = replica != null ? replica.position() : PatriotRemoteEffects.opticalPoint(missileUuid, missileId);
                  if (point == null) {
                     throw new IllegalStateException("No real remote flight snapshot at " + RANGES[index]);
                  }

                  if (!PatriotThermalCamera.isTracked(missileUuid, missileId)) {
                     failures++;
                     Object snapshot = null;

                     try {
                        Field field = screen.getClass().getDeclaredField("latest");
                        field.setAccessible(true);
                        snapshot = field.get(null);
                     } catch (ReflectiveOperationException var8) {
                     }

                     LOG.error(
                        "LIVE_OPTICAL_NOT_TRACKED range={} vehicle={} screen={} snapshot={}",
                        new Object[]{RANGES[index], mc.player.getVehicle(), mc.screen, snapshot}
                     );
                  }

                  LOG.info(
                     "LIVE_OPTICAL_TRACK range={} age={} nativePresent={} replicaPos={} tracked={} radarContacts={}",
                     new Object[]{
                        RANGES[index],
                        age,
                        mc.level.getEntity(missileId) != null,
                        point,
                        PatriotThermalCamera.isTracked(missileUuid, missileId),
                        screen instanceof PatriotDefenseScreen patriot
                           ? patriot.mapTargets().size()
                           : (screen instanceof IronDomeDefenseScreen dome ? dome.mapTargets().size() : 0)
                     }
                  );
                  Vec3 expectedOrigin = new Vec3(index < 2 ? 12.0 : 0.0, 106.0, 0.0);
                  Vec3 actualOrigin = mc.gameRenderer.getMainCamera().getPosition();
                  if (actualOrigin.distanceTo(expectedOrigin) > 0.01) {
                     failures++;
                     LOG.error("LIVE_OPTICAL_WRONG_SENSOR_ORIGIN expected={} actual={}", expectedOrigin, actualOrigin);
                  }

                  LOG.info("LIVE_OPTICAL_SENSOR_ORIGIN range={} position={} expected={}", new Object[]{RANGES[index], actualOrigin, expectedOrigin});
                  capture = true;
               }

               if (age++ >= 110) {
                  mc.setScreen(null);
                  if (index == 0) {
                     ClientCamera.restore();
                  }

                  screen = null;
                  age = 0;
               }
            }
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (event.phase == Phase.END && capture) {
         capture = false;

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(dir);
               shot.writeToFile(
                  dir.resolve(
                     "live-"
                        + (index < 2 ? "patriot" : (index == 2 ? "iron-dome" : "native-optics"))
                        + "-"
                        + (index < 3 ? "jagm" : (index == 3 ? "stinger" : "igla"))
                        + "-"
                        + RANGES[index]
                        + "m-"
                        + age
                        + ".png"
                  )
               );
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

   private static final class NativeOpticalScreen extends Screen {
      private final PatriotThermalCamera thermalCamera = new PatriotThermalCamera();

      NativeOpticalScreen() {
         super(Component.literal("Native remote hull verification"));
      }

      public boolean isPauseScreen() {
         return false;
      }

      public void removed() {
         this.thermalCamera.close();
      }

      public void render(GuiGraphics graphics, int x, int y, float partial) {
         this.thermalCamera.capture(graphics);
         this.thermalCamera.render(graphics, 16, 32, this.width - 32, this.height - 48);
         graphics.drawString(
            this.font,
            "Actual server "
               + (LiveOpticalReview.index == 3 ? "FIM-92" : "Igla")
               + " / "
               + LiveOpticalReview.RANGES[LiveOpticalReview.index]
               + " m / native entity absent",
            16,
            12,
            16777215
         );
      }
   }
}
