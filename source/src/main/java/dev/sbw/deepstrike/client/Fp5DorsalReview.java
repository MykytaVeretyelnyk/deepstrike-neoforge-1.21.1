package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.model.MissileGeometry;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import net.neoforged.bus.api.EventPriority;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

final class Fp5DorsalReview {
   private static boolean boot;
   private static boolean passed;
   private static boolean boosterVerified;
   private static boolean cutoffVerified;
   private static boolean stopping;
   private static volatile GuidedMissileEntity server;
   private static volatile Throwable failure;
   private static GuidedMissileEntity observer;
   private static final Set<String> captures = new HashSet<>();

   private static Object field(Object o, String name) throws ReflectiveOperationException {
      Field f = o.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(o);
   }

   static void tick(Minecraft mc) {
      if (!stopping) {
         if (failure != null) {
            throw new IllegalStateException("FP5 dorsal review", failure);
         } else if (!boot) {
            boot = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) -> {
               if (observer != null) {
                  event.setYaw(observer.getYRot());
                  event.setPitch(observer.getXRot());
                  event.setRoll(0.0F);
                  ClientCamera.moveCameraToNose(event.getCamera(), observer.getEyePosition(), observer.getYRot(), observer.getXRot());
               }
            });
            mc.getSingleplayerServer().execute(() -> {
               try {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.FP5);
                  missile.setPos(0.0, 155.0, 0.0);
                  missile.setWarhead("none");
                  missile.setMissionId(UUID.randomUUID());
                  missile.setMission(List.of(new RoutePoint(2000.0, 190.0, 0.0, 600.0)), false);
                  missile.setLaunchVector(new Vec3(1.0, 0.2, 0.0).normalize(), 12);
                  missile.setDeltaMovement(0.08, 0.016, 0.0);
                  missile.setYRot(-90.0F);
                  missile.setXRot(-11.0F);
                  level.addFreshEntity(missile);
                  server = missile;
               } catch (Throwable var3) {
                  failure = var3;
               }
            });
         } else {
            if (server != null && server.tickCount > 245) {
               if (!passed || !boosterVerified || !cutoffVerified || captures.size() < 5) {
                  throw new IllegalStateException("Missing nozzle verification or photos: " + captures);
               }

               stopping = true;
               LoggerFactory.getLogger(Fp5DorsalReview.class).info("FP5_DORSAL_REVIEW_PASS captures={} FP5_OPTIONS_FINISHED", captures);
               mc.stop();
            }
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (server != null && !stopping) {
         GuidedMissileEntity missile = RemoteFlights.get(server.getId());
         if (missile == null && mc.level.getEntity(server.getId()) instanceof GuidedMissileEntity tracked) {
            missile = tracked;
         }

         if (missile != null) {
            int age = server.tickCount;
            String stage = age < 80 ? "boost" : (age < 130 ? "cutoff" : (age < 165 ? "separated" : (age < 205 ? "upper-nozzle" : "lower-panel")));
            if (event.phase == Phase.START) {
               if (observer == null) {
                  observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
               }

               Vec3 local = stage.equals("lower-panel") ? new Vec3(3.0625, -0.021875, 0.0) : new Vec3(3.063125, 0.75625, 0.0);
               Vec3 target = missile.position()
                  .add(new Vec3(MissileGeometry.flight(MissileKind.FP5, missile.getYRot(), missile.getXRot()).transformPosition(local.toVector3f())));
               Vec3 offset = new Vec3(
                  MissileGeometry.flight(MissileKind.FP5, missile.getYRot(), missile.getXRot())
                     .transformDirection(
                        new Vector3f(
                           stage.equals("boost") ? 10.0F : (stage.equals("separated") ? 6.0F : 3.2F),
                           stage.equals("lower-panel") ? -0.4F : 0.6F,
                           stage.equals("boost") ? 7.0F : 0.8F
                        )
                     )
               );
               Vec3 position = target.add(offset);
               observer.setPos(position.subtract(0.0, observer.getEyeHeight(), 0.0));
               Vec3 delta = target.subtract(position);
               observer.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
               observer.setXRot((float)(-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()))));
               observer.xo = observer.getX();
               observer.yo = observer.getY();
               observer.zo = observer.getZ();
               observer.yRotO = observer.getYRot();
               observer.xRotO = observer.getXRot();
               mc.setCameraEntity(observer);
               mc.options.setCameraType(CameraType.FIRST_PERSON);
               mc.options.hideGui = true;
               mc.options.fov().set(50);
            } else {
               if (!boosterVerified && age > 35 && age < 75 && missile.fp5BoosterBurning()) {
                  try {
                     Map<?, ?> trails = (Map<?, ?>)fieldStatic(ContrailRenderer.class, "TRAILS");

                     for (Object trail : trails.values()) {
                        if (field(trail, "source") == missile) {
                           Vec3 emission = (Vec3)field(trail, "lightPosition");
                           Vec3 local = new Vec3(
                              MissileGeometry.flight(MissileKind.FP5, missile.getYRot(), missile.getXRot())
                                 .invert()
                                 .transformPosition(emission.subtract(missile.position()).toVector3f())
                           );
                           if (local.distanceTo(new Vec3(0.75, -0.478125, 0.0)) > 0.12 || !(Boolean)field(trail, "lightActive")) {
                              throw new IllegalStateException("Booster flame detached from lower nozzle: " + local);
                           }

                           boosterVerified = true;
                           LoggerFactory.getLogger(Fp5DorsalReview.class).info("FP5_BOOSTER_NOZZLE_PASS local={}", local);
                        }
                     }
                  } catch (ReflectiveOperationException var16) {
                     throw new IllegalStateException(var16);
                  }
               }

               if (!cutoffVerified && age > 105 && age < 125) {
                  ArrayList<FlightLighting.Glow> glows = new ArrayList<>();
                  FlightLighting.collectEngineGlows(glows, 1.0F, 0.0F);
                  int missileId = missile.getId();
                  if (glows.stream().anyMatch(g -> g.id() == missileId && g.halo() > 0.01F)) {
                     throw new IllegalStateException("FP5 flame persisted after booster cutoff");
                  }

                  cutoffVerified = true;
                  LoggerFactory.getLogger(Fp5DorsalReview.class).info("FP5_BOOSTER_CUTOFF_PASS flame=false");
               }

               if (!passed && age > 145 && !missile.fp5BoosterAttached()) {
                  try {
                     Map<?, ?> trails = (Map<?, ?>)fieldStatic(ContrailRenderer.class, "TRAILS");

                     for (Object trailx : trails.values()) {
                        if (field(trailx, "source") == missile) {
                           Vec3 emission = (Vec3)field(trailx, "lightPosition");
                           Vec3 local = new Vec3(
                              MissileGeometry.flight(MissileKind.FP5, missile.getYRot(), missile.getXRot())
                                 .invert()
                                 .transformPosition(emission.subtract(missile.position()).toVector3f())
                           );
                           if (Math.abs(local.x - 3.063125) > 0.35 || Math.abs(local.y - 0.75625) > 0.04 || Math.abs(local.z) > 0.04) {
                              throw new IllegalStateException("Wrong nozzle " + local);
                           }

                           if ((Boolean)field(trailx, "lightActive") || missile.fp5BoosterBurning()) {
                              throw new IllegalStateException("Cruise flame/light active");
                           }

                           PoseStack emptyPose = new PoseStack();
                           MultiBufferSource noFire = ignored -> {
                              throw new IllegalStateException("Cruise requested a flame buffer");
                           };
                           ClientSetup.renderExhaust(missile, 0.0F, emptyPose, noFire);
                           ClientSetup.renderExhaust(missile, 0.0F, emptyPose, noFire, true);
                           ClientSetup.renderDistantExhaust(missile, 0.0F, emptyPose, noFire);
                           if (!passed) {
                              LoggerFactory.getLogger(Fp5DorsalReview.class)
                                 .info("FP5_DORSAL_EMITTER_PASS local={} boosterAttached=false flame=false light=false", local);
                           }

                           passed = true;
                        }
                     }
                  } catch (ReflectiveOperationException var15) {
                     throw new IllegalStateException(var15);
                  }
               }

               if (age < 20
                  || age < 95 && stage.equals("cutoff")
                  || age < 145 && stage.equals("separated")
                  || age < 185 && stage.equals("upper-nozzle")
                  || age < 225 && stage.equals("lower-panel")
                  || !captures.add(stage)) {
                  return;
               }

               try {
                  NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

                  try {
                     Path path = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
                     Files.createDirectories(path);
                     shot.writeToFile(path.resolve(stage + ".png"));
                  } catch (Throwable var13) {
                     if (shot != null) {
                        try {
                           shot.close();
                        } catch (Throwable var12) {
                           var13.addSuppressed(var12);
                        }
                     }

                     throw var13;
                  }

                  if (shot != null) {
                     shot.close();
                  }
               } catch (Exception var14) {
                  failure = var14;
               }
            }
         }
      }
   }

   private static Object fieldStatic(Class<?> c, String name) throws ReflectiveOperationException {
      Field f = c.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(null);
   }

   private Fp5DorsalReview() {
   }
}
