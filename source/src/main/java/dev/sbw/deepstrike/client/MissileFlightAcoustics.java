package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.AcousticWave;
import dev.sbw.deepstrike.game.Atmosphere;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class MissileFlightAcoustics {
   private static final Map<Integer, MissileFlightAcoustics.MissileTracker> TRACKERS = new HashMap<>();
   private static final List<MissileFlightAcoustics.PendingLaunchWave> PENDING_LAUNCHES = new ArrayList<>();

   public static void notifyMissileLaunch(Vec3 position, MissileKind kind, int seed) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
         if (camPos == null || !(position.distanceTo(camPos) >= 1350.0)) {
            double camY = camPos != null ? camPos.y : position.y;
            double soundSpeed = Atmosphere.soundSpeed((position.y + camY) * 0.5);
            if (PENDING_LAUNCHES.size() >= 64) {
               PENDING_LAUNCHES.remove(0);
            }

            PENDING_LAUNCHES.add(new MissileFlightAcoustics.PendingLaunchWave(position, kind, seed, soundSpeed));
         }
      }
   }

   public static void clientTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         long currentTick = mc.level.getGameTime();
         Vec3 playerEyePos = mc.player.getEyePosition();
         Iterator<MissileFlightAcoustics.PendingLaunchWave> launchIt = PENDING_LAUNCHES.iterator();

         while (launchIt.hasNext()) {
            MissileFlightAcoustics.PendingLaunchWave lw = launchIt.next();
            lw.age++;
            double dist = playerEyePos.distanceTo(lw.position);
            if (dist >= 1000.0) {
               launchIt.remove();
            } else {
               if (!lw.mainPlayed && AcousticWave.arrived(lw.age, dist, lw.soundSpeed)) {
                  lw.mainPlayed = true;
                  lw.mainArrivalAge = lw.age;
                  if (dist < 1000.0) {
                     boolean occluded = mc.level.clip(new ClipContext(playerEyePos, lw.position, Block.COLLIDER, Fluid.NONE, mc.player)).getType()
                        != Type.MISS;
                     Vec3 dir = dist > 0.001 ? lw.position.subtract(playerEyePos).normalize() : new Vec3(0.0, 1.0, 0.0);
                     Vec3 apparent = playerEyePos.add(dir.scale(Math.min(4.0, Math.max(1.5, dist))));
                     float distFade = Math.max(0.0F, 1.0F - (float)(dist / 1000.0));
                     float vol = dist < 24.0 ? 0.55F : (float)(2.25 / (1.0 + Math.pow(dist / 210.0, 1.25))) * distFade;

                     float pitch = switch (lw.kind) {
                        case FP5, PEACEKEEPER -> 0.94F;
                        case STORM_SHADOW -> 1.0F;
                        case JAGM, SPIKE_NLOS -> 1.06F;
                     };
                     mc.level
                        .playLocalSound(
                           apparent.x,
                           apparent.y,
                           apparent.z,
                           BFSounds.missileLaunch(lw.kind, dist, occluded),
                           SoundSource.NEUTRAL,
                           occluded ? vol * 0.58F : vol,
                           pitch,
                           false
                        );
                  }
               }

               if (lw.mainPlayed && !lw.echoPlayed && dist >= 75.0 && dist < 1000.0 && lw.age - lw.mainArrivalAge >= 11) {
                  lw.echoPlayed = true;
                  Vec3 dir = dist > 0.001 ? lw.position.subtract(playerEyePos).normalize() : new Vec3(0.0, 1.0, 0.0);
                  Vec3 apparent = playerEyePos.add(dir.scale(Math.min(6.0, dist)));
                  float distFade = Math.max(0.0F, 1.0F - (float)(dist / 1000.0));
                  float echoVol = (float)(1.25 / (1.0 + Math.pow(dist / 260.0, 1.2))) * distFade;
                  mc.level
                     .playLocalSound(
                        apparent.x,
                        apparent.y,
                        apparent.z,
                        (SoundEvent)BFSounds.LAUNCH_ECHO.get(),
                        SoundSource.NEUTRAL,
                        echoVol,
                        0.88F,
                        false
                     );
               }

               if (lw.mainPlayed && (dist < 75.0 || lw.echoPlayed) || lw.age > 500) {
                  launchIt.remove();
               }
            }
         }

         Map<Integer, GuidedMissileEntity> liveMissiles = new HashMap<>();

         for (GuidedMissileEntity m : RemoteFlights.all()) {
            if (m.isAlive() && !m.isRemoved()) {
               liveMissiles.put(m.getId(), m);
            }
         }

         for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof GuidedMissileEntity mx && mx.isAlive() && !mx.isRemoved()) {
               liveMissiles.put(mx.getId(), mx);
            }
         }

         for (Entry<Integer, GuidedMissileEntity> entry : liveMissiles.entrySet()) {
            int id = entry.getKey();
            GuidedMissileEntity missile = entry.getValue();
            double dx = missile.getX() - playerEyePos.x;
            double dz = missile.getZ() - playerEyePos.z;
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            if (!(horizDist > 420.0) || TRACKERS.containsKey(id)) {
               MissileFlightAcoustics.MissileTracker tracker = TRACKERS.computeIfAbsent(
                  id, k -> new MissileFlightAcoustics.MissileTracker(id, missile.kind(), false)
               );
               tracker.updateLive(missile, currentTick);
            }
         }

         List<PatriotRemoteEffects.SamAcousticSample> samSamples = new ArrayList<>();
         PatriotRemoteEffects.collectActiveSamFlights(samSamples);

         for (PatriotRemoteEffects.SamAcousticSample sam : samSamples) {
            int key = -100000 - sam.id();
            double dx = sam.position().x - playerEyePos.x;
            double dz = sam.position().z - playerEyePos.z;
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            if (!(horizDist > 420.0) || TRACKERS.containsKey(key)) {
               MissileFlightAcoustics.MissileTracker tracker = TRACKERS.computeIfAbsent(
                  key, k -> new MissileFlightAcoustics.MissileTracker(key, MissileKind.JAGM, true)
               );
               tracker.updateLiveSam(sam.position(), sam.velocity(), currentTick);
            }
         }

         Iterator<Entry<Integer, MissileFlightAcoustics.MissileTracker>> it = TRACKERS.entrySet().iterator();

         while (it.hasNext()) {
            MissileFlightAcoustics.MissileTracker tracker = it.next().getValue();
            boolean stillActive = tracker.tick(mc, playerEyePos, currentTick);
            if (!stillActive) {
               tracker.stop();
               it.remove();
            }
         }
      } else {
         clear();
      }
   }

   private static void clear() {
      for (MissileFlightAcoustics.MissileTracker tracker : TRACKERS.values()) {
         tracker.stop();
      }

      TRACKERS.clear();
      PENDING_LAUNCHES.clear();
   }

   private MissileFlightAcoustics() {
   }

   private static final class AcousticSnapshot {
      final double x;
      final double y;
      final double z;
      final double vx;
      final double vy;
      final double vz;
      final long tick;

      AcousticSnapshot(double x, double y, double z, double vx, double vy, double vz, long tick) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.vx = vx;
         this.vy = vy;
         this.vz = vz;
         this.tick = tick;
      }
   }

   private static final class MissileThrustSound extends AbstractTickableSoundInstance {
      private final MissileFlightAcoustics.MissileTracker tracker;

      MissileThrustSound(MissileFlightAcoustics.MissileTracker tracker) {
         super(
            tracker.samInterceptor ? (SoundEvent)BFSounds.SAM_FLIGHT_LOOP.get() : (SoundEvent)BFSounds.FLIGHT_LOOP.get(),
            SoundSource.PLAYERS,
            RandomSource.create()
         );
         this.tracker = tracker;
         this.looping = true;
         this.delay = 0;
         this.volume = tracker.thrustVolume;
         this.pitch = tracker.thrustPitch;
         this.x = tracker.soundX;
         this.y = tracker.soundY;
         this.z = tracker.soundZ;
      }

      public void stopSound() {
         this.stop();
      }

      public Attenuation getAttenuation() {
         return Attenuation.NONE;
      }

      public void tick() {
         if (!this.isStopped()) {
            this.x = this.tracker.soundX;
            this.y = this.tracker.soundY;
            this.z = this.tracker.soundZ;
            this.volume = this.tracker.thrustVolume;
            this.pitch = this.tracker.thrustPitch;
         }
      }
   }

   private static final class MissileTracker {
      final int missileId;
      final MissileKind kind;
      final boolean samInterceptor;
      final Deque<MissileFlightAcoustics.AcousticSnapshot> history = new ArrayDeque<>(60);
      boolean alive = true;
      long lastSeenTick = -1L;
      MissileFlightAcoustics.MissileThrustSound thrustSound;
      MissileFlightAcoustics.MissileWhistleSound whistleSound;
      boolean hasAudibleWave = false;
      double soundX;
      double soundY;
      double soundZ;
      float thrustVolume = 0.0F;
      float thrustPitch = 1.0F;
      float whistleVolume = 0.0F;
      float whistlePitch = 1.0F;

      MissileTracker(int missileId, MissileKind kind, boolean samInterceptor) {
         this.missileId = missileId;
         this.kind = kind;
         this.samInterceptor = samInterceptor;
      }

      void updateLiveSam(Vec3 pos, Vec3 vel, long currentTick) {
         this.alive = true;
         this.lastSeenTick = currentTick;
         this.history
            .addLast(
               new MissileFlightAcoustics.AcousticSnapshot(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, currentTick)
            );

         while (this.history.size() > 50) {
            this.history.removeFirst();
         }
      }

      void updateLive(GuidedMissileEntity missile, long currentTick) {
         this.alive = true;
         this.lastSeenTick = currentTick;
         Vec3 pos = missile.position();
         Vec3 vel = missile.getDeltaMovement();
         this.history
            .addLast(
               new MissileFlightAcoustics.AcousticSnapshot(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, currentTick)
            );

         while (this.history.size() > 50) {
            this.history.removeFirst();
         }
      }

      boolean tick(Minecraft mc, Vec3 playerEyePos, long currentTick) {
         if (currentTick - this.lastSeenTick > 2L) {
            this.alive = false;
         }

         if (this.history.isEmpty()) {
            return false;
         } else {
            MissileFlightAcoustics.AcousticSnapshot targetSnap = null;
            double targetEffDist = 0.0;
            double minDiff = Double.MAX_VALUE;

            for (MissileFlightAcoustics.AcousticSnapshot snap : this.history) {
               long deltaTicks = currentTick - snap.tick;
               if (deltaTicks >= 0L) {
                  double soundTraveled = deltaTicks * 17.15;
                  double dx = snap.x - playerEyePos.x;
                  double dy = snap.y - playerEyePos.y;
                  double dz = snap.z - playerEyePos.z;
                  double horizDist = Math.sqrt(dx * dx + dz * dz);
                  double effDist = horizDist + Math.abs(dy) * 0.05;
                  double diff = Math.abs(soundTraveled - effDist);
                  if (diff < minDiff) {
                     minDiff = diff;
                     targetSnap = snap;
                     targetEffDist = effDist;
                  }
               }
            }

            MissileFlightAcoustics.AcousticSnapshot oldest = this.history.peekFirst();
            double oldestDx = oldest.x - playerEyePos.x;
            double oldestDz = oldest.z - playerEyePos.z;
            double oldestHoriz = Math.sqrt(oldestDx * oldestDx + oldestDz * oldestDz);
            double oldestTraveled = (currentTick - oldest.tick) * 17.15;
            if (oldestTraveled < oldestHoriz - 10.0) {
               this.hasAudibleWave = false;
               this.thrustVolume = 0.0F;
               this.whistleVolume = 0.0F;
               return true;
            } else {
               if (!this.alive) {
                  MissileFlightAcoustics.AcousticSnapshot newest = this.history.peekLast();
                  double newestDx = newest.x - playerEyePos.x;
                  double newestDz = newest.z - playerEyePos.z;
                  double newestHoriz = Math.sqrt(newestDx * newestDx + newestDz * newestDz);
                  double newestTraveled = (currentTick - newest.tick) * 17.15;
                  if (newestTraveled > newestHoriz + 24.0) {
                     return false;
                  }
               }

               if (targetSnap != null && !(targetEffDist > 380.0)) {
                  this.hasAudibleWave = true;
                  double dx = targetSnap.x - playerEyePos.x;
                  double dy = targetSnap.y - playerEyePos.y;
                  double dz = targetSnap.z - playerEyePos.z;
                  double dist3D = Math.sqrt(dx * dx + dy * dy + dz * dz);
                  Vec3 dir = dist3D > 1.0E-4 ? new Vec3(dx / dist3D, dy / dist3D, dz / dist3D) : new Vec3(0.0, 1.0, 0.0);
                  Vec3 virtualPos = playerEyePos.add(dir.scale(Math.min(8.0, Math.max(2.0, dist3D))));
                  this.soundX = virtualPos.x;
                  this.soundY = virtualPos.y;
                  this.soundZ = virtualPos.z;
                  Vec3 mVel = new Vec3(targetSnap.vx, targetSnap.vy, targetSnap.vz);
                  double speedMs = mVel.length() * 20.0;
                  double speedKmh = speedMs * 3.6;
                  Vec3 toPlayer = playerEyePos.subtract(targetSnap.x, targetSnap.y, targetSnap.z);
                  double toPlayerLen = toPlayer.length();
                  double approachSpeedMs = (toPlayerLen > 1.0E-4 ? toPlayer.dot(mVel) / toPlayerLen : 0.0) * 20.0;
                  double velSqr = mVel.lengthSqr();
                  double tCpaTicks = velSqr > 1.0E-6 ? toPlayer.dot(mVel) / velSqr : 0.0;
                  Vec3 cpaVec = toPlayer.subtract(mVel.scale(Math.max(0.0, tCpaTicks)));
                  double cpaDist = cpaVec.length();
                  boolean isDirectHit = cpaDist <= 14.0 && approachSpeedMs > 6.0 && toPlayer.dot(mVel) > 0.0;
                  double timeToImpact = isDirectHit ? toPlayerLen / Math.max(1.0, approachSpeedMs) : 999.0;
                  double normDist380 = Math.min(1.0, targetEffDist / 380.0);
                  float distFalloff = (float)Math.pow(1.0 - normDist380, 1.25);
                  float speedMult = (float)Math.min(1.35, 0.75 + speedKmh / 1400.0 * 0.45);
                  this.thrustVolume = Math.min(1.25F, distFalloff * speedMult * 1.05F);
                  float basePitch;
                  if (targetEffDist < 60.0) {
                     basePitch = 1.06F - (float)(targetEffDist / 60.0) * 0.16F;
                  } else if (targetEffDist < 180.0) {
                     basePitch = 0.9F - (float)((targetEffDist - 60.0) / 120.0) * 0.16F;
                  } else {
                     basePitch = 0.74F - (float)((targetEffDist - 180.0) / 200.0) * 0.12F;
                  }

                  float doppler = (float)Math.max(-0.25, Math.min(0.28, approachSpeedMs / 340.0));
                  this.thrustPitch = Math.max(0.48F, Math.min(1.55F, basePitch + doppler));
                  if (isDirectHit && timeToImpact <= 0.5) {
                     this.whistleVolume = 0.0F;
                     this.whistlePitch = 1.0F;
                  } else if (speedMs < 4.0 || targetEffDist > 350.0) {
                     this.whistleVolume = 0.0F;
                     this.whistlePitch = 1.0F;
                  } else if (isDirectHit) {
                     double normDist350 = Math.min(1.0, targetEffDist / 350.0);
                     this.whistleVolume = (float)Math.min(1.35, Math.pow(1.0 - normDist350, 0.95) * 1.35);
                     this.whistlePitch = (float)Math.min(1.55, 1.12 + approachSpeedMs / 280.0 * 0.35);
                  } else {
                     double normDist350 = Math.min(1.0, targetEffDist / 350.0);
                     float baseWhistle = (float)Math.pow(1.0 - normDist350, 1.15);
                     float cpaFactor = (float)Math.max(0.45, 1.0 - Math.min(1.0, cpaDist / 100.0) * 0.55);
                     this.whistleVolume = Math.min(1.2F, baseWhistle * cpaFactor * 1.15F);
                     float flybyDoppler = (float)Math.max(-0.35, Math.min(0.38, approachSpeedMs / 320.0));
                     this.whistlePitch = Math.max(0.6F, Math.min(1.5F, 1.02F + flybyDoppler - (float)(normDist350 * 0.22)));
                  }

                  if (this.thrustSound == null || this.thrustSound.isStopped()) {
                     this.thrustSound = new MissileFlightAcoustics.MissileThrustSound(this);
                     mc.getSoundManager().play(this.thrustSound);
                  }

                  if (this.whistleSound == null || this.whistleSound.isStopped()) {
                     this.whistleSound = new MissileFlightAcoustics.MissileWhistleSound(this);
                     mc.getSoundManager().play(this.whistleSound);
                  }

                  return true;
               } else {
                  this.hasAudibleWave = false;
                  this.thrustVolume = 0.0F;
                  this.whistleVolume = 0.0F;
                  return this.alive;
               }
            }
         }
      }

      void stop() {
         if (this.thrustSound != null) {
            this.thrustSound.stopSound();
         }

         if (this.whistleSound != null) {
            this.whistleSound.stopSound();
         }
      }
   }

   private static final class MissileWhistleSound extends AbstractTickableSoundInstance {
      private final MissileFlightAcoustics.MissileTracker tracker;

      MissileWhistleSound(MissileFlightAcoustics.MissileTracker tracker) {
         super((SoundEvent)BFSounds.WHISTLE_FLYBY.get(), SoundSource.PLAYERS, RandomSource.create());
         this.tracker = tracker;
         this.looping = true;
         this.delay = 0;
         this.volume = tracker.whistleVolume;
         this.pitch = tracker.whistlePitch;
         this.x = tracker.soundX;
         this.y = tracker.soundY;
         this.z = tracker.soundZ;
      }

      public void stopSound() {
         this.stop();
      }

      public Attenuation getAttenuation() {
         return Attenuation.NONE;
      }

      public void tick() {
         if (!this.isStopped()) {
            this.x = this.tracker.soundX;
            this.y = this.tracker.soundY;
            this.z = this.tracker.soundZ;
            this.volume = this.tracker.whistleVolume;
            this.pitch = this.tracker.whistlePitch;
         }
      }
   }

   private static final class PendingLaunchWave {
      final Vec3 position;
      final MissileKind kind;
      final int seed;
      final double soundSpeed;
      int age;
      boolean mainPlayed;
      int mainArrivalAge = -1;
      boolean echoPlayed;

      PendingLaunchWave(Vec3 position, MissileKind kind, int seed, double soundSpeed) {
         this.position = position;
         this.kind = kind;
         this.seed = seed;
         this.soundSpeed = soundSpeed;
      }
   }
}
