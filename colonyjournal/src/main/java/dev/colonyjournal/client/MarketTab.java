package dev.colonyjournal.client;

import dev.colonyjournal.net.Payloads;
import dev.colonyjournal.net.Views.EconomyView;
import dev.colonyjournal.net.Views.OfferView;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The market. Sub-tab "Colony goods": goods the colonies sell by themselves. Sub-tab "Player offers": buy offers with the
 * colony treasury, withdraw own offers, sell the item in the main hand.
 */
final class MarketTab extends JournalTab {
   private static final ItemStack ICON = new ItemStack(Items.EMERALD);
   private static final int HEADER_H = 18;
   private static final int ROW_H = 24;
   private static final int GAP = 2;
   private static final int SELL_H = 52;
   private static final int BUTTON_W = 56;
   private static final int MAX_PRICE = 1000000;
   /** Height of the sub-tab row at the top of the content. */
   static final int SUB_H = 17;

   private final Scroller scroller = new Scroller();
   private final GoodsPanel goods = new GoodsPanel(this);
   private boolean showGoods = true;
   private EditBox priceBox;
   private String priceText = "";
   private int refreshIn = -1;
   private Component tip;
   private ItemStack tipStack = ItemStack.EMPTY;

   MarketTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "market";
   }

   @Override
   boolean ownMessageLine() {
      return true;
   }

   @Override
   void onOpen() {
      EconomyTab.request(this.screen);
      if (this.showGoods) {
         GoodsPanel.request();
      }
   }

   @Override
   int refreshInterval() {
      return 100;
   }

   @Override
   void requestRefresh() {
      EconomyTab.request(this.screen);
      if (this.showGoods) {
         this.goods.autoRefresh();
      }
   }

   @Override
   void tick() {
      this.goods.tick();
      if (this.refreshIn > 0 && --this.refreshIn == 0) {
         this.refreshIn = -1;
         EconomyTab.request(this.screen);
      }
   }

   private EconomyView view() {
      EconomyView v = ClientData.economy;
      return v != null && v.colony().equals(this.screen.ref()) ? v : null;
   }

   // ------------------------------------------------------------------ geometry shared by drawing and hit-testing

   private int left() {
      return this.cx() + 6;
   }

   private int width() {
      return this.cw() - 12;
   }

   private int listTop() {
      return this.cy() + SUB_H + HEADER_H;
   }

   private int listH() {
      return Math.max(0, this.ch() - SUB_H - HEADER_H - SELL_H - 4);
   }

   private int subW() {
      return Math.max(40, Math.min(110, (this.width() - 4) / 2));
   }

   private int subX(int index) {
      return this.left() + index * (this.subW() + 4);
   }

   private int subY() {
      return this.cy() + 2;
   }

   /** The sub-tab button under the mouse: 0 goods, 1 offers, -1 none. */
   private int subAt(double mx, double my) {
      for (int i = 0; i < 2; i++) {
         if (Ui.in(mx, my, this.subX(i), this.subY(), this.subW(), 14)) {
            return i;
         }
      }

      return -1;
   }

   private void setMode(boolean goodsMode) {
      this.showGoods = goodsMode;
      this.applyMode();
      if (goodsMode) {
         GoodsPanel.request();
      } else {
         EconomyTab.request(this.screen);
      }
   }

   private void applyMode() {
      this.goods.setActive(this.showGoods);
      if (this.priceBox != null) {
         this.priceBox.visible = !this.showGoods;
         this.priceBox.active = !this.showGoods;
         if (this.showGoods) {
            this.priceBox.setFocused(false);
         }
      }
   }

   private void renderSubTabs(GuiGraphics g, int mx, int my) {
      int hover = this.subAt(mx, my);

      for (int i = 0; i < 2; i++) {
         boolean selected = (i == 0) == this.showGoods;
         String key = i == 0 ? "journal.colonyjournal.market.sub_goods" : "journal.colonyjournal.market.sub_offers";
         String label = this.font().plainSubstrByWidth(Component.translatable(key).getString(), this.subW() - 6);
         Ui.button(g, this.font(), this.subX(i), this.subY(), this.subW(), 14, Component.literal(label), !selected, hover == i);
         if (selected) {
            g.fill(this.subX(i), this.subY() + 13, this.subX(i) + this.subW(), this.subY() + 14, 0xFFE0C040);
         }
      }
   }

   private int sellTop() {
      return this.cy() + this.ch() - SELL_H;
   }

   private int listW() {
      return this.width() - 6;
   }

   private int rowY(int index) {
      return this.listTop() + 2 - this.scroller.offset + index * (ROW_H + GAP);
   }

   private int buttonX() {
      return this.left() + this.listW() - BUTTON_W - 4;
   }

   private boolean inList(double mx, double my) {
      return Ui.in(mx, my, this.left(), this.listTop(), this.width(), this.listH());
   }

   private int postX() {
      return this.left() + this.width() - BUTTON_W;
   }

   private int boxX() {
      return this.postX() - 4 - 72;
   }

   private int sellRowY() {
      return this.sellTop() + 17;
   }

   private boolean overPost(double mx, double my) {
      return Ui.in(mx, my, this.postX(), this.sellRowY(), BUTTON_W, 16);
   }

   /** The price in the box, or -1 when it is not a number from 1 to 1000000. */
   private int price() {
      try {
         int p = Integer.parseInt(this.priceText);
         return p >= 1 && p <= MAX_PRICE ? p : -1;
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   private static ItemStack held() {
      Minecraft mc = Minecraft.getInstance();
      return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
   }

   @Override
   void init() {
      this.priceBox = new EditBox(this.font(), this.boxX(), this.sellRowY(), 72, 16, Component.translatable("journal.colonyjournal.market.price"));
      this.priceBox.setMaxLength(7);
      this.priceBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("journal.colonyjournal.market.price_tip")));
      this.priceBox.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9'));
      this.priceBox.setHint(Component.translatable("journal.colonyjournal.market.price").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
      this.priceBox.setValue(this.priceText);
      this.priceBox.setResponder(s -> this.priceText = s);
      this.screen.addTabWidget(this.priceBox);
      this.goods.init();
      this.applyMode();
   }

   // ------------------------------------------------------------------ drawing

   @Override
   void render(GuiGraphics g, int mx, int my) {
      this.tip = null;
      this.tipStack = ItemStack.EMPTY;
      EconomyView v = this.view();
      int x = this.left();
      int w = this.width();
      this.renderSubTabs(g, mx, my);
      if (this.subAt(mx, my) >= 0) {
         this.tip = Component.translatable(this.subAt(mx, my) == 0 ? "journal.colonyjournal.market.sub_goods_tip" : "journal.colonyjournal.market.sub_offers_tip");
      }

      if (this.showGoods) {
         this.goods.render(g, mx, my, v);
         return;
      }

      this.renderSell(g, mx, my);
      if (v == null) {
         Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.loading"), x, this.cy() + SUB_H + 6, 0xFFAAAAAA);
         return;
      }

      int head = this.cy() + SUB_H;
      Ui.coin(g, x, head + 1);
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.market.treasury", Ui.coins(v.balance())), x + 18, head + 5, v.balance() < 0L ? 0xFFFF6060 : 0xFFFFD75E);
      String count = Component.translatable("journal.colonyjournal.market.count", v.offers().size()).getString();
      Ui.textRight(g, this.font(), count, x + w, head + 5, 0xFFB8B8B8);
      List<OfferView> offers = v.offers();
      this.scroller.setSize(4 + offers.size() * (ROW_H + GAP), this.listH());
      g.enableScissor(this.cx(), this.listTop(), this.cx() + this.cw(), this.listTop() + this.listH());
      if (offers.isEmpty()) {
         int ey = this.listTop() + 8;

         for (net.minecraft.util.FormattedCharSequence line : this.font().split(Component.translatable("journal.colonyjournal.market.none"), Math.max(20, w - 8))) {
            g.drawString(this.font(), line, x + 4, ey, 0xFF909090, true);
            ey += 10;
         }
      }

      int listW = this.listW();
      int btnX = this.buttonX();

      for (int i = 0; i < offers.size(); i++) {
         int y = this.rowY(i);
         if (y + ROW_H < this.listTop() || y > this.listTop() + this.listH()) {
            continue;
         }

         OfferView offer = offers.get(i);
         ItemStack stack = offer.stack();
         boolean hoverRow = this.inList(mx, my) && Ui.in(mx, my, x, y, btnX - x - 2, ROW_H);
         g.fill(x, y, x + listW, y + ROW_H, hoverRow ? 0x88404040 : 0x77000000);
         g.renderOutline(x, y, listW, ROW_H, 0xFF000000);
         g.blitSprite(Ui.SLOT, x + 3, y + 3, 18, 18);
         g.renderItem(stack, x + 4, y + 4);
         g.renderItemDecorations(this.font(), stack, x + 4, y + 4);
         String price = Ui.coins(offer.price());
         int coinX = btnX - 6 - 16;
         int priceRight = coinX - 2;
         int nameW = priceRight - this.font().width(price) - (x + 26) - 6;
         Ui.text(g, this.font(), this.font().plainSubstrByWidth(stack.getHoverName().getString(), Math.max(20, nameW)), x + 26, y + 4, 0xFFFFFFFF);
         String seller = Component.translatable("journal.colonyjournal.market.seller", offer.sellerName(), offer.colonyName()).getString();
         Ui.text(g, this.font(), this.font().plainSubstrByWidth(seller, Math.max(20, nameW)), x + 26, y + 14, offer.own() ? 0xFFE0C040 : 0xFF9A9A9A);
         Ui.coin(g, coinX, y + 4);
         Ui.textRight(g, this.font(), price, priceRight, y + 8, 0xFFFFD75E);
         boolean canBuy = !offer.own() && v.balance() >= offer.price();
         boolean enabled = offer.own() || canBuy;
         boolean hoverBtn = this.inList(mx, my) && Ui.in(mx, my, btnX, y + 4, BUTTON_W, 16);
         Ui.button(g, this.font(), btnX, y + 4, BUTTON_W, 16, Component.translatable(offer.own() ? "journal.colonyjournal.market.withdraw" : "journal.colonyjournal.market.buy"), enabled, hoverBtn);
         if (hoverRow) {
            this.tipStack = stack;
         } else if (hoverBtn) {
            if (offer.own()) {
               this.tip = Component.translatable("journal.colonyjournal.market.withdraw_tip");
            } else if (!canBuy) {
               this.tip = Component.translatable("msg.colonyjournal.no_funds", Ui.coins(offer.price() - v.balance()));
            } else {
               this.tip = Component.translatable("journal.colonyjournal.market.buy_tip", Ui.coins(offer.price()));
            }
         }
      }

      g.disableScissor();
      this.scroller.renderBar(g, this.cx() + this.cw() - 5, this.listTop(), this.listH());
   }

   private void renderSell(GuiGraphics g, int mx, int my) {
      int x = this.left();
      int w = this.width();
      int top = this.sellTop();
      g.fill(this.cx(), top - 2, this.cx() + this.cw(), this.cy() + this.ch(), 0x88000000);
      g.fill(this.cx(), top - 2, this.cx() + this.cw(), top - 1, 0xFF000000);
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.market.sell_title"), x, top + 3, 0xFFE0D0A0);
      ItemStack held = held();
      int row = this.sellRowY();
      g.blitSprite(Ui.SLOT, x, row - 1, 18, 18);
      int nameX = x + 22;
      int nameW = this.boxX() - 4 - nameX;
      if (held.isEmpty()) {
         Ui.text(g, this.font(), this.font().plainSubstrByWidth(Component.translatable("journal.colonyjournal.market.sell_none").getString(), nameW), nameX, row + 4, 0xFF909090);
      } else {
         g.renderItem(held, x + 1, row);
         g.renderItemDecorations(this.font(), held, x + 1, row);
         Ui.text(g, this.font(), this.font().plainSubstrByWidth(held.getHoverName().getString(), nameW), nameX, row + 4, 0xFFFFFFFF);
         if (Ui.in(mx, my, x, row - 1, 18, 18)) {
            this.tipStack = held;
         }
      }

      boolean canPost = !held.isEmpty() && this.price() > 0;
      boolean hover = this.overPost(mx, my);
      Ui.button(g, this.font(), this.postX(), row, BUTTON_W, 16, Component.translatable("journal.colonyjournal.market.post"), canPost, hover);
      if (hover && !canPost) {
         this.tip = Component.translatable(held.isEmpty() ? "msg.colonyjournal.no_item" : "msg.colonyjournal.bad_price");
      }

      this.screen.renderMessage(g, x, top + 38, w);
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      if (this.showGoods) {
         if (this.subAt(mx, my) >= 0 && this.tip != null) {
            Ui.tooltip(g, this.font(), this.tip, mx, my);
         } else {
            this.goods.renderTooltips(g, mx, my);
         }

         return;
      }

      if (!this.tipStack.isEmpty()) {
         g.renderTooltip(this.font(), this.tipStack, mx, my);
      } else if (this.tip != null) {
         Ui.tooltip(g, this.font(), this.tip, mx, my);
      }
   }

   // ------------------------------------------------------------------ input

   @Override
   boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return false;
      }

      int sub = this.subAt(mx, my);
      if (sub >= 0) {
         Ui.click();
         this.setMode(sub == 0);
         return true;
      }

      if (this.showGoods) {
         return this.goods.mouseClicked(mx, my, this.view());
      }

      if (this.overPost(mx, my)) {
         int price = this.price();
         if (!held().isEmpty() && price > 0) {
            Ui.click();
            Ui.send(new Payloads.PostOffer(this.screen.ref(), price));
            this.refreshIn = 10;
         }

         return true;
      }

      EconomyView v = this.view();
      if (v == null || !this.inList(mx, my)) {
         return false;
      }

      List<OfferView> offers = v.offers();

      for (int i = 0; i < offers.size(); i++) {
         int y = this.rowY(i);
         if (Ui.in(mx, my, this.buttonX(), y + 4, BUTTON_W, 16)) {
            OfferView offer = offers.get(i);
            if (offer.own()) {
               Ui.click();
               Ui.send(new Payloads.CancelOffer(offer.id()));
               this.refreshIn = 10;
            } else if (v.balance() >= offer.price()) {
               Ui.click();
               Ui.send(new Payloads.BuyOffer(offer.id(), this.screen.ref()));
               this.refreshIn = 10;
            }

            return true;
         }
      }

      return false;
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (this.showGoods) {
         return this.goods.mouseScrolled(sy);
      }

      this.scroller.wheel(sy);
      return true;
   }
}
