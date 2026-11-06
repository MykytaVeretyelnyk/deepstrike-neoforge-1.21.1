package dev.sbw.deepstrike.client.terminal;

import dev.krutlexik.skyfront.client.gui.ConsoleStyle;
import dev.krutlexik.skyfront.client.gui.TerminalButton;
import dev.sbw.deepstrike.client.CommandTabletScreen;
import dev.sbw.deepstrike.game.DefenseRuntime;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.game.RoutePoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Task editor of the Skyfront control panel (replaces the tablet's CommandTaskScreen). Same frame as {@link MissileTerminalScreen}:
 * fields and unit list inside the CRT, Save / Back on the bottom strip, route edited in {@link MissileRouteScreen} task mode.
 */
public class PanelTaskEditorScreen extends Screen {
   private static final int UNIT_ROWS = 3;
   private static final int UNIT_ROW_H = 18;
   private final MissileTerminalScreen parent;
   private final BlockPos terminalPos;
   private final PanelData.Task existing;
   private final boolean copy;
   private PanelData.Panel panel;
   private CompoundTag seenTag;
   private String name;
   private String count;
   private String interval;
   private String delay;
   private String frequency;
   private boolean readyOnly;
   private boolean redstone;
   private boolean ripple;
   private final Map<UUID, Integer> counts = new LinkedHashMap<>();
   private final List<RoutePoint> route = new ArrayList<>();
   private int unitScroll;
   private int refreshTimer;
   private String error = "";

   /** existing == null makes a new task; copy keeps the values of existing but sends no Task id. */
   PanelTaskEditorScreen(MissileTerminalScreen parent, BlockPos terminalPos, PanelData.Panel panel, PanelData.Task existing, boolean copy) {
      super(CommandTabletScreen.tr("task_editor"));
      this.parent = parent;
      this.terminalPos = terminalPos;
      this.panel = panel;
      this.existing = existing;
      this.copy = copy && existing != null;
      this.seenTag = MissileTerminalScreen.latestPanel(terminalPos);
      if (existing == null) {
         this.name = CommandTabletScreen.tr("default_task").getString();
         this.count = "1";
         this.interval = "6";
         this.delay = "1";
         this.frequency = "";
      } else {
         this.name = this.copy ? CommandTabletScreen.tr("copy_name", existing.name()).getString() : existing.name();
         this.count = String.valueOf(existing.assignments().isEmpty() ? 1 : Mth.clamp(existing.assignments().get(0).count(), 1, 6));
         this.interval = String.valueOf(Mth.clamp(existing.waveInterval() / 20, 6, 60));
         this.delay = String.valueOf(Mth.clamp(existing.launchDelay() / 20, 1, 3600));
         this.frequency = existing.frequency();
         this.readyOnly = existing.readyOnly();
         this.redstone = existing.redstone();
         this.ripple = existing.ripple();

         for (PanelData.Assignment a : existing.assignments()) {
            this.counts.put(a.unit(), Mth.clamp(a.count(), 1, 6));
         }

         for (PanelData.RoutePt p : existing.route()) {
            this.route.add(new RoutePoint(p.x(), p.y(), p.z(), p.speed()));
         }
      }
   }

   private int panelLeft() {
      return this.width / 2 - 170;
   }

   private int listTop() {
      return this.height / 2 - 62;
   }

   /** Top of the first field row (CRT top + 4). */
   private int base() {
      return this.listTop() - 2;
   }

   private int unitsTop() {
      return this.base() + 88;
   }

   private int maxUnitScroll() {
      return Math.max(0, this.panel.units().size() - UNIT_ROWS);
   }

   private EditBox box(int x, int y, int w, String value, int maxLength, boolean digits, Consumer<String> sink) {
      EditBox box = new EditBox(this.font, x, y, w, 13, Component.empty());
      box.setMaxLength(maxLength);
      if (digits) {
         box.setFilter(s -> s.matches("[0-9]*"));
      }

      box.setValue(value);
      box.setResponder(sink);
      return this.addRenderableWidget(box);
   }

   protected void init() {
      int colA = this.panelLeft() + 6;
      int colB = this.panelLeft() + 174;
      int by = this.base();
      this.box(colA, by + 9, 160, this.name, 32, false, s -> this.name = s);
      this.box(colB, by + 9, 160, this.frequency, 12, true, s -> this.frequency = s);
      this.box(colA, by + 32, 160, this.count, 1, true, this::countEdited);
      this.box(colB, by + 32, 160, this.interval, 2, true, s -> this.interval = s);
      this.box(colA, by + 55, 160, this.delay, 4, true, s -> this.delay = s);
      this.addRenderableWidget(
         new TerminalButton(colB, by + 55, 160, 13, Component.translatable("gui.deepstrike.panel.task.route_btn", new Object[]{this.route.size()}), b -> this.editRoute())
            .style(this.route.isEmpty() ? TerminalButton.Style.KEY : TerminalButton.Style.LIT)
      );
      int tw = 106;
      this.addRenderableWidget(this.toggle(colA, by + 70, tw, "gui.deepstrike.panel.task.t_ready", this.readyOnly, () -> this.readyOnly = !this.readyOnly));
      this.addRenderableWidget(this.toggle(colA + tw + 4, by + 70, tw, "gui.deepstrike.panel.task.redstone", this.redstone, () -> this.redstone = !this.redstone));
      this.addRenderableWidget(this.toggle(colA + 2 * (tw + 4), by + 70, tw, "gui.deepstrike.panel.task.ripple", this.ripple, () -> this.ripple = !this.ripple));
      List<PanelData.Unit> units = this.panel.units();
      int top = this.unitsTop();
      int left = this.panelLeft();

      for (int i = 0; i < UNIT_ROWS; i++) {
         int index = this.unitScroll + i;
         if (index >= units.size()) {
            break;
         }

         PanelData.Unit unit = units.get(index);
         int y = top + i * UNIT_ROW_H;
         boolean on = this.counts.containsKey(unit.id());
         TerminalButton select = new TerminalButton(left + 6, y, 16, 16, Component.literal(on ? "x" : ""), b -> {
            if (this.counts.containsKey(unit.id())) {
               this.counts.remove(unit.id());
            } else {
               this.counts.put(unit.id(), this.defaultCount());
            }

            this.rebuildWidgets();
         });
         this.addRenderableWidget(on ? select.style(TerminalButton.Style.LIT) : select);
         if (on) {
            this.addRenderableWidget(new TerminalButton(left + 290, y, 14, 16, Component.literal("-"), b -> this.bump(unit.id(), -1)));
            this.addRenderableWidget(new TerminalButton(left + 318, y, 14, 16, Component.literal("+"), b -> this.bump(unit.id(), 1)));
         }
      }

      int y = this.listTop() + 146 + 5;
      this.addRenderableWidget(
         new TerminalButton(left + 340 - 70, y, 70, 13, Component.translatable("gui.deepstrike.panel.task.cancel"), b -> this.onClose())
      );
      this.addRenderableWidget(
         new TerminalButton(left + 340 - 70 - 4 - 100, y, 100, 13, Component.translatable("gui.deepstrike.panel.task.save"), b -> this.save())
            .style(TerminalButton.Style.LIT)
      );
   }

   private TerminalButton toggle(int x, int y, int w, String key, boolean on, Runnable flip) {
      TerminalButton button = new TerminalButton(x, y, w, 15, Component.translatable(key), b -> {
         flip.run();
         this.rebuildWidgets();
      });
      return on ? button.style(TerminalButton.Style.LIT) : button;
   }

   private int parseInt(String s, int fallback) {
      try {
         return Integer.parseInt(s.trim());
      } catch (NumberFormatException var4) {
         return fallback;
      }
   }

   private int defaultCount() {
      return Mth.clamp(this.parseInt(this.count, 1), 1, 6);
   }

   private void countEdited(String value) {
      this.count = value;
      int v = this.parseInt(value, 0);
      if (v >= 1 && v <= 6) {
         this.counts.replaceAll((id, old) -> v);
      }
   }

   private void bump(UUID unit, int delta) {
      Integer old = this.counts.get(unit);
      if (old != null) {
         this.counts.put(unit, Mth.clamp(old + delta, 1, 6));
      }
   }

   /** The first assigned unit that still exists in the network, in network order. */
   private PanelData.Unit firstAssigned() {
      for (PanelData.Unit u : this.panel.units()) {
         if (this.counts.containsKey(u.id())) {
            return u;
         }
      }

      return null;
   }

   private void editRoute() {
      PanelData.Unit unit = this.firstAssigned();
      BlockPos origin;
      int family;
      boolean rings;
      if (unit != null) {
         if (this.minecraft.level != null && !unit.dimension().equals(this.minecraft.level.dimension().location().toString())) {
            this.error = "dimension";
            return;
         }

         origin = BlockPos.of(unit.position());
         family = unit.familyIndex();
         rings = family >= 0;
      } else {
         origin = this.minecraft.player != null ? this.minecraft.player.blockPosition() : this.terminalPos;
         family = -1;
         rings = false;
      }

      this.error = "";
      this.minecraft
         .setScreen(
            new MissileRouteScreen(this, this.terminalPos, origin, family, this.route, rings, points -> {
               this.route.clear();
               this.route.addAll(points);
            })
         );
   }

   private void save() {
      int quantity = this.parseInt(this.count, -1);
      int seconds = this.parseInt(this.interval, -1);
      int delaySeconds = this.parseInt(this.delay, -1);
      if (quantity < 1 || quantity > 6 || seconds < 6 || seconds > 60 || delaySeconds < 1 || delaySeconds > 3600) {
         this.error = "task_values";
         return;
      }

      if (!RadioFrequency.valid(this.frequency)) {
         this.error = "frequency";
         return;
      }

      List<PanelData.Unit> assigned = new ArrayList<>();
      for (PanelData.Unit u : this.panel.units()) {
         if (this.counts.containsKey(u.id())) {
            assigned.add(u);
         }
      }

      if (assigned.isEmpty()) {
         this.error = "select_units";
         return;
      }

      if (this.route.isEmpty() || this.route.size() > 32) {
         this.error = "route";
         return;
      }

      int duty = dutyCap();
      if (duty > 0 && assigned.size() > duty) {
         this.error = "duty_limit";
         return;
      }

      CompoundTag p = new CompoundTag();
      if (this.existing != null && !this.copy) {
         p.putUUID("Task", this.existing.id());
      }

      p.putString("Name", this.name.isBlank() ? CommandTabletScreen.tr("default_task").getString() : this.name);
      p.putString("Frequency", this.frequency);
      p.putBoolean("ReadyOnly", this.readyOnly);
      p.putBoolean("Redstone", this.redstone);
      p.putBoolean("Ripple", this.ripple);
      p.putInt("WaveInterval", seconds * 20);
      p.putInt("LaunchDelay", delaySeconds * 20);
      ListTag assignments = new ListTag();

      for (PanelData.Unit u : assigned) {
         CompoundTag a = new CompoundTag();
         a.putUUID("Unit", u.id());
         a.putInt("Count", Mth.clamp(this.counts.get(u.id()), 1, 6));
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
      MissileTerminalScreen.sendPanel(this.terminalPos, "save", p);
      this.parent.afterEditor();
      this.minecraft.setScreen(this.parent);
   }

   private static int dutyCap() {
      try {
         return DefenseRuntime.MAX_COMMAND_DUTY.get();
      } catch (RuntimeException var1) {
         return 0;
      }
   }

   private static int salvoCap() {
      try {
         return DefenseRuntime.MAX_COMMAND_SALVO.get();
      } catch (RuntimeException var1) {
         return 0;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (mouseY >= (double)this.unitsTop() && this.maxUnitScroll() > 0) {
         int next = Mth.clamp(this.unitScroll - (int)Math.signum(scrollY), 0, this.maxUnitScroll());
         if (next != this.unitScroll) {
            this.unitScroll = next;
            this.rebuildWidgets();
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public void tick() {
      super.tick();
      CompoundTag tag = MissileTerminalScreen.latestPanel(this.terminalPos);
      if (tag != null && tag != this.seenTag) {
         this.seenTag = tag;
         int before = this.panel.units().size();
         this.panel = PanelData.parse(tag);
         this.unitScroll = Math.min(this.unitScroll, this.maxUnitScroll());
         if (before != this.panel.units().size()) {
            this.rebuildWidgets();
         }
      }

      if (++this.refreshTimer >= 40) {
         this.refreshTimer = 0;
         MissileTerminalScreen.sendPanel(this.terminalPos, "refresh", new CompoundTag());
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBlurredBackground(partialTick);
      int left = this.panelLeft();
      int listTop = this.listTop();
      int top = listTop - 30;
      int screenTop = listTop - 6;
      int screenBottom = listTop + 146;
      int bottom = screenBottom + 21;
      ConsoleStyle.panel(graphics, left - 10, top, left + 340 + 10, bottom);
      ConsoleStyle.plate(graphics, this.font, this.width / 2, top + 6, this.title);
      ConsoleStyle.crt(graphics, left - 2, screenTop, left + 340 + 2, screenBottom);
      int by = this.base();
      int colA = left + 6;
      int colB = left + 174;
      int dim = ConsoleStyle.AMBER_DIM;
      graphics.drawString(this.font, Component.translatable("gui.deepstrike.panel.task.name"), colA, by, dim, false);
      graphics.drawString(this.font, Component.translatable("gui.deepstrike.panel.task.frequency"), colB, by, dim, false);
      graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.deepstrike.panel.task.count").getString(), 160), colA, by + 23, dim, false);
      graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.deepstrike.panel.task.interval").getString(), 160), colB, by + 23, dim, false);
      graphics.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("gui.deepstrike.panel.task.delay").getString(), 160), colA, by + 46, dim, false);
      graphics.drawString(this.font, Component.translatable("gui.deepstrike.panel.task.route"), colB, by + 46, dim, false);
      this.renderUnits(graphics, left);
      int total = 0;
      for (int c : this.counts.values()) {
         total += c;
      }

      Component message;
      int color;
      if (!this.error.isEmpty()) {
         message = CommandTabletScreen.tr("error." + this.error);
         color = ConsoleStyle.AMBER_RED;
      } else {
         int duty = dutyCap();
         int salvo = salvoCap();
         message = Component.translatable(
            "gui.deepstrike.panel.task.total",
            new Object[]{this.counts.size(), duty > 0 ? String.valueOf(duty) : "?", total, salvo > 0 ? String.valueOf(salvo) : "?"}
         );
         color = ConsoleStyle.AMBER;
      }

      ConsoleStyle.lamps(graphics, left - 2, bottom - 13, ConsoleStyle.LAMP_GREEN, ConsoleStyle.LAMP_GREEN, ConsoleStyle.LAMP_AMBER);
      graphics.drawString(this.font, this.font.plainSubstrByWidth(message.getString(), 340 - 70 - 4 - 100 - 8 - 24), left + 22, bottom - 15, color, false);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void renderUnits(GuiGraphics graphics, int left) {
      List<PanelData.Unit> units = this.panel.units();
      int top = this.unitsTop();
      if (units.isEmpty()) {
         graphics.drawString(
            this.font, this.font.plainSubstrByWidth(Component.translatable("gui.deepstrike.panel.task.no_units").getString(), 316), left + 6, top + 20, ConsoleStyle.PHOSPHOR, false
         );
         return;
      }

      for (int i = 0; i < UNIT_ROWS; i++) {
         int index = this.unitScroll + i;
         if (index >= units.size()) {
            break;
         }

         PanelData.Unit u = units.get(index);
         int y = top + i * UNIT_ROW_H;
         Integer n = this.counts.get(u.id());
         String text = Component.translatable(
               "gui.deepstrike.panel.task.unit_row",
               new Object[]{u.name(), MissileTerminalScreen.familyName(u.familyIndex()), u.ammo(), CommandTabletScreen.tr("state." + u.state().toLowerCase(Locale.ROOT))}
            )
            .getString();
         graphics.drawString(this.font, this.font.plainSubstrByWidth(text, 260), left + 26, y + 4, n != null ? ConsoleStyle.AMBER : ConsoleStyle.PHOSPHOR, false);
         if (n != null) {
            graphics.drawCenteredString(this.font, String.valueOf(n), left + 311, y + 4, ConsoleStyle.AMBER);
         }
      }

      if (this.maxUnitScroll() > 0) {
         int x0 = left + 335;
         int track = UNIT_ROWS * UNIT_ROW_H - 2;
         int thumb = Math.max(8, track * UNIT_ROWS / units.size());
         int thumbY = top + (track - thumb) * this.unitScroll / this.maxUnitScroll();
         graphics.fill(x0, top, x0 + 3, top + track, -16445689);
         graphics.fill(x0, thumbY, x0 + 3, thumbY + thumb, ConsoleStyle.PHOSPHOR);
      }
   }

   /** Esc / Back leaves without saving. */
   public void onClose() {
      this.parent.afterEditor();
      this.minecraft.setScreen(this.parent);
   }

   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
   }

   public boolean isPauseScreen() {
      return false;
   }
}
