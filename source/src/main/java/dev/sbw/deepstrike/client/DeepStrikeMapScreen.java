package dev.sbw.deepstrike.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.math.Axis;
import dev.sbw.deepstrike.game.CommandCenterData;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileItem;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RadioFrequency;
import dev.sbw.deepstrike.game.PresetRoutes;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.neoforge.client.event.ScreenEvent.Render.Post;

public final class DeepStrikeMapScreen extends Screen implements EquipmentUiTheme {
   private double plannerUserScale = 3.0;
   private double cameraX;
   private double cameraZ;
   private final TerrainMap terrain = new TerrainMap();
   private Consumer<List<RoutePoint>> commandCompletion;
   private Screen commandParent;
   private boolean commandOverview;
   private int commandRefresh;
   private MissileKind commandKind;
   private final BlockPos launcherPos;
   private final Direction facing;
   private final LauncherBlock.Family family;
   private final List<RoutePoint> route = new ArrayList<>();
   private final Set<Integer> manualAltitude = new HashSet<>();
   private final List<DeepStrikeMapScreen.Hit> controls = new ArrayList<>();
   private List<String> journal = List.of();
   private int selected = -1;
   private int menuX = -1;
   private int menuY;
   private int menuPoint = -1;
   private int panelX = 56;
   private int panelY = 52;
   private int panelW;
   private int panelH;
   private int panelScroll;
   private boolean settings = false;
   private boolean clippedControls;
   private int listScroll;
   private int dragPoint = -1;
   private double pressX;
   private double pressY;
   private boolean mapDragging;
   private boolean moved;
   private DeepStrikeMapScreen.Tab tab = DeepStrikeMapScreen.Tab.CONFIG;
   private final EditBox[] config = new EditBox[4];
   private EditBox frequency;
   private String error = "";
   private static final ResourceLocation MISSILE_ICON = ResourceLocation.fromNamespaceAndPath("deepstrike", "textures/ui/missile_marker_16.png");
   private boolean trackingMissile = false;
   private int trackedMissileId = -1;
   private boolean routeOpen;
   private boolean profileOpen;
   private boolean chromeHidden;
   private int pointerX;
   private int pointerY;

   @Override
   public String equipmentSkin() {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && level.getBlockEntity(this.launcherPos) instanceof LauncherBlockEntity launcher) {
         MissileData data = MissileData.fromItemStack(launcher.loaded());
         if (data != null) {
            return data.camo();
         }
      }

