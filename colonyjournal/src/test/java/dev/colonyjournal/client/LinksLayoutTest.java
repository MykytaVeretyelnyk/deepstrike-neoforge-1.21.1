package dev.colonyjournal.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.client.LinksFrame.Rect;
import dev.colonyjournal.graph.BuildingGraph;
import dev.colonyjournal.graph.ChainLayout.Seg;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The pure helpers of the Links tab: frame rectangles for every window size, text fitting and dashes. */
class LinksLayoutTest {
   private static final String NL = System.lineSeparator();

   private static int width(String s) {
      return s.length() * 6;
   }

   private static boolean inside(Rect r, int w, int h) {
      return r.x() >= 0 && r.y() >= 0 && r.x() + r.w() <= w && r.y() + r.h() <= h;
   }

   private static boolean overlap(Rect a, Rect b) {
      return !a.empty() && !b.empty() && a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
   }

   @Test
   void theRectanglesFitEveryWindow() {
      // content sizes from the smallest window (160x100) to the biggest, at any GUI scale
      for (int w = 142; w <= 402; w += 20) {
         for (int h = 55; h <= 221; h += 11) {
            LinksFrame f = LinksFrame.of(w, h, 9);
            String at = w + "x" + h;
            assertTrue(inside(f.strip, w, h) && inside(f.view, w, h) && inside(f.detail, w, h), at);
            assertFalse(overlap(f.strip, f.view) || overlap(f.view, f.detail) || overlap(f.strip, f.detail), at);
            assertTrue(f.view.h() >= 28, "the diagram needs room at " + at + ": " + f.view.h());
            assertTrue(f.chipW >= LinksFrame.CHIP_MIN && f.chipW <= LinksFrame.CHIP_MAX, at);
            assertTrue(f.detail.empty() || f.detailLines >= 1, at);
         }
      }
   }

   private static LinksFrame.CardFit fitIn(LinksFrame f, BuildingGraph.Chain chain) {
      return LinksFrame.fitCards(chain.members(), chain.edges(), f.innerW(), f.innerH(), e -> e.need().length() * 6 + 4);
   }

   @Test
   void chainsFitTheRealViewWithoutScrolling() {
      // GUI scale 4 on 1080p: 402x187 content; the biggest window: 402x221
      LinksFrame small = LinksFrame.of(402, 187, BuildingGraph.CHAINS.size());
      assertEquals(22, small.strip.h());
      assertEquals(28, small.detail.h());
      assertEquals(133, small.view.h());
      for (String id : List.of("metal", "wood", "crops", "dyes", "stone", "meat", "basics", "other")) {
         LinksFrame.CardFit fit = fitIn(small, BuildingGraph.chain(id));
         assertTrue(fit.fits(small.innerW(), small.innerH()), id + " must fit 402x187 without scrolling: " + fit.result().width() + "x" + fit.result().height() + " in " + small.innerW() + "x" + small.innerH());
      }

      // the one chain with a 1-to-7 fan-out needs at most a short scroll
      LinksFrame.CardFit tools = fitIn(small, BuildingGraph.chain("tools"));
      assertTrue(tools.result().width() <= small.innerW());
      assertTrue(tools.result().height() - small.innerH() <= 40, "tools scrolls by " + (tools.result().height() - small.innerH()));

      LinksFrame big = LinksFrame.of(402, 221, BuildingGraph.CHAINS.size());
      for (BuildingGraph.Chain chain : BuildingGraph.CHAINS) {
         LinksFrame.CardFit fit = fitIn(big, chain);
         assertTrue(fit.fits(big.innerW(), big.innerH()), chain.id() + " must fit 402x221");
      }

      LinksFrame tiny = LinksFrame.of(300, 150, BuildingGraph.CHAINS.size());
      for (String id : List.of("metal", "wood", "basics")) {
         assertTrue(fitIn(tiny, BuildingGraph.chain(id)).fits(tiny.innerW(), tiny.innerH()), id + " must fit 300x150");
      }

      for (BuildingGraph.Chain chain : BuildingGraph.CHAINS) {
         assertTrue(fitIn(tiny, chain).result().width() <= tiny.innerW() + 40, chain.id() + " must not need sideways scrolling at 300x150");
      }
   }

