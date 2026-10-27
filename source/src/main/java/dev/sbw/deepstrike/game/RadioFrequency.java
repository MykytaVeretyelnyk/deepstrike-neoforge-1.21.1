package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class RadioFrequency {
   public static boolean valid(String value) {
      return value != null && (value.isEmpty() || value.matches("[0-9]{1,12}"));
   }

   public static List<String> parse(String csv, int limit) {
      if (csv != null && !csv.isBlank()) {
         String[] parts = csv.split(",", -1);
         if (parts.length > limit) {
            throw new IllegalArgumentException("Too many frequencies");
         } else {
            List<String> result = new ArrayList<>();

            for (String raw : parts) {
               String value = raw.trim();
               if (!valid(value) || value.isEmpty()) {
                  throw new IllegalArgumentException("Invalid frequency");
               }

               if (!result.contains(value)) {
                  result.add(value);
               }
            }

            return List.copyOf(result);
         }
      } else {
         return List.of();
      }
   }

   public static void save(CompoundTag tag, String key, List<String> values) {
      ListTag list = new ListTag();
      values.forEach(value -> list.add(StringTag.valueOf(value)));
      tag.put(key, list);
   }

   public static List<String> load(CompoundTag tag, String key, int limit) {
      List<String> result = new ArrayList<>();
      ListTag list = tag.getList(key, 8);

      for (int i = 0; i < Math.min(limit, list.size()); i++) {
         String value = list.getString(i);
         if (valid(value) && !value.isEmpty() && !result.contains(value)) {
            result.add(value);
         }
      }

      return List.copyOf(result);
   }

   public static List<String> tabletChannels(ItemStack stack) {
      return NbtCompat.hasTag(stack) ? load(NbtCompat.getTag(stack), "RadioChannels", 3) : List.of();
   }

   public static boolean playerReceives(Player player, String frequency) {
      if (player != null && valid(frequency) && !frequency.isEmpty()) {
         for (ItemStack stack : player.getInventory().items) {
            if (stack.is((Item)BFItems.JAGM_CAMERA_TABLET.get()) && tabletChannels(stack).contains(frequency)) {
               return true;
            }
         }

         for (ItemStack stackx : player.getInventory().offhand) {
            if (stackx.is((Item)BFItems.JAGM_CAMERA_TABLET.get()) && tabletChannels(stackx).contains(frequency)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private RadioFrequency() {
   }
}
