package dev.colonyjournal.client;

import dev.colonyjournal.analysis.ColonyReport.Row;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One row per building: icon, name, level, workers, open requests and position, under a header that explains the columns. */
final class BuildingsTab extends JournalTab {
   private static final int ROW_H = 22;
   private static final int GAP = 2;
   private static final int HEAD_H = 13;
   private static final ItemStack ICON = new ItemStack(Items.BRICKS);

   /** Column positions. The widths are fixed, the name takes what is left. */
   private record Cols(int nameX, int nameW, int levelX, int workersX, int requestsX, int posX, boolean wide) {
   }

   private final Scroller scroller = new Scroller();
   private Row hoveredRow;
   private Component headTip;

   BuildingsTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "buildings";
   }

   private int listX() {
      return this.cx() + 3;
   }

   private int listW() {
      return this.cw() - 6 - 6;
   }

   private int rowsTop() {
      return this.cy() + HEAD_H;
   }

   private int rowsH() {
      return Math.max(0, this.ch() - HEAD_H);
   }

   private int contentHeight() {
      return 4 + this.screen.report().rows.size() * (ROW_H + GAP);
   }

   private Cols cols() {
      int x = this.listX();
      int w = this.listW();
      boolean wide = w >= 330;
      int posW = wide ? 72 : 0;
      int requestsW = 66;
      int workersW = 70;
      int levelW = 62;
      int nameX = x + 28;
      int levelX = x + w - posW - requestsW - workersW - levelW - 4;
      int workersX = levelX + levelW;
      int requestsX = workersX + workersW;
      int posX = requestsX + requestsW;
      return new Cols(nameX, Math.max(30, levelX - nameX - 4), levelX, workersX, requestsX, posX, wide);
   }

   /** Index of the row under the mouse, or -1. */
   private int rowAt(double mx, double my) {
      if (!Ui.in(mx, my, this.listX(), this.rowsTop(), this.listW(), this.rowsH())) {
         return -1;
      }

      int rel = (int)my - (this.rowsTop() + 4 - this.scroller.offset);
      int index = rel / (ROW_H + GAP);
      int within = rel % (ROW_H + GAP);
      List<Row> rows = this.screen.report().rows;
      return rel >= 0 && within < ROW_H && index < rows.size() ? index : -1;
   }

   /** The tooltip of the header cell under the mouse, or null. */
   private Component headerTip(double mx, double my, Cols c) {
      if (!Ui.in(mx, my, this.listX(), this.cy(), this.listW(), HEAD_H)) {
         return null;
      }

      if (mx >= c.levelX() && mx < c.workersX()) {
         return Component.translatable("journal.colonyjournal.buildings.tip.level");
      } else if (mx >= c.workersX() && mx < c.requestsX()) {
         return Component.translatable("journal.colonyjournal.buildings.tip.workers");
      } else if (mx >= c.requestsX() && mx < c.posX() || !c.wide() && mx >= c.requestsX()) {
         return Component.translatable("journal.colonyjournal.buildings.tip.requests");
      } else if (c.wide() && mx >= c.posX()) {
         return Component.translatable("journal.colonyjournal.buildings.tip.pos");
      }

      return null;
   }

   @Override
   void render(GuiGraphics g, int mx, int my) {
      this.scroller.setSize(this.contentHeight(), this.rowsH());
      List<Row> rows = this.screen.report().rows;
      int x = this.listX();
      int w = this.listW();
      Cols c = this.cols();
      int hover = this.rowAt(mx, my);
      this.hoveredRow = hover >= 0 ? rows.get(hover) : null;
      this.headTip = this.headerTip(mx, my, c);

      // header
      g.fill(x, this.cy(), x + w, this.cy() + HEAD_H - 1, 0x88000000);
      int hy = this.cy() + 3;
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.buildings.col_name"), c.nameX(), hy, 0xFFE0D0A0);
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.buildings.col_level"), c.levelX(), hy, 0xFFE0D0A0);
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.buildings.col_workers"), c.workersX(), hy, 0xFFE0D0A0);
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.buildings.col_requests"), c.requestsX(), hy, 0xFFE0D0A0);
      if (c.wide()) {
         Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.buildings.col_pos"), c.posX(), hy, 0xFFE0D0A0);
      }

      // rows, clipped below the header
      g.enableScissor(this.cx(), this.rowsTop(), this.cx() + this.cw(), this.rowsTop() + this.rowsH());
      if (rows.isEmpty()) {
         int ey = this.rowsTop() + 6;

         for (FormattedCharSequence line : this.font().split(Component.translatable("journal.colonyjournal.buildings.empty"), w - 8)) {
            g.drawString(this.font(), line, x + 4, ey, 0xFFAAAAAA, true);
            ey += 10;
         }
      }

      for (int i = 0; i < rows.size(); i++) {
         int y = this.rowsTop() + 4 - this.scroller.offset + i * (ROW_H + GAP);
         if (y + ROW_H < this.rowsTop() || y > this.rowsTop() + this.rowsH()) {
            continue;
         }

         Row row = rows.get(i);
         g.fill(x, y, x + w, y + ROW_H, i == hover ? 0x88404040 : 0x77000000);
         g.renderOutline(x, y, w, ROW_H, 0xFF000000);
         g.fill(x, y, x + 3, y + ROW_H, LinksTab.color(row.status()));
         g.renderItem(BuildingIcons.of(row.typeId()), x + 7, y + 3);
         int ty = y + 7;
         g.drawString(this.font(), this.font().plainSubstrByWidth(row.name().getString(), c.nameW()), c.nameX(), ty, 0xFFFFFFFF, true);
         g.drawString(this.font(), Component.translatable("journal.colonyjournal.level", row.level(), row.maxLevel()), c.levelX(), ty, 0xFFCCCCCC, true);
         if (row.slots() > 0) {
            int color = row.workers() >= row.slots() ? 0xFF7FE08A : (row.workers() > 0 ? 0xFFE0C040 : 0xFFE08A30);
            g.drawString(this.font(), Component.translatable("journal.colonyjournal.workers", row.workers(), row.slots()), c.workersX(), ty, color, true);
         }

         if (row.requests() > 0) {
            g.drawString(this.font(), Component.translatable("journal.colonyjournal.requests", row.requests()), c.requestsX(), ty, 0xFFE0C040, true);
         }

         if (c.wide()) {
            g.drawString(this.font(), row.pos().toShortString(), c.posX(), ty, 0xFF909090, true);
         }
      }

      g.disableScissor();
      this.scroller.renderBar(g, this.cx() + this.cw() - 5, this.rowsTop(), this.rowsH());
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      if (this.headTip != null) {
         Ui.tooltip(g, this.font(), this.headTip, mx, my);
         return;
      }

      Row row = this.hoveredRow;
      if (row != null) {
         List<Component> lines = new ArrayList<>();
         lines.add(row.name());
         lines.add(Component.translatable("journal.colonyjournal.reason." + row.status().name().toLowerCase(java.util.Locale.ROOT)));
         lines.add(Component.translatable("journal.colonyjournal.level", row.level(), row.maxLevel()));
         if (row.slots() > 0) {
            lines.add(Component.translatable("journal.colonyjournal.workers", row.workers(), row.slots()));
         }

         if (row.requests() > 0) {
            lines.add(Component.translatable("journal.colonyjournal.requests", row.requests()));
         }

         lines.add(Component.literal(row.pos().toShortString()));
         g.renderComponentTooltip(this.font(), lines, mx, my);
      }
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      this.scroller.wheel(sy);
      return true;
   }
}
