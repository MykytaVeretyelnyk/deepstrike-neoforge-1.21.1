package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

public final class ManpadFlight {
   private static final Map<MissileProjectile, ManpadFlight.Prepared> PREPARED = new WeakHashMap<>();
   private static final Map<MissileProjectile, Integer> LAST_TICK = new WeakHashMap<>();
   private static final TicketType<UUID> TICKET = TicketType.create("dsm_manpad", Comparator.comparing(UUID::toString));
   private static final Map<MissileProjectile, Set<ChunkPos>> CHUNKS = new WeakHashMap<>();

   public static boolean beginTick(MissileProjectile missile) {
      if (!(missile.level() instanceof ServerLevel level && profile(missile) != null)) {
         return true;
      } else if (!missile.isAlive()) {
         return false;
      } else {
         int tick = level.getServer().getTickCount();
         if (Objects.equals(LAST_TICK.get(missile), tick)) {
            return false;
         } else {
            LAST_TICK.put(missile, tick);
            if (!PREPARED.containsKey(missile)) {
               Entity target = null;

               try {
                  target = level.getEntity(UUID.fromString(missile.getTargetUUID()));
               } catch (IllegalArgumentException var5) {
               }

               prepare(missile, target);
            }

            return missile.isAlive();
         }
      }
   }

