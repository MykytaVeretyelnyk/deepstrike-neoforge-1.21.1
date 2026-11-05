package dev.colonyjournal.graph;

import dev.colonyjournal.analysis.ColonyReport.Status;
import dev.colonyjournal.graph.BuildingGraph.Chain;
import dev.colonyjournal.graph.BuildingGraph.Edge;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** How healthy a chain is, from the statuses of its buildings. Pure, so the chain selection can be unit tested. */
public final class ChainHealth {
   public enum Light {
      GREEN,
      YELLOW,
      RED
   }

   /** What a link looks like: it works, it is broken, it is broken but optional, or nobody waits for it yet. */
   public enum EdgeState {
      OK,
      BROKEN,
      OPTIONAL_BROKEN,
      IDLE
   }

   /**
    * @param working members that work
    * @param present members that exist (built or not)
    * @param total   all members
    * @param broken  required links whose producer is missing or idle while the consumer exists
    */
   public record Summary(Light light, int working, int present, int total, int broken) {
   }

   private ChainHealth() {
   }

   /** The state of a link; it matches the "broken" rule of the colony report. */
   public static EdgeState edgeState(Edge edge, Function<String, Status> status) {
      if (status.apply(edge.to()) == Status.ABSENT) {
         return EdgeState.IDLE;
      } else if (status.apply(edge.from()).working()) {
         return EdgeState.OK;
      }

      return edge.optional() ? EdgeState.OPTIONAL_BROKEN : EdgeState.BROKEN;
   }

   public static Summary summarize(Chain chain, Function<String, Status> status) {
      int working = 0;
      int present = 0;
      boolean soft = false;
      boolean hard = false;

      for (String id : chain.members()) {
         Status s = status.apply(id);
         if (s.working()) {
            working++;
         }

         if (s != Status.ABSENT) {
            present++;
         }

         if (s == Status.NO_WORKER || s == Status.UNBUILT || s == Status.PARTIAL) {
            soft = true;
         }

         if (chain.required() && !s.working()) {
            hard = true;
         }
      }

      int broken = 0;
      for (Edge edge : chain.edges()) {
         if (edgeState(edge, status) == EdgeState.BROKEN) {
            broken++;
         }
      }

      Light light = hard || broken > 0 ? Light.RED : (soft ? Light.YELLOW : Light.GREEN);
      return new Summary(light, working, present, chain.members().size(), broken);
   }

   /**
    * The chain to show first among the candidates (indexes into {@link BuildingGraph#CHAINS}): the first red one, else
    * the first yellow one, else the first candidate.
    */
   public static int pick(List<Integer> candidates, Function<String, Status> status) {
      int yellow = -1;

      for (int index : candidates) {
         Light light = summarize(BuildingGraph.CHAINS.get(index), status).light();
         if (light == Light.RED) {
            return index;
         } else if (light == Light.YELLOW && yellow < 0) {
            yellow = index;
         }
      }

      return yellow >= 0 ? yellow : candidates.get(0);
   }

   /** The default chain: the first one with a problem, else the first. */
   public static int firstWithProblem(Function<String, Status> status) {
      List<Integer> all = new ArrayList<>();

      for (int i = 0; i < BuildingGraph.CHAINS.size(); i++) {
         all.add(i);
      }

      return pick(all, status);
   }
}
