package dev.sbw.deepstrike.model;

import dev.sbw.deepstrike.game.MissileKind;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WreckGeometry {
   private static double smooth(double t) {
      t = Math.max(0.0, Math.min(1.0, t));
      return t * t * (3.0 - 2.0 * t);
   }

   private static double bend(double t, double length, MissileKind kind) {
      double first = Math.max(0.0, t - 0.27);
      double second = Math.max(0.0, t - 0.72);
      double angle = kind == MissileKind.PEACEKEEPER ? 0.31 : (kind == MissileKind.JAGM ? 0.38 : 0.43);
      return length * (first * angle * smooth(first / 0.14) - second * 0.23 * smooth(second / 0.13));
   }

   public static Vec3 deform(MissileKind kind, String bone, Vec3 point) {
      AABB box = MissileGeometry.canonicalBounds(kind);
      double length = box.getXsize();
      double t = Math.max(0.0, Math.min(1.0, (point.x - box.minX) / length));
      double centerY = (box.minY + box.maxY) * 0.5;
      double centerZ = (box.minZ + box.maxZ) * 0.5;
      double x = point.x - length * 0.065 * t * (1.0 - smooth(t / 0.3));
      double y = point.y - centerY;
      double z = point.z - centerZ;
      double dent = Math.exp(-Math.pow((t - 0.39) / 0.115, 2.0));
      double crush = 1.0 - 0.3 * (1.0 - smooth(t / 0.21)) - 0.15 * dent;
      y *= crush;
      z *= 1.0 - 0.19 * dent;
      double offset = bend(t, length, kind);
      double epsilon = 2.0E-4;
      double slope = (bend(Math.min(1.0, t + epsilon), length, kind) - bend(Math.max(0.0, t - epsilon), length, kind)) / (2.0 * epsilon * length);
      double angle = Math.atan(slope);
      if (bone.contains("wing") || bone.contains("fin")) {
         double radius = Math.hypot(y, z);
         double root = Math.min(box.getYsize(), box.getZsize()) * 0.25;
         double fold = 0.66 * smooth((radius - root) / Math.max(0.1, root * 2.0));
         double co = Math.cos(fold);
         double si = Math.sin(fold);
         double oldY = y;
         y = y * co - z * si;
         z = oldY * si + z * co;
      }

      double scuff = 0.035 * Math.sin(t * 47.0) * Math.exp(-Math.pow((t - 0.4) / 0.16, 2.0));
      return new Vec3(
         x - y * Math.sin(angle), centerY + offset + y * Math.cos(angle), centerZ + z + length * 0.025 * Math.max(0.0, t - 0.35) * Math.sin(t * 3.0) + scuff
      );
   }

   public static float soot(MissileKind kind, Vec3 original) {
      AABB box = MissileGeometry.canonicalBounds(kind);
      double t = (original.x - box.minX) / box.getXsize();
      double bend = Math.exp(-Math.pow((t - 0.39) / 0.15, 2.0));
      double nose = 1.0 - smooth(t / 0.24);
      double mottling = 0.045 * Math.sin(original.x * 19.0 + original.y * 13.0) * Math.sin(original.z * 21.0 + original.x * 7.0);
      return (float)Math.max(0.4, Math.min(0.96, 0.89 - 0.3 * bend - 0.12 * nose + mottling));
   }

   private WreckGeometry() {
   }
}
