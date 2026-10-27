package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
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

public final class VehicleDeployItem extends Item {
   private final Supplier<? extends EntityType<? extends Entity>> type;

   public VehicleDeployItem(Supplier<? extends EntityType<? extends Entity>> type, Properties properties) {
      super(properties);
      this.type = type;
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      if (level.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         Entity entity = this.type.get().create(level);
         if (entity == null) {
            return InteractionResult.FAIL;
         } else {
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            float yaw = context.getPlayer() == null ? 0.0F : context.getPlayer().getYRot();
            Vec3 placement = placement(context, entity, yaw);
            if (placement == null) {
               if (context.getPlayer() != null) {
                  context.getPlayer().displayClientMessage(Component.translatable("message.deepstrike.vehicle.clear_ground"), true);
               }

               return InteractionResult.FAIL;
            } else {
               entity.moveTo(placement.x, placement.y, placement.z, yaw, 0.0F);
               if (entity instanceof Su24mEntity aircraft) {
                  aircraft.setGearUp(false);
               }

               if (context.getPlayer() != null) {
                  entity.getPersistentData().putUUID("DeepStrikeOwner", context.getPlayer().getUUID());
               }

               if (level.noCollision(entity, entity.getBoundingBox()) && level.addFreshEntity(entity)) {
                  if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
                     context.getItemInHand().shrink(1);
                  }

                  level.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, pos);
                  return InteractionResult.CONSUME;
               } else {
                  return InteractionResult.FAIL;
               }
            }
         }
      }
   }

   static Vec3 placement(UseOnContext context, Entity entity, float yaw) {
      if (!(entity instanceof Su24mEntity)) {
         return Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace())).add(0.0, 0.02, 0.0);
      } else {
         Level level = context.getLevel();
         Player player = context.getPlayer();
         double angle = Math.toRadians(yaw);
         double sin = Math.sin(angle);
         double cos = Math.cos(angle);
         Vec3 forward = new Vec3(-sin, 0.0, cos);
         Vec3 side = new Vec3(cos, 0.0, sin);
         Vec3 clicked = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
         Vec3 base = player == null ? clicked : player.position().add(forward.scale(17.0));
         double ex = Math.abs(cos) * 9.2 + Math.abs(sin) * 12.5;
         double ez = Math.abs(sin) * 9.2 + Math.abs(cos) * 12.5;

         for (int step = 0; step < 4; step++) {
            for (int offset : new int[]{0, 1, -1}) {
               Vec3 candidate = base.add(forward.scale(step * 3)).add(side.scale(offset * 20));

               for (int dy = 2; dy >= -3; dy--) {
                  int gy = (player == null ? context.getClickedPos().getY() : Mth.floor(player.getY())) + dy - 1;
                  BlockPos ground = BlockPos.containing(candidate.x, gy, candidate.z);
                  VoxelShape shape = level.getBlockState(ground).getCollisionShape(level, ground);
                  if (!shape.isEmpty()) {
                     double floor = gy + shape.max(Axis.Y);
                     AABB bounds = new AABB(
                        candidate.x - ex, floor + 0.05, candidate.z - ez, candidate.x + ex, floor + 6.8, candidate.z + ez
                     );
                     if ((player == null || !bounds.inflate(1.5).intersects(player.getBoundingBox()))
                        && level.noCollision(entity, bounds)
                        && level.getEntities(entity, bounds, e -> e.isAlive() && !(e instanceof ItemEntity)).isEmpty()) {
                        boolean supported = true;

                        for (double[] wheel : new double[][]{{0.0, 6.3}, {-1.4, -0.7525}, {1.4, -0.7525}}) {
                           Vec3 point = candidate.add(side.scale(wheel[0])).add(forward.scale(wheel[1]));
                           BlockPos under = BlockPos.containing(point.x, floor - 0.12, point.z);
                           VoxelShape foot = level.getBlockState(under).getCollisionShape(level, under);
                           if (foot.isEmpty() || Math.abs(under.getY() + foot.max(Axis.Y) - floor) > 0.15) {
                              supported = false;
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
}