   @Test
   void oneRuleForBasicBuildings() {
      for (String id : BuildingGraph.BASICS) {
         assertTrue(BuildingGraph.onMap(id), id);
         assertTrue(OverviewModel.onMap(id), id);
      }

      assertTrue(BuildingGraph.onMap("miner"));
      assertFalse(BuildingGraph.onMap("stash"));
      assertFalse(BuildingGraph.onMap(null));
   }

   @Test
   void aBrokenLinkOpensTheChainThatDrawsIt() {
      // blacksmith to builder is drawn in the tools chain only, although the blacksmith is also in the metal chain
      List<Integer> both = BuildingGraph.chainsOfLink("blacksmith", "builder");
      assertEquals(List.of(BuildingGraph.CHAINS.indexOf(BuildingGraph.chain("tools"))), both);
      assertTrue(BuildingGraph.chainsOf("blacksmith").size() > 1);
      // without a drawn link the producer's chains are used
      assertEquals(BuildingGraph.chainsOf("miner"), BuildingGraph.chainsOfLink("miner", "townhall"));
   }

   @Test
   void chipsAreReachableAndNeverOverlap() {
      for (int w : new int[] {142, 200, 300, 402}) {
         LinksFrame f = LinksFrame.of(w, 200, 9);
         for (int scroll = 0; scroll <= f.maxScroll(); scroll += 7) {
            for (int i = 0; i < 9; i++) {
               int x = f.chipX(i, scroll);
               if (i > 0) {
                  assertEquals(f.chipW, x - f.chipX(i - 1, scroll));
               }
            }
         }

         for (int i = 0; i < 9; i++) {
            int scroll = f.scrollToShow(i, 0);
            int x = f.chipX(i, scroll);
            assertTrue(x >= f.clipX() && x + f.chipW <= f.clipX() + f.clipW() + 0, "chip " + i + " visible at width " + w);
            assertEquals(i, f.chipAt(x + f.chipW / 2.0, f.chipH / 2.0, scroll));
         }

         assertEquals(w < 9 * 20 ? true : false, f.scrolls);
         if (f.scrolls) {
            assertEquals(-1, f.chipAt(1, 1, 0), "the arrow area is not a chip");
         }
      }
   }

   @Test
   void theDiagramIsCentredOrClamped() {
      assertEquals(50, LinksFrame.place(200, 100, 0));
      assertEquals(LinksFrame.PAD, LinksFrame.place(100, 300, 500));
      assertEquals(100 - 300 - LinksFrame.PAD, LinksFrame.place(100, 300, -500));
      double pan = LinksFrame.panFor(100, 120, 20);
      assertEquals(50.0, pan + 120 + 10, 0.001);
   }

   @Test
   void namesWrapIntoTwoLinesWithAnEllipsis() {
      assertEquals(List.of("Lumberjack's", "Hut"), TextFit.wrap("Lumberjack's Hut", 12 * 6, 2, LinksLayoutTest::width));
      assertEquals(List.of("Hut"), TextFit.wrap("Hut", 60, 2, LinksLayoutTest::width));
      List<String> cut = TextFit.wrap("Chicken Farmer Supply Hut Number Two", 60, 2, LinksLayoutTest::width);
      assertEquals(2, cut.size());
      assertTrue(cut.get(1).endsWith(TextFit.ELLIPSIS));
      for (String line : cut) {
         assertTrue(width(line) <= 60, line);
      }

      List<String> word = TextFit.wrap("Supercalifragilisticexpialidocious", 60, 2, LinksLayoutTest::width);
      for (String line : word) {
         assertTrue(width(line) <= 60, line);
      }

      assertEquals("abcdefghi" + TextFit.ELLIPSIS, TextFit.ellipsize("abcdefghijklmnop", 60, LinksLayoutTest::width).substring(0, 10));
      assertTrue(TextFit.wrap("anything", 60, 0, LinksLayoutTest::width).isEmpty());
   }

   @Test
   void dashesCoverTheLineInPieces() {
      List<Seg> dashes = Dashes.split(new Seg(10, 5, 40, 5));
      assertEquals(new Seg(10, 5, 13, 5), dashes.get(0));
      assertEquals(new Seg(17, 5, 20, 5), dashes.get(1));
      for (Seg dash : dashes) {
         assertTrue(dash.horizontal() && dash.x1() >= 10 && dash.x2() <= 40);
      }

      List<Seg> vertical = Dashes.split(new Seg(3, 0, 3, 9));
      assertEquals(new Seg(3, 0, 3, 3), vertical.get(0));
      assertFalse(vertical.get(vertical.size() - 1).horizontal());
   }
}
