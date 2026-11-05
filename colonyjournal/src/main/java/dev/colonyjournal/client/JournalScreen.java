package dev.colonyjournal.client;

import com.minecolonies.api.colony.IColonyView;
import dev.colonyjournal.analysis.ColonyReport;
import dev.colonyjournal.graph.BuildingGraph;
import dev.colonyjournal.net.Payloads;
import dev.colonyjournal.net.Views.ColonyRef;
import dev.colonyjournal.net.Views.EconomyView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The colony journal, drawn like the vanilla advancements book: a framed window, tabs along the top and a stone
 * background. The screen keeps the window, the tab switching, the one-line subtitle with the help button, the refresh
 * timers and the message line; everything else lives in the {@link JournalTab} classes.
 */
public final class JournalScreen extends Screen {
   private static final int TAB_W = 28;
   private static final int TAB_H = 32;
   /** Height of the subtitle band at the top of the content. */
   private static final int SUB_H = 12;
   private static final int HELP_W = 12;
   private static final int MESSAGE_TICKS = 100;
   private static final String[] TAB_SPRITES = {"tab_above_left", "tab_above_middle", "tab_above_right"};
   private static final ResourceLocation[] TAB_NORMAL = new ResourceLocation[3];
   private static final ResourceLocation[] TAB_SELECTED = new ResourceLocation[3];

   static {
      for (int i = 0; i < 3; i++) {
         TAB_NORMAL[i] = Ui.sprite("advancements/" + TAB_SPRITES[i]);
         TAB_SELECTED[i] = Ui.sprite("advancements/" + TAB_SPRITES[i] + "_selected");
      }
   }

   private final IColonyView colony;
   private final ColonyRef ref;
   private final List<JournalTab> tabs;
   private final LinksTab linksTab;
   private final GuideTab guideTab;
   private ColonyReport report;
   private int tabIndex;
   private int winX;
   private int winY;
   private int winW;
   private int winH;
   private int reportTimer;
   private int refreshTimer;
   private int seenSerial;
   private int messageTicks;
   private Component message = Component.empty();
   private boolean messageOk = true;
   private boolean opened;

   public JournalScreen(IColonyView colony) {
      super(Component.translatable("journal.colonyjournal.title"));
      this.colony = colony;
      this.ref = new ColonyRef(colony.getDimension().location().toString(), colony.getID());
      this.report = ColonyReport.build(colony);
      this.seenSerial = ClientData.resultSerial();
      this.linksTab = new LinksTab(this);
      this.guideTab = new GuideTab(this);
      this.tabs = List.of(
         new OverviewTab(this), this.linksTab, new ProblemsTab(this), new BuildingsTab(this), new EconomyTab(this), new MarketTab(this), new StatsTab(this), this.guideTab
      );
      if (FirstRun.consume()) {
         this.guideTab.select("steps");
         this.tabIndex = this.indexOf("guide");
      }
   }

   // ------------------------------------------------------------------ accessors for the tabs

   ColonyReport report() {
      return this.report;
   }

   ColonyRef ref() {
      return this.ref;
   }

   Font font() {
      return this.font;
   }

   /** The economy data of this colony, or null while it is loading or belongs to another colony. */
   EconomyView economy() {
      EconomyView v = ClientData.economy;
      return v != null && v.colony().equals(this.ref) ? v : null;
   }

   /** Overall happiness of the citizens, 0 to 10. */
   double happiness() {
      return this.colony.getOverallHappiness();
   }

   /** The in-game day of the colony. */
   int day() {
      try {
         return this.colony.getDay();
      } catch (RuntimeException e) {
         Minecraft mc = Minecraft.getInstance();
         return mc.level == null ? 0 : (int)(mc.level.getDayTime() / 24000L);
      }
   }

   int contentX() {
      return this.winX + 9;
   }

   int contentY() {
      return this.winY + 18 + SUB_H;
   }

   int contentW() {
      return this.winW - 18;
   }

   int contentH() {
      return this.winH - 27 - SUB_H;
   }

   void addTabWidget(AbstractWidget widget) {
      this.addRenderableWidget(widget);
   }

   private JournalTab tab() {
      return this.tabs.get(this.tabIndex);
   }

   private int indexOf(String key) {
      for (int i = 0; i < this.tabs.size(); i++) {
         if (this.tabs.get(i).key().equals(key)) {
            return i;
         }
      }

      return 0;
   }

   /** Opens the tab with the given key ("overview", "map", "problems", "buildings", "economy", "market", "stats", "guide"). */
   void openTab(String key) {
      this.selectTab(this.indexOf(key));
   }

   /** Switches to the Links tab and centres the map on the building type. */
   void showOnMap(String buildingId, String partner) {
      this.linksTab.focus(buildingId, partner);
      this.openTab("map");
   }

   /** Shows the building on the map when it is part of the map, otherwise opens the given tab. */
   void jump(String tabKey, String focus, String partner) {
      // Only a to-do that asks for the map goes there: the housing to-do names a basic building but wants the Buildings tab.
      if ("map".equals(tabKey) && BuildingGraph.onMap(focus)) {
         this.showOnMap(focus, partner);
      } else {
         this.openTab(tabKey);
      }
   }

