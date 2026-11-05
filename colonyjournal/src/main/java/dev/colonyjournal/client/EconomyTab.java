package dev.colonyjournal.client;

import dev.colonyjournal.analysis.ColonyReport;
import dev.colonyjournal.net.Payloads;
import dev.colonyjournal.net.Views.EconomyView;
import dev.colonyjournal.net.Views.LineView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Treasury, tax slider, citizen mood, projected income, history chart and the income table of the colony. */
final class EconomyTab extends JournalTab {
   private static final ItemStack ICON = new ItemStack(Items.GOLD_INGOT);
   private static final int MSG_H = 14;
   // vertical layout, relative to the top of the scrolled content
   private static final int TAX_ROW = 48;
   private static final int SLIDER_HIT = 66;
   private static final int TRACK = 72;
   private static final int MARKER_LABEL = 85;
   private static final int MOOD_LABEL = 99;
   private static final int METER = 110;
   private static final int PROS_LABEL = 124;
   private static final int PROS_METER = 135;
   private static final int CHART_LABEL = 150;
   private static final int CHART = 162;
   private static final int CHART_H = 36;
   private static final int TABLE_HEAD = 206;
   private static final int TABLE_ROWS = 220;
   /** The tax mood factor runs from MOOD_MIN (furious) to MOOD_MAX (a tax far below what the colony tolerates). */
   private static final double MOOD_MIN = 0.5;
   private static final double MOOD_MAX = 1.10;
   private static final int LINE_H = 14;
   private static long lastRequest;

   private final Scroller scroller = new Scroller();
   private final Map<String, String> names = new HashMap<>();
   private int pending;
   private boolean dirty;
   private boolean confirmed;
   private int confirmTicks;
   private boolean sliding;
   private Component tip;

   EconomyTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "economy";
   }

   @Override
   boolean ownMessageLine() {
      return true;
   }

   /** Sends RequestEconomy, but not more often than twice a second, whichever tab asks. */
   static void request(JournalScreen screen) {
      long now = System.currentTimeMillis();
      if (now - lastRequest >= 500L) {
         lastRequest = now;
         Ui.send(new Payloads.RequestEconomy(screen.ref()));
      }
   }

   @Override
   void onOpen() {
      request(this.screen);
   }

   @Override
   int refreshInterval() {
      return 100;
   }

   @Override
   void requestRefresh() {
      request(this.screen);
   }

   @Override
   void tick() {
      EconomyView v = this.view();
      if (v == null || !this.dirty || this.sliding) {
         return;
      }

      if (this.confirmed) {
         if (v.taxRate() == this.pending || --this.confirmTicks <= 0) {
            this.dirty = false;
            this.confirmed = false;
         }
      } else if (v.taxRate() == this.pending) {
         this.dirty = false;
      }
   }

   private EconomyView view() {
      EconomyView v = ClientData.economy;
      return v != null && v.colony().equals(this.screen.ref()) ? v : null;
   }

   private String nameOf(String buildingId) {
      return this.names.computeIfAbsent(buildingId, id -> ColonyReport.nameOf(id).getString());
   }

   // ------------------------------------------------------------------ geometry shared by drawing and hit-testing

   private int left() {
      return this.cx() + 6;
   }

   private int width() {
      return this.cw() - 12 - 5;
   }

   private int viewH() {
      return this.ch() - MSG_H;
   }

   private int top() {
      return this.cy() + 4 - this.scroller.offset;
   }

   private boolean inView(double mx, double my) {
      return Ui.in(mx, my, this.cx(), this.cy(), this.cw(), this.viewH());
   }

   private int sliderX() {
      return this.left() + 4;
   }

   private int sliderW() {
      return Math.max(10, this.width() - 8);
   }

   private int rateX(int rate, int max) {
      return this.sliderX() + (max <= 0 ? 0 : rate * this.sliderW() / max);
   }

   private boolean overSlider(double mx, double my) {
      return this.inView(mx, my) && Ui.in(mx, my, this.left(), this.top() + SLIDER_HIT, this.width(), 22);
   }

   private int confirmX() {
      return this.left() + this.width() - 74;
   }

   private boolean overConfirm(double mx, double my) {
      return this.inView(mx, my) && Ui.in(mx, my, this.confirmX(), this.top() + TAX_ROW, 74, 16);
   }

   private void setFromMouse(double mx, EconomyView v) {
      int rate = (int)Math.round((mx - this.sliderX()) * v.maxRate() / this.sliderW());
      this.pending = Ui.clamp(rate, 0, Math.max(0, v.maxRate()));
      this.dirty = true;
      this.confirmed = false;
   }

   // ------------------------------------------------------------------ drawing

   @Override
   void render(GuiGraphics g, int mx, int my) {
      this.tip = null;
      EconomyView v = this.view();
      int msgY = this.cy() + this.ch() - 11;
      this.screen.renderMessage(g, this.left(), msgY, this.cw() - 12);
      if (v == null) {
         Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.loading"), this.left(), this.cy() + 8, 0xFFAAAAAA);
         int hy = this.cy() + 22;

         for (net.minecraft.util.FormattedCharSequence line : this.font().split(Component.translatable("journal.colonyjournal.loading_hint"), Math.max(20, this.cw() - 16))) {
            g.drawString(this.font(), line, this.left(), hy, 0xFF808080, true);
            hy += 10;
         }

         return;
      }

      if (!this.dirty) {
         this.pending = v.taxRate();
      }

      this.pending = Ui.clamp(this.pending, 0, Math.max(0, v.maxRate()));
      int rows = v.lines().size();
      this.scroller.setSize(TABLE_ROWS + Math.max(1, rows) * LINE_H + 8, this.viewH());
      int x = this.left();
      int w = this.width();
      int y = this.top();
      boolean live = this.inView(mx, my);
      g.enableScissor(this.cx(), this.cy(), this.cx() + this.cw(), this.cy() + this.viewH());
      this.renderHead(g, v, x, y, w, mx, my, live);
      this.renderTax(g, v, x, y, w, mx, my, live);
      this.renderMood(g, v, x, y, w, mx, my, live);
      this.renderProsperity(g, v, x, y, w, mx, my, live);
      this.renderChart(g, v, x, y, w, mx, my, live);
      this.renderTable(g, v, x, y, w, mx, my, live);
      g.disableScissor();
      this.scroller.renderBar(g, this.cx() + this.cw() - 5, this.cy(), this.viewH());
   }

   private void renderHead(GuiGraphics g, EconomyView v, int x, int y, int w, int mx, int my, boolean live) {
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.economy.balance"), x, y, 0xFFB8B8B8);
      String balance = Ui.coins(v.balance());
      g.pose().pushPose();
      g.pose().translate((float)x, (float)(y + 12), 0.0F);
      g.pose().scale(2.0F, 2.0F, 1.0F);
      g.drawString(this.font(), balance, 0, 0, v.balance() < 0L ? 0xFFFF5555 : 0xFFFFD75E, true);
      g.pose().popPose();
      Ui.coin(g, x + this.font().width(balance) * 2 + 4, y + 12);
      int x2 = Math.min(x + Math.max(w / 2, 130), x + w - 112);
      long net = v.income() + v.tradeIncome() - v.expense();
      this.line(g, x2, x + w, y, "journal.colonyjournal.economy.income", "+" + Ui.coins(v.income()), 0xFF7FE08A);
      this.line(g, x2, x + w, y + 11, "journal.colonyjournal.economy.trade", "+" + Ui.coins(v.tradeIncome()), v.tradeIncome() > 0L ? 0xFF7FE08A : 0xFF909090);
      this.line(g, x2, x + w, y + 22, "journal.colonyjournal.economy.expense", "-" + Ui.coins(v.expense()), 0xFFFF6060);
      this.line(g, x2, x + w, y + 33, "journal.colonyjournal.economy.net", Ui.signed(net), net < 0L ? 0xFFFF6060 : 0xFF7FE08A);
      if (live) {
         String[] tips = {"income_tip", "trade_tip", "expense_tip", "net_tip"};

         for (int i = 0; i < tips.length; i++) {
            if (Ui.in(mx, my, x2, y + i * 11, x + w - x2, 11)) {
               this.tip = Component.translatable("journal.colonyjournal.economy." + tips[i]);
            }
         }

         if (Ui.in(mx, my, x, y, Math.max(0, x2 - x - 4), 34)) {
            this.tip = Component.translatable("journal.colonyjournal.economy.balance_tip");
         }
      }
   }

   private void line(GuiGraphics g, int labelX, int right, int y, String key, String value, int color) {
      Ui.text(g, this.font(), Component.translatable(key), labelX, y, 0xFFC8C8C8);
      Ui.textRight(g, this.font(), value, right, y, color);
   }

   private void renderTax(GuiGraphics g, EconomyView v, int x, int y, int w, int mx, int my, boolean live) {
      Component label = Component.translatable("journal.colonyjournal.economy.tax_rate", this.pending);
      Ui.text(g, this.font(), label, x, y + TAX_ROW + 4, this.pending != v.taxRate() ? 0xFFFFE070 : 0xFFFFFFFF);
      boolean confirmOn = v.canManage() && this.pending != v.taxRate();
      boolean overConfirm = live && this.overConfirm(mx, my);
      Ui.button(g, this.font(), this.confirmX(), y + TAX_ROW, 74, 16, Component.translatable("journal.colonyjournal.economy.confirm"), confirmOn, overConfirm);
      if (overConfirm) {
         this.tip = Component.translatable(!v.canManage() ? "msg.colonyjournal.no_permission" : (confirmOn ? "journal.colonyjournal.economy.confirm_tip" : "journal.colonyjournal.economy.confirm_none_tip"));
      }

      int max = Math.max(1, v.maxRate());
      int sx = this.sliderX();
      int sw = this.sliderW();
      int ty = y + TRACK;
      int marker = this.rateX(Math.min(v.tolerableRate(), max), max);
      int yellowEnd = marker + (int)((sx + sw - marker) * 0.45);
      g.fill(sx - 1, ty - 1, sx + sw + 1, ty + 9, 0xFF000000);
      g.fill(sx, ty, marker, ty + 8, 0xFF3F9F4F);
      g.fill(marker, ty, yellowEnd, ty + 8, 0xFFC8A838);
      g.fill(yellowEnd, ty, sx + sw, ty + 8, 0xFFB04040);
      g.fill(marker - 1, ty - 4, marker + 2, ty + 12, 0xFF000000);
      g.fill(marker, ty - 3, marker + 1, ty + 11, 0xFFFFFFFF);
      boolean overSlider = live && this.overSlider(mx, my);
      int hx = this.rateX(this.pending, max);
      g.blitSprite(overSlider || this.sliding ? Ui.HANDLE_HOVER : Ui.HANDLE, hx - 4, ty - 4, 8, 16);
      String mark = Component.translatable("journal.colonyjournal.economy.tax_marker", v.tolerableRate()).getString();
      int markW = this.font().width(mark);
      Ui.text(g, this.font(), mark, Ui.clamp(marker - markW / 2, x, x + w - markW), y + MARKER_LABEL, 0xFFE8E8E8);
      if (overSlider && !this.sliding) {
         this.tip = Component.translatable("journal.colonyjournal.economy.tax_tip", v.tolerableRate());
      }
   }

   private void renderMood(GuiGraphics g, EconomyView v, int x, int y, int w, int mx, int my, boolean live) {
      int pct = (int)Math.round(v.taxFactor() * 100.0);
      float fraction = (float)Math.max(0.0, Math.min(1.0, (v.taxFactor() - MOOD_MIN) / (MOOD_MAX - MOOD_MIN)));
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.economy.mood", pct), x, y + MOOD_LABEL, 0xFFFFFFFF);
      Ui.bar(g, x, y + METER, w, 9, fraction, Ui.heat(fraction));
      if (live && Ui.in(mx, my, x, y + MOOD_LABEL - 2, w, METER + 11 - MOOD_LABEL)) {
         this.tip = Component.translatable("journal.colonyjournal.economy.mood_tip", v.tolerableRate(), v.taxRate());
      }
   }

   private void renderProsperity(GuiGraphics g, EconomyView v, int x, int y, int w, int mx, int my, boolean live) {
      double p = Math.max(0.0, Math.min(1.0, v.prosperity()));
      int pct = (int)Math.round(p * 100.0);
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.economy.prosperity", pct), x, y + PROS_LABEL, 0xFFFFFFFF);
      Ui.bar(g, x, y + PROS_METER, w, 9, (float)p, Ui.heat((float)p));
      if (live && Ui.in(mx, my, x, y + PROS_LABEL - 2, w, PROS_METER + 11 - PROS_LABEL)) {
         this.tip = Component.translatable("journal.colonyjournal.economy.prosperity_tip");
      }
   }

   /** Number of history days shown and the width of one bar column. */
   private int stride(int count, int w) {
      return Ui.clamp((w - 4) / Math.max(1, count), 3, 14);
   }

   private void renderChart(GuiGraphics g, EconomyView v, int x, int y, int w, int mx, int my, boolean live) {
      List<Long> history = v.history();
      int maxBars = Math.max(1, (w - 4) / 3);
      int from = Math.max(0, history.size() - maxBars);
      int count = history.size() - from;
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.economy.history", count), x, y + CHART_LABEL, 0xFFFFFFFF);
      int cy = y + CHART;
      g.fill(x, cy, x + w, cy + CHART_H, 0x66000000);
      g.renderOutline(x, cy, w, CHART_H, 0xFF000000);
      if (count == 0) {
         Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.economy.history_none"), x + 6, cy + 14, 0xFF909090);
         return;
      }

      long lo = 0L;
      long hi = 0L;

      for (int i = from; i < history.size(); i++) {
         lo = Math.min(lo, history.get(i));
         hi = Math.max(hi, history.get(i));
      }

      long range = Math.max(1L, hi - lo);
      int inner = CHART_H - 6;
      int zero = cy + 3 + (int)(hi * inner / range);
      int stride = this.stride(count, w);
      g.fill(x + 1, zero, x + w - 1, zero + 1, 0x88FFFFFF);

      for (int i = 0; i < count; i++) {
         long value = history.get(from + i);
         int bx = x + 2 + i * stride;
         int bh = value == 0L ? 0 : Math.max(1, (int)(Math.abs(value) * inner / range));
         if (value > 0L) {
            g.fill(bx, zero - bh, bx + stride - 1, zero, 0xFF55CC66);
         } else if (value < 0L) {
            g.fill(bx, zero + 1, bx + stride - 1, zero + 1 + bh, 0xFFE05050);
         }

         if (live && Ui.in(mx, my, bx, cy, stride, CHART_H)) {
            int daysAgo = history.size() - 1 - (from + i);
            this.tip = Component.translatable("journal.colonyjournal.economy.history_tip", daysAgo, Ui.signed(value));
         }
      }
   }

   private void renderTable(GuiGraphics g, EconomyView v, int x, int y, int w, int mx, int my, boolean live) {
      int right = x + w - 4;
      int amountRight = right;
      int workersRight = amountRight - 66;
      int countRight = workersRight - 48;
      int head = y + TABLE_HEAD;
      g.fill(x, head, x + w, head + 12, 0x88000000);
      if (live && Ui.in(mx, my, x, head, w, 12)) {
         this.tip = Component.translatable("journal.colonyjournal.economy.table_tip");
      }

      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.economy.col_building"), x + 4, head + 2, 0xFFE0D0A0);
      Ui.textRight(g, this.font(), Component.translatable("journal.colonyjournal.economy.col_count").getString(), countRight, head + 2, 0xFFE0D0A0);
      Ui.textRight(g, this.font(), Component.translatable("journal.colonyjournal.economy.col_workers").getString(), workersRight, head + 2, 0xFFE0D0A0);
      Ui.textRight(g, this.font(), Component.translatable("journal.colonyjournal.economy.col_amount").getString(), amountRight, head + 2, 0xFFE0D0A0);
      int ry = y + TABLE_ROWS;
      if (v.lines().isEmpty()) {
         Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.no_buildings"), x + 4, ry + 3, 0xFF909090);
         return;
      }

      int nameW = countRight - 70 - (x + 20);

      for (int i = 0; i < v.lines().size(); i++) {
         LineView line = v.lines().get(i);
         int rowY = ry + i * LINE_H;
         if (rowY + LINE_H < this.cy() || rowY > this.cy() + this.viewH()) {
            continue;
         }

         g.fill(x, rowY, x + w, rowY + LINE_H, i % 2 == 0 ? 0x44000000 : 0x22000000);
         g.pose().pushPose();
         g.pose().translate((float)(x + 3), (float)(rowY + 1), 0.0F);
         g.pose().scale(0.75F, 0.75F, 1.0F);
         g.renderItem(BuildingIcons.of(line.buildingId()), 0, 0);
         g.pose().popPose();
         String name = this.font().plainSubstrByWidth(this.nameOf(line.buildingId()), Math.max(20, nameW));
         Ui.text(g, this.font(), name, x + 20, rowY + 3, 0xFFFFFFFF);
         Ui.textRight(g, this.font(), line.count() + "×" + line.levels(), countRight, rowY + 3, 0xFFCCCCCC);
         Ui.textRight(g, this.font(), String.valueOf(line.workers()), workersRight, rowY + 3, 0xFFCCCCCC);
         long amount = line.amount();
         Ui.textRight(g, this.font(), Ui.signed(amount), amountRight, rowY + 3, amount > 0L ? 0xFF7FE08A : (amount < 0L ? 0xFFFF6060 : 0xFF909090));
      }
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      if (this.tip != null) {
         Ui.tooltip(g, this.font(), this.tip, mx, my);
      }
   }

   // ------------------------------------------------------------------ input

   @Override
   boolean mouseClicked(double mx, double my, int button) {
      EconomyView v = this.view();
      if (button != 0 || v == null || !this.inView(mx, my)) {
         return false;
      }

      if (this.overConfirm(mx, my)) {
         if (v.canManage() && this.pending != v.taxRate()) {
            Ui.click();
            Ui.send(new Payloads.SetTax(this.screen.ref(), this.pending));
            this.dirty = true;
            this.confirmed = true;
            this.confirmTicks = 60;
         }

         return true;
      }

      if (v.canManage() && this.overSlider(mx, my)) {
         this.sliding = true;
         this.setFromMouse(mx, v);
         return true;
      }

      return false;
   }

   @Override
   boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      EconomyView v = this.view();
      if (this.sliding && v != null) {
         this.setFromMouse(mx, v);
         return true;
      }

      return false;
   }

   @Override
   boolean mouseReleased(double mx, double my, int button) {
      boolean was = this.sliding;
      this.sliding = false;
      return was;
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      EconomyView v = this.view();
      if (v != null && v.canManage() && this.overSlider(mx, my)) {
         this.pending = Ui.clamp(this.pending + (sy > 0.0 ? 1 : -1), 0, Math.max(0, v.maxRate()));
         this.dirty = true;
         this.confirmed = false;
         return true;
      }

      this.scroller.wheel(sy);
      return true;
   }
}
