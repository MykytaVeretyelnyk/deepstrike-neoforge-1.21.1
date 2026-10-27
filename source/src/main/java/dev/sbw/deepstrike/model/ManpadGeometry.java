package dev.sbw.deepstrike.model;

import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ManpadGeometry {
   private static final Map<String, ManpadGeometry.Body> BODIES = new HashMap<>();

   private static synchronized ManpadGeometry.Body body(MissileProjectile missile) {
      String name = missile instanceof Fim92MissileEntity ? "fim_92_missile" : "igla_9k38_missile";
      return BODIES.computeIfAbsent(
         name,
         key -> {
            ModelGeometry geometry = ModelGeometry.resource("/assets/superbwarfare/models/bedrock/projectile/" + key + ".geo.json");
            Set<String> visual = new HashSet<>();
            visual.add("flare");

            boolean changed;
            do {
               changed = false;

               for (ModelGeometry.Bone bone : geometry.bones) {
                  if (visual.contains(bone.parent()) && visual.add(bone.name())) {
                     changed = true;
                  }
               }
            } while (changed);

            List<Vector3f> vertices = new ArrayList<>();
            double radius = 0.0;

            for (ModelGeometry.Bone bonex : geometry.bones) {
               if (!visual.contains(bonex.name())) {
                  for (Vector3f vertex : bonex.meshVertices()) {
                     Vector3f point = new Matrix4f((Matrix4fc)geometry.defaultTransforms().get(bonex.name())).transformPosition(new Vector3f(vertex));
                     vertices.add(point);
                     radius = Math.max(radius, (double)point.length());
                  }

                  for (ModelGeometry.Cube cube : bonex.cubes()) {
                     Matrix4f matrix = new Matrix4f((Matrix4fc)geometry.defaultTransforms().get(bonex.name())).mul(cube.transform());

                     for (int i = 0; i < 8; i++) {
                        Vector3f vertex = matrix.transformPosition(
                           new Vector3f(cube.min())
                              .add((i & 1) == 0 ? 0.0F : cube.size().x, (i & 2) == 0 ? 0.0F : cube.size().y, (i & 4) == 0 ? 0.0F : cube.size().z)
                        );
                        vertices.add(vertex);
                        radius = Math.max(radius, (double)vertex.length());
                     }
                  }
               }
            }

            if (vertices.isEmpty()) {
               throw new IllegalStateException("Empty MANPADS body: " + key);
            } else {
               return new ManpadGeometry.Body(List.copyOf(vertices), radius);
            }
         }
      );
   }

   public static Matrix4f rotation(Vec3 movement, float fallbackYaw, float fallbackPitch) {
      Vec3 direction = movement.lengthSqr() > 1.0E-12 ? movement : FlightOrientation.direction(fallbackYaw, fallbackPitch);
      return new Matrix4f()
         .rotateY((float)Mth.atan2(direction.x, direction.z))
         .rotateX((float)(Math.PI - Mth.atan2(direction.y, direction.horizontalDistance())))
         .rotateZ((float) Math.PI);
   }

   public static AABB worldBounds(MissileProjectile missile, Vec3 position, Vec3 movement) {
      Matrix4f matrix = rotation(movement, missile.getYRot(), missile.getXRot());
      double minX = Double.POSITIVE_INFINITY;
      double minY = minX;
      double minZ = minX;
      double maxX = -minX;
      double maxY = -minX;
      double maxZ = -minX;

      for (Vector3f vertex : body(missile).vertices()) {
         Vector3f point = matrix.transformPosition(new Vector3f(vertex));
         minX = Math.min(minX, (double)point.x);
         minY = Math.min(minY, (double)point.y);
         minZ = Math.min(minZ, (double)point.z);
         maxX = Math.max(maxX, (double)point.x);
         maxY = Math.max(maxY, (double)point.y);
         maxZ = Math.max(maxZ, (double)point.z);
      }

      return new AABB(minX, minY, minZ, maxX, maxY, maxZ).move(position.add(0.0, missile.getBbHeight() * 0.5, 0.0));
   }

   public static AABB substepBounds(MissileProjectile missile, Vec3 position, Vec3 before, Vec3 after) {
      Quaternionf first = rotation(before, missile.getYRot(), missile.getXRot()).getNormalizedRotation(new Quaternionf());
      Quaternionf last = rotation(after, missile.getYRot(), missile.getXRot()).getNormalizedRotation(new Quaternionf());
      double angle = 2.0 * Math.acos(Math.min(1.0F, Math.abs(first.dot(last))));
      return worldBounds(missile, position, before).minmax(worldBounds(missile, position, after)).inflate(body(missile).radius() * angle * 0.5);
   }

   public static Vec3 nozzle(MissileProjectile missile, Vec3 position, Vec3 movement) {
      return endpoint(missile, position, movement, false);
   }

   public static Vec3 nose(MissileProjectile missile, Vec3 position, Vec3 movement) {
      return endpoint(missile, position, movement, true);
   }

   private static Vec3 endpoint(MissileProjectile missile, Vec3 position, Vec3 movement, boolean front) {
      Vec3 direction = movement.lengthSqr() > 1.0E-10 ? movement.normalize() : FlightOrientation.direction(missile.getYRot(), missile.getXRot());
      Matrix4f matrix = rotation(direction, missile.getYRot(), missile.getXRot());
      Vector3f localDirection = new Matrix4f(matrix)
         .transpose()
         .transformDirection(new Vector3f((float)direction.x, (float)direction.y, (float)direction.z));
      double extreme = front ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

      for (Vector3f vertex : body(missile).vertices()) {
         extreme = front ? Math.max(extreme, (double)vertex.dot(localDirection)) : Math.min(extreme, (double)vertex.dot(localDirection));
      }

      Vector3f center = new Vector3f();
      int count = 0;

      for (Vector3f vertex : body(missile).vertices()) {
         if (Math.abs(vertex.dot(localDirection) - extreme) < 1.0E-5) {
            center.add(vertex);
            count++;
         }
      }

      matrix.transformPosition(center.div(count));
      return position.add(0.0, missile.getBbHeight() * 0.5, 0.0).add(new Vec3(center));
   }

   private ManpadGeometry() {
   }

   private record Body(List<Vector3f> vertices, double radius) {
   }
}
