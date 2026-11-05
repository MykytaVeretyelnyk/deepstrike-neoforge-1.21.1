package dev.colonyjournal.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Plain data sent from the server to the journal screen. Money is in coins; rates are whole percents. */
public final class Views {
   /** Colony identity: dimension id string (for example "minecraft:overworld") plus the MineColonies colony id. */
   public record ColonyRef(String dim, int id) {
      public static final StreamCodec<RegistryFriendlyByteBuf, ColonyRef> CODEC = StreamCodec.of(
         (buf, v) -> {
            buf.writeUtf(v.dim);
            buf.writeVarInt(v.id);
         },
         buf -> new ColonyRef(buf.readUtf(), buf.readVarInt())
      );
   }

   /**
    * One row of the income/expense table.
    *
    * @param amount coins per day: positive = income from producers, negative = upkeep of services
    */
   public record LineView(String buildingId, int count, int levels, int workers, long amount) {
      static void write(RegistryFriendlyByteBuf buf, LineView v) {
         buf.writeUtf(v.buildingId);
         buf.writeVarInt(v.count);
         buf.writeVarInt(v.levels);
         buf.writeVarInt(v.workers);
         buf.writeLong(v.amount);
      }

      static LineView read(RegistryFriendlyByteBuf buf) {
         return new LineView(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readLong());
      }
   }

   /** A market offer. {@code own} is true when the viewer's own colony posted it (cancel instead of buy). */
   public record OfferView(UUID id, String sellerName, String colonyName, ItemStack stack, int price, boolean own) {
      static void write(RegistryFriendlyByteBuf buf, OfferView v) {
         buf.writeUUID(v.id);
         buf.writeUtf(v.sellerName);
         buf.writeUtf(v.colonyName);
         ItemStack.STREAM_CODEC.encode(buf, v.stack);
         buf.writeVarInt(v.price);
         buf.writeBoolean(v.own);
      }

      static OfferView read(RegistryFriendlyByteBuf buf) {
         return new OfferView(buf.readUUID(), buf.readUtf(), buf.readUtf(), ItemStack.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readBoolean());
      }
   }

   /**
    * Goods a colony offers by itself, produced by its working buildings. The price rises with the prosperity of the colony.
    *
    * @param item          item registry id, for example "minecraft:iron_ingot"
    * @param stock         units in stock right now
    * @param unitPrice     coins per unit for a buyer
    * @param prosperityPct prosperity of the seller colony in percent (0..100)
    * @param own           the viewer manages (or owns) the seller colony
    */
   public record GoodView(ColonyRef colony, String colonyName, String ownerName, String item, int stock, int unitPrice, int prosperityPct, boolean own) {
      static void write(RegistryFriendlyByteBuf buf, GoodView v) {
         ColonyRef.CODEC.encode(buf, v.colony);
         buf.writeUtf(v.colonyName);
         buf.writeUtf(v.ownerName);
         buf.writeUtf(v.item);
         buf.writeVarInt(v.stock);
         buf.writeVarInt(v.unitPrice);
         buf.writeVarInt(v.prosperityPct);
         buf.writeBoolean(v.own);
      }

      static GoodView read(RegistryFriendlyByteBuf buf) {
         return new GoodView(ColonyRef.CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
      }

      public static void writeList(RegistryFriendlyByteBuf buf, List<GoodView> goods) {
         buf.writeVarInt(goods.size());
         goods.forEach(g -> write(buf, g));
      }

      public static List<GoodView> readList(RegistryFriendlyByteBuf buf) {
         int n = buf.readVarInt();
         List<GoodView> goods = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            goods.add(read(buf));
         }

         return goods;
      }
   }

