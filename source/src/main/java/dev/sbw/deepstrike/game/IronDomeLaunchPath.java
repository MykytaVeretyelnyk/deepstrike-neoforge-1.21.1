package dev.sbw.deepstrike.game;

import net.minecraft.world.phys.Vec3;

public final class IronDomeLaunchPath {
   public static final int AXIAL_TICKS = 20;
   public static final int CLIMB_END_TICKS = 30;
   public static final int ACQUISITION_START_TICKS = 30;
   public static final int ACQUISITION_END_TICKS = 48;

   private static double ease(double x) {
      x = Math.max(0.0, Math.min(1.0, x));
      return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
   }

   public static double speed(int age, double maxSpeed) {
      if (age <= 20) {
         double t = Math.max(0.0, Math.min(1.0, (age - 1.0) / 19.0));
         return 0.28 + 0.6699999999999999 * Math.pow(t, 1.25);
      } else {
         double ramp = Math.max(0.0, Math.min(1.0, (age - 20) / 28.0));
         double smooth = ramp * ramp * (3.0 - 2.0 * ramp);
         return 0.95 + (maxSpeed - 0.95) * smooth;
      }
   }

   public static double acquisition(int age) {
      return age <= 30 ? 0.0 : ease((age - 30) / 18.0);
   }

   public static Vec3 direction(Vec3 axis, Vec3 pursuit, int age) {
      if (age <= 20) {
         return axis;
      } else {
         Vec3 horizontal = new Vec3(axis.x, 0.0, axis.z);
         horizontal = horizontal.lengthSqr() > 1.0E-8 ? horizontal.normalize() : new Vec3(0.0, 0.0, 1.0);
         double climbAngle = Math.toRadians(86.0);
         Vec3 climb = horizontal.scale(Math.cos(climbAngle)).add(0.0, Math.sin(climbAngle), 0.0);
         double upBlend = ease((age - 20) / 10.0);
         Vec3 departure = axis.lerp(climb, upBlend).normalize();
         double acq = acquisition(age);
         Vec3 blend = departure.lerp(pursuit, acq);
         return blend.lengthSqr() < 1.0E-10 ? departure : blend.normalize();
      }
   }

   private IronDomeLaunchPath() {
   }
}
