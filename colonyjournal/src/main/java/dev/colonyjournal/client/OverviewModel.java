package dev.colonyjournal.client;

import dev.colonyjournal.analysis.ColonyReport.Issue;
import dev.colonyjournal.analysis.ColonyReport.Row;
import dev.colonyjournal.analysis.ColonyReport.Severity;
import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.graph.BuildingGraph;
import dev.colonyjournal.net.Views.EconomyView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The decisions of the Overview tab: the four traffic-light cards and the list of next actions. It produces lang keys and
 * their arguments only, so it can be unit tested without a screen.
 */
final class OverviewModel {
   static final int MAX_TODOS = 5;

   enum Light {
      GREEN(0xFF55CC66),
      YELLOW(0xFFE0C040),
      RED(0xFFE05050),
      /** Not known yet (data still loading). */
      NONE(0xFF909090),
      /** A suggestion, not a problem. */
      INFO(0xFF8AB4D8);

      final int color;

      Light(int color) {
         this.color = color;
      }
   }

   /** What the model reads. {@code eco} is null while the economy data of this colony has not arrived. */
   record Facts(List<Issue> issues, List<Row> rows, int citizens, int limit, double happiness, EconomyView eco) {
   }

   /** A traffic-light card; {@code tab} is the tab key the card opens. */
   record Card(String id, Light light, String key, Object[] args, String tab) {
   }

   /** One suggested action; {@code focus} is a building type to show on the map, or null. */
   record Todo(Light light, String key, Object[] args, String tab, String focus, String partner) {
      Todo(Light light, String key, Object[] args, String tab, String focus) {
         this(light, key, args, tab, focus, null);
      }
   }

   // ------------------------------------------------------------------ cards

   static List<Card> cards(Facts f) {
      return List.of(chains(f), treasury(f), mood(f), trade(f));
   }

   static Card chains(Facts f) {
      int errors = 0;
      int warns = 0;

      for (Issue issue : f.issues()) {
         if (issue.severity() == Severity.ERROR) {
            errors++;
         } else if (issue.severity() == Severity.WARN) {
            warns++;
         }
      }

      if (errors > 0) {
         return new Card("chains", Light.RED, "journal.colonyjournal.ov.chains.red", new Object[] {errors, warns}, "problems");
      } else if (warns > 0) {
         return new Card("chains", Light.YELLOW, "journal.colonyjournal.ov.chains.yellow", new Object[] {warns}, "problems");
      }

      return new Card("chains", Light.GREEN, "journal.colonyjournal.ov.chains.green", new Object[0], "problems");
   }

   static long net(EconomyView eco) {
      return eco.income() + eco.tradeIncome() - eco.expense();
   }

   static Card treasury(Facts f) {
      EconomyView eco = f.eco();
      if (eco == null) {
         return new Card("treasury", Light.NONE, "journal.colonyjournal.loading", new Object[0], "economy");
      }

      long net = net(eco);
      long balance = eco.balance();
      if (balance < 0L) {
         return new Card("treasury", Light.RED, "journal.colonyjournal.ov.treasury.debt", new Object[] {coins(-balance)}, "economy");
      } else if (net < 0L) {
         long deficit = -net;
         if (balance < deficit * 3L) {
            return new Card("treasury", Light.RED, "journal.colonyjournal.ov.treasury.runout", new Object[] {coins(deficit), balance / deficit}, "economy");
         }

         return new Card("treasury", Light.YELLOW, "journal.colonyjournal.ov.treasury.deficit", new Object[] {coins(deficit)}, "economy");
      } else if (eco.taxRate() > eco.tolerableRate()) {
         return new Card("treasury", Light.YELLOW, "journal.colonyjournal.ov.treasury.tax_high", new Object[] {eco.taxRate(), eco.tolerableRate()}, "economy");
      }

      String key = eco.taxRate() < eco.tolerableRate() ? "journal.colonyjournal.ov.treasury.low" : "journal.colonyjournal.ov.treasury.ok";
      return new Card("treasury", Light.GREEN, key, new Object[] {coins(balance), signed(net), eco.taxRate(), eco.tolerableRate()}, "economy");
   }

   static Card mood(Facts f) {
      double h = f.happiness();
      Light light = h < 4.0 ? Light.RED : (h < 7.0 ? Light.YELLOW : Light.GREEN);
      String hs = String.format(Locale.ROOT, "%.1f", h);
      EconomyView eco = f.eco();
      if (eco == null) {
         return new Card("mood", light, "journal.colonyjournal.ov.mood.only", new Object[] {hs}, "economy");
      }

      double tf = eco.taxFactor();
      if (tf < 0.7) {
         light = Light.RED;
      } else if (tf < 0.95 && light == Light.GREEN) {
         light = Light.YELLOW;
      }

      String key = light == Light.RED ? "journal.colonyjournal.ov.mood.red" : (light == Light.YELLOW ? "journal.colonyjournal.ov.mood.yellow" : "journal.colonyjournal.ov.mood.green");
      return new Card("mood", light, key, new Object[] {hs, (int)Math.round(tf * 100.0)}, "economy");
   }

