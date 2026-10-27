package dev.sbw.deepstrike.registry;

import dev.sbw.deepstrike.game.AirDefenseMissileItem;
import dev.sbw.deepstrike.game.AirDefenseSpawnItem;
import dev.sbw.deepstrike.game.AirDefenseTerminalItem;
import dev.sbw.deepstrike.game.AirframeItem;
import dev.sbw.deepstrike.game.DeepStrikeCameraTabletItem;
import dev.sbw.deepstrike.game.DeepStrikeLaptopItem;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.ModelBlockItem;
import dev.sbw.deepstrike.game.ModularComponentItem;
import dev.sbw.deepstrike.game.PaintSprayItem;
import dev.sbw.deepstrike.game.VehicleDeployItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class BFItems {
   public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, "deepstrike");
   public static final DeferredHolder<Item, Item> SU24M = ITEMS.register("su24m", () -> new VehicleDeployItem(BFEntities.SU24M, new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> FP5 = ITEMS.register("fp5", () -> new MissileItem(0, true, new Properties().stacksTo(4)));
   public static final DeferredHolder<Item, Item> FP5_GRAPHITE = ITEMS.register("fp5_graphite", () -> new MissileItem(0, true, new Properties().stacksTo(4)));
   public static final DeferredHolder<Item, Item> STORM_SHADOW = ITEMS.register("storm_shadow", () -> new MissileItem(1, new Properties().stacksTo(2)));
   public static final DeferredHolder<Item, Item> JAGM = ITEMS.register("jagm", () -> new MissileItem(2, new Properties().stacksTo(4)));
   public static final DeferredHolder<Item, Item> PEACEKEEPER = ITEMS.register("peacekeeper", () -> new MissileItem(3, new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> SPIKE_NLOS_MISSILE = ITEMS.register("spike_nlos_missile", () -> new MissileItem(4, new Properties().stacksTo(4)));
   public static final DeferredHolder<Item, Item> PEACEKEEPER_PLATFORM = ITEMS.register(
      "peacekeeper_platform", () -> new ModelBlockItem((Block)BFBlocks.PEACEKEEPER_PLATFORM.get(), new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> DEEPSTRIKE = ITEMS.register("deepstrike_laptop", () -> new DeepStrikeLaptopItem(new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> JAGM_CAMERA_TABLET = ITEMS.register(
      "jagm_camera_tablet", () -> new DeepStrikeCameraTabletItem(new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> PAINT_SPRAY = ITEMS.register("paint_spray", () -> new PaintSprayItem(new Properties().stacksTo(1)));
   public static final DeferredHolder<Item, Item> FP5_LAUNCHER = ITEMS.register(
      "fp5_launcher", () -> new ModelBlockItem((Block)BFBlocks.FP5_LAUNCHER.get(), new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> FP5_LAUNCHER_GRAPHITE = ITEMS.register(
      "fp5_launcher_graphite", () -> new ModelBlockItem((Block)BFBlocks.FP5_LAUNCHER_GRAPHITE.get(), new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> JAGM_LAUNCHER = ITEMS.register(
      "jagm_launcher", () -> new ModelBlockItem((Block)BFBlocks.JAGM_LAUNCHER.get(), new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> SIREN_TOWER = ITEMS.register(
      "siren_tower", () -> new ModelBlockItem((Block)BFBlocks.SIREN_TOWER.get(), new Properties().stacksTo(4))
   );
   public static final DeferredHolder<Item, Item> PATRIOT_TRUCK = ITEMS.register(
      "mim_104_patriot_truck", () -> new AirDefenseSpawnItem(BFEntities.PATRIOT_TRUCK, new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> IRON_DOME = ITEMS.register(
      "iron_dome", () -> new AirDefenseSpawnItem(BFEntities.IRON_DOME, new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> AN_MPQ_65 = ITEMS.register(
      "an_mpq_65", () -> new AirDefenseSpawnItem(BFEntities.AN_MPQ_65, new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> AIR_DEFENSE_TERMINAL = ITEMS.register(
      "air_defense_terminal", () -> new AirDefenseTerminalItem(new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> TAMIR = ITEMS.register(
      "tamir", () -> new AirDefenseMissileItem("tamir", "tamir", 0.18F, new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> PAC_2 = ITEMS.register(
      "pac_2", () -> new AirDefenseMissileItem("pac_2", "pac_2", 0.14F, new Properties().stacksTo(4))
   );
   public static final DeferredHolder<Item, Item> PAC_3 = ITEMS.register(
      "pac_3", () -> new AirDefenseMissileItem("pac_3", "pac_3", 0.16F, new Properties().stacksTo(4))
   );
   public static final DeferredHolder<Item, Item> FP5_AIRFRAME = ITEMS.register("fp5_airframe", () -> new AirframeItem(MissileKind.FP5, new Properties().stacksTo(4)));
   public static final DeferredHolder<Item, Item> STORM_SHADOW_AIRFRAME = ITEMS.register(
      "storm_shadow_airframe", () -> new AirframeItem(MissileKind.STORM_SHADOW, new Properties().stacksTo(2))
   );
   public static final DeferredHolder<Item, Item> JAGM_AIRFRAME = ITEMS.register(
      "jagm_airframe", () -> new AirframeItem(MissileKind.JAGM, new Properties().stacksTo(4))
   );
   public static final DeferredHolder<Item, Item> PEACEKEEPER_AIRFRAME = ITEMS.register(
      "peacekeeper_airframe", () -> new AirframeItem(MissileKind.PEACEKEEPER, new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> SPIKE_NLOS_AIRFRAME = ITEMS.register(
      "spike_nlos_airframe", () -> new AirframeItem(MissileKind.SPIKE_NLOS, new Properties().stacksTo(4))
   );
   public static final DeferredHolder<Item, Item> WARHEAD_HE = ITEMS.register(
      "warhead_he", () -> new ModularComponentItem("warhead", "he", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> WARHEAD_PENETRATING = ITEMS.register(
      "warhead_penetrating", () -> new ModularComponentItem("warhead", "penetrating", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> WARHEAD_GRAPHITE = ITEMS.register(
      "warhead_graphite", () -> new ModularComponentItem("warhead", "graphite", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> WARHEAD_CLUSTER = ITEMS.register(
      "warhead_cluster", () -> new ModularComponentItem("warhead", "cluster", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> WARHEAD_THERMOBARIC = ITEMS.register(
      "warhead_thermobaric", () -> new ModularComponentItem("warhead", "thermobaric", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> WARHEAD_NUCLEAR = ITEMS.register(
      "warhead_nuclear", () -> new ModularComponentItem("warhead", "nuclear", new Properties().stacksTo(1))
   );
   public static final DeferredHolder<Item, Item> WARHEAD_SHAPED_CHARGE = ITEMS.register(
      "warhead_shaped_charge", () -> new ModularComponentItem("warhead", "shaped_charge", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> ROCKET_ENGINE = ITEMS.register(
      "rocket_engine", () -> new ModularComponentItem("engine", "rocket", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> TURBOJET_ENGINE = ITEMS.register(
      "turbojet_engine", () -> new ModularComponentItem("engine", "turbojet", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> MODULE_RANGE = ITEMS.register(
      "module_range", () -> new ModularComponentItem("module", "range", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> MODULE_CAMERA = ITEMS.register(
      "module_camera", () -> new ModularComponentItem("module", "camera", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> MODULE_ECM = ITEMS.register(
      "module_ecm", () -> new ModularComponentItem("module", "ecm", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> MODULE_BOOSTER = ITEMS.register(
      "module_booster", () -> new ModularComponentItem("module", "booster", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> MODULE_GPS = ITEMS.register(
      "module_gps", () -> new ModularComponentItem("module", "gps", new Properties().stacksTo(8))
   );
   public static final DeferredHolder<Item, Item> STEEL_FRAME = part("steel_frame");
   public static final DeferredHolder<Item, Item> CONTROL_CHIP = part("control_chip");
   public static final DeferredHolder<Item, Item> OPTICAL_SEEKER = part("optical_seeker");
   public static final DeferredHolder<Item, Item> AERODYNAMIC_FINS = part("aerodynamic_fins");
   public static final DeferredHolder<Item, Item> REINFORCED_CASING = part("reinforced_casing");
   public static final DeferredHolder<Item, Item> ROCKET_NOSE = part("rocket_nose");
   public static final DeferredHolder<Item, Item> GUIDANCE_CORE = part("guidance_core");
   public static final DeferredHolder<Item, Item> STAGE_ONE = part("stage_one");
   public static final DeferredHolder<Item, Item> STAGE_TWO = part("stage_two");
   public static final DeferredHolder<Item, Item> STAGE_THREE = part("stage_three");
   public static final DeferredHolder<Item, Item> REENTRY_BUS = part("reentry_bus");
   public static final DeferredHolder<Item, Item> LAUNCHER_FRAME = part("launcher_frame");
   public static final DeferredHolder<Item, Item> VEHICLE_CHASSIS = part("vehicle_chassis");
   public static final DeferredHolder<Item, Item> RADAR_ARRAY = part("radar_array");
   public static final DeferredHolder<Item, Item> DEFENSE_CONTROLLER = part("defense_controller");
   public static final DeferredHolder<Item, Item> MISSILE_ASSEMBLY_STATION = ITEMS.register(
      "missile_assembly_station", () -> new BlockItem((Block)BFBlocks.MISSILE_ASSEMBLY_STATION.get(), new Properties())
   );
   public static final DeferredHolder<Item, Item> COMMAND_RELAY = ITEMS.register(
      "command_relay", () -> new BlockItem((Block)BFBlocks.COMMAND_RELAY.get(), new Properties())
   );
   public static final DeferredHolder<Item, Item> SPIKE_NLOS = ITEMS.register(
      "spike_nlos_jltv", () -> new AirDefenseSpawnItem(BFEntities.SPIKE_NLOS, new Properties().stacksTo(1))
   );

   private static DeferredHolder<Item, Item> part(String id) {
      return ITEMS.register(id, () -> new Item(new Properties().stacksTo(16)));
   }

   private BFItems() {
   }
}
