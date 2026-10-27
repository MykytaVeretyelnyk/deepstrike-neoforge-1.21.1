package dev.sbw.deepstrike.model;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class InterceptorGeometry {
   private static final Map<String, AABB> BOUNDS = new HashMap<>();

   public static float axialScale(int warhead) {
      return warhead == 3 ? 1.12F : (warhead == 0 ? 1.438F : 1.652F);
   }

   public static float radialScale(int warhead) {
      return warhead == 3 ? 0.66F : 1.0F;
   }

   public static String model(int warhead) {
      return warhead == 3 ? "tamir" : (warhead == 0 ? "pac_2" : "pac_3");
   }

   public static synchronized AABB canonicalBounds(int warhead) {
      String name = model(warhead);
      return BOUNDS.computeIfAbsent(
         name,
         key -> {
            ModelGeometry geometry = ModelGeometry.get(key);
            Map<String, Matrix4f> transforms = geometry.defaultTransforms();
            double minX = Double.POSITIVE_INFINITY;
            double minY = minX;
            double minZ = minX;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = maxX;
            double maxZ = maxX;

            for (ModelGeometry.Bone bone : geometry.bones) {
               for (ModelGeometry.Cube cube : bone.cubes()) {
                  Matrix4f matrix = new Matrix4f()
                     .scale(axialScale(warhead), radialScale(warhead), radialScale(warhead))
                     .mul((Matrix4fc)transforms.get(bone.name()))
                     .mul(cube.transform());

                  for (int i = 0; i < 8; i++) {
                     Vector3f point = matrix.transformPosition(
                        new Vector3f(cube.min())
                           .add((i & 1) == 0 ? 0.0F : cube.size().x, (i & 2) == 0 ? 0.0F : cube.size().y, (i & 4) == 0 ? 0.0F : cube.size().z)
                     );
                     minX = Math.min(minX, (double)point.x);
                     minY = Math.min(minY, (double)point.y);
                     minZ = Math.min(minZ, (double)point.z);
                     maxX = Math.max(maxX, (double)point.x);
                     maxY = Math.max(maxY, (double)point.y);
                     maxZ = Math.max(maxZ, (double)point.z);
                  }
               }
            }

            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
         }
      );
   }

   public static AABB worldBounds(int warhead, Vec3 position, Vec3 movement, float yaw, float pitch) {
      return MissileGeometry.worldBounds(canonicalBounds(warhead), position, movement, yaw, pitch);
   }

   public static AABB substepBounds(int warhead, Vec3 position, Vec3 before, Vec3 after, float yaw, float pitch) {
      return MissileGeometry.substepBounds(canonicalBounds(warhead), position, before, after, yaw, pitch);
   }

   private InterceptorGeometry() {
   }
}
