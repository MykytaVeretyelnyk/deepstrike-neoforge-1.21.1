package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.network.CommandTabletNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class CameraTerrainServer {
   public static final int RADIUS = 3;
   private static final TicketType<UUID> TICKET = TicketType.create("deepstrike_camera", Comparator.<UUID>naturalOrder());
   private static final Map<ServerPlayer, CameraTerrainServer.View> views = new IdentityHashMap<>();

   private static boolean allowed(ServerPlayer p, GuidedMissileEntity missile) {
      CompoundTag tag = missile.getPersistentData();
      return tag.hasUUID("DeepStrikeCommandNetwork")
         ? CommandCenterData.get(p.serverLevel()).canViewNetwork(p, tag.getUUID("DeepStrikeCommandNetwork"))
         : p.getUUID().equals(missile.ownerId());
   }

   private static boolean near(ServerPlayer p, GuidedMissileEntity missile) {
      int distance = Math.max(2, p.getServer().getPlayerList().getViewDistance() - 1) * 16;
      return Math.abs(p.getX() - missile.getX()) < distance && Math.abs(p.getZ() - missile.getZ()) < distance;
   }

   public static boolean watch(ServerPlayer p, UUID id) {
      if (!(p.serverLevel().getEntity(id) instanceof GuidedMissileEntity missile && missile.cameraAvailable())) {
         return false;
      } else if (!allowed(p, missile)) {
         return false;
      } else if (near(p, missile)) {
         close(p);
         return true;
      } else {
         CameraTerrainServer.View old = views.get(p);
         if (old != null && (!old.missile.equals(id) || old.level != p.serverLevel())) {
            close(p);
         }

         if (!views.containsKey(p) && views.size() >= 8) {
            return false;
         } else {
            views.computeIfAbsent(p, key -> new CameraTerrainServer.View(p.serverLevel(), id, p.getServer().getTickCount())).heartbeat = p.getServer().getTickCount();
            return true;
         }
      }
   }

   private static void release(ServerPlayer p, CameraTerrainServer.View view) {
      for (ChunkPos pos : view.held) {
         view.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, p.getUUID());
      }

      view.held.clear();
      view.sent.clear();
   }

   public static void close(ServerPlayer p) {
      CameraTerrainServer.View view = views.remove(p);
      if (view != null) {
         release(p, view);
      }
   }

   public static void stop(ServerStoppedEvent event) {
      for (ServerPlayer p : List.copyOf(views.keySet())) {
         close(p);
      }
   }

   public static void tick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         long now = event.getServer().getTickCount();

         for (ServerPlayer p : List.copyOf(views.keySet())) {
            CameraTerrainServer.View view = views.get(p);
            Entity entity = view.level.getEntity(view.missile);
            if (p.hasDisconnected()
               || p.isRemoved()
               || p.serverLevel() != view.level
               || !(now - view.heartbeat <= 60L && entity instanceof GuidedMissileEntity missile)
               || !missile.cameraAvailable()) {
               close(p);
            } else if (!allowed(p, missile)) {
               CommandTabletNetwork.cameraDenied(p, view.missile);
               close(p);
            } else if (near(p, missile)) {
               if (!view.held.isEmpty()) {
                  release(p, view);
               }
            } else {
               ChunkPos center = entity.chunkPosition();
               List<ChunkPos> wanted = new ArrayList<>();

               for (int dx = -3; dx <= 3; dx++) {
                  for (int dz = -3; dz <= 3; dz++) {
                     wanted.add(new ChunkPos(center.x + dx, center.z + dz));
                  }
               }

               wanted.sort(Comparator.comparingInt(posx -> Math.abs(posx.x - center.x) + Math.abs(posx.z - center.z)));
               Set<ChunkPos> keep = new HashSet<>(wanted);
               Iterator<ChunkPos> it = view.held.iterator();

               while (it.hasNext()) {
                  ChunkPos old = it.next();
                  if (!keep.contains(old)) {
                     view.level.getChunkSource().removeRegionTicket(TICKET, old, 0, p.getUUID());
                     view.sent.remove(old);
                     it.remove();
                  }
               }

               int added = 0;
               int sent = 0;

               for (ChunkPos pos : wanted) {
                  if (!view.held.contains(pos) && added < 4) {
                     view.level.getChunkSource().addRegionTicket(TICKET, pos, 0, p.getUUID());
                     view.held.add(pos);
                     added++;
                  }

                  if (sent < 3 && view.held.contains(pos)) {
                     long interval = Math.max(Math.abs(pos.x - center.x), Math.abs(pos.z - center.z)) <= 1 ? 40L : 200L;
                     if (now - view.sent.getOrDefault(pos, -10000L) >= interval) {
                        LevelChunk chunk = view.level.getChunkSource().getChunkNow(pos.x, pos.z);
                        if (chunk != null) {
                           CommandTabletNetwork.terrain(p, view.missile, new ClientboundLevelChunkWithLightPacket(chunk, view.level.getLightEngine(), null, null));
                           view.sent.put(pos, now);
                           sent++;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static int viewers() {
      return views.size();
   }

   private static final class View {
      final ServerLevel level;
      final UUID missile;
      final Set<ChunkPos> held = new HashSet<>();
      final Map<ChunkPos, Long> sent = new HashMap<>();
      long heartbeat;

      View(ServerLevel level, UUID missile, long tick) {
         this.level = level;
         this.missile = missile;
         this.heartbeat = tick;
      }
   }
}
