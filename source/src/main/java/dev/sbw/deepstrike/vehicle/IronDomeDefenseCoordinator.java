package dev.sbw.deepstrike.vehicle;

import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class IronDomeDefenseCoordinator {
   private static final int RETRY_DELAY_TICKS = 10;
   private static final int COORDINATION_SCAN_TICKS = 5;
   private static final Map<ServerLevel, IronDomeDefenseCoordinator.State> STATES = new WeakHashMap<>();

   private IronDomeDefenseCoordinator() {
   }

   static boolean engaging(ServerLevel level, UUID target) {
      IronDomeDefenseCoordinator.State state = STATES.get(level);
      return state != null && state.engagements.containsKey(target);
   }

   static boolean fireManual(ServerLevel level, IronDomeEntity battery, Entity target) {
      IronDomeDefenseCoordinator.State state = STATES.computeIfAbsent(level, ignored -> new IronDomeDefenseCoordinator.State());
      if (!state.engagements.containsKey(target.getUUID()) && battery.getReadyTamir() > 0 && !battery.isReloading()) {
         PatriotInterceptorEntity interceptor = battery.launchCoordinated(level, target, 1, true);
         if (interceptor == null) {
            return false;
         } else {
            state.engagements
               .put(
                  target.getUUID(), new IronDomeDefenseCoordinator.Engagement(target.getUUID(), battery.getUUID(), interceptor.getUUID(), level.getGameTime())
               );
            state.batteryFiredAt.put(battery.getUUID(), level.getGameTime());
            return true;
         }
      } else {
         return false;
      }
   }

   static void tick(ServerLevel level) {
      long now = level.getGameTime();
      IronDomeDefenseCoordinator.State state = STATES.computeIfAbsent(level, ignored -> new IronDomeDefenseCoordinator.State());
      if (state.lastTick != now) {
         if (now >= state.nextScanTick) {
            state.lastTick = now;
            state.nextScanTick = now + 5L;
            state.batteryFiredAt.entrySet().removeIf(entry -> entry.getValue() < now);
            ArrayList<IronDomeEntity> batteries = new ArrayList<>();
            ArrayList<Entity> targets = new ArrayList<>();

            for (Entity entity : level.getAllEntities()) {
               if (entity instanceof IronDomeEntity battery && battery.isAlive()) {
                  batteries.add(battery);
               } else if (AirDefenseTargets.target(entity)) {
                  targets.add(entity);
               }
            }

            batteries.sort(Comparator.comparing(Entity::getUUID));
            targets.sort(Comparator.<Entity>comparingDouble(targetx -> urgency(level, targetx, batteries)).thenComparing(Entity::getUUID));

            for (Entity entityx : level.getAllEntities()) {
               if (entityx instanceof PatriotInterceptorEntity interceptor
                  && (
                     interceptor.isDomeLaunch()
                        || (interceptor.defenseSource() == null ? null : level.getEntity(interceptor.defenseSource())) instanceof IronDomeEntity
                  )
                  && interceptor.isAlive()
                  && interceptor.targetId() != null
                  && interceptor.defenseSource() != null) {
                  IronDomeDefenseCoordinator.Engagement engagement = state.engagements.get(interceptor.targetId());
                  if (engagement == null) {
                     engagement = new IronDomeDefenseCoordinator.Engagement(interceptor.targetId(), interceptor.defenseSource(), interceptor.getUUID(), now);
                     state.engagements.put(interceptor.targetId(), engagement);
                  } else {
                     if (!engagement.batteries.contains(interceptor.defenseSource())) {
                        engagement.batteries.add(interceptor.defenseSource());
                     }

                     if (!engagement.interceptors.contains(interceptor.getUUID())) {
                        engagement.interceptors.add(interceptor.getUUID());
                     }
                  }

                  engagement.wave = Math.max(engagement.wave, interceptor.getPersistentData().getInt("IronDomeVolley"));
               }
            }

            state.engagements.entrySet().removeIf(entry -> {
               Entity targetx = level.getEntity(entry.getKey());
               return targetx != null ? !AirDefenseTargets.target(targetx) : !ActiveFlights.get(level).hasActiveFlight(entry.getKey());
            });

            for (IronDomeDefenseCoordinator.Engagement engagement : new ArrayList<>(state.engagements.values())) {
               Entity target = level.getEntity(engagement.target);
               if (AirDefenseTargets.target(target)) {
                  boolean interceptorInFlight = false;

                  for (UUID interceptorId : engagement.interceptors) {
                     Entity interceptor = level.getEntity(interceptorId);
                     if (interceptor != null ? interceptor.isAlive() : ActiveFlights.get(level).hasActiveFlight(interceptorId)) {
                        interceptorInFlight = true;
                        break;
                     }
                  }

                  if (interceptorInFlight) {
                     engagement.interceptorsInFlight = true;
                  } else if (engagement.interceptorsInFlight) {
                     engagement.interceptorsInFlight = false;
                     engagement.nextWave = now + 10L;
                  } else if (now >= engagement.nextWave) {
                     ArrayList<IronDomeEntity> volley = new ArrayList<>();
                     IronDomeEntity retry = bestBattery(level, target, batteries, state, now, List.of());
                     if (retry != null) {
                        if (!engagement.batteries.contains(retry.getUUID())) {
                           engagement.batteries.add(retry.getUUID());
                        }

                        volley.add(retry);
                     }

                     engagement.interceptors.clear();
                     int nextWave = engagement.wave + 1;

                     for (IronDomeEntity battery : volley) {
                        PatriotInterceptorEntity interceptor = battery.launchCoordinated(level, target, nextWave, false);
                        if (interceptor != null) {
                           engagement.interceptors.add(interceptor.getUUID());
                           state.batteryFiredAt.put(battery.getUUID(), now);
                        }
                     }

                     if (engagement.interceptors.isEmpty()) {
                        engagement.nextWave = now + 20L;
                     } else {
                        engagement.wave = nextWave;
                        engagement.nextWave = now + 10L;
                        engagement.interceptorsInFlight = true;
                     }
                  }
               }
            }

            for (Entity target : targets) {
               if (!state.engagements.containsKey(target.getUUID())) {
                  IronDomeEntity batteryx = bestBattery(level, target, batteries, state, now, List.of());
                  if (batteryx != null) {
                     PatriotInterceptorEntity interceptor = batteryx.launchCoordinated(level, target, 1, false);
                     if (interceptor != null) {
                        state.engagements
                           .put(
                              target.getUUID(), new IronDomeDefenseCoordinator.Engagement(target.getUUID(), batteryx.getUUID(), interceptor.getUUID(), now)
                           );
                        state.batteryFiredAt.put(batteryx.getUUID(), now);
                     }
                  }
               }
            }
         }
      }
   }

   private static IronDomeEntity bestBattery(
      ServerLevel level, Entity target, List<IronDomeEntity> batteries, IronDomeDefenseCoordinator.State state, long now, List<UUID> excluded
   ) {
      IronDomeEntity best = null;
      double bestScore = Double.POSITIVE_INFINITY;
      Vec3 aim = AirDefenseTargets.aim(target);

      for (IronDomeEntity battery : batteries) {
         if (!excluded.contains(battery.getUUID())
            && !Objects.equals(state.batteryFiredAt.get(battery.getUUID()), now)
            && battery.canAutoEngage(level, target)) {
            double score = battery.engagementScore(level, aim);
            if (score < bestScore) {
               bestScore = score;
               best = battery;
            }
         }
      }

      return best;
   }

   private static IronDomeEntity findBattery(List<IronDomeEntity> batteries, UUID id) {
      for (IronDomeEntity battery : batteries) {
         if (battery.getUUID().equals(id)) {
            return battery;
         }
      }

      return null;
   }

   private static double urgency(ServerLevel level, Entity target, List<IronDomeEntity> batteries) {
      Vec3 aim = AirDefenseTargets.aim(target);
      double time = Double.POSITIVE_INFINITY;

      for (IronDomeEntity battery : batteries) {
         if (battery.canAutoEngage(level, target)) {
            double distance = aim.distanceTo(battery.position());
            double speed = Math.max(1.0, target.getDeltaMovement().length());
            time = Math.min(time, distance / speed);
         }
      }

      return time;
   }

   private static final class Engagement {
      final UUID target;
      final ArrayList<UUID> batteries = new ArrayList<>();
      final ArrayList<UUID> interceptors = new ArrayList<>();
      long nextWave;
      int wave = 1;
      boolean interceptorsInFlight = true;

      Engagement(UUID target, UUID battery, UUID interceptor, long now) {
         this.target = target;
         this.batteries.add(battery);
         this.interceptors.add(interceptor);
         this.nextWave = now + 10L;
      }
   }

   private static final class State {
      long lastTick = Long.MIN_VALUE;
      long nextScanTick;
      final Map<UUID, IronDomeDefenseCoordinator.Engagement> engagements = new LinkedHashMap<>();
      final Map<UUID, Long> batteryFiredAt = new HashMap<>();
   }
}
