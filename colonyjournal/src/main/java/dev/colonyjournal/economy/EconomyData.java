package dev.colonyjournal.economy;

import dev.colonyjournal.net.Views.ColonyRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent state of the economy: treasuries, the market and items waiting for pickup. ONE instance lives in the
 * overworld data storage. Every mutation goes through this class (or a {@link Ledger} it owns) and marks it dirty.
 */
public final class EconomyData extends SavedData {
   public static final String NAME = "colonyjournal_economy";
   public static final int HISTORY = 14;

   /** Treasury of one colony. */
   public static final class Ledger {
      private final Runnable onChange;
      private long balance;
      private int taxRate;
      private int lastDay = -1;
      private long lastIncome;
      private long lastExpense;
      private int debtDays;
      private long lastTrade;
      private final Map<String, Integer> stock = new LinkedHashMap<>();
      private final ArrayList<Long> history = new ArrayList<>();

      Ledger(Runnable onChange, int taxRate) {
         this.onChange = onChange;
         this.taxRate = taxRate;
      }

      public long balance() {
         return this.balance;
      }

      public void setBalance(long v) {
         this.balance = EconomyRules.clampBalance(v);
         this.onChange.run();
      }

      public int taxRate() {
         return this.taxRate;
      }

      public void setTaxRate(int v) {
         this.taxRate = v;
         this.onChange.run();
      }

      /** Day counter of the colony at the last settlement; -1 when the colony was never seen. */
      public int lastDay() {
         return this.lastDay;
      }

      public void setLastDay(int v) {
         this.lastDay = v;
         this.onChange.run();
      }

      public long lastIncome() {
         return this.lastIncome;
      }

      public long lastExpense() {
         return this.lastExpense;
      }

      public int debtDays() {
         return this.debtDays;
      }

      /** Coins earned on the last settled day by selling surplus goods automatically. */
      public long lastTrade() {
         return this.lastTrade;
      }

      /** Copy of the goods in stock: item id to units. */
      public Map<String, Integer> stock() {
         return new LinkedHashMap<>(this.stock);
      }

      public int stockOf(String item) {
         return this.stock.getOrDefault(item, 0);
      }

      /** Sets the stock of one item; zero or less removes it. */
      public void setStock(String item, int units) {
         if (units <= 0) {
            this.stock.remove(item);
         } else {
            this.stock.put(item, units);
         }

         this.onChange.run();
      }

      public void replaceStock(Map<String, Integer> newStock) {
         this.stock.clear();
         for (Map.Entry<String, Integer> e : newStock.entrySet()) {
            if (e.getValue() > 0) {
               this.stock.put(e.getKey(), e.getValue());
            }
         }

         this.onChange.run();
      }

      /** Credits coins earned outside a settlement (first production of a new colony). */
      public void bookTrade(long coins) {
         this.balance = EconomyRules.addBalance(this.balance, coins);
         this.lastTrade = coins;
         this.onChange.run();
      }

      /** Net coins of the last days, oldest first. */
      public List<Long> history() {
         return List.copyOf(this.history);
      }

      /** Books one settled day: moves the balance, records the net value and updates the debt counter. */
      public void settle(long income, long expense, long trade, int day, int debtFloor) {
         long net = income + trade - expense;
         long old = this.balance;
         long next = EconomyRules.addBalance(old, net);
         // The settlement never pushes the balance below the floor (a balance that is already lower stays where it is).
         this.balance = Math.max(next, Math.min(old, -(long)debtFloor));
         this.lastIncome = income;
         this.lastExpense = expense;
         this.lastTrade = trade;
         this.history.add(net);
         while (this.history.size() > HISTORY) {
            this.history.remove(0);
         }

         this.debtDays = this.balance < 0L ? Math.min(100, this.debtDays + 1) : 0;
         this.lastDay = day;
         this.onChange.run();
      }

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putLong("balance", this.balance);
         t.putInt("tax", this.taxRate);
         t.putInt("lastDay", this.lastDay);
         t.putLong("lastIncome", this.lastIncome);
         t.putLong("lastExpense", this.lastExpense);
         t.putInt("debtDays", this.debtDays);
         t.putLong("lastTrade", this.lastTrade);
         ListTag stockList = new ListTag();
         for (Map.Entry<String, Integer> e : this.stock.entrySet()) {
            CompoundTag st = new CompoundTag();
            st.putString("item", e.getKey());
            st.putInt("n", e.getValue());
            stockList.add(st);
         }

