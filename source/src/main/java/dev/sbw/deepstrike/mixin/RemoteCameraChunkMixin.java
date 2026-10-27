package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.client.RemoteCameraTerrain;
import dev.sbw.deepstrike.client.smoke.SmokeFlow;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.BlockEntityTagOutput;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {ClientChunkCache.class},
   remap = false
)
public abstract class RemoteCameraChunkMixin {
   @Inject(
      method = {"getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void deepstrike$cameraChunk(int x, int z, ChunkStatus status, boolean required, CallbackInfoReturnable<LevelChunk> cir) {
      if (!RemoteCameraTerrain.vanillaLookup && (cir.getReturnValue() == null || cir.getReturnValue() instanceof EmptyLevelChunk)) {
         LevelChunk remote = RemoteCameraTerrain.get(x, z);
         if (remote != null) {
            cir.setReturnValue(remote);
         }
      }
   }

   @Inject(
      method = {"replaceWithPacketData"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void deepstrike$cameraData(
      int x, int z, FriendlyByteBuf data, CompoundTag heights, Consumer<BlockEntityTagOutput> entities, CallbackInfoReturnable<LevelChunk> cir
   ) {
      SmokeFlow.invalidateChunk(x, z);
      if (RemoteCameraTerrain.receiving) {
         cir.setReturnValue(RemoteCameraTerrain.replace(x, z, data, heights, entities));
      }
   }
}
