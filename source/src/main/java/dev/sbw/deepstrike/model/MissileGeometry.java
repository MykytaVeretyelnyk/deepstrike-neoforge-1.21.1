package dev.sbw.deepstrike.model;

import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.MissileKind;
import java.util.EnumMap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MissileGeometry {
   private static final EnumMap<MissileKind, AABB> CANONICAL = new EnumMap<>(MissileKind.class);
   private static final EnumMap<MissileKind, Double> CLEARANCES = new EnumMap<>(MissileKind.class);

   public static Matrix4f exportRotation(MissileKind kind) {
      return kind == MissileKind.PEACEKEEPER ? new Matrix4f().rotateZ((float) (Math.PI / 2)) : new Matrix4f();
   }

   public static Matrix4f flight(MissileKind kind, float yaw, float pitch) {
      return ModelGeometry.flight(yaw, pitch).mul(exportRotation(kind));
   }

   public static Vec3 localNose(MissileKind kind) {
      return kind == MissileKind.PEACEKEEPER ? new Vec3(0.0, 1.0, 0.0) : new Vec3(-1.0, 0.0, 0.0);
   }

   public static synchronized AABB canonicalBounds(MissileKind kind) {
      AABB cached = CANONICAL.get(kind);
      if (cached != null) {
         return cached;
      } else {
         ModelGeometry geometry = ModelGeometry.get(ModelAssets.missile(kind));
         double[] bounds = new double[]{
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY
         };

         for (ModelGeometry.Bone bone : geometry.bones) {
            for (ModelGeometry.Cube cube : bone.cubes()) {
               Matrix4f transform = exportRotation(kind).mul((Matrix4fc)geometry.defaultTransforms().get(bone.name())).mul(cube.transform());

               for (int i = 0; i < 8; i++) {
                  Vector3f vertex = new Vector3f(cube.min())
                     .add((i & 1) == 0 ? 0.0F : cube.size().x, (i & 2) == 0 ? 0.0F : cube.size().y, (i & 4) == 0 ? 0.0F : cube.size().z);
                  include(bounds, transform.transformPosition(vertex));
               }
            }
         }

         AABB box = box(bounds);
         CANONICAL.put(kind, box);
         return box;
      }
   }

   public static double tailDistance(MissileKind kind) {
      return Math.max(0.0, canonicalBounds(kind).maxX);
   }

   public static double noseDistance(MissileKind kind) {
      return Math.max(0.0, -canonicalBounds(kind).minX);
   }

   public static Vec3 worldCenter(MissileKind kind, Vec3 position, float yaw, float pitch) {
      Vec3 center = canonicalBounds(kind).getCenter();
      Vector3f rotated = ModelGeometry.flight(yaw, pitch)
         .transformPosition(new Vector3f((float)center.x, (float)center.y, (float)center.z));
      return position.add(new Vec3(rotated));
   }

   public static double clearance(MissileKind kind) {
      return CLEARANCES.computeIfAbsent(
         kind,
         k -> Math.max(
            Math.max(10.0, tailDistance(k) + 2.0), k == MissileKind.PEACEKEEPER ? LauncherLayout.verticalClearance(LauncherBlock.Family.PEACEKEEPER, k) : 0.0
         )
      );
   }

   public static AABB worldBounds(MissileKind kind, Vec3 position, Vec3 velocity, float fallbackYaw, float fallbackPitch) {
      return worldBounds(canonicalBounds(kind), position, velocity, fallbackYaw, fallbackPitch);
   }

   public static AABB substepBounds(MissileKind kind, Vec3 position, Vec3 before, Vec3 after, float yaw, float pitch) {
      return substepBounds(canonicalBounds(kind), position, before, after, yaw, pitch);
   }

   public static AABB substepBounds(AABB box, Vec3 position, Vec3 before, Vec3 after, float yaw, float pitch) {
      FlightOrientation.Angles first = FlightOrientation.along(before, yaw, pitch);
      FlightOrientation.Angles last = FlightOrientation.along(after, yaw, pitch);
      Quaternionf q0 = FlightOrientation.rotation(first.yaw(), first.pitch());
      Quaternionf q1 = FlightOrientation.rotation(last.yaw(), last.pitch());
      Quaternionf relative = new Quaternionf(q1).mul(new Quaternionf(q0).conjugate()).normalize();
      double axisLength = Math.sqrt(relative.x * relative.x + relative.y * relative.y + relative.z * relative.z);
      double angle = 2.0 * Math.atan2(axisLength, Math.abs(relative.w));
      double radius = 0.0;
      if (axisLength > 1.0E-9) {
         double ax = relative.x / axisLength;
         double ay = relative.y / axisLength;
         double az = relative.z / axisLength;

         for (int i = 0; i < 8; i++) {
            Vector3f v = q0.transform(
               new Vector3f(
                  (float)((i & 1) == 0 ? box.minX : box.maxX),
                  (float)((i & 2) == 0 ? box.minY : box.maxY),
                  (float)((i & 4) == 0 ? box.minZ : box.maxZ)
               )
            );
            double dot = v.x * ax + v.y * ay + v.z * az;
            radius = Math.max(radius, Math.sqrt(Math.max(0.0, (double)v.x * v.x + (double)v.y * v.y + (double)v.z * v.z - dot * dot)));
         }
      }

      return worldBounds(box, position, before, yaw, pitch)
         .minmax(worldBounds(box, position, after, yaw, pitch))
         .inflate(radius * angle * 0.5 + (angle > 1.0E-6 ? 1.0E-5 : 0.0));
   }

   public static AABB worldBounds(AABB box, Vec3 position, Vec3 velocity, float fallbackYaw, float fallbackPitch) {
      float yaw = velocity.horizontalDistance() > 1.0E-7 ? (float)Math.toDegrees(Math.atan2(-velocity.x, velocity.z)) : fallbackYaw;
      float pitch = velocity.lengthSqr() > 1.0E-12 ? (float)(-Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()))) : fallbackPitch;
      Matrix4f rotation = ModelGeometry.flight(yaw, pitch);
      double[] bounds = new double[]{
         Double.POSITIVE_INFINITY,
         Double.POSITIVE_INFINITY,
         Double.POSITIVE_INFINITY,
         Double.NEGATIVE_INFINITY,
         Double.NEGATIVE_INFINITY,
         Double.NEGATIVE_INFINITY
      };

      for (int i = 0; i < 8; i++) {
         include(
            bounds,
            rotation.transformPosition(
               new Vector3f(
                  (float)((i & 1) == 0 ? box.minX : box.maxX),
                  (float)((i & 2) == 0 ? box.minY : box.maxY),
                  (float)((i & 4) == 0 ? box.minZ : box.maxZ)
               )
            )
         );
      }

      return box(bounds).move(position);
   }

   private static void include(double[] b, Vector3f v) {
      b[0] = Math.min(b[0], (double)v.x);
      b[1] = Math.min(b[1], (double)v.y);
      b[2] = Math.min(b[2], (double)v.z);
      b[3] = Math.max(b[3], (double)v.x);
      b[4] = Math.max(b[4], (double)v.y);
      b[5] = Math.max(b[5], (double)v.z);
   }

   private static AABB box(double[] b) {
      return new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
   }

   private MissileGeometry() {
   }
}
