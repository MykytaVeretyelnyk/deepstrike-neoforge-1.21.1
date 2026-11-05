package dev.colonyjournal.net;

import dev.colonyjournal.ColonyJournal;
import dev.colonyjournal.net.Views.ColonyRef;
import dev.colonyjournal.net.Views.EconomyView;
import dev.colonyjournal.net.Views.GoodView;
import dev.colonyjournal.net.Views.StatsRow;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** All packets of the mod. Client to server first, then server to client. */
public final class Payloads {
   private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
      return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ColonyJournal.MOD_ID, path));
   }

   // ---- client to server ----

   /** Ask for the economy view of a colony (also the way to refresh it). */
   public record RequestEconomy(ColonyRef colony) implements CustomPacketPayload {
      public static final Type<RequestEconomy> TYPE = typeOf("request_economy");
      public static final StreamCodec<RegistryFriendlyByteBuf, RequestEconomy> CODEC = ColonyRef.CODEC.map(RequestEconomy::new, RequestEconomy::colony);

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Set the tax of a colony in percent (needs colony manager rank). */
   public record SetTax(ColonyRef colony, int rate) implements CustomPacketPayload {
      public static final Type<SetTax> TYPE = typeOf("set_tax");
      public static final StreamCodec<RegistryFriendlyByteBuf, SetTax> CODEC = StreamCodec.composite(
         ColonyRef.CODEC, SetTax::colony, ByteBufCodecs.VAR_INT, SetTax::rate, SetTax::new
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Put the stack in the main hand on the market for {@code price} coins; the colony of the poster is {@code colony}. */
   public record PostOffer(ColonyRef colony, int price) implements CustomPacketPayload {
      public static final Type<PostOffer> TYPE = typeOf("post_offer");
      public static final StreamCodec<RegistryFriendlyByteBuf, PostOffer> CODEC = StreamCodec.composite(
         ColonyRef.CODEC, PostOffer::colony, ByteBufCodecs.VAR_INT, PostOffer::price, PostOffer::new
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Buy an offer, paying from the treasury of {@code buyer}. */
   public record BuyOffer(UUID offer, ColonyRef buyer) implements CustomPacketPayload {
      public static final Type<BuyOffer> TYPE = typeOf("buy_offer");
      public static final StreamCodec<RegistryFriendlyByteBuf, BuyOffer> CODEC = StreamCodec.composite(
         UUIDUtil.STREAM_CODEC, BuyOffer::offer, ColonyRef.CODEC, BuyOffer::buyer, BuyOffer::new
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Withdraw an own offer; the stack goes back to the player. */
   public record CancelOffer(UUID offer) implements CustomPacketPayload {
      public static final Type<CancelOffer> TYPE = typeOf("cancel_offer");
      public static final StreamCodec<RegistryFriendlyByteBuf, CancelOffer> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, CancelOffer::offer, CancelOffer::new);

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Ask for the statistics of all colonies. The server rate-limits this. */
   public record RequestStats() implements CustomPacketPayload {
      public static final Type<RequestStats> TYPE = typeOf("request_stats");
      public static final StreamCodec<RegistryFriendlyByteBuf, RequestStats> CODEC = StreamCodec.unit(new RequestStats());

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Ask for the goods all colonies currently offer. The server rate-limits this. */
   public record RequestGoods() implements CustomPacketPayload {
      public static final Type<RequestGoods> TYPE = typeOf("request_goods");
      public static final StreamCodec<RegistryFriendlyByteBuf, RequestGoods> CODEC = StreamCodec.unit(new RequestGoods());

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /**
    * Buy {@code units} (1..64) of {@code item} from the stock of {@code seller}, paying from the treasury of {@code buyer}.
    * {@code maxUnitPrice} is the price the player saw; the server refuses (price_changed) if the live price is higher.
    */
   public record BuyGoods(ColonyRef seller, String item, int units, ColonyRef buyer, int maxUnitPrice) implements CustomPacketPayload {
      public static final Type<BuyGoods> TYPE = typeOf("buy_goods");
      public static final StreamCodec<RegistryFriendlyByteBuf, BuyGoods> CODEC = StreamCodec.composite(
         ColonyRef.CODEC, BuyGoods::seller, ByteBufCodecs.STRING_UTF8, BuyGoods::item, ByteBufCodecs.VAR_INT, BuyGoods::units, ColonyRef.CODEC, BuyGoods::buyer,
         ByteBufCodecs.VAR_INT, BuyGoods::maxUnitPrice, BuyGoods::new
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   // ---- server to client ----

   public record EconomySnapshot(EconomyView view) implements CustomPacketPayload {
      public static final Type<EconomySnapshot> TYPE = typeOf("economy_snapshot");
      public static final StreamCodec<RegistryFriendlyByteBuf, EconomySnapshot> CODEC = EconomyView.CODEC.map(EconomySnapshot::new, EconomySnapshot::view);

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record GoodsSnapshot(List<GoodView> goods) implements CustomPacketPayload {
      public static final Type<GoodsSnapshot> TYPE = typeOf("goods_snapshot");
      public static final StreamCodec<RegistryFriendlyByteBuf, GoodsSnapshot> CODEC = StreamCodec.of(
         (buf, v) -> GoodView.writeList(buf, v.goods), buf -> new GoodsSnapshot(GoodView.readList(buf))
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public record StatsSnapshot(List<StatsRow> rows) implements CustomPacketPayload {
      public static final Type<StatsSnapshot> TYPE = typeOf("stats_snapshot");
      public static final StreamCodec<RegistryFriendlyByteBuf, StatsSnapshot> CODEC = StreamCodec.of(
         (buf, v) -> StatsRow.writeList(buf, v.rows), buf -> new StatsSnapshot(StatsRow.readList(buf))
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /**
    * Result of an action, shown as a message. The text is the lang key {@code msg.colonyjournal.<key>} with {@code arg}
    * as its only parameter (may be empty).
    */
   public record ActionResult(String key, boolean ok, String arg) implements CustomPacketPayload {
      public static final Type<ActionResult> TYPE = typeOf("action_result");
      public static final StreamCodec<RegistryFriendlyByteBuf, ActionResult> CODEC = StreamCodec.composite(
         ByteBufCodecs.STRING_UTF8, ActionResult::key, ByteBufCodecs.BOOL, ActionResult::ok, ByteBufCodecs.STRING_UTF8, ActionResult::arg, ActionResult::new
      );

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   private Payloads() {
   }
}
