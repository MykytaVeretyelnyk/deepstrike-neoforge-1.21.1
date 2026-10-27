package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFEntities;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class Fp5BoosterEntity extends Entity {
   private static final Map<UUID, Fp5BoosterEntity> RELEASED = new ConcurrentHashMap<>();
   public static final Vec3 CENTER = new Vec3(-0.6109375, -0.44625, 0.0);
   private static final EntityDataAccessor<String> CAMO = SynchedEntityData.defineId(Fp5BoosterEntity.class, EntityDataSerializers.STRING);
   private long expiresAt;
   private int steps;
   private double tx;
   private double ty;
   private double tz;
   private float yaw;
   private float pitch;
   public float roll;
   public float rollO;
   public float pitchWobble;
   public float pitchWobbleO;
   private final float swaySeed = (float)(this.random.nextDouble() * Math.PI * 2.0);

   public static Fp5BoosterEntity releasedFrom(GuidedMissileEntity missile) {
      return RELEASED.get(missile.getUUID());
   }

   public static void forget(GuidedMissileEntity missile) {
      RELEASED.remove(missile.getUUID());
   }

   public Fp5BoosterEntity(EntityType<? extends Fp5BoosterEntity> type, Level level) {
      super(type, level);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      builder.define(CAMO, "olive");
   }

   public String camo() {
      return (String)this.entityData.get(CAMO);
   }

   public void setCamo(String camo) {
      this.entityData.set(CAMO, camo != null ? camo : "olive");
   }

   public float getRoll(float partial) {
      return Mth.lerp(partial, this.rollO, this.roll);
   }

   public float getPitchWobble(float partial) {
      return Mth.lerp(partial, this.pitchWobbleO, this.pitchWobble);
   }

   public static void release(GuidedMissileEntity missile) {
      Fp5BoosterEntity part = new Fp5BoosterEntity((EntityType<? extends Fp5BoosterEntity>)BFEntities.FP5_BOOSTER.get(), missile.level());
      Vector3f offset = MissileGeometry.flight(MissileKind.FP5, missile.getYRot(), missile.getXRot())
         .transformPosition(new Vector3f((float)CENTER.x, (float)CENTER.y, 0.0F));
      part.moveTo(missile.getX() + offset.x, missile.getY() + offset.y, missile.getZ() + offset.z, missile.getYRot(), missile.getXRot());
      part.entityData.set(CAMO, missile.graphite() ? "graphite" : missile.camo());
      Vec3 vel = missile.getDeltaMovement();
      part.setDeltaMovement(vel);
      part.expiresAt = missile.level().getGameTime() + 300L;
      RELEASED.put(missile.getUUID(), part);
      part.emitSeparationEffects(missile.getLookAngle());
      missile.level().addFreshEntity(part);
      if (!missile.level().isClientSide()) {
         DeepStrikeNetwork.broadcastBoosterRelease(missile, part);
      }
   }

   public void emitSeparationEffects(Vec3 lookAngle) {
      if (this.level() instanceof ServerLevel server) {
         Vec3 dir = lookAngle.lengthSqr() > 1.0E-4 ? lookAngle.normalize() : new Vec3(0.0, 0.0, -1.0);
         server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY(), this.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
         server.sendParticles(
            ParticleTypes.POOF,
            this.getX() - dir.x * 0.4,
            this.getY() - 0.2,
            this.getZ() - dir.z * 0.4,
            4,
            0.15,
            0.1,
            0.15,
            0.03
         );
         this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 1.4F, 1.8F);
      } else if (this.level().isClientSide) {
         Vec3 dir = lookAngle.lengthSqr() > 1.0E-4 ? lookAngle.normalize() : new Vec3(0.0, 0.0, -1.0);

         for (int i = 0; i < 5; i++) {
            this.level()
               .addParticle(
                  ParticleTypes.CAMPFIRE_COSY_SMOKE,
                  this.getX() + (this.random.nextDouble() - 0.5) * 0.3,
                  this.getY() + (this.random.nextDouble() - 0.5) * 0.2,
                  this.getZ() + (this.random.nextDouble() - 0.5) * 0.3,
                  -dir.x * 0.05,
                  -0.02,
                  -dir.z * 0.05
               );
         }

         this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.NEUTRAL, 1.2F, 1.8F, false);
      }
   }

   public void onSeparatedClient() {
      this.emitSeparationEffects(this.getLookAngle());
   }

   // 1.21 re-sends these as the target when a move packet carries only rotation (or only position).
   @Override
   public double lerpTargetX() {
      return this.steps > 0 ? this.tx : this.getX();
   }

   @Override
   public double lerpTargetY() {
      return this.steps > 0 ? this.ty : this.getY();
   }

   @Override
   public double lerpTargetZ() {
      return this.steps > 0 ? this.tz : this.getZ();
   }

   @Override
   public float lerpTargetYRot() {
      return this.steps > 0 ? this.yaw : this.getYRot();
   }

   @Override
   public float lerpTargetXRot() {
      return this.steps > 0 ? this.pitch : this.getXRot();
   }

   public void lerpTo(double x, double y, double z, float yrot, float xrot, int n) {
      boolean teleport = false;
      this.tx = x;
      this.ty = y;
      this.tz = z;
      this.yaw = yrot;
      this.pitch = xrot;
      this.steps = Math.max(1, n);
   }

   public void tick() {
      super.tick();
      this.rollO = this.roll;
      this.pitchWobbleO = this.pitchWobble;
      if (this.level().isClientSide) {
         if (this.steps > 0) {
            this.setPos(
               this.getX() + (this.tx - this.getX()) / this.steps,
               this.getY() + (this.ty - this.getY()) / this.steps,
               this.getZ() + (this.tz - this.getZ()) / this.steps
            );
            this.setYRot(this.getYRot() + Mth.wrapDegrees(this.yaw - this.getYRot()) / this.steps);
            this.setXRot(this.getXRot() + (this.pitch - this.getXRot()) / this.steps);
            this.steps--;
            this.updateFlutter();
         } else {
            this.advancePhysics();
         }
      } else {
         if (this.expiresAt == 0L) {
            this.expiresAt = this.level().getGameTime() + Math.max(0, 300 - this.tickCount);
         }

         if (this.level().getGameTime() >= this.expiresAt) {
            this.discard();
         } else {
            this.advancePhysics();
         }
      }
   }

   private void updateFlutter() {
      if (!this.onGround()) {
         float swayFreq = 0.17F;
         float phase = (this.tickCount + this.swaySeed) * swayFreq;
         this.roll = (float)Math.sin(phase) * 15.0F;
         this.pitchWobble = (float)Math.cos(phase * 1.22F) * 5.0F;
      } else {
         this.roll *= 0.5F;
         this.pitchWobble *= 0.5F;
      }
   }

   private void advancePhysics() {
      Vec3 v = this.getDeltaMovement();
      Vec3 fwd = v.lengthSqr() > 1.0E-4 ? v.normalize() : this.getLookAngle();
      if (this.tickCount < 18) {
         float sepProgress = Math.min(1.0F, this.tickCount / 14.0F);
         double backDrag = 0.036 * sepProgress;
         double dropRate = 0.014 * sepProgress;
         v = v.subtract(fwd.scale(backDrag)).add(0.0, -dropRate, 0.0);
         if (!this.onGround()) {
            this.setXRot(this.getXRot() + 0.35F);
         }
      }

      v = v.add(0.0, -0.038, 0.0);
      this.updateFlutter();
      if (!this.onGround()) {
         float swayFreq = 0.17F;
         float phase = (this.tickCount + this.swaySeed) * swayFreq;
         Vec3 lat = new Vec3(-fwd.z, 0.0, fwd.x);
         if (lat.lengthSqr() > 1.0E-4) {
            lat = lat.normalize();
         }

         double swayLift = Math.sin(phase) * 0.018;
         v = v.add(lat.scale(swayLift));
         this.setYRot(this.getYRot() + 0.28F);
         this.setXRot(this.getXRot() + 0.45F);
      }

      if (this.onGround()) {
         v = v.multiply(0.6, 0.0, 0.6);
      } else {
         v = v.scale(0.985);
      }

      this.setDeltaMovement(v);
      this.move(MoverType.SELF, this.getDeltaMovement());
   }

   public boolean shouldRenderAtSqrDistance(double distance) {
      double max = 2048.0;
      return distance < max * max;
   }

   public boolean isPickable() {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   protected void readAdditionalSaveData(CompoundTag t) {
      this.tickCount = t.getInt("Age");
      this.expiresAt = t.getLong("ExpiresAt");
      this.entityData.set(CAMO, t.getString("Camo"));
   }

   protected void addAdditionalSaveData(CompoundTag t) {
      t.putInt("Age", this.tickCount);
      t.putLong("ExpiresAt", this.expiresAt);
      t.putString("Camo", this.camo());
   }

}
