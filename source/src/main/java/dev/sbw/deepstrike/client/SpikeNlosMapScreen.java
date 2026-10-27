package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.network.SpikeNlosNetwork;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class SpikeNlosMapScreen extends TacticalXaeroMap implements EquipmentUiTheme {
   private final int vehicleId;
   private EditBox targetX;
   private EditBox targetY;
   private EditBox targetZ;
   private EditBox altitude;
   private EditBox speed;
   private EditBox frequency;
   private int mx;
   private int my;
   private int mw;
   private int mh;
   private int ticks;
   private int ammo;
   private double cx;
   private double cz;
   private List<SpikeNlosNetwork.Contact> contacts = List.of();
   private boolean selected;
   private Button fire;

   @Override
   public String equipmentSkin() {
      return EquipmentUiTheme.vehicle(this.vehicleId);
   }

   public SpikeNlosMapScreen(int vehicle) {
      super(null, vehicleX(vehicle), vehicleZ(vehicle));
      this.vehicleId = vehicle;
   }

   private static double vehicleX(int id) {
      Minecraft mc = Minecraft.getInstance();
      Entity v = mc.level.getEntity(id);
      return v == null ? mc.player.getX() : v.getX();
   }

   private static double vehicleZ(int id) {
      Minecraft mc = Minecraft.getInstance();
      Entity v = mc.level.getEntity(id);
      return v == null ? mc.player.getZ() : v.getZ();
   }

   @Override
   public void init() {
      super.init();
      String[] preserved = this.targetX == null
         ? null
         : new String[]{
            this.targetX.getValue(),
            this.targetY.getValue(),
            this.targetZ.getValue(),
            this.altitude.getValue(),
            this.speed.getValue(),
            this.frequency.getValue()
         };
      if (this.targetX == null) {
         Entity vehicle = this.minecraft.level.getEntity(this.vehicleId);
         this.cx = vehicle == null ? this.minecraft.player.getX() : vehicle.getX();
         this.cz = vehicle == null ? this.minecraft.player.getZ() : vehicle.getZ();
      }

      this.mx = 16;
      this.my = 60;
      this.mw = Math.max(64, this.width - 32);
      this.mh = Math.max(64, this.height - 210);
      if (this.targetX == null) {
         this.mapSpan(1100.0);
      }

      int x = 30;
      int y = this.height - 110;
      int step = Math.max(72, (this.width - 60) / 6);
      int fieldW = step - 14;
      this.targetX = this.field(x, y, "X", this.cx);
      this.targetY = this.field(x + step, y, "Y", this.minecraft.player.getY());
      this.targetZ = this.field(x + step * 2, y, "Z", this.cz);
      this.altitude = this.field(
         x + step * 3, y, FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.8c83efa4f2"), Math.max(100.0, this.minecraft.player.getY() + 50.0)
      );
      this.speed = this.field(x + step * 4, y, FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.61c3d99245"), 450.0);
      this.addRenderableWidget(
         Button.builder(
               Component.literal(FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.34907a5305")), button -> UnitJournalScreen.open(this.vehicleId)
            )
            .bounds(30, this.height - 58, 160, 26)
            .build(QuietButton::new)
      );
      this.frequency = new InstrumentField(
         this.font, x + step * 5, y, fieldW, 18, Component.literal(FlightUi.localized("screen.deepstrike.frequency.label"))
      );
      this.frequency.setBordered(false);
      this.frequency.setTextColor(-1182997);
      this.frequency.setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
      this.frequency.setMaxLength(12);
      this.frequency.setFilter(v -> v.matches("[0-9]{0,12}"));
      this.addRenderableWidget(this.frequency);
      if (this.minecraft.level.getEntity(this.vehicleId) instanceof SpikeNlosEntity spike) {
         this.frequency.setValue(spike.transmitFrequency());
      }

      if (preserved != null) {
         EditBox[] fields = new EditBox[]{this.targetX, this.targetY, this.targetZ, this.altitude, this.speed, this.frequency};

         for (int i = 0; i < fields.length; i++) {
            fields[i].setValue(preserved[i]);
         }
      }

      for (EditBox box : new EditBox[]{this.targetX, this.targetY, this.targetZ, this.altitude, this.speed}) {
         box.setWidth(fieldW);
      }

      this.targetX.setResponder(value -> this.armManualTarget());
      this.targetY.setResponder(value -> this.armManualTarget());
      this.targetZ.setResponder(value -> this.armManualTarget());
      this.fire = (Button)this.addRenderableWidget(
         Button.builder(Component.literal(FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.fe83a9251a")), b -> this.launch())
            .bounds(this.width - 210, this.height - 58, 180, 26)
            .build(QuietButton::new)
      );
      this.fire.active = this.selected && this.ammo > 0;
      SpikeNlosNetwork.CHANNEL.sendToServer(new SpikeNlosNetwork.Request(this.vehicleId));
   }

   private void armManualTarget() {
      this.selected = true;
      if (this.fire != null) {
         this.fire.active = this.ammo > 0;
      }
   }

   private EditBox field(int x, int y, String name, double value) {
      EditBox field = new InstrumentField(this.font, x, y, 128, 18, Component.literal(name));
      field.setBordered(false);
      field.setTextColor(-1182997);
      field.setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
      field.setMaxLength(16);
      field.setValue(String.format(Locale.ROOT, "%.1f", value));
      return (EditBox)this.addRenderableWidget(field);
   }

   public static void accept(SpikeNlosNetwork.Snapshot s) {
      if (Minecraft.getInstance().screen instanceof SpikeNlosMapScreen screen && screen.vehicleId == s.vehicle()) {
         screen.contacts = s.contacts();
         screen.ammo = s.ammo();
         if (screen.fire != null) {
            screen.fire.active = screen.selected && screen.ammo > 0;
         }
      }
   }

   public void tick() {
      if (this.minecraft.player.getVehicle() instanceof SpikeNlosEntity v && v.getId() == this.vehicleId) {
         if (++this.ticks % 10 == 0) {
            SpikeNlosNetwork.CHANNEL.sendToServer(new SpikeNlosNetwork.Request(this.vehicleId));
         }
      } else {
         this.onClose();
      }
   }

   @Override
   protected void drawOverlay(GuiGraphics g, int mouseX, int mouseY, float partial) {
      if (!this.chromeHidden) {
         FlightUi.panel(g, 12, 10, this.width - 24, 40, -182838503, -10854328);
         FlightUi.label(g, FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.741b2ed516"), 28, 19, -1182997);
         FlightUi.label(g, FlightInstruments.tr("command"), 28, 34, -5196890);
         int ammoX = this.width - 150;

         for (int i = 0; i < 4; i++) {
            FlightUi.rounded(g, ammoX + i * 25, 23, 17, 15, i < this.ammo ? -1000096 : -12498886);
         }

         FlightUi.panel(g, 16, this.height - 137, this.width - 32, 112, -182838503, -10854328);
      }

      g.enableScissor(
         this.chromeHidden ? 0 : this.mx,
         this.chromeHidden ? 0 : this.my,
         this.chromeHidden ? this.width : this.mx + this.mw,
         this.chromeHidden ? this.height : this.my + this.mh
      );

      for (SpikeNlosNetwork.Contact c : this.contacts) {
         int x = this.px(c.x());
         int y = this.pz(c.z());
         int col = c.player() ? -5194868 : -4413569;
         g.fill(x - 2, y - 2, x + 3, y + 3, col);
         if (Math.abs(mouseX - x) < 6 && Math.abs(mouseY - y) < 6) {
            g.drawString(this.font, c.name(), x + 6, y - 4, col, false);
         }
      }

      Entity v = this.minecraft.level.getEntity(this.vehicleId);
      if (v != null) {
         g.renderOutline(this.px(v.getX()) - 3, this.pz(v.getZ()) - 3, 7, 7, -6575733);
      }

      if (this.selected) {
         try {
            int x = this.px(Double.parseDouble(this.targetX.getValue()));
            int y = this.pz(Double.parseDouble(this.targetZ.getValue()));
            g.renderOutline(x - 6, y - 6, 13, 13, -4355200);
         } catch (NumberFormatException var10) {
         }
      }

      g.disableScissor();
      if (!this.chromeHidden) {
         g.drawString(this.font, FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.1a64c697c4"), 12, this.height - 16, -4930379, false);
         String[] labels = new String[]{
            "X",
            "Y",
            "Z",
            FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.8c83efa4f2"),
            FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.a185f97c9a"),
            FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.221d5dd666")
         };
         EditBox[] fields = new EditBox[]{this.targetX, this.targetY, this.targetZ, this.altitude, this.speed, this.frequency};

         for (int i = 0; i < fields.length; i++) {
            EditBox box = fields[i];
            FlightUi.rounded(g, box.getX() - 5, box.getY() - 4, box.getWidth() + 10, 27, -14078937);
            FlightUi.label(g, FlightUi.clip(labels[i], box.getWidth()), box.getX(), box.getY() - 18, -5196890);
         }
      }
   }

   private int px(double x) {
      return this.mapX(x);
   }

   private int pz(double z) {
      return this.mapZ(z);
   }

   @Override
   protected boolean mapArea(double x, double y) {
      return x >= this.mx && x < this.mx + this.mw && y >= this.my && y < this.my + this.mh;
   }

   @Override
   protected boolean choose(double x, double y, int button) {
      if (button != 1) {
         return false;
      } else {
         double wx = this.worldX(x);
         double wz = this.worldZ(y);
         this.targetX.setValue(String.format(Locale.ROOT, "%.1f", wx));
         this.targetZ.setValue(String.format(Locale.ROOT, "%.1f", wz));
         Optional<SpikeNlosNetwork.Contact> best = this.contacts.stream().filter(c -> Math.hypot(this.px(c.x()) - x, this.pz(c.z()) - y) < 8.0).findFirst();
         if (best.isPresent()) {
            this.targetX.setValue(Double.toString(best.get().x()));
            this.targetY.setValue(Double.toString(best.get().y()));
            this.targetZ.setValue(Double.toString(best.get().z()));
         } else if (this.minecraft.level.hasChunkAt(BlockPos.containing(wx, 0.0, wz))) {
            this.targetY.setValue(Integer.toString(this.minecraft.level.getHeight(Types.WORLD_SURFACE, (int)Math.floor(wx), (int)Math.floor(wz))));
         }

         this.selected = true;
         this.fire.active = this.ammo > 0;
         return true;
      }
   }

   private void launch() {
      try {
         double x = Double.parseDouble(this.targetX.getValue());
         double y = Double.parseDouble(this.targetY.getValue());
         double z = Double.parseDouble(this.targetZ.getValue());
         double h = Double.parseDouble(this.altitude.getValue());
         double v = Double.parseDouble(this.speed.getValue());
         Entity vehicle = this.minecraft.level.getEntity(this.vehicleId);
         if (vehicle == null
            || !Double.isFinite(x)
            || !Double.isFinite(y)
            || !Double.isFinite(z)
            || !Double.isFinite(h)
            || !Double.isFinite(v)
            || v < 80.0
            || v > 650.0
            || y < this.minecraft.level.getMinBuildHeight() + 1
            || y > this.minecraft.level.getMaxBuildHeight() - 2
            || vehicle.distanceToSqr(x, y, z) > 9000000.0) {
            this.minecraft.player.displayClientMessage(Component.literal(FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.86e81d89b2")), true);
            return;
         }

         if (!RadioFrequency.valid(this.frequency.getValue())) {
            this.minecraft.player.displayClientMessage(Component.literal(FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.f4dee0c362")), true);
            return;
         }

         SpikeNlosNetwork.CHANNEL.sendToServer(new SpikeNlosNetwork.Launch(this.vehicleId, x, y, z, h, v, this.frequency.getValue()));
         this.onClose();
      } catch (NumberFormatException var12) {
         this.minecraft.player.displayClientMessage(Component.literal(FlightUi.localized("ui.deepstrike.spike_nlos_map_screen.62f9e8ee0a")), true);
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
