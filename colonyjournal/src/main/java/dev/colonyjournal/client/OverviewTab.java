package dev.colonyjournal.client;

import dev.colonyjournal.analysis.ColonyReport;
import dev.colonyjournal.client.OverviewModel.Card;
import dev.colonyjournal.client.OverviewModel.Facts;
import dev.colonyjournal.client.OverviewModel.Light;
import dev.colonyjournal.client.OverviewModel.Todo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The first page: colony name, four traffic-light cards and a short list of what to do next. All rectangles come from
 * {@link #layout()}, which drawing and hit-testing both use.
 */
final class OverviewTab extends JournalTab {
   private static final ItemStack ICON = new ItemStack(Items.COMPASS);
   private static final int PAD = 6;
   private static final int GAP = 4;
   private static final int KIND_CARD = 0;
   private static final int KIND_TODO = 1;
   private static final int KIND_GUIDE = 2;
   private static final int GUIDE_H = 16;
   private static final int LAMP = 5;

   /** A clickable rectangle. {@code y} is relative to the top of the scrolled content. */
   private record Box(int kind, int index, int x, int y, int w, int h, List<FormattedCharSequence> lines) {
   }

   private record Layout(List<Box> boxes, List<FormattedCharSequence> legend, int todoTitleY) {
   }

   private final Scroller scroller = new Scroller();
   private List<Card> cards = List.of();
   private List<Todo> todos = List.of();
   private Box hover;

   OverviewTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "overview";
   }

   @Override
   void onOpen() {
      EconomyTab.request(this.screen);
   }

   @Override
   int refreshInterval() {
      return 100;
   }

   @Override
   void requestRefresh() {
      EconomyTab.request(this.screen);
   }

   // ------------------------------------------------------------------ geometry shared by drawing and hit-testing

   private int left() {
      return this.cx() + PAD;
   }

   private int width() {
      return Math.max(40, this.cw() - PAD - 8);
   }

   private int top() {
      return this.cy() + 4 - this.scroller.offset;
   }

   private boolean inView(double mx, double my) {
      return Ui.in(mx, my, this.cx(), this.cy(), this.cw(), this.ch());
   }

   private void refreshData() {
      ColonyReport r = this.screen.report();
      Facts facts = new Facts(r.issues, r.rows, r.citizens, r.citizenLimit, this.screen.happiness(), this.screen.economy());
      this.cards = OverviewModel.cards(facts);
      this.todos = OverviewModel.todos(facts);
   }

   private static Component cardText(Card c) {
      return Component.translatable(c.key(), c.args());
   }

   private static Component todoText(Todo t) {
      return Component.translatable(t.key(), t.args());
   }

   /** Computes every rectangle for the current data and width, and the scroll range. Offsets are relative to the content top. */
   private Layout layout() {
      this.refreshData();
      int w = this.width();
      List<Box> boxes = new ArrayList<>();
      List<FormattedCharSequence> legend = this.font().split(Component.translatable("journal.colonyjournal.ov.legend"), w);
      int y = 22 + legend.size() * 10 + 6;

      int cols = w >= 290 ? 2 : 1;
      int cardW = (w - GAP * (cols - 1)) / cols;
      int textW = Math.max(20, cardW - 12);
      int lineMax = 1;
      List<List<FormattedCharSequence>> cardLines = new ArrayList<>();

      for (Card c : this.cards) {
         List<FormattedCharSequence> lines = this.font().split(cardText(c), textW);
         cardLines.add(lines);
         lineMax = Math.max(lineMax, lines.size());
      }

      int cardH = 17 + lineMax * 10 + 5;

      for (int i = 0; i < this.cards.size(); i++) {
         int row = i / cols;
         int col = i % cols;
         boxes.add(new Box(KIND_CARD, i, col * (cardW + GAP), y + row * (cardH + GAP), cardW, cardH, cardLines.get(i)));
      }

      int cardRows = (this.cards.size() + cols - 1) / cols;
      y += cardRows * (cardH + GAP) + 4;
      int todoTitleY = y;
      y += 14;
      int todoTextW = Math.max(20, w - 16 - 28);

      for (int i = 0; i < this.todos.size(); i++) {
         List<FormattedCharSequence> lines = this.font().split(todoText(this.todos.get(i)), todoTextW);
         int h = Math.max(22, lines.size() * 10 + 8);
         boxes.add(new Box(KIND_TODO, i, 0, y, w, h, lines));
         y += h + 3;
      }

      y += 4;
      boxes.add(new Box(KIND_GUIDE, 0, 0, y, Math.min(w, 150), GUIDE_H, List.of()));
      y += GUIDE_H + 6;
      this.scroller.setSize(y + 4, this.ch());
      return new Layout(boxes, legend, todoTitleY);
   }

   private int absX(Box b) {
      return this.left() + b.x();
   }

   private int absY(Box b) {
      return this.top() + b.y();
   }

   private Box boxAt(Layout layout, double mx, double my) {
      if (!this.inView(mx, my)) {
         return null;
      }

      for (Box b : layout.boxes()) {
         if (Ui.in(mx, my, this.absX(b), this.absY(b), b.w(), b.h())) {
            return b;
         }
      }

      return null;
   }

   // ------------------------------------------------------------------ drawing

   private static void lamps(GuiGraphics g, int x, int y, Light light) {
      int[] on = {0xFFE05050, 0xFFE0C040, 0xFF55CC66};
      int[] off = {0xFF3A1A1A, 0xFF3A361A, 0xFF1A3A20};
      Light[] order = {Light.RED, Light.YELLOW, Light.GREEN};

      for (int i = 0; i < 3; i++) {
         int lx = x + i * (LAMP + 2);
         g.fill(lx - 1, y - 1, lx + LAMP + 1, y + LAMP + 1, 0xFF000000);
         g.fill(lx, y, lx + LAMP, y + LAMP, light == order[i] ? on[i] : off[i]);
      }
   }

   @Override
   void render(GuiGraphics g, int mx, int my) {
      Layout layout = this.layout();
      this.hover = this.boxAt(layout, mx, my);
      int x = this.left();
      int w = this.width();
      int y = this.top();
      Component name = Component.literal(this.screen.report().colonyName);
      Component day = Component.translatable("journal.colonyjournal.ov.day", this.screen.day());
      int dayW = this.font().width(day);
      Ui.text(g, this.font(), this.font().plainSubstrByWidth(name.getString(), Math.max(20, w - dayW - 8)), x, y, 0xFFFFD75E);
      Ui.textRight(g, this.font(), day.getString(), x + w, y, 0xFFB8B8B8);
      ColonyReport r = this.screen.report();
      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.citizens", r.citizens, r.citizenLimit), x, y + 11, 0xFFFFFFFF);

      int ly = y + 22;
      for (FormattedCharSequence line : layout.legend()) {
         g.drawString(this.font(), line, x, ly, 0xFF9A9A9A, true);
         ly += 10;
      }

      for (Box b : layout.boxes()) {
         int bx = this.absX(b);
         int by = this.absY(b);
         if (by + b.h() < this.cy() || by > this.cy() + this.ch()) {
            continue;
         }

         switch (b.kind()) {
            case KIND_CARD -> this.renderCard(g, b, bx, by);
            case KIND_TODO -> this.renderTodo(g, b, bx, by);
            default -> Ui.button(g, this.font(), bx, by, b.w(), b.h(), Component.translatable("journal.colonyjournal.ov.guide_button"), true, b == this.hover);
         }
      }

      Ui.text(g, this.font(), Component.translatable("journal.colonyjournal.ov.todo_title"), x, y + layout.todoTitleY() + 1, 0xFFE0D0A0);
      this.scroller.renderBar(g, this.cx() + this.cw() - 5, this.cy(), this.ch());
   }

   private void renderCard(GuiGraphics g, Box b, int bx, int by) {
      Card c = this.cards.get(b.index());
      boolean hot = b == this.hover;
      g.fill(bx, by, bx + b.w(), by + b.h(), hot ? 0x88404040 : 0x77000000);
      g.renderOutline(bx, by, b.w(), b.h(), 0xFF000000);
      g.fill(bx, by, bx + 3, by + b.h(), c.light().color);
      lamps(g, bx + 8, by + 5, c.light());
      int titleX = bx + 8 + 3 * (LAMP + 2) + 3;
      Component title = Component.translatable("journal.colonyjournal.ov.card." + c.id());
      Component details = Component.translatable("journal.colonyjournal.ov.details");
      int detailsW = this.font().width(details);
      boolean room = b.w() - (titleX - bx) - detailsW - 10 >= this.font().width(title);
      int titleW = b.w() - (titleX - bx) - 5 - (room ? detailsW + 5 : 0);
      Ui.text(g, this.font(), this.font().plainSubstrByWidth(title.getString(), Math.max(10, titleW)), titleX, by + 4, 0xFFFFFFFF);
      if (room) {
         Ui.textRight(g, this.font(), details.getString(), bx + b.w() - 5, by + 4, hot ? 0xFFFFFFFF : 0xFFB8B8B8);
      }

      int ty = by + 17;
      for (FormattedCharSequence line : b.lines()) {
         g.drawString(this.font(), line, bx + 6, ty, 0xFFD8D8D8, true);
         ty += 10;
      }
   }

   private void renderTodo(GuiGraphics g, Box b, int bx, int by) {
      Todo t = this.todos.get(b.index());
      boolean hot = b == this.hover;
      g.fill(bx, by, bx + b.w(), by + b.h(), hot ? 0x88404040 : 0x77000000);
      g.renderOutline(bx, by, b.w(), b.h(), 0xFF000000);
      g.fill(bx, by, bx + 3, by + b.h(), t.light().color);
      Ui.text(g, this.font(), String.valueOf(b.index() + 1), bx + 7, by + (b.h() - 8) / 2, 0xFF9A9A9A);
      int ty = by + (b.h() - b.lines().size() * 10) / 2 + 1;

      for (FormattedCharSequence line : b.lines()) {
         g.drawString(this.font(), line, bx + 17, ty, 0xFFFFFFFF, true);
         ty += 10;
      }

      if ("map".equals(t.tab()) && t.focus() != null) {
         g.renderItem(BuildingIcons.of(t.focus()), bx + b.w() - 22, by + (b.h() - 16) / 2);
      } else {
         Ui.text(g, this.font(), ">", bx + b.w() - 12, by + (b.h() - 8) / 2, 0xFFB8B8B8);
      }
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      Box b = this.hover;
      if (b == null) {
         return;
      }

      Component tip;
      if (b.kind() == KIND_CARD) {
         Card c = this.cards.get(b.index());
         tip = Component.translatable("journal.colonyjournal.ov.card_tip", Component.translatable("journal.colonyjournal.tab." + c.tab()));
      } else if (b.kind() == KIND_TODO) {
         tip = Component.translatable("map".equals(this.todos.get(b.index()).tab()) && this.todos.get(b.index()).focus() != null ? "journal.colonyjournal.click_to_map" : "journal.colonyjournal.ov.todo_tip");
      } else {
         tip = Component.translatable("journal.colonyjournal.ov.guide_tip");
      }

      Ui.tooltip(g, this.font(), tip, mx, my);
   }

   // ------------------------------------------------------------------ input

   @Override
   boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return false;
      }

      Box b = this.boxAt(this.layout(), mx, my);
      if (b == null) {
         return false;
      }

      Ui.click();
      if (b.kind() == KIND_CARD) {
         this.screen.openTab(this.cards.get(b.index()).tab());
      } else if (b.kind() == KIND_TODO) {
         Todo t = this.todos.get(b.index());
         this.screen.jump(t.tab(), t.focus(), t.partner());
      } else {
         this.screen.openGuide("intro");
      }

      return true;
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      this.scroller.wheel(sy);
      return true;
   }
}
