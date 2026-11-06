package dev.sbw.deepstrike.client.terminal;

import dev.krutlexik.skyfront.client.ClientWorldMapData;
import dev.krutlexik.skyfront.client.gui.ConsoleStyle;
import dev.krutlexik.skyfront.client.gui.TerminalButton;
import dev.krutlexik.skyfront.client.gui.TerrainMapView;
import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.network.RequestWorldMapPacket;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.game.TerminalMissileData;
import dev.sbw.deepstrike.game.TerminalMissiles;
import dev.sbw.deepstrike.network.TerminalMissileNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Route editor for one launcher of a Skyfront terminal. Layout, map handling and controls follow Skyfront's ShahedRouteScreen;
 * the terrain comes from Skyfront's own world-map packets so it works without a map mod.
 */
public class MissileRouteScreen extends Screen {
   private static final int PANEL_W = 150;
   private static final int LIST_ROWS = 6;
   private static final double PAN_THRESHOLD = 3.0;
   private static final int ERROR_COLOR = -42436;
   private static final int NOTICE_COLOR = -11141291;
   private final MissileTerminalScreen parent;
   /** Task mode: edits a plain route for the task editor and hands it to taskDone instead of sending SetRoute. */
   private final Screen taskParent;
   private final Consumer<List<RoutePoint>> taskDone;
   private final boolean rings;
   private final BlockPos terminalPos;
   private final BlockPos launcher;
   private final double originX;
   private final double originZ;
   private final float viewHalf;
   private final TerrainMapView mapView = new TerrainMapView(ResourceLocation.fromNamespaceAndPath("deepstrike", "dynamic/terminal_missile_map"));
   private final List<MissileRouteScreen.Pt> points = new ArrayList<>();
   private TerminalMissileNetwork.Row row;
   private MissileKind kind;
   private int selected = -1;
   private int listScroll;
   private float zoom = 1.0F;
   private float offsetX;
   private float offsetZ;
   private boolean rightDragging;
   private boolean pressedInMap;
   private boolean pannedWhilePressed;
   private double pressX;
   private double pressY;
   private int dragIndex = -1;
   private boolean dirty;
   private int refreshTimer;
   private int mapTimer;
   private int messageTicks;
   private Component message = Component.empty();
   private int messageColor = ERROR_COLOR;
   private TerminalButton editButton;
   private TerminalButton deleteButton;
   private TerminalButton clearButton;
   private TerminalButton saveButton;
   private TerminalButton launchButton;

   MissileRouteScreen(MissileTerminalScreen parent, BlockPos terminalPos, TerminalMissileNetwork.Row row) {
      super(Component.translatable("gui.deepstrike.terminal.route.title", new Object[]{MissileTerminalScreen.familyName(row.family())}));
      this.parent = parent;
      this.taskParent = null;
      this.taskDone = null;
      this.rings = true;
      this.terminalPos = terminalPos;
      this.launcher = row.launcher();
      this.row = row;
      this.kind = kindOf(row);
      this.originX = (double)this.launcher.getX() + 0.5;
      this.originZ = (double)this.launcher.getZ() + 0.5;
      this.viewHalf = Mth.clamp((float)this.kind.maxRange * 1.15F, 500.0F, 6900.0F);
      for (RoutePoint p : row.route()) {
         this.points.add(new MissileRouteScreen.Pt(p.x(), p.y(), p.z(), p.speed(), true));
      }

      ClientWorldMapData.getInstance().clear();
   }

   /**
    * Task mode. origin is the first assigned unit (or the player), family its family index or -1; range rings are drawn only
    * when rings is true. done receives the route when the player saves or leaves the screen after changing it.
    */
   MissileRouteScreen(Screen taskParent, BlockPos terminalPos, BlockPos origin, int family, List<RoutePoint> initial, boolean rings, Consumer<List<RoutePoint>> done) {
      super(Component.translatable("gui.deepstrike.terminal.route.title", new Object[]{MissileTerminalScreen.familyName(family)}));
      this.parent = null;
      this.taskParent = taskParent;
      this.taskDone = done;
      this.rings = rings;
      this.terminalPos = terminalPos;
      this.launcher = origin;
      this.row = new TerminalMissileNetwork.Row(origin, 0, family, -1, "", 0, 0, TerminalMissiles.STATUS_NO_SIGNAL, false, new ArrayList<>(initial));
      this.kind = kindOf(this.row);
      this.originX = (double)origin.getX() + 0.5;
      this.originZ = (double)origin.getZ() + 0.5;
      this.viewHalf = Mth.clamp((float)this.kind.maxRange * 1.15F, 500.0F, 6900.0F);
      for (RoutePoint p : initial) {
         this.points.add(new MissileRouteScreen.Pt(p.x(), p.y(), p.z(), p.speed(), true));
      }

      ClientWorldMapData.getInstance().clear();
   }

