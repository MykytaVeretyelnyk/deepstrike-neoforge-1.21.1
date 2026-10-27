package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class McheliModel {
   private static final Map<String, McheliModel> CACHE = new HashMap<>();
   private final Map<String, McheliModel.MeshPart> parts = new LinkedHashMap<>();

   public static McheliModel get(String name) {
      return CACHE.computeIfAbsent(name, McheliModel::load);
   }

   private static McheliModel load(String name) {
      McheliModel model = new McheliModel();
      ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("deepstrike", "models/" + name + ".bin");

      try (
         InputStream raw = Minecraft.getInstance().getResourceManager().open(loc);
         DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
      ) {
         int numParts = in.readInt();

         for (int p = 0; p < numParts; p++) {
            int nameLen = in.readUnsignedShort();
            byte[] nameBytes = new byte[nameLen];
            in.readFully(nameBytes);
            String partName = new String(nameBytes, StandardCharsets.UTF_8);
            int numQuads = in.readInt();
            List<McheliModel.Quad> quads = new ArrayList<>(numQuads);
            float[] xs = new float[4];
            float[] ys = new float[4];
            float[] zs = new float[4];
            float[] us = new float[4];
            float[] vs = new float[4];

            for (int q = 0; q < numQuads; q++) {
               for (int i = 0; i < 4; i++) {
                  xs[i] = in.readFloat();
                  ys[i] = in.readFloat();
                  zs[i] = in.readFloat();
               }

               for (int i = 0; i < 4; i++) {
                  us[i] = in.readFloat();
                  vs[i] = in.readFloat();
               }

               float nx = in.readFloat();
               float ny = in.readFloat();
               float nz = in.readFloat();
               quads.add(new McheliModel.Quad(xs, ys, zs, us, vs, nx, ny, nz));
            }

            model.parts.put(partName, new McheliModel.MeshPart(partName, quads));
         }
      } catch (Exception var25) {
         var25.printStackTrace();
      }

      return model;
   }

   public McheliModel.MeshPart getPart(String name) {
      return this.parts.get(name);
   }

   public Collection<McheliModel.MeshPart> getAllParts() {
      return this.parts.values();
   }

   public void renderAll(PoseStack pose, VertexConsumer consumer, int packedLight, int packedOverlay, float r, float g, float b, float a) {
      for (McheliModel.MeshPart part : this.parts.values()) {
         part.render(pose, consumer, packedLight, packedOverlay, r, g, b, a);
      }
   }

   public static final class MeshPart {
      public final String name;
      public final List<McheliModel.Quad> quads;

      public MeshPart(String name, List<McheliModel.Quad> quads) {
         this.name = name;
         this.quads = quads;
      }

      public void render(PoseStack pose, VertexConsumer consumer, int packedLight, int packedOverlay, float r, float g, float b, float a) {
         Matrix4f mat = pose.last().pose();
         Matrix3f norm = pose.last().normal();

         for (McheliModel.Quad q : this.quads) {
            float nx = norm.m00() * q.nx + norm.m10() * q.ny + norm.m20() * q.nz;
            float ny = norm.m01() * q.nx + norm.m11() * q.ny + norm.m21() * q.nz;
            float nz = norm.m02() * q.nx + norm.m12() * q.ny + norm.m22() * q.nz;

            for (int i = 0; i < 4; i++) {
               consumer.addVertex(mat, q.x[i], q.y[i], q.z[i])
                  .setColor(r, g, b, a)
                  .setUv(q.u[i], q.v[i])
                  .setOverlay(packedOverlay)
                  .setLight(packedLight)
                  .setNormal(nx, ny, nz)
                  ;
            }
         }
      }
   }

   public static final class Quad {
      public final float[] x = new float[4];
      public final float[] y = new float[4];
      public final float[] z = new float[4];
      public final float[] u = new float[4];
      public final float[] v = new float[4];
      public final float nx;
      public final float ny;
      public final float nz;

      public Quad(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float nx, float ny, float nz) {
         System.arraycopy(xs, 0, this.x, 0, 4);
         System.arraycopy(ys, 0, this.y, 0, 4);
         System.arraycopy(zs, 0, this.z, 0, 4);
         System.arraycopy(us, 0, this.u, 0, 4);
         System.arraycopy(vs, 0, this.v, 0, 4);
         this.nx = nx;
         this.ny = ny;
         this.nz = nz;
      }
   }
}
