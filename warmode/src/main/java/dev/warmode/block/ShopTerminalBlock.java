package dev.warmode.block;

import com.mojang.serialization.MapCodec;
import dev.warmode.game.WarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Supply terminal: placed on a team base, opens the shop for the player's team. */
public class ShopTerminalBlock extends Block {
   public static final MapCodec<ShopTerminalBlock> CODEC = simpleCodec(ShopTerminalBlock::new);

   public ShopTerminalBlock(Properties properties) {
      super(properties);
   }

   @Override
   protected MapCodec<? extends Block> codec() {
      return CODEC;
   }

   @Override
   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
         WarManager.openShop(serverPlayer, pos);
      }

      return InteractionResult.sidedSuccess(level.isClientSide);
   }
}
