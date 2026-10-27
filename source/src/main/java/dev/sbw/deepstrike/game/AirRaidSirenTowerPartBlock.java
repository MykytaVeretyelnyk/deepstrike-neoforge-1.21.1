package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.registry.BFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AirRaidSirenTowerPartBlock extends Block {
   static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
   public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 2);

   public AirRaidSirenTowerPartBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(LEVEL, 1));
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{LEVEL});
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return state.getValue(LEVEL) == 1 ? box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0) : box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
   }

   public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      InteractionHand hand = InteractionHand.MAIN_HAND;
      BlockPos base = pos.below((Integer)state.getValue(LEVEL));
      BlockState baseState = level.getBlockState(base);
      return baseState.getBlock() == BFBlocks.SIREN_TOWER.get()
         ? baseState.useWithoutItem(level, player, new BlockHitResult(hit.getLocation(), hit.getDirection(), base, hit.isInside()))
         : InteractionResult.PASS;
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
      if (!state.is(next.getBlock()) && !level.isClientSide && !REMOVING.get()) {
         BlockPos base = pos.below((Integer)state.getValue(LEVEL));
         if (level.getBlockState(base).getBlock() == BFBlocks.SIREN_TOWER.get()) {
            level.destroyBlock(base, true);
         }
      }

      super.onRemove(state, level, pos, next, moving);
   }
}
