package dev.colonyjournal.client;

import com.minecolonies.api.colony.IColonyView;
import dev.colonyjournal.analysis.ColonyReport;
import dev.colonyjournal.analysis.ColonyReport.Issue;
import dev.colonyjournal.analysis.ColonyReport.Link;
import dev.colonyjournal.analysis.ColonyReport.Row;
import dev.colonyjournal.analysis.ColonyReport.Severity;
import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.analysis.ColonyReport.TypeInfo;
import dev.colonyjournal.graph.BuildingGraph;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The colony journal: a map of building links, a list of problems and a list of buildings. */
public final class JournalScreen extends Screen {
   private enum Tab {
      MAP,
      PROBLEMS,
      BUILDINGS
   }

   private static final int NODE_W = 92;
   private static final int NODE_H = 16;
   private static final int GAP_X = 52;
   private static final int GAP_Y = 6;
   private static final int[] OK_COLORS = {0xFFE05050, 0xFFE05050, 0xFFE08A30, 0xFFE0C040, 0xFF55CC66};

   private final IColonyView colony;
   private ColonyReport report;
   private Tab tab = Tab.MAP;
   private String selected;
   private double panX = 8;
   private double panY = 8;
   private int scroll;
   private int refreshTimer;
   private boolean dragging;

   public JournalScreen(IColonyView colony) {
      super(Component.translatable("journal.colonyjournal.title"));
      this.colony = colony;
      this.report = ColonyReport.build(colony);
   }

   private int left() {
      return Math.max(6, (this.width - 460) / 2);
   }

   private int panelWidth() {
      return Math.min(460, this.width - 12);
   }

   private int top() {
      return 44;
   }

   private int panelHeight() {
      return this.height - this.top() - 8;
   }

   @Override
   protected void init() {
      int x = this.left();
      int y = 22;
      int w = 84;

      for (Tab t : Tab.values()) {
         Button button = Button.builder(Component.translatable("journal.colonyjournal.tab." + t.name().toLowerCase()), b -> {
            this.tab = t;
            this.scroll = 0;
            this.rebuildWidgets();
         }).bounds(x, y, w, 18).build();
         button.active = this.tab != t;
         this.addRenderableWidget(button);
         x += w + 4;
      }
   }

   @Override
   public void tick() {
      if (++this.refreshTimer >= 20) {
         this.refreshTimer = 0;
         this.report = ColonyReport.build(this.colony);
      }
   }

   private static int color(Status status) {
      return OK_COLORS[status.ordinal()];
   }

   // ------------------------------------------------------------------ map

   private List<String> visibleNodes() {
      Set<String> ids = new LinkedHashSet<>();

      for (String id : BuildingGraph.NODES.keySet()) {
         if (this.report.statusOf(id) != Status.ABSENT) {
            ids.add(id);
         }
      }

      for (Link link : this.report.links) {
         if (this.report.statusOf(link.edge().to()) != Status.ABSENT) {
            ids.add(link.edge().from());
         }
      }

      return new ArrayList<>(ids);
   }

   private int[] nodePos(List<String> visible, String id) {
      BuildingGraph.Node node = BuildingGraph.NODES.get(id);
      if (node == null) {
         return null;
      }

      int row = 0;

      for (String other : visible) {
         if (other.equals(id)) {
            break;
         }

         BuildingGraph.Node o = BuildingGraph.NODES.get(other);
         if (o != null && o.layer() == node.layer()) {
            row++;
         }
      }

      int x = this.left() + (int)this.panX + node.layer() * (NODE_W + GAP_X);
      int y = this.top() + 10 + (int)this.panY + row * (NODE_H + GAP_Y);
      return new int[] {x, y};
   }

   private static void line(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
      g.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
      g.fill(x2, Math.min(y1, y2), x2 + 1, Math.max(y1, y2) + 1, color);
   }

   private void drawLink(GuiGraphics g, int[] from, int[] to, int color, int channel) {
      boolean forward = to[0] > from[0];
      int sx = forward ? from[0] + NODE_W : from[0];
      int sy = from[1] + NODE_H / 2;
      int tx = forward ? to[0] : to[0] + NODE_W;
      int ty = to[1] + NODE_H / 2;
      int mid = forward ? sx + 8 + channel % 20 : sx - 8 - channel % 20;
      if (Math.abs(to[0] - from[0]) < 4) {
         mid = sx + NODE_W + 4 + channel % 12;
      }

      g.fill(Math.min(sx, mid), sy, Math.max(sx, mid) + 1, sy + 1, color);
      g.fill(mid, Math.min(sy, ty), mid + 1, Math.max(sy, ty) + 1, color);
      g.fill(Math.min(mid, tx), ty, Math.max(mid, tx) + 1, ty + 1, color);
   }

