package dev.colonyjournal.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.economy.EconomyRules;
import dev.colonyjournal.economy.EconomyRules.BuildingInfo;
import dev.colonyjournal.economy.EconomyRules.Params;
import dev.colonyjournal.economy.Goods;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The numbers the guide texts promise must be the numbers the rules really use. */
class GuideNumbersTest {
   private static final Params P = Params.DEFAULT;

   private static String guideEn() throws IOException {
      return Files.readString(TestPaths.ROOT.resolve("src/main/resources/assets/colonyjournal/lang/en_us.json"), StandardCharsets.UTF_8);
   }

   @Test
   void defaultsMatchTheGuide() {
      assertEquals(30, P.producerBase());
      assertEquals(15, P.producerPerLevel());
      assertEquals(90, P.producerCapPerLevel());
      assertEquals(2, P.upkeepBase());
      assertEquals(2, P.upkeepPerLevel());
      assertEquals(3, P.townhallBase());
      assertEquals(3, P.townhallPerLevel());
      assertEquals(12, P.defaultTax());
      assertEquals(50, P.maxRate());
      assertEquals(5, P.feePercent());
      assertEquals(8, P.maxOffers());
      assertEquals(1_000_000, P.maxPrice());
      assertEquals(500, P.debtFloor());
      assertEquals(48, Goods.CAP_BASE);
      assertEquals(16, Goods.CAP_PER_LEVEL);
   }

   @Test
   void workedExampleAddsUp() {
      List<BuildingInfo> colony = List.of(
         new BuildingInfo("farmer", 2, true, 2),
         new BuildingInfo("lumberjack", 2, true, 1),
         new BuildingInfo("townhall", 2, true, 0),
         new BuildingInfo("warehouse", 1, true, 0),
         new BuildingInfo("guardtower", 1, true, 1)
      );
      EconomyRules.Result r = EconomyRules.evaluate(colony, 12, P);
      assertEquals(21L, r.income());
      assertEquals(17L, r.expense());
      assertEquals(4L, r.income() - r.expense());
   }

   @Test
   void comfortableRateExamples() {
      assertEquals(36, EconomyRules.tolerableRate(10));
      assertEquals(20, EconomyRules.tolerableRate(50));
      assertEquals(10, EconomyRules.tolerableRate(75));
      assertEquals(40, EconomyRules.tolerableRate(0));
      assertEquals(1.10, EconomyRules.taxFactor(0, 40, 50), 1e-9);
      assertEquals(0.5, EconomyRules.taxFactor(50, 36, 50), 1e-9);
   }

   @Test
   void priceAndProsperityRanges() {
      assertEquals(0.8, EconomyRules.priceMultiplier(0.0), 1e-9);
      assertEquals(1.7, EconomyRules.priceMultiplier(1.0), 1e-9);
      assertEquals(1.0, EconomyRules.prosperity(5.0, 10.0, 80), 1e-9);
      assertEquals(0.4, EconomyRules.prosperity(5.0, 0.0, 0), 1e-9);
      assertEquals(0.2, EconomyRules.prosperity(0.0, 0.0, 80), 1e-9);
      // workers x (2 + level) x (1 + 0.5 x prosperity)
      assertEquals(15, Goods.unitsPerDay(2, 3, 1.0));
      assertEquals(4, Goods.unitsPerDay(1, 2, 0.0));
      assertEquals(48 + 16 * 5, Goods.cap(5));
   }

   @Test
   void guideTextQuotesTheExampleResult() throws IOException {
      String json = guideEn();
      assertTrue(json.contains("earns 21 coins a day"), "worked example income");
      assertTrue(json.contains("Net: +4 coins a day"), "worked example net");
      assertTrue(json.contains("36%%") && json.contains("20%%"), "comfortable rate examples");
   }
}
