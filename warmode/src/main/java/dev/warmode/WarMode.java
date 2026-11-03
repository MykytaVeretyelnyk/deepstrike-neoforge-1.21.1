package dev.warmode;

import com.mojang.logging.LogUtils;
import dev.warmode.block.HeadquartersBlock;
import dev.warmode.block.ShopTerminalBlock;
import dev.warmode.command.WarCommands;
import dev.warmode.game.DeepStrikeHooks;
import dev.warmode.game.SbwHooks;
import dev.warmode.game.WarEvents;
import dev.warmode.game.WarManager;
import dev.warmode.net.WarNet;
import dev.warmode.shop.ShopCatalog;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(WarMode.MOD_ID)
public final class WarMode {
   public static final String MOD_ID = "warmode";
   public static final Logger LOGGER = LogUtils.getLogger();
   public static boolean SBW;
   public static boolean DEEPSTRIKE;

   public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
   public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
   public static final DeferredBlock<ShopTerminalBlock> SHOP_TERMINAL = BLOCKS.register(
      "shop_terminal", () -> new ShopTerminalBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).noLootTable().lightLevel(state -> 8))
   );
   public static final DeferredBlock<HeadquartersBlock> HEADQUARTERS = BLOCKS.register(
      "headquarters", () -> new HeadquartersBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F).noLootTable().lightLevel(state -> 10))
   );
   public static final DeferredItem<BlockItem> SHOP_TERMINAL_ITEM = ITEMS.registerSimpleBlockItem("shop_terminal", SHOP_TERMINAL);
   public static final DeferredItem<BlockItem> HEADQUARTERS_ITEM = ITEMS.registerSimpleBlockItem("headquarters", HEADQUARTERS);

   public WarMode(IEventBus bus) {
      SBW = ModList.get().isLoaded("superbwarfare");
      DEEPSTRIKE = ModList.get().isLoaded("deepstrike");
      BLOCKS.register(bus);
      ITEMS.register(bus);
      bus.addListener(WarNet::register);
      bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
         if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(SHOP_TERMINAL_ITEM.get());
            event.accept(HEADQUARTERS_ITEM.get());
         }
      });
      NeoForge.EVENT_BUS.register(new WarEvents());
      if (SBW) {
         NeoForge.EVENT_BUS.register(new SbwHooks());
      }

      if (DEEPSTRIKE) {
         NeoForge.EVENT_BUS.register(new DeepStrikeHooks());
      }

      NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> WarCommands.register(event.getDispatcher()));
      NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
         ShopCatalog.load();
         WarManager.ensureScoreboardTeams(event.getServer());
      });
      if (FMLEnvironment.dist == Dist.CLIENT) {
         dev.warmode.client.WarClient.init(bus);
      }

      LOGGER.info("War Mode loaded (Superb Warfare: {}, DeepStrike: {})", SBW, DEEPSTRIKE);
   }
}
