package dev.colonyjournal.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.economy.EconomyRules.BuildingInfo;
import dev.colonyjournal.graph.BuildingGraph;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoodsTest {
   @BeforeAll
   static void bootstrap() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @Test
   void everyProducerHasGoodsAndTheyExist() {
      for (BuildingGraph.Node n : BuildingGraph.NODES.values()) {
         if (n.layer() > 2) {
            continue;
         }

         List<Goods.Good> goods = Goods.goodsOf(n.id());
         assertFalse(goods.isEmpty(), "no goods for " + n.id());
         assertTrue(goods.size() <= 3);
         for (Goods.Good g : goods) {
            ResourceLocation id = ResourceLocation.tryParse(g.item());
            assertTrue(id != null, g.item());
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            assertTrue(item != null && item != Items.AIR, "unknown item " + g.item());
            assertTrue(g.price() >= 1);
         }
      }

      assertEquals(Goods.table().size(), BuildingGraph.NODES.values().stream().filter(n -> n.layer() <= 2).count());
   }

   @Test
   void capFormula() {
      assertEquals(48, Goods.cap(0));
      assertEquals(64, Goods.cap(1));
      assertEquals(48 + 16 * 7, Goods.cap(7));
   }

   @Test
   void unitsAndSplit() {
      assertEquals(3, Goods.unitsPerDay(1, 1, 0.0));
      assertEquals(5, Goods.unitsPerDay(1, 3, 0.0));
      assertEquals(12, Goods.unitsPerDay(2, 2, 1.0));
      assertEquals(10, Goods.unitsPerDay(2, 2, 0.5));
      assertEquals(List.of(4, 3, 3), Arrays.stream(Goods.split(10, 3)).boxed().toList());
      assertEquals(List.of(5), Arrays.stream(Goods.split(5, 1)).boxed().toList());
   }

   @Test
   void productionFillsStockAndSkipsIdleBuildings() {
      List<BuildingInfo> b = List.of(
         new BuildingInfo("miner", 2, true, 1), new BuildingInfo("farmer", 3, true, 0), new BuildingInfo("fisherman", 1, false, 2),
         new BuildingInfo("guardtower", 2, true, 1)
      );
      Goods.DayResult r = Goods.runDay(b, 0.0, Map.of());
      // miner: 1 * (2 + 2) = 4 units over 3 goods -> 2, 1, 1
      assertEquals(Map.of("minecraft:cobblestone", 2, "minecraft:coal", 1, "minecraft:raw_iron", 1), r.stock());
      assertEquals(0L, r.coins());
   }

   @Test
   void overflowIsSoldAtTheWholesalePrice() {
      List<BuildingInfo> b = List.of(new BuildingInfo("sifter", 1, true, 1));
      // sifter makes sand: 1 * 3 = 3 per day at score 0; cap 64
      Goods.DayResult r = Goods.runDay(b, 0.0, Map.of("minecraft:sand", 63));
      assertEquals(64, r.stock().get("minecraft:sand"));
      assertEquals(2L * EconomyRules.wholesalePrice(1, 0.0), r.coins());
      // 10 over the cap of iron ingots (smeltery level 2 -> cap 80) at a perfect colony
      Goods.DayResult s = Goods.runDay(List.of(new BuildingInfo("smeltery", 2, true, 0)), 1.0, Map.of("minecraft:iron_ingot", 90));
      assertEquals(80, s.stock().get("minecraft:iron_ingot"));
      assertEquals(10L * EconomyRules.wholesalePrice(8, 1.0), s.coins());
      assertNotEquals(0L, s.coins());
   }
}
