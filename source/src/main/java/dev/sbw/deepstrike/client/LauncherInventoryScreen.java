package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.menu.LauncherMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class LauncherInventoryScreen extends AbstractContainerScreen<LauncherMenu> implements EquipmentUiTheme {
   @Override
   public String equipmentSkin() {
      MissileData data = MissileData.fromItemStack(((LauncherMenu)this.menu).launcher.loaded());
      return data == null ? "" : data.camo();
   }

   public LauncherInventoryScreen(LauncherMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
      this.imageWidth = 176;
      this.imageHeight = 190;
      this.inventoryLabelY = 92;
   }

   protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
      FlightUi.panel(g, this.leftPos - 10, this.topPos - 12, this.imageWidth + 20, this.imageHeight + 22, -182838503, -10854328);
      g.fill(this.leftPos + 8, this.topPos + 19, this.leftPos + this.imageWidth - 8, this.topPos + 20, -10854328);

      for (Slot slot : ((LauncherMenu)this.menu).slots) {
         int x = this.leftPos + slot.x;
         int y = this.topPos + slot.y;
         FlightUi.rounded(g, x - 2, y - 2, 20, 20, -14868713);
         g.fill(x - 1, y - 1, x + 17, y, -15724276);
         g.fill(x - 1, y + 16, x + 17, y + 17, slot.getItem().isEmpty() ? -10854328 : -4668269);
      }

      for (int i = 0; i < ((LauncherMenu)this.menu).launcher.capacity(); i++) {
         Slot slot = (Slot)((LauncherMenu)this.menu).slots.get(i);
         g.drawString(this.font, Integer.toString(i + 1), this.leftPos + slot.x - 10, this.topPos + slot.y + 5, -4207720, false);
      }
   }

   protected void renderLabels(GuiGraphics g, int mx, int my) {
      FlightUi.label(g, this.font.plainSubstrByWidth(this.title.getString(), 160), 8, 4, -1182997);
      FlightUi.label(g, this.playerInventoryTitle.getString(), 8, this.inventoryLabelY, -5196890);
      if (((LauncherMenu)this.menu).launcher.family() == LauncherBlock.Family.PEACEKEEPER) {
         g.drawString(
            this.font,
            Component.translatable(
               ((LauncherMenu)this.menu).launcher.platformFueled() ? "screen.deepstrike.platform.fueled" : "screen.deepstrike.platform.refuel"
            ),
            8,
            55,
            -2167631,
            false
         );
         g.drawString(this.font, ((LauncherMenu)this.menu).launcher.platformFuelStatusComponent(), 8, 69, -4207720, false);
      }
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      this.renderTransparentBackground(g);
      super.render(g, mx, my, partial);
      this.renderTooltip(g, mx, my);
   }
}
