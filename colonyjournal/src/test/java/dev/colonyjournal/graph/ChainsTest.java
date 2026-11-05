package dev.colonyjournal.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.graph.BuildingGraph.Chain;
import dev.colonyjournal.graph.BuildingGraph.Edge;
import dev.colonyjournal.graph.ChainAdvice.Advice;
import dev.colonyjournal.graph.ChainHealth.Light;
import dev.colonyjournal.graph.ChainLayout.Box;
import dev.colonyjournal.graph.ChainLayout.Label;
import dev.colonyjournal.graph.ChainLayout.Result;
import dev.colonyjournal.graph.ChainLayout.Route;
import dev.colonyjournal.graph.ChainLayout.Seg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

/** The chain table, the layout invariants of every chain, the health colours and the chain selection. */
class ChainsTest {
   private static final int NODE_W = 64;
   private static final int NODE_H = 28;
   private static final ToIntFunction<Edge> LABEL = edge -> edge.need().length() * 6 + 4;
   /** Window-like view sizes: huge, a normal window, a small window with scrolling. */
   private static final int[][] VIEWS = {{100000, 100000}, {400, 130}, {300, 90}, {140, 40}};

   // ------------------------------------------------------------------ the table

   @Test
   void everyNodeAndEdgeIsInAChain() {
      Set<String> members = new HashSet<>();
      Set<Edge> edges = new HashSet<>();

      for (Chain chain : BuildingGraph.CHAINS) {
         members.addAll(chain.members());
         edges.addAll(chain.edges());
      }

      for (String id : BuildingGraph.NODES.keySet()) {
         assertTrue(members.contains(id), "node in no chain: " + id);
      }

      for (Edge edge : BuildingGraph.EDGES) {
         assertTrue(edges.contains(edge), "edge in no chain: " + edge);
      }

      for (String id : BuildingGraph.BASICS) {
         assertTrue(members.contains(id), "basic building in no chain: " + id);
      }
   }

   @Test
   void chainMembersExistAndAreTidy() {
      Set<String> ids = new HashSet<>();

      for (Chain chain : BuildingGraph.CHAINS) {
         assertTrue(ids.add(chain.id()), "duplicate chain id " + chain.id());
         assertEquals(new HashSet<>(chain.members()).size(), chain.members().size(), "duplicate member in " + chain.id());
         assertTrue(chain.members().contains(chain.icon()) || BuildingGraph.NODES.containsKey(chain.icon()) || BuildingGraph.BASICS.contains(chain.icon()), "icon of " + chain.id());
         if (!chain.plain()) {
            assertTrue(chain.members().size() <= 12, chain.id() + " is too big to read: " + chain.members().size());
         }

         for (String member : chain.members()) {
            assertTrue(BuildingGraph.NODES.containsKey(member) || BuildingGraph.BASICS.contains(member), "unknown member " + member + " in " + chain.id());
         }

         for (String skip : chain.skip()) {
            boolean found = BuildingGraph.EDGES.stream().anyMatch(e -> (e.from() + ">" + e.to()).equals(skip));
            assertTrue(found, "skip names no edge: " + skip);
         }
      }
   }

   // ------------------------------------------------------------------ layout invariants

   @Test
   void everyChainLaysOutCleanlyInEveryWindow() {
      for (Chain chain : BuildingGraph.CHAINS) {
         for (int[] view : VIEWS) {
            Result r = ChainLayout.fit(chain.members(), chain.edges(), NODE_W, NODE_H, view[0], view[1], LABEL);
            check(chain.id() + " in " + view[0] + "x" + view[1], chain.members(), chain.edges(), r);
            assertTrue(r.dropped().isEmpty(), chain.id() + " has a loop");
         }
      }
   }

   @Test
   void generousViewsAreUsedAndTightViewsShrinkTheSpacing() {
      Chain stone = BuildingGraph.chain("stone");
      Result roomy = ChainLayout.fit(stone.members(), stone.edges(), NODE_W, NODE_H, 100000, 100000, LABEL);
      Result tight = ChainLayout.fit(stone.members(), stone.edges(), NODE_W, NODE_H, 380, 100000, LABEL);
      assertTrue(tight.width() <= 380 || tight.width() < roomy.width(), "the spacing must shrink: " + tight.width() + " vs " + roomy.width());
      assertTrue(tight.width() <= roomy.width());
   }

