package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.RadarDisclosure;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public final class CommandDefenseScreen extends TacticalXaeroMap implements EquipmentUiTheme {
   private final CompoundTag origin;
   private UUID selected;
   private int ticks;
   private int scroll;
   private Button primary;
   private Button secondary;

   @Override
   public String equipmentSkin() {
      return this.radar().getString("Skin");
   }

   public CommandDefenseScreen(Screen parent, CompoundTag origin) {
      super(parent, origin.getDouble("X"), origin.getDouble("Z"));
      this.origin = origin;
   }

   private CompoundTag radar() {
      return CommandTabletScreen.latestSnapshot.getCompound("Defense").getCompound("Radar");
   }

   private List<CompoundTag> contacts() {
      List<CompoundTag> contacts = new ArrayList<>();

      for (Tag raw : this.radar().getList("Contacts", 10)) {
         contacts.add((CompoundTag)raw);
      }

      return contacts;
   }

   private void send(String action, CompoundTag p) {
      CommandTabletNetwork.request(action, p);
   }

   @Override
   public void init() {
      super.init();
      double radius = 400.0;

      for (CompoundTag contact : this.contacts()) {
         radius = Math.max(radius, Math.hypot(contact.getDouble("X") - this.origin.getDouble("X"), contact.getDouble("Z") - this.origin.getDouble("Z")));
      }

      this.mapSpan(Math.min(16000.0, radius * 2.6));
      this.addRenderableWidget(Button.builder(CommandTabletScreen.tr("back"), b -> this.onClose()).bounds(this.width - 160, 18, 136, 26).build(QuietButton::new));
      this.primary = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("TAMIR / PAC-2"), b -> this.fire(0))
            .bounds(this.width - 266, this.height - 70, 118, 26)
            .build(QuietButton::new)
      );
      this.secondary = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("PAC-3"), b -> this.fire(1)).bounds(this.width - 142, this.height - 70, 118, 26).build(QuietButton::new)
      );
      this.addRenderableWidget(
         Button.builder(CommandTabletScreen.tr("defense_reload"), b -> this.send("defense_reload", new CompoundTag()))
            .bounds(this.width - 266, this.height - 160, 242, 26)
            .build(QuietButton::new)
      );
      this.addRenderableWidget(
         Button.builder(CommandTabletScreen.tr("defense_toggle"), b -> this.send("defense_toggle", new CompoundTag()))
            .bounds(this.width - 266, this.height - 125, 242, 26)
            .build(QuietButton::new)
      );
   }

   private void fire(int warhead) {
      if (this.selected != null) {
         CompoundTag p = new CompoundTag();
         p.putUUID("Target", this.selected);
         p.putInt("Warhead", warhead);
         this.send("defense_fire", p);
      }
   }

   public void tick() {
      if (++this.ticks % 10 == 1) {
         this.send("refresh", new CompoundTag());
      }

      CompoundTag r = this.radar();
      boolean can = CommandTabletScreen.latestSnapshot.getInt("Role") >= 1
         && r.getBoolean("Online")
         && this.contacts().stream().anyMatch(c -> this.selected != null && this.selected.equals(c.getUUID("Id")) && !c.getBoolean("Friendly"));

      for (GuiEventListener child : this.children()) {
         if (child instanceof Button button) {
            button.active = button.getMessage().getString().equals(CommandTabletScreen.tr("back").getString())
               || CommandTabletScreen.latestSnapshot.getInt("Role") >= 1 && r.getBoolean("Controllable");
         }
      }

      this.primary.setMessage(Component.literal(r.getBoolean("Iron") ? "TAMIR" : "PAC-2"));
      this.primary.active = can && r.getInt("Ready0") > 0;
      this.secondary.visible = !r.getBoolean("Iron");
      this.secondary.active = can && r.getInt("Ready1") > 0;
   }

   @Override
   protected boolean mapArea(double x, double y) {
      return x >= 12.0 && x < this.width - 290 && y > 62.0 && y < this.height - 32;
   }

   @Override
   protected boolean choose(double x, double y, int button) {
      if (button != 0) {
         return false;
      } else {
         for (CompoundTag c : this.contacts()) {
            if (Math.hypot(this.mapX(c.getDouble("X")) - x, this.mapZ(c.getDouble("Z")) - y) < 14.0) {
               this.selected = c.getUUID("Id");
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean mouseClicked(double x, double y, int button) {
      if (!this.chromeHidden && button == 0 && x >= this.width - 276 && x < this.width - 24 && y >= 130.0 && y < this.height - 190) {
         int row = this.scroll + (int)(y - 130.0) / 38;
         List<CompoundTag> cs = this.contacts();
         if (row < cs.size()) {
            this.selected = cs.get(row).getUUID("Id");
         }

         return true;
      } else {
         return super.mouseClicked(x, y, button);
      }
   }

   @Override
   public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
      if (!this.chromeHidden && x > this.width - 290) {
         this.scroll = Math.max(0, this.scroll + (delta > 0.0 ? -1 : 1));
         return true;
      } else {
         return super.mouseScrolled(x, y, 0.0, delta);
      }
   }

   @Override
   protected void drawOverlay(GuiGraphics g, int mx, int my, float pt) {
      CompoundTag r = this.radar();
      if (!this.chromeHidden) {
         g.fill(0, 0, this.width, 58, -182838503);
         FlightUi.label(g, "DEEPSTRIKE / " + this.origin.getString("Name"), 24, 22, -1182997);
         FlightUi.panel(g, this.width - 288, 64, 276, this.height - 88, -182838503, -10854328);
         FlightUi.label(
            g,
            CommandTabletScreen.tr(r.getBoolean("Online") ? "connected" : "disconnected").getString(),
            this.width - 270,
            80,
            r.getBoolean("Online") ? -6696818 : -1076610
         );
         FlightUi.label(g, "TAMIR / PAC-2: " + r.getInt("Ready0") + "  PAC-3: " + r.getInt("Ready1"), this.width - 270, 103, -5196890);
      }

      g.enableScissor(
         this.chromeHidden ? 0 : 12,
         this.chromeHidden ? 0 : 62,
         this.chromeHidden ? this.width : this.width - 294,
         this.chromeHidden ? this.height : this.height - 30
      );
      int bx = this.mapX(r.getDouble("X"));
      int bz = this.mapZ(r.getDouble("Z"));
      g.renderOutline(bx - 6, bz - 6, 13, 13, -4668269);

      for (CompoundTag c : this.contacts()) {
         int x = this.mapX(c.getDouble("X"));
         int y = this.mapZ(c.getDouble("Z"));
         int color = c.getBoolean("Friendly") ? -6696818 : -1076610;
         boolean dirKnown = c.getBoolean("Friendly") || RadarDisclosure.impactKnown(c.getUUID("Id"));
         RadarContactRenderer.drawContact(
            g, this.font, x, y, c.getInt("Kind"), dirKnown, color, this.selected != null && this.selected.equals(c.getUUID("Id")), 14
         );
         if (this.selected != null && this.selected.equals(c.getUUID("Id"))) {
            FlightUi.label(g, AirDefenseTargets.label(c.getInt("Kind")), x + 12, y - 4, color);
         }
      }

      g.disableScissor();
      if (!this.chromeHidden) {
         List<CompoundTag> cs = this.contacts();
         int capacity = Math.max(1, (this.height - 330) / 38);
         this.scroll = Math.min(this.scroll, Math.max(0, cs.size() - capacity));

         for (int i = this.scroll; i < Math.min(cs.size(), this.scroll + capacity); i++) {
            CompoundTag cx = cs.get(i);
            int y = 130 + (i - this.scroll) * 38;
            g.fill(this.width - 278, y, this.width - 24, y + 34, cx.getUUID("Id").equals(this.selected) ? -12498886 : -14078937);
            FlightUi.label(g, AirDefenseTargets.label(cx.getInt("Kind")), this.width - 270, y + 5, cx.getBoolean("Friendly") ? -6696818 : -1182997);
            FlightUi.label(g, String.format(Locale.ROOT, "Y %.0f / %.0f km/h", cx.getDouble("Y"), cx.getDouble("Speed")), this.width - 270, y + 20, -5196890);
         }

         String error = CommandTabletScreen.latestSnapshot.getString("Error");
         if (!error.isEmpty()) {
            FlightUi.label(g, FlightUi.clip(CommandTabletScreen.tr("error." + error).getString(), this.width - 350), 24, this.height - 21, -1076610);
         }
      }
   }
}
