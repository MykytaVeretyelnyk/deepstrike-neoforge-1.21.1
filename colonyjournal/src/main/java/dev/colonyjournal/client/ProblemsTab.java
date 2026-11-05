package dev.colonyjournal.client;

import dev.colonyjournal.analysis.ColonyReport.Issue;
import dev.colonyjournal.analysis.ColonyReport.Severity;
import dev.colonyjournal.graph.BuildingGraph;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The list of problems found in the colony. A click on a row shows the guilty building type on the map. */
final class ProblemsTab extends JournalTab {
   private static final int GAP = 3;
   private static final ItemStack ICON = new ItemStack(Items.REDSTONE_TORCH);
   private static final ItemStack ERROR = new ItemStack(Items.BARRIER);
   private static final ItemStack WARN = new ItemStack(Items.GLOWSTONE_DUST);
   private static final ItemStack INFO = new ItemStack(Items.LIGHT_BLUE_DYE);

   private record RowData(Issue issue, List<FormattedCharSequence> lines, int height) {
   }

   private final Scroller scroller = new Scroller();
   private List<RowData> rows = List.of();
   private boolean dirty = true;
   private Issue hoverIssue;

   ProblemsTab(JournalScreen screen) {
      super(screen);
   }

   @Override
   ItemStack icon() {
      return ICON;
   }

   @Override
   String key() {
      return "problems";
   }

   @Override
   void init() {
      this.dirty = true;
   }

   @Override
   void onReport() {
      this.dirty = true;
   }

   private int listX() {
      return this.cx() + 3;
   }

   private int listW() {
      return this.cw() - 6 - 6;
   }

   private int textW() {
      return this.listW() - 28 - 26;
   }

   private void ensure() {
      if (!this.dirty) {
         return;
      }

      this.dirty = false;
      List<RowData> list = new ArrayList<>();
      int total = 4;

      for (Issue issue : this.screen.report().issues) {
         List<FormattedCharSequence> lines = this.font().split(issue.text(), this.textW());
         int h = Math.max(24, lines.size() * 10 + 8);
         list.add(new RowData(issue, lines, h));
         total += h + GAP;
      }

      this.rows = list;
      this.scroller.setSize(total, this.ch());
   }

   private static ItemStack severityIcon(Severity severity) {
      return switch (severity) {
         case ERROR -> ERROR;
         case WARN -> WARN;
         case INFO -> INFO;
      };
   }

   private static int severityColor(Severity severity) {
      return switch (severity) {
         case ERROR -> 0xFFE05050;
         case WARN -> 0xFFE0C040;
         case INFO -> 0xFF8AB4D8;
      };
   }

   private static boolean focusable(Issue issue) {
      return BuildingGraph.onMap(issue.focus());
   }

   /** Index of the row under the mouse, or -1. Walks the rows exactly like {@link #render}. */
   private int rowAt(double mx, double my) {
      if (!Ui.in(mx, my, this.cx(), this.cy(), this.cw(), this.ch())) {
         return -1;
      }

      int y = this.cy() + 4 - this.scroller.offset;

      for (int i = 0; i < this.rows.size(); i++) {
         int h = this.rows.get(i).height();
         if (my >= y && my < y + h && mx >= this.listX() && mx < this.listX() + this.listW()) {
            return i;
         }

         y += h + GAP;
      }

      return -1;
   }

   @Override
   void render(GuiGraphics g, int mx, int my) {
      this.ensure();
      this.hoverIssue = null;
      int x = this.listX();
      int w = this.listW();
      int y = this.cy() + 4 - this.scroller.offset;
      if (this.rows.isEmpty()) {
         int ey = this.cy() + 8;

         for (FormattedCharSequence line : this.font().split(Component.translatable("journal.colonyjournal.all_good"), w - 8)) {
            g.drawString(this.font(), line, x + 4, ey, 0xFF55CC66, true);
            ey += 10;
         }
      }

      int hover = this.rowAt(mx, my);

      for (int i = 0; i < this.rows.size(); i++) {
         RowData row = this.rows.get(i);
         Issue issue = row.issue();
         int h = row.height();
         if (y + h >= this.cy() && y <= this.cy() + this.ch()) {
            boolean focusable = focusable(issue);
            g.fill(x, y, x + w, y + h, i == hover && focusable ? 0x88404040 : 0x77000000);
            g.renderOutline(x, y, w, h, 0xFF000000);
            g.fill(x, y, x + 3, y + h, severityColor(issue.severity()));
            g.renderItem(severityIcon(issue.severity()), x + 7, y + (h - 16) / 2);
            int ty = y + (h - row.lines().size() * 10) / 2 + 1;

            for (FormattedCharSequence line : row.lines()) {
               g.drawString(this.font(), line, x + 28, ty, 0xFFFFFFFF, true);
               ty += 10;
            }

            if (focusable) {
               g.renderItem(BuildingIcons.of(issue.focus()), x + w - 22, y + (h - 16) / 2);
            }
         }

         if (i == hover) {
            this.hoverIssue = issue;
         }

         y += h + GAP;
      }

      this.scroller.renderBar(g, this.cx() + this.cw() - 5, this.cy(), this.ch());
   }

   @Override
   void renderTooltips(GuiGraphics g, int mx, int my) {
      Issue issue = this.hoverIssue;
      if (issue != null) {
         List<Component> lines = new ArrayList<>();
         lines.add(Component.translatable("journal.colonyjournal.severity." + issue.severity().name().toLowerCase(java.util.Locale.ROOT)).withStyle(style -> style.withColor(severityColor(issue.severity()) & 0xFFFFFF)));
         if (focusable(issue)) {
            lines.add(Component.translatable("journal.colonyjournal.click_to_map").withStyle(net.minecraft.ChatFormatting.GRAY));
         }

         g.renderComponentTooltip(this.font(), lines, mx, my);
      }
   }

   @Override
   boolean mouseClicked(double mx, double my, int button) {
      if (button != 0) {
         return false;
      }

      this.ensure();
      int index = this.rowAt(mx, my);
      if (index >= 0 && focusable(this.rows.get(index).issue())) {
         Ui.click();
         this.screen.showOnMap(this.rows.get(index).issue().focus(), this.rows.get(index).issue().partner());
         return true;
      }

      return false;
   }

   @Override
   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      this.scroller.wheel(sy);
      return true;
   }
}
