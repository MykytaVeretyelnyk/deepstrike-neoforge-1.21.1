package dev.sbw.deepstrike.model;

import net.minecraft.world.phys.Vec3;

public final class IronDomeCanisterLayout {
   public static final int CELLS = 20;
   public static final int COLUMNS = 4;
   public static final int ROWS = 5;
   public static final double PIVOT_Y = 1.38;
   public static final double PIVOT_Z = 0.85;
   public static final double TILT_DEGREES = 27.0;
   public static final double Y_MOUTH = 4.3;

   public static Vec3 mouth(int cell) {
      int col = Math.floorMod(cell, 4);
      int row = Math.max(0, Math.min(4, cell / 4));
      return new Vec3((col - 1.5) * 0.44, 4.3, 0.85 + (row - 2) * 0.42);
   }

   public static Vec3 elevatedMouth(int cell) {
      Vec3 p = mouth(cell);
      double t = Math.toRadians(27.0);
      double dy = p.y - 1.38;
      double dz = p.z - 0.85;
      return new Vec3(p.x, 1.38 + dy * Math.cos(t) + dz * Math.sin(t), -(0.85 - dy * Math.sin(t) + dz * Math.cos(t)));
   }

   public static Vec3 axis() {
      double t = Math.toRadians(27.0);
      return new Vec3(0.0, Math.cos(t), Math.sin(t));
   }

   public static double nose() {
      return Math.max(0.0, -InterceptorGeometry.canonicalBounds(3).minX);
   }

   public static double setback() {
      return nose() + 0.04;
   }

   private IronDomeCanisterLayout() {
   }
}
