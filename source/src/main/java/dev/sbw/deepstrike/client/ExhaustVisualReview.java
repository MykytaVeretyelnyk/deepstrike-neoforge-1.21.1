package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ExhaustVisualReview {
   private static final Logger LOG = LoggerFactory.getLogger(ExhaustVisualReview.class);
   private static final MissileKind[] KINDS = new MissileKind[]{MissileKind.SPIKE_NLOS, MissileKind.JAGM, MissileKind.PEACEKEEPER};
   private static final int SEGMENT = 72;
   private static boolean opening;
   private static volatile boolean prepared;
   private static int ticks;
   private static GuidedMissileEntity observer;
   private static String pending;
   private static final Vec3 CENTER = new Vec3(0.0, 1000.0, 0.0);

   private static boolean enabled(Minecraft mc) {
      return Boolean.getBoolean("deepstrike.exhaustReview")
         && mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("exhaust-options");
   }

   static int index() {
      return Math.min(14, Math.max(0, (ticks - 20) / 72));
   }

   static int phase() {
      return Math.max(0, (ticks - 20) % 72);
   }

   static MissileKind kind() {
      return KINDS[index() / 5];
   }

   static double distance() {
      int phase = phase();
      boolean large = kind() == MissileKind.PEACEKEEPER;
      return phase < 36 ? (large ? 45 : 7) : (phase < 52 ? (large ? 220 : 55) : (large ? 1200 : 350));
   }

   static void tick(ClientTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase == Phase.END && enabled(mc)) {
         mc.options.pauseOnLostFocus = false;
         if (mc.level == null) {
            if (!opening && mc.screen instanceof TitleScreen) {
               opening = true;
               mc.createWorldOpenFlows().openWorld("New World", () -> {});
            }
         } else if (mc.player != null && mc.getSingleplayerServer() != null) {
            if (ticks == 0) {
               ticks = 1;
               mc.setScreen(null);
               mc.options.hideGui = true;
               mc.options.fov().set(55);
               mc.getSingleplayerServer().execute(() -> {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  level.setDayTime(6000L);
                  level.setWeatherParameters(100000, 0, false, false);
                  ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  p.setGameMode(GameType.SPECTATOR);
                  p.teleportTo(0.0, 1000.0, 20.0);
                  ArrayList<Entity> remove = new ArrayList<>();

                  for (Entity e : level.getAllEntities()) {
                     if (e instanceof GuidedMissileEntity) {
                        remove.add(e);
                     }
                  }

                  remove.forEach(Entity::discard);
                  prepared = true;
               });
            } else if (prepared) {
               ticks++;
               if (ticks >= 1100) {
                  LOG.info("EXHAUST_OPTIONS_FINISHED variants=15 captures=75");
                  mc.stop();
               } else if (ticks >= 20) {
                  ExhaustStyles.previewKind = kind();
                  ExhaustStyles.previewVariant = index() % 5 + 1;

                  String view = switch (phase()) {
                     case 12 -> "near-a";
                     case 20 -> "near-b";
                     case 28 -> "near-c";
                     case 44 -> "medium";
                     case 64 -> "far";
                     default -> null;
                  };
                  if (view != null) {
                     pending = kind().id + "-v" + (index() % 5 + 1) + "-" + view;
                  }
               }
            }
         }
      }
   }

   static void render(RenderTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (enabled(mc) && mc.level != null && mc.player != null && ticks >= 20) {
         if (event.phase == Phase.START) {
            if (observer == null) {
               observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            }

            float tail = (float)MissileGeometry.tailDistance(kind());
            double targetX = tail + ExhaustStyles.length(kind(), index() % 5 + 1) * 0.25;
            Vec3 target = CENTER.add(targetX, 0.0, 0.0);
            Vec3 position = target.add(new Vec3(-0.25, 0.055, 0.9665).normalize().scale(distance()));
            observer.setPos(position);
            Vec3 d = target.subtract(observer.getEyePosition());
            observer.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            observer.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
            observer.xo = position.x;
            observer.yo = position.y;
            observer.zo = position.z;
            observer.yRotO = observer.getYRot();
            observer.xRotO = observer.getXRot();
            mc.setCameraEntity(observer);
         } else if (pending != null) {
            String name = pending;
            pending = null;

            try {
               NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

               try {
                  Path dir = mc.gameDirectory.toPath().resolve("screenshots");
                  Files.createDirectories(dir);
                  shot.writeToFile(dir.resolve(name + ".png"));
                  LOG.info("EXHAUST_OPTION_CAPTURE {} distance={} tick={} fps={}", new Object[]{name, distance(), ticks, mc.getFps()});
               } catch (Throwable var9) {
                  if (shot != null) {
                     try {
                        shot.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (shot != null) {
                  shot.close();
               }
            } catch (Exception var10) {
               throw new RuntimeException(var10);
            }
         }
      }
   }

   static void scene(RenderLevelStageEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getStage() == Stage.AFTER_ENTITIES && enabled(mc) && ticks >= 20 && prepared) {
         PoseStack pose = event.getPoseStack();
         BufferSource buffers = mc.renderBuffers().bufferSource();
         buffers.endBatch();
         Vec3 relative = CENTER.subtract(event.getCamera().getPosition());
         float scale = (float)Math.min(1.0, 24.0 / distance());
         pose.pushPose();
         pose.translate(relative.x * scale, relative.y * scale, relative.z * scale);
         pose.scale(scale, scale, scale);
         pose.pushPose();
         pose.mulPose(MissileGeometry.exportRotation(kind()));
         ModelRenderer.render(
            ModelAssets.missile(kind()), ModelAssets.texture(kind(), "olive", false), pose, buffers, 15728880, OverlayTexture.NO_OVERLAY, 0.0F, 0.0F, b -> true
         );
         pose.popPose();
         buffers.endBatch();
         ExhaustPlume.renderVariant(pose, buffers, ticks + event.getPartialTick().getGameTimeDeltaPartialTick(false), (float)MissileGeometry.tailDistance(kind()), kind(), index() % 5 + 1);
         buffers.endBatch();
         pose.popPose();
      }
   }

   private ExhaustVisualReview() {
   }
}
