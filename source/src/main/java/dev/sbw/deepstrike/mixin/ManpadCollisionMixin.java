package dev.sbw.deepstrike.mixin;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.entity.projectile.IAdvancedHitDetection.Companion;
import com.atsuishio.superbwarfare.world.phys.EntityResult;
import dev.sbw.deepstrike.game.ManpadFlight;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
   value = {FastThrowableProjectile.class},
   remap = false
)
public abstract class ManpadCollisionMixin {
   private boolean deepstrike$hasPhysicalPath() {
      return (Object)this instanceof MissileProjectile missile && ManpadFlight.controlsNativeCollision(missile);
   }

   @Redirect(
      method = {"tick"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/atsuishio/superbwarfare/entity/projectile/IAdvancedHitDetection$Companion;rayTraceBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;Ljava/util/function/Predicate;)Lnet/minecraft/world/phys/BlockHitResult;"
      ),
      require = 1
   )
   private BlockHitResult deepstrike$physicalBlockPath(Companion detector, Level level, ClipContext context, Predicate<BlockState> predicate) {
      if (!this.deepstrike$hasPhysicalPath()) {
         return detector.rayTraceBlocks(level, context, predicate);
      } else {
         Vec3 to = context.getTo();
         return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
      }
   }

   @Redirect(
      method = {"tick"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/atsuishio/superbwarfare/entity/projectile/FastThrowableProjectile;findEntitiesOnPath(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Ljava/util/List;"
      ),
      require = 1
   )
   private List<EntityResult> deepstrike$physicalEntityPath(FastThrowableProjectile projectile, Vec3 from, Vec3 to) {
      return this.deepstrike$hasPhysicalPath() ? List.of() : projectile.findEntitiesOnPath(from, to);
   }
}
