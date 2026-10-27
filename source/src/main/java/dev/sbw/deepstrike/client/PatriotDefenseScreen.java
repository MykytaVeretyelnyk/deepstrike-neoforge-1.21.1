package dev.sbw.deepstrike.client;

import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;

public final class PatriotDefenseScreen extends Screen implements EquipmentUiTheme {
   private double pointerX;
   private double pointerY;
   private int miniX;
   private int miniY;
   private int miniW;
   private int miniH;
   private static final KeyMapping OPEN = new KeyMapping("key.deepstrike.patriot_console", 88, "key.categories.deepstrike");
   private static volatile PatriotDefenseNetwork.TargetState latest;
   private static long lastSnapshotNanos;
   private final int vehicleId;
   private UUID selectedId;
   private int selected = -1;
   private int targetScroll;
   private int requestTicks = 10;
   private final PatriotThermalCamera thermalCamera = new PatriotThermalCamera();
   private TerrainMap terrain;
   private long nextTerrainRefresh;
   private double mapCenterX;
   private double mapCenterZ;
   private boolean terrainReady;
   private boolean monitorView;
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private float uiScale = 1.0F;
   private float screenX;
   private float screenY;
   private static final int CAMERA_X = 14;
   private static final int CAMERA_Y = 68;
   private static final int CAMERA_W = 644;
   private static final int CAMERA_H = 320;
   private static final int TARGET_X = 672;
   private static final int TARGET_W = 314;
   private static final int CONTENT_H = 538;

   @Override
   public String equipmentSkin() {
      return EquipmentUiTheme.vehicle(this.vehicleId);
   }

   public PatriotDefenseScreen(int vehicleId) {
      super(Component.translatable("screen.deepstrike.patriot.title"));
      this.vehicleId = vehicleId;
      if (latest != null && latest.vehicleId() != vehicleId) {
         latest = null;
      }
   }

   static PatriotDefenseScreen monitor(int vehicleId) {
      PatriotDefenseScreen screen = new PatriotDefenseScreen(vehicleId);
      screen.monitorView = true;
      screen.init(Minecraft.getInstance(), 1016, 636);
      return screen;
   }

   public static void register(RegisterKeyMappingsEvent event) {
      event.register(OPEN);
   }

   public static void clientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         PatriotCabMonitor.tick();

