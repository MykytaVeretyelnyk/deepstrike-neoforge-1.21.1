package dev.sbw.deepstrike.compat.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Forge-style SimpleChannel facade on top of NeoForge's payload system.
 * Every message class becomes its own payload type named {@code <channel>_<id>}.
 */
public final class SimpleChannel {
   private static final List<SimpleChannel> CHANNELS = new ArrayList<>();
   private final ResourceLocation name;
   private final String version;
   private final List<Registration<?>> registrations = new ArrayList<>();
   private final Map<Class<?>, Registration<?>> byClass = new HashMap<>();

   SimpleChannel(ResourceLocation name, String version) {
      this.name = name;
      this.version = version;
      CHANNELS.add(this);
   }

   public static void registerAll(RegisterPayloadHandlersEvent event) {
      for (SimpleChannel channel : CHANNELS) {
         PayloadRegistrar registrar = event.registrar(channel.version);
         for (Registration<?> reg : channel.registrations) {
            reg.register(registrar);
         }
      }
   }

   public <M> MessageBuilder<M> messageBuilder(Class<M> type, int id, NetworkDirection direction) {
      return new MessageBuilder<>(this, type, id, direction);
   }

   public <M> MessageBuilder<M> messageBuilder(Class<M> type, int id) {
      return new MessageBuilder<>(this, type, id, null);
   }

   private Payload wrap(Object msg) {
      Registration<?> reg = this.byClass.get(msg.getClass());
      if (reg == null) {
         throw new IllegalArgumentException("Unregistered message " + msg.getClass().getName() + " on channel " + this.name);
      }
      return new Payload(reg.type, msg);
   }

   public void sendToServer(Object msg) {
      net.neoforged.neoforge.network.PacketDistributor.sendToServer(this.wrap(msg));
   }

   public void send(PacketDistributor.PacketTarget target, Object msg) {
      target.send(this.wrap(msg));
   }

   public record Payload(CustomPacketPayload.Type<Payload> type, Object msg) implements CustomPacketPayload {
   }

   private static final class Registration<M> {
      final CustomPacketPayload.Type<Payload> type;
      final NetworkDirection direction;
      final BiConsumer<M, FriendlyByteBuf> encoder;
      final Function<FriendlyByteBuf, M> decoder;
      final BiConsumer<M, Supplier<NetworkEvent.Context>> handler;

      Registration(
         CustomPacketPayload.Type<Payload> type,
         NetworkDirection direction,
         BiConsumer<M, FriendlyByteBuf> encoder,
         Function<FriendlyByteBuf, M> decoder,
         BiConsumer<M, Supplier<NetworkEvent.Context>> handler
      ) {
         this.type = type;
         this.direction = direction;
         this.encoder = encoder;
         this.decoder = decoder;
         this.handler = handler;
      }

      @SuppressWarnings("unchecked")
      void register(PayloadRegistrar registrar) {
         StreamCodec<RegistryFriendlyByteBuf, Payload> codec = StreamCodec.of(
            (buf, p) -> this.encoder.accept((M)p.msg(), buf), buf -> new Payload(this.type, this.decoder.apply(buf))
         );
         if (this.direction == NetworkDirection.PLAY_TO_CLIENT) {
            registrar.playToClient(this.type, codec, (p, ctx) -> this.handle(p, ctx, NetworkDirection.PLAY_TO_CLIENT));
         } else if (this.direction == NetworkDirection.PLAY_TO_SERVER) {
            registrar.playToServer(this.type, codec, (p, ctx) -> this.handle(p, ctx, NetworkDirection.PLAY_TO_SERVER));
         } else {
            registrar.playBidirectional(
               this.type,
               codec,
               (p, ctx) -> this.handle(p, ctx, ctx.flow().isClientbound() ? NetworkDirection.PLAY_TO_CLIENT : NetworkDirection.PLAY_TO_SERVER)
            );
         }
      }

      @SuppressWarnings("unchecked")
      private void handle(Payload payload, IPayloadContext ctx, NetworkDirection dir) {
         if (this.handler != null) {
            NetworkEvent.Context context = new NetworkEvent.Context(ctx, dir);
            this.handler.accept((M)payload.msg(), () -> context);
         }
      }
   }

   public static final class MessageBuilder<M> {
      private final SimpleChannel channel;
      private final Class<M> type;
      private final int id;
      private final NetworkDirection direction;
      private BiConsumer<M, FriendlyByteBuf> encoder = (m, b) -> {
      };
      private Function<FriendlyByteBuf, M> decoder;
      private BiConsumer<M, Supplier<NetworkEvent.Context>> handler;

      MessageBuilder(SimpleChannel channel, Class<M> type, int id, NetworkDirection direction) {
         this.channel = channel;
         this.type = type;
         this.id = id;
         this.direction = direction;
      }

      public MessageBuilder<M> encoder(BiConsumer<M, FriendlyByteBuf> encoder) {
         this.encoder = encoder;
         return this;
      }

      public MessageBuilder<M> decoder(Function<FriendlyByteBuf, M> decoder) {
         this.decoder = decoder;
         return this;
      }

      public MessageBuilder<M> consumerMainThread(BiConsumer<M, Supplier<NetworkEvent.Context>> handler) {
         this.handler = handler;
         return this;
      }

      public MessageBuilder<M> consumerNetworkThread(BiConsumer<M, Supplier<NetworkEvent.Context>> handler) {
         this.handler = handler;
         return this;
      }

      public void add() {
         CustomPacketPayload.Type<Payload> payloadType = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(this.channel.name.getNamespace(), this.channel.name.getPath() + "_" + this.id)
         );
         Registration<M> reg = new Registration<>(payloadType, this.direction, this.encoder, this.decoder, this.handler);
         this.channel.registrations.add(reg);
         this.channel.byClass.put(this.type, reg);
      }
   }
}
