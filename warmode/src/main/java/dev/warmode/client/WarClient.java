package dev.warmode.client;

import dev.warmode.WarMode;
import dev.warmode.game.WarTeam;
import dev.warmode.net.WarNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client side: keeps the last round state and draws the status bar. */
public final class WarClient {
   public static WarNet.State state;

   public static void init(IEventBus bus) {
      bus.addListener(WarClient::registerLayers);
   }

   private static void registerLayers(RegisterGuiLayersEvent event) {
      event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WarMode.MOD_ID, "hud"), WarClient::renderHud);
   }

   public static void onState(WarNet.State payload) {
      state = payload;
   }

   public static void openShop(WarNet.OpenShop payload) {
      Minecraft.getInstance().setScreen(new ShopScreen(payload.terminal(), payload.entries()));
   }

   public static int bankOf(WarNet.State s, WarTeam team) {
      return team == WarTeam.BLUE ? s.blueBank() : s.redBank();
   }

   private static String clock(int seconds) {
      return String.format("%d:%02d", seconds / 60, seconds % 60);
   }

   private static void renderHud(GuiGraphics g, DeltaTracker delta) {
      Minecraft mc = Minecraft.getInstance();
      WarNet.State s = state;
      if (s == null || mc.options.hideGui || mc.player == null || s.team() < 0 && !s.running()) {
         return;
      }

      Font font = mc.font;
      int width = g.guiWidth();
      int center = width / 2;
      int y = 4;
      String blue = s.blueBank() + "  [" + s.blueScore() + "]";
      String red = "[" + s.redScore() + "]  " + s.redBank();
      String middle = s.running() ? (s.secondsLeft() >= 0 ? clock(s.secondsLeft()) : "∞") : Component.translatable("hud.warmode.idle").getString();
      int half = 62;
      g.fill(center - half - 70, y - 2, center + half + 70, y + 22, 0x99000000);
      g.fill(center - half - 70, y - 2, center - 1, y, 0xFF000000 | WarTeam.BLUE.color);
      g.fill(center + 1, y - 2, center + half + 70, y, 0xFF000000 | WarTeam.RED.color);
      g.drawString(font, blue, center - half - 62, y + 3, 0xFF000000 | WarTeam.BLUE.color, true);
      g.drawString(font, red, center + half + 62 - font.width(red), y + 3, 0xFF000000 | WarTeam.RED.color, true);
      g.drawCenteredString(font, middle, center, y + 3, 0xFFFFFFFF);
      if (s.blueHq() >= 0 || s.redHq() >= 0) {
         String hq = Component.translatable("hud.warmode.hq", Math.max(0, s.blueHq()), Math.max(0, s.redHq())).getString();
         g.drawCenteredString(font, hq, center, y + 13, 0xFFCCCCCC);
      }

      WarTeam mine = WarTeam.byOrdinal(s.team());
      if (mine != null) {
         Component line = Component.translatable("hud.warmode.your_team", mine.title(), bankOf(s, mine), s.bankCap());
         g.drawCenteredString(font, line, center, y + 26, 0xFFFFFFFF);
      }
   }

   private WarClient() {
   }
}