   private void renderMap(GuiGraphics g, int mouseX, int mouseY) {
      int px = this.left();
      int py = this.top();
      g.enableScissor(px, py, px + this.panelWidth(), py + this.panelHeight() - 54);
      List<String> visible = this.visibleNodes();
      int channel = 0;

      for (Link link : this.report.links) {
         int[] a = this.nodePos(visible, link.edge().from());
         int[] b = this.nodePos(visible, link.edge().to());
         if (a == null || b == null || !visible.contains(link.edge().from()) || !visible.contains(link.edge().to()) || this.report.statusOf(link.edge().to()) == Status.ABSENT) {
            continue;
         }

         boolean focus = link.edge().from().equals(this.selected) || link.edge().to().equals(this.selected);
         int color = link.broken() ? (link.edge().optional() ? 0xFFB09030 : 0xFFE05050) : 0xFF5C9A68;
         if (this.selected != null && !focus) {
            color = (color & 0x00FFFFFF) | 0x40000000;
         } else if (!focus) {
            color = (color & 0x00FFFFFF) | 0x90000000;
         }

         this.drawLink(g, a, b, color, channel += 5);
      }

      for (String id : visible) {
         int[] pos = this.nodePos(visible, id);
         TypeInfo info = this.report.types.get(id);
         Status status = this.report.statusOf(id);
         boolean ghost = status == Status.ABSENT;
         int c = color(status);
         g.fill(pos[0], pos[1], pos[0] + NODE_W, pos[1] + NODE_H, ghost ? 0xCC2A1414 : 0xCC10181E);
         g.fill(pos[0], pos[1], pos[0] + 3, pos[1] + NODE_H, c);
         if (id.equals(this.selected)) {
            g.renderOutline(pos[0] - 1, pos[1] - 1, NODE_W + 2, NODE_H + 2, 0xFFFFFFFF);
         }

         String label = info == null ? id : info.name().getString();
         if (info != null && info.count() > 0) {
            label += " ×" + info.count();
         }

         g.drawString(this.font, this.font.plainSubstrByWidth(label, NODE_W - 8), pos[0] + 6, pos[1] + 4, ghost ? 0xFFB08080 : 0xFFFFFFFF, false);
      }

      g.disableScissor();
      this.renderDetail(g, px, py + this.panelHeight() - 52);
   }

