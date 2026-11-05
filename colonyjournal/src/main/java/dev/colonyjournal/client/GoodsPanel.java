package dev.colonyjournal.client;

import dev.colonyjournal.net.Payloads;
import dev.colonyjournal.net.Views.EconomyView;
import dev.colonyjournal.net.Views.GoodView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The "Colony goods" sub-tab of the market: goods the colonies produce and sell by themselves. */
final class GoodsPanel {
   private static final long MIN_INTERVAL_MS = 2200L;
   private static final long AUTO_INTERVAL_MS = 10000L;
   private static final long BUY_GAP_MS = 300L;
   private static final int ROW_H = 24;
   private static final int GAP = 2;
   private static final int BTN_W = 22;
   private static final int BTN_STEP = 24;
   private static final int BTN_BLOCK = BTN_STEP * 2 + BTN_W;
   private static final int PRICE_W = 36;
   private static final int BAR_W = 44;
   private static final int STOCK_W = 26;
   private static final int NAME_MIN = 48;
   private static final int[] UNITS = {1, 8, 64};
   private static long lastRequest;
   private static long lastBuy;

   private enum Sort {
      ITEM("item"),
      COLONY("colony"),
      PROSPERITY("prosperity"),
      PRICE("price");

      final String key;

      Sort(String key) {
         this.key = key;
      }
   }

   private record Cols(int btnX, int priceRight, int barX, boolean barOn, int stockRight, boolean stockOn, int nameRight) {
   }

   private final MarketTab tab;
   private final Scroller scroller = new Scroller();
   private final Map<String, ItemStack> stacks = new HashMap<>();
   private final List<GoodView> shown = new ArrayList<>();
   private List<GoodView> source;
   private String shownFilter = "";
   private Sort sort = Sort.ITEM;
   private boolean ascending = true;
   private boolean dirty = true;
   private EditBox filterBox;
   private String filter = "";
   private int goodsIn = -1;
   private int economyIn = -1;
   private ItemStack tipStack = ItemStack.EMPTY;
   private Component tip;

   GoodsPanel(MarketTab tab) {
      this.tab = tab;
   }

   // ------------------------------------------------------------------ requests

   /** Sends RequestGoods unless one went out less than 2.2 s ago (shared by every journal screen). */
   static boolean request() {
      long now = System.currentTimeMillis();
      if (now - lastRequest >= MIN_INTERVAL_MS) {
         lastRequest = now;
         Ui.send(new Payloads.RequestGoods());
         return true;
      }

      return false;
   }

   /** Called by the periodic refresh: asks again only when the last request is about 10 s old. */
   void autoRefresh() {
      if (System.currentTimeMillis() - lastRequest >= AUTO_INTERVAL_MS - 500L) {
         request();
      }
   }

   void tick() {
      if (this.goodsIn > 0) {
         this.goodsIn--;
      }

      if (this.goodsIn == 0 && request()) {
         this.goodsIn = -1;
      }

      if (this.economyIn > 0 && --this.economyIn == 0) {
         this.economyIn = -1;
         EconomyTab.request(this.tab.screen);
      }
   }

   // ------------------------------------------------------------------ geometry

   private int left() {
      return this.tab.cx() + 6;
   }

   private int width() {
      return this.tab.cw() - 12;
   }

   private int listW() {
      return this.width() - 6;
   }

   private int topY() {
      return this.tab.cy() + MarketTab.SUB_H;
   }

   private int headY() {
      return this.topY() + 18;
   }

   private int listTop() {
      return this.headY() + 13;
   }

   private int listH() {
      return Math.max(0, this.tab.cy() + this.tab.ch() - 13 - this.listTop());
   }

   private int rowY(int index) {
      return this.listTop() + 2 - this.scroller.offset + index * (ROW_H + GAP);
   }

   private boolean inList(double mx, double my) {
      return Ui.in(mx, my, this.left(), this.listTop(), this.width(), this.listH());
   }

