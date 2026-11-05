package dev.colonyjournal.graph;

import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.graph.BuildingGraph.Chain;
import dev.colonyjournal.graph.BuildingGraph.Edge;
import dev.colonyjournal.graph.ChainHealth.EdgeState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Picks the plain-words advice for a building or a whole chain. The result is a lang key suffix
 * ({@code journal.colonyjournal.links.todo.<key>}) plus the links that fill the placeholders; the tab turns it into text.
 */
public final class ChainAdvice {
   public static final String ABSENT = "absent";
   public static final String ABSENT_IDLE = "absent_idle";
   public static final String UNBUILT = "unbuilt";
   public static final String NO_WORKER = "no_worker";
   public static final String PARTIAL = "partial";
   public static final String LACKING = "lacking";
   public static final String OK = "ok";
   public static final String BASIC_MISSING = "basic_missing";
   public static final String CHAIN_OK = "chain_ok";
   public static final String CHAIN_EMPTY = "chain_empty";
   public static final List<String> KEYS = List.of(ABSENT, ABSENT_IDLE, UNBUILT, NO_WORKER, PARTIAL, LACKING, OK, BASIC_MISSING, CHAIN_OK, CHAIN_EMPTY);

   /**
    * @param key   one of {@link #KEYS}
    * @param id    the building the advice is about, or null for the whole chain
    * @param edges the links named in the text: the consumers that wait for the building, or the supplies it lacks
    */
   public record Advice(String key, String id, List<Edge> edges) {
      /** True when there is something to fix. */
      public boolean problem() {
         return !this.key.equals(OK) && !this.key.equals(CHAIN_OK) && !this.key.equals(CHAIN_EMPTY) && !this.key.equals(ABSENT_IDLE);
      }
   }

   private ChainAdvice() {
   }

   public static Advice forNode(Chain chain, String id, Function<String, Status> status) {
      List<Edge> waiting = new ArrayList<>();
      List<Edge> lacking = new ArrayList<>();

      for (Edge edge : chain.edges()) {
         EdgeState state = ChainHealth.edgeState(edge, status);
         if (edge.from().equals(id) && state != EdgeState.IDLE) {
            waiting.add(edge);
         }

         if (edge.to().equals(id) && state == EdgeState.BROKEN) {
            lacking.add(edge);
         }
      }

      Status own = status.apply(id);
      return switch (own) {
         case ABSENT -> chain.required() ? new Advice(BASIC_MISSING, id, List.of()) : (waiting.isEmpty() ? new Advice(ABSENT_IDLE, id, List.of()) : new Advice(ABSENT, id, waiting));
         case UNBUILT -> new Advice(UNBUILT, id, waiting);
         case NO_WORKER -> new Advice(NO_WORKER, id, waiting);
         case PARTIAL -> new Advice(PARTIAL, id, List.of());
         case OK -> lacking.isEmpty() ? new Advice(OK, id, List.of()) : new Advice(LACKING, id, lacking);
      };
   }

   /** The most useful first step in a chain: the producer of the first broken link, else the first building that needs care. */
   public static Advice forChain(Chain chain, Function<String, Status> status) {
      for (Edge edge : chain.edges()) {
         if (ChainHealth.edgeState(edge, status) == EdgeState.BROKEN) {
            return forNode(chain, edge.from(), status);
         }
      }

      boolean any = false;
      for (String id : chain.members()) {
         Status s = status.apply(id);
         any |= s != Status.ABSENT;
         if (chain.required() ? !s.working() : (s == Status.NO_WORKER || s == Status.UNBUILT || s == Status.PARTIAL)) {
            return forNode(chain, id, status);
         }
      }

      return new Advice(any ? CHAIN_OK : CHAIN_EMPTY, null, List.of());
   }
}
