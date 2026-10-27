package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.network.SpikeNlosNetwork;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

public final class SpikeTvCamera {
   private static final KeyMapping THERMAL = new KeyMapping("key.deepstrike.spike_thermal", 78, "key.categories.deepstrike");
   private static boolean thermal;
   private static double cursorX;
   private static double cursorY;
   private static TextureTarget frame;
   private static int previous = -1;
   private static int controlTick;

   static void resetCursor() {
      cursorY = 0.0;
      cursorX = 0.0;
   }

   static void moveCursor(double dx, double dy) {
      Window window = Minecraft.getInstance().getWindow();
      cursorX = Mth.clamp(cursorX + dx * 2.0 / window.getWidth(), -0.9, 0.9);
      cursorY = Mth.clamp(cursorY + dy * 2.0 / window.getHeight(), -0.8, 0.8);
   }

   static Vec3 aimDirection(Camera camera) {
      Window w = Minecraft.getInstance().getWindow();
      double tangent = Math.tan(Math.toRadians(ClientCamera.viewFov() * 0.5));
      return new Vec3(camera.getLookVector())
         .add(new Vec3(camera.getLeftVector()).scale(-cursorX * tangent * w.getWidth() / w.getHeight()))
         .add(new Vec3(camera.getUpVector()).scale(-cursorY * tangent))
         .normalize();
   }

   public static void register(RegisterKeyMappingsEvent e) {
      e.register(THERMAL);
   }

   public static void tick(ClientTickEvent e) {
      if (e.phase == Phase.END) {
         int tick = ++controlTick;
         Minecraft mc = Minecraft.getInstance();
         GuidedMissileEntity missile = ClientCamera.spike();
         if (missile != null) {
            if (previous != missile.getId()) {
               thermal = false;
               previous = missile.getId();
            }

            while (THERMAL.consumeClick()) {
               thermal = !thermal;
            }

            if (mc.player != null && tick % 10 == 0) {
               SpikeNlosNetwork.CHANNEL.sendToServer(new SpikeNlosNetwork.Viewer(missile.getId(), (Integer)mc.options.renderDistance().get(), true));
            }

            if (mc.player != null && mc.screen == null && tick % 3 == 0) {
               int throttle = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
               SpikeNlosNetwork.CHANNEL.sendToServer(new SpikeNlosNetwork.Control(missile.getId(), throttle, false, 0.0, 0.0, 0.0, -1));
            }
         } else {
            while (THERMAL.consumeClick()) {
            }

            if (previous >= 0 && mc.getConnection() != null) {
               SpikeNlosNetwork.CHANNEL.sendToServer(new SpikeNlosNetwork.Viewer(previous, 0, false));
            }

            previous = -1;
         }
      }
   }

