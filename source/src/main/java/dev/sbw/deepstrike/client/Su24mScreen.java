package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.Su24mNetwork;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;

public final class Su24mScreen extends TacticalXaeroMap implements EquipmentUiTheme {
   private final int vehicle;
   private final UUID identity;
   private final List<RoutePoint>[] draft = new List[]{new ArrayList(), new ArrayList()};
   private final boolean[] dirty = new boolean[]{false, false};
   private final List<RoutePoint>[] submitted = new List[2];
   private Su24mNetwork.Snapshot snapshot;
   private int slot;
   private int tab;
   private int mode;
   private int selected = -1;
   private int page;
   private int presetIndex;
   private int ticks;
   private int panelX;
   private boolean advanceAfterSave;
   private int dragPoint = -1;
   private double pressX;
   private double pressY;
   private boolean mapDragging;
   private boolean moved;
   private boolean updatingFields;
   private final List<QuietButton> rackButtons = new ArrayList<>();
   private final List<QuietButton> stepButtons = new ArrayList<>();
   private final List<QuietButton> modeButtons = new ArrayList<>();
   private Button addPoint;
   private Button saveRoute;
   private Button copyRoute;
   private Button changePoint;
   private Button deletePoint;
   private Button cancelOrder;
   private Button loadPreset;
   private Button savePreset;
   private EditBox x;
   private EditBox y;
   private EditBox z;
   private EditBox speed;
   private EditBox name;
   private EditBox delay;
   private EditBox threshold;
   private final List<Button> armButtons = new ArrayList<>();
   private final List<Button> groundButtons = new ArrayList<>();
   private String notice = "";
   private static UUID navigatorPlane;
   private static int navigatorSeat = -1;

   private Su24mScreen(Su24mEntity plane) {
      super(null, plane.getX(), plane.getZ());
      this.vehicle = plane.getId();
      this.identity = plane.getUUID();
   }

