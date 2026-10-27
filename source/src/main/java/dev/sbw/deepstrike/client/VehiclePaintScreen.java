package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.game.PaintSprayItem;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class VehiclePaintScreen extends Screen implements EquipmentUiTheme {
   private final int vehicleId;
   private String[] skins = new String[0];
   private String current = "";

   @Override
   public String equipmentSkin() {
      return EquipmentUiTheme.vehicle(this.vehicleId);
   }

   public VehiclePaintScreen(int vehicleId) {
      super(Component.translatable("screen.deepstrike.paint.title"));
      this.vehicleId = vehicleId;
   }

   protected void init() {
      Entity entity = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.vehicleId);
      this.skins = entity == null ? new String[0] : PaintSprayItem.skins(entity);
      this.current = entity instanceof VehicleEntity vehicle ? vehicle.getSkinId() : "";
      int x = this.width / 2 - 85;
      int y = this.height / 2 - (this.skins.length * 25 + 18) / 2;

      for (int i = 0; i < this.skins.length; i++) {
         String skin = this.skins[i];
         this.addRenderableWidget(Button.builder(Component.literal(skin.equals(this.current) ? "◆ " : "  ").append(PaintSprayItem.label(skin)), button -> {
            DeepStrikeNetwork.CHANNEL.sendToServer(new DeepStrikeNetwork.PaintVehicle(this.vehicleId, skin));
            this.onClose();
         }).bounds(x, y + 22 + i * 25, 170, 20).build(QuietButton::new));
      }
   }

   public void tick() {
      if (this.minecraft.player == null
         || this.minecraft.level == null
         || this.minecraft.level.getEntity(this.vehicleId) == null
         || this.minecraft.player.distanceToSqr(this.minecraft.level.getEntity(this.vehicleId)) > 100.0) {
         this.onClose();
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderTransparentBackground(graphics);
      int x = this.width / 2 - 109;
      int y = this.height / 2 - (this.skins.length * 25 + 18) / 2;
      FlightUi.panel(graphics, x, y - 24, 218, this.skins.length * 25 + 64, -182838503, -10854328);
      FlightUi.label(graphics, this.title.getString(), x + 23, y - 8, -1182997);
      graphics.fill(x + 22, y + 8, x + 196, y + 9, -10854328);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
