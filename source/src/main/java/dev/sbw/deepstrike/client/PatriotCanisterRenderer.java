package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.model.PatriotCanisterLayout;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

final class PatriotCanisterRenderer {
   private static final ResourceLocation MEMBRANE = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/patriot_canister_membrane.png");
   private static final float HALF_WIDTH = 0.5155F;
   private static final float HALF_HEIGHT = 0.478F;

   static void render(PoseStack pose, MultiBufferSource buffers, GeoBone launcher, PatriotTruckEntity truck, float partial, int light, int overlay) {
      pose.pushPose();

      try {
         RenderUtil.prepMatrixForBone(pose, launcher);

         for (int cell = 0; cell < 4; cell++) {
            Vec3 mouth = PatriotCanisterLayout.mouth(cell);
            int type = truck.loadedCellType(cell);
            if (type >= 0) {
               pose.pushPose();

               try {
                  pose.translate(-mouth.x, mouth.y, mouth.z + PatriotCanisterLayout.nose(type) + 0.08);
                  pose.mulPose(ModelGeometry.flight(180.0F, 0.0F));
                  pose.scale(InterceptorGeometry.axialScale(type), InterceptorGeometry.radialScale(type), InterceptorGeometry.radialScale(type));
                  String model = InterceptorGeometry.model(type);
                  ModelRenderer.render(model, model, pose, buffers, light, overlay, 0.0F, 0.0F, b -> true);
               } finally {
                  pose.popPose();
               }
            }

            if (type >= 0 || truck.tornCell(cell)) {
               float progress = truck.cellOpening(cell, partial);
               float angle = (float)Math.toRadians(86.0) * progress * progress * (3.0F - 2.0F * progress);
               pose.pushPose();

               try {
                  pose.translate(-mouth.x, mouth.y, mouth.z - 0.012);
                  VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(MEMBRANE));
                  pose.scale(1.132967F, 1.0505494F, 1.0F);

                  for (int side = 0; side < 4; side++) {
                     pose.pushPose();
                     pose.mulPose(Axis.ZP.rotationDegrees(90 * side));
                     sealWall(out, pose, light, overlay);
                     triangle(out, pose, -0.463F, -0.463F, 0.463F, -0.463F, 0.455F, -0.432F, 0.48F, light, overlay);
                     triangle(out, pose, -0.463F, -0.463F, 0.455F, -0.432F, -0.455F, -0.432F, 0.48F, light, overlay);
                     pose.popPose();
                  }

                  for (int petal = 0; petal < 4; petal++) {
                     pose.pushPose();

                     try {
                        pose.mulPose(Axis.ZP.rotationDegrees(90 * petal));
                        pose.translate(0.0, -0.455, 0.0);
                        pose.mulPose(Axis.XP.rotation(-angle * (0.9F + 0.035F * petal)));
                        float shade = truck.tornCell(cell) ? 0.76F : 1.0F;
                        if (progress == 0.0F) {
                           triangle(out, pose, -0.455F, 0.0F, 0.455F, 0.0F, 0.0F, 0.455F, shade, light, overlay);
                        } else {
                           float[] xs = new float[]{-0.453F, -0.34F, -0.29F, -0.19F, -0.15F, -0.045F, 0.018F, 0.08F, 0.19F, 0.23F, 0.36F, 0.453F};
                           float[] ys = new float[]{0.0F, 0.095F, 0.072F, 0.23F, 0.17F, 0.36F, 0.3F, 0.35F, 0.18F, 0.22F, 0.065F, 0.0F};

                           for (int i = 0; i < xs.length - 1; i++) {
                              triangle(out, pose, 0.0F, 0.0F, xs[i], ys[i], xs[i + 1], ys[i + 1], shade, light, overlay);
                           }
                        }
                     } finally {
                        pose.popPose();
                     }
                  }
               } finally {
                  pose.popPose();
               }
            }
         }
      } finally {
         pose.popPose();
      }
   }

   private static void triangle(
      VertexConsumer out, PoseStack pose, float ax, float ay, float bx, float by, float cx, float cy, float shade, int light, int overlay
   ) {
      vertex(out, pose, ax, ay, ax / 0.91F + 0.5F, 1.0F - ay / 0.91F, shade, light, overlay);
      vertex(out, pose, bx, by, bx / 0.91F + 0.5F, 1.0F - by / 0.91F, shade, light, overlay);
      vertex(out, pose, cx, cy, cx / 0.91F + 0.5F, 1.0F - cy / 0.91F, shade, light, overlay);
      vertex(out, pose, cx, cy, cx / 0.91F + 0.5F, 1.0F - cy / 0.91F, shade, light, overlay);
   }

   private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float u, float v, float shade, int light, int overlay) {
      out.addVertex(pose.last().pose(), x, y, 0.0F)
         .setColor(shade, shade * 0.99F, shade * 0.83F, 1.0F)
         .setUv(u, v)
         .setOverlay(overlay)
         .setLight(light)
         .setNormal(pose.last(), 0.0F, 0.0F, -1.0F)
         ;
   }

   private static void sealWall(VertexConsumer out, PoseStack pose, int light, int overlay) {
      float[][] corners = new float[][]{
         {-0.463F, -0.463F, 0.0F, 0.0F, 0.0F},
         {0.463F, -0.463F, 0.0F, 1.0F, 0.0F},
         {0.463F, -0.463F, 0.067F, 1.0F, 0.045F},
         {-0.463F, -0.463F, 0.067F, 0.0F, 0.045F}
      };

      for (float[] p : corners) {
         out.addVertex(pose.last().pose(), p[0], p[1], p[2])
            .setColor(0.65F, 0.65F, 0.65F, 1.0F)
            .setUv(p[3], p[4])
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(pose.last(), 0.0F, -1.0F, 0.0F)
            ;
      }
   }

   private PatriotCanisterRenderer() {
   }
}
