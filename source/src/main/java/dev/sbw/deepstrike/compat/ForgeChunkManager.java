package dev.sbw.deepstrike.compat;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.world.chunk.LoadingValidationCallback;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

/** Chunk forcing through a single NeoForge TicketController, with the old Forge call shapes. */
public final class ForgeChunkManager {
   private static TicketController controller;

   private ForgeChunkManager() {
   }

   public static void setForcedChunkLoadingCallback(String modId, LoadingValidationCallback callback) {
      controller = new TicketController(ResourceLocation.fromNamespaceAndPath(modId, "default"), callback);
   }

   public static void register(RegisterTicketControllersEvent event) {
      event.register(controller);
   }

   public static boolean forceChunk(ServerLevel level, String modId, BlockPos owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
      return controller.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
   }

   public static boolean forceChunk(ServerLevel level, String modId, Entity owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
      return controller.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
   }

   public static boolean forceChunk(ServerLevel level, String modId, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking) {
      return controller.forceChunk(level, owner, chunkX, chunkZ, add, ticking);
   }
}
