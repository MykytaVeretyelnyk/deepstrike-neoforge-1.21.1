package dev.colonyjournal.economy;

import dev.colonyjournal.graph.BuildingGraph;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The economy formulas as pure static functions on plain records, so they can be unit tested without Minecraft.
 * Money is in whole coins and every sum is overflow checked.
 */
public final class EconomyRules {
   public enum Kind {
      /** Gathers or crafts goods: earns taxable income. */
      PRODUCER,
      /** Costs upkeep: town hall, warehouse, couriers and the layer 3 consumers. */
      SERVICE,
      /** Does not take part (residences and so on). */
      NEUTRAL
   }

   /** Tunable numbers. {@link #DEFAULT} is what the server config starts with. */
   public record Params(
      int producerBase, int producerPerLevel, int producerCapPerLevel, int upkeepBase, int upkeepPerLevel, int townhallBase, int townhallPerLevel,
      int defaultTax, int maxRate, int feePercent, int maxOffers, int maxPrice, int debtFloor
   ) {
      public static final Params DEFAULT = new Params(30, 15, 90, 2, 2, 3, 3, 12, 50, 5, 8, 1_000_000, 500);
   }

   /** What the rules need to know about one building. */
   public record BuildingInfo(String id, int level, boolean built, int workers) {
   }

   /** One aggregated row per building type; {@code amount} is positive for income after tax, negative for upkeep. */
   public record Line(String id, int count, int levels, int workers, long amount) {
   }

   public record Result(long income, long expense, List<Line> lines) {
   }

   /** Absolute limit of a treasury, in both directions. */
   public static final long BALANCE_LIMIT = 1_000_000_000L;
   public static final int MIN_TOLERABLE = 10;
   public static final int MAX_TOLERABLE = 40;
   private static final Set<String> SERVICE_EXTRA = Set.of("townhall", "warehouse", "deliveryman");

   public static Kind kind(String id) {
      BuildingGraph.Node node = BuildingGraph.NODES.get(id);
      if (node != null) {
         return node.layer() <= 2 ? Kind.PRODUCER : Kind.SERVICE;
      }

      return SERVICE_EXTRA.contains(id) ? Kind.SERVICE : Kind.NEUTRAL;
   }

   /** Gross output of one building per day, before tax. */
   public static long gross(BuildingInfo b, Params p) {
      if (kind(b.id()) != Kind.PRODUCER || !b.built() || b.workers() < 1 || b.level() < 1) {
         return 0L;
      }

      long perWorker = Math.addExact((long)p.producerBase(), Math.multiplyExact((long)p.producerPerLevel(), (long)b.level()));
      long raw = Math.multiplyExact((long)b.workers(), perWorker);
      return Math.min(raw, Math.multiplyExact((long)p.producerCapPerLevel(), (long)b.level()));
   }

   /** Upkeep of one building per day (positive number). */
   public static long upkeep(BuildingInfo b, Params p) {
      if (kind(b.id()) != Kind.SERVICE || !b.built() || b.level() < 1) {
         return 0L;
      }

      boolean hall = b.id().equals("townhall");
      long base = hall ? p.townhallBase() : p.upkeepBase();
      long perLevel = hall ? p.townhallPerLevel() : p.upkeepPerLevel();
      return Math.addExact(base, Math.multiplyExact(perLevel, (long)b.level()));
   }

   /** floor(gross * rate / 100). The sum is taken first so no coin is lost to per-building rounding. */
   public static long applyTax(long gross, int rate) {
      return Math.floorDiv(Math.multiplyExact(gross, (long)rate), 100L);
   }

