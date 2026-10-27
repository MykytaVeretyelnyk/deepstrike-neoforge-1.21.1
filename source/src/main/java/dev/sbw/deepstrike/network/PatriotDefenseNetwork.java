package dev.sbw.deepstrike.network;

import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import dev.sbw.deepstrike.client.PatriotDefenseScreen;
import dev.sbw.deepstrike.client.PatriotRemoteEffects;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LaunchJournal;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.MissileLaunchEffects;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.game.RadarDisclosure;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.registry.BFSounds;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class PatriotDefenseNetwork {
   private static final String PROTOCOL = "9";
   private static final int MAX_RADAR_CONTACTS = 1024;
   private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "patriot_defense"), () -> "9", "9"::equals, "9"::equals
   );
   private static int id;

   private PatriotDefenseNetwork() {
   }

   public static void register() {
      CHANNEL.messageBuilder(PatriotDefenseNetwork.InterceptorState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(PatriotDefenseNetwork.InterceptorState::encode)
         .decoder(PatriotDefenseNetwork.InterceptorState::decode)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PatriotRemoteEffects.accept(m)))
         .add();
      CHANNEL.messageBuilder(PatriotDefenseNetwork.InterceptorBurst.class, id++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(PatriotDefenseNetwork.InterceptorBurst::encode)
         .decoder(PatriotDefenseNetwork.InterceptorBurst::decode)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PatriotRemoteEffects.accept(m)))
         .add();
      CHANNEL.messageBuilder(PatriotDefenseNetwork.RequestScan.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(PatriotDefenseNetwork.RequestScan::encode)
         .decoder(PatriotDefenseNetwork.RequestScan::decode)
         .consumerMainThread((m, c) -> scan(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(PatriotDefenseNetwork.TargetState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(PatriotDefenseNetwork.TargetState::encode)
         .decoder(PatriotDefenseNetwork.TargetState::decode)
         .consumerMainThread((m, c) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PatriotDefenseScreen.accept(m)))
         .add();
      CHANNEL.messageBuilder(PatriotDefenseNetwork.FireInterceptor.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(PatriotDefenseNetwork.FireInterceptor::encode)
         .decoder(PatriotDefenseNetwork.FireInterceptor::decode)
         .consumerMainThread((m, c) -> fire(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(PatriotDefenseNetwork.ToggleDeploy.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(PatriotDefenseNetwork.ToggleDeploy::encode)
         .decoder(PatriotDefenseNetwork.ToggleDeploy::decode)
         .consumerMainThread((m, c) -> toggleDeploy(m, (Context)c.get()))
         .add();
      CHANNEL.messageBuilder(PatriotDefenseNetwork.RequestReload.class, id++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(PatriotDefenseNetwork.RequestReload::encode)
         .decoder(PatriotDefenseNetwork.RequestReload::decode)
         .consumerMainThread((m, c) -> manualReload(m, (Context)c.get()))
         .add();
   }

   private static PatriotTruckEntity occupiedTruck(ServerPlayer player) {
      return player.getVehicle() instanceof PatriotTruckEntity truck && truck.getPassengers().contains(player) ? truck : null;
   }

   private static void toggleDeploy(PatriotDefenseNetwork.ToggleDeploy msg, Context context) {
      ServerPlayer player = context.getSender();
      if (player != null && player.level().getEntity(msg.vehicleId()) instanceof PatriotTruckEntity truck) {
         if (occupiedTruck(player) == truck && !(player.distanceToSqr(truck) > 256.0)) {
            truck.toggleDeployment();
            scan(new PatriotDefenseNetwork.RequestScan(truck.getId()), context);
         }
      }
   }

   private static void manualReload(PatriotDefenseNetwork.RequestReload msg, Context context) {
      ServerPlayer player = context.getSender();
      if (player != null && player.level().getEntity(msg.vehicleId()) instanceof PatriotTruckEntity truck) {
         if (occupiedTruck(player) == truck && !(player.distanceToSqr(truck) > 256.0)) {
            if (!truck.isReloading() && truck.totalReady() < 4 && truck.pac2Count() + truck.pac3Count() > 0) {
               truck.startManualReload();
               scan(new PatriotDefenseNetwork.RequestScan(truck.getId()), context);
            }
         }
      }
   }

   private static void scan(PatriotDefenseNetwork.RequestScan msg, Context context) {
      ServerPlayer player = context.getSender();
      if (player != null && player.level().getEntity(msg.vehicleId()) instanceof PatriotTruckEntity truck) {
         if (occupiedTruck(player) == truck && !(player.distanceToSqr(truck) > 256.0)) {
            truck.refreshDefenseInventory();
            if (!truck.radarConnected()) {
               CHANNEL.send(
                  PacketDistributor.PLAYER.with(() -> player),
                  new PatriotDefenseNetwork.TargetState(
                     truck.getId(),
                     false,
                     truck.radarRange(),
                     truck.pac2Count(),
                     truck.pac3Count(),
                     truck.readyPac2(),
                     truck.readyPac3(),
                     truck.getReloadTicks(),
                     truck.getDeployState(),
                     truck.getDeployTicks(),
                     truck.friendlyFrequencies(),
                     List.of()
                  )
               );
            } else {
               List<PatriotDefenseNetwork.Contact> contacts = truck.scan(player.serverLevel())
                  .stream()
                  .limit(1024L)
                  .map(
                     c -> {
                        Random error = new Random(c.id().getMostSignificantBits() ^ c.id().getLeastSignificantBits() ^ c.lastSeen());
                        float speed = c.speedKmh() * (float)(0.95 + error.nextDouble() * 0.1);
                        float altitude = c.altitude() * (float)(0.95 + error.nextDouble() * 0.1);
                        Entity entity = player.serverLevel().getEntity(c.id());
                        int assessment = -1;
                        if (entity != null && RadarDisclosure.impactKnown(c.id())) {
                           Vec3 impact = entity instanceof GuidedMissileEntity missile
                              ? IronDomeEntity.predictImpactPoint(missile)
                              : entity.position().add(entity.getDeltaMovement().scale(50.0));
                           assessment = Math.hypot(impact.x - truck.getX(), impact.z - truck.getZ()) <= 3000.0 ? 1 : 0;
                        }

                        return new PatriotDefenseNetwork.Contact(
                           c.id(),
                           entity == null ? -1 : entity.getId(),
                           c.x(),
                           c.y(),
                           c.z(),
                           speed,
                           altitude,
                           RadarDisclosure.model(c.id(), c.kind()),
                           c.friendly(),
                           assessment
                        );
                     }
                  )
                  .toList();
               CHANNEL.send(
                  PacketDistributor.PLAYER.with(() -> player),
                  new PatriotDefenseNetwork.TargetState(
                     truck.getId(),
                     truck.radarConnected(),
                     truck.radarRange(),
                     truck.pac2Count(),
                     truck.pac3Count(),
                     truck.readyPac2(),
                     truck.readyPac3(),
                     truck.getReloadTicks(),
                     truck.getDeployState(),
                     truck.getDeployTicks(),
                     truck.friendlyFrequencies(),
                     contacts
                  )
               );
            }
         }
      }
   }

   private static void fire(PatriotDefenseNetwork.FireInterceptor msg, Context context) {
      ServerPlayer player = context.getSender();
      if (player != null && msg.warhead() >= 0 && msg.warhead() <= 1) {
         PatriotTruckEntity truck = occupiedTruck(player);
         if (truck != null && !(player.distanceToSqr(truck) > 256.0)) {
            fireFromCommand(player, truck, msg.target(), msg.warhead());
            scan(new PatriotDefenseNetwork.RequestScan(truck.getId()), context);
         }
      }
   }

   public static boolean fireFromCommand(ServerPlayer player, PatriotTruckEntity truck, UUID targetId, int warhead) {
      if (player != null && truck != null && truck.level() == player.level() && warhead >= 0 && warhead <= 1) {
         truck.refreshDefenseInventory();
         if (!truck.radarConnected()) {
            return false;
         } else if (!truck.isDeployed()) {
            player.displayClientMessage(Component.translatable("message.deepstrike.patriot.not_deployed"), true);
            return false;
         } else {
            boolean known = truck.scan(player.serverLevel()).stream().anyMatch(c -> c.id().equals(targetId) && !c.friendly());
            if (!known) {
               return false;
            } else {
               Entity found = player.serverLevel().getEntity(targetId);
               if (!AirDefenseTargets.target(found)) {
                  return false;
               } else {
                  double range = warhead == 0 ? 1600.0 : 3000.0;
                  if (found.distanceToSqr(truck) > range * range) {
                     return false;
                  } else if ((warhead == 0 ? truck.readyPac2() : truck.readyPac3()) <= 0) {
                     if (truck.isReloading()) {
                        player.displayClientMessage(
                           Component.translatable("message.deepstrike.patriot.reloading", new Object[]{Math.round(truck.getReloadProgress() * 100.0F)}), true
                        );
                     } else {
                        player.displayClientMessage(
                           Component.translatable(
                              "message.deepstrike.patriot.empty",
                              new Object[]{Component.translatable(warhead == 0 ? "item.deepstrike.pac_2" : "item.deepstrike.pac_3")}
                           ),
                           true
                        );
                     }

                     return false;
                  } else {
                     double altitude = found.getY();
                     double speed = FlightUnits.kilometresPerHour(found.getDeltaMovement().length());
                     double chance;
                     if (warhead == 0) {
                        chance = Math.max(0.6, 0.65 - Math.min(0.05, speed / 1400.0 * 0.05) - (altitude < 70.0 ? 0.035 : 0.0));
                     } else {
                        chance = Math.max(0.8, 0.95 - Math.min(0.09, speed / 2000.0 * 0.09) - (altitude < 70.0 ? 0.06 : (altitude < 120.0 ? 0.025 : 0.0)));
                     }

                     if (found instanceof GuidedMissileEntity missile) {
                        if (missile.kind() == MissileKind.PEACEKEEPER) {
                           chance -= missile.peacekeeperInterceptPenalty();
                        } else if (missile.hasEcm()) {
                           chance -= 0.35;
                        }
                     }

                     chance = Math.max(0.15, chance);
                     boolean hit = player.serverLevel().random.nextDouble() < chance;
                     PatriotInterceptorEntity interceptor = new PatriotInterceptorEntity(player.serverLevel(), truck, found, warhead, hit);
                     if (!player.serverLevel().addFreshEntity(interceptor)) {
                        return false;
                     } else {
                        truck.spendMissile(warhead);
                        LaunchJournal.recordOnce(
                           player.serverLevel(),
                           LaunchJournal.id(truck),
                           interceptor.getUUID(),
                           "launch",
                           truck.position(),
                           warhead == 0 ? "PAC-2" : "PAC-3",
                           AirDefenseTargets.aim(found),
                           Component.translatable("journal.deepstrike.manual")
                        );
                        MissileLaunchEffects.spawnLaunchPlume(player.serverLevel(), interceptor.position(), Vec3.directionFromRotation(interceptor.pitch(), interceptor.yaw()));
                        player.serverLevel()
                           .playSound(
                              null,
                              interceptor.getX(),
                              interceptor.getY(),
                              interceptor.getZ(),
                              (SoundEvent)BFSounds.PATRIOT_LAUNCH.get(),
                              SoundSource.HOSTILE,
                              3.5F,
                              1.0F
                           );
                        player.displayClientMessage(
                           Component.translatable(
                              "message.deepstrike.patriot.launch",
                              new Object[]{Component.translatable(warhead == 0 ? "item.deepstrike.pac_2" : "item.deepstrike.pac_3"), Math.round(chance * 100.0)}
                           ),
                           true
                        );
                        return true;
                     }
                  }
               }
            }
         }
      } else {
         return false;
      }
   }

   public static void requestScan(int vehicleId) {
      CHANNEL.sendToServer(new PatriotDefenseNetwork.RequestScan(vehicleId));
   }

   public static void toggleDeploy(int vehicleId) {
      CHANNEL.sendToServer(new PatriotDefenseNetwork.ToggleDeploy(vehicleId));
   }

   public static void requestReload(int vehicleId) {
      CHANNEL.sendToServer(new PatriotDefenseNetwork.RequestReload(vehicleId));
   }

   public static void flight(PatriotInterceptorEntity missile, boolean alive) {
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> missile.level().dimension()),
         new PatriotDefenseNetwork.InterceptorState(
            missile.getId(),
            missile.getUUID(),
            missile.warhead(),
            alive,
            missile.getX(),
            missile.getY(),
            missile.getZ(),
            missile.yaw(),
            missile.pitch(),
            missile.level().getGameTime(),
            alive
         )
      );
   }

   public static void nativeFlight(MissileProjectile missile, boolean alive) {
      Vec3 nozzle = ManpadGeometry.nozzle(missile, missile.position(), missile.getDeltaMovement());
      FlightOrientation.Angles angles = FlightOrientation.along(missile.getDeltaMovement(), missile.getYRot(), missile.getXRot());
      int profile = missile instanceof Fim92MissileEntity ? 4 : 5;
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> missile.level().dimension()),
         new PatriotDefenseNetwork.InterceptorState(
            missile.getId(),
            missile.getUUID(),
            profile,
            alive,
            nozzle.x,
            nozzle.y,
            nozzle.z,
            angles.yaw(),
            angles.pitch(),
            missile.level().getGameTime(),
            alive && !missile.isLost() && !missile.isLostTarget()
         )
      );
   }

   public static void burst(PatriotInterceptorEntity missile) {
      broadcastBurst(missile.level(), missile.warhead(), missile.getX(), missile.getY(), missile.getZ());
   }

   public static void broadcastBurst(Level level, int warhead, double x, double y, double z) {
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> level.dimension()), new PatriotDefenseNetwork.InterceptorBurst(warhead, x, y, z, level.random.nextLong())
      );
   }

   public static void fire(UUID target, int warhead) {
      CHANNEL.sendToServer(new PatriotDefenseNetwork.FireInterceptor(target, warhead));
   }

   public record Contact(UUID id, int entityId, double x, double y, double z, float speedKmh, float altitude, int kind, boolean friendly, int impactAssessment) {
      public Contact(UUID id, int entityId, double x, double y, double z, float speedKmh, float altitude, int kind, boolean friendly) {
         this(id, entityId, x, y, z, speedKmh, altitude, kind, friendly, -1);
      }

      static void encode(PatriotDefenseNetwork.Contact c, FriendlyByteBuf b) {
         b.writeUUID(c.id);
         b.writeVarInt(c.entityId);
         b.writeDouble(c.x);
         b.writeDouble(c.y);
         b.writeDouble(c.z);
         b.writeFloat(c.speedKmh);
         b.writeFloat(c.altitude);
         b.writeVarInt(c.kind);
         b.writeBoolean(c.friendly);
         b.writeVarInt(c.impactAssessment);
      }

      static PatriotDefenseNetwork.Contact decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.Contact(
            b.readUUID(),
            b.readVarInt(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readFloat(),
            b.readFloat(),
            b.readVarInt(),
            b.readBoolean(),
            b.readVarInt()
         );
      }
   }

   public record FireInterceptor(UUID target, int warhead) {
      static void encode(PatriotDefenseNetwork.FireInterceptor m, FriendlyByteBuf b) {
         b.writeUUID(m.target());
         b.writeVarInt(m.warhead());
      }

      static PatriotDefenseNetwork.FireInterceptor decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.FireInterceptor(b.readUUID(), b.readVarInt());
      }
   }

   public record InterceptorBurst(int warhead, double x, double y, double z, long seed) {
      static void encode(PatriotDefenseNetwork.InterceptorBurst m, FriendlyByteBuf b) {
         b.writeVarInt(m.warhead);
         b.writeDouble(m.x);
         b.writeDouble(m.y);
         b.writeDouble(m.z);
         b.writeLong(m.seed);
      }

      static PatriotDefenseNetwork.InterceptorBurst decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.InterceptorBurst(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readLong());
      }
   }

   public record InterceptorState(
      int id, UUID missileId, int warhead, boolean alive, double x, double y, double z, float yaw, float pitch, long serverTick, boolean powered
   ) {
      static void encode(PatriotDefenseNetwork.InterceptorState m, FriendlyByteBuf b) {
         b.writeInt(m.id);
         b.writeUUID(m.missileId);
         b.writeVarInt(m.warhead);
         b.writeBoolean(m.alive);
         b.writeDouble(m.x);
         b.writeDouble(m.y);
         b.writeDouble(m.z);
         b.writeFloat(m.yaw);
         b.writeFloat(m.pitch);
         b.writeLong(m.serverTick);
         b.writeBoolean(m.powered);
      }

      static PatriotDefenseNetwork.InterceptorState decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.InterceptorState(
            b.readInt(),
            b.readUUID(),
            b.readVarInt(),
            b.readBoolean(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readFloat(),
            b.readFloat(),
            b.readLong(),
            b.readBoolean()
         );
      }
   }

   public record RequestReload(int vehicleId) {
      static void encode(PatriotDefenseNetwork.RequestReload m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
      }

      static PatriotDefenseNetwork.RequestReload decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.RequestReload(b.readVarInt());
      }
   }

   public record RequestScan(int vehicleId) {
      static void encode(PatriotDefenseNetwork.RequestScan m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
      }

      static PatriotDefenseNetwork.RequestScan decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.RequestScan(b.readVarInt());
      }
   }

   public record TargetState(
      int vehicleId,
      boolean radar,
      double radarRange,
      int pac2,
      int pac3,
      int readyPac2,
      int readyPac3,
      int reloadTicks,
      int deployState,
      int deployTicks,
      List<String> friendlyFrequencies,
      List<PatriotDefenseNetwork.Contact> contacts
   ) {
      static void encode(PatriotDefenseNetwork.TargetState m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId);
         b.writeBoolean(m.radar);
         b.writeDouble(m.radarRange);
         b.writeVarInt(m.pac2);
         b.writeVarInt(m.pac3);
         b.writeVarInt(m.readyPac2);
         b.writeVarInt(m.readyPac3);
         b.writeVarInt(m.reloadTicks);
         b.writeVarInt(m.deployState);
         b.writeVarInt(m.deployTicks);
         b.writeCollection(m.friendlyFrequencies, (buf, value) -> buf.writeUtf(value, 12));
         b.writeCollection(m.contacts, (buf, contact) -> PatriotDefenseNetwork.Contact.encode(contact, buf));
      }

      static PatriotDefenseNetwork.TargetState decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.TargetState(
            b.readVarInt(),
            b.readBoolean(),
            b.readDouble(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            b.readVarInt(),
            (List<String>)b.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 16), buf -> buf.readUtf(12)),
            (List<PatriotDefenseNetwork.Contact>)b.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 1024), PatriotDefenseNetwork.Contact::decode)
         );
      }
   }

   public record ToggleDeploy(int vehicleId) {
      static void encode(PatriotDefenseNetwork.ToggleDeploy m, FriendlyByteBuf b) {
         b.writeVarInt(m.vehicleId());
      }

      static PatriotDefenseNetwork.ToggleDeploy decode(FriendlyByteBuf b) {
         return new PatriotDefenseNetwork.ToggleDeploy(b.readVarInt());
      }
   }
}
