package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.registry.BFParticles;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

final class BlastParticle extends TextureSheetParticle {
   private final BlastParticle.Mode mode;
   private float size = 1.0F;
   private float tint = 1.0F;

   BlastParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, BlastParticle.Mode mode) {
      super(level, x, y, z, dx, dy, dz);
      this.mode = mode;
      this.xd = dx;
      this.yd = dy;
      this.zd = dz;
      this.hasPhysics = false;
      this.friction = mode == BlastParticle.Mode.EMBER ? 0.96F : 0.935F;

      this.lifetime = switch (mode) {
         case FLASH -> 14;
         case RING -> 26;
         case FIRE -> 38 + this.random.nextInt(14);
         case SMOKE -> 120 + this.random.nextInt(30);
         case DUST -> 180 + this.random.nextInt(80);
         case EMBER -> 65 + this.random.nextInt(35);
      };
      this.roll = this.random.nextFloat() * 6.283F;
      this.oRoll = this.roll;
      this.pickSprite(sprites);
      this.updateAppearance();
   }

   void configure(float scale, float shade) {
      this.configure(scale, shade, this.lifetime);
   }

   void configure(float scale, float shade, int life) {
      this.size = scale;
      this.tint = shade;
      this.setLifetime(Math.max(1, life));
      this.updateAppearance();
   }

   private void updateAppearance() {
      float t = Math.min(1.0F, (float)this.age / this.lifetime);
      switch (this.mode) {
         case FLASH:
            this.quadSize = this.size * (0.2F + 1.8F * (float)Math.exp(-4.2F * t));
            this.alpha = (float)Math.pow(1.0F - t, 2.2) * 0.98F;
            this.setColor(1.0F, 1.0F, 1.0F);
            break;
         case RING:
            this.quadSize = this.size * (0.3F + 4.2F * (float)Math.pow(t, 0.45));
            this.alpha = (float)Math.pow(1.0F - t, 1.5) * 0.75F;
            this.setColor(1.0F, 0.98F, 0.95F);
            break;
         case FIRE:
            this.quadSize = this.size * (0.65F + 2.4F * (float)Math.pow(t, 0.42));
            this.alpha = (1.0F - (float)Math.pow(t, 1.8)) * 0.92F;
            if (t < 0.22F) {
               float ft = t / 0.22F;
               this.setColor(1.0F, 1.0F - 0.08F * ft, 0.92F - 0.25F * ft);
            } else if (t < 0.62F) {
               float ft = (t - 0.22F) / 0.4F;
               this.setColor(1.0F, 0.92F - 0.35F * ft, 0.67F - 0.52F * ft);
            } else {
               float ft = (t - 0.62F) / 0.38F;
               this.setColor(1.0F - 0.35F * ft, 0.57F - 0.35F * ft, 0.15F - 0.08F * ft);
            }
            break;
         case SMOKE:
            this.quadSize = this.size * (0.65F + 3.4F * (float)Math.pow(t, 0.48));
            this.alpha = SmokeAppearance.alpha(t);
            float c = this.tint * (0.9F + t * 0.08F);
            this.setColor(c, c, c);
            break;
         case DUST:
            this.quadSize = this.size * (0.6F + 2.8F * (float)Math.pow(t, 0.42));
            this.alpha = 0.98F * SmokeAppearance.fade(t, 0.55F);
            this.setColor(0.58F * this.tint, 0.48F * this.tint, 0.38F * this.tint);
            break;
         case EMBER:
            this.quadSize = this.size * (1.0F - t * 0.65F);
            this.alpha = (float)Math.pow(1.0F - t, 1.2);
            this.setColor(1.0F, Math.max(0.12F, 0.85F - t * 0.9F), 0.05F);
      }
   }

   public void tick() {
      super.tick();
      if (this.mode == BlastParticle.Mode.SMOKE) {
         this.yd += 0.0028;
         this.xd = this.xd + (this.random.nextFloat() - 0.5F) * 6.0E-4;
         this.zd = this.zd + (this.random.nextFloat() - 0.5F) * 6.0E-4;
      }

      if (this.mode == BlastParticle.Mode.DUST) {
         this.yd += 4.0E-4;
      }

      if (this.mode == BlastParticle.Mode.EMBER) {
         this.yd -= 0.024;
      }

      this.updateAppearance();
   }

   protected int getLightColor(float partial) {
      if (this.mode == BlastParticle.Mode.RING || this.mode == BlastParticle.Mode.FLASH) {
         return 15728640;
      } else {
         return this.mode != BlastParticle.Mode.SMOKE && this.mode != BlastParticle.Mode.DUST ? 15728880 : SmokeAppearance.ambient(super.getLightColor(partial));
      }
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   static void register(RegisterParticleProvidersEvent e) {
      e.registerSpriteSet(
         (ParticleType)BFParticles.BLAST_FIRE.get(), s -> (p, l, x, y, z, dx, dy, dz) -> new BlastParticle(l, x, y, z, dx, dy, dz, s, BlastParticle.Mode.FIRE)
      );

      for (SimpleParticleType type : List.of((SimpleParticleType)BFParticles.BLAST_SMOKE.get(), (SimpleParticleType)BFParticles.SOOT_SMOKE.get())) {
         e.registerSpriteSet(
            type,
            s -> (p, l, x, y, z, dx, dy, dz) -> {
               ContrailRenderer.emitSmoke(
                  new Vec3(x, y, z), new Vec3(dx, dy + 0.06, dz).scale(20.0), 0.25F, l.random.nextInt(), p == BFParticles.SOOT_SMOKE.get()
               );
               return null;
            }
         );
      }

      e.registerSpriteSet(
         (ParticleType)BFParticles.BLAST_DUST.get(), s -> (p, l, x, y, z, dx, dy, dz) -> new BlastParticle(l, x, y, z, dx, dy, dz, s, BlastParticle.Mode.DUST)
      );
      e.registerSpriteSet(
         (ParticleType)BFParticles.BLAST_FLASH.get(),
         s -> (p, l, x, y, z, dx, dy, dz) -> new BlastParticle(l, x, y, z, dx, dy, dz, s, BlastParticle.Mode.FLASH)
      );
      e.registerSpriteSet(
         (ParticleType)BFParticles.BLAST_RING.get(), s -> (p, l, x, y, z, dx, dy, dz) -> new BlastParticle(l, x, y, z, dx, dy, dz, s, BlastParticle.Mode.RING)
      );
      e.registerSpriteSet(
         (ParticleType)BFParticles.BLAST_EMBER.get(),
         s -> (p, l, x, y, z, dx, dy, dz) -> new BlastParticle(l, x, y, z, dx, dy, dz, s, BlastParticle.Mode.EMBER)
      );
   }

   static enum Mode {
      FIRE,
      SMOKE,
      DUST,
      FLASH,
      RING,
      EMBER;
   }
}
