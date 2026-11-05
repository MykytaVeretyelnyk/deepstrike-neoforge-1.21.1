package dev.colonyjournal.economy;

import dev.colonyjournal.economy.EconomyRules.BuildingInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The goods every producer building makes, with their base unit prices, plus the pure daily production and overflow
 * sale arithmetic. No Minecraft classes: item ids are plain strings such as "minecraft:iron_ingot".
 */
public final class Goods {
   /** One good: item registry id and the base price of one unit in coins. */
   public record Good(String item, int price) {
   }

   /** Result of one production day: the new stock, the coins from selling the overflow, and what was produced. */
   public record DayResult(Map<String, Integer> stock, long coins, Map<String, Integer> produced) {
   }

   public static final int CAP_BASE = 48;
   public static final int CAP_PER_LEVEL = 16;

   private static final Map<String, List<Good>> TABLE = new LinkedHashMap<>();
   private static final Map<String, Integer> BASE_PRICE = new LinkedHashMap<>();

   private static void add(String building, String... itemAndPrice) {
      List<Good> list = new ArrayList<>();
      for (int i = 0; i < itemAndPrice.length; i += 2) {
         String item = "minecraft:" + itemAndPrice[i];
         int price = Integer.parseInt(itemAndPrice[i + 1]);
         list.add(new Good(item, price));
         BASE_PRICE.putIfAbsent(item, price);
      }

      TABLE.put(building, List.copyOf(list));
   }

   static {
      add("miner", "cobblestone", "2", "coal", "3", "raw_iron", "5");
      add("lumberjack", "oak_log", "2", "spruce_log", "2");
      add("farmer", "wheat", "2", "carrot", "2", "potato", "2");
      add("fisherman", "cod", "3", "salmon", "4");
      add("chickenherder", "egg", "2", "chicken", "3");
      add("cowboy", "beef", "4", "leather", "3");
      add("swineherder", "porkchop", "4");
      add("shepherd", "white_wool", "3", "mutton", "4");
      add("rabbithutch", "rabbit", "3");
      add("plantation", "sugar_cane", "2", "cocoa_beans", "3");
      add("florist", "dandelion", "2", "poppy", "2");
      add("beekeeper", "honey_bottle", "5");
      add("netherworker", "quartz", "6");
      add("smeltery", "iron_ingot", "8", "gold_ingot", "18");
      add("sawmill", "oak_planks", "2");
      add("crusher", "gravel", "2");
      add("sifter", "sand", "2");
      add("baker", "bread", "4");
      add("composter", "bone_meal", "2");
      add("stonesmeltery", "stone", "2");
      add("blacksmith", "iron_pickaxe", "25", "iron_sword", "22");
      add("stonemason", "stone_bricks", "2", "bricks", "3");
      add("glassblower", "glass", "3");
      add("fletcher", "arrow", "2", "bow", "10");
      add("mechanic", "redstone", "6");
      add("concretemixer", "white_concrete", "2");
      add("dyer", "red_dye", "2", "blue_dye", "2");
      add("alchemist", "nether_wart", "5");
      add("enchanter", "lapis_lazuli", "6");
      add("cook", "cooked_beef", "8", "cooked_porkchop", "8");
      add("kitchen", "cooked_chicken", "8");
   }

   /** Goods of a building type; empty for buildings that make nothing. */
   public static List<Good> goodsOf(String buildingId) {
      return TABLE.getOrDefault(buildingId, List.of());
   }

   public static Map<String, List<Good>> table() {
      return Map.copyOf(TABLE);
   }

   /** Base price of an item, or 0 when the item is not a known good. */
   public static int basePrice(String item) {
      return BASE_PRICE.getOrDefault(item, 0);
   }

   public static boolean isKnown(String item) {
      return BASE_PRICE.containsKey(item);
   }

   /** Stock limit of one item; {@code levelSum} is the sum of levels of the built buildings that make it. */
   public static int cap(long levelSum) {
      return (int)Math.min(Integer.MAX_VALUE / 2, CAP_BASE + (long)CAP_PER_LEVEL * Math.max(0L, levelSum));
   }

   /** Units a building makes per day: workers * (2 + level) * (1 + 0.5 * score), rounded. */
   public static int unitsPerDay(int workers, int level, double score) {
      return (int)Math.round((double)workers * (double)(2 + level) * (1.0 + 0.5 * score));
   }

   /** Splits a total over {@code n} goods; the remainder goes to the first good. */
   public static int[] split(int total, int n) {
      int[] parts = new int[n];
      if (n == 0) {
         return parts;
      }

      int each = total / n;
      java.util.Arrays.fill(parts, each);
      parts[0] += total - each * n;
      return parts;
   }

   /**
    * Runs one production day. Every built producer with at least one worker adds units to the stock; everything above
    * the cap of an item is sold at the wholesale price. {@code stock} is not modified.
    */
   public static DayResult runDay(List<BuildingInfo> buildings, double score, Map<String, Integer> stock) {
      Map<String, Integer> out = new LinkedHashMap<>(stock);
      Map<String, Integer> produced = new LinkedHashMap<>();
      Map<String, Long> levels = new LinkedHashMap<>();
      for (BuildingInfo b : buildings) {
         List<Good> goods = goodsOf(b.id());
         if (goods.isEmpty() || !b.built() || b.level() < 1) {
            continue;
         }

         for (Good g : goods) {
            levels.merge(g.item(), (long)b.level(), Long::sum);
         }

         if (b.workers() < 1) {
            continue;
         }

         int[] parts = split(unitsPerDay(b.workers(), b.level(), score), goods.size());
         for (int i = 0; i < parts.length; i++) {
            if (parts[i] > 0) {
               String item = goods.get(i).item();
               produced.merge(item, parts[i], Integer::sum);
               out.merge(item, parts[i], (a, c) -> (int)Math.min(Integer.MAX_VALUE / 2, (long)a + c));
            }
         }
      }

      long coins = 0L;
      for (Map.Entry<String, Integer> e : new ArrayList<>(out.entrySet())) {
         int cap = cap(levels.getOrDefault(e.getKey(), 0L));
         int have = e.getValue();
         if (have > cap) {
            int base = basePrice(e.getKey());
            if (base > 0) {
               coins += (long)(have - cap) * EconomyRules.wholesalePrice(base, score);
               out.put(e.getKey(), cap);
            }
         }
      }

      return new DayResult(out, coins, produced);
   }

   private Goods() {
   }
}
