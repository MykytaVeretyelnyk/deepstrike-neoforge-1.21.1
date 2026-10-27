package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.sbw.deepstrike.client.smoke.SmokeFlow;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

public final class LaunchEffectWarmup {
   private static final int MAX_JOBS = 16;
   private static final String[] TEXTURES = new String[]{"ported/cloud_1_512.png", "ported/smoke_light.png", "zerok/trail.png", "explosion_billow.png"};
   private static final int MATERIALS = 4;
   private static final Map<BlockPos, LaunchEffectWarmup.Job> JOBS = new LinkedHashMap<>();
   private static final Map<BlockPos, LaunchEffectWarmup.Review> FINISHED = new HashMap<>();
   private static final Logger LOG = LogUtils.getLogger();
   private static ClientLevel level;
   private static boolean registered;

   private static List<BlockPos> nearbyTiles(BlockPos origin, BlockPos section) {
      List<BlockPos> result = new ArrayList<>(64);

      for (int x = section.getX() * 4; x < section.getX() * 4 + 4; x++) {
         for (int y = section.getY() * 4; y < section.getY() * 4 + 4; y++) {
            for (int z = section.getZ() * 4; z < section.getZ() * 4 + 4; z++) {
               result.add(new BlockPos(x, y, z));
            }
         }
      }

      result.sort(Comparator.comparingDouble(tile -> {
         double xx = tile.getX() * 4 + 2 - origin.getX();
         double yx = tile.getY() * 4 + 2 - origin.getY();
         double zx = tile.getZ() * 4 + 2 - origin.getZ();
         return xx * xx + yx * yx + zx * zx;
      }));
      return result;
   }

