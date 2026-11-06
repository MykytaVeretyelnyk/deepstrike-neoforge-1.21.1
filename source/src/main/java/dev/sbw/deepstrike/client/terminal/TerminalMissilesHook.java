package dev.sbw.deepstrike.client.terminal;

import dev.sbw.deepstrike.game.TerminalMissiles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Adds the Shahed | Missiles tabs to the Skyfront shahed terminal screen. Skyfront classes are only touched inside {@link SkyfrontTerminalLink}. */
@EventBusSubscriber(
   modid = "deepstrike",
   value = {Dist.CLIENT}
)
public final class TerminalMissilesHook {
   private static final String TERMINAL_SCREEN = "dev.krutlexik.skyfront.client.gui.ShahedTerminalScreen";

   private TerminalMissilesHook() {
   }

   @SubscribeEvent
   public static void onScreenInit(ScreenEvent.Init.Post event) {
      if (event.getScreen().getClass().getName().equals(TERMINAL_SCREEN)) {
         SkyfrontTerminalLink.addTabs(event);
      }
   }

   /** "Bound to terminal: x, y, z" on missiles and launcher items bound to a Skyfront terminal, like Skyfront's Shahed items. */
   @SubscribeEvent
   public static void onTooltip(ItemTooltipEvent event) {
      if (TerminalMissiles.isBindable(event.getItemStack())) {
         BlockPos terminal = TerminalMissiles.boundTerminal(event.getItemStack());
         if (terminal != null) {
            event.getToolTip()
               .add(
                  Component.translatable("tooltip.deepstrike.terminal.bound", new Object[]{terminal.getX(), terminal.getY(), terminal.getZ()})
                     .withStyle(ChatFormatting.AQUA)
               );
         }
      }
   }
}
