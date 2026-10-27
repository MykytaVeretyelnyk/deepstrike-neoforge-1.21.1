package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.DeepStrikeAddon;
import dev.sbw.deepstrike.game.AcousticWave;
import dev.sbw.deepstrike.game.Atmosphere;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFParticles;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;

public final class SonicBoomEffects {
   private static final ResourceLocation VIGNETTE_TEXTURE = ResourceLocation.parse("textures/misc/vignette.png");
   private static final List<SonicBoomEffects.PendingBoom> PENDING_BOOMS = new ArrayList<>();
   private static ClientLevel boomWorld;
   private static int deafnessTicks;
   private static int ringingTicks;
   private static int vignetteTicks;
   private static int deafnessDuration = 1;
   private static int ringingDuration = 1;
   private static int vignetteDuration = 1;
   private static float ringingPeak;
   private static float cameraTremor = 0.0F;
   private static SonicBoomEffects.EarRingingSoundInstance activeRingingSound = null;

   public static void accept(DeepStrikeNetwork.SonicBoomEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         if (boomWorld != mc.level) {
            clear();
            boomWorld = mc.level;
         }

         Vec3 boomPos = new Vec3(event.x(), event.y(), event.z());
         Vec3 playerEye = mc.gameRenderer.getMainCamera().getPosition();
         double dist = playerEye.distanceTo(boomPos);
         spawnVaporCone(mc.level, boomPos, event.doubleClap());
         if (!(dist > 1224.0)) {
            DeepStrikeAddon.LOGGER
               .debug(
                  "Sonic boom received: distance={} speed={} doubleClap={}", new Object[]{Math.round(dist), Math.round(event.speedKmh()), event.doubleClap()}
               );
            double soundSpeed = Atmosphere.soundSpeed((boomPos.y + playerEye.y) * 0.5);
            if (PENDING_BOOMS.size() >= 64) {
               PENDING_BOOMS.remove(0);
            }

            PENDING_BOOMS.add(new SonicBoomEffects.PendingBoom(boomPos, event.doubleClap(), soundSpeed));
         }
      }
   }

   private static void spawnVaporCone(ClientLevel level, Vec3 pos, boolean doubleClap) {
      if (level != null && level.isRaining()) {
         Minecraft mc = Minecraft.getInstance();
         GuidedMissileEntity missile = RemoteFlights.all()
            .stream()
            .filter(e -> e.position().distanceToSqr(pos) < 9216.0)
            .min(Comparator.comparingDouble(e -> e.position().distanceToSqr(pos)))
            .orElse(null);
         if (missile != null && !(missile.position().distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 262144.0)) {
            Vec3 axis = missile.getDeltaMovement().normalize();
            if (!(axis.lengthSqr() < 0.5)) {
               Vec3 side = axis.cross(Math.abs(axis.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0)).normalize();
               Vec3 up = side.cross(axis).normalize();

               for (int i = 0; i < 12; i++) {
                  double angle = i * Math.PI / 6.0;
                  Vec3 radial = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                  Vec3 point = missile.position().subtract(axis.scale(0.5)).add(radial.scale(0.8));
                  Vec3 drift = radial.scale(0.035).subtract(axis.scale(0.02));
                  Particle particle = mc.particleEngine
                     .createParticle(
                        (ParticleOptions)BFParticles.VAPOR.get(),
                        point.x,
                        point.y,
                        point.z,
                        drift.x,
                        drift.y,
                        drift.z
                     );
                  if (particle != null) {
                     particle.setLifetime(8 + level.random.nextInt(5));
                  }
               }
            }
         }
      }
   }

   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null) {
            if (boomWorld != mc.level) {
               clear();
               boomWorld = mc.level;
            }

            if (!mc.isPaused()) {
               Vec3 playerEye = mc.gameRenderer.getMainCamera().getPosition();
               Iterator<SonicBoomEffects.PendingBoom> it = PENDING_BOOMS.iterator();

               while (it.hasNext()) {
                  SonicBoomEffects.PendingBoom boom = it.next();
                  if (AcousticWave.arrived(++boom.age, boom.pos.distanceTo(playerEye), boom.soundSpeed)) {
                     playArrivingSonicBoom(mc, playerEye, boom);
                     it.remove();
                  } else if (boom.age >= 400) {
                     it.remove();
                  }
               }

               if (vignetteTicks > 0) {
                  vignetteTicks--;
               }

               if (ringingTicks > 0) {
                  ringingTicks--;
                  if (ringingTicks == 0) {
                     ringingPeak = 0.0F;
                  }

                  if (ringingTicks == 0 && activeRingingSound != null) {
                     activeRingingSound.stopSound();
                     activeRingingSound = null;
                  }
               }

               if (deafnessTicks > 0) {
                  deafnessTicks--;
               }

               if (cameraTremor > 0.005F) {
                  cameraTremor *= 0.91F;
               } else {
                  cameraTremor = 0.0F;
               }
            }
         } else {
            clear();
         }
      }
   }

   private static void playArrivingSonicBoom(Minecraft mc, Vec3 playerEye, SonicBoomEffects.PendingBoom boom) {
      Vec3 delta = boom.pos.subtract(playerEye);
      double dist = delta.length();
      Vec3 dir = dist > 1.0E-4 ? delta.scale(1.0 / dist) : new Vec3(0.0, 1.0, 0.0);
      Vec3 virtualPos = playerEye.add(dir.scale(Math.min(4.0, Math.max(1.5, dist))));
      double normDist = Math.min(1.0, dist / 1224.0);
      float volume = (float)Math.min(3.8, Math.pow(1.0 - normDist, 0.65) * 3.2);
      float randPitch = (float)((Math.random() - 0.5) * 0.05);
      float pitch;
      if (dist < 60.0) {
         pitch = 1.02F + randPitch;
      } else if (dist < 250.0) {
         pitch = 0.94F + randPitch;
      } else if (dist < 700.0) {
         pitch = 0.8F + randPitch;
      } else {
         pitch = 0.66F + randPitch;
      }

      SoundEvent boomSound = boom.doubleClap ? (SoundEvent)BFSounds.SONIC_BOOM_DOUBLE.get() : (SoundEvent)BFSounds.SONIC_BOOM_SINGLE.get();
      mc.getSoundManager().play(new SonicBoomEffects.SonicBoomSoundInstance(boomSound, virtualPos, volume, pitch));
      if (dist < 80.0) {
         cameraTremor = Math.max(cameraTremor, (float)(0.25 * (1.0 - dist / 80.0)));
      }

      if (dist <= 50.0) {
         triggerDeafeningShock(mc, dist);
      }
   }

   private static void triggerDeafeningShock(Minecraft mc, double distance) {
      float proximity = (float)Math.max(0.0, 1.0 - distance / 50.0);
      if (!(proximity <= 0.0F)) {
         deafnessDuration = Math.max(deafnessTicks, 20 + (int)(60.0F * proximity));
         deafnessTicks = deafnessDuration;
         ringingDuration = Math.max(ringingTicks, 20 + (int)(100.0F * proximity));
         ringingTicks = ringingDuration;
         vignetteDuration = Math.max(vignetteTicks, 8 + (int)(22.0F * proximity));
         vignetteTicks = vignetteDuration;
         ringingPeak = Math.max(ringingPeak, 0.18F * proximity);
         if (activeRingingSound == null || activeRingingSound.isStopped()) {
            activeRingingSound = new SonicBoomEffects.EarRingingSoundInstance();
            mc.getSoundManager().play(activeRingingSound);
         }
      }
   }

   public static void onPlaySound(PlaySoundEvent event) {
      if (deafnessTicks > 0) {
         SoundInstance sound = event.getSound();
         if (sound != null) {
            ResourceLocation loc = sound.getLocation();
            String path = loc.getPath();
            if (!path.contains("ear_ringing") && !path.contains("sonic_boom") && !path.startsWith("ui.") && sound.getSource() != SoundSource.MASTER) {
               float progress = Math.min(1.0F, (float)deafnessTicks / Math.max(1, deafnessDuration));
               event.setSound(new SonicBoomEffects.MuffledSoundInstance(sound, sound.getVolume() * (1.0F - 0.55F * progress * progress)));
            }
         }
      }
   }

   public static void onRenderGuiOverlay(Pre event) {
      if (vignetteTicks > 0 && event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)) {
         int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
         int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
         float progress = (float)vignetteTicks / Math.max(1, vignetteDuration);
         float smoothDecay = (float)Math.pow(progress, 1.25);
         float alpha = Math.min(0.18F, smoothDecay * 0.18F);
         int edge = Math.max(1, height / 7);
         int shade = (int)(alpha * 150.0F) << 24;
         GuiGraphics graphics = event.getGuiGraphics();
         graphics.fillGradient(0, 0, width, edge, shade, 0);
         graphics.fillGradient(0, height - edge, width, height, 0, shade);
      }
   }

   public static void onComputeCameraAngles(ComputeCameraAngles event) {
      if (!(cameraTremor <= 0.005F)) {
         float roll = (float)(Math.sin(System.currentTimeMillis() * 0.045) * cameraTremor * 2.8F);
         float pitch = (float)(Math.cos(System.currentTimeMillis() * 0.038) * cameraTremor * 1.6F);
         event.setRoll(event.getRoll() + roll);
         event.setPitch(event.getPitch() + pitch);
      }
   }

   private static void clear() {
      PENDING_BOOMS.clear();
      boomWorld = null;
      deafnessTicks = 0;
      ringingTicks = 0;
      vignetteTicks = 0;
      ringingPeak = 0.0F;
      cameraTremor = 0.0F;
      if (activeRingingSound != null) {
         activeRingingSound.stopSound();
         activeRingingSound = null;
      }
   }

   private SonicBoomEffects() {
   }

   private static final class EarRingingSoundInstance extends AbstractTickableSoundInstance {
      EarRingingSoundInstance() {
         super((SoundEvent)BFSounds.EAR_RINGING.get(), SoundSource.MASTER, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.relative = true;
         this.volume = 0.95F;
         this.pitch = 1.0F;
      }

      public void stopSound() {
         this.stop();
      }

      public Attenuation getAttenuation() {
         return Attenuation.NONE;
      }

      public void tick() {
         if (!this.isStopped()) {
            if (SonicBoomEffects.ringingTicks <= 0) {
               this.stopSound();
            } else {
               float t = (float)SonicBoomEffects.ringingTicks / Math.max(1, SonicBoomEffects.ringingDuration);
               this.volume = (float)Math.pow(t, 0.72) * SonicBoomEffects.ringingPeak;
            }
         }
      }
   }

   private static final class MuffledSoundInstance implements SoundInstance {
      private final SoundInstance delegate;
      private final float volume;

      MuffledSoundInstance(SoundInstance delegate, float volume) {
         this.delegate = delegate;
         this.volume = volume;
      }

      public ResourceLocation getLocation() {
         return this.delegate.getLocation();
      }

      public WeighedSoundEvents resolve(SoundManager m) {
         return this.delegate.resolve(m);
      }

      public Sound getSound() {
         return this.delegate.getSound();
      }

      public SoundSource getSource() {
         return this.delegate.getSource();
      }

      public boolean isLooping() {
         return this.delegate.isLooping();
      }

      public boolean isRelative() {
         return this.delegate.isRelative();
      }

      public int getDelay() {
         return this.delegate.getDelay();
      }

      public float getVolume() {
         return this.volume;
      }

      public float getPitch() {
         return this.delegate.getPitch() * 0.88F;
      }

      public double getX() {
         return this.delegate.getX();
      }

      public double getY() {
         return this.delegate.getY();
      }

      public double getZ() {
         return this.delegate.getZ();
      }

      public Attenuation getAttenuation() {
         return this.delegate.getAttenuation();
      }
   }

   private static final class PendingBoom {
      final Vec3 pos;
      final boolean doubleClap;
      final double soundSpeed;
      int age;

      PendingBoom(Vec3 pos, boolean doubleClap, double soundSpeed) {
         this.pos = pos;
         this.doubleClap = doubleClap;
         this.soundSpeed = soundSpeed;
      }
   }

   private static final class SonicBoomSoundInstance extends AbstractSoundInstance {
      SonicBoomSoundInstance(SoundEvent event, Vec3 virtualPos, float volume, float pitch) {
         super(event, SoundSource.MASTER, RandomSource.create());
         this.looping = false;
         this.delay = 0;
         this.volume = volume;
         this.pitch = pitch;
         this.x = virtualPos.x;
         this.y = virtualPos.y;
         this.z = virtualPos.z;
      }

      public Attenuation getAttenuation() {
         return Attenuation.NONE;
      }
   }
}
