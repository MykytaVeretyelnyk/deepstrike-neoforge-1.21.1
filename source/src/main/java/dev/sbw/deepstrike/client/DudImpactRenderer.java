package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sbw.deepstrike.game.DudMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

final class DudImpactRenderer {
   private static final Map<DudMissileEntity, DudImpactRenderer.Cache> CACHE = new WeakHashMap<>();

   private static Vec3 surface(DudMissileEntity e, double x, double z) {
      Vec3 hit = e.impactPoint();
      BlockPos pos = BlockPos.containing(x, hit.y, z);
      if (!e.level().hasChunkAt(pos)) {
         return null;
      } else {
         BlockHitResult ray = e.level()
            .clip(new ClipContext(new Vec3(x, hit.y + 1.1, z), new Vec3(x, hit.y - 2.5, z), Block.COLLIDER, Fluid.NONE, e));
         return ray.getType() == Type.MISS ? null : ray.getLocation();
      }
   }

   private static long stamp(DudMissileEntity e) {
      long hash = 1L;
      BlockPos b = BlockPos.containing(e.impactPoint());

      for (int x = -3; x <= 3; x++) {
         for (int z = -3; z <= 3; z++) {
            for (int y = -2; y <= 1; y++) {
               hash = hash * 31L + net.minecraft.world.level.block.Block.getId(e.level().getBlockState(b.offset(x, y, z)));
            }
         }
      }

      return hash;
   }

