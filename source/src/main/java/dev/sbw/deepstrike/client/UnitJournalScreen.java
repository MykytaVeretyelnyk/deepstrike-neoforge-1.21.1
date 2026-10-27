package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.LaunchJournal;
import dev.sbw.deepstrike.network.UnitJournalNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class UnitJournalScreen extends Screen implements EquipmentUiTheme {
   private final Screen parent;
   private final int entity;
   private BlockPos block;
   private int page;
   private int scroll;
   private List<String> entries = List.of();

   @Override
   public String equipmentSkin() {
      return this.parent instanceof EquipmentUiTheme ? EquipmentUiTheme.inherited(this.parent) : EquipmentUiTheme.vehicle(this.entity);
   }

   private UnitJournalScreen(Screen parent, int entity) {
      super(Component.translatable("screen.deepstrike.journal.title"));
      this.parent = parent;
      this.entity = entity;
   }

   public static void open(int entity) {
      Minecraft mc = Minecraft.getInstance();
      mc.setScreen(new UnitJournalScreen(mc.screen, entity));
   }

   public static void open(BlockPos block) {
      Minecraft mc = Minecraft.getInstance();
      UnitJournalScreen screen = new UnitJournalScreen(mc.screen, -1);
      screen.block = block.immutable();
      mc.setScreen(screen);
   }

   public static void accept(UnitJournalNetwork.Snapshot snapshot) {
      if (Minecraft.getInstance().screen instanceof UnitJournalScreen screen && screen.entity == snapshot.entity() && screen.page == snapshot.page()) {
         screen.entries = snapshot.entries();
         screen.scroll = 0;
      }
   }

   private void request() {
      if (this.block != null) {
         UnitJournalNetwork.CHANNEL.sendToServer(new UnitJournalNetwork.RequestBlock(this.block, this.page));
      } else {
         UnitJournalNetwork.CHANNEL.sendToServer(new UnitJournalNetwork.Request(this.entity, this.page));
      }
   }

   protected void init() {
      this.request();
      int x = this.width / 2 - 150;
      this.addRenderableWidget(Button.builder(Component.translatable("screen.deepstrike.journal.newer"), b -> {
         this.page = Math.max(0, this.page - 1);
         this.request();
      }).bounds(x, this.height - 46, 95, 20).build(QuietButton::new));
      this.addRenderableWidget(Button.builder(Component.translatable("screen.deepstrike.journal.older"), b -> {
         this.page++;
         this.request();
      }).bounds(x + 100, this.height - 46, 95, 20).build(QuietButton::new));
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.deepstrike.back"), b -> this.onClose())
            .bounds(x + 200, this.height - 46, 95, 20)
            .build(QuietButton::new)
      );
   }

   public void render(GuiGraphics g, int mx, int my, float partial) {
      this.renderTransparentBackground(g);
      int left = Math.max(16, (this.width - 760) / 2);
      int right = this.width - left;
      FlightUi.panel(g, left, 16, right - left, this.height - 32, -182838503, -10854328);
      FlightUi.label(g, this.title.getString(), left + 22, 32, -1182997);
      FlightUi.label(g, Component.translatable("screen.deepstrike.journal.page", new Object[]{this.page + 1}).getString(), right - 120, 32, -5196890);
      g.fill(left + 20, 54, right - 20, 55, -10854328);
      g.enableScissor(left + 20, 65, right - 20, this.height - 58);
      int y = 70 - this.scroll;
      if (this.entries.isEmpty()) {
         FlightUi.label(g, Component.translatable("screen.deepstrike.journal.empty").getString(), left + 22, y, -5196890);
      }

      for (String line : this.entries) {
         for (FormattedCharSequence text : this.font.split(LaunchJournal.display(line).copy().withStyle(FlightUi.STYLE), right - left - 52)) {
            g.drawString(this.font, text, left + 24, y, -1182997, false);
            y += 14;
         }

         y += 8;
         g.fill(left + 24, y, right - 24, y + 1, 944919119);
         y += 15;
      }

      g.disableScissor();
      super.render(g, mx, my, partial);
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
      this.scroll = Math.max(0, Math.min(this.entries.size() * 90, this.scroll - (int)(delta * 30.0)));
      return true;
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }

   public boolean isPauseScreen() {
      return false;
   }
}
