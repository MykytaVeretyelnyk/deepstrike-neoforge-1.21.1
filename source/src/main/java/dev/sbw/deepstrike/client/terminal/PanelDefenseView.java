package dev.sbw.deepstrike.client.terminal;

import dev.krutlexik.skyfront.client.gui.ConsoleStyle;
import dev.krutlexik.skyfront.client.gui.TerminalButton;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The "Air defense" sub-tab of the missile terminal: DeepStrike air-defense units of the logged-in command network, the radar
 * contacts of the selected unit and the fire / reload / toggle buttons. Layout (relative to the panel left and the list top, the
 * CRT content is 120 px high): units list x 2..142, rows 12 px, 4 visible; status lines below it at y 52..92; contacts list
 * x 146..326, rows 12 px, 8 visible; button row at y 98, 20 px high. Everything except the buttons is drawn directly.
 */
final class PanelDefenseView {
   private static final int ROW = 12;
   private static final int UNIT_ROWS = 4;
   private static final int CONTACT_ROWS = 8;
   private static final int UNIT_X = 2;
   private static final int UNIT_W = 140;
   private static final int CONTACT_X = 146;
   private static final int CONTACT_W = 180;
   private static final int BUTTON_Y = 98;
   private static final int SELECTED = 0x4044AA44;
   private CompoundTag defense = new CompoundTag();
   private final List<CompoundTag> units = new ArrayList<>();
   private final List<CompoundTag> contacts = new ArrayList<>();
   private UUID selectedUnit;
   private UUID selectedContact;
   private int unitScroll;
   private int contactScroll;
   private int role;
   private BiConsumer<String, CompoundTag> sender = (a, p) -> {
   };
   private TerminalButton fire0;
   private TerminalButton fire1;
   private TerminalButton reload;
   private TerminalButton toggle;

   /** Takes the newest panel snapshot (root tag). Defensive: every field may be missing. */
   void accept(CompoundTag root) {
      this.role = root == null ? 0 : root.getInt("Role");
      this.defense = root != null && root.contains("Defense", 10) ? root.getCompound("Defense") : new CompoundTag();
      this.units.clear();
      this.contacts.clear();

      for (Tag raw : this.defense.getList("Units", 10)) {
         this.units.add((CompoundTag)raw);
      }

      CompoundTag radar = this.radar();
      for (Tag raw : radar.getList("Contacts", 10)) {
         CompoundTag c = (CompoundTag)raw;
         if (c.hasUUID("Id")) {
            this.contacts.add(c);
         }
      }

      if (this.selectedUnit == null && radar.hasUUID("Id")) {
         this.selectedUnit = radar.getUUID("Id");
      }

      if (this.selectedContact != null && this.contact(this.selectedContact) == null) {
         this.selectedContact = null;
      }

      this.unitScroll = Math.min(this.unitScroll, Math.max(0, this.units.size() - UNIT_ROWS));
      this.contactScroll = Math.min(this.contactScroll, Math.max(0, this.contacts.size() - CONTACT_ROWS));
   }

   private CompoundTag radar() {
      return this.defense.getCompound("Radar");
   }

   /** The radar block belongs to the selected unit only once the server has switched to it. */
   private boolean radarMatches() {
      CompoundTag radar = this.radar();
      return this.selectedUnit != null && radar.hasUUID("Id") && radar.getUUID("Id").equals(this.selectedUnit);
   }

   private CompoundTag contact(UUID id) {
      for (CompoundTag c : this.contacts) {
         if (c.getUUID("Id").equals(id)) {
            return c;
         }
      }

      return null;
   }

