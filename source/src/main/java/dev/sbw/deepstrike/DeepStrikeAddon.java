package dev.sbw.deepstrike;

import com.mojang.logging.LogUtils;
import dev.sbw.deepstrike.client.ClientSetup;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.AirDefenseTargets;
import dev.sbw.deepstrike.game.CameraTerrainServer;
import dev.sbw.deepstrike.game.CommandCenterData;
import dev.sbw.deepstrike.game.CommandDefense;
import dev.sbw.deepstrike.game.DefenseRuntime;
import dev.sbw.deepstrike.game.PaintSprayItem;
import dev.sbw.deepstrike.network.AirRaidSirenNetwork;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.network.FrequencyNetwork;
import dev.sbw.deepstrike.network.IronDomeNetwork;
import dev.sbw.deepstrike.network.MissileCameraStream;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.network.SpikeNlosNetwork;
import dev.sbw.deepstrike.network.Su24mNetwork;
import dev.sbw.deepstrike.network.UnitJournalNetwork;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFCreativeTabs;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.registry.BFMenus;
import dev.sbw.deepstrike.registry.BFParticles;
import dev.sbw.deepstrike.registry.BFSounds;
import dev.sbw.deepstrike.validation.AirDefenseReview;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.ForgeChunkManager;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import dev.sbw.deepstrike.compat.TickEvent;
import dev.sbw.deepstrike.compat.net.SimpleChannel;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import org.slf4j.Logger;

@Mod("deepstrike")
public final class DeepStrikeAddon {
   public static final String MOD_ID = "deepstrike";
   public static final Logger LOGGER = LogUtils.getLogger();
   public static ModContainer CONTAINER;

   public DeepStrikeAddon(IEventBus bus, ModContainer container) {
      CONTAINER = container;
      TickEvent.install(NeoForge.EVENT_BUS);
      bus.addListener(SimpleChannel::registerAll);
      bus.addListener(ForgeChunkManager::register);
      bus.addListener(
         (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, BFBlocks.MISSILE_ASSEMBLY_BE.get(), (be, side) -> be.itemHandler()
         )
      );
      BFBlocks.BLOCKS.register(bus);
      BFBlocks.BLOCK_ENTITY_TYPES.register(bus);
      BFItems.ITEMS.register(bus);
      BFEntities.ENTITY_TYPES.register(bus);
      BFMenus.TYPES.register(bus);
      BFParticles.TYPES.register(bus);
      BFSounds.SOUNDS.register(bus);
      BFCreativeTabs.TABS.register(bus);
      container.registerConfig(Type.SERVER, DefenseRuntime.SPEC, "dsm-server.toml");
      ForgeChunkManager.setForcedChunkLoadingCallback("deepstrike", DefenseRuntime::validateTickets);
      NeoForge.EVENT_BUS.addListener(DefenseRuntime::join);
      NeoForge.EVENT_BUS.addListener(DefenseRuntime::tick);
      NeoForge.EVENT_BUS.addListener(DefenseRuntime::commands);
      DeepStrikeNetwork.register();
      CommandTabletNetwork.register();
      NeoForge.EVENT_BUS.addListener(CameraTerrainServer::tick);
      NeoForge.EVENT_BUS.addListener(CameraTerrainServer::stop);
      NeoForge.EVENT_BUS.addListener(CommandDefense::interact);
      NeoForge.EVENT_BUS.addListener(CommandCenterData::tick);
      NeoForge.EVENT_BUS.addListener(AirDefenseTargets::join);
      NeoForge.EVENT_BUS.addListener(AirDefenseTargets::tick);
      NeoForge.EVENT_BUS.addListener(AirDefenseReview::tick);
      UnitJournalNetwork.register();
      NeoForge.EVENT_BUS.addListener(UnitJournalNetwork::interact);
      PatriotDefenseNetwork.register();
      IronDomeNetwork.register();
      AirRaidSirenNetwork.register();
      SpikeNlosNetwork.register();
      Su24mNetwork.register();
      FrequencyNetwork.register();
      NeoForge.EVENT_BUS.addListener(ActiveFlights::onJoin);
      NeoForge.EVENT_BUS.addListener(ActiveFlights::tick);
      NeoForge.EVENT_BUS.addListener(IronDomeNetwork::tick);
      NeoForge.EVENT_BUS.addListener(MissileCameraStream::tick);
      NeoForge.EVENT_BUS.addListener(MissileCameraStream::onLogout);
      NeoForge.EVENT_BUS.addListener(MissileCameraStream::onLogin);
      NeoForge.EVENT_BUS.addListener(PaintSprayItem::entityInteract);
      NeoForge.EVENT_BUS.addListener(PaintSprayItem::entityInteractSpecific);
      if (FMLEnvironment.dist == Dist.CLIENT) {
         ClientSetup.register(bus);
      }
      bus.addListener(DeepStrikeAddon::addCreativeItems);
   }

   private static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey() == CreativeModeTabs.COMBAT) {
         event.accept(BFItems.FP5.get());
         event.accept(BFItems.FP5_GRAPHITE.get());
         event.accept(BFItems.STORM_SHADOW.get());
         event.accept(BFItems.JAGM.get());
         event.accept(BFItems.FP5_LAUNCHER.get());
         event.accept(BFItems.FP5_LAUNCHER_GRAPHITE.get());
         event.accept(BFItems.JAGM_LAUNCHER.get());
         event.accept(BFItems.DEEPSTRIKE.get());
         event.accept(BFItems.JAGM_CAMERA_TABLET.get());
         event.accept(BFItems.PATRIOT_TRUCK.get());
         event.accept(BFItems.IRON_DOME.get());
         event.accept(BFItems.AN_MPQ_65.get());
         event.accept(BFItems.PAINT_SPRAY.get());
         event.accept(BFItems.AIR_DEFENSE_TERMINAL.get());
         event.accept(BFItems.TAMIR.get());
         event.accept(BFItems.PAC_2.get());
         event.accept(BFItems.PAC_3.get());
         event.accept(BFItems.SIREN_TOWER.get());
      }
   }
}
