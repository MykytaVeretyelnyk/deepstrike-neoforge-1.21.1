package dev.sbw.deepstrike.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class SmokeAppearance {
   static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/zerok/clouds.png");
   static final ResourceLocation CLOUD = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/zerok/smoke.png");

   static float fade(float life, float start) {
      float t = Mth.clamp((life - start) / (1.0F - start), 0.0F, 1.0F);
      return 1.0F - (float)Mth.smoothstep(t);
   }

   static float dissipation(float life) {
      float t = Mth.clamp(life, 0.0F, 1.0F);
      double end = Math.exp(-4.0);
      return (float)((Math.exp(-4.0F * t) - end) / (1.0 - end));
   }

   static float alpha(float life) {
      return 0.98F * fade(life, 0.65F);
   }

   static int ambient(int light) {
      return Math.max(32, light & 65535) | Math.max(80, light >>> 16 & 65535) << 16;
   }

   static int light(ClientLevel level, Vec3 position) {
      BlockPos block = BlockPos.containing(position);
      return level != null && level.dimensionType().hasSkyLight() && block.getY() >= level.getMaxBuildHeight()
         ? ambient(15728640)
         : ambient(level != null && level.hasChunkAt(block) ? LevelRenderer.getLightColor(level, block) : 10485760);
   }

   private SmokeAppearance() {
   }
}
