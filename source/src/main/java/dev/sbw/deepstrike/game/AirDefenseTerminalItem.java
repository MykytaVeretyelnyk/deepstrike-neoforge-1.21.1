package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.client.ClientSetup;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;
import org.jetbrains.annotations.Nullable;

public class AirDefenseTerminalItem extends Item {
   public AirDefenseTerminalItem(Properties properties) {
      super(properties);
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (player.getVehicle() instanceof PatriotTruckEntity truck) {
         if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.openPatriotScreen(truck.getId()));
         }

         return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
      } else {
         CompoundTag tag = NbtCompat.getTag(stack);
         if (tag != null && "Iron Dome".equals(tag.getString("TargetName"))) {
            if (!level.isClientSide && tag.hasUUID("TargetUUID") && player instanceof ServerPlayer sp) {
               IronDomeNetwork.openBound(sp, tag);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
         } else if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
         } else if (tag == null || !tag.hasUUID("TargetUUID")) {
            player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.bind"));
            return InteractionResultHolder.sidedSuccess(stack, false);
         } else if ("AN/MPQ-65".equals(tag.getString("TargetName"))) {
            player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.radar_help"));
            return InteractionResultHolder.sidedSuccess(stack, false);
         } else {
            UUID targetUuid = tag.getUUID("TargetUUID");
            if (player.level() instanceof ServerLevel serverLevel) {
               Entity target = serverLevel.getEntity(targetUuid);
               if (target == null || !target.isAlive()) {
                  player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.missing"));
                  return InteractionResultHolder.sidedSuccess(stack, false);
               }

               if (player.distanceToSqr(target) > 262144.0) {
                  player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.range"));
                  return InteractionResultHolder.sidedSuccess(stack, false);
               }

               String name = tag.getString("TargetName");
               player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.connected", new Object[]{name}));
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
         }
      }
   }

   public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      Level level = context.level();
      CompoundTag tag = NbtCompat.getTag(stack);
      if (tag != null && tag.hasUUID("TargetUUID")) {
         tooltip.add(Component.translatable("message.deepstrike.terminal.bound", new Object[]{tag.getString("TargetName")}));
      } else {
         tooltip.add(Component.translatable("message.deepstrike.terminal.connect_hint"));
      }

      tooltip.add(Component.translatable("message.deepstrike.terminal.vehicle_hint"));
   }
}
