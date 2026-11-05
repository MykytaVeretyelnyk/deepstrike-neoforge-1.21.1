package dev.colonyjournal.client;

import dev.colonyjournal.graph.ChainLayout.Seg;
import java.util.ArrayList;
import java.util.List;

/** Cuts a horizontal or vertical piece of a line into dashes; pure, so the dash pattern can be unit tested. */
final class Dashes {
   static final int ON = 4;
   static final int OFF = 3;

   private Dashes() {
   }

   /** The dashes of a piece, starting at its top or left end; each dash is a Seg of at least one pixel. */
   static List<Seg> split(Seg seg) {
      List<Seg> out = new ArrayList<>();
      boolean horizontal = seg.horizontal();
      int from = horizontal ? seg.x1() : seg.y1();
      int to = horizontal ? seg.x2() : seg.y2();

      for (int p = from; p <= to; p += ON + OFF) {
         int end = Math.min(to, p + ON - 1);
         out.add(horizontal ? new Seg(p, seg.y1(), end, seg.y1()) : new Seg(seg.x1(), p, seg.x1(), end));
      }

      return out;
   }
}