   @Test
   void randomGraphsKeepTheInvariants() {
      Random random = new Random(20240607L);

      for (int round = 0; round < 500; round++) {
         int n = 2 + random.nextInt(11);
         List<String> ids = new ArrayList<>();
         for (int i = 0; i < n; i++) {
            ids.add("n" + i);
         }

         List<Edge> edges = new ArrayList<>();
         Set<String> seen = new HashSet<>();
         int count = random.nextInt(n * 3 + 1);
         for (int k = 0; k < count; k++) {
            int a = random.nextInt(n);
            int b = random.nextInt(n);
            // mostly forward edges, now and then a back edge that closes a loop
            if (a > b && random.nextInt(6) != 0) {
               int t = a;
               a = b;
               b = t;
            }

            if (a != b && seen.add(a + ">" + b)) {
               edges.add(new Edge(ids.get(a), ids.get(b), "ores", random.nextBoolean()));
            }
         }

         for (int[] view : new int[][] {{100000, 100000}, {300, 120}}) {
            Result r = ChainLayout.fit(ids, edges, NODE_W, NODE_H, view[0], view[1], LABEL);
            List<Edge> drawn = new ArrayList<>(edges);
            drawn.removeAll(r.dropped());
            check("random " + round + " " + edges, ids, drawn, r);
         }
      }
   }

   private static void check(String what, List<String> members, List<Edge> edges, Result r) {
      assertEquals(members.size(), r.boxes().size(), what + ": every building is placed");
      for (String id : members) {
         assertNotNull(r.byId().get(id), what + ": missing " + id);
      }

      List<Box> boxes = r.boxes();
      for (int i = 0; i < boxes.size(); i++) {
         Box a = boxes.get(i);
         assertTrue(a.x() >= 0 && a.y() >= 0 && a.right() <= r.width() && a.bottom() <= r.height(), what + ": " + a.id() + " outside the diagram");
         for (int j = i + 1; j < boxes.size(); j++) {
            Box b = boxes.get(j);
            boolean overlap = a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
            assertFalse(overlap, what + ": " + a.id() + " overlaps " + b.id());
         }
      }

      assertEquals(edges.size(), r.routes().size(), what + ": every edge has a route");
      for (Route route : r.routes()) {
         Box from = r.byId().get(route.edge().from());
         Box to = r.byId().get(route.edge().to());
         assertTrue(from.layer() < to.layer(), what + ": " + route.edge() + " must go from a lower to a higher layer");
         assertEquals(from.layer(), route.fromLayer());
         assertEquals(to.layer(), route.toLayer());
         int[] p = route.points();
         assertEquals(from.right(), p[0], what + ": " + route.edge() + " starts at the right side of the producer");
         assertTrue(p[1] > from.y() && p[1] < from.bottom(), what + ": start port inside the producer side");
         assertEquals(to.x(), route.endX(), what + ": " + route.edge() + " ends at the left side of the consumer");
         assertTrue(route.endY() > to.y() && route.endY() < to.bottom(), what + ": end port inside the consumer side");
         assertTrue(route.endX() - p[p.length - 4] >= ChainLayout.ARROW, what + ": room for the arrow head on " + route.edge());
         for (int i = 0; i + 3 < p.length; i += 2) {
            assertTrue(p[i] == p[i + 2] || p[i + 1] == p[i + 3], what + ": " + route.edge() + " has a diagonal piece");
         }

         for (Seg seg : route.segs()) {
            assertTrue(seg.x1() >= 0 && seg.y1() >= 0 && seg.x2() <= r.width() && seg.y2() <= r.height(), what + ": " + route.edge() + " leaves the diagram");
            for (Box box : boxes) {
               assertFalse(seg.crossesInside(box.x(), box.y(), box.w(), box.h()), what + ": " + route.edge() + " runs through " + box.id());
            }
         }
      }

      List<Route> routes = r.routes();
      for (int i = 0; i < routes.size(); i++) {
         for (int j = i + 1; j < routes.size(); j++) {
            for (Seg a : routes.get(i).segs()) {
               for (Seg b : routes.get(j).segs()) {
                  assertFalse(a.sharesLine(b), what + ": " + routes.get(i).edge() + " and " + routes.get(j).edge() + " share a stretch " + a + " " + b);
               }
            }
         }
      }

      for (int i = 0; i < routes.size(); i++) {
         Label label = routes.get(i).label();
         if (label == null) {
            continue;
         }

         assertEquals(LABEL.applyAsInt(routes.get(i).edge()), label.w());
         for (Box box : boxes) {
            assertFalse(label.x() < box.right() && label.x() + label.w() > box.x() && label.y() < box.bottom() && label.y() + label.h() > box.y(), what + ": label over " + box.id());
         }

         for (int j = 0; j < routes.size(); j++) {
            for (Seg seg : routes.get(j).segs()) {
               if (i == j && seg.horizontal() && seg.y1() == label.y() + label.h() / 2) {
                  continue;
               }

               assertFalse(seg.touches(label.x(), label.y(), label.w(), label.h(), 0), what + ": label of " + routes.get(i).edge() + " touches " + routes.get(j).edge());
            }
         }
      }
   }

