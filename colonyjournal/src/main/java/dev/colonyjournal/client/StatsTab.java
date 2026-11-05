package dev.colonyjournal.client;

import dev.colonyjournal.net.Payloads;
import dev.colonyjournal.net.Views.StatsRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A sortable table of all colonies on the server with a summary line on top. */
final class StatsTab extends JournalTab {
   private static final ItemStack ICON = new ItemStack(Items.CLOCK);
   private static final int HEAD_H = 14;
   private static final int ROW_H = 13;
   private static final long MIN_INTERVAL_MS = 2200L;
   private static long lastRequest;

   private enum Column {
      COLONY("colony", 84, true, Comparator.comparing((StatsRow r) -> r.name().toLowerCase(Locale.ROOT))),
      OWNER("owner", 66, true, Comparator.comparing((StatsRow r) -> r.owner().toLowerCase(Locale.ROOT))),
      CITIZENS("citizens", 46, false, Comparator.comparingInt(StatsRow::citizens)),
      HAPPINESS("happiness", 64, false, Comparator.comparingDouble(StatsRow::happiness)),
      TREASURY("treasury", 58, false, Comparator.comparingLong(StatsRow::balance)),
      FLOW("flow", 76, false, Comparator.comparingLong((StatsRow r) -> r.income() - r.expense())),
      TAX("tax", 34, false, Comparator.comparingInt(StatsRow::taxRate)),
      BUILDINGS("buildings", 34, false, Comparator.comparingInt(StatsRow::buildings)),
      LEVEL("level", 34, false, Comparator.comparingDouble(StatsRow::avgLevel)),
      DAY("day", 34, false, Comparator.comparingInt(StatsRow::day));

      final String key;
      final int width;
      final boolean text;
      final Comparator<StatsRow> comparator;

      Column(String key, int width, boolean text, Comparator<StatsRow> comparator) {
         this.key = key;
         this.width = width;
         this.text = text;
         this.comparator = comparator;
      }
   }

   private final Scroller scroller = new Scroller();
   private final List<StatsRow> sorted = new ArrayList<>();
   private List<StatsRow> source;
   private Column sortColumn = Column.COLONY;
   private boolean ascending = true;
   private boolean sortDirty = true;
   private int scrollX;
   private int maxScrollX;
   private List<Component> tip;
   private Component headTip;
   private List<StatsRow> summarySource;
   private int summaryWidth;
   private List<FormattedCharSequence> summaryLines;

   StatsTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "stats";
   }

   /** Sends RequestStats, but not more often than the server accepts it. */
   private static void request() {
      long now = System.currentTimeMillis();
      if (now - lastRequest >= MIN_INTERVAL_MS) {
         lastRequest = now;
         Ui.send(new Payloads.RequestStats());
      }
   }

   @Override
   void onOpen() {
      request();
   }

   @Override
   int refreshInterval() {
      return 200;
   }

   @Override
   void requestRefresh() {
      request();
   }

   // ------------------------------------------------------------------ data

   private void ensureSorted() {
      List<StatsRow> data = ClientData.stats;
      if (data != this.source || this.sortDirty) {
         this.source = data;
         this.sortDirty = false;
         this.sorted.clear();
         this.sorted.addAll(data);
         Comparator<StatsRow> cmp = this.sortColumn.comparator;
         this.sorted.sort(this.ascending ? cmp : cmp.reversed());
      }
   }

   private List<FormattedCharSequence> summary() {
      List<StatsRow> rows = ClientData.stats;
      if (rows == this.summarySource && this.cw() == this.summaryWidth && this.summaryLines != null) {
         return this.summaryLines;
      }

      this.summarySource = rows;
      this.summaryWidth = this.cw();
      this.summaryLines = this.buildSummary(rows);
      return this.summaryLines;
   }

   private List<FormattedCharSequence> buildSummary(List<StatsRow> rows) {
      if (rows.isEmpty()) {
         return this.font().split(Component.translatable("journal.colonyjournal.stats.none"), this.cw() - 12);
      }

      double happiness = 0.0;
      int citizens = 0;
      StatsRow richest = rows.get(0);

      for (StatsRow r : rows) {
         happiness += r.happiness();
         citizens += r.citizens();
         if (r.balance() > richest.balance()) {
            richest = r;
         }
      }

      Component text = Component.translatable(
         "journal.colonyjournal.stats.summary", rows.size(), String.format(Locale.ROOT, "%.1f", happiness / rows.size()), citizens, richest.name() + " (" + Ui.coins(richest.balance()) + ")"
      );
      return this.font().split(text, this.cw() - 12);
   }

   // ------------------------------------------------------------------ geometry shared by drawing and hit-testing

   private int left() {
      return this.cx() + 3;
   }

   private int tableW() {
      return this.cw() - 6 - 6;
   }

   private int summaryH() {
      return 4 + Math.min(2, this.summary().size()) * 10 + 2;
   }

   private int headY() {
      return this.cy() + this.summaryH();
   }

   private int rowsY() {
      return this.headY() + HEAD_H;
   }

   private int rowsH() {
      return this.cy() + this.ch() - this.rowsY();
   }

   /** Width of every column; the text columns share the extra space when the table is narrower than the view. */
   private int[] widths() {
      Column[] cols = Column.values();
      int[] w = new int[cols.length];
      int total = 0;

      for (int i = 0; i < cols.length; i++) {
         w[i] = cols[i].width;
         total += w[i];
      }

      int extra = this.tableW() - total;
      if (extra > 0) {
         w[0] += extra / 2;
         w[1] += extra - extra / 2;
         total += extra;
      }

      this.maxScrollX = Math.max(0, total - this.tableW());
      this.scrollX = Ui.clamp(this.scrollX, 0, this.maxScrollX);
      return w;
   }

   /** The column whose header lies under the mouse, or null. */
   private Column headerAt(double mx, double my) {
      if (!Ui.in(mx, my, this.left(), this.headY(), this.tableW(), HEAD_H)) {
         return null;
      }

      int[] w = this.widths();
      int x = this.left() - this.scrollX;
      Column[] cols = Column.values();

      for (int i = 0; i < cols.length; i++) {
         if (mx >= x && mx < x + w[i]) {
            return cols[i];
         }

         x += w[i];
      }

      return null;
   }

   private int rowAt(double mx, double my) {
      if (!Ui.in(mx, my, this.left(), this.rowsY(), this.tableW(), this.rowsH())) {
         return -1;
      }

      int index = ((int)my - this.rowsY() + this.scroller.offset) / ROW_H;
      return index >= 0 && index < this.sorted.size() ? index : -1;
   }

   // ------------------------------------------------------------------ drawing

   @Override
   void render(GuiGraphics g, int mx, int my) {
      this.ensureSorted();
      this.tip = null;
      this.headTip = null;
      List<FormattedCharSequence> summary = this.summary();
      int sy = this.cy() + 4;

      for (int i = 0; i < summary.size() && i < 2; i++) {
         g.drawString(this.font(), summary.get(i), this.cx() + 6, sy + i * 10, 0xFFE0D0A0, true);
      }

      int[] w = this.widths();
      int x0 = this.left();
      int tw = this.tableW();
      this.scroller.setSize(this.sorted.size() * ROW_H + 2, this.rowsH());
      int hover = this.rowAt(mx, my);
      Column hoverHead = this.headerAt(mx, my);

      // header
      g.fill(x0, this.headY(), x0 + tw, this.headY() + HEAD_H, 0xAA000000);
      g.enableScissor(x0, this.headY(), x0 + tw, this.headY() + HEAD_H);
      Column[] cols = Column.values();
      int x = x0 - this.scrollX;

      for (int i = 0; i < cols.length; i++) {
         Column c = cols[i];
         Component label = Component.translatable("journal.colonyjournal.stats.h." + c.key);
         boolean active = c == this.sortColumn;
         int color = active ? 0xFFFFE070 : (c == hoverHead ? 0xFFFFFFFF : 0xFFC8B890);
         int tx = c.text ? x + 4 : x + w[i] - 4 - this.font().width(label) - (active ? 7 : 0);
         g.drawString(this.font(), label, tx, this.headY() + 3, color, true);
         if (active) {
            this.arrow(g, c.text ? tx + this.font().width(label) + 3 : x + w[i] - 9, this.headY() + 5, this.ascending);
         }

         if (c == hoverHead) {
            this.headTip = Component.translatable("journal.colonyjournal.stats.col." + c.key);
         }

         x += w[i];
      }

      g.disableScissor();

      // rows
      g.enableScissor(x0, this.rowsY(), x0 + tw, this.rowsY() + this.rowsH());

      for (int r = 0; r < this.sorted.size(); r++) {
         int y = this.rowsY() - this.scroller.offset + r * ROW_H;
         if (y + ROW_H < this.rowsY() || y > this.rowsY() + this.rowsH()) {
            continue;
         }

         StatsRow row = this.sorted.get(r);
         int bg = row.own() ? 0x66B08A20 : (r % 2 == 0 ? 0x55000000 : 0x33000000);
         if (r == hover) {
            bg = row.own() ? 0x99C8A030 : 0x88505050;
         }

         g.fill(x0, y, x0 + tw, y + ROW_H, bg);
         this.renderRow(g, row, y, w);
         if (r == hover) {
            this.tip = this.rowTooltip(row);
         }
      }

      g.disableScissor();
      this.scroller.renderBar(g, this.cx() + this.cw() - 5, this.rowsY(), this.rowsH());
      if (this.maxScrollX > 0) {
         int bw = Math.max(12, tw * tw / (tw + this.maxScrollX));
         int bx = x0 + (tw - bw) * this.scrollX / this.maxScrollX;
         g.fill(bx, this.cy() + this.ch() - 3, bx + bw, this.cy() + this.ch() - 1, 0xFFB0A090);
      }
   }

   private void arrow(GuiGraphics g, int x, int y, boolean up) {
      for (int i = 0; i < 3; i++) {
         int half = up ? i : 2 - i;
         g.fill(x + 2 - half, y + i, x + 3 + half, y + i + 1, 0xFFFFE070);
      }
   }

   private void renderRow(GuiGraphics g, StatsRow row, int y, int[] w) {
      Column[] cols = Column.values();
      int x = this.left() - this.scrollX;
      int ty = y + 3;
      int nameColor = row.own() ? 0xFFFFE070 : 0xFFFFFFFF;

      for (int i = 0; i < cols.length; i++) {
         int cellW = w[i];
         int right = x + cellW - 4;
         switch (cols[i]) {
            case COLONY -> g.drawString(this.font(), this.font().plainSubstrByWidth(row.name(), cellW - 6), x + 4, ty, nameColor, true);
            case OWNER -> g.drawString(this.font(), this.font().plainSubstrByWidth(row.owner(), cellW - 6), x + 4, ty, row.own() ? 0xFFFFE070 : 0xFFB8B8B8, true);
            case CITIZENS -> Ui.textRight(g, this.font(), row.citizens() + "/" + row.maxCitizens(), right, ty, 0xFFFFFFFF);
            case HAPPINESS -> {
               String text = String.format(Locale.ROOT, "%.1f", row.happiness());
               int textW = this.font().width(text);
               int barW = Math.max(8, cellW - 8 - textW - 4);
               float f = row.happiness() / 10.0F;
               Ui.bar(g, x + 4, y + 3, barW, 7, f, Ui.heat(f));
               Ui.textRight(g, this.font(), text, right, ty, Ui.heat(f));
            }
            case TREASURY -> Ui.textRight(g, this.font(), Ui.coins(row.balance()), right, ty, row.balance() < 0L ? 0xFFFF6060 : 0xFFFFD75E);
            case FLOW -> {
               String minus = "-" + Ui.coins(row.expense());
               Ui.textRight(g, this.font(), minus, right, ty, 0xFFFF6060);
               String plus = "+" + Ui.coins(row.income()) + "/";
               Ui.textRight(g, this.font(), plus, right - this.font().width(minus), ty, 0xFF7FE08A);
            }
            case TAX -> Ui.textRight(g, this.font(), row.taxRate() + "%", right, ty, 0xFFFFFFFF);
            case BUILDINGS -> Ui.textRight(g, this.font(), String.valueOf(row.buildings()), right, ty, 0xFFFFFFFF);
            case LEVEL -> Ui.textRight(g, this.font(), String.format(Locale.ROOT, "%.1f", row.avgLevel()), right, ty, 0xFFFFFFFF);
            case DAY -> Ui.textRight(g, this.font(), String.valueOf(row.day()), right, ty, 0xFFB8B8B8);
         }

         x += cellW;
      }
   }

   private List<Component> rowTooltip(StatsRow row) {
      List<Component> lines = new ArrayList<>();
      lines.add(Component.literal(row.name()).withStyle(row.own() ? net.minecraft.ChatFormatting.GOLD : net.minecraft.ChatFormatting.WHITE));
      if (row.own()) {
         lines.add(Component.translatable("journal.colonyjournal.stats.own").withStyle(net.minecraft.ChatFormatting.YELLOW));
      }

      lines.add(this.stat("owner", row.owner()));
      lines.add(this.stat("citizens", row.citizens() + " / " + row.maxCitizens()));
      lines.add(this.stat("happiness", String.format(Locale.ROOT, "%.2f / 10", row.happiness())));
      lines.add(this.stat("treasury", Ui.coins(row.balance())));
      lines.add(this.stat("income", "+" + Ui.coins(row.income())));
      lines.add(this.stat("expense", "-" + Ui.coins(row.expense())));
      lines.add(this.stat("net", Ui.signed(row.income() - row.expense())));
      lines.add(this.stat("tax", row.taxRate() + "%"));
      lines.add(this.stat("buildings", String.valueOf(row.buildings())));
      lines.add(this.stat("level", String.format(Locale.ROOT, "%.2f", row.avgLevel())));
      lines.add(this.stat("day", String.valueOf(row.day())));
      return lines;
   }

   private Component stat(String key, String value) {
      return Component.translatable("journal.colonyjournal.stats.col." + key).append(": ").append(Component.literal(value).withStyle(net.minecraft.ChatFormatting.WHITE)).withStyle(net.minecraft.ChatFormatting.GRAY);
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      if (this.headTip != null) {
         g.renderTooltip(this.font(), this.headTip, mx, my);
      } else if (this.tip != null) {
         g.renderComponentTooltip(this.font(), this.tip, mx, my);
      }
   }

   // ------------------------------------------------------------------ input

   @Override
   boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return false;
      }

      Column column = this.headerAt(mx, my);
      if (column != null) {
         Ui.click();
         if (column == this.sortColumn) {
            this.ascending = !this.ascending;
         } else {
            this.sortColumn = column;
            this.ascending = column.text;
         }

         this.sortDirty = true;
         return true;
      }

      return false;
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (Screen.hasShiftDown() || sx != 0.0) {
         double amount = sx != 0.0 ? sx : sy;
         this.scrollX = Ui.clamp(this.scrollX - (int)(amount * 24.0), 0, this.maxScrollX);
      } else {
         this.scroller.wheel(sy);
      }

      return true;
   }
}
