package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.ModelGeometry;
import java.util.Map;
import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class MissileModelContact {
   public static double fraction(GuidedMissileEntity target, Vec3 worldFrom, Vec3 worldTo, Vec3 targetMovement) {
      Vec3 from = worldFrom.subtract(target.position());
      Vec3 to = worldTo.subtract(target.position()).subtract(targetMovement);
      ModelGeometry geometry = ModelGeometry.get(ModelAssets.missile(target.kind()));
      Map<String, Matrix4f> transforms = geometry.defaultTransforms();
      double first = Double.POSITIVE_INFINITY;

      for (ModelGeometry.Bone bone : geometry.bones) {
         if (target.visibleFlightBone(bone.name())) {
            for (ModelGeometry.Cube cube : bone.cubes()) {
               Matrix4f inverse = MissileGeometry.flight(target.kind(), target.getYRot(), target.getXRot())
                  .mul((Matrix4fc)transforms.get(bone.name()))
                  .mul(cube.transform())
                  .invert();
               Vec3 localFrom = new Vec3(inverse.transformPosition(new Vector3f((float)from.x, (float)from.y, (float)from.z)));
               Vec3 localTo = new Vec3(inverse.transformPosition(new Vector3f((float)to.x, (float)to.y, (float)to.z)));
               Vector3f min = cube.min();
               Vector3f size = cube.size();
               AABB box = new AABB(min.x, min.y, min.z, min.x + size.x, min.y + size.y, min.z + size.z).inflate(0.04);
               if (box.contains(localFrom)) {
                  return 0.0;
               }

               Optional<Vec3> contact = box.clip(localFrom, localTo);
               if (contact.isPresent()) {
                  double length = localFrom.distanceTo(localTo);
                  if (length > 1.0E-9) {
                     first = Math.min(first, localFrom.distanceTo(contact.get()) / length);
                  }
               }
            }
         }
      }

      return Double.isFinite(first) ? first : -1.0;
   }

   public static Vec3 aimOffset(GuidedMissileEntity target) {
      return aimOffset(target, 0.5F);
   }

   public static Vec3 aimOffset(GuidedMissileEntity target, float bodyFraction) {
      bodyFraction = Mth.clamp(bodyFraction, 0.25F, 0.75F);
      ModelGeometry geometry = ModelGeometry.get(ModelAssets.missile(target.kind()));
      Map<String, Matrix4f> transforms = geometry.defaultTransforms();
      double largest = 0.0;
      Vec3 offset = Vec3.ZERO;

      for (ModelGeometry.Bone bone : geometry.bones) {
         if (target.visibleFlightBone(bone.name())) {
            for (ModelGeometry.Cube cube : bone.cubes()) {
               Vector3f size = cube.size();
               double volume = size.x * size.y * size.z;
               if (!(volume <= largest)) {
                  Matrix4f matrix = MissileGeometry.flight(target.kind(), target.getYRot(), target.getXRot())
                     .mul((Matrix4fc)transforms.get(bone.name()))
                     .mul(cube.transform());
                  Vector3f inside = new Vector3f(size).mul(0.5F);
                  if (size.x >= size.y && size.x >= size.z) {
                     inside.x = size.x * bodyFraction;
                  } else if (size.y >= size.z) {
                     inside.y = size.y * bodyFraction;
                  } else {
                     inside.z = size.z * bodyFraction;
                  }

                  offset = new Vec3(matrix.transformPosition(new Vector3f(cube.min()).add(inside)));
                  largest = volume;
               }
            }
         }
      }

      return offset;
   }

   private MissileModelContact() {
   }
}
