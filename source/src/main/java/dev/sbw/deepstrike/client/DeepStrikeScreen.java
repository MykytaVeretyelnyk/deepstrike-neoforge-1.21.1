package dev.sbw.deepstrike.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PresetRoutes;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class DeepStrikeScreen extends Screen {
   private final BlockPos launcherPos;
   private final Direction facing;
   private final LauncherBlock.Family family;
   private final List<RoutePoint> route = new ArrayList<>();
   private final Set<Integer> manualAltitude = new HashSet<>();
   private final List<DeepStrikeScreen.Hit> controls = new ArrayList<>();
   private List<String> journal = List.of();
   private TerrainMap terrain;
   private double centerX;
   private double centerZ;
   private double blocksPerPixel = 1.0;
   private int selected = -1;
   private int frame;
   private int lastRefresh = -100;
   private int menuX = -1;
   private int menuY;
   private int menuPoint = -1;
   private int panelX = 56;
   private int panelY = 52;
   private int panelW;
   private int panelH;
   private int panelScroll;
   private boolean settings;
   private boolean dragged;
   private boolean dragging;
   private boolean dirty = true;
   private boolean clippedControls;
   private double downX;
   private double downY;
   private DeepStrikeScreen.Tab tab = DeepStrikeScreen.Tab.CONFIG;
   private final EditBox[] config = new EditBox[4];
   private EditBox frequency;
   private String error = "";
   private int pointerX;
   private int pointerY;

   public DeepStrikeScreen(BlockPos pos, Direction direction, LauncherBlock.Family family) {
      super(FlightUi.text("DeepStrike"));
      this.launcherPos = pos.immutable();
      this.facing = direction;
      this.family = family;
      this.centerX = pos.getX() + 0.5;
      this.centerZ = pos.getZ() + 0.5;
   }

   public static void open(BlockPos pos, Direction facing, LauncherBlock.Family family) {
      Minecraft.getInstance().setScreen(new DeepStrikeScreen(pos, facing, family));
   }

   public static void acceptJournal(List<String> entries) {
      if (Minecraft.getInstance().screen instanceof DeepStrikeScreen s) {
         s.journal = List.copyOf(entries);
      }
   }

   protected void init() {
      String previousFrequency = this.frequency == null && this.minecraft.level.getBlockEntity(this.launcherPos) instanceof LauncherBlockEntity launcher
         ? launcher.transmitFrequency()
         : (this.frequency == null ? "" : this.frequency.getValue());
      this.panelW = Math.min(310, this.width - 72);
      this.panelH = Math.max(100, Math.min(400, this.height - 104));

      for (int i = 0; i < 4; i++) {
         EditBox box = new InstrumentField(this.font, 0, 0, this.panelW - 32, 20, FlightUi.text(""));
         box.setMaxLength(18);
         box.setTextColor(-1182997);
         box.setBordered(false);
         box.setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
         this.config[i] = (EditBox)this.addRenderableWidget(box);
      }

      this.frequency = new InstrumentField(
         this.font, this.width - 240, this.height - 36, 80, 19, FlightUi.text(FlightUi.localized("screen.deepstrike.frequency.label"))
      );
      this.frequency.setMaxLength(12);
      this.frequency.setFilter(value -> value.matches("[0-9]{0,12}"));
      this.frequency.setValue(previousFrequency);
      this.addRenderableWidget(this.frequency);
      this.syncConfig();
      this.layoutFields();
      this.dirty = true;
   }

   private void syncConfig() {
      if (this.config[0] != null) {
         RoutePoint p = this.route.isEmpty()
            ? new RoutePoint(this.centerX, this.launcherPos.getY(), this.centerZ, this.loadedKind().defaultSpeed)
            : this.route.get(this.route.size() - 1);
         double[] v = new double[]{p.x(), p.y(), p.z(), p.speed()};

         for (int i = 0; i < 4; i++) {
            this.config[i].setValue(String.format(Locale.ROOT, "%.2f", v[i]));
         }

         this.error = "";
      }
   }

   private void layoutFields() {
      for (int i = 0; i < 4; i++) {
         EditBox b = this.config[i];
         if (b != null) {
            int y = this.panelY + 100 + i * 48 - this.panelScroll;
            b.setX(this.panelX + 18);
            b.setY(y);
            b.setWidth(this.panelW - 36);
            b.visible = this.settings && this.tab == DeepStrikeScreen.Tab.CONFIG && y >= this.panelY + 70 && y + 20 < this.panelY + this.panelH - 12;
            b.active = b.visible;
            if (!b.visible) {
               b.setFocused(false);
            }
         }
      }
   }

   private void toggleSettings() {
      this.settings = !this.settings;
      this.menuX = -1;
      this.layoutFields();
   }

   private void selectTab(DeepStrikeScreen.Tab next) {
      this.tab = next;
      this.panelScroll = 0;
      this.layoutFields();
      if (next == DeepStrikeScreen.Tab.JOURNAL) {
         DeepStrikeNetwork.CHANNEL.sendToServer(new DeepStrikeNetwork.RequestJournal(this.launcherPos));
         UnitJournalScreen.open(this.launcherPos);
      }
   }

   private double worldX(double x) {
      return this.centerX + (x - this.width / 2.0) * this.blocksPerPixel;
   }

   private double worldZ(double y) {
      return this.centerZ + (y - this.height / 2.0) * this.blocksPerPixel;
   }

   private int screenX(double x) {
      return (int)Math.round(this.width / 2.0 + (x - this.centerX) / this.blocksPerPixel);
   }

   private int screenY(double z) {
      return (int)Math.round(this.height / 2.0 + (z - this.centerZ) / this.blocksPerPixel);
   }

   public void tick() {
      if (++this.frame - this.lastRefresh > 60) {
         this.dirty = true;
      }

      if (this.dirty && this.minecraft.level != null) {
         if (this.terrain == null) {
            this.terrain = new TerrainMap();
         }

         this.terrain
            .refresh(
               this.centerX, this.centerZ, Math.max(1, (int)(this.width * this.blocksPerPixel)), Math.max(1, (int)(this.height * this.blocksPerPixel))
            );
         this.dirty = false;
         this.lastRefresh = this.frame;
      }
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.pointerX = mx;
      this.pointerY = my;
      this.controls.clear();
      g.fill(0, 0, this.width, this.height, -15592945);
      if (this.terrain != null) {
         this.terrain.render(g, 0, 0, this.width, this.height);
      }

      this.drawRoute(g);
      g.flush();
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 200.0F);
      FlightUi.rounded(g, 12, 12, Math.min(260, this.width - 132), 29, -182838503);
      FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_screen.be4540d462"), 22, 21, -4668269);
      this.button(g, this.width - 108, 12, 28, 28, "−", () -> this.zoom(this.width / 2.0, this.height / 2.0, 1.5));
      this.button(g, this.width - 74, 12, 28, 28, "+", () -> this.zoom(this.width / 2.0, this.height / 2.0, 0.6666666666666666));
      this.button(g, this.width - 40, 12, 28, 28, "×", this::onClose);
      this.button(g, 12, 52, 32, 32, "", this::toggleSettings);
      this.gear(g, 28, 68);
      this.button(g, 12, 90, 32, 28, "◎", () -> {
         this.centerX = this.launcherPos.getX() + 0.5;
         this.centerZ = this.launcherPos.getZ() + 0.5;
         this.dirty = true;
      });
      FlightUi.rounded(g, 12, this.height - 38, this.width - 24, 26, -182838503);
      String info = String.format(
         Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_screen.e56c0db0b3"), this.worldX(mx), this.worldZ(my), this.route.size()
      );
      FlightUi.label(g, info, 22, this.height - 30, -5196890);
      this.button(g, this.width - 120, this.height - 36, 106, 22, FlightUi.localized("ui.deepstrike.deep_strike_screen.f3d4efd4a1"), this::launch);
      FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_screen.13831e74c4"), this.width - 304, this.height - 30, -5196890);
      int scaleBlocks = (int)Math.pow(2.0, Math.floor(Math.log(70.0 * this.blocksPerPixel) / Math.log(2.0)));
      int scalePixels = Math.max(1, (int)(scaleBlocks / this.blocksPerPixel));
      FlightUi.rounded(g, this.width - 108, this.height - 82, 96, 35, -182838503);
      g.fill(this.width - 98, this.height - 59, this.width - 98 + scalePixels, this.height - 58, -469622);
      FlightUi.label(g, scaleBlocks + FlightUi.localized("ui.deepstrike.deep_strike_screen.bf2cef53b5"), this.width - 98, this.height - 75, -1182997);
      FlightUi.rounded(g, this.width - 40, 52, 28, 26, -182838503);
      FlightUi.label(g, "N", this.width - 30, 59, -469622);
      if (this.settings) {
         this.drawSettings(g);
      }

      if (this.menuX >= 0) {
         int x = Math.min(this.menuX, this.width - 158);
         int y = Math.min(this.menuY, this.height - 66);
         FlightUi.rounded(g, x, y, 150, 54, -182838503);
         if (this.menuPoint < 0) {
            this.button(g, x + 5, y + 5, 140, 22, FlightUi.localized("ui.deepstrike.deep_strike_screen.ee14772916"), () -> {
               this.addPoint(this.menuX, this.menuY);
               this.menuX = -1;
            });
         } else {
            this.button(g, x + 5, y + 5, 140, 20, FlightUi.localized("ui.deepstrike.deep_strike_screen.64b9882ba2"), () -> {
               this.editPoint(this.menuPoint);
               this.menuX = -1;
            });
            this.button(g, x + 5, y + 29, 140, 20, FlightUi.localized("ui.deepstrike.deep_strike_screen.e83fca4e96"), () -> {
               this.removePoint(this.menuPoint);
               this.menuX = -1;
               this.syncConfig();
            });
         }
      }

      g.pose().translate(0.0F, 0.0F, 2.0F);
      super.render(g, mx, my, pt);
      g.flush();
      g.pose().popPose();
   }

   private MissileKind loadedKind() {
      if (this.minecraft.level != null
         && this.minecraft.level.getBlockEntity(this.launcherPos) instanceof LauncherBlockEntity be
         && be.loaded().getItem() instanceof MissileItem m) {
         return m.kind();
      } else {
         return this.family == LauncherBlock.Family.JAGM ? MissileKind.JAGM : MissileKind.FP5;
      }
   }

   private void gear(GuiGraphics g, int x, int y) {
      for (int i = 0; i < 8; i++) {
         double a = i * Math.PI / 4.0;
         int xx = x + (int)(Math.cos(a) * 8.0);
         int yy = y + (int)(Math.sin(a) * 8.0);
         g.fill(xx - 2, yy - 2, xx + 3, yy + 3, -469622);
      }

      FlightUi.rounded(g, x - 7, y - 7, 15, 15, -469622);
      FlightUi.rounded(g, x - 3, y - 3, 7, 7, -14078937);
   }

   private void drawRoute(GuiGraphics g) {
      int lx = this.screenX(this.launcherPos.getX() + 0.5);
      int ly = this.screenY(this.launcherPos.getZ() + 0.5);
      MissileKind kind = this.loadedKind();
      float rMax = (float)(kind.maxRange / this.blocksPerPixel);
      FlightUi.circle(g, lx, ly, rMax, -4668269, true);

      for (int i = 0; i < this.route.size(); i++) {
         RoutePoint p = this.route.get(i);
         int x = this.screenX(p.x());
         int y = this.screenY(p.z());
         if (i > 0) {
            RoutePoint before = this.route.get(i - 1);
            this.line(g, this.screenX(before.x()), this.screenY(before.z()), x, y);
         }

         if (x >= -20 && x <= this.width + 20 && y >= -20 && y <= this.height + 20) {
            FlightUi.rounded(g, x - 9, y - 9, 19, 19, -15592945);
            FlightUi.rounded(g, x - 7, y - 7, 15, 15, i == this.selected ? -4413569 : -469622);
            FlightUi.centered(g, Integer.toString(i + 1), x, y - 5, -15263977);
            String caption = i == this.route.size() - 1 ? FlightUi.localized("ui.deepstrike.deep_strike_screen.6f17c22da4") + (int)p.y() : "Y " + (int)p.y();
            int tw = this.font.width(FlightUi.text(caption));
            FlightUi.rounded(g, x + 13, y - 9, tw + 12, 18, -182838503);
            FlightUi.label(g, caption, x + 19, y - 5, -1182997);
         }
      }

      UUID myId = this.minecraft.player != null ? this.minecraft.player.getUUID() : null;

      for (GuidedMissileEntity m : RemoteFlights.all()) {
         if (m.isAlive() && RadioFrequency.playerReceives(this.minecraft.player, m.frequency())) {
            boolean isMine = myId != null && myId.equals(m.ownerId());
            int sx = this.screenX(m.getX());
            int sy = this.screenY(m.getZ());
            int c = isMine ? -16711868 : -4668269;
            FlightUi.circle(g, sx, sy, 8.0F, c, false);
            ResourceLocation missileIcon = RadarContactRenderer.getIconForKind(m.kind().ordinal());
            if (missileIcon != null) {
               g.blit(missileIcon, sx - 8, sy - 8, 16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
            } else {
               g.fill(sx - 2, sy - 2, sx + 3, sy + 3, c);
            }

            FlightUi.line(g, lx, ly, sx, sy, isMine ? -6696818 : -1000096);
            String tip = isMine ? FlightUi.localized("ui.deepstrike.deep_strike_screen.2f2b351b74") : FlightUi.localized("screen.deepstrike.unit");
            FlightUi.label(g, tip + " · Y " + (int)m.getY(), sx + 10, sy - 4, c);
         }
      }

      g.renderOutline(lx - 5, ly - 5, 11, 11, -1118482);
   }

   private void line(GuiGraphics g, int ax, int ay, int bx, int by) {
      double dx = bx - ax;
      double dy = by - ay;
      int n = Math.min(6000, Math.max(Math.abs(bx - ax), Math.abs(by - ay)));

      for (int i = 0; i <= n; i++) {
         double t = (double)i / Math.max(1, n);
         int x = (int)(ax + dx * t);
         int y = (int)(ay + dy * t);
         if (x >= 0 && x < this.width && y >= 0 && y < this.height) {
            g.fill(x, y, x + 2, y + 2, -469622);
         }
      }

      double a = Math.atan2(dy, dx);
      int x = (ax + bx) / 2;
      int y = (ay + by) / 2;

      for (int side : new int[]{-1, 1}) {
         for (int ix = 0; ix < 8; ix++) {
            int xx = x - (int)(Math.cos(a + side * 0.6) * ix);
            int yy = y - (int)(Math.sin(a + side * 0.6) * ix);
            g.fill(xx, yy, xx + 2, yy + 2, -469622);
         }
      }
   }

   private void drawSettings(GuiGraphics g) {
      FlightUi.rounded(g, this.panelX, this.panelY, this.panelW, this.panelH, -182838503);
      FlightUi.label(g, FlightUi.localized("screen.deepstrike.mission_setup"), this.panelX + 16, this.panelY + 13, -469622);
      int w = (this.panelW - 24) / 3;
      String[] names = new String[]{
         FlightUi.localized("ui.deepstrike.deep_strike_screen.f67a185fb3"),
         FlightUi.localized("ui.deepstrike.deep_strike_screen.d9c756b17e"),
         FlightUi.localized("ui.deepstrike.deep_strike_screen.97c459a6bb")
      };

      for (int i = 0; i < 3; i++) {
         DeepStrikeScreen.Tab next = DeepStrikeScreen.Tab.values()[i];
         this.button(g, this.panelX + 8 + i * w, this.panelY + 35, w - 4, 24, names[i], () -> this.selectTab(next));
      }

      this.clippedControls = true;
      g.enableScissor(this.panelX + 4, this.panelY + 67, this.panelX + this.panelW - 4, this.panelY + this.panelH - 5);
      int y = this.panelY + 76 - this.panelScroll;
      if (this.tab == DeepStrikeScreen.Tab.CONFIG) {
         String[] labels = new String[]{
            FlightUi.localized("ui.deepstrike.deep_strike_screen.5305e699ac"),
            FlightUi.localized("ui.deepstrike.deep_strike_screen.7885c72ca1"),
            FlightUi.localized("ui.deepstrike.deep_strike_screen.bd61b97491"),
            FlightUi.localized("ui.deepstrike.deep_strike_screen.8b3b1de34a")
         };

         for (int i = 0; i < 4; i++) {
            FlightUi.label(g, labels[i], this.panelX + 16, y + i * 48, -5196890);
            FlightUi.rounded(g, this.panelX + 12, y + 19 + i * 48, this.panelW - 24, 24, -14078937);
         }

         this.button(g, this.panelX + 12, y + 201, this.panelW - 24, 25, FlightUi.localized("ui.deepstrike.deep_strike_screen.3a399f4879"), this::applyConfig);
         FlightUi.label(g, this.error, this.panelX + 14, y + 235, -4413569);
         FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_screen.5f9654f6bb"), this.panelX + 14, y + 255, -5196890);
         this.button(g, this.panelX + 12, y + 277, this.panelW - 24, 24, FlightUi.localized("ui.deepstrike.deep_strike_screen.5181ba18a8"), () -> {
            this.route.clear();
            this.manualAltitude.clear();
            this.selected = -1;
            this.syncConfig();
         });
         if (this.family == LauncherBlock.Family.JAGM) {
            FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_screen.82c268077e"), this.panelX + 14, y + 315, -5196890);
            String[] a = new String[]{"−15°", "+15°", "−5°", "+5°"};

            for (int ix = 0; ix < 4; ix++) {
               int i = ix;
               this.button(
                  g,
                  this.panelX + 12 + i * (this.panelW - 24) / 4,
                  y + 333,
                  (this.panelW - 32) / 4,
                  24,
                  a[i],
                  () -> DeepStrikeNetwork.CHANNEL
                     .sendToServer(
                        new DeepStrikeNetwork.AdjustLauncher(
                           this.launcherPos, i == 0 ? -15.0F : (i == 1 ? 15.0F : 0.0F), i == 2 ? -5.0F : (i == 3 ? 5.0F : 0.0F)
                        )
                     )
               );
            }
         }
      } else if (this.tab == DeepStrikeScreen.Tab.PRESETS) {
         FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_screen.29d771a4f3"), this.panelX + 14, y, -5196890);
         String[] names2 = new String[]{
            FlightUi.localized("ui.deepstrike.deep_strike_screen.88b19891ad"),
            FlightUi.localized("ui.deepstrike.deep_strike_screen.6eab0f8b8e"),
            FlightUi.localized("ui.deepstrike.deep_strike_screen.ff2405498a")
         };

         for (int ix = 0; ix < 3; ix++) {
            int i = ix;
            this.button(g, this.panelX + 12, y + 24 + i * 36, this.panelW - 24, 28, names2[i], () -> this.loadPreset(i));
         }

         this.button(g, this.panelX + 12, y + 148, this.panelW - 24, 26, FlightUi.localized("ui.deepstrike.deep_strike_screen.93f7b6f229"), this::savePreset);
         this.button(g, this.panelX + 12, y + 184, this.panelW - 24, 26, FlightUi.localized("ui.deepstrike.deep_strike_screen.978d25971a"), this::restorePreset);
         FlightUi.label(g, this.error, this.panelX + 14, y + 225, -469622);
      } else {
         FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_screen.4737cf58b4"), this.panelX + 14, y, -5196890);
         if (this.journal.isEmpty()) {
            FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_screen.ef07a4f4c3"), this.panelX + 14, y + 29, -1182997);
         }

         int yy = y + 28;

         for (String entry : this.journal) {
            for (FormattedCharSequence line : this.font.split(FlightUi.text(entry), this.panelW - 32)) {
               g.drawString(this.font, line, this.panelX + 14, yy, -1182997, false);
               yy += 13;
            }

            yy += 16;
         }
      }

      g.disableScissor();
      this.clippedControls = false;
      int content = this.tab == DeepStrikeScreen.Tab.CONFIG ? 452 : (this.tab == DeepStrikeScreen.Tab.PRESETS ? 335 : Math.max(200, this.journal.size() * 80));
      if (content > this.panelH) {
         int track = this.panelH - 80;
         int thumb = Math.max(16, track * this.panelH / content);
         int sy = this.panelY + 69 + (track - thumb) * this.panelScroll / Math.max(1, content - this.panelH);
         FlightUi.rounded(g, this.panelX + this.panelW - 6, sy, 3, thumb, -469622);
      }
   }

   private void button(GuiGraphics g, int x, int y, int w, int h, String value, Runnable action) {
      boolean hover = this.pointerX >= x && this.pointerX < x + w && this.pointerY >= y && this.pointerY < y + h;
      FlightUi.control(g, x, y, w, h, value, hover, true, false, hover && this.minecraft.mouseHandler.isLeftPressed());
      if (!this.clippedControls || y >= this.panelY + 67 && y + h <= this.panelY + this.panelH) {
         this.controls.add(new DeepStrikeScreen.Hit(x, y, w, h, action));
      }
   }

   public boolean mouseClicked(double x, double y, int b) {
      if (this.frequency.isMouseOver(x, y)) {
         this.setFocused(this.frequency);
         return this.frequency.mouseClicked(x, y, b);
      } else {
         for (int i = this.controls.size() - 1; i >= 0; i--) {
            DeepStrikeScreen.Hit hit = this.controls.get(i);
            if (hit.contains(x, y)) {
               if (b == 0) {
                  hit.action.run();
               }

               return true;
            }
         }

         if (this.settings && x >= this.panelX && x < this.panelX + this.panelW && y >= this.panelY && y < this.panelY + this.panelH) {
            return super.mouseClicked(x, y, b);
         } else if (!(y < 44.0) && !(y > this.height - 42)) {
            int index = this.pointAt(x, y);
            if (b == 1) {
               this.menuX = (int)x;
               this.menuY = (int)y;
               this.menuPoint = index;
               return true;
            } else {
               this.menuX = -1;
               if (b != 0) {
                  return super.mouseClicked(x, y, b);
               } else if (index >= 0) {
                  this.selected = index;
                  this.editPoint(index);
                  return true;
               } else {
                  this.downX = x;
                  this.downY = y;
                  this.dragging = true;
                  this.dragged = false;
                  return true;
               }
            }
         } else {
            return true;
         }
      }
   }

   public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
      if (this.dragging && b == 0) {
         if (Math.hypot(x - this.downX, y - this.downY) > 3.0) {
            this.dragged = true;
         }

         if (this.dragged) {
            this.centerX = this.centerX - dx * this.blocksPerPixel;
            this.centerZ = this.centerZ - dy * this.blocksPerPixel;
            this.dirty = true;
         }

         return true;
      } else {
         return super.mouseDragged(x, y, b, dx, dy);
      }
   }

   public boolean mouseReleased(double x, double y, int b) {
      if (this.dragging && b == 0) {
         this.dragging = false;
         if (!this.dragged) {
            this.addPoint(x, y);
         }

         return true;
      } else {
         return super.mouseReleased(x, y, b);
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
      if (this.settings && x >= this.panelX && x < this.panelX + this.panelW && y >= this.panelY && y < this.panelY + this.panelH) {
         int content = this.tab == DeepStrikeScreen.Tab.CONFIG
            ? 452
            : (this.tab == DeepStrikeScreen.Tab.PRESETS ? 335 : Math.max(200, this.journal.size() * 80));
         this.panelScroll = Math.max(0, Math.min(Math.max(0, content - this.panelH), this.panelScroll - (int)(delta * 24.0)));
         this.layoutFields();
         return true;
      } else {
         this.zoom(x, y, delta > 0.0 ? 0.6666666666666666 : 1.5);
         return true;
      }
   }

   private void zoom(double x, double y, double factor) {
      double wx = this.worldX(x);
      double wz = this.worldZ(y);
      this.blocksPerPixel = Math.max(0.125, Math.min(16.0, this.blocksPerPixel * factor));
      this.centerX = wx - (x - this.width / 2.0) * this.blocksPerPixel;
      this.centerZ = wz - (y - this.height / 2.0) * this.blocksPerPixel;
      this.dirty = true;
   }

   private int pointAt(double x, double y) {
      for (int i = this.route.size() - 1; i >= 0; i--) {
         if (Math.abs(this.screenX(this.route.get(i).x()) - x) < 11.0 && Math.abs(this.screenY(this.route.get(i).z()) - y) < 11.0) {
            return i;
         }
      }

      return -1;
   }

   private void addPoint(double x, double y) {
      if (this.route.size() < 32) {
         double wx = this.worldX(x);
         double wz = this.worldZ(y);
         BlockPos p = BlockPos.containing(wx, 0.0, wz);
         int ground = this.minecraft.level.hasChunkAt(p)
            ? this.minecraft.level.getHeight(Types.MOTION_BLOCKING, p.getX(), p.getZ())
            : this.launcherPos.getY();
         if (!this.route.isEmpty() && !this.manualAltitude.contains(this.route.size() - 1)) {
            int last = this.route.size() - 1;
            RoutePoint before = this.route.get(last);
            this.route
               .set(
                  last,
                  new RoutePoint(
                     before.x(),
                     Math.min((double)(this.minecraft.level.getMaxBuildHeight() - 1), Math.max(before.y() + 32.0, (double)(this.launcherPos.getY() + 32))),
                     before.z(),
                     before.speed()
                  )
               );
         }

         this.route.add(new RoutePoint(Math.floor(wx) + 0.5, ground, Math.floor(wz) + 0.5, this.loadedKind().defaultSpeed));
         this.selected = this.route.size() - 1;
         this.syncConfig();
      }
   }

   private void editPoint(int i) {
      this.selected = i;
      this.minecraft.setScreen(new RoutePointScreen(this, this.route.get(i), i == this.route.size() - 1, this.loadedKind(), p -> {
         this.route.set(i, p);
         this.manualAltitude.add(i);
         this.syncConfig();
      }));
   }

   private void removePoint(int index) {
      this.route.remove(index);
      Set<Integer> shifted = new HashSet<>();

      for (int i : this.manualAltitude) {
         if (i != index) {
            shifted.add(i > index ? i - 1 : i);
         }
      }

      this.manualAltitude.clear();
      this.manualAltitude.addAll(shifted);
      this.selected = Math.min(this.selected, this.route.size() - 1);
   }

   private boolean configurationChanged() {
      if (this.route.isEmpty()) {
         return false;
      } else {
         RoutePoint p = this.route.get(this.route.size() - 1);
         double[] current = new double[]{p.x(), p.y(), p.z(), p.speed()};

         try {
            for (int i = 0; i < 4; i++) {
               double value = Double.parseDouble(this.config[i].getValue().replace(',', '.'));
               if (!Double.isFinite(value) || Math.abs(value - current[i]) > 1.0E-4) {
                  return true;
               }
            }

            return false;
         } catch (NumberFormatException var6) {
            return true;
         }
      }
   }

   private boolean applyConfig() {
      if (this.route.isEmpty()) {
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.de39987c60");
         return false;
      } else {
         try {
            double[] v = new double[4];

            for (int i = 0; i < 4; i++) {
               v[i] = Double.parseDouble(this.config[i].getValue().replace(',', '.'));
               if (!Double.isFinite(v[i])) {
                  throw new NumberFormatException();
               }
            }

            if (!(v[1] < this.minecraft.level.getMinBuildHeight()) && !(v[1] >= this.minecraft.level.getMaxBuildHeight())) {
               MissileKind kind = this.loadedKind();
               if (!(v[3] < kind.minSpeed) && !(v[3] > kind.maxSpeed)) {
                  for (int ix = 0; ix < this.route.size(); ix++) {
                     RoutePoint p = this.route.get(ix);
                     this.route.set(ix, ix == this.route.size() - 1 ? new RoutePoint(v[0], v[1], v[2], v[3]) : new RoutePoint(p.x(), p.y(), p.z(), v[3]));
                  }

                  this.manualAltitude.add(this.route.size() - 1);
                  this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.eebd7700aa");
                  return true;
               } else {
                  this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.6e94c78f78")
                     + kind.minSpeed
                     + "–"
                     + kind.maxSpeed
                     + FlightUi.localized("ui.deepstrike.deep_strike_screen.ea49235b0a");
                  return false;
               }
            } else {
               this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.20ab15e20d");
               return false;
            }
         } catch (NumberFormatException var5) {
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.dec6b10d51");
            return false;
         }
      }
   }

   private void launch() {
      if (!this.settings || this.tab != DeepStrikeScreen.Tab.CONFIG || !this.configurationChanged() || this.applyConfig()) {
         if (this.route.isEmpty()) {
            this.settings = true;
            this.selectTab(DeepStrikeScreen.Tab.CONFIG);
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.fdc1d25663");
         } else {
            RoutePoint target = this.route.get(this.route.size() - 1);
            double dx = target.x() - (this.launcherPos.getX() + 0.5);
            double dz = target.z() - (this.launcherPos.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            MissileKind kind = this.loadedKind();
            if (dist < kind.minRange) {
               this.settings = true;
               this.selectTab(DeepStrikeScreen.Tab.CONFIG);
               this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.7924510b9c")
                  + kind.minRange
                  + FlightUi.localized("ui.deepstrike.deep_strike_screen.99abed765e");
            } else if (dist > kind.maxRange) {
               this.settings = true;
               this.selectTab(DeepStrikeScreen.Tab.CONFIG);
               this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.c5993f354b")
                  + kind.maxRange
                  + FlightUi.localized("ui.deepstrike.deep_strike_screen.99abed765e");
            } else {
               DeepStrikeNetwork.CHANNEL
                  .sendToServer(new DeepStrikeNetwork.LaunchMission(this.launcherPos, false, List.copyOf(this.route), this.frequency.getValue()));
               this.minecraft.setScreen(null);
            }
         }
      }
   }

   private void loadPreset(int index) {
      MissileKind kind = this.loadedKind();
      RoutePoint target = this.route.isEmpty()
         ? PresetRoutes.defaultTarget(this.launcherPos, this.facing, kind, (x, z) -> this.groundAt(x, z, Double.NaN))
         : this.route.get(this.route.size() - 1);
      List<RoutePoint> preset = PresetRoutes.build(
         index, this.launcherPos, this.facing, target, kind, this.minecraft.level.getMaxBuildHeight() - 1, (x, z) -> this.groundAt(x, z, Double.NaN)
      );
      this.route.clear();
      this.manualAltitude.clear();
      this.route.addAll(preset);
      this.manualAltitude.add(this.route.size() - 1);
      this.selected = this.route.size() - 1;
      this.syncConfig();
      this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.553609e1f3");
   }

   private double groundAt(double x, double z, double fallback) {
      BlockPos p = BlockPos.containing(x, 0.0, z);
      return this.minecraft.level.hasChunkAt(p) ? this.minecraft.level.getHeight(Types.MOTION_BLOCKING, p.getX(), p.getZ()) : fallback;
   }

   private Path presetPath() {
      return this.minecraft.gameDirectory.toPath().resolve("config/deepstrike-route.json");
   }

   private void savePreset() {
      if (this.route.isEmpty()) {
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.0a2b1df82d");
      } else {
         try {
            Files.createDirectories(this.presetPath().getParent());
            Files.writeString(this.presetPath(), new GsonBuilder().setPrettyPrinting().create().toJson(this.route));
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.1bb6d220d7");
         } catch (Exception var2) {
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.43ff4d26ed");
         }
      }
   }

   private void restorePreset() {
      try {
         RoutePoint[] points = (RoutePoint[])new Gson().fromJson(Files.readString(this.presetPath()), RoutePoint[].class);
         if (points == null || points.length == 0 || points.length > 32) {
            throw new IllegalArgumentException();
         }

         for (RoutePoint p : points) {
            if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z()) || !Double.isFinite(p.speed())) {
               throw new IllegalArgumentException();
            }
         }

         this.route.clear();
         this.manualAltitude.clear();
         this.route.addAll(List.of(points));

         for (int i = 0; i < this.route.size(); i++) {
            this.manualAltitude.add(i);
         }

         this.selected = this.route.size() - 1;
         this.syncConfig();
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.553609e1f3");
      } catch (Exception var6) {
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_screen.e668331b6e");
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 256 && this.settings) {
         this.settings = false;
         this.layoutFields();
         return true;
      } else {
         return super.keyPressed(key, scan, mods);
      }
   }

   public void removed() {
      if (this.terrain != null) {
         this.terrain.close();
         this.terrain = null;
      }

      this.dirty = true;
   }

   List<RoutePoint> routeSnapshot() {
      return List.copyOf(this.route);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private record Hit(int x, int y, int w, int h, Runnable action) {
      boolean contains(double a, double b) {
         return a >= this.x && a < this.x + this.w && b >= this.y && b < this.y + this.h;
      }
   }

   private static enum Tab {
      CONFIG,
      PRESETS,
      JOURNAL;
   }
}
