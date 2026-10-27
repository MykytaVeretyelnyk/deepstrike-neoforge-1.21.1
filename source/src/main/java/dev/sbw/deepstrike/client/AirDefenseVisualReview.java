package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.MissileModelContact;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.validation.AirDefenseReview;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AirDefenseVisualReview {
   private static final Logger LOG = LoggerFactory.getLogger(AirDefenseVisualReview.class);
   private static boolean started;
   private static GuidedMissileEntity observer;
   private static int lastCapture = -1;
   private static int lastContactCapture;
   private static final List<CompletableFuture<Void>> contactWrites = new ArrayList<>();
   private static boolean allWakesVerified;
   private static int nativeScene = -1;
   private static final Set<Integer> nativeContacts = new HashSet<>();

   static void nativeContactFrame(int kind, MissileProjectile body, Vec3 center, Vec3 direction) {
      if (Boolean.getBoolean("deepstrike.reviewNativeContact") && !nativeContacts.contains(kind)) {
         Vec3 nose = ManpadGeometry.nose(body, center, direction);

         for (GuidedMissileEntity target : RemoteFlights.all()) {
            if (target.isIntercepted() && target.position().distanceToSqr(nose) < 1024.0) {
               if (MissileModelContact.fraction(target, nose.subtract(direction.scale(1.0E-5)), nose.add(direction.scale(1.0E-5)), Vec3.ZERO)
                  >= 0.0) {
                  nativeContacts.add(kind);
                  LOG.info(
                     "AIR_DEFENSE_RENDERED_NATIVE_CONTACT_VERIFIED kind={} nose={} target={} yaw={} pitch={}",
                     new Object[]{kind, nose, target.position(), target.getYRot(), target.getXRot()}
                  );
               } else {
                  LOG.info(
                     "AIR_DEFENSE_RENDERED_NATIVE_CONTACT_PENDING kind={} nose={} target={} yaw={} pitch={}",
                     new Object[]{kind, nose, target.position(), target.getYRot(), target.getXRot()}
                  );
               }
            }
         }
      }
   }

   static void tick(Minecraft mc) {
      if (!started) {
         started = true;
         ContrailRenderer.reviewSpiralGeometry();
         mc.getSingleplayerServer().execute(() -> AirDefenseReview.start(mc.getSingleplayerServer().overworld()));
      }

      if (AirDefenseReview.failure != null) {
         LOG.error("AIR_DEFENSE_VISUAL_FAILED {}", AirDefenseReview.failure);
         mc.stop();
      } else {
         if (Boolean.getBoolean("deepstrike.reviewNativeContact") && AirDefenseReview.phase == 5 && nativeScene != AirDefenseReview.nativeScene) {
            nativeScene = AirDefenseReview.nativeScene;
            ContrailRenderer.reviewClearPreviousScene();
         }

         if (AirDefenseReview.phase == 4 && AirDefenseReview.age >= 40 && !allWakesVerified) {
            for (int id : AirDefenseReview.additionalIds) {
               if (!ContrailRenderer.reviewHasWake(id)) {
                  throw new IllegalStateException("Missing air-defense smoke wake: " + id);
               }
            }

            if (Boolean.getBoolean("deepstrike.reviewFar")) {
               for (int idx : AirDefenseReview.additionalIds) {
                  if (mc.level.getEntity(idx) != null) {
                     throw new IllegalStateException("Far effect check still has tracked entity: " + idx);
                  }
               }

               LOG.info("AIR_DEFENSE_UNTRACKED_EFFECTS_VERIFIED distance={}", System.getProperty("deepstrike.reviewDistance"));
            }

            allWakesVerified = true;
            LOG.info("AIR_DEFENSE_ALL_WAKES_VERIFIED types=PAC2,PAC3,FIM92,IGLA");
         }

         if (AirDefenseReview.finished) {
            if (Boolean.getBoolean("deepstrike.reviewNativeContact") && !nativeContacts.containsAll(Set.of(4, 5))) {
               throw new IllegalStateException("Missing rendered native contact: " + nativeContacts);
            }

            contactWrites.forEach(CompletableFuture::join);
            LOG.info("FP5_OPTIONS_FINISHED airDefense=true");
            mc.stop();
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      int phase = AirDefenseReview.phase;
      int age = AirDefenseReview.age;
      if (phase >= 0) {
         if (event.phase == Phase.START) {
            if (observer == null) {
               observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            }

            Vec3 target = AirDefenseReview.focus;
            Vec3 position = target.add(phase >= 3 ? new Vec3(25.0, 7.0, -35.0) : new Vec3(13.0, 5.0, -19.0));
            if (phase == 4 && Boolean.getBoolean("deepstrike.reviewFar")) {
               position = target.add(0.0, 15.0, Double.parseDouble(System.getProperty("deepstrike.reviewDistance", "8000")));
            }

            observer.setPos(position);
            Vec3 d = target.subtract(observer.getEyePosition());
            observer.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            observer.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
            observer.xo = position.x;
            observer.yo = position.y;
            observer.zo = position.z;
            observer.yRotO = observer.getYRot();
            observer.xRotO = observer.getXRot();
            mc.options.fov().set(55);
            mc.options.hideGui = true;
            mc.setCameraEntity(observer);
         } else {
            if ((phase == 6 || phase == 5 && Boolean.getBoolean("deepstrike.reviewNativeContact")) && AirDefenseReview.contactCapture > lastContactCapture) {
               lastContactCapture = AirDefenseReview.contactCapture;
               NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());
               Path file = mc.gameDirectory
                  .toPath()
                  .resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"))
                  .resolve((phase == 5 ? "native-contact-" : "pac-contact-") + AirDefenseReview.contactWarhead + "-" + lastContactCapture + ".png");
               contactWrites.add(CompletableFuture.runAsync(() -> {
                  try {
                     NativeImage ex = shot;

                     try {
                        Files.createDirectories(file.getParent());
                        shot.writeToFile(file);
                     } catch (Throwable var6x) {
                        if (shot != null) {
                           try {
                              ex.close();
                           } catch (Throwable var5x) {
                              var6x.addSuppressed(var5x);
                           }
                        }

                        throw var6x;
                     }

                     if (shot != null) {
                        shot.close();
                     }
                  } catch (Exception var7) {
                     throw new IllegalStateException(var7);
                  }
               }));
            }

            int[] captures = phase == 2
               ? new int[]{20, 45, 70, 100, 150, 220, 320}
               : (phase == 3 ? new int[]{1, 6, 20, 60} : (phase == 4 ? new int[]{20, 40, 70, 90} : (phase == 7 ? new int[]{6, 20, 60} : new int[0])));

            for (int at : captures) {
               int key = phase * 1000 + at + (phase == 7 ? AirDefenseReview.nativeExplosionType * 100 : 0);
               if (age >= at && lastCapture < key) {
                  lastCapture = key;

                  try {
                     NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

                     try {
                        Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
                        Files.createDirectories(dir);
                        shot.writeToFile(
                           dir.resolve(
                              (
                                    phase == 2
                                       ? "interceptor-trail-"
                                       : (
                                          phase == 3
                                             ? "airburst-"
                                             : (phase == 7 ? "native-ground-" + AirDefenseReview.nativeExplosionType + "-" : "pvo-all-types-")
                                       )
                                 )
                                 + at
                                 + ".png"
                           )
                        );
                        LOG.info("AIR_DEFENSE_CAPTURE phase={} age={} fps={}", new Object[]{phase, age, mc.getFps()});
                     } catch (Throwable var14) {
                        if (shot != null) {
                           try {
                              shot.close();
                           } catch (Throwable var13) {
                              var14.addSuppressed(var13);
                           }
                        }

                        throw var14;
                     }

                     if (shot != null) {
                        shot.close();
                     }
                     break;
                  } catch (Exception var15) {
                     throw new IllegalStateException(var15);
                  }
               }
            }
         }
      }
   }

   private AirDefenseVisualReview() {
   }
}