         t.put("stock", stockList);
         t.putLongArray("history", this.history.stream().mapToLong(Long::longValue).toArray());
         return t;
      }

      static Ledger load(CompoundTag t, Runnable onChange) {
         Ledger l = new Ledger(onChange, t.getInt("tax"));
         l.balance = EconomyRules.clampBalance(t.getLong("balance"));
         l.lastDay = t.getInt("lastDay");
         l.lastIncome = t.getLong("lastIncome");
         l.lastExpense = t.getLong("lastExpense");
         l.debtDays = t.getInt("debtDays");
         l.lastTrade = t.getLong("lastTrade");
         // Saves from before the goods market have no "stock" tag: that simply means an empty stock.
         ListTag stockList = t.getList("stock", Tag.TAG_COMPOUND);
         for (int i = 0; i < stockList.size(); i++) {
            CompoundTag st = stockList.getCompound(i);
            if (!st.getString("item").isEmpty() && st.getInt("n") > 0) {
               l.stock.put(st.getString("item"), st.getInt("n"));
            }
         }

         for (long v : t.getLongArray("history")) {
            l.history.add(v);
         }

         while (l.history.size() > HISTORY) {
            l.history.remove(0);
         }

         return l;
      }
   }

   /** A market offer. The stack is held in escrow here while the offer is open. */
   public record Offer(UUID id, UUID poster, String posterName, ColonyRef colony, String colonyName, ItemStack stack, int price) {
   }

   private final Map<ColonyRef, Ledger> ledgers = new LinkedHashMap<>();
   private final Map<UUID, Offer> offers = new LinkedHashMap<>();
   private final Map<UUID, List<ItemStack>> claims = new HashMap<>();

   public static SavedData.Factory<EconomyData> factory() {
      return new SavedData.Factory<>(EconomyData::new, EconomyData::load, null);
   }

   // ---- ledgers ----

   /** The ledger of a colony, created with the default tax when it does not exist. */
   public Ledger ledger(ColonyRef ref, int defaultTax) {
      Ledger l = this.ledgers.get(ref);
      if (l == null) {
         l = new Ledger(this::setDirty, defaultTax);
         this.ledgers.put(ref, l);
         this.setDirty();
      }

      return l;
   }

   /** The ledger of a colony or null; never creates one. */
   public Ledger peekLedger(ColonyRef ref) {
      return this.ledgers.get(ref);
   }

   public void removeLedger(ColonyRef ref) {
      if (this.ledgers.remove(ref) != null) {
         this.setDirty();
      }
   }

   // ---- market ----

   public Offer offer(UUID id) {
      return this.offers.get(id);
   }

   public Collection<Offer> offers() {
      return List.copyOf(this.offers.values());
   }

   public int offerCount(ColonyRef colony) {
      int n = 0;
      for (Offer o : this.offers.values()) {
         if (o.colony().equals(colony)) {
            n++;
         }
      }

      return n;
   }

   public void addOffer(Offer offer) {
      this.offers.put(offer.id(), offer);
      this.setDirty();
   }

   /** Removes and returns the offer, or null if it is already gone. */
   public Offer removeOffer(UUID id) {
      Offer o = this.offers.remove(id);
      if (o != null) {
         this.setDirty();
      }

      return o;
   }

   // ---- claims ----

   public void addClaim(UUID player, ItemStack stack) {
      if (!stack.isEmpty()) {
         this.claims.computeIfAbsent(player, k -> new ArrayList<>()).add(stack);
         this.setDirty();
      }
   }

   public List<ItemStack> claims(UUID player) {
      return List.copyOf(this.claims.getOrDefault(player, List.of()));
   }

   /** Removes and returns everything waiting for the player. */
   public List<ItemStack> takeClaims(UUID player) {
      List<ItemStack> list = this.claims.remove(player);
      if (list == null) {
         return List.of();
      }

      this.setDirty();
      return list;
   }

   /** Colony deleted: the ledger goes, the escrowed items of its offers move to the claim lists of the posters. */
   public int dissolveColony(ColonyRef ref) {
      int moved = 0;
      for (Offer o : new ArrayList<>(this.offers.values())) {
         if (o.colony().equals(ref)) {
            this.offers.remove(o.id());
            this.addClaim(o.poster(), o.stack());
            moved++;
         }
      }

      this.removeLedger(ref);
      this.setDirty();
      return moved;
   }

   // ---- persistence ----

   public static EconomyData load(CompoundTag root, HolderLookup.Provider registries) {
      EconomyData d = new EconomyData();
      ListTag ledgerList = root.getList("ledgers", Tag.TAG_COMPOUND);
      for (int i = 0; i < ledgerList.size(); i++) {
         CompoundTag t = ledgerList.getCompound(i);
         d.ledgers.put(new ColonyRef(t.getString("dim"), t.getInt("id")), Ledger.load(t, d::setDirty));
      }

      ListTag offerList = root.getList("offers", Tag.TAG_COMPOUND);
      for (int i = 0; i < offerList.size(); i++) {
         CompoundTag t = offerList.getCompound(i);
         ItemStack stack = ItemStack.parseOptional(registries, t.getCompound("stack"));
         if (stack.isEmpty()) {
            continue;
         }

         UUID id = NbtUtils.loadUUID(t.get("uuid"));
         d.offers.put(
            id,
            new Offer(id, NbtUtils.loadUUID(t.get("poster")), t.getString("posterName"), new ColonyRef(t.getString("dim"), t.getInt("colony")), t.getString("colonyName"), stack, t.getInt("price"))
         );
      }

      ListTag claimList = root.getList("claims", Tag.TAG_COMPOUND);
      for (int i = 0; i < claimList.size(); i++) {
         CompoundTag t = claimList.getCompound(i);
         List<ItemStack> items = new ArrayList<>();
         ListTag stacks = t.getList("items", Tag.TAG_COMPOUND);
         for (int j = 0; j < stacks.size(); j++) {
            ItemStack stack = ItemStack.parseOptional(registries, stacks.getCompound(j));
            if (!stack.isEmpty()) {
               items.add(stack);
            }
         }

         if (!items.isEmpty()) {
            d.claims.put(NbtUtils.loadUUID(t.get("player")), items);
         }
      }

      return d;
   }

   @Override
   public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
      ListTag ledgerList = new ListTag();
      for (Map.Entry<ColonyRef, Ledger> e : this.ledgers.entrySet()) {
         CompoundTag t = e.getValue().save();
         t.putString("dim", e.getKey().dim());
         t.putInt("id", e.getKey().id());
         ledgerList.add(t);
      }

      root.put("ledgers", ledgerList);
      ListTag offerList = new ListTag();
      for (Offer o : this.offers.values()) {
         CompoundTag t = new CompoundTag();
         t.put("uuid", NbtUtils.createUUID(o.id()));
         t.put("poster", NbtUtils.createUUID(o.poster()));
         t.putString("posterName", o.posterName());
         t.putString("dim", o.colony().dim());
         t.putInt("colony", o.colony().id());
         t.putString("colonyName", o.colonyName());
         t.put("stack", o.stack().saveOptional(registries));
         t.putInt("price", o.price());
         offerList.add(t);
      }

      root.put("offers", offerList);
      ListTag claimList = new ListTag();
      for (Map.Entry<UUID, List<ItemStack>> e : this.claims.entrySet()) {
         CompoundTag t = new CompoundTag();
         t.put("player", NbtUtils.createUUID(e.getKey()));
         ListTag stacks = new ListTag();
         for (ItemStack s : e.getValue()) {
            stacks.add(s.saveOptional(registries));
         }

         t.put("items", stacks);
         claimList.add(t);
      }

      root.put("claims", claimList);
      return root;
   }
}