   @Test
   void plainChainsAreAGridWithoutOverlap() {
      for (Chain chain : BuildingGraph.CHAINS) {
         if (chain.plain()) {
            for (int[] view : VIEWS) {
               Result r = ChainLayout.fit(chain.members(), chain.edges(), NODE_W, NODE_H, view[0], view[1], LABEL);
               check(chain.id(), chain.members(), List.of(), r);
               assertTrue(r.routes().isEmpty());
            }
         }
      }
   }

   @Test
   void sourcesLeftSinksRight() {
      Chain metal = BuildingGraph.chain("metal");
      Result r = ChainLayout.fit(metal.members(), metal.edges(), NODE_W, NODE_H, 100000, 100000, LABEL);
      assertEquals(0, r.byId().get("miner").layer());
      assertEquals(1, r.byId().get("smeltery").layer());
      assertEquals(2, r.byId().get("blacksmith").layer());
      assertEquals(2, r.byId().get("mechanic").layer());
   }

   @Test
   void longEdgesRouteAroundTheMiddleLayers() {
      Chain stone = BuildingGraph.chain("stone");
      Result r = ChainLayout.fit(stone.members(), stone.edges(), NODE_W, NODE_H, 100000, 100000, LABEL);
      Route masonry = r.routes().stream().filter(x -> x.edge().from().equals("stonemason") && x.edge().to().equals("builder")).findFirst().orElseThrow();
      assertTrue(masonry.toLayer() - masonry.fromLayer() > 1, "the stone mason is not next to the builder");
      assertTrue(masonry.segs().size() >= 1);
      for (Box box : r.boxes()) {
         for (Seg seg : masonry.segs()) {
            assertFalse(seg.crossesInside(box.x(), box.y(), box.w(), box.h()));
         }
      }
   }

   @Test
   void loopsAreBrokenInsteadOfLooping() {
      List<Edge> edges = List.of(new Edge("a", "b", "ores", false), new Edge("b", "c", "ores", false), new Edge("c", "a", "ores", false));
      Result r = ChainLayout.fit(List.of("a", "b", "c"), edges, NODE_W, NODE_H, 1000, 1000, LABEL);
      assertEquals(1, r.dropped().size());
      assertEquals(2, r.routes().size());
   }

   // ------------------------------------------------------------------ health and selection

   private static Function<String, Status> statuses(Status fallback, Object... pairs) {
      Map<String, Status> map = new HashMap<>();
      for (int i = 0; i < pairs.length; i += 2) {
         map.put((String)pairs[i], (Status)pairs[i + 1]);
      }

      return id -> map.getOrDefault(id, fallback);
   }

   private static Function<String, Status> allOk() {
      Map<String, Status> map = new HashMap<>();
      for (String id : BuildingGraph.NODES.keySet()) {
         map.put(id, Status.OK);
      }

      for (String id : BuildingGraph.BASICS) {
         map.put(id, Status.OK);
      }

      return id -> map.getOrDefault(id, Status.OK);
   }

   private static int index(String id) {
      return BuildingGraph.CHAINS.indexOf(BuildingGraph.chain(id));
   }

   @Test
   void aNewColonyStartsAtTheBasics() {
      assertEquals(index("basics"), ChainHealth.firstWithProblem(id -> Status.ABSENT));
   }

   @Test
   void aHealthyColonyStartsAtTheFirstChain() {
      assertEquals(0, ChainHealth.firstWithProblem(allOk()));
      for (Chain chain : BuildingGraph.CHAINS) {
         assertEquals(Light.GREEN, ChainHealth.summarize(chain, allOk()).light());
      }
   }

   @Test
   void theFirstBrokenChainIsPicked() {
      // everything fine except the smeltery, which the blacksmith and the mechanic wait for
      Function<String, Status> st = id -> id.equals("smeltery") ? Status.ABSENT : Status.OK;
      assertEquals(Light.RED, ChainHealth.summarize(BuildingGraph.chain("metal"), st).light());
      assertEquals(index("metal"), ChainHealth.firstWithProblem(st));
      // a later chain with a problem is found when the earlier ones are fine
      Function<String, Status> later = id -> id.equals("dyer") ? Status.NO_WORKER : Status.OK;
      assertEquals(index("dyes"), ChainHealth.firstWithProblem(later));
   }

