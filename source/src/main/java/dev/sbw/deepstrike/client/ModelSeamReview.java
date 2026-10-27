package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import net.neoforged.bus.api.EventPriority;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.LoggerFactory;

final class ModelSeamReview {
   private static final String[] NAMES = new String[]{
      "fp5_flamingo",
      "storm_shadow",
      "agm179_jagm",
      "lgm118_peacekeeper",
      "spike_nlos_missile",
      "tamir",
      "pac_2",
      "pac_3",
      "fp5_launcher",
      "jagm_launcher",
      "jagm_cover",
      "lgm118_holder",
      "iron_dome",
      "an_mpq_65",
      "spike_nlos_jltv",
      "mim_104_patriot_truck",
      "siren_tower",
      "fp5_flamingo_damaged",
      "storm_shadow_damaged",
      "agm179_jagm_damaged",
      "lgm118_peacekeeper_damaged",
      "spike_nlos_missile_damaged"
   };
   private static final float[] ANGLES = new float[]{25.0F, 115.0F, 205.0F, 295.0F};
   private static final int PAGES = (NAMES.length + 3) / 4;
   private static final int ATLAS = PAGES * 4;
   private static int ticks;
   private static int index = -1;
   private static boolean capture;
   private static boolean boot;
   private static volatile PatriotTruckEntity truck;
   private static GuidedMissileEntity observer;
   private static Su24mEntity plane;

