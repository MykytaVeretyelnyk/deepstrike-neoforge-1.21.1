package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class FrequencyNetwork {
   private static final String PROTOCOL = "1";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "frequencies"), () -> "1", "1"::equals, "1"::equals
   );

   public static void register() {
      CHANNEL.messageBuilder(FrequencyNetwork.SetTablet.class, 0, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeBoolean(m.offhand);
            b.writeUtf(m.first, 12);
            b.writeUtf(m.second, 12);
            b.writeUtf(m.third, 12);
         })
         .decoder(b -> new FrequencyNetwork.SetTablet(b.readBoolean(), b.readUtf(12), b.readUtf(12), b.readUtf(12)))
         .consumerMainThread((m, c) -> setTablet(((Context)c.get()).getSender(), m))
         .add();
      CHANNEL.messageBuilder(FrequencyNetwork.SetDefense.class, 1, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeVarInt(m.entityId);
            b.writeUtf(m.values, 220);
         })
         .decoder(b -> new FrequencyNetwork.SetDefense(b.readVarInt(), b.readUtf(220)))
         .consumerMainThread((m, c) -> setDefense(((Context)c.get()).getSender(), m))
         .add();
   }

   private static void setTablet(ServerPlayer player, FrequencyNetwork.SetTablet msg) {
      if (player != null) {
         ItemStack stack = player.getItemInHand(msg.offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
         if (stack.is((Item)BFItems.JAGM_CAMERA_TABLET.get())) {
            if (RadioFrequency.valid(msg.first) && RadioFrequency.valid(msg.second) && RadioFrequency.valid(msg.third)) {
               List<String> values = List.of(msg.first, msg.second, msg.third).stream().filter(s -> !s.isEmpty()).distinct().toList();
               NbtCompat.update(stack, t -> RadioFrequency.save(t, "RadioChannels", values));
               player.getInventory().setChanged();
            }
         }
      }
   }

   private static void setDefense(ServerPlayer player, FrequencyNetwork.SetDefense msg) {
      if (player != null) {
         List<String> values;
         try {
            values = RadioFrequency.parse(msg.values, 16);
         } catch (IllegalArgumentException var9) {
            return;
         }

         Entity entity = player.serverLevel().getEntity(msg.entityId);
         if (entity instanceof IronDomeEntity dome) {
            boolean terminal = false;

            for (ItemStack stack : Stream.concat(player.getInventory().items.stream(), player.getInventory().offhand.stream()).toList()) {
               if (stack.is((Item)BFItems.AIR_DEFENSE_TERMINAL.get())
                  && NbtCompat.hasTag(stack)
                  && NbtCompat.getTag(stack).hasUUID("TargetUUID")
                  && NbtCompat.getTag(stack).getUUID("TargetUUID").equals(dome.getUUID())) {
                  terminal = true;
                  break;
               }
            }

            if (!terminal && player.distanceToSqr(dome) > 1024.0) {
               return;
            }

            dome.setFriendlyFrequencies(values);
         } else if (entity instanceof PatriotTruckEntity truck && player.getVehicle() == truck) {
            truck.setFriendlyFrequencies(values);
         }
      }
   }

   private FrequencyNetwork() {
   }

   public record SetDefense(int entityId, String values) {
   }

   public record SetTablet(boolean offhand, String first, String second, String third) {
   }
}