      return "";
   }

   public static void openCommand(
      Screen parent, BlockPos pos, LauncherBlock.Family family, MissileKind kind, List<RoutePoint> initial, Consumer<List<RoutePoint>> callback
   ) {
      open(pos, Direction.NORTH, family);
      if (Minecraft.getInstance().screen instanceof DeepStrikeMapScreen screen) {
         screen.commandCompletion = callback;
         screen.commandParent = parent;
         screen.commandKind = kind;
         screen.route.clear();
         screen.route.addAll(initial);

         for (int i = 0; i < initial.size(); i++) {
            screen.manualAltitude.add(i);
         }

         screen.selected = initial.size() - 1;
         screen.syncConfig();
      }
   }

   public static void openOverview(Screen parent, CompoundTag unit) {
      open(BlockPos.of(unit.getLong("Position")), Direction.NORTH, LauncherBlock.Family.valueOf(unit.getString("Family")));
      if (Minecraft.getInstance().screen instanceof DeepStrikeMapScreen screen) {
         screen.commandParent = parent;
         screen.commandOverview = true;
         screen.profileOpen = false;
         screen.routeOpen = false;
      }
   }

   public DeepStrikeMapScreen(Screen parent, Screen escape, Entity cameraEntity, BlockPos pos, Direction facing, LauncherBlock.Family family) {
      super(FlightUi.text("DeepStrike"));
      this.launcherPos = pos.immutable();
      this.facing = facing;
      this.family = family;
   }

   public boolean isPauseScreen() {
      return false;
   }

   public static void open(BlockPos pos, Direction facing, LauncherBlock.Family family) {
      Minecraft mc = Minecraft.getInstance();
      mc.setScreen(new DeepStrikeMapScreen(null, null, mc.getCameraEntity(), pos, facing, family));
   }

   public void tick() {
      super.tick();
      if (this.commandParent != null && ++this.commandRefresh % 10 == 0) {
         CommandTabletNetwork.request("refresh", new CompoundTag());
      }

      if (this.trackingMissile) {
         GuidedMissileEntity m = RemoteFlights.get(this.trackedMissileId);
         if (m != null && m.isAlive() && RadioFrequency.playerReceives(this.minecraft.player, m.frequency())) {
            this.setCameraX(m.getX());
            this.setCameraZ(m.getZ());
         } else {
            UUID myId = this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
            boolean found = false;

            for (GuidedMissileEntity active : RemoteFlights.all()) {
               if (active.isAlive()
                  && RadioFrequency.playerReceives(this.minecraft.player, active.frequency())
                  && (myId == null || myId.equals(active.ownerId()))) {
                  this.trackedMissileId = active.getId();
                  this.setCameraX(active.getX());
                  this.setCameraZ(active.getZ());
                  found = true;
                  break;
               }
            }

            if (!found) {
               this.trackingMissile = false;
            }
         }
      }
   }

   public static void acceptJournal(List<String> entries) {
      if (Minecraft.getInstance().screen instanceof DeepStrikeMapScreen s) {
         s.journal = List.copyOf(entries);
      }
   }

   public void onClose() {
      if (this.commandParent != null) {
         this.minecraft.setScreen(this.commandParent);
      } else {
         super.onClose();
      }
   }

   public double getCameraX() {
      return this.cameraX;
   }

   public void setCameraX(double value) {
      this.cameraX = value;
   }

   public double getCameraZ() {
      return this.cameraZ;
   }

   public void setCameraZ(double value) {
      this.cameraZ = value;
   }

   private double mapDpiScale() {
      return Math.max(1.0, Math.min(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight()) / 1080.0);
   }

   public double getMapScale() {
      return this.plannerUserScale * this.mapDpiScale();
   }

   public void setMapScale(double value) {
      this.plannerUserScale = Math.max(0.125, Math.min(8.0, value)) / this.mapDpiScale();
   }

   public void removed() {
      this.terrain.close();
      super.removed();
   }

   public void centerOnLauncher() {
      this.setCameraX(this.launcherPos.getX() + 0.5);
      this.setCameraZ(this.launcherPos.getZ() + 0.5);
   }

   public double getEffectiveMapScale() {
      Minecraft mc = Minecraft.getInstance();
      double guiScale = mc != null && mc.getWindow() != null ? mc.getWindow().getGuiScale() : 1.0;
      if (guiScale <= 0.0) {
         guiScale = 1.0;
      }

      return this.getMapScale() / guiScale / UiViewport.scale();
   }

   public double worldX(double screenX) {
      return this.getCameraX() + (screenX - this.width / 2.0) / this.getEffectiveMapScale();
   }

   public double worldZ(double screenY) {
      return this.getCameraZ() + (screenY - this.height / 2.0) / this.getEffectiveMapScale();
   }

   public int screenX(double worldX) {
      return (int)Math.round(this.width / 2.0 + (worldX - this.getCameraX()) * this.getEffectiveMapScale());
   }

   public int screenY(double worldZ) {
      return (int)Math.round(this.height / 2.0 + (worldZ - this.getCameraZ()) * this.getEffectiveMapScale());
   }

   public MissileKind getLoadedMissileKind() {
      if (this.commandKind != null) {
         return this.commandKind;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.level.getBlockEntity(this.launcherPos) instanceof LauncherBlockEntity be) {
            ItemStack loaded = be.loaded();
            if (loaded.getItem() instanceof MissileItem m) {
               return m.kind();
            }
         }
         return switch (this.family) {
            case JAGM -> MissileKind.JAGM;
            case FP5 -> MissileKind.FP5;
            case PEACEKEEPER -> MissileKind.PEACEKEEPER;
         };
      }
   }

   public void init() {
      String previousFrequency = this.frequency == null && this.minecraft.level.getBlockEntity(this.launcherPos) instanceof LauncherBlockEntity launcher
         ? launcher.transmitFrequency()
         : (this.frequency == null ? "" : this.frequency.getValue());
      super.init();
      this.clearWidgets();
      this.setCameraX(this.launcherPos.getX() + 0.5);
      this.setCameraZ(this.launcherPos.getZ() + 0.5);
      cleanupMinimapWaypoints();
      this.panelW = Math.min(220, Math.max(176, this.width / 4));
      this.panelX = this.width - this.panelW - 12;
      this.panelY = 62;
      this.panelH = Math.max(100, this.height - 114 - (this.profileOpen ? 65 : 0));

      for (int i = 0; i < 4; i++) {
         EditBox box = new InstrumentField(this.font, 0, 0, this.panelW - 32, 20, FlightUi.text(""));
         box.setMaxLength(18);
         box.setTextColor(-1182997);
         box.setBordered(false);
         box.setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
         this.config[i] = (EditBox)this.addRenderableWidget(box);
      }

      this.frequency = new InstrumentField(
         this.font, this.width - 254, this.height - 29, 90, 19, FlightUi.text(FlightUi.localized("screen.deepstrike.frequency.label"))
      );
      this.frequency.setMaxLength(12);
      this.frequency.setFilter(value -> value.matches("[0-9]{0,12}"));
      this.frequency.setValue(previousFrequency);
      this.addRenderableWidget(this.frequency);
      this.frequency.setBordered(false);
      this.frequency.setTextColor(-1182997);
      this.frequency.setFormatter((v, c) -> FlightUi.text(v).getVisualOrderText());
      this.syncConfig();
      this.layoutFields();
   }

   private void syncConfig() {
      if (this.config[0] != null) {
         RoutePoint p = this.route.isEmpty()
            ? new RoutePoint(
               this.launcherPos.getX() + 0.5, this.launcherPos.getY(), this.launcherPos.getZ() + 0.5, this.getLoadedMissileKind().defaultSpeed
            )
            : this.route.get(Math.max(0, Math.min(this.selected, this.route.size() - 1)));
         double[] v = new double[]{p.x(), p.y(), p.z(), p.speed()};

         for (int i = 0; i < 4; i++) {
            this.config[i].setValue(String.format(Locale.ROOT, "%.2f", v[i]));
         }

         this.error = "";
      }
   }

   private void layoutFields() {
      if (this.frequency != null) {
         this.frequency.visible = !this.chromeHidden && !this.commandOverview;
         this.frequency.active = this.frequency.visible;
         if (!this.frequency.visible) {
            this.frequency.setFocused(false);
         }
      }

      for (int i = 0; i < 4; i++) {
         EditBox b = this.config[i];
         if (b != null) {
            int y = this.panelY + 72 + i * 42 - this.panelScroll;
            b.setX(this.panelX + 18);
            b.setY(y);
            b.setWidth(this.panelW - 36);
            b.visible = !this.chromeHidden
               && this.settings
               && this.tab == DeepStrikeMapScreen.Tab.CONFIG
               && y >= this.panelY + 48
               && y + 20 < this.panelY + this.panelH - 8;
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

   private void selectTab(DeepStrikeMapScreen.Tab next) {
      this.tab = next;
      this.panelScroll = 0;
      this.layoutFields();
      if (next == DeepStrikeMapScreen.Tab.JOURNAL) {
         DeepStrikeNetwork.CHANNEL.sendToServer(new DeepStrikeNetwork.RequestJournal(this.launcherPos));
         UnitJournalScreen.open(this.launcherPos);
      }
   }

   public void render(GuiGraphics g, int mx, int my, float pt) {
      this.pointerX = mx;
      this.pointerY = my;
      this.controls.clear();
      this.layoutFields();
      this.terrain
         .refresh(
            this.getCameraX(),
            this.getCameraZ(),
            Math.max(1, (int)Math.round(this.width / this.getEffectiveMapScale())),
            Math.max(1, (int)Math.round(this.height / this.getEffectiveMapScale()))
         );
      this.terrain.render(g, 0, 0, this.width, this.height);
      g.pose().pushPose();
      g.pose().translate(0.0F, 0.0F, 1100.0F);
      if (this.commandOverview) {
         this.renderCommandOverview(g);
         this.renderChromeToggle(g);
         g.pose().popPose();
      } else {
         if (!this.chromeHidden) {
            this.renderRangeRings(g);
         }

         this.renderRoute(g);
         this.renderActiveFlights(g);
         g.flush();
         g.pose().translate(0.0F, 0.0F, 10.0F);
         if (!this.chromeHidden) {
            this.renderTacticalHud(g, mx, my);
         }

         this.renderChromeToggle(g);
         if (this.settings && !this.chromeHidden) {
            this.drawSettings(g);
         }

         if (this.menuX >= 0) {
            this.renderContextMenu(g);
         }

         for (EditBox field : this.config) {
            if (field.visible) {
               field.render(g, mx, my, pt);
            }
         }

         if (this.frequency.visible) {
            this.frequency.render(g, mx, my, pt);
         }

         g.pose().popPose();
      }
   }

   private void renderCommandOverview(GuiGraphics g) {
      CompoundTag snapshot = CommandTabletScreen.latestSnapshot;
      CompoundTag network = snapshot.getCompound("Network");
      HashSet<UUID> liveTasks = new HashSet<>();

      for (Tag raw : snapshot.getList("Flights", 10)) {
         CompoundTag f = (CompoundTag)raw;
         if (f.hasUUID("Task")) {
            liveTasks.add(f.getUUID("Task"));
         }
      }

      g.enableScissor(0, 58, this.width, this.height - 48);

      for (Tag rawx : network.getList("Tasks", 10)) {
         CompoundTag task = (CompoundTag)rawx;
         if (CommandCenterData.active(task) || liveTasks.contains(task.getUUID("Id"))) {
            ListTag points = task.getList("Route", 10);
            if (!points.isEmpty()) {
               CompoundTag first = points.getCompound(0);

               for (Tag assigned : task.getList("Assignments", 10)) {
                  CompoundTag assignment = (CompoundTag)assigned;

                  for (Tag ur : network.getList("Units", 10)) {
                     CompoundTag u = (CompoundTag)ur;
                     if (u.getUUID("Id").equals(assignment.getUUID("Unit"))
                        && u.getString("Dimension").equals(this.minecraft.level.dimension().location().toString())) {
                        BlockPos pos = BlockPos.of(u.getLong("Position"));
                        this.commandLine(
                           g,
                           this.screenX(pos.getX() + 0.5),
                           this.screenY(pos.getZ() + 0.5),
                           this.screenX(first.getDouble("X")),
                           this.screenY(first.getDouble("Z")),
                           1889643392
                        );
                     }
                  }
               }

               for (int i = 1; i < points.size(); i++) {
                  CompoundTag a = points.getCompound(i - 1);
                  CompoundTag b = points.getCompound(i);
                  this.commandLine(
                     g,
                     this.screenX(a.getDouble("X")),
                     this.screenY(a.getDouble("Z")),
                     this.screenX(b.getDouble("X")),
                     this.screenY(b.getDouble("Z")),
                     1889643392
                  );
               }
            }
         }
      }

      g.disableScissor();
      ArrayList<int[]> labelBoxes = new ArrayList<>();

      for (Tag rawxx : network.getList("Units", 10)) {
         CompoundTag unit = (CompoundTag)rawxx;
         if (unit.getString("Dimension").equals(this.minecraft.level.dimension().location().toString())) {
            BlockPos pos = BlockPos.of(unit.getLong("Position"));
            int x = this.screenX(pos.getX() + 0.5);
            int y = this.screenY(pos.getZ() + 0.5);
            if (x >= 0 && x <= this.width && y >= 62 && y <= this.height - 54) {
               int color = unit.getString("State").equals("OFFLINE") ? -5196890 : (unit.getString("State").equals("BUSY") ? -1000096 : -4668269);
               g.renderOutline(x - 6, y - 6, 13, 13, color);
               this.commandLabel(g, labelBoxes, FlightUi.clip(unit.getString("Name"), 140), x + 12, y - 5, color);
            }
         }
      }

      for (Tag rawxxx : snapshot.getList("Flights", 10)) {
         CompoundTag f = (CompoundTag)rawxxx;
         int x = this.screenX(f.getDouble("X"));
         int y = this.screenY(f.getDouble("Z"));
         if (x >= 0 && x <= this.width && y >= 62 && y <= this.height - 54) {
            FlightUi.crosshair(g, x, y, 7, -6696818);
            this.commandLabel(g, labelBoxes, FlightUi.clip(f.getString("Name"), 160), x + 12, y - 5, -6696818);
         }
      }

      if (!this.chromeHidden) {
         g.fill(0, 0, this.width, 58, -182838503);
         FlightUi.label(g, "DEEPSTRIKE / " + network.getString("Nickname"), 24, 20, -1182997);
         g.fill(0, this.height - 48, this.width, this.height, -182838503);
         FlightUi.label(g, Component.translatable("command.deepstrike.map_hint").getString(), 24, this.height - 30, -5196890);
         this.button(g, this.width - 170, this.height - 38, 146, 26, Component.translatable("command.deepstrike.back").getString(), this::onClose);
      }
   }

   private void commandLabel(GuiGraphics g, List<int[]> occupied, String value, int x, int y, int color) {
      int w = this.font.width(FlightUi.text(value));

      for (int[] b : occupied) {
         if (x < b[0] + b[2] + 4 && x + w + 4 > b[0] && y < b[1] + 14 && y + 14 > b[1]) {
            return;
         }
      }

      occupied.add(new int[]{x, y, w});
      FlightUi.label(g, value, x, y, color);
   }

   private void commandLine(GuiGraphics g, int ax, int ay, int bx, int by, int color) {
      if ((ax >= 0 || bx >= 0)
         && (ax <= this.width || bx <= this.width)
         && (ay >= 58 || by >= 58)
         && (ay <= this.height - 48 || by <= this.height - 48)) {
         double dx = (double)bx - ax;
         double dy = (double)by - ay;
         g.pose().pushPose();
         g.pose().translate(ax, ay, 0.0F);
         g.pose().mulPose(Axis.ZP.rotationDegrees((float)Math.toDegrees(Math.atan2(dy, dx))));
         g.fill(0, 0, (int)Math.ceil(Math.hypot(dx, dy)), 1, color);
         g.pose().popPose();
      }
   }

   private void renderActiveFlights(GuiGraphics g) {
      Minecraft mc = Minecraft.getInstance();
      UUID myId = mc.player != null ? mc.player.getUUID() : null;

      for (GuidedMissileEntity m : RemoteFlights.all()) {
         if (m.isAlive() && RadioFrequency.playerReceives(mc.player, m.frequency())) {
            boolean isMine = myId != null && myId.equals(m.ownerId());
            int sx = this.screenX(m.getX());
            int sy = this.screenY(m.getZ());
            long time = System.currentTimeMillis();
            float pingRadius = 8.0F + (float)(time % 1000L) / 60.0F;
            FlightUi.circle(g, sx, sy, pingRadius, isMine ? -16711868 : -1000096, false);
            ResourceLocation missileIcon = RadarContactRenderer.getIconForKind(m.kind().ordinal());
            if (missileIcon == null) {
               missileIcon = MISSILE_ICON;
            }

            g.pose().pushPose();
            g.pose().translate(sx, sy, 0.0F);
            float heading = m.getYRot();
            g.pose().mulPose(Axis.ZP.rotationDegrees(heading - 180.0F));
            g.blit(missileIcon, -8, -8, 16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
            g.pose().popPose();
            int markerColor = isMine ? -16711868 : -4668269;
            if (!this.chromeHidden) {
               double speedKmh = FlightUnits.kilometresPerHour(m.getDeltaMovement().length());
               if (speedKmh < 1.0) {
                  speedKmh = m.kind().defaultSpeed;
               }

               String header = isMine
                  ? FlightUi.localized("ui.deepstrike.deep_strike_map_screen.95e8771578") + m.kind().name() + "]"
                  : FlightUi.localized("ui.deepstrike.deep_strike_map_screen.8ee8c5aaef") + m.kind().name() + "]";
               String details = String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.99692c83d1"), m.getY(), speedKmh);
               int tw = Math.max(this.font.width(FlightUi.text(header)), this.font.width(FlightUi.text(details)));
               FlightUi.panel(g, sx + 14, sy - 14, tw + 12, 28, -182838503, markerColor);
               FlightUi.label(g, header, sx + 20, sy - 10, markerColor);
               FlightUi.label(g, details, sx + 20, sy + 2, -1182997);
            }

            int lx = this.screenX(this.launcherPos.getX() + 0.5);
            int ly = this.screenY(this.launcherPos.getZ() + 0.5);
            FlightUi.line(g, lx, ly, sx, sy, isMine ? -6696818 : -1000096);
            if (!this.route.isEmpty()) {
               RoutePoint target = this.route.get(this.route.size() - 1);
               FlightUi.line(g, sx, sy, this.screenX(target.x()), this.screenY(target.z()), markerColor);
            }
         }
      }
   }

   private void renderRangeRings(GuiGraphics g) {
      double scale = this.getEffectiveMapScale();
      int lx = this.screenX(this.launcherPos.getX() + 0.5);
      int ly = this.screenY(this.launcherPos.getZ() + 0.5);
      MissileKind kind = this.getLoadedMissileKind();
      int minRange = kind.minRange;
      int maxRange = kind.maxRange;
      float rMin = (float)(minRange * scale);
      float rMax = (float)(maxRange * scale);
      if (rMin > 1.0F) {
         FlightUi.circle(g, lx, ly, rMin, -1000096, true);
         if (rMin > 24.0F) {
            String minLabel = String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.9c6b8cd55a"), minRange, kind.name());
            int tw = this.font.width(FlightUi.text(minLabel));
            FlightUi.panel(g, lx - tw / 2 - 6, ly - (int)rMin - 16, tw + 12, 14, -871428340, -1000096);
            FlightUi.centered(g, minLabel, lx, ly - (int)rMin - 13, -1000096);
         }
      }

      FlightUi.circle(g, lx, ly, rMax, -4668269, true);
      if (rMax > 24.0F) {
         String maxLabel = String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.42e24e8acf"), maxRange, kind.name());
         int tw = this.font.width(FlightUi.text(maxLabel));
         FlightUi.panel(g, lx - tw / 2 - 6, ly - (int)rMax - 16, tw + 12, 14, -871428340, -4668269);
         FlightUi.centered(g, maxLabel, lx, ly - (int)rMax - 13, -4668269);
      }

      FlightUi.rounded(g, lx - 7, ly - 7, 15, 15, -14078937);
      g.renderOutline(lx - 7, ly - 7, 15, 15, -4668269);
      FlightUi.cornerBrackets(g, lx - 9, ly - 9, 19, 19, 4, -4668269);
      int px = lx + this.facing.getStepX() * 12;
      int py = ly + this.facing.getStepZ() * 12;
      FlightUi.line(g, lx, ly, px, py, -469622);
      g.fill(px - 2, py - 2, px + 3, py + 3, -469622);
      FlightUi.label(g, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.f1b908e4dd") + this.family.name(), lx + 12, ly - 5, -1182997);
   }

   private void renderRoute(GuiGraphics g) {
      int lx = this.screenX(this.launcherPos.getX() + 0.5);
      int ly = this.screenY(this.launcherPos.getZ() + 0.5);
      MissileKind kind = this.getLoadedMissileKind();

      for (int i = 0; i < this.route.size(); i++) {
         RoutePoint p = this.route.get(i);
         int x = this.screenX(p.x());
         int y = this.screenY(p.z());
         int prevX = i == 0 ? lx : this.screenX(this.route.get(i - 1).x());
         int prevY = i == 0 ? ly : this.screenY(this.route.get(i - 1).z());
         FlightUi.line(g, prevX, prevY, x, y, i == this.route.size() - 1 ? -4668269 : -1000096);
         int mx = (prevX + x) / 2;
         int my = (prevY + y) / 2;
         double a = Math.atan2(y - prevY, x - prevX);

         for (int side : new int[]{-1, 1}) {
            for (int s = 0; s < 7; s++) {
               int ax = mx - (int)(Math.cos(a + side * 0.55) * s);
               int ay = my - (int)(Math.sin(a + side * 0.55) * s);
               g.fill(ax, ay, ax + 2, ay + 2, i == this.route.size() - 1 ? -4668269 : -1000096);
            }
         }

         boolean isTarget = i == this.route.size() - 1;
         if (isTarget) {
            double dx = p.x() - (this.launcherPos.getX() + 0.5);
            double dz = p.z() - (this.launcherPos.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            boolean inRange = dist >= kind.minRange && dist <= kind.maxRange;
            int reticleColor = inRange ? -6696818 : (dist < kind.minRange ? -1000096 : -1076610);
            FlightUi.crosshair(g, x, y, 16, reticleColor);
            FlightUi.circle(g, x, y, 10.0F, reticleColor, false);
            FlightUi.cornerBrackets(g, x - 12, y - 12, 25, 25, 5, reticleColor);
            String distLabel = this.ui("target") + String.format(Locale.ROOT, " · %.0f m", dist);
            if (!inRange) {
               distLabel = dist < kind.minRange
                  ? String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.769270c194"), kind.minRange)
                  : String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.493a2dfac5"), kind.maxRange);
            }

            int tw = this.font.width(FlightUi.text(distLabel));
            FlightUi.panel(g, x + 16, y - 8, tw + 12, 16, -182838503, reticleColor);
            FlightUi.label(g, distLabel, x + 22, y - 4, reticleColor);
         } else {
            FlightUi.rounded(g, x - 8, y - 8, 17, 17, -14078937);
            g.renderOutline(x - 8, y - 8, 17, 17, i == this.selected ? -4668269 : -10854328);
            FlightUi.centered(g, Integer.toString(i + 1), x, y - 4, i == this.selected ? -4668269 : -1182997);
            String caption = "Y " + (int)p.y();
            int tw = this.font.width(FlightUi.text(caption));
            FlightUi.rounded(g, x + 12, y - 7, tw + 8, 14, -182838503);
            FlightUi.label(g, caption, x + 16, y - 4, -5196890);
         }
      }
   }

   private String ui(String key) {
      return FlightUi.localized("screen.deepstrike.planner." + key);
   }

   private int listWidth() {
      return Math.min(192, Math.max(142, this.width / 5));
   }

   private int profileY() {
      return this.height - (this.profileOpen ? 106 : 47);
   }

   private void renderTacticalHud(GuiGraphics g, int mx, int my) {
      FlightUi.panel(g, 10, 10, this.width - 20, 42, -182838503, -10854328);
      this.button(g, 20, 19, 28, 24, "≡", () -> {
         this.routeOpen = !this.routeOpen;
         if (this.routeOpen) {
            this.settings = false;
            this.layoutFields();
         }
      });
      this.button(g, 54, 19, 28, 24, "Y", () -> {
         this.profileOpen = !this.profileOpen;
         this.panelH = Math.max(100, this.height - 114 - (this.profileOpen ? 65 : 0));
         this.layoutFields();
      });
      if (this.width > 650) {
         FlightUi.label(g, this.ui("title"), 94, 19, -1182997);
         FlightUi.label(g, this.getLoadedMissileKind().title().getString(), 94, 35, -5196890);
      }

      this.button(g, this.width - 260, 19, 80, 24, this.ui("presets"), () -> {
         this.settings = true;
         this.selectTab(DeepStrikeMapScreen.Tab.PRESETS);
      });
      this.button(g, this.width - 174, 19, 110, 24, this.ui("journal"), () -> UnitJournalScreen.open(this.launcherPos));
      this.button(g, this.width - 56, 19, 32, 24, "×", this::onClose);
      int lw = this.listWidth();
      if (this.routeOpen && !this.settings) {
         FlightUi.panel(g, 12, 62, lw, Math.max(96, this.profileY() - 74), -182838503, -10854328);
         FlightUi.label(g, this.ui("route"), 24, 75, -1182997);
         FlightUi.label(g, String.format(Locale.ROOT, "%02d", this.route.size()), lw - 16, 75, -5196890);
         int visible = Math.max(1, (this.profileY() - 150) / 39);
         this.listScroll = Math.max(0, Math.min(this.listScroll, Math.max(0, this.route.size() - visible)));

         for (int row = 0; row < visible && row + this.listScroll < this.route.size(); row++) {
            int i = row + this.listScroll;
            int y = 97 + row * 39;
            RoutePoint p = this.route.get(i);
            boolean active = i == this.selected;
            if (active) {
               FlightUi.rounded(g, 19, y, lw - 14, 35, -12498886);
               g.fill(19, y + 5, 21, y + 30, -1000096);
            }

            FlightUi.label(g, String.format(Locale.ROOT, "%02d", i + 1), 27, y + 6, active ? -469622 : -5196890);
            FlightUi.label(g, i == this.route.size() - 1 ? this.ui("target") : this.ui("waypoint"), 50, y + 6, -1182997);
            FlightUi.label(g, String.format(Locale.ROOT, "Y %.0f m · %.0f km/h", p.y(), p.speed()), 50, y + 20, -5196890);
            this.controls.add(new DeepStrikeMapScreen.Hit(19, y, lw - 14, 35, () -> {
               this.selected = i;
               this.settings = true;
               this.tab = DeepStrikeMapScreen.Tab.CONFIG;
               this.panelScroll = 0;
               this.syncConfig();
               this.layoutFields();
            }));
         }

         if (this.route.isEmpty()) {
            int yy = 105;

            for (FormattedCharSequence line : this.font.split(FlightUi.text(this.ui("empty")), lw - 24)) {
               g.drawString(this.font, line, 24, yy, -5196890, false);
               yy += 14;
            }
         }

         int by = this.profileY() - 46;
         this.button(g, 20, by, lw / 2 - 10, 24, "−", () -> {
            if (this.selected >= 0 && this.selected < this.route.size()) {
               this.removePoint(this.selected);
               this.syncConfig();
               this.layoutFields();
            }
         });
         this.button(g, 20 + lw / 2 - 4, by, lw / 2 - 10, 24, "⌖", this::centerOnLauncher);
      }

      int mapRight = this.settings ? this.panelX - 8 : this.width - 12;
      this.button(g, mapRight - 80, this.profileY() - 30, 34, 24, "−", () -> this.setMapScale(this.getMapScale() / 1.35));
      this.button(g, mapRight - 40, this.profileY() - 30, 34, 24, "+", () -> this.setMapScale(this.getMapScale() * 1.35));
      if (!this.settings) {
         this.button(g, this.width - 110, 63, 98, 24, this.ui("edit"), this::toggleSettings);
      }

      this.button(g, mapRight - 120, this.profileY() - 30, 34, 24, "⌖", this::fitRoute);
      if (this.profileOpen) {
         this.renderAltitudeProfile(g);
      }

      FlightUi.panel(g, 12, this.height - 39, this.width - 24, 29, -182838503, -10854328);
      String status = this.error.isEmpty() ? this.ui("map_hint") : this.error;
      if (this.width >= 700) {
         FlightUi.label(g, FlightUi.clip(status, this.width - 390), 24, this.height - 29, this.error.isEmpty() ? -5196890 : -469622);
      }

      FlightUi.label(g, this.ui("frequency"), this.width - 325, this.height - 27, -5196890);
      FlightUi.rounded(g, this.width - 260, this.height - 32, 100, 22, -14078937);
      this.button(
         g,
         this.width - 150,
         this.height - 33,
         126,
         24,
         this.commandCompletion == null ? this.ui("launch") : Component.translatable("command.deepstrike.use_route").getString(),
         this::launch
      );
   }

   private void toggleChrome() {
      this.chromeHidden = !this.chromeHidden;
      this.menuX = -1;
      this.setFocused(null);
      this.layoutFields();
   }

   private void renderChromeToggle(GuiGraphics g) {
      int x = this.chromeHidden ? this.width - 180 : this.width - 414;
      int y = this.chromeHidden ? 12 : 19;
      this.button(
         g,
         x,
         y,
         this.chromeHidden ? 168 : 144,
         26,
         FlightUi.localized(this.chromeHidden ? "screen.deepstrike.map.show_gui" : "screen.deepstrike.map.hide_gui"),
         this::toggleChrome
      );
   }

   private void fitRoute() {
      if (this.route.isEmpty()) {
         this.centerOnLauncher();
      } else {
         double minX = this.launcherPos.getX();
         double maxX = minX;
         double minZ = this.launcherPos.getZ();
         double maxZ = minZ;

         for (RoutePoint p : this.route) {
            minX = Math.min(minX, p.x());
            maxX = Math.max(maxX, p.x());
            minZ = Math.min(minZ, p.z());
            maxZ = Math.max(maxZ, p.z());
         }

         int left = this.routeOpen && !this.settings ? this.listWidth() + 35 : 25;
         int right = this.settings ? this.panelX - 25 : this.width - 30;
         int top = 100;
         int bottom = this.profileY() - 40;
         double scale = Math.min((right - left) / Math.max(100.0, maxX - minX), (bottom - top) / Math.max(100.0, maxZ - minZ));
         this.setMapScale(scale * this.minecraft.getWindow().getGuiScale() * UiViewport.scale());
         double effective = this.getEffectiveMapScale();
         this.setCameraX((minX + maxX) / 2.0 - ((left + right) / 2.0 - this.width / 2.0) / effective);
         this.setCameraZ((minZ + maxZ) / 2.0 - ((top + bottom) / 2.0 - this.height / 2.0) / effective);
      }
   }

   private void renderAltitudeProfile(GuiGraphics g) {
      int x = 12;
      int y = this.profileY();
      int w = this.width - 24;
      int h = 59;
      FlightUi.panel(g, x, y, w, h, -182838503, -10854328);
      FlightUi.label(g, this.ui("altitude_profile"), x + 12, y + 8, -5196890);
      if (this.route.isEmpty()) {
         FlightUi.label(g, this.ui("profile_empty"), x + 12, y + 32, -5196890);
      } else {
         double distance = 0.0;
         double min = this.launcherPos.getY();
         double max = min;
         double[] cumulative = new double[this.route.size() + 1];
         double px = this.launcherPos.getX() + 0.5;
         double pz = this.launcherPos.getZ() + 0.5;

         for (int i = 0; i < this.route.size(); i++) {
            RoutePoint p = this.route.get(i);
            distance += Math.hypot(p.x() - px, p.z() - pz);
            cumulative[i + 1] = distance;
            min = Math.min(min, p.y());
            max = Math.max(max, p.y());
            px = p.x();
            pz = p.z();
         }

         double span = Math.max(20.0, max - min);
         int left = x + 105;
         int right = x + w - 65;
         int bottom = y + 47;
         int top = y + 24;
         g.fill(left, bottom, right, bottom + 1, -10854328);
         int beforeX = left;
         int beforeY = bottom - (int)((this.launcherPos.getY() - min) / span * (bottom - top));

         for (int i = 0; i < this.route.size(); i++) {
            RoutePoint p = this.route.get(i);
            int xx = left + (int)(cumulative[i + 1] / Math.max(1.0, distance) * (right - left));
            int yy = bottom - (int)((p.y() - min) / span * (bottom - top));
            FlightUi.line(g, beforeX, beforeY, xx, yy, -1000096);
            FlightUi.rounded(g, xx - 2, yy - 2, 5, 5, i == this.selected ? -1182997 : -1000096);
            if (i == this.selected) {
               FlightUi.label(g, "Y " + (int)p.y(), Math.min(right - 35, xx + 5), y + 12, -1182997);
            }

            beforeX = xx;
            beforeY = yy;
         }

         FlightUi.label(g, (int)min + "–" + (int)max + " m", x + 12, y + 32, -1182997);
         FlightUi.label(g, String.format(Locale.ROOT, "%.1f km", distance / 1000.0), right + 8, y + 32, -5196890);
      }
   }

   private void renderContextMenu(GuiGraphics g) {
      int x = Math.min(this.menuX, this.width - 165);
      int y = Math.min(this.menuY, this.height - 75);
      FlightUi.panel(g, x, y, 155, 60, -182838503, -10854328);
      if (this.menuPoint < 0) {
         this.button(g, x + 6, y + 6, 143, 24, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.5a756bff88"), () -> {
            this.addPoint(this.menuX, this.menuY);
            this.menuX = -1;
         });
         this.button(g, x + 6, y + 33, 143, 22, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.8fbe9b75cb"), () -> this.menuX = -1);
      } else {
         this.button(g, x + 6, y + 6, 143, 22, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.64b9882ba2"), () -> {
            this.editPoint(this.menuPoint);
            this.menuX = -1;
         });
         this.button(g, x + 6, y + 31, 143, 22, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.e83fca4e96"), () -> {
            this.removePoint(this.menuPoint);
            this.menuX = -1;
            this.syncConfig();
         });
      }
   }

   private void drawSettings(GuiGraphics g) {
      FlightUi.panel(g, this.panelX, this.panelY, this.panelW, this.panelH, -182838503, -10854328);
      String title = this.selected >= 0 && this.selected < this.route.size()
         ? this.ui(this.selected == this.route.size() - 1 ? "target" : "waypoint") + " " + String.format(Locale.ROOT, "%02d", this.selected + 1)
         : this.ui("edit");
      FlightUi.label(g, title, this.panelX + 14, this.panelY + 13, -1182997);
      this.button(g, this.panelX + this.panelW - 35, this.panelY + 8, 24, 22, "×", this::toggleSettings);
      this.button(
         g, this.panelX + 10, this.panelY + 31, (this.panelW - 26) / 2, 22, this.ui("parameters"), () -> this.selectTab(DeepStrikeMapScreen.Tab.CONFIG)
      );
      this.button(
         g,
         this.panelX + 16 + (this.panelW - 26) / 2,
         this.panelY + 31,
         (this.panelW - 26) / 2,
         22,
         this.ui("presets"),
         () -> this.selectTab(DeepStrikeMapScreen.Tab.PRESETS)
      );
      this.clippedControls = true;
      g.enableScissor(this.panelX + 5, this.panelY + 55, this.panelX + this.panelW - 5, this.panelY + this.panelH - 6);
      int y = this.panelY + 58 - this.panelScroll;
      if (this.tab == DeepStrikeMapScreen.Tab.CONFIG) {
         String[] names = new String[]{"X", this.ui("altitude") + " · Y", "Z", this.ui("speed")};

         for (int i = 0; i < 4; i++) {
            FlightUi.label(g, names[i], this.panelX + 14, y + i * 42, -5196890);
            FlightUi.rounded(g, this.panelX + 10, y + 12 + i * 42, this.panelW - 20, 25, -14078937);
         }

         this.button(g, this.panelX + 10, y + 176, this.panelW - 20, 25, this.ui("apply"), this::applyConfig);
         FlightUi.label(g, FlightUi.clip(this.ui("exact_altitude"), this.panelW - 28), this.panelX + 14, y + 214, -5196890);
         if (this.family == LauncherBlock.Family.JAGM) {
            FlightUi.label(g, this.ui("launcher_angle"), this.panelX + 14, y + 240, -5196890);
            String[] values = new String[]{"−15°", "+15°", "−5°", "+5°"};

            for (int i = 0; i < 4; i++) {
               int j = i;
               this.button(
                  g,
                  this.panelX + 10 + i * (this.panelW - 20) / 4,
                  y + 258,
                  (this.panelW - 24) / 4,
                  22,
                  values[i],
                  () -> DeepStrikeNetwork.CHANNEL
                     .sendToServer(
                        new DeepStrikeNetwork.AdjustLauncher(
                           this.launcherPos, j == 0 ? -15.0F : (j == 1 ? 15.0F : 0.0F), j == 2 ? -5.0F : (j == 3 ? 5.0F : 0.0F)
                        )
                     )
               );
            }
         }
      } else {
         String[] names = this.family == LauncherBlock.Family.PEACEKEEPER
            ? new String[]{"1.2 km", "3.6 km", "5.8 km"}
            : new String[]{this.ui("direct"), this.ui("detour"), this.ui("high")};

         for (int i = 0; i < 3; i++) {
            int k = i;
            this.button(g, this.panelX + 10, y + 8 + i * 35, this.panelW - 20, 27, names[i], () -> this.loadPreset(k));
         }

         this.button(g, this.panelX + 10, y + 126, this.panelW - 20, 25, this.ui("save_route"), this::savePreset);
         this.button(g, this.panelX + 10, y + 160, this.panelW - 20, 25, this.ui("load_route"), this::restorePreset);
         this.button(g, this.panelX + 10, y + 206, this.panelW - 20, 25, this.ui("clear"), () -> {
            this.route.clear();
            this.manualAltitude.clear();
            this.selected = -1;
            this.syncConfig();
         });
      }

      g.disableScissor();
      this.clippedControls = false;
   }

   private void button(GuiGraphics g, int x, int y, int w, int h, String value, Runnable action) {
      boolean hover = this.pointerX >= x && this.pointerX < x + w && this.pointerY >= y && this.pointerY < y + h;
      FlightUi.control(g, x, y, w, h, value, hover, true, false, hover && this.minecraft.mouseHandler.isLeftPressed());
      if (!this.clippedControls || y >= this.panelY + 55 && y + h <= this.panelY + this.panelH) {
         this.controls.add(new DeepStrikeMapScreen.Hit(x, y, w, h, action));
      }
   }

   public boolean mouseClicked(double x, double y, int b) {
      this.setFocused(null);
      if (!this.chromeHidden && !this.commandOverview && this.frequency.isMouseOver(x, y)) {
         this.setFocused(this.frequency);
         return this.frequency.mouseClicked(x, y, b);
      } else {
         for (EditBox box : this.config) {
            box.setFocused(false);
            if (box.visible && box.isMouseOver(x, y)) {
               box.setFocused(true);
               this.setFocused(box);
               return box.mouseClicked(x, y, b);
            }
         }

         for (int i = this.controls.size() - 1; i >= 0; i--) {
            DeepStrikeMapScreen.Hit hit = this.controls.get(i);
            if (hit.contains(x, y)) {
               if (b == 0) {
                  hit.action.run();
               }

               return true;
            }
         }

         if (this.overChrome(x, y)) {
            return true;
         } else {
            this.menuX = -1;
            if (this.commandOverview && b == 1) {
               return true;
            } else if (b == 1) {
               this.menuX = (int)x;
               this.menuY = (int)y;
               this.menuPoint = this.pointAt(x, y);
               return true;
            } else if (b == 0) {
               this.pressX = x;
               this.pressY = y;
               this.moved = false;
               this.mapDragging = true;
               this.dragPoint = this.pointAt(x, y);
               if (this.dragPoint >= 0) {
                  this.selected = this.dragPoint;
                  this.settings = true;
                  this.tab = DeepStrikeMapScreen.Tab.CONFIG;
                  this.syncConfig();
                  this.layoutFields();
               }

               return true;
            } else {
               return false;
            }
         }
      }
   }

   private boolean overChrome(double x, double y) {
      return this.chromeHidden
         ? x >= this.width - 180 && y >= 12.0 && y < 38.0
         : y < 58.0
            || y >= (this.commandOverview ? this.height - 48 : this.profileY() - 4)
            || this.routeOpen && !this.settings && x < this.listWidth() + 18
            || this.settings && x >= this.panelX - 4;
   }

   public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
      if (button == 0 && this.mapDragging) {
         this.moved = this.moved | Math.hypot(x - this.pressX, y - this.pressY) > 3.0;
         if (!this.moved) {
            return true;
         } else {
            if (this.dragPoint >= 0 && this.dragPoint < this.route.size()) {
               RoutePoint p = this.route.get(this.dragPoint);
               this.route.set(this.dragPoint, new RoutePoint(this.worldX(x), p.y(), this.worldZ(y), p.speed()));
               this.manualAltitude.add(this.dragPoint);
               this.syncConfig();
            } else {
               this.setCameraX(this.getCameraX() - dx / this.getEffectiveMapScale());
               this.setCameraZ(this.getCameraZ() - dy / this.getEffectiveMapScale());
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public boolean mouseReleased(double x, double y, int button) {
      if (button == 0 && this.mapDragging) {
         this.mapDragging = false;
         if (!this.commandOverview && !this.moved && this.dragPoint < 0 && !this.overChrome(x, y)) {
            this.addPoint(x, y);
            this.layoutFields();
         }

         this.dragPoint = -1;
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseScrolled(double x, double y, double scrollX, double delta) {
      if (!this.chromeHidden && this.routeOpen && !this.settings && x < this.listWidth() + 18) {
         this.listScroll = Math.max(0, this.listScroll - (int)delta);
         return true;
      } else if (!this.chromeHidden && this.settings && x >= this.panelX) {
         this.panelScroll = Math.max(0, Math.min(Math.max(0, 310 - this.panelH), this.panelScroll - (int)(delta * 24.0)));
         this.layoutFields();
         return true;
      } else if (this.overChrome(x, y)) {
         return true;
      } else {
         double wx = this.worldX(x);
         double wz = this.worldZ(y);
         this.setMapScale(Math.max(0.125, Math.min(8.0, this.getMapScale() * Math.pow(1.3, delta))));
         this.setCameraX(this.getCameraX() + wx - this.worldX(x));
         this.setCameraZ(this.getCameraZ() + wz - this.worldZ(y));
         return true;
      }
   }

   public boolean charTyped(char c, int mods) {
      if (this.frequency.isFocused()) {
         return this.frequency.charTyped(c, mods);
      } else {
         for (EditBox box : this.config) {
            if (box.isFocused() && box.visible) {
               return box.charTyped(c, mods);
            }
         }

         return false;
      }
   }

   private int pointAt(double x, double y) {
      for (int i = this.route.size() - 1; i >= 0; i--) {
         if (Math.abs(this.screenX(this.route.get(i).x()) - x) < 14.0 && Math.abs(this.screenY(this.route.get(i).z()) - y) < 14.0) {
            return i;
         }
      }

      return -1;
   }

   private void addPoint(double sx, double sy) {
      if (this.route.size() < (this.family == LauncherBlock.Family.PEACEKEEPER ? 1 : 32)) {
         double wx = this.worldX(sx);
         double wz = this.worldZ(sy);
         BlockPos p = BlockPos.containing(wx, 0.0, wz);
         int ground = this.minecraft.level != null && this.minecraft.level.hasChunkAt(p)
            ? this.minecraft.level.getHeight(Types.MOTION_BLOCKING, p.getX(), p.getZ())
            : this.launcherPos.getY();
         if (this.minecraft.level != null) {
            if (ground <= this.minecraft.level.getMinBuildHeight()) {
               ground = this.launcherPos.getY();
            }

            ground = Math.max(this.minecraft.level.getMinBuildHeight() + 2, Math.min(this.minecraft.level.getMaxBuildHeight() - 2, ground));
         }

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

         double defaultSpd = this.getLoadedMissileKind().defaultSpeed;
         this.route.add(new RoutePoint(Math.floor(wx) + 0.5, ground, Math.floor(wz) + 0.5, defaultSpd));
         this.selected = this.route.size() - 1;
         this.syncConfig();
      }
   }

   private void editPoint(int i) {
      this.selected = i;
      this.minecraft.setScreen(new RoutePointScreen(this, this.route.get(i), i == this.route.size() - 1, this.getLoadedMissileKind(), p -> {
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

   private boolean applyConfig() {
      if (this.route.isEmpty()) {
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.412e4d1514");
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

            if (Math.abs(v[0]) > 2.9999984E7 || Math.abs(v[2]) > 2.9999984E7) {
               this.error = FlightUi.localized("ui.deepstrike.route_point_screen.38d6074b2c");
               return false;
            } else if (!(v[1] < this.minecraft.level.getMinBuildHeight()) && !(v[1] >= this.minecraft.level.getMaxBuildHeight())) {
               MissileKind kind = this.getLoadedMissileKind();
               if (!(v[3] < kind.minSpeed) && !(v[3] > kind.maxSpeed)) {
                  int index = Math.max(0, Math.min(this.selected, this.route.size() - 1));
                  this.route.set(index, new RoutePoint(v[0], v[1], v[2], v[3]));
                  this.manualAltitude.add(index);
                  this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.eebd7700aa");
                  return true;
               } else {
                  this.error = String.format(
                     Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.1b8565cb9f"), kind.name(), kind.minSpeed, kind.maxSpeed
                  );
                  return false;
               }
            } else {
               this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.20ab15e20d");
               return false;
            }
         } catch (NumberFormatException var4) {
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.dec6b10d51");
            return false;
         }
      }
   }

   private void launch() {
      if (this.commandCompletion != null) {
         if (!this.settings || this.tab != DeepStrikeMapScreen.Tab.CONFIG || this.route.isEmpty() || !this.configurationChanged() || this.applyConfig()) {
            if (!this.route.isEmpty()) {
               this.commandCompletion.accept(List.copyOf(this.route));
               this.minecraft.setScreen(this.commandParent);
            }
         }
      } else if (!this.settings || this.tab != DeepStrikeMapScreen.Tab.CONFIG || this.route.isEmpty() || !this.configurationChanged() || this.applyConfig()) {
         if (!RadioFrequency.valid(this.frequency.getValue())) {
            this.error = FlightUi.localized("screen.deepstrike.frequency.invalid");
         } else if (this.route.isEmpty()) {
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.412e4d1514");
         } else {
            RoutePoint target = this.route.get(this.route.size() - 1);
            double dx = target.x() - (this.launcherPos.getX() + 0.5);
            double dz = target.z() - (this.launcherPos.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            MissileKind kind = this.getLoadedMissileKind();
            if (dist < kind.minRange) {
               this.error = String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.93a925750a"), kind.minRange);
            } else {
               int maxRange = kind.maxRange;
               if (this.minecraft.level.getBlockEntity(this.launcherPos) instanceof LauncherBlockEntity launcher) {
                  MissileData data = MissileData.fromItemStack(launcher.loaded());
                  if (data != null) {
                     maxRange = data.calculateMaxRange();
                  }
               }

               if (dist > maxRange) {
                  this.error = String.format(Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.78d3895ed8"), maxRange);
               } else {
                  DeepStrikeNetwork.CHANNEL
                     .sendToServer(new DeepStrikeNetwork.LaunchMission(this.launcherPos, false, List.copyOf(this.route), this.frequency.getValue()));
                  this.onClose();
                  this.minecraft.setScreen(null);
               }
            }
         }
      }
   }

   public static void cleanupMinimapWaypoints() {
      MapProviders.cleanupWaypoints();
   }

   private void loadPreset(int index) {
      List<RoutePoint> previous = List.copyOf(this.route);
      this.route.clear();
      this.manualAltitude.clear();
      if (this.family == LauncherBlock.Family.PEACEKEEPER) {
         int[] distance = new int[]{1200, 3600, 5800};
         double x = this.launcherPos.getX() + 0.5 + this.facing.getStepX() * distance[index];
         double z = this.launcherPos.getZ() + 0.5 + this.facing.getStepZ() * distance[index];
         this.route.add(new RoutePoint(x, this.launcherPos.getY(), z, MissileKind.PEACEKEEPER.defaultSpeed));
         this.selected = 0;
         this.syncConfig();
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.583f2b683f");
      } else {
         MissileKind kind = this.getLoadedMissileKind();
         RoutePoint target = previous.isEmpty()
            ? PresetRoutes.defaultTarget(this.launcherPos, this.facing, kind, this::groundAt)
            : previous.get(previous.size() - 1);
         this.route.addAll(PresetRoutes.build(index, this.launcherPos, this.facing, target, kind, this.minecraft.level.getMaxBuildHeight() - 1, this::groundAt));
         this.manualAltitude.add(this.route.size() - 1);
         this.selected = this.route.size() - 1;
         this.syncConfig();
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.553609e1f3");
      }
   }

   private double groundAt(double x, double z) {
      BlockPos p = BlockPos.containing(x, 0.0, z);
      if (this.minecraft.level == null || !this.minecraft.level.hasChunkAt(p)) {
         return Double.NaN;
      }

      int ground = this.minecraft.level.getHeight(Types.MOTION_BLOCKING, p.getX(), p.getZ());
      return ground <= this.minecraft.level.getMinBuildHeight() ? Double.NaN : ground;
   }

   private Path presetPath() {
      return this.minecraft.gameDirectory.toPath().resolve("config/deepstrike-route.json");
   }

   private void savePreset() {
      if (this.route.isEmpty()) {
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.0a2b1df82d");
      } else {
         try {
            Files.createDirectories(this.presetPath().getParent());
            Files.writeString(this.presetPath(), new GsonBuilder().setPrettyPrinting().create().toJson(this.route));
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.1bb6d220d7");
         } catch (Exception var2) {
            this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.43ff4d26ed");
         }
      }
   }

   private void restorePreset() {
      try {
         RoutePoint[] points = (RoutePoint[])new Gson().fromJson(Files.readString(this.presetPath()), RoutePoint[].class);
         if (points == null || points.length == 0 || points.length > (this.family == LauncherBlock.Family.PEACEKEEPER ? 1 : 32)) {
            throw new IllegalArgumentException();
         }

         for (RoutePoint point : points) {
            if (point == null || !Double.isFinite(point.x()) || !Double.isFinite(point.y()) || !Double.isFinite(point.z()) || !Double.isFinite(point.speed())) {
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
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.553609e1f3");
      } catch (Exception var6) {
         this.error = FlightUi.localized("ui.deepstrike.deep_strike_map_screen.e668331b6e");
      }
   }

   private boolean configurationChanged() {
      if (this.route.isEmpty()) {
         return false;
      } else {
         RoutePoint p = this.route.get(Math.max(0, Math.min(this.selected, this.route.size() - 1)));
         double[] values = new double[]{p.x(), p.y(), p.z(), p.speed()};

         try {
            for (int i = 0; i < 4; i++) {
               if (Math.abs(Double.parseDouble(this.config[i].getValue().replace(',', '.')) - values[i]) > 0.005) {
                  return true;
               }
            }

            return false;
         } catch (NumberFormatException var4) {
            return true;
         }
      }
   }

   public boolean keyPressed(int key, int scan, int mods) {
      if (key == 72 && !(this.getFocused() instanceof EditBox)) {
         this.toggleChrome();
         return true;
      } else if (key == 256) {
         if (this.menuX >= 0) {
            this.menuX = -1;
            return true;
         } else {
            this.onClose();
            return true;
         }
      } else if (this.frequency.isFocused() && this.frequency.keyPressed(key, scan, mods)) {
         return true;
      } else {
         for (EditBox box : this.config) {
            if (box.isFocused() && box.visible && box.keyPressed(key, scan, mods)) {
               return true;
            }
         }

         if (key == 258 && !this.chromeHidden) {
            List<EditBox> focusable = new ArrayList<>();

            for (EditBox boxx : this.config) {
               if (boxx.visible) {
                  focusable.add(boxx);
               }
            }

            focusable.add(this.frequency);
            int current = focusable.indexOf(this.getFocused());
            int next = Math.floorMod(current + ((mods & 1) != 0 ? -1 : 1), focusable.size());

            for (EditBox boxxx : focusable) {
               boxxx.setFocused(false);
            }

            this.setFocused((GuiEventListener)focusable.get(next));
            focusable.get(next).setFocused(true);
            return true;
         } else {
            return (key == 257 || key == 335) && this.settings && this.tab == DeepStrikeMapScreen.Tab.CONFIG ? this.applyConfig() : false;
         }
      }
   }

   public static void onScreenRenderPost(Post event) {
      Screen screen = event.getScreen();
      if (!(screen instanceof DeepStrikeMapScreen) && !(screen instanceof TacticalXaeroMap)) {
         MapProviders.View view = MapProviders.nativeView(screen);
         if (view != null) {
            Collection<GuidedMissileEntity> missiles = RemoteFlights.all();
            if (!missiles.isEmpty()) {
               double camX = view.x();
               double camZ = view.z();
               double scale = view.pixelsPerBlock();
               GuiGraphics g = event.getGuiGraphics();
               int width = screen.width;
               int height = screen.height;
               g.pose().pushPose();
               g.pose().translate(0.0F, 0.0F, 950.0F);
               Minecraft mc = Minecraft.getInstance();
               UUID myId = mc.player != null ? mc.player.getUUID() : null;
               double guiScale = mc.getWindow() != null ? mc.getWindow().getGuiScale() : 1.0;
               if (guiScale <= 0.0) {
                  guiScale = 1.0;
               }

               double effScale = scale / guiScale;

               for (GuidedMissileEntity m : missiles) {
                  if (m.isAlive() && RadioFrequency.playerReceives(mc.player, m.frequency())) {
                     boolean isMine = myId != null && myId.equals(m.ownerId());
                     int sx = (int)Math.round(width / 2.0 + (m.getX() - camX) * effScale);
                     int sy = (int)Math.round(height / 2.0 + (m.getZ() - camZ) * effScale);
                     if (sx >= -120 && sx <= width + 120 && sy >= -120 && sy <= height + 120) {
                        long time = System.currentTimeMillis();
                        float pingRadius = 8.0F + (float)(time % 1000L) / 60.0F;
                        FlightUi.circle(g, sx, sy, pingRadius, isMine ? -16711868 : -1000096, false);
                        ResourceLocation missileIcon = RadarContactRenderer.getIconForKind(m.kind().ordinal());
                        if (missileIcon == null) {
                           missileIcon = MISSILE_ICON;
                        }

                        g.pose().pushPose();
                        g.pose().translate(sx, sy, 0.0F);
                        float heading = m.getYRot();
                        g.pose().mulPose(Axis.ZP.rotationDegrees(heading - 180.0F));
                        g.blit(missileIcon, -8, -8, 16, 16, 0.0F, 0.0F, 32, 32, 32, 32);
                        g.pose().popPose();
                        double speedKmh = FlightUnits.kilometresPerHour(m.getDeltaMovement().length());
                        if (speedKmh < 1.0) {
                           speedKmh = m.kind().defaultSpeed;
                        }

                        String header = isMine
                           ? FlightUi.localized("ui.deepstrike.deep_strike_map_screen.95e8771578") + m.kind().name() + "]"
                           : FlightUi.localized("ui.deepstrike.deep_strike_map_screen.8ee8c5aaef") + m.kind().name() + "]";
                        String details = String.format(
                           Locale.ROOT, FlightUi.localized("ui.deepstrike.deep_strike_map_screen.99692c83d1"), m.getY(), speedKmh
                        );
                        int tw = Math.max(mc.font.width(FlightUi.text(header)), mc.font.width(FlightUi.text(details)));
                        int markerColor = isMine ? -16711868 : -4668269;
                        FlightUi.panel(g, sx + 14, sy - 14, tw + 12, 28, -182838503, markerColor);
                        FlightUi.label(g, header, sx + 20, sy - 10, markerColor);
                        FlightUi.label(g, details, sx + 20, sy + 2, -1182997);
                     }
                  }
               }

               g.pose().popPose();
            }
         }
      }
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
