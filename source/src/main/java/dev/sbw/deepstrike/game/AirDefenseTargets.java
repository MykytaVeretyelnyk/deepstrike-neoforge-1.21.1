package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.data.vehicle.subdata.VehicleType;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.vehicle.DeepStrikeVehicleEntity;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Explosion.BlockInteraction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import dev.sbw.deepstrike.compat.TickEvent.LevelTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class AirDefenseTargets {
   private static final ClassValue<Map<String, Optional<Field>>> FIELDS = new ClassValue<Map<String, Optional<Field>>>() {
      protected Map<String, Optional<Field>> computeValue(Class<?> type) {
         return new HashMap<>();
      }
   };
   private static final Map<ServerLevel, Set<MissileProjectile>> MANPADS = new WeakHashMap<>();

   private static Object field(Object object, String name) {
      if (object == null) {
         return null;
      } else {
         Optional<Field> cached = FIELDS.get(object.getClass()).computeIfAbsent(name, key -> {
            for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
               try {
                  Field f = type.getDeclaredField(key);
                  f.setAccessible(true);
                  return Optional.of(f);
               } catch (ReflectiveOperationException var4x) {
               }
            }

            return Optional.empty();
         });

         try {
            return cached.isPresent() ? cached.get().get(object) : null;
         } catch (IllegalAccessException var4) {
            return null;
         }
      }
   }

   public static boolean aircraft(Entity entity) {
      if (entity == null || !entity.isAlive()) {
         return false;
      } else if (entity instanceof VehicleEntity vehicle) {
         VehicleType type = vehicle.getVehicleType();
         return type == VehicleType.AIRPLANE || type == VehicleType.HELICOPTER || type == VehicleType.AIRSHIP || type == VehicleType.DRONE;
      } else if (!entity.getClass().getSimpleName().equals("BuilderEntityExisting")) {
         return false;
      } else {
         Object internal = field(entity, "entity");
         if (internal != null && internal.getClass().getSimpleName().contains("EntityVehicle")) {
            Object definition = field(internal, "definition");
            Object motorized = field(definition, "motorized");
            return Boolean.TRUE.equals(field(motorized, "isAircraft")) || field(definition, "plane") != null || field(definition, "blimp") != null;
         } else {
            return false;
         }
      }
   }

   public static boolean target(Entity entity) {
      return entity != null && entity.isAlive() && (entity instanceof GuidedMissileEntity missile ? !missile.isIntercepted() : aircraft(entity));
   }

   public static int kind(Entity entity) {
      return entity instanceof GuidedMissileEntity missile
         ? missile.kind().ordinal()
         : (entity instanceof Su24mEntity ? 200 : (entity instanceof VehicleEntity v && v.getVehicleType() == VehicleType.HELICOPTER ? 101 : 100));
   }

   public static String label(int kind) {
      if (kind < 0) {
         return Component.translatable("radar.deepstrike.unknown").getString();
      } else {
         return kind == 200
            ? "Su-24M"
            : (kind == 101
                  ? Component.translatable("label.deepstrike.helicopter")
                  : (kind >= 100 ? Component.translatable("label.deepstrike.aircraft") : MissileKind.byId(kind).title()))
               .getString();
      }
   }

   public static Vec3 aim(Entity entity) {
      return entity instanceof GuidedMissileEntity ? entity.position() : entity.getBoundingBox().getCenter();
   }

   public static void hit(Entity target, Entity projectile, int defense) {
      if (target instanceof GuidedMissileEntity missile) {
         missile.intercept(defense);
      } else if (target.level() instanceof ServerLevel level && aircraft(target)) {
         InterceptionEffects.emit(level, aim(target), defense);
         if (target instanceof VehicleEntity vehicle) {
            target.hurt(level.damageSources().explosion(projectile, projectile), Math.max(300.0F, vehicle.getMaxHealth() * 1.1F));
         } else {
            Vec3 point = aim(target);
            Explosion explosion = new Explosion(level, projectile, point.x, point.y, point.z, 7.0F, false, BlockInteraction.KEEP);
            if (!EventHooks.onExplosionStart(level, explosion)) {
               explosion.explode();
            }
         }
      }
   }

   public static void join(EntityJoinLevelEvent event) {
      if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof MissileProjectile missile && InterceptionEffects.isManpad(missile)) {
         MANPADS.computeIfAbsent(level, key -> new HashSet<>()).add(missile);
      }
   }

   public static void tick(LevelTickEvent event) {
      if (event.level instanceof ServerLevel level) {
         Set<MissileProjectile> missiles = MANPADS.get(level);
         if (missiles != null) {
            Iterator<MissileProjectile> iterator = missiles.iterator();

            while (iterator.hasNext()) {
               MissileProjectile missile = iterator.next();
               if (!missile.isAlive()) {
                  PatriotDefenseNetwork.nativeFlight(missile, false);
                  ManpadFlight.release(missile);
                  iterator.remove();
               } else {
                  CompoundTag tag = missile.getPersistentData();
                  Entity owner = missile.getOwner();
                  Entity target = null;

                  try {
                     target = level.getEntity(UUID.fromString(missile.getTargetUUID()));
                  } catch (IllegalArgumentException var16) {
                  }

                  if (event.phase == Phase.START) {
                     ManpadFlight.prepare(missile, target);
                  } else {
                     ManpadFlight.tickIfUntracked(missile);
                     ManpadFlight.finish(missile);
                     PatriotDefenseNetwork.nativeFlight(missile, missile.isAlive());
                     if (!target(target) && missile.tickCount < 8) {
                        Vec3 direction = owner != null && !(owner instanceof VehicleEntity) && !(owner.getVehicle() instanceof VehicleEntity)
                           ? owner.getLookAngle()
                           : missile.getDeltaMovement().normalize();
                        double best = -1.0;

                        for (Entity candidate : level.getAllEntities()) {
                           if (aircraft(candidate)
                              && candidate != owner
                              && (owner == null || candidate != owner.getVehicle())
                              && candidate.distanceToSqr(missile) < 2.025E7) {
                              Vec3 relative = aim(candidate).subtract(missile.position());
                              double cosine = direction.dot(relative.normalize());
                              if (cosine > Math.cos(Math.toRadians(9.0))
                                 && cosine > best
                                 && FlightCollision.knownClear(level, missile.position(), aim(candidate), missile)) {
                                 best = cosine;
                                 target = candidate;
                              }
                           }
                        }

                        if (target != null) {
                           missile.setTargetUuid(target.getUUID().toString());
                           missile.setLost(false);
                           missile.setLostTarget(false);
                        }
                     }

                     recordManpadLaunch(missile, target);
                     if (aircraft(target) && tag.contains("FlightPrevX")) {
                        Vec3 from = new Vec3(tag.getDouble("FlightPrevX"), tag.getDouble("FlightPrevY"), tag.getDouble("FlightPrevZ"));
                        if (target.getBoundingBox().inflate(0.7).clip(from, missile.position()).isPresent()
                           || target.getBoundingBox().inflate(0.7).contains(missile.position())) {
                           hit(target, missile, 2);
                           missile.discard();
                           iterator.remove();
                           continue;
                        }
                     }

                     tag.putDouble("FlightPrevX", missile.getX());
                     tag.putDouble("FlightPrevY", missile.getY());
                     tag.putDouble("FlightPrevZ", missile.getZ());
                  }
               }
            }
         }
      }
   }

   static void recordManpadLaunch(MissileProjectile missile, Entity target) {
      CompoundTag tag = missile.getPersistentData();
      if (!tag.getBoolean("DeepStrikeLogged")) {
         Entity owner = missile.getOwner();
         Entity unit = owner == null ? null : owner.getVehicle();
         if (unit instanceof DeepStrikeVehicleEntity) {
            LaunchJournal.record(unit, "FIM-92", target == null ? missile.position() : aim(target), Component.translatable("journal.deepstrike.operator"));
         }

         tag.putBoolean("DeepStrikeLogged", true);
      }
   }

   private AirDefenseTargets() {
   }
}
