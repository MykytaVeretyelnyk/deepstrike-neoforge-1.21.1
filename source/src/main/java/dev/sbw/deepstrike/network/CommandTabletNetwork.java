package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.DeepStrikeAddon;
import dev.sbw.deepstrike.client.CommandTabletScreen;
import dev.sbw.deepstrike.client.RemoteCameraTerrain;
import dev.sbw.deepstrike.game.CameraTerrainServer;
import dev.sbw.deepstrike.game.CommandCenterData;
import dev.sbw.deepstrike.registry.BFItems;
import io.netty.handler.codec.DecoderException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class CommandTabletNetwork {
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "command"), () -> "4", "4"::equals, "4"::equals
   );
   private static final Map<ServerPlayer, CommandTabletNetwork.Rate> requests = new WeakHashMap<>();
   private static final Map<ServerPlayer, String> errors = new WeakHashMap<>();
   private static final Map<ServerPlayer, LinkedHashSet<UUID>> processed = new WeakHashMap<>();
   private static CommandTabletNetwork.Action lastReviewAction;

   public static boolean holding(ServerPlayer p) {
      return p.getMainHandItem().is((Item)BFItems.JAGM_CAMERA_TABLET.get()) || p.getOffhandItem().is((Item)BFItems.JAGM_CAMERA_TABLET.get());
   }

   public static void watch(UUID missile, boolean active) {
      CHANNEL.sendToServer(new CommandTabletNetwork.CameraWatch(missile, active));
   }

   public static void terrain(ServerPlayer player, UUID missile, ClientboundLevelChunkWithLightPacket packet) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CommandTabletNetwork.CameraTerrain(missile, packet));
   }

   public static void cameraDenied(ServerPlayer p, UUID missile) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CommandTabletNetwork.CameraDenied(missile));
   }

   public static void register() {
      CHANNEL.messageBuilder(CommandTabletNetwork.CameraDenied.class, 4, NetworkDirection.PLAY_TO_CLIENT)
         .encoder((m, b) -> b.writeUUID(m.missile))
         .decoder(b -> new CommandTabletNetwork.CameraDenied(b.readUUID()))
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemoteCameraTerrain.denied(m.missile)))
         .add();
      CHANNEL.messageBuilder(CommandTabletNetwork.CameraWatch.class, 2, NetworkDirection.PLAY_TO_SERVER).encoder((m, b) -> {
         b.writeUUID(m.missile);
         b.writeBoolean(m.active);
      }).decoder(b -> new CommandTabletNetwork.CameraWatch(b.readUUID(), b.readBoolean())).consumerMainThread((m, c) -> {
         ServerPlayer p = ((Context)c.get()).getSender();
         if (p != null) {
            if (m.active) {
               if (!CameraTerrainServer.watch(p, m.missile)) {
                  cameraDenied(p, m.missile);
               }
            } else {
               CameraTerrainServer.close(p);
            }
         }
      }).add();
      CHANNEL.messageBuilder(CommandTabletNetwork.CameraTerrain.class, 3, NetworkDirection.PLAY_TO_CLIENT)
         .encoder((m, b) -> {
            b.writeUUID(m.missile);
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode((net.minecraft.network.RegistryFriendlyByteBuf)b, m.packet);
         })
         .decoder(b -> new CommandTabletNetwork.CameraTerrain(b.readUUID(), ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode((net.minecraft.network.RegistryFriendlyByteBuf)b)))
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemoteCameraTerrain.accept(m.missile, m.packet)))
         .add();
      CHANNEL.messageBuilder(CommandTabletNetwork.Action.class, 0, NetworkDirection.PLAY_TO_SERVER)
         .encoder(CommandTabletNetwork.Action::encode)
         .decoder(CommandTabletNetwork.Action::decode)
         .consumerMainThread((m, c) -> {
            ServerPlayer p = ((Context)c.get()).getSender();
            if (p != null && holding(p)) {
               long tick = p.getServer().getTickCount();
               CommandTabletNetwork.Rate rate = requests.get(p);
               if (rate == null || tick - rate.window >= 20L) {
                  rate = new CommandTabletNetwork.Rate(tick, 0);
               }

               if (rate.count < 24) {
                  requests.put(p, new CommandTabletNetwork.Rate(rate.window, rate.count + 1));
                  CommandCenterData data = CommandCenterData.get(p.serverLevel());
                  LinkedHashSet<UUID> seen = processed.computeIfAbsent(p, key -> new LinkedHashSet<>());
                  if (!seen.add(m.commandId)) {
                     CompoundTag state = data.snapshot(p);
                     state.putString("Error", errors.getOrDefault(p, ""));
                     send(p, state);
                  } else {
                     while (seen.size() > 128) {
                        seen.remove(seen.iterator().next());
                     }

                     String error = m.payload == null ? "action" : data.action(p, m.action, m.payload);
                     if (!m.action.equals("refresh")) {
                        errors.put(p, error);
                     }

                     if (Boolean.getBoolean("deepstrike.commandTabletReview") && !m.action.equals("refresh")) {
                        DeepStrikeAddon.LOGGER.info("COMMAND_TABLET_ACTION action={} error={} payload={}", new Object[]{m.action, error, m.payload});
                     }

                     CompoundTag snapshot = data.snapshot(p);
                     snapshot.putString("Error", errors.getOrDefault(p, error));
                     send(p, snapshot);
                  }
               }
            }
         })
         .add();
      CHANNEL.messageBuilder(CommandTabletNetwork.Snapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(CommandTabletNetwork.Snapshot::encode)
         .decoder(CommandTabletNetwork.Snapshot::decode)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CommandTabletScreen.accept(m.payload)))
         .add();
   }

   public static void send(ServerPlayer p, CompoundTag payload) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new CommandTabletNetwork.Snapshot(payload));
   }

   public static CommandTabletNetwork.Action lastReviewAction() {
      return lastReviewAction;
   }

   public static void request(String action, CompoundTag payload) {
      CommandTabletNetwork.Action message = new CommandTabletNetwork.Action(action, payload);
      if (Boolean.getBoolean("deepstrike.commandTabletReview")) {
         lastReviewAction = message;
      }

      CHANNEL.sendToServer(message);
   }

   public record Action(String action, CompoundTag payload, UUID commandId) {
      public Action(String action, CompoundTag payload) {
         this(action, payload, UUID.randomUUID());
      }

      static void encode(CommandTabletNetwork.Action m, FriendlyByteBuf b) {
         b.writeUtf(m.action, 16);
         b.writeUUID(m.commandId);
         b.writeNbt(m.payload);
      }

      static CommandTabletNetwork.Action decode(FriendlyByteBuf b) {
         String action = b.readUtf(16);
         UUID commandId = b.readUUID();
         if (b.readableBytes() > 16384) {
            throw new DecoderException("Command payload exceeds 16KiB");
         } else {
            return new CommandTabletNetwork.Action(action, b.readNbt(), commandId);
         }
      }
   }

   public record CameraDenied(UUID missile) {
   }

   public record CameraTerrain(UUID missile, ClientboundLevelChunkWithLightPacket packet) {
   }

   public record CameraWatch(UUID missile, boolean active) {
   }

   private record Rate(long window, int count) {
   }

   public record Snapshot(CompoundTag payload) {
      static void encode(CommandTabletNetwork.Snapshot m, FriendlyByteBuf b) {
         b.writeNbt(m.payload);
      }

      static CommandTabletNetwork.Snapshot decode(FriendlyByteBuf b) {
         return new CommandTabletNetwork.Snapshot(b.readNbt());
      }
   }
}
