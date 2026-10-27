package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

final class DiffuseLightVolume {
   private static final int SIDES = 12;
   private static final int BANDS = 8;

   static void render(
      PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 direction, Vec3 camera, float far, float radius, float intensity, boolean flash
   ) {
      if (!(intensity <= 0.005F) && !(radius <= 0.0F)) {
         Vec3 toCamera = center.subtract(camera);
         double distance = toCamera.length();
         if (!(distance < 0.001)) {
            float projection = (float)Math.min(1.0, far / distance);
            Vec3 axis = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : new Vec3(0.0, 1.0, 0.0);
            Vec3 reference = Math.abs(axis.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 right = axis.cross(reference).normalize();
            Vec3 up = right.cross(axis).normalize();
            Vec3 view = camera.subtract(center).normalize();
            pose.pushPose();
            pose.translate(toCamera.x * projection, toCamera.y * projection, toCamera.z * projection);
            Matrix4f matrix = pose.last().pose();
            VertexConsumer out = buffers.getBuffer(RenderType.lightning());

            for (int shell = 0; shell < 3; shell++) {
               float spread = shell == 0 ? 1.0F : (shell == 1 ? 0.57F : 0.23F);
               float opacity = intensity * (shell == 0 ? 0.035F : (shell == 1 ? 0.085F : 0.24F));
               float width = radius * spread * projection;
               float length = width * (flash ? 1.0F : 1.75F);
               Vec3 shift = flash ? Vec3.ZERO : axis.scale(-length * 0.26);
               int red = 255;
               int green = flash ? (shell == 0 ? 191 : (shell == 1 ? 225 : 251)) : (shell == 0 ? 147 : (shell == 1 ? 196 : 244));
               int blue = flash ? (shell == 0 ? 139 : (shell == 1 ? 186 : 240)) : (shell == 0 ? 63 : (shell == 1 ? 112 : 208));

               for (int band = 0; band < 8; band++) {
                  double latitude0 = (-Math.PI / 2) + band * Math.PI / 8.0;
                  double latitude1 = (-Math.PI / 2) + (band + 1) * Math.PI / 8.0;

                  for (int side = 0; side < 12; side++) {
                     double longitude0 = side * Math.PI * 2.0 / 12.0;
                     double longitude1 = (side + 1) * Math.PI * 2.0 / 12.0;
                     Vec3 a = normal(latitude0, longitude0);
                     Vec3 b = normal(latitude0, longitude1);
                     Vec3 c = normal(latitude1, longitude1);
                     Vec3 d = normal(latitude1, longitude0);
                     Vec3 faceNormal = a.add(b).add(c).add(d).normalize();
                     Vec3 worldNormal = right.scale(faceNormal.x)
                        .add(up.scale(faceNormal.y))
                        .add(axis.scale(faceNormal.z));
                     if (!(worldNormal.dot(view) <= -0.08)) {
                        float diffuse = (float)(0.42 + 0.42 * Math.max(0.0, worldNormal.dot(view)) + 0.16 * Math.max(0.0, -faceNormal.z));
                        int alpha = Math.max(0, Math.min(255, (int)(255.0F * opacity * diffuse)));
                        vertex(out, matrix, a, right, up, axis, shift, width, length, red, green, blue, alpha);
                        vertex(out, matrix, b, right, up, axis, shift, width, length, red, green, blue, alpha);
                        vertex(out, matrix, c, right, up, axis, shift, width, length, red, green, blue, alpha);
                        vertex(out, matrix, d, right, up, axis, shift, width, length, red, green, blue, alpha);
                     }
                  }
               }
            }

            pose.popPose();
         }
      }
   }

   private static Vec3 normal(double latitude, double longitude) {
      double width = Math.cos(latitude);
      return new Vec3(width * Math.cos(longitude), width * Math.sin(longitude), Math.sin(latitude));
   }

   private static void vertex(
      VertexConsumer out, Matrix4f matrix, Vec3 normal, Vec3 right, Vec3 up, Vec3 axis, Vec3 shift, float width, float length, int r, int g, int b, int a
   ) {
      Vec3 p = shift.add(right.scale(normal.x * width))
         .add(up.scale(normal.y * width))
         .add(axis.scale(normal.z * length));
      out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setColor(r, g, b, a);
   }

   private DiffuseLightVolume() {
   }
}
