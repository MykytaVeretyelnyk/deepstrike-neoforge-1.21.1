package dev.sbw.deepstrike.model;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class FlightOrientation {
   public static FlightOrientation.Angles along(Vec3 velocity, float fallbackYaw, float fallbackPitch) {
      if (velocity.lengthSqr() < 1.0E-12) {
         return new FlightOrientation.Angles(fallbackYaw, fallbackPitch);
      } else {
         float yaw = velocity.horizontalDistance() > 1.0E-7 ? (float)Math.toDegrees(Math.atan2(-velocity.x, velocity.z)) : fallbackYaw;
         return new FlightOrientation.Angles(yaw, (float)(-Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance()))));
      }
   }

   public static Quaternionf rotation(float yaw, float pitch) {
      return new Quaternionf().rotateY((float)Math.toRadians(90.0F - yaw)).rotateZ((float)Math.toRadians(pitch));
   }

   public static Vec3 direction(float yaw, float pitch) {
      double y = Math.toRadians(yaw);
      double p = Math.toRadians(pitch);
      double horizontal = Math.cos(p);
      return new Vec3(-Math.sin(y) * horizontal, -Math.sin(p), Math.cos(y) * horizontal);
   }

   public static Matrix4f interpolate(float oldYaw, float oldPitch, float yaw, float pitch, float partial) {
      float fraction = Math.max(0.0F, Math.min(1.0F, partial));
      return new Matrix4f().rotation(rotation(oldYaw, oldPitch).slerp(rotation(yaw, pitch), fraction).normalize());
   }

   private FlightOrientation() {
   }

   public record Angles(float yaw, float pitch) {
   }
}
