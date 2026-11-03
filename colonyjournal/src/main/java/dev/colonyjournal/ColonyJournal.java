package dev.colonyjournal;

import com.mojang.logging.LogUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(ColonyJournal.MOD_ID)
public final class ColonyJournal {
   public static final String MOD_ID = "colonyjournal";
   public static final Logger LOGGER = LogUtils.getLogger();
   public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

   /** Right click opens the journal for the colony around the player. */
   public static final DeferredItem<Item> JOURNAL = ITEMS.register("colony_journal", () -> new Item(new Item.Properties().stacksTo(1)) {
      @Override
      public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
         if (level.isClientSide) {
            dev.colonyjournal.client.JournalClient.open();
         }

         return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
      }
   });

   public ColonyJournal(IEventBus bus) {
      ITEMS.register(bus);
      bus.addListener((BuildCreativeModeTabContentsEvent event) -> {
         if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(JOURNAL.get());
         }
      });
      if (FMLEnvironment.dist == Dist.CLIENT) {
         dev.colonyjournal.client.JournalClient.init(bus);
      }

      LOGGER.info("Colony Journal loaded");
   }
}
