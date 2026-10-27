package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MissileData {
   public static final String WARHEAD_NONE = "none";
   public static final String WARHEAD_HE = "he";
   public static final String WARHEAD_PENETRATING = "penetrating";
   public static final String WARHEAD_GRAPHITE = "graphite";
   public static final String WARHEAD_CLUSTER = "cluster";
   public static final String WARHEAD_THERMOBARIC = "thermobaric";
   public static final String WARHEAD_NUCLEAR = "nuclear";
   public static final String WARHEAD_SHAPED_CHARGE = "shaped_charge";
   public static final String ENGINE_ROCKET = "rocket";
   public static final String ENGINE_TURBOJET = "turbojet";
   public static final String MODULE_RANGE = "range";
   public static final String MODULE_CAMERA = "camera";
   public static final String MODULE_ECM = "ecm";
   public static final String MODULE_BOOSTER = "booster";
   public static final String MODULE_GPS = "gps";
   public static final String CAMO_DEFAULT = "default";
   public static final String CAMO_GRAPHITE = "graphite";
   public static final String CAMO_OLIVE = "olive";
   public static final String CAMO_DESERT = "desert";
   public static final String CAMO_GRAY = "gray";
   public static final String CAMO_STEALTH = "stealth";
   public static final String CAMO_SKY = "sky";
   public static final String CAMO_PINK = "pink";
   public static final String CAMO_SEA = "sea";
   private final MissileKind kind;
   private final String warhead;
   private final String engine;
   private final List<String> modules;
   private final String camo;
   private int fuel;
   private int maxFuel;

   public static String defaultCamo(MissileKind kind) {
      return kind == MissileKind.FP5 ? "graphite" : "default";
   }

   public MissileData(MissileKind kind, String warhead, String engine, List<String> modules, String camo, int fuel, int maxFuel) {
      this.kind = kind;
      this.warhead = warhead != null ? warhead : "he";
      this.engine = engine != null ? engine : "rocket";
      this.modules = modules != null ? new ArrayList<>(modules) : new ArrayList<>();
      this.camo = camo != null ? camo : defaultCamo(kind);
      this.fuel = fuel;
      this.maxFuel = maxFuel > 0 ? maxFuel : calculateDefaultMaxFuel(kind, this.modules);
   }

   public static int calculateDefaultMaxFuel(MissileKind kind, List<String> modules) {
      int base = switch (kind) {
         case FP5 -> 1200;
         case STORM_SHADOW -> 1500;
         case JAGM -> 800;
         case PEACEKEEPER -> 3000;
         case SPIKE_NLOS -> 1000;
      };
      if (modules != null && modules.contains("range")) {
         base = (int)(base * 1.4);
      }

      return base;
   }

   public static MissileData fromItemStack(ItemStack stack) {
      if (stack.isEmpty()) {
         return null;
      } else {
         MissileKind kind = stack.getItem() instanceof MissileItem m ? m.kind() : MissileKind.FP5;
         boolean isAirframe = stack.getItem() instanceof AirframeItem;
         CompoundTag tag = NbtCompat.getTag(stack);
         if (tag != null && tag.contains("DeepStrikeMissile")) {
            CompoundTag ds = tag.getCompound("DeepStrikeMissile");
            String warhead = ds.getString("Warhead");
            if (warhead.isEmpty()) {
               warhead = isAirframe ? "none" : "he";
            }

            String engine = ds.getString("Engine");
            if (engine.isEmpty()) {
               engine = "rocket";
            }

            String camo = ds.getString("Camo");
            if (camo.isEmpty()) {
               camo = defaultCamo(kind);
            }

            List<String> modules = new ArrayList<>();
            if (ds.contains("Modules", 9)) {
               ListTag list = ds.getList("Modules", 8);

               for (int i = 0; i < list.size(); i++) {
                  modules.add(list.getString(i));
               }
            }

            int maxFuel = ds.getInt("MaxFuel");
            if (maxFuel <= 0) {
               maxFuel = calculateDefaultMaxFuel(kind, modules);
            }

            int fuel = ds.getInt("Fuel");
            return new MissileData(kind, warhead, engine, modules, camo, fuel, maxFuel);
         } else {
            boolean legacyGraphiteItem = stack.is((Item)BFItems.FP5_GRAPHITE.get());
            String defaultWarhead = isAirframe ? "none" : (legacyGraphiteItem ? "graphite" : "he");
            int maxF = calculateDefaultMaxFuel(kind, List.of());
            int initialFuel = isAirframe ? 0 : maxF;
            return new MissileData(kind, defaultWarhead, "rocket", List.of(), defaultCamo(kind), initialFuel, maxF);
         }
      }
   }

   public void writeToItemStack(ItemStack stack) {
      CompoundTag ds = new CompoundTag();
      ds.putString("Kind", this.kind.name());
      ds.putString("Warhead", this.warhead);
      ds.putString("Engine", this.engine);
      ds.putString("Camo", this.camo);
      ds.putInt("Fuel", this.fuel);
      ds.putInt("MaxFuel", this.maxFuel);
      ListTag list = new ListTag();

      for (String mod : this.modules) {
         list.add(StringTag.valueOf(mod));
      }

      ds.put("Modules", list);
      NbtCompat.update(stack, t -> t.put("DeepStrikeMissile", ds));
   }

   public MissileKind kind() {
      return this.kind;
   }

   public String warhead() {
      return this.warhead;
   }

   public String engine() {
      return this.engine;
   }

   public List<String> modules() {
      return List.copyOf(this.modules);
   }

   public String camo() {
      return this.camo;
   }

   public int fuel() {
      return this.fuel;
   }

   public int maxFuel() {
      return this.maxFuel;
   }

   public void setFuel(int fuel) {
      this.fuel = Math.max(0, Math.min(this.maxFuel, fuel));
   }

   public int addFuel(int amount) {
      int added = Math.min(amount, this.maxFuel - this.fuel);
      this.fuel += added;
      return added;
   }

   public boolean isFueled() {
      return this.fuel > 0;
   }

   public boolean isAirframeOnly() {
      return "none".equals(this.warhead);
   }

   public int calculateMaxRange() {
      if (this.kind == MissileKind.PEACEKEEPER) {
         return 6000 + (this.modules.contains("range") ? 2400 : 0) + (this.modules.contains("booster") ? 1200 : 0);
      } else {
         int r = this.kind.maxRange;
         if ("turbojet".equals(this.engine)) {
            r = (int)(r * 1.25);
         }

         if (this.modules.contains("range")) {
            r = (int)(r * 1.4);
         }

         return r;
      }
   }

   public int calculateMaxSpeed() {
      if (this.kind == MissileKind.PEACEKEEPER) {
         return this.kind.maxSpeed;
      } else {
         int s = this.kind.maxSpeed;
         if ("turbojet".equals(this.engine)) {
            s = (int)(s * 1.15);
         }

         if (this.modules.contains("booster")) {
            s = (int)(s * 1.25);
         }

         return s;
      }
   }

   public double calculateEcmDebuff() {
      if (this.kind == MissileKind.PEACEKEEPER) {
         return this.modules.contains("ecm") ? 0.05 : 0.0;
      } else {
         double debuff = 0.0;
         if ("stealth".equals(this.camo)) {
            debuff += 0.1;
         }

         if (this.modules.contains("ecm")) {
            debuff += 0.35;
         }

         return debuff;
      }
   }

   public boolean hasCamera() {
      return this.kind == MissileKind.JAGM || this.kind == MissileKind.SPIKE_NLOS || this.modules.contains("camera");
   }

   public boolean supportsWarhead() {
      return switch (this.kind) {
         case PEACEKEEPER -> List.of("none", "he", "thermobaric", "cluster", "nuclear").contains(this.warhead);
         case SPIKE_NLOS -> List.of("none", "he", "shaped_charge").contains(this.warhead);
         default -> !"nuclear".equals(this.warhead) && !"shaped_charge".equals(this.warhead);
      };
   }

   public boolean hasEcm() {
      return this.modules.contains("ecm") || "stealth".equals(this.camo);
   }

   public boolean hasBooster() {
      return this.modules.contains("booster");
   }

   public boolean hasRangeModule() {
      return this.modules.contains("range");
   }

   public boolean hasGps() {
      return this.modules.contains("gps");
   }
}
