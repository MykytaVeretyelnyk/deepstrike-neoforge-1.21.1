package dev.colonyjournal.client;

import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.client.GuideText.Block;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The tutorial: a list of sections on the left (a previous/next bar when the window is narrow) and the text of the chosen
 * section on the right, word-wrapped, with headings, bullets and small icons.
 */
final class GuideTab extends JournalTab {
   private static final ItemStack ICON = new ItemStack(Items.WRITTEN_BOOK);
   private static final int ROW_H = 14;
   private static final int NAV_H = 17;
   private static final int NAV_BTN_W = 18;
   private static final int ICON_COL = 22;
   private static final int DOT_COL = 10;
   private static final Map<String, ItemStack> SECTION_ICONS = new HashMap<>();

   static {
      SECTION_ICONS.put("intro", new ItemStack(Items.WRITTEN_BOOK));
      SECTION_ICONS.put("steps", new ItemStack(Items.OAK_SIGN));
      SECTION_ICONS.put("overview", new ItemStack(Items.COMPASS));
      SECTION_ICONS.put("links", new ItemStack(Items.CHAIN));
      SECTION_ICONS.put("problems", new ItemStack(Items.REDSTONE_TORCH));
      SECTION_ICONS.put("buildings", new ItemStack(Items.BRICKS));
      SECTION_ICONS.put("economy", new ItemStack(Items.GOLD_INGOT));
      SECTION_ICONS.put("happiness", new ItemStack(Items.GOLDEN_APPLE));
      SECTION_ICONS.put("market", new ItemStack(Items.EMERALD));
      SECTION_ICONS.put("stats", new ItemStack(Items.CLOCK));
      SECTION_ICONS.put("glossary", new ItemStack(Items.BOOK));
      SECTION_ICONS.put("controls", new ItemStack(Items.LEVER));
   }

   private static final int KIND_RULE = 0;
   private static final int KIND_TEXT = 1;
   private static final int KIND_ICON = 2;
   private static final int KIND_DOT = 3;

   /** One drawn element of the section text. {@code y} is relative to the top of the scrolled text. */
   private record Piece(int kind, int x, int y, FormattedCharSequence text, String icon, int color) {
   }

   private final Scroller text = new Scroller();
   private final Scroller list = new Scroller();
   private final Map<String, ItemStack> stacks = new HashMap<>();
   private List<Piece> items = List.of();
   private String builtKey = "";
   private int section;
   private int hoverRow = -1;
   private int hoverNav = -1;

   GuideTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "guide";
   }

   @Override
   boolean showHelp() {
      return false;
   }

   /** Shows the section with the given id (an unknown id shows the first one). */
   void select(String id) {
      int index = GuideText.IDS.indexOf(id);
      this.section = Math.max(0, index);
      this.text.reset();
   }

   private String id() {
      return GuideText.IDS.get(this.section);
   }

   private void step(int delta) {
      int n = GuideText.IDS.size();
      this.section = Math.floorMod(this.section + delta, n);
      this.text.reset();
   }

   // ------------------------------------------------------------------ geometry shared by drawing and hit-testing

   private boolean narrow() {
      return this.cw() < 250;
   }

   private int navH() {
      return this.narrow() ? NAV_H : 0;
   }

   private int listW() {
      return this.narrow() ? 0 : Ui.clamp(this.cw() / 3, 70, 112);
   }

   private int listX() {
      return this.cx() + 3;
   }

   private int listY() {
      return this.cy() + 3;
   }

   private int listH() {
      return Math.max(0, this.ch() - 6);
   }

   private int rowY(int index) {
      return this.listY() + 2 - this.list.offset + index * ROW_H;
   }

   private boolean inList(double mx, double my) {
      return !this.narrow() && Ui.in(mx, my, this.listX(), this.listY(), this.listW(), this.listH());
   }

   private int rowAt(double mx, double my) {
      if (!this.inList(mx, my)) {
         return -1;
      }

      int rel = (int)my - (this.listY() + 2 - this.list.offset);
      int index = rel / ROW_H;
      return rel >= 0 && index < GuideText.IDS.size() ? index : -1;
   }

   private int paneX() {
      return this.narrow() ? this.cx() + 3 : this.listX() + this.listW() + 3;
   }

   private int paneY() {
      return this.cy() + 3 + this.navH();
   }

   private int paneW() {
      return this.cx() + this.cw() - 3 - this.paneX();
   }

   private int paneH() {
      return this.cy() + this.ch() - 3 - this.paneY();
   }

   private int textX() {
      return this.paneX() + 5;
   }

   private int textY() {
      return this.paneY() + 4;
   }

   private int textW() {
      return Math.max(30, this.paneW() - 5 - 9);
   }

   private int textH() {
      return Math.max(0, this.paneH() - 8);
   }

   private int navX(int which) {
      return which == 0 ? this.cx() + 3 : this.cx() + this.cw() - 3 - NAV_BTN_W;
   }

   private int navY() {
      return this.cy() + 2;
   }

   /** 0 previous, 1 next, -1 none; only in the narrow layout. */
   private int navAt(double mx, double my) {
      if (!this.narrow()) {
         return -1;
      }

      for (int i = 0; i < 2; i++) {
         if (Ui.in(mx, my, this.navX(i), this.navY(), NAV_BTN_W, 14)) {
            return i;
         }
      }

      return -1;
   }

   // ------------------------------------------------------------------ text layout

   private ItemStack stackOf(String id) {
      return this.stacks.computeIfAbsent(id, key -> {
         ResourceLocation rl = ResourceLocation.tryParse(key.contains(":") ? key : "minecraft:" + key);
         Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
         return new ItemStack(item == null ? Items.BARRIER : item);
      });
   }

   private static String lang(String id, String part) {
      return Component.translatable("journal.colonyjournal.guide." + id + "." + part).getString();
   }

   private void ensureText() {
      String id = this.id();
      int w = this.textW();
      String lang = Minecraft.getInstance().getLanguageManager().getSelected();
      String key = id + "|" + w + "|" + lang;
      if (!key.equals(this.builtKey)) {
         this.builtKey = key;
         this.items = this.build(id, w);
      }

      this.text.setSize(this.items.isEmpty() ? 0 : this.items.get(this.items.size() - 1).y() + 4, this.textH());
   }

   /** Lays the section out in lines. The last element's y plus its height is the content height (see ensureText). */
   private List<Piece> build(String id, int w) {
      List<Piece> out = new ArrayList<>();
      int y = 0;
      out.add(new Piece(KIND_ICON, 0, y, null, "section:" + id, 0));
      out.add(new Piece(KIND_TEXT, 22, y + 4, Component.literal(lang(id, "title")).getVisualOrderText(), null, 0xFFFFD75E));
      out.add(new Piece(KIND_RULE, 0, y + 19, null, null, 0x66FFFFFF));
      y += 25;

      for (Block b : GuideText.parse(lang(id, "body"))) {
         switch (b.kind()) {
            case HEADING -> {
               y += 4;
               for (FormattedCharSequence line : this.font().split(Component.literal(b.text()), w)) {
                  out.add(new Piece(KIND_TEXT, 0, y, line, null, 0xFFE0D0A0));
                  y += 10;
               }

               y += 3;
            }
            case PARA -> {
               for (FormattedCharSequence line : this.font().split(Component.literal(b.text()), w)) {
                  out.add(new Piece(KIND_TEXT, 0, y, line, null, 0xFFE8E8E8));
                  y += 10;
               }

               y += 5;
            }
            case BULLET -> {
               int indent = b.icon() != null ? ICON_COL : DOT_COL;
               List<FormattedCharSequence> lines = this.font().split(Component.literal(b.text()), Math.max(20, w - indent));
               int h = Math.max(lines.size() * 10, b.icon() != null ? 18 : 0);
               if (b.icon() != null) {
                  out.add(new Piece(KIND_ICON, 0, y + (h - 16) / 2, null, b.icon(), 0));
               } else {
                  out.add(new Piece(KIND_DOT, 2, y + 1, null, null, 0xFFE0D0A0));
               }

               int ty = y + (h - lines.size() * 10) / 2;
               for (FormattedCharSequence line : lines) {
                  out.add(new Piece(KIND_TEXT, indent, ty, line, null, 0xFFFFFFFF));
                  ty += 10;
               }

               y += h + 3;
            }
         }
      }

      // a sentinel carrying the end of the content, so the scroll range includes the last paragraph
      out.add(new Piece(KIND_RULE, 0, y, null, null, 0));
      return out;
   }

   // ------------------------------------------------------------------ drawing

   @Override
   void render(GuiGraphics g, int mx, int my) {
      this.ensureText();
      if (this.narrow()) {
         this.list.reset();
      } else {
         this.list.setSize(GuideText.IDS.size() * ROW_H + 4, this.listH());
      }

      this.hoverRow = this.rowAt(mx, my);
      this.hoverNav = this.navAt(mx, my);
      if (this.narrow()) {
         this.renderNav(g);
      } else {
         this.renderList(g);
      }

      this.renderPane(g);
   }

   private void renderNav(GuiGraphics g) {
      int y = this.navY();
      Ui.button(g, this.font(), this.navX(0), y, NAV_BTN_W, 14, Component.literal("<"), true, this.hoverNav == 0);
      Ui.button(g, this.font(), this.navX(1), y, NAV_BTN_W, 14, Component.literal(">"), true, this.hoverNav == 1);
      int from = this.navX(0) + NAV_BTN_W + 4;
      int to = this.navX(1) - 4;
      String label = lang(this.id(), "title") + "  " + (this.section + 1) + "/" + GuideText.IDS.size();
      String shown = this.font().plainSubstrByWidth(label, Math.max(10, to - from));
      Ui.text(g, this.font(), shown, from + Math.max(0, (to - from - this.font().width(shown)) / 2), y + 3, 0xFFFFD75E);
   }

   private void renderList(GuiGraphics g) {
      int x = this.listX();
      int y = this.listY();
      int w = this.listW();
      int h = this.listH();
      g.fill(x, y, x + w, y + h, 0x66000000);
      g.renderOutline(x, y, w, h, 0xFF000000);
      boolean bar = this.list.max() > 0;
      int labelW = w - 10 - (bar ? 5 : 0);
      g.enableScissor(x, y, x + w, y + h);

      for (int i = 0; i < GuideText.IDS.size(); i++) {
         int ry = this.rowY(i);
         if (ry + ROW_H < y || ry > y + h) {
            continue;
         }

         boolean selected = i == this.section;
         if (selected) {
            g.fill(x + 1, ry, x + w - 1, ry + ROW_H - 1, 0x88B08A20);
            g.fill(x + 1, ry, x + 3, ry + ROW_H - 1, 0xFFFFD75E);
         } else if (i == this.hoverRow) {
            g.fill(x + 1, ry, x + w - 1, ry + ROW_H - 1, 0x55FFFFFF);
         }

         String title = this.font().plainSubstrByWidth(lang(GuideText.IDS.get(i), "title"), labelW);
         Ui.text(g, this.font(), title, x + 7, ry + 3, selected ? 0xFFFFFFFF : 0xFFD0D0D0);
      }

      g.disableScissor();
      this.list.renderBar(g, x + w - 5, y + 1, h - 2);
   }

   private void renderPane(GuiGraphics g) {
      int px = this.paneX();
      int py = this.paneY();
      int pw = this.paneW();
      int ph = this.paneH();
      g.fill(px, py, px + pw, py + ph, 0x55000000);
      g.renderOutline(px, py, pw, ph, 0xFF000000);
      int tx = this.textX();
      int ty = this.textY();
      int tw = this.textW();
      int th = this.textH();
      g.enableScissor(px, ty, px + pw, ty + th);

      for (Piece item : this.items) {
         int y = ty - this.text.offset + item.y();
         if (y + 20 < ty || y > ty + th) {
            continue;
         }

         int x = tx + item.x();
         switch (item.kind()) {
            case KIND_TEXT -> g.drawString(this.font(), item.text(), x, y, item.color(), true);
            case KIND_DOT -> Ui.text(g, this.font(), "•", x, y, item.color());
            case KIND_ICON -> this.drawIcon(g, item.icon(), x, y);
            default -> {
               if ((item.color() >>> 24) != 0) {
                  g.fill(tx, y, tx + tw, y + 1, item.color());
               }
            }
         }
      }

      g.disableScissor();
      this.text.renderBar(g, px + pw - 6, ty, th);
   }

   private void drawIcon(GuiGraphics g, String icon, int x, int y) {
      if (icon.startsWith("section:")) {
         g.renderItem(SECTION_ICONS.getOrDefault(icon.substring(8), ICON), x, y);
      } else if (icon.startsWith("frame:")) {
         Status status;
         try {
            status = Status.valueOf(icon.substring(6).toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException e) {
            status = Status.ABSENT; // a broken token in a translation must never crash the renderer
         }

         LinksTab.sample(g, this.font(), status, x, y - 1, 18, true);
      } else if (icon.startsWith("color:")) {
         int c = 0xFF000000 | parseColor(icon.substring(6));
         g.fill(x + 2, y + 2, x + 14, y + 14, 0xFF000000);
         g.fill(x + 3, y + 3, x + 13, y + 13, c);
      } else if (icon.startsWith("line:")) {
         int c = 0xFF000000 | parseColor(icon.substring(5));
         g.fill(x, y + 6, x + 18, y + 11, 0xE0000000);
         g.fill(x + 1, y + 7, x + 17, y + 10, c);
      } else if (icon.equals("coin")) {
         Ui.coin(g, x, y);
      } else {
         g.renderItem(this.stackOf(icon), x, y);
      }
   }

   static int parseColor(String hex) {
      try {
         return Integer.parseInt(hex, 16) & 0xFFFFFF;
      } catch (NumberFormatException e) {
         return 0x909090;
      }
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      if (this.hoverNav >= 0) {
         int target = Math.floorMod(this.section + (this.hoverNav == 0 ? -1 : 1), GuideText.IDS.size());
         g.renderTooltip(this.font(), Component.literal(lang(GuideText.IDS.get(target), "title")), mx, my);
      }
   }

   // ------------------------------------------------------------------ input

   @Override
   boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return false;
      }

      int nav = this.navAt(mx, my);
      if (nav >= 0) {
         Ui.click();
         this.step(nav == 0 ? -1 : 1);
         return true;
      }

      int row = this.rowAt(mx, my);
      if (row >= 0) {
         Ui.click();
         this.section = row;
         this.text.reset();
         return true;
      }

      return false;
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (this.inList(mx, my)) {
         this.list.wheel(sy);
      } else {
         this.text.wheel(sy);
      }

      return true;
   }
}