   /** Opens the guide at the section (see {@link GuideText#IDS}). */
   void openGuide(String section) {
      this.guideTab.select(section);
      this.openTab("guide");
   }

   private void selectTab(int index) {
      if (index == this.tabIndex) {
         return;
      }

      this.tabIndex = index;
      this.refreshTimer = 0;
      this.rebuildWidgets();
      this.tab().onOpen();
   }

   // ------------------------------------------------------------------ message line

   /** The text of the last action result while it is fresh, or null. */
   Component message() {
      return this.messageTicks > 0 ? this.message : null;
   }

   boolean messageOk() {
      return this.messageOk;
   }

   /** Draws the message (if any) on one line inside the given width. */
   void renderMessage(GuiGraphics g, int x, int y, int width) {
      Component text = this.message();
      if (text != null) {
         String line = this.font.plainSubstrByWidth(text.getString(), width);
         g.drawString(this.font, line, x, y, this.messageOk ? 0xFF7FE08A : 0xFFFF6060, true);
      }
   }

   // ------------------------------------------------------------------ layout

   @Override
   protected void init() {
      this.winW = Math.max(160, Math.min(this.width - 20, 420));
      this.winH = Math.max(100, Math.min(this.height - 20 - 24, 260));
      this.winX = (this.width - this.winW) / 2;
      this.winY = (this.height - (this.winH + TAB_H - 4)) / 2 + TAB_H - 4;
      this.tab().init();
      if (!this.opened) {
         this.opened = true;
         this.tab().onOpen();
      }
   }

   /** Width of one tab sprite: the full 28 pixels, squeezed when eight tabs do not fit the window. */
   private int tabW() {
      return Math.min(TAB_W, (this.winW - 4) / this.tabs.size());
   }

   private int tabPitch() {
      int w = this.tabW();
      return Ui.clamp((this.winW - w) / Math.max(1, this.tabs.size() - 1), w, TAB_W + 4);
   }

   private int tabX(int index) {
      return this.winX + this.tabPitch() * index;
   }

   private int tabY() {
      return this.winY - TAB_H + 4;
   }

   /** The tab under the mouse, or -1. The tabs never overlap, so drawing and hit-testing use the same rectangles. */
   private int tabAt(double mx, double my) {
      for (int i = 0; i < this.tabs.size(); i++) {
         if (Ui.in(mx, my, this.tabX(i), this.tabY(), this.tabW(), TAB_H - 4)) {
            return i;
         }
      }

      return -1;
   }

   private int subX() {
      return this.winX + 9;
   }

   private int subY() {
      return this.winY + 18;
   }

   private int subW() {
      return this.winW - 18;
   }

   private int helpX() {
      return this.subX() + this.subW() - HELP_W - 1;
   }

   private boolean overHelp(double mx, double my) {
      return this.tab().showHelp() && Ui.in(mx, my, this.helpX(), this.subY() + 1, HELP_W, SUB_H - 2);
   }

   /** The width the subtitle text may use. */
   private int subtitleW() {
      return this.subW() - 8 - (this.tab().showHelp() ? HELP_W + 2 : 0);
   }

   // ------------------------------------------------------------------ update

   @Override
   public void tick() {
      super.tick();
      if (++this.reportTimer >= 20) {
         this.reportTimer = 0;
         this.report = ColonyReport.build(this.colony);
         for (JournalTab t : this.tabs) {
            t.onReport();
         }
      }

      int serial = ClientData.resultSerial();
      if (serial != this.seenSerial) {
         this.seenSerial = serial;
         Payloads.ActionResult result = ClientData.lastResult;
         if (result != null) {
            this.message = Component.translatable("msg.colonyjournal." + result.key(), result.arg());
            this.messageOk = result.ok();
            this.messageTicks = MESSAGE_TICKS;
         }
      } else if (this.messageTicks > 0) {
         this.messageTicks--;
      }

      JournalTab current = this.tab();
      current.tick();
      int interval = current.refreshInterval();
      if (interval > 0 && ++this.refreshTimer >= interval) {
         this.refreshTimer = 0;
         current.requestRefresh();
      }
   }

   // ------------------------------------------------------------------ rendering

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
      // Called first by Screen.render, so everything that must lie below the widgets is drawn here.
      this.renderTransparentBackground(g);
      JournalTab current = this.tab();
      int x = this.contentX();
      int y = this.contentY();
      int w = this.contentW();
      int h = this.contentH();
      if (!current.ownBackground()) {
         Ui.stone(g, x, y, w, h, 0, 0);
         g.fill(x, y, x + w, y + h, 0xB0000000);
      }