   void build(int left, int listTop, Consumer<TerminalButton> adder, BiConsumer<String, CompoundTag> sender) {
      this.sender = sender;
      int y = listTop + BUTTON_Y;
      this.fire0 = new TerminalButton(left + 2, y, 80, 20, Component.literal("TAMIR"), b -> this.fire(0)).style(TerminalButton.Style.LAUNCH);
      this.fire1 = new TerminalButton(left + 85, y, 80, 20, Component.literal("PAC-3"), b -> this.fire(1)).style(TerminalButton.Style.LAUNCH);
      this.reload = new TerminalButton(
         left + 168, y, 80, 20, Component.translatable("gui.deepstrike.panel.defense.reload"), b -> this.sender.accept("defense_reload", new CompoundTag())
      );
      this.toggle = new TerminalButton(
         left + 251, y, 80, 20, Component.translatable("gui.deepstrike.panel.defense.auto"), b -> this.sender.accept("defense_toggle", new CompoundTag())
      );
      adder.accept(this.fire0);
      adder.accept(this.fire1);
      adder.accept(this.reload);
      adder.accept(this.toggle);
   }

   private void fire(int warhead) {
      if (this.selectedContact != null) {
         CompoundTag p = new CompoundTag();
         p.putUUID("Target", this.selectedContact);
         p.putInt("Warhead", warhead);
         this.sender.accept("defense_fire", p);
      }
   }

   private void updateButtons() {
      if (this.fire0 == null) {
         return;
      }

      CompoundTag radar = this.radar();
      boolean match = this.radarMatches();
      boolean iron = match && radar.getBoolean("Iron");
      boolean control = match && this.role >= 1 && radar.getBoolean("Controllable") && radar.getBoolean("Online");
      CompoundTag target = this.selectedContact == null ? null : this.contact(this.selectedContact);
      boolean can = control && target != null && !target.getBoolean("Friendly");
      this.fire0.setMessage(Component.literal(iron ? "TAMIR" : "PAC-2"));
      this.fire0.active = can && radar.getInt("Ready0") > 0;
      this.fire1.visible = !iron;
      this.fire1.active = can && radar.getInt("Ready1") > 0;
      this.reload.active = control;
      this.toggle.active = control;
      String key = iron
         ? (radar.getBoolean("Enabled") ? "gui.deepstrike.panel.defense.auto_on" : "gui.deepstrike.panel.defense.auto_off")
         : (radar.getBoolean("Enabled") ? "gui.deepstrike.panel.defense.stow" : "gui.deepstrike.panel.defense.deploy");
      this.toggle.setMessage(Component.translatable(key));
   }

