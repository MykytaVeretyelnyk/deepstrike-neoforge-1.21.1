package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.MissileAssemblyBlock;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.model.LauncherLayout;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;

public final class ModelPreviewScreen extends Screen {
   private static boolean opened;
   private int frames;

   public ModelPreviewScreen() {
      super(Component.literal("DeepStrike renderer verification"));
   }

   static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END && Boolean.getBoolean("deepstrike.visualCheck")) {
         Minecraft mc = Minecraft.getInstance();
         if (!opened
            && mc.getOverlay() == null
            && (mc.screen instanceof TitleScreen || mc.screen != null && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))) {
            opened = true;
            mc.setScreen(new ModelPreviewScreen());
         }
      }
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      if (Boolean.getBoolean("deepstrike.stage2Visual")) {
         this.renderStageTwo(g);
      } else if (Boolean.getBoolean("deepstrike.stage1Visual")) {
         this.renderAssembly(g);
      } else {
         g.fill(0, 0, this.width, this.height, -15263726);
         g.drawString(this.font, "DEEPSTRIKE / ACTUAL MINECRAFT MODEL RENDER", 16, 12, -2233424);
         g.drawString(this.font, "FP-5 on cradle", 16, 35, -2233424);
         g.drawString(this.font, "JAGM / 6 separate tubes (3 loaded)", this.width / 2 + 8, 35, -2233424);
         Lighting.setupFor3DItems();
         PoseStack pose = g.pose();
         float scale = Math.min(this.width / 19.0F, this.height / 13.0F);
         this.begin(pose, this.width * 0.25F, this.height * 0.37F, scale, 25.0F, 32.0F);
         this.mount(g, LauncherBlock.Family.FP5, 0.0F, 0.0F, 1);
         pose.popPose();
         this.begin(pose, this.width * 0.75F, this.height * 0.37F, scale, 20.0F, 35.0F);
         this.mount(g, LauncherBlock.Family.JAGM, 0.0F, 20.0F, 3);
         pose.popPose();
         g.drawString(this.font, "Flight: nose follows -X / exhaust from +X", 16, (int)(this.height * 0.55F), -2233424);
         this.begin(pose, this.width * 0.27F, this.height * 0.69F, scale, 15.0F, 35.0F);
         ModelRenderer.render("fp5_flamingo", "fp5_missile_graphite", pose, g.bufferSource(), 15728880, 655360, 0.0F, 0.0F, b -> true);
         pose.popPose();
         this.begin(pose, this.width * 0.27F, this.height * 0.86F, scale * 0.85F, 15.0F, 35.0F);
         ModelRenderer.render("storm_shadow", "storm_shadow", pose, g.bufferSource(), 15728880, 655360, 0.0F, 0.0F, b -> true);
         pose.popPose();
         this.begin(pose, this.width * 0.73F, this.height * 0.67F, scale * 1.25F, 15.0F, 35.0F);
         ModelRenderer.render("agm179_jagm", "jagm_atlas", pose, g.bufferSource(), 15728880, 655360, 0.0F, 0.0F, b -> true);
         ExhaustPlume.render(pose, g.bufferSource(), 10.0F);
         pose.popPose();
         g.flush();
         g.drawString(this.font, "Inventory: actual 3D item renderer", this.width / 2 + 8, (int)(this.height * 0.8), -2233424);
         ItemStack[] items = new ItemStack[]{
            new ItemStack((ItemLike)BFItems.FP5.get()), new ItemStack((ItemLike)BFItems.JAGM.get()), new ItemStack((ItemLike)BFItems.JAGM_LAUNCHER.get())
         };

         for (int i = 0; i < items.length; i++) {
            pose.pushPose();
            pose.translate(this.width * 0.56 + i * 55, this.height * 0.84, 0.0);
            pose.scale(2.5F, 2.5F, 1.0F);
            g.renderItem(items[i], 0, 0);
            pose.popPose();
         }

         g.flush();
         if (++this.frames == 80) {
            Minecraft mc = Minecraft.getInstance();

            try {
               NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

               try {
                  Path dir = mc.gameDirectory.toPath().resolve("screenshots");
                  Files.createDirectories(dir);
                  shot.writeToFile(dir.resolve("deepstrike-model-verification.png"));
               } catch (Throwable var13) {
                  if (shot != null) {
                     try {
                        shot.close();
                     } catch (Throwable var12) {
                        var13.addSuppressed(var12);
                     }
                  }

                  throw var13;
               }

               if (shot != null) {
                  shot.close();
               }
            } catch (Exception var14) {
               throw new RuntimeException(var14);
            }
         }
      }
   }

   public void tick() {
      if (this.frames > 100) {
         Minecraft mc = Minecraft.getInstance();
         mc.createWorldOpenFlows().openWorld("New World", () -> {});
         this.frames = -100000;
      }
   }

   private void renderAssembly(GuiGraphics g) {
      g.fill(0, 0, this.width, this.height, -14671334);
      g.drawString(this.font, "DSM 1.1.0 / Assembly station / Minecraft baked model", 12, 12, -1118482);
      Lighting.setupFor3DItems();
      PoseStack pose = g.pose();
      BlockState state = ((MissileAssemblyBlock)BFBlocks.MISSILE_ASSEMBLY_STATION.get()).defaultBlockState();
      float scale = Math.min(this.width / 5.0F, this.height / 4.0F);

      for (int i = 0; i < 4; i++) {
         this.begin(pose, this.width * (i % 2 == 0 ? 0.25F : 0.75F), this.height * (i < 2 ? 0.38F : 0.78F), scale, i == 3 ? -28.0F : 25.0F, 35 + i * 90);
         pose.translate(-0.5, -0.5, -0.5);
         Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, g.bufferSource(), 15728880, 655360);
         pose.popPose();
      }

      pose.pushPose();
      pose.translate(this.width - 52, 22.0F, 0.0F);
      pose.scale(2.0F, 2.0F, 1.0F);
      g.renderItem(new ItemStack(state.getBlock()), 0, 0);
      pose.popPose();
      g.flush();
      if (++this.frames == 80) {
         Minecraft mc = Minecraft.getInstance();

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path dir = mc.gameDirectory.toPath().resolve("screenshots");
               Files.createDirectories(dir);
               shot.writeToFile(dir.resolve("stage1-assembly-station.png"));
            } catch (Throwable var10) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }
               }

               throw var10;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var11) {
            throw new RuntimeException(var11);
         }
      }
   }

   private void renderStageTwo(GuiGraphics g) {
      g.fill(0, 0, this.width, this.height, -14671334);
      g.drawString(this.font, "DSM 1.1.0 / Stage 2 / Actual game geometry", 12, 12, -1118482);
      Lighting.setupFor3DItems();
      PoseStack pose = g.pose();
      MissileKind[] kinds = MissileKind.values();

      for (int i = 0; i < 5; i++) {
         MissileKind kind = kinds[i];
         float x = this.width * ((i % 3 + 0.5F) / 3.0F);
         float y = this.height * (i < 3 ? 0.32F : 0.76F);
         g.drawString(this.font, kind.name(), (int)(x - this.width / 7.0F), (int)(y - this.height * 0.14), -1118482);
         AABB bounds = MissileGeometry.canonicalBounds(kind);
         float scale = Math.min(this.width / 3.6F / (float)bounds.getXsize(), this.height / 4.0F / (float)Math.max(1.0, bounds.getYsize()));
         this.begin(pose, x, y, scale, 15.0F, 35.0F);
         pose.translate(-(bounds.minX + bounds.maxX) * 0.5, 0.0, 0.0);
         pose.mulPose(MissileGeometry.flight(kind, 90.0F, 0.0F));
         ModelRenderer.render(
            ModelAssets.missile(kind),
            ModelAssets.texture(kind, MissileData.defaultCamo(kind), false),
            pose,
            g.bufferSource(),
            15728880,
            655360,
            0.0F,
            0.0F,
            b -> true
         );
         pose.popPose();
      }

      float x = this.width * 5.0F / 6.0F;
      float y = this.height * 0.76F;
      g.drawString(this.font, "Peacekeeper / mounted", (int)(x - this.width / 7.0F), (int)(this.height * 0.53), -1118482);
      this.begin(pose, x, y, Math.min(this.width / 24.0F, this.height / 55.0F), 15.0F, 35.0F);
      pose.translate(0.0F, -10.0F, 0.0F);
      pose.pushPose();
      pose.mulPose(LauncherLayout.root(LauncherBlock.Family.PEACEKEEPER, 0.0F));
      ModelRenderer.render("lgm118_holder", "lgm118_holder_graphite", pose, g.bufferSource(), 15728880, 655360, 0.0F, 0.0F, b -> true);
      pose.popPose();
      pose.mulPose(LauncherLayout.mount(LauncherBlock.Family.PEACEKEEPER, 0.0F, 0.0F, 0.0F, 0));
      ModelRenderer.render("lgm118_peacekeeper", "lgm118_peacekeeper_graphite", pose, g.bufferSource(), 15728880, 655360, 0.0F, 0.0F, b -> true);
      pose.popPose();
      g.flush();
      if (++this.frames == 80) {
         Minecraft mc = Minecraft.getInstance();

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path dir = mc.gameDirectory.toPath().resolve("screenshots");
               Files.createDirectories(dir);
               shot.writeToFile(dir.resolve("stage2-models-and-mount.png"));
            } catch (Throwable var11) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var12) {
            throw new RuntimeException(var12);
         }
      }
   }

   private void begin(PoseStack pose, float x, float y, float scale, float pitch, float yaw) {
      pose.pushPose();
      pose.translate(x, y, 100.0F);
      pose.scale(scale, -scale, scale);
      pose.mulPose(Axis.XP.rotationDegrees(pitch));
      pose.mulPose(Axis.YP.rotationDegrees(yaw));
   }

   private void mount(GuiGraphics g, LauncherBlock.Family family, float yaw, float pitch, int loaded) {
      PoseStack pose = g.pose();
      String tex = family == LauncherBlock.Family.JAGM ? "jagm_atlas" : "fp5_graphite";
      pose.pushPose();
      pose.translate(-0.5, 0.0, -0.5);
      pose.mulPose(LauncherLayout.root(family, 90.0F));
      ModelRenderer.render(
         ModelAssets.launcher(family), tex, pose, g.bufferSource(), 15728880, 655360, yaw, pitch, b -> !b.startsWith("cover_") && !b.equals("booster")
      );
      pose.popPose();

      for (int i = 0; i < loaded; i++) {
         pose.pushPose();
         pose.translate(-0.5, 0.0, -0.5);
         pose.mulPose(LauncherLayout.mount(family, 90.0F, yaw, pitch, i));
         ModelRenderer.render(
            family == LauncherBlock.Family.JAGM ? "agm179_jagm" : "fp5_flamingo",
            family == LauncherBlock.Family.JAGM ? tex : "fp5_missile_graphite",
            pose,
            g.bufferSource(),
            15728880,
            655360,
            0.0F,
            0.0F,
            b -> true
         );
         if (family == LauncherBlock.Family.JAGM) {
            pose.translate(-1.196875, 0.0, 0.0);
            ModelRenderer.render("jagm_cover", tex, pose, g.bufferSource(), 15728880, 655360, 0.0F, 0.0F, b -> true);
         }

         pose.popPose();
      }
   }
}
