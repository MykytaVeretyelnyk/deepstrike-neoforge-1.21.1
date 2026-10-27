package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class GroundLightRenderer {
   private static ShaderInstance shader;
   private static final int[][] CORNERS = new int[][]{{4, 5, 1, 0}, {2, 3, 7, 6}, {0, 1, 3, 2}, {5, 4, 6, 7}, {4, 0, 2, 6}, {1, 5, 7, 3}};

   static void register(RegisterShadersEvent event) {
      try {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath("deepstrike", "ground_light"), DefaultVertexFormat.POSITION_COLOR_NORMAL), s -> shader = s
         );
      } catch (IOException var2) {
         throw new IllegalStateException("Cannot load ground-light gradient", var2);
      }
   }

   static void draw(PoseStack pose, List<FlightLighting.Face> faces, FlightLighting.Glow glow, Vec3 camera) {
      if (shader != null && !faces.isEmpty()) {
         Matrix4f matrix = pose.last().pose();
         Matrix3f normalMatrix = matrix.normal(new Matrix3f());
         Vec3 from = glow.position().subtract(camera);
         Vec3 to = (glow.end() == null ? glow.position() : glow.end()).subtract(camera);
         Vector3f source = matrix.transformPosition(new Vector3f((float)from.x, (float)from.y, (float)from.z));
         Vector3f end = matrix.transformPosition(new Vector3f((float)to.x, (float)to.y, (float)to.z));
         shader.safeGetUniform("Source").set(source.x, source.y, source.z);
         shader.safeGetUniform("EmitterEnd").set(end.x, end.y, end.z);
         shader.safeGetUniform("LightRadius").set(glow.radius());
         shader.safeGetUniform("Strength").set(glow.strength());
         float[] rgb = glow.rgb();
         shader.safeGetUniform("LightColor").set(rgb == null ? 1.0F : rgb[0], rgb == null ? 0.6F : rgb[1], rgb == null ? 0.25F : rgb[2]);
         ShaderInstance previous = RenderSystem.getShader();
         BufferBuilder bb = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_NORMAL);

         for (FlightLighting.Face face : faces) {
            AABB box = face.box();
            Vec3i normal = face.normal().getNormal();

            for (int corner : CORNERS[face.normal().ordinal()]) {
               double x = ((corner & 1) == 0 ? box.minX : box.maxX) + normal.getX() * 0.004;
               double y = ((corner & 2) == 0 ? box.minY : box.maxY) + normal.getY() * 0.004;
               double z = ((corner & 4) == 0 ? box.minZ : box.maxZ) + normal.getZ() * 0.004;
               float shadow = FlightLighting.transmission(glow, new Vec3(x, y, z));
               bb.addVertex(matrix, (float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
                  .setColor(1.0F, 1.0F, 1.0F, shadow)
                  .setNormal(pose.last(), normal.getX(), normal.getY(), normal.getZ())
                  ;
            }
         }

         RenderSystem.setShader(() -> shader);
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(770, 1);
         RenderSystem.enableDepthTest();
         RenderSystem.depthMask(false);
         RenderSystem.disableCull();

         try {
            dev.sbw.deepstrike.compat.RenderCompat.draw(bb);
         } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.setShader(() -> previous);
         }
      }
   }

   private GroundLightRenderer() {
   }
}
