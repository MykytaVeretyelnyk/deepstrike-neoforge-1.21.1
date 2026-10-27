package dev.sbw.deepstrike.model;

import net.minecraft.world.phys.Vec3;

public final class PatriotCanisterLayout {
   public static final double PIVOT_Y = 1.78;
   public static final double PIVOT_Z = 7.66;
   public static final double ELEVATION_DEGREES = 38.0;

   public static Vec3 mouth(int cell) {
      return new Vec3((cell % 2 - 0.5) * 1.075, 2.43 + cell / 2 * 1.005, 1.6);
   }

   public static Vec3 elevatedMouth(int cell) {
      Vec3 p = mouth(cell);
      double a = Math.toRadians(38.0);
      double y = p.y - 1.78;
      double z = p.z - 7.66;
      return new Vec3(p.x, 1.78 + y * Math.cos(a) - z * Math.sin(a), -(7.66 + y * Math.sin(a) + z * Math.cos(a)));
   }

   public static Vec3 axis() {
      double a = Math.toRadians(38.0);
      return new Vec3(0.0, Math.sin(a), Math.cos(a));
   }

   public static double nose(int type) {
      return Math.max(0.0, -InterceptorGeometry.canonicalBounds(type).minX);
   }

   private PatriotCanisterLayout() {
   }
}
