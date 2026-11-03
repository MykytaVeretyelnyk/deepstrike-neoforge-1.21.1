package dev.warmode.net;

import dev.warmode.WarMode;
import dev.warmode.game.WarManager;
import dev.warmode.shop.ShopEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WarNet {
   private static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath(WarMode.MOD_ID, path);
   }

   /** Round state, sent to every player once a second and after every change of the bank. */
   public record State(
      int team, int mode, boolean running, int blueBank, int redBank, int blueScore, int redScore, int secondsLeft, int blueHq, int redHq, int bankCap
   ) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<State> TYPE = new CustomPacketPayload.Type<>(id("state"));
      public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(State::write, State::read);

      private static void write(RegistryFriendlyByteBuf buf, State s) {
         buf.writeVarInt(s.team + 1);
         buf.writeVarInt(s.mode);
         buf.writeBoolean(s.running);
         buf.writeVarInt(s.blueBank);
         buf.writeVarInt(s.redBank);
         buf.writeVarInt(s.blueScore);
         buf.writeVarInt(s.redScore);
         buf.writeVarInt(s.secondsLeft + 1);
         buf.writeVarInt(s.blueHq + 1);
         buf.writeVarInt(s.redHq + 1);
         buf.writeVarInt(s.bankCap);
      }

      private static State read(RegistryFriendlyByteBuf buf) {
         return new State(
            buf.readVarInt() - 1,
            buf.readVarInt(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt() - 1,
            buf.readVarInt() - 1,
            buf.readVarInt() - 1,
            buf.readVarInt()
         );
      }

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Server to client: open the shop of the terminal at the given position. */
   public record OpenShop(BlockPos terminal, List<ShopEntry> entries) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<OpenShop> TYPE = new CustomPacketPayload.Type<>(id("open_shop"));
      public static final StreamCodec<RegistryFriendlyByteBuf, OpenShop> CODEC = StreamCodec.of(OpenShop::write, OpenShop::read);

      private static void write(RegistryFriendlyByteBuf buf, OpenShop p) {
         buf.writeBlockPos(p.terminal);
         buf.writeVarInt(p.entries.size());

         for (ShopEntry entry : p.entries) {
            entry.write(buf);
         }
      }

      private static OpenShop read(RegistryFriendlyByteBuf buf) {
         BlockPos pos = buf.readBlockPos();
         int n = buf.readVarInt();
         List<ShopEntry> entries = new ArrayList<>(n);

         for (int i = 0; i < n; i++) {
            entries.add(ShopEntry.read(buf));
         }

         return new OpenShop(pos, entries);
      }

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Client to server: buy one lot of the entry. */
   public record Buy(BlockPos terminal, String entry) implements CustomPacketPayload {
      public static final CustomPacketPayload.Type<Buy> TYPE = new CustomPacketPayload.Type<>(id("buy"));
      public static final StreamCodec<FriendlyByteBuf, Buy> CODEC = StreamCodec.of(
         (buf, p) -> {
            buf.writeBlockPos(p.terminal);
            buf.writeUtf(p.entry);
         },
         buf -> new Buy(buf.readBlockPos(), buf.readUtf())
      );

      @Override
      public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar registrar = event.registrar("1");
      registrar.playToClient(State.TYPE, State.CODEC, (payload, context) -> context.enqueueWork(() -> dev.warmode.client.WarClient.onState(payload)));
      registrar.playToClient(OpenShop.TYPE, OpenShop.CODEC, (payload, context) -> context.enqueueWork(() -> dev.warmode.client.WarClient.openShop(payload)));
      registrar.playToServer(Buy.TYPE, Buy.CODEC, (payload, context) -> context.enqueueWork(() -> {
         if (context.player() instanceof ServerPlayer player) {
            WarManager.buy(player, payload.terminal(), payload.entry());
         }
      }));
   }

   private WarNet() {
   }
}