   @Test
   void redBeatsAnEarlierYellowAndOptionalLinksAreNotAProblem() {
      Function<String, Status> st = id -> id.equals("library") ? Status.UNBUILT : (id.equals("smeltery") ? Status.ABSENT : Status.OK);
      assertEquals(Light.YELLOW, ChainHealth.summarize(BuildingGraph.chain("other"), st).light());
      assertEquals(index("metal"), ChainHealth.pick(List.of(index("other"), index("metal")), st));
      assertEquals(index("other"), ChainHealth.pick(List.of(index("other"), index("wood")), st));
      // the lumberjack is only optional fuel for the smeltery
      Function<String, Status> noFuel = id -> id.equals("lumberjack") ? Status.ABSENT : Status.OK;
      assertEquals(Light.GREEN, ChainHealth.summarize(BuildingGraph.chain("metal"), noFuel).light());
   }

   @Test
   void everyEdgeStateMatchesTheReportRule() {
      Edge hard = new Edge("a", "b", "ores", false);
      Edge soft = new Edge("a", "b", "ores", true);
      assertEquals(ChainHealth.EdgeState.OK, ChainHealth.edgeState(hard, statuses(Status.OK)));
      assertEquals(ChainHealth.EdgeState.IDLE, ChainHealth.edgeState(hard, statuses(Status.OK, "b", Status.ABSENT)));
      assertEquals(ChainHealth.EdgeState.BROKEN, ChainHealth.edgeState(hard, statuses(Status.OK, "a", Status.NO_WORKER)));
      assertEquals(ChainHealth.EdgeState.OPTIONAL_BROKEN, ChainHealth.edgeState(soft, statuses(Status.OK, "a", Status.UNBUILT)));
   }

   // ------------------------------------------------------------------ advice

   @Test
   void adviceNamesTheMissingProducerAndWhoWaits() {
      Chain metal = BuildingGraph.chain("metal");
      Function<String, Status> st = id -> id.equals("smeltery") ? Status.ABSENT : Status.OK;
      Advice node = ChainAdvice.forNode(metal, "smeltery", st);
      assertEquals(ChainAdvice.ABSENT, node.key());
      assertEquals(2, node.edges().size());
      Advice chain = ChainAdvice.forChain(metal, st);
      assertEquals("smeltery", chain.id());
      assertTrue(chain.problem());
      assertEquals(ChainAdvice.LACKING, ChainAdvice.forNode(metal, "blacksmith", st).key());
   }

   @Test
   void adviceForWorkersAndBasics() {
      Chain metal = BuildingGraph.chain("metal");
      assertEquals(ChainAdvice.NO_WORKER, ChainAdvice.forNode(metal, "blacksmith", statuses(Status.OK, "blacksmith", Status.NO_WORKER)).key());
      assertEquals(ChainAdvice.PARTIAL, ChainAdvice.forNode(metal, "blacksmith", statuses(Status.OK, "blacksmith", Status.PARTIAL)).key());
      assertEquals(ChainAdvice.UNBUILT, ChainAdvice.forNode(metal, "blacksmith", statuses(Status.OK, "blacksmith", Status.UNBUILT)).key());
      assertEquals(ChainAdvice.OK, ChainAdvice.forNode(metal, "blacksmith", allOk()).key());
      Chain basics = BuildingGraph.chain("basics");
      assertEquals(ChainAdvice.BASIC_MISSING, ChainAdvice.forNode(basics, "townhall", statuses(Status.OK, "townhall", Status.ABSENT)).key());
      assertEquals("townhall", ChainAdvice.forChain(basics, statuses(Status.OK, "townhall", Status.ABSENT)).id());
   }

   @Test
   void adviceForWholeChains() {
      Chain wood = BuildingGraph.chain("wood");
      assertEquals(ChainAdvice.CHAIN_OK, ChainAdvice.forChain(wood, allOk()).key());
      assertEquals(ChainAdvice.CHAIN_EMPTY, ChainAdvice.forChain(wood, id -> Status.ABSENT).key());
      assertFalse(ChainAdvice.forChain(wood, allOk()).problem());
      // an absent producer nobody waits for is not a problem
      assertEquals(ChainAdvice.ABSENT_IDLE, ChainAdvice.forNode(wood, "sawmill", statuses(Status.ABSENT)).key());
   }
}
