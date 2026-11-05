package dev.colonyjournal.server;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import dev.colonyjournal.economy.EconomyConfig;
import dev.colonyjournal.economy.EconomyData;
import dev.colonyjournal.economy.EconomyRules;
import dev.colonyjournal.economy.EconomyService;
import dev.colonyjournal.economy.Goods;
import dev.colonyjournal.net.Payloads;
import dev.colonyjournal.net.Views.ColonyRef;
import dev.colonyjournal.net.Views.GoodView;
import dev.colonyjournal.net.Views.StatsRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side packet handlers. Handlers run on the main server thread. Every handler re-checks permission (colony
 * manager rank), that the colony and the offer exist, and rate limits; nothing from the client is trusted.
 */
public final class ServerHandlers {
   private static final int ECONOMY_WINDOW_TICKS = 20;
   private static final int ECONOMY_PER_WINDOW = 4;
   private static final int STATS_INTERVAL_TICKS = 40;
   private static final Map<UUID, long[]> ECONOMY_LIMIT = new HashMap<>();
   private static final Map<UUID, Long> STATS_LIMIT = new HashMap<>();
   private static final Map<UUID, Long> GOODS_LIMIT = new HashMap<>();
   private static final int MAX_GOODS_ENTRIES = 600;

   public static void clearLimits() {
      ECONOMY_LIMIT.clear();
      STATS_LIMIT.clear();
      GOODS_LIMIT.clear();
   }

   public static void forget(UUID player) {
      ECONOMY_LIMIT.remove(player);
      STATS_LIMIT.remove(player);
      GOODS_LIMIT.remove(player);
   }

   // ---- helpers ----

   private static ServerPlayer playerOf(IPayloadContext ctx) {
      return ctx.player() instanceof ServerPlayer sp ? sp : null;
   }

   public static void reply(ServerPlayer player, String key, boolean ok, String arg) {
      PacketDistributor.sendToPlayer(player, new Payloads.ActionResult(key, ok, arg));
   }

   private static void snapshot(ServerPlayer player, IColony colony) {
      MinecraftServer server = player.server;
      PacketDistributor.sendToPlayer(player, new Payloads.EconomySnapshot(EconomyService.view(server, colony, player, EconomyConfig.params())));
   }

   /** Hands the stack to the player; what does not fit is dropped at the feet. Returns true when everything fit. */
   public static boolean giveOrDrop(ServerPlayer player, ItemStack stack) {
      ItemStack rest = stack.copy();
      player.getInventory().add(rest);
      if (rest.isEmpty()) {
         return true;
      }

      player.drop(rest, false);
      return false;
   }

   /** Hands out claimable items (offers of deleted colonies) to one online player; returns how many items were given. */
   public static int deliverClaims(ServerPlayer player) {
      EconomyData data = EconomyService.data(player.server);
      if (data.claims(player.getUUID()).isEmpty()) {
         return 0;
      }

      int count = 0;
      for (ItemStack stack : data.takeClaims(player.getUUID())) {
         count += stack.getCount();
         giveOrDrop(player, stack);
      }

      reply(player, "collected", true, Integer.toString(count));
      return count;
   }

   /** Same for every online player, so a poster who is online does not have to relog to get the items back. */
   public static void deliverClaims(MinecraftServer server) {
      for (ServerPlayer online : server.getPlayerList().getPlayers()) {
         deliverClaims(online);
      }
   }

   /** Like {@link #playerOf} but also applies the per player action limit; null (after a too_fast reply) when exceeded. */
   private static ServerPlayer limitedPlayerOf(IPayloadContext ctx) {
      ServerPlayer player = playerOf(ctx);
      if (player != null && !economyAllowed(player)) {
         reply(player, "too_fast", false, "");
         return null;
      }

      return player;
   }

   private static boolean economyAllowed(ServerPlayer player) {
      long now = player.server.getTickCount();
      long[] w = ECONOMY_LIMIT.computeIfAbsent(player.getUUID(), k -> new long[]{now, 0});
      if (now - w[0] >= ECONOMY_WINDOW_TICKS || now < w[0]) {
         w[0] = now;
         w[1] = 0;
      }

      if (w[1] >= ECONOMY_PER_WINDOW) {
         return false;
      }

      w[1]++;
      return true;
   }

   // ---- handlers ----

   public static void onRequestEconomy(Payloads.RequestEconomy msg, IPayloadContext ctx) {
      ServerPlayer player = playerOf(ctx);
      if (player == null) {
         return;
      }

      if (!economyAllowed(player)) {
         reply(player, "too_fast", false, "");
         return;
      }

      IColony colony = EconomyService.resolve(msg.colony());
      if (colony == null) {
         reply(player, "no_colony", false, "");
         return;
      }

      snapshot(player, colony);
   }

