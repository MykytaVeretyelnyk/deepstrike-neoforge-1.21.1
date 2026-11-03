package dev.colonyjournal.analysis;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Burnable;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Maps an open colony request to the building types that can supply it. */
public final class NeedClassifier {
   private static final List<String> NONE = List.of();

   private static boolean has(String path, String... parts) {
      for (String part : parts) {
         if (path.contains(part)) {
            return true;
         }
      }

      return false;
   }

   /** Building ids that normally produce what the request asks for; empty when unknown (supply it by hand). */
   public static List<String> producers(IRequest<?> request) {
      Object requestable = request.getRequest();
      if (requestable instanceof Tool tool) {
         String type = tool.getEquipmentType().getRegistryName().getPath();
         if (type.contains("bow") || type.contains("crossbow")) {
            return List.of("fletcher", "blacksmith");
         }

         return List.of("blacksmith");
      } else if (requestable instanceof Food) {
         return List.of("cook", "baker", "farmer", "fisherman");
      } else if (requestable instanceof Burnable) {
         return List.of("lumberjack", "sawmill");
      }

      List<ItemStack> stacks = request.getDisplayStacks();
      if (stacks == null || stacks.isEmpty() || stacks.get(0).isEmpty()) {
         return NONE;
      }

      ItemStack stack = stacks.get(0);
      if (stack.getFoodProperties(null) != null) {
         return List.of("cook", "baker", "farmer", "fisherman");
      }

      return producersForItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
   }

   static List<String> producersForItem(String path) {
      if (has(path, "_ingot", "nugget", "iron_block", "gold_block", "copper_block")) {
         return List.of("smeltery", "blacksmith");
      } else if (has(path, "raw_", "_ore", "coal", "redstone", "lapis", "diamond", "emerald")) {
         return List.of("miner");
      } else if (has(path, "plank")) {
         return List.of("sawmill", "lumberjack");
      } else if (has(path, "_log", "_wood", "stick", "sapling")) {
         return List.of("lumberjack", "sawmill");
      } else if (has(path, "brick", "cobble", "stone", "andesite", "granite", "diorite", "deepslate", "tuff")) {
         return List.of("stonemason", "miner");
      } else if (has(path, "glass")) {
         return List.of("glassblower");
      } else if (has(path, "sand", "gravel")) {
         return List.of("sifter", "crusher", "miner");
      } else if (has(path, "concrete")) {
         return List.of("concretemixer");
      } else if (has(path, "dye", "wool", "carpet", "banner")) {
         return List.of("dyer", "shepherd");
      } else if (has(path, "wheat", "carrot", "potato", "beetroot", "seeds", "melon", "pumpkin", "hay")) {
         return List.of("farmer");
      } else if (has(path, "bread", "cake", "cookie", "pie")) {
         return List.of("baker", "cook");
      } else if (has(path, "arrow", "bow")) {
         return List.of("fletcher");
      } else if (has(path, "piston", "rail", "repeater", "comparator", "hopper", "dispenser", "dropper", "observer", "lever")) {
         return List.of("mechanic");
      } else if (has(path, "potion", "bottle")) {
         return List.of("alchemist");
      } else if (has(path, "enchanted_book", "book")) {
         return List.of("enchanter", "library");
      } else if (has(path, "cod", "salmon", "fish")) {
         return List.of("fisherman");
      } else if (has(path, "egg", "feather")) {
         return List.of("chickenherder");
      } else if (has(path, "beef", "milk", "leather")) {
         return List.of("cowboy");
      } else if (has(path, "porkchop", "pork")) {
         return List.of("swineherder");
      } else if (has(path, "mutton")) {
         return List.of("shepherd");
      } else if (has(path, "rabbit")) {
         return List.of("rabbithutch");
      } else if (has(path, "honey", "bee")) {
         return List.of("beekeeper");
      } else if (has(path, "flower", "tulip", "poppy", "dandelion", "orchid", "allium", "azure", "daisy", "cornflower", "lily", "rose", "peony", "lilac", "sunflower")) {
         return List.of("florist");
      }

      return NONE;
   }

   private NeedClassifier() {
   }
}