   void render(GuiGraphics g, Font font, int left, int listTop) {
      this.updateButtons();
      if (this.units.isEmpty()) {
         int y = listTop + 6;
         for (var line : font.split(Component.translatable("gui.deepstrike.panel.defense.empty"), 316)) {
            g.drawString(font, line, left + 8, y, ConsoleStyle.PHOSPHOR, false);
            y += 10;
         }

         return;
      }

      CompoundTag radar = this.radar();
      boolean match = this.radarMatches();
      CompoundTag own = this.radar();
      double ox = own.getDouble("X");
      double oz = own.getDouble("Z");
      int shownUnits = Math.min(UNIT_ROWS, this.units.size() - this.unitScroll);

      for (int i = 0; i < shownUnits; i++) {
         CompoundTag u = this.units.get(this.unitScroll + i);
         int y = listTop + i * ROW;
         boolean chosen = u.hasUUID("Id") && u.getUUID("Id").equals(this.selectedUnit);
         if (chosen) {
            g.fill(left + UNIT_X, y, left + UNIT_X + UNIT_W, y + ROW - 1, SELECTED);
         }

         String coords = u.contains("X") ? String.format(Locale.ROOT, "%.0f %.0f", u.getDouble("X"), u.getDouble("Z")) : "—";
         int coordsW = font.width(coords);
         int color = u.getBoolean("Online") ? -2039584 : ConsoleStyle.AMBER_RED;
         this.line(g, font, u.getString("Name"), left + UNIT_X + 2, y + 2, UNIT_W - coordsW - 8, color);
         g.drawString(font, coords, left + UNIT_X + UNIT_W - coordsW - 2, y + 2, ConsoleStyle.AMBER_DIM, false);
      }

      this.scrollbar(g, left + UNIT_X + UNIT_W, listTop, UNIT_ROWS, this.units.size(), this.unitScroll);
      int sy = listTop + 52;
      if (!match) {
         this.line(g, font, Component.translatable("gui.deepstrike.panel.defense.select").getString(), left + 4, sy, UNIT_W, ConsoleStyle.AMBER);
      } else {
         boolean iron = radar.getBoolean("Iron");
         boolean online = radar.getBoolean("Online");
         String state = Component.translatable(online ? "gui.deepstrike.panel.defense.online" : "gui.deepstrike.panel.defense.offline").getString();
         String mode = Component.translatable(
            iron
               ? (radar.getBoolean("Enabled") ? "gui.deepstrike.panel.defense.auto_on" : "gui.deepstrike.panel.defense.auto_off")
               : (radar.getBoolean("Enabled") ? "gui.deepstrike.panel.defense.deployed" : "gui.deepstrike.panel.defense.stowed")
         ).getString();
         this.line(g, font, state + " / " + mode, left + 4, sy, UNIT_W, online ? ConsoleStyle.PHOSPHOR : ConsoleStyle.AMBER_RED);
         String ammo = iron
            ? "TAMIR: " + radar.getInt("Ready0") + (radar.getInt("Reserve0") > 0 ? " (+" + radar.getInt("Reserve0") + ")" : "")
            : "PAC-2: " + radar.getInt("Ready0") + "  PAC-3: " + radar.getInt("Ready1");
         this.line(g, font, ammo, left + 4, sy + 10, UNIT_W, ConsoleStyle.AMBER);
         int reloadTicks = radar.getInt("ReloadTicks");
         String second;
         if (reloadTicks > 0) {
            second = Component.translatable("gui.deepstrike.panel.defense.reloading", (reloadTicks + 19) / 20).getString();
         } else if (iron) {
            second = Component.translatable(
               radar.getBoolean("Aircraft") ? "gui.deepstrike.panel.defense.aircraft_on" : "gui.deepstrike.panel.defense.aircraft_off"
            ).getString();
         } else {
            second = Component.translatable(
               radar.getBoolean("RadarConnected") ? "gui.deepstrike.panel.defense.radar_ok" : "gui.deepstrike.panel.defense.radar_none"
            ).getString();
         }

         this.line(g, font, second, left + 4, sy + 20, UNIT_W, ConsoleStyle.AMBER_DIM);
         String third = iron && radar.getInt("ZoneR") > 0
            ? Component.translatable("gui.deepstrike.panel.defense.zone", radar.getInt("ZoneX"), radar.getInt("ZoneZ"), radar.getInt("ZoneR")).getString()
            : Component.translatable("gui.deepstrike.panel.defense.range", (int)radar.getDouble("Range")).getString();
         this.line(g, font, third, left + 4, sy + 30, UNIT_W, ConsoleStyle.AMBER_DIM);
      }

      if (!match || this.contacts.isEmpty()) {
         this.line(
            g,
            font,
            Component.translatable(match ? "gui.deepstrike.panel.defense.no_contacts" : "gui.deepstrike.panel.defense.select").getString(),
            left + CONTACT_X + 4,
            listTop + 4,
            CONTACT_W - 8,
            ConsoleStyle.PHOSPHOR_DIM
         );
         return;
      }

      int shown = Math.min(CONTACT_ROWS, this.contacts.size() - this.contactScroll);

      for (int i = 0; i < shown; i++) {
         CompoundTag c = this.contacts.get(this.contactScroll + i);
         int y = listTop + i * ROW;
         int x = left + CONTACT_X;
         boolean friendly = c.getBoolean("Friendly");
         if (c.getUUID("Id").equals(this.selectedContact)) {
            g.fill(x, y, x + CONTACT_W, y + ROW - 1, SELECTED);
         }

         double dist = Math.hypot(c.getDouble("X") - ox, c.getDouble("Z") - oz);
         String distText = dist >= 1000.0 ? String.format(Locale.ROOT, "%.1fkm", dist / 1000.0) : String.format(Locale.ROOT, "%.0fm", dist);
         int alt = c.contains("Alt") ? c.getInt("Alt") : (int)c.getDouble("Y");
         String speed = String.format(Locale.ROOT, "%.0fkm/h", c.getDouble("Speed"));
         int color = friendly ? -6696818 : -1076610;
         String label = (friendly ? "* " : "") + AirDefenseTargets.label(c.getInt("Kind"));
         this.line(g, font, label, x + 2, y + 2, 50, color);
         g.drawString(font, distText, x + 54, y + 2, -2039584, false);
         g.drawString(font, "Y" + alt, x + 92, y + 2, ConsoleStyle.AMBER_DIM, false);
         g.drawString(font, speed, x + CONTACT_W - 2 - font.width(speed), y + 2, ConsoleStyle.AMBER_DIM, false);
      }

      this.scrollbar(g, left + CONTACT_X + CONTACT_W, listTop, CONTACT_ROWS, this.contacts.size(), this.contactScroll);
   }

