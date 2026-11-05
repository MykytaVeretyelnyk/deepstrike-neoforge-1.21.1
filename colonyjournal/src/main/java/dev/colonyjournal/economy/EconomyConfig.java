package dev.colonyjournal.economy;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server config of the economy. Values are read through {@link #params()}, which falls back to the defaults before the config loads. */
public final class EconomyConfig {
   private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
   private static final EconomyRules.Params D = EconomyRules.Params.DEFAULT;

   // The "softEconomy" section is new: config files written by older versions hold the old (harsher) numbers under the top level
   // keys, which are now ignored, so the softer defaults really apply to existing worlds.
   private static final ModConfigSpec.IntValue PRODUCER_BASE = B.push("softEconomy").comment("Coins per working worker per day at building level 0 (before tax)").defineInRange("producerBase", D.producerBase(), 0, 10_000);
   private static final ModConfigSpec.IntValue PRODUCER_PER_LEVEL = B.comment("Extra coins per worker per building level").defineInRange("producerPerLevel", D.producerPerLevel(), 0, 10_000);
   private static final ModConfigSpec.IntValue PRODUCER_CAP = B.comment("Maximum gross coins per building and level").defineInRange("producerCapPerLevel", D.producerCapPerLevel(), 0, 100_000);
   private static final ModConfigSpec.IntValue UPKEEP_BASE = B.comment("Upkeep base of a service building").defineInRange("upkeepBase", D.upkeepBase(), 0, 10_000);
   private static final ModConfigSpec.IntValue UPKEEP_PER_LEVEL = B.comment("Upkeep per level of a service building").defineInRange("upkeepPerLevel", D.upkeepPerLevel(), 0, 10_000);
   private static final ModConfigSpec.IntValue HALL_BASE = B.comment("Upkeep base of the town hall").defineInRange("townhallBase", D.townhallBase(), 0, 10_000);
   private static final ModConfigSpec.IntValue HALL_PER_LEVEL = B.comment("Upkeep per level of the town hall").defineInRange("townhallPerLevel", D.townhallPerLevel(), 0, 10_000);
   private static final ModConfigSpec.IntValue DEFAULT_TAX = B.comment("Tax rate of a new colony in percent").defineInRange("defaultTax", D.defaultTax(), 0, 100);
   private static final ModConfigSpec.IntValue MAX_RATE = B.comment("Highest tax rate a player may set in percent").defineInRange("maxRate", D.maxRate(), 1, 100);
   private static final ModConfigSpec.IntValue FEE_PERCENT = B.comment("Share of a market price that is destroyed as fee").defineInRange("marketFeePercent", D.feePercent(), 0, 100);
   private static final ModConfigSpec.IntValue MAX_OFFERS = B.comment("Maximum open market offers per colony").defineInRange("maxOffersPerColony", D.maxOffers(), 1, 1000);
   private static final ModConfigSpec.IntValue MAX_PRICE = B.comment("Highest price of a market offer").defineInRange("maxPrice", D.maxPrice(), 1, 1_000_000_000);

   private static final ModConfigSpec.IntValue DEBT_FLOOR = B.comment("The daily settlement never pushes a treasury below minus this many coins").defineInRange("debtFloor", D.debtFloor(), 0, 1_000_000_000);

   public static final ModConfigSpec SPEC = B.pop().build();

   public static EconomyRules.Params params() {
      try {
         return new EconomyRules.Params(
            PRODUCER_BASE.get(), PRODUCER_PER_LEVEL.get(), PRODUCER_CAP.get(), UPKEEP_BASE.get(), UPKEEP_PER_LEVEL.get(), HALL_BASE.get(), HALL_PER_LEVEL.get(),
            DEFAULT_TAX.get(), MAX_RATE.get(), FEE_PERCENT.get(), MAX_OFFERS.get(), MAX_PRICE.get(), DEBT_FLOOR.get()
         );
      } catch (IllegalStateException e) {
         return D;
      }
   }

   private EconomyConfig() {
   }
}
