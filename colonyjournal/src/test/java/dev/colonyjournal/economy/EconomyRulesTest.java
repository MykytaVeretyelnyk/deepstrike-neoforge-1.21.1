package dev.colonyjournal.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.economy.EconomyRules.BuildingInfo;
import dev.colonyjournal.economy.EconomyRules.Kind;
import dev.colonyjournal.economy.EconomyRules.Params;
import dev.colonyjournal.economy.EconomyRules.Result;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EconomyRulesTest {
   private static final Params P = Params.DEFAULT;

   @Test
   void classification() {
      assertEquals(Kind.PRODUCER, EconomyRules.kind("miner"));
      assertEquals(Kind.PRODUCER, EconomyRules.kind("blacksmith"));
      assertEquals(Kind.SERVICE, EconomyRules.kind("guardtower"));
      assertEquals(Kind.SERVICE, EconomyRules.kind("townhall"));
      assertEquals(Kind.SERVICE, EconomyRules.kind("warehouse"));
      assertEquals(Kind.SERVICE, EconomyRules.kind("deliveryman"));
      assertEquals(Kind.NEUTRAL, EconomyRules.kind("residence"));
      assertEquals(Kind.NEUTRAL, EconomyRules.kind("unknown_addon_building"));
   }

   @Test
   void tolerableRate() {
      assertEquals(40, EconomyRules.tolerableRate(0));
      assertEquals(32, EconomyRules.tolerableRate(20));
      assertEquals(10, EconomyRules.tolerableRate(75));
      assertEquals(10, EconomyRules.tolerableRate(10_000));
      assertEquals(40, EconomyRules.tolerableRate(-5));
   }

   @Test
   void taxFactorBoundaries() {
      assertEquals(1.0, EconomyRules.taxFactor(18, 18, 50));
      assertEquals(0.5, EconomyRules.taxFactor(50, 10, 50), 1e-9);
      assertEquals(1.0 - 0.5 * 5.0 / 32.0, EconomyRules.taxFactor(23, 18, 50), 1e-9);
      assertEquals(1.10, EconomyRules.taxFactor(0, 32, 50), 1e-9);
      assertTrue(EconomyRules.taxFactor(10, 18, 50) > 1.0);
      assertTrue(EconomyRules.taxFactor(51, 10, 50) >= 0.5);
      // degenerate max rate must not divide by zero
      assertEquals(0.5, EconomyRules.taxFactor(31, 30, 30), 1e-9);
   }

   @Test
   void debtFactor() {
      assertEquals(1.0, EconomyRules.debtFactor(0, 5));
      assertEquals(1.0, EconomyRules.debtFactor(100, 5));
      assertEquals(0.95, EconomyRules.debtFactor(-1, 1), 1e-9);
      assertEquals(0.6, EconomyRules.debtFactor(-1, 8), 1e-9);
      assertEquals(0.6, EconomyRules.debtFactor(-1, 999), 1e-9);
   }

   @Test
   void producerGrossAndCap() {
      assertEquals(60, EconomyRules.gross(new BuildingInfo("miner", 2, true, 1), P));
      assertEquals(120, EconomyRules.gross(new BuildingInfo("miner", 2, true, 2), P));
      // 3 workers at level 2 are 180, exactly the cap of 180; 5 workers are capped
      assertEquals(180, EconomyRules.gross(new BuildingInfo("miner", 2, true, 3), P));
      assertEquals(180, EconomyRules.gross(new BuildingInfo("miner", 2, true, 5), P));
      assertEquals(0, EconomyRules.gross(new BuildingInfo("miner", 2, false, 1), P));
      assertEquals(0, EconomyRules.gross(new BuildingInfo("miner", 2, true, 0), P));
      assertEquals(0, EconomyRules.gross(new BuildingInfo("guardtower", 2, true, 3), P));
   }

   @Test
   void upkeep() {
      assertEquals(6, EconomyRules.upkeep(new BuildingInfo("guardtower", 2, true, 1), P));
      assertEquals(9, EconomyRules.upkeep(new BuildingInfo("townhall", 2, true, 0), P));
      assertEquals(0, EconomyRules.upkeep(new BuildingInfo("guardtower", 2, false, 1), P));
      assertEquals(0, EconomyRules.upkeep(new BuildingInfo("miner", 2, true, 1), P));
      assertEquals(0, EconomyRules.upkeep(new BuildingInfo("residence", 2, true, 1), P));
   }

   @Test
   void incomeRoundsOnTheSum() {
      // 3 buildings of gross 45 (workers 1, level 1): per-building 12% would floor to 5 each = 15; the sum is 135 -> 16
      List<BuildingInfo> b = List.of(new BuildingInfo("miner", 1, true, 1), new BuildingInfo("farmer", 1, true, 1), new BuildingInfo("lumberjack", 1, true, 1));
      Result r = EconomyRules.evaluate(b, 12, P);
      assertEquals(16, r.income());
      assertEquals(0, r.expense());
      assertEquals(3, r.lines().size());
   }

   @Test
   void typicalColonyIsAClearProfitAtTheDefaultTax() {
      List<BuildingInfo> b = new ArrayList<>();
      String[] producers = {"miner", "lumberjack", "farmer", "fisherman", "baker", "smeltery", "blacksmith", "cook"};
      for (String id : producers) {
         b.add(new BuildingInfo(id, 2, true, 1));
      }

      b.add(new BuildingInfo("townhall", 2, true, 0));
      for (String id : new String[]{"warehouse", "deliveryman", "guardtower", "hospital", "builder"}) {
         b.add(new BuildingInfo(id, 2, true, 1));
      }

      b.add(new BuildingInfo("residence", 2, true, 2));
      assertEquals(12, P.defaultTax());
      Result atDefault = EconomyRules.evaluate(b, P.defaultTax(), P);
      // gross 8 * 60 = 480; upkeep 9 + 5 * 6 = 39
      assertEquals(39, atDefault.expense());
      assertEquals(57, atDefault.income());
      assertTrue(atDefault.income() - atDefault.expense() >= 15);
      int tolerable = EconomyRules.tolerableRate(20);
      assertEquals(32, tolerable);
      assertEquals(153, EconomyRules.evaluate(b, tolerable, P).income());
   }

   @Test
   void prosperityBoundsAndMonotonic() {
      assertEquals(0.0, EconomyRules.prosperity(0, 0, 0), 1e-9);
      assertEquals(1.0, EconomyRules.prosperity(9, 10, 500), 1e-9);
      assertEquals(0.0, EconomyRules.prosperity(-3, -2, -4), 1e-9);
      assertEquals(1.0, EconomyRules.prosperity(99, 99, 99999), 1e-9);
      assertEquals(0.8, EconomyRules.priceMultiplier(0.0), 1e-9);
      assertEquals(1.7, EconomyRules.priceMultiplier(1.0), 1e-9);
      for (int base : new int[]{1, 3, 8, 25}) {
         int lastRetail = 0;
         int lastWholesale = 0;
         for (int step = 0; step <= 20; step++) {
            double s = EconomyRules.prosperity(step * 0.25, step * 0.5, step * 4);
            assertTrue(s >= 0.0 && s <= 1.0);
            int retail = EconomyRules.retailPrice(base, s);
            int wholesale = EconomyRules.wholesalePrice(base, s);
            assertTrue(retail >= lastRetail && wholesale >= lastWholesale);
            assertTrue(retail >= 1 && wholesale >= 1 && wholesale <= retail);
            lastRetail = retail;
            lastWholesale = wholesale;
         }
      }

      assertEquals(1, EconomyRules.retailPrice(1, 0.0));
      assertEquals(2, EconomyRules.retailPrice(1, 1.0));
      assertEquals(Math.round(25 * 1.7), EconomyRules.retailPrice(25, 1.0));
      assertEquals(Math.round(25 * 1.7 * 0.35), EconomyRules.wholesalePrice(25, 1.0));
      // a cheap good (base 2) must still react to prosperity
      assertTrue(EconomyRules.retailPrice(2, 1.0) > EconomyRules.retailPrice(2, 0.0));
   }

   @Test
   void feeAndBalance() {
      assertEquals(5, EconomyRules.fee(100, P));
      assertEquals(0, EconomyRules.fee(19, P));
      assertEquals(1, EconomyRules.fee(20, P));
      assertEquals(50_000, EconomyRules.fee(1_000_000, P));
      assertEquals(EconomyRules.BALANCE_LIMIT, EconomyRules.addBalance(EconomyRules.BALANCE_LIMIT, 5));
      assertEquals(-EconomyRules.BALANCE_LIMIT, EconomyRules.addBalance(-EconomyRules.BALANCE_LIMIT, -5));
      assertEquals(EconomyRules.BALANCE_LIMIT, EconomyRules.addBalance(0, Long.MAX_VALUE));
      assertEquals(-EconomyRules.BALANCE_LIMIT, EconomyRules.addBalance(0, Long.MIN_VALUE));
      assertEquals(EconomyRules.BALANCE_LIMIT, EconomyRules.clampBalance(Long.MAX_VALUE));
   }

   @Test
   void overflowIsDetected() {
      assertThrows(ArithmeticException.class, () -> EconomyRules.applyTax(Long.MAX_VALUE, 50));
      assertThrows(ArithmeticException.class, () -> EconomyRules.fee(Long.MAX_VALUE, P));
      assertFalse(EconomyRules.validPrice(0, P));
      assertTrue(EconomyRules.validPrice(1, P));
      assertTrue(EconomyRules.validPrice(1_000_000, P));
      assertFalse(EconomyRules.validPrice(1_000_001, P));
      assertTrue(EconomyRules.validRate(0, P));
      assertTrue(EconomyRules.validRate(50, P));
      assertFalse(EconomyRules.validRate(51, P));
      assertFalse(EconomyRules.validRate(-1, P));
   }
}
