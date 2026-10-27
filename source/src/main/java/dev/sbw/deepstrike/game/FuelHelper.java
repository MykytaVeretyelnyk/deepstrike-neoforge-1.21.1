package dev.sbw.deepstrike.game;

import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FuelHelper {
   private FuelHelper() {
   }

   public static boolean isFuelContainer(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else if (stack.is(Items.LAVA_BUCKET)) {
         return true;
      } else {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (id != null) {
            String path = id.getPath().toLowerCase();
            String ns = id.getNamespace().toLowerCase();
            if (path.contains("kerosene") || path.contains("fuel") || path.contains("diesel") || path.contains("oil")) {
               return true;
            }
         }

         Stream<TagKey<Item>> tags = stack.getTags();

         for (TagKey<Item> tag : tags.toList()) {
            String tagPath = tag.location().getPath().toLowerCase();
            if (tagPath.contains("kerosene") || tagPath.contains("fuel") || tagPath.contains("diesel")) {
               return true;
            }
         }

         return false;
      }
   }

   public static int getFuelCapacity(ItemStack stack) {
      return !isFuelContainer(stack) ? 0 : 1000;
   }

   public static ItemStack getEmptyContainer(ItemStack stack) {
      if (stack.is(Items.LAVA_BUCKET)) {
         return new ItemStack(Items.BUCKET);
      } else if (stack.getItem() instanceof BucketItem) {
         return new ItemStack(Items.BUCKET);
      } else {
         ItemStack remaining = stack.getCraftingRemainingItem();
         return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
      }
   }
}
