package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.projectile.BasicGeoProjectileEntity;
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.init.ModEntities;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.AcousticWave;
import dev.sbw.deepstrike.game.Atmosphere;
import dev.sbw.deepstrike.game.FlightSnapshotHistory;
import dev.sbw.deepstrike.game.FlightTimeline;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.InterceptorGeometry;
import dev.sbw.deepstrike.model.ManpadGeometry;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PatriotRemoteEffects {
   private static final ResourceLocation PUFF_TEXTURE = SmokeAppearance.ATLAS;
   private static final Map<Integer, PatriotRemoteEffects.Flight> FLIGHTS = new HashMap<>();
   private static final FlightSnapshotHistory PACKET_HISTORY = new FlightSnapshotHistory(1200L);
   private static final List<PatriotRemoteEffects.Burst> BURSTS = new ArrayList<>();
   private static ClientLevel level;
   private static long effectTicks;
   private static boolean renderingNativeContact;
   private static final List<PatriotRemoteEffects.LaunchAcoustic> LAUNCH_ACOUSTICS = new ArrayList<>();
   private static final Set<Integer> reviewBodies = new HashSet<>();

   static boolean renderingNativeContact() {
      return renderingNativeContact;
   }

   public static boolean hidesNativeBody(MissileProjectile missile) {
      return !renderingNativeContact
         && (PACKET_HISTORY.finished(missile.getUUID()) || FLIGHTS.values().stream().anyMatch(f -> missile.getUUID().equals(f.uuid)));
   }

   private static float flightAge(PatriotRemoteEffects.Flight flight, float partial) {
      return flight.startedTick < 0L ? 15.0F : Math.max(0.0F, (float)(effectTicks - flight.startedTick) + partial);
   }

   private static boolean syncLevel() {
      ClientLevel current = Minecraft.getInstance().level;
      if (level != current) {
         FLIGHTS.clear();
         PACKET_HISTORY.clear();
         BURSTS.clear();
         LAUNCH_ACOUSTICS.clear();
         reviewBodies.clear();
         level = current;
         effectTicks = 0L;
      }

      return level != null;
   }

   public static void collectActiveSamFlights(List<PatriotRemoteEffects.SamAcousticSample> out) {
      if (syncLevel()) {
         for (Entry<Integer, PatriotRemoteEffects.Flight> entry : FLIGHTS.entrySet()) {
            PatriotRemoteEffects.Flight f = entry.getValue();
            if (f.position != null && f.finishedAt < 0L && f.powered) {
               Vec3 pos = f.visualPosition != null ? f.visualPosition : f.position;
               Vec3 vel = f.visualVelocity != null && f.visualVelocity.lengthSqr() > 1.0E-4 ? f.visualVelocity.scale(0.05) : f.packetVelocity.scale(0.05);
               out.add(new PatriotRemoteEffects.SamAcousticSample(entry.getKey(), pos, vel, f.warhead));
            }
         }
      }
   }

   static void collectBurstSmokeLights(List<FlightLighting.Glow> out, float partialTick) {
      if (syncLevel()) {
         int idx = 0;

         for (PatriotRemoteEffects.Burst burst : BURSTS) {
            float age = (float)(effectTicks - burst.started) + partialTick;
            if (!(age < 0.0F) && !(age >= 34.0F)) {
               float p = age / 34.0F;

               float basePower = switch (burst.warhead) {
                  case 0 -> 1.65F;
                  case 1 -> 1.25F;
                  default -> 1.15F;
                  case 3 -> 1.48F;
               };
               float strength = (float)(Math.pow(1.0F - p, 1.45) * basePower);
               if (!(strength <= 0.02F)) {
                  float baseRad = switch (burst.warhead) {
                     case 0 -> 28.0F;
                     case 1 -> 20.0F;
                     default -> 17.0F;
                     case 3 -> 24.0F;
                  };
                  float lightRad = baseRad * (0.72F + 0.55F * (float)Math.sqrt(p));
                  float kelvin = 1550.0F + 4350.0F * (float)Math.exp(-p * 3.6F);
                  float[] rgb = new float[3];
                  FlightLighting.kelvinToRgb(kelvin, rgb);
                  Vec3 top = burst.position.add(0.0, 1.4 + p * 3.2, 0.0);
                  out.add(
                     new FlightLighting.Glow(
                        -4000 - idx++,
                        burst.position,
                        lightRad,
                        strength,
                        lightRad * 1.35F,
                        false,
                        new Vec3(0.0, -1.0, 0.0),
                        top,
                        rgb,
                        Mth.clamp(p, 0.0F, 1.0F)
                     )
                  );
               }
            }
         }
      }
   }

   static void engineLights(List<FlightLighting.Glow> lights, float night, float partial) {
      for (Entry<Integer, PatriotRemoteEffects.Flight> entry : FLIGHTS.entrySet()) {
         PatriotRemoteEffects.Flight flight = entry.getValue();
         if (flight.position != null && flight.finishedAt < 0L && flight.powered) {
            Vec3 direction = FlightOrientation.direction(flight.visualYaw, flight.visualPitch);
            Vec3 center = flight.visualPosition != null ? flight.visualPosition : flight.position;
            boolean nativeMissile = flight.warhead >= 4;
            if (!nativeMissile) {
               center = center.subtract(
                  direction.scale(ModelGeometry.get(InterceptorGeometry.model(flight.warhead)).tailX * InterceptorGeometry.axialScale(flight.warhead))
               );
            }

            float age = flightAge(flight, partial);
            float flash = ExhaustStyles.launchFlashFromAge(age, flight.warhead == 3 ? 50.0F : 65.0F);
            float burnStage = ExhaustStyles.samBurnStage(age);
            float jetLen = ExhaustStyles.samMotorLength(flight.warhead, age);
            float lightRad = ExhaustStyles.samLightRadius(flight.warhead) * (0.15F + 0.85F * ExhaustStyles.ignition(age));
            float haloRad = 6.0F * ExhaustStyles.samMotorRadius(flight.warhead, age);
            float motorScale = ExhaustStyles.samMotorScale(flight.warhead);
            float strength = (0.76F + 0.36F * motorScale) * (1.0F + 0.35F * flash) * (0.65F + 0.35F * night);
            Vec3 end = center.subtract(direction.scale(jetLen));
            if (level != null) {
               BlockHitResult hit = level.clip(new ClipContext(center, end, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player));
               if (hit.getType() != Type.MISS) {
                  end = hit.getLocation().add(direction.scale(0.02));
               }
            }

            float[] rgb = new float[3];
            FlightLighting.kelvinToRgb(Mth.lerp(burnStage, 3150.0F, 2250.0F), rgb);
            lights.add(new FlightLighting.Glow(entry.getKey().intValue(), center, lightRad, strength, haloRad, false, direction, end, rgb, burnStage));
         }
      }
   }

   public static boolean hidesTracked(PatriotInterceptorEntity entity) {
      return PACKET_HISTORY.finished(entity.getUUID()) || FLIGHTS.values().stream().anyMatch(flight -> entity.getUUID().equals(flight.uuid));
   }

   public static boolean hasFlight(int id) {
      return FLIGHTS.containsKey(id);
   }

   public static void renderTick(RenderTickEvent event) {
      if (event.phase == Phase.START && syncLevel()) {
         float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

         for (Entry<Integer, PatriotRemoteEffects.Flight> entry : FLIGHTS.entrySet()) {
            PatriotRemoteEffects.Flight flight = entry.getValue();
            if (flight.finishedAt < 0L) {
               smooth(flight);
            }
         }
      }
   }

   private static MissileProjectile nativeBody(int id, PatriotRemoteEffects.Flight flight) {
      if (flight.nativeBody == null) {
         flight.nativeBody = (MissileProjectile)(flight.warhead == 4
            ? new Fim92MissileEntity((EntityType)ModEntities.FIM_92_MISSILE.get(), level)
            : new IglaMissileEntity((EntityType)ModEntities.IGLA_MISSILE.get(), level));
         flight.nativeBody.setId(id);
         flight.nativeBody.setUUID(flight.uuid);
         int visibleAge = ((BasicGeoProjectileEntity)flight.nativeBody).getHiddenTicks() + 1;
         flight.nativeBody.getEntityData().set(FastThrowableProjectile.SYNCED_TICK, visibleAge);
         flight.nativeBody.tickCount = visibleAge;
      }

      return flight.nativeBody;
   }

   static Vec3 opticalPoint(UUID uuid, int id) {
      if (level == null) {
         return null;
      } else {
         PatriotRemoteEffects.Flight flight = uuid == null
            ? FLIGHTS.get(id)
            : FLIGHTS.values().stream().filter(f -> uuid.equals(f.uuid)).findFirst().orElse(null);
         if (flight != null && flight.visualPosition != null && flight.finishedAt < 0L) {
            Vec3 direction = FlightOrientation.direction(flight.visualYaw, flight.visualPitch);
            if (flight.warhead >= 4) {
               MissileProjectile missile = nativeBody(id, flight);
               Vec3 origin = flight.visualPosition.subtract(ManpadGeometry.nozzle(missile, Vec3.ZERO, direction));
               return ManpadGeometry.worldBounds(missile, origin, direction).getCenter();
            } else {
               AABB bounds = InterceptorGeometry.canonicalBounds(flight.warhead);
               Vec3 center = bounds.getCenter();
               Vector3f rotated = ModelGeometry.flight(flight.visualYaw, flight.visualPitch)
                  .transformPosition(new Vector3f((float)center.x, (float)center.y, (float)center.z));
               return flight.visualPosition.add(new Vec3(rotated));
            }
         } else {
            return null;
         }
      }
   }

   public static void accept(PatriotDefenseNetwork.InterceptorState state) {
      if (syncLevel()) {
         if (PACKET_HISTORY.accept(state.missileId(), null, state.serverTick(), state.alive())) {
            if (!state.alive()) {
               for (PatriotRemoteEffects.Flight flight : FLIGHTS.values()) {
                  if (state.missileId().equals(flight.uuid)) {
                     if (flight.nativeBody == null
                        && level.getEntity(state.id()) instanceof MissileProjectile missile
                        && missile.getUUID().equals(state.missileId())) {
                        flight.nativeBody = missile;
                     }

                     flight.finishedAt = effectTicks;
                     flight.position = new Vec3(state.x(), state.y(), state.z());
                     flight.visualPosition = flight.position;
                     flight.visualVelocity = Vec3.ZERO;
                     flight.visualYaw = state.yaw();
                     flight.visualPitch = state.pitch();
                  }
               }
            } else {
               for (Entry<Integer, PatriotRemoteEffects.Flight> entry : new ArrayList<>(FLIGHTS.entrySet())) {
                  if (entry.getKey() != state.id() && state.missileId().equals(entry.getValue().uuid)) {
                     FLIGHTS.remove(entry.getKey());
                     FLIGHTS.put(state.id(), entry.getValue());
                  }
               }

               PatriotRemoteEffects.Flight previous = FLIGHTS.get(state.id());
               if (previous != null && !state.missileId().equals(previous.uuid)) {
                  FLIGHTS.remove(state.id());
               }

               PatriotRemoteEffects.Flight flightx = FLIGHTS.computeIfAbsent(state.id(), ignored -> new PatriotRemoteEffects.Flight());
               boolean newlyLaunched = flightx.startedTick < 0L;
               if (newlyLaunched) {
                  flightx.startedTick = effectTicks;
               }

               flightx.uuid = state.missileId();
               if (state.serverTick() > flightx.packetServerTick) {
                  Vec3 next = new Vec3(state.x(), state.y(), state.z());
                  long received = System.nanoTime();
                  if (flightx.position != null && flightx.packetServerTick > Long.MIN_VALUE) {
                     long ticks = Math.max(1L, state.serverTick() - flightx.packetServerTick);
                     flightx.packetVelocity = next.subtract(flightx.position).scale(20.0 / ticks);
                  }

                  if (flightx.visualPosition == null) {
                     flightx.visualPosition = next;
                     flightx.visualYaw = state.yaw();
                     flightx.visualPitch = state.pitch();
                  }

                  flightx.lastPacketNanos = received;
                  flightx.packetServerTick = state.serverTick();
                  FlightDisplay.observe(state.serverTick());
                  flightx.track.add(state.serverTick(), next, flightx.packetVelocity.scale(0.05), state.yaw(), state.pitch());
                  flightx.previous = flightx.position == null ? next : flightx.position;
                  flightx.oldYaw = flightx.position == null ? state.yaw() : flightx.yaw;
                  flightx.oldPitch = flightx.position == null ? state.pitch() : flightx.pitch;
                  flightx.position = next;
                  flightx.yaw = state.yaw();
                  flightx.pitch = state.pitch();
                  flightx.warhead = state.warhead();
                  flightx.powered = state.powered();
                  flightx.updated = effectTicks;
                  if (newlyLaunched && state.powered()) {
                     Vec3 launchDir = FlightOrientation.direction(state.yaw(), state.pitch());
                     float motorScale = ExhaustStyles.samMotorScale(state.warhead());
                     ContrailRenderer.emitLaunchBackblast(next, launchDir, motorScale, state.id() * 31 + state.warhead());
                     Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                     if (camPos == null || next.distanceTo(camPos) < 1350.0) {
                        if (LAUNCH_ACOUSTICS.size() >= 64) {
                           LAUNCH_ACOUSTICS.remove(0);
                        }

                        double camY = camPos != null ? camPos.y : next.y;
                        double soundSpeed = Atmosphere.soundSpeed((next.y + camY) * 0.5);
                        LAUNCH_ACOUSTICS.add(new PatriotRemoteEffects.LaunchAcoustic(next, effectTicks, state.warhead(), soundSpeed));
                     }
                  }
               }
            }
         }
      }
   }

   public static void accept(PatriotDefenseNetwork.InterceptorBurst state) {
      if (syncLevel()) {
         Random random = new Random(state.seed());
         int defense = state.warhead();

         int sparkCount = switch (defense) {
            case 0 -> 96;
            case 1 -> 58;
            case 2 -> 44;
            case 3 -> 88;
            default -> 68;
         };
         List<PatriotRemoteEffects.Spark> sparks = new ArrayList<>(sparkCount);

         for (int i = 0; i < sparkCount; i++) {
            boolean streamer = i < (defense != 0 && defense != 3 ? 7 : 10);
            double angle = random.nextDouble() * Math.PI * 2.0;
            double speed = streamer ? 0.22 + random.nextDouble() * 0.38 : 0.1 + random.nextDouble() * 0.42;
            double vertical = (random.nextDouble() < 0.64 ? 1 : -1) * (0.06 + random.nextDouble() * 0.34);
            int life = streamer ? 24 + random.nextInt(32) : 18 + random.nextInt(72);
            float sz = streamer ? 0.11F + random.nextFloat() * 0.08F : 0.07F + random.nextFloat() * 0.075F;
            sparks.add(
               new PatriotRemoteEffects.Spark(
                  new Vec3(Math.cos(angle) * speed, vertical, Math.sin(angle) * speed), life, streamer || random.nextBoolean(), sz, streamer
               )
            );
         }
         int lobeCount = switch (defense) {
            case 0 -> 28;
            case 1 -> 22;
            case 2 -> 16;
            case 3 -> 32;
            default -> 24;
         };

         float radius = switch (defense) {
            case 0 -> 1.15F;
            case 1 -> 0.75F;
            case 2 -> 0.5F;
            case 3 -> 1.25F;
            default -> 0.9F;
         };
         List<PatriotRemoteEffects.PuffLobe> lobes = new ArrayList<>(lobeCount);

         for (int i = 0; i < lobeCount; i++) {
            double u = random.nextDouble() * 2.0 - 1.0;
            double phi = random.nextDouble() * Math.PI * 2.0;
            double r = radius * (0.45 + random.nextDouble() * 1.4);
            double sin = Math.sqrt(Math.max(0.0, 1.0 - u * u));
            float ox = (float)(r * sin * Math.cos(phi));
            float oy = (float)(r * u);
            float oz = (float)(r * sin * Math.sin(phi));
            float size = radius * (float)(1.85 + random.nextDouble() * 1.55);
            float roll = (float)(random.nextDouble() * 360.0);
            lobes.add(new PatriotRemoteEffects.PuffLobe(ox, oy, oz, size, roll));
         }

         if (BURSTS.size() >= 128) {
            BURSTS.remove(0);
         }

         Vec3 position = new Vec3(state.x(), state.y(), state.z());
         long started = effectTicks;
         double soundSpeed = Atmosphere.soundSpeed((position.y + Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y) * 0.5);

         // A burst a few blocks wide is a speck from 200+ blocks away; grow it with distance so the kill stays readable.
         double burstDistance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(position);
         float boost = (float)Mth.clamp(burstDistance / 35.0, 1.0, 10.0);

         for (int i = 0; i < lobes.size(); i++) {
            PatriotRemoteEffects.PuffLobe lobe = lobes.get(i);
            Vec3 offset = new Vec3(lobe.ox, lobe.oy, lobe.oz);
            ContrailRenderer.emitSmoke(position.add(offset.scale(boost)), offset.scale(2.35 * boost), lobe.size * 0.24F * boost * 0.7F, i);
         }

         int ringCount = defense != 0 && defense != 3 ? 10 : 14;

         for (int i = 0; i < ringCount; i++) {
            double a = (i + random.nextDouble() * 0.4) * ((Math.PI * 2) / ringCount);
            Vec3 ringDir = new Vec3(Math.cos(a), (random.nextDouble() - 0.5) * 0.28, Math.sin(a)).normalize();
            ContrailRenderer.emitBlastWave(
               position.add(ringDir.scale(radius * 0.8 * boost)), ringDir.scale(7.5 * radius * boost), radius * 0.34F * Math.min(boost, 6.0F), i * 19 + defense
            );
         }

         // Lingering marker puff: hangs where the missile died for ~20 s so the interception can be found afterwards.
         ContrailRenderer.emitSmoke(position, new Vec3(0.0, 0.02, 0.0), (0.45F + radius * 0.35F) * boost, 1000 + defense);
         float flashRadius = switch (defense) {
            case 0 -> 42.0F;
            case 1 -> 28.0F;
            default -> 24.0F;
            case 3 -> 36.0F;
         };
         FlightLighting.flash(position, flashRadius * Math.min(2.0F, 1.0F + boost * 0.12F), 30);
         BURSTS.add(new PatriotRemoteEffects.Burst(position, started, defense, List.copyOf(sparks), List.of(), soundSpeed));
      }
   }

   public static void tick(ClientTickEvent event) {
      if (event.phase == Phase.END && syncLevel()) {
         if (!Minecraft.getInstance().isPaused()) {
            long now = ++effectTicks;
            FLIGHTS.values().removeIf(flight -> now - flight.updated > 40L || flight.finishedAt >= 0L && now - flight.finishedAt >= 2L);
            BURSTS.removeIf(burstx -> now - burstx.started > (burstx.soundPlayed ? 240 : 600));
            LAUNCH_ACOUSTICS.removeIf(la -> la.soundPlayed || now - la.started > 450L);
            Vec3 listener = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

            for (PatriotRemoteEffects.LaunchAcoustic la : LAUNCH_ACOUSTICS) {
               long age = now - la.started;
               double distance = listener.distanceTo(la.position);
               if (!la.soundPlayed && AcousticWave.arrived((int)age, distance, la.soundSpeed)) {
                  la.soundPlayed = true;
                  if (distance < 1000.0) {
                     float fade = Math.max(0.0F, 1.0F - (float)(distance / 1000.0));
                     float volume = (float)(2.1 / (1.0 + Math.pow(distance / 190.0, 1.25))) * fade;
                     boolean occluded = level.clip(new ClipContext(listener, la.position, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player))
                           .getType()
                        != Type.MISS;
                     SoundEvent sound = BFSounds.samLaunch(distance, occluded);
                     Vec3 dir = distance > 0.001 ? la.position.subtract(listener).normalize() : new Vec3(0.0, 1.0, 0.0);
                     Vec3 apparent = listener.add(dir.scale(Math.min(3.0, Math.max(1.0, distance))));
                     float pitch = la.warhead == 3 ? 1.04F : (la.warhead == 0 ? 0.94F : 1.0F);
                     level.playLocalSound(
                        apparent.x, apparent.y, apparent.z, sound, SoundSource.HOSTILE, occluded ? volume * 0.58F : volume, pitch, false
                     );
                  }
               }
            }

            for (PatriotRemoteEffects.Burst burst : BURSTS) {
               long age = now - burst.started;
               if (age >= 1L && age <= 26L && age % 2L == 0L) {
                  float fade = 1.0F - (float)age / 28.0F;

                  for (int i = 0; i < burst.sparks.size(); i++) {
                     PatriotRemoteEffects.Spark spark = burst.sparks.get(i);
                     if (spark.streamer() && age < spark.lifetime()) {
                        Vec3 point = burst.position.add(spark.velocity().scale(age)).add(0.0, -0.0045 * age * age, 0.0);
                        Vec3 instVel = spark.velocity().add(0.0, -0.009 * age, 0.0);
                        ContrailRenderer.emitSmoke(point, instVel.scale(3.0), 0.14F * fade, (int)(burst.started * 31L + i));
                     }
                  }
               }

               if (!burst.soundPlayed && AcousticWave.arrived((int)age, listener.distanceTo(burst.position), burst.soundSpeed)) {
                  burst.soundPlayed = true;
                  double distance = listener.distanceTo(burst.position);
                  if (distance < 3200.0) {
                     float volume = (float)(2.35 / (1.0 + Math.pow(distance / 195.0, 1.28)));
                     boolean occluded = level.clip(new ClipContext(listener, burst.position, Block.COLLIDER, Fluid.NONE, Minecraft.getInstance().player))
                           .getType()
                        != Type.MISS;
                     SoundEvent sound = BFSounds.interception(burst.warhead, distance, occluded);
                     Vec3 direction = burst.position.subtract(listener).normalize();
                     Vec3 apparent = listener.add(direction.scale(2.0));
                     level.playLocalSound(
                        apparent.x, apparent.y, apparent.z, sound, SoundSource.HOSTILE, occluded ? volume * 0.55F : volume, 1.0F, false
                     );
                     if (!occluded && distance < 360.0) {
                        SoundEvent layer = BFSounds.defenseInterceptLayer(burst.warhead);
                        level.playLocalSound(
                           apparent.x,
                           apparent.y,
                           apparent.z,
                           layer,
                           SoundSource.HOSTILE,
                           volume * 0.62F,
                           0.96F + (burst.warhead == 3 ? 0.06F : 0.0F),
                           false
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private static boolean trackedNativePose(int id, PatriotRemoteEffects.Flight flight, float partial) {
      if (flight.warhead >= 4 && level != null) {
         if (level.getEntity(id) instanceof MissileProjectile missile && missile.getUUID().equals(flight.uuid) && missile.isAlive()) {
            flight.nativeBody = missile;
            Vec3 movement = missile.getDeltaMovement();
            Vec3 position = new Vec3(
               Mth.lerp(partial, missile.xo, missile.getX()),
               Mth.lerp(partial, missile.yo, missile.getY()),
               Mth.lerp(partial, missile.zo, missile.getZ())
            );
            flight.visualPosition = ManpadGeometry.nozzle(missile, position, movement);
            FlightOrientation.Angles angles = FlightOrientation.along(movement, missile.getYRot(), missile.getXRot());
            flight.visualYaw = angles.yaw();
            flight.visualPitch = angles.pitch();
            flight.visualVelocity = movement.scale(20.0);
            flight.lastRenderNanos = System.nanoTime();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void renderNativeBody(int id, PatriotRemoteEffects.Flight flight, PoseStack pose, BufferSource buffers, Vec3 camera, float partial) {
      MissileProjectile missile = nativeBody(id, flight);
      Vec3 direction = FlightOrientation.direction(flight.visualYaw, flight.visualPitch);
      Vec3 center = flight.visualPosition.subtract(ManpadGeometry.nozzle(missile, Vec3.ZERO, direction));
      Vec3 oldPosition = missile.position();
      Vec3 oldMovement = missile.getDeltaMovement();
      double oldXo = missile.xo;
      double oldYo = missile.yo;
      double oldZo = missile.zo;
      float oldYaw = missile.getYRot();
      float oldPitch = missile.getXRot();
      float oldYawO = missile.yRotO;
      float oldPitchO = missile.xRotO;
      missile.setPos(center);
      missile.xo = center.x;
      missile.yo = center.y;
      missile.zo = center.z;
      missile.setDeltaMovement(direction);
      missile.setYRot(flight.visualYaw);
      missile.setXRot(flight.visualPitch);
      missile.yRotO = flight.visualYaw;
      missile.xRotO = flight.visualPitch;
      Vec3 relative = center.subtract(camera);
      boolean sensor = PatriotThermalCamera.isTracked(flight.uuid, id);
      boolean thermal = PatriotThermalCamera.isThermalTarget(flight.uuid, id);
      float visibility = sensor ? MissileVisibility.sensorOpacity(relative.length()) : MissileVisibility.opacity(relative.length());
      AABB bounds = ManpadGeometry.worldBounds(missile, Vec3.ZERO, direction);
      double extent = Math.sqrt(
            Math.pow(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)), 2.0)
               + Math.pow(Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY)), 2.0)
               + Math.pow(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)), 2.0)
         )
         + ExhaustStyles.samLength(flight.warhead) * 1.35;
      EntityRenderer<? super MissileProjectile> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(missile);
      ResourceLocation texture = renderer.getTextureLocation(missile);
      boolean previousContact = renderingNativeContact;
      renderingNativeContact = true;
      float age = flightAge(flight, partial);

      try {
         DistantFlightRenderer.renderMesh(
            pose,
            buffers,
            relative.x,
            relative.y,
            relative.z,
            relative.length(),
            extent,
            visibility,
            (modelPose, modelBuffers) -> {
               renderer.render(
                  missile,
                  flight.visualYaw,
                  0.0F,
                  modelPose,
                  renderType -> modelBuffers.getBuffer(DistantFlightRenderer.type(texture, thermal, renderType.mode())),
                  15728880
               );
               modelBuffers.endBatch();
               if (flight.finishedAt < 0L && flight.powered) {
                  Vec3 offset = flight.visualPosition.subtract(center);
                  modelPose.pushPose();

                  try {
                     modelPose.translate(offset.x, offset.y, offset.z);
                     modelPose.mulPose(ModelGeometry.flight(flight.visualYaw, flight.visualPitch));
                     renderJet(modelPose, modelBuffers, partial, 0.0F, flight.warhead, age);
                  } finally {
                     modelPose.popPose();
                  }
               }

               AirDefenseVisualReview.nativeContactFrame(flight.warhead, missile, center, direction);
               if (Boolean.getBoolean("deepstrike.airDefenseReview") && level.getEntity(id) == null && relative.length() > 256.0 && reviewBodies.add(id)) {
                  LogUtils.getLogger()
                     .info("NATIVE_REMOTE_BODY_RENDERED id={} type={} distance={} texture={}", new Object[]{id, flight.warhead, relative.length(), texture});
               }
            }
         );
      } finally {
         renderingNativeContact = previousContact;
         missile.setPos(oldPosition);
         missile.setDeltaMovement(oldMovement);
         missile.xo = oldXo;
         missile.yo = oldYo;
         missile.zo = oldZo;
         missile.setYRot(oldYaw);
         missile.setXRot(oldPitch);
         missile.yRotO = oldYawO;
         missile.xRotO = oldPitchO;
      }
   }

   private static void renderJet(PoseStack pose, BufferSource buffers, float partial, float tail, int warhead, float age) {
      ExhaustPlume.renderSam(pose, buffers, (float)effectTicks + partial, tail, warhead, age);
      buffers.endBatch();
   }

   private static void renderPacBody(int id, PatriotRemoteEffects.Flight flight, PoseStack pose, BufferSource buffers, Vec3 camera, float partial) {
      String model = InterceptorGeometry.model(flight.warhead);
      Vec3 relative = flight.visualPosition.subtract(camera);
      double distance = relative.length();
      AABB bounds = InterceptorGeometry.canonicalBounds(flight.warhead);
      double extent = Math.sqrt(
            Math.pow(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)), 2.0)
               + Math.pow(Math.max(Math.abs(bounds.minY), Math.abs(bounds.maxY)), 2.0)
               + Math.pow(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)), 2.0)
         )
         + ExhaustStyles.samLength(flight.warhead) * 1.35;
      boolean thermal = PatriotThermalCamera.isThermalTarget(flight.uuid, id);
      float visibility = PatriotThermalCamera.isTracked(flight.uuid, id) ? MissileVisibility.sensorOpacity(distance) : MissileVisibility.opacity(distance);
      float age = flightAge(flight, partial);
      DistantFlightRenderer.renderMesh(
         pose, buffers, relative.x, relative.y, relative.z, distance, extent, visibility, (modelPose, modelBuffers) -> {
            modelPose.mulPose(ModelGeometry.flight(flight.visualYaw, flight.visualPitch));
            float axial = InterceptorGeometry.axialScale(flight.warhead);
            float radial = InterceptorGeometry.radialScale(flight.warhead);
            modelPose.pushPose();
            modelPose.scale(axial, radial, radial);
            VertexConsumer vertices = modelBuffers.getBuffer(DistantFlightRenderer.type(model, thermal));
            ModelRenderer.render(model, model, modelPose, ignored -> vertices, 15728880, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, bone -> true);
            modelPose.popPose();
            modelBuffers.endBatch();
            if (flight.finishedAt < 0L && flight.powered) {
               renderJet(modelPose, modelBuffers, partial, ModelGeometry.get(model).tailX * axial, flight.warhead, age);
            }
         }
      );
   }

   private static void smooth(PatriotRemoteEffects.Flight flight) {
      double time = FlightDisplay.time();
      if (!Double.isNaN(time)) {
         FlightTimeline.Pose pose = flight.track.sample(time);
         if (pose != null) {
            flight.visualPosition = pose.position();
            flight.visualVelocity = pose.velocity().scale(20.0);
            flight.visualYaw = pose.yaw();
            flight.visualPitch = pose.pitch();
            if (Boolean.getBoolean("deepstrike.authorityReview")) {
               FlightAuthorityReview.inspect("defense-" + flight.warhead, flight.track, flight.visualPosition);
            }
         }
      }
   }

   public static void render(RenderLevelStageEvent event) {
      render(event, false);
   }

   public static void renderSensor(RenderLevelStageEvent event) {
      render(event, true);
   }

   private static void render(RenderLevelStageEvent event, boolean sensorPass) {
      if (event.getStage() == Stage.AFTER_LEVEL && syncLevel()) {
         event = LateWorldEffects.worldView(event);
         Minecraft mc = Minecraft.getInstance();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         PoseStack pose = event.getPoseStack();
         Vec3 camera = event.getCamera().getPosition();
         float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
         double far = Math.max(1.0, mc.gameRenderer.getDepthFar() * 0.7);
         buffers.endBatch();
         float fogStart = RenderSystem.getShaderFogStart();
         float fogEnd = RenderSystem.getShaderFogEnd();
         boolean previousLatePass = ModelRenderer.lateWorldPass;
         float[] previousColor = (float[])RenderSystem.getShaderColor().clone();
         ModelRenderer.lateWorldPass = true;
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.setShaderFogStart(Float.MAX_VALUE);
         RenderSystem.setShaderFogEnd(Float.MAX_VALUE);

         try {
            for (Entry<Integer, PatriotRemoteEffects.Flight> entry : FLIGHTS.entrySet()) {
               PatriotRemoteEffects.Flight flight = entry.getValue();
               if (PatriotThermalCamera.isTracked(flight.uuid, entry.getKey()) == sensorPass) {
                  double limit = sensorPass ? 8000.0 : 5000.0;
                  if (!(flight.visualPosition.distanceToSqr(camera) >= limit * limit)) {
                     boolean nativeMissile = flight.warhead >= 4;
                     String model = InterceptorGeometry.model(flight.warhead);
                     float authoredScale = nativeMissile ? 1.0F : InterceptorGeometry.axialScale(flight.warhead);
                     Vec3 direction = FlightOrientation.direction(flight.visualYaw, flight.visualPitch);
                     Vec3 nozzle = nativeMissile
                        ? flight.visualPosition
                        : flight.visualPosition.subtract(direction.scale(ModelGeometry.get(model).tailX * authoredScale));
                     float girth = nativeMissile ? 0.16F : (flight.warhead == 3 ? 0.22F : (flight.warhead == 0 ? 0.32F : 0.26F));
                     if (flight.powered && flight.finishedAt < 0L) {
                        ContrailRenderer.recordInterceptorFrame(entry.getKey(), nozzle, direction, flight.warhead, flightAge(flight, partial), girth, partial);
                     }

                     if (nativeMissile) {
                        renderNativeBody(entry.getKey(), flight, pose, buffers, camera, partial);
                     } else {
                        renderPacBody(entry.getKey(), flight, pose, buffers, camera, partial);
                     }
                  }
               }
            }

            for (PatriotRemoteEffects.Burst burst : BURSTS) {
               if (!sensorPass) {
                  float age = (float)(effectTicks - burst.started) + partial;
                  if (!(age < 0.0F) && !(age > 1200.0F) && age < 100.0F) {
                     for (PatriotRemoteEffects.Spark spark : burst.sparks) {
                        if (!(age >= spark.lifetime)) {
                           float life = age / spark.lifetime;
                           Vec3 point = burst.position.add(spark.velocity.scale(age)).add(0.0, -0.0045 * age * age, 0.0);
                           Vec3 instVel = spark.velocity.add(0.0, -0.009 * age, 0.0);
                           float fade = (1.0F - life) * (1.0F - life);
                           shrapnelStreak(event, buffers, point, instVel, far, spark.size, fade, spark.yellow, life, spark.streamer);
                        }
                     }
                  }
               }
            }

            buffers.endBatch();
         } finally {
            ModelRenderer.lateWorldPass = previousLatePass;
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
         }
      }
   }

   private static void billboard(
      PoseStack pose, VertexConsumer vertices, Vec3 point, Vec3 camera, double far, float size, float roll, float r, float g, float b, float a
   ) {
      billboard(pose, vertices, point, camera, far, size, roll, r, g, b, a, 15728880);
   }

   private static void billboard(
      PoseStack pose, VertexConsumer vertices, Vec3 point, Vec3 camera, double far, float size, float roll, float r, float g, float b, float a, int light
   ) {
      Vec3 rel = point.subtract(camera);
      double dist = rel.length();
      if (!(dist < 0.001)) {
         float scale = dist > far ? (float)(far / dist) : 1.0F;
         pose.pushPose();
         pose.translate(rel.x * scale, rel.y * scale, rel.z * scale);
         pose.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
         if (roll != 0.0F) {
            pose.mulPose(Axis.ZP.rotationDegrees(roll));
         }

         Matrix4f mat = pose.last().pose();
         int tile = (int)roll / 90 & 3;
         float u0 = tile * 0.25F;
         float u1 = u0 + 0.25F;
         float hs = Math.max(size * scale * 0.5F, 0.4F);
         vertices.addVertex(mat, -hs, -hs, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u0, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0F, 1.0F, 0.0F)
            ;
         vertices.addVertex(mat, -hs, hs, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u0, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0F, 1.0F, 0.0F)
            ;
         vertices.addVertex(mat, hs, hs, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u1, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0F, 1.0F, 0.0F)
            ;
         vertices.addVertex(mat, hs, -hs, 0.0F)
            .setColor(r, g, b, a)
            .setUv(u1, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0F, 1.0F, 0.0F)
            ;
         pose.popPose();
      }
   }

   private static void shrapnelStreak(
      RenderLevelStageEvent event,
      MultiBufferSource buffers,
      Vec3 point,
      Vec3 velocity,
      double far,
      float size,
      float alpha,
      boolean yellow,
      float life,
      boolean streamer
   ) {
      Vec3 cam = event.getCamera().getPosition();
      Vec3 relative = point.subtract(cam);
      double dist = relative.length();
      float scale = (float)Math.min(1.0, far / Math.max(1.0, dist));
      float s = size * scale + (float)Math.min(0.14, dist * 2.8E-4);
      float green = yellow ? Math.max(0.36F, 0.96F - life * 0.48F) : Math.max(0.14F, 0.44F - life * 0.21F);
      float blue = yellow ? Math.max(0.04F, 0.62F * (1.0F - Math.min(1.0F, life * 2.4F))) : 0.03F;
      VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
      PoseStack pose = event.getPoseStack();
      double speed = velocity.length();
      if (speed > 0.001 && life < 0.85F) {
         double streakLen = (streamer ? 4.8 : 3.2) * speed * (1.0 - life * 0.65);
         Vec3 tailRel = relative.subtract(velocity.normalize().scale(streakLen));
         Vec3 midRel = relative.lerp(tailRel, 0.5);
         Vec3 side = velocity.cross(midRel);
         if (side.lengthSqr() < 1.0E-6) {
            side = new Vec3(1.0, 0.0, 0.0);
         } else {
            side = side.normalize();
         }

         float wHead = s * (streamer ? 0.85F : 0.65F);
         float wTail = wHead * 0.15F;
         float hx = (float)(relative.x * scale);
         float hy = (float)(relative.y * scale);
         float hz = (float)(relative.z * scale);
         float tx = (float)(tailRel.x * scale);
         float ty = (float)(tailRel.y * scale);
         float tz = (float)(tailRel.z * scale);
         float sx = (float)side.x;
         float sy = (float)side.y;
         float sz = (float)side.z;
         Matrix4f worldMat = pose.last().pose();
         float tailAlpha = alpha * (streamer ? 0.88F : 0.72F);
         vertices.addVertex(worldMat, hx - sx * wHead, hy - sy * wHead, hz - sz * wHead).setColor(1.0F, green, blue, tailAlpha);
         vertices.addVertex(worldMat, hx + sx * wHead, hy + sy * wHead, hz + sz * wHead).setColor(1.0F, green, blue, tailAlpha);
         vertices.addVertex(worldMat, tx + sx * wTail, ty + sy * wTail, tz + sz * wTail).setColor(1.0F, green * 0.45F, 0.01F, 0.0F);
         vertices.addVertex(worldMat, tx - sx * wTail, ty - sy * wTail, tz - sz * wTail).setColor(1.0F, green * 0.45F, 0.01F, 0.0F);
      }

      pose.pushPose();
      pose.translate(relative.x * scale, relative.y * scale, relative.z * scale);
      pose.mulPose(event.getCamera().rotation());
      Matrix4f matrix = pose.last().pose();
      vertices.addVertex(matrix, -s, 0.0F, 0.0F).setColor(1.0F, green, blue, alpha);
      vertices.addVertex(matrix, 0.0F, -s * 0.65F, 0.0F).setColor(1.0F, green, blue, alpha);
      vertices.addVertex(matrix, s, 0.0F, 0.0F).setColor(1.0F, green * 0.78F, blue * 0.5F, alpha);
      vertices.addVertex(matrix, 0.0F, s * 0.65F, 0.0F).setColor(1.0F, green * 0.78F, blue * 0.5F, alpha);
      pose.popPose();
   }

   private PatriotRemoteEffects() {
   }

   private static final class Burst {
      final Vec3 position;
      final long started;
      final int warhead;
      final List<PatriotRemoteEffects.Spark> sparks;
      final List<PatriotRemoteEffects.PuffLobe> lobes;
      final double soundSpeed;
      boolean soundPlayed;

      Burst(Vec3 position, long started, int warhead, List<PatriotRemoteEffects.Spark> sparks, List<PatriotRemoteEffects.PuffLobe> lobes, double soundSpeed) {
         this.position = position;
         this.started = started;
         this.warhead = warhead;
         this.sparks = sparks;
         this.lobes = lobes;
         this.soundSpeed = soundSpeed;
      }
   }

   private static final class Flight {
      UUID uuid;
      final FlightTimeline track = new FlightTimeline();
      Vec3 previous;
      Vec3 position;
      Vec3 visualPosition;
      Vec3 visualVelocity = Vec3.ZERO;
      Vec3 packetVelocity = Vec3.ZERO;
      float oldYaw;
      float yaw;
      float oldPitch;
      float pitch;
      float visualYaw;
      float visualPitch;
      int warhead;
      boolean powered;
      long startedTick = -1L;
      long updated;
      long finishedAt = -1L;
      long lastPacketNanos;
      long lastRenderNanos;
      long packetServerTick = Long.MIN_VALUE;
      MissileProjectile nativeBody;
   }

   private static final class LaunchAcoustic {
      final Vec3 position;
      final long started;
      final int warhead;
      final double soundSpeed;
      boolean soundPlayed;

      LaunchAcoustic(Vec3 position, long started, int warhead, double soundSpeed) {
         this.position = position;
         this.started = started;
         this.warhead = warhead;
         this.soundSpeed = soundSpeed;
      }
   }

   private static final class PuffLobe {
      final float ox;
      final float oy;
      final float oz;
      final float size;
      final float roll;

      PuffLobe(float ox, float oy, float oz, float size, float roll) {
         this.ox = ox;
         this.oy = oy;
         this.oz = oz;
         this.size = size;
         this.roll = roll;
      }
   }

   public record SamAcousticSample(int id, Vec3 position, Vec3 velocity, int warhead) {
   }

   private record Spark(Vec3 velocity, int lifetime, boolean yellow, float size, boolean streamer) {
   }
}