   static MissileKind kindOf(TerminalMissileNetwork.Row row) {
      MissileKind[] all = MissileKind.values();
      if (row.kind() >= 0 && row.kind() < all.length) {
         return all[row.kind()];
      } else {
         return switch (row.family()) {
            case 1 -> MissileKind.JAGM;
            case 2 -> MissileKind.PEACEKEEPER;
            default -> MissileKind.FP5;
         };
      }
   }

   BlockPos terminalPos() {
      return this.terminalPos;
   }

   MissileKind kind() {
      return this.kind;
   }

   private int maxPoints() {
      return this.row.family() == 2 ? 1 : TerminalMissileData.MAX_POINTS;
   }

   void update(List<TerminalMissileNetwork.Row> rows) {
      if (this.taskDone != null) {
         return;
      }

      for (TerminalMissileNetwork.Row fresh : rows) {
         if (fresh.launcher().equals(this.launcher)) {
            this.row = fresh;
            this.kind = kindOf(fresh);
            return;
         }
      }

      this.parent.reload();
      this.minecraft.setScreen(this.parent);
   }

   private boolean canLaunch() {
      return this.row.status() == TerminalMissiles.STATUS_READY && !this.points.isEmpty();
   }

   private int mapSize() {
      return Math.min(this.width - 150 - 40, this.height - 50);
   }

   private int layoutLeft() {
      return (this.width - (this.mapSize() + 12 + 150)) / 2;
   }

   private int mapCenterX() {
      return this.layoutLeft() + this.mapSize() / 2;
   }

   private int mapCenterY() {
      return this.height / 2;
   }

   private int panelLeft() {
      return this.layoutLeft() + this.mapSize() + 12;
   }

   private int listTop() {
      return this.mapCenterY() - this.mapSize() / 2 + 54;
   }

   private float scale() {
      return (float)(this.mapSize() / 2) / (this.viewHalf / this.zoom);
   }

   protected void init() {
      int left = this.panelLeft();
      int buttonsTop = this.listTop() + 72 + 6;
      this.editButton = (TerminalButton)this.addRenderableWidget(
         new TerminalButton(left, buttonsTop, 150, 16, Component.translatable("gui.deepstrike.terminal.route.edit"), b -> {
            if (this.selected >= 0 && this.selected < this.points.size()) {
               this.minecraft.setScreen(new MissileRoutePointScreen(this, this.selected));
            }
         })
      );
      this.deleteButton = (TerminalButton)this.addRenderableWidget(
         new TerminalButton(left, buttonsTop + 18, 73, 16, Component.translatable("gui.deepstrike.terminal.route.delete"), b -> this.removeSelected())
      );
      this.clearButton = (TerminalButton)this.addRenderableWidget(
         new TerminalButton(left + 75 + 2, buttonsTop + 18, 73, 16, Component.translatable("gui.deepstrike.terminal.route.clear"), b -> {
            this.points.clear();
            this.selected = -1;
            this.dirty = true;
         })
      );
      this.saveButton = (TerminalButton)this.addRenderableWidget(
         new TerminalButton(left, buttonsTop + 36, 150, 16, Component.translatable("gui.deepstrike.terminal.route.save"), b -> {
            this.save();
            this.notice(Component.translatable("gui.deepstrike.terminal.route.saved"));
         }).style(TerminalButton.Style.LIT)
      );
      this.launchButton = (TerminalButton)this.addRenderableWidget(
         new TerminalButton(left, buttonsTop + 56, 150, 18, Component.translatable("gui.deepstrike.terminal.launch"), b -> this.launch())
            .style(TerminalButton.Style.LAUNCH)
      );
      this.launchButton.visible = this.taskDone == null;
      this.addRenderableWidget(
         new TerminalButton(left, buttonsTop + 78, 150, 16, Component.translatable("gui.deepstrike.terminal.route.back"), b -> this.onClose())
      );
      this.requestMap();
   }

