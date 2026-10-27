package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.registry.BFParticles;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

final class ExhaustParticle extends TextureSheetParticle {
   private final float initialSize;
   private final boolean launch;
   private final float sizeScale;
   private final float flowPhase;

   ExhaustParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean dense, boolean launch) {
      super(level, x, y, z, dx, dy, dz);
      this.launch = launch;
      this.flowPhase = this.random.nextFloat() * 6.283185F;
      this.hasPhysics = launch;
      this.friction = launch ? 0.88F : 0.98F;
      double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (launch) {
         this.sizeScale = (float)Math.max(0.8, speed > 0.3 ? speed : 1.0);
         this.xd = dx;
         this.yd = dy;
         this.zd = dz;
         this.initialSize = (0.75F + this.random.nextFloat() * 0.45F) * Math.min(this.sizeScale, 2.2F);
      } else if (dense) {
         this.sizeScale = (float)Math.max(0.5, speed > 0.2 ? speed : 1.0);
         this.xd = dx * 0.3;
         this.yd = dy * 0.3;
         this.zd = dz * 0.3;
         this.initialSize = 0.3F * this.sizeScale;
      } else {
         float extractedScale = speed > 0.2 ? (float)speed : 1.0F;
         this.sizeScale = Math.max(0.25F, Math.min(6.0F, extractedScale));
         this.xd = (this.random.nextDouble() - 0.5) * 0.004;
         this.yd = (this.random.nextDouble() - 0.5) * 0.004;
         this.zd = (this.random.nextDouble() - 0.5) * 0.004;
         this.initialSize = 0.22F * this.sizeScale;
      }

      this.lifetime = launch ? 180 + this.random.nextInt(100) : (dense ? 45 + this.random.nextInt(25) : 95 + this.random.nextInt(40));
      this.quadSize = this.initialSize;
      this.alpha = SmokeAppearance.alpha(0.0F);
      float baseGray = launch ? 0.92F + this.random.nextFloat() * 0.05F : 0.95F + this.random.nextFloat() * 0.04F;
      this.setColor(baseGray, baseGray, baseGray);
      this.pickSprite(sprites);
      this.roll = this.random.nextFloat() * 6.283185F;
      this.oRoll = this.roll;
   }

   public void tick() {
      super.tick();
      if (this.isAlive()) {
         float life = (float)this.age / this.lifetime;
         float growth = Math.min(1.0F, life / (this.launch ? 0.7F : 0.75F));
         this.quadSize = this.initialSize * (1.0F + (this.launch ? 3.4F : 3.8F) * (float)Math.pow(growth, this.launch ? 0.4 : 0.38));
         this.alpha = SmokeAppearance.alpha(life);
         this.oRoll = this.roll;
         this.roll = this.roll + 8.0E-4F * (float)Math.sin(this.flowPhase + this.age * 0.025);
         this.xd = this.xd + (7.0E-4 + Math.sin(this.flowPhase + this.age * 0.027) * 3.5E-4);
         this.zd = this.zd + (2.5E-4 + Math.cos(this.flowPhase + this.age * 0.021) * 3.5E-4);
         if (this.launch && this.onGround) {
            this.yd = 4.0E-4;
            this.xd *= 0.9;
            this.zd *= 0.9;
         } else {
            this.yd = this.yd + (this.launch ? 5.0E-4 : 3.0E-4) * (1.0F - life);
         }
      }
   }

   public int getLightColor(float partialTick) {
      return SmokeAppearance.ambient(super.getLightColor(partialTick));
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }

   static void register(RegisterParticleProvidersEvent event) {
      for (SimpleParticleType type : List.of(
         (SimpleParticleType)BFParticles.FP5_LAUNCH_SMOKE.get(),
         (SimpleParticleType)BFParticles.EXHAUST.get(),
         (SimpleParticleType)BFParticles.VAPOR.get(),
         (SimpleParticleType)BFParticles.LAUNCH_SMOKE.get()
      )) {
         event.registerSpriteSet(type, sprites -> (particle, level, x, y, z, dx, dy, dz) -> {
            boolean launch = particle == BFParticles.LAUNCH_SMOKE.get() || particle == BFParticles.FP5_LAUNCH_SMOKE.get();
            ContrailRenderer.emitSmoke(new Vec3(x, y, z), new Vec3(dx, dy, dz).scale(20.0), launch ? 0.4F : 0.1F, level.random.nextInt());
            return null;
         });
      }
   }
}
