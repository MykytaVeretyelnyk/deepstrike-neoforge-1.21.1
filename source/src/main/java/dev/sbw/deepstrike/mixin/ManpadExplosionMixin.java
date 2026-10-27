package dev.sbw.deepstrike.mixin;

import com.atsuishio.superbwarfare.tools.ParticleTool;
import com.atsuishio.superbwarfare.tools.ParticleTool.ParticleType;
import dev.sbw.deepstrike.game.InterceptionEffects;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.validation.AirDefenseReview;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   targets = {"com.atsuishio.superbwarfare.tools.CustomExplosion$Builder"},
   remap = false
)
public abstract class ManpadExplosionMixin {
   @Shadow
   private Entity directSource;

   @Redirect(
      method = {"explode"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/atsuishio/superbwarfare/tools/ParticleTool;spawnExplosionParticles(Lcom/atsuishio/superbwarfare/tools/ParticleTool$ParticleType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)V"
      ),
      require = 1
   )
   private void deepstrike$sharedSmoke(ParticleType type, Level level, Vec3 position) {
      if (InterceptionEffects.isManpad(this.directSource) && level instanceof ServerLevel server) {
         DeepStrikeNetwork.nativeExplosion(server, position);
         if (Boolean.getBoolean("deepstrike.airDefenseReview")) {
            AirDefenseReview.nativeExplosionVisuals++;
         }
      } else {
         ParticleTool.spawnExplosionParticles(type, level, position);
      }
   }
}
