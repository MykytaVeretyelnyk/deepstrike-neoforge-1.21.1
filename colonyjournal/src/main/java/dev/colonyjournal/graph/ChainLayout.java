package dev.colonyjournal.graph;

import dev.colonyjournal.graph.BuildingGraph.Edge;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Pure geometry of one supply chain, with no Minecraft classes so it can be unit tested. The drawing and the mouse
 * hit-testing of the Links tab both read the rectangles and segments computed here.
 *
 * <p>The layout is the usual layered one. Every building gets a layer: the longest path from a source, so goods flow
 * from left to right and the sinks are on the right. Edges that skip layers get one invisible "dummy" item per skipped
 * layer, which keeps a free band for the line to pass between the cards. The order inside a layer minimises crossings
 * (barycenter sweeps and neighbour swaps), the vertical positions pull each item towards the middle of its neighbours.
 * Every edge gets its own port on the side of a card, and between two layers every edge that has to change its height
 * runs in its own vertical track, so no two edges ever share a stretch of line.
 */
public final class ChainLayout {
   /** An invisible item of a skipped layer is this high. */
   static final int DUMMY_H = 8;
   /** Distance between two ports on the side of a card. */
   static final int PORT_PITCH = 5;
   static final int PORT_INSET = 4;
   /** Free space between a card and the first vertical track, and between the last track and the next card. */
   static final int MARGIN_L = 4;
   static final int MARGIN_R = 9;
   static final int TRACK_MIN = 4;
   static final int TRACK_MAX = 8;
   /** Two vertical lines on one track must stay this many pixels apart. */
   static final int SLACK = 2;
   public static final int LABEL_H = 9;
   public static final int ARROW = 6;
   /** The gaps between layers and between rows that are tried, from generous to compact. */
   private static final int[] GAPS_X = {84, 64, 48, 36, 26, 10};
   private static final int[] GAPS_Y = {16, 12, 10, 8, 6, 4, 3};

   /** Size of a card and the wanted gaps; the layer gap grows when more tracks are needed. */
   public record Spec(int nodeW, int nodeH, int gapX, int gapY) {
   }

   public record Box(String id, int layer, int x, int y, int w, int h) {
      public int right() {
         return this.x + this.w;
      }

      public int bottom() {
         return this.y + this.h;
      }

      public boolean contains(double px, double py) {
         return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
      }
   }

   /** An axis-aligned piece of a line, normalised so that x1 &lt;= x2 and y1 &lt;= y2. */
   public record Seg(int x1, int y1, int x2, int y2) {
      static Seg of(int ax, int ay, int bx, int by) {
         return new Seg(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by));
      }

      public boolean horizontal() {
         return this.y1 == this.y2;
      }

      public int length() {
         return (this.x2 - this.x1) + (this.y2 - this.y1);
      }

      /** True when the two pieces lie on one line and share at least one point. */
      public boolean sharesLine(Seg o) {
         if (this.horizontal() && o.horizontal()) {
            return this.y1 == o.y1 && this.x1 <= o.x2 && o.x1 <= this.x2;
         } else if (!this.horizontal() && !o.horizontal()) {
            return this.x1 == o.x1 && this.y1 <= o.y2 && o.y1 <= this.y2;
         }

         return false;
      }

      /** Distance from a point to the piece. */
      public double distance(double px, double py) {
         double dx = Math.max(Math.max(this.x1 - px, 0.0), px - this.x2);
         double dy = Math.max(Math.max(this.y1 - py, 0.0), py - this.y2);
         return Math.sqrt(dx * dx + dy * dy);
      }

      /** True when the piece enters the inside of the rectangle (touching the border does not count). */
      public boolean crossesInside(int rx, int ry, int rw, int rh) {
         return this.x2 > rx && this.x1 < rx + rw && this.y2 > ry && this.y1 < ry + rh;
      }