   private static TextureAtlasSprite sprite(BlockState state) {
      return Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state);
   }

   private static void face(List<DudImpactRenderer.Face> out, TextureAtlasSprite sprite, float shade, Vec3... p) {
      Vec3 normal = p[1].subtract(p[0]).cross(p[3].subtract(p[0])).normalize();
      if (normal.lengthSqr() > 0.5) {
         out.add(new DudImpactRenderer.Face(p, sprite, normal, shade));
      }
   }

   private static List<DudImpactRenderer.Face> bake(DudMissileEntity e) {
      List<DudImpactRenderer.Face> out = new ArrayList<>();
      Vec3 center = e.impactPoint();
      double radius = e.kind() == MissileKind.PEACEKEEPER ? 1.6 : 0.65;
      TextureAtlasSprite texture = sprite(e.material());
      int n = 32;
      Vec3[][] rings = new Vec3[3][n];

      for (int i = 0; i < n; i++) {
         double a = i * Math.PI * 2.0 / n;
         double variation = 1.0 + 0.08 * Math.sin(a * 3.0 + 0.7) + 0.04 * Math.cos(a * 7.0);

         for (int r = 0; r < 3; r++) {
            double dist = radius * (r == 0 ? 0.33 : (r == 1 ? 0.66 : 1.0)) * variation;
            Vec3 ground = surface(e, center.x + Math.cos(a) * dist, center.z + Math.sin(a) * dist);
            if (ground != null) {
               rings[r][i] = ground.add(0.0, r == 1 ? (0.07 + radius * 0.07) * (1.0 + 0.18 * Math.cos(a * 5.0)) : 0.006, 0.0);
            }
         }
      }

      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;

         for (int rx = 0; rx < 2; rx++) {
            if (rings[rx][i] != null && rings[rx][j] != null && rings[rx + 1][i] != null && rings[rx + 1][j] != null) {
               Vec3 a = rings[rx][i];
               Vec3 b = rings[rx][j];
               Vec3 c = rings[rx + 1][j];
               Vec3 d = rings[rx + 1][i];
               if (!(Math.max(Math.abs(a.y - d.y), Math.abs(b.y - c.y)) > 0.24)) {
                  face(out, texture, 0.88F, a, b, c, d);
                  face(out, texture, 0.72F, d, c, c.add(0.0, -0.04, 0.0), d.add(0.0, -0.04, 0.0));
                  face(out, texture, 0.72F, b, a, a.add(0.0, -0.04, 0.0), b.add(0.0, -0.04, 0.0));
                  face(out, texture, 0.65F, d.add(0.0, -0.04, 0.0), c.add(0.0, -0.04, 0.0), b.add(0.0, -0.04, 0.0), a.add(0.0, -0.04, 0.0));
               }
            }
         }
      }

      Random random = new Random(e.getUUID().getLeastSignificantBits());
      int count = e.kind() == MissileKind.PEACEKEEPER ? 42 : 24;
      int[][] sides = new int[][]{{0, 4, 6, 2}, {5, 1, 3, 7}, {0, 1, 5, 4}, {6, 7, 3, 2}, {1, 0, 2, 3}, {4, 5, 7, 6}};

      for (int i = 0; i < count; i++) {
         double a = random.nextDouble() * Math.PI * 2.0;
         double dist = radius * (0.5 + random.nextDouble() * 0.76);
         double x = center.x + Math.cos(a) * dist;
         double z = center.z + Math.sin(a) * dist;
         double width = (0.07 + random.nextDouble() * 0.15) * (radius > 1.0 ? 1.5 : 1.0);
         double depth = width * (0.65 + random.nextDouble() * 0.55);
         double height = width * (0.35 + random.nextDouble() * 0.45);
         double yaw = random.nextDouble() * Math.PI;
         Vec3[] p = new Vec3[8];
         double min = Double.POSITIVE_INFINITY;
         double max = Double.NEGATIVE_INFINITY;

         for (int c = 0; c < 4; c++) {
            double u = ((c & 1) == 0 ? -1 : 1) * width * 0.5;
            double v = ((c & 2) == 0 ? -1 : 1) * depth * 0.5;
            Vec3 foot = surface(e, x + u * Math.cos(yaw) - v * Math.sin(yaw), z + u * Math.sin(yaw) + v * Math.cos(yaw));
            if (foot == null) {
               break;
            }

            min = Math.min(min, foot.y);
            max = Math.max(max, foot.y);
            int low = c & 1 | (c & 2) << 1;
            p[low] = foot.add(0.0, -0.008, 0.0);
            p[low + 2] = foot.add(-u * 0.15, height * (0.8 + random.nextDouble() * 0.35), -v * 0.15);
         }

         if (p[7] != null && !(max - min > 0.3)) {
            BlockState material = e.level().getBlockState(BlockPos.containing(p[0].add(0.0, -0.04, 0.0)));
            TextureAtlasSprite spr = material.isAir() ? texture : sprite(material);

            for (int[] side : sides) {
               face(out, spr, (float)(0.83 + random.nextDouble() * 0.14), p[side[0]], p[side[1]], p[side[2]], p[side[3]]);
            }
         }
      }

      return out;
   }

   static void render(DudMissileEntity e, PoseStack pose, MultiBufferSource buffers, int light) {
      if (!e.material().isAir()) {
         long now = e.level().getGameTime();
         DudImpactRenderer.Cache cache = CACHE.get(e);
         if (cache == null || now - cache.checked >= 40L) {
            long hash = stamp(e);
            cache = new DudImpactRenderer.Cache(cache != null && cache.stamp == hash ? cache.faces : bake(e), hash, now);
            CACHE.put(e, cache);
         }

         VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));

         for (DudImpactRenderer.Face f : cache.faces) {
            for (int i = 0; i < 4; i++) {
               Vec3 p = f.p[i].subtract(e.position());
               float u = f.sprite.getU(i != 0 && i != 3 ? 0.75F : 0.125F);
               float v = f.sprite.getV(i < 2 ? 0.125F : 0.75F);
               vertices.addVertex(pose.last().pose(), (float)p.x, (float)p.y, (float)p.z)
                  .setColor(f.shade, f.shade, f.shade, 1.0F)
                  .setUv(u, v)
                  .setOverlay(OverlayTexture.NO_OVERLAY)
                  .setLight(light)
                  .setNormal(pose.last(), (float)f.normal.x, (float)f.normal.y, (float)f.normal.z)
                  ;
            }
         }
      }
   }

   private DudImpactRenderer() {
   }

   private record Cache(List<DudImpactRenderer.Face> faces, long stamp, long checked) {
   }

   private record Face(Vec3[] p, TextureAtlasSprite sprite, Vec3 normal, float shade) {
   }
}
