package dev.sbw.deepstrike.client;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.util.FormattedCharSequence;

public final class CommandTaskDetailsScreen extends Screen {
   private final Screen parent;
   private final CompoundTag network;
   private final CompoundTag task;
   private int scroll;

   public CommandTaskDetailsScreen(Screen parent, CompoundTag network, CompoundTag task) {
      super(CommandTabletScreen.tr("details"));
      this.parent = parent;
      this.network = network;
      this.task = task;
   }

   protected void init() {
      this.addRenderableWidget(Button.builder(CommandTabletScreen.tr("back"), b -> this.onClose()).bounds(40, this.height - 60, 160, 26).build(QuietButton::new));
   }

   public void render(GuiGraphics g, int x, int y, float partial) {
      FlightUi.desktop(g, this.width, this.height);
      FlightUi.label(g, this.task.getString("Name"), 40, 34, -1182997);
      int at = 90;
      ListTag rows = this.task.getList("Assignments", 10);
      int capacity = Math.max(1, (this.height - 180) / 90);
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, rows.size() - capacity)));

      for (int i = this.scroll; i < Math.min(rows.size(), this.scroll + capacity); i++) {
         CompoundTag a = rows.getCompound(i);
         String name = "?";

         for (Tag raw : this.network.getList("Units", 10)) {
            CompoundTag u = (CompoundTag)raw;
            if (u.getUUID("Id").equals(a.getUUID("Unit"))) {
               name = u.getString("Name");
            }
         }

         FlightUi.row(
            g,
            40,
            at,
            this.width - 80,
            80,
            false,
            a.getString("Phase").equals("ERROR") ? -1076610 : (a.getString("Phase").equals("READY") ? -6696818 : -4668269)
         );
         FlightUi.label(g, name + " / " + a.getInt("Launched") + " / " + a.getInt("Count"), 54, at + 10, -1182997);
         FlightUi.label(g, CommandTabletScreen.tr("state." + a.getString("Phase").toLowerCase(Locale.ROOT)).getString(), 54, at + 29, -5196890);
         Component reason = (Component)(a.contains("ErrorDetail")
            ? Serializer.fromJson(a.getString("ErrorDetail"), net.minecraft.core.RegistryAccess.EMPTY)
            : (a.getString("Error").isEmpty() ? Component.empty() : CommandTabletScreen.tr("error." + a.getString("Error"))));
         if (reason != null) {
            int rowY = at + 48;

            for (FormattedCharSequence line : this.font.split(reason, this.width - 130)) {
               g.drawString(this.font, line, 54, rowY, -26517, false);
               rowY += 11;
               if (rowY >= at + 74) {
                  break;
               }
            }
         }

         at += 90;
      }

      super.render(g, x, y, partial);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double d) {
      this.scroll += d > 0.0 ? -1 : 1;
      return true;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