   private void line(GuiGraphics g, Font font, String text, int x, int y, int max, int color) {
      g.drawString(font, font.plainSubstrByWidth(text, Math.max(0, max)), x, y, color, false);
   }

   private void scrollbar(GuiGraphics g, int x, int top, int rows, int total, int scroll) {
      if (total > rows) {
         int height = rows * ROW;
         int thumb = Math.max(8, height * rows / total);
         int y = top + (height - thumb) * scroll / Math.max(1, total - rows);
         g.fill(x + 1, top, x + 3, top + height, -16445689);
         g.fill(x + 1, y, x + 3, y + thumb, ConsoleStyle.PHOSPHOR_DIM);
      }
   }

   /** A click on the drawn lists. */
   boolean click(double mouseX, double mouseY, int left, int listTop) {
      double rx = mouseX - (double)left;
      double ry = mouseY - (double)listTop;
      if (ry < 0.0 || ry >= (double)(CONTACT_ROWS * ROW)) {
         return false;
      }

      int row = (int)(ry / (double)ROW);
      if (rx >= (double)UNIT_X && rx < (double)(UNIT_X + UNIT_W) && row < UNIT_ROWS) {
         int index = this.unitScroll + row;
         if (index < this.units.size() && this.units.get(index).hasUUID("Id")) {
            UUID id = this.units.get(index).getUUID("Id");
            this.selectedUnit = id;
            this.selectedContact = null;
            CompoundTag p = new CompoundTag();
            p.putUUID("Unit", id);
            this.sender.accept("defense_select", p);
            return true;
         }
      } else if (rx >= (double)CONTACT_X && rx < (double)(CONTACT_X + CONTACT_W) && this.radarMatches()) {
         int index = this.contactScroll + row;
         if (index < this.contacts.size()) {
            this.selectedContact = this.contacts.get(index).getUUID("Id");
            return true;
         }
      }

      return false;
   }

   boolean scroll(double mouseX, double mouseY, int left, int listTop, double delta) {
      double rx = mouseX - (double)left;
      double ry = mouseY - (double)listTop;
      if (ry < 0.0 || ry >= (double)(CONTACT_ROWS * ROW)) {
         return false;
      }

      int step = (int)-Math.signum(delta);
      if (rx >= (double)CONTACT_X) {
         this.contactScroll = Mth.clamp(this.contactScroll + step, 0, Math.max(0, this.contacts.size() - CONTACT_ROWS));
      } else if (ry < (double)(UNIT_ROWS * ROW)) {
         this.unitScroll = Mth.clamp(this.unitScroll + step, 0, Math.max(0, this.units.size() - UNIT_ROWS));
      }

      return true;
   }
}
