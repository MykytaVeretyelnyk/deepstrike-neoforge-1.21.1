package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AirDefenseSpawnItem extends Item {
   private final Supplier<? extends EntityType<? extends Entity>> entityTypeSupplier;

   public AirDefenseSpawnItem(Supplier<? extends EntityType<? extends Entity>> entityTypeSupplier, Properties properties) {
      super(properties);
      this.entityTypeSupplier = entityTypeSupplier;
   }


   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (level.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         BlockPos pos = context.getClickedPos();
         Direction direction = context.getClickedFace();
         BlockPos spawnPos = direction == Direction.UP ? pos.above() : pos.relative(direction);
         EntityType<?> type = (EntityType<?>)this.entityTypeSupplier.get();
         Entity entity = type.create(level);
         if (entity != null) {
            UUID transferId = null;
            UUID transferToken = null;
            CompoundTag itemTag = NbtCompat.getTag(context.getItemInHand());
            if (itemTag != null && itemTag.contains("DeepStrikeVehicle", 10)) {
               CompoundTag snapshot = itemTag.getCompound("DeepStrikeVehicle").copy();
               UUID freshId = entity.getUUID();
               boolean transfer = snapshot.hasUUID("UUID")
                  && itemTag.hasUUID("DeepStrikeTransfer")
                  && DefenseRuntime.get((ServerLevel)level).canTransfer(snapshot.getUUID("UUID"), itemTag.getUUID("DeepStrikeTransfer"));
               if (transfer) {
                  transferId = snapshot.getUUID("UUID");
                  transferToken = itemTag.getUUID("DeepStrikeTransfer");
                  if (((ServerLevel)level).getEntity(transferId) != null) {
                     return InteractionResult.FAIL;
                  }
               }

               snapshot.remove("Passengers");
               snapshot.remove("PendingLaunch");
               if (!transfer) {
                  snapshot.remove("SpikeActiveMissile");
                  snapshot.remove("Engagements");
                  snapshot.putUUID("UUID", freshId);
                  snapshot.getCompound("ForgeData").remove("LaunchJournalId");
                  snapshot.getCompound("ForgeData").remove("DeepStrikeOwner");
               }

               entity.load(snapshot);
               entity.setDeltaMovement(Vec3.ZERO);
            }

            if (context.getPlayer() != null && !entity.getPersistentData().hasUUID("DeepStrikeOwner")) {
               entity.getPersistentData().putUUID("DeepStrikeOwner", context.getPlayer().getUUID());
               entity.getPersistentData().putBoolean("DeepStrikePublicAccess", true);
            }

            float yaw = context.getPlayer() != null ? context.getPlayer().getYRot() : 0.0F;
            Vec3 placement = findPlacement(context, entity, spawnPos, yaw);
            if (placement == null) {
               if (context.getPlayer() != null) {
                  context.getPlayer().displayClientMessage(Component.translatable("message.deepstrike.vehicle.clear_ground"), true);
               }

               return InteractionResult.FAIL;
            }

            entity.moveTo(placement.x, placement.y, placement.z, yaw, 0.0F);
            if ((itemTag == null || !itemTag.contains("DeepStrikeVehicle")) && entity instanceof VehicleEntity vehicle) {
               vehicle.setSkinId(entity instanceof SpikeNlosEntity ? "sand" : "forest");
            }

            if (!level.addFreshEntity(entity)) {
               if (transferId != null) {
                  DefenseRuntime.get((ServerLevel)level).restoreFailedTransfer((ServerLevel)level, transferId, transferToken);
               }

               return InteractionResult.FAIL;
            }

            if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
               context.getItemInHand().shrink(1);
            }

            level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, BlockPos.containing(placement));
         }

         return InteractionResult.CONSUME;
      }
   }

   private static Vec3 findPlacement(UseOnContext context, Entity entity, BlockPos clicked, float yaw) {
      Player player = context.getPlayer();
      Level level = context.getLevel();
      double halfLength = entity instanceof PatriotTruckEntity ? 8.5 : (entity instanceof SpikeNlosEntity ? 3.6 : 3.0);
      double halfWidth = entity instanceof PatriotTruckEntity ? 1.6 : 1.5;
      double radians = Math.toRadians(yaw);
      double sin = Math.sin(radians);
      double cos = Math.cos(radians);
      Vec3 forward = new Vec3(-sin, 0.0, cos);
      Vec3 side = new Vec3(cos, 0.0, sin);
      Vec3 base = player == null ? Vec3.atBottomCenterOf(clicked) : player.position().add(forward.scale(halfLength + 3.0));
      double extentX = Math.abs(cos) * halfWidth + Math.abs(sin) * halfLength;
      double extentZ = Math.abs(sin) * halfWidth + Math.abs(cos) * halfLength;

      for (int step = 0; step < 4; step++) {
         for (int lateral : new int[]{0, 1, -1}) {
            Vec3 candidate = base.add(forward.scale(step * 2)).add(side.scale(lateral * (halfWidth * 2.0 + 1.0)));

            for (int dy = 2; dy >= -3; dy--) {
               int groundY = (player == null ? clicked.getY() : Mth.floor(player.getY())) + dy - 1;
               BlockPos ground = new BlockPos(Mth.floor(candidate.x), groundY, Mth.floor(candidate.z));
               VoxelShape shape = level.getBlockState(ground).getCollisionShape(level, ground);
               if (!shape.isEmpty()) {
                  double floor = groundY + shape.max(Axis.Y);
                  AABB bounds = new AABB(
                     candidate.x - extentX,
                     floor + 0.03,
                     candidate.z - extentZ,
                     candidate.x + extentX,
                     floor + 3.8,
                     candidate.z + extentZ
                  );
                  if ((player == null || !bounds.inflate(1.5).intersects(player.getBoundingBox()))
                     && level.noCollision(entity, bounds)
                     && level.getEntities(entity, bounds, e -> e.isAlive() && !(e instanceof ItemEntity)).isEmpty()) {
                     boolean supported = true;

                     for (int sx : new int[]{-1, 1}) {
                        for (int sz : new int[]{-1, 1}) {
                           Vec3 support = candidate.add(side.scale(sx * halfWidth * 0.8)).add(forward.scale(sz * halfLength * 0.85));
                           BlockPos under = BlockPos.containing(support.x, floor - 0.15, support.z);
                           if (level.getBlockState(under).getCollisionShape(level, under).isEmpty()) {
                              supported = false;
                           }
                        }
                     }

                     if (supported) {
                        return new Vec3(candidate.x, floor + 0.02, candidate.z);
                     }
                  }
               }
            }
         }
      }

      return null;
   }
}
