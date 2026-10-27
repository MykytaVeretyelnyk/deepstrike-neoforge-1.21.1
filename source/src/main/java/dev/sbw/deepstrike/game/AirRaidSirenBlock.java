package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.client.AirRaidSirenScreen;
import dev.sbw.deepstrike.registry.BFBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;

public final class AirRaidSirenBlock extends BaseEntityBlock {
   @Override
   protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
      return com.mojang.serialization.MapCodec.unit(this);
   }

   private final AirRaidSirenBlock.Tier tier;

   public AirRaidSirenBlock(AirRaidSirenBlock.Tier tier, Properties properties) {
      super(properties);
      this.tier = tier;
   }

   public AirRaidSirenBlock.Tier tier() {
      return this.tier;
   }

   public RenderShape getRenderShape(BlockState state) {
      return RenderShape.INVISIBLE;
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return this.tier == AirRaidSirenBlock.Tier.TOWER
         ? box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0)
         : (this.tier == AirRaidSirenBlock.Tier.ADVANCED ? box(2.0, 0.0, 2.0, 14.0, 16.0, 14.0) : box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0));
   }

   @Nullable
   public BlockState getStateForPlacement(BlockPlaceContext context) {
      if (this.tier == AirRaidSirenBlock.Tier.TOWER) {
         BlockPos pos = context.getClickedPos();
         if (pos.getY() + 2 >= context.getLevel().getMaxBuildHeight()) {
            return null;
         }

         if (!context.getLevel().getBlockState(pos.above()).canBeReplaced(context) || !context.getLevel().getBlockState(pos.above(2)).canBeReplaced(context)) {
            return null;
         }
      }

      return this.defaultBlockState();
   }

   public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
      super.setPlacedBy(level, pos, state, placer, stack);
      if (this.tier == AirRaidSirenBlock.Tier.TOWER && !level.isClientSide) {
         level.setBlock(pos.above(), (BlockState)((Block)BFBlocks.SIREN_TOWER_PART.get()).defaultBlockState().setValue(AirRaidSirenTowerPartBlock.LEVEL, 1), 3);
         level.setBlock(pos.above(2), (BlockState)((Block)BFBlocks.SIREN_TOWER_PART.get()).defaultBlockState().setValue(AirRaidSirenTowerPartBlock.LEVEL, 2), 3);
      }
   }

   public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
      if (this.tier == AirRaidSirenBlock.Tier.TOWER && !state.is(next.getBlock()) && !level.isClientSide) {
         AirRaidSirenTowerPartBlock.REMOVING.set(true);

         try {
            for (int i = 1; i <= 2; i++) {
               if (level.getBlockState(pos.above(i)).getBlock() == BFBlocks.SIREN_TOWER_PART.get()) {
                  level.removeBlock(pos.above(i), false);
               }
            }
         } finally {
            AirRaidSirenTowerPartBlock.REMOVING.set(false);
         }
      }

      super.onRemove(state, level, pos, next, moving);
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new AirRaidSirenBlockEntity(pos, state);
   }

   @Nullable
   public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
      return level.isClientSide ? null : createTickerHelper(type, (BlockEntityType)BFBlocks.SIREN_BE.get(), AirRaidSirenBlockEntity::serverTick);
   }

   public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      InteractionHand hand = InteractionHand.MAIN_HAND;
      if (level.isClientSide) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AirRaidSirenScreen.open(pos));
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }

   public static enum Tier {
      BASIC,
      ADVANCED,
      TOWER;
   }
}
