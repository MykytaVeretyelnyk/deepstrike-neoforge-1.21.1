package dev.sbw.deepstrike.network;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.client.ClientCamera;
import dev.sbw.deepstrike.client.DeepStrikeMapScreen;
import dev.sbw.deepstrike.client.DeepStrikeScreen;
import dev.sbw.deepstrike.client.LaunchCountdownHud;
import dev.sbw.deepstrike.client.MissileExplosionEffects;
import dev.sbw.deepstrike.client.RemoteFlights;
import dev.sbw.deepstrike.client.SonicBoomEffects;
import dev.sbw.deepstrike.game.Fp5BoosterEntity;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LaunchJournal;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileAssemblyBlockEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PaintSprayItem;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.menu.MissileAssemblyMenu;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import dev.sbw.deepstrike.compat.DistExecutor;
import dev.sbw.deepstrike.compat.net.NetworkDirection;
import dev.sbw.deepstrike.compat.net.NetworkRegistry;
import dev.sbw.deepstrike.compat.net.PacketDistributor;
import dev.sbw.deepstrike.compat.net.NetworkEvent.Context;
import dev.sbw.deepstrike.compat.net.SimpleChannel;

public final class DeepStrikeNetwork {
   private static final String PROTOCOL = "13";
   public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
      ResourceLocation.fromNamespaceAndPath("deepstrike", "main"), () -> "13", "13"::equals, "13"::equals
   );
   private static int nextId;

   public static void register() {
      CHANNEL.messageBuilder(DeepStrikeNetwork.FlightState.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(DeepStrikeNetwork.FlightState::encode)
         .decoder(DeepStrikeNetwork.FlightState::decode)
         .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemoteFlights.accept(msg)))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.LaunchMission.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(DeepStrikeNetwork.LaunchMission::encode)
         .decoder(DeepStrikeNetwork.LaunchMission::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.LaunchMission.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.AdjustLauncher.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(DeepStrikeNetwork.AdjustLauncher::encode)
         .decoder(DeepStrikeNetwork.AdjustLauncher::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.AdjustLauncher.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.RequestCamera.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(DeepStrikeNetwork.RequestCamera::encode)
         .decoder(DeepStrikeNetwork.RequestCamera::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.RequestCamera.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.RequestCameraFeed.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(DeepStrikeNetwork.RequestCameraFeed::encode)
         .decoder(DeepStrikeNetwork.RequestCameraFeed::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.RequestCameraFeed.handle((Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.CameraTarget.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(DeepStrikeNetwork.CameraTarget::encode)
         .decoder(DeepStrikeNetwork.CameraTarget::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.CameraTarget.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.RequestJournal.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(DeepStrikeNetwork.RequestJournal::encode)
         .decoder(DeepStrikeNetwork.RequestJournal::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.RequestJournal.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.JournalSnapshot.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(DeepStrikeNetwork.JournalSnapshot::encode)
         .decoder(DeepStrikeNetwork.JournalSnapshot::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.JournalSnapshot.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.BlastEffect.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(DeepStrikeNetwork.BlastEffect::encode)
         .decoder(DeepStrikeNetwork.BlastEffect::decode)
         .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MissileExplosionEffects.accept(msg)))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.SonicBoomEvent.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(DeepStrikeNetwork.SonicBoomEvent::encode)
         .decoder(DeepStrikeNetwork.SonicBoomEvent::decode)
         .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SonicBoomEffects.accept(msg)))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.AssembleMissileAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(DeepStrikeNetwork.AssembleMissileAction::encode)
         .decoder(DeepStrikeNetwork.AssembleMissileAction::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.AssembleMissileAction.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.PaintVehicle.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
         .encoder(DeepStrikeNetwork.PaintVehicle::encode)
         .decoder(DeepStrikeNetwork.PaintVehicle::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.PaintVehicle.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.LaunchCountdown.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(DeepStrikeNetwork.LaunchCountdown::encode)
         .decoder(DeepStrikeNetwork.LaunchCountdown::decode)
         .consumerMainThread((msg, ctx) -> DeepStrikeNetwork.LaunchCountdown.handle(msg, (Context)ctx.get()))
         .add();
      CHANNEL.messageBuilder(DeepStrikeNetwork.Fp5BoosterRelease.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
         .encoder(DeepStrikeNetwork.Fp5BoosterRelease::encode)
         .decoder(DeepStrikeNetwork.Fp5BoosterRelease::decode)
         .consumerMainThread((msg, ctx) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DeepStrikeNetwork.Fp5BoosterRelease.handle(msg)))
         .add();
   }

   public static void sendLaunchCountdown(ServerPlayer player, BlockPos launcher, int remainingTicks, boolean active) {
      long now = player.serverLevel().getGameTime();
      sendLaunchCountdown(player, launcher, null, now + remainingTicks, active, "PREPARING");
   }

   public static void sendLaunchCountdown(ServerPlayer player, BlockPos launcher, UUID mission, long launchAtTick, boolean active, String status) {
      CHANNEL.send(
         PacketDistributor.PLAYER.with(() -> player),
         new DeepStrikeNetwork.LaunchCountdown(launcher, mission, launchAtTick, player.serverLevel().getGameTime(), active, status)
      );
   }

   public static void blast(GuidedMissileEntity e) {
      Vec3 p = e.impactPosition();
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> e.level().dimension()),
         new DeepStrikeNetwork.BlastEffect(e.kind().ordinal(), p.x, p.y, p.z, e.level().random.nextLong(), e.warhead())
      );
   }

   public static void nativeExplosion(ServerLevel level, Vec3 position) {
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(level::dimension),
         new DeepStrikeNetwork.BlastEffect(
            MissileKind.JAGM.ordinal(), position.x, position.y, position.z, level.random.nextLong(), "NATIVE"
         )
      );
   }

   public static void intercepted(GuidedMissileEntity e) {
      Vec3 p = e.position();
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> e.level().dimension()),
         new DeepStrikeNetwork.BlastEffect(e.kind().ordinal(), p.x, p.y, p.z, e.level().random.nextLong(), "AIRBURST")
      );
   }

   public static void flight(GuidedMissileEntity e, boolean alive) {
      Vec3 v = e.getDeltaMovement();
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> e.level().dimension()),
         new DeepStrikeNetwork.FlightState(
            e.getId(),
            e.getUUID(),
            e.kind().ordinal(),
            e.graphite(),
            alive,
            e.isIntercepted(),
            e.getX(),
            e.getY(),
            e.getZ(),
            v.x,
            v.y,
            v.z,
            e.getYRot(),
            e.getXRot(),
            e.ownerId(),
            e.camo(),
            e.missionId(),
            e.frequency(),
            e.level().getGameTime(),
            e.propulsionBurning(),
            e.exhaustVisible(),
            e.tickCount
         )
      );
   }

   public static void broadcastBoosterRelease(GuidedMissileEntity missile, Fp5BoosterEntity part) {
      Vec3 v = part.getDeltaMovement();
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> missile.level().dimension()),
         new DeepStrikeNetwork.Fp5BoosterRelease(
            missile.getId(),
            part.getId(),
            part.getX(),
            part.getY(),
            part.getZ(),
            v.x,
            v.y,
            v.z,
            part.getYRot(),
            part.getXRot(),
            part.camo()
         )
      );
   }

   public static void sendCameraTarget(ServerPlayer player, int entityId) {
      CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DeepStrikeNetwork.CameraTarget(entityId));
   }

   public static void sonicBoom(GuidedMissileEntity e, boolean doubleClap, float speedKmh) {
      CHANNEL.send(
         PacketDistributor.DIMENSION.with(() -> e.level().dimension()),
         new DeepStrikeNetwork.SonicBoomEvent(e.getX(), e.getY(), e.getZ(), doubleClap, speedKmh)
      );
   }

   private DeepStrikeNetwork() {
   }

   public record AdjustLauncher(BlockPos pos, float yaw, float elevation) {
      static void encode(DeepStrikeNetwork.AdjustLauncher msg, FriendlyByteBuf buf) {
         buf.writeBlockPos(msg.pos);
         buf.writeFloat(msg.yaw);
         buf.writeFloat(msg.elevation);
      }

      static DeepStrikeNetwork.AdjustLauncher decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.AdjustLauncher(buf.readBlockPos(), buf.readFloat(), buf.readFloat());
      }

      static void handle(DeepStrikeNetwork.AdjustLauncher msg, Context ctx) {
         ServerPlayer player = ctx.getSender();
         if (player != null && !(player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 1.6777216E7)) {
            if (player.level().getBlockEntity(msg.pos) instanceof LauncherBlockEntity launcher && launcher.family() == LauncherBlock.Family.JAGM) {
               launcher.adjust(Math.max(-15.0F, Math.min(15.0F, msg.yaw)), Math.max(-5.0F, Math.min(5.0F, msg.elevation)));
            }
         }
      }
   }

   public record AssembleMissileAction(BlockPos pos, String camo) {
      static void encode(DeepStrikeNetwork.AssembleMissileAction msg, FriendlyByteBuf buf) {
         buf.writeBlockPos(msg.pos);
         buf.writeUtf(msg.camo);
      }

      static DeepStrikeNetwork.AssembleMissileAction decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.AssembleMissileAction(buf.readBlockPos(), buf.readUtf());
      }

      static void handle(DeepStrikeNetwork.AssembleMissileAction msg, Context ctx) {
         ServerPlayer player = ctx.getSender();
         if (player != null) {
            if (player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) <= 64.0
               && player.containerMenu instanceof MissileAssemblyMenu menu
               && menu.blockEntity.getBlockPos().equals(msg.pos)
               && player.level().getBlockEntity(msg.pos) instanceof MissileAssemblyBlockEntity be) {
               if (msg.camo.length() > 24) {
                  return;
               }

               be.setSelectedCamo(msg.camo);
               be.assemble(msg.camo, player);
            }
         }
      }
   }

   public record BlastEffect(int kind, double x, double y, double z, long seed, String warhead) {
      public BlastEffect(int kind, double x, double y, double z, long seed) {
         this(kind, x, y, z, seed, "he");
      }

      static void encode(DeepStrikeNetwork.BlastEffect m, FriendlyByteBuf b) {
         b.writeVarInt(m.kind);
         b.writeDouble(m.x);
         b.writeDouble(m.y);
         b.writeDouble(m.z);
         b.writeLong(m.seed);
         b.writeUtf(m.warhead, 32);
      }

      static DeepStrikeNetwork.BlastEffect decode(FriendlyByteBuf b) {
         return new DeepStrikeNetwork.BlastEffect(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble(), b.readLong(), b.readUtf(32));
      }
   }

   public record CameraTarget(int entityId) {
      static void encode(DeepStrikeNetwork.CameraTarget msg, FriendlyByteBuf buf) {
         buf.writeVarInt(msg.entityId);
      }

      static DeepStrikeNetwork.CameraTarget decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.CameraTarget(buf.readVarInt());
      }

      static void handle(DeepStrikeNetwork.CameraTarget msg, Context ctx) {
         ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCamera.view(msg.entityId)));
      }
   }

   public record FlightState(
      int id,
      UUID missileId,
      int kind,
      boolean graphite,
      boolean alive,
      boolean intercepted,
      double x,
      double y,
      double z,
      double vx,
      double vy,
      double vz,
      float yaw,
      float pitch,
      UUID owner,
      String camo,
      UUID missionId,
      String frequency,
      long serverTick,
      boolean engineRunning,
      boolean exhaustVisible,
      int flightAge
   ) {
      static void encode(DeepStrikeNetwork.FlightState m, FriendlyByteBuf b) {
         b.writeInt(m.id);
         b.writeUUID(m.missileId);
         b.writeInt(m.kind);
         b.writeBoolean(m.graphite);
         b.writeBoolean(m.alive);
         b.writeBoolean(m.intercepted);
         b.writeDouble(m.x);
         b.writeDouble(m.y);
         b.writeDouble(m.z);
         b.writeDouble(m.vx);
         b.writeDouble(m.vy);
         b.writeDouble(m.vz);
         b.writeFloat(m.yaw);
         b.writeFloat(m.pitch);
         b.writeBoolean(m.owner != null);
         if (m.owner != null) {
            b.writeUUID(m.owner);
         }

         b.writeUtf(m.camo != null ? m.camo : "olive");
         b.writeBoolean(m.missionId != null);
         if (m.missionId != null) {
            b.writeUUID(m.missionId);
         }

         b.writeUtf(m.frequency, 12);
         b.writeLong(m.serverTick);
         b.writeBoolean(m.engineRunning);
         b.writeBoolean(m.exhaustVisible);
         b.writeVarInt(m.flightAge);
      }

      static DeepStrikeNetwork.FlightState decode(FriendlyByteBuf b) {
         return new DeepStrikeNetwork.FlightState(
            b.readInt(),
            b.readUUID(),
            b.readInt(),
            b.readBoolean(),
            b.readBoolean(),
            b.readBoolean(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readFloat(),
            b.readFloat(),
            b.readBoolean() ? b.readUUID() : null,
            b.readUtf(),
            b.readBoolean() ? b.readUUID() : null,
            b.readUtf(12),
            b.readLong(),
            b.readBoolean(),
            b.readBoolean(),
            b.readVarInt()
         );
      }
   }

   public record Fp5BoosterRelease(
      int missileId, int boosterId, double x, double y, double z, double vx, double vy, double vz, float yaw, float pitch, String camo
   ) {
      static void encode(DeepStrikeNetwork.Fp5BoosterRelease m, FriendlyByteBuf b) {
         b.writeInt(m.missileId);
         b.writeInt(m.boosterId);
         b.writeDouble(m.x);
         b.writeDouble(m.y);
         b.writeDouble(m.z);
         b.writeDouble(m.vx);
         b.writeDouble(m.vy);
         b.writeDouble(m.vz);
         b.writeFloat(m.yaw);
         b.writeFloat(m.pitch);
         b.writeUtf(m.camo != null ? m.camo : "olive");
      }

      static DeepStrikeNetwork.Fp5BoosterRelease decode(FriendlyByteBuf b) {
         return new DeepStrikeNetwork.Fp5BoosterRelease(
            b.readInt(),
            b.readInt(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readDouble(),
            b.readFloat(),
            b.readFloat(),
            b.readUtf()
         );
      }

      @OnlyIn(Dist.CLIENT)
      static void handle(DeepStrikeNetwork.Fp5BoosterRelease msg) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            Entity existing = mc.level.getEntity(msg.boosterId);
            if (!(existing instanceof Fp5BoosterEntity)) {
               Fp5BoosterEntity booster = new Fp5BoosterEntity((EntityType<? extends Fp5BoosterEntity>)BFEntities.FP5_BOOSTER.get(), mc.level);
               booster.setId(msg.boosterId);
               booster.moveTo(msg.x, msg.y, msg.z, msg.yaw, msg.pitch);
               booster.setDeltaMovement(new Vec3(msg.vx, msg.vy, msg.vz));
               booster.setCamo(msg.camo);
               booster.setId(msg.boosterId);
               mc.level.addEntity(booster);
               booster.onSeparatedClient();
            }
         }
      }
   }

   public record JournalSnapshot(List<String> entries) {
      static void encode(DeepStrikeNetwork.JournalSnapshot msg, FriendlyByteBuf buf) {
         buf.writeCollection(msg.entries, (b, s) -> b.writeUtf(s, 2048));
      }

      static DeepStrikeNetwork.JournalSnapshot decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.JournalSnapshot(buf.readList(b -> b.readUtf(2048)));
      }

      static void handle(DeepStrikeNetwork.JournalSnapshot msg, Context ctx) {
         ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            DeepStrikeScreen.acceptJournal(msg.entries);
            DeepStrikeMapScreen.acceptJournal(msg.entries);
         }));
      }
   }

   public record LaunchCountdown(BlockPos launcher, UUID mission, long launchAtTick, long serverTick, boolean active, String status) {
      static void encode(DeepStrikeNetwork.LaunchCountdown msg, FriendlyByteBuf buf) {
         buf.writeBlockPos(msg.launcher);
         buf.writeNullable(msg.mission, (FriendlyByteBuf b, UUID u) -> b.writeUUID(u));
         buf.writeLong(msg.launchAtTick);
         buf.writeLong(msg.serverTick);
         buf.writeBoolean(msg.active);
         buf.writeUtf(msg.status, 32);
      }

      static DeepStrikeNetwork.LaunchCountdown decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.LaunchCountdown(
            buf.readBlockPos(), (UUID)buf.readNullable((FriendlyByteBuf b) -> b.readUUID()), buf.readLong(), buf.readLong(), buf.readBoolean(), buf.readUtf(32)
         );
      }

      static void handle(DeepStrikeNetwork.LaunchCountdown msg, Context ctx) {
         DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LaunchCountdownHud.accept(msg));
      }
   }

   public record LaunchMission(BlockPos launcher, boolean ballistic, List<RoutePoint> route, String frequency, UUID commandId) {
      public LaunchMission(BlockPos launcher, boolean ballistic, List<RoutePoint> route, String frequency) {
         this(launcher, ballistic, route, frequency, UUID.randomUUID());
      }

      static void encode(DeepStrikeNetwork.LaunchMission msg, FriendlyByteBuf buf) {
         buf.writeBlockPos(msg.launcher);
         buf.writeBoolean(msg.ballistic);
         buf.writeVarInt(msg.route.size());

         for (RoutePoint p : msg.route) {
            buf.writeDouble(p.x());
            buf.writeDouble(p.y());
            buf.writeDouble(p.z());
            buf.writeFloat((float)p.speed());
         }

         buf.writeUtf(msg.frequency, 12);
         buf.writeUUID(msg.commandId);
      }

      static DeepStrikeNetwork.LaunchMission decode(FriendlyByteBuf buf) {
         BlockPos pos = buf.readBlockPos();
         boolean ballistic = buf.readBoolean();
         int count = buf.readVarInt();
         if (count >= 0 && count <= 32) {
            List<RoutePoint> route = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
               route.add(new RoutePoint(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()).clamped());
            }

            return new DeepStrikeNetwork.LaunchMission(pos, ballistic, route, buf.readUtf(12), buf.readUUID());
         } else {
            throw new DecoderException("Invalid DeepStrike route length");
         }
      }

      static void handle(DeepStrikeNetwork.LaunchMission msg, Context ctx) {
         ServerPlayer player = ctx.getSender();
         if (player != null) {
            if (!RadioFrequency.valid(msg.frequency)) {
               player.displayClientMessage(Component.translatable("message.deepstrike.launch.invalid_frequency"), false);
            } else {
               if (player.level().getBlockEntity(msg.launcher) instanceof LauncherBlockEntity launcher) {
                  if (!launcher.fire(player, msg.route, msg.ballistic, msg.frequency, msg.commandId)) {
                     player.displayClientMessage(Component.translatable("message.deepstrike.launch.rejected", new Object[]{launcher.getLastFireErrorComponent()}), false);
                  }
               } else {
                  player.displayClientMessage(Component.translatable("message.deepstrike.launch.missing"), false);
               }
            }
         }
      }
   }

   public record PaintVehicle(int entityId, String skin) {
      static void encode(DeepStrikeNetwork.PaintVehicle msg, FriendlyByteBuf buf) {
         buf.writeVarInt(msg.entityId);
         buf.writeUtf(msg.skin, 24);
      }

      static DeepStrikeNetwork.PaintVehicle decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.PaintVehicle(buf.readVarInt(), buf.readUtf(24));
      }

      static void handle(DeepStrikeNetwork.PaintVehicle msg, Context ctx) {
         ServerPlayer player = ctx.getSender();
         if (player != null) {
            if (player.getMainHandItem().is((Item)BFItems.PAINT_SPRAY.get()) || player.getOffhandItem().is((Item)BFItems.PAINT_SPRAY.get())) {
               Entity entity = player.serverLevel().getEntity(msg.entityId);
               if (entity instanceof VehicleEntity vehicle && entity.isAlive() && player.distanceToSqr(entity) <= 64.0 && PaintSprayItem.accepts(entity, msg.skin)) {
                  vehicle.setSkinId(msg.skin);
               }
            }
         }
      }
   }

   public record RequestCamera(BlockPos launcher) {
      static void encode(DeepStrikeNetwork.RequestCamera msg, FriendlyByteBuf buf) {
         buf.writeBlockPos(msg.launcher);
      }

      static DeepStrikeNetwork.RequestCamera decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.RequestCamera(buf.readBlockPos());
      }

      static void handle(DeepStrikeNetwork.RequestCamera msg, Context ctx) {
         ServerPlayer player = ctx.getSender();
         if (player != null && !(player.distanceToSqr(msg.launcher.getX() + 0.5, msg.launcher.getY() + 0.5, msg.launcher.getZ() + 0.5) > 1.6777216E7)
            )
          {
            if (player.level().getBlockEntity(msg.launcher) instanceof LauncherBlockEntity launcher && launcher.family() == LauncherBlock.Family.JAGM) {
               GuidedMissileEntity missile = player.serverLevel()
                  .getEntitiesOfClass(
                     GuidedMissileEntity.class,
                     AABB.ofSize(player.position(), 1200.0, 640.0, 1200.0),
                     e -> e.kind() == MissileKind.JAGM && player.getUUID().equals(e.ownerId())
                  )
                  .stream()
                  .min(Comparator.comparingDouble(player::distanceToSqr))
                  .orElse(null);
               DeepStrikeNetwork.CHANNEL
                  .send(PacketDistributor.PLAYER.with(() -> player), new DeepStrikeNetwork.CameraTarget(missile == null ? -1 : missile.getId()));
            }
         }
      }
   }

   public record RequestCameraFeed() {
      static void encode(DeepStrikeNetwork.RequestCameraFeed msg, FriendlyByteBuf buf) {
      }

      static DeepStrikeNetwork.RequestCameraFeed decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.RequestCameraFeed();
      }

      static void handle(Context ctx) {
         ServerPlayer player = ctx.getSender();
         if (player != null) {
            GuidedMissileEntity missile = player.serverLevel()
               .getEntitiesOfClass(
                  GuidedMissileEntity.class,
                  AABB.ofSize(player.position(), 2400.0, 640.0, 2400.0),
                  e -> e.kind() == MissileKind.JAGM && player.getUUID().equals(e.ownerId())
               )
               .stream()
               .min(Comparator.comparingDouble(player::distanceToSqr))
               .orElse(null);
            DeepStrikeNetwork.CHANNEL
               .send(PacketDistributor.PLAYER.with(() -> player), new DeepStrikeNetwork.CameraTarget(missile == null ? -1 : missile.getId()));
         }
      }
   }

   public record RequestJournal(BlockPos launcher) {
      static void encode(DeepStrikeNetwork.RequestJournal msg, FriendlyByteBuf buf) {
         buf.writeBlockPos(msg.launcher);
      }

      static DeepStrikeNetwork.RequestJournal decode(FriendlyByteBuf buf) {
         return new DeepStrikeNetwork.RequestJournal(buf.readBlockPos());
      }

      static void handle(DeepStrikeNetwork.RequestJournal msg, Context ctx) {
         ServerPlayer player = ctx.getSender();
         if (player != null
            && player.serverLevel().getBlockEntity(msg.launcher) instanceof LauncherBlockEntity unit
            && player.distanceToSqr(Vec3.atCenterOf(msg.launcher)) <= 1.6777216E7) {
            DeepStrikeNetwork.CHANNEL
               .send(
                  PacketDistributor.PLAYER.with(() -> player),
                  new DeepStrikeNetwork.JournalSnapshot(LaunchJournal.get(player.serverLevel()).page(unit.journalId(), 0))
               );
         }
      }
   }

   public record SonicBoomEvent(double x, double y, double z, boolean doubleClap, float speedKmh) {
      static void encode(DeepStrikeNetwork.SonicBoomEvent m, FriendlyByteBuf b) {
         b.writeDouble(m.x);
         b.writeDouble(m.y);
         b.writeDouble(m.z);
         b.writeBoolean(m.doubleClap);
         b.writeFloat(m.speedKmh);
      }

      static DeepStrikeNetwork.SonicBoomEvent decode(FriendlyByteBuf b) {
         return new DeepStrikeNetwork.SonicBoomEvent(b.readDouble(), b.readDouble(), b.readDouble(), b.readBoolean(), b.readFloat());
      }
   }
}
