package dev.sbw.deepstrike.game;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;

public final class LaunchCoverEntity extends Entity {
   private static final EntityDataAccessor<Integer> SPIN_TICKS = SynchedEntityData.defineId(LaunchCoverEntity.class, EntityDataSerializers.INT);
   private int interpolationSteps;
   private double targetX;
   private double targetY;
   private double targetZ;
   private float targetYaw;
   private float targetPitch;
   private float previousSpin;
   private float renderSpin;

   public LaunchCoverEntity(EntityType<? extends LaunchCoverEntity> type, Level level) {
      super(type, level);
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      builder.define(SPIN_TICKS, 0);
   }

   // 1.21 re-sends these as the target when a move packet carries only rotation (or only position).
   @Override
   public double lerpTargetX() {
      return this.interpolationSteps > 0 ? this.targetX : this.getX();
   }

   @Override
   public double lerpTargetY() {
      return this.interpolationSteps > 0 ? this.targetY : this.getY();
   }

   @Override
   public double lerpTargetZ() {
      return this.interpolationSteps > 0 ? this.targetZ : this.getZ();
   }

   @Override
   public float lerpTargetYRot() {
      return this.interpolationSteps > 0 ? this.targetYaw : this.getYRot();
   }

   @Override
   public float lerpTargetXRot() {
      return this.interpolationSteps > 0 ? this.targetPitch : this.getXRot();
   }

   public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
      boolean teleport = false;
      this.targetX = x;
      this.targetY = y;
      this.targetZ = z;
      this.targetYaw = yaw;
      this.targetPitch = pitch;
      this.interpolationSteps = 2;
   }

   public float spin(float partial) {
      return Mth.lerp(partial, this.previousSpin, this.renderSpin);
   }

   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         this.previousSpin = this.renderSpin;
         this.renderSpin = Mth.lerp(0.65F, this.renderSpin, ((Integer)this.entityData.get(SPIN_TICKS)).intValue());
         if (this.interpolationSteps > 0) {
            this.setPos(
               this.getX() + (this.targetX - this.getX()) / this.interpolationSteps,
               this.getY() + (this.targetY - this.getY()) / this.interpolationSteps,
               this.getZ() + (this.targetZ - this.getZ()) / this.interpolationSteps
            );
            this.setYRot(this.getYRot() + Mth.wrapDegrees(this.targetYaw - this.getYRot()) / this.interpolationSteps);
            this.setXRot(this.getXRot() + (this.targetPitch - this.getXRot()) / this.interpolationSteps);
            this.interpolationSteps--;
         }
      } else if (this.tickCount >= 200) {
         this.discard();
      } else {
         if (!this.onGround()) {
            this.entityData.set(SPIN_TICKS, (Integer)this.entityData.get(SPIN_TICKS) + 1);
         }

         this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
         this.move(MoverType.SELF, this.getDeltaMovement());
         if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.55, 0.0, 0.55));
         } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
         }
      }
   }

   public boolean isPickable() {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   public boolean hurt(DamageSource source, float amount) {
      return false;
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      this.tickCount = tag.getInt("Age");
      this.entityData.set(SPIN_TICKS, tag.getInt("SpinTicks"));
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putInt("Age", this.tickCount);
      tag.putInt("SpinTicks", (Integer)this.entityData.get(SPIN_TICKS));
   }

}