   private void requestMap() {
      NetworkHandler.sendToServer(new RequestWorldMapPacket(this.launcher.getX(), this.launcher.getZ(), 2000));
   }

   private void error(Component text) {
      this.message = text;
      this.messageColor = ERROR_COLOR;
      this.messageTicks = 80;
   }

   private void notice(Component text) {
      this.message = text;
      this.messageColor = NOTICE_COLOR;
      this.messageTicks = 60;
   }

   List<RoutePoint> toRoute() {
      List<RoutePoint> route = new ArrayList<>(this.points.size());

      for (MissileRouteScreen.Pt p : this.points) {
         route.add(new RoutePoint(p.x, p.y, p.z, p.speed));
      }

      return route;
   }

   private void save() {
      if (this.taskDone != null) {
         this.taskDone.accept(this.toRoute());
         this.dirty = false;
         return;
      }

      TerminalMissileNetwork.CHANNEL.sendToServer(new TerminalMissileNetwork.SetRoute(this.terminalPos, this.launcher, this.row.ballistic(), this.toRoute()));
      this.dirty = false;
   }

   private void launch() {
      if (this.canLaunch()) {
         MissileRouteScreen.Pt target = this.points.get(this.points.size() - 1);
         double dist = Math.hypot(target.x - this.originX, target.z - this.originZ);
         if (dist < (double)this.kind.minRange) {
            this.error(Component.translatable("gui.deepstrike.terminal.route.min_range", new Object[]{this.kind.minRange}));
         } else if (dist > (double)this.kind.maxRange) {
            this.error(Component.translatable("gui.deepstrike.terminal.route.max_range", new Object[]{this.kind.maxRange}));
         } else {
            TerminalMissileNetwork.CHANNEL.sendToServer(
               new TerminalMissileNetwork.Launch(this.terminalPos, this.launcher, this.row.ballistic(), this.toRoute())
            );
            this.dirty = false;
            this.parent.reload();
            this.minecraft.setScreen(this.parent);
         }
      }
   }

   /** Surface height under a world column when the client has it loaded, otherwise 100. */
   private double groundAt(double x, double z) {
      Level level = this.minecraft.level;
      int fallback = 100;
      if (level == null) {
         return (double)fallback;
      } else {
         int min = level.getMinBuildHeight() + 2;
         int max = level.getMaxBuildHeight() - 2;
         BlockPos column = BlockPos.containing(x, 0.0, z);
         int ground = fallback;
         if (level.hasChunkAt(column)) {
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            if (height > level.getMinBuildHeight()) {
               ground = height;
            }
         }

         return (double)Mth.clamp(ground, min, max);
      }
   }

   private void addPoint(double wx, double wz) {
      double x = Math.floor(Mth.clamp(wx, -2.9999984E7, 2.9999984E7)) + 0.5;
      double z = Math.floor(Mth.clamp(wz, -2.9999984E7, 2.9999984E7)) + 0.5;
      if (this.points.size() >= this.maxPoints()) {
         if (this.maxPoints() == 1) {
            this.points.clear();
         } else {
            this.error(Component.translatable("gui.deepstrike.terminal.route.max_points", new Object[]{this.maxPoints()}));
            return;
         }
      }

      if (!this.points.isEmpty()) {
         MissileRouteScreen.Pt last = this.points.get(this.points.size() - 1);
         if (!last.manual && this.minecraft.level != null) {
            last.y = Math.min((double)(this.minecraft.level.getMaxBuildHeight() - 1), Math.max(last.y + 32.0, (double)(this.launcher.getY() + 32)));
         }
      }

      this.points.add(new MissileRouteScreen.Pt(x, this.groundAt(x, z), z, (double)this.kind.defaultSpeed, false));
      this.selected = this.points.size() - 1;
      this.dirty = true;
   }

   private void removeSelected() {
      if (this.selected >= 0 && this.selected < this.points.size()) {
         this.points.remove(this.selected);
         this.selected = -1;
         this.dirty = true;
      }
   }

   double[] pointValues(int index) {
      MissileRouteScreen.Pt p = this.points.get(index);
      return new double[]{p.x, p.y, p.z, p.speed};
   }

