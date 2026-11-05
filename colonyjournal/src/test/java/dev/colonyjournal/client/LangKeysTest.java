package dev.colonyjournal.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.colonyjournal.analysis.ColonyReport;
import dev.colonyjournal.graph.BuildingGraph;
import dev.colonyjournal.graph.ChainAdvice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Keeps the two lang files and the code in step: every key the code uses exists in en_us and ru_ru, both files have the
 * same keys and the same placeholders, and the guide texts are well formed. Keys built by concatenation are checked by
 * enumerating their suffixes (see {@link #dynamic()}).
 */
class LangKeysTest {
   private static final Path SRC = TestPaths.ROOT.resolve("src/main/java");
   private static final Path LANG = TestPaths.ROOT.resolve("src/main/resources/assets/colonyjournal/lang");
   private static final Pattern LITERAL = Pattern.compile("\"((?:journal|msg|key|item)\\.colonyjournal\\.[A-Za-z0-9_.]*|key\\.categories\\.colonyjournal)\"");
   private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[sd]");
   private static final Pattern HEX6 = Pattern.compile("[0-9A-Fa-f]{6}");

   private static Map<String, String> en;
   private static Map<String, String> ru;

   @BeforeAll
   static void load() throws IOException {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
      en = read("en_us.json");
      ru = read("ru_ru.json");
   }

   private static Map<String, String> read(String name) throws IOException {
      Path file = LANG.resolve(name);
      String text = Files.readString(file, StandardCharsets.UTF_8);
      Map<String, String> out = new LinkedHashMap<>();
      JsonObject json = JsonParser.parseString(text).getAsJsonObject();
      for (Map.Entry<String, JsonElement> e : json.entrySet()) {
         out.put(e.getKey(), e.getValue().getAsString());
      }

      // Gson keeps only the last of two equal keys, so count the key lines separately
      Matcher m = Pattern.compile("(?m)^\\s*\"([^\"]+)\"\\s*:").matcher(text);
      Set<String> seen = new LinkedHashSet<>();
      while (m.find()) {
         assertTrue(seen.add(m.group(1)), name + ": duplicate key " + m.group(1));
      }

      assertEquals(out.size(), seen.size(), name + ": key lines do not match the parsed keys");
      return out;
   }

   private static String source(Path file) {
      try {
         return Files.readString(file, StandardCharsets.UTF_8);
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }
   }

   private static List<Path> javaFiles() throws IOException {
      try (Stream<Path> s = Files.walk(SRC)) {
         return s.filter(p -> p.toString().endsWith(".java")).toList();
      }
   }

   private static List<String> scan(Pattern pattern, Path dir) throws IOException {
      List<String> out = new ArrayList<>();
      try (Stream<Path> s = Files.walk(dir)) {
         for (Path p : s.filter(x -> x.toString().endsWith(".java")).toList()) {
            Matcher m = pattern.matcher(source(p));
            while (m.find()) {
               out.add(m.group(1));
            }
         }
      }

      return out;
   }

   /** The suffixes of keys that the code builds by concatenation, by prefix. */
   private static Map<String, List<String>> dynamic() throws IOException {
      Map<String, List<String>> map = new HashMap<>();
      List<String> tabs = scan(Pattern.compile("String key\\(\\) \\{\\s*return \"([a-z]+)\";"), SRC.resolve("dev/colonyjournal/client"));
      assertTrue(tabs.size() >= 8, "expected the 8 tab classes, found " + tabs);
      map.put("journal.colonyjournal.tab.", tabs);
      map.put("journal.colonyjournal.sub.", tabs);

      List<String> status = new ArrayList<>();
      for (ColonyReport.Status s : ColonyReport.Status.values()) {
         status.add(s.name().toLowerCase(Locale.ROOT));
      }

      map.put("journal.colonyjournal.reason.", status);
      List<String> severity = new ArrayList<>();
      for (ColonyReport.Severity s : ColonyReport.Severity.values()) {
         severity.add(s.name().toLowerCase(Locale.ROOT));
      }

      map.put("journal.colonyjournal.severity.", severity);
      List<String> needs = new ArrayList<>();
      for (BuildingGraph.Edge e : BuildingGraph.EDGES) {
         needs.add(e.need());
      }

      map.put("journal.colonyjournal.need.", needs);
      // result keys sent by the server: reply(player, "posted", ...)
      map.put("msg.colonyjournal.", scan(Pattern.compile("reply\\(\\s*\\w+,\\s*\"([a-z_]+)\""), SRC.resolve("dev/colonyjournal/server")));
      map.put("journal.colonyjournal.goods.col_", List.of("item", "colony", "prosperity", "price", "stock"));
      List<String> columns = scan(Pattern.compile("(?m)^\\s+[A-Z]+\\(\"([a-z]+)\", \\d+,"), SRC.resolve("dev/colonyjournal/client/StatsTab.java").getParent());
      assertTrue(columns.size() >= 10, "expected the statistics columns, found " + columns);
      map.put("journal.colonyjournal.stats.h.", columns);
      List<String> statCols = new ArrayList<>(columns);
      statCols.addAll(List.of("income", "expense", "net"));
      map.put("journal.colonyjournal.stats.col.", statCols);
      map.put("journal.colonyjournal.economy.", List.of("income_tip", "trade_tip", "expense_tip", "net_tip"));
      map.put("journal.colonyjournal.legend.", List.of("line_ok", "line_missing", "line_optional", "line_idle"));
      List<String> chains = new ArrayList<>();
      for (BuildingGraph.Chain chain : BuildingGraph.CHAINS) {
         chains.add(chain.id());
         chains.add(chain.id() + ".desc");
      }

      map.put("journal.colonyjournal.chain.", chains);
      map.put("journal.colonyjournal.links.todo.", ChainAdvice.KEYS);
      map.put("journal.colonyjournal.links.light.", List.of("green", "yellow", "red"));
      map.put("journal.colonyjournal.links.edge.", List.of("ok", "broken", "optional", "idle"));
      map.put("journal.colonyjournal.ov.card.", List.of("chains", "treasury", "mood", "trade"));
      List<String> guide = new ArrayList<>();
      for (String id : GuideText.IDS) {
         guide.add(id + ".title");
         guide.add(id + ".body");
      }

      map.put("journal.colonyjournal.guide.", guide);
      return map;
   }

   @Test
   void bothLanguagesHaveTheSameKeys() {
      Set<String> onlyEn = new LinkedHashSet<>(en.keySet());
      onlyEn.removeAll(ru.keySet());
      Set<String> onlyRu = new LinkedHashSet<>(ru.keySet());
      onlyRu.removeAll(en.keySet());
      assertTrue(onlyEn.isEmpty(), "missing in ru_ru.json: " + onlyEn);
      assertTrue(onlyRu.isEmpty(), "missing in en_us.json: " + onlyRu);
   }

   @Test
   void everyKeyUsedByTheCodeExists() throws IOException {
      Map<String, List<String>> dynamic = dynamic();
      List<String> problems = new ArrayList<>();
      Set<String> literals = new LinkedHashSet<>();

      for (Path file : javaFiles()) {
         Matcher m = LITERAL.matcher(source(file));
         while (m.find()) {
            literals.add(m.group(1));
         }
      }

      assertFalse(literals.isEmpty(), "found no lang keys in the sources");

      for (String literal : literals) {
         if (en.containsKey(literal)) {
            if (!ru.containsKey(literal)) {
               problems.add("missing in ru_ru.json: " + literal);
            }

            continue;
         }

         List<String> suffixes = dynamic.get(literal);
         if (suffixes == null) {
            problems.add("unknown key or dynamic prefix (add it to LangKeysTest.dynamic): " + literal);
            continue;
         }

         for (String suffix : suffixes) {
            String key = literal + suffix;
            if (!en.containsKey(key)) {
               problems.add("missing in en_us.json: " + key);
            }

            if (!ru.containsKey(key)) {
               problems.add("missing in ru_ru.json: " + key);
            }
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void placeholdersMatchBetweenLanguages() {
      List<String> problems = new ArrayList<>();
      for (Map.Entry<String, String> e : en.entrySet()) {
         String other = ru.get(e.getKey());
         if (other != null && count(e.getValue()) != count(other)) {
            problems.add(e.getKey() + ": " + count(e.getValue()) + " placeholders in en, " + count(other) + " in ru");
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   private static int count(String text) {
      Matcher m = PLACEHOLDER.matcher(text);
      int n = 0;
      while (m.find()) {
         n++;
      }

      return n;
   }

   @Test
   void noBarePercentSigns() {
      List<String> problems = new ArrayList<>();
      for (Map<String, String> lang : List.of(en, ru)) {
         for (Map.Entry<String, String> e : lang.entrySet()) {
            String rest = PLACEHOLDER.matcher(e.getValue().replace("%%", "")).replaceAll("");
            if (rest.contains("%")) {
               problems.add(e.getKey() + " has a lone % (write %%): " + e.getValue());
            }
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void guideSectionsAreComplete() {
      for (Map<String, String> lang : List.of(en, ru)) {
         for (String id : GuideText.IDS) {
            String title = lang.get("journal.colonyjournal.guide." + id + ".title");
            String body = lang.get("journal.colonyjournal.guide." + id + ".body");
            assertTrue(title != null && !title.isBlank(), "title of " + id);
            assertTrue(body != null && !body.isBlank(), "body of " + id);
            assertTrue(title.length() <= 24, "keep list titles short: " + title);
            List<GuideText.Block> blocks = GuideText.parse(body);
            assertFalse(blocks.isEmpty(), "blocks of " + id);

            for (GuideText.Block b : blocks) {
               assertFalse(b.text().isBlank(), "empty block in " + id);
               if (b.icon() != null) {
                  checkIcon(id, b.icon());
               }
            }
         }
      }
   }

   private static void checkIcon(String section, String icon) {
      if (icon.startsWith("frame:")) {
         ColonyReport.Status.valueOf(icon.substring(6).toUpperCase(Locale.ROOT));
      } else if (icon.startsWith("color:")) {
         assertTrue(HEX6.matcher(icon.substring(6)).matches(), section + ": bad colour " + icon);
      } else if (icon.startsWith("line:")) {
         assertTrue(HEX6.matcher(icon.substring(5)).matches(), section + ": bad colour " + icon);
      } else if (!icon.equals("coin")) {
         ResourceLocation rl = ResourceLocation.tryParse(icon.contains(":") ? icon : "minecraft:" + icon);
         assertTrue(rl != null && BuiltInRegistries.ITEM.containsKey(rl), section + ": unknown item icon " + icon);
      }
   }

   @Test
   void everyTabHasAGuideSection() {
      for (String tab : List.of("overview", "map", "problems", "buildings", "economy", "market", "stats", "guide")) {
         assertTrue(GuideText.IDS.contains(GuideText.sectionFor(tab)), tab);
      }
   }
}
