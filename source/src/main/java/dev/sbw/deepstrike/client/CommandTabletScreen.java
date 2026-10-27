package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.CommandCenterData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public final class CommandTabletScreen extends Screen {
   public static CompoundTag latestSnapshot = new CompoundTag();
   private CompoundTag snapshot = new CompoundTag();
   private String tab = "units";
   private String error = "";
   private int scroll;
   private int ticks;
   private UUID task;
   private final Set<UUID> selected = new LinkedHashSet<>();
   private EditBox nickname;
   private EditBox filter;
   private final Screen parent;

   public CommandTabletScreen() {
      this(null);
   }

   public CommandTabletScreen(Screen parent) {
      super(tr("title"));
      this.parent = parent;
   }

   public static Component tr(String key, Object... args) {
      return Component.translatable("command.deepstrike." + key, args);
   }

   public static void open() {
      Minecraft.getInstance().setScreen(new CommandTabletScreen());
      CommandTabletNetwork.request("refresh", new CompoundTag());
   }

   public static void accept(CompoundTag tag) {
      if (tag != null) {
         latestSnapshot = tag;
      }

      if (tag != null && Minecraft.getInstance().screen instanceof CommandTabletScreen screen) {
         boolean login = screen.snapshot.contains("Network");
         screen.snapshot = tag;
         screen.error = tag.getString("Error");
         if (login != tag.contains("Network")) {
            screen.rebuildWidgets();
         }
      }
   }

   private CompoundTag network() {
      return this.snapshot.getCompound("Network");
   }

   private int role() {
      return this.snapshot.getInt("Role");
   }

   private static List<CompoundTag> rows(CompoundTag tag, String key) {
      List<CompoundTag> out = new ArrayList<>();

      for (Tag raw : tag.getList(key, 10)) {
         out.add((CompoundTag)raw);
      }

      return out;
   }

   private void action(String action, CompoundTag data) {
      CommandTabletNetwork.request(action, data);
   }

   private CompoundTag taskPayload() {
      CompoundTag p = new CompoundTag();
      if (this.task != null) {
         p.putUUID("Task", this.task);
      }

      return p;
   }

   private Button button(String key, int x, int y, int w, Runnable run) {
      QuietButton button = (QuietButton)this.addRenderableWidget(Button.builder(tr(key), b -> run.run()).bounds(x, y, w, 26).build(QuietButton::new));
      return button.selected(key.equals(this.tab));
   }

   protected void init() {
      if (!this.snapshot.contains("Network")) {
         this.nickname = (EditBox)this.addRenderableWidget(new InstrumentField(this.font, this.width / 2 - 160, this.height / 2 - 8, 320, 26, tr("nickname")));
         this.nickname.setMaxLength(16);
         this.nickname.setHint(tr("nickname"));
         this.nickname.setValue(this.minecraft.player == null ? "" : this.minecraft.player.getGameProfile().getName());
         this.button("login", this.width / 2 - 160, this.height / 2 + 34, 320, () -> {
            CompoundTag p = new CompoundTag();
            p.putString("Nickname", this.nickname.getValue());
            this.action("login", p);
         });
      } else {
         int x = 36;

         for (String t : List.of("units", "tasks", "flights", "defense")) {
            this.button(t, x, 66, 150, () -> {
               this.tab = t;
               this.scroll = 0;
               this.rebuildWidgets();
            });
            x += 160;
         }

         this.button("journal", this.width - 318, 66, 130, () -> {
            this.tab = "journal";
            this.scroll = 0;
            this.rebuildWidgets();
         });
         this.button("access", this.width - 178, 66, 140, () -> {
            this.tab = "access";
            this.scroll = 0;
            this.rebuildWidgets();
         });
         if (this.tab.equals("units")) {
            this.filter = (EditBox)this.addRenderableWidget(new InstrumentField(this.font, 40, 116, 320, 24, tr("filter")));
            this.filter.setMaxLength(32);
            this.filter.setHint(tr("filter"));
            this.button("route_selected", this.width - 300, this.height - 66, 260, () -> this.editTask(null)).active = this.role() >= 1;
            this.button("map", this.width - 300, 116, 260, () -> {
               List<CompoundTag> us = this.visibleUnits();
               if (!us.isEmpty()) {
                  DeepStrikeMapScreen.openOverview(this, us.get(0));
               }
            });
            this.button("unit_settings", 400, this.height - 66, 210, () -> {
               if (this.selected.size() != 1) {
                  this.error = "select_one";
               } else {
                  for (CompoundTag u : rows(this.network(), "Units")) {
                     if (this.selected.contains(u.getUUID("Id"))) {
                        this.minecraft.setScreen(new CommandUnitScreen(this, u));
                        break;
                     }
                  }
               }
            });
            this.button("select_all", 40, this.height - 66, 170, () -> {
               for (CompoundTag u : this.visibleUnits()) {
                  this.selected.add(u.getUUID("Id"));
               }
            });
            this.button("clear_selection", 220, this.height - 66, 170, this.selected::clear);
         } else if (this.tab.equals("tasks")) {
            int bx = 40;

            for (String act : List.of("arm", "launch", "disarm")) {
               this.button(act, bx, this.height - 66, 150, () -> this.action(act, this.taskPayload())).active = this.role() >= 1;
               bx += 160;
            }

            this.button("edit", bx, this.height - 66, 120, () -> this.editTask(this.currentTask())).active = this.role() >= 1;
            bx += 130;
            this.button("relay_select", bx, this.height - 66, 180, () -> this.action("select_relay", this.taskPayload())).active = this.role() >= 2;
            this.button("details", 40, this.height - 104, 150, () -> {
               CompoundTag t = this.currentTask();
               if (t != null) {
                  this.minecraft.setScreen(new CommandTaskDetailsScreen(this, this.network(), t));
               }
            });
            this.button("copy", 200, this.height - 104, 100, () -> {
               CompoundTag t = this.currentTask();
               if (t != null) {
                  CompoundTag copy = t.copy();
                  copy.remove("Id");
                  copy.putString("Name", tr("copy_name", t.getString("Name")).getString());
                  this.minecraft.setScreen(new CommandTaskScreen(this, this.network(), copy, Set.of()));
               }
            }).active = this.role() >= 1;
            this.button("delete", 310, this.height - 104, 126, () -> this.action("delete", this.taskPayload())).active = this.role() >= 2;
         } else if (this.tab.equals("access")) {
            this.nickname = (EditBox)this.addRenderableWidget(new InstrumentField(this.font, 40, 124, 260, 24, tr("nickname")));
            this.nickname.setMaxLength(16);
            this.nickname.setHint(tr("nickname"));
            int bx = 316;

            for (int i = -1; i <= 2; i++) {
               int r = i;
               this.button(i < 0 ? "remove" : (i == 0 ? "observer" : (i == 1 ? "operator" : "admin")), bx, 122, 146, () -> {
                  CompoundTag p = new CompoundTag();
                  p.putString("Nickname", this.nickname.getValue());
                  p.putInt("Role", r);
                  this.action("member", p);
               }).active = this.role() >= 2;
               bx += 156;
            }

            this.button("switch_network", 40, this.height - 66, 250, () -> {
               this.snapshot = new CompoundTag();
               this.rebuildWidgets();
            });
         }
      }
   }

   private static int statusColor(String state) {
      return switch (state) {
         case "ERROR" -> -1076610;
         case "DUTY", "DONE" -> -6696818;
         case "PREPARING", "RECOVERING", "COUNTDOWN" -> -1000096;
         case "OFFLINE" -> -5196890;
         default -> -4668269;
      };
   }

   private int rowsBottom() {
      return this.height - (this.tab.equals("tasks") ? 148 : 100);
   }

   private List<CompoundTag> visibleUnits() {
      String f = this.filter == null ? "" : this.filter.getValue().toLowerCase(Locale.ROOT);
      return rows(this.network(), "Units")
         .stream()
         .filter(u -> (u.getString("Name") + " " + u.getString("Group")).toLowerCase(Locale.ROOT).contains(f))
         .toList();
   }

   private CompoundTag currentTask() {
      return rows(this.network(), "Tasks").stream().filter(t -> this.task != null && t.getUUID("Id").equals(this.task)).findFirst().orElse(null);
   }

   private void editTask(CompoundTag existing) {
      if (existing == null && this.selected.isEmpty()) {
         this.error = "select_units";
      } else if (existing != null && CommandCenterData.active(existing)) {
         this.error = "busy";
      } else {
         this.minecraft.setScreen(new CommandTaskScreen(this, this.network(), existing, this.selected));
      }
   }

   public void tick() {
      super.tick();
      if (++this.ticks % 10 == 0) {
         this.action("refresh", new CompoundTag());
      }
   }

   private void label(GuiGraphics g, String s, int x, int y, int color) {
      FlightUi.label(g, s, x, y, color);
   }

   private static String modelName(String id) {
      for (MissileKind kind : MissileKind.values()) {
         if (kind.id.equals(id)) {
            return kind.title().getString();
         }
      }

      return "—";
   }

   private String limited(String s, int max) {
      return FlightUi.clip(s, max);
   }

   private String state(CompoundTag tag, String key) {
      return tr("state." + tag.getString(key).toLowerCase(Locale.ROOT)).getString();
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      FlightUi.desktop(g, this.width, this.height);
      this.label(g, "DEEPSTRIKE / " + tr("title").getString(), 40, 30, -1182997);
      if (!this.snapshot.contains("Network")) {
         int x = this.width / 2 - 160;
         int y = this.height / 2;
         FlightUi.panel(g, x - 24, y - 90, 368, 210, -182838503, -4668269);
         this.label(g, tr("login_hint").getString(), x, y - 65, -1182997);
         this.label(g, tr("no_registration").getString(), x, y - 42, -5196890);
         super.render(g, mx, my, pt);
         if (!this.error.isEmpty()) {
            this.label(g, tr("error." + this.error).getString(), x, y + 80, -26517);
         }
      } else {
         this.label(g, this.network().getString("Nickname") + "  /  " + tr("role." + this.role()).getString(), this.width - 350, 30, -5196890);
         g.fill(40, 108, this.width - 40, 109, -10854328);
         int top = !this.tab.equals("units") && !this.tab.equals("access") ? 130 : 164;
         int bottom = this.rowsBottom();
         int rowH = 48;
         int capacity = Math.max(1, (bottom - top) / rowH);
         List<CompoundTag> list = this.tab.equals("units")
            ? this.visibleUnits()
            : (
               this.tab.equals("tasks")
                  ? rows(this.network(), "Tasks")
                  : (
                     this.tab.equals("journal")
                        ? rows(this.network(), "Journal")
                        : (
                           this.tab.equals("access")
                              ? rows(this.network(), "Members")
                              : (this.tab.equals("defense") ? rows(this.snapshot.getCompound("Defense"), "Units") : this.flightRows())
                        )
                  )
            );
         this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, list.size() - capacity)));

         for (int i = this.scroll; i < Math.min(list.size(), this.scroll + capacity); i++) {
            CompoundTag r = list.get(i);
            int y = top + (i - this.scroll) * rowH;
            boolean active = this.tab.equals("units") && this.selected.contains(r.getUUID("Id"))
               || this.tab.equals("tasks") && r.getUUID("Id").equals(this.task);
            int accent = this.tab.equals("tasks")
               ? statusColor(r.getString("State"))
               : (this.tab.equals("units") ? statusColor(r.getString("State")) : -4668269);
            FlightUi.row(g, 40, y, this.width - 80, 42, active, accent);
            if (this.tab.equals("units")) {
               this.label(g, (active ? "[x] " : "[ ] ") + r.getString("Name"), 54, y + 7, -1182997);
               BlockPos pos = BlockPos.of(r.getLong("Position"));
               this.label(g, r.getString("Group") + "  " + pos.toShortString(), 54, y + 25, -5196890);
               this.label(
                  g, modelName(r.getString("Missile")) + "  " + r.getInt("Ammo") + "  /  " + this.state(r, "State"), this.width - 430, y + 10, -1182997
               );
            } else if (!this.tab.equals("tasks")) {
               if (this.tab.equals("defense")) {
                  this.label(g, r.getString("Name"), 54, y + 7, -1182997);
                  this.label(g, tr(r.getBoolean("Online") ? "connected" : "disconnected").getString(), 54, y + 25, -5196890);
               } else if (this.tab.equals("journal")) {
                  this.label(
                     g,
                     "T+"
                        + r.getLong("Tick")
                        + "  "
                        + this.limited(
                           r.contains("Key")
                              ? tr(
                                    "journal." + r.getString("Key"),
                                    r.getString("Actor"),
                                    r.getString("Key").equals("flight")
                                       ? Component.translatable("journal.deepstrike.outcome." + r.getString("Object").toLowerCase(Locale.ROOT))
                                       : r.getString("Object")
                                 )
                                 .getString()
                              : r.getString("Text"),
                           this.width - 230
                        ),
                     54,
                     y + 14,
                     -1182997
                  );
               } else if (this.tab.equals("access")) {
                  this.label(g, r.getString("Nickname") + " / " + tr("role." + r.getInt("Role")).getString(), 54, y + 14, -1182997);
               } else {
                  this.label(g, r.getString("Name"), 54, y + 7, -1182997);
                  this.label(g, r.getString("Telemetry"), 54, y + 25, -5196890);
               }
            } else {
               this.label(g, r.getString("Name"), 54, y + 7, -1182997);
               String line = "";

               for (CompoundTag a : rows(r, "Assignments")) {
                  line = line + (line.isEmpty() ? "" : " · ") + a.getInt("Launched") + "/" + a.getInt("Count");
               }

               int ready = 0;

               for (CompoundTag a : rows(r, "Assignments")) {
                  if (a.getString("Phase").equals("READY")) {
                     ready++;
                  }
               }

               line = tr("ready_count", ready, rows(r, "Assignments").size()).getString() + "  / " + line;
               this.label(
                  g,
                  this.limited(line + (r.getString("Error").isEmpty() ? "" : " / " + tr("error." + r.getString("Error")).getString()), this.width - 400),
                  54,
                  y + 25,
                  -5196890
               );
               String status = this.state(r, "State");
               if (r.getString("State").equals("COUNTDOWN")) {
                  status = status + " " + Math.max(0L, (r.getLong("StartAt") - this.snapshot.getLong("Now") + 19L) / 20L) + "s";
               }

               FlightUi.badge(g, status, this.width - 320, y + 12, statusColor(r.getString("State")));
            }
         }

         if (list.isEmpty()) {
            this.label(g, tr(this.tab.equals("units") ? "bind_hint" : "empty").getString(), 54, top + 20, -5196890);
         }

         if (this.tab.equals("units")) {
            this.label(g, tr("selected", this.selected.size()).getString(), 380, 122, -5196890);
         }

         if (!this.error.isEmpty()) {
            this.label(g, tr("error." + this.error).getString(), 40, this.tab.equals("tasks") ? this.height - 126 : this.height - 88, -26517);
         }

         super.render(g, mx, my, pt);
      }
   }

   private List<CompoundTag> flightRows() {
      List<CompoundTag> result = rows(this.snapshot, "Flights").stream().<CompoundTag>map(CompoundTag::copy).toList();

      for (CompoundTag f : result) {
         f.putString("Name", f.getString("Name") + " / " + modelName(f.getString("Model")));
         f.putString(
            "Telemetry",
            String.format(
               Locale.ROOT,
               "X %.0f  Y %.0f  Z %.0f / %.0f km/h%s",
               f.getDouble("X"),
               f.getDouble("Y"),
               f.getDouble("Z"),
               f.getDouble("Speed"),
               (f.getInt("Eta") >= 0 ? " / ETA ~" + f.getInt("Eta") + "s" : "") + (f.getBoolean("Camera") ? " / CAM" : "")
            )
         );
      }

      return result;
   }

   public boolean mouseClicked(double x, double y, int button) {
      if (button == 0 && this.snapshot.contains("Network")) {
         int top = !this.tab.equals("units") && !this.tab.equals("access") ? 130 : 164;
         if (x >= 40.0 && x < this.width - 40 && y >= top && y < this.rowsBottom()) {
            int index = this.scroll + (int)(y - top) / 48;
            List<CompoundTag> rows = this.tab.equals("units")
               ? this.visibleUnits()
               : (
                  this.tab.equals("tasks")
                     ? rows(this.network(), "Tasks")
                     : (
                        this.tab.equals("flights")
                           ? this.flightRows()
                           : (this.tab.equals("defense") ? rows(this.snapshot.getCompound("Defense"), "Units") : List.of())
                     )
               );
            if (index < rows.size()) {
               CompoundTag r = rows.get(index);
               if (this.tab.equals("units")) {
                  UUID id = r.getUUID("Id");
                  if (button == 0 && !this.selected.remove(id)) {
                     this.selected.add(id);
                  }
               } else if (this.tab.equals("tasks")) {
                  this.task = r.getUUID("Id");
               } else if (this.tab.equals("defense")) {
                  CompoundTag p = new CompoundTag();
                  p.putUUID("Unit", r.getUUID("Id"));
                  this.action("defense_select", p);
                  this.minecraft.setScreen(new CommandDefenseScreen(this, r));
               } else if (this.tab.equals("flights")) {
                  CompoundTag p = new CompoundTag();
                  p.putUUID("Mission", r.getUUID("Mission"));
                  this.action("camera", p);
               }

               return true;
            }
         }
      }

      return super.mouseClicked(x, y, button);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
      this.scroll += delta > 0.0 ? -1 : 1;
      return true;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