   int pointCount() {
      return this.points.size();
   }

   void applyPoint(int index, double x, double y, double z, double speed) {
      if (index >= 0 && index < this.points.size()) {
         MissileRouteScreen.Pt p = this.points.get(index);
         p.x = x;
         p.y = y;
         p.z = z;
         p.speed = speed;
         p.manual = true;
         this.dirty = true;
      }
   }

   private int worldToScreenX(double worldX) {
      return this.mapCenterX() + Math.round((float)((worldX - this.originX + (double)this.offsetX) * (double)this.scale()));
   }

   private int worldToScreenY(double worldZ) {
      return this.mapCenterY() + Math.round((float)((worldZ - this.originZ + (double)this.offsetZ) * (double)this.scale()));
   }

   private double screenToWorldX(double mouseX) {
      return this.originX + (mouseX - (double)this.mapCenterX()) / (double)this.scale() - (double)this.offsetX;
   }

   private double screenToWorldZ(double mouseY) {
      return this.originZ + (mouseY - (double)this.mapCenterY()) / (double)this.scale() - (double)this.offsetZ;
   }

   private boolean inMap(double mouseX, double mouseY) {
      int half = this.mapSize() / 2;
      return Math.abs(mouseX - (double)this.mapCenterX()) <= (double)half && Math.abs(mouseY - (double)this.mapCenterY()) <= (double)half;
   }

