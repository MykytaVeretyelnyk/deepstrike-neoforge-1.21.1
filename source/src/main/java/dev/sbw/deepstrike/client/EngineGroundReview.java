package dev.sbw.deepstrike.client;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import net.neoforged.bus.api.EventPriority;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EngineGroundReview {
   private static final Logger LOG = LoggerFactory.getLogger(EngineGroundReview.class);
   private static volatile boolean ready;
   private static volatile Throwable failure;
   private static boolean started;
   private static boolean active;
   private static int tick;
   private static float power;
   private static String pending;
   private static Su24mEntity serverPlane;
   private static Vec3 camera = new Vec3(200.0, 106.0, 175.0);
   private static Vec3 aim = new Vec3(200.0, 103.0, 191.0);
   private static final List<Map<String, Object>> samples = new ArrayList<>();
   private static final Set<String> profiles = new HashSet<>();
   private static final Vec3 GALLERY = new Vec3(200.0, 1500.0, 200.0);

   static boolean tick(Minecraft mc, Su24mEntity plane) {
      if (!started) {
         started = true;
         active = true;
         serverPlane = plane;
         if (!mc.gameDirectory.toPath().toString().contains("/build/")) {
            throw new IllegalStateException("Disposable world required");
         } else {
            mc.setScreen(null);
            ClientCamera.restore();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            mc.options.fov().set(55);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) -> {
               if (active) {
                  Vec3 d = aim.subtract(camera);
                  float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
                  float pitch = (float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
                  event.setYaw(yaw);
                  event.setPitch(pitch);
                  event.setRoll(0.0F);
                  ClientCamera.moveCameraToNose(event.getCamera(), camera, yaw, pitch);
               }
            });
            mc.getSingleplayerServer().execute(() -> {
               try {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  p.stopRiding();
                  p.teleportTo(200.0, 109.0, 175.0);
                  level.setDayTime(18000L);

                  for (int x = 182; x <= 218; x++) {
                     for (int z = 173; z <= 222; z++) {
                        level.setBlock(new BlockPos(x, 100, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                     }
                  }

                  lock(plane, 0.0F, false);
                  ready = true;
               } catch (Throwable var6x) {
                  failure = var6x;
               }
            });
            return false;
         }
      } else if (failure != null) {
         throw new IllegalStateException("Engine review server", failure);
      } else if (!ready) {
         return false;
      } else {
         tick++;
         power = tick < 35 ? 0.0F : (tick < 95 ? 0.35F : (tick < 155 ? 0.65F : 1.0F));
         boolean running = tick >= 35 && tick < 235;
         mc.getSingleplayerServer().execute(() -> lock(serverPlane, power, running));
         if (mc.level.getEntity(plane.getId()) instanceof Su24mEntity live) {
            lock(live, power, running);
         }

         if (tick == 25) {
            pending = "su24-ground-off.png";
         }

         if (tick == 80) {
            pending = "su24-ground-35.png";
         }

         if (tick == 140) {
            pending = "su24-ground-65.png";
         }

         if (tick == 200) {
            pending = "su24-ground-100.png";
         }

         if (tick == 205) {
            camera = new Vec3(200.0, 117.0, 186.0);
            aim = new Vec3(200.0, 101.0, 190.0);
         }

         if (tick == 225) {
            pending = "su24-ground-100-top.png";
         }

         if (tick >= 235) {
            int index = (tick - 235) / 30;
            int age = (tick - 235) % 30;
            if (index >= 11) {
               if (profiles.size() != 11) {
                  throw new IllegalStateException("Missing motor profiles " + profiles);
               }

               LOG.info("ENGINE_GROUND_AND_MOTORS_PASS stages=off_35_65_100 symmetricProjection=true profiles={}", profiles);
               active = false;
               return true;
            }

            boolean guided = index < 5;
            MissileKind kind = guided ? MissileKind.values()[index] : null;
            float radius = guided ? ExhaustStyles.motorRadius(kind, 12.0F) : ExhaustStyles.samMotorRadius(index - 5, 12.0F);
            float length = guided ? ExhaustStyles.motorLength(kind, 12.0F) : ExhaustStyles.samMotorLength(index - 5, 12.0F);
            float before = guided ? ExhaustStyles.motorLength(kind, 0.0F) : ExhaustStyles.samMotorLength(index - 5, 0.0F);
            float full = guided ? ExhaustStyles.length(kind, 2) : ExhaustStyles.samLength(index - 5);
            boolean gradual = guided && kind == MissileKind.PEACEKEEPER;
            if (!(before > 0.0F) || !(before < length) || (gradual ? !(length < 0.6F * full) : !(length > 0.98F * full)) || !(radius > 0.0F)) {
               throw new IllegalStateException("Invalid motor spool/scale " + index);
            }

            String var10000;
            if (guided) {
               var10000 = kind.id;
            } else {
               switch (index - 5) {
                  case 0:
                     var10000 = "pac2";
                     break;
                  case 1:
                     var10000 = "pac3";
                     break;
                  case 2:
                     var10000 = "pac3_mse";
                     break;
                  case 3:
                     var10000 = "tamir";
                     break;
                  case 4:
                     var10000 = "stinger";
                     break;
                  default:
                     var10000 = "igla";
               }
            }

            String name = var10000;
            float tail = guided
               ? (float)MissileGeometry.tailDistance(kind)
               : (index < 9 ? ModelGeometry.get(InterceptorGeometry.model(index - 5)).tailX * InterceptorGeometry.axialScale(index - 5) : 0.6F);
            Vec3 focus = GALLERY.add(tail + length * 0.22, 0.0, 0.0);
            double distance = guided && kind == MissileKind.PEACEKEEPER
               ? 32.0
               : (guided && kind == MissileKind.FP5 ? 14.0 : (guided && kind == MissileKind.STORM_SHADOW ? 11.0 : (index < 9 ? 13.0 : 7.0)));
            camera = focus.add(distance * 0.35, distance * 0.17, -distance);
            aim = focus;
            if (age == 18) {
               profiles.add(name);
               pending = "motor-" + name + ".png";
               LOG.info("MOTOR_PROFILE_RENDER name={} radius={} length={} ignitionLength={}", new Object[]{name, radius, length, before});
            }
         }

         return false;
      }
   }

   private static void lock(Su24mEntity plane, float power, boolean running) {
      plane.setPos(200.0, 101.02, 200.0);
      plane.xo = plane.xOld = 200.0;
      plane.yo = plane.yOld = 101.02;
      plane.zo = plane.zOld = 200.0;
      plane.setYRot(0.0F);
      plane.yRotO = 0.0F;
      plane.setXRot(0.0F);
      plane.xRotO = 0.0F;
      plane.setRoll(0.0F);
      plane.setPrevRoll(0.0F);
      plane.setFakePitch(0.0F);
      plane.setFakePitchO(0.0F);
      plane.setFakeRoll(0.0F);
      plane.setFakeRollO(0.0F);
      plane.setDeltaMovement(Vec3.ZERO);
      plane.setOnGround(true);
      plane.setGearUp(false);
      plane.setEngineStart(running);
      plane.setEngineStartOver(running);
      plane.setPower(power);
      plane.setEnergy(plane.getMaxEnergy());
   }

   static void scene(RenderLevelStageEvent event) {
      if (active && ready && tick >= 235) {
         if (event.getStage() == Stage.AFTER_LEVEL) {
            int profile = (tick - 235) / 30;
            if (profile < 11) {
               boolean guided = profile < 5;
               MissileKind kind = guided ? MissileKind.values()[profile] : null;
               float radius = guided ? ExhaustStyles.motorRadius(kind, 12.0F) : ExhaustStyles.samMotorRadius(profile - 5, 12.0F);
               float length = guided ? ExhaustStyles.motorLength(kind, 12.0F) : ExhaustStyles.samMotorLength(profile - 5, 12.0F);
               float tail = guided
                  ? (float)MissileGeometry.tailDistance(kind)
                  : (profile < 9 ? ModelGeometry.get(InterceptorGeometry.model(profile - 5)).tailX * InterceptorGeometry.axialScale(profile - 5) : 0.6F);
               Vec3 nozzle = GALLERY.add(tail, 0.0, 0.0);
               FlightLighting.Glow source = new FlightLighting.Glow(
                  -78000 - profile,
                  nozzle,
                  1.0F,
                  0.88F * ExhaustStyles.motorBrightness(12.0F),
                  6.0F * radius,
                  false,
                  new Vec3(-1.0, 0.0, 0.0),
                  nozzle.add(length, 0.0, 0.0),
                  null,
                  0.0F
               );
               RenderLevelStageEvent world = LateWorldEffects.worldView(event);
               EngineLightRenderer.renderAircraftMotor(
                  world.getPoseStack(),
                  Minecraft.getInstance().renderBuffers().bufferSource(),
                  source,
                  event.getCamera().getPosition(),
                  Minecraft.getInstance().gameRenderer.getDepthFar() * 0.72F
               );
            }
         } else if (event.getStage() == Stage.AFTER_ENTITIES) {
            int index = (tick - 235) / 30;
            if (index < 11) {
               Minecraft mc = Minecraft.getInstance();
               PoseStack pose = event.getPoseStack();
               BufferSource buffers = mc.renderBuffers().bufferSource();
               buffers.endBatch();
               Vec3 relative = GALLERY.subtract(event.getCamera().getPosition());
               pose.pushPose();
               pose.translate(relative.x, relative.y, relative.z);
               if (index < 5) {
                  MissileKind kind = MissileKind.values()[index];
                  pose.pushPose();
                  pose.mulPose(MissileGeometry.exportRotation(kind));
                  ModelRenderer.render(
                     ModelAssets.missile(kind), ModelAssets.texture(kind, false), pose, buffers, 15728880, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, b -> true
                  );
                  pose.popPose();
                  buffers.endBatch();
                  pose.pushPose();
                  pose.translate(MissileGeometry.tailDistance(kind), 0.0, 0.0);
                  Fp5BoosterFire.renderMotor(
                     pose,
                     buffers,
                     12.0F,
                     ExhaustStyles.motorRadius(kind, 12.0F),
                     ExhaustStyles.motorLength(kind, 12.0F),
                     ExhaustStyles.motorBrightness(12.0F),
                     0.0,
                     false
                  );
                  pose.popPose();
               } else {
                  int warhead = index - 5;
                  float tail = 0.6F;
                  if (warhead < 4) {
                     String model = InterceptorGeometry.model(warhead);
                     pose.pushPose();
                     pose.scale(InterceptorGeometry.axialScale(warhead), InterceptorGeometry.radialScale(warhead), InterceptorGeometry.radialScale(warhead));
                     ModelRenderer.render(model, model, pose, buffers, 15728880, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, b -> true);
                     pose.popPose();
                     tail = ModelGeometry.get(model).tailX * InterceptorGeometry.axialScale(warhead);
                  }

                  buffers.endBatch();
                  ExhaustPlume.renderSam(pose, buffers, 12.0F, tail, warhead, 12.0F);
               }

               buffers.endBatch();
               pose.popPose();
            }
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (active && event.phase == Phase.END && pending != null) {
         String name = pending;
         pending = null;

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path folder = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(folder);
               shot.writeToFile(folder.resolve(name));
               if (tick < 235) {
                  Map<String, Object> sample = new LinkedHashMap<>();
                  sample.put("image", name);
                  sample.put("power", power);
                  Su24mEntity live = (Su24mEntity)mc.level.getEntity(serverPlane.getId());
                  ArrayList<FlightLighting.Glow> lights = new ArrayList<>();
                  FlightLighting.collectEngineGlows(lights, 1.0F, mc.getTimer().getGameTimeDeltaPartialTick(false));
                  lights.stream().filter(g -> g.id() == -1099511627776L - live.getId()).findFirst().ifPresent(g -> {
                     sample.put("lightRadius", g.radius());
                     sample.put("strength", g.strength());
                  });
                  ArrayList<List<Float>> projected = new ArrayList<>();
                  Matrix4f rotation = Su24mExhaust.modelRotation(live, mc.getTimer().getGameTimeDeltaPartialTick(false));
                  Vec3 origin = Su24mExhaust.renderOrigin(live, mc.getTimer().getGameTimeDeltaPartialTick(false));

                  for (float side : new float[]{-0.7525F, 0.7525F}) {
                     Vector3f point = rotation.transformPosition(new Vector3f(side, 2.205F, 8.83F + Fp5BoosterFire.aircraftLength(power) * 0.32F));
                     Vec3 delta = origin.add(point.x, point.y, point.z).subtract(mc.gameRenderer.getMainCamera().getPosition());
                     Vector3f view = new Vector3f((float)delta.x, (float)delta.y, (float)delta.z)
                        .rotate(mc.gameRenderer.getMainCamera().rotation().conjugate(new Quaternionf()));
                     Vector4f clip = mc.gameRenderer.getProjectionMatrix(((Integer)mc.options.fov().get()).intValue()).transform(new Vector4f(view, 1.0F));
                     projected.add(List.of((clip.x / clip.w * 0.5F + 0.5F) * shot.getWidth(), (0.5F - clip.y / clip.w * 0.5F) * shot.getHeight()));
                  }

                  sample.put("jetPixels", projected);
                  samples.add(sample);
                  Files.writeString(folder.resolve("engine-ground-samples.json"), new GsonBuilder().setPrettyPrinting().create().toJson(samples));
               }

               LOG.info("ENGINE_EFFECTS_CAPTURE {}", name);
            } catch (Throwable var20) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var19) {
                     var20.addSuppressed(var19);
                  }
               }

               throw var20;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var21) {
            throw new IllegalStateException(var21);
         }
      }
   }

   private EngineGroundReview() {
   }
}