   public static void open(int id) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.level.getEntity(id) instanceof Su24mEntity plane && MapProviders.ready()) {
         mc.setScreen(new Su24mScreen(plane));
      }
   }

   @Override
   public String equipmentSkin() {
      return EquipmentUiTheme.vehicle(this.vehicle);
   }

   private static Component text(String key, Object... args) {
      return Component.translatable("su24." + key, args);
   }

   private static String tr(String key, Object... args) {
      return text(key, args).getString();
   }

   public static void clientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.player.getVehicle() instanceof Su24mEntity plane) {
            int seat = plane.getSeatIndex(mc.player);
            boolean enter = seat == 1 && (!plane.getUUID().equals(navigatorPlane) || navigatorSeat != 1);
            navigatorPlane = plane.getUUID();
            navigatorSeat = seat;
            if (enter && mc.screen == null) {
               open(plane.getId());
            }
         } else {
            navigatorPlane = null;
            navigatorSeat = -1;
         }
      }
   }

   private EditBox field(int atX, int atY, int width, String label, String value) {
      EditBox box = new InstrumentField(this.font, atX, atY, width, 20, text(label));
      box.setMaxLength(label.equals("name") ? 32 : 16);
      box.setValue(value);
      return (EditBox)this.addRenderableWidget(box);
   }

   private QuietButton button(String label, int atX, int atY, int width, Su24mScreen.Role role, Runnable action) {
      QuietButton control = (QuietButton)this.addRenderableWidget(Button.builder(text(label), b -> action.run()).bounds(atX, atY, width, 26).build(QuietButton::new));

      return control.tone(switch (role) {
         case SAVE -> FlightUi.Tone.PRIMARY;
         case LAUNCH -> FlightUi.Tone.LAUNCH;
         case REMOVE -> FlightUi.Tone.DANGER;
         default -> FlightUi.Tone.SECONDARY;
      });
   }

   private void step(int next) {
      this.tab = next;
      this.selected = -1;
      this.init();
   }

   @Override
   public void init() {
      String[] values = this.x == null
         ? null
         : new String[]{
            this.x.getValue(),
            this.y.getValue(),
            this.z.getValue(),
            this.speed.getValue(),
            this.name.getValue(),
            this.delay.getValue(),
            this.threshold.getValue()
         };
      super.init();
      this.panelX = this.width - 326;
      this.armButtons.clear();
      this.groundButtons.clear();
      this.rackButtons.clear();
      this.stepButtons.clear();
      this.modeButtons.clear();
      this.addPoint = this.saveRoute = this.copyRoute = this.changePoint = this.deletePoint = this.cancelOrder = this.loadPreset = this.savePreset = null;
      if (this.ticks == 0) {
         this.mapSpan(1800.0);
      }

      int p = this.panelX + 12;
      int w = 302;
      this.button("center", this.panelX - 180, 18, 156, Su24mScreen.Role.NAVIGATION, () -> {
         Entity plane = this.minecraft.level.getEntity(this.vehicle);
         if (plane != null) {
            this.mapCenter(plane.getX(), plane.getZ());
         }
      });
      this.rackButtons.add(this.button("left", p, 84, 147, Su24mScreen.Role.NAVIGATION, () -> this.selectSlot(0)));
      this.rackButtons.add(this.button("right", p + 155, 84, 147, Su24mScreen.Role.NAVIGATION, () -> this.selectSlot(1)));

      for (int i = 0; i < 3; i++) {
         int next = i;
         this.stepButtons.add(this.button("step." + i, p + i * 103, 160, i == 2 ? 96 : 97, Su24mScreen.Role.NAVIGATION, () -> this.step(next)));
      }

      double cx = this.minecraft.player.getX();
      double cy = this.minecraft.player.getY();
      double cz = this.minecraft.player.getZ();
      this.x = this.field(p, 238, 94, "x", fmt(cx));
      this.y = this.field(p + 104, 238, 94, "y", fmt(cy));
      this.z = this.field(p + 208, 238, 94, "z", fmt(cz));
      this.speed = this.field(p, 280, 94, "missile_speed", "1200");
      this.name = this.field(p + 104, 280, 198, "name", tr("plan_default"));
      this.delay = this.field(p, 310, 140, "delay", "5");
      this.threshold = this.field(p + 155, 310, 147, "threshold", "200");
      if (values != null) {
         EditBox[] fields = new EditBox[]{this.x, this.y, this.z, this.speed, this.name, this.delay, this.threshold};

         for (int i = 0; i < fields.length; i++) {
            fields[i].setValue(values[i]);
         }
      }

      this.x.setResponder(val -> this.applyFieldChange());
      this.y.setResponder(val -> this.applyFieldChange());
      this.z.setResponder(val -> this.applyFieldChange());
      this.speed.setResponder(val -> this.applyFieldChange());

      for (EditBox box : List.of(this.x, this.y, this.z, this.speed, this.name)) {
         box.visible = this.tab == 1;
      }

      this.delay.visible = this.tab == 2 && (this.mode == 1 || this.mode == 3);
      this.threshold.visible = this.tab == 2 && (this.mode == 2 || this.mode == 3);
      if (this.tab == 0) {
         this.groundButtons.add(this.button("load", p, 250, 147, Su24mScreen.Role.SAVE, () -> this.action(3)));
         this.groundButtons.add(this.button("unload", p + 155, 250, 147, Su24mScreen.Role.REMOVE, () -> this.action(4)));
         this.groundButtons.add(this.button("reload_cannon", p, 326, w, Su24mScreen.Role.SAVE, () -> this.action(6)));
         this.name.visible = true;
         this.name.setX(p);
         this.name.setY(406);
         this.name.setWidth(w);
         this.button("preset_previous", p, 437, 38, Su24mScreen.Role.NAVIGATION, () -> this.cyclePreset(-1));
         this.button("preset_next", p + 264, 437, 38, Su24mScreen.Role.NAVIGATION, () -> this.cyclePreset(1));
         this.loadPreset = this.button("use_preset", p, 473, 147, Su24mScreen.Role.NAVIGATION, () -> {
            this.send(8, this.slot, this.presetIndex, 0, 0.0, List.of(), "");
            this.dirty[this.slot] = false;
            this.submitted[this.slot] = null;
         });
         this.savePreset = this.button("save_preset_short", p + 155, 473, 147, Su24mScreen.Role.SAVE, () -> {
            this.prepare(this.slot);
            this.send(7, this.slot, 0, 0, 0.0, List.of(), this.name.getValue());
         });
         this.button("to_route", p, 550, w, Su24mScreen.Role.NAVIGATION, () -> this.step(1));
      } else if (this.tab == 1) {
         this.addPoint = this.button("add", p, 312, 94, Su24mScreen.Role.SAVE, () -> this.edit(false));
         this.changePoint = this.button("change", p + 104, 312, 94, Su24mScreen.Role.NAVIGATION, () -> this.edit(true));
         this.deletePoint = this.button("delete", p + 208, 312, 94, Su24mScreen.Role.REMOVE, () -> {
            if (this.selected >= 0 && this.selected < this.draft[this.slot].size()) {
               this.draft[this.slot].remove(this.selected);
               this.selected = -1;
               this.dirty[this.slot] = true;
               this.updateEnabled();
            }
         });
         this.button("previous", p, 446, 65, Su24mScreen.Role.NAVIGATION, () -> this.page = Math.max(0, this.page - 1));
         this.button("next", p + 237, 446, 65, Su24mScreen.Role.NAVIGATION, () -> this.page = Math.min(7, this.page + 1));
         this.saveRoute = this.button("save_route", p, 480, w, Su24mScreen.Role.SAVE, () -> this.prepare(this.slot));
         this.copyRoute = this.button("copy_both", p, 512, w, Su24mScreen.Role.SAVE, () -> {
            int other = 1 - this.slot;
            this.draft[other] = new ArrayList<>(this.draft[this.slot]);
            this.dirty[other] = true;
            this.prepare(this.slot);
            this.prepare(other);
         });
         this.button("to_launch", p, 552, w, Su24mScreen.Role.NAVIGATION, () -> {
            if (this.dirty[this.slot]) {
               this.advanceAfterSave = true;
               this.prepare(this.slot);
            } else {
               this.step(2);
            }
         });
      } else {
         for (int i = 0; i < 4; i++) {
            int next = i;
            this.modeButtons.add(this.button("mode_short." + i, p + i % 2 * 155, 218 + i / 2 * 32, 147, Su24mScreen.Role.NAVIGATION, () -> {
               this.mode = next;
               this.init();
            }));
         }

         this.armButtons.add(this.button("fire_selected", p, 408, w, Su24mScreen.Role.LAUNCH, () -> this.arm(this.slot)));
         this.armButtons.add(this.button("fire_both", p, 440, w, Su24mScreen.Role.LAUNCH, () -> {
            this.arm(0);
            this.arm(1);
         }));
         this.cancelOrder = this.button("cancel_short", p, 472, w, Su24mScreen.Role.REMOVE, () -> this.action(2));
         this.button("throttle_down", p, 542, 94, Su24mScreen.Role.NEUTRAL, () -> this.throttle(-0.1));
         this.button("pilot_throttle", p + 104, 542, 94, Su24mScreen.Role.NAVIGATION, () -> this.send(5, this.slot, 0, 0, -1.0, List.of(), ""));
         this.button("throttle_up", p + 208, 542, 94, Su24mScreen.Role.NEUTRAL, () -> this.throttle(0.1));
      }

      for (EditBox field : List.of(this.delay, this.threshold)) {
         field.setResponder(value -> this.updateEnabled());
      }

      this.poll();
      this.updateEnabled();
   }

   private void cyclePreset(int delta) {
      if (this.snapshot != null && !this.snapshot.presets().isEmpty()) {
         this.presetIndex = Math.floorMod(this.presetIndex + delta, this.snapshot.presets().size());
      }
   }

   private void selectSlot(int next) {
      this.slot = next;
      this.selected = -1;
      this.page = 0;
      this.advanceAfterSave = false;
      this.updateEnabled();
   }

   private static String fmt(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
   }

   private void applyFieldChange() {
      if (!this.updatingFields && this.selected >= 0 && this.selected < this.draft[this.slot].size()) {
         try {
            double px = Double.parseDouble(this.x.getValue());
            double py = Double.parseDouble(this.y.getValue());
            double pz = Double.parseDouble(this.z.getValue());
            double ps = Double.parseDouble(this.speed.getValue());
            if (Double.isFinite(px + py + pz + ps) && ps >= 850.0 && ps <= 1850.0) {
               this.draft[this.slot].set(this.selected, new RoutePoint(px, py, pz, ps));
               this.dirty[this.slot] = true;
               this.updateEnabled();
            }
         } catch (Exception var9) {
         }
      }
   }

   private void edit(boolean change) {
      try {
         RoutePoint point = new RoutePoint(
            Double.parseDouble(this.x.getValue()),
            Double.parseDouble(this.y.getValue()),
            Double.parseDouble(this.z.getValue()),
            Double.parseDouble(this.speed.getValue())
         );
         if (!Double.isFinite(point.x() + point.y() + point.z() + point.speed()) || point.speed() < 850.0 || point.speed() > 1850.0) {
            throw new NumberFormatException();
         }

         if (change && this.selected >= 0 && this.selected < this.draft[this.slot].size()) {
            this.draft[this.slot].set(this.selected, point);
         } else if (!change && this.draft[this.slot].size() < 32) {
            this.draft[this.slot].add(point);
            this.selected = this.draft[this.slot].size() - 1;
            this.page = this.selected / 4;
         }

         this.dirty[this.slot] = true;
         this.updateEnabled();
      } catch (NumberFormatException var3) {
         this.notice = tr("invalid");
      }
   }

   private void prepare(int index) {
      if (this.draft[index].isEmpty()) {
         this.notice = tr("ux.no_points");
         this.advanceAfterSave = false;
      } else {
         this.submitted[index] = List.copyOf(this.draft[index]);
         this.send(0, index, 0, 0, 0.0, this.draft[index], "");
      }
   }

   private void arm(int index) {
      if (this.dirty[index]) {
         this.notice = tr("prepare_first");
      } else {
         try {
            this.send(
               1,
               index,
               this.mode,
               this.mode != 1 && this.mode != 3 ? 0 : Integer.parseInt(this.delay.getValue()),
               this.mode != 2 && this.mode != 3 ? 0.0 : Double.parseDouble(this.threshold.getValue()),
               List.of(),
               ""
            );
         } catch (NumberFormatException var3) {
            this.notice = tr("invalid");
         }
      }
   }

   private void throttle(double delta) {
      if (this.snapshot != null) {
         this.send(5, this.slot, 0, 0, Math.max(0.0, Math.min(1.0, this.snapshot.power() + delta)), List.of(), "");
      }
   }

   private void action(int op) {
      this.send(op, this.slot, 0, 0, 0.0, List.of(), "");
   }

   private void send(int op, int index, int mode, int seconds, double value, List<RoutePoint> route, String name) {
      Su24mNetwork.CHANNEL.sendToServer(new Su24mNetwork.Action(this.vehicle, this.identity, op, index, mode, seconds, value, List.copyOf(route), name));
   }

   private void poll() {
      Su24mNetwork.CHANNEL.sendToServer(new Su24mNetwork.Request(this.vehicle, this.identity));
   }

   public static void accept(Su24mNetwork.Snapshot state) {
      if (Minecraft.getInstance().screen instanceof Su24mScreen screen && screen.vehicle == state.vehicle() && screen.identity.equals(state.identity())) {
         screen.snapshot = state;

         for (int i = 0; i < 2; i++) {
            if (screen.submitted[i] != null && screen.submitted[i].equals(state.racks().get(i).route())) {
               screen.dirty[i] = false;
               screen.submitted[i] = null;
            }

            if (!screen.dirty[i]) {
               screen.draft[i] = new ArrayList<>(state.racks().get(i).route());
            }
         }

         if (!state.notice().isEmpty()) {
            screen.notice = Component.translatable(state.notice()).getString();
         }

         screen.updateEnabled();
         if (screen.advanceAfterSave && !screen.dirty[screen.slot]) {
            screen.advanceAfterSave = false;
            screen.step(2);
         }
      }
   }

   private boolean validLaunchFields() {
      try {
         return (this.mode != 1 && this.mode != 3 || Integer.parseInt(this.delay.getValue()) >= 0 && Integer.parseInt(this.delay.getValue()) <= 3600)
            && (
               this.mode != 2 && this.mode != 3
                  || Double.isFinite(Double.parseDouble(this.threshold.getValue()))
                     && Double.parseDouble(this.threshold.getValue()) >= 0.0
                     && Double.parseDouble(this.threshold.getValue()) <= 3000.0
            );
      } catch (NumberFormatException var2) {
         return false;
      }
   }

   private String launchIssue(int index) {
      if (this.snapshot == null) {
         return "sync";
      } else {
         Su24mNetwork.Rack rack = this.snapshot.racks().get(index);
         if (rack.mode() >= 0) {
            return "ux.queued";
         } else if (!rack.loaded()) {
            return "ux.need_missile";
         } else if (this.draft[index].isEmpty()) {
            return "ux.no_points";
         } else if (this.dirty[index]) {
            return "prepare_first";
         } else if (this.minecraft.player == null || this.minecraft.player.getVehicle() != this.minecraft.level.getEntity(this.vehicle)) {
            return "ux.need_crew";
         } else {
            return !this.validLaunchFields() ? "ux.invalid_condition" : "";
         }
      }
   }

   private void updateEnabled() {
      if (this.delay != null) {
         boolean parked = this.snapshot != null && this.snapshot.parked();
         Su24mNetwork.Rack rack = this.snapshot == null ? null : this.snapshot.racks().get(this.slot);

         for (int i = 0; i < this.groundButtons.size(); i++) {
            this.groundButtons.get(i).active = parked
               && (i == 0 ? !rack.loaded() && rack.mode() < 0 : (i == 1 ? rack.loaded() && rack.mode() < 0 : this.snapshot.rounds() < 500));
         }

         for (int i = 0; i < this.rackButtons.size(); i++) {
            QuietButton control = this.rackButtons.get(i);
            control.selected(i == this.slot);
            if (this.snapshot != null) {
               Su24mNetwork.Rack store = this.snapshot.racks().get(i);
               control.setMessage(text(i == 0 ? "ux.rack_left" : "ux.rack_right", store.loaded() ? 1 : 0));
               control.setTooltip(Tooltip.create(text("status." + store.status())));
            }
         }

         for (int ix = 0; ix < this.stepButtons.size(); ix++) {
            this.stepButtons.get(ix).selected(ix == this.tab);
         }

         for (int ix = 0; ix < this.modeButtons.size(); ix++) {
            this.modeButtons.get(ix).selected(ix == this.mode);
         }

         for (int ix = 0; ix < this.armButtons.size(); ix++) {
            String issue = ix == 0 ? this.launchIssue(this.slot) : (!this.launchIssue(0).isEmpty() ? this.launchIssue(0) : this.launchIssue(1));
            this.armButtons.get(ix).active = issue.isEmpty();
            this.armButtons.get(ix).setTooltip(Tooltip.create(text(issue.isEmpty() ? "ux.launch_rules" : issue)));
         }

         boolean editable = rack != null && rack.mode() < 0;
         if (this.addPoint != null) {
            this.addPoint.active = editable && this.draft[this.slot].size() < 32;
         }

         if (this.saveRoute != null) {
            this.saveRoute.active = editable && !this.draft[this.slot].isEmpty();
         }

         if (this.copyRoute != null) {
            this.copyRoute.active = editable && this.snapshot.racks().get(1 - this.slot).mode() < 0 && !this.draft[this.slot].isEmpty();
         }

         if (this.changePoint != null) {
            this.changePoint.active = editable && this.selected >= 0;
         }

         if (this.deletePoint != null) {
            this.deletePoint.active = editable && this.selected >= 0;
         }

         if (this.cancelOrder != null) {
            this.cancelOrder.active = rack != null && rack.mode() >= 0;
         }

         if (this.loadPreset != null) {
            this.loadPreset.active = editable && !this.snapshot.presets().isEmpty();
         }

         if (this.savePreset != null) {
            this.savePreset.active = editable && !this.draft[this.slot].isEmpty();
         }
      }
   }

   public void tick() {
      if (this.minecraft.player != null
         && this.minecraft.level.getEntity(this.vehicle) instanceof Su24mEntity plane
         && plane.getUUID().equals(this.identity)
         && (this.minecraft.player.getVehicle() == plane || !(this.minecraft.player.distanceToSqr(plane) > 144.0))) {
         this.updateEnabled();
         if (++this.ticks % 10 == 0) {
            this.poll();
         }
      } else {
         this.onClose();
      }
   }

   @Override
   protected boolean mapArea(double x, double y) {
      return x >= 16.0 && x < this.panelX - 10 && y >= 80.0 && y < this.height - 108;
   }

   private int pointAt(double mx, double my) {
      List<RoutePoint> points = this.draft[this.slot];

      for (int i = points.size() - 1; i >= 0; i--) {
         RoutePoint p = points.get(i);
         if (Math.abs(this.mapX(p.x()) - mx) < 14.0 && Math.abs(this.mapZ(p.z()) - my) < 14.0) {
            return i;
         }
      }

      return -1;
   }

   private void addWaypointAt(double mx, double my) {
      if (this.draft[this.slot].size() < 32) {
         if (this.tab != 1) {
            this.step(1);
         }

         double wx = Math.floor(this.worldX(mx)) + 0.5;
         double wz = Math.floor(this.worldZ(my)) + 0.5;
         Entity plane = this.minecraft.level.getEntity(this.vehicle);
         double alt = plane != null ? plane.getY() : 120.0;
         if (!this.draft[this.slot].isEmpty()) {
            alt = this.draft[this.slot].get(this.draft[this.slot].size() - 1).y();
         } else if (this.minecraft.level != null) {
            int ground = this.minecraft.level.getHeight(Types.MOTION_BLOCKING, (int)wx, (int)wz);
            alt = Math.max(ground + 50.0, alt);
         }

         double spd = 1200.0;

         try {
            spd = Double.parseDouble(this.speed.getValue());
         } catch (Exception var15) {
         }

         RoutePoint pt = new RoutePoint(wx, Math.floor(alt) + 0.5, wz, spd);
         this.draft[this.slot].add(pt);
         this.selected = this.draft[this.slot].size() - 1;
         this.page = this.selected / 4;
         this.syncFieldsToPoint(pt);
         this.dirty[this.slot] = true;
         this.notice = tr("ux.map_selected");
         this.updateEnabled();
      }
   }

   private void syncFieldsToPoint(RoutePoint pt) {
      this.updatingFields = true;

      try {
         this.x.setValue(fmt(pt.x()));
         this.y.setValue(fmt(pt.y()));
         this.z.setValue(fmt(pt.z()));
         this.speed.setValue(fmt(pt.speed()));
      } finally {
         this.updatingFields = false;
      }
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      int toggleX = this.chromeHidden ? this.width - 180 : this.width - 330;
      if (mx >= toggleX && mx < toggleX + 166 && my >= 14.0 && my < 40.0) {
         return super.mouseClicked(mx, my, button);
      } else {
         if (!this.chromeHidden) {
            for (GuiEventListener child : this.children()) {
               if (child instanceof AbstractWidget widget && widget.visible && widget.isMouseOver(mx, my)) {
                  this.setFocused(widget);
                  return widget.mouseClicked(mx, my, button);
               }
            }

            if (this.tab == 1 && mx >= this.panelX + 12 && mx <= this.width - 12 && my >= 348.0 && my < 436.0) {
               int index = this.page * 4 + (int)(my - 348.0) / 22;
               if (index < this.draft[this.slot].size()) {
                  this.selected = index;
                  this.syncFieldsToPoint(this.draft[this.slot].get(index));
                  this.updateEnabled();
                  return true;
               }
            }
         }

         this.setFocused(null);
         if (this.mapArea(mx, my)) {
            if (button == 0) {
               this.pressX = mx;
               this.pressY = my;
               this.moved = false;
               this.mapDragging = true;
               int pt = this.pointAt(mx, my);
               if (pt >= 0) {
                  this.dragPoint = pt;
                  this.selected = pt;
                  this.page = this.selected / 4;
                  this.syncFieldsToPoint(this.draft[this.slot].get(pt));
                  this.updateEnabled();
               } else {
                  this.dragPoint = -1;
               }

               return true;
            }

            if (button == 1) {
               double wx = this.worldX(mx);
               double wz = this.worldZ(my);
               this.x.setValue(fmt(wx));
               this.z.setValue(fmt(wz));
               if (this.snapshot != null) {
                  for (Su24mNetwork.Contact contact : this.snapshot.contacts()) {
                     if (Math.hypot(this.mapX(contact.x()) - mx, this.mapZ(contact.z()) - my) < 9.0) {
                        this.x.setValue(fmt(contact.x()));
                        this.y.setValue(fmt(contact.y()));
                        this.z.setValue(fmt(contact.z()));
                        this.notice = contact.name();
                        break;
                     }
                  }
               }

               this.applyFieldChange();
               return true;
            }
         }

         return super.mouseClicked(mx, my, button);
      }
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (button == 0 && this.mapDragging) {
         this.moved = this.moved | Math.hypot(mx - this.pressX, my - this.pressY) > 3.0;
         if (!this.moved) {
            return true;
         } else {
            if (this.dragPoint >= 0 && this.dragPoint < this.draft[this.slot].size()) {
               RoutePoint p = this.draft[this.slot].get(this.dragPoint);
               double wx = Math.round(this.worldX(mx) * 2.0) / 2.0;
               double wz = Math.round(this.worldZ(my) * 2.0) / 2.0;
               this.draft[this.slot].set(this.dragPoint, new RoutePoint(wx, p.y(), wz, p.speed()));
               this.updatingFields = true;

               try {
                  this.x.setValue(fmt(wx));
                  this.z.setValue(fmt(wz));
               } finally {
                  this.updatingFields = false;
               }

               this.dirty[this.slot] = true;
               this.updateEnabled();
            } else {
               this.mapCenter(this.worldX(this.width / 2.0) - dx / this.effectiveScale(), this.worldZ(this.height / 2.0) - dy / this.effectiveScale());
            }

            return true;
         }
      } else {
         return super.mouseDragged(mx, my, button, dx, dy);
      }
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      if (button == 0 && this.mapDragging) {
         this.mapDragging = false;
         if (!this.moved && this.dragPoint < 0 && this.mapArea(mx, my)) {
            this.addWaypointAt(mx, my);
         }

         this.dragPoint = -1;
         return true;
      } else {
         return super.mouseReleased(mx, my, button);
      }
   }

   private void routeLine(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
      double dx = (double)bx - ax;
      double dy = (double)by - ay;
      double lo = 0.0;
      double hi = 1.0;
      double[] p = new double[]{-dx, dx, -dy, dy};
      double[] q = new double[]{
         ax - (this.chromeHidden ? 0 : 16),
         (this.chromeHidden ? this.width : this.panelX - 10) - ax,
         ay - (this.chromeHidden ? 0 : 80),
         (this.chromeHidden ? this.height : this.height - 108) - ay
      };

      for (int i = 0; i < 4; i++) {
         if (p[i] == 0.0) {
            if (q[i] < 0.0) {
               return;
            }
         } else {
            double t = q[i] / p[i];
            if (p[i] < 0.0) {
               lo = Math.max(lo, t);
            } else {
               hi = Math.min(hi, t);
            }

            if (lo > hi) {
               return;
            }
         }
      }

      FlightUi.line(g, (int)(ax + dx * lo), (int)(ay + dy * lo), (int)(ax + dx * hi), (int)(ay + dy * hi), color);
   }

   private void chevron(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
      int mx = (ax + bx) / 2;
      int my = (ay + by) / 2;
      int minX = this.chromeHidden ? 0 : 16;
      int maxX = this.chromeHidden ? this.width : this.panelX - 10;
      int minY = this.chromeHidden ? 0 : 80;
      int maxY = this.chromeHidden ? this.height : this.height - 108;
      if (mx >= minX && mx <= maxX && my >= minY && my <= maxY) {
         double a = Math.atan2(by - ay, bx - ax);

         for (int side : new int[]{-1, 1}) {
            for (int s = 0; s < 7; s++) {
               int px = mx - (int)(Math.cos(a + side * 0.55) * s);
               int py = my - (int)(Math.sin(a + side * 0.55) * s);
               if (px >= minX && px < maxX && py >= minY && py < maxY) {
                  g.fill(px, py, px + 2, py + 2, color);
               }
            }
         }
      }
   }

   @Override
   protected void drawOverlay(GuiGraphics g, int mouseX, int mouseY, float partial) {
      int p = this.panelX + 12;
      if (!this.chromeHidden) {
         FlightUi.panel(g, 12, 10, this.width - 24, 58, -182838503, -10854328);
         FlightUi.label(g, tr("title"), 28, 23, -1182997);
         FlightUi.label(g, tr("help"), 28, 43, -5196890);
         FlightUi.panel(g, this.panelX, 76, 326, this.height - 82, -182838503, -10854328);
         Su24mNetwork.Rack rack = this.snapshot == null ? null : this.snapshot.racks().get(this.slot);
         FlightUi.label(
            g, FlightUi.clip(rack == null ? tr("sync") : tr("status." + rack.status()), 294), p, 119, rack != null && rack.mode() >= 0 ? -1000096 : -5196890
         );
         FlightUi.label(g, tr("ux.step_help." + this.tab), p, 193, -5196890);
         if (this.tab == 0) {
            FlightUi.label(
               g,
               tr("ux.stores", this.snapshot == null ? 0 : (this.snapshot.racks().get(0).loaded() ? 1 : 0) + (this.snapshot.racks().get(1).loaded() ? 1 : 0)),
               p,
               216,
               -1182997
            );
            FlightUi.label(g, tr(this.snapshot != null && this.snapshot.parked() ? "ux.load_hint" : "ux.park_only"), p, 233, -5196890);
            FlightUi.label(g, tr("ux.cannon", this.snapshot == null ? 0 : this.snapshot.rounds()), p, 289, -1182997);
            FlightUi.label(g, tr("ux.ammo_hint"), p, 308, -5196890);
            FlightUi.label(g, tr("ux.library"), p, 369, -5196890);
            FlightUi.label(g, tr("name"), p, 390, -5196890);
            String preset = this.snapshot != null && !this.snapshot.presets().isEmpty()
               ? this.snapshot.presets().get(Math.floorMod(this.presetIndex, this.snapshot.presets().size())).name()
               : tr("ux.no_presets");
            FlightUi.label(g, FlightUi.clip(preset, 208), p + 47, 446, -1182997);
            FlightUi.label(g, tr("ux.library_hint"), p, 511, -5196890);
         } else if (this.tab != 1) {
            if (this.delay.visible) {
               FlightUi.label(g, tr("delay"), this.delay.getX(), 296, -5196890);
            }

            if (this.threshold.visible) {
               FlightUi.label(g, tr("threshold"), this.threshold.getX(), 296, -5196890);
            }

            String issue = this.launchIssue(this.slot);
            FlightUi.label(g, FlightUi.clip(tr(issue.isEmpty() ? "ux.ready" : issue), 294), p, 350, issue.isEmpty() ? -1182997 : -1000096);
            FlightUi.label(g, tr("ux.launch_rules"), p, 370, -5196890);
            if (rack != null && rack.mode() >= 0) {
               FlightUi.label(g, tr("ux.order", rack.waitTicks() / 20L, fmt(rack.speed())), p, 389, -1000096);
            }

            FlightUi.label(g, tr("throttle", this.snapshot == null ? 0 : (int)(this.snapshot.power() * 100.0)), p, 520, -5196890);
            FlightUi.label(g, tr("ux.pilot_priority"), p, 578, -5196890);
         } else {
            FlightUi.label(g, tr("ux.route_hint"), p, 210, -5196890);

            for (EditBox field : List.of(this.x, this.y, this.z, this.speed, this.name)) {
               FlightUi.label(g, field.getMessage().getString(), field.getX(), field.getY() - 14, -5196890);
            }

            for (int row = 0; row < 4; row++) {
               int i = this.page * 4 + row;
               if (i >= this.draft[this.slot].size()) {
                  break;
               }

               RoutePoint point = this.draft[this.slot].get(i);
               if (i == this.selected) {
                  g.fill(p, 348 + row * 22, this.width - 12, 369 + row * 22, -12498886);
               }

               String mark = i == this.draft[this.slot].size() - 1 ? tr("ux.target") : Integer.toString(i + 1);
               FlightUi.label(
                  g, FlightUi.clip(mark + " · " + fmt(point.x()) + " / " + fmt(point.y()) + " / " + fmt(point.z()), 294), p + 3, 353 + row * 22, -1182997
               );
            }

            FlightUi.label(
               g, tr(this.dirty[this.slot] ? "ux.unsaved" : "ux.saved", this.draft[this.slot].size()), p + 75, 454, this.dirty[this.slot] ? -1000096 : -1182997
            );
         }

         FlightUi.panel(g, 16, this.height - 100, this.panelX - 26, 84, -182838503, -10854328);
         if (this.snapshot != null) {
            FlightUi.label(
               g, tr("telemetry", fmt(this.snapshot.speed()), fmt(this.snapshot.altitude()), fmt(this.snapshot.heading())), 28, this.height - 87, -1182997
            );
            FlightUi.label(
               g,
               tr("weapons", this.snapshot.rounds(), (this.snapshot.racks().get(0).loaded() ? 1 : 0) + (this.snapshot.racks().get(1).loaded() ? 1 : 0)),
               28,
               this.height - 66,
               -1182997
            );
         }

         FlightUi.label(g, FlightUi.clip(this.notice, Math.max(100, this.panelX - 60)), 28, this.height - 43, -1000096);
      }

      g.enableScissor(
         this.chromeHidden ? 0 : 16,
         this.chromeHidden ? 0 : 80,
         this.chromeHidden ? this.width : this.panelX - 10,
         this.chromeHidden ? this.height : this.height - 108
      );
      Entity plane = this.minecraft.level.getEntity(this.vehicle);
      int planeX = plane != null ? this.mapX(plane.getX()) : this.width / 2;
      int planeY = plane != null ? this.mapZ(plane.getZ()) : this.height / 2;
      if (plane != null) {
         FlightUi.rounded(g, planeX - 7, planeY - 7, 15, 15, -14078937);
         g.renderOutline(planeX - 7, planeY - 7, 15, 15, -4668269);
         FlightUi.cornerBrackets(g, planeX - 9, planeY - 9, 19, 19, 4, -4668269);
         FlightUi.label(g, "SU-24M", planeX + 12, planeY - 5, -1182997);
         float heading = plane.getYRot();
         int hx = planeX + (int)(Math.sin(Math.toRadians(-heading)) * 16.0);
         int hy = planeY + (int)(Math.cos(Math.toRadians(-heading)) * 16.0);
         FlightUi.line(g, planeX, planeY, hx, hy, -469622);
         g.fill(hx - 2, hy - 2, hx + 3, hy + 3, -469622);
      }

      for (int rackx = 0; rackx < 2; rackx++) {
         List<RoutePoint> points = this.draft[rackx];
         int prevX = planeX;
         int prevY = planeY;

         for (int ix = 0; ix < points.size(); ix++) {
            RoutePoint point = points.get(ix);
            int px = this.mapX(point.x());
            int py = this.mapZ(point.z());
            boolean isLast = ix == points.size() - 1;
            int segColor = rackx == this.slot ? (isLast ? -4668269 : -1000096) : (isLast ? -7499652 : -10854328);
            this.routeLine(g, prevX, prevY, px, py, segColor);
            this.chevron(g, prevX, prevY, px, py, segColor);
            if (rackx == this.slot) {
               if (isLast) {
                  double dx = point.x() - (plane != null ? plane.getX() : 0.0);
                  double dz = point.z() - (plane != null ? plane.getZ() : 0.0);
                  double dist = Math.sqrt(dx * dx + dz * dz);
                  int reticleColor = -6696818;
                  FlightUi.crosshair(g, px, py, 16, reticleColor);
                  FlightUi.circle(g, px, py, 10.0F, reticleColor, false);
                  FlightUi.cornerBrackets(g, px - 12, py - 12, 25, 25, 5, reticleColor);
                  String distLabel = tr("ux.target") + String.format(Locale.ROOT, " · %.0f m", dist);
                  int tw = this.font.width(distLabel);
                  FlightUi.panel(g, px + 16, py - 8, tw + 12, 16, -182838503, reticleColor);
                  FlightUi.label(g, distLabel, px + 22, py - 4, reticleColor);
               } else {
                  FlightUi.rounded(g, px - 8, py - 8, 17, 17, -14078937);
                  g.renderOutline(px - 8, py - 8, 17, 17, ix == this.selected ? -4668269 : -10854328);
                  FlightUi.centered(g, Integer.toString(ix + 1), px, py - 4, ix == this.selected ? -4668269 : -1182997);
                  String caption = "Y " + (int)point.y();
                  int tw = this.font.width(caption);
                  FlightUi.rounded(g, px + 12, py - 7, tw + 8, 14, -182838503);
                  FlightUi.label(g, caption, px + 16, py - 4, -5196890);
               }
            } else {
               FlightUi.rounded(g, px - 5, py - 5, 11, 11, -14078937);
               g.renderOutline(px - 5, py - 5, 11, 11, -10854328);
            }

            prevX = px;
            prevY = py;
         }
      }

      if (this.snapshot != null) {
         for (Su24mNetwork.Contact contact : this.snapshot.contacts()) {
            int px = this.mapX(contact.x());
            int py = this.mapZ(contact.z());
            boolean isOurMissile = false;

            for (int ix = 0; ix < 2; ix++) {
               if (contact.identity().equals(this.snapshot.racks().get(ix).flight())) {
                  isOurMissile = true;
                  break;
               }
            }

            if (isOurMissile) {
               long time = System.currentTimeMillis();
               float pingRadius = 8.0F + (float)(time % 1000L) / 60.0F;
               FlightUi.circle(g, px, py, pingRadius, -16711868, false);
               ResourceLocation icon = RadarContactRenderer.getIconForKind(MissileKind.STORM_SHADOW.ordinal());
               if (icon != null) {
                  g.blit(icon, px - 8, py - 8, 16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
               }

               FlightUi.label(g, "STORM SHADOW · Y " + (int)contact.y(), px + 12, py - 4, -16711868);
            } else {
               int col = contact.missile() ? -4355200 : -6575733;
               RadarContactRenderer.drawContact(g, this.font, px, py, contact.kind(), true, col, false, 12);
               if (Math.abs(mouseX - px) < 10 && Math.abs(mouseY - py) < 10) {
                  g.drawString(this.font, contact.name(), px + 8, py - 4, col, false);
               }
            }
         }
      }

      g.disableScissor();
   }

   private static enum Role {
      NEUTRAL,
      NAVIGATION,
      SAVE,
      LAUNCH,
      REMOVE;
   }
}