      g.enableScissor(x, y, x + w, y + h);
      current.render(g, mx, my);
      g.disableScissor();
      this.renderSubtitle(g, mx, my);
      Ui.window(g, this.winX, this.winY, this.winW, this.winH);
      this.renderTabs(g);
      Component head = Component.translatable("journal.colonyjournal.window_title_tab", current.title(), this.report.colonyName);
      Component citizens = Component.translatable("journal.colonyjournal.citizens", this.report.citizens, this.report.citizenLimit);
      int citizensW = this.font.width(citizens);
      int free = this.winW - 16 - citizensW - 8;
      g.drawString(this.font, this.font.plainSubstrByWidth(head.getString(), Math.max(20, free)), this.winX + 8, this.winY + 6, 0xFF404040, false);
      if (free >= this.font.width(head) || free > 80) {
         g.drawString(this.font, citizens, this.winX + this.winW - 8 - citizensW, this.winY + 6, 0xFF404040, false);
      }
   }

   private void renderSubtitle(GuiGraphics g, int mx, int my) {
      JournalTab current = this.tab();
      int x = this.subX();
      int y = this.subY();
      int w = this.subW();
      Ui.stone(g, x, y, w, SUB_H, 0, 0);
      g.fill(x, y, x + w, y + SUB_H, 0xD0000000);
      g.fill(x, y + SUB_H - 1, x + w, y + SUB_H, 0xFF000000);
      String text = this.font.plainSubstrByWidth(current.subtitle().getString(), this.subtitleW());
      g.drawString(this.font, text, x + 4, y + 2, 0xFFC8C8C8, false);
      if (current.showHelp()) {
         Ui.button(g, this.font, this.helpX(), y + 1, HELP_W, SUB_H - 2, Component.literal("?"), true, this.overHelp(mx, my));
      }
   }

   private void renderTabs(GuiGraphics g) {
      int n = this.tabs.size();
      int y = this.tabY();
      int w = this.tabW();

      for (int i = 0; i < n; i++) {
         int kind = i == 0 ? 0 : (i == n - 1 ? 2 : 1);
         int x = this.tabX(i);
         g.blitSprite(i == this.tabIndex ? TAB_SELECTED[kind] : TAB_NORMAL[kind], x, y, w, TAB_H);
         g.renderItem(this.tabs.get(i).icon(), x + (w - 16) / 2, y + 9);
      }
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float partial) {
      super.render(g, mx, my, partial);
      JournalTab current = this.tab();
      if (!current.ownMessageLine()) {
         Component text = this.message();
         if (text != null) {
            int w = Math.min(this.contentW() - 8, this.font.width(text.getString()) + 12);
            int x = this.contentX() + (this.contentW() - w) / 2;
            int y = this.contentY() + this.contentH() - 16;
            g.fill(x, y, x + w, y + 13, 0xD0000000);
            g.renderOutline(x, y, w, 13, this.messageOk ? 0xFF55AA66 : 0xFFCC4444);
            this.renderMessage(g, x + 6, y + 3, w - 12);
         }
      }

      current.renderTooltips(g, mx, my);
      int hoverTab = this.tabAt(mx, my);
      if (hoverTab >= 0) {
         JournalTab t = this.tabs.get(hoverTab);
         List<Component> lines = new ArrayList<>();
         lines.add(t.title());
         lines.add(t.subtitle().copy().withStyle(ChatFormatting.GRAY));
         g.renderComponentTooltip(this.font, lines, mx, my);
      } else if (this.overHelp(mx, my)) {
         Ui.tooltip(g, this.font, Component.translatable("journal.colonyjournal.help_tip"), mx, my);
      } else if (Ui.in(mx, my, this.subX(), this.subY(), this.subW(), SUB_H)) {
         Component sub = current.subtitle();
         if (this.font.width(sub) > this.subtitleW()) {
            Ui.tooltip(g, this.font, sub, mx, my);
         }
      }
   }

   // ------------------------------------------------------------------ input

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == 0) {
         int hit = this.tabAt(mx, my);
         if (hit >= 0) {
            Ui.click();
            this.selectTab(hit);
            return true;
         }

         if (this.overHelp(mx, my)) {
            Ui.click();
            this.openGuide(this.tab().guideSection());
            return true;
         }
      }

      if (super.mouseClicked(mx, my, button)) {
         return true;
      }

      this.setFocused(null);
      return this.tab().mouseClicked(mx, my, button);
   }

   @Override
   public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      return this.tab().mouseDragged(mx, my, button, dx, dy) || super.mouseDragged(mx, my, button, dx, dy);
   }

   @Override
   public boolean mouseReleased(double mx, double my, int button) {
      boolean handled = this.tab().mouseReleased(mx, my, button);
      return super.mouseReleased(mx, my, button) || handled;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double sx, double sy) {
      return this.tab().mouseScrolled(mx, my, sx, sy) || super.mouseScrolled(mx, my, sx, sy);
   }

   @Override
   public boolean keyPressed(int key, int scan, int modifiers) {
      if (super.keyPressed(key, scan, modifiers)) {
         return true;
      }

      boolean typing = this.getFocused() instanceof EditBox box && box.isFocused();
      if (!typing && this.minecraft != null && this.minecraft.options.keyInventory.matches(key, scan)) {
         this.onClose();
         return true;
      }

      return false;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
