package dev.sbw.deepstrike.game;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.minecraft.core.registries.BuiltInRegistries;

public final class PeacekeeperFuel {
   public static boolean tfmgPresent() {
      return ModList.get().isLoaded("tfmg");
   }

   public static int fuelSlot(ItemStack stack) {
      if (!tfmgPresent()) {
         return stack.is(Items.LAVA_BUCKET) ? 3 : -1;
      } else {
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         if (id != null && id.getNamespace().equals("tfmg")) {
            String var2 = id.getPath();

            return switch (var2) {
               case "kerosene_bucket" -> 0;
               case "diesel_bucket" -> 1;
               case "heavy_oil_bucket" -> 2;
               default -> -1;
            };
         } else {
            return -1;
         }
      }
   }

   private PeacekeeperFuel() {
   }
}
