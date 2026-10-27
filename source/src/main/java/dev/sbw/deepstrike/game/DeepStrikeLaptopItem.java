package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.client.DeepStrikeMapScreen;
import dev.sbw.deepstrike.client.Su24mScreen;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;

public final class DeepStrikeLaptopItem extends Item {
   public DeepStrikeLaptopItem(Properties properties) {
      super(properties);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      BlockPos pos = context.getClickedPos();
      BlockState state = level.getBlockState(pos);
      if (state.getBlock() instanceof LauncherBlock block) {
         ItemStack stack = context.getItemInHand();
         NbtCompat.update(stack, t -> t.putLong("BoundLauncher", pos.asLong()));
         if (level.isClientSide) {
            Direction facing = (Direction)state.getValue(LauncherBlock.FACING);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DeepStrikeMapScreen.open(pos, facing, block.family()));
         } else if (context.getPlayer() != null) {
            context.getPlayer()
               .displayClientMessage(
                  Component.translatable("message.deepstrike.laptop.bound", new Object[]{block.getName(), pos.getX(), pos.getY(), pos.getZ()}),
                  true
               );
         }

         return InteractionResult.sidedSuccess(level.isClientSide);
      } else {
         return super.useOn(context);
      }
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (player.getVehicle() instanceof Su24mEntity plane) {
         if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Su24mScreen.open(plane.getId()));
         }

         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
      } else {
         if (level.isClientSide) {
            BlockPos target = null;
            Direction facing = Direction.NORTH;
            LauncherBlock.Family family = LauncherBlock.Family.FP5;
            if (NbtCompat.hasTag(stack) && NbtCompat.getTag(stack).contains("BoundLauncher")) {
               BlockPos bound = BlockPos.of(NbtCompat.getTag(stack).getLong("BoundLauncher"));
               if (level.hasChunkAt(bound)) {
                  BlockState boundState = level.getBlockState(bound);
                  if (boundState.getBlock() instanceof LauncherBlock lb) {
                     target = bound;
                     facing = (Direction)boundState.getValue(LauncherBlock.FACING);
                     family = lb.family();
                  }
               }
            }

            if (target == null) {
               BlockPos origin = player.blockPosition();
               double bestD = 4225.0;

               for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-64, -24, -64), origin.offset(64, 24, 64))) {
                  BlockState state = level.getBlockState(pos);
                  if (state.getBlock() instanceof LauncherBlock lb) {
                     double d = pos.distToCenterSqr(player.position());
                     if (d < bestD) {
                        bestD = d;
                        target = pos.immutable();
                        facing = (Direction)state.getValue(LauncherBlock.FACING);
                        family = lb.family();
                     }
                  }
               }
            }

            if (target != null) {
               BlockPos finalTarget = target;
               Direction finalFacing = facing;
               LauncherBlock.Family finalFamily = family;
               DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DeepStrikeMapScreen.open(finalTarget, finalFacing, finalFamily));
               return InteractionResultHolder.success(stack);
            }

            player.displayClientMessage(Component.translatable("message.deepstrike.laptop.missing"), true);
         }

         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
      }
   }
}
