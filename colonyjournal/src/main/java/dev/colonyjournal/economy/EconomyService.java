package dev.colonyjournal.economy;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessModifier;
import com.minecolonies.api.entity.citizen.happiness.StaticHappinessSupplier;
import dev.colonyjournal.ColonyJournal;
import dev.colonyjournal.net.Views.ColonyRef;
import dev.colonyjournal.net.Views.EconomyView;
import dev.colonyjournal.net.Views.LineView;
import dev.colonyjournal.net.Views.OfferView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Server side glue between the pure rules, the saved data and MineColonies. Everything runs on the main thread. */
public final class EconomyService {
   public static final String TAX_ID = "colonyjournal_tax";
   public static final String DEBT_ID = "colonyjournal_debt";
   private static final double MODIFIER_WEIGHT = 2.0;

   /** Projected numbers of a colony at its current tax rate. */
   public record Projection(EconomyRules.Result result, int citizens, int tolerable, double taxFactor, double debtFactor) {
   }

   /** Last factors written into the citizens of a colony; the happiness modifiers are re-applied only when these change. */
   private static final Map<ColonyRef, double[]> APPLIED = new HashMap<>();

   public static EconomyData data(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(EconomyData.factory(), EconomyData.NAME);
   }

   public static void clearCaches() {
      APPLIED.clear();
   }

   // ---- colony lookup ----

   public static ColonyRef refOf(IColony colony) {
      return new ColonyRef(colony.getDimension().location().toString(), colony.getID());
   }

   /** Resolves a client supplied colony reference; null when it is malformed or the colony does not exist. */
   public static IColony resolve(ColonyRef ref) {
      if (ref == null || ref.dim() == null) {
         return null;
      }

      ResourceLocation dim = ResourceLocation.tryParse(ref.dim());
      if (dim == null) {
         return null;
      }

      return IMinecoloniesAPI.getInstance().getColonyManager().getColonyByDimension(ref.id(), ResourceKey.create(Registries.DIMENSION, dim));
   }

   public static boolean isManager(IColony colony, Player player) {
      if (player.getUUID().equals(colony.getPermissions().getOwner())) {
         return true;
      }

      var rank = colony.getPermissions().getRank(player);
      return rank != null && rank.isColonyManager();
   }

   // ---- projection ----

   public static List<EconomyRules.BuildingInfo> buildings(IColony colony) {
      List<EconomyRules.BuildingInfo> list = new ArrayList<>();
      for (IBuilding b : colony.getServerBuildingManager().getBuildings().values()) {
         BuildingEntry type = b.getBuildingType();
         if (type == null) {
            continue;
         }

         list.add(new EconomyRules.BuildingInfo(type.getRegistryName().getPath(), b.getBuildingLevel(), b.isBuilt(), b.getAllAssignedCitizen().size()));
      }

      return list;
   }

   /** Projection for a colony; {@code ledger} may be null for a colony that has no ledger yet (defaults apply). */
   public static Projection project(IColony colony, EconomyData.Ledger ledger, EconomyRules.Params p) {
      int tax = ledger == null ? p.defaultTax() : ledger.taxRate();
      long balance = ledger == null ? 0L : ledger.balance();
      int debtDays = ledger == null ? 0 : ledger.debtDays();
      EconomyRules.Result res = EconomyRules.evaluate(buildings(colony), tax, p);
      int citizens = colony.getCitizenManager().getCurrentCitizenCount();
      int tolerable = EconomyRules.tolerableRate(citizens);
      return new Projection(res, citizens, tolerable, EconomyRules.taxFactor(tax, tolerable, p.maxRate()), EconomyRules.debtFactor(balance, debtDays));
   }

   /** Prosperity score 0..1 of a colony: average level of its built buildings, happiness and size. */
   public static double prosperity(IColony colony, List<EconomyRules.BuildingInfo> buildings) {
      int built = 0;
      long levels = 0L;
      for (EconomyRules.BuildingInfo b : buildings) {
         if (b.built()) {
            built++;
            levels += b.level();
         }
      }

      double avg = built == 0 ? 0.0 : (double)levels / (double)built;
      return EconomyRules.prosperity(avg, colony.getOverallHappiness(), colony.getCitizenManager().getCurrentCitizenCount());
   }

   public static double prosperity(IColony colony) {
      return prosperity(colony, buildings(colony));
   }

