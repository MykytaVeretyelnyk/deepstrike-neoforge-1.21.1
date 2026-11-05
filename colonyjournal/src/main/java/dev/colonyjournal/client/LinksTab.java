package dev.colonyjournal.client;

import dev.colonyjournal.analysis.ColonyReport;
import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.analysis.ColonyReport.TypeInfo;
import dev.colonyjournal.graph.BuildingGraph;
import dev.colonyjournal.graph.BuildingGraph.Chain;
import dev.colonyjournal.graph.BuildingGraph.Edge;
import dev.colonyjournal.graph.ChainAdvice;
import dev.colonyjournal.graph.ChainAdvice.Advice;
import dev.colonyjournal.graph.ChainHealth;
import dev.colonyjournal.graph.ChainHealth.EdgeState;
import dev.colonyjournal.graph.ChainHealth.Light;
import dev.colonyjournal.graph.ChainHealth.Summary;
import dev.colonyjournal.graph.ChainLayout;
import dev.colonyjournal.graph.ChainLayout.Box;
import dev.colonyjournal.graph.ChainLayout.Route;
import dev.colonyjournal.graph.ChainLayout.Seg;
import dev.colonyjournal.client.LinksFrame.Rect;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The supply links, one chain at a time: a strip of chain chips on top, the diagram of the chosen chain in the middle
 * and a "what to do" panel at the bottom. All rectangles come from {@link LinksFrame} and {@link ChainLayout}; drawing
 * and mouse hit-testing use the same helper methods below.
 */
final class LinksTab extends JournalTab {
   private static final int NODE_H = LinksFrame.NODE_H;
   private static final int FRAME = 22;
   private static final int LEGEND_ENTRIES = 11;
   private static final int LEGEND_ROW = 14;
   private static final int LINE_OK = 0xFF6FE07F;
   private static final int LINE_BROKEN = 0xFFE05050;
   private static final int LINE_OPTIONAL = 0xFFE0A030;
   private static final int LINE_IDLE = 0xFF8C8C8C;
   private static final Map<Status, ResourceLocation> FRAMES = new EnumMap<>(Status.class);
   private static final ItemStack ICON = new ItemStack(Items.CHAIN);

   static {
      FRAMES.put(Status.OK, Ui.sprite("advancements/task_frame_obtained"));
      FRAMES.put(Status.PARTIAL, Ui.sprite("advancements/goal_frame_obtained"));
      FRAMES.put(Status.NO_WORKER, Ui.sprite("advancements/challenge_frame_unobtained"));
      FRAMES.put(Status.UNBUILT, Ui.sprite("advancements/goal_frame_unobtained"));
      FRAMES.put(Status.ABSENT, Ui.sprite("advancements/task_frame_unobtained"));
   }

   private boolean dirty = true;
   private Summary[] summaries = new Summary[0];
   private LinksFrame frame;
   private int chain = -1;
   private String selected;
   private int chipScroll;
   private double panX = LinksFrame.PAD;
   private double panY = LinksFrame.PAD;
   private boolean dragging;
   private boolean dragMoved;
   private boolean legend;
   private List<FormattedCharSequence> tip;
   private ChainLayout.Result layout;
   private int layoutChain = -1;
   private int layoutW = -1;
   private int layoutH = -1;
   private int cardW = 64;
   private final Map<String, List<String>> nameLines = new HashMap<>();

   LinksTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "map";
   }

   @Override
   boolean ownBackground() {
      return true;
   }

   @Override
   void onReport() {
      this.dirty = true;
   }

   static int color(Status status) {
      return switch (status) {
         case ABSENT -> 0xFF9A9A9A;
         case UNBUILT -> 0xFFE05050;
         case NO_WORKER -> 0xFFE08A30;
         case PARTIAL -> 0xFFE0C040;
         case OK -> 0xFF55CC66;
      };
   }

   private static int color(Light light) {
      return switch (light) {
         case GREEN -> 0xFF55CC66;
         case YELLOW -> 0xFFE0C040;
         case RED -> 0xFFE05050;
      };
   }

   private static int color(EdgeState state, boolean optional) {
      return switch (state) {
         case OK -> LINE_OK;
         case BROKEN -> LINE_BROKEN;
         case OPTIONAL_BROKEN -> LINE_OPTIONAL;
         case IDLE -> LINE_IDLE;
      };
   }

   private Status status(String id) {
      return this.screen.report().statusOf(id);
   }

   private static String reasonKey(Status status) {
      return "journal.colonyjournal.reason." + status.name().toLowerCase(Locale.ROOT);
   }

   private Chain current() {
      return BuildingGraph.CHAINS.get(this.chain);
   }

   // ------------------------------------------------------------------ state

   /** Brings the summaries, the frame, the chosen chain and the layout up to date. */
   private void ensure() {
      if (this.dirty || this.summaries.length != BuildingGraph.CHAINS.size()) {
         this.dirty = false;
         this.summaries = new Summary[BuildingGraph.CHAINS.size()];

         for (int i = 0; i < this.summaries.length; i++) {
            this.summaries[i] = ChainHealth.summarize(BuildingGraph.CHAINS.get(i), this::status);
         }
      }

      if (this.frame == null || this.frame.width != this.cw() || this.frame.height != this.ch()) {
         this.frame = LinksFrame.of(this.cw(), this.ch(), BuildingGraph.CHAINS.size());
         this.chipScroll = this.frame.clampScroll(this.chipScroll);
      }

      if (this.chain < 0) {
         this.chain = ChainHealth.firstWithProblem(this::status);
         this.chipScroll = this.frame.scrollToShow(this.chain, 0);
      }

      int vw = this.frame.innerW();
      int vh = this.frame.innerH();
      if (this.layout == null || this.layoutChain != this.chain || this.layoutW != vw || this.layoutH != vh) {
         this.buildLayout(vw, vh);
      }
   }

   private void buildLayout(int vw, int vh) {
      Chain chain = this.current();
      this.layoutChain = this.chain;
      this.layoutW = vw;
      this.layoutH = vh;
      LinksFrame.CardFit fit = LinksFrame.fitCards(chain.members(), chain.edges(), vw, vh, edge -> this.font().width(needText(edge)) + 4);
      ChainLayout.Result best = fit.result();
      this.cardW = fit.cardW();
      this.layout = best;
      this.nameLines.clear();
      int textW = this.cardW - FRAME - 8;

      for (Box box : this.layout.boxes()) {
         this.nameLines.put(box.id(), TextFit.wrap(ColonyReport.nameOf(box.id()).getString(), textW, 2, this.font()::width));
      }

      this.panX = LinksFrame.PAD;
      this.panY = LinksFrame.PAD;
   }

   private static String needText(Edge edge) {
      return Component.translatable("journal.colonyjournal.need." + edge.need()).getString();
   }

   private void selectChain(int index) {
      if (index != this.chain) {
         this.chain = index;
         this.selected = null;
         this.layout = null;
      }

      this.chipScroll = this.frame.scrollToShow(index, this.chipScroll);
   }

   /** Selects the chain that draws the link id to partner (else any chain with the building; the first one with a problem if several do), selects it and scrolls it into view. */
   void focus(String id, String partner) {
      List<Integer> candidates = partner == null ? BuildingGraph.chainsOf(id) : BuildingGraph.chainsOfLink(id, partner);
      if (candidates.isEmpty()) {
         return;
      }

      this.ensure();
      this.selectChain(ChainHealth.pick(candidates, this::status));
      this.ensure();
      this.selected = id;
      Box box = this.layout.byId().get(id);
      if (box != null) {
         this.panX = LinksFrame.panFor(this.frame.view.w(), box.x(), box.w());
         this.panY = LinksFrame.panFor(this.frame.view.h(), box.y(), box.h());
         this.clampPan();
      }
   }

   private void clampPan() {
      int vw = this.frame.view.w();
      int vh = this.frame.view.h();
      this.panX = Math.max(vw - this.layout.width() - LinksFrame.PAD, Math.min(LinksFrame.PAD, this.panX));
      this.panY = Math.max(vh - this.layout.height() - LinksFrame.PAD, Math.min(LinksFrame.PAD, this.panY));
   }

   // ------------------------------------------------------------------ geometry shared by drawing and mouse

   private int viewX() {
      return this.cx() + this.frame.view.x();
   }

   private int viewY() {
      return this.cy() + this.frame.view.y();
   }

   /** Screen x of the diagram origin. */
   private int originX() {
      return this.viewX() + LinksFrame.place(this.frame.view.w(), this.layout.width(), this.panX);
   }

   private int originY() {
      return this.viewY() + LinksFrame.place(this.frame.view.h(), this.layout.height(), this.panY);
   }

   private boolean inView(double mx, double my) {
      return this.frame.view.contains(mx - this.cx(), my - this.cy());
   }

   private int legendBtnW() {
      return Math.min(60, Math.max(36, this.font().width(Component.translatable("journal.colonyjournal.legend.button")) + 10));
   }

   private int legendBtnX() {
      return this.viewX() + this.frame.view.w() - this.legendBtnW() - 4;
   }

   private int legendBtnY() {
      return this.viewY() + 3;
   }

   private boolean overLegendButton(double mx, double my) {
      return this.inView(mx, my) && Ui.in(mx, my, this.legendBtnX(), this.legendBtnY(), this.legendBtnW(), 13);
   }

   private int legendCols() {
      int cols = Ui.clamp(this.cw() / 150, 1, 3);
      while (cols < 5 && ((LEGEND_ENTRIES + cols - 1) / cols) * LEGEND_ROW + 6 > this.frame.view.h() - 20) {
         cols++;
      }

      return cols;
   }

   private int legendRows() {
      return (LEGEND_ENTRIES + this.legendCols() - 1) / this.legendCols();
   }

   private int legendX() {
      return this.viewX() + 4;
   }

   private int legendY() {
      return this.legendBtnY() + 16;
   }

   private int legendW() {
      return this.frame.view.w() - 8;
   }

   private int legendH() {
      return this.legendRows() * LEGEND_ROW + 6;
   }

   private boolean overLegend(double mx, double my) {
      return this.legend && this.inView(mx, my) && Ui.in(mx, my, this.legendX(), this.legendY(), this.legendW(), this.legendH());
   }

   private int boxX(Box box) {
      return this.originX() + box.x();
   }

   private int boxY(Box box) {
      return this.originY() + box.y();
   }

   /** The card under the mouse, or null. Uses the same rectangles as the drawing. */
   private Box cardAt(double mx, double my) {
      if (!this.inView(mx, my) || this.overLegend(mx, my) || this.overLegendButton(mx, my)) {
         return null;
      }

      List<Box> boxes = this.layout.boxes();
      for (int i = boxes.size() - 1; i >= 0; i--) {
         Box box = boxes.get(i);
         if (Ui.in(mx, my, this.boxX(box), this.boxY(box), box.w(), box.h())) {
            return box;
         }
      }

      return null;
   }

   /** Index of the route whose line is within two pixels of the mouse, or -1. */
   private int edgeAt(double mx, double my) {
      if (!this.inView(mx, my) || this.overLegend(mx, my) || this.overLegendButton(mx, my)) {
         return -1;
      }

      double px = mx - this.originX();
      double py = my - this.originY();
      int best = -1;
      double bestDistance = 2.5;
      List<Route> routes = this.layout.routes();

      for (int i = 0; i < routes.size(); i++) {
         double d = routes.get(i).distance(px, py);
         if (d <= bestDistance) {
            bestDistance = d;
            best = i;
         }
      }

      return best;
   }

   // ------------------------------------------------------------------ drawing helpers

   /** A frame in the style of a status, with a coloured example number when it is big enough. Used by the legend and the guide. */
   static void sample(GuiGraphics g, Font font, Status status, int x, int y, int size, boolean number) {
      g.blitSprite(FRAMES.get(status), x, y, size, size);
      if (number) {
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 200.0F);
         g.drawString(font, "3", x + size - 7, y + size - 10, color(status), true);
         g.pose().popPose();
      }
   }

   private static int withAlpha(int color, int alpha) {
      return (color & 0x00FFFFFF) | alpha << 24;
   }

   /** One piece of a line, t pixels thick, grown by pad pixels on every side. */
   private static void stroke(GuiGraphics g, Seg seg, int ox, int oy, int t, int pad, int color) {
      g.fill(ox + seg.x1() - pad, oy + seg.y1() - pad, ox + seg.x2() + t + pad, oy + seg.y2() + t + pad, color);
   }

   private static void strokeDashed(GuiGraphics g, Seg seg, int ox, int oy, int t, int pad, int color) {
      for (Seg dash : Dashes.split(seg)) {
         stroke(g, dash, ox, oy, t, pad, color);
      }
   }

   private static void arrow(GuiGraphics g, int ox, int oy, Route route, int pad, int color) {
      int ex = ox + route.endX();
      int ey = oy + route.endY();

      for (int d = 0; d < ChainLayout.ARROW; d++) {
         int h = d * 3 / 5;
         int x = ex - 1 - d;
         g.fill(x - pad, ey - h - pad, x + 1 + pad, ey + h + 2 + pad, color);
      }
   }

   private static void dashedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
      for (Seg side : new Seg[] {new Seg(x, y, x + w - 1, y), new Seg(x, y + h - 1, x + w - 1, y + h - 1), new Seg(x, y, x, y + h - 1), new Seg(x + w - 1, y, x + w - 1, y + h - 1)}) {
         for (Seg dash : Dashes.split(side)) {
            g.fill(dash.x1(), dash.y1(), dash.x2() + 1, dash.y2() + 1, color);
         }
      }
   }

   // ------------------------------------------------------------------ rendering

   @Override
   void render(GuiGraphics g, int mx, int my) {
      this.ensure();
      this.tip = null;
      int x0 = this.cx();
      int y0 = this.cy();
      Ui.stone(g, x0, y0, this.cw(), this.ch(), 0, 0);
      g.fill(x0, y0, x0 + this.cw(), y0 + this.ch(), 0x70000000);
      this.renderStrip(g, mx, my);
      Rect v = this.frame.view;
      g.fill(this.viewX(), this.viewY(), this.viewX() + v.w(), this.viewY() + v.h(), 0x40000000);
      g.enableScissor(this.viewX(), this.viewY(), this.viewX() + v.w(), this.viewY() + v.h());
      this.renderDiagram(g, mx, my);
      // above the cards, whose icons and texts are drawn at z 150-200
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 300.0F);
      this.renderLegend(g, mx, my);
      g.pose().popPose();
      g.disableScissor();
      this.renderDetail(g);
   }

   private Component chainName(int index) {
      return Component.translatable("journal.colonyjournal.chain." + BuildingGraph.CHAINS.get(index).id());
   }

   private Component chainDesc(int index) {
      return Component.translatable("journal.colonyjournal.chain." + BuildingGraph.CHAINS.get(index).id() + ".desc");
   }

   private void renderStrip(GuiGraphics g, int mx, int my) {
      LinksFrame f = this.frame;
      int x0 = this.cx();
      int y0 = this.cy();
      int hover = f.chipAt(mx - x0, my - y0, this.chipScroll);
      g.fill(x0, y0, x0 + f.width, y0 + f.chipH, 0x90000000);
      g.enableScissor(x0 + f.clipX(), y0, x0 + f.clipX() + f.clipW(), y0 + f.chipH);

      for (int i = 0; i < BuildingGraph.CHAINS.size(); i++) {
         Chain chain = BuildingGraph.CHAINS.get(i);
         Summary s = this.summaries[i];
         int x = x0 + f.chipX(i, this.chipScroll);
         boolean on = i == this.chain;
         g.fill(x + 1, y0 + 1, x + f.chipW - 1, y0 + f.chipH - 1, on ? 0xE03C5A8C : (i == hover ? 0xE0303030 : 0xC0181818));
         g.renderOutline(x + 1, y0 + 1, f.chipW - 2, f.chipH - 2, on ? 0xFFFFFFFF : 0xFF404040);
         boolean text = f.chipW >= LinksFrame.CHIP_TEXT;
         int ix = text ? x + 4 : x + (f.chipW - 16) / 2;
         int iy = y0 + (f.chipH - 16) / 2;
         g.renderItem(BuildingIcons.of(chain.icon()), ix, iy);
         g.pose().pushPose();
         g.pose().translate(0.0F, 0.0F, 200.0F);
         g.fill(ix + 10, iy - 1, ix + 17, iy + 6, 0xFF000000);
         g.fill(ix + 11, iy, ix + 16, iy + 5, color(s.light()));
         if (text) {
            Ui.text(g, this.font(), s.working() + "/" + s.total(), ix + 20, y0 + (f.chipH - 8) / 2 + 1, color(s.light()));
         }

         g.pose().popPose();
      }

      g.disableScissor();
      if (f.scrolls) {
         Rect left = f.leftArrow();
         Rect right = f.rightArrow();
         Ui.button(g, this.font(), x0 + left.x(), y0, left.w(), left.h(), Component.literal("<"), this.chipScroll > 0, left.contains(mx - x0, my - y0));
         Ui.button(g, this.font(), x0 + right.x(), y0, right.w(), right.h(), Component.literal(">"), this.chipScroll < f.maxScroll(), right.contains(mx - x0, my - y0));
      }

      if (hover >= 0) {
         Summary s = this.summaries[hover];
         this.tip = this.tipOf(
            this.chainName(hover).copy().withStyle(ChatFormatting.WHITE),
            this.chainDesc(hover).copy().withStyle(ChatFormatting.GRAY),
            Component.translatable("journal.colonyjournal.links.chip_tip", s.working(), s.total()).withStyle(ChatFormatting.GRAY),
            Component.translatable("journal.colonyjournal.links.light." + s.light().name().toLowerCase(Locale.ROOT)).withStyle(s.light() == Light.GREEN ? ChatFormatting.GREEN : (s.light() == Light.YELLOW ? ChatFormatting.YELLOW : ChatFormatting.RED))
         );
      }
   }

   private List<FormattedCharSequence> tipOf(Component... parts) {
      List<FormattedCharSequence> lines = new ArrayList<>();

      for (Component part : parts) {
         lines.addAll(this.font().split(part, 220));
      }

      return lines;
   }

   private void renderDiagram(GuiGraphics g, int mx, int my) {
      ColonyReport report = this.screen.report();
      Chain chain = this.current();
      List<Route> routes = this.layout.routes();
      Box hoverCard = this.cardAt(mx, my);
      int hoverEdge = hoverCard == null ? this.edgeAt(mx, my) : -1;
      String focus = hoverCard != null ? hoverCard.id() : this.selected;
      int ox = this.originX();
      int oy = this.originY();

      // dimmed links first, the highlighted ones on top
      List<Integer> order = new ArrayList<>();
      for (int pass = 0; pass < 2; pass++) {
         for (int i = 0; i < routes.size(); i++) {
            if (this.lit(routes.get(i), i, focus, hoverEdge) == (pass == 1)) {
               order.add(i);
            }
         }
      }

      boolean emphasis = focus != null || hoverEdge >= 0;
      // all outlines first so that no outline covers the lighter line of another link
      for (int pass = 0; pass < 2; pass++) {
         for (int i : order) {
            Route route = routes.get(i);
            boolean lit = this.lit(route, i, focus, hoverEdge);
            EdgeState state = ChainHealth.edgeState(route.edge(), this::status);
            int t = lit && emphasis ? 2 : 1;
            int alpha = lit ? 0xFF : 0x40;
            int color = pass == 0 ? withAlpha(0xFF000000, alpha * 0xE0 / 0xFF) : withAlpha(color(state, route.edge().optional()), lit ? (emphasis ? 0xFF : 0xE0) : 0x40);
            int pad = pass == 0 ? 1 : 0;
            boolean dashed = route.edge().optional();

            for (Seg seg : route.segs()) {
               if (dashed) {
                  strokeDashed(g, seg, ox, oy, t, pad, color);
               } else {
                  stroke(g, seg, ox, oy, t, pad, color);
               }
            }

            arrow(g, ox, oy, route, pad, color);
         }
      }

      for (int i : order) {
         Route route = routes.get(i);
         if (route.label() != null) {
            boolean lit = this.lit(route, i, focus, hoverEdge);
            EdgeState state = ChainHealth.edgeState(route.edge(), this::status);
            ChainLayout.Label label = route.label();
            g.fill(ox + label.x(), oy + label.y(), ox + label.x() + label.w(), oy + label.y() + label.h(), lit ? 0xE0000000 : 0x60000000);
            g.drawString(this.font(), needText(route.edge()), ox + label.x() + 2, oy + label.y() + 1, withAlpha(color(state, false), lit ? 0xFF : 0x60), false);
         }
      }

      for (Box box : this.layout.boxes()) {
         boolean related = focus == null || box.id().equals(focus) || this.linked(chain, focus, box.id());
         this.drawCard(g, report, box, !related);
         if (box.id().equals(this.selected)) {
            g.renderOutline(this.boxX(box) - 2, this.boxY(box) - 2, box.w() + 4, box.h() + 4, 0xFFFFFFFF);
         } else if (hoverCard != null && box.id().equals(hoverCard.id())) {
            g.renderOutline(this.boxX(box) - 1, this.boxY(box) - 1, box.w() + 2, box.h() + 2, 0xFFFFE080);
         }
      }

      this.renderScrollBars(g);
      if (this.tip == null) {
         if (hoverCard != null) {
            this.tip = this.cardTip(chain, hoverCard.id());
         } else if (hoverEdge >= 0) {
            this.tip = this.edgeTip(routes.get(hoverEdge));
         }
      }
   }

   private boolean lit(Route route, int index, String focus, int hoverEdge) {
      if (hoverEdge >= 0) {
         return index == hoverEdge;
      }

      return focus == null || route.edge().from().equals(focus) || route.edge().to().equals(focus);
   }

   private boolean linked(Chain chain, String focus, String id) {
      for (Edge edge : chain.edges()) {
         if ((edge.from().equals(focus) && edge.to().equals(id)) || (edge.to().equals(focus) && edge.from().equals(id))) {
            return true;
         }
      }

      return false;
   }

   private void drawCard(GuiGraphics g, ColonyReport report, Box box, boolean dim) {
      String id = box.id();
      Status status = report.statusOf(id);
      boolean ghost = status == Status.ABSENT || status == Status.UNBUILT;
      int x = this.boxX(box);
      int y = this.boxY(box);
      int accent = color(status);
      g.fill(x, y, x + box.w(), y + box.h(), ghost ? 0x90101010 : 0xD0101018);
      if (ghost) {
         dashedRect(g, x, y, box.w(), box.h(), accent);
      } else {
         g.renderOutline(x, y, box.w(), box.h(), withAlpha(accent, 0xC0));
      }

      int fx = x + 3;
      int fy = y + (box.h() - FRAME) / 2;
      g.blitSprite(FRAMES.get(status), fx, fy, FRAME, FRAME);
      g.renderItem(BuildingIcons.of(id), fx + 3, fy + 3);
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 200.0F);
      if (ghost) {
         g.fill(fx + 3, fy + 3, fx + 19, fy + 19, 0x90181818);
      }

      TypeInfo info = report.types.get(id);
      if (info != null && info.count() > 1) {
         g.drawString(this.font(), "x" + info.count(), fx + 1, fy + 1, accent, true);
      }

      if (info != null && info.slots() > 0) {
         String workers = info.workers() + "/" + info.slots();
         if (this.font().width(workers) > FRAME) {
            workers = Integer.toString(info.workers()); // the full ratio is in the tooltip; the badge must stay on the frame
         }

         g.drawString(this.font(), workers, fx + FRAME - this.font().width(workers), fy + FRAME - 8, accent, true);
      }

      List<String> lines = this.nameLines.getOrDefault(id, List.of());
      int textY = y + (box.h() - lines.size() * 9) / 2 + 1;
      for (int i = 0; i < lines.size(); i++) {
         g.drawString(this.font(), lines.get(i), fx + FRAME + 3, textY + i * 9, ghost ? 0xFF9A9A9A : 0xFFFFFFFF, true);
      }

      if (dim) {
         g.fill(x, y, x + box.w(), y + box.h(), 0xA0202020);
      }

      g.pose().popPose();
   }

   private void renderScrollBars(GuiGraphics g) {
      Rect v = this.frame.view;
      int vx = this.viewX();
      int vy = this.viewY();
      if (this.layout.height() + 2 * LinksFrame.PAD > v.h()) {
         int total = this.layout.height() + 2 * LinksFrame.PAD;
         int barH = Math.max(10, v.h() * v.h() / total);
         int span = v.h() - barH;
         double done = (LinksFrame.PAD - this.panY) / Math.max(1, total - v.h());
         int by = vy + (int)Math.round(span * Math.max(0.0, Math.min(1.0, done)));
         g.fill(vx + v.w() - 3, vy, vx + v.w() - 1, vy + v.h(), 0x40000000);
         g.fill(vx + v.w() - 3, by, vx + v.w() - 1, by + barH, 0xA0FFFFFF);
      }

      if (this.layout.width() + 2 * LinksFrame.PAD > v.w()) {
         int total = this.layout.width() + 2 * LinksFrame.PAD;
         int barW = Math.max(10, v.w() * v.w() / total);
         int span = v.w() - barW;
         double done = (LinksFrame.PAD - this.panX) / Math.max(1, total - v.w());
         int bx = vx + (int)Math.round(span * Math.max(0.0, Math.min(1.0, done)));
         g.fill(vx, vy + v.h() - 3, vx + v.w(), vy + v.h() - 1, 0x40000000);
         g.fill(bx, vy + v.h() - 3, bx + barW, vy + v.h() - 1, 0xA0FFFFFF);
      }
   }

   private List<FormattedCharSequence> cardTip(Chain chain, String id) {
      ColonyReport report = this.screen.report();
      Status status = report.statusOf(id);
      TypeInfo info = report.types.get(id);
      List<Component> parts = new ArrayList<>();
      parts.add(ColonyReport.nameOf(id).copy().withStyle(ChatFormatting.WHITE));
      String reason = Component.translatable(reasonKey(status)).getString();
      if (info != null && info.slots() > 0) {
         reason += "  " + Component.translatable("journal.colonyjournal.workers", info.workers(), info.slots()).getString();
      }

      parts.add(Component.literal(reason).withStyle(style -> style.withColor(color(status) & 0xFFFFFF)));
      parts.add(Component.literal(this.adviceText(chain, ChainAdvice.forNode(chain, id, this::status))).withStyle(ChatFormatting.GRAY));
      return this.tipOf(parts.toArray(new Component[0]));
   }

   private List<FormattedCharSequence> edgeTip(Route route) {
      Edge edge = route.edge();
      EdgeState state = ChainHealth.edgeState(edge, this::status);
      String stateKey = switch (state) {
         case OK -> "ok";
         case BROKEN -> "broken";
         case OPTIONAL_BROKEN -> "optional";
         case IDLE -> "idle";
      };
      return this.tipOf(
         Component.translatable("journal.colonyjournal.links.edge_tip", ColonyReport.nameOf(edge.from()), ColonyReport.nameOf(edge.to()), needText(edge)).withStyle(ChatFormatting.WHITE),
         Component.translatable("journal.colonyjournal.links.edge." + stateKey, ColonyReport.nameOf(edge.from()), ColonyReport.nameOf(edge.to())).withStyle(style -> style.withColor(color(state, edge.optional()) & 0xFFFFFF))
      );
   }

   // ------------------------------------------------------------------ legend

   private void renderLegend(GuiGraphics g, int mx, int my) {
      boolean hover = this.overLegendButton(mx, my);
      Ui.button(g, this.font(), this.legendBtnX(), this.legendBtnY(), this.legendBtnW(), 13, Component.translatable("journal.colonyjournal.legend.button"), true, hover || this.legend);
      if (hover) {
         this.tip = this.tipOf(Component.translatable("journal.colonyjournal.legend.tip"));
      }

      if (!this.legend) {
         return;
      }

      int px = this.legendX();
      int py = this.legendY();
      g.fill(px, py, px + this.legendW(), py + this.legendH(), 0xEE101010);
      g.renderOutline(px, py, this.legendW(), this.legendH(), 0xFF606060);
      int cols = this.legendCols();
      int colW = (this.legendW() - 8) / cols;
      int rows = this.legendRows();
      Status[] statuses = {Status.OK, Status.PARTIAL, Status.NO_WORKER, Status.UNBUILT, Status.ABSENT};
      int[] lineColors = {LINE_OK, LINE_BROKEN, LINE_OPTIONAL, LINE_IDLE};
      String[] lineKeys = {"line_ok", "line_missing", "line_optional", "line_idle"};

      for (int i = 0; i < LEGEND_ENTRIES; i++) {
         int ex = px + 4 + (i / rows) * colW;
         int ey = py + 3 + (i % rows) * LEGEND_ROW;
         int labelX = ex + 20;
         int labelW = colW - 24;
         String label;
         int labelColor = 0xFFE8E8E8;
         if (i < 5) {
            sample(g, this.font(), statuses[i], ex, ey, 13, false);
            label = Component.translatable(reasonKey(statuses[i])).getString();
            labelColor = color(statuses[i]);
         } else if (i < 9) {
            g.fill(ex, ey + 4, ex + 16, ey + 9, 0xE0000000);
            if (i == 7) {
               for (Seg dash : Dashes.split(new Seg(ex + 1, ey + 6, ex + 14, ey + 6))) {
                  g.fill(dash.x1(), ey + 5, dash.x2() + 1, ey + 8, lineColors[i - 5]);
               }
            } else {
               g.fill(ex + 1, ey + 5, ex + 15, ey + 8, lineColors[i - 5]);
            }

            label = Component.translatable("journal.colonyjournal.legend." + lineKeys[i - 5]).getString();
         } else if (i == 9) {
            Ui.text(g, this.font(), "x3", ex + 1, ey + 3, 0xFFFFFFFF);
            label = Component.translatable("journal.colonyjournal.legend.count").getString();
         } else {
            Ui.text(g, this.font(), "1/2", ex + 1, ey + 3, 0xFFFFFFFF);
            label = Component.translatable("journal.colonyjournal.legend.workers").getString();
         }

         Ui.text(g, this.font(), TextFit.ellipsize(label, labelW, this.font()::width), labelX, ey + 3, labelColor);
      }
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      if (this.tip != null && !this.tip.isEmpty()) {
         g.renderTooltip(this.font(), this.tip, mx, my);
      }
   }

   // ------------------------------------------------------------------ the "what to do" panel

   private String names(List<Edge> edges, boolean producers) {
      StringBuilder out = new StringBuilder();

      for (Edge edge : edges) {
         if (out.length() > 0) {
            out.append(", ");
         }

         if (producers) {
            out.append(Component.translatable("journal.colonyjournal.links.from", needText(edge), ColonyReport.nameOf(edge.from()).getString(), Component.translatable(reasonKey(this.status(edge.from()))).getString()).getString());
         } else {
            out.append(ColonyReport.nameOf(edge.to()).getString()).append(" (").append(needText(edge)).append(")");
         }
      }

      return out.toString();
   }

   /** The advice as one plain sentence (or two). */
   private String adviceText(Chain chain, Advice advice) {
      String prefix = "journal.colonyjournal.links.todo.";
      if (advice.id() == null) {
         return Component.translatable(prefix + advice.key(), this.chainName(this.chain).getString()).getString();
      }

      String name = ColonyReport.nameOf(advice.id()).getString();
      TypeInfo info = this.screen.report().types.get(advice.id());
      String text = switch (advice.key()) {
         case ChainAdvice.ABSENT -> Component.translatable(prefix + advice.key(), name, this.names(advice.edges(), false)).getString();
         case ChainAdvice.LACKING -> Component.translatable(prefix + advice.key(), name, this.names(advice.edges(), true)).getString();
         case ChainAdvice.PARTIAL -> Component.translatable(prefix + advice.key(), name, info == null ? 0 : info.workers(), info == null ? 0 : info.slots()).getString();
         default -> Component.translatable(prefix + advice.key(), name).getString();
      };
      if ((advice.key().equals(ChainAdvice.NO_WORKER) || advice.key().equals(ChainAdvice.UNBUILT)) && !advice.edges().isEmpty()) {
         text += " " + Component.translatable("journal.colonyjournal.links.waiting", this.names(advice.edges(), false)).getString();
      }

      return text;
   }

   private void renderDetail(GuiGraphics g) {
      Rect d = this.frame.detail;
      if (d.empty()) {
         return;
      }

      Chain chain = this.current();
      int x = this.cx() + d.x() + 3;
      int y = this.cy() + d.y();
      int w = d.w() - 6;
      g.blitSprite(Ui.TITLE_BOX, x, y, w, d.h());
      boolean node = this.selected != null && this.layout.byId().containsKey(this.selected);
      Advice advice = node ? ChainAdvice.forNode(chain, this.selected, this::status) : ChainAdvice.forChain(chain, this::status);
      String text = this.adviceText(chain, advice);
      if (!node && advice.problem()) {
         text = Component.translatable("journal.colonyjournal.links.start", text).getString();
      }

      int color = node ? color(this.status(this.selected)) : (advice.problem() ? color(this.summaries[this.chain].light()) : 0xFFE8E8E8);
      List<String> lines = TextFit.wrap(text, w - 18, this.frame.detailLines, this.font()::width);
      for (int i = 0; i < lines.size(); i++) {
         g.drawString(this.font(), lines.get(i), x + 9, y + 6 + i * 11, i == 0 && node ? color : 0xFFE8E8E8, true);
      }
   }

   // ------------------------------------------------------------------ input

   @Override
   boolean mouseClicked(double mx, double my, int button) {
      if (button != 0 || mx < this.cx() || my < this.cy() || mx >= this.cx() + this.cw() || my >= this.cy() + this.ch()) {
         return false;
      }

      this.ensure();
      double lx = mx - this.cx();
      double ly = my - this.cy();
      LinksFrame f = this.frame;
      if (f.strip.contains(lx, ly)) {
         if (f.scrolls && f.leftArrow().contains(lx, ly)) {
            this.chipScroll = f.clampScroll(this.chipScroll - f.chipW);
            Ui.click();
         } else if (f.scrolls && f.rightArrow().contains(lx, ly)) {
            this.chipScroll = f.clampScroll(this.chipScroll + f.chipW);
            Ui.click();
         } else {
            int chip = f.chipAt(lx, ly, this.chipScroll);
            if (chip >= 0) {
               this.selectChain(chip);
               Ui.click();
            }
         }

         return true;
      }

      if (!f.view.contains(lx, ly)) {
         return true;
      }

      if (this.overLegendButton(mx, my)) {
         this.legend = !this.legend;
         Ui.click();
         return true;
      }

      if (this.overLegend(mx, my)) {
         return true;
      }

      Box card = this.cardAt(mx, my);
      if (card != null) {
         this.selected = card.id().equals(this.selected) ? null : card.id();
         Ui.click();
      } else {
         this.dragging = true;
         this.dragMoved = false;
      }

      return true;
   }

   @Override
   boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      if (this.dragging && this.layout != null) {
         if (Math.abs(dx) + Math.abs(dy) > 0.0) {
            this.dragMoved = true;
         }

         this.panX += dx;
         this.panY += dy;
         this.clampPan();
         return true;
      }

      return false;
   }

   @Override
   boolean mouseReleased(double mx, double my, int button) {
      boolean was = this.dragging;
      this.dragging = false;
      if (was && !this.dragMoved) {
         // a click on empty space clears the selection
         this.selected = null;
      }

      return was;
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      if (mx < this.cx() || my < this.cy() || mx >= this.cx() + this.cw() || my >= this.cy() + this.ch()) {
         return false;
      }

      this.ensure();
      double lx = mx - this.cx();
      double ly = my - this.cy();
      if (this.frame.strip.contains(lx, ly)) {
         this.chipScroll = this.frame.clampScroll(this.chipScroll - (int)Math.round((sy != 0.0 ? sy : sx) * this.frame.chipW));
         return true;
      }

      boolean tall = this.layout.height() + 2 * LinksFrame.PAD > this.frame.view.h();
      if (net.minecraft.client.gui.screens.Screen.hasShiftDown() || !tall) {
         this.panX += (sy + sx) * 16.0;
      } else {
         this.panX += sx * 16.0;
         this.panY += sy * 16.0;
      }

      this.clampPan();
      return true;
   }
}
