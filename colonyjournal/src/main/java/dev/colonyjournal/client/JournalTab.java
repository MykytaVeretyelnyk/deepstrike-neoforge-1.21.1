package dev.colonyjournal.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One page of the journal. The screen owns the window and calls the tab with the content rectangle. */
abstract class JournalTab {
   protected final JournalScreen screen;

   protected JournalTab(JournalScreen screen) {
      this.screen = screen;
   }

   /** Item shown on the tab. */
   abstract ItemStack icon();

   /** Lang key suffix: {@code journal.colonyjournal.tab.<key>}. */
   abstract String key();

   Component title() {
      return Component.translatable("journal.colonyjournal.tab." + this.key());
   }

   /** One plain line under the tab strip: what this tab shows. */
   Component subtitle() {
      return Component.translatable("journal.colonyjournal.sub." + this.key());
   }

   /** The guide section the "?" button of this tab opens. */
   String guideSection() {
      return GuideText.sectionFor(this.key());
   }

   /** False for the guide itself, which needs no "?" button. */
   boolean showHelp() {
      return true;
   }

   /** True when the tab draws its own background instead of the plain darkened stone. */
   boolean ownBackground() {
      return false;
   }

   /** True when the tab shows the action message itself, so the screen does not add a toast. */
   boolean ownMessageLine() {
      return false;
   }

   /** Called on every layout (open, resize): create widgets here. */
   void init() {
   }

   /** Called when the tab becomes visible. */
   void onOpen() {
   }

   /** Called when the colony report was rebuilt. */
   void onReport() {
   }

   void tick() {
   }

   /** Period in ticks of the server refresh while the tab is visible, 0 for none. */
   int refreshInterval() {
      return 0;
   }

   void requestRefresh() {
   }

   /** Draws the content inside the content rectangle. Hover results are remembered for {@link #renderTooltips}. */
   abstract void render(GuiGraphics g, int mx, int my);

   void renderTooltips(GuiGraphics g, int mx, int my) {
   }

   boolean mouseClicked(double mx, double my, int button) {
      return false;
   }

   boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
      return false;
   }

   boolean mouseReleased(double mx, double my, int button) {
      return false;
   }

   boolean mouseScrolled(double mx, double my, double sx, double sy) {
      return false;
   }

   final int cx() {
      return this.screen.contentX();
   }

   final int cy() {
      return this.screen.contentY();
   }

   final int cw() {
      return this.screen.contentW();
   }

   final int ch() {
      return this.screen.contentH();
   }

   final Font font() {
      return this.screen.font();
   }
}
