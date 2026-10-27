package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.common.ItemAbilities;

public final class DudMissileEntity extends Entity {
   private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> CAMO = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Boolean> GRAPHITE = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> BOOSTER = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<BlockPos> CONTACT = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.BLOCK_POS);
   private static final EntityDataAccessor<Integer> MATERIAL = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<Float> HIT_X = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> HIT_Y = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.FLOAT);
   private static final EntityDataAccessor<Float> HIT_Z = SynchedEntityData.defineId(DudMissileEntity.class, EntityDataSerializers.FLOAT);
   private String warhead = "he";
   private CompoundTag estimate = new CompoundTag();
   private boolean excavated;

   public Vec3 impactPoint() {
      BlockPos b = this.contactBlock();
      return new Vec3(
         b.getX() + (Float)this.entityData.get(HIT_X),
         b.getY() + (Float)this.entityData.get(HIT_Y),
         b.getZ() + (Float)this.entityData.get(HIT_Z)
      );
   }

   public BlockPos contactBlock() {
      return (BlockPos)this.entityData.get(CONTACT);
   }

   public BlockState material() {
      return Block.stateById((Integer)this.entityData.get(MATERIAL));
   }

   public boolean visibleBone(String bone) {
      return !bone.equals("booster") || this.kind() != MissileKind.FP5 || (Boolean)this.entityData.get(BOOSTER);
   }

   public DudMissileEntity(EntityType<? extends DudMissileEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
      builder.define(KIND, 0);
      builder.define(CAMO, "default");
      builder.define(GRAPHITE, false);
      builder.define(BOOSTER, true);
      builder.define(CONTACT, BlockPos.ZERO);
      builder.define(MATERIAL, 0);
      builder.define(HIT_X, 0.5F);
      builder.define(HIT_Y, 1.0F);
      builder.define(HIT_Z, 0.5F);
   }

   public MissileKind kind() {
      return MissileKind.byId((Integer)this.entityData.get(KIND));
   }

   public String camo() {
      return (String)this.entityData.get(CAMO);
   }

   public boolean graphite() {
      return (Boolean)this.entityData.get(GRAPHITE);
   }

   public static void plant(GuidedMissileEntity missile, Vec3 origin) {
      if (!missile.hasImpactPosition()) {
         throw new IllegalStateException("An unexploded missile requires physical contact");
      } else {
         DudMissileEntity dud = (DudMissileEntity)((EntityType)BFEntities.DUD_MISSILE.get()).create(missile.level());
         if (dud != null) {
            dud.entityData.set(KIND, missile.kind().ordinal());
            dud.entityData.set(CAMO, missile.camo());
            dud.entityData.set(GRAPHITE, missile.graphite());
            dud.warhead = missile.warhead();
            dud.entityData.set(BOOSTER, missile.fp5BoosterAttached());
            Vec3 dir = missile.getDeltaMovement().normalize();
            if (dir.lengthSqr() < 0.1) {
               dir = new Vec3(0.0, -1.0, 0.0);
            }

            double nose = MissileGeometry.noseDistance(missile.kind());
            Vec3 impact = missile.impactPosition();
            BlockPos block = BlockPos.containing(impact.add(dir.scale(0.08)));
            BlockState state = missile.level().getBlockState(block);
            if (state.getCollisionShape(missile.level(), block).isEmpty()) {
               BlockHitResult hit = GroundContact.below(missile.level(), impact);
               if (hit.getType() == Type.MISS) {
                  return;
               }

               block = hit.getBlockPos();
               state = missile.level().getBlockState(block);
               impact = hit.getLocation();
            }

            dud.entityData.set(CONTACT, block);
            dud.entityData.set(MATERIAL, Block.getId(state));
            dud.entityData.set(HIT_X, (float)(impact.x - block.getX()));
            dud.entityData.set(HIT_Y, (float)(impact.y - block.getY()));
            dud.entityData.set(HIT_Z, (float)(impact.z - block.getZ()));
            double hardness = Math.max(0.0F, state.getDestroySpeed(missile.level(), block));
            double depth = missile.kind() == MissileKind.PEACEKEEPER ? (hardness < 1.5 ? 5.5 : 3.6) : (hardness < 1.5 ? 1.2 : 0.55);
            Vec3 center = impact.add(dir.scale(depth - nose));
            float yaw = (float)Math.toDegrees(Math.atan2(-dir.x, dir.z));
            float pitch = (float)(-Math.toDegrees(Math.asin(dir.y)));
            dud.moveTo(center.x, center.y, center.z, yaw, pitch);
            double error = 0.2 + missile.level().random.nextDouble() * 0.1;
            double[] coords = new double[]{origin.x, origin.y, origin.z};
            String[] keys = new String[]{"X", "Y", "Z"};

            for (int i = 0; i < 3; i++) {
               double spread = Math.max(1.0, Math.abs(coords[i]) * error);
               dud.estimate.putInt(keys[i] + "Min", (int)Math.floor(coords[i] - spread));
               dud.estimate.putInt(keys[i] + "Max", (int)Math.ceil(coords[i] + spread));
            }

            missile.level().addFreshEntity(dud);
         }
      }
   }

   public void tick() {
      super.tick();
      float length = this.kind() == MissileKind.PEACEKEEPER ? 20.0F : Math.max(1.0F, -ModelGeometry.get(ModelAssets.missile(this.kind())).noseX);
      Vec3 direction = this.getLookAngle();
      AABB base = new AABB(
         this.getX() - 0.55, this.getY() - 0.55, this.getZ() - 0.55, this.getX() + 0.55, this.getY() + 0.55, this.getZ() + 0.55
      );
      this.setBoundingBox(base.minmax(base.move(direction.scale(length * 0.75))).minmax(base.move(direction.scale(-length * 0.75))));
   }

   public boolean isPickable() {
      return !this.isRemoved();
   }

   public AABB getBoundingBoxForCulling() {
      return this.getBoundingBox().inflate(8.0);
   }

   public InteractionResult interact(Player player, InteractionHand hand) {
      if (!player.getItemInHand(hand).canPerformAction(ItemAbilities.SHOVEL_DIG)) {
         return InteractionResult.PASS;
      } else if (this.level().isClientSide) {
         return InteractionResult.SUCCESS;
      } else if (this.excavated) {
         return InteractionResult.CONSUME;
      } else {
         this.excavated = true;
         if (this.level().random.nextDouble() < 0.05) {
            GuidedMissileEntity charge = new GuidedMissileEntity(this.level(), this.kind(), this.graphite(), this.camo());
            charge.moveTo(this.position());
            charge.applyMissileData(new MissileData(this.kind(), this.warhead, "rocket", List.of(), this.camo(), 0, 0));
            MissileExplosion.detonate((ServerLevel)this.level(), charge, this.warhead);
            this.discard();
            return InteractionResult.CONSUME;
         } else {
            Item item = switch (this.kind()) {
               case FP5 -> this.graphite() ? (Item)BFItems.FP5_GRAPHITE.get() : (Item)BFItems.FP5.get();
               case STORM_SHADOW -> (Item)BFItems.STORM_SHADOW.get();
               case JAGM -> (Item)BFItems.JAGM.get();
               case PEACEKEEPER -> (Item)BFItems.PEACEKEEPER.get();
               case SPIKE_NLOS -> (Item)BFItems.SPIKE_NLOS_MISSILE.get();
            };
            ItemStack recovered = new ItemStack(item);
            new MissileData(this.kind(), "none", "rocket", List.of(), this.camo(), 0, 0).writeToItemStack(recovered);
            NbtCompat.update(recovered, t -> t.put("LaunchEstimate", this.estimate.copy()));
            if (!player.getInventory().add(recovered)) {
               player.drop(recovered, false);
            }

            player.getItemInHand(hand).hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            this.discard();
            return InteractionResult.CONSUME;
         }
      }
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
      tag.putFloat("HitX", (Float)this.entityData.get(HIT_X));
      tag.putFloat("HitY", (Float)this.entityData.get(HIT_Y));
      tag.putFloat("HitZ", (Float)this.entityData.get(HIT_Z));
      tag.putLong("ContactBlock", this.contactBlock().asLong());
      tag.putInt("ImpactMaterial", (Integer)this.entityData.get(MATERIAL));
      tag.putBoolean("BoosterAttached", (Boolean)this.entityData.get(BOOSTER));
      tag.putInt("Kind", this.kind().ordinal());
      tag.putString("Camo", this.camo());
      tag.putBoolean("Graphite", this.graphite());
      tag.putString("Warhead", this.warhead);
      tag.put("LaunchEstimate", this.estimate.copy());
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      this.entityData.set(CONTACT, BlockPos.of(tag.getLong("ContactBlock")));
      this.entityData.set(HIT_X, tag.contains("HitX") ? tag.getFloat("HitX") : 0.5F);
      this.entityData.set(HIT_Y, tag.contains("HitY") ? tag.getFloat("HitY") : 1.0F);
      this.entityData.set(HIT_Z, tag.contains("HitZ") ? tag.getFloat("HitZ") : 0.5F);
      this.entityData.set(MATERIAL, tag.getInt("ImpactMaterial"));
      this.entityData.set(BOOSTER, !tag.contains("BoosterAttached") || tag.getBoolean("BoosterAttached"));
      this.entityData.set(KIND, tag.getInt("Kind"));
      this.entityData.set(CAMO, tag.getString("Camo"));
      this.entityData.set(GRAPHITE, tag.getBoolean("Graphite"));
      this.warhead = tag.getString("Warhead");
      this.estimate = tag.getCompound("LaunchEstimate").copy();
   }

}
