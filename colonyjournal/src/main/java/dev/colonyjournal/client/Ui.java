package dev.colonyjournal.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Drawing helpers shared by the tabs: the advancements window, buttons, bars and number formatting. */
final class Ui {
   static final ResourceLocation WINDOW = ResourceLocation.withDefaultNamespace("textures/gui/advancements/window.png");
   static final ResourceLocation STONE = ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
   static final ResourceLocation TITLE_BOX = sprite("advancements/title_box");
   static final ResourceLocation BOX_OBTAINED = sprite("advancements/box_obtained");
   static final ResourceLocation BOX_UNOBTAINED = sprite("advancements/box_unobtained");
   static final ResourceLocation BUTTON = sprite("widget/button");
   static final ResourceLocation BUTTON_HOVER = sprite("widget/button_highlighted");
   static final ResourceLocation BUTTON_OFF = sprite("widget/button_disabled");
   static final ResourceLocation HANDLE = sprite("widget/slider_handle");
   static final ResourceLocation HANDLE_HOVER = sprite("widget/slider_handle_highlighted");
   static final ResourceLocation SLOT = sprite("container/slot");
   static final ItemStack COIN = new ItemStack(Items.GOLD_NUGGET);

   // The window.png frame: 252x140 in a 256x256 sheet. Borders: 9 left/right/bottom, 18 top (title bar).
   private static final int FRAME_W = 252;
   private static final int FRAME_H = 140;
   private static final int BORDER = 9;
   private static final int BORDER_TOP = 18;

   static ResourceLocation sprite(String path) {
      return ResourceLocation.withDefaultNamespace(path);
   }

   static boolean in(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   // ------------------------------------------------------------------ window

   /** Draws the advancements window frame as a 9-slice; the edges are tiled so any size works. */
   static void window(GuiGraphics g, int x, int y, int w, int h) {
      int innerW = w - BORDER * 2;
      int innerH = h - BORDER_TOP - BORDER;
      int srcInnerW = FRAME_W - BORDER * 2;
      int srcInnerH = FRAME_H - BORDER_TOP - BORDER;
      g.blit(WINDOW, x, y, 0.0F, 0.0F, BORDER, BORDER_TOP, 256, 256);
      g.blit(WINDOW, x + w - BORDER, y, (float)(FRAME_W - BORDER), 0.0F, BORDER, BORDER_TOP, 256, 256);
      g.blit(WINDOW, x, y + h - BORDER, 0.0F, (float)(FRAME_H - BORDER), BORDER, BORDER, 256, 256);
      g.blit(WINDOW, x + w - BORDER, y + h - BORDER, (float)(FRAME_W - BORDER), (float)(FRAME_H - BORDER), BORDER, BORDER, 256, 256);

      for (int o = 0; o < innerW; o += srcInnerW) {
         int cw = Math.min(srcInnerW, innerW - o);
         g.blit(WINDOW, x + BORDER + o, y, (float)BORDER, 0.0F, cw, BORDER_TOP, 256, 256);
         g.blit(WINDOW, x + BORDER + o, y + h - BORDER, (float)BORDER, (float)(FRAME_H - BORDER), cw, BORDER, 256, 256);
      }

      for (int o = 0; o < innerH; o += srcInnerH) {
         int ch = Math.min(srcInnerH, innerH - o);
         g.blit(WINDOW, x, y + BORDER_TOP + o, 0.0F, (float)BORDER_TOP, BORDER, ch, 256, 256);
         g.blit(WINDOW, x + w - BORDER, y + BORDER_TOP + o, (float)(FRAME_W - BORDER), (float)BORDER_TOP, BORDER, ch, 256, 256);
      }
   }

   /** Tiles the stone background over the rectangle; the pattern is shifted by the pan offset. */
   static void stone(GuiGraphics g, int x, int y, int w, int h, int offX, int offY) {
      g.enableScissor(x, y, x + w, y + h);
      int ox = Math.floorMod(offX, 16);
      int oy = Math.floorMod(offY, 16);

      for (int tx = x - 16 + ox; tx < x + w; tx += 16) {
         for (int ty = y - 16 + oy; ty < y + h; ty += 16) {
            g.blit(STONE, tx, ty, 0.0F, 0.0F, 16, 16, 16, 16);
         }
      }

      g.disableScissor();
   }

   // ------------------------------------------------------------------ widgets

   static void button(GuiGraphics g, Font font, int x, int y, int w, int h, Component label, boolean enabled, boolean hovered) {
      g.blitSprite(!enabled ? BUTTON_OFF : (hovered ? BUTTON_HOVER : BUTTON), x, y, w, h);
      g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, enabled ? 0xFFFFFFFF : 0xFFA0A0A0);
   }

   static void bar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
      g.fill(x, y, x + w, y + h, 0xFF000000);
      g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF2A2A2A);
      int fill = Math.round((w - 2) * Math.max(0.0F, Math.min(1.0F, fraction)));
      if (fill > 0) {
         g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color);
      }
   }

   static void text(GuiGraphics g, Font font, String s, int x, int y, int color) {
      g.drawString(font, s, x, y, color, true);
   }

   static void text(GuiGraphics g, Font font, Component s, int x, int y, int color) {
      g.drawString(font, s, x, y, color, true);
   }

   static void textRight(GuiGraphics g, Font font, String s, int right, int y, int color) {
      g.drawString(font, s, right - font.width(s), y, color, true);
   }

   /** The coin icon, 16x16. */
   static void coin(GuiGraphics g, int x, int y) {
      g.renderItem(COIN, x, y);
   }

   /** Draws a tooltip made of a long text that is wrapped to a readable width. */
   static void tooltip(GuiGraphics g, Font font, Component text, int mx, int my) {
      g.renderTooltip(font, font.split(text, 220), mx, my);
   }

   // ------------------------------------------------------------------ numbers and colours

   static String coins(long v) {
      return String.format(Locale.ROOT, "%,d", v);
   }

   static String signed(long v) {
      return v > 0 ? "+" + coins(v) : coins(v);
   }

   /** Red (0) over yellow (0.5) to green (1). */
   static int heat(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int r1 = 0xE0;
      int g1 = 0x50;
      int b1 = 0x50;
      int r2 = 0xE0;
      int g2 = 0xC0;
      int b2 = 0x40;
      int r3 = 0x55;
      int g3 = 0xCC;
      int b3 = 0x66;
      float k = t < 0.5F ? t * 2.0F : (t - 0.5F) * 2.0F;
      int r = (int)(t < 0.5F ? r1 + (r2 - r1) * k : r2 + (r3 - r2) * k);
      int gr = (int)(t < 0.5F ? g1 + (g2 - g1) * k : g2 + (g3 - g2) * k);
      int b = (int)(t < 0.5F ? b1 + (b2 - b1) * k : b2 + (b3 - b2) * k);
      return 0xFF000000 | r << 16 | gr << 8 | b;
   }

   // ------------------------------------------------------------------ misc

   static void click() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
   }

   static void send(CustomPacketPayload payload) {
      PacketDistributor.sendToServer(payload);
   }

   private Ui() {
   }
}
