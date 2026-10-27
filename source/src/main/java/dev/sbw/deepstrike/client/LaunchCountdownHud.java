package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Post;

public final class LaunchCountdownHud {
   private static ClientLevel level;
   private static DeepStrikeNetwork.LaunchCountdown countdown;
   private static long receivedNanos;

   public static void accept(DeepStrikeNetwork.LaunchCountdown message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         LaunchEffectWarmup.accept(message);
         if (level != mc.level) {
            level = mc.level;
            countdown = null;
         }

         if (!message.active()) {
            if (countdown != null && Objects.equals(countdown.mission(), message.mission()) && countdown.launcher().equals(message.launcher())) {
               countdown = null;
            }
         } else if (countdown == null || !Objects.equals(countdown.mission(), message.mission()) || message.serverTick() >= countdown.serverTick()) {
            boolean first = countdown == null || !Objects.equals(countdown.mission(), message.mission());
            countdown = message;
            receivedNanos = System.nanoTime();
            if (first && (MapProviders.isNativeMap(mc.screen) || mc.screen instanceof DeepStrikeScreen || mc.screen instanceof SpikeNlosMapScreen)) {
               mc.setScreen(null);
            }
         }
      }
   }

   public static void render(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (level != mc.level) {
         level = mc.level;
         countdown = null;
      }

      if (countdown != null && mc.screen == null) {
         long remaining = Math.max(0L, countdown.launchAtTick() - countdown.serverTick());
         boolean stale = System.nanoTime() - receivedNanos > 1500000000L;
         Component title = stale
            ? Component.translatable("hud.deepstrike.launch.waiting")
            : (
               remaining > 0L
                  ? Component.translatable("hud.deepstrike.launch.countdown", new Object[]{(remaining + 19L) / 20L})
                  : Component.translatable("hud.deepstrike.launch.now")
            );
         String status = countdown.status();

         Component detail = switch (status) {
            case "READY" -> Component.translatable("hud.deepstrike.launch.ready");
            case "FALLBACK" -> Component.translatable("hud.deepstrike.launch.fallback");
            default -> Component.translatable("hud.deepstrike.launch.preparing");
         };
         title = title.copy().withStyle(FlightUi.STYLE);
         detail = detail.copy().withStyle(FlightUi.STYLE);
         GuiGraphics g = event.getGuiGraphics();
         UiViewport.begin(g);
         int center = UiViewport.width() / 2;
         int y = UiViewport.height() - 132;
         int width = Math.min(UiViewport.width() - 28, Math.max(300, Math.max(mc.font.width(title), mc.font.width(detail)) + 90));
         int x = center - width / 2;
         FlightInstruments.card(g, x, y, width, 70);
         g.fill(x + 68, y + 12, x + 69, y + 57, -10854328);
         String number = stale ? "—" : (remaining > 0L ? Long.toString((remaining + 19L) / 20L) : "0");
         g.pose().pushPose();
         g.pose().translate(x + 22, y + 17, 1.0F);
         g.pose().scale(2.8F, 2.8F, 1.0F);
         FlightInstruments.type(g, number, 0, 0, stale ? -1076610 : -1000096);
         g.pose().popPose();
         FlightInstruments.type(g, title.getString(), x + 83, y + 13, -1182997);
         FlightInstruments.type(g, detail.getString(), x + 83, y + 30, -5196890);
         int segmentW = Math.max(8, (width - 100) / 5);

         for (int i = 0; i < 5; i++) {
            int sx = x + 83 + i * segmentW;
            boolean completed = !stale && i >= Math.min(5L, (remaining + 19L) / 20L);
            FlightUi.rounded(g, sx, y + 52, segmentW - 5, 4, completed ? -1000096 : -12498886);
         }

         UiViewport.end(g);
      }
   }

   private LaunchCountdownHud() {
   }
}
