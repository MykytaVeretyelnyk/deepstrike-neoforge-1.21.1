package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.MissileKind;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class RadarContactRenderer {
   private static final ResourceLocation FP5_ICON = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/item/fp5.png");
   private static final ResourceLocation STORM_SHADOW_ICON = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/item/storm_shadow.png");
   private static final ResourceLocation JAGM_ICON = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/item/jagm.png");
   private static final ResourceLocation PEACEKEEPER_ICON = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/item/peacekeeper.png");
   private static final ResourceLocation SPIKE_NLOS_ICON = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/item/spike_nlos_missile.png");
   private static final ResourceLocation SU24M_ICON = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/item/su24m.png");

   public static ResourceLocation getIconForKind(int kind) {
      if (kind < 0) {
         return null;
      } else if (kind < MissileKind.values().length) {
         return switch (MissileKind.byId(kind)) {
            case FP5 -> FP5_ICON;
            case STORM_SHADOW -> STORM_SHADOW_ICON;
            case JAGM -> JAGM_ICON;
            case PEACEKEEPER -> PEACEKEEPER_ICON;
            case SPIKE_NLOS -> SPIKE_NLOS_ICON;
         };
      } else {
         return kind == 200 ? SU24M_ICON : null;
      }
   }

   public static void drawContact(GuiGraphics g, Font font, int x, int y, int kind, boolean knownDirection, int color, boolean selected, int size) {
      ResourceLocation icon = getIconForKind(kind);
      boolean knownModel = icon != null;
      if (!knownModel) {
         if (kind < 0) {
            int qw = font.width("?");
            g.drawString(font, "?", x - qw / 2, y - 4, color, false);
         } else if (kind == 100) {
            g.fill(x - 3, y - 1, x + 4, y + 2, color);
            g.fill(x - 1, y - 3, x + 2, y + 4, color);
         } else if (kind == 101) {
            g.fill(x - 4, y, x + 5, y + 1, color);
            g.fill(x, y - 4, x + 1, y + 5, color);
            g.fill(x - 1, y - 1, x + 2, y + 2, color);
         } else if (kind == 102) {
            g.fill(x - 3, y - 3, x + 4, y + 4, color);
            g.fill(x - 2, y - 2, x + 3, y + 3, -14802917);
         } else {
            g.fill(x - 2, y - 2, x + 3, y + 3, color);
         }
      } else {
         int half = size / 2;
         g.blit(icon, x - half, y - half, size, size, 0.0F, 0.0F, 32, 32, 32, 32);
         if (!knownDirection) {
            g.drawString(font, "?", x - half - 7, y - 4, -469622, false);
         }
      }

      if (selected) {
         int boxSize = Math.max(12, size + 4);
         int boxHalf = boxSize / 2;
         FlightUi.cornerBrackets(g, x - boxHalf, y - boxHalf, boxSize, boxSize, 3, -4413569);
      }
   }

   private RadarContactRenderer() {
   }
}
