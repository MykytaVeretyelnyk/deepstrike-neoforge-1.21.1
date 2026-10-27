package dev.sbw.deepstrike.network;

import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;

public final class MissileCameraStream {
   private static final String RECOVERY_TAG = "DeepStrikeSpikeTvRecovery";
   private static final TicketType<UUID> VEHICLE_TICKET = TicketType.create("deepstrike_spike_vehicle", Comparator.<UUID>naturalOrder());
   private static final Map<UUID, MissileCameraStream.View> viewers = new HashMap<>();
   private static final Map<UUID, MissileCameraStream.Return> returning = new HashMap<>();

   public static void begin(ServerPlayer player, GuidedMissileEntity missile) {
      if (player != null && missile != null && missile.kind() == MissileKind.SPIKE_NLOS && player.getVehicle() instanceof SpikeNlosEntity vehicle) {
         close(player.getUUID(), false);
         ServerLevel var7 = player.serverLevel();
         ChunkPos chunk = vehicle.chunkPosition();
         MissileCameraStream.View view = new MissileCameraStream.View(
            player,
            missile,
            vehicle,
            var7,
            chunk,
            vehicle.position(),
            player.gameMode.getGameModeForPlayer(),
            player.isInvulnerable(),
            player.isInvisible(),
            player.isNoGravity(),
            player.noPhysics,
            var7.getGameTime() + 40L
         );
         viewers.put(player.getUUID(), view);
         CompoundTag recovery = new CompoundTag();
         recovery.putDouble("X", vehicle.getX());
         recovery.putDouble("Y", vehicle.getY());
         recovery.putDouble("Z", vehicle.getZ());
         recovery.putInt("GameMode", view.gameMode().getId());
         recovery.putBoolean("Invulnerable", view.invulnerable());
         recovery.putBoolean("Invisible", view.invisible());
         recovery.putBoolean("NoGravity", view.noGravity());
         recovery.putBoolean("NoPhysics", view.noPhysics());
         player.getPersistentData().put("DeepStrikeSpikeTvRecovery", recovery);
         var7.getChunkSource().addRegionTicket(VEHICLE_TICKET, chunk, 2, player.getUUID());
         player.stopRiding();
         player.setGameMode(GameType.SPECTATOR);
         player.setInvulnerable(true);
         player.setInvisible(true);
         player.setNoGravity(true);
         player.noPhysics = true;
         player.setDeltaMovement(Vec3.ZERO);
         follow(view);
      }
   }

   public static boolean owns(ServerPlayer player, GuidedMissileEntity missile) {
      MissileCameraStream.View view = player == null ? null : viewers.get(player.getUUID());
      return view != null && view.missile() == missile && missile.isAlive();
   }

   public static void lease(ServerPlayer player, int missileId, int distance, boolean enabled) {
      if (player != null) {
         MissileCameraStream.View view = viewers.get(player.getUUID());
         if (!enabled) {
            if (view != null && view.missile().getId() == missileId) {
               close(player.getUUID(), false);
            }
         } else {
            if (view != null && view.missile().getId() == missileId && view.missile().isAlive()) {
               viewers.put(player.getUUID(), view.withExpiry(player.serverLevel().getGameTime() + 40L));
            }
         }
      }
   }

   public static void tick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         for (MissileCameraStream.View view : List.copyOf(viewers.values())) {
            ServerPlayer player = view.player();
            if (player.hasDisconnected()
               || !view.missile().isAlive()
               || player.serverLevel() != view.level()
               || view.missile().level() != view.level()
               || view.level().getGameTime() > view.expires()) {
               close(player.getUUID(), true);
            } else if (view.level().getGameTime() % 3L == 0L) {
               follow(view);
            }
         }

