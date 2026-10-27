package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class EngineFlameMesh {
   private static final int AXIAL = 16;
   private static final int RADIAL = 8;
   private static final int STRIDE = 7;
   private static final float[] PROFILE = new float[17];
   private static final float[] SLOPE = new float[17];
   private static final float[] COS = new float[9];
   private static final float[] SIN = new float[9];
   private static int axisX;
   private static int axisY;
   private static int axisZ;
   private static final Matrix3f NORMAL = new Matrix3f();
   private static final float[] GRID = new float[1071];

   private static float profile(float u) {
      float rise = (float)Math.pow(Math.max(0.0F, Math.min(1.0F, u / 0.1F)), 0.54);
      float taper = (float)Math.pow(Math.max(0.0F, 1.0F - Math.max(0.0F, u - 0.12F) / 0.88F), 0.88);
      float expand = (0.68F + 0.64F * u) * (1.0F - (float)Math.pow(Math.max(0.0F, (u - 0.58F) / 0.42F), 1.35));
      return rise * (0.54F * taper + 0.46F * expand) * (0.94F + 0.08F * (float)Math.sin(Math.PI * u));
   }

   static void draw(
      PoseStack pose, VertexConsumer out, float age, float radius, float length, float brightness, float opacity, double distanceSquared, boolean projected
   ) {
      int step = distanceSquared > 25600.0 ? 2 : 1;
      pose.last().pose().normal(NORMAL);
      if (!projected) {
         Vector3f axis = NORMAL.transform(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
         axisX = Math.round(axis.x * 32767.0F);
         axisY = Math.round(axis.y * 32767.0F);
         axisZ = Math.round(axis.z * 32767.0F);
      }

      Vector3f camera = projected
         ? new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()).invert().transformPosition(new Vector3f())
         : null;

      for (int i = 0; i <= 16; i += step) {
         for (int j = 0; j <= 8; j += step) {
            float u = i / 16.0F;
            float r = radius * PROFILE[i] * (1.0F + 0.025F * (float)Math.sin(u * 24.0F - j * (float) Math.PI * 6.0F / 8.0F - age * 1.2F));
            float d = SLOPE[i] * radius / Math.max(0.01F, length);
            float inv = 1.0F / (float)Math.sqrt(1.0F + d * d);
            int k = (i * 9 + j) * 7;
            GRID[k] = u * length;
            GRID[k + 1] = COS[j] * r;
            GRID[k + 2] = SIN[j] * r;
            GRID[k + 3] = -d * inv;
            GRID[k + 4] = COS[j] * inv;
            GRID[k + 5] = SIN[j] * inv;
            if (projected) {
               float vx = camera.x - GRID[k];
               float vy = camera.y - GRID[k + 1];
               float vz = camera.z - GRID[k + 2];
               float invView = 1.0F / (float)Math.sqrt(Math.max(1.0E-8, (double)(vx * vx + vy * vy + vz * vz)));
               float facing = Math.min(1.0F, Math.abs(GRID[k + 3] * vx + GRID[k + 4] * vy + GRID[k + 5] * vz) * invView);
               GRID[k + 6] = (float)Math.sqrt(Math.max(0.0F, 1.0F - facing * facing));
            }
         }
      }

      for (int i = 0; i < 16; i += step) {
         for (int jx = 0; jx < 8; jx += step) {
            vertex(pose, out, i, jx, brightness, opacity, projected);
            vertex(pose, out, i + step, jx, brightness, opacity, projected);
            vertex(pose, out, i + step, jx + step, brightness, opacity, projected);
            vertex(pose, out, i, jx + step, brightness, opacity, projected);
         }
      }
   }

   private static void vertex(PoseStack pose, VertexConsumer out, int i, int j, float brightness, float opacity, boolean projected) {
      int k = (i * 9 + j) * 7;
      out.addVertex(pose.last().pose(), GRID[k], GRID[k + 1], GRID[k + 2]);
      if (projected) {
         out.setUv(i / 16.0F, GRID[k + 6]).setColor(0.125F, brightness, 0.0F, opacity);
      } else {
         out.setColor(brightness, brightness, brightness, opacity)
            .setUv(i / 16.0F, j / 8.0F)
            .setUv1(axisX, axisY)
            .setUv2(axisZ, 0)
            .setNormal(pose.last(), GRID[k + 3], GRID[k + 4], GRID[k + 5])
            ;
      }
   }

   private EngineFlameMesh() {
   }

   static {
      for (int i = 0; i <= 16; i++) {
         float u = i / 16.0F;
         float d = 0.002F;
         PROFILE[i] = profile(u);
         SLOPE[i] = (profile(Math.min(1.0F, u + d)) - profile(Math.max(0.0F, u - d))) / (2.0F * d);
      }

      for (int i = 0; i <= 8; i++) {
         float a = i / 8.0F * (float) Math.PI * 2.0F;
         COS[i] = (float)Math.cos(a);
         SIN[i] = (float)Math.sin(a);
      }
   }
}
