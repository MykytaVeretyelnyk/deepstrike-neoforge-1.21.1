package dev.sbw.deepstrike.mixin;

import dev.sbw.deepstrike.client.LaunchEffectWarmup;
import dev.sbw.deepstrike.client.smoke.SmokeFlow;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.LauncherPartBlockEntity;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {LauncherBlockEntity.class, LauncherPartBlockEntity.class},
   remap = false
)
public abstract class SmokeLauncherDataMixin {
   @Unique
   private long[] deepstrike$smokeBits;

   @Inject(
      method = {"loadAdditional"},
      at = {@At("RETURN")}
   )
   private void deepstrike$smokeData(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, CallbackInfo info) {
      BlockEntity be = ((BlockEntity)(Object)this);
      if (be.getLevel() instanceof ClientLevel world) {
         long[] bits = tag.getLongArray((Object)this instanceof LauncherBlockEntity ? "CollisionVoxels" : "Voxels");
         if (!Arrays.equals(bits, this.deepstrike$smokeBits)) {
            this.deepstrike$smokeBits = bits;
            SmokeFlow.invalidateBlock(world, be.getBlockPos());
         }

         if ((Object)this instanceof LauncherBlockEntity && tag.getInt("FiringSlot") >= 0 && !tag.getBoolean("CommandHold")) {
            long end = tag.getLong("LaunchAtTick");
            if (end > world.getGameTime()) {
               LaunchEffectWarmup.accept(
                  new DeepStrikeNetwork.LaunchCountdown(
                     be.getBlockPos(), tag.hasUUID("QueuedMissionId") ? tag.getUUID("QueuedMissionId") : null, end, world.getGameTime(), true, "PREPARING"
                  )
               );
            }
         }
      }
   }
}
