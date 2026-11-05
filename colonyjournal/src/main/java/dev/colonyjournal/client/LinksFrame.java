package dev.colonyjournal.client;

/**
 * The rectangles of the Links tab inside its content area: the strip of chain chips, the diagram view and the
 * "what to do" panel (two text lines, one in a small window, none in a tiny one). No Minecraft classes, so every window size can be unit tested. Coordinates start at the top
 * left corner of the content area; the tab adds its origin. Drawing and mouse hit-testing both call these methods.
 */
final class LinksFrame {
   /** Card widths tried from roomy to compact; the first that lets the chain fit the view is used. */
   static final int[] NODE_WIDTHS = {96, 80, 64, 56};
   static final int NODE_H = 28;
   static final int ARROW_W = 9;
   static final int CHIP_MIN = 20;
   static final int CHIP_MAX = 64;
   /** From this chip width on a chip also shows the "working/total" numbers. */
   static final int CHIP_TEXT = 44;
   static final int PAD = 3;

   record Rect(int x, int y, int w, int h) {
      boolean contains(double px, double py) {
         return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
      }

      boolean empty() {
         return this.w <= 0 || this.h <= 0;
      }
   }

   final int width;
   final int height;
   final int chips;
   final int chipW;
   final int chipH;
   /** True when the chips do not fit and the strip scrolls between two arrows. */
   final boolean scrolls;
   final Rect strip;
   final Rect view;
   final Rect detail;
   /** How many text lines the detail panel holds. */
   final int detailLines;

   private LinksFrame(int width, int height, int chips) {
      this.width = width;
      this.height = height;
      this.chips = chips;
      this.chipH = height < 80 ? 20 : 22;
      int n = Math.max(1, chips);
      int w = Math.max(CHIP_MIN, Math.min(CHIP_MAX, width / n));
      this.chipW = w;
      this.scrolls = w * n > width;
      this.strip = new Rect(0, 0, width, this.chipH);
      int detailH = height >= 150 ? 28 : (height >= 100 ? 17 : 0);
      int top = this.chipH + 2;
      if (height - top - (detailH > 0 ? detailH + 2 : 0) < 30) {
         detailH = 0;
      }

      int viewH = height - top - (detailH > 0 ? detailH + 2 : 0);
      this.view = new Rect(0, top, width, Math.max(1, viewH));
      this.detail = new Rect(0, height - detailH, width, detailH);
      this.detailLines = detailH == 0 ? 0 : Math.max(1, (detailH - 6) / 11);
   }

   static LinksFrame of(int width, int height, int chips) {
      return new LinksFrame(width, height, chips);
   }

   /** A chain laid out with the card width that fits the view best. */
   record CardFit(dev.colonyjournal.graph.ChainLayout.Result result, int cardW) {
      boolean fits(int viewW, int viewH) {
         return this.result.width() <= viewW && this.result.height() <= viewH;
      }
   }

   /** The diagram area inside the view rectangle, where the PAD margin is kept free. */
   int innerW() {
      return this.view.w() - 2 * PAD;
   }

   int innerH() {
      return this.view.h() - 2 * PAD;
   }

   static CardFit fitCards(java.util.List<String> members, java.util.List<dev.colonyjournal.graph.BuildingGraph.Edge> edges, int viewW, int viewH, java.util.function.ToIntFunction<dev.colonyjournal.graph.BuildingGraph.Edge> label) {
      CardFit best = null;
      long bestScore = Long.MAX_VALUE;

      for (int w : NODE_WIDTHS) {
         dev.colonyjournal.graph.ChainLayout.Result r = dev.colonyjournal.graph.ChainLayout.fit(members, edges, w, NODE_H, viewW, viewH, label);
         long score = Math.max(0L, r.width() - viewW) * 1000L + Math.max(0L, r.height() - viewH);
         if (score < bestScore) {
            bestScore = score;
            best = new CardFit(r, w);
         }

         if (score == 0L) {
            break;
         }
      }

      return best;
   }

   // ------------------------------------------------------------------ chips

   /** The width the chips scroll in: the strip without the two arrows. */
   int clipX() {
      return this.scrolls ? ARROW_W : 0;
   }

   int clipW() {
      return this.scrolls ? this.width - 2 * ARROW_W : this.width;
   }

   int maxScroll() {
      return Math.max(0, this.chipW * Math.max(1, this.chips) - this.clipW());
   }

   int clampScroll(int scroll) {
      return Math.max(0, Math.min(this.maxScroll(), scroll));
   }

   /** The chip row is centred when everything fits and starts at the left when it scrolls. */
   int chipX(int index, int scroll) {
      int lead = this.scrolls ? 0 : (this.width - this.chipW * this.chips) / 2;
      return this.clipX() + lead + index * this.chipW - this.clampScroll(scroll);
   }

   /** The scroll offset that brings the chip into view, starting from the current offset. */
   int scrollToShow(int index, int scroll) {
      int left = index * this.chipW;
      int s = this.clampScroll(scroll);
      if (left < s) {
         s = left;
      } else if (left + this.chipW > s + this.clipW()) {
         s = left + this.chipW - this.clipW();
      }

      return this.clampScroll(s);
   }

   Rect leftArrow() {
      return new Rect(0, 0, ARROW_W, this.chipH);
   }

   Rect rightArrow() {
      return new Rect(this.width - ARROW_W, 0, ARROW_W, this.chipH);
   }

   /** The chip at the point (content coordinates), or -1. Chips cut off by the strip clip cannot be hit. */
   int chipAt(double mx, double my, int scroll) {
      if (!this.strip.contains(mx, my) || mx < this.clipX() || mx >= this.clipX() + this.clipW()) {
         return -1;
      }

      for (int i = 0; i < this.chips; i++) {
         if (new Rect(this.chipX(i, scroll), 0, this.chipW, this.chipH).contains(mx, my)) {
            return i;
         }
      }

      return -1;
   }

   // ------------------------------------------------------------------ diagram placement

   /**
    * Where the diagram starts along one axis: centred when it fits into the view, else the pan offset limited so the
    * diagram never leaves the view.
    */
   static int place(int viewLen, int contentLen, double pan) {
      if (contentLen + 2 * PAD <= viewLen) {
         return (viewLen - contentLen) / 2;
      }

      return (int)Math.round(Math.max(viewLen - contentLen - PAD, Math.min(PAD, pan)));
   }

   /** The pan value that puts the middle of [start, start + len) into the middle of the view. */
   static double panFor(int viewLen, int start, int len) {
      return viewLen / 2.0 - start - len / 2.0;
   }
}