   public static void mouse(Pre e) {
      Minecraft mc = Minecraft.getInstance();
      GuidedMissileEntity missile = ClientCamera.spike();
      if (missile != null && mc.screen == null && e.getButton() == 0) {
         e.setCanceled(true);
         if (e.getAction() == 1 && mc.level != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 from = camera.getPosition();
            Vec3 direction = aimDirection(camera);
            Vec3 to = from.add(direction.scale(1500.0));
            BlockHitResult hit = mc.level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.NONE, mc.player));
            Vec3 target = hit.getLocation();
            int entityId = -1;
            double closest = from.distanceToSqr(target);

            for (Entity candidate : mc.level
               .getEntities(mc.player, new AABB(from, target).inflate(3.0), x -> x.isPickable() && x != missile && x != mc.player.getVehicle())) {
               Optional<Vec3> intersection = candidate.getBoundingBox().inflate(0.15).clip(from, target);
               if (intersection.isPresent() && from.distanceToSqr(intersection.get()) < closest) {
                  target = intersection.get();
                  closest = from.distanceToSqr(target);
                  entityId = candidate.getId();
               }
            }

            SpikeNlosNetwork.CHANNEL
               .sendToServer(new SpikeNlosNetwork.Control(missile.getId(), 0, true, target.x, target.y, target.z, entityId));
         }
      }
   }

   public static void render(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre event) {
      Minecraft mc = Minecraft.getInstance();
      GuidedMissileEntity missile = ClientCamera.spike();
      if (missile != null && mc.screen == null) {
         event.setCanceled(true);
         GuiGraphics g = event.getGuiGraphics();
         UiViewport.begin(g);
         int w = UiViewport.width();
         int h = UiViewport.height();
         if (thermal) {
            drawThermal(g, w, h);
         }

         int cx = w / 2;
         int cy = h / 2;
         int margin = Math.max(12, w / 50);
         int base = h - 109;
         FlightInstruments.card(g, margin, margin, 176, 43);
         FlightInstruments.type(g, "SPIKE NLOS", margin + 12, margin + 8, -1182997);
         FlightInstruments.type(g, FlightInstruments.tr("command"), margin + 12, margin + 25, -5196890);
         FlightInstruments.heading(g, cx, margin + 15, Math.min(270, w / 3), missile.getYRot());
         FlightInstruments.card(g, w - margin - 94, margin, 94, 43);
         FlightInstruments.type(g, thermal ? "IR / WHITE HOT" : "TV / DAY", w - margin - 82, margin + 9, -1182997);
         FlightInstruments.type(g, "N · TV / IR", w - margin - 82, margin + 25, -5196890);
         FlightInstruments.horizon(g, cx, cy, missile.getXRot(), Math.min(270, w / 2));
         int aimX = (int)(cx + cursorX * w / 2.0);
         int aimY = (int)(cy + cursorY * h / 2.0);
         int half = 22;
         g.renderOutline(aimX - half - 1, aimY - half - 1, half * 2 + 2, half * 2 + 2, -871361514);
         g.renderOutline(aimX - half, aimY - half, half * 2, half * 2, -1842985);
         g.fill(aimX - 1, aimY - 1, aimX + 2, aimY + 2, -1842985);
         int cardW = Math.max(90, Math.min(126, w / 5));
         FlightInstruments.metric(g, margin, base, cardW, FlightInstruments.tr("altitude"), String.format(Locale.ROOT, "%.0f", missile.getY()), "m");
         FlightInstruments.metric(
            g,
            margin + cardW + 7,
            base,
            cardW,
            FlightInstruments.tr("speed"),
            String.format(Locale.ROOT, "%.0f", FlightUnits.kilometresPerHour(missile.getDeltaMovement().length()) * 1.5),
            "km/h"
         );
         FlightInstruments.card(g, w - margin - 132, base, 132, 48);
         FlightInstruments.type(g, FlightInstruments.tr("control"), w - margin - 120, base + 8, -5196890);
         FlightInstruments.type(g, "W / S   ·   " + FlightInstruments.tr("speed"), w - margin - 120, base + 28, -1182997);
         FlightInstruments.footer(g, FlightUi.localized("ui.deepstrike.spike_tv_camera.fac5c7c066"), w, h);
         UiViewport.end(g);
      }
   }

   private static void drawThermal(GuiGraphics g, int w, int h) {
      ShaderInstance shader = PatriotThermalCamera.thermalShader();
      if (shader != null) {
         g.flush();
         Minecraft mc = Minecraft.getInstance();
         RenderTarget source = mc.getMainRenderTarget();
         if (frame == null) {
            frame = new TextureTarget(source.width, source.height, false, Minecraft.ON_OSX);
         }

         if (frame.width != source.width || frame.height != source.height) {
            frame.resize(source.width, source.height, Minecraft.ON_OSX);
         }

         GL30.glBindFramebuffer(36008, source.frameBufferId);
         GL30.glBindFramebuffer(36009, frame.frameBufferId);
         GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, frame.width, frame.height, 16384, 9728);
         source.bindWrite(true);
         RenderSystem.setShader(() -> shader);
         RenderSystem.setShaderTexture(0, frame.getColorTextureId());
         RenderSystem.disableDepthTest();
         Matrix4f matrix = g.pose().last().pose();
         BufferBuilder builder = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
         builder.addVertex(matrix, 0.0F, h, 0.0F).setUv(0.0F, 0.0F);
         builder.addVertex(matrix, w, h, 0.0F).setUv(1.0F, 0.0F);
         builder.addVertex(matrix, w, 0.0F, 0.0F).setUv(1.0F, 1.0F);
         builder.addVertex(matrix, 0.0F, 0.0F, 0.0F).setUv(0.0F, 1.0F);
         dev.sbw.deepstrike.compat.RenderCompat.draw(builder);
         RenderSystem.enableDepthTest();
      }
   }

   private SpikeTvCamera() {
   }
}