   /**
    * Economy of one colony.
    *
    * @param taxRate       current tax in percent
    * @param tolerableRate tax rate at which citizens are neutral for the current colony size
    * @param maxRate       upper bound the slider may use
    * @param taxFactor     happiness factor of the tax, 0.3..1.15 (1.0 = neutral, above = bonus for a low tax)
    * @param income        projected coins per day from producers after tax
    * @param expense       projected coins per day for upkeep (positive number)
    * @param history       net coins of the last days, oldest first
    * @param offers        the whole market, own offers included
    * @param prosperity    0..1 score of the colony (building levels, happiness, size); it drives the prices of its goods
    * @param tradeIncome   coins the colony earned on the last settled day by selling surplus goods automatically
    */
   public record EconomyView(
      ColonyRef colony, String colonyName, long balance, int taxRate, int tolerableRate, int maxRate, double taxFactor,
      long income, long expense, boolean canManage, List<LineView> lines, List<Long> history, List<OfferView> offers,
      double prosperity, long tradeIncome
   ) {
      public static final StreamCodec<RegistryFriendlyByteBuf, EconomyView> CODEC = StreamCodec.of(
         (buf, v) -> {
            ColonyRef.CODEC.encode(buf, v.colony);
            buf.writeUtf(v.colonyName);
            buf.writeLong(v.balance);
            buf.writeVarInt(v.taxRate);
            buf.writeVarInt(v.tolerableRate);
            buf.writeVarInt(v.maxRate);
            buf.writeDouble(v.taxFactor);
            buf.writeLong(v.income);
            buf.writeLong(v.expense);
            buf.writeBoolean(v.canManage);
            buf.writeVarInt(v.lines.size());
            v.lines.forEach(l -> LineView.write(buf, l));
            buf.writeVarInt(v.history.size());
            v.history.forEach(buf::writeLong);
            buf.writeVarInt(v.offers.size());
            v.offers.forEach(o -> OfferView.write(buf, o));
            buf.writeDouble(v.prosperity);
            buf.writeLong(v.tradeIncome);
         },
         buf -> {
            ColonyRef colony = ColonyRef.CODEC.decode(buf);
            String name = buf.readUtf();
            long balance = buf.readLong();
            int tax = buf.readVarInt();
            int tolerable = buf.readVarInt();
            int max = buf.readVarInt();
            double factor = buf.readDouble();
            long income = buf.readLong();
            long expense = buf.readLong();
            boolean manage = buf.readBoolean();
            int n = buf.readVarInt();
            List<LineView> lines = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
               lines.add(LineView.read(buf));
            }

            n = buf.readVarInt();
            List<Long> history = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
               history.add(buf.readLong());
            }

            n = buf.readVarInt();
            List<OfferView> offers = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
               offers.add(OfferView.read(buf));
            }

            double prosperity = buf.readDouble();
            long trade = buf.readLong();
            return new EconomyView(colony, name, balance, tax, tolerable, max, factor, income, expense, manage, lines, history, offers, prosperity, trade);
         }
      );
   }

   /** One row of the statistics tab: a colony of any player on the server. */
   public record StatsRow(
      ColonyRef colony, String name, String owner, int citizens, int maxCitizens, float happiness, long balance,
      long income, long expense, int taxRate, int buildings, float avgLevel, int day, boolean own
   ) {
      static void write(RegistryFriendlyByteBuf buf, StatsRow v) {
         ColonyRef.CODEC.encode(buf, v.colony);
         buf.writeUtf(v.name);
         buf.writeUtf(v.owner);
         buf.writeVarInt(v.citizens);
         buf.writeVarInt(v.maxCitizens);
         buf.writeFloat(v.happiness);
         buf.writeLong(v.balance);
         buf.writeLong(v.income);
         buf.writeLong(v.expense);
         buf.writeVarInt(v.taxRate);
         buf.writeVarInt(v.buildings);
         buf.writeFloat(v.avgLevel);
         buf.writeVarInt(v.day);
         buf.writeBoolean(v.own);
      }

      static StatsRow read(RegistryFriendlyByteBuf buf) {
         return new StatsRow(
            ColonyRef.CODEC.decode(buf), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readLong(),
            buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()
         );
      }

      public static void writeList(RegistryFriendlyByteBuf buf, List<StatsRow> rows) {
         buf.writeVarInt(rows.size());
         rows.forEach(r -> write(buf, r));
      }

      public static List<StatsRow> readList(RegistryFriendlyByteBuf buf) {
         int n = buf.readVarInt();
         List<StatsRow> rows = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            rows.add(read(buf));
         }

         return rows;
      }
   }

   private Views() {
   }
}