   private int pointAt(double mouseX, double mouseY) {
      for (int i = this.points.size() - 1; i >= 0; i--) {
         MissileRouteScreen.Pt p = this.points.get(i);
         if (Math.abs(mouseX - (double)this.worldToScreenX(p.x)) <= 5.0 && Math.abs(mouseY - (double)this.worldToScreenY(p.z)) <= 5.0) {
            return i;
         }
      }

      return -1;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else {
         int left = this.panelLeft();
         int listTop = this.listTop();
         if (button == 0 && mouseX >= (double)left && mouseX <= (double)(left + 150) && mouseY >= (double)listTop && mouseY < (double)(listTop + 72)) {
            int index = this.listScroll + (int)((mouseY - (double)listTop) / 12.0);
            if (index < this.points.size()) {
               this.selected = index;
            }

            return true;
         } else {
            if (this.inMap(mouseX, mouseY)) {
               if (button == 1) {
                  this.rightDragging = true;
                  return true;
               }

               if (button == 0) {
                  int hit = this.pointAt(mouseX, mouseY);
                  if (hit >= 0) {
                     this.selected = hit;
                     this.dragIndex = hit;
                     return true;
                  }

                  this.pressedInMap = true;
                  this.pannedWhilePressed = false;
                  this.pressX = mouseX;
                  this.pressY = mouseY;
                  return true;
               }
            }

            return false;
         }
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 1) {
         this.rightDragging = false;
      }

      if (button == 0 && this.dragIndex >= 0) {
         this.dragIndex = -1;
         return true;
      } else if (button == 0 && this.pressedInMap) {
         this.pressedInMap = false;
         if (!this.pannedWhilePressed && this.inMap(mouseX, mouseY)) {
            this.addPoint(this.screenToWorldX(mouseX), this.screenToWorldZ(mouseY));
         }

         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   private float maxPan() {
      return Math.max(0.0F, this.viewHalf - this.viewHalf / this.zoom);
   }

   private void clampPan() {
      float max = this.maxPan();
      this.offsetX = Mth.clamp(this.offsetX, -max, max);
      this.offsetZ = Mth.clamp(this.offsetZ, -max, max);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (button == 0 && this.dragIndex >= 0 && this.dragIndex < this.points.size()) {
         MissileRouteScreen.Pt p = this.points.get(this.dragIndex);
         double x = Math.floor(Mth.clamp(this.screenToWorldX(mouseX), -2.9999984E7, 2.9999984E7)) + 0.5;
         double z = Math.floor(Mth.clamp(this.screenToWorldZ(mouseY), -2.9999984E7, 2.9999984E7)) + 0.5;
         if (x != p.x || z != p.z) {
            p.x = x;
            p.z = z;
            if (!p.manual) {
               p.y = this.groundAt(x, z);
            }

            this.dirty = true;
         }

         return true;
      } else if (button == 0 && this.pressedInMap) {
         if (!this.pannedWhilePressed && Math.abs(mouseX - this.pressX) + Math.abs(mouseY - this.pressY) > 3.0) {
            this.pannedWhilePressed = true;
         }

         if (this.pannedWhilePressed) {
            this.panBy(dragX, dragY);
         }

         return true;
      } else if (this.rightDragging && button == 1) {
         this.panBy(dragX, dragY);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   private void panBy(double dragX, double dragY) {
      float s = this.scale();
      this.offsetX += (float)(dragX / (double)s);
      this.offsetZ += (float)(dragY / (double)s);
      this.clampPan();
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.inMap(mouseX, mouseY)) {
         this.zoom = scrollY > 0.0 ? Math.min(this.zoom * 1.2F, 20.0F) : Math.max(this.zoom / 1.2F, 1.0F);
         this.clampPan();
         return true;
      } else if (mouseX >= (double)this.panelLeft() && this.points.size() > 6) {
         this.listScroll = Mth.clamp(this.listScroll - (int)Math.signum(scrollY), 0, this.points.size() - 6);
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public void tick() {
      super.tick();
      if (++this.refreshTimer >= 40) {
         this.refreshTimer = 0;
         if (this.taskDone == null) {
            TerminalMissileNetwork.CHANNEL.sendToServer(new TerminalMissileNetwork.Request(this.terminalPos));
         }
      }

      if (++this.mapTimer >= 100) {
         this.mapTimer = 0;
         this.requestMap();
      }

      if (this.messageTicks > 0) {
         this.messageTicks--;
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBlurredBackground(partialTick);
      boolean hasSelection = this.selected >= 0 && this.selected < this.points.size();
      this.editButton.active = hasSelection;
      this.deleteButton.active = hasSelection;
      this.clearButton.active = !this.points.isEmpty();
      this.saveButton.active = this.dirty;
      this.launchButton.active = this.taskDone == null && this.canLaunch();
      int size = this.mapSize();
      int half = size / 2;
      int cx = this.mapCenterX();
      int cy = this.mapCenterY();
      ConsoleStyle.panel(graphics, cx - half - 8, cy - half - 19, cx + half + 7, cy + half + 7);
      ConsoleStyle.bezel(graphics, cx - half, cy - half, cx + half, cy + half);
      graphics.fill(cx - half, cy - half, cx + half, cy + half, -16777216);
      this.mapView.render(graphics, cx, cy, half, this.originX, this.originZ, this.scale(), this.offsetX, this.offsetZ, 0.5F);
      graphics.enableScissor(cx - half, cy - half, cx + half, cy + half);
      this.renderGrid(graphics, cx, cy, half);
      if (this.rings) {
         this.renderRing(graphics, cx, cy, half, (double)this.kind.minRange, 0xB0AA3333);
         this.renderRing(graphics, cx, cy, half, (double)this.kind.maxRange, -1711319723);
      }

      this.renderRoute(graphics);
      graphics.disableScissor();
      this.renderPanel(graphics);
      ConsoleStyle.plate(graphics, this.font, cx, cy - half - 16, this.title);
      ConsoleStyle.hintStrip(graphics, cx - half - 8, cy + half + 9, cx + half + 7, cy + half + 20);
      this.renderHint(graphics, cx - half - 4, cx + half + 3, cy + half + 11);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   private void renderHint(GuiGraphics graphics, int x0, int x1, int y) {
      Component status = this.taskDone != null ? Component.empty() : MissileTerminalScreen.statusText(this.row.status());
      int statusWidth = this.font.width(status);
      graphics.drawString(this.font, status, x1 - statusWidth, y, MissileTerminalScreen.statusColor(this.row.status()), false);
      Component text = this.messageTicks > 0 ? this.message : Component.translatable("gui.deepstrike.terminal.route.controls");
      int color = this.messageTicks > 0 ? this.messageColor : ConsoleStyle.PHOSPHOR;
      graphics.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), x1 - x0 - statusWidth - 8), x0, y, color, false);
   }

   private void renderGrid(GuiGraphics graphics, int cx, int cy, int half) {
      float s = this.scale();
      double[] steps = new double[]{50.0, 100.0, 250.0, 500.0, 1000.0, 2500.0, 5000.0, 10000.0};
      double step = steps[steps.length - 1];

      for (double candidate : steps) {
         if (candidate * (double)s >= 44.0) {
            step = candidate;
            break;
         }
      }

      int x0 = cx - half;
      int x1 = cx + half;
      int y0 = cy - half;
      int y1 = cy + half;
      double minX = this.screenToWorldX((double)x0);
      double maxX = this.screenToWorldX((double)x1);
      double minZ = this.screenToWorldZ((double)y0);
      double maxZ = this.screenToWorldZ((double)y1);

      long k = (long)Math.ceil(minX / step);
      for (int guard = 0; (double)k * step <= maxX && guard < 80; guard++, k++) {
         int sx = this.worldToScreenX((double)k * step);
         graphics.fill(sx, y0, sx + 1, y1, 0x30FFFFFF);
         graphics.drawString(this.font, Long.toString((long)((double)k * step)), sx + 2, y0 + 2, 0xA0B8C8B8, false);
      }

      k = (long)Math.ceil(minZ / step);
      for (int guard = 0; (double)k * step <= maxZ && guard < 80; guard++, k++) {
         int sy = this.worldToScreenY((double)k * step);
         graphics.fill(x0, sy, x1, sy + 1, 0x30FFFFFF);
         graphics.drawString(this.font, Long.toString((long)((double)k * step)), x0 + 2, sy + 2, 0xA0B8C8B8, false);
      }
   }

   private void renderRing(GuiGraphics graphics, int cx, int cy, int half, double radiusBlocks, int color) {
      float s = this.scale();
      double r = radiusBlocks * (double)s;
      int ox = this.worldToScreenX(this.originX);
      int oy = this.worldToScreenY(this.originZ);
      int segments = 96;
      int left = cx - half;
      int right = cx + half;
      int top = cy - half;
      int bottom = cy + half;

      for (int i = 0; i < segments; i++) {
         double a1 = (Math.PI * 2) * (double)i / (double)segments;
         double a2 = (Math.PI * 2) * (double)(i + 1) / (double)segments;
         int ax = ox + (int)(Math.cos(a1) * r);
         int ay = oy + (int)(Math.sin(a1) * r);
         int bx = ox + (int)(Math.cos(a2) * r);
         int by = oy + (int)(Math.sin(a2) * r);
         if (Math.max(ax, bx) >= left && Math.min(ax, bx) <= right && Math.max(ay, by) >= top && Math.min(ay, by) <= bottom) {
            drawLine(graphics, ax, ay, bx, by, color);
         }
      }
   }

   private void renderRoute(GuiGraphics graphics) {
      int lx = this.worldToScreenX(this.originX);
      int ly = this.worldToScreenY(this.originZ);
      int px = lx;
      int py = ly;

      for (int i = 0; i < this.points.size(); i++) {
         MissileRouteScreen.Pt p = this.points.get(i);
         int x = this.worldToScreenX(p.x);
         int y = this.worldToScreenY(p.z);
         boolean last = i == this.points.size() - 1;
         drawLine(graphics, px, py, x, y, last ? -1711319723 : -1717526368);
         px = x;
         py = y;
         int color = last ? -48060 : NOTICE_COLOR;
         graphics.fill(x - 3, y - 3, x + 4, y + 4, -16777216);
         graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
         if (i == this.selected) {
            drawRect(graphics, x - 5, y - 5, x + 6, y + 6, -1);
         }

         graphics.drawString(this.font, String.valueOf(i + 1), x + 5, y - 9, color, false);
      }

      graphics.fill(lx - 4, ly - 4, lx + 5, ly + 5, -16777216);
      graphics.fill(lx - 3, ly - 3, lx + 4, ly + 4, ConsoleStyle.AMBER);
      graphics.fill(lx - 1, ly - 1, lx + 2, ly + 2, -16777216);
      graphics.drawString(this.font, MissileTerminalScreen.familyName(this.row.family()), lx + 7, ly - 4, ConsoleStyle.AMBER, false);
   }

   private void renderPanel(GuiGraphics graphics) {
      int left = this.panelLeft();
      int top = this.mapCenterY() - this.mapSize() / 2;
      int bottom = top + this.mapSize() + 2;
      ConsoleStyle.panel(graphics, left - 4, top - 2, left + 150 + 4, bottom);
      int listTop = this.listTop();
      int lx0 = left + 3;
      int lx1 = left + 150 - 3;
      int tx = lx0 + 3;
      ConsoleStyle.lcd(graphics, lx0, top + 4, lx1, listTop + 72 + 1);
      graphics.drawString(
         this.font, Component.translatable("gui.deepstrike.terminal.route.points", new Object[]{this.points.size(), this.maxPoints()}), tx, top + 7, ConsoleStyle.AMBER, false
      );
      if (this.selected >= 0 && this.selected < this.points.size()) {
         MissileRouteScreen.Pt p = this.points.get(this.selected);
         boolean last = this.selected == this.points.size() - 1;
         graphics.drawString(
            this.font, Component.translatable("gui.deepstrike.terminal.route.point", new Object[]{this.selected + 1}), tx, top + 16, last ? ERROR_COLOR : ConsoleStyle.AMBER, false
         );
         graphics.drawString(this.font, "X " + (long)Math.floor(p.x) + "  Z " + (long)Math.floor(p.z), tx, top + 25, ConsoleStyle.AMBER, false);
         graphics.drawString(
            this.font, Component.translatable("gui.deepstrike.terminal.route.yspeed", new Object[]{(int)Math.round(p.y), (int)Math.round(p.speed)}), tx, top + 34, ConsoleStyle.AMBER, false
         );
         int d = (int)Math.round(Math.hypot(p.x - this.originX, p.z - this.originZ));
         graphics.drawString(
            this.font, Component.translatable("gui.deepstrike.terminal.route.dist", new Object[]{d}), tx, top + 43, ConsoleStyle.AMBER_DIM, false
         );
      } else {
         graphics.drawString(this.font, Component.translatable("gui.deepstrike.terminal.route.nosel"), tx, top + 16, ConsoleStyle.AMBER_DIM, false);
      }

      for (int i = 0; i < 6; i++) {
         int index = this.listScroll + i;
         if (index >= this.points.size()) {
            break;
         }

         MissileRouteScreen.Pt p = this.points.get(index);
         int y = listTop + i * 12;
         boolean last = index == this.points.size() - 1;
         if (index == this.selected) {
            graphics.fill(lx0, y - 1, lx1, y + 10, 1090498620);
         }

         graphics.drawString(
            this.font, (index + 1) + ". " + (long)Math.floor(p.x) + ", " + (long)Math.floor(p.z), tx, y, last ? ERROR_COLOR : ConsoleStyle.AMBER, false
         );
      }

      if (bottom - 12 > this.listTop() + 72 + 6 + 96) {
         ConsoleStyle.lamps(graphics, left, bottom - 10, ConsoleStyle.LAMP_GREEN, ConsoleStyle.LAMP_AMBER, ConsoleStyle.LAMP_RED_OFF);
      }
   }

   private static void drawRect(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
      graphics.fill(x0, y0, x1, y0 + 1, color);
      graphics.fill(x0, y1 - 1, x1, y1, color);
      graphics.fill(x0, y0, x0 + 1, y1, color);
      graphics.fill(x1 - 1, y0, x1, y1, color);
   }

   private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
      int dx = Math.abs(x2 - x1);
      int dy = Math.abs(y2 - y1);
      int sx = x1 < x2 ? 1 : -1;
      int sy = y1 < y2 ? 1 : -1;
      int err = dx - dy;
      int guard = 0;

      while (guard++ < 8192) {
         graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
         if (x1 == x2 && y1 == y2) {
            break;
         }

         int e2 = 2 * err;
         if (e2 > -dy) {
            err -= dy;
            x1 += sx;
         }

         if (e2 < dx) {
            err += dx;
            y1 += sy;
         }
      }
   }

   public void onClose() {
      if (this.dirty) {
         this.save();
      }

      if (this.taskParent != null) {
         this.minecraft.setScreen(this.taskParent);
         return;
      }

      this.parent.reload();
      this.minecraft.setScreen(this.parent);
   }

   public void removed() {
      super.removed();
      this.mapView.close();
   }

   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static final class Pt {
      double x;
      double y;
      double z;
      double speed;
      boolean manual;

      Pt(double x, double y, double z, double speed, boolean manual) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.speed = speed;
         this.manual = manual;
      }
   }
}
