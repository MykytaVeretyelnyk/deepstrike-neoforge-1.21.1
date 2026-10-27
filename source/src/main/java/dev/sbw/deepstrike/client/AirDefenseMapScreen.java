package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

final class AirDefenseMapScreen extends TacticalXaeroMap implements EquipmentUiTheme {
   final Screen console;
   private final int vehicle;
   private final boolean iron;
   private UUID selected;
   private int ticks;
   private int scroll;
   private Button primary;
   private Button secondary;

   @Override
   public String equipmentSkin() {
      return EquipmentUiTheme.inherited(this.console);
   }

   AirDefenseMapScreen(Screen console, int vehicle) {
      super(console, origin(vehicle, true), origin(vehicle, false));
      this.console = console;
      this.vehicle = vehicle;
      this.iron = console instanceof IronDomeDefenseScreen;
   }

   private static double origin(int id, boolean x) {
      Minecraft mc = Minecraft.getInstance();
      Entity v = mc.level.getEntity(id);
      return v == null ? (x ? mc.player.getX() : mc.player.getZ()) : (x ? v.getX() : v.getZ());
   }

   static Component text(String key) {
      return Component.translatable("screen.deepstrike.radar_map." + key);
   }

   @Override
   public void init() {
      super.init();
      this.mapSpan(!this.iron && !(this.radarRange() <= 0.0) ? this.radarRange() * 2.0 : 16000.0);
      this.addRenderableWidget(Button.builder(text("back"), b -> this.onClose()).bounds(this.width - 132, 18, 112, 24).build(QuietButton::new));
      int x = this.width - 262;
      this.primary = (Button)this.addRenderableWidget(
         Button.builder(this.iron ? Component.literal("TAMIR") : Component.literal("PAC-2"), b -> this.fire(0))
            .bounds(x, this.height - 76, 118, 26)
            .build(QuietButton::new)
      );
      this.secondary = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("PAC-3"), b -> this.fire(1)).bounds(x + 124, this.height - 76, 118, 26).build(QuietButton::new)
      );
      this.secondary.visible = !this.iron;
   }

   private double radarRange() {
      return this.console instanceof PatriotDefenseScreen p ? p.mapRadarRange() : 8000.0;
   }

   List<AirDefenseMapScreen.Target> targets() {
      return this.console instanceof PatriotDefenseScreen p ? p.mapTargets() : ((IronDomeDefenseScreen)this.console).mapTargets();
   }

   private void select(UUID id) {
      this.selected = id;
      if (this.console instanceof PatriotDefenseScreen p) {
         p.mapSelect(id);
      } else {
         ((IronDomeDefenseScreen)this.console).mapSelect(id);
      }

      if (this.primary != null) {
         this.primary.active = this.canFire(0);
         this.secondary.active = this.canFire(1);
      }
   }

   private boolean canFire(int type) {
      Entity vehicleEntity = this.minecraft.level.getEntity(this.vehicle);
      return this.selected != null
         && this.targets()
            .stream()
            .anyMatch(
               t -> t.id.equals(this.selected) && !t.friendly && (!this.iron || vehicleEntity != null && vehicleEntity.distanceToSqr(t.x, t.y, t.z) <= 2.025E7)
            )
         && (this.console instanceof PatriotDefenseScreen p ? p.mapCanFire(type) : ((IronDomeDefenseScreen)this.console).mapCanFire());
   }

   private void fire(int type) {
      if (this.canFire(type)) {
         if (this.iron) {
            IronDomeNetwork.fireManual(this.vehicle, this.selected);
         } else {
            PatriotDefenseNetwork.fire(this.selected, type);
         }
      }
   }

   public void tick() {
      Entity v = this.minecraft.level.getEntity(this.vehicle);
      boolean remoteDomeConsole = this.iron && this.console instanceof IronDomeDefenseScreen && v != null && this.minecraft.player.distanceToSqr(v) <= 262144.0;
      if (v != null && (this.minecraft.player.getVehicle() == v || remoteDomeConsole)) {
         if (++this.ticks % 10 == 1) {
            if (this.iron) {
               IronDomeNetwork.requestScan(this.vehicle);
            } else {
               PatriotDefenseNetwork.requestScan(this.vehicle);
            }
         }

         if (this.selected != null && this.targets().stream().noneMatch(t -> t.id.equals(this.selected))) {
            this.selected = null;
         }

         this.primary.active = this.canFire(0);
         this.secondary.active = this.canFire(1);
      } else {
         this.onClose();
      }
   }

   @Override
   protected boolean mapArea(double x, double y) {
      return x >= 12.0 && x < this.width - 282 && y > 62.0 && y < this.height - 30;
   }

   @Override
   protected boolean choose(double x, double y, int button) {
      return button != 0
         ? false
         : this.targets()
            .stream()
            .filter(t -> Math.hypot(this.mapX(t.x) - x, this.mapZ(t.z) - y) < 12.0)
            .min(Comparator.comparingDouble(t -> Math.hypot(this.mapX(t.x) - x, this.mapZ(t.z) - y)))
            .map(t -> {
               this.select(t.id);
               return true;
            })
            .orElse(false);
   }

   @Override
   public boolean mouseClicked(double x, double y, int button) {
      if (!this.chromeHidden && button == 0 && x >= this.width - 266 && x < this.width - 16 && y >= 96.0 && y < this.height - 114) {
         int index = (int)(y - 96.0) / 35 + this.scroll;
         List<AirDefenseMapScreen.Target> list = this.targets();
         if (index < list.size()) {
            this.select(list.get(index).id);
            return true;
         }
      }

      return super.mouseClicked(x, y, button);
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
      if (!this.chromeHidden && x >= this.width - 280) {
         this.scroll = Math.max(0, Math.min(Math.max(0, this.targets().size() - this.rows()), this.scroll - (int)delta));
         return true;
      } else {
         return super.mouseScrolled(x, y, 0.0, delta);
      }
   }

   private int rows() {
      return Math.max(1, (this.height - 210) / 35);
   }

   @Override
   protected void drawOverlay(GuiGraphics g, int mx, int my, float partial) {
      if (!this.chromeHidden) {
         FlightUi.panel(g, 12, 10, this.width - 24, 42, -182838503, -10854328);
         FlightUi.label(g, (this.iron ? "IRON DOME" : "PATRIOT") + " / " + text("title").getString(), 26, 24, -1182997);
         FlightUi.panel(g, this.width - 276, 64, 264, this.height - 88, -182838503, -10854328);
         FlightUi.label(g, text("targets").getString() + " · " + this.targets().size(), this.width - 260, 78, -5196890);
      }

      List<AirDefenseMapScreen.Target> list = this.targets();
      this.scroll = Math.min(this.scroll, Math.max(0, list.size() - this.rows()));
      g.enableScissor(
         this.chromeHidden ? 0 : 12,
         this.chromeHidden ? 0 : 62,
         this.chromeHidden ? this.width : this.width - 282,
         this.chromeHidden ? this.height : this.height - 30
      );
      Entity v = this.minecraft.level.getEntity(this.vehicle);
      if (v != null) {
         int x = this.mapX(v.getX());
         int y = this.mapZ(v.getZ());
         g.renderOutline(x - 6, y - 6, 13, 13, -6696818);
         double radius = this.radarRange() * this.effectiveScale();

         for (int i = 0; i < 180; i++) {
            double a = i * Math.PI / 90.0;
            int xx = x + (int)(Math.cos(a) * radius);
            int yy = y + (int)(Math.sin(a) * radius);
            g.fill(xx, yy, xx + 1, yy + 1, -1868846709);
         }
      }

      for (AirDefenseMapScreen.Target target : list) {
         int x = this.mapX(target.x);
         int y = this.mapZ(target.z);
         int color = target.friendly ? -6696818 : (target.assessment < 0 ? -7829368 : (target.assessment == 1 ? -1076610 : -6696818));
         boolean dirKnown = target.friendly || target.assessment >= 0;
         RadarContactRenderer.drawContact(g, this.font, x, y, target.kind, dirKnown, color, target.id.equals(this.selected), 14);
         FlightUi.label(g, AirDefenseTargets.label(target.kind) + " · Y " + Math.round(target.y), x + 13, y - 4, color);
      }

      g.disableScissor();
      if (!this.chromeHidden) {
         for (int i = 0; i < this.rows() && i + this.scroll < list.size(); i++) {
            AirDefenseMapScreen.Target t = list.get(i + this.scroll);
            int x = this.width - 266;
            int y = 96 + i * 35;
            FlightUi.rounded(g, x, y, 250, 32, t.id.equals(this.selected) ? -12498886 : -14078937);
            FlightUi.label(g, FlightUi.clip(AirDefenseTargets.label(t.kind), 230), x + 8, y + 5, t.friendly ? -6696818 : -1182997);
            FlightUi.label(g, String.format(Locale.ROOT, "Y %.0f m · %.0f km/h", t.y, t.speed), x + 8, y + 19, -5196890);
         }

         AirDefenseMapScreen.Target chosen = list.stream().filter(tx -> tx.id.equals(this.selected)).findFirst().orElse(null);
         FlightUi.label(g, text(chosen == null ? "select" : "selected").getString(), this.width - 260, this.height - 103, -1000096);
         FlightUi.label(g, text("hint").getString(), 48, this.height - 18, -5196890);
      }
   }

   record Target(UUID id, double x, double y, double z, float speed, int kind, boolean friendly, int assessment) {
      Target(UUID id, double x, double y, double z, float speed, int kind, boolean friendly) {
         this(id, x, y, z, speed, kind, friendly, -1);
      }
   }
}