   public static Result evaluate(List<BuildingInfo> buildings, int taxRate, Params p) {
      Map<String, long[]> rows = new LinkedHashMap<>();
      long grossSum = 0L;
      long expense = 0L;
      for (BuildingInfo b : buildings) {
         if (kind(b.id()) == Kind.NEUTRAL) {
            continue;
         }

         // row: count, levels, workers, gross, upkeep
         long[] row = rows.computeIfAbsent(b.id(), k -> new long[5]);
         row[0]++;
         row[1] += b.level();
         row[2] += b.workers();
         long g = gross(b, p);
         long u = upkeep(b, p);
         row[3] = Math.addExact(row[3], g);
         row[4] = Math.addExact(row[4], u);
         grossSum = Math.addExact(grossSum, g);
         expense = Math.addExact(expense, u);
      }

      List<Line> lines = new ArrayList<>();
      for (Map.Entry<String, long[]> e : rows.entrySet()) {
         long[] r = e.getValue();
         long amount = kind(e.getKey()) == Kind.PRODUCER ? applyTax(r[3], taxRate) : -r[4];
         lines.add(new Line(e.getKey(), (int)r[0], (int)r[1], (int)r[2], amount));
      }

      return new Result(applyTax(grossSum, taxRate), expense, lines);
   }

   /** Tax rate at which citizens are neutral: 40 for a tiny colony, falling to 10 for large ones. */
   public static int tolerableRate(int citizens) {
      int v = (int)Math.round(40.0 - 0.4 * (double)Math.max(0, citizens));
      return Math.max(MIN_TOLERABLE, Math.min(MAX_TOLERABLE, v));
   }

   /** Happiness factor of the tax: 1.0 at the tolerable rate, up to 1.10 below it, down to 0.5 above it. */
   public static double taxFactor(int rate, int tolerable, int maxRate) {
      if (rate <= tolerable) {
         return 1.0 + 0.10 * (double)(tolerable - rate) / (double)Math.max(1, tolerable);
      }

      double span = Math.max(1, maxRate - tolerable);
      return Math.max(0.5, 1.0 - 0.5 * (double)(rate - tolerable) / span);
   }

   /** Separate happiness factor of an empty treasury. Exactly 1.0 when not in debt (MineColonies skips such factors). */
   public static double debtFactor(long balance, int debtDays) {
      if (balance >= 0L) {
         return 1.0;
      }

      return Math.max(0.6, 1.0 - 0.05 * (double)Math.min(8, Math.max(0, debtDays)));
   }

   // ---- prosperity and prices ----

   /** 0..1 score from the average building level, happiness (0..10) and the citizen count. */
   public static double prosperity(double avgBuiltLevel, double happiness, int citizens) {
      double score = 0.4 * Math.min(1.0, Math.max(0.0, avgBuiltLevel) / 5.0)
         + 0.4 * (Math.max(0.0, Math.min(10.0, happiness)) / 10.0)
         + 0.2 * Math.min(1.0, (double)Math.max(0, citizens) / 80.0);
      return Math.max(0.0, Math.min(1.0, score));
   }

   /** Price multiplier of a colony: 0.8 for a poor one up to 1.7 for a perfect one. */
   public static double priceMultiplier(double score) {
      return 0.8 + 0.9 * Math.max(0.0, Math.min(1.0, score));
   }

   /** Price of one unit for a buyer. */
   public static int retailPrice(int base, double score) {
      return (int)Math.max(1L, Math.round((double)base * priceMultiplier(score)));
   }

   /** Price of one unit when the colony sells its surplus automatically. */
   public static int wholesalePrice(int base, double score) {
      return (int)Math.max(1L, Math.round((double)base * priceMultiplier(score) * 0.35));
   }

   /** Market fee that is destroyed on every sale. */
   public static long fee(long price, Params p) {
      return Math.floorDiv(Math.multiplyExact(price, (long)p.feePercent()), 100L);
   }

   public static long clampBalance(long value) {
      return Math.max(-BALANCE_LIMIT, Math.min(BALANCE_LIMIT, value));
   }

   /** Adds to a treasury and clamps to the limit; never overflows. */
   public static long addBalance(long balance, long delta) {
      long d = Math.max(-2 * BALANCE_LIMIT, Math.min(2 * BALANCE_LIMIT, delta));
      return clampBalance(Math.addExact(clampBalance(balance), d));
   }

   public static boolean validPrice(long price, Params p) {
      return price >= 1L && price <= (long)p.maxPrice();
   }

   public static boolean validRate(int rate, Params p) {
      return rate >= 0 && rate <= p.maxRate();
   }

   private EconomyRules() {
   }
}