   public static void onSetTax(Payloads.SetTax msg, IPayloadContext ctx) {
      ServerPlayer player = limitedPlayerOf(ctx);
      if (player == null) {
         return;
      }

      IColony colony = EconomyService.resolve(msg.colony());
      if (colony == null) {
         reply(player, "no_colony", false, "");
         return;
      }

      EconomyRules.Params p = EconomyConfig.params();
      if (!EconomyService.isManager(colony, player)) {
         reply(player, "no_permission", false, "");
         return;
      }

      if (!EconomyRules.validRate(msg.rate(), p)) {
         reply(player, "bad_rate", false, "");
         return;
      }

      ColonyRef ref = EconomyService.refOf(colony);
      EconomyData.Ledger ledger = EconomyService.data(player.server).ledger(ref, p.defaultTax());
      ledger.setTaxRate(msg.rate());
      EconomyService.syncHappiness(colony, ref, ledger, p, true);
      reply(player, "tax_set", true, Integer.toString(msg.rate()));
      snapshot(player, colony);
   }

   public static void onPostOffer(Payloads.PostOffer msg, IPayloadContext ctx) {
      ServerPlayer player = limitedPlayerOf(ctx);
      if (player == null) {
         return;
      }

      IColony colony = EconomyService.resolve(msg.colony());
      if (colony == null) {
         reply(player, "no_colony", false, "");
         return;
      }

      EconomyRules.Params p = EconomyConfig.params();
      if (!EconomyService.isManager(colony, player)) {
         reply(player, "no_permission", false, "");
         return;
      }

      if (!EconomyRules.validPrice(msg.price(), p)) {
         reply(player, "bad_price", false, "");
         return;
      }

      ItemStack hand = player.getMainHandItem();
      // Opening the journal with the item leaves it in the hand and the hotbar slot cannot change while the screen is open.
      if (hand.isEmpty() || hand.is(dev.colonyjournal.ColonyJournal.JOURNAL.get())) {
         reply(player, "no_item", false, "");
         return;
      }

      EconomyData data = EconomyService.data(player.server);
      ColonyRef ref = EconomyService.refOf(colony);
      if (data.offerCount(ref) >= p.maxOffers()) {
         reply(player, "too_many_offers", false, Integer.toString(p.maxOffers()));
         return;
      }

      // Escrow the whole stack: copy first, then empty the hand slot.
      ItemStack copy = hand.copy();
      player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      data.addOffer(new EconomyData.Offer(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName(), ref, colony.getName(), copy, msg.price()));
      reply(player, "posted", true, "");
      snapshot(player, colony);
   }

   public static void onBuyOffer(Payloads.BuyOffer msg, IPayloadContext ctx) {
      ServerPlayer player = limitedPlayerOf(ctx);
      if (player == null) {
         return;
      }

      IColony buyer = EconomyService.resolve(msg.buyer());
      if (buyer == null) {
         reply(player, "no_colony", false, "");
         return;
      }

      if (!EconomyService.isManager(buyer, player)) {
         reply(player, "no_permission", false, "");
         return;
      }

      EconomyRules.Params p = EconomyConfig.params();
      EconomyData data = EconomyService.data(player.server);
      EconomyData.Offer offer = data.offer(msg.offer());
      if (offer == null) {
         reply(player, "offer_gone", false, "");
         snapshot(player, buyer);
         return;
      }

      IColony seller = EconomyService.resolve(offer.colony());
      if (seller == null) {
         // The seller colony vanished without the delete event: give the item back to the poster instead of losing it.
         data.removeOffer(offer.id());
         data.addClaim(offer.poster(), offer.stack());
         deliverClaims(player.server);
         reply(player, "offer_gone", false, "");
         snapshot(player, buyer);
         return;
      }

      ColonyRef buyerRef = EconomyService.refOf(buyer);
      UUID buyerOwner = buyer.getPermissions().getOwner();
      if (offer.colony().equals(buyerRef) || buyerOwner != null && buyerOwner.equals(seller.getPermissions().getOwner()) || offer.poster().equals(player.getUUID())) {
         reply(player, "own_offer", false, "");
         return;
      }

      EconomyData.Ledger buyerLedger = data.ledger(buyerRef, p.defaultTax());
      long price = offer.price();
      if (buyerLedger.balance() < price) {
         reply(player, "no_funds", false, Long.toString(price - buyerLedger.balance()));
         return;
      }

      // Remove first: from here on nobody else can take this offer.
      if (data.removeOffer(offer.id()) == null) {
         reply(player, "offer_gone", false, "");
         return;
      }

      buyerLedger.setBalance(EconomyRules.addBalance(buyerLedger.balance(), -price));
      EconomyData.Ledger sellerLedger = data.ledger(offer.colony(), p.defaultTax());
      sellerLedger.setBalance(EconomyRules.addBalance(sellerLedger.balance(), price - EconomyRules.fee(price, p)));
      boolean fit = giveOrDrop(player, offer.stack());
      reply(player, "bought", true, "");
      if (!fit) {
         reply(player, "inv_full", false, "");
      }

      snapshot(player, buyer);
   }

