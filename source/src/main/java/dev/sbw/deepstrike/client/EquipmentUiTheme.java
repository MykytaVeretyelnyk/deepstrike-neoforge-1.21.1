package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;

public interface EquipmentUiTheme {
   String equipmentSkin();

   static String inherited(Screen parent) {
      return parent instanceof EquipmentUiTheme themed ? themed.equipmentSkin() : "";
   }

   static String vehicle(int id) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && level.getEntity(id) instanceof VehicleEntity v) {
         String skin = v.getSkinId();
         if (skin != null && !skin.isEmpty()) {
            return skin;
         } else {
            return v instanceof SpikeNlosEntity ? "sand" : "forest";
         }
      } else {
         return "";
      }
   }

   static EquipmentUiTheme.Palette palette(String skin) {
      String var1 = skin == null ? "" : skin;

      return switch (var1) {
         case "desert", "sand" -> new EquipmentUiTheme.Palette(-13028310, -7768493, -10136008, -2703736);
         case "winter" -> new EquipmentUiTheme.Palette(-13355471, -7828611, -11117490, -2631225);
         case "urban", "gray", "stealth" -> new EquipmentUiTheme.Palette(-14079192, -9867934, -12170432, -4670037);
         case "multicam" -> new EquipmentUiTheme.Palette(-13618393, -8684460, -11182534, -3881845);
         default -> new EquipmentUiTheme.Palette(-14078937, -10128563, -12167372, -4668269);
      };
   }

   static EquipmentUiTheme.Palette current() {
      Screen screen = Minecraft.getInstance().screen;
      return palette(screen == null ? ClientCamera.equipmentSkin() : inherited(screen));
   }

   public record Palette(int surface, int edge, int button, int accent) {
   }
}
