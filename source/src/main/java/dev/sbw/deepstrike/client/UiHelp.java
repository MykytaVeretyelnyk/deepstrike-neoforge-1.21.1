package dev.sbw.deepstrike.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.Post;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class UiHelp {
   private static Screen owner;
   private static final SpotlightTour tour = new SpotlightTour();
   private static final LinkedHashMap<String, SpotlightTour.Box> targets = new LinkedHashMap<>();
   private static boolean collecting;
   private static final Map<String, FlightUi.Tone> roles = new HashMap<>();

   private static void screen(Screen screen) {
      if (owner != screen) {
         owner = screen;
         tour.close();
         targets.clear();
      }
   }

   public static void before(Pre event) {
      screen(event.getScreen());
      targets.clear();
      roles.clear();
      collecting = UiViewport.applies(owner);
   }

   static void control(GuiGraphics g, int x, int y, int w, int h, String caption, boolean enabled, FlightUi.Tone tone) {
      if (collecting && !caption.isBlank()) {
         Matrix4f matrix = g.pose().last().pose();
         Vector4f a = matrix.transform(new Vector4f(x, y, 0.0F, 1.0F));
         Vector4f b = matrix.transform(new Vector4f(x + w, y + h, 0.0F, 1.0F));
         float scale = UiViewport.scale();
         String key = caption;
         int duplicate = 2;

         while (targets.containsKey(key)) {
            key = caption + " #" + duplicate++;
         }

         roles.put(key, tone);
         targets.put(
            key, new SpotlightTour.Box(Math.round(a.x / scale), Math.round(a.y / scale), Math.round((b.x - a.x) / scale), Math.round((b.y - a.y) / scale))
         );
      }
   }

   public static void after(Post event) {
      collecting = false;
      if (UiViewport.applies(event.getScreen())) {
         GuiGraphics g = event.getGuiGraphics();
         int mx = (int)(event.getMouseX() / UiViewport.scale());
         int my = (int)(event.getMouseY() / UiViewport.scale());
         UiViewport.begin(g);

         try {
            g.pose().translate(0.0F, 0.0F, 4000.0F);
            FlightUi.control(
               g, 8, owner.height - 30, 26, 24, "?", mx >= 8 && mx < 34 && my >= owner.height - 30 && my < owner.height - 6, true, false, false
            );
            tour.render(g, owner.width, owner.height, mx, my);
         } finally {
            UiViewport.end(g);
         }
      }
   }

   public static void click(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
      if (UiViewport.applies(event.getScreen())) {
         screen(event.getScreen());
         double x = event.getMouseX();
         double y = event.getMouseY();
         if (tour.active()) {
            tour.click(x, y, event.getButton());
            event.setCanceled(true);
         } else {
            if (event.getButton() == 0 && x >= 8.0 && x < 34.0 && y >= owner.height - 30 && y < owner.height - 6) {
               ArrayList<SpotlightTour.Step> steps = new ArrayList<>();

               for (Entry<String, SpotlightTour.Box> entry : targets.entrySet()) {
                  String key = entry.getKey();
                  SpotlightTour.Box fallback = entry.getValue();
                  steps.add(new SpotlightTour.Step(() -> targets.getOrDefault(key, fallback), explanation(key)));
               }

               tour.start(steps);
               event.setCanceled(true);
            }
         }
      }
   }

   private static boolean label(String value, String key) {
      return value.equals(FlightUi.localized(key).replaceAll("^\\[\\s*|\\s*\\]$", "").strip());
   }

   private static Component explanation(String caption) {
      String value = caption.replaceAll(" #\\d+$", "");
      String topic = "control";
      if (roles.get(caption) == FlightUi.Tone.LAUNCH || FlightUi.tone(value) == FlightUi.Tone.LAUNCH) {
         topic = "launch";
      } else if (roles.get(caption) == FlightUi.Tone.DANGER || FlightUi.tone(value) == FlightUi.Tone.DANGER) {
         topic = "delete";
      } else if (value.equals(FlightUi.localized("screen.deepstrike.map.fullscreen")) || value.equals(FlightUi.localized("screen.deepstrike.radar_map.title"))) {
         topic = "map";
      } else if (value.equals(FlightUi.localized("screen.deepstrike.siren.select_map"))) {
         topic = "zone";
      } else if (value.equals(FlightUi.localized("screen.deepstrike.save")) || value.equals(CommandTabletScreen.tr("save").getString())) {
         topic = "save";
      } else if (value.equals("+") || value.equals("−") || value.equals("-")) {
         topic = "adjust";
      } else if (value.equals(FlightUi.localized("screen.deepstrike.map.hide_gui")) || value.equals(FlightUi.localized("screen.deepstrike.map.show_gui"))) {
         topic = "hide";
      }

      if (label(value, "ui.deepstrike.patriot_defense_screen.10d49f4361") || label(value, "ui.deepstrike.iron_dome_defense_screen.10d49f4361")) {
         topic = "journal";
      }

      if (label(value, "ui.deepstrike.patriot_defense_screen.32103b8dbe") || label(value, "ui.deepstrike.iron_dome_defense_screen.32103b8dbe")) {
         topic = "frequency";
      }

      return Component.literal(value).append("\n").append(Component.translatable("help.deepstrike." + topic));
   }

   public static void key(net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) {
      if (owner == event.getScreen() && tour.active()) {
         tour.key(event.getKeyCode());
         event.setCanceled(true);
      }
   }

   public static void scroll(net.neoforged.neoforge.client.event.ScreenEvent.MouseScrolled.Pre event) {
      if (owner == event.getScreen() && tour.active()) {
         event.setCanceled(true);
      }
   }

   public static void drag(net.neoforged.neoforge.client.event.ScreenEvent.MouseDragged.Pre event) {
      if (owner == event.getScreen() && tour.active()) {
         event.setCanceled(true);
      }
   }

   public static void typed(net.neoforged.neoforge.client.event.ScreenEvent.CharacterTyped.Pre event) {
      if (owner == event.getScreen() && tour.active()) {
         event.setCanceled(true);
      }
   }

   static boolean ready(Screen screen) {
      return owner == screen && !targets.isEmpty();
   }

   private UiHelp() {
   }
}
