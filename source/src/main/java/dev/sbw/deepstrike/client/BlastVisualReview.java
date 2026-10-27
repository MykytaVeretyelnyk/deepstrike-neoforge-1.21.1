package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.LoggerFactory;

final class BlastVisualReview {
   private static final boolean COLUMNS = Boolean.getBoolean("deepstrike.smokeColumnReview");
   private static final List<BlastVisualReview.Case> cases = new ArrayList<>();
   private static GuidedMissileEntity observer;
   private static int index = -1;
   private static int age;
   private static int lastCapture = -1;
   private static long started;
   private static boolean pending;
   private static Vec3 focus;

   static void tick(Minecraft mc) {
      if (cases.isEmpty()) {
         for (MissileKind kind : MissileKind.values()) {
            cases.add(new BlastVisualReview.Case(kind, "he"));
         }

         cases.add(new BlastVisualReview.Case(MissileKind.PEACEKEEPER, "nuclear"));
         cases.add(new BlastVisualReview.Case(MissileKind.SPIKE_NLOS, "shaped_charge"));

         for (String warhead : List.of("thermobaric", "cluster", "graphite", "penetrating")) {
            cases.add(new BlastVisualReview.Case(MissileKind.FP5, warhead));
         }

         if (Boolean.getBoolean("deepstrike.reviewFar")) {
            cases.add(new BlastVisualReview.Case(MissileKind.FP5, "he"));
         }
      }

      if (COLUMNS && cases.size() > 1) {
         cases.subList(1, cases.size()).clear();
      }

      age = index < 0 ? 1500 : (COLUMNS ? age + 1 : (int)(mc.level.getGameTime() - started));
      if (pending && age >= 1) {
         pending = false;
         BlastVisualReview.Case sample = cases.get(index);
         MissileExplosionEffects.accept(
            new DeepStrikeNetwork.BlastEffect(sample.kind.ordinal(), focus.x, focus.y, focus.z, 42 + index, sample.warhead)
         );
      }

      if (COLUMNS && index >= 0 && age % 8 == 0) {
         ContrailRenderer.smokeColumn(focus.add(14.0, 0.0, 6.0), 0.55F, 123, false);
         ContrailRenderer.smokeColumn(focus.add(-12.0, 0.0, 10.0), 0.55F, 456, false);
      }

      if (age >= (COLUMNS ? 1400 : 80)) {
         if (++index >= cases.size()) {
            LoggerFactory.getLogger(BlastVisualReview.class).info("FP5_OPTIONS_FINISHED explosionCases={}", cases.size());
            mc.stop();
         } else {
            started = mc.level.getGameTime();
            age = 0;
            lastCapture = -1;
            focus = new Vec3(index * 800 + (COLUMNS ? 8 : 0), COLUMNS ? 101.5 : 1000.0, 0.0);
            observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            setCamera(mc);
            BlastVisualReview.Case sample = cases.get(index);
            pending = true;
            LoggerFactory.getLogger(BlastVisualReview.class)
               .info("BLAST_REVIEW_CASE index={} kind={} warhead={}", new Object[]{index, sample.kind, sample.warhead});
         }
      }
   }

   private static void setCamera(Minecraft mc) {
      BlastVisualReview.Case sample = cases.get(index);
      double distance = sample.warhead.equals("nuclear")
         ? 200.0
         : (
            sample.warhead.equals("shaped_charge")
               ? 18.0
               : (sample.kind != MissileKind.JAGM && sample.kind != MissileKind.SPIKE_NLOS ? (sample.kind == MissileKind.STORM_SHADOW ? 45.0 : 120.0) : 25.0)
         );
      if (farCase()) {
         distance = Double.parseDouble(System.getProperty("deepstrike.reviewDistance", "2000"));
      }

      Vec3 position = farCase()
         ? focus.add(new Vec3(0.7, 0.45, -0.7).normalize().scale(distance))
         : focus.add(distance * 0.7, distance * 0.45, -distance * 0.7);
      if (COLUMNS) {
         position = age < 100 ? focus.add(70.0, 45.0, -70.0) : focus.add(160.0, 110.0, -220.0);
      }

      observer.setPos(position);
      Vec3 d = focus.add(0.0, COLUMNS && age >= 100 ? 85.0 : 0.0, 0.0).subtract(observer.getEyePosition());
      observer.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
      observer.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
      observer.xo = position.x;
      observer.yo = position.y;
      observer.zo = position.z;
      observer.yRotO = observer.getYRot();
      observer.xRotO = observer.getXRot();
      mc.options.hideGui = true;
      mc.options.fov().set(55);
      mc.setCameraEntity(observer);
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (index >= 0 && index < cases.size()) {
         if (event.phase == Phase.START) {
            setCamera(mc);
         } else {
            for (int at : COLUMNS ? new int[]{1, 20, 60, 200, 600, 1200} : new int[]{1, 6, 20, 60}) {
               if (age >= at && lastCapture < at) {
                  lastCapture = at;

                  try {
                     NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

                     try {
                        Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
                        Files.createDirectories(dir);
                        BlastVisualReview.Case sample = cases.get(index);
                        shot.writeToFile(dir.resolve(sample.kind.id + "-" + sample.warhead + (farCase() ? "-far" : "") + "-" + at + ".png"));
                     } catch (Throwable var10) {
                        if (shot != null) {
                           try {
                              shot.close();
                           } catch (Throwable var9) {
                              var10.addSuppressed(var9);
                           }
                        }

                        throw var10;
                     }

                     if (shot != null) {
                        shot.close();
                     }
                     break;
                  } catch (Exception var11) {
                     throw new IllegalStateException(var11);
                  }
               }
            }
         }
      }
   }

   private static boolean farCase() {
      return Boolean.getBoolean("deepstrike.reviewFar") && index == cases.size() - 1;
   }

   private BlastVisualReview() {
   }

   private record Case(MissileKind kind, String warhead) {
   }
}