   private void renderDetail(GuiGraphics g, int x, int y) {
      g.fill(x, y, x + this.panelWidth(), y + 52, 0xAA000000);
      if (this.selected == null) {
         g.drawString(this.font, Component.translatable("journal.colonyjournal.hint"), x + 4, y + 4, 0xFFAAAAAA, false);
         return;
      }

      TypeInfo info = this.report.types.get(this.selected);
      Status status = this.report.statusOf(this.selected);
      Component head = Component.empty().append(ColonyReport.nameOf(this.selected)).append("  ").append(Component.translatable("journal.colonyjournal.reason." + status.name().toLowerCase()));
      g.drawString(this.font, head, x + 4, y + 4, color(status), false);
      if (info != null && info.slots() > 0) {
         g.drawString(this.font, Component.translatable("journal.colonyjournal.workers", info.workers(), info.slots()), x + 4, y + 15, 0xFFCCCCCC, false);
      }

      StringBuilder needs = new StringBuilder();
      StringBuilder gives = new StringBuilder();

      for (Link link : this.report.links) {
         if (link.edge().to().equals(this.selected)) {
            needs.append(ColonyReport.nameOf(link.edge().from()).getString()).append(link.broken() ? " ✗" : " ✓").append(", ");
         }

         if (link.edge().from().equals(this.selected) && this.report.statusOf(link.edge().to()) != Status.ABSENT) {
            gives.append(ColonyReport.nameOf(link.edge().to()).getString()).append(", ");
         }
      }

      g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("journal.colonyjournal.needs").getString() + " " + trimComma(needs), this.panelWidth() - 8), x + 4, y + 26, 0xFFE0E0A0, false);
      g.drawString(this.font, this.font.plainSubstrByWidth(Component.translatable("journal.colonyjournal.supplies").getString() + " " + trimComma(gives), this.panelWidth() - 8), x + 4, y + 38, 0xFFA0E0A0, false);
   }

   private static String trimComma(StringBuilder text) {
      String s = text.toString();
      return s.endsWith(", ") ? s.substring(0, s.length() - 2) : (s.isEmpty() ? "-" : s);
   }

   // ------------------------------------------------------------------ lists

   private int issueColor(Severity severity) {
      return switch (severity) {
         case ERROR -> 0xFFE05050;
         case WARN -> 0xFFE0C040;
         case INFO -> 0xFF8AB4D8;
      };
   }

   private void renderIssues(GuiGraphics g, int mouseX, int mouseY) {
      int x = this.left();
      int y = this.top();
      int bottom = y + this.panelHeight();
      g.enableScissor(x, y, x + this.panelWidth(), bottom);
      int cursor = y + 4 - this.scroll;
      if (this.report.issues.isEmpty()) {
         g.drawString(this.font, Component.translatable("journal.colonyjournal.all_good"), x + 6, cursor, 0xFF55CC66, false);
      }

      for (Issue issue : this.report.issues) {
         List<FormattedCharSequence> lines = this.font.split(issue.text(), this.panelWidth() - 20);
         int height = lines.size() * 10 + 4;
         g.fill(x, cursor, x + this.panelWidth(), cursor + height, 0x66000000);
         g.fill(x, cursor, x + 3, cursor + height, this.issueColor(issue.severity()));
         int ly = cursor + 2;

         for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x + 8, ly, 0xFFFFFFFF, false);
            ly += 10;
         }

         cursor += height + 3;
      }

      g.disableScissor();
   }

   private int issuesHeight() {
      int total = 4;

      for (Issue issue : this.report.issues) {
         total += this.font.split(issue.text(), this.panelWidth() - 20).size() * 10 + 7;
      }

      return total;
   }

   private void renderBuildings(GuiGraphics g) {
      int x = this.left();
      int y = this.top();
      g.enableScissor(x, y, x + this.panelWidth(), y + this.panelHeight());
      int cursor = y + 4 - this.scroll;

      for (Row row : this.report.rows) {
         g.fill(x, cursor, x + this.panelWidth(), cursor + 14, 0x66000000);
         g.fill(x, cursor, x + 3, cursor + 14, color(row.status()));
         g.drawString(this.font, this.font.plainSubstrByWidth(row.name().getString(), 150), x + 8, cursor + 3, 0xFFFFFFFF, false);
         g.drawString(this.font, Component.translatable("journal.colonyjournal.level", row.level(), row.maxLevel()), x + 162, cursor + 3, 0xFFCCCCCC, false);
         if (row.slots() > 0) {
            g.drawString(this.font, Component.translatable("journal.colonyjournal.workers", row.workers(), row.slots()), x + 222, cursor + 3, 0xFFCCCCCC, false);
         }

         if (row.requests() > 0) {
            g.drawString(this.font, Component.translatable("journal.colonyjournal.requests", row.requests()), x + 300, cursor + 3, 0xFFE0C040, false);
         }

         g.drawString(this.font, row.pos().toShortString(), x + 360, cursor + 3, 0xFF888888, false);
         cursor += 16;
      }

      g.disableScissor();
   }

   // ------------------------------------------------------------------ screen

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      super.render(g, mouseX, mouseY, partial);
      g.drawString(this.font, this.title, this.left(), 8, 0xFFFFFFFF, true);
      Component stat = Component.translatable("journal.colonyjournal.colony", this.report.colonyName, this.report.citizens, this.report.citizenLimit);
      g.drawString(this.font, stat, this.left() + this.panelWidth() - this.font.width(stat), 8, 0xFFCCCCCC, true);
      g.fill(this.left() - 2, this.top() - 2, this.left() + this.panelWidth() + 2, this.top() + this.panelHeight() + 2, 0x99000000);
      switch (this.tab) {
         case MAP -> this.renderMap(g, mouseX, mouseY);
         case PROBLEMS -> this.renderIssues(g, mouseX, mouseY);
         case BUILDINGS -> this.renderBuildings(g);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      }

      if (this.tab == Tab.MAP && button == 0) {
         List<String> visible = this.visibleNodes();

         for (String id : visible) {
            int[] pos = this.nodePos(visible, id);
            if (mouseX >= pos[0] && mouseX < pos[0] + NODE_W && mouseY >= pos[1] && mouseY < pos[1] + NODE_H) {
               this.selected = id.equals(this.selected) ? null : id;
               return true;
            }
         }

         this.dragging = mouseY < this.top() + this.panelHeight() - 54;
         return true;
      } else if (this.tab == Tab.PROBLEMS && button == 0) {
         int cursor = this.top() + 4 - this.scroll;

         for (Issue issue : this.report.issues) {
            int height = this.font.split(issue.text(), this.panelWidth() - 20).size() * 10 + 4;
            if (mouseY >= cursor && mouseY < cursor + height && issue.focus() != null && BuildingGraph.NODES.containsKey(issue.focus())) {
               this.selected = issue.focus();
               this.tab = Tab.MAP;
               this.rebuildWidgets();
               return true;
            }

            cursor += height + 3;
         }
      }

      return false;
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.tab == Tab.MAP && this.dragging) {
         this.panX += dragX;
         this.panY += dragY;
         return true;
      }

      return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.dragging = false;
      return super.mouseReleased(mouseX, mouseY, button);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.tab == Tab.MAP) {
         this.panY += scrollY * 14.0;
      } else {
         int content = this.tab == Tab.PROBLEMS ? this.issuesHeight() : this.report.rows.size() * 16 + 8;
         int max = Math.max(0, content - this.panelHeight());
         this.scroll = Math.max(0, Math.min(max, this.scroll - (int)(scrollY * 18.0)));
      }

      return true;
   }

   @Override
   public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
      this.renderTransparentBackground(g);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }
}