   private Cols cols() {
      int x = this.left();
      int btnX = x + this.listW() - 4 - BTN_BLOCK;
      int priceRight = btnX - 4;
      int priceLeft = priceRight - PRICE_W;
      int nameLeft = x + 26;
      int barX = priceLeft - 4 - BAR_W;
      boolean barOn = barX - 4 - nameLeft >= NAME_MIN;
      int after = barOn ? barX : priceLeft;
      int stockRight = after - 4;
      boolean stockOn = stockRight - STOCK_W - 4 - nameLeft >= NAME_MIN;
      int leftmost = stockOn ? stockRight - STOCK_W : after;
      return new Cols(btnX, priceRight, barX, barOn, stockRight, stockOn, leftmost - 4);
   }

   int filterX() {
      return this.left() + this.width() - this.filterW();
   }

   int filterY() {
      return this.topY() + 2;
   }

   int filterW() {
      return Math.max(50, Math.min(96, this.width() / 3));
   }

   // ------------------------------------------------------------------ widgets

   void init() {
      this.filterBox = new EditBox(this.tab.font(), this.filterX(), this.filterY(), this.filterW(), 14, Component.translatable("journal.colonyjournal.goods.filter"));
      this.filterBox.setMaxLength(32);
      this.filterBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("journal.colonyjournal.goods.filter_tip")));
      this.filterBox.setHint(Component.translatable("journal.colonyjournal.goods.filter").withStyle(ChatFormatting.DARK_GRAY));
      this.filterBox.setValue(this.filter);
      this.filterBox.setResponder(s -> {
         this.filter = s;
         this.dirty = true;
         this.scroller.reset();
      });
      this.tab.screen.addTabWidget(this.filterBox);
   }

   void setActive(boolean on) {
      if (this.filterBox != null) {
         this.filterBox.visible = on;
         this.filterBox.active = on;
         if (!on) {
            this.filterBox.setFocused(false);
         }
      }
   }

   // ------------------------------------------------------------------ data

   private ItemStack stackOf(String id) {
      return this.stacks.computeIfAbsent(id, key -> {
         ResourceLocation rl = ResourceLocation.tryParse(key);
         Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
         return new ItemStack(item == null ? Items.BARRIER : item);
      });
   }

   private String nameOf(GoodView g) {
      return this.stackOf(g.item()).getHoverName().getString();
   }

   private void ensureSorted() {
      List<GoodView> data = ClientData.goods;
      String f = this.filter.trim().toLowerCase(Locale.ROOT);
      if (data == this.source && !this.dirty && f.equals(this.shownFilter)) {
         return;
      }

      this.source = data;
      this.dirty = false;
      this.shownFilter = f;
      this.shown.clear();

      for (GoodView g : data) {
         if (f.isEmpty() || this.nameOf(g).toLowerCase(Locale.ROOT).contains(f) || g.colonyName().toLowerCase(Locale.ROOT).contains(f) || g.item().contains(f)) {
            this.shown.add(g);
         }
      }

      Comparator<GoodView> byItem = Comparator.comparing((GoodView g) -> this.nameOf(g).toLowerCase(Locale.ROOT)).thenComparing(g -> g.colonyName().toLowerCase(Locale.ROOT));
      Comparator<GoodView> cmp = switch (this.sort) {
         case ITEM -> byItem;
         case COLONY -> Comparator.comparing((GoodView g) -> g.colonyName().toLowerCase(Locale.ROOT)).thenComparing(byItem);
         case PROSPERITY -> Comparator.comparingInt(GoodView::prosperityPct).thenComparing(byItem);
         case PRICE -> Comparator.comparingInt(GoodView::unitPrice).thenComparing(byItem);
      };
      this.shown.sort(this.ascending ? cmp : cmp.reversed());
   }

   private static int units(GoodView g, int wanted) {
      return Math.min(wanted, g.stock());
   }

   /** Null when the purchase is possible, otherwise the reason it is not. */
   private Component blocked(GoodView g, int wanted, EconomyView v) {
      if (v == null || !v.canManage()) {
         return Component.translatable("msg.colonyjournal.no_permission");
      }

      if (g.stock() <= 0) {
         return Component.translatable("msg.colonyjournal.no_stock");
      }

      long cost = (long)units(g, wanted) * g.unitPrice();
      if (v.balance() < cost) {
         return Component.translatable("msg.colonyjournal.no_funds", Ui.coins(cost - v.balance()));
      }

      return null;
   }

   // ------------------------------------------------------------------ drawing

   void render(GuiGraphics g, int mx, int my, EconomyView v) {
      this.tip = null;
      this.tipStack = ItemStack.EMPTY;
      this.ensureSorted();
      int x = this.left();
      int w = this.width();
      int topY = this.topY();
      Ui.coin(g, x, topY + 1);
      if (v == null) {
         Ui.text(g, this.tab.font(), Component.translatable("journal.colonyjournal.loading"), x + 18, topY + 5, 0xFFAAAAAA);
      } else {
         String treasury = this.tab.font().plainSubstrByWidth(Component.translatable("journal.colonyjournal.market.treasury", Ui.coins(v.balance())).getString(), Math.max(20, this.filterX() - x - 22));
         Ui.text(g, this.tab.font(), treasury, x + 18, topY + 5, v.balance() < 0L ? 0xFFFF6060 : 0xFFFFD75E);
      }

      Cols c = this.cols();
      this.renderHeader(g, mx, my, c);
      List<GoodView> rows = this.shown;
      this.scroller.setSize(4 + rows.size() * (ROW_H + GAP), this.listH());
      int listTop = this.listTop();
      int listH = this.listH();
      g.enableScissor(this.tab.cx(), listTop, this.tab.cx() + this.tab.cw(), listTop + listH);
      if (rows.isEmpty()) {
         String key = ClientData.goods.isEmpty() ? "journal.colonyjournal.goods.none" : "journal.colonyjournal.goods.no_match";
         int ey = listTop + 8;

         for (net.minecraft.util.FormattedCharSequence line : this.tab.font().split(Component.translatable(key), Math.max(20, w - 8))) {
            g.drawString(this.tab.font(), line, x + 4, ey, 0xFF909090, true);
            ey += 10;
         }
      }

      for (int i = 0; i < rows.size(); i++) {
         int y = this.rowY(i);
         if (y + ROW_H < listTop || y > listTop + listH) {
            continue;
         }

         this.renderRow(g, mx, my, v, c, rows.get(i), y);
      }

      g.disableScissor();
      this.scroller.renderBar(g, this.tab.cx() + this.tab.cw() - 5, listTop, listH);
      this.tab.screen.renderMessage(g, x, this.tab.cy() + this.tab.ch() - 11, w);
   }

   private Sort headerAt(double mx, double my, Cols c) {
      if (!Ui.in(mx, my, this.left(), this.headY(), this.width(), 12)) {
         return null;
      }

      int nameLeft = this.left() + 26;
      int itemW = this.tab.font().width(Component.translatable("journal.colonyjournal.goods.col_item"));
      int colonyW = this.tab.font().width(Component.translatable("journal.colonyjournal.goods.col_colony"));
      if (mx >= nameLeft && mx < nameLeft + itemW + 2) {
         return Sort.ITEM;
      }

      if (mx >= nameLeft + itemW + 8 && mx < nameLeft + itemW + 8 + colonyW + 2) {
         return Sort.COLONY;
      }

      if (c.barOn() && mx >= c.barX() && mx < c.barX() + BAR_W) {
         return Sort.PROSPERITY;
      }

      if (mx >= c.priceRight() - PRICE_W && mx < c.priceRight()) {
         return Sort.PRICE;
      }

      return null;
   }

   private void renderHeader(GuiGraphics g, int mx, int my, Cols c) {
      int x = this.left();
      int y = this.headY();
      g.fill(x, y, x + this.listW(), y + 12, 0x88000000);
      Sort hover = this.headerAt(mx, my, c);
      int nameLeft = x + 26;
      int itemW = this.tab.font().width(Component.translatable("journal.colonyjournal.goods.col_item"));
      this.headLabel(g, Sort.ITEM, nameLeft, itemW + 2, nameLeft, hover);
      this.headLabel(g, Sort.COLONY, nameLeft + itemW + 8, this.tab.font().width(Component.translatable("journal.colonyjournal.goods.col_colony")) + 2, nameLeft + itemW + 8, hover);
      if (c.barOn()) {
         this.headLabel(g, Sort.PROSPERITY, c.barX(), BAR_W, c.barX(), hover);
      }

      this.headLabel(g, Sort.PRICE, c.priceRight() - PRICE_W, PRICE_W, c.priceRight() - PRICE_W, hover);
      if (c.stockOn()) {
         String stock = this.tab.font().plainSubstrByWidth(Component.translatable("journal.colonyjournal.goods.col_stock").getString(), STOCK_W);
         Ui.textRight(g, this.tab.font(), stock, c.stockRight(), y + 2, 0xFFE0D0A0);
      }

      if (hover != null) {
         this.tip = Component.translatable("journal.colonyjournal.goods.sort_tip", Component.translatable("journal.colonyjournal.goods.col_" + hover.key));
      }
   }

   private void headLabel(GuiGraphics g, Sort s, int x, int w, int textX, Sort hover) {
      String label = Component.translatable("journal.colonyjournal.goods.col_" + s.key).getString();
      label = this.tab.font().plainSubstrByWidth(label, Math.max(8, w));
      int color = s == this.sort ? 0xFFFFFFFF : (s == hover ? 0xFFFFF0B0 : 0xFFE0D0A0);
      Ui.text(g, this.tab.font(), label, textX, this.headY() + 2, color);
      if (s == this.sort) {
         int arrowX = textX + this.tab.font().width(label) + 2;
         g.drawString(this.tab.font(), this.ascending ? "^" : "v", arrowX, this.headY() + 2, 0xFFFFFFFF, true);
      }
   }

   private void renderRow(GuiGraphics g, int mx, int my, EconomyView v, Cols c, GoodView good, int y) {
      int x = this.left();
      int listW = this.listW();
      boolean inList = this.inList(mx, my);
      boolean hoverRow = inList && Ui.in(mx, my, x, y, c.btnX() - x - 2, ROW_H);
      g.fill(x, y, x + listW, y + ROW_H, hoverRow ? 0x88404040 : 0x77000000);
      g.renderOutline(x, y, listW, ROW_H, 0xFF000000);
      ItemStack base = this.stackOf(good.item());
      ItemStack stack = base.copy();
      stack.setCount(Math.max(1, Math.min(good.stock(), Math.max(1, base.getMaxStackSize()))));
      g.blitSprite(Ui.SLOT, x + 3, y + 3, 18, 18);
      g.renderItem(stack, x + 4, y + 4);
      g.renderItemDecorations(this.tab.font(), stack, x + 4, y + 4);
      int nameLeft = x + 26;
      int nameW = Math.max(16, c.nameRight() - nameLeft);
      Ui.text(g, this.tab.font(), this.tab.font().plainSubstrByWidth(this.nameOf(good), nameW), nameLeft, y + 4, good.stock() > 0 ? 0xFFFFFFFF : 0xFF909090);
      String sellerKey = good.own() ? "journal.colonyjournal.goods.seller_own" : "journal.colonyjournal.goods.seller";
      String seller = Component.translatable(sellerKey, good.colonyName(), good.ownerName()).getString();
      Ui.text(g, this.tab.font(), this.tab.font().plainSubstrByWidth(seller, nameW), nameLeft, y + 14, good.own() ? 0xFFE0C040 : 0xFF9A9A9A);
      if (c.stockOn()) {
         Ui.textRight(g, this.tab.font(), "x" + good.stock(), c.stockRight(), y + 8, good.stock() > 0 ? 0xFFCCCCCC : 0xFFFF6060);
      }

      Ui.textRight(g, this.tab.font(), Ui.coins(good.unitPrice()), c.priceRight(), y + 4, 0xFFFFD75E);
      Ui.textRight(g, this.tab.font(), Component.translatable("journal.colonyjournal.goods.per_unit").getString(), c.priceRight(), y + 14, 0xFF9A9A9A);
      if (c.barOn()) {
         float frac = Ui.clamp(good.prosperityPct(), 0, 100) / 100.0F;
         Ui.bar(g, c.barX(), y + 9, BAR_W, 7, frac, Ui.heat(frac));
         if (inList && Ui.in(mx, my, c.barX(), y + 5, BAR_W, 15)) {
            this.tip = Component.translatable("journal.colonyjournal.goods.prosperity_tip", good.prosperityPct());
         }
      }

      boolean overButton = false;

      for (int b = 0; b < UNITS.length; b++) {
         int bx = c.btnX() + b * BTN_STEP;
         Component reason = this.blocked(good, UNITS[b], v);
         boolean hover = inList && Ui.in(mx, my, bx, y + 4, BTN_W, 16);
         Ui.button(g, this.tab.font(), bx, y + 4, BTN_W, 16, Component.literal("x" + UNITS[b]), reason == null, hover);
         if (hover) {
            overButton = true;
            if (reason != null) {
               this.tip = reason;
            } else {
               int n = units(good, UNITS[b]);
               this.tip = Component.translatable("journal.colonyjournal.goods.buy_tip", n, Ui.coins((long)n * good.unitPrice()));
            }
         }
      }

      if (hoverRow && !overButton && this.tip == null) {
         this.tipStack = stack;
      }
   }

   void renderTooltips(GuiGraphics g, int mx, int my) {
      if (this.tip != null) {
         Ui.tooltip(g, this.tab.font(), this.tip, mx, my);
      } else if (!this.tipStack.isEmpty()) {
         g.renderTooltip(this.tab.font(), this.tipStack, mx, my);
      }
   }

   // ------------------------------------------------------------------ input

   boolean mouseClicked(double mx, double my, EconomyView v) {
      Cols c = this.cols();
      Sort head = this.headerAt(mx, my, c);
      if (head != null) {
         Ui.click();
         if (head == this.sort) {
            this.ascending = !this.ascending;
         } else {
            this.sort = head;
            this.ascending = head == Sort.ITEM || head == Sort.COLONY;
         }

         this.dirty = true;
         return true;
      }

      if (!this.inList(mx, my)) {
         return false;
      }

      for (int i = 0; i < this.shown.size(); i++) {
         int y = this.rowY(i);
         for (int b = 0; b < UNITS.length; b++) {
            if (Ui.in(mx, my, c.btnX() + b * BTN_STEP, y + 4, BTN_W, 16)) {
               this.buy(this.shown.get(i), UNITS[b], v);
               return true;
            }
         }
      }

      return false;
   }

   private void buy(GoodView good, int wanted, EconomyView v) {
      long now = System.currentTimeMillis();
      if (this.blocked(good, wanted, v) != null || now - lastBuy < BUY_GAP_MS) {
         return;
      }

      lastBuy = now;
      Ui.click();
      Ui.send(new Payloads.BuyGoods(good.colony(), good.item(), units(good, wanted), this.tab.screen.ref(), good.unitPrice()));
      this.goodsIn = 8;
   }

   boolean mouseScrolled(double sy) {
      this.scroller.wheel(sy);
      return true;
   }
}
