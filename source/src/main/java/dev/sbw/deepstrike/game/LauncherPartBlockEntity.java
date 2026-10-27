package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.registry.BFBlocks;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LauncherPartBlockEntity extends BlockEntity {
   private BlockPos master = BlockPos.ZERO;
   private long[] bits = new long[0];
   private VoxelShape shape = Shapes.empty();

   public LauncherPartBlockEntity(BlockPos pos, BlockState state) {
      super((BlockEntityType)BFBlocks.LAUNCHER_PART_BE.get(), pos, state);
   }

   public BlockPos master() {
      return this.master;
   }

   public VoxelShape shape() {
      return this.shape;
   }

   public void configure(BlockPos master, long[] bits) {
      if (!this.master.equals(master) || !Arrays.equals(this.bits, bits)) {
         this.master = master.immutable();
         this.bits = (long[])bits.clone();
         this.shape = LauncherCollision.shape(bits);
         this.setChanged();
         if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
         }
      }
   }

   protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      super.saveAdditional(tag, registries);
      tag.putLong("Master", this.master.asLong());
      tag.putLongArray("Voxels", this.bits);
   }

   public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      super.loadAdditional(tag, registries);
      this.master = BlockPos.of(tag.getLong("Master"));
      this.bits = tag.getLongArray("Voxels");
      this.shape = LauncherCollision.shape(this.bits);
   }

   public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
      return NbtCompat.saveBE(this);
   }

   public ClientboundBlockEntityDataPacket getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }
}