         while (OPEN.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof PatriotTruckEntity truck) {
               mc.setScreen(new PatriotDefenseScreen(truck.getId()));
            } else if (mc.player != null && mc.player.getVehicle() instanceof Su24mEntity plane) {
               Su24mScreen.open(plane.getId());
            } else if (mc.player != null && mc.player.getVehicle() instanceof SpikeNlosEntity spike) {
               mc.setScreen(new SpikeNlosMapScreen(spike.getId()));
            }
         }
      }
   }

   public static void accept(PatriotDefenseNetwork.TargetState state) {
      Minecraft mc = Minecraft.getInstance();
      if (!(mc.screen instanceof PatriotDefenseScreen px)
         && !(mc.screen instanceof AirDefenseMapScreen map && map.console instanceof PatriotDefenseScreen p)) {
         Object var10000 = null;
      }

      if (mc.level != null && mc.player != null && mc.player.getVehicle() instanceof PatriotTruckEntity truck && truck.getId() == state.vehicleId()) {
         latest = state;
         lastSnapshotNanos = System.nanoTime();
      }
   }

   private boolean hasFreshSnapshot() {
      return this.minecraft != null
         && this.minecraft.level != null
         && latest != null
         && latest.vehicleId() == this.vehicleId
         && System.nanoTime() - lastSnapshotNanos <= 3000000000L;
   }

   private int visibleRows() {
      return Math.max(1, 16);
   }

   private void layoutPanel() {
      this.panelX = 0;
      this.panelY = 0;
      this.panelW = 1000;
      this.panelH = 620;
      this.uiScale = Math.max(0.1F, Math.min(1.0F, Math.min((this.width - 16.0F) / this.panelW, (this.height - 16.0F) / this.panelH)));
      this.screenX = (this.width - this.panelW * this.uiScale) / 2.0F;
      this.screenY = (this.height - this.panelH * this.uiScale) / 2.0F;
   }

   protected void init() {
      this.layoutPanel();
   }

   public void removed() {
      if (this.terrain != null) {
         this.terrain.close();
         this.terrain = null;
      }

      this.thermalCamera.close();
      this.terrainReady = false;
      super.removed();
   }

   public boolean keyPressed(int key, int scanCode, int modifiers) {
      if (OPEN.matches(key, scanCode)) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(key, scanCode, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void tick() {
      if (this.minecraft != null
         && this.minecraft.player != null
         && this.minecraft.player.getVehicle() instanceof PatriotTruckEntity truck
         && truck.getId() == this.vehicleId) {
         if (!this.monitorView) {
            this.thermalCamera.track(truck, this.contacts().stream().filter(c -> c.id().equals(this.selectedId)).findFirst().orElse(null));
         }

         if (this.terrain == null) {
            this.terrain = new TerrainMap(256);
         }

         if (this.hasFreshSnapshot() && latest.radar() && (!this.terrainReady || this.minecraft.level.getGameTime() >= this.nextTerrainRefresh)) {
            this.mapCenterX = truck.getX();
            this.mapCenterZ = truck.getZ();
            int diameter = (int)(this.hasFreshSnapshot() ? latest.radarRange() * 2.0 : 9000.0);
            this.terrain.refresh(this.mapCenterX, this.mapCenterZ, diameter, diameter);
            this.terrainReady = true;
            this.nextTerrainRefresh = this.minecraft.level.getGameTime() + 40L;
         }

         if (++this.requestTicks >= 10) {
            PatriotDefenseNetwork.requestScan(this.vehicleId);
            this.requestTicks = 0;
         }
      } else {
         this.onClose();
      }
   }

   private List<PatriotDefenseNetwork.Contact> contacts() {
      PatriotDefenseNetwork.TargetState state = latest;
      return this.hasFreshSnapshot() && state.radar() ? state.contacts() : List.of();
   }

   private PatriotTruckEntity localTruck() {
      return this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getEntity(this.vehicleId) instanceof PatriotTruckEntity truck
         ? truck
         : null;
   }

   private int deployState() {
      if (this.hasFreshSnapshot()) {
         return latest.deployState();
      } else {
         PatriotTruckEntity truck = this.localTruck();
         return truck != null ? truck.getDeployState() : 0;
      }
   }

   private int deployTicks() {
      if (this.hasFreshSnapshot()) {
         return latest.deployTicks();
      } else {
         PatriotTruckEntity truck = this.localTruck();
         return truck != null ? truck.getDeployTicks() : 0;
      }
   }

   private boolean isDeployed() {
      return this.deployState() == 2;
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      if (!this.monitorView) {
         this.thermalCamera.capture(g);
      }

      this.selected = -1;
      List<PatriotDefenseNetwork.Contact> currentContacts = this.contacts();

      for (int i = 0; i < currentContacts.size(); i++) {
         if (currentContacts.get(i).id().equals(this.selectedId)) {
            this.selected = i;
            break;
         }
      }

      g.fill(0, 0, this.width, this.height, -1341782525);
      this.layoutPanel();
      this.pointerX = (mouseX - this.screenX) / this.uiScale;
      this.pointerY = (mouseY - this.screenY) / this.uiScale;
      g.pose().pushPose();
      g.pose().translate(this.screenX, this.screenY, 0.0F);
      g.pose().scale(this.uiScale, this.uiScale, 1.0F);
      FlightUi.panel(g, 0, 0, this.panelW, this.panelH, -182838503, -10854328);
      FlightUi.label(g, "PATRIOT  /  AN-MPQ 65", 20, 15, -1182997);
      FlightUi.label(g, FlightUi.localized("screen.deepstrike.patriot.station"), 20, 34, -5196890);
      this.instrumentControl(g, 635, 15, 110, 28, FlightUi.localized("ui.deepstrike.patriot_defense_screen.10d49f4361"), true, false, FlightUi.Tone.SECONDARY);
      this.instrumentControl(g, 754, 15, 170, 28, FlightUi.localized("ui.deepstrike.patriot_defense_screen.32103b8dbe"), true, false, FlightUi.Tone.SECONDARY);
      this.instrumentControl(g, 938, 15, 44, 28, "×", true, false, FlightUi.Tone.SECONDARY);
      this.instrumentControl(g, 498, 15, 126, 28, AirDefenseMapScreen.text("title").getString(), true, false, FlightUi.Tone.PRIMARY);
      g.fill(20, 55, 980, 56, -10854328);
      PatriotDefenseNetwork.TargetState state = latest;
      boolean connected = this.hasFreshSnapshot() && state.radar();
      if (connected) {
         double range = latest.radarRange();
         if (this.monitorView) {
            this.drawRadar(g, 14, 68, 644, 538, "AN-MPQ 65", range, partial);
         } else {
            this.drawThermal(g, 14, 68, 644, 320);
            this.drawRadar(
               g,
               14,
               402,
               644,
               204,
               range > 4500.0
                  ? FlightUi.localized("ui.deepstrike.patriot_defense_screen.255639b8da")
                  : FlightUi.localized("ui.deepstrike.patriot_defense_screen.cc6918eec1"),
               range,
               partial
            );
         }
      } else {
         this.drawDisconnected(g, 14, 68, 644, 538, state);
      }

      this.drawTargets(g, 672, 68, 314, 538);
      g.pose().popPose();
      super.render(g, mouseX, mouseY, partial);
   }

   private void drawDisconnected(GuiGraphics g, int px, int py, int pw, int ph, PatriotDefenseNetwork.TargetState state) {
      int cx = px + pw / 2;
      int cy = py + ph / 2;
      g.fill(cx - 180, cy - 65, cx + 180, cy + 65, -267448564);
      this.drawRectBorder(g, cx - 180, cy - 65, 360, 130, -12895432);
      this.centered(g, FlightUi.localized("ui.deepstrike.patriot_defense_screen.3ca12d83bc"), cx, cy - 42, -4355200);
      this.centered(g, FlightUi.localized("ui.deepstrike.patriot_defense_screen.43740baa7b"), cx, cy - 18, -4079170);
      this.centered(g, FlightUi.localized("ui.deepstrike.patriot_defense_screen.45c421bc18"), cx, cy - 2, -4079170);
      this.centered(g, FlightUi.localized("ui.deepstrike.patriot_defense_screen.7df9677863"), cx, cy + 14, -8222104);
      if (this.hasFreshSnapshot()) {
         this.centered(
            g, FlightUi.localized("ui.deepstrike.patriot_defense_screen.4de29d192a") + state.pac2() + "   ·   PAC-3 — " + state.pac3(), cx, cy + 38, -6575733
         );
      }
   }

   private void drawRadar(GuiGraphics g, int x, int y, int w, int h, String label, double range, float partial) {
      g.fill(x, y, x + w, y + h, -14408671);
      this.drawRectBorder(g, x, y, w, h, -15324645);
      g.fill(x + 1, y + 1, x + w - 1, y + 20, -13816534);
      g.fill(x + 8, y + 8, x + 12, y + 12, -6575733);
      g.drawString(this.font, label, x + 16, y + 6, -6575733, false);
      int half = (h - 32) / 2;
      int r = Math.min(76, (h - 42) / 2);
      int cx = x + 102;
      int cy = y + 26 + r;
      this.fillCircle(g, cx, cy, r, -16316668);
      this.drawCircle(g, cx, cy, r, -6575733);
      this.drawCircle(g, cx, cy, r * 2 / 3, -6575733);
      this.drawCircle(g, cx, cy, r / 3, -15977705);
      g.fill(cx - 1, cy - r, cx + 1, cy + r, 899393931);
      g.fill(cx - r, cy - 1, cx + r, cy + 1, 899393931);
      if (this.hasFreshSnapshot() && latest.radar() && this.minecraft != null && this.minecraft.level != null) {
         float sweep = (float)(((float)this.minecraft.level.getGameTime() + partial) * 0.06283185307179587);

         for (int trail = 8; trail >= 0; trail--) {
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0.0F);
            g.pose().mulPose(Axis.ZP.rotation(sweep - trail * 0.035F));
            int alpha = Math.min(255, 20 + (8 - trail) * 29);
            g.fill(0, -1, r, 2, alpha << 24 | 10201483);
            g.pose().popPose();
         }
      }

      g.fill(cx - 2, cy - 2, cx + 3, cy + 3, -6575733);
      this.drawContactDots(g, cx, cy, r, range);
      int mapLabelY = y + 6;
      g.fill(x + 208, mapLabelY - 2, x + w - 1, mapLabelY + 16, -13816534);
      g.fill(x + 215, mapLabelY + 4, x + 219, mapLabelY + 8, -6575733);
      g.drawString(
         this.font,
         FlightUi.localized("ui.deepstrike.patriot_defense_screen.24b391c6c1")
            + String.format(Locale.ROOT, "%.1f", range / 1000.0)
            + FlightUi.localized("ui.deepstrike.patriot_defense_screen.09daf86a79"),
         x + 226,
         mapLabelY + 2,
         -6575733,
         false
      );
      int mapW = w - 224;
      int mapH = h - 40;
      int mapX = x + 216;
      int mapY = y + 28;
      this.miniX = mapX;
      this.miniY = mapY;
      this.miniW = mapW;
      this.miniH = mapH;
      g.fill(mapX, mapY, mapX + mapW, mapY + mapH, -16382461);
      this.drawRectBorder(g, mapX, mapY, mapW, mapH, -15189984);
      this.drawCornerBrackets(g, mapX, mapY, mapW, mapH, -6575733);
      if (this.terrain != null && this.terrainReady) {
         this.terrain.render(g, mapX + 1, mapY + 1, mapW - 2, mapH - 2);
      }

      this.instrumentControl(
         g, mapX + mapW - 96, mapY + 3, 92, 18, FlightUi.localized("screen.deepstrike.map.fullscreen"), true, false, FlightUi.Tone.SECONDARY
      );
      int mapCx = mapX + mapW / 2;
      int mapCy = mapY + mapH / 2;
      g.fill(mapCx, mapY + 1, mapCx + 1, mapY + mapH - 1, 630958475);
      g.fill(mapX + 1, mapCy, mapX + mapW - 1, mapCy + 1, 630958475);
      g.drawString(this.font, "N", mapCx - 2, mapY + 3, -6575733, false);
      PatriotTruckEntity truck = this.localTruck();
      if (truck != null) {
         int tx = mapCx + (int)((truck.getX() - this.mapCenterX) / range * (mapW / 2.0));
         int tz = mapCy + (int)((truck.getZ() - this.mapCenterZ) / range * (mapH / 2.0));
         if (tx >= mapX + 2 && tx <= mapX + mapW - 3 && tz >= mapY + 2 && tz <= mapY + mapH - 3) {
            g.fill(tx - 2, tz - 2, tx + 3, tz + 3, -6575733);
            this.drawRectBorder(g, tx - 4, tz - 4, 9, 9, -6575733);
         }
      }

      for (int i = 0; i < this.contacts().size(); i++) {
         PatriotDefenseNetwork.Contact c = this.contacts().get(i);
         if (truck != null && !(Math.hypot(c.x() - truck.getX(), c.z() - truck.getZ()) > range)) {
            int px = mapCx + (int)((c.x() - this.mapCenterX) / range * (mapW / 2.0));
            int py = mapCy + (int)((c.z() - this.mapCenterZ) / range * (mapH / 2.0));
            if (px >= mapX + 2 && px < mapX + mapW - 2 && py >= mapY + 2 && py < mapY + mapH - 2) {
               int col = i == this.selected
                  ? -4413569
                  : (c.friendly() ? -6575733 : (c.impactAssessment() < 0 ? -7829368 : (c.impactAssessment() == 1 ? -4355200 : -6575733)));
               boolean dirKnown = c.friendly() || c.impactAssessment() >= 0;
               RadarContactRenderer.drawContact(g, this.font, px, py, c.kind(), dirKnown, col, i == this.selected, 14);
            }
         }
      }
   }

   private void drawContactDots(GuiGraphics g, int cx, int cy, int r, double range) {
      PatriotTruckEntity truck = this.localTruck();
      if (truck != null) {
         for (int i = 0; i < this.contacts().size(); i++) {
            PatriotDefenseNetwork.Contact c = this.contacts().get(i);
            double dx = c.x() - truck.getX();
            double dz = c.z() - truck.getZ();
            double dist = Math.hypot(dx, dz);
            if (!(dist > range)) {
               int px = cx + (int)(dx / range * r);
               int py = cy + (int)(dz / range * r);
               int color = i == this.selected
                  ? -4413569
                  : (c.friendly() ? -6575733 : (c.impactAssessment() < 0 ? -7829368 : (c.impactAssessment() == 1 ? -4355200 : -6575733)));
               boolean dirKnown = c.friendly() || c.impactAssessment() >= 0;
               RadarContactRenderer.drawContact(g, this.font, px, py, c.kind(), dirKnown, color, i == this.selected, 10);
            }
         }
      }
   }

   private void drawThermal(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, -14408671);
      this.drawRectBorder(g, x, y, w, h, -15324645);
      g.fill(x + 1, y + 1, x + w - 1, y + 20, -13816534);
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.patriot_defense_screen.ce846bc9a4"), x + 6, y + 6, -6050431, false);
      PatriotThermalCamera.CameraMode curMode = this.thermalCamera.getMode();
      int btnY = y + 3;
      int btnH = 14;
      int optW = 42;
      int pnvW = 30;
      int thmW = 44;
      int thmX = x + w - 6 - thmW;
      int pnvX = thmX - 4 - pnvW;
      int optX = pnvX - 4 - optW;
      this.drawModeBtn(
         g, optX, btnY, optW, btnH, FlightUi.localized("ui.deepstrike.patriot_defense_screen.2b3ea28c54"), curMode == PatriotThermalCamera.CameraMode.NORMAL
      );
      this.drawModeBtn(
         g,
         pnvX,
         btnY,
         pnvW,
         btnH,
         FlightUi.localized("ui.deepstrike.patriot_defense_screen.1b2d727d3a"),
         curMode == PatriotThermalCamera.CameraMode.NIGHT_VISION
      );
      this.drawModeBtn(
         g, thmX, btnY, thmW, btnH, FlightUi.localized("ui.deepstrike.patriot_defense_screen.43932a4b67"), curMode == PatriotThermalCamera.CameraMode.THERMAL
      );
      int vx = x + 6;
      int vy = y + 24;
      int vw = w - 12;
      int vh = h - 90;
      g.fill(vx, vy, vx + vw, vy + vh, -16382461);
      this.drawRectBorder(g, vx, vy, vw, vh, -15058910);
      int cx = vx + vw / 2;
      int cy = vy + vh / 2;
      if (this.selected >= 0 && this.selected < this.contacts().size()) {
         this.thermalCamera.render(g, vx, vy, vw, vh);
         PatriotDefenseNetwork.Contact contact = this.contacts().get(this.selected);
         Entity ent = RemoteFlights.get(contact.entityId());
         if (ent == null && this.minecraft != null && this.minecraft.level != null) {
            ent = this.minecraft.level.getEntity(contact.entityId());
         }

         boolean falling = ent instanceof GuidedMissileEntity gm && gm.isIntercepted();
         int reticleColor = falling ? -4355200 : (curMode == PatriotThermalCamera.CameraMode.THERMAL ? -4413569 : -6575733);
         g.fill(cx - 26, cy - 26, cx - 24, cy + 27, reticleColor);
         g.fill(cx + 24, cy - 26, cx + 26, cy + 27, reticleColor);
         g.fill(cx - 26, cy - 26, cx + 27, cy - 24, reticleColor);
         g.fill(cx - 26, cy + 25, cx + 27, cy + 27, reticleColor);
         if (falling) {
            boolean blink = this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getGameTime() / 6L % 2L == 0L;
            if (blink) {
               g.fill(vx + 4, vy + 4, vx + vw - 4, vy + 18, -574780544);
               this.centered(g, FlightUi.localized("ui.deepstrike.patriot_defense_screen.12935964bc"), cx, vy + 6, -1);
            }
         }

         g.drawString(
            this.font,
            FlightUi.localized("ui.deepstrike.patriot_defense_screen.21a91e657a")
               + Math.round(contact.altitude())
               + FlightUi.localized("ui.deepstrike.patriot_defense_screen.9dede9db56"),
            x + 8,
            vy + vh + 10,
            -1776415,
            false
         );
         g.drawString(
            this.font,
            FlightUi.localized("ui.deepstrike.patriot_defense_screen.7bdfa2d434")
               + Math.round(contact.speedKmh())
               + FlightUi.localized("ui.deepstrike.patriot_defense_screen.2370f7c88f"),
            x + 8,
            vy + vh + 24,
            -1776415,
            false
         );
         if (falling) {
            g.drawString(this.font, FlightUi.localized("ui.deepstrike.patriot_defense_screen.b4a21cadad"), x + 8, vy + vh + 38, -4355200, false);
         }
      } else {
         g.fill(cx - 10, cy, cx + 11, cy + 1, 1716606529);
         g.fill(cx, cy - 10, cx + 1, cy + 11, 1716606529);
         this.centered(g, FlightUi.localized("ui.deepstrike.patriot_defense_screen.88d327e2e5"), cx, cy + 16, -9670058);
      }

      int zoomY = vy + 4;
      int zoomInX = vx + vw - 18;
      int zoomOutX = zoomInX - 18;
      this.drawButton(g, zoomOutX, zoomY, 16, 14, "-", -15329773, -14399442, -1);
      this.drawButton(g, zoomInX, zoomY, 16, 14, "+", -15329773, -14399442, -1);
      String zoomStr = String.format(Locale.US, FlightUi.localized("hud.deepstrike.camera.zoom"), this.thermalCamera.getZoom());
      int zw = this.font.width(zoomStr);
      g.fill(zoomOutX - zw - 8, zoomY, zoomOutX - 2, zoomY + 14, -871823098);
      this.drawRectBorder(g, zoomOutX - zw - 8, zoomY, zw + 6, 14, -14992092);
      g.drawString(this.font, zoomStr, zoomOutX - zw - 5, zoomY + 3, -6575733, false);
      this.drawCornerBrackets(g, vx, vy, vw, vh, -6575733);
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.patriot_defense_screen.1187d89625") + curMode.title, x + 8, y + h - 14, -10591157, false);
   }

   private void drawTargets(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, -14079706);
      this.drawRectBorder(g, x, y, w, h, -14671843);
      g.fill(x + 1, y + 1, x + w - 1, y + 20, -15395566);
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.patriot_defense_screen.24314783d9"), x + 6, y + 6, -6050431, false);
      int deployBoxY = y + 24;
      g.fill(x + 4, deployBoxY, x + w - 4, deployBoxY + 46, -15658738);
      this.drawRectBorder(g, x + 4, deployBoxY, w - 8, 46, -14211292);
      int dState = this.deployState();
      int dTicks = this.deployTicks();
      String statusText;
      int statusColor;
      String buttonText;
      int buttonBg;
      int buttonBorder;
      boolean buttonEnabled;
      if (dState == 0) {
         statusText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.15f568e0ba");
         statusColor = -4079170;
         buttonText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.302d237c54");
         buttonBg = -14671843;
         buttonBorder = -14006216;
         buttonEnabled = true;
      } else if (dState == 1) {
         int pct = Math.min(100, dTicks * 100 / 160);
         if (dTicks < 80) {
            statusText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.03b524b397") + pct + "%)";
         } else {
            statusText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.82870c844f") + pct + "%)";
         }

         statusColor = -4355200;
         buttonText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.c5ead75012") + pct + "%";
         buttonBg = -14673392;
         buttonBorder = -11912674;
         buttonEnabled = false;
      } else if (dState == 2) {
         statusText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.fab060657b");
         statusColor = -6575733;
         buttonText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.8847fdf9f8");
         buttonBg = -14543340;
         buttonBorder = -11918304;
         buttonEnabled = true;
      } else {
         int pct = Math.min(100, dTicks * 100 / 160);
         if (dTicks < 80) {
            statusText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.e5e576e0cd") + pct + "%)";
         } else {
            statusText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.db8f7462fe") + pct + "%)";
         }

         statusColor = -4355200;
         buttonText = FlightUi.localized("ui.deepstrike.patriot_defense_screen.0731e5da1f") + pct + "%";
         buttonBg = -14673392;
         buttonBorder = -11912674;
         buttonEnabled = false;
      }

      g.drawString(this.font, statusText, x + 8, deployBoxY + 5, statusColor, false);
      this.drawButton(g, x + 7, deployBoxY + 18, w - 14, 22, buttonText, buttonBg, buttonBorder, buttonEnabled ? -1 : -7237234);
      int listHeaderY = deployBoxY + 52;
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.patriot_defense_screen.040772941f"), x + 6, listHeaderY, -7563920, false);
      List<PatriotDefenseNetwork.Contact> cs = this.contacts();
      int rowY = listHeaderY + 12;
      int rows = this.visibleRows();
      this.targetScroll = Math.max(0, Math.min(this.targetScroll, Math.max(0, cs.size() - rows)));

      for (int row = 0; row < Math.min(rows, cs.size() - this.targetScroll); row++) {
         int i = this.targetScroll + row;
         PatriotDefenseNetwork.Contact c = cs.get(i);
         int ry = rowY + row * 22;
         if (this.localTruck() != null && this.localTruck().position().distanceTo(new Vec3(c.x(), c.y(), c.z())) <= 3000.0) {
            boolean var57 = true;
         } else {
            boolean var10000 = false;
         }

         int rowBg = i == this.selected ? -14473956 : (i % 2 == 0 ? -15527152 : -15790324);
         g.fill(x + 4, ry, x + w - 4, ry + 20, rowBg);
         if (i == this.selected) {
            this.drawRectBorder(g, x + 4, ry, w - 8, 20, -11315134);
         }

         g.fill(
            x + 8, ry + 7, x + 14, ry + 13, c.friendly() ? -6575733 : (c.impactAssessment() < 0 ? -7829368 : (c.impactAssessment() == 1 ? -4355200 : -6575733))
         );
         String label = AirDefenseTargets.label(c.kind())
            + " "
            + (c.friendly() ? FlightUi.localized("ui.deepstrike.patriot_defense_screen.7b635dba1e") : "")
            + " #"
            + (i + 1)
            + "  ·  "
            + Math.round(c.speedKmh())
            + FlightUi.localized("ui.deepstrike.patriot_defense_screen.ea49235b0a");
         g.drawString(this.font, label, x + 20, ry + 2, -1776415, false);
         g.drawString(
            this.font,
            FlightUi.localized(
               c.impactAssessment() < 0
                  ? "radar.deepstrike.unknown"
                  : (c.impactAssessment() == 1 ? "radar.deepstrike.impact_inside" : "radar.deepstrike.impact_outside")
            ),
            x + 20,
            ry + 11,
            c.impactAssessment() < 0 ? -7829368 : (c.impactAssessment() == 1 ? -4355200 : -6575733),
            false
         );
      }

      if (cs.isEmpty()) {
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.patriot_defense_screen.fdfa9bbcfd"), x + 8, rowY + 6, -10591157, false);
      }

      PatriotDefenseNetwork.TargetState state = this.hasFreshSnapshot() ? latest : null;
      int p2 = state == null ? 0 : state.pac2();
      int p3 = state == null ? 0 : state.pac3();
      int r2 = state == null ? 0 : state.readyPac2();
      int r3 = state == null ? 0 : state.readyPac3();
      int reloadTicks = state == null ? 0 : state.reloadTicks();
      boolean reloading = reloadTicks > 0;
      float reloadPct = Math.min(1.0F, reloadTicks / 800.0F);
      int ammoY = y + h - (reloading ? 86 : 74);
      g.fill(x + 5, ammoY - 2, x + w - 5, ammoY + (reloading ? 34 : 22), -15724531);
      this.drawRectBorder(g, x + 5, ammoY - 2, w - 10, reloading ? 36 : 24, -14606050);
      String readyStr = FlightUi.localized("ui.deepstrike.patriot_defense_screen.936d438c18") + (r2 + r3) + "/4  (PAC-2: " + r2 + " · PAC-3: " + r3 + ")";
      g.drawString(this.font, readyStr, x + 8, ammoY + 1, -1776415, false);
      String reserveStr = FlightUi.localized("ui.deepstrike.patriot_defense_screen.62a21e6850")
         + p2
         + FlightUi.localized("ui.deepstrike.patriot_defense_screen.0797bb368e")
         + p3
         + FlightUi.localized("ui.deepstrike.patriot_defense_screen.589dfeb5ef");
      g.drawString(this.font, reserveStr, x + 8, ammoY + 11, -7366542, false);
      if (reloading) {
         int barW = w - 18;
         int fillW = (int)(barW * reloadPct);
         float secLeft = Math.max(0.0F, (800 - reloadTicks) / 20.0F);
         g.fill(x + 9, ammoY + 22, x + 9 + barW, ammoY + 27, -15132394);
         g.fill(x + 9, ammoY + 22, x + 9 + fillW, ammoY + 27, -4355200);
         this.drawRectBorder(g, x + 9, ammoY + 22, barW, 5, -11912674);
         g.drawString(
            this.font,
            FlightUi.localized("ui.deepstrike.patriot_defense_screen.cbd48e9d94")
               + Math.round(reloadPct * 100.0F)
               + "% ("
               + String.format(Locale.US, "%.1f", secLeft)
               + FlightUi.localized("ui.deepstrike.patriot_defense_screen.7b76341021"),
            x + 9,
            ammoY + 29,
            -4355200,
            false
         );
      } else if (r2 + r3 < 4 && p2 + p3 > 0) {
         int btnRw = 84;
         int btnRh = 16;
         int btnRx = x + w - 7 - btnRw;
         int btnRy = ammoY + 3;
         this.drawButton(g, btnRx, btnRy, btnRw, btnRh, FlightUi.localized("ui.deepstrike.patriot_defense_screen.c381bd5600"), -14671334, -12499661, -1776415);
      }

      int buttonY = y + h - 48;
      boolean deployed = this.isDeployed();
      boolean pac2Ready = this.canFire(0);
      boolean pac3Ready = this.canFire(1);
      String pac2Label;
      if (!deployed) {
         pac2Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.bbc5ec335c");
      } else if (r2 > 0) {
         pac2Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.267356087d")
            + r2
            + FlightUi.localized("ui.deepstrike.patriot_defense_screen.2ddfff6acd")
            + p2
            + ")";
      } else if (reloading) {
         pac2Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.c9ee969762") + Math.round(reloadPct * 100.0F) + "%]";
      } else {
         pac2Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.7b42bd0b0d") + p2 + ")";
      }

      int pac2Bg = pac2Ready ? -14017514 : -15592945;
      int pac2Border = pac2Ready ? -4355200 : -14474464;
      int pac2Text = pac2Ready ? -7984 : -10790056;
      this.drawButton(g, x + 5, buttonY, w - 10, 22, pac2Label, pac2Bg, pac2Border, pac2Text);
      String pac3Label;
      if (!deployed) {
         pac3Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.1221aabadb");
      } else if (r3 > 0) {
         pac3Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.d20be7229d")
            + r3
            + FlightUi.localized("ui.deepstrike.patriot_defense_screen.2ddfff6acd")
            + p3
            + ")";
      } else if (reloading) {
         pac3Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.841052741c") + Math.round(reloadPct * 100.0F) + "%]";
      } else {
         pac3Label = FlightUi.localized("ui.deepstrike.patriot_defense_screen.e37801d243") + p3 + ")";
      }

      int pac3Bg = pac3Ready ? -14473956 : -15592945;
      int pac3Border = pac3Ready ? -13279163 : -14474464;
      int pac3Text = pac3Ready ? -3080224 : -10790056;
      this.drawButton(g, x + 5, buttonY + 24, w - 10, 22, pac3Label, pac3Bg, pac3Border, pac3Text);
   }

   private void drawButton(GuiGraphics g, int x, int y, int w, int h, String text, int bg, int border, int textColor) {
      boolean enabled = textColor != -10790056 && textColor != -7237234;
      FlightUi.Tone tone = text.contains("PAC-") ? FlightUi.Tone.LAUNCH : FlightUi.Tone.SECONDARY;
      this.instrumentControl(g, x, y, w, h, text, enabled, false, tone);
   }

   private void instrumentControl(GuiGraphics g, int x, int y, int w, int h, String caption, boolean enabled, boolean selected, FlightUi.Tone tone) {
      boolean hover = this.pointerX >= x && this.pointerX < x + w && this.pointerY >= y && this.pointerY < y + h;
      FlightUi.control(g, x, y, w, h, caption, hover, enabled, selected, hover && this.minecraft.mouseHandler.isLeftPressed(), tone);
   }

   private void drawRectBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
      g.fill(x, y, x + w, y + 1, color);
      g.fill(x, y + h - 1, x + w, y + h, color);
      g.fill(x, y, x + 1, y + h, color);
      g.fill(x + w - 1, y, x + w, y + h, color);
   }

   private void drawCircle(GuiGraphics g, int cx, int cy, int r, int color) {
      for (int a = 0; a < 360; a++) {
         double q = Math.toRadians(a);
         int px = cx + (int)(Math.cos(q) * r);
         int py = cy + (int)(Math.sin(q) * r);
         g.fill(px, py, px + 1, py + 1, color);
      }
   }

   private void fillCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
      for (int dy = -radius; dy <= radius; dy++) {
         int inside = (int)Math.sqrt(radius * radius - dy * dy);
         g.fill(cx - inside, cy + dy, cx + inside + 1, cy + dy + 1, color);
      }
   }

   private void drawModeBtn(GuiGraphics g, int x, int y, int w, int h, String text, boolean active) {
      this.instrumentControl(g, x, y, w, h, text, true, active, active ? FlightUi.Tone.NAVIGATION : FlightUi.Tone.SECONDARY);
   }

   private void drawCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int color) {
      int len = 6;
      g.fill(x, y, x + len, y + 1, color);
      g.fill(x, y, x + 1, y + len, color);
      g.fill(x + w - len, y, x + w, y + 1, color);
      g.fill(x + w - 1, y, x + w, y + len, color);
      g.fill(x, y + h - 1, x + len, y + h, color);
      g.fill(x, y + h - len, x + 1, y + h, color);
      g.fill(x + w - len, y + h - 1, x + w, y + h, color);
      g.fill(x + w - 1, y + h - len, x + w, y + h, color);
   }

   private void centered(GuiGraphics g, String s, int x, int y, int color) {
      FlightUi.centered(g, s, x, y, color);
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return super.mouseClicked(mx, my, button);
      } else {
         mx = (mx - this.screenX) / this.uiScale;
         my = (my - this.screenY) / this.uiScale;
         if (mx >= this.miniX && mx < this.miniX + this.miniW && my >= this.miniY && my < this.miniY + this.miniH) {
            this.minecraft.setScreen(new AirDefenseMapScreen(this, this.vehicleId));
            return true;
         } else if (mx >= 498.0 && mx < 624.0 && my >= 15.0 && my < 43.0) {
            this.minecraft.setScreen(new AirDefenseMapScreen(this, this.vehicleId));
            return true;
         } else if (mx >= 635.0 && mx < 745.0 && my >= 15.0 && my < 43.0) {
            UnitJournalScreen.open(this.vehicleId);
            return true;
         } else if (mx >= 754.0 && mx < 924.0 && my >= 15.0 && my < 43.0) {
            FrequencySettingsScreen.defense(this, this.vehicleId, this.hasFreshSnapshot() ? latest.friendlyFrequencies() : List.of());
            return true;
         } else {
            int colW = 644;
            int midX = 14;
            int rightX = 672;
            int contentY = 68;
            int contentH = 538;
            int rightW = 314;
            if (mx >= 938.0 && mx <= 982.0 && my >= 15.0 && my <= 43.0) {
               this.onClose();
               return true;
            } else {
               int modeBtnY = contentY + 3;
               int modeBtnH = 14;
               int optW = 42;
               int pnvW = 30;
               int thmW = 44;
               int thmX = midX + colW - 6 - thmW;
               int pnvX = thmX - 4 - pnvW;
               int optX = pnvX - 4 - optW;
               if (my >= modeBtnY && my <= modeBtnY + modeBtnH) {
                  if (mx >= optX && mx <= optX + optW) {
                     this.thermalCamera.setMode(PatriotThermalCamera.CameraMode.NORMAL);
                     return true;
                  }

                  if (mx >= pnvX && mx <= pnvX + pnvW) {
                     this.thermalCamera.setMode(PatriotThermalCamera.CameraMode.NIGHT_VISION);
                     return true;
                  }

                  if (mx >= thmX && mx <= thmX + thmW) {
                     this.thermalCamera.setMode(PatriotThermalCamera.CameraMode.THERMAL);
                     return true;
                  }
               }

               int vx = midX + 6;
               int vy = contentY + 24;
               int vw = colW - 12;
               int zoomY = vy + 4;
               int zoomInX = vx + vw - 18;
               int zoomOutX = zoomInX - 18;
               if (my >= zoomY && my <= zoomY + 14) {
                  if (mx >= zoomOutX && mx <= zoomOutX + 16) {
                     this.thermalCamera.zoomOut();
                     return true;
                  }

                  if (mx >= zoomInX && mx <= zoomInX + 16) {
                     this.thermalCamera.zoomIn();
                     return true;
                  }
               }

               int deployBoxY = contentY + 24;
               int btnY = deployBoxY + 18;
               int btnH = 22;
               if (mx >= rightX + 7 && mx <= rightX + rightW - 7 && my >= btnY && my <= btnY + btnH) {
                  int dState = this.deployState();
                  if (dState == 0 || dState == 2) {
                     PatriotDefenseNetwork.toggleDeploy(this.vehicleId);
                     return true;
                  }
               }

               int listHeaderY = deployBoxY + 52;
               int rowY = listHeaderY + 12;
               int rows = this.visibleRows();
               if (mx >= rightX + 4 && mx <= rightX + rightW - 4 && my >= rowY && my < rowY + rows * 22) {
                  int row = this.targetScroll + (int)(my - rowY) / 22;
                  if (row >= 0 && row < this.contacts().size()) {
                     this.selected = row;
                     this.selectedId = this.contacts().get(row).id();
                     return true;
                  }
               }

               PatriotDefenseNetwork.TargetState st = this.hasFreshSnapshot() ? latest : null;
               if (st != null && st.reloadTicks() == 0 && st.readyPac2() + st.readyPac3() < 4 && st.pac2() + st.pac3() > 0) {
                  int ammoY = contentY + contentH - 74;
                  int btnRw = 84;
                  int btnRh = 16;
                  int btnRx = rightX + rightW - 7 - btnRw;
                  int btnRy = ammoY + 3;
                  if (mx >= btnRx && mx <= btnRx + btnRw && my >= btnRy && my <= btnRy + btnRh) {
                     PatriotDefenseNetwork.requestReload(this.vehicleId);
                     return true;
                  }
               }

               int buttonY = contentY + contentH - 48;
               if (mx >= rightX + 5 && mx <= rightX + rightW - 5 && my >= buttonY && my < buttonY + 22) {
                  if (this.canFire(0)) {
                     this.fire(0);
                  }

                  return true;
               } else if (mx >= rightX + 5 && mx <= rightX + rightW - 5 && my >= buttonY + 24 && my < buttonY + 46) {
                  if (this.canFire(1)) {
                     this.fire(1);
                  }

                  return true;
               } else {
                  if (mx < this.panelX || mx > this.panelX + this.panelW || my < this.panelY || my > this.panelY + this.panelH) {
                     this.onClose();
                  }

                  return true;
               }
            }
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      double localX = (mouseX - this.screenX) / this.uiScale;
      double localY = (mouseY - this.screenY) / this.uiScale;
      int colW = 644;
      int midX = 14;
      int rightX = 672;
      int contentY = 68;
      int contentH = 538;
      int vx = midX + 6;
      int vy = contentY + 24;
      int vw = colW - 12;
      int vh = 230;
      if (localX >= vx && localX <= vx + vw && localY >= vy && localY <= vy + vh) {
         this.thermalCamera.adjustZoom((float)delta);
         return true;
      } else if (localX >= rightX && localX <= this.panelX + this.panelW - 10 && localY >= this.panelY + 100 && localY < this.panelY + this.panelH - 60) {
         this.targetScroll = Math.max(0, Math.min(Math.max(0, this.contacts().size() - this.visibleRows()), this.targetScroll - (int)Math.signum(delta)));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, 0.0, delta);
      }
   }

   private PatriotDefenseNetwork.Contact selectedContact() {
      return this.contacts().stream().filter(contact -> contact.id().equals(this.selectedId)).findFirst().orElse(null);
   }

   private void fire(int warhead) {
      PatriotDefenseNetwork.Contact contact = this.selectedContact();
      if (contact != null && this.canFire(warhead)) {
         PatriotDefenseNetwork.fire(contact.id(), warhead);
      }
   }

   private boolean canFire(int warhead) {
      if (!this.isDeployed()) {
         return false;
      } else {
         PatriotDefenseNetwork.TargetState state = latest;
         if (this.hasFreshSnapshot() && state != null && state.radar()) {
            int ready = warhead == 0 ? state.readyPac2() : state.readyPac3();
            return ready > 0 && this.selectedInRange(warhead == 0 ? 1600.0 : 3000.0);
         } else {
            return false;
         }
      }
   }

   private boolean selectedInRange(double maxRange) {
      PatriotTruckEntity truck = this.localTruck();
      PatriotDefenseNetwork.Contact contact = this.selectedContact();
      return truck != null && contact != null && truck.position().distanceToSqr(new Vec3(contact.x(), contact.y(), contact.z())) <= maxRange * maxRange;
   }

   List<AirDefenseMapScreen.Target> mapTargets() {
      return this.contacts()
         .stream()
         .map(c -> new AirDefenseMapScreen.Target(c.id(), c.x(), c.y(), c.z(), c.speedKmh(), c.kind(), c.friendly(), c.impactAssessment()))
         .toList();
   }

   void mapSelect(UUID id) {
      this.selectedId = id;
   }

   double mapRadarRange() {
      return this.hasFreshSnapshot() && latest.radar() ? latest.radarRange() : 0.0;
   }

   boolean mapCanFire(int type) {
      return this.canFire(type);
   }
}
