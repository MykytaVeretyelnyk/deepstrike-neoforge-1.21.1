package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

final class Su24mExhaust {
   private static final long LIGHT_BASE = -1099511627776L;
   private static final float[] WARM = new float[]{1.0F, 0.48F, 0.16F};
   private static final Map<Su24mEntity, Su24mExhaust.Throttle> THROTTLES = new WeakHashMap<>();
   private static int anchorFrames;
   private static double maxAnchorError;

   static boolean running(Su24mEntity p) {
      return p.isAlive() && p.getEngineStart() && p.getEngineStartOver() && p.getEnergy() > 0 && p.getHealth() > 0.0F;
   }

   static float power(Su24mEntity p, float partial) {
      Su24mExhaust.Throttle state = THROTTLES.computeIfAbsent(p, Su24mExhaust.Throttle::new);
      if (state.tick != p.tickCount) {
         state.previous = state.current;
         state.current = Mth.clamp(p.getPower(), 0.0F, 1.0F);
         state.tick = p.tickCount;
      }

      return Mth.lerp(partial, state.previous, state.current);
   }

   static boolean aircraftLight(long id) {
      return id <= -1099511627776L && id > -1101659111423L;
   }

   static void collect(List<FlightLighting.Glow> lights, ClientLevel level, float ambient, float partial) {
      Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
      Su24mEntity first = null;
      Su24mEntity second = null;
      double firstDistance = 16384.0;
      double secondDistance = 16384.0;

      for (Entity entity : level.entitiesForRendering()) {
         if (entity instanceof Su24mEntity p && running(p)) {
            double distance = p.distanceToSqr(camera);
            if (distance < firstDistance) {
               second = first;
               secondDistance = firstDistance;
               first = p;
               firstDistance = distance;
            } else if (distance < secondDistance) {
               second = p;
               secondDistance = distance;
            }
         }
      }

      if (first != null) {
         add(lights, first, ambient, partial);
      }

      if (second != null) {
         add(lights, second, ambient, partial);
      }
   }

   static void renderHalos(PoseStack pose, MultiBufferSource buffers, FlightLighting.Glow glow, Vec3 camera, float far, ClientLevel level) {
      if (level != null && glow != null) {
         if (level.getEntity((int)(-1099511627776L - glow.id())) instanceof Su24mEntity plane && running(plane)) {
            float throttle = power(plane, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            float pulse = 1.0F + 0.018F * (float)Math.sin((plane.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) * 1.7);
            float night = Mth.clamp((level.getSkyDarken() - 2) / 8.0F, 0.0F, 1.0F);
            float strength = (0.13F + 0.75F * throttle * throttle) * pulse * (0.65F + 0.35F * night);
            Matrix4f rotation = modelRotation(plane, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            Vec3 origin = renderOrigin(plane, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));

            for (int i = 0; i < 2; i++) {
               if (!((i == 0 ? plane.getMainEngineHealth() : plane.getSubEngineHealth()) <= 0.0F)) {
                  Vector3f local = rotation.transformPosition(new Vector3f(i == 0 ? -0.7525F : 0.7525F, 2.205F, 8.83F));
                  Vec3 start = origin.add(local.x, local.y, local.z);
                  FlightLighting.Glow motor = new FlightLighting.Glow(
                     glow.id(),
                     start,
                     glow.radius(),
                     strength,
                     1.5F + 1.2F * throttle,
                     false,
                     glow.direction(),
                     start.subtract(glow.direction().scale(Fp5BoosterFire.aircraftLength(throttle))),
                     WARM,
                     0.0F
                  );
                  EngineLightRenderer.renderAircraftMotor(pose, buffers, motor, camera, far);
               }
            }
         }
      }
   }

   private static void add(List<FlightLighting.Glow> lights, Su24mEntity p, float ambient, float partial) {
      int engines = (p.getMainEngineHealth() > 0.0F ? 1 : 0) + (p.getSubEngineHealth() > 0.0F ? 1 : 0);
      if (engines != 0) {
         float throttle = power(p, partial);
         double side = engines == 2 ? 0.0 : (p.getMainEngineHealth() > 0.0F ? 0.7525 : -0.7525);
         Matrix4f transform = modelRotation(p, partial);
         Vector3f at = transform.transformPosition(new Vector3f((float)(-side), 2.205F, 8.83F));
         Vector3f forward = transform.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
         Vec3 nozzle = renderOrigin(p, partial).add(at.x, at.y, at.z);
         Vec3 direction = new Vec3(forward.x, forward.y, forward.z).normalize();
         float length = Fp5BoosterFire.aircraftLength(throttle);
         float radius = Fp5BoosterFire.aircraftRadius(throttle);
         float strength = (0.015F + 1.1F * throttle * throttle) * ambient * (engines == 2 ? 1.0F : 0.58F);
         float reach = 0.45F + 0.78F * length + 3.0F * radius;
         lights.add(
            new FlightLighting.Glow(
               -1099511627776L - p.getId(),
               nozzle,
               reach,
               strength,
               0.0F,
               false,
               direction,
               nozzle.subtract(direction.scale(Fp5BoosterFire.aircraftLength(throttle))),
               WARM,
               0.0F
            )
         );
      }
   }

   static Matrix4f modelRotation(Su24mEntity plane, float partial) {
      PoseStack pose = new PoseStack();
      if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(plane) instanceof Su24mRenderer vehicle) {
         vehicle.rotateVehicleAxis(plane, pose, Mth.rotLerp(partial, plane.yRotO, plane.getYRot()), partial);
      }

      return new Matrix4f(pose.last().pose());
   }

   static Vec3 renderOrigin(Su24mEntity plane, float partial) {
      return new Vec3(
         Mth.lerp(partial, plane.xOld, plane.getX()),
         Mth.lerp(partial, plane.yOld, plane.getY()),
         Mth.lerp(partial, plane.zOld, plane.getZ())
      );
   }

   static void verifyAnchor(Su24mEntity plane, float partial, Matrix4f actual) {
      Matrix4f light = modelRotation(plane, partial);

      for (float side : new float[]{-0.7525F, 0.7525F}) {
         Vector3f nozzle = new Vector3f(side, 2.205F, 8.83F);
         Vector3f a = actual.transformPosition(new Vector3f(nozzle));
         Vector3f b = light.transformPosition(new Vector3f(nozzle));
         maxAnchorError = Math.max(maxAnchorError, (double)a.distance(b));
         if (a.distance(b) > 0.002) {
            throw new IllegalStateException("SU24 nozzle/light pose differs: " + a.distance(b));
         }
      }

      anchorFrames++;
   }

   static void verifyAnchorEvidence() {
      if (anchorFrames < 40) {
         throw new IllegalStateException("Insufficient SU24 rendered anchor samples: " + anchorFrames);
      } else {
         LoggerFactory.getLogger(Su24mExhaust.class)
            .info("SU24_EXHAUST_ANCHOR_PASS frames={} maxErrorBlocks={} nativeModelPose=true", anchorFrames, maxAnchorError);
      }
   }

   private Su24mExhaust() {
   }

   private static final class Throttle {
      int tick;
      float previous;
      float current;

      Throttle(Su24mEntity p) {
         this.tick = p.tickCount;
         this.previous = this.current = Mth.clamp(p.getPower(), 0.0F, 1.0F);
      }
   }
}
