package dev.sbw.deepstrike.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.fml.ModList;

public final class MapProviders {
   public static final ModConfigSpec CONFIG;
   private static final EnumValue<MapProviders.Provider> CHOICE;

   static boolean xaero() {
      return ModList.get().isLoaded("xaeroworldmap");
   }

   static boolean journey() {
      return ModList.get().isLoaded("journeymap");
   }

   public static String selected() {
      MapProviders.Provider requested = (MapProviders.Provider)CHOICE.get();
      if (requested == MapProviders.Provider.JOURNEYMAP && journey()) {
         return "journeymap";
      } else if (requested == MapProviders.Provider.XAERO && xaero()) {
         return "xaero";
      } else if (xaero()) {
         return "xaero";
      } else if (journey()) {
         return "journeymap";
      } else {
         throw new IllegalStateException("DeepStrike requires Xaero's World Map OR JourneyMap on the client.");
      }
   }

   static MapSurface create() {
      return (MapSurface)(selected().equals("xaero") ? MapProviders.XaeroProxy.create() : new JourneyTerrainMap());
   }

   public static boolean ready() {
      if (!xaero() && !journey()) {
         return false;
      } else {
         return selected().equals("journeymap") ? JourneyTerrainMap.ready() : MapProviders.XaeroProxy.ready();
      }
   }

   public static boolean isNativeMap(Screen screen) {
      if (screen == null) {
         return false;
      } else {
         for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("xaero.map.gui.GuiMap") || type.getName().equals("journeymap.client.ui.fullscreen.Fullscreen")) {
               return true;
            }
         }

         return false;
      }
   }

   static MapProviders.View nativeView(Screen screen) {
      if (!isNativeMap(screen)) {
         return null;
      } else {
         return screen.getClass().getName().startsWith("xaero.") ? MapProviders.XaeroProxy.nativeView(screen) : JourneyTerrainMap.nativeView(screen);
      }
   }

   static void cleanupWaypoints() {
      if (xaero() && ModList.get().isLoaded("xaerominimap")) {
         MapProviders.XaeroProxy.cleanupWaypoints();
      }
   }

   static void openNativeMap() {
      Minecraft mc = Minecraft.getInstance();
      if (selected().equals("xaero")) {
         MapProviders.XaeroProxy.openNativeMap();
      } else {
         try {
            mc.setScreen((Screen)Class.forName("journeymap.client.ui.fullscreen.Fullscreen").getConstructor().newInstance());
         } catch (ReflectiveOperationException var2) {
            throw new IllegalStateException(var2);
         }
      }
   }

   static boolean nativeUiHidden() {
      return xaero() && MapProviders.XaeroProxy.hiddenUI();
   }

   private MapProviders() {
   }

   static {
      Builder builder = new Builder();
      CHOICE = builder.comment("Terrain provider for every DeepStrike map. AUTO prefers Xaero when both are installed.")
         .defineEnum("mapProvider", MapProviders.Provider.AUTO);
      CONFIG = builder.build();
   }

   public static enum Provider {
      AUTO,
      XAERO,
      JOURNEYMAP;
   }

   public record View(double x, double z, double pixelsPerBlock) {
   }

   private static final class XaeroProxy {
      static MapSurface create() {
         return new XaeroTerrainMap();
      }

      static boolean ready() {
         return XaeroTerrainMap.ready();
      }

      static MapProviders.View nativeView(Screen screen) {
         return XaeroTerrainMap.nativeView(screen);
      }

      static void cleanupWaypoints() {
         XaeroTerrainMap.cleanupWaypoints();
      }

      static void openNativeMap() {
         XaeroTerrainMap.openNativeMap();
      }

      static boolean hiddenUI() {
         return XaeroTerrainMap.hiddenUI();
      }
   }
}
