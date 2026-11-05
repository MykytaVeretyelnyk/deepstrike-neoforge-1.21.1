package dev.colonyjournal.client;

import dev.colonyjournal.net.Payloads;
import dev.colonyjournal.net.Views.EconomyView;
import dev.colonyjournal.net.Views.StatsRow;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Last data received from the server. Must not reference client-only classes at class load, because the network
 * registration references it on the dedicated server too.
 */
public final class ClientData {
   public static volatile EconomyView economy;
   public static volatile List<StatsRow> stats = List.of();
   public static volatile List<dev.colonyjournal.net.Views.GoodView> goods = List.of();
   public static volatile Payloads.ActionResult lastResult;
   private static final AtomicInteger RESULT_SERIAL = new AtomicInteger();

   public static void onEconomy(Payloads.EconomySnapshot msg, IPayloadContext ctx) {
      economy = msg.view();
   }

   public static void onStats(Payloads.StatsSnapshot msg, IPayloadContext ctx) {
      stats = msg.rows();
   }

   public static void onGoods(Payloads.GoodsSnapshot msg, IPayloadContext ctx) {
      goods = msg.goods();
   }

   public static void onResult(Payloads.ActionResult msg, IPayloadContext ctx) {
      lastResult = msg;
      RESULT_SERIAL.incrementAndGet();
   }

   /** Changes every time a new action result arrives, so the screen can tell a fresh message from an old one. */
   public static int resultSerial() {
      return RESULT_SERIAL.get();
   }

   private ClientData() {
   }
}
