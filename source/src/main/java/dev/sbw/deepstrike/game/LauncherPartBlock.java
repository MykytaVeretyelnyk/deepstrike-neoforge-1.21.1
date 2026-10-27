package dev.sbw.deepstrike.game;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LauncherPartBlock extends BaseEntityBlock {
   @Override
   protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
      return com.mojang.serialization.MapCodec.unit(this);
   }

   public LauncherPartBlock(Properties properties) {
      super(properties);
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new LauncherPartBlockEntity(pos, state);
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part ? part.shape() : Shapes.empty();
   }

   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.getShape(state, level, pos, context);
   }

   public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      InteractionHand hand = InteractionHand.MAIN_HAND;
      if (level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part) {
         BlockPos master = part.master();
         BlockState anchor = level.getBlockState(master);
         if (anchor.getBlock() instanceof LauncherBlock launcher) {
            return launcher.useWithoutItem(anchor, level, master, player, new BlockHitResult(hit.getLocation(), hit.getDirection(), master, hit.isInside()));
         }
      }

      return InteractionResult.PASS;
   }

   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (!level.isClientSide && level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part) {
         level.destroyBlock(part.master(), !player.isCreative(), player);
      }

      return super.playerWillDestroy(level, pos, state, player);
   }

   public ItemStack getCloneItemStack(BlockState state, HitResult target, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
      return level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part ? new ItemStack(level.getBlockState(part.master()).getBlock()) : ItemStack.EMPTY;
   }
}
