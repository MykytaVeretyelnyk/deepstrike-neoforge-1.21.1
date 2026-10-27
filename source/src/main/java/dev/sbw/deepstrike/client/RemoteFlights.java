package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.game.FlightSnapshotHistory;
import dev.sbw.deepstrike.game.FlightTimeline;
import dev.sbw.deepstrike.game.Fp5BoosterEntity;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;

public final class RemoteFlights {
   private static final Map<Integer, GuidedMissileEntity> flights = new HashMap<>();
   private static final Map<Integer, UUID> identities = new HashMap<>();
   private static final Map<Integer, UUID> missions = new HashMap<>();
   private static final Map<Integer, FlightTimeline> samples = new HashMap<>();
   private static final Map<Integer, Long> lastSeen = new HashMap<>();
   private static final Map<Integer, RemoteFlights.VisualPose> visualPoses = new HashMap<>();
   private static final FlightSnapshotHistory packetHistory = new FlightSnapshotHistory(1200L);
   private static long clientTicks;
   private static ClientLevel level;
   private static final Map<Integer, Long> terminalUntil = new HashMap<>();
   private static boolean renderingFrame;
   private static boolean posesReadyForFrame;

   /** Within this camera distance the real tracked missile entity is drawn instead of the packet replica. */
   public static final double NATIVE_RENDER_DISTANCE = 256.0;

   public static boolean hidesTracked(GuidedMissileEntity entity) {
      return packetHistory.finished(entity.getUUID());
   }

