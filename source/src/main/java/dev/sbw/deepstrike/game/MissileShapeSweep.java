package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.ModelGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

final class MissileShapeSweep {
   private static final Vec3[] WORLD = new Vec3[]{new Vec3(1.0, 0.0, 0.0), new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0)};

   static MissileShapeSweep.Mesh prepare(MissileKind kind, float yaw, float pitch, double padding, Predicate<String> visible) {
      ModelGeometry geometry = ModelGeometry.get(ModelAssets.missile(kind));
      Map<String, Matrix4f> transforms = geometry.defaultTransforms();
      Matrix4f orientation = MissileGeometry.flight(kind, yaw, pitch);
      ArrayList<MissileShapeSweep.Cuboid> cubes = new ArrayList<>();

      for (ModelGeometry.Bone bone : geometry.bones) {
         if (visible.test(bone.name())) {
            for (ModelGeometry.Cube cube : bone.cubes()) {
               Matrix4f matrix = new Matrix4f(orientation).mul((Matrix4fc)transforms.get(bone.name())).mul(cube.transform());
               Vector3f size = cube.size();
               Vec3 center = new Vec3(matrix.transformPosition(new Vector3f(cube.min()).add(size.x * 0.5F, size.y * 0.5F, size.z * 0.5F)));
               Vec3 x = new Vec3(matrix.transformDirection(new Vector3f(size.x * 0.5F, 0.0F, 0.0F)));
               Vec3 y = new Vec3(matrix.transformDirection(new Vector3f(0.0F, size.y * 0.5F, 0.0F)));
               Vec3 z = new Vec3(matrix.transformDirection(new Vector3f(0.0F, 0.0F, size.z * 0.5F)));
               Vec3 extent = new Vec3(
                  Math.abs(x.x) + Math.abs(y.x) + Math.abs(z.x),
                  Math.abs(x.y) + Math.abs(y.y) + Math.abs(z.y),
                  Math.abs(x.z) + Math.abs(y.z) + Math.abs(z.z)
               );
               cubes.add(new MissileShapeSweep.Cuboid(center, x, y, z, new AABB(center.subtract(extent), center.add(extent))));
            }
         }
      }

      return new MissileShapeSweep.Mesh(cubes, Math.max(0.0, padding));
   }

   static MissileShapeSweep.Mesh motion(MissileKind kind, Vec3 before, Vec3 movement, float yaw, float pitch, double padding, Predicate<String> visible) {
      FlightOrientation.Angles angles = FlightOrientation.along(movement, yaw, pitch);
      Vec3 prior = before.lengthSqr() > 1.0E-12 ? before.normalize() : FlightOrientation.direction(yaw, pitch);
      Vec3 next = movement.lengthSqr() > 1.0E-12 ? movement.normalize() : prior;
      double turn = Math.acos(Math.max(-1.0, Math.min(1.0, prior.dot(next))));
      AABB bounds = MissileGeometry.canonicalBounds(kind);
      double radius = Math.sqrt(
         Math.pow(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)), 2.0)
            + Math.pow(Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY)), 2.0)
            + Math.pow(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)), 2.0)
      );
      return prepare(kind, angles.yaw(), angles.pitch(), padding + radius * turn, visible);
   }

   private static double fraction(MissileShapeSweep.Cuboid cube, AABB box, Vec3 from, Vec3 movement, double padding) {
      Vec3 offset = cube.center.add(from).subtract(box.getCenter());
      Vec3 half = new Vec3(box.getXsize() * 0.5, box.getYsize() * 0.5, box.getZsize() * 0.5);
      Vec3[] edges = new Vec3[]{cube.x, cube.y, cube.z};
      double enter = 0.0;
      double exit = 1.0;

      for (int i = 0; i < 15; i++) {
         Vec3 axis = i < 3 ? WORLD[i] : (i < 6 ? edges[(i - 3 + 1) % 3].cross(edges[(i - 3 + 2) % 3]) : edges[(i - 6) / 3].cross(WORLD[(i - 6) % 3]));
         double length = axis.length();
         if (!(length < 1.0E-10)) {
            axis = axis.scale(1.0 / length);
            double radius = Math.abs(cube.x.dot(axis))
               + Math.abs(cube.y.dot(axis))
               + Math.abs(cube.z.dot(axis))
               + half.x * Math.abs(axis.x)
               + half.y * Math.abs(axis.y)
               + half.z * Math.abs(axis.z)
               + padding;
            double position = offset.dot(axis);
            double velocity = movement.dot(axis);
            if (Math.abs(velocity) < 1.0E-12) {
               if (Math.abs(position) > radius) {
                  return -1.0;
               }
            } else {
               double a = (-radius - position) / velocity;
               double b = (radius - position) / velocity;
               if (a > b) {
                  double swap = a;
                  a = b;
                  b = swap;
               }

               enter = Math.max(enter, a);
               exit = Math.min(exit, b);
               if (enter > exit + 1.0E-10) {
                  return -1.0;
               }
            }
         }
      }

      return enter >= 0.0 && enter <= 1.0 ? enter : -1.0;
   }

   private MissileShapeSweep() {
   }

   record Contact(double fraction, Vec3 origin, Vec3 surface) {
   }

   private record Cuboid(Vec3 center, Vec3 x, Vec3 y, Vec3 z, AABB bounds) {
   }

   static final class Mesh {
      private final List<MissileShapeSweep.Cuboid> cubes;
      private final double padding;

      private Mesh(List<MissileShapeSweep.Cuboid> cubes, double padding) {
         this.cubes = cubes;
         this.padding = padding;
      }

      MissileShapeSweep.Contact contact(AABB obstacle, Vec3 from, Vec3 to) {
         Vec3 movement = to.subtract(from);
         double first = Double.POSITIVE_INFINITY;
         MissileShapeSweep.Cuboid chosen = null;

         for (MissileShapeSweep.Cuboid cube : this.cubes) {
            if (cube.bounds.move(from).inflate(this.padding).expandTowards(movement).intersects(obstacle)) {
               double fraction = MissileShapeSweep.fraction(cube, obstacle, from, movement, this.padding);
               if (fraction >= 0.0 && fraction < first) {
                  first = fraction;
                  chosen = cube;
               }
            }
         }

         if (chosen == null) {
            return null;
         } else {
            Vec3 origin = from.add(movement.scale(first));
            Vec3 surface = ContactSurface.find(obstacle, chosen.bounds.move(origin).inflate(this.padding), movement);
            return new MissileShapeSweep.Contact(first, origin, surface);
         }
      }
   }
}
