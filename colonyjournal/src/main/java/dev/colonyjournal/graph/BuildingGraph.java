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

   private BuildingGraph() {
   }
}