   static void tick(Minecraft mc) {
      if (System.getProperty("deepstrike.reviewCase", "").startsWith("model-seams-design")) {
         try {
            VehicleDesignReview.tick(mc);
         } catch (Throwable var3) {
            LoggerFactory.getLogger(ModelSeamReview.class).error("VEHICLE_DESIGN_TICK_FAILED", var3);
            throw var3;
         }
      } else if (!mc.gameDirectory.toPath().toAbsolutePath().toString().contains("/build/")) {
         throw new IllegalStateException("Model review requires disposable lab");
      } else {
         if (index == -1 && System.getProperty("deepstrike.reviewCase", "").startsWith("model-seams-cab")) {
            ticks = ATLAS * 35;
         }

         int next = ticks / 35;
         if (next >= ATLAS + 6) {
            mc.setCameraEntity(mc.player);
            mc.setScreen(null);
            LoggerFactory.getLogger(ModelSeamReview.class).info("MODEL_SEAM_REVIEW_PASS views={} meshes=26 FP5_OPTIONS_FINISHED", ATLAS + 6);
            mc.stop();
         } else {
            if (next != index) {
               index = next;
               if (index >= ATLAS && index < ATLAS + 2) {
                  mc.setScreen(null);
               } else {
                  mc.setScreen(new ModelSeamReview.View());
               }
            }

            if (index >= ATLAS && index < ATLAS + 2) {
               if (!boot) {
                  boot = true;
                  NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) -> {
                     if (index >= ATLAS && index < ATLAS + 2) {
                        float pitchx = index == ATLAS ? 38.0F : 58.0F;
                        event.setYaw(0.0F);
                        event.setPitch(pitchx);
                        event.setRoll(0.0F);
                        if (!ClientCamera.moveCameraToNose(event.getCamera(), cabPosition(mc), 0.0F, pitchx)) {
                           throw new IllegalStateException("Cab camera placement failed");
                        }
                     }
                  });
                  mc.options.hideGui = true;
                  mc.options.fov().set(75);
                  mc.getSingleplayerServer().execute(() -> {
                     ServerLevel level = mc.getSingleplayerServer().overworld();
                     truck = (PatriotTruckEntity)((EntityType)BFEntities.PATRIOT_TRUCK.get()).create(level);
                     truck.setPos(0.0, 105.0, 0.0);
                     truck.setYRot(0.0F);
                     truck.setNoGravity(true);
                     level.addFreshEntity(truck);
                  });
               }

               if (truck == null || mc.level.getEntity(truck.getId()) == null) {
                  return;
               }

               if (observer == null) {
                  observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
               }

               float pitch = index == ATLAS ? 38.0F : 58.0F;
               observer.setPos(cabPosition(mc));
               observer.xo = observer.getX();
               observer.yo = observer.getY();
               observer.zo = observer.getZ();
               observer.setYRot(0.0F);
               observer.yRotO = 0.0F;
               observer.setXRot(pitch);
               observer.xRotO = pitch;
               mc.setCameraEntity(observer);
            } else if (index >= ATLAS + 2) {
               mc.setCameraEntity(mc.player);
               mc.options.hideGui = false;
            }

            if (ticks % 35 == 22) {
               capture = true;
            }

            ticks++;
         }
      }
   }

   private static Vec3 cabPosition(Minecraft mc) {
      Entity live = truck == null ? null : mc.level.getEntity(truck.getId());
      return live == null ? new Vec3(0.625, 107.48, 6.3) : live.position().add(0.625, 2.48, 6.3);
   }

   static void render(Minecraft mc, RenderTickEvent e) {
      if (System.getProperty("deepstrike.reviewCase", "").startsWith("model-seams-design")) {
         try {
            VehicleDesignReview.render(mc, e);
         } catch (Throwable var6) {
            LoggerFactory.getLogger(ModelSeamReview.class).error("VEHICLE_DESIGN_RENDER_FAILED", var6);
            throw var6;
         }
      } else if (e.phase == Phase.END && capture) {
         capture = false;
         if (index == ATLAS || index == ATLAS + 1) {
            LoggerFactory.getLogger(ModelSeamReview.class).info("MODEL_CAB_POSITION entity={} camera={}", truck.position(), mc.gameRenderer.getMainCamera().getPosition());
         }

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path path = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(path);
               shot.writeToFile(path.resolve(String.format("view-%02d.png", index)));
            } catch (Throwable var7) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var5) {
                     var7.addSuppressed(var5);
                  }
               }

               throw var7;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var8) {
            throw new IllegalStateException(var8);
         }
      }
   }

   private static String texture(String name) {
      String base = name.replace("_damaged", "");

      return switch (base) {
         case "fp5_flamingo" -> "fp5_missile_olive";
         case "fp5_launcher" -> "fp5_olive";
         case "agm179_jagm", "jagm_launcher", "jagm_cover" -> "jagm_atlas";
         case "lgm118_peacekeeper", "lgm118_holder" -> base + "_graphite";
         case "iron_dome", "an_mpq_65", "mim_104_patriot_truck" -> base + "_forest";
         case "spike_nlos_jltv" -> base + "_green";
         default -> base;
      };
   }

   private static final class View extends Screen {
      View() {
         super(Component.literal("Model joint inspection"));
      }

      public boolean isPauseScreen() {
         return false;
      }

      public void render(GuiGraphics g, int mx, int my, float partial) {
         g.fill(0, 0, this.width, this.height, -12235952);
         g.drawString(this.font, "Production geometry / view " + ModelSeamReview.index, 10, 8, -1);
         Lighting.setupFor3DItems();
         if (ModelSeamReview.index >= ModelSeamReview.ATLAS + 2) {
            if (ModelSeamReview.plane == null) {
               ModelSeamReview.plane = (Su24mEntity)((EntityType)BFEntities.SU24M.get()).create(Minecraft.getInstance().level);
            }

            PoseStack pose = g.pose();
            pose.pushPose();
            pose.translate(this.width * 0.5, this.height * 0.63, 200.0);
            float s = Math.min(this.width / 31.0F, this.height / 18.0F);
            pose.scale(s, -s, s);
            pose.mulPose(Axis.XP.rotationDegrees(18.0F));
            pose.mulPose(Axis.YP.rotationDegrees(ModelSeamReview.ANGLES[ModelSeamReview.index - ModelSeamReview.ATLAS - 2]));
            Minecraft.getInstance().getEntityRenderDispatcher().render(ModelSeamReview.plane, 0.0, 0.0, 0.0, 0.0F, 0.0F, pose, g.bufferSource(), 15728880);
            pose.popPose();
         } else {
            for (int i = 0; i < 4; i++) {
               int modelIndex = ModelSeamReview.index / 4 * 4 + i;
               if (modelIndex >= ModelSeamReview.NAMES.length) {
                  break;
               }

               String name = ModelSeamReview.NAMES[modelIndex];
               ModelRenderer.BakedModel model = ModelRenderer.bakeModel(name);
               Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
               Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

               for (ModelRenderer.BakedBone bone : model.bones) {
                  for (ModelRenderer.BakedFace f : bone.staticFaces) {
                     float[][] vs = new float[][]{{f.x0, f.y0, f.z0}, {f.x1, f.y1, f.z1}, {f.x2, f.y2, f.z2}, {f.x3, f.y3, f.z3}};

                     for (float[] v : vs) {
                        Vector3f p = new Vector3f(v);
                        min.min(p);
                        max.max(p);
                     }
                  }
               }

               float angle = ModelSeamReview.ANGLES[ModelSeamReview.index % 4];
               Matrix4f rotation = new Matrix4f()
                  .rotateX((float)Math.toRadians(ModelSeamReview.index % 2 == 0 ? 18.0 : -18.0))
                  .rotateY((float)Math.toRadians(angle));
               Vector3f rmin = new Vector3f(Float.POSITIVE_INFINITY);
               Vector3f rmax = new Vector3f(Float.NEGATIVE_INFINITY);

               for (int c = 0; c < 8; c++) {
                  Vector3f v = new Vector3f((c & 1) == 0 ? min.x : max.x, (c & 2) == 0 ? min.y : max.y, (c & 4) == 0 ? min.z : max.z);
                  rotation.transformPosition(v);
                  rmin.min(v);
                  rmax.max(v);
               }

               float cellW = this.width / 2.0F;
               float cellH = (this.height - 24) / 2.0F;
               float scale = Math.min(cellW * 0.86F / Math.max(0.1F, rmax.x - rmin.x), cellH * 0.78F / Math.max(0.1F, rmax.y - rmin.y));
               float x = cellW * (i % 2 + 0.5F);
               float y = 24.0F + cellH * (i / 2 + 0.55F);
               g.drawString(this.font, name, (int)(x - cellW * 0.46F), (int)(y - cellH * 0.45F), -1118482);
               PoseStack pose = g.pose();
               pose.pushPose();
               pose.translate(x, y, 200.0F);
               pose.scale(scale, -scale, scale);
               pose.mulPose(rotation);
               pose.translate(-(min.x + max.x) / 2.0F, -(min.y + max.y) / 2.0F, -(min.z + max.z) / 2.0F);
               ModelRenderer.render(name, ModelSeamReview.texture(name), pose, g.bufferSource(), 15728880, 655360, 0.0F, 0.0F, b -> true);
               pose.popPose();
            }
         }

         g.flush();
         Lighting.setupForFlatItems();
      }
   }
}
