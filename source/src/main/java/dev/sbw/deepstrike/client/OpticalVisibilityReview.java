package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OpticalVisibilityReview {
   private static final PatriotThermalCamera.CameraMode NORMAL = PatriotThermalCamera.CameraMode.NORMAL;
   private static final OpticalVisibilityReview.Case[] CASES = new OpticalVisibilityReview.Case[]{
      new OpticalVisibilityReview.Case(MissileKind.JAGM, 500, 1, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.JAGM, 3000, 1, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.JAGM, 4500, 6, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.JAGM, 4900, 12, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.JAGM, 3000, 6, PatriotThermalCamera.CameraMode.THERMAL),
      new OpticalVisibilityReview.Case(MissileKind.JAGM, 3000, 6, PatriotThermalCamera.CameraMode.NIGHT_VISION),
      new OpticalVisibilityReview.Case(MissileKind.PEACEKEEPER, 4500, 1, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.SPIKE_NLOS, 4500, 6, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.FP5, 4500, 1, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.STORM_SHADOW, 4500, 1, NORMAL),
      new OpticalVisibilityReview.Case(MissileKind.JAGM, 5050, 6, NORMAL)
   };
   private static final int ID = 1999901;
   private static int ticks;
   private static int index = -1;
   private static boolean capture;
   private static OpticalVisibilityReview.View view;
   private static final Logger LOG = LoggerFactory.getLogger(OpticalVisibilityReview.class);

   static void worldFrame(Minecraft mc) {
      if (capture) {
         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(dir);
               shot.writeToFile(dir.resolve("raw-" + index + ".png"));
               LOG.info(
                  "OPTICAL_WORLD index={} draw={} main={} error={}", new Object[]{index, GL30.glGetInteger(36006), mc.getMainRenderTarget().frameBufferId, GL11.glGetError()}
               );
            } catch (Throwable var5) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var6) {
            throw new IllegalStateException(var6);
         }
      }
   }

   static void tick(Minecraft mc) {
      if (Boolean.getBoolean("deepstrike.opticalLiveReview")) {
         LiveOpticalReview.tick(mc);
      } else {
         int next = ticks / 25;
         if (next >= CASES.length) {
            mc.setScreen(null);
            LOG.info("OPTICAL_VISIBILITY_PASS cases={} FP5_OPTIONS_FINISHED", CASES.length);
            mc.stop();
         } else {
            if (next != index) {
               if (index >= 0) {
                  RemoteFlights.accept(state(mc, CASES[index], false));
               }

               index = next;
               RemoteFlights.accept(state(mc, CASES[index], true));
               view = new OpticalVisibilityReview.View(CASES[index]);
               mc.setScreen(view);
               mc.options.hideGui = false;
               view.aim();
            }

            RemoteFlights.accept(state(mc, CASES[index], true));
            if (ticks % 25 == 20) {
               capture = true;
            }

            ticks++;
         }
      }
   }

   private static DeepStrikeNetwork.FlightState state(Minecraft mc, OpticalVisibilityReview.Case sample, boolean alive) {
      UUID uuid = UUID.nameUUIDFromBytes(sample.toString().getBytes(StandardCharsets.UTF_8));
      return new DeepStrikeNetwork.FlightState(
         1999901,
         uuid,
         sample.kind.ordinal(),
         false,
         alive,
         false,
         ticks * 0.5,
         1000.0,
         0.0,
         0.5,
         0.0,
         0.0,
         -90.0F,
         0.0F,
         null,
         "olive",
         uuid,
         "0002400",
         mc.level.getGameTime(),
         true,
         true,
         100
      );
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (Boolean.getBoolean("deepstrike.opticalLiveReview")) {
         LiveOpticalReview.render(mc, event);
      } else if (event.phase == Phase.END && capture && view != null) {
         capture = false;
         OpticalVisibilityReview.Case sample = CASES[index];
         String name = sample.kind.id + "-" + sample.distance + "m-" + sample.zoom + "x-" + sample.mode;

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(dir);
               shot.writeToFile(dir.resolve(name + ".png"));
               System.out
                  .println(
                     "OPTICAL_CAPTURE "
                        + name
                        + " replicaOnly="
                        + (mc.level.getEntity(1999901) == null)
                        + " visibility="
                        + MissileVisibility.sensorOpacity(sample.distance)
                  );
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
            throw new IllegalStateException(var9);
         }
      }
   }

   private record Case(MissileKind kind, int distance, int zoom, PatriotThermalCamera.CameraMode mode) {
   }

   private static final class View extends Screen {
      final PatriotThermalCamera camera = new PatriotThermalCamera();
      final OpticalVisibilityReview.Case sample;
      final Vec3 origin;

      View(OpticalVisibilityReview.Case sample) {
         super(Component.literal("Optical visibility review"));
         this.sample = sample;
         this.origin = new Vec3(OpticalVisibilityReview.ticks * 0.5, 1000.0, -sample.distance);
         this.camera.setMode(sample.mode);
         this.camera.adjustZoom(sample.zoom - 1);
      }

      void aim() {
         GuidedMissileEntity missile = RemoteFlights.get(1999901);
         if (missile != null) {
            this.camera.track(this.origin, missile.getX(), missile.getY(), missile.getZ(), 1999901);
         }
      }

      public void render(GuiGraphics graphics, int x, int y, float partial) {
         this.aim();
         this.camera.capture(graphics);
         graphics.fill(0, 0, this.width, this.height, -15722464);
         this.camera.render(graphics, 8, 24, this.width - 16, this.height - 32);
         graphics.drawString(
            this.font, this.sample.kind.id + " / " + this.sample.distance + " m / " + this.sample.zoom + "x / " + this.sample.mode, 8, 8, -1118482
         );
      }

      public boolean isPauseScreen() {
         return false;
      }

      public void removed() {
         this.camera.close();
      }
   }
}