   /** The real, server-tracked entity for a replica, when the client currently tracks it. */
   private static GuidedMissileEntity tracked(GuidedMissileEntity replica) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      } else {
         return mc.level.getEntity(replica.getId()) instanceof GuidedMissileEntity real
               && real != replica
               && real.isAlive()
               && real.getUUID().equals(replica.getUUID())
            ? real
            : null;
      }
   }

   private static boolean nativeNear(GuidedMissileEntity real) {
      Minecraft mc = Minecraft.getInstance();
      return real.position().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < NATIVE_RENDER_DISTANCE * NATIVE_RENDER_DISTANCE;
   }

   public static boolean ownsEffects(GuidedMissileEntity entity) {
      if (packetHistory.finished(entity.getUUID())) {
         return false;
      } else if (flights.get(entity.getId()) == entity) {
         return tracked(entity) == null;
      } else {
         return true;
      }
   }

   public static GuidedMissileEntity get(int id) {
      return flights.get(id);
   }

   public static GuidedMissileEntity get(UUID id) {
      if (id == null) {
         return null;
      } else {
         for (GuidedMissileEntity flight : flights.values()) {
            if (id.equals(flight.getUUID())) {
               return flight;
            }
         }

         return null;
      }
   }

   public static Collection<GuidedMissileEntity> all() {
      return flights.values();
   }

   private static void remove(int id) {
      GuidedMissileEntity old = flights.remove(id);
      if (old != null) {
         old.discard();
      }

      identities.remove(id);
      missions.remove(id);
      samples.remove(id);
      lastSeen.remove(id);
      visualPoses.remove(id);
      terminalUntil.remove(id);
   }

   private static <T> void moveKey(Map<Integer, T> map, int oldId, int newId) {
      T value = map.remove(oldId);
      if (value != null) {
         map.put(newId, value);
      }
   }

   private static void reindex(int oldId, int newId) {
      remove(newId);
      moveKey(flights, oldId, newId);
      moveKey(identities, oldId, newId);
      moveKey(missions, oldId, newId);
      moveKey(samples, oldId, newId);
      moveKey(lastSeen, oldId, newId);
      moveKey(visualPoses, oldId, newId);
      moveKey(terminalUntil, oldId, newId);
      GuidedMissileEntity replica = flights.get(newId);
      if (replica != null) {
         replica.setId(newId);
      }
   }

   public static void accept(DeepStrikeNetwork.FlightState s) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (level != mc.level) {
            DistantFlightRenderer.close();
            flights.values().forEach(Entity::discard);
            flights.clear();
            identities.clear();
            missions.clear();
            samples.clear();
            lastSeen.clear();
            visualPoses.clear();
            packetHistory.clear();
            level = mc.level;
            terminalUntil.clear();
         }

         if (packetHistory.accept(s.missileId(), s.missionId(), s.serverTick(), s.alive(), s.intercepted())) {
            if (!s.alive()) {
               for (Entry<Integer, UUID> entry : new ArrayList<>(identities.entrySet())) {
                  if (entry.getValue().equals(s.missileId()) || s.missionId() != null && s.missionId().equals(missions.get(entry.getKey()))) {
                     int id = entry.getKey();
                     GuidedMissileEntity entity = flights.get(id);
                     if (entity != null) {
                        Vec3 contact = new Vec3(s.x(), s.y(), s.z());
                        entity.setRemotePose(contact, s.yaw(), s.pitch());
                        entity.setDeltaMovement(Vec3.ZERO);
                        entity.xo = s.x();
                        entity.yo = s.y();
                        entity.zo = s.z();
                        entity.yRotO = s.yaw();
                        entity.xRotO = s.pitch();
                        entity.setEngineRunning(false);
                        entity.setExhaustVisible(false);
                        samples.remove(id);
                        visualPoses.remove(id);
                        terminalUntil.put(id, clientTicks + 2L);
                     }
                  }
               }
            } else {
               for (Entry<Integer, UUID> entryx : new ArrayList<>(identities.entrySet())) {
                  if (entryx.getKey() != s.id() && entryx.getValue().equals(s.missileId())) {
                     reindex(entryx.getKey(), s.id());
                  }
               }

               if (s.missionId() != null) {
                  for (Entry<Integer, UUID> entryxx : new ArrayList<>(missions.entrySet())) {
                     if (entryxx.getKey() != s.id() && entryxx.getValue().equals(s.missionId())) {
                        remove(entryxx.getKey());
                     }
                  }
               }

               if (identities.containsKey(s.id()) && !identities.get(s.id()).equals(s.missileId())) {
                  remove(s.id());
               }

               GuidedMissileEntity e = flights.get(s.id());
               boolean newlyLaunched = e == null;
               if (e == null) {
                  e = new GuidedMissileEntity(level, MissileKind.byId(s.kind()), s.graphite(), s.camo());
                  e.setId(s.id());
                  e.setUUID(s.missileId());
                  e.setMissionId(s.missionId());
                  e.moveTo(s.x(), s.y(), s.z(), s.yaw(), s.pitch());
                  e.xo = s.x();
                  e.yo = s.y();
                  e.zo = s.z();
                  flights.put(s.id(), e);
               } else {
                  e.setCamo(s.camo());
               }

               e.setFrequency(s.frequency());
               e.setEngineRunning(s.engineRunning());
               e.setExhaustVisible(s.exhaustVisible());
               e.tickCount = s.flightAge();
               if (newlyLaunched && s.flightAge() <= 16 && s.engineRunning()) {
                  Vec3 launchPos = new Vec3(s.x(), s.y(), s.z());
                  Vec3 launchDir = FlightOrientation.direction(s.yaw(), s.pitch());
                  MissileKind kind = e.kind();

                  float motorScale = switch (kind) {
                     case FP5, PEACEKEEPER -> 1.38F;
                     case STORM_SHADOW -> 1.02F;
                     case JAGM, SPIKE_NLOS -> 0.74F;
                  };
                  ContrailRenderer.emitLaunchBackblast(launchPos, launchDir, motorScale, s.id() * 37 + s.kind());
                  MissileFlightAcoustics.notifyMissileLaunch(launchPos, kind, s.id());
               }

               identities.put(s.id(), s.missileId());
               if (s.missionId() != null) {
                  missions.put(s.id(), s.missionId());
               }

               lastSeen.put(s.id(), clientTicks);
               if (s.owner() != null) {
                  e.setOwnerId(s.owner());
               }

               if (s.intercepted() && !e.isIntercepted()) {
                  Vec3 contact = new Vec3(s.x(), s.y(), s.z());
                  Vec3 debrisVelocity = new Vec3(s.vx(), s.vy(), s.vz());
                  samples.remove(s.id());
                  long now = System.nanoTime();
                  RemoteFlights.VisualPose impactPose = new RemoteFlights.VisualPose(contact, debrisVelocity, s.yaw(), s.pitch(), now);
                  impactPose.impactUntilNanos = now + 100000000L;
                  visualPoses.put(s.id(), impactPose);
                  e.setRemotePose(contact, s.yaw(), s.pitch());
                  e.setDeltaMovement(debrisVelocity);
                  e.xo = s.x();
                  e.yo = s.y();
                  e.zo = s.z();
                  e.yRotO = s.yaw();
                  e.xRotO = s.pitch();
                  e.setIntercepted(true);
               }

               FlightDisplay.observe(s.serverTick());
               samples.computeIfAbsent(s.id(), ignored -> new FlightTimeline())
                  .add(s.serverTick(), new Vec3(s.x(), s.y(), s.z()), new Vec3(s.vx(), s.vy(), s.vz()), s.yaw(), s.pitch());
            }
         }
      }
   }

   public static void tick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         clientTicks++;
         Minecraft mc = Minecraft.getInstance();
         if (level != mc.level) {
            DistantFlightRenderer.close();
            flights.values().forEach(Entity::discard);
            flights.clear();
            identities.clear();
            missions.clear();
            samples.clear();
            lastSeen.clear();
            visualPoses.clear();
            packetHistory.clear();
            level = mc.level;
            terminalUntil.clear();
         }

         updatePoses();

         for (int id : new ArrayList<>(flights.keySet())) {
            if (terminalUntil.containsKey(id)) {
               if (clientTicks >= terminalUntil.get(id)) {
                  remove(id);
               }
            } else if (clientTicks - lastSeen.getOrDefault(id, 0L) > (flights.get(id).kind() == MissileKind.PEACEKEEPER ? 40 : 200)) {
               remove(id);
            } else {
               GuidedMissileEntity e = flights.get(id);
               e.xo = e.getX();
               e.yo = e.getY();
               e.zo = e.getZ();
               e.yRotO = e.getYRot();
               e.xRotO = e.getXRot();
               e.tick();
            }
         }

         if (mc.player != null && mc.player.tickCount % 40 == 0) {
            purgeLingeringWaypoints();
         }
      }
   }

   public static void updatePoses() {
      if (!renderingFrame || !posesReadyForFrame) {
         if (renderingFrame) {
            posesReadyForFrame = true;
         }

         long now = System.nanoTime();
         double time = FlightDisplay.time();
         if (!Double.isNaN(time)) {
            for (Entry<Integer, GuidedMissileEntity> entry : flights.entrySet()) {
               FlightTimeline history = samples.get(entry.getKey());
               if (history != null && !history.isEmpty()) {
                  RemoteFlights.VisualPose impact = visualPoses.get(entry.getKey());
                  if (impact != null && now < impact.impactUntilNanos) {
                     impact.updatedNanos = now;
                     GuidedMissileEntity entity = entry.getValue();
                     entity.setDeltaMovement(Vec3.ZERO);
                     entity.setRemotePose(impact.position, impact.yaw, impact.pitch);
                     entity.xo = impact.position.x;
                     entity.yo = impact.position.y;
                     entity.zo = impact.position.z;
                     entity.yRotO = impact.yaw;
                     entity.xRotO = impact.pitch;
                  } else {
                     FlightTimeline.Pose sampled = history.sample(time);
                     if (sampled != null) {
                        Vec3 pos = sampled.position();
                        Vec3 velocity = sampled.velocity();
                        float yaw = sampled.yaw();
                        float pitch = sampled.pitch();
                        RemoteFlights.VisualPose visual = visualPoses.get(entry.getKey());
                        if (visual == null) {
                           visual = new RemoteFlights.VisualPose(pos, velocity, yaw, pitch, now);
                           visualPoses.put(entry.getKey(), visual);
                        } else {
                           visual.position = pos;
                           visual.velocity = velocity;
                           visual.renderedVelocity = velocity;
                           visual.yaw = yaw;
                           visual.pitch = pitch;
                           visual.updatedNanos = now;
                        }

                        GuidedMissileEntity entity = entry.getValue();
                        entity.setDeltaMovement(velocity);
                        entity.setRemotePose(pos, yaw, pitch);
                        entity.xo = pos.x;
                        entity.yo = pos.y;
                        entity.zo = pos.z;
                        entity.yRotO = yaw;
                        entity.xRotO = pitch;
                        if (Boolean.getBoolean("deepstrike.authorityReview")) {
                           FlightAuthorityReview.inspect("guided-" + entity.kind(), history, pos);
                        }

                        if (Boolean.getBoolean("deepstrike.stage2Visual")) {
                           StageTwoVisualReview.inspectMotion(entity, velocity, time, history.newestTick());
                        }
                     }
                  }
               }
            }

            FlightLighting.updateEngines();
         }
      }
   }

   public static void renderTick(RenderTickEvent event) {
      if (event.phase == Phase.START) {
         renderingFrame = true;
         posesReadyForFrame = false;
         updatePoses();
         FlightMotionReview.sample(flights.values());
      } else {
         renderingFrame = false;
         posesReadyForFrame = false;
      }
   }

   private static void purgeLingeringWaypoints() {
      MapProviders.cleanupWaypoints();
   }

   public static void render(RenderLevelStageEvent event) {
      render(event, false);
   }

   public static void renderSensor(RenderLevelStageEvent event) {
      render(event, true);
   }

   private static void render(RenderLevelStageEvent event, boolean sensorPass) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         event = LateWorldEffects.worldView(event);
         updatePoses();
         Minecraft mc = Minecraft.getInstance();
         Vec3 camera = event.getCamera().getPosition();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         float t = event.getPartialTick().getGameTimeDeltaPartialTick(false);
         buffers.endBatch();
         float fogStart = RenderSystem.getShaderFogStart();
         float fogEnd = RenderSystem.getShaderFogEnd();
         float[] color = (float[])RenderSystem.getShaderColor().clone();
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.setShaderFogStart(1.0E7F);
         RenderSystem.setShaderFogEnd(2.0E7F);
         ModelRenderer.lateWorldPass = true;

         try {
            if (Boolean.getBoolean("deepstrike.stage2Visual")) {
               StageTwoVisualReview.drawOccluder(event, buffers, camera);
            }

            double drawRadius = mc.gameRenderer.getDepthFar() * 0.75;
            Set<UUID> drawnMissions = new HashSet<>();

            for (GuidedMissileEntity e : flights.values()
               .stream()
               .sorted(Comparator.<GuidedMissileEntity>comparingDouble(missile -> missile.position().distanceToSqr(camera)).reversed())
               .toList()) {
               UUID mission = missions.get(e.getId());
               GuidedMissileEntity real = sensorPass ? null : tracked(e);
               if ((mission == null || drawnMissions.add(mission))
                  && (real == null || !nativeNear(real))
                  && (sensorPass || !ClientCamera.isFollowed(e))
                  && PatriotThermalCamera.isTracked(e) == sensorPass) {
                  if (Boolean.getBoolean("deepstrike.stage2Visual")) {
                     StageTwoVisualReview.inspectModelFrame(e, sensorPass);
                  }

                  double x = Mth.lerp(t, e.xo, e.getX()) - camera.x;
                  double y = Mth.lerp(t, e.yo, e.getY()) - camera.y;
                  double z = Mth.lerp(t, e.zo, e.getZ()) - camera.z;
                  double distance = Math.sqrt(x * x + y * y + z * z);
                  if (!(distance >= (sensorPass ? 8000.0 : 5000.0)) && !DistantFlightRenderer.render(e, t, event.getPoseStack(), buffers, x, y, z, distance)) {
                     float scale = (float)Math.min(1.0, drawRadius / Math.max(1.0, distance));
                     PoseStack pose = event.getPoseStack();
                     pose.pushPose();

                     try {
                        pose.translate(x * scale, y * scale, z * scale);
                        pose.scale(scale, scale, scale);
                        if (sensorPass) {
                           mc.getEntityRenderDispatcher().getRenderer(e).render(e, e.getYRot(), t, pose, buffers, 15728880);
                        } else {
                           mc.getEntityRenderDispatcher().render(e, 0.0, 0.0, 0.0, e.getYRot(), t, pose, buffers, 15728880);
                        }
                     } finally {
                        pose.popPose();
                     }
                  }
               }
            }

            if (mc.level != null) {
               for (Entity entity : mc.level.entitiesForRendering()) {
                  if (entity instanceof Fp5BoosterEntity b && b.isAlive()) {
                     double bx = Mth.lerp(t, b.xo, b.getX()) - camera.x;
                     double by = Mth.lerp(t, b.yo, b.getY()) - camera.y;
                     double bz = Mth.lerp(t, b.zo, b.getZ()) - camera.z;
                     double bDist = Math.sqrt(bx * bx + by * by + bz * bz);
                     if (bDist > drawRadius && bDist < (sensorPass ? 8000.0 : 5000.0)) {
                        float bScale = (float)(drawRadius / bDist);
                        PoseStack pose = event.getPoseStack();
                        pose.pushPose();

                        try {
                           pose.translate(bx * bScale, by * bScale, bz * bScale);
                           pose.scale(bScale, bScale, bScale);
                           mc.getEntityRenderDispatcher().render(b, 0.0, 0.0, 0.0, b.getYRot(), t, pose, buffers, 15728880);
                        } finally {
                           pose.popPose();
                        }
                     }
                  }
               }
            }

            buffers.endBatch();
            if (sensorPass && Boolean.getBoolean("deepstrike.opticalVisibilityReview")) {
               OpticalVisibilityReview.worldFrame(mc);
            }
         } finally {
            ModelRenderer.lateWorldPass = false;
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
         }
      }
   }

   private RemoteFlights() {
   }

   private static final class VisualPose {
      Vec3 position;
      Vec3 velocity;
      Vec3 renderedVelocity;
      float yaw;
      float pitch;
      long updatedNanos;
      long impactUntilNanos;

      VisualPose(Vec3 position, Vec3 velocity, float yaw, float pitch, long updatedNanos) {
         this.position = position;
         this.velocity = velocity;
         this.renderedVelocity = velocity;
         this.yaw = yaw;
         this.pitch = pitch;
         this.updatedNanos = updatedNanos;
      }
   }
}