   public static void onCancelOffer(Payloads.CancelOffer msg, IPayloadContext ctx) {
      ServerPlayer player = limitedPlayerOf(ctx);
      if (player == null) {
         return;
      }

      EconomyData data = EconomyService.data(player.server);
      EconomyData.Offer offer = data.offer(msg.offer());
      if (offer == null) {
         reply(player, "offer_gone", false, "");
         return;
      }

      boolean allowed = offer.poster().equals(player.getUUID());
      IColony seller = EconomyService.resolve(offer.colony());
      if (!allowed && seller != null) {
         allowed = EconomyService.isManager(seller, player);
      }

      if (!allowed) {
         reply(player, "no_permission", false, "");
         return;
      }

      if (data.removeOffer(offer.id()) == null) {
         reply(player, "offer_gone", false, "");
         return;
      }

      boolean fit = giveOrDrop(player, offer.stack());
      reply(player, "cancelled", true, "");
      if (!fit) {
         reply(player, "inv_full", false, "");
      }

      if (seller != null) {
         snapshot(player, seller);
      }
   }

   public static void onRequestGoods(Payloads.RequestGoods msg, IPayloadContext ctx) {
      ServerPlayer player = playerOf(ctx);
      if (player == null) {
         return;
      }

      long now = player.server.getTickCount();
      Long last = GOODS_LIMIT.get(player.getUUID());
      if (last != null && now >= last && now - last < STATS_INTERVAL_TICKS) {
         return; // silent: the client polls on a timer, an error line would only be noise
      }

      GOODS_LIMIT.put(player.getUUID(), now);
      EconomyData data = EconomyService.data(player.server);
      List<GoodView> goods = new ArrayList<>();
      for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
         try {
            ColonyRef ref = EconomyService.refOf(colony);
            EconomyData.Ledger ledger = data.peekLedger(ref);
            if (ledger == null) {
               continue;
            }

            Map<String, Integer> stock = ledger.stock();
            if (stock.isEmpty()) {
               continue;
            }

            double score = EconomyService.prosperity(colony);
            int pct = (int)Math.round(score * 100.0);
            boolean own = EconomyService.isManager(colony, player);
            String owner = colony.getPermissions().getOwnerName();
            for (Map.Entry<String, Integer> e : stock.entrySet()) {
               int base = Goods.basePrice(e.getKey());
               if (e.getValue() > 0 && base > 0) {
                  goods.add(new GoodView(ref, colony.getName(), owner == null ? "" : owner, e.getKey(), e.getValue(), EconomyRules.retailPrice(base, score), pct, own));
               }
            }
         } catch (Exception e) {
            dev.colonyjournal.ColonyJournal.LOGGER.warn("Colony Journal goods: colony {} skipped", colony.getID(), e);
         }
      }

      goods.sort((a, b) -> Integer.compare(b.stock(), a.stock()));
      if (goods.size() > MAX_GOODS_ENTRIES) {
         goods = new ArrayList<>(goods.subList(0, MAX_GOODS_ENTRIES));
      }

