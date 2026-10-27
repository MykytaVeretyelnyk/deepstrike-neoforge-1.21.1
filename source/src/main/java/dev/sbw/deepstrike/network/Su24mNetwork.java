package dev.sbw.deepstrike.network;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.client.Su24mScreen;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class Su24mNetwork {
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("deepstrike", "su24"), () -> "1", "1"::equals, "1"::equals);
   public static final int PREPARE = 0;
   public static final int ARM = 1;
   public static final int CANCEL = 2;
   public static final int LOAD = 3;
   public static final int UNLOAD = 4;
   public static final int THROTTLE = 5;
   public static final int RELOAD = 6;
   public static final int SAVE_PRESET = 7;
   public static final int LOAD_PRESET = 8;

   public static void register() {
      CHANNEL.messageBuilder(Su24mNetwork.Request.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder((m, b) -> {
         b.writeVarInt(m.vehicle);
         b.writeUUID(m.identity);
      }).decoder(b -> new Su24mNetwork.Request(b.readVarInt(), b.readUUID())).consumerMainThread((m, c) -> {
         ServerPlayer p = ((Context)c.get()).getSender();
         Su24mEntity v = resolve(p, m.vehicle, m.identity);
         if (v != null) {
            long now = p.serverLevel().getGameTime();
            long last = p.getPersistentData().getLong("Su24Poll");
            if (now < last || now - last >= 8L) {
               p.getPersistentData().putLong("Su24Poll", now);
               snapshot(p, v, "");
            }
         }
      }).add();
      CHANNEL.messageBuilder(Su24mNetwork.Action.class, 1, NetworkDirection.PLAY_TO_SERVER)
         .encoder((m, b) -> {
            b.writeVarInt(m.vehicle);
            b.writeUUID(m.identity);
            b.writeByte(m.operation);
            b.writeByte(m.slot);
            b.writeVarInt(m.mode);
            b.writeVarInt(m.delay);
            b.writeDouble(m.value);
            writeRoute(b, m.route);
            b.writeUtf(m.name, 32);
         })
         .decoder(
            b -> new Su24mNetwork.Action(
               b.readVarInt(),
               b.readUUID(),
               b.readUnsignedByte(),
               b.readUnsignedByte(),
               b.readVarInt(),
               b.readVarInt(),
               b.readDouble(),
               readRoute(b),
               b.readUtf(32)
            )
         )
         .consumerMainThread((m, c) -> action(m, ((Context)c.get()).getSender()))
         .add();
      CHANNEL.messageBuilder(Su24mNetwork.Snapshot.class, 2, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(Su24mNetwork::encodeSnapshot)
         .decoder(Su24mNetwork::decodeSnapshot)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Su24mScreen.accept(m)))
         .add();
   }

   private static Su24mEntity resolve(ServerPlayer p, int id, UUID uuid) {
      return p != null && p.serverLevel().getEntity(id) instanceof Su24mEntity plane && plane.getUUID().equals(uuid) && plane.canOperate(p, false) ? plane : null;
   }

   private static CompoundTag library(ServerPlayer player) {
      CompoundTag forge = player.getPersistentData();
      String key = "PlayerPersisted";
      if (!forge.contains(key, 10)) {
         forge.put(key, new CompoundTag());
      }

      return forge.getCompound(key);
   }

   private static List<Su24mNetwork.Preset> presets(ServerPlayer p) {
      ArrayList<Su24mNetwork.Preset> result = new ArrayList<>();

      for (Tag raw : library(p).getList("Su24Presets", 10)) {
         CompoundTag tag = (CompoundTag)raw;
         ArrayList<RoutePoint> points = new ArrayList<>();

         for (Tag item : tag.getList("Route", 10)) {
            CompoundTag n = (CompoundTag)item;
            points.add(new RoutePoint(n.getDouble("X"), n.getDouble("Y"), n.getDouble("Z"), n.getDouble("Speed")));
         }

         if (result.size() < 8) {
            result.add(new Su24mNetwork.Preset(tag.getString("Name"), List.copyOf(points)));
         }
      }

      return result;
   }

   private static boolean savePreset(ServerPlayer p, Su24mEntity v, int slot, String name) {
      if (slot >= 0 && slot <= 1 && !name.isBlank() && !v.missionRoute(slot).isEmpty()) {
         ArrayList<Su24mNetwork.Preset> list = new ArrayList<>(presets(p));
         list.removeIf(x -> x.name.equals(name));
         if (list.size() >= 8) {
            return false;
         } else {
            list.add(new Su24mNetwork.Preset(name, v.missionRoute(slot)));
            ListTag entries = new ListTag();

            for (Su24mNetwork.Preset item : list) {
               CompoundTag tag = new CompoundTag();
               tag.putString("Name", item.name);
               ListTag route = new ListTag();

               for (RoutePoint point : item.route) {
                  CompoundTag n = new CompoundTag();
                  n.putDouble("X", point.x());
                  n.putDouble("Y", point.y());
                  n.putDouble("Z", point.z());
                  n.putDouble("Speed", point.speed());
                  route.add(n);
               }

               tag.put("Route", route);
               entries.add(tag);
            }

            library(p).put("Su24Presets", entries);
            return true;
         }
      } else {
         return false;
      }
   }

   private static void action(Su24mNetwork.Action m, ServerPlayer p) {
      Su24mEntity v = resolve(p, m.vehicle, m.identity);
      if (v != null) {
         boolean ok = switch (m.operation) {
            case 0 -> v.prepare(p, m.slot, m.route);
            case 1 -> v.arm(p, m.slot, m.mode, m.delay, m.value);
            case 2 -> v.cancel(p, m.slot);
            case 3 -> v.loadStore(p, m.slot);
            case 4 -> v.unloadStore(p, m.slot);
            case 5 -> v.setAvionicsThrottle(p, m.value);
            case 6 -> v.reloadCannon(p);
            case 7 -> savePreset(p, v, m.slot, m.name.strip());
            case 8 -> {
               List<Su24mNetwork.Preset> all = presets(p);
               yield m.mode >= 0 && m.mode < all.size() && v.prepare(p, m.slot, all.get(m.mode).route);
            }
            default -> false;
         };
         snapshot(p, v, ok ? "su24.accepted" : "su24.rejected");
      }
   }

   private static void snapshot(ServerPlayer p, Su24mEntity v, String notice) {
      ArrayList<Su24mNetwork.Rack> racks = new ArrayList<>();

      for (int i = 0; i < 2; i++) {
         Su24mEntity.Order order = v.order(i);
         racks.add(
            new Su24mNetwork.Rack(
               v.loaded(i),
               v.storeStatus(i),
               v.missionRoute(i),
               order == null ? -1 : order.mode(),
               order == null ? 0L : Math.max(0L, order.earliest() - v.level().getGameTime()),
               order == null ? 0.0 : order.speed(),
               v.lastFlight(i)
            )
         );
      }

      LinkedHashMap<UUID, Su24mNetwork.Contact> contacts = new LinkedHashMap<>();
      ArrayList<Entity> entities = new ArrayList<>();
      p.serverLevel().getAllEntities().forEach(entities::add);
      entities.addAll(ActiveFlights.get(p.serverLevel()).radarEntities());

      for (Entity e : entities) {
         if (e != v && e.isAlive() && !(e.distanceToSqr(v) > 1.6777216E7) && contacts.size() < 128) {
            boolean missile = e instanceof GuidedMissileEntity || e instanceof PatriotInterceptorEntity;
            if (missile || e instanceof VehicleEntity) {
               String name = e.getDisplayName().getString();
               if (name.length() > 96) {
                  name = name.substring(0, 96);
               }

               int kind = -1;
               if (e instanceof GuidedMissileEntity gm) {
                  kind = gm.kind().ordinal();
               } else if (e instanceof PatriotInterceptorEntity) {
                  kind = 10;
               } else if (e instanceof Su24mEntity) {
                  kind = 200;
               } else if (e instanceof VehicleEntity ve) {
                  VehicleType vt = ve.getVehicleType();
                  if (vt == VehicleType.HELICOPTER) {
                     kind = 101;
                  } else if (vt == VehicleType.AIRPLANE) {
                     kind = 100;
                  } else {
                     kind = 102;
                  }
               }

               contacts.put(e.getUUID(), new Su24mNetwork.Contact(e.getId(), e.getUUID(), name, e.getX(), e.getY(), e.getZ(), missile, kind));
            }
         }
      }

      CHANNEL.send(
         PacketDistributor.PLAYER.with(() -> p),
         new Su24mNetwork.Snapshot(
            v.getId(),
            v.getUUID(),
            v.cannonRounds(),
            v.speedKmh(),
            v.getY(),
            v.getYRot(),
            v.getPower(),
            v.throttleSetting(),
            v.parked(),
            racks,
            List.copyOf(contacts.values()),
            presets(p),
            notice
         )
      );
   }

   static void writeRoute(FriendlyByteBuf b, List<RoutePoint> route) {
      b.writeVarInt(route.size());

      for (RoutePoint p : route) {
         b.writeDouble(p.x());
         b.writeDouble(p.y());
         b.writeDouble(p.z());
         b.writeDouble(p.speed());
      }
   }

   static List<RoutePoint> readRoute(FriendlyByteBuf b) {
      int n = b.readVarInt();
      if (n >= 0 && n <= 32) {
         ArrayList<RoutePoint> route = new ArrayList<>();

         for (int i = 0; i < n; i++) {
            route.add(new RoutePoint(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble()));
         }

         return List.copyOf(route);
      } else {
         throw new DecoderException("SU24 route length");
      }
   }

   private static void encodeSnapshot(Su24mNetwork.Snapshot m, FriendlyByteBuf b) {
      b.writeVarInt(m.vehicle);
      b.writeUUID(m.identity);
      b.writeVarInt(m.rounds);
      b.writeDouble(m.speed);
      b.writeDouble(m.altitude);
      b.writeDouble(m.heading);
      b.writeDouble(m.power);
      b.writeDouble(m.commandedThrottle);
      b.writeBoolean(m.parked);

      for (Su24mNetwork.Rack r : m.racks) {
         b.writeBoolean(r.loaded);
         b.writeUtf(r.status, 32);
         writeRoute(b, r.route);
         b.writeInt(r.mode);
         b.writeLong(r.waitTicks);
         b.writeDouble(r.speed);
         b.writeBoolean(r.flight != null);
         if (r.flight != null) {
            b.writeUUID(r.flight);
         }
      }

      b.writeVarInt(m.contacts.size());

      for (Su24mNetwork.Contact c : m.contacts) {
         b.writeInt(c.id);
         b.writeUUID(c.identity);
         b.writeUtf(c.name, 96);
         b.writeDouble(c.x);
         b.writeDouble(c.y);
         b.writeDouble(c.z);
         b.writeBoolean(c.missile);
         b.writeVarInt(c.kind);
      }

      b.writeVarInt(m.presets.size());

      for (Su24mNetwork.Preset preset : m.presets) {
         b.writeUtf(preset.name, 32);
         writeRoute(b, preset.route);
      }

      b.writeUtf(m.notice, 96);
   }

   private static Su24mNetwork.Snapshot decodeSnapshot(FriendlyByteBuf b) {
      int id = b.readVarInt();
      UUID identity = b.readUUID();
      int rounds = b.readVarInt();
      double speed = b.readDouble();
      double altitude = b.readDouble();
      double heading = b.readDouble();
      double power = b.readDouble();
      double throttle = b.readDouble();
      boolean parked = b.readBoolean();
      ArrayList<Su24mNetwork.Rack> racks = new ArrayList<>();

      for (int i = 0; i < 2; i++) {
         racks.add(
            new Su24mNetwork.Rack(
               b.readBoolean(), b.readUtf(32), readRoute(b), b.readInt(), b.readLong(), b.readDouble(), b.readBoolean() ? b.readUUID() : null
            )
         );
      }

      int n = b.readVarInt();
      if (n >= 0 && n <= 128) {
         ArrayList<Su24mNetwork.Contact> contacts = new ArrayList<>();

         for (int i = 0; i < n; i++) {
            contacts.add(
               new Su24mNetwork.Contact(
                  b.readInt(), b.readUUID(), b.readUtf(96), b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean(), b.readVarInt()
               )
            );
         }

         int count = b.readVarInt();
         if (count >= 0 && count <= 8) {
            ArrayList<Su24mNetwork.Preset> presets = new ArrayList<>();

            for (int i = 0; i < count; i++) {
               presets.add(new Su24mNetwork.Preset(b.readUtf(32), readRoute(b)));
            }

            return new Su24mNetwork.Snapshot(id, identity, rounds, speed, altitude, heading, power, throttle, parked, racks, contacts, presets, b.readUtf(96));
         } else {
            throw new DecoderException("SU24 presets");
         }
      } else {
         throw new DecoderException("SU24 contacts");
      }
   }

   public record Action(int vehicle, UUID identity, int operation, int slot, int mode, int delay, double value, List<RoutePoint> route, String name) {
   }

   public record Contact(int id, UUID identity, String name, double x, double y, double z, boolean missile, int kind) {
   }

   public record Preset(String name, List<RoutePoint> route) {
   }

   public record Rack(boolean loaded, String status, List<RoutePoint> route, int mode, long waitTicks, double speed, UUID flight) {
   }

   public record Request(int vehicle, UUID identity) {
   }

   public record Snapshot(
      int vehicle,
      UUID identity,
      int rounds,
      double speed,
      double altitude,
      double heading,
      double power,
      double commandedThrottle,
      boolean parked,
      List<Su24mNetwork.Rack> racks,
      List<Su24mNetwork.Contact> contacts,
      List<Su24mNetwork.Preset> presets,
      String notice
   ) {
   }
}
