package dev.sbw.deepstrike.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

final class Fp5LaunchParticle extends TextureSheetParticle {
   private final float start;

   Fp5LaunchParticle(ClientLevel world, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
      super(world, x, y, z, dx, dy, dz);
      this.xd = dx;
      this.yd = dy;
      this.zd = dz;
      this.hasPhysics = true;
      this.friction = 0.94F;
      this.lifetime = 150 + this.random.nextInt(90);
      this.start = 0.45F + this.random.nextFloat() * 0.3F;
      this.quadSize = this.start;
      this.setColor(0.98F, 0.98F, 0.97F);
      this.alpha = 0.55F;
      this.pickSprite(sprites);
      this.roll = this.random.nextFloat() * 6.283185F;
      this.oRoll = this.roll;
   }

   public void tick() {
      super.tick();
      float life = (float)this.age / this.lifetime;
      this.quadSize = this.start * (1.0F + 3.0F * (float)Math.sqrt(life));
      this.alpha = 0.55F * SmokeAppearance.fade(life, 0.5F);
      this.yd += 7.0E-4;
   }

   public int getLightColor(float partial) {
      return SmokeAppearance.ambient(super.getLightColor(partial));
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }
}
