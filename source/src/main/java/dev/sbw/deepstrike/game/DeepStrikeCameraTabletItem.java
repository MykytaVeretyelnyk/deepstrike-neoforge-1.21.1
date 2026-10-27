package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.client.CommandTabletScreen;
import dev.sbw.deepstrike.client.FrequencySettingsScreen;
import dev.sbw.deepstrike.client.Su24mScreen;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;

public final class DeepStrikeCameraTabletItem extends Item {
   public DeepStrikeCameraTabletItem(Properties properties) {
      super(properties);
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
            if (player.isShiftKeyDown()) {
               DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FrequencySettingsScreen.tablet(hand, stack));
            } else {
               DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CommandTabletScreen.open());
            }
         }

         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
      }
   }

   public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      Level level = context.level();
      tooltip.add(Component.translatable("tooltip.deepstrike.tablet.configure"));
      tooltip.add(Component.translatable("tooltip.deepstrike.tablet.channels", new Object[]{String.join(", ", RadioFrequency.tabletChannels(stack))}));
   }
}
