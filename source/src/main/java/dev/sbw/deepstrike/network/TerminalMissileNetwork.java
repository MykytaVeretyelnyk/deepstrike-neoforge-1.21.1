package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.client.terminal.MissileTerminalScreen;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.SimpleChannel;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.game.TerminalMissiles;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;

/** Packets of the "Missiles" function of the Skyfront terminal. Every client packet is validated in {@link TerminalMissiles}. */
public final class TerminalMissileNetwork {
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "terminal_missiles"), () -> "2", "2"::equals, "2"::equals
   );

   private TerminalMissileNetwork() {
   }

   public static void sendToPlayer(ServerPlayer player, Snapshot snapshot) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
   }

   public static void register() {
      CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> b.writeBlockPos(m.terminal))
         .decoder(b -> new Request(b.readBlockPos()))
         .consumerMainThread((m, c) -> TerminalMissiles.handleRequest(((Context)c.get()).getSender(), m.terminal))
         .add();
      CHANNEL.messageBuilder(Snapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(TerminalMissileNetwork::writeSnapshot)
         .decoder(TerminalMissileNetwork::readSnapshot)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MissileTerminalScreen.accept(m)))
         .add();
      CHANNEL.messageBuilder(SetRoute.class, 2, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeBlockPos(m.terminal);
            b.writeBlockPos(m.launcher);
            b.writeBoolean(m.ballistic);
            writeRoute(b, m.route);
         })
         .decoder(b -> new SetRoute(b.readBlockPos(), b.readBlockPos(), b.readBoolean(), readRoute(b)))
         .consumerMainThread((m, c) -> TerminalMissiles.handleSetRoute(((Context)c.get()).getSender(), m.terminal, m.launcher, m.route, m.ballistic))
         .add();
      CHANNEL.messageBuilder(Launch.class, 3, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeBlockPos(m.terminal);
            b.writeBlockPos(m.launcher);
            b.writeBoolean(m.ballistic);
            writeRoute(b, m.route);
         })
         .decoder(b -> new Launch(b.readBlockPos(), b.readBlockPos(), b.readBoolean(), readRoute(b)))
         .consumerMainThread((m, c) -> TerminalMissiles.handleLaunch(((Context)c.get()).getSender(), m.terminal, m.launcher, m.route, m.ballistic))
         .add();
      CHANNEL.messageBuilder(Unbind.class, 4, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeBlockPos(m.terminal);
            b.writeBlockPos(m.launcher);
         })
         .decoder(b -> new Unbind(b.readBlockPos(), b.readBlockPos()))
         .consumerMainThread((m, c) -> TerminalMissiles.handleUnbind(((Context)c.get()).getSender(), m.terminal, m.launcher))
         .add();
      CHANNEL.messageBuilder(PanelCommand.class, 5, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeBlockPos(m.terminal);
            b.writeUtf(m.action, 16);
            b.writeUUID(m.commandId);
            b.writeNbt(m.payload);
         })
         .decoder(b -> {
            BlockPos terminal = b.readBlockPos();
            String action = b.readUtf(16);
            UUID commandId = b.readUUID();
            if (b.readableBytes() > 16384) {
               throw new DecoderException("Panel payload exceeds 16KiB");
            }

            return new PanelCommand(terminal, action, commandId, b.readNbt());
         })
         .consumerMainThread((m, c) -> TerminalMissiles.handlePanel(((Context)c.get()).getSender(), m.terminal, m.action, m.commandId, m.payload))
         .add();
      CHANNEL.messageBuilder(PanelSnapshot.class, 6, NetworkDirection.PLAY_TO_CLIENT)
         .encoder((m, b) -> {
            b.writeBlockPos(m.terminal);
            b.writeNbt(m.snapshot);
         })
         .decoder(b -> new PanelSnapshot(b.readBlockPos(), b.readNbt()))
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MissileTerminalScreen.acceptPanel(m.terminal, m.snapshot)))
         .add();
   }

   public static void sendPanel(ServerPlayer player, BlockPos terminal, CompoundTag snapshot) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PanelSnapshot(terminal, snapshot));
   }

   private static void writeRoute(FriendlyByteBuf buf, List<RoutePoint> route) {
      buf.writeVarInt(route.size());

      for (RoutePoint p : route) {
         buf.writeDouble(p.x());
         buf.writeDouble(p.y());
         buf.writeDouble(p.z());
         buf.writeDouble(p.speed());
      }
   }

   private static List<RoutePoint> readRoute(FriendlyByteBuf buf) {
      int count = buf.readVarInt();
      if (count >= 0 && count <= 32) {
         List<RoutePoint> route = new ArrayList<>(count);

         for (int i = 0; i < count; i++) {
            route.add(new RoutePoint(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
         }

         return route;
      } else {
         throw new DecoderException("Invalid terminal route length");
      }
   }

   private static void writeSnapshot(Snapshot m, FriendlyByteBuf b) {
      b.writeBlockPos(m.terminal);
      b.writeVarInt(m.rows.size());

      for (Row row : m.rows) {
         b.writeBlockPos(row.launcher);
         b.writeVarInt(row.number);
         b.writeVarInt(row.family + 1);
         b.writeVarInt(row.kind + 1);
         b.writeUtf(row.missileKey, 128);
         b.writeVarInt(row.loaded);
         b.writeVarInt(row.capacity);
         b.writeVarInt(row.status);
         b.writeBoolean(row.ballistic);
         writeRoute(b, row.route);
      }
   }

   private static Snapshot readSnapshot(FriendlyByteBuf b) {
      BlockPos terminal = b.readBlockPos();
      int count = b.readVarInt();
      if (count >= 0 && count <= 64) {
         List<Row> rows = new ArrayList<>(count);

         for (int i = 0; i < count; i++) {
            BlockPos launcher = b.readBlockPos();
            int number = b.readVarInt();
            int family = b.readVarInt() - 1;
            int kind = b.readVarInt() - 1;
            String key = b.readUtf(128);
            int loaded = b.readVarInt();
            int capacity = b.readVarInt();
            int status = b.readVarInt();
            boolean ballistic = b.readBoolean();
            rows.add(new Row(launcher, number, family, kind, key, loaded, capacity, status, ballistic, readRoute(b)));
         }

         return new Snapshot(terminal, rows);
      } else {
         throw new DecoderException("Invalid terminal snapshot size");
      }
   }

   public record Request(BlockPos terminal) {
   }

   public record Row(
      BlockPos launcher, int number, int family, int kind, String missileKey, int loaded, int capacity, int status, boolean ballistic, List<RoutePoint> route
   ) {
   }

   public record Snapshot(BlockPos terminal, List<Row> rows) {
   }

   public record SetRoute(BlockPos terminal, BlockPos launcher, boolean ballistic, List<RoutePoint> route) {
   }

   public record Launch(BlockPos terminal, BlockPos launcher, boolean ballistic, List<RoutePoint> route) {
   }

   public record Unbind(BlockPos terminal, BlockPos launcher) {
   }

   public record PanelCommand(BlockPos terminal, String action, UUID commandId, CompoundTag payload) {
   }

   public record PanelSnapshot(BlockPos terminal, CompoundTag snapshot) {
   }
}
