package dev.colonyjournal.net;

import dev.colonyjournal.client.ClientData;
import dev.colonyjournal.server.ServerHandlers;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers every payload. The version is required: client and server must run the same protocol. */
public final class Network {
   public static final String VERSION = "2";

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar(VERSION);
      r.playToServer(Payloads.RequestEconomy.TYPE, Payloads.RequestEconomy.CODEC, ServerHandlers::onRequestEconomy);
      r.playToServer(Payloads.SetTax.TYPE, Payloads.SetTax.CODEC, ServerHandlers::onSetTax);
      r.playToServer(Payloads.PostOffer.TYPE, Payloads.PostOffer.CODEC, ServerHandlers::onPostOffer);
      r.playToServer(Payloads.BuyOffer.TYPE, Payloads.BuyOffer.CODEC, ServerHandlers::onBuyOffer);
      r.playToServer(Payloads.CancelOffer.TYPE, Payloads.CancelOffer.CODEC, ServerHandlers::onCancelOffer);
      r.playToServer(Payloads.RequestStats.TYPE, Payloads.RequestStats.CODEC, ServerHandlers::onRequestStats);
      r.playToServer(Payloads.RequestGoods.TYPE, Payloads.RequestGoods.CODEC, ServerHandlers::onRequestGoods);
      r.playToServer(Payloads.BuyGoods.TYPE, Payloads.BuyGoods.CODEC, ServerHandlers::onBuyGoods);
      r.playToClient(Payloads.GoodsSnapshot.TYPE, Payloads.GoodsSnapshot.CODEC, ClientData::onGoods);
      r.playToClient(Payloads.EconomySnapshot.TYPE, Payloads.EconomySnapshot.CODEC, ClientData::onEconomy);
      r.playToClient(Payloads.StatsSnapshot.TYPE, Payloads.StatsSnapshot.CODEC, ClientData::onStats);
      r.playToClient(Payloads.ActionResult.TYPE, Payloads.ActionResult.CODEC, ClientData::onResult);
   }

   private Network() {
   }
}