   private static void retainTerrain(MissileProjectile missile) {
      if (missile.level() instanceof ServerLevel level) {
         Set<ChunkPos> var8 = CHUNKS.computeIfAbsent(missile, key -> new HashSet<>());
         HashSet<ChunkPos> next = new HashSet<>();
         if (missile.getY() < level.getMaxBuildHeight() + 32) {
            ChunkPos centre = missile.chunkPosition();

            for (int x = centre.x - 1; x <= centre.x + 1; x++) {
               for (int z = centre.z - 1; z <= centre.z + 1; z++) {
                  ChunkPos chunk = new ChunkPos(x, z);
                  next.add(chunk);
                  if (var8.add(chunk)) {
                     level.getChunkSource().addRegionTicket(TICKET, chunk, 2, missile.getUUID());
                  }
               }
            }
         }

         for (ChunkPos chunk : var8) {
            if (!next.contains(chunk)) {
               level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, missile.getUUID());
            }
         }

         var8.clear();
         var8.addAll(next);
      }
   }

   public static void release(MissileProjectile missile) {
      Set<ChunkPos> chunks = CHUNKS.remove(missile);
      PREPARED.remove(missile);
      LAST_TICK.remove(missile);
      if (chunks != null && missile.level() instanceof ServerLevel level) {
         for (ChunkPos chunk : chunks) {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, missile.getUUID());
         }
      }
   }

   public static void tickIfUntracked(MissileProjectile missile) {
      ManpadFlight.Prepared prepared = PREPARED.get(missile);
      if (prepared != null && missile.isAlive() && missile.getSyncedTick() == prepared.syncedTick && missile.level() instanceof ServerLevel level) {
         level.tickNonPassenger(missile);
      }
   }

   public static boolean controlsNativeCollision(MissileProjectile missile) {
      return missile.level() instanceof ServerLevel && PREPARED.containsKey(missile);
   }

   private static ManpadDynamics.Profile profile(MissileProjectile missile) {
      return missile instanceof Fim92MissileEntity ? ManpadDynamics.Profile.FIM92 : (missile instanceof IglaMissileEntity ? ManpadDynamics.Profile.IGLA : null);
   }

   public static void prepare(MissileProjectile missile, Entity target) {
      ManpadDynamics.Profile profile = profile(missile);
      if (profile != null && missile.isAlive()) {
         if (missile.level() instanceof ServerLevel server && missile.getPersistentData().getInt("DSMManpadAge") >= 500) {
            InterceptionEffects.emit(server, missile.position(), 2);
            PatriotDefenseNetwork.nativeFlight(missile, false);
            release(missile);
            missile.discard();
         } else {
            retainTerrain(missile);
            CompoundTag tag = missile.getPersistentData();
            int age = Math.max(0, tag.contains("DSMManpadAge") ? tag.getInt("DSMManpadAge") : missile.getSyncedTick());
            boolean launched = missile.getSyncedTick() > 0;
            boolean saved = tag.contains("DSMManpadVx", 99) && tag.contains("DSMManpadVy", 99) && tag.contains("DSMManpadVz", 99);
            Vec3 velocity = saved ? new Vec3(tag.getDouble("DSMManpadVx"), tag.getDouble("DSMManpadVy"), tag.getDouble("DSMManpadVz")) : missile.getDeltaMovement();
            double limit = !saved && !launched ? 10.0 : 168.42105263157893;
            if (!saved) {
               velocity = velocity.scale(20.0);
            }

            if (!Double.isFinite(velocity.lengthSqr())) {
               velocity = Vec3.ZERO;
            }

            if (velocity.length() > limit) {
               velocity = velocity.normalize().scale(limit);
            }

            if (!saved && !launched && velocity.lengthSqr() < 1.0E-10) {
               velocity = missile.getLookAngle().scale(10.0);
            }

            boolean powered = !missile.isLost() && !missile.isLostTarget();
            Vec3 direction = velocity.lengthSqr() > 1.0E-10 ? velocity.normalize() : missile.getLookAngle();
            Vec3 aim = missile.position().add(direction.scale(1000.0));
            if (powered && target != null && target.isAlive()) {
               if (target instanceof GuidedMissileEntity guided) {
                  List<Vec3> forecast = guided.interceptOriginForecast();
                  Vec3 bodyOffset = MissileModelContact.aimOffset(guided, 0.25F + 0.5F * ((missile.getUUID().hashCode() & 65535) / 65535.0F));
                  Vec3 noseOffset = ManpadGeometry.nose(missile, Vec3.ZERO, velocity);
                  Vec3 correction = bodyOffset.subtract(noseOffset);
                  double travel = 0.0;
                  double speed = velocity.length();
                  double gap = -missile.position().distanceTo(forecast.get(0).add(correction));
                  Vec3 previous = forecast.get(0).add(correction);
                  boolean reachable = false;

                  for (int future = 1; future < forecast.size(); future++) {
                     speed = Math.min(168.42105263157893, speed + profile.acceleration * 0.05);
                     travel += speed * 0.05;
                     aim = forecast.get(future).add(correction);
                     double nextGap = travel - missile.position().distanceTo(aim);
                     if (nextGap >= 0.0) {
                        reachable = true;
                        aim = previous.lerp(aim, Mth.clamp(-gap / (nextGap - gap), 0.0, 1.0));
                        break;
                     }

                     previous = aim;
                     gap = nextGap;
                  }

                  if (!reachable) {
                     double lead = Math.min(80.0, missile.position().distanceTo(target.position()) / Math.max(0.25, 168.42105263157893 * 0.05));
                     aim = target.position().add(correction).add(target.getDeltaMovement().scale(lead));
                  }
               } else {
                  double lead = Math.min(4.0, missile.position().distanceTo(AirDefenseTargets.aim(target)) / Math.max(30.0, velocity.length()));
                  aim = AirDefenseTargets.aim(target).add(target.getDeltaMovement().scale(lead * 20.0));
               }
            }

            Vec3 start = missile.position();
            Vec3 aimRay = aim.subtract(start);
            double lookAhead = velocity.length() * 0.05 * 2.0 + profile.acceleration * 0.05 * 0.05;
            if (aimRay.lengthSqr() > 1.0E-10 && aimRay.lengthSqr() < lookAhead * lookAhead) {
               aim = start.add(aimRay.normalize().scale(lookAhead));
            }

            ManpadDynamics.State state = new ManpadDynamics.State(start, velocity);
            ArrayList<FlightCollision.Segment> segments = new ArrayList<>(4);

            for (int i = 0; i < 4; i++) {
               ManpadDynamics.State before = state;
               state = ManpadDynamics.step(profile, state, aim, powered);
               segments.add(
                  new FlightCollision.Segment(
                     before.position(), state.position(), ManpadGeometry.substepBounds(missile, before.position(), before.velocity(), state.velocity())
                  )
               );
            }

            if (missile.level() instanceof ServerLevel level) {
               Entity owner = missile.getOwner();
               Optional<FlightCollision.PathContact> contact = FlightCollision.sweepPath(
                  level,
                  missile,
                  segments,
                  candidate -> candidate.isAlive()
                     && candidate != owner
                     && (owner == null || !candidate.isPassengerOfSameVehicle(owner))
                     && !(owner instanceof Player player && candidate instanceof Player other && !player.canHarmPlayer(other))
                     && (candidate.isPickable() || AirDefenseTargets.target(candidate)),
                  target
               );
               if (contact.isPresent()) {
                  AirDefenseTargets.recordManpadLaunch(missile, target);
                  FlightCollision.SweepResult hit = contact.get().contact();
                  missile.setPos(hit.hit().getLocation());
                  if (hit.hit() instanceof EntityHitResult entityHit) {
                     if (AirDefenseTargets.target(entityHit.getEntity())) {
                        if (entityHit.getEntity() instanceof GuidedMissileEntity guided) {
                           guided.setPos(
                              guided.position().add(guided.getDeltaMovement().scale((contact.get().segment() + hit.fraction()) / segments.size()))
                           );
                        }

                        if (Boolean.getBoolean("deepstrike.airDefenseReview")) {
                           LoggerFactory.getLogger(ManpadFlight.class)
                              .info(
                                 "AIR_DEFENSE_NATIVE_PHYSICAL_HIT type={} target={} point={} segment={} fraction={}",
                                 new Object[]{
                                    missile.getClass().getSimpleName(),
                                    entityHit.getEntity().getUUID(),
                                    missile.position(),
                                    contact.get().segment(),
                                    hit.fraction()
                                 }
                              );
                        }

                        PatriotDefenseNetwork.nativeFlight(missile, false);
                        AirDefenseTargets.hit(entityHit.getEntity(), missile, 2);
                        missile.discard();
                     } else {
                        missile.onHitEntity(new EntityHitResult(entityHit.getEntity(), hit.surface()));
                     }
                  } else if (hit.hit() instanceof BlockHitResult blockHit) {
                     missile.onHit(new BlockHitResult(hit.surface(), blockHit.getDirection(), blockHit.getBlockPos(), blockHit.isInside()));
                  }

                  if (missile.getExploded()) {
                     missile.discard();
                  }

                  if (!missile.isAlive()) {
                     PREPARED.remove(missile);
                     return;
                  }

                  missile.setPos(start);
               }
            }

            missile.setDeltaMovement(state.position().subtract(start));
            orient(missile, missile.getDeltaMovement());
            PREPARED.put(missile, new ManpadFlight.Prepared(state.velocity(), age, missile.getSyncedTick()));
         }
      }
   }

   public static void finish(MissileProjectile missile) {
      ManpadFlight.Prepared step = PREPARED.get(missile);
      if (step != null && missile.isAlive() && missile.getSyncedTick() != step.syncedTick) {
         PREPARED.remove(missile);
         CompoundTag tag = missile.getPersistentData();
         tag.putInt("DSMManpadAge", step.age + 1);
         tag.putDouble("DSMManpadVx", step.velocity.x);
         tag.putDouble("DSMManpadVy", step.velocity.y);
         tag.putDouble("DSMManpadVz", step.velocity.z);
         missile.setDeltaMovement(step.velocity.scale(0.05));
         orient(missile, missile.getDeltaMovement());
      }
   }

   private static void orient(MissileProjectile missile, Vec3 movement) {
      FlightOrientation.Angles angles = FlightOrientation.along(movement, missile.getYRot(), missile.getXRot());
      missile.setYRot(angles.yaw());
      missile.setXRot(angles.pitch());
   }

   private ManpadFlight() {
   }

   private record Prepared(Vec3 velocity, int age, int syncedTick) {
   }
}
