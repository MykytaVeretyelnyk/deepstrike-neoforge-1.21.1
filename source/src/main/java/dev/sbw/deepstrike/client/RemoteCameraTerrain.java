package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.network.CommandTabletNetwork;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;

public final class RemoteCameraTerrain {
   private static ClientLevel level;
   private static UUID missile;
   private static int ticks;
   public static boolean receiving;
   public static boolean vanillaLookup;
   private static final Map<Long, LevelChunk> chunks = new LinkedHashMap<>();

   public static void denied(UUID id) {
      if (id.equals(missile)) {
         ClientCamera.restore();
         LocalPlayer p = Minecraft.getInstance().player;
         if (p != null) {
            p.displayClientMessage(CommandTabletScreen.tr("error.camera"), true);
         }
      }
   }

   public static void start(UUID id) {
      if (!id.equals(missile)) {
         clear();
         missile = id;
         level = Minecraft.getInstance().level;
         ticks = 0;
      }

      CommandTabletNetwork.watch(id, true);
   }

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      if (missile != null) {
         if (mc.level != level) {
            clear();
         } else {
            if (++ticks % 10 == 0) {
               CommandTabletNetwork.watch(missile, true);
            }

            if (mc.getCameraEntity() != null) {
               ChunkPos center = mc.getCameraEntity().chunkPosition();
               Iterator<Entry<Long, LevelChunk>> it = chunks.entrySet().iterator();

               while (it.hasNext()) {
                  Entry<Long, LevelChunk> e = it.next();
                  ChunkPos pos = e.getValue().getPos();
                  if (Math.max(Math.abs(pos.x - center.x), Math.abs(pos.z - center.z)) > 4) {
                     unload(e.getValue());
                     it.remove();
                  }
               }
            }
         }
      }
   }

   public static void clear() {
      UUID previous = missile;
      missile = null;

      for (LevelChunk chunk : chunks.values()) {
         unload(chunk);
      }

      chunks.clear();
      level = null;
      receiving = false;
      Minecraft mc = Minecraft.getInstance();
      if (previous != null && mc.getConnection() != null) {
         CommandTabletNetwork.watch(previous, false);
      }
   }

   private static void unload(LevelChunk chunk) {
      if (level != null) {
         vanillaLookup = true;

         LevelChunk normal;
         try {
            normal = level.getChunkSource().getChunk(chunk.getPos().x, chunk.getPos().z, ChunkStatus.FULL, false);
         } finally {
            vanillaLookup = false;
         }

         if (normal != null && !(normal instanceof EmptyLevelChunk)) {
            chunk.clearAllBlockEntities();
            return;
         }

         level.unload(chunk);

         for (int section = level.getMinSection(); section < level.getMaxSection(); section++) {
            level.setSectionDirtyWithNeighbors(chunk.getPos().x, section, chunk.getPos().z);
         }
      }
   }

   public static LevelChunk get(int x, int z) {
      return missile != null && Minecraft.getInstance().level == level ? chunks.get(ChunkPos.asLong(x, z)) : null;
   }

   public static LevelChunk replace(int x, int z, FriendlyByteBuf data, CompoundTag heights, Consumer<BlockEntityTagOutput> entities) {
      if (level == null) {
         return null;
      } else {
         long key = ChunkPos.asLong(x, z);
         LevelChunk chunk = chunks.computeIfAbsent(key, k -> new LevelChunk(level, new ChunkPos(x, z)));
         chunk.replaceWithPacketData(data, heights, entities);
         level.onChunkLoaded(chunk.getPos());
         NeoForge.EVENT_BUS.post(new Load(chunk, false));
         return chunk;
      }
   }

   public static void accept(UUID id, ClientboundLevelChunkWithLightPacket packet) {
      Minecraft mc = Minecraft.getInstance();
      if (id.equals(missile) && mc.level == level && mc.getConnection() != null) {
         if (mc.getCameraEntity() != null) {
            ChunkPos center = mc.getCameraEntity().chunkPosition();
            if (Math.max(Math.abs(packet.getX() - center.x), Math.abs(packet.getZ() - center.z)) > 4) {
               return;
            }
         }

         receiving = true;

         try {
            mc.getConnection().handleLevelChunkWithLight(packet);
         } finally {
            receiving = false;
         }
      }
   }

   public static int size() {
      return chunks.size();
   }
}
