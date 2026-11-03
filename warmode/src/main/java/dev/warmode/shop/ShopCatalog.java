package dev.warmode.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.warmode.WarMode;
import dev.warmode.game.WarTeam;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The shop catalog. It lives in config/warmode/shop.json and is generated with defaults on first start.
 * Entries whose item is not installed (for example when Superb Warfare is absent) are skipped.
 */
public final class ShopCatalog {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Map<String, ShopEntry> ENTRIES = new LinkedHashMap<>();
   private static final List<String> MISSING = new ArrayList<>();

   private static Path file() {
      return FMLPaths.CONFIGDIR.get().resolve("warmode").resolve("shop.json");
   }

   public static synchronized void load() {
      ENTRIES.clear();
      MISSING.clear();
      Path path = file();

      try {
         if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());

            try (Writer writer = Files.newBufferedWriter(path)) {
               GSON.toJson(defaults(), writer);
            }
         }

         JsonElement root;

         try (Reader reader = Files.newBufferedReader(path)) {
            root = GSON.fromJson(reader, JsonElement.class);
         }

         if (root != null && root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
               if (element.isJsonObject()) {
                  parse(element.getAsJsonObject());
               }
            }
         }
      } catch (IOException | RuntimeException e) {
         WarMode.LOGGER.error("Could not read {}: {}", path, e.toString());
      }

      WarMode.LOGGER.info("War shop: {} entries available, {} skipped (item not installed){}", ENTRIES.size(), MISSING.size(), MISSING.isEmpty() ? "" : ": " + MISSING);
   }

   private static void parse(JsonObject o) {
      if (!o.has("item") || !o.has("price")) {
         return;
      }

      String item = o.get("item").getAsString();
      ResourceLocation id = ResourceLocation.tryParse(item);
      if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
         MISSING.add(item);
         return;
      }

      int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
      int price = Math.max(0, o.get("price").getAsInt());
      String category = o.has("category") ? o.get("category").getAsString() : "misc";
      String team = o.has("team") ? o.get("team").getAsString() : "any";
      String key = o.has("id") ? o.get("id").getAsString() : item + "#" + ENTRIES.size();
      ENTRIES.put(key, new ShopEntry(key, item, count, price, category, team));
   }

   public static synchronized ShopEntry get(String id) {
      return ENTRIES.get(id);
   }

   public static synchronized List<ShopEntry> forTeam(WarTeam team) {
      List<ShopEntry> out = new ArrayList<>();

      for (ShopEntry entry : ENTRIES.values()) {
         if (entry.availableTo(team)) {
            out.add(entry);
         }
      }

      return out;
   }

   private static JsonArray defaults() {
      JsonArray out = new JsonArray();
      // DeepStrike: missiles
      add(out, "deepstrike:fp5", 1, 600, "missiles");
      add(out, "deepstrike:fp5_graphite", 1, 700, "missiles");
      add(out, "deepstrike:storm_shadow", 1, 1200, "missiles");
      add(out, "deepstrike:jagm", 1, 500, "missiles");
      add(out, "deepstrike:spike_nlos_missile", 1, 450, "missiles");
      add(out, "deepstrike:peacekeeper", 1, 4500, "missiles");
      add(out, "deepstrike:warhead_he", 1, 250, "missiles");
      add(out, "deepstrike:warhead_penetrating", 1, 350, "missiles");
      add(out, "deepstrike:warhead_cluster", 1, 450, "missiles");
      add(out, "deepstrike:warhead_thermobaric", 1, 500, "missiles");
      add(out, "deepstrike:warhead_nuclear", 1, 4000, "missiles");
      // DeepStrike: launchers and command gear
      add(out, "deepstrike:fp5_launcher", 1, 900, "launchers");
      add(out, "deepstrike:fp5_launcher_graphite", 1, 1000, "launchers");
      add(out, "deepstrike:jagm_launcher", 1, 800, "launchers");
      add(out, "deepstrike:peacekeeper_platform", 1, 2500, "launchers");
      add(out, "deepstrike:deepstrike_laptop", 1, 400, "launchers");
      add(out, "deepstrike:jagm_camera_tablet", 1, 200, "launchers");
      add(out, "deepstrike:su24m", 1, 5000, "launchers");
      add(out, "deepstrike:siren_tower", 1, 150, "launchers");
      // DeepStrike: air defense
      add(out, "deepstrike:mim_104_patriot_truck", 1, 3000, "defense");
      add(out, "deepstrike:iron_dome", 1, 2500, "defense");
      add(out, "deepstrike:an_mpq_65", 1, 1500, "defense");
      add(out, "deepstrike:air_defense_terminal", 1, 300, "defense");
      add(out, "deepstrike:tamir", 4, 160, "defense");
      add(out, "deepstrike:pac_2", 1, 400, "defense");
      add(out, "deepstrike:pac_3", 1, 600, "defense");
      // Superb Warfare: weapons
      for (Object[] g : new Object[][] {
         {"m_4", 250}, {"ak_47", 250}, {"ak_12", 280}, {"hk_416", 300}, {"mp_5", 200}, {"qbz_95", 260}, {"qbz_191", 300},
         {"m_60", 450}, {"rpk", 350}, {"minigun", 1200}, {"mk_14", 350}, {"sks", 280}, {"awm", 500}, {"m_98b", 550},
         {"ntw_20", 900}, {"m_870", 220}, {"aa_12", 320}, {"glock_17", 120}, {"m_1911", 130}, {"mp_443", 120}
      }) {
         add(out, "superbwarfare:" + g[0], 1, (Integer)g[1], "weapons");
      }
      // Superb Warfare: heavy weapons and launchers
      add(out, "superbwarfare:rpg", 1, 700, "heavy");
      add(out, "superbwarfare:javelin", 1, 1500, "heavy");
      add(out, "superbwarfare:igla_9k38", 1, 1400, "heavy");
      add(out, "superbwarfare:secondary_cataclysm", 1, 2500, "heavy");
      add(out, "superbwarfare:m_79", 1, 500, "heavy");
      add(out, "superbwarfare:mortar_deployer", 1, 1200, "heavy");
      add(out, "superbwarfare:drone", 1, 600, "heavy");
      // Superb Warfare: ammunition and explosives
      add(out, "superbwarfare:rifle_ammo_box", 1, 80, "ammo");
      add(out, "superbwarfare:handgun_ammo_box", 1, 60, "ammo");
      add(out, "superbwarfare:shotgun_ammo_box", 1, 70, "ammo");
      add(out, "superbwarfare:sniper_ammo_box", 1, 90, "ammo");
      add(out, "superbwarfare:rpg_rocket_standard", 2, 200, "ammo");
      add(out, "superbwarfare:javelin_missile", 1, 400, "ammo");
      add(out, "superbwarfare:grenade_40mm", 4, 100, "ammo");
      add(out, "superbwarfare:mortar_shell", 4, 160, "ammo");
      add(out, "superbwarfare:hand_grenade", 2, 90, "ammo");
      add(out, "superbwarfare:c4_bomb", 1, 350, "ammo");
      add(out, "superbwarfare:claymore_mine", 2, 150, "ammo");
      add(out, "superbwarfare:medium_aerial_bomb", 1, 900, "ammo");
      add(out, "superbwarfare:large_aerial_bomb", 1, 1800, "ammo");
      // Superb Warfare: support
      add(out, "superbwarfare:medical_kit", 2, 80, "support");
      add(out, "superbwarfare:repair_tool", 1, 300, "support");
      add(out, "superbwarfare:armor_plate", 4, 100, "support");
      add(out, "superbwarfare:sandbag", 16, 80, "support");
      return out;
   }

   private static void add(JsonArray out, String item, int count, int price, String category) {
      JsonObject o = new JsonObject();
      o.addProperty("id", item.substring(item.indexOf(':') + 1) + (count > 1 ? "_x" + count : ""));
      o.addProperty("item", item);
      o.addProperty("count", count);
      o.addProperty("price", price);
      o.addProperty("category", category);
      o.addProperty("team", "any");
      out.add(o);
   }

   private ShopCatalog() {
   }
}