   public static void accept(DeepStrikeNetwork.LaunchCountdown message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         if (level != mc.level) {
            level = mc.level;
            JOBS.clear();
            FINISHED.clear();
         }

         LaunchEffectWarmup.Job job = JOBS.get(message.launcher());
         if (!message.active()) {
            if (job != null && Objects.equals(job.mission, message.mission())) {
               JOBS.remove(message.launcher());
            }

            FINISHED.remove(message.launcher());
         } else if (message.launchAtTick() > message.serverTick()) {
            if (job != null && Objects.equals(job.mission, message.mission())) {
               job.extend(message, level);
            } else {
               if (JOBS.size() >= 16) {
                  JOBS.remove(JOBS.keySet().iterator().next());
               }

               job = new LaunchEffectWarmup.Job(message, level);
               JOBS.put(message.launcher(), job);
               FINISHED.remove(message.launcher());
            }

            if (!registered) {
               registered = true;
               NeoForge.EVENT_BUS.addListener(LaunchEffectWarmup::render);
            }
         }
      }
   }

   private static void primeEntity(RenderType type) {
      BufferSource buffers = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(256));
      VertexConsumer out = buffers.getBuffer(type);
      Matrix4f inverse = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).invert();

      for (int i = 0; i < 4; i++) {
         float x = i < 2 ? -0.001F : 0.001F;
         float y = i != 0 && i != 3 ? 0.001F : -0.001F;
         Vector3f position = inverse.transformProject(new Vector3f(x, y, 0.0F));
         out.addVertex(position.x, position.y, position.z)
            .setColor(255, 255, 255, 1)
            .setUv(i < 2 ? 0.0F : 1.0F, i != 0 && i != 3 ? 1.0F : 0.0F)
            .setOverlay(0)
            .setLight(15728880)
            .setNormal(0.0F, 1.0F, 0.0F)
            ;
      }

      buffers.endBatch();
   }

   private static void render(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_LEVEL) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != level) {
            JOBS.clear();
            FINISHED.clear();
            level = mc.level;
         } else if (level != null && !JOBS.isEmpty() && !mc.isPaused() && MissileEffectsRenderTypes.smokeReady()) {
            Iterator<LaunchEffectWarmup.Job> iterator = JOBS.values().iterator();

            while (iterator.hasNext()) {
               LaunchEffectWarmup.Job job = iterator.next();
               if (level.getGameTime() >= job.until) {
                  if (!job.complete) {
                     report(job, false);
                  }

                  iterator.remove();
               } else if (!job.complete) {
                  long frameStarted = System.nanoTime();

                  for (int unit = 0; unit < 4 && !job.complete; unit++) {
                     String name = job.taskName();
                     long started = System.nanoTime();
                     boolean done = job.step(mc);
                     double elapsedMs = (System.nanoTime() - started) / 1000000.0;
                     if (elapsedMs > job.peakTaskMs) {
                        job.peakTaskMs = elapsedMs;
                        job.peakTask = name;
                     }

                     if (done) {
                        job.complete = true;
                        FINISHED.put(job.origin, new LaunchEffectWarmup.Review(job.task, job.peakTaskMs));
                        report(job, true);
                        break;
                     }

                     if (elapsedMs >= 1.0 || System.nanoTime() - frameStarted >= 1250000L) {
                        break;
                     }
                  }

                  return;
               }
            }
         }
      }
   }

   private static void report(LaunchEffectWarmup.Job job, boolean complete) {
      if (Boolean.getBoolean("deepstrike.installedSmokeReview")) {
         LOG.info("LAUNCH_PREWARM complete={} tasks={} peakTask={} peakTaskMs={}", new Object[]{complete, job.task, job.peakTask, job.peakTaskMs});
      }
   }

   static boolean reviewComplete(BlockPos origin) {
      return FINISHED.containsKey(origin) || JOBS.containsKey(origin) && JOBS.get(origin).complete;
   }

   static int reviewTasks(BlockPos origin) {
      LaunchEffectWarmup.Review review = FINISHED.get(origin);
      return review != null ? review.tasks : 0;
   }

   static int reviewExpectedTasks() {
      return TEXTURES.length + 4 + 65;
   }

   static double reviewPeakTaskMs(BlockPos origin) {
      LaunchEffectWarmup.Review review = FINISHED.get(origin);
      return review != null ? review.peakTaskMs : 0.0;
   }

   private LaunchEffectWarmup() {
   }

   private static final class Job {
      final UUID mission;
      final BlockPos origin;
      final BlockPos section;
      final List<BlockPos> tiles;
      long until;
      int task;
      double peakTaskMs;
      String peakTask = "";
      boolean complete;

      Job(DeepStrikeNetwork.LaunchCountdown message, ClientLevel world) {
         this.mission = message.mission();
         this.origin = message.launcher();
         this.section = new BlockPos(
            Math.floorDiv(this.origin.getX(), 16), Math.floorDiv(this.origin.getY(), 16), Math.floorDiv(this.origin.getZ(), 16)
         );
         this.tiles = LaunchEffectWarmup.nearbyTiles(this.origin, this.section);
         this.extend(message, world);
      }

      void extend(DeepStrikeNetwork.LaunchCountdown message, ClientLevel world) {
         this.until = world.getGameTime() + Math.max(0L, message.launchAtTick() - message.serverTick());
      }

      boolean step(Minecraft mc) {
         if (this.task < LaunchEffectWarmup.TEXTURES.length) {
            mc.getTextureManager().getTexture(ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/particle/" + LaunchEffectWarmup.TEXTURES[this.task]));
         } else if (this.task < LaunchEffectWarmup.TEXTURES.length + 4) {
            LaunchEffectWarmup.primeEntity(switch (this.task - LaunchEffectWarmup.TEXTURES.length) {
               case 0 -> MissileEffectsRenderTypes.RIBBON;
               case 1 -> MissileEffectsRenderTypes.CLOUD;
               case 2 -> MissileEffectsRenderTypes.BLAST_CORE;
               default -> MissileEffectsRenderTypes.DETAIL;
            });
         } else if (this.task < LaunchEffectWarmup.TEXTURES.length + 4 + this.tiles.size()) {
            BlockPos tile = this.tiles.get(this.task - LaunchEffectWarmup.TEXTURES.length - 4);
            SmokeFlow.prewarmTile(LaunchEffectWarmup.level, tile.getX(), tile.getY(), tile.getZ());
         } else {
            SmokeFlow.prewarmSection(LaunchEffectWarmup.level, this.section.getX(), this.section.getY(), this.section.getZ());
         }

         this.task++;
         return this.task >= LaunchEffectWarmup.TEXTURES.length + 4 + 1 + this.tiles.size();
      }

      String taskName() {
         if (this.task < LaunchEffectWarmup.TEXTURES.length) {
            return "texture";
         } else if (this.task < LaunchEffectWarmup.TEXTURES.length + 4) {
            return "material";
         } else {
            return this.task < LaunchEffectWarmup.TEXTURES.length + 4 + this.tiles.size() ? "tile" : "section";
         }
      }
   }

   private record Review(int tasks, double peakTaskMs) {
   }
}
