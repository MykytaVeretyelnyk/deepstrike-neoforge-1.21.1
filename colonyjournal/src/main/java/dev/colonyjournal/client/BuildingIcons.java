package dev.colonyjournal.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One item icon per MineColonies building type. Unknown types get a sheet of paper. */
public final class BuildingIcons {
   private static final Map<String, Item> ICONS = new HashMap<>();
   private static final Map<String, ItemStack> STACKS = new HashMap<>();
   private static final ItemStack FALLBACK = new ItemStack(Items.PAPER);

   private static void put(Item item, String... ids) {
      for (String id : ids) {
         ICONS.put(id, item);
      }
   }

   static {
      put(Items.IRON_PICKAXE, "miner");
      put(Items.IRON_AXE, "lumberjack");
      put(Items.IRON_HOE, "farmer");
      put(Items.FISHING_ROD, "fisherman");
      put(Items.EGG, "chickenherder");
      put(Items.BEEF, "cowboy");
      put(Items.PORKCHOP, "swineherder");
      put(Items.SHEARS, "shepherd");
      put(Items.RABBIT_FOOT, "rabbithutch");
      put(Items.SUGAR_CANE, "plantation");
      put(Items.POPPY, "florist");
      put(Items.HONEYCOMB, "beekeeper");
      put(Items.NETHER_BRICK, "netherworker");
      put(Items.FURNACE, "smeltery");
      put(Items.OAK_PLANKS, "sawmill");
      put(Items.COBBLESTONE, "crusher");
      put(Items.GRAVEL, "sifter");
      put(Items.BREAD, "baker");
      put(Items.COMPOSTER, "composter");
      put(Items.BLAST_FURNACE, "stonesmeltery");
      put(Items.ANVIL, "blacksmith");
      put(Items.STONECUTTER, "stonemason");
      put(Items.GLASS, "glassblower");
      put(Items.FLETCHING_TABLE, "fletcher");
      put(Items.PISTON, "mechanic");
      put(Items.GRAY_CONCRETE, "concretemixer");
      put(Items.BLUE_DYE, "dyer");
      put(Items.BREWING_STAND, "alchemist");
      put(Items.ENCHANTING_TABLE, "enchanter");
      put(Items.COOKED_BEEF, "cook");
      put(Items.SMOKER, "kitchen");
      put(Items.BRICKS, "builder");
      put(Items.IRON_SWORD, "guardtower");
      put(Items.SHIELD, "barracks");
      put(Items.IRON_HELMET, "barrackstower");
      put(Items.BOW, "archery");
      put(Items.DIAMOND_SWORD, "combatacademy");
      put(Items.GOLDEN_APPLE, "hospital");
      put(Items.BOOKSHELF, "library");
      put(Items.ENCHANTED_BOOK, "university");
      put(Items.WRITABLE_BOOK, "school");
      put(Items.COOKIE, "tavern");
      put(Items.BONE, "graveyard");
      put(Items.BELL, "townhall");
      put(Items.CHEST, "warehouse");
      put(Items.RED_BED, "residence");
      put(Items.MINECART, "deliveryman");
      put(Items.BARREL, "postbox");
      put(Items.ENDER_CHEST, "stash");
      put(Items.AMETHYST_SHARD, "mysticalsite");
      put(Items.SADDLE, "stable");
      put(Items.IRON_SHOVEL, "simplequarry", "mediumquarry", "largequarry");
      put(Items.IRON_DOOR, "gatehouse");
   }

   /** The icon stack of a building type. The returned stack is shared and must not be modified. */
   public static ItemStack of(String buildingId) {
      Item item = ICONS.get(buildingId);
      return item == null ? FALLBACK : STACKS.computeIfAbsent(buildingId, k -> new ItemStack(item));
   }

   private BuildingIcons() {
   }
}