   static Card trade(Facts f) {
      EconomyView eco = f.eco();
      if (eco == null) {
         return new Card("trade", Light.NONE, "journal.colonyjournal.loading", new Object[0], "market");
      }

      int pct = (int)Math.round(Math.max(0.0, Math.min(1.0, eco.prosperity())) * 100.0);
      if (eco.income() <= 0L) {
         return new Card("trade", Light.YELLOW, "journal.colonyjournal.ov.trade.none", new Object[] {pct}, "market");
      } else if (pct < 30) {
         return new Card("trade", Light.YELLOW, "journal.colonyjournal.ov.trade.low", new Object[] {pct}, "market");
      }

      return new Card("trade", Light.GREEN, "journal.colonyjournal.ov.trade.ok", new Object[] {pct, coins(eco.tradeIncome())}, "market");
   }

   // ------------------------------------------------------------------ next actions

   static boolean onMap(String id) {
      return BuildingGraph.onMap(id);
   }

   /** Up to {@link #MAX_TODOS} actions, the most important first; never empty. */
   static List<Todo> todos(Facts f) {
      List<Todo> out = new ArrayList<>();
      EconomyView eco = f.eco();

      int errors = 0;
      for (Issue issue : f.issues()) {
         if (issue.severity() == Severity.ERROR && errors < 3) {
            errors++;
            out.add(new Todo(Light.RED, "journal.colonyjournal.ov.todo.error", new Object[] {issue.text()}, onMap(issue.focus()) ? "map" : "problems", issue.focus(), issue.partner()));
         }
      }

      if (eco != null) {
         if (eco.balance() < 0L) {
            out.add(new Todo(Light.RED, "journal.colonyjournal.ov.todo.debt", new Object[] {coins(-eco.balance())}, "economy", null));
         }

         if (eco.taxRate() > eco.tolerableRate()) {
            out.add(new Todo(Light.YELLOW, "journal.colonyjournal.ov.todo.tax_high", new Object[] {eco.taxRate(), eco.tolerableRate()}, "economy", null));
         }
      }

      if (f.limit() > 0 && f.citizens() >= f.limit()) {
         out.add(new Todo(Light.YELLOW, "journal.colonyjournal.ov.todo.housing", new Object[] {f.citizens(), f.limit()}, "buildings", "residence"));
      }

      Row first = null;
      int idle = 0;
      for (Row row : f.rows()) {
         if (row.status() == Status.NO_WORKER) {
            idle++;
            if (first == null) {
               first = row;
            }
         }
      }

      if (first != null) {
         String tab = onMap(first.typeId()) ? "map" : "buildings";
         if (idle == 1) {
            out.add(new Todo(Light.YELLOW, "journal.colonyjournal.ov.todo.no_worker", new Object[] {first.name()}, tab, first.typeId()));
         } else {
            out.add(new Todo(Light.YELLOW, "journal.colonyjournal.ov.todo.no_workers", new Object[] {idle, first.name()}, tab, first.typeId()));
         }
      }

      if (eco != null) {
         if (eco.balance() >= 0L && net(eco) < 0L) {
            out.add(new Todo(Light.YELLOW, "journal.colonyjournal.ov.todo.deficit", new Object[] {coins(-net(eco))}, "economy", null));
         }
      }

      if (f.happiness() < 5.0) {
         out.add(new Todo(Light.YELLOW, "journal.colonyjournal.ov.todo.unhappy", new Object[] {String.format(Locale.ROOT, "%.1f", f.happiness())}, "economy", null));
      }

      if (eco != null && eco.income() <= 0L) {
         out.add(new Todo(Light.INFO, "journal.colonyjournal.ov.todo.nothing_to_sell", new Object[0], "buildings", null));
      }

      int warns = 0;
      for (Issue issue : f.issues()) {
         if (issue.severity() == Severity.WARN && warns < 2 && !hasFocus(out, issue.focus())) {
            warns++;
            out.add(new Todo(Light.YELLOW, "journal.colonyjournal.ov.todo.warn", new Object[] {issue.text()}, onMap(issue.focus()) ? "map" : "problems", issue.focus(), issue.partner()));
         }
      }

      if (out.isEmpty()) {
         out.add(new Todo(Light.INFO, "journal.colonyjournal.ov.todo.upgrade", new Object[0], "buildings", null));
         out.add(new Todo(Light.INFO, "journal.colonyjournal.ov.todo.market", new Object[0], "market", null));
      }

      return out.size() > MAX_TODOS ? new ArrayList<>(out.subList(0, MAX_TODOS)) : out;
   }

   private static boolean hasFocus(List<Todo> list, String focus) {
      if (focus == null) {
         return false;
      }

      for (Todo t : list) {
         if (focus.equals(t.focus())) {
            return true;
         }
      }

      return false;
   }

   // ------------------------------------------------------------------ formatting

   static String coins(long v) {
      return String.format(Locale.ROOT, "%,d", v);
   }

   static String signed(long v) {
      return v > 0L ? "+" + coins(v) : coins(v);
   }

   private OverviewModel() {
   }
}