      /** True when the piece touches the rectangle, a border of the given width around it included. */
      boolean touches(int rx, int ry, int rw, int rh, int border) {
         return this.x2 >= rx - border && this.x1 <= rx + rw - 1 + border && this.y2 >= ry - border && this.y1 <= ry + rh - 1 + border;
      }
   }

   /** Where the short text of an edge is drawn: a rectangle of the label height centred on the line. */
   public record Label(int x, int y, int w) {
      public int h() {
         return LABEL_H;
      }
   }

   /**
    * One edge as a polyline of x, y pairs from the producer card to the consumer card (the arrow head sits at the end,
    * pointing right), its pieces, and the label spot or null when the text does not fit anywhere.
    */
   public record Route(Edge edge, int fromLayer, int toLayer, int[] points, List<Seg> segs, Label label) {
      public int endX() {
         return this.points[this.points.length - 2];
      }

      public int endY() {
         return this.points[this.points.length - 1];
      }

      /** Distance from a point to the nearest piece of the line. */
      public double distance(double px, double py) {
         double best = Double.MAX_VALUE;

         for (Seg seg : this.segs) {
            best = Math.min(best, seg.distance(px, py));
         }

         return best;
      }
   }

   /**
    * The result.
    *
    * @param dropped edges that were left out because they close a loop
    */
   public record Result(List<Box> boxes, Map<String, Box> byId, List<Route> routes, List<Edge> dropped, int width, int height, int layers) {
   }

   private static final class Item {
      final String id;
      final boolean dummy;
      int layer;
      int h;
      int x;
      int y;
      int idx;
      final List<Hop> ins = new ArrayList<>();
      final List<Hop> outs = new ArrayList<>();

      Item(String id, boolean dummy) {
         this.id = id;
         this.dummy = dummy;
      }

      double center() {
         return this.y + this.h / 2.0;
      }
   }

   private static final class Hop {
      final int edge;
      final Item u;
      final Item v;
      int a;
      int b;
      int track = -1;
      int trackX;
      /** Pixels the port on the producer side was moved to avoid a deadlock between two links. */
      int da;

      Hop(int edge, Item u, Item v) {
         this.edge = edge;
         this.u = u;
         this.v = v;
      }

      boolean straight() {
         return this.a == this.b;
      }

      int lo() {
         return Math.min(this.a, this.b);
      }

      int hi() {
         return Math.max(this.a, this.b);
      }
   }

   private ChainLayout() {
   }

   // ------------------------------------------------------------------ entry points

   /**
    * Lays the chain out in the view: tries the generous spacings first and takes the first that fits; when even the
    * most compact one does not fit the result is larger than the view and the caller scrolls.
    *
    * @param labelWidth width in pixels of the label of an edge including its padding, 0 for none
    */
   public static Result fit(List<String> members, List<Edge> edges, int nodeW, int nodeH, int viewW, int viewH, ToIntFunction<Edge> labelWidth) {
      if (edges.isEmpty()) {
         return grid(members, nodeW, nodeH, viewW, viewH);
      }

      int cgx = GAPS_X[GAPS_X.length - 1];
      int cgy = GAPS_Y[GAPS_Y.length - 1];
      Result plain = layout(members, edges, new Spec(nodeW, nodeH, cgx, cgy), labelWidth, null);
      int tallest = 1;
      int[] perLayer = new int[plain.layers()];
      for (Box box : plain.boxes()) {
         tallest = Math.max(tallest, ++perLayer[box.layer()]);
      }

      // wrap tall layers into sub-columns: the fewest columns that fit the view, else the best compromise
      int bestRows = tallest;
      Result best = null;
      for (int rows = tallest; rows >= 1; rows--) {
         Result r = rows == tallest ? plain : layout(members, edges, new Spec(nodeW, nodeH, cgx, cgy), labelWidth, wrapped(plain, rows));
         if (r.width() <= viewW && r.height() <= viewH) {
            best = r;
            bestRows = rows;
            break;
         }

         // no fit: prefer a result that is not too wide (scrolling down is easier than sideways), then the shortest
         if (best == null || (r.width() <= viewW && (best.width() > viewW || r.height() < best.height())) || (best.width() > viewW && r.width() < best.width())) {
            best = r;
            bestRows = rows;
         }
      }

      Map<String, Integer> override = bestRows == tallest ? null : wrapped(plain, bestRows);
      int gx = cgx;
      int gy = cgy;

      for (int g : GAPS_X) {
         gx = g;
         if (layout(members, edges, new Spec(nodeW, nodeH, g, cgy), labelWidth, override).width() <= viewW) {
            break;
         }
      }

      for (int g : GAPS_Y) {
         gy = g;
         if (layout(members, edges, new Spec(nodeW, nodeH, gx, g), labelWidth, override).height() <= viewH) {
            break;
         }
      }

      return layout(members, edges, new Spec(nodeW, nodeH, gx, gy), labelWidth, override);
   }

   /** New layer numbers: every layer with more than rows buildings becomes several consecutive sub-columns, split in the vertical order of the layout. */
   private static Map<String, Integer> wrapped(Result plain, int rows) {
      Map<String, Integer> out = new HashMap<>();
      int next = 0;

      for (int l = 0; l < plain.layers(); l++) {
         List<Box> column = new ArrayList<>();
         for (Box box : plain.boxes()) {
            if (box.layer() == l) {
               column.add(box);
            }
         }

         column.sort(Comparator.comparingInt(Box::y));
         for (int i = 0; i < column.size(); i++) {
            out.put(column.get(i).id(), next + i / rows);
         }

         next += Math.max(1, (column.size() + rows - 1) / rows);
      }

      return out;
   }

   /** Lays buildings that have no links out as rows of cards, as many columns as fit the width. */
   public static Result grid(List<String> members, int nodeW, int nodeH, int viewW, int viewH) {
      List<String> ids = new ArrayList<>(new java.util.LinkedHashSet<>(members));
      int gx = 10;
      int gy = GAPS_Y[GAPS_Y.length - 1];
      int cols = Math.max(1, Math.min(ids.size(), (viewW + gx) / (nodeW + gx)));
      int rows = Math.max(1, (ids.size() + cols - 1) / cols);
      cols = Math.max(1, (ids.size() + rows - 1) / rows);
      for (int g : GAPS_Y) {
         gy = g;
         if (rows * nodeH + (rows - 1) * g <= viewH) {
            break;
         }
      }

      List<Box> boxes = new ArrayList<>();
      Map<String, Box> byId = new LinkedHashMap<>();

      for (int i = 0; i < ids.size(); i++) {
         Box box = new Box(ids.get(i), i % cols, (i % cols) * (nodeW + gx), (i / cols) * (nodeH + gy), nodeW, nodeH);
         boxes.add(box);
         byId.put(box.id(), box);
      }

      int width = ids.isEmpty() ? 0 : cols * nodeW + (cols - 1) * gx;
      int height = ids.isEmpty() ? 0 : rows * nodeH + (rows - 1) * gy;
      return new Result(boxes, byId, List.of(), List.of(), width, height, cols);
   }

   // ------------------------------------------------------------------ the layered layout

   public static Result layout(List<String> members, List<Edge> edges, Spec spec, ToIntFunction<Edge> labelWidth) {
      return layout(members, edges, spec, labelWidth, null);
   }

   /** @param override layer of every building, replacing the computed layers (used to wrap tall layers into sub-columns) */
   private static Result layout(List<String> members, List<Edge> edges, Spec spec, ToIntFunction<Edge> labelWidth, Map<String, Integer> override) {
      List<String> nodes = new ArrayList<>(new java.util.LinkedHashSet<>(members));
      Map<String, Integer> index = new HashMap<>();
      for (int i = 0; i < nodes.size(); i++) {
         index.put(nodes.get(i), i);
      }

      List<Edge> candidates = new ArrayList<>();
      for (Edge edge : edges) {
         if (index.containsKey(edge.from()) && index.containsKey(edge.to()) && !edge.from().equals(edge.to())) {
            candidates.add(edge);
         }
      }

      // break loops: an edge that closes a loop is dropped from the drawing
      int n = nodes.size();
      int[] state = new int[n];
      boolean[] drop = new boolean[candidates.size()];
      for (int i = 0; i < n; i++) {
         if (state[i] == 0) {
            visit(i, state, drop, candidates, index);
         }
      }

      List<Edge> kept = new ArrayList<>();
      List<Edge> dropped = new ArrayList<>();
      for (int i = 0; i < candidates.size(); i++) {
         (drop[i] ? dropped : kept).add(candidates.get(i));
      }

      // layers: the longest path from a source
      int[] layer = new int[n];
      int[] indeg = new int[n];
      for (Edge e : kept) {
         indeg[index.get(e.to())]++;
      }

      int[] remaining = indeg.clone();
      List<Integer> queue = new ArrayList<>();
      for (int i = 0; i < n; i++) {
         if (remaining[i] == 0) {
            queue.add(i);
         }
      }

      for (int q = 0; q < queue.size(); q++) {
         int u = queue.get(q);
         for (Edge e : kept) {
            if (index.get(e.from()) == u) {
               int v = index.get(e.to());
               layer[v] = Math.max(layer[v], layer[u] + 1);
               if (--remaining[v] == 0) {
                  queue.add(v);
               }
            }
         }
      }

      // a source that only feeds far away buildings moves next to the nearest of them: shorter lines
      for (int i = 0; i < n; i++) {
         if (indeg[i] == 0) {
            int min = Integer.MAX_VALUE;
            for (Edge e : kept) {
               if (index.get(e.from()) == i) {
                  min = Math.min(min, layer[index.get(e.to())]);
               }
            }

            layer[i] = min == Integer.MAX_VALUE ? 0 : min - 1;
         }
      }

      if (override != null) {
         for (int i = 0; i < n; i++) {
            layer[i] = override.get(nodes.get(i));
         }
      }

      int layers = 1;
      for (int l : layer) {
         layers = Math.max(layers, l + 1);
      }

      // items: one per building, one dummy per skipped layer of an edge
      Item[] real = new Item[n];
      List<List<Item>> columns = new ArrayList<>();
      for (int l = 0; l < layers; l++) {
         columns.add(new ArrayList<>());
      }

      for (int i = 0; i < n; i++) {
         real[i] = new Item(nodes.get(i), false);
         real[i].layer = layer[i];
         real[i].h = spec.nodeH();
         columns.get(layer[i]).add(real[i]);
      }

      List<Hop> hops = new ArrayList<>();
      List<List<Item>> paths = new ArrayList<>();
      for (int e = 0; e < kept.size(); e++) {
         Item from = real[index.get(kept.get(e).from())];
         Item to = real[index.get(kept.get(e).to())];
         List<Item> path = new ArrayList<>();
         path.add(from);
         for (int l = from.layer + 1; l < to.layer; l++) {
            Item dummy = new Item(null, true);
            dummy.layer = l;
            dummy.h = DUMMY_H;
            columns.get(l).add(dummy);
            path.add(dummy);
         }

         path.add(to);
         paths.add(path);
         for (int k = 0; k + 1 < path.size(); k++) {
            Hop hop = new Hop(e, path.get(k), path.get(k + 1));
            hops.add(hop);
            hop.u.outs.add(hop);
            hop.v.ins.add(hop);
         }
      }

      orderColumns(columns, hops);
      placeVertically(columns, spec.gapY());
      List<List<Hop>> gaps = new ArrayList<>();
      for (int g = 0; g < layers; g++) {
         List<Hop> gap = new ArrayList<>();
         for (Hop hop : hops) {
            if (hop.u.layer == g) {
               gap.add(hop);
            }
         }

         gaps.add(gap);
      }

      // ports and tracks; when two links would have to swap heights (no track order can keep their stubs apart) one
      // port is moved by a pixel and everything is tried again
      int[] tracksOf = new int[layers];
      for (int attempt = 0; attempt < 60; attempt++) {
         assignPorts(columns);
         List<Hop> blame = new ArrayList<>();
         for (int g = 0; g + 1 < layers; g++) {
            tracksOf[g] = assignTracks(gaps.get(g), blame);
         }

         if (blame.isEmpty()) {
            break;
         }

         for (Hop hop : blame) {
            int d = attempt % 2 == 0 ? 1 : -1;
            if (hop.u.dummy) {
               hop.u.y += d;
            } else {
               hop.da += d;
            }
         }
      }

      // the x position of every layer
      int[] layerX = new int[layers];
      int x = 0;
      for (int g = 0; g < layers; g++) {
         layerX[g] = x;
         if (g + 1 < layers) {
            List<Hop> gap = gaps.get(g);
            int tracks = tracksOf[g];
            int minGap = MARGIN_L + MARGIN_R + Math.max(0, tracks - 1) * TRACK_MIN;
            int width = Math.max(spec.gapX(), minGap);
            int step = tracks <= 1 ? 0 : Math.max(TRACK_MIN, Math.min(TRACK_MAX, (width - MARGIN_L - MARGIN_R) / tracks));
            for (Hop hop : gap) {
               hop.trackX = x + spec.nodeW() + MARGIN_L + Math.max(0, hop.track) * step;
            }

            x += spec.nodeW() + width;
         }
      }

      int minTop = Integer.MAX_VALUE;
      int maxBottom = 0;
      for (List<Item> column : columns) {
         for (Item item : column) {
            item.x = layerX[item.layer];
            minTop = Math.min(minTop, item.y);
         }
      }

      if (minTop == Integer.MAX_VALUE) {
         minTop = 0;
      }

      List<Box> boxes = new ArrayList<>();
      Map<String, Box> byId = new LinkedHashMap<>();
      for (List<Item> column : columns) {
         for (Item item : column) {
            item.y -= minTop;
            maxBottom = Math.max(maxBottom, item.y + item.h);
         }
      }

      for (Hop hop : hops) {
         hop.a -= minTop;
         hop.b -= minTop;
      }

      for (int i = 0; i < n; i++) {
         Box box = new Box(real[i].id, real[i].layer, real[i].x, real[i].y, spec.nodeW(), real[i].h);
         boxes.add(box);
         byId.put(box.id(), box);
      }

      // the polylines
      List<Route> routes = new ArrayList<>();
      for (int e = 0; e < kept.size(); e++) {
         List<Item> path = paths.get(e);
         List<int[]> pts = new ArrayList<>();
         int px = path.get(0).x + spec.nodeW();
         boolean started = false;
         for (int k = 0; k + 1 < path.size(); k++) {
            Hop hop = hopOf(path.get(k), path.get(k + 1), e);
            if (!started) {
               pts.add(new int[] {px, hop.a});
               started = true;
            }

            if (!hop.straight()) {
               pts.add(new int[] {hop.trackX, hop.a});
               pts.add(new int[] {hop.trackX, hop.b});
            }

            pts.add(new int[] {path.get(k + 1).x, hop.b});
            if (path.get(k + 1).dummy) {
               pts.add(new int[] {path.get(k + 1).x + spec.nodeW(), hop.b});
            }
         }

         routes.add(route(kept.get(e), layer[index.get(kept.get(e).from())], layer[index.get(kept.get(e).to())], pts));
      }

      routes = placeLabels(routes, boxes, spec, labelWidth);
      int width = layers == 0 ? 0 : layerX[layers - 1] + spec.nodeW();
      return new Result(boxes, byId, routes, dropped, width, maxBottom, layers);
   }

   private static Hop hopOf(Item u, Item v, int edge) {
      for (Hop hop : u.outs) {
         if (hop.v == v && hop.edge == edge) {
            return hop;
         }
      }

      throw new IllegalStateException("no hop");
   }

   private static void visit(int u, int[] state, boolean[] drop, List<Edge> edges, Map<String, Integer> index) {
      state[u] = 1;
      for (int i = 0; i < edges.size(); i++) {
         if (index.get(edges.get(i).from()) != u) {
            continue;
         }

         int v = index.get(edges.get(i).to());
         if (state[v] == 1) {
            drop[i] = true;
         } else if (state[v] == 0) {
            visit(v, state, drop, edges, index);
         }
      }

      state[u] = 2;
   }

   // ------------------------------------------------------------------ order inside the layers

   private static void orderColumns(List<List<Item>> columns, List<Hop> hops) {
      reindex(columns);
      List<List<Item>> best = snapshot(columns);
      int bestCross = crossings(hops);

      for (int pass = 0; pass < 12 && bestCross > 0; pass++) {
         if (pass % 2 == 0) {
            for (int l = 1; l < columns.size(); l++) {
               sortByNeighbours(columns.get(l), true);
            }
         } else {
            for (int l = columns.size() - 2; l >= 0; l--) {
               sortByNeighbours(columns.get(l), false);
            }
         }

         int c = crossings(hops);
         if (c < bestCross) {
            bestCross = c;
            best = snapshot(columns);
         }
      }

      restore(columns, best);
      boolean improved = true;
      for (int round = 0; round < 20 && improved && bestCross > 0; round++) {
         improved = false;
         for (List<Item> column : columns) {
            for (int i = 0; i + 1 < column.size(); i++) {
               Collections.swap(column, i, i + 1);
               reindex(columns);
               int c = crossings(hops);
               if (c < bestCross) {
                  bestCross = c;
                  improved = true;
               } else {
                  Collections.swap(column, i, i + 1);
                  reindex(columns);
               }
            }
         }
      }
   }

   private static void sortByNeighbours(List<Item> column, boolean usePreds) {
      Map<Item, Double> key = new HashMap<>();
      for (Item item : column) {
         List<Hop> side = usePreds ? item.ins : item.outs;
         double sum = 0.0;
         for (Hop hop : side) {
            sum += (usePreds ? hop.u : hop.v).idx;
         }

         key.put(item, side.isEmpty() ? (double)item.idx : sum / side.size());
      }

      List<Item> sorted = new ArrayList<>(column);
      sorted.sort(Comparator.comparingDouble((Item i) -> key.get(i)).thenComparingInt(i -> i.idx));
      column.clear();
      column.addAll(sorted);
      for (int i = 0; i < column.size(); i++) {
         column.get(i).idx = i;
      }
   }

   private static void reindex(List<List<Item>> columns) {
      for (List<Item> column : columns) {
         for (int i = 0; i < column.size(); i++) {
            column.get(i).idx = i;
         }
      }
   }

   private static List<List<Item>> snapshot(List<List<Item>> columns) {
      List<List<Item>> copy = new ArrayList<>();
      for (List<Item> column : columns) {
         copy.add(new ArrayList<>(column));
      }

      return copy;
   }

   private static void restore(List<List<Item>> columns, List<List<Item>> saved) {
      for (int i = 0; i < columns.size(); i++) {
         columns.get(i).clear();
         columns.get(i).addAll(saved.get(i));
      }

      reindex(columns);
   }

   private static int crossings(List<Hop> hops) {
      int count = 0;
      for (int i = 0; i < hops.size(); i++) {
         Hop p = hops.get(i);
         for (int j = i + 1; j < hops.size(); j++) {
            Hop q = hops.get(j);
            if (p.u.layer == q.u.layer && (p.u.idx - q.u.idx) * (p.v.idx - q.v.idx) < 0) {
               count++;
            }
         }
      }

      return count;
   }

   // ------------------------------------------------------------------ vertical positions

   private static void placeVertically(List<List<Item>> columns, int gapY) {
      for (List<Item> column : columns) {
         int y = 0;
         for (Item item : column) {
            item.y = y;
            y += item.h + gapY;
         }
      }

      for (int round = 0; round < 6; round++) {
         for (int l = 1; l < columns.size(); l++) {
            pull(columns.get(l), true, gapY);
         }

         for (int l = columns.size() - 2; l >= 0; l--) {
            pull(columns.get(l), false, gapY);
         }
      }

      for (int l = 1; l < columns.size(); l++) {
         pull(columns.get(l), true, gapY);
      }
   }

   /**
    * Moves the items of a layer as close as possible to the middle of their neighbours without changing their order or
    * the gap between them (least squares with an order constraint, solved by pooling adjacent violators).
    */
   private static void pull(List<Item> column, boolean usePreds, int gapY) {
      int n = column.size();
      if (n == 0) {
         return;
      }

      double[] offset = new double[n];
      double[] e = new double[n];
      double s = 0.0;
      for (int i = 0; i < n; i++) {
         Item item = column.get(i);
         offset[i] = s;
         s += item.h + gapY;
         List<Hop> side = usePreds ? item.ins : item.outs;
         double want = item.center();
         if (!side.isEmpty()) {
            double sum = 0.0;
            for (Hop hop : side) {
               sum += (usePreds ? hop.u : hop.v).center();
            }

            want = sum / side.size();
         }

         e[i] = want - item.h / 2.0 - offset[i];
      }

      double[] sum = new double[n];
      int[] count = new int[n];
      int blocks = 0;
      for (int i = 0; i < n; i++) {
         sum[blocks] = e[i];
         count[blocks] = 1;
         blocks++;
         while (blocks > 1 && sum[blocks - 2] / count[blocks - 2] > sum[blocks - 1] / count[blocks - 1]) {
            sum[blocks - 2] += sum[blocks - 1];
            count[blocks - 2] += count[blocks - 1];
            blocks--;
         }
      }

      int i = 0;
      for (int b = 0; b < blocks; b++) {
         long z = Math.round(sum[b] / count[b]);
         for (int k = 0; k < count[b]; k++, i++) {
            column.get(i).y = (int)(z + Math.round(offset[i]));
         }
      }
   }

   // ------------------------------------------------------------------ ports and tracks

   private static void assignPorts(List<List<Item>> columns) {
      for (List<Item> column : columns) {
         for (Item item : column) {
            int center = (int)Math.round(item.center());
            if (item.dummy) {
               for (Hop hop : item.ins) {
                  hop.b = center;
               }

               for (Hop hop : item.outs) {
                  hop.a = center;
               }

               continue;
            }

            List<Hop> outs = new ArrayList<>(item.outs);
            outs.sort(Comparator.comparingDouble((Hop h) -> h.v.center()).thenComparingInt(h -> h.edge));
            for (int k = 0; k < outs.size(); k++) {
               outs.get(k).a = port(item, k, outs.size()) + outs.get(k).da;
            }

            List<Hop> ins = new ArrayList<>(item.ins);
            ins.sort(Comparator.comparingDouble((Hop h) -> h.u.center()).thenComparingInt(h -> h.edge));
            for (int k = 0; k < ins.size(); k++) {
               ins.get(k).b = port(item, k, ins.size());
            }
         }
      }
   }

   private static int port(Item item, int k, int count) {
      if (count <= 1) {
         return (int)Math.round(item.center());
      }

      double pitch = Math.max(1, Math.min(PORT_PITCH, (item.h - 2 * PORT_INSET) / (count - 1)));
      return (int)Math.round(item.center() + (k - (count - 1) / 2.0) * pitch);
   }

   /** Gives every hop of one gap between two layers a track; returns the number of tracks used. */
   private static int assignTracks(List<Hop> gap, List<Hop> blame) {
      List<Hop> nets = new ArrayList<>();
      for (Hop hop : gap) {
         hop.track = -1;
         if (!hop.straight()) {
            nets.add(hop);
         }
      }

      nets.sort(Comparator.comparingInt(Hop::lo).thenComparingInt(Hop::hi).thenComparingInt(h -> h.edge));
      int m = nets.size();
      // a before b when the left stub of a runs at the height of the right stub of b: a must turn first
      List<Set<Integer>> after = new ArrayList<>();
      int[] indeg = new int[m];
      for (int i = 0; i < m; i++) {
         after.add(new HashSet<>());
      }

      for (int i = 0; i < m; i++) {
         for (int j = 0; j < m; j++) {
            if (i != j && nets.get(i).a == nets.get(j).b && after.get(i).add(j)) {
               indeg[j]++;
            }
         }
      }

      boolean[] done = new boolean[m];
      int tracks = 0;
      for (int step = 0; step < m; step++) {
         int pick = -1;
         for (int i = 0; i < m && pick < 0; i++) {
            if (!done[i] && indeg[i] == 0) {
               pick = i;
            }
         }

         if (pick < 0) {
            // a deadlock: two links would have to turn before each other; the caller moves the port of the one that waits
            for (int i = 0; i < m && pick < 0; i++) {
               if (!done[i]) {
                  pick = i;
               }
            }

            for (int i = 0; i < m; i++) {
               if (!done[i] && i != pick && after.get(i).contains(pick) && !blame.contains(nets.get(i))) {
                  blame.add(nets.get(i));
               }
            }
         }

         done[pick] = true;
         Hop hop = nets.get(pick);
         int t = 0;
         for (int i = 0; i < m; i++) {
            if (done[i] && i != pick && after.get(i).contains(pick)) {
               t = Math.max(t, nets.get(i).track + 1);
            }
         }

         while (conflict(nets, hop, t)) {
            t++;
         }

         hop.track = t;
         tracks = Math.max(tracks, t + 1);
         for (int j : after.get(pick)) {
            indeg[j]--;
         }
      }

      for (Hop hop : gap) {
         if (hop.straight()) {
            hop.track = 0;
         }
      }

      return tracks;
   }

   private static boolean conflict(List<Hop> nets, Hop hop, int track) {
      for (Hop other : nets) {
         if (other != hop && other.track == track && hop.lo() <= other.hi() + SLACK && other.lo() <= hop.hi() + SLACK) {
            return true;
         }
      }

      return false;
   }

   // ------------------------------------------------------------------ routes and labels

   private static Route route(Edge edge, int fromLayer, int toLayer, List<int[]> raw) {
      List<int[]> pts = new ArrayList<>();
      for (int[] p : raw) {
         if (!pts.isEmpty() && Arrays.equals(pts.get(pts.size() - 1), p)) {
            continue;
         }

         // drop the middle point of three points on one line
         while (pts.size() >= 2) {
            int[] a = pts.get(pts.size() - 2);
            int[] b = pts.get(pts.size() - 1);
            if ((a[0] == b[0] && b[0] == p[0]) || (a[1] == b[1] && b[1] == p[1])) {
               pts.remove(pts.size() - 1);
            } else {
               break;
            }
         }

         pts.add(p);
      }

      int[] flat = new int[pts.size() * 2];
      List<Seg> segs = new ArrayList<>();
      for (int i = 0; i < pts.size(); i++) {
         flat[i * 2] = pts.get(i)[0];
         flat[i * 2 + 1] = pts.get(i)[1];
         if (i > 0) {
            segs.add(Seg.of(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1]));
         }
      }

      return new Route(edge, fromLayer, toLayer, flat, segs, null);
   }

   private static List<Route> placeLabels(List<Route> routes, List<Box> boxes, Spec spec, ToIntFunction<Edge> labelWidth) {
      List<int[]> placed = new ArrayList<>();
      List<Route> out = new ArrayList<>();

      for (int r = 0; r < routes.size(); r++) {
         Route route = routes.get(r);
         int w = labelWidth.applyAsInt(route.edge());
         Label label = null;
         if (w > 0) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < route.segs().size(); i++) {
               if (route.segs().get(i).horizontal()) {
                  order.add(i);
               }
            }

            order.sort(Comparator.comparingInt((Integer i) -> -route.segs().get(i).length()));
            for (int si : order) {
               Seg seg = route.segs().get(si);
               boolean last = si == route.segs().size() - 1;
               int lo = seg.x1() + 2;
               int hi = seg.x2() - 2 - (last ? ARROW : 0);
               if (hi - lo < w) {
                  continue;
               }

               int[] xs = {lo + (hi - lo - w) / 2, lo, hi - w};
               for (int lx : xs) {
                  int ly = seg.y1() - LABEL_H / 2;
                  if (labelFree(routes, boxes, placed, r, si, lx, ly, w)) {
                     label = new Label(lx, ly, w);
                     placed.add(new int[] {lx, ly, w});
                     break;
                  }
               }

               if (label != null) {
                  break;
               }
            }
         }

         out.add(new Route(route.edge(), route.fromLayer(), route.toLayer(), route.points(), route.segs(), label));
      }

      return out;
   }

   private static boolean labelFree(List<Route> routes, List<Box> boxes, List<int[]> placed, int self, int hostSeg, int lx, int ly, int w) {
      for (Box box : boxes) {
         if (lx < box.right() + 1 && lx + w > box.x() - 1 && ly < box.bottom() + 1 && ly + LABEL_H > box.y() - 1) {
            return false;
         }
      }

      for (int[] p : placed) {
         if (lx < p[0] + p[2] + 1 && lx + w > p[0] - 1 && ly < p[1] + LABEL_H + 1 && ly + LABEL_H > p[1] - 1) {
            return false;
         }
      }

      for (int r = 0; r < routes.size(); r++) {
         Route route = routes.get(r);
         for (int i = 0; i < route.segs().size(); i++) {
            if (r == self && i == hostSeg) {
               continue;
            }

            if (route.segs().get(i).touches(lx, ly, w, LABEL_H, 1)) {
               return false;
            }
         }

         // the arrow head of every route
         int ex = route.endX();
         int ey = route.endY();
         if (ex + 1 > lx - 1 && ex - ARROW < lx + w + 1 && ey + 4 > ly - 1 && ey - 3 < ly + LABEL_H + 1) {
            return false;
         }
      }

      return true;
   }
}