   public static EconomyView view(MinecraftServer server, IColony colony, ServerPlayer viewer, EconomyRules.Params p) {
      EconomyData data = data(server);
      ColonyRef ref = refOf(colony);
      EconomyData.Ledger ledger = data.peekLedger(ref);
      Projection pr = project(colony, ledger, p);
      List<LineView> lines = new ArrayList<>();
      for (EconomyRules.Line l : pr.result().lines()) {
         lines.add(new LineView(l.id(), l.count(), l.levels(), l.workers(), l.amount()));
      }

      UUID owner = colony.getPermissions().getOwner();
      List<OfferView> offers = new ArrayList<>();
      for (EconomyData.Offer o : data.offers()) {
         boolean own = o.colony().equals(ref);
         if (!own && owner != null) {
            IColony seller = resolve(o.colony());
            own = seller != null && owner.equals(seller.getPermissions().getOwner());
         }

         offers.add(new OfferView(o.id(), o.posterName(), o.colonyName(), o.stack().copy(), o.price(), own));
      }

      return new EconomyView(
         ref, colony.getName(), ledger == null ? 0L : ledger.balance(), ledger == null ? p.defaultTax() : ledger.taxRate(), pr.tolerable(), p.maxRate(),
         pr.taxFactor(), pr.result().income(), pr.result().expense(), isManager(colony, viewer), lines, ledger == null ? List.of() : ledger.history(), offers,
         prosperity(colony), ledger == null ? 0L : ledger.lastTrade()
      );
   }

   // ---- daily poll ----

   /** Called every 100 ticks for every colony. Settles at most one day and keeps the happiness modifiers in sync. */
   public static void pollColony(EconomyData data, IColony colony, EconomyRules.Params p) {
      if (!colony.isActive()) {
         return;
      }

      ColonyRef ref = refOf(colony);
      EconomyData.Ledger ledger = data.ledger(ref, p.defaultTax());
      int day = colony.getDay();
      if (ledger.lastDay() == -1) {
         ledger.setLastDay(day);
         // First sight: produce one day of goods so the market is not empty on day one.
         List<EconomyRules.BuildingInfo> first = buildings(colony);
         Goods.DayResult made = Goods.runDay(first, prosperity(colony, first), ledger.stock());
         ledger.replaceStock(made.stock());
         ledger.bookTrade(made.coins());
      } else if (day > ledger.lastDay()) {
         List<EconomyRules.BuildingInfo> list = buildings(colony);
         EconomyRules.Result res = EconomyRules.evaluate(list, ledger.taxRate(), p);
         Goods.DayResult made = Goods.runDay(list, prosperity(colony, list), ledger.stock());
         ledger.replaceStock(made.stock());
         ledger.settle(res.income(), res.expense(), made.coins(), day, p.debtFloor());
      } else if (day < ledger.lastDay()) {
         ledger.setLastDay(day);
      }

      syncHappiness(colony, ref, ledger, p, false);
   }

   // ---- happiness ----

   /**
    * Writes the tax and debt factors into the citizens. MineColonies drops unknown modifier ids when it reads a save, so
    * this runs when the factors change, on the first poll after a load, and for any citizen that lacks the modifiers.
    */
   public static void syncHappiness(IColony colony, ColonyRef ref, EconomyData.Ledger ledger, EconomyRules.Params p, boolean force) {
      Projection pr = project(colony, ledger, p);
      double[] last = APPLIED.get(ref);
      boolean changed = force || last == null || last[0] != pr.taxFactor() || last[1] != pr.debtFactor();
      for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
         ICitizenHappinessHandler h = citizen.getCitizenHappinessHandler();
         if (changed || h.getModifier(TAX_ID) == null || h.getModifier(DEBT_ID) == null) {
            applyTo(citizen, pr.taxFactor(), pr.debtFactor());
         }
      }

      APPLIED.put(ref, new double[]{pr.taxFactor(), pr.debtFactor()});
   }

   /** Applies the current factors to one citizen (new citizens). */
   public static void applyNewCitizen(MinecraftServer server, IColony colony, ICitizenData citizen) {
      EconomyRules.Params p = EconomyConfig.params();
      ColonyRef ref = refOf(colony);
      Projection pr = project(colony, data(server).peekLedger(ref), p);
      applyTo(citizen, pr.taxFactor(), pr.debtFactor());
   }

   private static void applyTo(ICitizenData citizen, double tax, double debt) {
      ICitizenHappinessHandler h = citizen.getCitizenHappinessHandler();
      h.addModifier(new StaticHappinessModifier(TAX_ID, MODIFIER_WEIGHT, new StaticHappinessSupplier(tax)));
      h.addModifier(new StaticHappinessModifier(DEBT_ID, MODIFIER_WEIGHT, new StaticHappinessSupplier(debt)));
      citizen.markDirty(0);
   }

   private static void logFailure(String what, Exception e) {
      ColonyJournal.LOGGER.warn("Colony Journal economy: {} failed", what, e);
   }

   /** Runs the poll for every colony; one broken colony never stops the others. */
   public static void pollAll(MinecraftServer server) {
      EconomyData data = data(server);
      EconomyRules.Params p = EconomyConfig.params();
      for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
         try {
            pollColony(data, colony, p);
         } catch (Exception e) {
            logFailure("poll of colony " + colony.getID(), e);
         }
      }
   }

   private EconomyService() {
   }
}
