package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.client.Su24mScreen;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import net.minecraft.core.BlockPos;
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
         if (!level.isClientSide && context.getPlayer() != null) {
            context.getPlayer()
               .displayClientMessage(
                  Component.translatable("message.deepstrike.laptop.bound", new Object[]{block.getName(), pos.getX(), pos.getY(), pos.getZ()}),
                  true
               );
            context.getPlayer().displayClientMessage(Component.translatable("message.deepstrike.laptop.use_panel"), false);
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
         if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.deepstrike.laptop.use_panel"), true);
         }

         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
      }
   }
}
