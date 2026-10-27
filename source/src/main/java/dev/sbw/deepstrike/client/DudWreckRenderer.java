package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sbw.deepstrike.game.DudMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.WreckGeometry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class DudWreckRenderer {
   private static final EnumMap<MissileKind, List<DudWreckRenderer.Face>> MESHES = new EnumMap<>(MissileKind.class);

   private static List<DudWreckRenderer.Face> bake(MissileKind kind) {
      ModelRenderer.BakedModel model = ModelRenderer.bakeModel(ModelAssets.missile(kind));
      Matrix4f export = MissileGeometry.exportRotation(kind);
      ArrayList<DudWreckRenderer.Face> faces = new ArrayList<>();
      double slice = Math.max(0.16, MissileGeometry.canonicalBounds(kind).getXsize() / 26.0);

      for (ModelRenderer.BakedBone bone : model.bones) {
         for (ModelRenderer.BakedFace f : bone.staticFaces) {
            Vec3[] p = new Vec3[]{
               canonical(export, f.x0, f.y0, f.z0),
               canonical(export, f.x1, f.y1, f.z1),
               canonical(export, f.x2, f.y2, f.z2),
               canonical(export, f.x3, f.y3, f.z3)
            };
            float[] us = new float[]{f.u0, f.u1, f.u2, f.u3};
            float[] vs = new float[]{f.v0, f.v1, f.v2, f.v3};
            int nu = Math.max(
               1, Math.min(16, (int)Math.ceil(Math.max(Math.abs(p[1].x - p[0].x), Math.abs(p[2].x - p[3].x)) / slice))
            );
            int nv = Math.max(
               1, Math.min(16, (int)Math.ceil(Math.max(Math.abs(p[3].x - p[0].x), Math.abs(p[2].x - p[1].x)) / slice))
            );

            for (int y = 0; y < nv; y++) {
               for (int x = 0; x < nu; x++) {
                  double[][] at = new double[][]{
                     {(double)x / nu, (double)y / nv}, {(x + 1.0) / nu, (double)y / nv}, {(x + 1.0) / nu, (y + 1.0) / nv}, {(double)x / nu, (y + 1.0) / nv}
                  };
                  DudWreckRenderer.Vertex[] vertices = new DudWreckRenderer.Vertex[4];

                  for (int j = 0; j < 4; j++) {
                     double u = at[j][0];
                     double v = at[j][1];
                     Vec3 original = p[0].lerp(p[1], u).lerp(p[3].lerp(p[2], u), v);
                     vertices[j] = new DudWreckRenderer.Vertex(
                        WreckGeometry.deform(kind, bone.name, original), mix(us, u, v), mix(vs, u, v), WreckGeometry.soot(kind, original)
                     );
                  }

                  Vec3 normal = vertices[1].point.subtract(vertices[0].point).cross(vertices[3].point.subtract(vertices[0].point)).normalize();
                  faces.add(new DudWreckRenderer.Face(bone.name, vertices, normal));
               }
            }
         }
      }

      return faces;
   }

   private static Vec3 canonical(Matrix4f transform, float x, float y, float z) {
      return new Vec3(transform.transformPosition(new Vector3f(x, y, z)));
   }

   private static float mix(float[] p, double u, double v) {
      return (float)((p[0] * (1.0 - u) + p[1] * u) * (1.0 - v) + (p[3] * (1.0 - u) + p[2] * u) * v);
   }

   static AABB worldBounds(DudMissileEntity entity) {
      Quaternionf rotation = FlightOrientation.rotation(entity.getYRot(), entity.getXRot());
      Vec3 min = new Vec3(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
      Vec3 max = new Vec3(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);

      for (DudWreckRenderer.Face face : MESHES.computeIfAbsent(entity.kind(), DudWreckRenderer::bake)) {
         for (DudWreckRenderer.Vertex vertex : face.vertices) {
            Vec3 p = new Vec3(rotation.transform(new Vector3f((float)vertex.point.x, (float)vertex.point.y, (float)vertex.point.z)))
               .add(entity.position());
            min = new Vec3(Math.min(min.x, p.x), Math.min(min.y, p.y), Math.min(min.z, p.z));
            max = new Vec3(Math.max(max.x, p.x), Math.max(max.y, p.y), Math.max(max.z, p.z));
         }
      }

      return new AABB(min, max);
   }

   static void render(DudMissileEntity entity, PoseStack pose, MultiBufferSource buffers, int light) {
      pose.pushPose();
      pose.mulPose(FlightOrientation.rotation(entity.getYRot(), entity.getXRot()));
      ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/" + ModelAssets.texture(entity.kind(), entity.camo(), entity.graphite()) + ".png"
      );
      VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));

      for (DudWreckRenderer.Face face : MESHES.computeIfAbsent(entity.kind(), DudWreckRenderer::bake)) {
         if (entity.visibleBone(face.bone)) {
            for (DudWreckRenderer.Vertex vertex : face.vertices) {
               float gray = vertex.shade;
               out.addVertex(pose.last().pose(), (float)vertex.point.x, (float)vertex.point.y, (float)vertex.point.z)
                  .setColor(gray, gray * 0.985F, gray * 0.955F, 1.0F)
                  .setUv(vertex.u, vertex.v)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(pose.last(), (float)face.normal.x, (float)face.normal.y, (float)face.normal.z)
                  ;
            }
         }
      }

      pose.popPose();
   }

   private DudWreckRenderer() {
   }

   private record Face(String bone, DudWreckRenderer.Vertex[] vertices, Vec3 normal) {
   }

   private record Vertex(Vec3 point, float u, float v, float shade) {
   }
}
