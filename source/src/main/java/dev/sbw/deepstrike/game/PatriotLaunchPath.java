package dev.sbw.deepstrike.game;

import net.minecraft.world.phys.Vec3;

public final class PatriotLaunchPath {
   public static final double CLEARANCE = 8.0;
   public static final double LOFT_END = 30.0;
   public static final double ACQUISITION_END = 80.0;

   private static double ease(double x) {
      x = Math.max(0.0, Math.min(1.0, x));
      return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
   }

   public static double acquisition(double distance) {
      return ease((distance - 30.0) / 50.0);
   }

   public static Vec3 direction(Vec3 axis, Vec3 pursuit, double distance) {
      Vec3 horizontal = new Vec3(axis.x, 0.0, axis.z).normalize();
      double elevation = Math.toRadians(70.0);
      Vec3 loft = horizontal.scale(Math.cos(elevation)).add(0.0, Math.sin(elevation), 0.0);
      Vec3 departure = axis.lerp(loft, ease((distance - 8.0) / 22.0)).normalize();
      Vec3 blend = departure.lerp(pursuit, acquisition(distance));
      return blend.lengthSqr() < 1.0E-10 ? departure : blend.normalize();
   }

   private PatriotLaunchPath() {
   }
}
