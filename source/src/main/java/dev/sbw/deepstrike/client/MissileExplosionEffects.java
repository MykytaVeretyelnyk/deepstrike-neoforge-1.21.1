package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.sbw.deepstrike.game.AcousticWave;
import dev.sbw.deepstrike.game.Atmosphere;
import dev.sbw.deepstrike.game.GroundContact;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFParticles;
import dev.sbw.deepstrike.registry.BFSounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Pre;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MissileExplosionEffects {
   private static final List<MissileExplosionEffects.Burst> bursts = new ArrayList<>();
   private static final List<MissileExplosionEffects.ActiveBlast> activeBlasts = new ArrayList<>();
   private static ClientLevel world;
   private static float screenFlash;
   private static float screenFlashPeak;
   private static int screenFlashAge;
   private static int screenFlashDuration;
   private static Vec3 screenFlashPosition = Vec3.ZERO;
   private static double screenFlashRadius;
   private static float cameraShake;
   private static int cameraShakeAge;
   private static int cameraShakeDuration;
   private static final ResourceLocation FLASH_TEXTURE = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/blast_glare.png");
   private static final ResourceLocation RING_TEXTURE = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/blast_ring.png");
   private static final ResourceLocation PUFF_TEXTURE = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/blast_puff.png");
   private static final float[] ELEV_DEG = new float[]{-90.0F, -35.0F, -12.0F, 0.0F, 8.0F, 20.0F, 38.0F, 60.0F, 90.0F};
   private static final float[][] SKY_COLORS = new float[][]{
      {0.8F, 0.25F, 0.04F},
      {0.9F, 0.33F, 0.06F},
      {1.0F, 0.46F, 0.12F},
      {1.0F, 0.6F, 0.23F},
      {1.0F, 0.66F, 0.31F},
      {0.93F, 0.48F, 0.17F},
      {0.72F, 0.29F, 0.08F},
      {0.45F, 0.17F, 0.04F},
      {0.25F, 0.09F, 0.02F}
   };
   private static final float[] SKY_ALPHAS = new float[]{0.0F, 0.0F, 0.22F, 0.8F, 1.0F, 0.9F, 0.55F, 0.12F, 0.0F};

   static void collectBlastSmokeLights(List<FlightLighting.Glow> out, float partialTick) {
      int idx = 0;

      for (MissileExplosionEffects.ActiveBlast blast : activeBlasts) {
         if (!blast.interception) {
            float cur = blast.age + partialTick;
            float maxGlowAge = blast.nuclear ? 145.0F : Math.min(blast.maxAge * 0.78F, 68.0F);
            if (!(cur < 0.0F) && !(cur >= maxGlowAge)) {
               float p = cur / maxGlowAge;
               float strength = (float)(Math.pow(1.0 - p, 1.38) * (blast.nuclear ? 2.5F : blast.peakIntensity * 1.95F));
               if (!(strength <= 0.02F)) {
                  float var10000;
                  if (blast.nuclear) {
                     var10000 = 120.0F;
                  } else if (blast.shaped) {
                     var10000 = 20.0F;
                  } else {
                     switch (blast.kind) {
                        case FP5:
                        case PEACEKEEPER:
                           var10000 = 54.0F;
                           break;
                        case STORM_SHADOW:
                           var10000 = 38.0F;
                           break;
                        case JAGM:
                        case SPIKE_NLOS:
                           var10000 = 25.0F;
                           break;
                        default:
                           throw new IncompatibleClassChangeError();
                     }
                  }

                  float baseRad = var10000;
                  float lightRad = baseRad * (0.68F + 0.68F * (float)Math.sqrt(p));
                  float kelvin = 1480.0F + 4650.0F * (float)Math.exp(-p * 3.6F);
                  float[] rgb = new float[3];
                  FlightLighting.kelvinToRgb(kelvin, rgb);
                  Vec3 base = blast.pos.add(0.0, 1.0, 0.0);
                  Vec3 top = blast.pos.add(0.0, 1.0 + baseRad * (0.28F + 0.92F * p), 0.0);
                  out.add(
                     new FlightLighting.Glow(
                        -3000 - idx++, base, lightRad, strength, lightRad * 1.4F, false, new Vec3(0.0, -1.0, 0.0), top, rgb, Mth.clamp(p, 0.0F, 1.0F)
                     )
                  );
               }
            }
         }
      }
   }

   public static void accept(DeepStrikeNetwork.BlastEffect event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (world != mc.level) {
            bursts.clear();
            activeBlasts.clear();
            world = mc.level;
         }

         Vec3 blastPos = new Vec3(event.x(), event.y(), event.z());
         Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
         double distance = camPos.distanceTo(blastPos);
         if (!(distance > 8192.0)) {
            if ("NATIVE".equals(event.warhead())) {
               if (distance < 160.0) {
                  if (bursts.size() >= 12) {
                     bursts.remove(0);
                  }

                  MissileExplosionEffects.Burst burst = new MissileExplosionEffects.Burst(event);
                  bursts.add(burst);
                  burst.tick();
               }
            } else {
               if (activeBlasts.size() >= 16) {
                  activeBlasts.remove(0);
               }

               MissileExplosionEffects.ActiveBlast blast = new MissileExplosionEffects.ActiveBlast(event, distance);
               activeBlasts.add(blast);
               BlockHitResult smokeGround = GroundContact.below(mc.level, blastPos);
               if (!blast.interception && smokeGround.getType() != Type.MISS) {
                  ContrailRenderer.smokeColumn(
                     smokeGround.getLocation().add(0.0, 0.035, 0.0),
                     blast.nuclear
                        ? 3.8F
                        : (
                           blast.shaped
                              ? 0.55F
                              : (
                                 blast.kind != MissileKind.FP5 && blast.kind != MissileKind.PEACEKEEPER
                                    ? (blast.kind == MissileKind.STORM_SHADOW ? 1.1F : 0.6F)
                                    : 1.8F
                              )
                        ),
                     (int)blast.seed,
                     true
                  );
               }

               if (!blast.interception) {
                  float radius = blast.nuclear ? 220.0F : Math.max(28.0F, MissileKind.byId(event.kind()).blastPower * 4.0F);
                  FlightLighting.flash(blastPos, radius, blast.nuclear ? 70 : 32);
               }

               if (distance < (blast.nuclear ? 240.0 : 160.0)) {
                  if (bursts.size() >= 12) {
                     bursts.remove(0);
                  }

                  MissileExplosionEffects.Burst burst = new MissileExplosionEffects.Burst(event);
                  bursts.add(burst);
                  burst.tick();
               }

               if (blast.soundDelayTicks <= 0) {
                  blast.soundPlayed = true;
                  blast.soundArrivalAge = 0;
                  playMainShockwave(mc, blast, camPos);
               }
            }
         }
      }
   }

   static MissileExplosionEffects.ActiveBlast reviewBlast(long seed) {
      return activeBlasts.stream().filter(b -> b.seed == seed).findFirst().orElse(null);
   }

   public static void tick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) {
            bursts.clear();
            activeBlasts.clear();
            screenFlashPeak = 0.0F;
            screenFlash = 0.0F;
            cameraShake = 0.0F;
         } else {
            if (mc.level != world) {
               bursts.clear();
               activeBlasts.clear();
               world = mc.level;
               screenFlashPeak = 0.0F;
               screenFlash = 0.0F;
               screenFlashAge = 0;
               screenFlashPosition = Vec3.ZERO;
               screenFlashRadius = 0.0;
               cameraShake = 0.0F;
            }

            if (!mc.isPaused()) {
               if (cameraShakeDuration > 0 && cameraShakeAge < cameraShakeDuration) {
                  cameraShakeAge++;
               } else {
                  cameraShake = 0.0F;
                  cameraShakeDuration = 0;
                  cameraShakeAge = 0;
               }

               if (screenFlashDuration > 0 && screenFlashAge < screenFlashDuration) {
                  float t = (float)(++screenFlashAge) / screenFlashDuration;
                  screenFlash = (float)(screenFlashPeak * Math.pow(1.0 - t, 1.45));
               } else {
                  screenFlashPeak = 0.0F;
                  screenFlash = 0.0F;
                  screenFlashAge = 0;
                  screenFlashDuration = 0;
               }

               Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
               Iterator<MissileExplosionEffects.ActiveBlast> it = activeBlasts.iterator();

               while (it.hasNext()) {
                  MissileExplosionEffects.ActiveBlast b = it.next();
                  b.age++;
                  if (!b.soundPlayed && AcousticWave.arrived(b.age, b.pos.distanceTo(camPos), b.soundSpeed)) {
                     b.soundPlayed = true;
                     b.soundArrivalAge = b.age;
                     playMainShockwave(mc, b, camPos);
                  }

                  if (b.soundPlayed && !b.echo1Played && b.echoDelayTicks1 > 0 && b.age - b.soundArrivalAge >= b.echoDelayTicks1 - b.soundDelayTicks) {
                     b.echo1Played = true;
                     playSecondaryEcho(mc, b, camPos, 1);
                  }

                  if (b.soundPlayed && !b.echo2Played && b.echoDelayTicks2 > 0 && b.age - b.soundArrivalAge >= b.echoDelayTicks2 - b.soundDelayTicks) {
                     b.echo2Played = true;
                     playSecondaryEcho(mc, b, camPos, 2);
                  }

                  boolean acousticsFinished = b.soundPlayed && (b.echoDelayTicks1 <= 0 || b.echo1Played) && (b.echoDelayTicks2 <= 0 || b.echo2Played);
                  if (b.age >= b.maxAge && (acousticsFinished || b.age >= 1200)) {
                     it.remove();
                  }
               }

               bursts.removeIf(bx -> {
                  bx.tick();
                  return bx.age > (bx.nuclear ? 95 : 34);
               });
            }
         }
      }
   }

   private static void playMainShockwave(Minecraft mc, MissileExplosionEffects.ActiveBlast b, Vec3 camPos) {
      if (mc.level != null) {
         if (!b.interception) {
            Vec3 delta = b.pos.subtract(camPos);
            double dist = delta.length();
            RandomSource rand = RandomSource.create(b.seed ^ b.age);
            Vec3 soundPos = dist < 2.0 ? camPos : camPos.add(delta.normalize().scale(Math.min(16.0, dist)));
            if (dist < 75.0) {
               float volume = (float)Math.min(16.0, Math.max(12.0, 16.0 - dist * 0.05));

               float pitch = switch (b.kind) {
                  case FP5, PEACEKEEPER -> 0.98F + rand.nextFloat() * 0.04F;
                  case STORM_SHADOW -> 1.02F + rand.nextFloat() * 0.05F;
                  case JAGM, SPIKE_NLOS -> 1.08F + rand.nextFloat() * 0.06F;
               };
               mc.level.playLocalSound(soundPos.x, soundPos.y, soundPos.z, BFSounds.forKind(b.kind), SoundSource.BLOCKS, volume, pitch, false);
               float factor = (float)Math.max(0.0, 1.0 - dist / 75.0);

               float yieldMult = switch (b.kind) {
                  case FP5, PEACEKEEPER -> 1.0F;
                  case STORM_SHADOW -> 0.72F;
                  case JAGM, SPIKE_NLOS -> 0.42F;
               };
               cameraShake = Math.max(cameraShake, factor * yieldMult);
               cameraShakeDuration = 24;
               cameraShakeAge = 0;
            } else if (dist < 320.0) {
               float volume = (float)Math.min(13.0, Math.max(5.5, 290.0 / Math.sqrt(dist)));

               float pitch = switch (b.kind) {
                  case FP5, PEACEKEEPER -> 0.85F + rand.nextFloat() * 0.04F;
                  case STORM_SHADOW -> 0.89F + rand.nextFloat() * 0.04F;
                  case JAGM, SPIKE_NLOS -> 0.94F + rand.nextFloat() * 0.05F;
               };
               mc.level.playLocalSound(soundPos.x, soundPos.y, soundPos.z, BFSounds.forKind(b.kind), SoundSource.BLOCKS, volume, pitch, false);
               float factor = (float)Math.max(0.0, 1.0 - dist / 320.0);
               cameraShake = Math.max(cameraShake, factor * (b.kind == MissileKind.FP5 ? 0.65F : 0.4F));
               cameraShakeDuration = 18;
               cameraShakeAge = 0;
            } else {
               float volume = (float)Math.min(8.0, Math.max(2.2, 520.0 / Math.sqrt(dist)));

               float pitch = switch (b.kind) {
                  case FP5, PEACEKEEPER -> 0.62F + rand.nextFloat() * 0.04F;
                  case STORM_SHADOW -> 0.7F + rand.nextFloat() * 0.04F;
                  case JAGM, SPIKE_NLOS -> 0.78F + rand.nextFloat() * 0.04F;
               };
               mc.level
                  .playLocalSound(soundPos.x, soundPos.y, soundPos.z, BFSounds.farForKind(b.kind), SoundSource.BLOCKS, volume, pitch, false);
               if (b.kind == MissileKind.FP5 && dist < 1400.0) {
                  float factor = (float)Math.max(0.0, 1.0 - dist / 1400.0);
                  cameraShake = Math.max(cameraShake, factor * 0.25F);
                  cameraShakeDuration = 15;
                  cameraShakeAge = 0;
               }
            }
         }
      }
   }

   private static void playSecondaryEcho(Minecraft mc, MissileExplosionEffects.ActiveBlast b, Vec3 camPos, int echoStage) {
      if (mc.level != null) {
         Vec3 delta = b.pos.subtract(camPos);
         double dist = delta.length();
         RandomSource rand = RandomSource.create((b.seed ^ b.age * 31L) + echoStage);
         Vec3 lateral = new Vec3(-delta.z, 0.0, delta.x).normalize().scale(echoStage == 1 ? 7.0 : -9.0);
         Vec3 echoPos = camPos.add(delta.normalize().scale(Math.min(14.0, dist))).add(lateral);
         float baseVol = (float)Math.min(6.0, Math.max(1.4, 240.0 / Math.sqrt(Math.max(20.0, dist))));
         float volume = echoStage == 1 ? baseVol * 0.55F : baseVol * 0.35F;

         float basePitch = switch (b.kind) {
            case FP5, PEACEKEEPER -> 0.58F;
            case STORM_SHADOW -> 0.66F;
            case JAGM, SPIKE_NLOS -> 0.74F;
         };
         float pitch = basePitch - echoStage * 0.05F + rand.nextFloat() * 0.03F;
         mc.level.playLocalSound(echoPos.x, echoPos.y, echoPos.z, BFSounds.farForKind(b.kind), SoundSource.BLOCKS, volume, pitch, false);
      }
   }

   public static void onRenderLevelStage(RenderLevelStageEvent event) {
   }

   public static void renderSkyGlow(Pre event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && !activeBlasts.isEmpty()) {
         Camera camera = mc.gameRenderer.getMainCamera();
         int width = mc.getWindow().getGuiScaledWidth();
         int height = mc.getWindow().getGuiScaledHeight();
         double focal = height * 0.5 / Math.tan(Math.toRadians(((Integer)mc.options.fov().get()).intValue()) * 0.5);
         event.getGuiGraphics().flush();
         RenderSystem.enableBlend();
         RenderSystem.defaultBlendFunc();
         RenderSystem.disableDepthTest();
         RenderSystem.depthMask(false);
         RenderSystem.disableCull();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

         try {
            for (MissileExplosionEffects.ActiveBlast blast : activeBlasts) {
               double distance = blast.pos.distanceTo(camera.getPosition());
               float fade = skyGlowFade(distance);
               float intensity = blast.getZarevoIntensity(event.getPartialTick().getGameTimeDeltaPartialTick(false)) * fade;
               if (!(intensity < 0.002F)) {
                  double var10000;
                  if (blast.nuclear) {
                     var10000 = 110.0;
                  } else if (blast.shaped) {
                     var10000 = 24.0;
                  } else {
                     switch (blast.kind) {
                        case FP5:
                        case PEACEKEEPER:
                           var10000 = 65.0;
                           break;
                        case STORM_SHADOW:
                           var10000 = 50.0;
                           break;
                        case JAGM:
                        case SPIKE_NLOS:
                           var10000 = 28.0;
                           break;
                        default:
                           throw new IncompatibleClassChangeError();
                     }
                  }

                  double worldRadius = var10000;
                  Vec3 centre = blast.pos.add(0.0, worldRadius * 0.35, 0.0);
                  BlockHitResult hit = mc.level.clip(new ClipContext(camera.getPosition(), centre, Block.COLLIDER, Fluid.NONE, mc.getCameraEntity()));
                  if (hit.getType() == Type.MISS) {
                     Vec3 delta = centre.subtract(camera.getPosition());
                     Vector3f view = new Vector3f((float)delta.x, (float)delta.y, (float)delta.z)
                        .rotate(camera.rotation().conjugate(new Quaternionf()));
                     if (!(view.z >= -0.01F)) {
                        double cx = width * 0.5 + view.x / -view.z * focal;
                        double cy = height * 0.5 - view.y / -view.z * focal;
                        double radius = Math.min(worldRadius, distance * 0.22) * focal / -view.z;
                        if (!(cx + radius < 0.0) && !(cx - radius > width) && !(cy + radius < 0.0) && !(cy - radius > height)) {
                           BufferBuilder bb = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                           Matrix4f matrix = event.getGuiGraphics().pose().last().pose();

                           for (int row = 0; row < SKY_COLORS.length - 1; row++) {
                              for (int col = 0; col < 32; col++) {
                                 skyGlowVertex(bb, matrix, cx, cy, radius, row, col, intensity);
                                 skyGlowVertex(bb, matrix, cx, cy, radius, row + 1, col, intensity);
                                 skyGlowVertex(bb, matrix, cx, cy, radius, row + 1, col + 1, intensity);
                                 skyGlowVertex(bb, matrix, cx, cy, radius, row, col + 1, intensity);
                              }
                           }

                           dev.sbw.deepstrike.compat.RenderCompat.draw(bb);
                        }
                     }
                  }
               }
            }
         } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
         }
      }
   }

   static float skyGlowFade(double distance) {
      if (Double.isFinite(distance) && !(distance >= 3500.0)) {
         double t = Mth.clamp((distance - 2200.0) / 1300.0, 0.0, 1.0);
         return (float)((1.0 - t * t * (3.0 - 2.0 * t)) / (1.0 + distance / 1800.0));
      } else {
         return 0.0F;
      }
   }

   private static void skyGlowVertex(BufferBuilder bb, Matrix4f matrix, double cx, double cy, double radius, int row, int col, float intensity) {
      double x = col / 16.0 - 1.0;
      float horizontal = (float)Math.pow(Math.max(0.0, 1.0 - x * x), 3.0);
      float[] rgb = SKY_COLORS[row];
      bb.addVertex(matrix, (float)(cx + x * radius), (float)(cy - (row / 4.0 - 1.0) * radius), 0.0F)
         .setColor(rgb[0], rgb[1], rgb[2], SKY_ALPHAS[row] * horizontal * intensity * 0.65F)
         ;
   }

   private static void renderHorizonPlumes(RenderLevelStageEvent event) {
      Vec3 camPos = event.getCamera().getPosition();
      PoseStack pose = event.getPoseStack();

      for (MissileExplosionEffects.ActiveBlast blast : activeBlasts) {
         if (!blast.interception) {
            double dx = blast.pos.x - camPos.x;
            double dy = blast.pos.y - camPos.y;
            double dz = blast.pos.z - camPos.z;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!(dist < 8.0)) {
               float alpha = blast.getHorizonPlumeAlpha(event.getPartialTick().getGameTimeDeltaPartialTick(false));
               if (!(alpha <= 0.005F)) {
                  float ageTicks = blast.age + event.getPartialTick().getGameTimeDeltaPartialTick(false);
                  float t = ageTicks / blast.maxAge;
                  float var10000;
                  if (blast.nuclear) {
                     var10000 = 105.0F;
                  } else if (blast.shaped) {
                     var10000 = 13.0F;
                  } else {
                     switch (blast.kind) {
                        case FP5:
                        case PEACEKEEPER:
                           var10000 = 58.0F;
                           break;
                        case STORM_SHADOW:
                           var10000 = 32.0F;
                           break;
                        case JAGM:
                        case SPIKE_NLOS:
                           var10000 = 18.0F;
                           break;
                        default:
                           throw new IncompatibleClassChangeError();
                     }
                  }

                  float scale = var10000;
                  pose.pushPose();
                  pose.translate(dx, dy, dz);
                  RenderSystem.enableBlend();
                  RenderSystem.defaultBlendFunc();
                  RenderSystem.enableDepthTest();
                  RenderSystem.depthMask(false);
                  RenderSystem.disableCull();
                  if (ageTicks < 30.0F) {
                     float ringProgress = ageTicks / 30.0F;
                     float ringRadius = scale * (0.35F + 1.75F * (float)Math.sqrt(ringProgress));
                     float ringAlpha = (float)Math.pow(1.0F - ringProgress, 1.35) * 0.82F * blast.peakIntensity;
                     RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                     RenderSystem.setShaderTexture(0, RING_TEXTURE);
                     BufferBuilder bb = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                     Matrix4f ringMatrix = pose.last().pose();
                     bb.addVertex(ringMatrix, -ringRadius, 0.25F, -ringRadius).setUv(0.0F, 0.0F).setColor(1.0F, 0.95F, 0.82F, ringAlpha);
                     bb.addVertex(ringMatrix, -ringRadius, 0.25F, ringRadius).setUv(0.0F, 1.0F).setColor(1.0F, 0.95F, 0.82F, ringAlpha);
                     bb.addVertex(ringMatrix, ringRadius, 0.25F, ringRadius).setUv(1.0F, 1.0F).setColor(1.0F, 0.95F, 0.82F, ringAlpha);
                     bb.addVertex(ringMatrix, ringRadius, 0.25F, -ringRadius).setUv(1.0F, 0.0F).setColor(1.0F, 0.95F, 0.82F, ringAlpha);
                     dev.sbw.deepstrike.compat.RenderCompat.draw(bb);
                  }

                  pose.mulPose(event.getCamera().rotation());
                  Matrix4f m = pose.last().pose();
                  float expandProgress = Math.min(1.0F, ageTicks / 40.0F);
                  float sizeMult = 0.45F + 0.65F * (float)Math.sqrt(expandProgress);
                  float ascent = scale * 0.35F * t;
                  float curW = scale * sizeMult;
                  float curH = scale * 0.92F * sizeMult;
                  if (t < 0.35F) {
                     float glareT = t / 0.35F;
                     float glareSize = curW * (0.85F + 0.75F * (float)Math.sin(glareT * Math.PI));
                     float glareAlpha = (float)Math.pow(1.0F - glareT, 1.2) * 0.95F * blast.peakIntensity;
                     RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                     RenderSystem.setShaderTexture(0, FLASH_TEXTURE);
                     BufferBuilder bb = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                     drawQuad(bb, m, 0.0F, curH * 0.35F, glareSize * 0.5F, glareSize * 0.5F, 1.0F, 0.98F, 0.9F, glareAlpha);
                     dev.sbw.deepstrike.compat.RenderCompat.draw(bb);
                  }

                  RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                  RenderSystem.setShaderTexture(0, t >= 0.35F ? SmokeAppearance.CLOUD : PUFF_TEXTURE);
                  BufferBuilder bb = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                  float outerR;
                  float outerG;
                  float outerB;
                  float coreR;
                  float coreG;
                  float coreB;
                  if (t < 0.14F) {
                     coreR = 1.0F;
                     coreG = 0.99F;
                     coreB = 0.93F;
                     outerR = 1.0F;
                     outerG = 0.92F;
                     outerB = 0.75F;
                  } else if (t < 0.35F) {
                     float ft = (t - 0.14F) / 0.21F;
                     coreR = 1.0F;
                     coreG = Mth.lerp(ft, 0.95F, 0.78F);
                     coreB = Mth.lerp(ft, 0.85F, 0.35F);
                     outerR = 1.0F;
                     outerG = Mth.lerp(ft, 0.8F, 0.52F);
                     outerB = Mth.lerp(ft, 0.55F, 0.12F);
                  } else if (t < 0.58F) {
                     float st = (t - 0.35F) / 0.23F;
                     coreR = coreG = coreB = Mth.lerp(st, 0.48F, 0.32F);
                     outerR = outerG = outerB = Mth.lerp(st, 0.38F, 0.29F);
                  } else {
                     outerR = 0.32F;
                     coreR = 0.32F;
                     outerG = 0.32F;
                     coreG = 0.32F;
                     outerB = 0.32F;
                     coreB = 0.32F;
                  }

                  float lobeAlpha = t >= 0.35F ? SmokeAppearance.alpha(t) * Math.max(0.0F, 1.0F - (t - 0.35F) / 0.35F) : alpha * blast.peakIntensity;
                  drawQuad(bb, m, 0.0F, curH * 0.25F + ascent * 0.4F, curW * 0.35F, curH * 0.28F, coreR, coreG, coreB, lobeAlpha);
                  drawQuad(bb, m, 0.0F, curH * 0.52F + ascent * 0.7F, curW * 0.42F, curH * 0.32F, coreR, coreG, coreB, lobeAlpha);
                  drawQuad(bb, m, 0.0F, curH * 0.85F + ascent, curW * 0.58F, curH * 0.42F, coreR, coreG, coreB, lobeAlpha);
                  drawQuad(bb, m, -curW * 0.32F, curH * 0.8F + ascent, curW * 0.45F, curH * 0.35F, outerR, outerG, outerB, lobeAlpha);
                  drawQuad(bb, m, curW * 0.32F, curH * 0.8F + ascent, curW * 0.45F, curH * 0.35F, outerR, outerG, outerB, lobeAlpha);
                  drawQuad(bb, m, -curW * 0.52F, curH * 0.4F + ascent * 0.5F, curW * 0.38F, curH * 0.3F, outerR, outerG, outerB, lobeAlpha);
                  drawQuad(bb, m, curW * 0.52F, curH * 0.4F + ascent * 0.5F, curW * 0.38F, curH * 0.3F, outerR, outerG, outerB, lobeAlpha);
                  drawQuad(bb, m, -curW * 0.68F, curH * 0.88F + ascent * 0.9F, curW * 0.3F, curH * 0.24F, outerR, outerG, outerB, lobeAlpha * 0.92F);
                  drawQuad(bb, m, curW * 0.65F, curH * 0.85F + ascent * 0.9F, curW * 0.3F, curH * 0.24F, outerR, outerG, outerB, lobeAlpha * 0.92F);
                  drawQuad(bb, m, -curW * 0.38F, curH * 0.16F, curW * 0.32F, curH * 0.2F, coreR, coreG, coreB, lobeAlpha);
                  drawQuad(bb, m, curW * 0.38F, curH * 0.16F, curW * 0.32F, curH * 0.2F, coreR, coreG, coreB, lobeAlpha);
                  dev.sbw.deepstrike.compat.RenderCompat.draw(bb);
                  pose.popPose();
                  RenderSystem.enableCull();
                  RenderSystem.depthMask(true);
                  RenderSystem.disableBlend();
               }
            }
         }
      }
   }

   private static void drawQuad(BufferBuilder bb, Matrix4f m, float cx, float cy, float halfW, float halfH, float r, float g, float b, float a) {
      bb.addVertex(m, cx - halfW, cy - halfH, 0.0F).setUv(0.0F, 1.0F).setColor(r, g, b, a);
      bb.addVertex(m, cx - halfW, cy + halfH, 0.0F).setUv(0.0F, 0.0F).setColor(r, g, b, a);
      bb.addVertex(m, cx + halfW, cy + halfH, 0.0F).setUv(1.0F, 0.0F).setColor(r, g, b, a);
      bb.addVertex(m, cx + halfW, cy - halfH, 0.0F).setUv(1.0F, 1.0F).setColor(r, g, b, a);
   }

   public static void onComputeFogColor(ComputeFogColor event) {
      float radiance = 0.0F;

      for (MissileExplosionEffects.ActiveBlast blast : activeBlasts) {
         if (!blast.interception) {
            radiance = Math.max(radiance, blast.getZarevoIntensity(0.0F));
         }
      }

      float amount = Math.min(0.28F, radiance * 0.28F);
      event.setRed(event.getRed() + (1.0F - event.getRed()) * amount);
      event.setGreen(event.getGreen() + (0.57F - event.getGreen()) * amount * 0.65F);
      event.setBlue(event.getBlue() + (0.24F - event.getBlue()) * amount * 0.4F);
   }

   public static void onComputeCameraAngles(ComputeCameraAngles event) {
      if (!(cameraShake <= 0.001F)) {
         float t = cameraShakeAge + (float)event.getPartialTick();
         float strength = cameraShake * (1.0F - (float)cameraShakeAge / cameraShakeDuration);
         float pitchOffset = (float)Math.sin(t * 1.85F) * strength * 2.4F;
         float rollOffset = (float)Math.cos(t * 1.35F) * strength * 1.8F;
         float yawOffset = (float)Math.sin(t * 2.15F) * strength * 1.2F;
         event.setPitch(event.getPitch() + pitchOffset);
         event.setRoll(event.getRoll() + rollOffset);
         event.setYaw(event.getYaw() + yawOffset);
      }
   }

   public static void renderFlash(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
      if (!(screenFlash <= 0.015F) && event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR)) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 delta = screenFlashPosition.subtract(camera.getPosition());
            double distance = delta.length();
            if (!(distance < 1.0) && !(screenFlashRadius <= 0.0)) {
               Vector3f view = new Vector3f((float)delta.x, (float)delta.y, (float)delta.z)
                  .rotate(camera.rotation().conjugate(new Quaternionf()));
               if (!(view.z >= -0.01F)) {
                  int w = mc.getWindow().getGuiScaledWidth();
                  int h = mc.getWindow().getGuiScaledHeight();
                  double focal = h * 0.5 / Math.tan(Math.toRadians(((Integer)mc.options.fov().get()).intValue()) * 0.5);
                  double cx = w * 0.5 + view.x / -view.z * focal;
                  double cy = h * 0.5 - view.y / -view.z * focal;
                  double radius = Math.min(Math.max(w, h) * 1.6, screenFlashRadius * focal / distance);
                  if (!(cx + radius < 0.0) && !(cx - radius > w) && !(cy + radius < 0.0) && !(cy - radius > h)) {
                     int size = (int)Math.ceil(radius * 2.0);
                     int x = (int)Math.round(cx - radius);
                     int y = (int)Math.round(cy - radius);
                     event.getGuiGraphics().flush();
                     RenderSystem.enableBlend();
                     RenderSystem.defaultBlendFunc();
                     float flashProg = screenFlashDuration > 0 ? (float)screenFlashAge / screenFlashDuration : 0.0F;
                     float kelvin = 1400.0F + 5100.0F * (float)Math.exp(-flashProg * 4.5F);
                     float[] flashRgb = new float[3];
                     FlightLighting.kelvinToRgb(kelvin, flashRgb);
                     float hdrAlpha = screenFlash * 1.15F;
                     float ldrAlpha = Math.min(0.92F, hdrAlpha / (1.0F + hdrAlpha * 0.25F));
                     RenderSystem.setShaderColor(flashRgb[0], flashRgb[1], flashRgb[2], ldrAlpha);
                     event.getGuiGraphics().blit(FLASH_TEXTURE, x, y, size, size, 0.0F, 0.0F, 256, 256, 256, 256);
                     event.getGuiGraphics().flush();
                     RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                     RenderSystem.disableBlend();
                  }
               }
            }
         }
      }
   }

   public static void registerClientCommands(RegisterClientCommandsEvent event) {
      CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("bfblast")
               .executes(ctx -> triggerTestBlast((CommandSourceStack)ctx.getSource(), MissileKind.FP5, 450.0)))
            .then(((RequiredArgumentBuilder)Commands.argument("kind", StringArgumentType.word()).suggests((ctx, b) -> {
               b.suggest("fp5");
               b.suggest("storm_shadow");
               b.suggest("jagm");
               return b.buildFuture();
            }).executes(ctx -> {
               String k = StringArgumentType.getString(ctx, "kind");
               MissileKind kind = parseKind(k);
               return triggerTestBlast((CommandSourceStack)ctx.getSource(), kind, 450.0);
            })).then(Commands.argument("distance", DoubleArgumentType.doubleArg(10.0, 4000.0)).executes(ctx -> {
               String k = StringArgumentType.getString(ctx, "kind");
               double dist = DoubleArgumentType.getDouble(ctx, "distance");
               MissileKind kind = parseKind(k);
               return triggerTestBlast((CommandSourceStack)ctx.getSource(), kind, dist);
            })))
      );
   }

   private static MissileKind parseKind(String s) {
      if ("storm_shadow".equalsIgnoreCase(s)) {
         return MissileKind.STORM_SHADOW;
      } else {
         return "jagm".equalsIgnoreCase(s) ? MissileKind.JAGM : MissileKind.FP5;
      }
   }

   private static int triggerTestBlast(CommandSourceStack source, MissileKind kind, double dist) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         Vec3 look = mc.player.getLookAngle();
         double hDist = Math.max(1.0E-4, Math.sqrt(look.x * look.x + look.z * look.z));
         double targetX = mc.player.getX() + look.x / hDist * dist;
         double targetZ = mc.player.getZ() + look.z / hDist * dist;
         double targetY = mc.player.getY();
         accept(new DeepStrikeNetwork.BlastEffect(kind.ordinal(), targetX, targetY, targetZ, mc.level.random.nextLong()));
         source.sendSuccess(
            () -> Component.literal(
               FlightUi.localized("ui.deepstrike.missile_explosion_effects.9d2cdb8e9c")
                  + kind.id.toUpperCase()
                  + FlightUi.localized("ui.deepstrike.missile_explosion_effects.fc53fdc6dd")
                  + (int)dist
                  + FlightUi.localized("ui.deepstrike.missile_explosion_effects.b185f4d1c5")
            ),
            false
         );
         return 1;
      } else {
         return 0;
      }
   }

   private MissileExplosionEffects() {
   }

   public static final class ActiveBlast {
      public final Vec3 pos;
      public final MissileKind kind;
      public final boolean nuclear;
      public final boolean shaped;
      public final boolean interception;
      public final long seed;
      public final int maxAge;
      public final double distance;
      public final float peakIntensity;
      public final int soundDelayTicks;
      public final double soundSpeed;
      public final int echoDelayTicks1;
      public final int echoDelayTicks2;
      public int age;
      public boolean soundPlayed;
      public int soundArrivalAge = -1;
      public boolean echo1Played;
      public boolean echo2Played;

      public ActiveBlast(DeepStrikeNetwork.BlastEffect e, double dist) {
         this.pos = new Vec3(e.x(), e.y(), e.z());
         this.kind = MissileKind.byId(e.kind());
         this.nuclear = "nuclear".equals(e.warhead());
         this.shaped = "shaped_charge".equals(e.warhead());
         this.interception = "AIRBURST".equals(e.warhead());
         this.seed = e.seed();
         this.distance = dist;
         short var10001;
         if (this.interception) {
            var10001 = 45;
         } else if (this.nuclear) {
            var10001 = 240;
         } else if (this.shaped) {
            var10001 = 35;
         } else {
            switch (this.kind) {
               case FP5:
               case PEACEKEEPER:
                  var10001 = 140;
                  break;
               case STORM_SHADOW:
                  var10001 = 95;
                  break;
               case JAGM:
               case SPIKE_NLOS:
                  var10001 = 55;
                  break;
               default:
                  throw new IncompatibleClassChangeError();
            }
         }

         this.maxAge = var10001;
         float var6;
         if (this.interception) {
            var6 = 0.18F;
         } else if (this.nuclear) {
            var6 = 1.0F;
         } else if (this.shaped) {
            var6 = 0.35F;
         } else {
            switch (this.kind) {
               case FP5:
               case PEACEKEEPER:
                  var6 = 1.0F;
                  break;
               case STORM_SHADOW:
                  var6 = 0.78F;
                  break;
               case JAGM:
               case SPIKE_NLOS:
                  var6 = 0.52F;
                  break;
               default:
                  throw new IncompatibleClassChangeError();
            }
         }

         this.peakIntensity = var6;
         double listenerY = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().y;
         this.soundSpeed = Atmosphere.soundSpeed((this.pos.y + listenerY) * 0.5);
         this.soundDelayTicks = AcousticWave.delayTicks(dist, this.soundSpeed);
         if (this.interception) {
            this.soundPlayed = true;
            this.soundArrivalAge = 0;
         }

         if (this.interception || dist < 30.0) {
            this.echoDelayTicks1 = -1;
            this.echoDelayTicks2 = -1;
         } else if (dist < 180.0) {
            this.echoDelayTicks1 = this.soundDelayTicks + 5;
            this.echoDelayTicks2 = -1;
         } else if (dist < 800.0) {
            this.echoDelayTicks1 = this.soundDelayTicks + 7;
            this.echoDelayTicks2 = this.soundDelayTicks + 15;
         } else {
            this.echoDelayTicks1 = this.soundDelayTicks + 10;
            this.echoDelayTicks2 = this.soundDelayTicks + 22;
         }
      }

      public float getZarevoIntensity(float partialTick) {
         float cur = this.age + partialTick;
         if (!this.interception && !(cur < 0.0F) && !(cur >= this.maxAge)) {
            float flashRise = 0.8F;
            float envelope;
            if (cur < flashRise) {
               envelope = cur / flashRise;
            } else {
               float decayT = (cur - flashRise) / (this.maxAge - flashRise);
               float smoothDecay = 0.5F * (1.0F + (float)Math.cos(Math.min(1.0F, decayT) * Math.PI));
               envelope = (float)Math.pow(smoothDecay, 1.35);
               envelope *= 1.0F + 0.025F * (float)Math.sin(cur * 0.4F);
            }

            float distAttenuation = (float)Math.max(0.18, 1.0 - Math.pow(Math.min(this.distance, 4096.0) / 4096.0, 1.7));
            float horizon = (float)Mth.clamp((8192.0 - this.distance) / 4096.0, 0.0, 1.0);
            horizon = horizon * horizon * (3.0F - 2.0F * horizon);
            return Math.min(1.0F, this.peakIntensity * envelope * distAttenuation * horizon);
         } else {
            return 0.0F;
         }
      }

      public float getHorizonPlumeAlpha(float partialTick) {
         float cur = this.age + partialTick;
         if (cur < 0.0F || cur >= this.maxAge) {
            return 0.0F;
         } else if (cur < 3.0F) {
            return cur / 3.0F;
         } else {
            float t = cur / this.maxAge;
            float decay = 0.5F * (1.0F + (float)Math.cos(t * Math.PI));
            return (float)Math.pow(decay, 1.25);
         }
      }
   }

   private static final class Burst {
      final DeepStrikeNetwork.BlastEffect event;
      final MissileKind kind;
      final boolean nuclear;
      final boolean shaped;
      final boolean interception;
      final Random random;
      final float scale;
      final int flashTicks;
      final List<MissileExplosionEffects.DebrisStreamer> streamers;
      int age;

      Burst(DeepStrikeNetwork.BlastEffect e) {
         this.event = e;
         this.kind = MissileKind.byId(e.kind());
         this.interception = "AIRBURST".equals(e.warhead());
         this.nuclear = "nuclear".equals(e.warhead());
         this.shaped = "shaped_charge".equals(e.warhead());
         this.random = new Random(e.seed());
         float var10001;
         if (this.interception) {
            switch (this.kind) {
               case FP5:
                  var10001 = 3.0F;
                  break;
               case PEACEKEEPER:
                  var10001 = 3.5F;
                  break;
               case STORM_SHADOW:
                  var10001 = 1.7F;
                  break;
               case JAGM:
               case SPIKE_NLOS:
                  var10001 = 0.78F;
                  break;
               default:
                  throw new IncompatibleClassChangeError();
            }
         } else if (this.nuclear) {
            var10001 = 10.5F;
         } else if (this.shaped) {
            var10001 = 0.42F;
         } else {
            switch (this.kind) {
               case FP5:
               case PEACEKEEPER:
                  var10001 = 5.5F;
                  break;
               case STORM_SHADOW:
                  var10001 = 1.7F;
                  break;
               case JAGM:
               case SPIKE_NLOS:
                  var10001 = 0.62F;
                  break;
               default:
                  throw new IncompatibleClassChangeError();
            }
         }

         this.scale = var10001;
         double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(e.x(), e.y(), e.z()));
         this.flashTicks = this.interception ? 5 : 20 + (int)(distance / 100.0) * 4;
         byte var10000;
         if (this.interception) {
            var10000 = 6;
         } else if (this.nuclear) {
            var10000 = 16;
         } else {
            switch (this.kind) {
               case FP5:
               case PEACEKEEPER:
                  var10000 = 12;
                  break;
               case STORM_SHADOW:
                  var10000 = 9;
                  break;
               case JAGM:
               case SPIKE_NLOS:
                  var10000 = 6;
                  break;
               default:
                  throw new IncompatibleClassChangeError();
            }
         }

         int streamerCount = var10000;
         this.streamers = new ArrayList<>(streamerCount);

         for (int i = 0; i < streamerCount; i++) {
            double a = (i + this.random.nextDouble() * 0.65) * ((Math.PI * 2) / streamerCount);
            double hSpeed = (0.32 + this.random.nextDouble() * 0.58) * Math.min(2.8, Math.max(0.75, this.scale * 0.45));
            double vSpeed = (this.interception ? (this.random.nextDouble() - 0.35) * 0.65 : 0.38 + this.random.nextDouble() * 0.68)
               * Math.min(2.8, Math.max(0.75, this.scale * 0.45));
            int life = 16 + this.random.nextInt(16);
            float sz = (0.14F + this.random.nextFloat() * 0.12F) * (float)Math.min(2.2, Math.max(0.8, this.scale * 0.45));
            this.streamers.add(new MissileExplosionEffects.DebrisStreamer(new Vec3(Math.cos(a) * hSpeed, vSpeed, Math.sin(a) * hSpeed), life, sz));
         }
      }

      void particle(SimpleParticleType type, double x, double y, double z, double dx, double dy, double dz, float size, float tint) {
         if (type != BFParticles.BLAST_FLASH.get()) {
            if (type == BFParticles.BLAST_DUST.get()) {
               ContrailRenderer.emitBlastDust(
                  new Vec3(this.event.x() + x, this.event.y() + y, this.event.z() + z), new Vec3(dx, dy, dz).scale(20.0), size * 0.3F, this.random.nextInt()
               );
            } else if (type == BFParticles.BLAST_SMOKE.get()) {
               ContrailRenderer.emitBlastWave(
                  new Vec3(this.event.x() + x, this.event.y() + y, this.event.z() + z),
                  new Vec3(dx, dy, dz).scale(20.0),
                  size * 0.35F,
                  this.random.nextInt()
               );
            } else {
               if (Minecraft.getInstance().particleEngine.createParticle(type, this.event.x() + x, this.event.y() + y, this.event.z() + z, dx, dy, dz) instanceof BlastParticle b
                  )
                {
                  if (type == BFParticles.BLAST_FLASH.get()) {
                     b.configure(size, tint, this.flashTicks);
                  } else {
                     b.configure(size, tint);
                  }
               }
            }
         }
      }

      void tick() {
         if (this.age == 0) {
            float glare = this.interception
               ? (this.kind != MissileKind.FP5 && this.kind != MissileKind.PEACEKEEPER ? (this.kind == MissileKind.STORM_SHADOW ? 58.0F : 32.0F) : 90.0F)
               : (
                  this.nuclear
                     ? 280.0F
                     : (this.shaped ? 24.0F : (this.kind == MissileKind.FP5 ? 190.0F : (this.kind == MissileKind.STORM_SHADOW ? 92.0F : 42.0F)))
               );
            this.particle(
               (SimpleParticleType)BFParticles.BLAST_FLASH.get(),
               0.0,
               this.interception ? 0.0 : 5.0,
               0.0,
               0.0,
               0.0,
               0.0,
               this.interception ? glare * 0.3F : glare,
               1.0F
            );
            if (!this.interception) {
               this.particle((SimpleParticleType)BFParticles.BLAST_FLASH.get(), 0.0, 0.8, 0.0, 0.0, 0.0, 0.0, glare * 0.62F, 1.0F);
            }

            this.particle((SimpleParticleType)BFParticles.BLAST_RING.get(), 0.0, 0.25, 0.0, 0.0, 0.0, 0.0, 4.2F * this.scale, 1.0F);
         }

         if (this.age < 12) {
            int count = this.kind == MissileKind.JAGM ? 6 : (this.kind == MissileKind.FP5 ? 14 : 10);

            for (int i = 0; i < count; i++) {
               double a = this.random.nextDouble() * Math.PI * 2.0;
               double r = this.random.nextDouble() * this.scale * 2.1;
               double up = this.random.nextDouble() - (this.interception ? 0.5 : 0.0);
               this.particle(
                  (SimpleParticleType)BFParticles.BLAST_FIRE.get(),
                  Math.cos(a) * r,
                  up * this.scale * 2.1,
                  Math.sin(a) * r,
                  Math.cos(a) * 0.21 * this.scale,
                  (0.11 + up * 0.2) * this.scale,
                  Math.sin(a) * 0.21 * this.scale,
                  (0.85F + this.random.nextFloat() * 0.85F) * this.scale,
                  1.0F
               );
            }
         }

         if (this.age >= 1 && this.age <= 28) {
            Vec3 origin = new Vec3(this.event.x(), this.event.y() + 0.4, this.event.z());

            for (int i = 0; i < this.streamers.size(); i++) {
               MissileExplosionEffects.DebrisStreamer s = this.streamers.get(i);
               if (this.age < s.maxAge()) {
                  double gravity = -0.026 * this.age * this.age;
                  Vec3 pos = origin.add(s.velocity().scale(this.age)).add(0.0, gravity, 0.0);
                  Vec3 instVel = s.velocity().add(0.0, -0.052 * this.age, 0.0);
                  float lifeFade = 1.0F - (float)this.age / s.maxAge();
                  if (this.age % 2 == 0) {
                     ContrailRenderer.emitSmoke(
                        pos, instVel.scale(3.2), s.size() * (0.65F + 0.45F * lifeFade), (int)(this.event.seed() + i * 37L + this.age)
                     );
                  }

                  if (this.age <= 14) {
                     this.particle(
                        (SimpleParticleType)BFParticles.BLAST_EMBER.get(),
                        pos.x - this.event.x(),
                        pos.y - this.event.y(),
                        pos.z - this.event.z(),
                        instVel.x * 0.35,
                        instVel.y * 0.35,
                        instVel.z * 0.35,
                        0.08F + this.random.nextFloat() * 0.06F,
                        1.0F
                     );
                  }
               }
            }
         }

         if (this.age >= 2 && this.age < 16 && this.age % 3 == 2) {
            int count = this.kind == MissileKind.STORM_SHADOW ? 4 : (this.kind == MissileKind.FP5 ? 5 : 2);

            for (int ix = 0; ix < count; ix++) {
               double a = this.random.nextDouble() * Math.PI * 2.0;
               double r = this.random.nextDouble() * this.scale * 3.2;
               double column = this.kind == MissileKind.STORM_SHADOW ? this.age * 0.14 : this.age * 0.08;
               this.particle(
                  (SimpleParticleType)BFParticles.BLAST_SMOKE.get(),
                  Math.cos(a) * r,
                  column,
                  Math.sin(a) * r,
                  Math.cos(a) * 0.075 * this.scale,
                  (0.08 + this.random.nextDouble() * 0.11) * this.scale,
                  Math.sin(a) * 0.075 * this.scale,
                  (1.3F + this.random.nextFloat()) * this.scale,
                  this.kind == MissileKind.STORM_SHADOW ? 0.13F : 0.16F
               );
            }
         }

         if (this.nuclear && this.age >= 5 && this.age < 90 && this.age % 3 == 0) {
            for (int ix = 0; ix < 12; ix++) {
               double a = this.random.nextDouble() * Math.PI * 2.0;
               double cap = Math.min(1.0, (this.age - 5) / 55.0);
               double r = ix < 4 ? 3.0 + this.random.nextDouble() * 5.0 : (8.0 + 18.0 * cap) * (0.45 + this.random.nextDouble() * 0.55);
               double y = ix < 4 ? this.age * 1.1 : this.age * 1.35 + this.random.nextDouble() * 12.0;
               this.particle(
                  (SimpleParticleType)BFParticles.BLAST_SMOKE.get(),
                  Math.cos(a) * r,
                  y,
                  Math.sin(a) * r,
                  Math.cos(a) * 0.12,
                  0.32 + this.random.nextDouble() * 0.24,
                  Math.sin(a) * 0.12,
                  6.5F + this.random.nextFloat() * 6.0F,
                  0.12F
               );
            }
         }

         int waveEnd = this.interception
            ? (this.kind != MissileKind.FP5 && this.kind != MissileKind.PEACEKEEPER ? (this.kind == MissileKind.STORM_SHADOW ? 6 : 2) : 10)
            : (this.kind == MissileKind.FP5 ? 16 : (this.kind == MissileKind.STORM_SHADOW ? 9 : 4));
         if (this.age > 0 && this.age <= waveEnd && this.age % 3 == 1) {
            int count = this.kind == MissileKind.FP5 ? 12 : 7;

            for (int ix = 0; ix < count; ix++) {
               double a = this.random.nextDouble() * Math.PI * 2.0;
               double dy = this.interception ? this.random.nextDouble() * 2.0 - 1.0 : (this.random.nextDouble() - 0.38) * 0.8;
               double length = Math.sqrt(1.0 + dy * dy);
               double nx = Math.cos(a) / length;
               double ny = dy / length;
               double nz = Math.sin(a) / length;
               double radius = this.age * (this.kind == MissileKind.FP5 ? 1.6 : 0.8) * this.scale / 5.5;
               this.particle(
                  (SimpleParticleType)BFParticles.BLAST_SMOKE.get(),
                  nx * radius,
                  ny * radius,
                  nz * radius,
                  nx * (0.36 + this.random.nextDouble() * 0.45),
                  ny * (0.36 + this.random.nextDouble() * 0.45),
                  nz * (0.36 + this.random.nextDouble() * 0.45),
                  (this.kind == MissileKind.FP5 ? 2.2F : 1.15F) * this.scale * (0.65F + this.random.nextFloat() * 0.55F),
                  0.11F
               );
            }
         }

         if (!this.interception && (this.age == 1 || this.age == 4 || this.age == 8)) {
            int n = this.kind == MissileKind.JAGM ? 24 : 52;

            for (int ix = 0; ix < n; ix++) {
               double a = ix * Math.PI * 2.0 / n;
               double r = (this.age == 1 ? 0.8 : (this.age == 4 ? 2.2 : 3.8)) * this.scale;
               double v = (0.38 + this.random.nextDouble() * 0.38) * this.scale;
               this.particle(
                  (SimpleParticleType)BFParticles.BLAST_DUST.get(),
                  Math.cos(a) * r,
                  0.15,
                  Math.sin(a) * r,
                  Math.cos(a) * v,
                  0.035,
                  Math.sin(a) * v,
                  (0.68F + this.random.nextFloat()) * this.scale,
                  0.85F + this.random.nextFloat() * 0.3F
               );
            }
         }

         if (this.age == 1 || this.age == 3 || this.age == 6) {
            int n = this.kind == MissileKind.FP5 ? 95 : (this.kind == MissileKind.STORM_SHADOW ? 60 : 24);

            for (int ix = 0; ix < n; ix++) {
               double a = this.random.nextDouble() * Math.PI * 2.0;
               double v = (0.32 + this.random.nextDouble() * 0.95) * this.scale;
               this.particle(
                  (SimpleParticleType)BFParticles.BLAST_EMBER.get(),
                  0.0,
                  0.35,
                  0.0,
                  Math.cos(a) * v,
                  (0.18 + this.random.nextDouble() * 0.75) * this.scale,
                  Math.sin(a) * v,
                  0.065F + this.random.nextFloat() * 0.075F,
                  1.0F
               );
            }
         }

         this.age++;
      }
   }

   private record DebrisStreamer(Vec3 velocity, int maxAge, float size) {
   }
}
