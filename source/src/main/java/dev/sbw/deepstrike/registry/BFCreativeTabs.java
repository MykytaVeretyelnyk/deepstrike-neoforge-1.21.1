package dev.sbw.deepstrike.registry;

import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class BFCreativeTabs {
   public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "deepstrike");
   public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SYSTEMS = TABS.register(
      "systems",
      () -> CreativeModeTab.builder()
         .title(Component.literal("DeepStrike"))
         .icon(() -> new ItemStack((ItemLike)BFItems.JAGM.get()))
         .displayItems((parameters, output) -> {
            output.accept((ItemLike)BFItems.DEEPSTRIKE.get());
            output.accept((ItemLike)BFItems.JAGM_CAMERA_TABLET.get());
            output.accept((ItemLike)BFItems.AIR_DEFENSE_TERMINAL.get());
            output.accept((ItemLike)BFItems.COMMAND_RELAY.get());
            output.accept((ItemLike)BFItems.MISSILE_ASSEMBLY_STATION.get());
            output.accept((ItemLike)BFItems.PAINT_SPRAY.get());
            output.accept((ItemLike)BFItems.FP5_LAUNCHER.get());
            output.accept((ItemLike)BFItems.JAGM_LAUNCHER.get());
            output.accept((ItemLike)BFItems.PEACEKEEPER_PLATFORM.get());
            output.accept((ItemLike)BFItems.SU24M.get());
            output.accept((ItemLike)BFItems.SPIKE_NLOS.get());
            output.accept(combatReady((Item)BFItems.FP5.get(), MissileKind.FP5));
            output.accept(combatReady((Item)BFItems.STORM_SHADOW.get(), MissileKind.STORM_SHADOW));
            output.accept(combatReady((Item)BFItems.JAGM.get(), MissileKind.JAGM));
            output.accept(combatReady((Item)BFItems.PEACEKEEPER.get(), MissileKind.PEACEKEEPER));
            output.accept(combatReady((Item)BFItems.SPIKE_NLOS_MISSILE.get(), MissileKind.SPIKE_NLOS));
            output.accept(emptyCasing((Item)BFItems.FP5_AIRFRAME.get(), MissileKind.FP5));
            output.accept(emptyCasing((Item)BFItems.STORM_SHADOW_AIRFRAME.get(), MissileKind.STORM_SHADOW));
            output.accept(emptyCasing((Item)BFItems.JAGM_AIRFRAME.get(), MissileKind.JAGM));
            output.accept(emptyCasing((Item)BFItems.PEACEKEEPER_AIRFRAME.get(), MissileKind.PEACEKEEPER));
            output.accept(emptyCasing((Item)BFItems.SPIKE_NLOS_AIRFRAME.get(), MissileKind.SPIKE_NLOS));
            output.accept((ItemLike)BFItems.PATRIOT_TRUCK.get());
            output.accept((ItemLike)BFItems.AN_MPQ_65.get());
            output.accept((ItemLike)BFItems.IRON_DOME.get());
            output.accept((ItemLike)BFItems.TAMIR.get());
            output.accept((ItemLike)BFItems.PAC_2.get());
            output.accept((ItemLike)BFItems.PAC_3.get());
            output.accept((ItemLike)BFItems.SIREN_TOWER.get());
            output.accept((ItemLike)BFItems.WARHEAD_HE.get());
            output.accept((ItemLike)BFItems.WARHEAD_PENETRATING.get());
            output.accept((ItemLike)BFItems.WARHEAD_GRAPHITE.get());
            output.accept((ItemLike)BFItems.WARHEAD_CLUSTER.get());
            output.accept((ItemLike)BFItems.WARHEAD_THERMOBARIC.get());
            output.accept((ItemLike)BFItems.WARHEAD_NUCLEAR.get());
            output.accept((ItemLike)BFItems.WARHEAD_SHAPED_CHARGE.get());
            output.accept((ItemLike)BFItems.ROCKET_ENGINE.get());
            output.accept((ItemLike)BFItems.TURBOJET_ENGINE.get());
            output.accept((ItemLike)BFItems.MODULE_RANGE.get());
            output.accept((ItemLike)BFItems.MODULE_CAMERA.get());
            output.accept((ItemLike)BFItems.MODULE_ECM.get());
            output.accept((ItemLike)BFItems.MODULE_BOOSTER.get());
            output.accept((ItemLike)BFItems.MODULE_GPS.get());
            output.accept((ItemLike)BFItems.STEEL_FRAME.get());
            output.accept((ItemLike)BFItems.REINFORCED_CASING.get());
            output.accept((ItemLike)BFItems.AERODYNAMIC_FINS.get());
            output.accept((ItemLike)BFItems.ROCKET_NOSE.get());
            output.accept((ItemLike)BFItems.GUIDANCE_CORE.get());
            output.accept((ItemLike)BFItems.CONTROL_CHIP.get());
            output.accept((ItemLike)BFItems.OPTICAL_SEEKER.get());
            output.accept((ItemLike)BFItems.STAGE_ONE.get());
            output.accept((ItemLike)BFItems.STAGE_TWO.get());
            output.accept((ItemLike)BFItems.STAGE_THREE.get());
            output.accept((ItemLike)BFItems.REENTRY_BUS.get());
            output.accept((ItemLike)BFItems.LAUNCHER_FRAME.get());
            output.accept((ItemLike)BFItems.VEHICLE_CHASSIS.get());
            output.accept((ItemLike)BFItems.RADAR_ARRAY.get());
            output.accept((ItemLike)BFItems.DEFENSE_CONTROLLER.get());
         })
         .build()
   );

   private static ItemStack combatReady(Item item, MissileKind kind) {
      ItemStack stack = new ItemStack(item);
      int maxFuel = MissileData.calculateDefaultMaxFuel(kind, List.of());
      new MissileData(kind, "he", "rocket", List.of(), MissileData.defaultCamo(kind), maxFuel, maxFuel).writeToItemStack(stack);
      return stack;
   }

   private static ItemStack emptyCasing(Item item, MissileKind kind) {
      ItemStack stack = new ItemStack(item);
      int maxFuel = MissileData.calculateDefaultMaxFuel(kind, List.of());
      new MissileData(kind, "none", "rocket", List.of(), MissileData.defaultCamo(kind), 0, maxFuel).writeToItemStack(stack);
      return stack;
   }
}
