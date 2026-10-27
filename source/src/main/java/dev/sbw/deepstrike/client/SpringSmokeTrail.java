package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SpringSmokeTrail {
   static void draw(
      VertexConsumer out, Pose pose, Vec3 camera, SpringSmokeTrail.Point older, SpringSmokeTrail.Point newer, float red, float green, float blue, int seed
   ) {
      Vec3 dif1 = newer.position.subtract(camera).normalize();
      Vec3 dif2 = older.position.subtract(camera).normalize();
      Vec3 odir1 = side(dif1, newer.direction);
      Vec3 odir2 = side(dif2, older.direction);
      if (odir2.dot(odir1) < 0.0) {
         odir2 = odir2.scale(-1.0);
      }

      float opacity1 = opacity(newer, dif1);
      float opacity2 = opacity(older, dif2);
      if (newer.direction.dot(older.direction) < 0.98) {
         double dist = newer.position.distanceTo(older.position);
         Vec3 dirpos1 = newer.position.subtract(newer.direction.scale(dist * 0.33));
         Vec3 dirpos2 = older.position.add(older.direction.scale(dist * 0.33));
         Vec3 midpos = newer.position.add(older.position).scale(0.125).add(dirpos1.add(dirpos2).scale(0.375));
         Vec3 middir = newer.direction.add(older.direction).normalize();
         SpringSmokeTrail.Point middle = new SpringSmokeTrail.Point(
            midpos,
            middir,
            (newer.width + older.width) * 0.5F,
            (newer.age + older.age) * 0.5F,
            (newer.alpha + older.alpha) * 0.5F,
            (newer.u + older.u) * 0.5F,
            newer.light,
            (newer.heat + older.heat) * 0.5F
         );
         Vec3 difm = midpos.subtract(camera).normalize();
         Vec3 odirm = side(difm, middir);
         if (odirm.dot(odir1) < 0.0) {
            odirm = odirm.scale(-1.0);
         }

         float opacitym = opacity(middle, difm);
         quad(out, pose, camera, newer, middle, odir1, odirm, opacity1, opacitym, red, green, blue, seed);
         quad(out, pose, camera, middle, older, odirm, odir2, opacitym, opacity2, red, green, blue, seed);
      } else {
         quad(out, pose, camera, newer, older, odir1, odir2, opacity1, opacity2, red, green, blue, seed);
      }
   }

   private static float opacity(SpringSmokeTrail.Point p, Vec3 dif) {
      return Mth.clamp(p.alpha * (0.7F + (float)Math.abs(dif.dot(p.direction))), 0.0F, 1.0F);
   }

   private static Vec3 side(Vec3 dif, Vec3 direction) {
      Vec3 side = dif.cross(direction);
      if (side.lengthSqr() < 1.0E-10) {
         side = direction.cross(Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0));
      }

      return side.normalize();
   }

   private static void quad(
      VertexConsumer out,
      Pose pose,
      Vec3 camera,
      SpringSmokeTrail.Point a,
      SpringSmokeTrail.Point b,
      Vec3 acrossA,
      Vec3 acrossB,
      float alphaA,
      float alphaB,
      float red,
      float green,
      float blue,
      int seed
   ) {
      vertex(out, pose, camera, a, acrossA, -1.0F, alphaA, red, green, blue, seed);
      vertex(out, pose, camera, b, acrossB, -1.0F, alphaB, red, green, blue, seed);
      vertex(out, pose, camera, b, acrossB, 1.0F, alphaB, red, green, blue, seed);
      vertex(out, pose, camera, a, acrossA, 1.0F, alphaA, red, green, blue, seed);
   }

   private static void vertex(
      VertexConsumer out, Pose pose, Vec3 camera, SpringSmokeTrail.Point p, Vec3 side, float sign, float alpha, float red, float green, float blue, int seed
   ) {
      double distance = p.position.distanceTo(camera);
      if (!(distance >= 1500.0)) {
         double width = p.width;
         alpha *= MissileVisibility.trailOpacity(p.position, distance);
         if (!(alpha <= 1.0E-4F)) {
            Vec3 pos = p.position.add(side.scale(sign * width)).subtract(camera);
            double far = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.72;
            if (pos.length() > far) {
               pos = pos.scale(far / pos.length());
            }

            int u1 = p.heat > 0.005F ? -Math.max(1, Math.min(32767, (int)(32767.0F * p.heat))) : seed;
            out.addVertex(pose.pose(), (float)pos.x, (float)pos.y, (float)pos.z)
               .setColor(red, green, blue, alpha)
               .setUv(p.u, sign)
               .setUv1(u1, Math.min(32767, (int)(p.age * 20.0F)))
               .setLight(p.light)
               .setNormal(0.0F, 1.0F, 0.0F)
               ;
         }
      }
   }

   private SpringSmokeTrail() {
   }

   record Point(Vec3 position, Vec3 direction, float width, float age, float alpha, float u, int light, float heat) {
      Point(Vec3 position, Vec3 direction, float width, float age, float alpha, float u, int light) {
         this(position, direction, width, age, alpha, u, light, 0.0F);
      }
   }
}