      PacketDistributor.sendToPlayer(player, new Payloads.GoodsSnapshot(goods));
   }

   public static void onBuyGoods(Payloads.BuyGoods msg, IPayloadContext ctx) {
      ServerPlayer player = limitedPlayerOf(ctx);
      if (player == null) {
         return;
      }

      IColony seller = EconomyService.resolve(msg.seller());
      IColony buyer = EconomyService.resolve(msg.buyer());
      if (seller == null || buyer == null) {
         reply(player, "no_colony", false, "");
         return;
      }

      if (!EconomyService.isManager(buyer, player)) {
         reply(player, "no_permission", false, "");
         return;
      }

      // Never trust the client string: it must parse, name a real non-air item and be a known good.
      ResourceLocation id = msg.item() == null ? null : ResourceLocation.tryParse(msg.item());
      Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
      if (item == null || item == net.minecraft.world.item.Items.AIR || msg.units() < 1 || msg.units() > 64) {
         reply(player, "bad_goods", false, "");
         return;
      }

      String key = id.toString();
      int base = Goods.basePrice(key);
      EconomyData data = EconomyService.data(player.server);
      EconomyData.Ledger sellerLedger = data.peekLedger(EconomyService.refOf(seller));
      int stock = sellerLedger == null ? 0 : sellerLedger.stockOf(key);
      if (base <= 0 || stock <= 0) {
         reply(player, "bad_goods", false, "");
         return;
      }

      if (msg.units() > stock) {
         reply(player, "no_stock", false, Integer.toString(stock));
         return;
      }

      EconomyRules.Params p = EconomyConfig.params();
      int unitPrice = EconomyRules.retailPrice(base, EconomyService.prosperity(seller));
      if (unitPrice > msg.maxUnitPrice()) {
         reply(player, "price_changed", false, Integer.toString(unitPrice));
         return;
      }

      long price = (long)msg.units() * (long)unitPrice;
      EconomyData.Ledger buyerLedger = data.ledger(EconomyService.refOf(buyer), p.defaultTax());
      if (buyerLedger.balance() < price) {
         reply(player, "no_funds", false, Long.toString(price - buyerLedger.balance()));
         return;
      }

      // All state changes first, items last.
      sellerLedger.setStock(key, stock - msg.units());
      buyerLedger.setBalance(EconomyRules.addBalance(buyerLedger.balance(), -price));
      // Buying from a colony of the same owner pays nobody: the whole price is destroyed, so the goods are no free faucet.
      UUID buyerOwner = buyer.getPermissions().getOwner();
      boolean sameOwner = buyerOwner != null && buyerOwner.equals(seller.getPermissions().getOwner());
      if (!sameOwner) {
         sellerLedger.setBalance(EconomyRules.addBalance(sellerLedger.balance(), price - EconomyRules.fee(price, p)));
      }

      boolean fit = true;
      int left = msg.units();
      int maxStack = Math.max(1, item.getDefaultMaxStackSize());
      while (left > 0) {
         int n = Math.min(left, maxStack);
         fit &= giveOrDrop(player, new ItemStack(item, n));
         left -= n;
      }

      reply(player, "bought", true, Long.toString(price));
      if (!fit) {
         reply(player, "inv_full", false, "");
      }

      snapshot(player, buyer);
   }

   public static void onRequestStats(Payloads.RequestStats msg, IPayloadContext ctx) {
      ServerPlayer player = playerOf(ctx);
      if (player == null) {
         return;
      }

      long now = player.server.getTickCount();
      Long last = STATS_LIMIT.get(player.getUUID());
      if (last != null && now >= last && now - last < STATS_INTERVAL_TICKS) {
         return; // silent: the client polls on a timer, an error line would only be noise
      }

      STATS_LIMIT.put(player.getUUID(), now);
      EconomyRules.Params p = EconomyConfig.params();
      EconomyData data = EconomyService.data(player.server);
      List<StatsRow> rows = new ArrayList<>();
      for (IColony colony : IMinecoloniesAPI.getInstance().getColonyManager().getAllColonies()) {
         try {
            rows.add(statsRow(data, colony, player, p));
         } catch (Exception e) {
            dev.colonyjournal.ColonyJournal.LOGGER.warn("Colony Journal stats: colony {} skipped", colony.getID(), e);
         }
      }

      PacketDistributor.sendToPlayer(player, new Payloads.StatsSnapshot(rows));
   }

   private static StatsRow statsRow(EconomyData data, IColony colony, Player viewer, EconomyRules.Params p) {
      ColonyRef ref = EconomyService.refOf(colony);
      EconomyData.Ledger ledger = data.peekLedger(ref);
      EconomyService.Projection pr = EconomyService.project(colony, ledger, p);
      List<EconomyRules.BuildingInfo> buildings = EconomyService.buildings(colony);
      int built = 0;
      long levels = 0L;
      for (EconomyRules.BuildingInfo b : buildings) {
         if (b.built()) {
            built++;
            levels += b.level();
         }
      }

      return new StatsRow(
         ref, colony.getName(), colony.getPermissions().getOwnerName(), pr.citizens(), colony.getCitizenManager().getMaxCitizens(),
         (float)colony.getOverallHappiness(), ledger == null ? 0L : ledger.balance(), pr.result().income(), pr.result().expense(),
         ledger == null ? p.defaultTax() : ledger.taxRate(), buildings.size(), built == 0 ? 0.0F : (float)levels / (float)built, colony.getDay(),
         EconomyService.isManager(colony, viewer)
      );
   }

   private ServerHandlers() {
   }
}
