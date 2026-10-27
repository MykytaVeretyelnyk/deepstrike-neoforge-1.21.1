package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AcousticVisualReview {
   private static final Logger LOG = LoggerFactory.getLogger(AcousticVisualReview.class);
   private static final long SEED = 802018L;
   private static final Vec3 SOURCE = new Vec3(0.0, 1000.0, 0.0);
   private static GuidedMissileEntity observer;
   private static MissileExplosionEffects.ActiveBlast blast;
   private static int ticks;
   private static String capture;

   static void tick(Minecraft mc) {
      if (observer == null) {
         observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
      }

      if (ticks++ == 10) {
         MissileExplosionEffects.accept(
            new DeepStrikeNetwork.BlastEffect(MissileKind.FP5.ordinal(), SOURCE.x, SOURCE.y, SOURCE.z, 802018L, "he")
         );
         blast = MissileExplosionEffects.reviewBlast(802018L);
         if (blast == null) {
            throw new IllegalStateException("Production blast was not registered");
         }

         capture = "flash";
      }

      if (blast != null) {
         if (blast.age == 145) {
            if (MissileExplosionEffects.reviewBlast(802018L) == null || blast.soundPlayed) {
               throw new IllegalStateException("Distant acoustic event lost with flash or played before front arrived");
            }

            LOG.info(
               "ACOUSTIC_PENDING_AFTER_VISUAL_EXPIRY age={} visualMaxAge={} initialDelay={}", new Object[]{blast.age, blast.maxAge, blast.soundDelayTicks}
            );
            capture = "after-flash-before-boom";
         }

         if (blast.soundArrivalAge > 0 && blast.age == blast.soundArrivalAge + 1) {
            double expected = 3000.0 / (blast.soundSpeed - 50.0) / 0.05;
            if (Math.abs(blast.soundArrivalAge - expected) > 3.0 || blast.soundArrivalAge <= blast.soundDelayTicks) {
               throw new IllegalStateException("Moving observer did not alter physical acoustic arrival: " + blast.soundArrivalAge + " expected=" + expected);
            }

            LOG.info(
               "ACOUSTIC_MOVING_LISTENER_ARRIVAL age={} frozenDelay={} speedOfSound={}",
               new Object[]{blast.soundArrivalAge, blast.soundDelayTicks, blast.soundSpeed}
            );
            capture = "boom-arrival";
         }

         if (ticks > 280) {
            if (!blast.soundPlayed || !blast.echo1Played || !blast.echo2Played || MissileExplosionEffects.reviewBlast(802018L) != null) {
               throw new IllegalStateException("Main/echo playback or acoustic cleanup did not finish");
            }

            LOG.info(
               "ACOUSTIC_REALISM_NATIVE_PASS arrival={} visualMaxAge={} echoes=true cleaned=true FP5_OPTIONS_FINISHED", blast.soundArrivalAge, blast.maxAge
            );
            mc.stop();
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (observer != null) {
         if (event.phase == Phase.START) {
            Vec3 position = SOURCE.add(3000.0 + Math.max(0, ticks - 11) * 50 * 0.05, 0.0, 0.0);
            observer.setPos(position);
            observer.xo = position.x;
            observer.yo = position.y;
            observer.zo = position.z;
            Vec3 delta = SOURCE.subtract(observer.getEyePosition());
            observer.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            observer.setXRot((float)(-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()))));
            observer.yRotO = observer.getYRot();
            observer.xRotO = observer.getXRot();
            mc.setCameraEntity(observer);
            mc.options.hideGui = true;
            mc.options.fov().set(55);
         } else if (capture != null) {
            try {
               NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

               try {
                  Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
                  Files.createDirectories(dir);
                  shot.writeToFile(dir.resolve(capture + ".png"));
                  capture = null;
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
   }
}