         for (MissileCameraStream.Return pending : List.copyOf(returning.values())) {
            MissileCameraStream.View viewx = pending.view();
            ServerPlayer player = viewx.player();
            if (player.hasDisconnected()) {
               finishReturn(viewx);
            } else if (pending.ticks() > 0) {
               returning.put(player.getUUID(), new MissileCameraStream.Return(viewx, pending.ticks() - 1, pending.attempts()));
            } else {
               boolean mounted = viewx.vehicle().isAlive()
                  && player.serverLevel() == viewx.level()
                  && (player.getVehicle() == viewx.vehicle() || player.startRiding(viewx.vehicle(), true));
               if (!mounted && viewx.vehicle().isAlive() && pending.attempts() > 0) {
                  returning.put(player.getUUID(), new MissileCameraStream.Return(viewx, 1, pending.attempts() - 1));
               } else {
                  finishReturn(viewx);
               }
            }
         }
      }
   }

   public static void onLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         close(player.getUUID(), false);
         MissileCameraStream.Return pending = returning.remove(player.getUUID());
         if (pending != null) {
            finishReturn(pending.view());
         }
      }
   }

   public static void onLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains("DeepStrikeSpikeTvRecovery", 10)) {
         CompoundTag tag = player.getPersistentData().getCompound("DeepStrikeSpikeTvRecovery");
         player.getPersistentData().remove("DeepStrikeSpikeTvRecovery");
         player.setGameMode(GameType.byId(tag.getInt("GameMode")));
         player.setInvulnerable(tag.getBoolean("Invulnerable"));
         player.setInvisible(tag.getBoolean("Invisible"));
         player.setNoGravity(tag.getBoolean("NoGravity"));
         player.noPhysics = tag.getBoolean("NoPhysics");
         double x = tag.getDouble("X");
         double z = tag.getDouble("Z");
         double y = Math.max(tag.getDouble("Y") + 2.0, (double)(player.serverLevel().getHeight(Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + 1));
         player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
      }
   }

   private static void follow(MissileCameraStream.View view) {
      ServerPlayer player = view.player();
      GuidedMissileEntity missile = view.missile();
      player.setDeltaMovement(Vec3.ZERO);
      player.connection.teleport(missile.getX(), missile.getY(), missile.getZ(), missile.getYRot(), missile.getXRot());
   }

   private static void close(UUID id, boolean notify) {
      MissileCameraStream.View view = viewers.remove(id);
      if (view != null) {
         ServerPlayer player = view.player();
         if (player.serverLevel() == view.level() && !player.hasDisconnected()) {
            Vec3 body = view.vehicle().isAlive() ? view.vehicle().position() : view.fallback();
            double y = view.vehicle().isAlive()
               ? body.y + view.vehicle().getBbHeight() + 0.5
               : Math.max(body.y, (double)(view.level().getHeight(Types.MOTION_BLOCKING, Mth.floor(body.x), Mth.floor(body.z)) + 1));
            player.connection.teleport(body.x, y, body.z, view.vehicle().getYRot(), 0.0F);
            player.setGameMode(view.gameMode());
            player.setInvisible(view.invisible());
            if (view.vehicle().isAlive()) {
               returning.put(id, new MissileCameraStream.Return(view, 5, 20));
            } else {
               finishReturn(view);
            }
         } else {
            Vec3 body = view.vehicle().isAlive() ? view.vehicle().position() : view.fallback();
            player.setPos(body.x, body.y + Math.max(1.5, (double)view.vehicle().getBbHeight()), body.z);
            player.setGameMode(view.gameMode());
            player.setInvisible(view.invisible());
            finishReturn(view);
         }

         if (notify && !player.hasDisconnected()) {
            DeepStrikeNetwork.sendCameraTarget(player, -1);
         }
      }
   }

   private static void finishReturn(MissileCameraStream.View view) {
      returning.remove(view.player().getUUID());
      ServerPlayer player = view.player();
      player.getPersistentData().remove("DeepStrikeSpikeTvRecovery");
      player.setInvulnerable(view.invulnerable());
      player.setNoGravity(view.noGravity());
      player.noPhysics = view.noPhysics();
      view.level().getChunkSource().removeRegionTicket(VEHICLE_TICKET, view.vehicleChunk(), 2, player.getUUID());
   }

   private MissileCameraStream() {
   }

   private record Return(MissileCameraStream.View view, int ticks, int attempts) {
   }

   private record View(
      ServerPlayer player,
      GuidedMissileEntity missile,
      SpikeNlosEntity vehicle,
      ServerLevel level,
      ChunkPos vehicleChunk,
      Vec3 fallback,
      GameType gameMode,
      boolean invulnerable,
      boolean invisible,
      boolean noGravity,
      boolean noPhysics,
      long expires
   ) {
      MissileCameraStream.View withExpiry(long value) {
         return new MissileCameraStream.View(
            this.player,
            this.missile,
            this.vehicle,
            this.level,
            this.vehicleChunk,
            this.fallback,
            this.gameMode,
            this.invulnerable,
            this.invisible,
            this.noGravity,
            this.noPhysics,
            value
         );
      }
   }
}
