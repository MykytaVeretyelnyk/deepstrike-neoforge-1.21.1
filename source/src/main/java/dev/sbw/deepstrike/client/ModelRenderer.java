package dev.sbw.deepstrike.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sbw.deepstrike.model.ModelGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class ModelRenderer {
   private static final String[] FACES = new String[]{"west", "east", "down", "up", "north", "south"};
   private static final int[][] CORNERS = new int[][]{{0, 4, 6, 2}, {5, 1, 3, 7}, {0, 1, 5, 4}, {6, 7, 3, 2}, {1, 0, 2, 3}, {4, 5, 7, 6}};
   private static final Map<String, ModelRenderer.BakedModel> MODELS = new ConcurrentHashMap<>();
   private static final Map<String, RenderType> RENDER_TYPES = new ConcurrentHashMap<>();
   private static final ThreadLocal<Matrix4f> COMBINED_TEMP = ThreadLocal.withInitial(Matrix4f::new);
   private static final ThreadLocal<Matrix3f> NORMAL_TEMP = ThreadLocal.withInitial(Matrix3f::new);
   static boolean lateWorldPass;

   public static RenderType getRenderType(String texture) {
      return lateWorldPass
         ? DistantFlightRenderer.type(texture, false)
         : RENDER_TYPES.computeIfAbsent(texture, t -> RenderType.entityCutoutNoCull(ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/entity/" + t + ".png")));
   }

   public static ModelRenderer.BakedModel bakeModel(String name) {
      ModelGeometry model = ModelGeometry.get(name);
      Map<String, Matrix4f> defaultTransforms = model.defaultTransforms();
      List<ModelRenderer.BakedBone> bakedBones = new ArrayList<>(model.bones.size());
      Vector3f[] localVertices = new Vector3f[8];

      for (int i = 0; i < 8; i++) {
         localVertices[i] = new Vector3f();
      }

      for (ModelGeometry.Bone bone : model.bones) {
         List<ModelRenderer.BakedFace> localFaces = new ArrayList<>();
         List<ModelRenderer.BakedFace> staticFaces = new ArrayList<>();
         Matrix4f defaultBoneTransform = defaultTransforms.get(bone.name());
         if (defaultBoneTransform == null) {
            defaultBoneTransform = new Matrix4f();
         }

         for (ModelGeometry.Cube cube : bone.cubes()) {
            Matrix4f cubeTransform = cube.transform();

            for (int i = 0; i < 8; i++) {
               float var10001 = (i & 1) != 0 ? cube.size().x : 0.0F;
               float ox = cube.min().x + var10001;
               var10001 = (i & 2) != 0 ? cube.size().y : 0.0F;
               float oy = cube.min().y + var10001;
               var10001 = (i & 4) != 0 ? cube.size().z : 0.0F;
               float oz = cube.min().z + var10001;
               cubeTransform.transformPosition(ox, oy, oz, localVertices[i]);
            }

            for (int f = 0; f < 6; f++) {
               if ((f != 0 || !(cube.size().x < 1.0E-6F))
                  && (f != 2 || !(cube.size().y < 1.0E-6F))
                  && (f != 4 || !(cube.size().z < 1.0E-6F))
                  && cube.faces() != null
                  && cube.faces().has(FACES[f])) {
                  JsonObject uv = cube.faces().getAsJsonObject(FACES[f]);
                  JsonArray start = uv.getAsJsonArray("uv");
                  JsonArray size = uv.getAsJsonArray("uv_size");
                  float u = start.get(0).getAsFloat();
                  float v = start.get(1).getAsFloat();
                  float du = size.get(0).getAsFloat();
                  float dv = size.get(1).getAsFloat();
                  if (f == 2 || f == 3) {
                     u += du;
                     v += dv;
                     du = -du;
                     dv = -dv;
                  }

                  int[] ids = CORNERS[f];
                  Vector3f v0 = localVertices[ids[0]];
                  Vector3f v1 = localVertices[ids[1]];
                  Vector3f v2 = localVertices[ids[2]];
                  Vector3f v3 = localVertices[ids[3]];
                  Vector3f n = new Vector3f(v1).sub(v0).cross(new Vector3f(v2).sub(v0));
                  if (!(n.lengthSquared() < 1.0E-12)) {
                     n.normalize();
                     float tu0 = u / model.textureWidth;
                     float tv0 = (v + dv) / model.textureHeight;
                     float tu1 = (u + du) / model.textureWidth;
                     float tv1 = (v + dv) / model.textureHeight;
                     float tu2 = (u + du) / model.textureWidth;
                     float tv2 = v / model.textureHeight;
                     float tu3 = u / model.textureWidth;
                     float tv3 = v / model.textureHeight;
                     localFaces.add(
                        new ModelRenderer.BakedFace(
                           v0.x, v0.y, v0.z, tu0, tv0, v1.x, v1.y, v1.z, tu1, tv1, v2.x, v2.y, v2.z, tu2, tv2, v3.x, v3.y, v3.z, tu3, tv3, n.x, n.y, n.z
                        )
                     );
                     Vector3f s0 = defaultBoneTransform.transformPosition(new Vector3f(v0));
                     Vector3f s1 = defaultBoneTransform.transformPosition(new Vector3f(v1));
                     Vector3f s2 = defaultBoneTransform.transformPosition(new Vector3f(v2));
                     Vector3f s3 = defaultBoneTransform.transformPosition(new Vector3f(v3));
                     Vector3f sn = new Vector3f(s1).sub(s0).cross(new Vector3f(s2).sub(s0));
                     if (sn.lengthSquared() > 1.0E-12) {
                        sn.normalize();
                     } else {
                        sn.set(0.0F, 1.0F, 0.0F);
                     }

                     staticFaces.add(
                        new ModelRenderer.BakedFace(
                           s0.x, s0.y, s0.z, tu0, tv0, s1.x, s1.y, s1.z, tu1, tv1, s2.x, s2.y, s2.z, tu2, tv2, s3.x, s3.y, s3.z, tu3, tv3, sn.x, sn.y, sn.z
                        )
                     );
                  }
               }
            }
         }

         bakedBones.add(
            new ModelRenderer.BakedBone(bone.name(), localFaces.toArray(new ModelRenderer.BakedFace[0]), staticFaces.toArray(new ModelRenderer.BakedFace[0]))
         );
      }

      return new ModelRenderer.BakedModel(name, bakedBones.toArray(new ModelRenderer.BakedBone[0]));
   }

   public static void render(
      String name, String texture, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float yaw, float pitch, Predicate<String> visible
   ) {
      if (yaw == 0.0F && pitch == 0.0F) {
         renderStatic(name, texture, pose, buffers, light, overlay, visible);
      } else {
         ModelGeometry model = ModelGeometry.get(name);
         renderDynamic(name, texture, pose, buffers, light, overlay, model.transforms(yaw, pitch), visible);
      }
   }

   public static void render(
      String name,
      String texture,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay,
      Map<String, Matrix4f> transforms,
      Predicate<String> visible
   ) {
      renderDynamic(name, texture, pose, buffers, light, overlay, transforms, visible);
   }

   private static void renderStatic(String name, String texture, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Predicate<String> visible) {
      ModelRenderer.BakedModel model = MODELS.computeIfAbsent(name, ModelRenderer::bakeModel);
      Matrix4f poseMatrix = pose.last().pose();
      Matrix3f normalMatrix = NORMAL_TEMP.get();
      normalMatrix.set(poseMatrix);
      if (Math.abs(normalMatrix.determinant()) > 1.0E-6F) {
         normalMatrix.invert().transpose();
      }

      float m00 = poseMatrix.m00();
      float m01 = poseMatrix.m01();
      float m02 = poseMatrix.m02();
      float m10 = poseMatrix.m10();
      float m11 = poseMatrix.m11();
      float m12 = poseMatrix.m12();
      float m20 = poseMatrix.m20();
      float m21 = poseMatrix.m21();
      float m22 = poseMatrix.m22();
      float m30 = poseMatrix.m30();
      float m31 = poseMatrix.m31();
      float m32 = poseMatrix.m32();
      float n00 = normalMatrix.m00;
      float n01 = normalMatrix.m01;
      float n02 = normalMatrix.m02;
      float n10 = normalMatrix.m10;
      float n11 = normalMatrix.m11;
      float n12 = normalMatrix.m12;
      float n20 = normalMatrix.m20;
      float n21 = normalMatrix.m21;
      float n22 = normalMatrix.m22;
      VertexConsumer out = buffers.getBuffer(getRenderType(texture));

      for (ModelRenderer.BakedBone bone : model.bones) {
         if (bone.staticFaces.length != 0 && visible.test(bone.name)) {
            streamFaces(
               bone.staticFaces, out, m00, m01, m02, m10, m11, m12, m20, m21, m22, m30, m31, m32, n00, n01, n02, n10, n11, n12, n20, n21, n22, light, overlay
            );
         }
      }
   }

   private static void renderDynamic(
      String name,
      String texture,
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay,
      Map<String, Matrix4f> transforms,
      Predicate<String> visible
   ) {
      ModelRenderer.BakedModel model = MODELS.computeIfAbsent(name, ModelRenderer::bakeModel);
      Matrix4f poseMatrix = pose.last().pose();
      Matrix4f combined = COMBINED_TEMP.get();
      Matrix3f normalMatrix = NORMAL_TEMP.get();
      VertexConsumer out = buffers.getBuffer(getRenderType(texture));

      for (ModelRenderer.BakedBone bone : model.bones) {
         if (bone.localFaces.length != 0 && visible.test(bone.name)) {
            Matrix4f boneTransform = transforms.get(bone.name);
            if (boneTransform != null) {
               poseMatrix.mul(boneTransform, combined);
            } else {
               combined.set(poseMatrix);
            }

            normalMatrix.set(combined);
            if (Math.abs(normalMatrix.determinant()) > 1.0E-6F) {
               normalMatrix.invert().transpose();
            }

            float m00 = combined.m00();
            float m01 = combined.m01();
            float m02 = combined.m02();
            float m10 = combined.m10();
            float m11 = combined.m11();
            float m12 = combined.m12();
            float m20 = combined.m20();
            float m21 = combined.m21();
            float m22 = combined.m22();
            float m30 = combined.m30();
            float m31 = combined.m31();
            float m32 = combined.m32();
            float n00 = normalMatrix.m00;
            float n01 = normalMatrix.m01;
            float n02 = normalMatrix.m02;
            float n10 = normalMatrix.m10;
            float n11 = normalMatrix.m11;
            float n12 = normalMatrix.m12;
            float n20 = normalMatrix.m20;
            float n21 = normalMatrix.m21;
            float n22 = normalMatrix.m22;
            streamFaces(
               bone.localFaces, out, m00, m01, m02, m10, m11, m12, m20, m21, m22, m30, m31, m32, n00, n01, n02, n10, n11, n12, n20, n21, n22, light, overlay
            );
         }
      }
   }

   private static void streamFaces(
      ModelRenderer.BakedFace[] faces,
      VertexConsumer out,
      float m00,
      float m01,
      float m02,
      float m10,
      float m11,
      float m12,
      float m20,
      float m21,
      float m22,
      float m30,
      float m31,
      float m32,
      float n00,
      float n01,
      float n02,
      float n10,
      float n11,
      float n12,
      float n20,
      float n21,
      float n22,
      int light,
      int overlay
   ) {
      for (int idx = 0; idx < faces.length; idx++) {
         ModelRenderer.BakedFace face = faces[idx];
         float nx = n00 * face.nx + n10 * face.ny + n20 * face.nz;
         float ny = n01 * face.nx + n11 * face.ny + n21 * face.nz;
         float nz = n02 * face.nx + n12 * face.ny + n22 * face.nz;
         float invLen = (float)(1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz));
         if (Float.isFinite(invLen)) {
            nx *= invLen;
            ny *= invLen;
            nz *= invLen;
         }

         float x0 = m00 * face.x0 + m10 * face.y0 + m20 * face.z0 + m30;
         float y0 = m01 * face.x0 + m11 * face.y0 + m21 * face.z0 + m31;
         float z0 = m02 * face.x0 + m12 * face.y0 + m22 * face.z0 + m32;
         out.addVertex(x0, y0, z0).setColor(255, 255, 255, 255).setUv(face.u0, face.v0).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
         float x1 = m00 * face.x1 + m10 * face.y1 + m20 * face.z1 + m30;
         float y1 = m01 * face.x1 + m11 * face.y1 + m21 * face.z1 + m31;
         float z1 = m02 * face.x1 + m12 * face.y1 + m22 * face.z1 + m32;
         out.addVertex(x1, y1, z1).setColor(255, 255, 255, 255).setUv(face.u1, face.v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
         float x2 = m00 * face.x2 + m10 * face.y2 + m20 * face.z2 + m30;
         float y2 = m01 * face.x2 + m11 * face.y2 + m21 * face.z2 + m31;
         float z2 = m02 * face.x2 + m12 * face.y2 + m22 * face.z2 + m32;
         out.addVertex(x2, y2, z2).setColor(255, 255, 255, 255).setUv(face.u2, face.v2).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
         float x3 = m00 * face.x3 + m10 * face.y3 + m20 * face.z3 + m30;
         float y3 = m01 * face.x3 + m11 * face.y3 + m21 * face.z3 + m31;
         float z3 = m02 * face.x3 + m12 * face.y3 + m22 * face.z3 + m32;
         out.addVertex(x3, y3, z3).setColor(255, 255, 255, 255).setUv(face.u3, face.v3).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      }
   }

   private ModelRenderer() {
   }

   public static final class BakedBone {
      public final String name;
      public final ModelRenderer.BakedFace[] localFaces;
      public final ModelRenderer.BakedFace[] staticFaces;

      public BakedBone(String name, ModelRenderer.BakedFace[] localFaces, ModelRenderer.BakedFace[] staticFaces) {
         this.name = name;
         this.localFaces = localFaces;
         this.staticFaces = staticFaces;
      }
   }

   public static final class BakedFace {
      public final float x0;
      public final float y0;
      public final float z0;
      public final float u0;
      public final float v0;
      public final float x1;
      public final float y1;
      public final float z1;
      public final float u1;
      public final float v1;
      public final float x2;
      public final float y2;
      public final float z2;
      public final float u2;
      public final float v2;
      public final float x3;
      public final float y3;
      public final float z3;
      public final float u3;
      public final float v3;
      public final float nx;
      public final float ny;
      public final float nz;

      public BakedFace(
         float x0,
         float y0,
         float z0,
         float u0,
         float v0,
         float x1,
         float y1,
         float z1,
         float u1,
         float v1,
         float x2,
         float y2,
         float z2,
         float u2,
         float v2,
         float x3,
         float y3,
         float z3,
         float u3,
         float v3,
         float nx,
         float ny,
         float nz
      ) {
         this.x0 = x0;
         this.y0 = y0;
         this.z0 = z0;
         this.u0 = u0;
         this.v0 = v0;
         this.x1 = x1;
         this.y1 = y1;
         this.z1 = z1;
         this.u1 = u1;
         this.v1 = v1;
         this.x2 = x2;
         this.y2 = y2;
         this.z2 = z2;
         this.u2 = u2;
         this.v2 = v2;
         this.x3 = x3;
         this.y3 = y3;
         this.z3 = z3;
         this.u3 = u3;
         this.v3 = v3;
         this.nx = nx;
         this.ny = ny;
         this.nz = nz;
      }
   }

   public static final class BakedModel {
      public final String name;
      public final ModelRenderer.BakedBone[] bones;

      public BakedModel(String name, ModelRenderer.BakedBone[] bones) {
         this.name = name;
         this.bones = bones;
      }
   }
}
