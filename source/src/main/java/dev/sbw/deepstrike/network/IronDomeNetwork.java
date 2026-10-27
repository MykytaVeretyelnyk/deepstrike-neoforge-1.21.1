package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.client.ClientSetup;
import dev.sbw.deepstrike.client.IronDomeDefenseScreen;
import dev.sbw.deepstrike.game.DefenseRuntime;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class IronDomeNetwork {
   private static final String PROTOCOL = "3";
   private static final int MAX_CONTACTS = 1024;
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "iron_dome_network"), () -> "3", "3"::equals, "3"::equals
   );
   private static int id;
   private static final Map<UUID, IronDomeNetwork.PendingOpen> pendingOpen = new HashMap<>();

   private IronDomeNetwork() {
   }

   public static void register() {
      CHANNEL.messageBuilder(IronDomeNetwork.SetAircraft.class, id++, NetworkDirection.PLAY_TO_SERVER).encoder((m, b) -> {
         b.writeInt(m.vehicleId);
         b.writeBoolean(m.enabled);
      }).decoder(b -> new IronDomeNetwork.SetAircraft(b.readInt(), b.readBoolean())).consumerMainThread((m, c) -> {
         IronDomeEntity dome = findIronDome(((Context)c.get()).getSender(), m.vehicleId);
         if (dome != null) {
            dome.setAircraftTargets(m.enabled);
         }
      }).add();
      CHANNEL.messageBuilder(IronDomeNetwork.RequestScan.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(IronDomeNetwork.RequestScan::encode)
         .decoder(IronDomeNetwork.RequestScan::decode)
         .consumerMainThread((m, c) -> handleScan(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(IronDomeNetwork.TargetState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(IronDomeNetwork.TargetState::encode)
         .decoder(IronDomeNetwork.TargetState::decode)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> IronDomeDefenseScreen.accept(m)))
         .add();
      CHANNEL.messageBuilder(IronDomeNetwork.SetZone.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(IronDomeNetwork.SetZone::encode)
         .decoder(IronDomeNetwork.SetZone::decode)
         .consumerMainThread((m, c) -> handleSetZone(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(IronDomeNetwork.ToggleAutoDefense.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(IronDomeNetwork.ToggleAutoDefense::encode)
         .decoder(IronDomeNetwork.ToggleAutoDefense::decode)
         .consumerMainThread((m, c) -> handleToggleAuto(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(IronDomeNetwork.FireManual.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(IronDomeNetwork.FireManual::encode)
         .decoder(IronDomeNetwork.FireManual::decode)
         .consumerMainThread((m, c) -> handleFire(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(IronDomeNetwork.RequestReload.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(IronDomeNetwork.RequestReload::encode)
         .decoder(IronDomeNetwork.RequestReload::decode)
         .consumerMainThread((m, c) -> handleReload(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(IronDomeNetwork.OpenScreen.class, id++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder((m, b) -> b.writeVarInt(m.vehicleId()))
         .decoder(b -> new IronDomeNetwork.OpenScreen(b.readVarInt()))
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSetup.openIronDomeScreen(m.vehicleId())))
         .add();
   }

   private static IronDomeEntity findIronDome(ServerPlayer player, int vehicleId) {
      if (player != null && player.level() instanceof ServerLevel server) {
         return server.getEntity(vehicleId) instanceof IronDomeEntity dome && dome.isAlive() ? dome : null;
      } else {
         return null;
      }
   }

   private static void handleScan(IronDomeNetwork.RequestScan msg, Context context) {
      ServerPlayer player = context.getSender();
      IronDomeEntity dome = findIronDome(player, msg.vehicleId());
      if (dome != null) {
         dome.refreshInventory();
         List<IronDomeNetwork.Contact> contacts = dome.scanContacts(player.serverLevel());
         CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new IronDomeNetwork.TargetState(
               dome.getId(),
               dome.getX(),
               dome.getY(),
               dome.getZ(),
               dome.getReadyTamir(),
               dome.getReserveTamir(),
               dome.getReloadTicks(),
               dome.getZoneX(),
               dome.getZoneZ(),
               dome.getZoneRadius(),
               dome.isAutoDefense(),
               dome.aircraftTargets(),
               dome.friendlyFrequencies(),
               contacts
            )
         );
      }
   }

   private static void handleSetZone(IronDomeNetwork.SetZone msg, Context context) {
      ServerPlayer player = context.getSender();
      IronDomeEntity dome = findIronDome(player, msg.vehicleId());
      if (dome != null) {
         dome.setDefendedZone(msg.zoneX(), msg.zoneZ(), msg.zoneRadius());
         handleScan(new IronDomeNetwork.RequestScan(dome.getId()), context);
      }
   }

   private static void handleToggleAuto(IronDomeNetwork.ToggleAutoDefense msg, Context context) {
      ServerPlayer player = context.getSender();
      IronDomeEntity dome = findIronDome(player, msg.vehicleId());
      if (dome != null) {
         dome.setAutoDefense(!dome.isAutoDefense());
         handleScan(new IronDomeNetwork.RequestScan(dome.getId()), context);
      }
   }

   private static void handleFire(IronDomeNetwork.FireManual msg, Context context) {
      ServerPlayer player = context.getSender();
      IronDomeEntity dome = findIronDome(player, msg.vehicleId());
      if (dome != null) {
         dome.fireManual(msg.targetId(), player);
         handleScan(new IronDomeNetwork.RequestScan(dome.getId()), context);
      }
   }

   private static void handleReload(IronDomeNetwork.RequestReload msg, Context context) {
      ServerPlayer player = context.getSender();
      IronDomeEntity dome = findIronDome(player, msg.vehicleId());
      if (dome != null) {
         dome.startManualReload();
         handleScan(new IronDomeNetwork.RequestScan(dome.getId()), context);
      }
   }

   public static void requestScan(int vehicleId) {
      CHANNEL.sendToServer(new IronDomeNetwork.RequestScan(vehicleId));
   }

   public static void setZone(int vehicleId, double x, double z, double radius) {
      CHANNEL.sendToServer(new IronDomeNetwork.SetZone(vehicleId, x, z, radius));
   }

   public static void toggleAutoDefense(int vehicleId) {
      CHANNEL.sendToServer(new IronDomeNetwork.ToggleAutoDefense(vehicleId));
   }

   public static void fireManual(int vehicleId, UUID targetId) {
      CHANNEL.sendToServer(new IronDomeNetwork.FireManual(vehicleId, targetId));
   }

   public static void requestReload(int vehicleId) {
      CHANNEL.sendToServer(new IronDomeNetwork.RequestReload(vehicleId));
   }

   public static void openBound(ServerPlayer player, CompoundTag tag) {
      if (tag.contains("TargetDimension") && !tag.getString("TargetDimension").equals(player.serverLevel().dimension().location().toString())) {
         player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.dimension"));
      } else {
         UUID domeId = tag.getUUID("TargetUUID");
         if (player.serverLevel().getEntity(domeId) instanceof IronDomeEntity dome && dome.isAlive()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new IronDomeNetwork.OpenScreen(dome.getId()));
         } else if (tag.contains("TargetChunkX") && tag.contains("TargetChunkZ")) {
            DefenseRuntime runtime = DefenseRuntime.get(player.serverLevel());
            if (runtime.destroyed(domeId) || runtime.packed(domeId)) {
               player.sendSystemMessage(Component.translatable(runtime.packed(domeId) ? "message.deepstrike.terminal.packed" : "message.deepstrike.terminal.destroyed"));
               return;
            }

            runtime.wake(
               player.serverLevel(),
               domeId,
               tag.contains("TargetPosition")
                  ? BlockPos.of(tag.getLong("TargetPosition"))
                  : new ChunkPos(tag.getInt("TargetChunkX"), tag.getInt("TargetChunkZ")).getMiddleBlockPosition(64)
            );
            pendingOpen.put(
               player.getUUID(),
               new IronDomeNetwork.PendingOpen(
                  player, player.serverLevel(), domeId, tag.getInt("TargetChunkX"), tag.getInt("TargetChunkZ"), player.serverLevel().getGameTime() + 100L
               )
            );
         } else {
            player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.rebind"));
         }
      }
   }

   public static void tick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         Iterator<IronDomeNetwork.PendingOpen> iterator = pendingOpen.values().iterator();

         while (iterator.hasNext()) {
            IronDomeNetwork.PendingOpen pending = iterator.next();
            ServerPlayer player = pending.player();
            Entity entity = pending.level().getEntity(pending.dome());
            if (!player.hasDisconnected() && player.serverLevel() == pending.level() && pending.level().getGameTime() <= pending.expires()) {
               if (entity instanceof IronDomeEntity dome && dome.isAlive()) {
                  CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new IronDomeNetwork.OpenScreen(dome.getId()));
                  iterator.remove();
               }
            } else {
               if (!player.hasDisconnected() && player.serverLevel() == pending.level()) {
                  player.sendSystemMessage(Component.translatable("message.deepstrike.terminal.radar_unavailable"));
               }

               iterator.remove();
            }
         }
      }
   }

   public record Contact(
      UUID id,
      int entityId,
      double x,
      double y,
      double z,
      float speedKmh,
      float altitude,
      int kind,
      double impactX,
      double impactZ,
      boolean threatInZone,
      int status,
      boolean friendly
   ) {
      static void encode(IronDomeNetwork.Contact c, FriendlyByteBuf b) {
         b.writeUUID(c.id());
         b.writeVarInt(c.entityId());
         b.writeDouble(c.x());
         b.writeDouble(c.y());
         b.writeDouble(c.z());
         b.writeFloat(c.speedKmh());
         b.writeFloat(c.altitude());
         b.writeVarInt(c.kind());
         b.writeDouble(c.impactX());
         b.writeDouble(c.impactZ());
         b.writeBoolean(c.threatInZone());
         b.writeVarInt(c.status());
         b.writeBoolean(c.friendly());
      }

      static IronDomeNetwork.Contact decode(FriendlyByteBuf b) {
         return new IronDomeNetwork.Contact(
            b.readUUID(),
            b.readVarInt(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readFloat(),
            b.readFloat(),
            b.readVarInt(),
            b.readDouble(),
            b.readDouble(),
            b.readBoolean(),
            b.readVarInt(),
            b.readBoolean()
         );
      }
   }

   public record FireManual(int vehicleId, UUID targetId) {
      static void encode(IronDomeNetwork.FireManual m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
         b.writeUUID(m.targetId());
      }

      static IronDomeNetwork.FireManual decode(FriendlyByteBuf b) {
         return new IronDomeNetwork.FireManual(b.readVarInt(), b.readUUID());
      }
   }

   public record OpenScreen(int vehicleId) {
   }

   private record PendingOpen(ServerPlayer player, ServerLevel level, UUID dome, int chunkX, int chunkZ, long expires) {
   }

   public record RequestReload(int vehicleId) {
      static void encode(IronDomeNetwork.RequestReload m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
      }

      static IronDomeNetwork.RequestReload decode(FriendlyByteBuf b) {
         return new IronDomeNetwork.RequestReload(b.readVarInt());
      }
   }

   public record RequestScan(int vehicleId) {
      static void encode(IronDomeNetwork.RequestScan m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
      }

      static IronDomeNetwork.RequestScan decode(FriendlyByteBuf b) {
         return new IronDomeNetwork.RequestScan(b.readVarInt());
      }
   }

   public record SetAircraft(int vehicleId, boolean enabled) {
   }

   public record SetZone(int vehicleId, double zoneX, double zoneZ, double zoneRadius) {
      static void encode(IronDomeNetwork.SetZone m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
         b.writeDouble(m.zoneX());
         b.writeDouble(m.zoneZ());
         b.writeDouble(m.zoneRadius());
      }

      static IronDomeNetwork.SetZone decode(FriendlyByteBuf b) {
         return new IronDomeNetwork.SetZone(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble());
      }
   }

   public record TargetState(
      int vehicleId,
      double x,
      double y,
      double z,
      int readyTamir,
      int reserveTamir,
      int reloadTicks,
      double zoneX,
      double zoneZ,
      double zoneRadius,
      boolean autoDefense,
      boolean aircraftTargets,
      List<String> friendlyFrequencies,
      List<IronDomeNetwork.Contact> contacts
   ) {
      static void encode(IronDomeNetwork.TargetState m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
         b.writeDouble(m.x());
         b.writeDouble(m.y());
         b.writeDouble(m.z());
         b.writeVarInt(m.readyTamir());
         b.writeVarInt(m.reserveTamir());
         b.writeVarInt(m.reloadTicks());
         b.writeDouble(m.zoneX());
         b.writeDouble(m.zoneZ());
         b.writeDouble(m.zoneRadius());
         b.writeBoolean(m.autoDefense());
         b.writeBoolean(m.aircraftTargets());
         b.writeCollection(m.friendlyFrequencies(), (buf, value) -> buf.writeUtf(value, 12));
         b.writeCollection(m.contacts(), (buf, c) -> IronDomeNetwork.Contact.encode(c, buf));
      }

      static IronDomeNetwork.TargetState decode(FriendlyByteBuf b) {
         return new IronDomeNetwork.TargetState(
            b.readVarInt(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readBoolean(),
            b.readBoolean(),
            (List<String>)b.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 16), buf -> buf.readUtf(12)),
            (List<IronDomeNetwork.Contact>)b.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 1024), IronDomeNetwork.Contact::decode)
         );
      }
   }

   public record ToggleAutoDefense(int vehicleId) {
      static void encode(IronDomeNetwork.ToggleAutoDefense m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
      }

      static IronDomeNetwork.ToggleAutoDefense decode(FriendlyByteBuf b) {
         return new IronDomeNetwork.ToggleAutoDefense(b.readVarInt());
      }
   }
}
