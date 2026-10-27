package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.network.CommandTabletNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;

public final class CommandUnitScreen extends Screen {
   private final Screen parent;
   private final CompoundTag unit;
   private EditBox name;
   private EditBox group;

   public CommandUnitScreen(Screen parent, CompoundTag unit) {
      super(CommandTabletScreen.tr("unit_settings"));
      this.parent = parent;
      this.unit = unit;
   }

   protected void init() {
      int x = this.width / 2 - 380;
      int y = this.height / 2 - 100;
      boolean admin = CommandTabletScreen.latestSnapshot.getInt("Role") >= 2;
      String oldName = this.name == null ? this.unit.getString("Name") : this.name.getValue();
      String oldGroup = this.group == null ? this.unit.getString("Group") : this.group.getValue();
      this.name = (EditBox)this.addRenderableWidget(new InstrumentField(this.font, x, y, 360, 26, CommandTabletScreen.tr("callsign")));
      this.name.setMaxLength(32);
      this.name.setValue(oldName);
      this.name.setEditable(admin);
      this.group = (EditBox)this.addRenderableWidget(new InstrumentField(this.font, x, y + 70, 360, 26, CommandTabletScreen.tr("group")));
      this.group.setMaxLength(24);
      this.group.setValue(oldGroup);
      this.group.setEditable(admin);
      ((Button)this.addRenderableWidget(Button.builder(Component.translatable("screen.deepstrike.save"), b -> {
         CompoundTag p = new CompoundTag();
         p.putUUID("Unit", this.unit.getUUID("Id"));
         p.putString("Name", this.name.getValue());
         p.putString("Group", this.group.getValue());
         this.minecraft.setScreen(this.parent);
         CommandTabletNetwork.request("rename", p);
      }).bounds(x, y + 140, 170, 26).build(QuietButton::new))).active = admin;
      ((Button)this.addRenderableWidget(Button.builder(CommandTabletScreen.tr("unbind"), b -> {
         CompoundTag p = new CompoundTag();
         p.putUUID("Unit", this.unit.getUUID("Id"));
         this.minecraft.setScreen(this.parent);
         CommandTabletNetwork.request("unbind", p);
      }).bounds(x + 190, y + 140, 170, 26).build(QuietButton::new))).active = admin;
      this.addRenderableWidget(Button.builder(CommandTabletScreen.tr("back"), b -> this.onClose()).bounds(x, y + 190, 360, 26).build(QuietButton::new));
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      FlightUi.desktop(g, this.width, this.height);
      FlightUi.label(g, "DEEPSTRIKE / " + this.title.getString(), 40, 30, -1182997);
      int x = this.width / 2 - 380;
      int y = this.height / 2 - 100;
      FlightUi.panel(g, x - 22, y - 42, 400, 280, -182838503, -10854328);
      FlightUi.panel(g, x + 380, y - 42, 400, 280, -182838503, -10854328);
      FlightUi.label(g, this.title.getString(), x, y - 60, -1182997);
      FlightUi.label(g, CommandTabletScreen.tr("callsign").getString(), x, y - 18, -5196890);
      FlightUi.label(g, CommandTabletScreen.tr("group").getString(), x, y + 52, -5196890);
      FlightUi.label(g, CommandTabletScreen.tr("ammunition").getString(), x + 400, y - 18, -1182997);
      ListTag slots = this.unit.getList("Slots", 10);

      for (int i = 0; i < slots.size(); i++) {
         CompoundTag slot = slots.getCompound(i);
         Component label = (Component)(slot.getBoolean("Loaded") ? Serializer.fromJson(slot.getString("Name"), net.minecraft.core.RegistryAccess.EMPTY) : CommandTabletScreen.tr("slot_empty"));
         FlightUi.row(g, x + 391, y + i * 27 - 4, 368, 24, false, slot.getBoolean("Loaded") ? -6696818 : -10854328);
         FlightUi.label(
            g, i + 1 + " · " + FlightUi.clip(label == null ? "—" : label.getString(), 310), x + 405, y + i * 27, slot.getBoolean("Loaded") ? -1182997 : -5196890
         );
      }

      long seen = this.unit.getLong("Seen");
      FlightUi.label(
         g,
         CommandTabletScreen.tr("last_seen", Math.max(0L, (CommandTabletScreen.latestSnapshot.getLong("Now") - seen) / 20L)).getString(),
         x + 400,
         y + 180,
         -5196890
      );
      super.render(g, mx, my, pt);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
