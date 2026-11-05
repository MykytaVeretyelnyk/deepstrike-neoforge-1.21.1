package dev.colonyjournal.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static knowledge about how MineColonies buildings feed each other. Ids are the MineColonies building registry paths.
 * Layers are drawn left to right: gatherers, processors, crafters, consumers.
 */
public final class BuildingGraph {
   /** A building type in the map. */
   public record Node(String id, int layer) {
   }

   /**
    * Producer to consumer link.
    *
    * @param need     lang key suffix describing what flows along the link
    * @param optional when true a missing producer is only a warning
    */
   public record Edge(String from, String to, String need, boolean optional) {
   }

   public static final Map<String, Node> NODES = new LinkedHashMap<>();
   public static final List<Edge> EDGES = new ArrayList<>();

   private static void node(int layer, String... ids) {
      for (String id : ids) {
         NODES.put(id, new Node(id, layer));
      }
   }

   private static void edge(String from, String to, String need) {
      EDGES.add(new Edge(from, to, need, false));
   }

   private static void soft(String from, String to, String need) {
      EDGES.add(new Edge(from, to, need, true));
   }

   static {
      node(0, "miner", "lumberjack", "farmer", "fisherman", "chickenherder", "cowboy", "swineherder", "shepherd", "rabbithutch", "plantation", "florist", "beekeeper", "netherworker");
      node(1, "smeltery", "sawmill", "crusher", "sifter", "baker", "composter", "stonesmeltery");
      node(2, "blacksmith", "stonemason", "glassblower", "fletcher", "mechanic", "concretemixer", "dyer", "alchemist", "enchanter", "cook", "kitchen");
      node(3, "builder", "guardtower", "barracks", "barrackstower", "archery", "combatacademy", "hospital", "library", "university", "school", "tavern", "graveyard");

      // metal chain
      edge("miner", "smeltery", "ores");
      edge("smeltery", "blacksmith", "ingots");
      edge("smeltery", "mechanic", "ingots");
      soft("lumberjack", "smeltery", "fuel");
      // wood chain
      edge("lumberjack", "sawmill", "logs");
      edge("sawmill", "builder", "planks");
      edge("lumberjack", "fletcher", "wood");
      // stone chain
      edge("miner", "stonemason", "stone");
      edge("stonemason", "builder", "bricks");
      edge("miner", "crusher", "cobblestone");
      edge("crusher", "sifter", "gravel");
      edge("sifter", "glassblower", "sand");
      edge("glassblower", "builder", "glass");
      edge("concretemixer", "builder", "concrete");
      // tools and weapons
      edge("blacksmith", "builder", "tools");
      edge("blacksmith", "miner", "tools");
      edge("blacksmith", "lumberjack", "tools");
      edge("blacksmith", "farmer", "tools");
      edge("blacksmith", "fisherman", "tools");
      edge("blacksmith", "guardtower", "weapons");
      edge("fletcher", "guardtower", "bows");
      edge("blacksmith", "barrackstower", "weapons");
      // food chain
      edge("farmer", "baker", "wheat");
      edge("farmer", "cook", "crops");
      edge("baker", "cook", "bread");
      soft("fisherman", "cook", "fish");
      soft("chickenherder", "cook", "eggs");
      soft("cowboy", "cook", "beef");
      soft("swineherder", "cook", "pork");
      soft("shepherd", "cook", "mutton");
      soft("rabbithutch", "cook", "rabbit");
      soft("plantation", "cook", "crops");
      soft("beekeeper", "cook", "honey");
      soft("composter", "farmer", "compost");
      soft("composter", "plantation", "compost");
      // dyes and potions
      soft("florist", "dyer", "flowers");
      soft("shepherd", "dyer", "wool");
      soft("plantation", "alchemist", "herbs");
      soft("netherworker", "alchemist", "nether");
      soft("dyer", "builder", "dyes");
   }

   /** Buildings every colony needs regardless of the chains above. */
   public static final List<String> BASICS = List.of("townhall", "warehouse", "deliveryman", "residence", "builder", "cook", "hospital", "guardtower");

