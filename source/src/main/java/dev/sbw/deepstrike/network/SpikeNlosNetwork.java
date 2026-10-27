package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.client.SpikeNlosMapScreen;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;
import net.minecraft.core.registries.BuiltInRegistries;

public final class SpikeNlosNetwork {
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "spike"), () -> "3", "3"::equals, "3"::equals
   );
   private static final Map<UUID, Long> requestTimes = new HashMap<>();

   public static void register() {
      CHANNEL.messageBuilder(SpikeNlosNetwork.Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> b.writeVarInt(m.vehicle))
         .decoder(b -> new SpikeNlosNetwork.Request(b.readVarInt()))
         .consumerMainThread((m, c) -> request(m, ((Context)c.get()).getSender()))
         .add();
      CHANNEL.messageBuilder(SpikeNlosNetwork.Snapshot.class, 1, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(SpikeNlosNetwork::encodeSnapshot)
         .decoder(SpikeNlosNetwork::decodeSnapshot)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SpikeNlosMapScreen.accept(m)))
         .add();
      CHANNEL.messageBuilder(SpikeNlosNetwork.Launch.class, 2, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeVarInt(m.vehicle);
            b.writeDouble(m.x);
            b.writeDouble(m.y);
            b.writeDouble(m.z);
            b.writeDouble(m.altitude);
            b.writeDouble(m.speed);
            b.writeUtf(m.frequency, 12);
            b.writeUUID(m.commandId);
         })
         .decoder(
            b -> new SpikeNlosNetwork.Launch(
               b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readUtf(12), b.readUUID()
            )
         )
         .consumerMainThread(
            (m, c) -> {
               ServerPlayer p = ((Context)c.get()).getSender();
               if (p != null
                  && p.getVehicle() instanceof SpikeNlosEntity v
                  && v.getId() == m.vehicle
                  && !v.queueLaunch(p, new Vec3(m.x, m.y, m.z), m.altitude, m.speed, m.frequency, m.commandId)) {
                  p.displayClientMessage(Component.translatable("message.deepstrike.spike.unavailable"), true);
               }
            }
         )
         .add();
      CHANNEL.messageBuilder(SpikeNlosNetwork.Control.class, 3, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeVarInt(m.missile);
            b.writeByte(m.throttle);
            b.writeBoolean(m.retarget);
            if (m.retarget) {
               b.writeDouble(m.x);
               b.writeDouble(m.y);
               b.writeDouble(m.z);
               b.writeInt(m.target);
            }
         })
         .decoder(
            b -> {
               int id = b.readVarInt();
               int throttle = b.readByte();
               boolean retarget = b.readBoolean();
               return new SpikeNlosNetwork.Control(
                  id,
                  throttle,
                  retarget,
                  retarget ? b.readDouble() : 0.0,
                  retarget ? b.readDouble() : 0.0,
                  retarget ? b.readDouble() : 0.0,
                  retarget ? b.readInt() : -1
               );
            }
         )
         .consumerMainThread((m, c) -> control(m, ((Context)c.get()).getSender()))
         .add();
      CHANNEL.messageBuilder(SpikeNlosNetwork.Viewer.class, 4, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeVarInt(m.missile);
            b.writeVarInt(m.distance);
            b.writeBoolean(m.active);
         })
         .decoder(b -> new SpikeNlosNetwork.Viewer(b.readVarInt(), b.readVarInt(), b.readBoolean()))
         .consumerMainThread((m, c) -> MissileCameraStream.lease(((Context)c.get()).getSender(), m.missile, m.distance, m.active))
         .add();
   }

   private static void request(SpikeNlosNetwork.Request m, ServerPlayer p) {
      if (p != null && p.getVehicle() instanceof SpikeNlosEntity v && v.getId() == m.vehicle) {
         long tick = p.serverLevel().getGameTime();
         long last = requestTimes.getOrDefault(p.getUUID(), -100L);
         if (tick < last || tick - last >= 8L) {
            requestTimes.put(p.getUUID(), tick);
            List<SpikeNlosNetwork.Contact> contacts = new ArrayList<>();

            for (Entity e : p.serverLevel().getAllEntities()) {
               if (e.isAlive() && e != v && !(e.distanceToSqr(v) > 2250000.0)) {
                  ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
                  String ns = key == null ? "" : key.getNamespace();
                  if (e instanceof Player
                     || ns.equals("superbwarfare")
                     || ns.equals("deepstrike")
                     || ns.equals("mts")
                     || ns.contains("immersive")
                     || ns.contains("fpv")) {
                     String name = e.getDisplayName().getString();
                     if (name.length() > 256) {
                        name = name.substring(0, 256);
                     }

                     contacts.add(new SpikeNlosNetwork.Contact(e.getId(), e.getUUID(), name, e.getX(), e.getY(), e.getZ(), e instanceof Player));
                  }
               }
            }

            CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new SpikeNlosNetwork.Snapshot(v.getId(), contacts, v.readyMissiles()));
         }
      }
   }

   private static void control(SpikeNlosNetwork.Control m, ServerPlayer p) {
      if (p != null
         && p.serverLevel().getEntity(m.missile) instanceof GuidedMissileEntity missile
         && MissileCameraStream.owns(p, missile)
         && missile.kind() == MissileKind.SPIKE_NLOS
         && p.getUUID().equals(missile.ownerId())
         && missile.isAlive()) {
         missile.tvThrottle(m.throttle);
         if (m.retarget) {
            Entity target = p.serverLevel().getEntity(m.target);
            if (target != null && target.isAlive() && target.distanceToSqr(m.x, m.y, m.z) < 64.0) {
               missile.retargetSpike(target.getBoundingBox().getCenter(), target.getUUID());
            } else {
               missile.retargetSpike(new Vec3(m.x, m.y, m.z), null);
            }
         }
      }
   }

   private static void encodeSnapshot(SpikeNlosNetwork.Snapshot m, FriendlyByteBuf b) {
      b.writeVarInt(m.vehicle);
      b.writeVarInt(m.ammo);
      b.writeVarInt(m.contacts.size());

      for (SpikeNlosNetwork.Contact c : m.contacts) {
         b.writeInt(c.id);
         b.writeUUID(c.uuid);
         b.writeUtf(c.name, 256);
         b.writeDouble(c.x);
         b.writeDouble(c.y);
         b.writeDouble(c.z);
         b.writeBoolean(c.player);
      }
   }

   private static SpikeNlosNetwork.Snapshot decodeSnapshot(FriendlyByteBuf b) {
      int vehicle = b.readVarInt();
      int ammo = b.readVarInt();
      int count = b.readVarInt();
      if (count >= 0 && count <= 16384) {
         List<SpikeNlosNetwork.Contact> contacts = new ArrayList<>(count);

         for (int i = 0; i < count; i++) {
            contacts.add(
               new SpikeNlosNetwork.Contact(b.readInt(), b.readUUID(), b.readUtf(256), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean())
            );
         }

         return new SpikeNlosNetwork.Snapshot(vehicle, contacts, ammo);
      } else {
         throw new DecoderException("SPIKE contact count");
      }
   }

   private SpikeNlosNetwork() {
   }

   public record Contact(int id, UUID uuid, String name, double x, double y, double z, boolean player) {
   }

   public record Control(int missile, int throttle, boolean retarget, double x, double y, double z, int target) {
   }

   public record Launch(int vehicle, double x, double y, double z, double altitude, double speed, String frequency, UUID commandId) {
      public Launch(int vehicle, double x, double y, double z, double altitude, double speed, String frequency) {
         this(vehicle, x, y, z, altitude, speed, frequency, UUID.randomUUID());
      }
   }

   public record Request(int vehicle) {
   }

   public record Snapshot(int vehicle, List<SpikeNlosNetwork.Contact> contacts, int ammo) {
   }

   public record Viewer(int missile, int distance, boolean active) {
   }
}
