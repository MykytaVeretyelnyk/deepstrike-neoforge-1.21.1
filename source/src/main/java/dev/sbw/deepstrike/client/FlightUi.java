package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.game.LaunchJournal;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class FlightUi {
   public static final int BG = -182838503;
   public static final int SURFACE = -14078937;
   public static final int SURFACE_LIGHT = -12498886;
   public static final int BORDER = -10854328;
   public static final int CYAN = -4668269;
   public static final int AMBER = -1000096;
   public static final int GREEN = -6696818;
   public static final int RED = -1076610;
   public static final int YELLOW = -469622;
   public static final int TEXT = -1182997;
   public static final int MUTED = -5196890;
   public static final int WHITE = -459793;
   public static final Style STYLE = Style.EMPTY.withFont(ResourceLocation.fromNamespaceAndPath("deepstrike", "ui"));
   public static final int ICON_NONE = 0;
   public static final int ICON_GRID = 1;
   public static final int ICON_ROUTE = 2;
   public static final int ICON_ROCKET = 3;
   public static final int ICON_RADAR = 4;
   public static final int ICON_LOG = 5;
   public static final int ICON_ACCESS = 6;
   public static final int ICON_CHECK = 7;
   public static final int ICON_BACK = 8;
   public static final int ICON_CROSS = 9;
   public static final int ICON_SHIELD = 10;
   public static final int ICON_EYE = 11;
   private static final FlightUi.Treatment NEUTRAL = new FlightUi.Treatment(FlightUi.Tone.SECONDARY, 0);
   private static Language treatmentLanguage;
   private static final Map<String, FlightUi.Treatment> treatments = new HashMap<>();
   private static final String[][] GLYPHS = new String[][]{
      new String[0],
      {"###..###", "###..###", "###..###", "........", "###..###", "###..###", "###..###"},
      {"##......", "##..##..", ".#..##..", ".####...", "....#...", "....####", "......##", "......##"},
      {"...##...", "..####..", "..#..#..", "..####..", "..####..", ".######.", "##.##.##", "...##...", "..#..#.."},
      {"..####..", ".#....#.", "#...#..#", "#..##..#", "#.#.#..#", "#......#", ".#....#.", "..####.."},
      {"######..", "#....#..", "#.##.#..", "#....#..", "#.##.#..", "#....#..", "######.."},
      {"..##....", "..##....", "......##", ".####.##", "######..", "##..##.."},
      {"......#.", ".....##.", ".#..##..", ".####...", "..##...."},
      {"...#....", "..##....", ".#######", "..##....", "...#...."},
      {"##...##.", ".##.##..", "..###...", ".##.##..", "##...##."},
      {"#######.", "##...##.", "##.#.##.", "##.#.##.", ".#####..", "..###...", "...#...."},
      {"..####..", ".##..##.", "##.##.##", ".##..##.", "..####.."}
   };

   public static Component text(String s) {
      return LaunchJournal.display(s).copy().withStyle(STYLE);
   }

   public static String localized(String key) {
      return Language.getInstance().getOrDefault(key).replaceAll("(?i)§[0-9a-f]", "");
   }

   public static String clip(String value, int width) {
      return Minecraft.getInstance().font.substrByWidth(text(value), Math.max(0, width)).getString();
   }

   public static void label(GuiGraphics g, String s, int x, int y, int color) {
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 1.0F);
      g.drawString(Minecraft.getInstance().font, text(s), x, y, color, false);
      g.pose().popPose();
   }

   public static void centered(GuiGraphics g, String s, int x, int y, int color) {
      Font f = Minecraft.getInstance().font;
      label(g, s, x - f.width(text(s)) / 2, y, color);
   }

   public static void rounded(GuiGraphics g, int x, int y, int w, int h, int c) {
      g.fill(x + 3, y, x + w - 3, y + h, c);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, c);
      g.fill(x, y + 3, x + w, y + h - 3, c);
   }

   private static void bind(String key, FlightUi.Tone tone, int icon) {
      treatments.put(localized(key), new FlightUi.Treatment(tone, icon));
   }

   private static void treatments() {
      Language language = Language.getInstance();
      if (language != treatmentLanguage) {
         treatmentLanguage = language;
         treatments.clear();
         String prefix = "command.deepstrike.";

         for (String key : new String[]{"launch", "defense_fire"}) {
            bind(prefix + key, FlightUi.Tone.LAUNCH, 3);
         }

         for (String key : new String[]{"save", "login", "use_route", "route_selected", "edit_route"}) {
            bind(prefix + key, FlightUi.Tone.PRIMARY, key.equals("login") ? 6 : (key.contains("route") ? 2 : 7));
         }

         for (String key : new String[]{"delete", "unbind", "remove"}) {
            bind(prefix + key, FlightUi.Tone.DANGER, 9);
         }

         bind(prefix + "arm", FlightUi.Tone.NAVIGATION, 10);
         String[] tabs = new String[]{"units", "tasks", "flights", "defense", "journal", "access"};
         int[] icons = new int[]{1, 2, 3, 4, 5, 6};

         for (int i = 0; i < tabs.length; i++) {
            bind(prefix + tabs[i], FlightUi.Tone.SECONDARY, icons[i]);
         }

         for (String key : new String[]{"back", "switch_network"}) {
            bind(prefix + key, FlightUi.Tone.SECONDARY, 8);
         }

         bind(prefix + "map", FlightUi.Tone.PRIMARY, 2);
         bind(prefix + "details", FlightUi.Tone.SECONDARY, 5);
         bind("screen.deepstrike.save", FlightUi.Tone.PRIMARY, 7);
         bind("screen.deepstrike.assembly.action", FlightUi.Tone.PRIMARY, 7);
         bind("screen.deepstrike.planner.launch", FlightUi.Tone.LAUNCH, 3);
         bind("screen.deepstrike.planner.apply", FlightUi.Tone.PRIMARY, 7);
         bind("screen.deepstrike.planner.edit", FlightUi.Tone.SECONDARY, 2);
         bind("screen.deepstrike.map.hide_gui", FlightUi.Tone.SECONDARY, 11);
         bind("screen.deepstrike.map.show_gui", FlightUi.Tone.PRIMARY, 11);
         treatments.put("TAMIR", new FlightUi.Treatment(FlightUi.Tone.LAUNCH, 3));
         treatments.put("PAC-2", new FlightUi.Treatment(FlightUi.Tone.LAUNCH, 3));
         treatments.put("PAC-3", new FlightUi.Treatment(FlightUi.Tone.LAUNCH, 3));
      }
   }

   public static FlightUi.Tone tone(String caption) {
      treatments();
      return treatments.getOrDefault(caption, NEUTRAL).tone;
   }

   private static int icon(String caption) {
      treatments();
      return treatments.getOrDefault(caption, NEUTRAL).icon;
   }

   private static int tint(int c, int amount) {
      return c & 0xFF000000
         | Math.max(0, Math.min(255, (c >> 16 & 0xFF) + amount)) << 16
         | Math.max(0, Math.min(255, (c >> 8 & 0xFF) + amount)) << 8
         | Math.max(0, Math.min(255, (c & 0xFF) + amount));
   }

   public static void desktop(GuiGraphics g, int w, int h) {
      g.fill(0, 0, w, h, -15395312);
      g.fill(0, 0, w, 56, -14144736);
      g.fill(0, 56, w, 58, -11841476);

      for (int x = 34; x < w - 24; x += 80) {
         for (int y = 114; y < h - 126; y += 64) {
            g.fill(x, y, x + 2, y + 2, 408641865);
         }
      }

      g.fill(0, h - 120, w, h, -14868713);
      g.fill(24, 18, 28, 40, -1000096);
      g.fill(24, h - 25, 42, h - 23, -4668269);
   }

   public static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
      rounded(g, x, y, w, h, -15658483);
      rounded(g, x + 1, y + 1, w - 2, h - 2, bg == -182838503 ? EquipmentUiTheme.current().surface() : bg);
      border = border == -10854328 ? EquipmentUiTheme.current().edge() : border;
      g.fill(x + 3, y + 1, x + w - 3, y + 2, border);
      if (!EquipmentUiTheme.inherited(Minecraft.getInstance().screen).isEmpty()) {
         for (int patch = 0; patch < Math.min(5, w / 45); patch++) {
            int px = x + w - 18 - patch * 37;
            g.fill(px - 14, y + 5, px, y + 9, border & 16777215 | 889192448);
            g.fill(px - 7, y + 9, px + 5, y + 12, border & 16777215 | 620756992);
         }
      }

      g.fill(x + 2, y + 2, x + 3, y + h - 3, tint(border, -22));
      g.fill(x + 3, y + 3, x + w - 3, y + Math.min(18, h - 3), 625824318);
      g.fill(x + 3, y + h - 3, x + w - 3, y + h - 1, -2146496500);
      g.fill(x + 1, y + 5, x + 3, y + Math.min(h - 5, 18), border);
   }

   public static void field(GuiGraphics g, int x, int y, int w, int h, boolean focused, boolean enabled) {
      rounded(g, x - 2, y - 2, w + 4, h + 4, -15592690);
      g.fill(x - 1, y - 1, x + w + 1, y + h + 1, -14473956);
      g.fill(x, y, x + w, y + 2, -15131884);
      g.fill(x, y + h, x + w, y + h + 1, focused ? -4668269 : -10854328);
      g.fill(x - 2, y + 2, x - 1, y + h - 1, focused ? -4668269 : -12631246);
      if (focused && enabled) {
         g.fill(x + w - 7, y + h - 4, x + w - 3, y + h - 2, -4668269);
      }
   }

   public static void row(GuiGraphics g, int x, int y, int w, int h, boolean selected, int accent) {
      g.fill(x, y, x + w, y + h, selected ? -12368075 : -14078937);
      g.fill(x + 1, y + 1, x + w - 1, y + 2, selected ? -9077667 : -12631246);
      g.fill(x, y + 3, x + 3, y + h - 3, accent);
      g.fill(x + 3, y + h - 2, x + w, y + h, -15197676);
      if (selected) {
         g.fill(x + w - 5, y + 5, x + w - 3, y + h - 5, -4668269);
      }
   }

   public static void badge(GuiGraphics g, String value, int x, int y, int color) {
      int w = Minecraft.getInstance().font.width(text(value));
      rounded(g, x - 7, y - 4, w + 16, 20, -15131884);
      g.fill(x - 7, y - 1, x - 5, y + 9, color);
      label(g, value, x, y, color);
   }

   public static void buttonSurface(GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active) {
      buttonSurface(g, x, y, w, h, hovered, active, false, false, FlightUi.Tone.SECONDARY);
   }

   public static void buttonSurface(
      GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active, boolean selected, boolean pressed, FlightUi.Tone tone
   ) {
      int var10000;
      if (!active) {
         var10000 = -13552601;
      } else {
         switch (tone) {
            case PRIMARY:
               var10000 = EquipmentUiTheme.current().button();
               break;
            case LAUNCH:
               var10000 = -2776243;
               break;
            case DANGER:
               var10000 = -8896958;
               break;
            case NAVIGATION:
               var10000 = EquipmentUiTheme.current().button();
               break;
            default:
               var10000 = selected ? -10722743 : -12499661;
         }
      }

      int face = var10000;
      if (active && hovered) {
         face = tint(face, tone != FlightUi.Tone.PRIMARY && tone != FlightUi.Tone.NAVIGATION && !selected ? 18 : 8);
      }

      if (active && pressed) {
         face = tint(face, -10);
      }

      int lift = pressed ? 1 : 0;
      rounded(g, x, y, w, h, -15658483);
      rounded(g, x + 1, y + 1 + lift, w - 2, h - 3 - lift, tint(face, -16));
      g.fill(x + 3, y + 2 + lift, x + w - 3, y + h - 4, face);
      g.fill(x + 3, y + 1 + lift, x + w - 3, y + 3 + lift, tint(face, 29));
      g.fill(x + 2, y + 3 + lift, x + 3, y + h - 4, tint(face, 14));
      g.fill(x + w - 3, y + 4, x + w - 1, y + h - 3, tint(face, -24));
      g.fill(x + 3, y + h - 3, x + w - 3, y + h - 1, tint(face, -40));
      if (active && selected) {
         g.fill(x + 4, y + h - 4, x + w - 4, y + h - 2, -4668269);
      }

      if (active && hovered) {
         g.fill(x + w - 8, y + 5 + lift, x + w - 5, y + 7 + lift, tone == FlightUi.Tone.LAUNCH ? -12834285 : -459793);
      }
   }

   public static void control(GuiGraphics g, int x, int y, int w, int h, String caption, boolean hovered, boolean enabled, boolean selected, boolean pressed) {
      control(g, x, y, w, h, caption, hovered, enabled, selected, pressed, tone(caption));
   }

   public static void control(
      GuiGraphics g, int x, int y, int w, int h, String caption, boolean hovered, boolean enabled, boolean selected, boolean pressed, FlightUi.Tone tone
   ) {
      caption = caption.strip();
      if (caption.startsWith("[") && caption.endsWith("]")) {
         caption = caption.substring(1, caption.length() - 1).strip();
      }

      UiHelp.control(g, x, y, w, h, caption, enabled, tone);
      buttonSurface(g, x, y, w, h, hovered, enabled, selected, pressed, tone);
      int ink = !enabled ? -7563920 : (tone == FlightUi.Tone.LAUNCH ? -14673133 : -1182997);
      int symbol = w >= 92 ? icon(caption) : 0;
      int inset = symbol == 0 ? 0 : 20;
      int offset = pressed && enabled ? 1 : 0;
      if (symbol != 0) {
         g.fill(x + 7, y + h / 2 - 7 + offset, x + 21, y + h / 2 + 6 + offset, tone == FlightUi.Tone.LAUNCH ? 640886805 : 1427379472);
         glyph(g, x + 10, y + h / 2 - 5 + offset, symbol, ink);
      }

      int available = w - inset - (w < 92 ? Math.min(6, Math.max(2, w / 4)) : 14);
      String shown = clip(caption, available);
      if (!shown.equals(caption)) {
         shown = clip(caption, Math.max(0, available - Minecraft.getInstance().font.width(text("…")))) + "…";
      }

      centered(g, shown, x + inset + (w - inset) / 2, y + (h - 10) / 2 + offset, ink);
   }

   public static void glyph(GuiGraphics g, int x, int y, int icon, int color) {
      if (icon > 0 && icon < GLYPHS.length) {
         String[] rows = GLYPHS[icon];

         for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;

            while (c < row.length()) {
               if (row.charAt(c) != '#') {
                  c++;
               } else {
                  int start = c;

                  while (c < row.length() && row.charAt(c) == '#') {
                     c++;
                  }

                  g.fill(x + start, y + r, x + c, y + r + 1, color);
               }
            }
         }
      }
   }

   public static void cornerBrackets(GuiGraphics g, int x, int y, int w, int h, int len, int color) {
      g.fill(x, y, x + len, y + 1, color);
      g.fill(x, y, x + 1, y + len, color);
      g.fill(x + w - len, y, x + w, y + 1, color);
      g.fill(x + w - 1, y, x + w, y + len, color);
      g.fill(x, y + h - 1, x + len, y + h, color);
      g.fill(x, y + h - len, x + 1, y + h, color);
      g.fill(x + w - len, y + h - 1, x + w, y + h, color);
      g.fill(x + w - 1, y + h - len, x + w, y + h, color);
   }

   public static void line(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
      int dx = Math.abs(bx - ax);
      int dy = Math.abs(by - ay);
      int sx = ax < bx ? 1 : -1;
      int sy = ay < by ? 1 : -1;
      int err = dx - dy;
      int x = ax;
      int y = ay;

      while (true) {
         g.fill(x, y, x + 1, y + 1, color);
         if (x == bx && y == by) {
            return;
         }

         int e2 = 2 * err;
         if (e2 > -dy) {
            err -= dy;
            x += sx;
         }

         if (e2 < dx) {
            err += dx;
            y += sy;
         }
      }
   }

   public static void circle(GuiGraphics g, int cx, int cy, float radius, int color, boolean dashed) {
      if (!(radius <= 0.5F)) {
         int segments = Math.max(32, Math.min(180, (int)(radius * 0.45F)));
         double step = (Math.PI * 2) / segments;

         for (int i = 0; i < segments; i++) {
            if (!dashed || i % 4 < 2) {
               double a1 = i * step;
               double a2 = (i + 1) * step;
               int x1 = (int)Math.round(cx + Math.cos(a1) * radius);
               int y1 = (int)Math.round(cy + Math.sin(a1) * radius);
               int x2 = (int)Math.round(cx + Math.cos(a2) * radius);
               int y2 = (int)Math.round(cy + Math.sin(a2) * radius);
               line(g, x1, y1, x2, y2, color);
            }
         }
      }
   }

   public static void crosshair(GuiGraphics g, int cx, int cy, int size, int color) {
      g.fill(cx - size, cy, cx - 2, cy + 1, color);
      g.fill(cx + 3, cy, cx + size + 1, cy + 1, color);
      g.fill(cx, cy - size, cx + 1, cy - 2, color);
      g.fill(cx, cy + 3, cx + 1, cy + size + 1, color);
      g.renderOutline(cx - 4, cy - 4, 9, 9, color);
   }

   private FlightUi() {
   }

   public static enum Tone {
      SECONDARY,
      NAVIGATION,
      PRIMARY,
      LAUNCH,
      DANGER;
   }

   private record Treatment(FlightUi.Tone tone, int icon) {
   }
}
