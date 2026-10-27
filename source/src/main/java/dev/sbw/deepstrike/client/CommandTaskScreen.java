package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.DefenseRuntime;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class CommandTaskScreen extends Screen {
   private final CommandTabletScreen parent;
   private final CompoundTag network;
   private final CompoundTag existing;
   private final Set<UUID> selected = new LinkedHashSet<>();
   private final List<RoutePoint> route = new ArrayList<>();
   private EditBox name;
   private EditBox count;
   private EditBox interval;
   private EditBox delay;
   private EditBox frequency;
   private boolean readyOnly;
   private boolean redstone;
   private boolean ripple;
   private String error = "";

   public CommandTaskScreen(CommandTabletScreen parent, CompoundTag network, CompoundTag existing, Set<UUID> selected) {
      super(CommandTabletScreen.tr("task_editor"));
      this.parent = parent;
      this.network = network;
      this.existing = existing;
      if (existing != null) {
         for (Tag raw : existing.getList("Assignments", 10)) {
            this.selected.add(((CompoundTag)raw).getUUID("Unit"));
         }

         for (Tag raw : existing.getList("Route", 10)) {
            CompoundTag p = (CompoundTag)raw;
            this.route.add(new RoutePoint(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"), p.getDouble("Speed")));
         }

         this.readyOnly = existing.getBoolean("ReadyOnly");
         this.redstone = existing.getBoolean("Redstone");
         this.ripple = existing.getBoolean("Ripple");
      } else {
         this.selected.addAll(selected);
      }
   }

   private EditBox field(String label, int x, int y, int w, String value) {
      EditBox box = (EditBox)this.addRenderableWidget(new InstrumentField(this.font, x, y, w, 26, CommandTabletScreen.tr(label)));
      box.setMaxLength(32);
      box.setValue(value);
      return box;
   }

   private Button button(String key, int x, int y, int w, Runnable action) {
      QuietButton button = (QuietButton)this.addRenderableWidget(
         Button.builder(CommandTabletScreen.tr(key), b -> action.run()).bounds(x, y, w, 26).build(QuietButton::new)
      );
      return button.selected(key.equals("redstone_on") || key.equals("ready_only") || key.equals("group_waves"));
   }

   protected void init() {
      int x = this.width / 2 - 320;
      int y = this.height / 2 - 200;
      this.name = this.field(
         "name",
         x,
         y + 35,
         640,
         this.name != null
            ? this.name.getValue()
            : (this.existing == null ? CommandTabletScreen.tr("default_task").getString() : this.existing.getString("Name"))
      );
      this.count = this.field(
         "count",
         x,
         y + 105,
         190,
         this.count != null
            ? this.count.getValue()
            : (this.existing == null ? "1" : String.valueOf(this.existing.getList("Assignments", 10).getCompound(0).getInt("Count")))
      );
      this.interval = this.field(
         "interval",
         x + 220,
         y + 105,
         200,
         this.interval != null ? this.interval.getValue() : (this.existing == null ? "6" : String.valueOf(this.existing.getInt("WaveInterval") / 20))
      );
      this.delay = this.field(
         "delay",
         x + 450,
         y + 105,
         190,
         this.delay != null ? this.delay.getValue() : (this.existing == null ? "1" : String.valueOf(Math.max(1, this.existing.getInt("LaunchDelay") / 20)))
      );
      this.frequency = this.field(
         "frequency", x, y + 175, 190, this.frequency != null ? this.frequency.getValue() : (this.existing == null ? "" : this.existing.getString("Frequency"))
      );
      this.frequency.setMaxLength(12);
      this.button("edit_route", x + 220, y + 175, 420, this::editRoute);
      this.button(this.readyOnly ? "ready_only" : "all_ready", x, y + 240, 310, () -> {
         this.readyOnly = !this.readyOnly;
         this.rebuildWidgets();
      });
      this.button(this.redstone ? "redstone_on" : "redstone_off", x + 330, y + 240, 310, () -> {
         this.redstone = !this.redstone;
         this.rebuildWidgets();
      });
      this.button(this.ripple ? "group_waves" : "group_together", x, y + 280, 640, () -> {
         this.ripple = !this.ripple;
         this.rebuildWidgets();
      });
      this.button("save", x, y + 380, 310, this::save);
      this.button("back", x + 330, y + 380, 310, this::onClose);
   }

   private void editRoute() {
      CompoundTag unit = null;

      for (Tag raw : this.network.getList("Units", 10)) {
         CompoundTag u = (CompoundTag)raw;
         if (this.selected.contains(u.getUUID("Id"))) {
            unit = u;
            break;
         }
      }

      if (unit == null) {
         this.error = "select_units";
      } else if (!unit.getString("Dimension").equals(this.minecraft.level.dimension().location().toString())) {
         this.error = "dimension";
      } else {
         LauncherBlock.Family family = LauncherBlock.Family.valueOf(unit.getString("Family"));
         MissileKind kind = family == LauncherBlock.Family.PEACEKEEPER
            ? MissileKind.PEACEKEEPER
            : (family == LauncherBlock.Family.JAGM ? MissileKind.JAGM : MissileKind.FP5);
         DeepStrikeMapScreen.openCommand(this, BlockPos.of(unit.getLong("Position")), family, kind, this.route, points -> {
            this.route.clear();
            this.route.addAll(points);
         });
      }
   }

   private void save() {
      try {
         int quantity = Integer.parseInt(this.count.getValue());
         int seconds = Integer.parseInt(this.interval.getValue());
         int delaySeconds = Integer.parseInt(this.delay.getValue());
         if (quantity < 1
            || quantity > 6
            || seconds < 6
            || seconds > 60
            || delaySeconds < 1
            || delaySeconds > 3600
            || this.route.isEmpty()
            || this.selected.size() > (Integer)DefenseRuntime.MAX_COMMAND_DUTY.get()) {
            throw new IllegalArgumentException();
         }

         if (!RadioFrequency.valid(this.frequency.getValue())) {
            this.error = "frequency";
            return;
         }

         CompoundTag p = new CompoundTag();
         if (this.existing != null && this.existing.hasUUID("Id")) {
            p.putUUID("Task", this.existing.getUUID("Id"));
         }

         p.putString("Name", this.name.getValue());
         p.putString("Frequency", this.frequency.getValue());
         p.putBoolean("ReadyOnly", this.readyOnly);
         p.putBoolean("Redstone", this.redstone);
         p.putBoolean("Ripple", this.ripple);
         p.putInt("WaveInterval", seconds * 20);
         p.putInt("LaunchDelay", delaySeconds * 20);
         ListTag assignments = new ListTag();

         for (UUID id : this.selected) {
            CompoundTag a = new CompoundTag();
            a.putUUID("Unit", id);
            a.putInt("Count", quantity);
            assignments.add(a);
         }

         p.put("Assignments", assignments);
         ListTag points = new ListTag();

         for (RoutePoint point : this.route) {
            CompoundTag r = new CompoundTag();
            r.putDouble("X", point.x());
            r.putDouble("Y", point.y());
            r.putDouble("Z", point.z());
            r.putDouble("Speed", point.speed());
            points.add(r);
         }

         p.put("Route", points);
         this.minecraft.setScreen(this.parent);
         CommandTabletNetwork.request("save", p);
      } catch (IllegalArgumentException var10) {
         this.error = "task_values";
         if (Boolean.getBoolean("deepstrike.commandTabletReview")) {
            throw new IllegalStateException(
               "Native task values count="
                  + this.count.getValue()
                  + " interval="
                  + this.interval.getValue()
                  + " delay="
                  + this.delay.getValue()
                  + " route="
                  + this.route
                  + " selected="
                  + this.selected,
               var10
            );
         }
      }
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      FlightUi.desktop(g, this.width, this.height);
      FlightUi.label(g, "DEEPSTRIKE / " + this.title.getString(), 40, 30, -1182997);
      int x = this.width / 2 - 320;
      int y = this.height / 2 - 200;
      FlightUi.panel(g, x - 22, y - 32, 684, 456, -182838503, -10854328);
      FlightUi.glyph(g, x - 13, y - 11, 2, -4668269);
      FlightUi.label(g, this.title.getString(), x + 3, y - 12, -1182997);
      g.fill(x, y + 76, x + 640, y + 77, -12236489);
      String[] keys = new String[]{"name", "count", "interval", "delay", "frequency"};
      int[] xs = new int[]{x, x, x + 220, x + 450, x};
      int[] ys = new int[]{y + 20, y + 90, y + 90, y + 90, y + 160};

      for (int i = 0; i < keys.length; i++) {
         FlightUi.label(g, CommandTabletScreen.tr(keys[i]).getString(), xs[i], ys[i], -5196890);
      }

      FlightUi.row(g, x, y + 317, 640, 30, false, this.route.isEmpty() ? -1000096 : -4668269);
      FlightUi.glyph(g, x + 10, y + 327, 2, this.route.isEmpty() ? -1000096 : -4668269);
      FlightUi.label(g, CommandTabletScreen.tr("route_summary", this.selected.size(), this.route.size()).getString(), x + 30, y + 325, -1182997);
      if (!this.error.isEmpty()) {
         FlightUi.label(g, CommandTabletScreen.tr("error." + this.error).getString(), x, y + 345, -26517);
      }

      super.render(g, mx, my, pt);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