   /**
    * One page of the Links tab: a few buildings that belong together.
    *
    * @param id       lang key suffix: {@code journal.colonyjournal.chain.<id>} and {@code .<id>.desc}
    * @param icon     building id whose icon stands for the chain
    * @param members  the building types shown, in a stable order
    * @param skip     edges ("from>to") that both ends of which are members but that are not drawn here, because they would close a loop
    * @param required true when every member must work (the basic buildings), false when absent members are fine
    */
   public record Chain(String id, String icon, List<String> members, List<String> skip, boolean required) {
      /** The edges drawn in this chain: both ends are members and the edge is not skipped. */
      public List<Edge> edges() {
         List<Edge> out = new ArrayList<>();

         for (Edge edge : EDGES) {
            if (this.members.contains(edge.from()) && this.members.contains(edge.to()) && !this.skip.contains(edge.from() + ">" + edge.to())) {
               out.add(edge);
            }
         }

         return out;
      }

      /** True when the chain has no links and is drawn as a plain grid of tiles. */
      public boolean plain() {
         return this.edges().isEmpty();
      }
   }

   public static final List<Chain> CHAINS = new ArrayList<>();

   private static void chain(String id, String icon, List<String> skip, boolean required, String... members) {
      CHAINS.add(new Chain(id, icon, List.of(members), skip, required));
   }

   static {
      chain("metal", "smeltery", List.of("blacksmith>miner", "blacksmith>lumberjack"), false, "miner", "lumberjack", "smeltery", "blacksmith", "mechanic");
      chain("tools", "blacksmith", List.of(), false, "blacksmith", "miner", "lumberjack", "farmer", "fisherman", "builder", "guardtower", "barrackstower");
      chain("wood", "sawmill", List.of(), false, "lumberjack", "sawmill", "fletcher", "builder", "guardtower");
      chain("stone", "stonemason", List.of(), false, "miner", "stonemason", "crusher", "sifter", "glassblower", "concretemixer", "builder");
      chain("crops", "farmer", List.of(), false, "composter", "farmer", "plantation", "beekeeper", "baker", "cook");
      chain("meat", "cook", List.of(), false, "fisherman", "chickenherder", "cowboy", "swineherder", "shepherd", "rabbithutch", "cook");
      chain("dyes", "dyer", List.of(), false, "florist", "shepherd", "plantation", "netherworker", "dyer", "alchemist", "builder");
      chain("basics", "townhall", List.of(), true, BASICS.toArray(new String[0]));
      chain("other", "library", List.of(), false, "stonesmeltery", "enchanter", "kitchen", "barracks", "archery", "combatacademy", "library", "university", "school", "tavern", "graveyard");
   }

   /** The chain with the id, or null. */
   public static Chain chain(String id) {
      for (Chain chain : CHAINS) {
         if (chain.id().equals(id)) {
            return chain;
         }
      }

      return null;
   }

   /** Indexes of the chains in which the link from to is drawn; when none, the chains of the producer. */
   public static List<Integer> chainsOfLink(String from, String to) {
      List<Integer> out = new ArrayList<>();

      for (int i = 0; i < CHAINS.size(); i++) {
         for (Edge edge : CHAINS.get(i).edges()) {
            if (edge.from().equals(from) && edge.to().equals(to)) {
               out.add(i);
               break;
            }
         }
      }

      return out.isEmpty() ? chainsOf(from) : out;
   }

   /** The one rule for "can this building be shown on the Links tab": it is a member of some chain (basic buildings included). */
   public static boolean onMap(String buildingId) {
      return buildingId != null && !chainsOf(buildingId).isEmpty();
   }

   /** Indexes of the chains that contain the building type, in display order. */
   public static List<Integer> chainsOf(String buildingId) {
      List<Integer> out = new ArrayList<>();

      for (int i = 0; i < CHAINS.size(); i++) {
         if (CHAINS.get(i).members().contains(buildingId)) {
            out.add(i);
         }
      }

      return out;
   }

   private BuildingGraph() {
   }
}
