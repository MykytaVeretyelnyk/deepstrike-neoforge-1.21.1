package dev.sbw.deepstrike.client;

import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.RadarDisclosure;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class IronDomeDefenseScreen extends Screen implements EquipmentUiTheme {
   private double pointerX;
   private double pointerY;
   private static volatile IronDomeNetwork.TargetState latest;
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
   private boolean selectingZone = false;
   private int panelX;
   private int panelY;
   private int panelW;
   private int panelH;
   private float uiScale = 1.0F;
   private float screenX;
   private float screenY;
   private int lastMapX;
   private int lastMapY;
   private int lastMapW;
   private int lastMapH;
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

   public IronDomeDefenseScreen(int vehicleId) {
      super(Component.translatable("screen.deepstrike.iron_dome.title"));
      this.vehicleId = vehicleId;
      this.thermalCamera.setMode(PatriotThermalCamera.CameraMode.THERMAL);
      latest = null;
   }

   public static void accept(IronDomeNetwork.TargetState state) {
      Minecraft mc = Minecraft.getInstance();
      IronDomeDefenseScreen screen = mc.screen instanceof IronDomeDefenseScreen px
         ? px
         : (mc.screen instanceof AirDefenseMapScreen map && map.console instanceof IronDomeDefenseScreen p ? p : null);
      if (screen != null && screen.vehicleId == state.vehicleId() && mc.level != null) {
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

   private IronDomeEntity localIronDome() {
      if (this.minecraft != null && this.minecraft.level != null) {
         return this.minecraft.level.getEntity(this.vehicleId) instanceof IronDomeEntity dome ? dome : null;
      } else {
         return null;
      }
   }

   private double radarX() {
      IronDomeEntity dome = this.localIronDome();
      return this.hasFreshSnapshot() ? latest.x() : (dome == null ? 0.0 : dome.getX());
   }

   private double radarZ() {
      IronDomeEntity dome = this.localIronDome();
      return this.hasFreshSnapshot() ? latest.z() : (dome == null ? 0.0 : dome.getZ());
   }

   private List<IronDomeNetwork.Contact> contacts() {
      return this.hasFreshSnapshot() ? latest.contacts() : List.of();
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

   public void tick() {
      super.tick();
      if (this.minecraft != null && this.minecraft.level != null) {
         long now = this.minecraft.level.getGameTime();
         if (++this.requestTicks >= 10) {
            this.requestTicks = 0;
            IronDomeNetwork.requestScan(this.vehicleId);
         }

         IronDomeEntity dome = this.localIronDome();
         if (dome == null && !this.hasFreshSnapshot()) {
            this.thermalCamera.stopTracking();
         } else {
            this.mapCenterX = this.radarX();
            this.mapCenterZ = this.radarZ();
            IronDomeNetwork.Contact selectedContact = this.contacts().stream().filter(c -> c.id().equals(this.selectedId)).findFirst().orElse(null);
            if (selectedContact != null) {
               this.thermalCamera
                  .track(
                     new Vec3(this.radarX(), (dome != null ? dome.getY() : latest.y()) + 5.0, this.radarZ()),
                     selectedContact.x(),
                     selectedContact.y(),
                     selectedContact.z(),
                     selectedContact.entityId(),
                     selectedContact.id()
                  );
            } else {
               this.thermalCamera.stopTracking();
            }

            if (this.terrain == null) {
               this.terrain = new TerrainMap(256);
            }

            if (now >= this.nextTerrainRefresh && this.terrain != null) {
               this.nextTerrainRefresh = now + 40L;
               this.terrain.refresh(this.mapCenterX, this.mapCenterZ, 16000, 16000);
               this.terrainReady = true;
            }
         }
      }
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.thermalCamera.capture(g);
      this.selected = -1;
      List<IronDomeNetwork.Contact> currentContacts = this.contacts();

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
      FlightUi.label(g, "IRON DOME  /  EL-M 2084", 20, 15, -1182997);
      FlightUi.label(g, FlightUi.localized("screen.deepstrike.iron_dome.title"), 20, 34, -5196890);
      this.instrumentControl(g, 635, 15, 110, 28, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.10d49f4361"), true, false, FlightUi.Tone.SECONDARY);
      this.instrumentControl(g, 754, 15, 170, 28, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.32103b8dbe"), true, false, FlightUi.Tone.SECONDARY);
      this.instrumentControl(g, 938, 15, 44, 28, "×", true, false, FlightUi.Tone.SECONDARY);
      this.instrumentControl(g, 498, 15, 126, 28, AirDefenseMapScreen.text("title").getString(), true, false, FlightUi.Tone.PRIMARY);
      g.fill(20, 55, 980, 56, -10854328);
      this.drawThermal(g, 14, 68, 644, 320);
      this.drawRadar(g, 14, 402, 644, 204, partial);
      this.drawControlsAndTargets(g, 672, 68, 314, 538);
      g.pose().popPose();
      super.render(g, mouseX, mouseY, partial);
   }

   private void drawRadar(GuiGraphics g, int x, int y, int w, int h, float partial) {
      g.fill(x, y, x + w, y + h, -14408671);
      this.drawRectBorder(g, x, y, w, h, -15324645);
      g.fill(x + 1, y + 1, x + w - 1, y + 20, -13816534);
      g.fill(x + 8, y + 8, x + 12, y + 12, -5194868);
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.9ebbbf3ece"), x + 16, y + 6, -6575733, false);
      int half = (h - 32) / 2;
      int r = Math.min(76, (h - 42) / 2);
      int cx = x + 102;
      int cy = y + 26 + r;
      this.fillCircle(g, cx, cy, r, -16316668);
      this.drawCircle(g, cx, cy, r, -6575733);
      this.drawCircle(g, cx, cy, (int)(r * 0.75), -6575733);
      this.drawCircle(g, cx, cy, (int)(r * 0.5625), -5194868);
      this.drawCircle(g, cx, cy, (int)(r * 0.5), -6575733);
      this.drawCircle(g, cx, cy, (int)(r * 0.25), -15977705);
      g.fill(cx - 1, cy - r, cx + 1, cy + r, 899393931);
      g.fill(cx - r, cy - 1, cx + r, cy + 1, 899393931);
      if (this.minecraft != null && this.minecraft.level != null) {
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

      g.fill(cx - 2, cy - 2, cx + 3, cy + 3, -5194868);
      this.drawContactDots(g, cx, cy, r, 8000.0);
      int mapLabelY = y + 6;
      g.fill(x + 208, mapLabelY - 2, x + w - 1, mapLabelY + 16, -13816534);
      g.fill(x + 215, mapLabelY + 4, x + 219, mapLabelY + 8, this.selectingZone ? -4355200 : -5194868);
      g.drawString(
         this.font,
         this.selectingZone
            ? FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.86479a51b8")
            : FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.ed47956e6d"),
         x + 226,
         mapLabelY + 2,
         this.selectingZone ? -4355200 : -6575733,
         false
      );
      int mapW = w - 224;
      int mapH = h - 40;
      int mapX = x + 216;
      int mapY = y + 28;
      this.lastMapX = mapX;
      this.lastMapY = mapY;
      this.lastMapW = mapW;
      this.lastMapH = mapH;
      g.fill(mapX, mapY, mapX + mapW, mapY + mapH, -16382461);
      this.drawRectBorder(g, mapX, mapY, mapW, mapH, this.selectingZone ? -4355200 : -15189984);
      this.drawCornerBrackets(g, mapX, mapY, mapW, mapH, this.selectingZone ? -4355200 : -5194868);
      if (this.terrain != null && this.terrainReady) {
         this.terrain.render(g, mapX + 1, mapY + 1, mapW - 2, mapH - 2);
      }

      int mapCx = mapX + mapW / 2;
      int mapCy = mapY + mapH / 2;
      g.fill(mapCx, mapY + 1, mapCx + 1, mapY + mapH - 1, 630958475);
      g.fill(mapX + 1, mapCy, mapX + mapW - 1, mapCy + 1, 630958475);
      g.drawString(this.font, "N", mapCx - 2, mapY + 3, -6575733, false);
      IronDomeEntity dome = this.localIronDome();
      if (dome != null || this.hasFreshSnapshot()) {
         int tx = mapCx + (int)((this.radarX() - this.mapCenterX) / 8000.0 * (mapW / 2.0));
         int tz = mapCy + (int)((this.radarZ() - this.mapCenterZ) / 8000.0 * (mapH / 2.0));
         if (tx >= mapX + 2 && tx <= mapX + mapW - 3 && tz >= mapY + 2 && tz <= mapY + mapH - 3) {
            g.fill(tx - 2, tz - 2, tx + 3, tz + 3, -5194868);
            this.drawRectBorder(g, tx - 4, tz - 4, 9, 9, -5194868);
         }

         double zx = this.hasFreshSnapshot() ? latest.zoneX() : dome.getZoneX();
         double zz = this.hasFreshSnapshot() ? latest.zoneZ() : dome.getZoneZ();
         double zr = this.hasFreshSnapshot() ? latest.zoneRadius() : dome.getZoneRadius();
         int zpx = mapCx + (int)((zx - this.mapCenterX) / 8000.0 * (mapW / 2.0));
         int zpz = mapCy + (int)((zz - this.mapCenterZ) / 8000.0 * (mapH / 2.0));
         int zpr = Math.max(3, (int)(zr / 8000.0 * (mapW / 2.0)));
         this.drawCircle(g, zpx, zpz, zpr, -6575733);
         this.drawCircle(g, zpx, zpz, Math.max(1, zpr / 2), 1889249675);
         g.fill(zpx - 1, zpz - 1, zpx + 2, zpz + 2, -6575733);
      }

      for (int i = 0; i < this.contacts().size(); i++) {
         IronDomeNetwork.Contact c = this.contacts().get(i);
         int px = mapCx + (int)((c.x() - this.mapCenterX) / 8000.0 * (mapW / 2.0));
         int py = mapCy + (int)((c.z() - this.mapCenterZ) / 8000.0 * (mapH / 2.0));
         int ix = mapCx + (int)((c.impactX() - this.mapCenterX) / 8000.0 * (mapW / 2.0));
         int iy = mapCy + (int)((c.impactZ() - this.mapCenterZ) / 8000.0 * (mapH / 2.0));
         int col = c.friendly() ? -6575733 : (!RadarDisclosure.impactKnown(c.id()) ? -7829368 : (c.threatInZone() ? -4355200 : -6575733));
         if (i == this.selected) {
            col = -4413569;
         }

         if (RadarDisclosure.impactKnown(c.id())) {
            this.drawLine(g, px, py, ix, iy, c.threatInZone() ? -1866626176 : 1354599296);
         }

         if (px >= mapX + 2 && px <= mapX + mapW - 3 && py >= mapY + 2 && py <= mapY + mapH - 3) {
            boolean dirKnown = c.friendly() || RadarDisclosure.impactKnown(c.id());
            RadarContactRenderer.drawContact(g, this.font, px, py, c.kind(), dirKnown, col, i == this.selected, 14);
         }

         if (ix >= mapX + 2 && ix <= mapX + mapW - 3 && iy >= mapY + 2 && iy <= mapY + mapH - 3) {
            g.fill(ix - 1, iy - 1, ix + 2, iy + 2, col);
            g.drawString(this.font, "X", ix - 3, iy - 4, col, false);
         }
      }
   }

   private void drawContactDots(GuiGraphics g, int cx, int cy, int r, double range) {
      if (this.localIronDome() != null || this.hasFreshSnapshot()) {
         for (int i = 0; i < this.contacts().size(); i++) {
            IronDomeNetwork.Contact c = this.contacts().get(i);
            double dx = c.x() - this.radarX();
            double dz = c.z() - this.radarZ();
            double dist = Math.hypot(dx, dz);
            if (!(dist > range)) {
               int px = cx + (int)(dx / range * r);
               int py = cy + (int)(dz / range * r);
               int color = c.friendly() ? -6575733 : (!RadarDisclosure.impactKnown(c.id()) ? -7829368 : (c.threatInZone() ? -4355200 : -6575733));
               if (i == this.selected) {
                  color = -4413569;
               }

               boolean dirKnown = c.friendly() || RadarDisclosure.impactKnown(c.id());
               RadarContactRenderer.drawContact(g, this.font, px, py, c.kind(), dirKnown, color, i == this.selected, 10);
            }
         }
      }
   }

   private void drawThermal(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, -14408671);
      this.drawRectBorder(g, x, y, w, h, -15324645);
      g.fill(x + 1, y + 1, x + w - 1, y + 20, -13816534);
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.ce846bc9a4"), x + 6, y + 6, -6050431, false);
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
         g, optX, btnY, optW, btnH, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.2b3ea28c54"), curMode == PatriotThermalCamera.CameraMode.NORMAL
      );
      this.drawModeBtn(
         g,
         pnvX,
         btnY,
         pnvW,
         btnH,
         FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.1b2d727d3a"),
         curMode == PatriotThermalCamera.CameraMode.NIGHT_VISION
      );
      this.drawModeBtn(
         g, thmX, btnY, thmW, btnH, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.43932a4b67"), curMode == PatriotThermalCamera.CameraMode.THERMAL
      );
      int vx = x + 6;
      int vy = y + 24;
      int vw = w - 12;
      int vh = h - 90;
      g.fill(vx, vy, vx + vw, vy + vh, -16382461);
      this.drawRectBorder(g, vx, vy, vw, vh, -14863068);
      if (this.selected >= 0 && this.selected < this.contacts().size()) {
         this.thermalCamera.render(g, vx, vy, vw, vh);
         int cx = vx + vw / 2;
         int cy = vy + vh / 2;
         int reticleColor = curMode == PatriotThermalCamera.CameraMode.THERMAL ? -4413569 : -6575733;
         g.fill(cx - 26, cy - 26, cx - 24, cy + 27, reticleColor);
         g.fill(cx + 24, cy - 26, cx + 26, cy + 27, reticleColor);
         g.fill(cx - 26, cy - 26, cx + 27, cy - 24, reticleColor);
         g.fill(cx - 26, cy + 25, cx + 27, cy + 27, reticleColor);
         IronDomeNetwork.Contact contact = this.contacts().get(this.selected);
         if (contact.status() == 2) {
            boolean blink = this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getGameTime() / 6L % 2L == 0L;
            if (blink) {
               g.fill(vx + 4, vy + 4, vx + vw - 4, vy + 18, -574780544);
               this.centered(g, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.654687fecb"), cx, vy + 6, -1);
            }
         }
      }

      int zoomY = vy + vh + 6;
      g.fill(vx, zoomY, vx + 18, zoomY + 16, -15198187);
      this.drawRectBorder(g, vx, zoomY, 18, 16, -14007758);
      g.drawString(this.font, "-", vx + 6, zoomY + 4, -1776415, false);
      g.fill(vx + 22, zoomY, vx + 40, zoomY + 16, -15198187);
      this.drawRectBorder(g, vx + 22, zoomY, 18, 16, -14007758);
      g.drawString(this.font, "+", vx + 27, zoomY + 4, -1776415, false);
      String zoomLabel = String.format(Locale.ROOT, FlightUi.localized("hud.deepstrike.camera.zoom"), this.thermalCamera.getZoom());
      g.drawString(this.font, zoomLabel, vx + 46, zoomY + 4, -6575733, false);
   }

   private void drawControlsAndTargets(GuiGraphics g, int x, int y, int w, int h) {
      g.fill(x, y, x + w, y + h, -14408671);
      this.drawRectBorder(g, x, y, w, h, -15324645);
      g.fill(x + 1, y + 1, x + w - 1, y + 20, -13816534);
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.eed25edbac"), x + 8, y + 6, -6050431, false);
      IronDomeEntity dome = this.localIronDome();
      int ready = this.hasFreshSnapshot() ? latest.readyTamir() : (dome != null ? dome.getReadyTamir() : 0);
      int reserve = this.hasFreshSnapshot() ? latest.reserveTamir() : (dome != null ? dome.getReserveTamir() : 0);
      boolean auto = this.hasFreshSnapshot() ? latest.autoDefense() : dome != null && dome.isAutoDefense();
      boolean reloading = this.hasFreshSnapshot() ? latest.reloadTicks() > 0 : dome != null && dome.isReloading();
      boolean aircraftEnabled = this.hasFreshSnapshot() && latest.aircraftTargets();
      this.instrumentControl(
         g,
         x + 8,
         y + 21,
         w - 16,
         16,
         FlightUi.localized(aircraftEnabled ? "ui.deepstrike.iron_dome_defense_screen.4e03bc2f78" : "ui.deepstrike.iron_dome_defense_screen.fec234b448"),
         true,
         aircraftEnabled,
         aircraftEnabled ? FlightUi.Tone.NAVIGATION : FlightUi.Tone.SECONDARY
      );
      int cellStartY = y + 36;
      g.drawString(this.font, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.690f1005a9"), x + 8, cellStartY, -4079170, false);
      int cellGridX = x + 8;
      int cellGridY = cellStartY + 12;

      for (int i = 0; i < 20; i++) {
         int cx = cellGridX + i % 10 * 14;
         int cy = cellGridY + i / 10 * 14;
         boolean loaded = i < ready;
         g.fill(cx, cy, cx + 11, cy + 11, loaded ? -6575733 : -14671843);
         this.drawRectBorder(g, cx, cy, 11, 11, loaded ? -6575733 : -13553362);
      }

      int ammoInfoY = cellGridY + 32;
      g.drawString(
         this.font,
         FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.936d438c18")
            + ready
            + FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.c9c17bd33b")
            + reserve,
         x + 8,
         ammoInfoY,
         -1776415,
         false
      );
      int btnY = ammoInfoY + 14;
      String reloadText = reloading
         ? FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.ea4f84a0fd")
            + Math.round((this.hasFreshSnapshot() ? latest.reloadTicks() : 0) / 8.0)
            + "%)"
         : FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.c2e4f5d900");
      this.instrumentControl(g, x + 8, btnY, w - 16, 16, reloadText, !reloading, false, FlightUi.Tone.SECONDARY);
      int autoY = btnY + 20;
      String autoText = auto
         ? FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.931a69cbc8")
         : FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.836eb87f79");
      this.instrumentControl(g, x + 8, autoY, w - 16, 18, autoText, true, auto, FlightUi.Tone.NAVIGATION);
      int zoneBtnY = autoY + 22;
      int halfW = (w - 20) / 2;
      this.instrumentControl(
         g,
         x + 8,
         zoneBtnY,
         halfW,
         18,
         this.selectingZone
            ? FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.be41efaee7")
            : FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.dbcd209d51"),
         true,
         this.selectingZone,
         FlightUi.Tone.SECONDARY
      );
      int radX = x + 12 + halfW;
      this.instrumentControl(g, radX, zoneBtnY, 16, 18, "−", true, false, FlightUi.Tone.SECONDARY);
      double curRad = this.hasFreshSnapshot() ? latest.zoneRadius() : (dome != null ? dome.getZoneRadius() : 1500.0);
      String radStr = String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.06b15e050b"), curRad);
      g.drawString(this.font, radStr, radX + 20, zoneBtnY + 5, -6575733, false);
      int plusX = radX + halfW - 16;
      this.instrumentControl(g, plusX, zoneBtnY, 16, 18, "+", true, false, FlightUi.Tone.SECONDARY);
      int listY = zoneBtnY + 24;
      g.fill(x + 1, listY, x + w - 1, listY + 16, -13816534);
      g.drawString(
         this.font,
         FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.4d8a3f4a5c") + this.contacts().size() + "):",
         x + 6,
         listY + 4,
         -6575733,
         false
      );
      int rowY = listY + 18;
      int maxRows = (h - (rowY - y) - 30) / 24;
      List<IronDomeNetwork.Contact> contacts = this.contacts();

      for (int i = 0; i < maxRows && i + this.targetScroll < contacts.size(); i++) {
         IronDomeNetwork.Contact c = contacts.get(i + this.targetScroll);
         boolean isSel = i + this.targetScroll == this.selected;
         int cy = rowY + i * 24;
         g.fill(x + 6, cy, x + w - 6, cy + 22, isSel ? -14012910 : -15658738);
         this.drawRectBorder(
            g, x + 6, cy, w - 12, 22, isSel ? -4413569 : (!RadarDisclosure.impactKnown(c.id()) ? -7829368 : (c.threatInZone() ? -4355200 : -15060192))
         );
         String tag = c.friendly()
            ? FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.908e65c28f")
            : (
               !RadarDisclosure.impactKnown(c.id())
                  ? FlightUi.localized("radar.deepstrike.unknown")
                  : (
                     c.threatInZone()
                        ? FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.b2c969d9ef")
                        : FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.54957b25e0")
                  )
            );
         String title = AirDefenseTargets.label(c.kind())
            + " #"
            + (i + this.targetScroll + 1)
            + "  "
            + tag
            + "  ·  "
            + Math.round(c.speedKmh())
            + FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.ea49235b0a");
         g.drawString(this.font, title, x + 10, cy + 3, isSel ? -4413569 : -1776415, false);
         String sub = RadarDisclosure.impactKnown(c.id())
            ? String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.ca74cb372d"), c.altitude(), c.impactX(), c.impactZ())
            : String.format(Locale.ROOT, FlightUi.localized("radar.deepstrike.impact_unknown"), c.altitude());
         g.drawString(this.font, sub, x + 10, cy + 12, -7234957, false);
      }

      int fireY = y + h - 24;
      boolean canFire = this.selected >= 0 && this.selected < contacts.size() && ready > 0 && !reloading;
      this.instrumentControl(
         g, x + 8, fireY, w - 16, 20, FlightUi.localized("ui.deepstrike.iron_dome_defense_screen.7d2a2f1b2a"), canFire, false, FlightUi.Tone.LAUNCH
      );
   }

   private void openZoneMap() {
      double x = this.hasFreshSnapshot() ? latest.zoneX() : this.mapCenterX;
      double z = this.hasFreshSnapshot() ? latest.zoneZ() : this.mapCenterZ;
      double radius = this.hasFreshSnapshot() ? latest.zoneRadius() : 1500.0;
      this.minecraft.setScreen(new ZoneXaeroMapScreen(this, x, z, radius, (zx, zz) -> IronDomeNetwork.setZone(this.vehicleId, zx, zz, radius)));
   }

   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         double lx = (mx - this.screenX) / this.uiScale;
         double ly = (my - this.screenY) / this.uiScale;
         if (lx >= 498.0 && lx < 624.0 && ly >= 15.0 && ly < 43.0) {
            this.minecraft.setScreen(new AirDefenseMapScreen(this, this.vehicleId));
            return true;
         }

         if (lx >= 635.0 && lx < 745.0 && ly >= 15.0 && ly < 43.0) {
            UnitJournalScreen.open(this.vehicleId);
            return true;
         }

         if (lx >= 754.0 && lx < 924.0 && ly >= 15.0 && ly < 43.0) {
            FrequencySettingsScreen.defense(this, this.vehicleId, this.hasFreshSnapshot() ? latest.friendlyFrequencies() : List.of());
            return true;
         }

         int modeColumnW = 314;
         int modeRight = 672;
         if (lx >= 938.0 && lx <= 982.0 && ly >= 15.0 && ly <= 43.0) {
            this.onClose();
            return true;
         }

         if (lx >= modeRight + 8 && lx < modeRight + modeColumnW - 8 && ly >= 89.0 && ly < 105.0) {
            IronDomeNetwork.CHANNEL.sendToServer(new IronDomeNetwork.SetAircraft(this.vehicleId, !this.hasFreshSnapshot() || !latest.aircraftTargets()));
            return true;
         }

         if (!this.selectingZone && lx >= this.lastMapX && lx <= this.lastMapX + this.lastMapW && ly >= this.lastMapY && ly <= this.lastMapY + this.lastMapH) {
            this.openZoneMap();
            return true;
         }

         if (this.selectingZone && lx >= this.lastMapX && lx <= this.lastMapX + this.lastMapW && ly >= this.lastMapY && ly <= this.lastMapY + this.lastMapH) {
            double mapRelX = (lx - (this.lastMapX + this.lastMapW / 2.0)) / (this.lastMapW / 2.0);
            double mapRelZ = (ly - (this.lastMapY + this.lastMapH / 2.0)) / (this.lastMapH / 2.0);
            double newZoneX = this.mapCenterX + mapRelX * 8000.0;
            double newZoneZ = this.mapCenterZ + mapRelZ * 8000.0;
            IronDomeEntity dome = this.localIronDome();
            double rad = this.hasFreshSnapshot() ? latest.zoneRadius() : (dome != null ? dome.getZoneRadius() : 1500.0);
            IronDomeNetwork.setZone(this.vehicleId, newZoneX, newZoneZ, rad);
            this.selectingZone = false;
            return true;
         }

         int colW = 314;
         int rightX = 672;
         int contentY = 68;
         int contentH = 538;
         int reloadY = contentY + 36 + 12 + 32 + 14;
         if (lx >= rightX + 8 && lx <= rightX + colW - 8 && ly >= reloadY && ly <= reloadY + 16) {
            IronDomeNetwork.requestReload(this.vehicleId);
            return true;
         }

         int autoY = reloadY + 20;
         if (lx >= rightX + 8 && lx <= rightX + colW - 8 && ly >= autoY && ly <= autoY + 18) {
            IronDomeNetwork.toggleAutoDefense(this.vehicleId);
            return true;
         }

         int zoneBtnY = autoY + 22;
         int halfW = (colW - 20) / 2;
         if (lx >= rightX + 8 && lx <= rightX + 8 + halfW && ly >= zoneBtnY && ly <= zoneBtnY + 18) {
            this.openZoneMap();
            return true;
         }

         int radX = rightX + 12 + halfW;
         if (lx >= radX && lx <= radX + 16 && ly >= zoneBtnY && ly <= zoneBtnY + 18) {
            double curRad = this.hasFreshSnapshot() ? latest.zoneRadius() : 1500.0;
            double zx = this.hasFreshSnapshot() ? latest.zoneX() : this.mapCenterX;
            double zz = this.hasFreshSnapshot() ? latest.zoneZ() : this.mapCenterZ;
            IronDomeNetwork.setZone(this.vehicleId, zx, zz, Math.max(300.0, curRad - 300.0));
            return true;
         }

         int plusX = radX + halfW - 16;
         if (lx >= plusX && lx <= plusX + 16 && ly >= zoneBtnY && ly <= zoneBtnY + 18) {
            double curRad = this.hasFreshSnapshot() ? latest.zoneRadius() : 1500.0;
            double zx = this.hasFreshSnapshot() ? latest.zoneX() : this.mapCenterX;
            double zz = this.hasFreshSnapshot() ? latest.zoneZ() : this.mapCenterZ;
            IronDomeNetwork.setZone(this.vehicleId, zx, zz, Math.min(4500.0, curRad + 300.0));
            return true;
         }

         int listY = zoneBtnY + 24 + 18;
         List<IronDomeNetwork.Contact> contacts = this.contacts();

         for (int i = 0; i < 5 && i + this.targetScroll < contacts.size(); i++) {
            int cy = listY + i * 24;
            if (lx >= rightX + 6 && lx <= rightX + colW - 6 && ly >= cy && ly <= cy + 22) {
               this.selectedId = contacts.get(i + this.targetScroll).id();
               this.selected = i + this.targetScroll;
               return true;
            }
         }

         int fireY = contentY + contentH - 24;
         if (lx >= rightX + 8 && lx <= rightX + colW - 8 && ly >= fireY && ly <= fireY + 20) {
            if (this.selected >= 0 && this.selected < contacts.size()) {
               IronDomeNetwork.fireManual(this.vehicleId, contacts.get(this.selected).id());
            }

            return true;
         }

         int midX = 14;
         int btnY = contentY + 3;
         int btnH = 14;
         int optW = 42;
         int pnvW = 30;
         int thmW = 44;
         int thmX = midX + 644 - 6 - thmW;
         int pnvX = thmX - 4 - pnvW;
         int optX = pnvX - 4 - optW;
         if (ly >= btnY && ly <= btnY + btnH) {
            if (lx >= optX && lx <= optX + optW) {
               this.thermalCamera.setMode(PatriotThermalCamera.CameraMode.NORMAL);
               return true;
            }

            if (lx >= pnvX && lx <= pnvX + pnvW) {
               this.thermalCamera.setMode(PatriotThermalCamera.CameraMode.NIGHT_VISION);
               return true;
            }

            if (lx >= thmX && lx <= thmX + thmW) {
               this.thermalCamera.setMode(PatriotThermalCamera.CameraMode.THERMAL);
               return true;
            }
         }

         int vx = midX + 6;
         int vy = contentY + 24;
         int vw = 632;
         int vh = 230;
         int zoomY = vy + vh + 6;
         if (ly >= zoomY && ly <= zoomY + 16) {
            if (lx >= vx && lx <= vx + 18) {
               this.thermalCamera.zoomOut();
               return true;
            }

            if (lx >= vx + 22 && lx <= vx + 40) {
               this.thermalCamera.zoomIn();
               return true;
            }
         }
      }

      return super.mouseClicked(mx, my, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      if (delta > 0.0) {
         this.thermalCamera.zoomIn();
      } else if (delta < 0.0) {
         this.thermalCamera.zoomOut();
      }

      return true;
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void drawModeBtn(GuiGraphics g, int x, int y, int w, int h, String label, boolean active) {
      this.instrumentControl(g, x, y, w, h, label, true, active, active ? FlightUi.Tone.NAVIGATION : FlightUi.Tone.SECONDARY);
   }

   private void centered(GuiGraphics g, String s, int x, int y, int color) {
      FlightUi.centered(g, s, x, y, color);
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

   private void drawCornerBrackets(GuiGraphics g, int x, int y, int w, int h, int color) {
      int len = 7;
      g.fill(x, y, x + len, y + 1, color);
      g.fill(x, y, x + 1, y + len, color);
      g.fill(x + w - len, y, x + w, y + 1, color);
      g.fill(x + w - 1, y, x + w, y + len, color);
      g.fill(x, y + h - 1, x + len, y + h, color);
      g.fill(x, y + h - len, x + 1, y + h, color);
      g.fill(x + w - len, y + h - 1, x + w, y + h, color);
      g.fill(x + w - 1, y + h - len, x + w, y + h, color);
   }

   private void drawCircle(GuiGraphics g, int cx, int cy, int r, int color) {
      int segs = Math.max(16, (int)(r * 1.5));

      for (int i = 0; i < segs; i++) {
         double a1 = i * Math.PI * 2.0 / segs;
         double a2 = (i + 1) * Math.PI * 2.0 / segs;
         this.drawLine(g, (int)(cx + Math.cos(a1) * r), (int)(cy + Math.sin(a1) * r), (int)(cx + Math.cos(a2) * r), (int)(cy + Math.sin(a2) * r), color);
      }
   }

   private void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
      for (int dy = -r; dy <= r; dy++) {
         int dx = (int)Math.sqrt(Math.max(0, r * r - dy * dy));
         g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
      }
   }

   private void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
      int dx = Math.abs(x1 - x0);
      int dy = Math.abs(y1 - y0);
      int sx = x0 < x1 ? 1 : -1;
      int sy = y0 < y1 ? 1 : -1;
      int err = dx - dy;

      while (true) {
         g.fill(x0, y0, x0 + 1, y0 + 1, color);
         if (x0 == x1 && y0 == y1) {
            return;
         }

         int e2 = 2 * err;
         if (e2 > -dy) {
            err -= dy;
            x0 += sx;
         }

         if (e2 < dx) {
            err += dx;
            y0 += sy;
         }
      }
   }

   List<AirDefenseMapScreen.Target> mapTargets() {
      return this.contacts()
         .stream()
         .map(
            c -> new AirDefenseMapScreen.Target(
               c.id(), c.x(), c.y(), c.z(), c.speedKmh(), c.kind(), c.friendly(), RadarDisclosure.impactKnown(c.id()) ? (c.threatInZone() ? 1 : 0) : -1
            )
         )
         .toList();
   }

   void mapSelect(UUID id) {
      this.selectedId = id;
   }

   boolean mapCanFire() {
      return this.hasFreshSnapshot() && latest.readyTamir() > 0 && latest.reloadTicks() == 0;
   }
}
