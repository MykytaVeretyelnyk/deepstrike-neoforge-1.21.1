package dev.sbw.deepstrike.client.terminal;

import dev.krutlexik.skyfront.client.gui.ConsoleStyle;
import dev.krutlexik.skyfront.client.gui.TerminalButton;
import dev.krutlexik.skyfront.network.NetworkHandler;
import dev.krutlexik.skyfront.network.TerminalActionPacket;
import dev.sbw.deepstrike.game.TerminalMissileData;
import dev.sbw.deepstrike.game.TerminalMissiles;
import dev.sbw.deepstrike.network.TerminalMissileNetwork;
import dev.sbw.deepstrike.client.CommandTabletScreen;
import dev.sbw.deepstrike.game.MissileKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** List of the launchers linked to a Skyfront terminal; styled after Skyfront's ShahedTerminalScreen. */
public class MissileTerminalScreen extends Screen {
   private static final int ROW_HEIGHT = 24;
   private static final int VISIBLE_ROWS = 5;
   private static final int SUBTAB_H = 14;
   private static final int PANEL_W = 340;
   private static final int SCROLLBAR_W = 5;
   private static TerminalMissileNetwork.Snapshot latest;
   private final Screen parent;
   private final BlockPos terminalPos;
   private List<TerminalMissileNetwork.Row> rows;
   private boolean received;
   private int scrollOffset;
   private int refreshTimer;
   private boolean draggingScroll;
   private MissileTerminalScreen.Sub sub = MissileTerminalScreen.Sub.LAUNCHERS;
   private PanelData.Panel panel;
   private CompoundTag seenPanel;
   private int panelTimer;
   private UUID selectedTask;
   private String selectedMember = "";
   private String nickname;
   private boolean forceLogin;
   private CompoundTag loginBaseline;
   private EditBox nicknameBox;
   private final List<TerminalButton> bindButtons = new ArrayList<>();
   private final List<TerminalMissileNetwork.Row> bindRows = new ArrayList<>();
   private final PanelDefenseView defenseView = new PanelDefenseView();
   private TerminalButton taskArm;
   private TerminalButton taskLaunch;
   private TerminalButton taskDisarm;
   private TerminalButton taskEdit;
   private TerminalButton taskCopy;
   private TerminalButton taskDelete;

   private enum Sub {
      LAUNCHERS("launchers"),
      TASKS("tasks"),
      FLIGHTS("flights"),
      JOURNAL("journal"),
      ACCESS("access"),
      DEFENSE("defense");

      final String key;

      Sub(String key) {
         this.key = key;
      }
   }

   private MissileTerminalScreen(Screen parent, BlockPos terminalPos) {
      // The same title plate as Skyfront's own screen: the two tabs are one console.
      super(Component.translatable("gui.skyfront.terminal.title"));
      this.parent = parent;
      this.terminalPos = terminalPos;
      TerminalMissileNetwork.Snapshot cached = latest;
      this.received = cached != null && cached.terminal().equals(terminalPos);
      this.rows = this.received ? new ArrayList<>(cached.rows()) : new ArrayList<>();
      this.seenPanel = latestPanel(terminalPos);
      this.panel = this.seenPanel == null ? null : PanelData.parse(this.seenPanel);
      this.defenseView.accept(this.seenPanel);
      Minecraft mc = Minecraft.getInstance();
      this.nickname = mc.player == null ? "" : mc.player.getGameProfile().getName();
   }

   /** Sends one panel command with a fresh command id. */
   static void sendPanel(BlockPos terminal, String action, CompoundTag payload) {
      TerminalMissileNetwork.CHANNEL.sendToServer(new TerminalMissileNetwork.PanelCommand(terminal, action, UUID.randomUUID(), payload));
   }

   private void send(String action, CompoundTag payload) {
      sendPanel(this.terminalPos, action, payload);
   }

   private CompoundTag taskTag(PanelData.Task task) {
      CompoundTag tag = new CompoundTag();
      tag.putUUID("Task", task.id());
      return tag;
   }

   /** The task editor handed control back: re-sync with the newest snapshot and ask the server for a fresh one. */
   void afterEditor() {
      this.panelTimer = 0;
      this.send("refresh", new CompoundTag());
   }

   public static void open(Screen parent, BlockPos terminalPos) {
      sendPanel(terminalPos, "refresh", new CompoundTag());
      TerminalMissileNetwork.CHANNEL.sendToServer(new TerminalMissileNetwork.Request(terminalPos));
      Minecraft.getInstance().setScreen(new MissileTerminalScreen(parent, terminalPos));
   }

   private static BlockPos latestPanelTerminal;
   private static net.minecraft.nbt.CompoundTag latestPanel;

   /** Called on the client main thread for every panel snapshot received from the server. */
   public static void acceptPanel(BlockPos terminal, net.minecraft.nbt.CompoundTag snapshot) {
      latestPanelTerminal = terminal;
      latestPanel = snapshot;
   }

   /** The latest panel snapshot for that terminal, or null. */
   public static net.minecraft.nbt.CompoundTag latestPanel(BlockPos terminal) {
      return latestPanel != null && terminal != null && terminal.equals(latestPanelTerminal) ? latestPanel : null;
   }

   /** Called on the client main thread for every snapshot received from the server. */
   public static void accept(TerminalMissileNetwork.Snapshot snapshot) {
      latest = snapshot;
      Screen current = Minecraft.getInstance().screen;
      if (current instanceof MissileTerminalScreen list && list.terminalPos.equals(snapshot.terminal())) {
         list.update(snapshot.rows());
      } else if (current instanceof MissileRouteScreen route && route.terminalPos().equals(snapshot.terminal())) {
         route.update(snapshot.rows());
      }
   }

   static TerminalMissileNetwork.Snapshot latest() {
      return latest;
   }

   BlockPos terminalPos() {
      return this.terminalPos;
   }

   /** Takes the newest snapshot received while a child screen was open. The caller re-opens this screen afterwards. */
   void reload() {
      TerminalMissileNetwork.Snapshot cached = latest;
      if (cached != null && cached.terminal().equals(this.terminalPos)) {
         this.rows = new ArrayList<>(cached.rows());
         this.received = true;
         this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll());
      }
   }

   private void update(List<TerminalMissileNetwork.Row> fresh) {
      this.rows = new ArrayList<>(fresh);
      this.received = true;
      this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll());
      this.scrollChanged();
   }

   /** Only the launcher list is made of widgets; the other views are drawn directly. */
   private void scrollChanged() {
      if (this.sub == MissileTerminalScreen.Sub.LAUNCHERS) {
         this.rebuildWidgets();
      }
   }

   private int panelLeft() {
      return this.width / 2 - 170;
   }

   /** Top of the first list row; the sub-tab strip sits in the 14 px above it, inside the CRT. */
   private int listTop() {
      return this.height / 2 - 62 + 14;
   }

   private int visibleRows() {
      return switch (this.sub) {
         case TASKS -> 4;
         case ACCESS -> 3;
         default -> 5;
      };
   }

   private boolean needsLogin() {
      return this.sub != MissileTerminalScreen.Sub.LAUNCHERS && this.panel != null && (!this.panel.network() || this.forceLogin);
   }

   private int rowCount() {
      if (this.sub == MissileTerminalScreen.Sub.LAUNCHERS) {
         return this.rows.size();
      } else if (this.panel == null || this.needsLogin()) {
         return 0;
      } else {
         return switch (this.sub) {
            case TASKS -> this.panel.tasks().size();
            case FLIGHTS -> this.panel.flights().size();
            case JOURNAL -> this.panel.journal().size();
            case DEFENSE -> 0;
            default -> this.panel.members().size();
         };
      }
   }

   private int maxScroll() {
      return Math.max(0, this.rowCount() - this.visibleRows());
   }

   private boolean needsScrollbar() {
      return this.maxScroll() > 0;
   }

   private int trackLeft() {
      return this.panelLeft() + 340 - 5;
   }

   private int trackHeight() {
      return this.visibleRows() * 24;
   }

   private int thumbHeight() {
      return Math.max(20, this.trackHeight() * this.visibleRows() / Math.max(1, this.rowCount()));
   }

   private int thumbY() {
      int travel = this.trackHeight() - this.thumbHeight();
      return this.listTop() + (this.maxScroll() == 0 ? 0 : travel * this.scrollOffset / this.maxScroll());
   }

   private void scrollToMouse(double mouseY) {
      int travel = this.trackHeight() - this.thumbHeight();
      if (travel > 0) {
         double rel = mouseY - (double)this.listTop() - (double)this.thumbHeight() / 2.0;
         int target = (int)Math.round(rel / (double)travel * (double)this.maxScroll());
         int clamped = Mth.clamp(target, 0, this.maxScroll());
         if (clamped != this.scrollOffset) {
            this.scrollOffset = clamped;
            this.scrollChanged();
         }
      }
   }

   /** Back to Skyfront's screen (the Shahed tab); its data is refreshed at once instead of waiting for its 2 s timer. */
   private void showShahedTab() {
      this.minecraft.setScreen(this.parent);
      NetworkHandler.sendToServer(TerminalActionPacket.refresh(this.terminalPos));
   }

   protected void init() {
      TerminalTabs.add(this, true, this::addRenderableWidget, this::showShahedTab, () -> {
      });
      this.bindButtons.clear();
      this.bindRows.clear();
      this.nicknameBox = null;
      this.taskArm = null;
      this.taskLaunch = null;
      this.taskDisarm = null;
      this.taskEdit = null;
      this.taskCopy = null;
      this.taskDelete = null;
      int left = this.panelLeft();
      MissileTerminalScreen.Sub[] subs = MissileTerminalScreen.Sub.values();

      for (int i = 0; i < subs.length; i++) {
         MissileTerminalScreen.Sub target = subs[i];
         TerminalButton tab = new TerminalButton(
            left + 2 + i * 55, this.listTop() - 18, 54, SUBTAB_H, Component.translatable("gui.deepstrike.panel.sub." + target.key), b -> this.setSub(target)
         );
         this.addRenderableWidget(target == this.sub ? tab.style(TerminalButton.Style.LIT) : tab);
      }

      if (this.sub == MissileTerminalScreen.Sub.LAUNCHERS) {
         this.buildLaunchers();
      } else if (this.panel != null) {
         if (this.needsLogin()) {
            this.buildLogin();
         } else {
            this.buildNetworkFooter();
            switch (this.sub) {
               case TASKS -> this.buildTasks();
               case FLIGHTS -> {
               }
               case JOURNAL -> {
               }
               case DEFENSE -> this.defenseView.build(left, this.listTop(), this::addRenderableWidget, this::send);
               default -> this.buildAccess();
            }
         }
      }
   }

   private void setSub(MissileTerminalScreen.Sub target) {
      if (target != this.sub) {
         this.sub = target;
         this.scrollOffset = 0;
         this.panelTimer = 0;
         if (target != MissileTerminalScreen.Sub.LAUNCHERS) {
            this.send("refresh", new CompoundTag());
         }

         this.rebuildWidgets();
      }
   }

   private int role() {
      return this.panel == null ? 0 : this.panel.role();
   }

   /** Inline login prompt in the CRT: nickname box and a connect button. */
   private void buildLogin() {
      int left = this.panelLeft();
      int y = this.listTop() + 40;
      this.nicknameBox = new EditBox(this.font, left + 70, y, 200, 14, Component.translatable("gui.deepstrike.panel.login.nickname"));
      this.nicknameBox.setMaxLength(16);
      this.nicknameBox.setHint(Component.translatable("gui.deepstrike.panel.login.nickname"));
      this.nicknameBox.setValue(this.nickname);
      this.nicknameBox.setResponder(s -> this.nickname = s);
      this.addRenderableWidget(this.nicknameBox);
      this.addRenderableWidget(
         new TerminalButton(left + 120, y + 20, 100, 16, Component.translatable("gui.deepstrike.panel.login.login"), b -> {
            CompoundTag p = new CompoundTag();
            p.putString("Nickname", this.nickname);
            this.loginBaseline = this.seenPanel;
            this.send("login", p);
         }).style(TerminalButton.Style.LIT)
      );
   }

   /** The "switch network" button in the CRT footer, shown on every logged-in view. */
   private void buildNetworkFooter() {
      int left = this.panelLeft();
      this.addRenderableWidget(
         new TerminalButton(left + 245, this.listTop() + 120, 86, 11, Component.translatable("gui.deepstrike.panel.login.switch"), b -> {
            this.forceLogin = true;
            this.loginBaseline = null;
            this.rebuildWidgets();
         })
      );
   }

   private void buildTasks() {
      int left = this.panelLeft();
      int y = this.listTop() + 98;
      String[] keys = new String[]{"arm", "launch", "disarm", "edit", "copy", "delete", "new"};
      int w = 45;
      TerminalButton[] made = new TerminalButton[keys.length];

      for (int i = 0; i < keys.length; i++) {
         String key = keys[i];
         TerminalButton button = new TerminalButton(
            left + 2 + i * (w + 2), y, w, 20, Component.translatable("gui.deepstrike.panel.short." + key), b -> this.taskAction(key)
         );
         button.setTooltip(Tooltip.create(Component.translatable("gui.deepstrike.panel.tasks." + key)));
         if (key.equals("launch")) {
            button.style(TerminalButton.Style.LAUNCH);
         } else if (key.equals("new")) {
            button.style(TerminalButton.Style.LIT);
            button.active = this.role() >= 1;
         }

         made[i] = button;
         this.addRenderableWidget(button);
      }

      this.taskArm = made[0];
      this.taskLaunch = made[1];
      this.taskDisarm = made[2];
      this.taskEdit = made[3];
      this.taskCopy = made[4];
      this.taskDelete = made[5];
   }

   private void taskAction(String key) {
      PanelData.Task task = this.panel == null ? null : this.panel.task(this.selectedTask);
      switch (key) {
         case "new" -> this.minecraft.setScreen(new PanelTaskEditorScreen(this, this.terminalPos, this.panel, null, false));
         case "edit" -> {
            if (task != null && !task.active()) {
               this.minecraft.setScreen(new PanelTaskEditorScreen(this, this.terminalPos, this.panel, task, false));
            }
         }
         case "copy" -> {
            if (task != null) {
               this.minecraft.setScreen(new PanelTaskEditorScreen(this, this.terminalPos, this.panel, task, true));
            }
         }
         default -> {
            if (task != null) {
               this.send(key, this.taskTag(task));
            }
         }
      }
   }

   private void buildAccess() {
      int left = this.panelLeft();
      int top = this.listTop() + 76;
      this.nicknameBox = new EditBox(this.font, left + 2, top, 329, 14, Component.translatable("gui.deepstrike.panel.login.nickname"));
      this.nicknameBox.setMaxLength(16);
      this.nicknameBox.setHint(Component.translatable("gui.deepstrike.panel.access.add"));
      this.nicknameBox.setValue(this.selectedMember);
      this.nicknameBox.setResponder(s -> this.selectedMember = s);
      this.addRenderableWidget(this.nicknameBox);
      String[] keys = new String[]{"observer", "operator", "admin", "remove"};
      int w = 80;

      for (int i = 0; i < keys.length; i++) {
         int role = i == 3 ? -1 : i;
         TerminalButton button = new TerminalButton(
            left + 2 + i * (w + 3), top + 18, w, 16, Component.translatable("gui.deepstrike.panel.access." + keys[i]), b -> {
               CompoundTag p = new CompoundTag();
               p.putString("Nickname", this.selectedMember);
               p.putInt("Role", role);
               this.send("member", p);
            }
         );
         button.active = this.role() >= 2;
         this.addRenderableWidget(button);
      }
   }

   private void buildLaunchers() {
      int listTop = this.listTop();
      int right = this.panelLeft() + 340 - 5 - 4;
      int shown = Math.min(5, this.rows.size() - this.scrollOffset);

      for (int i = 0; i < shown; i++) {
         TerminalMissileNetwork.Row row = this.rows.get(this.scrollOffset + i);
         int y = listTop + i * 24;
         TerminalButton route = new TerminalButton(
            right - 20 - 2 - 52 - 2 - 52,
            y,
            52,
            20,
            Component.translatable("gui.deepstrike.terminal.route"),
            b -> this.minecraft.setScreen(new MissileRouteScreen(this, this.terminalPos, row))
         );
         route.active = row.family() >= 0;
         this.addRenderableWidget(route);
         TerminalButton launch = new TerminalButton(
            right - 20 - 2 - 52,
            y,
            52,
            20,
            Component.translatable("gui.deepstrike.terminal.launch"),
            b -> {
               TerminalMissileNetwork.CHANNEL.sendToServer(new TerminalMissileNetwork.Launch(this.terminalPos, row.launcher(), row.ballistic(), row.route()));
            }
         ).style(TerminalButton.Style.LAUNCH);
         launch.active = row.status() == TerminalMissiles.STATUS_READY && !row.route().isEmpty();
         this.addRenderableWidget(launch);
         this.addRenderableWidget(
            new TerminalButton(
               right - 20,
               y,
               20,
               20,
               Component.literal("✕"),
               b -> TerminalMissileNetwork.CHANNEL.sendToServer(new TerminalMissileNetwork.Unbind(this.terminalPos, row.launcher()))
            )
         );
         TerminalButton bind = new TerminalButton(
            right - 20 - 2 - 52 - 2 - 52 - 2 - 30, y, 30, 20, Component.translatable("gui.deepstrike.panel.bind_short"), b -> {
               CompoundTag p = new CompoundTag();
               p.putLong("Launcher", row.launcher().asLong());
               this.send("bind_unit", p);
            }
         );
         bind.setTooltip(Tooltip.create(Component.translatable("gui.deepstrike.panel.bind_unit")));
         this.bindButtons.add(bind);
         this.bindRows.add(row);
         this.addRenderableWidget(bind);
      }
   }

   /** A click on a drawn (non-widget) row of the Tasks / Flights / Access views. */
   private boolean clickRow(double mouseX, double mouseY) {
      if (this.sub == MissileTerminalScreen.Sub.LAUNCHERS || this.sub == MissileTerminalScreen.Sub.JOURNAL || this.panel == null || this.needsLogin()) {
         return false;
      }

      int left = this.panelLeft();
      if (this.sub == MissileTerminalScreen.Sub.DEFENSE) {
         return this.defenseView.click(mouseX, mouseY, left, this.listTop());
      }

      if (mouseX < (double)(left + 2) || mouseX > (double)(left + 340 - 9) || mouseY < (double)this.listTop()) {
         return false;
      }

      int index = this.scrollOffset + (int)((mouseY - (double)this.listTop()) / 24.0);
      if (mouseY >= (double)(this.listTop() + this.visibleRows() * 24) || index >= this.rowCount()) {
         return false;
      }

      switch (this.sub) {
         case TASKS -> this.selectedTask = this.panel.tasks().get(index).id();
         case FLIGHTS -> {
            CompoundTag p = new CompoundTag();
            p.putUUID("Mission", this.panel.flights().get(index).mission());
            this.send("camera", p);
         }
         case ACCESS -> {
            this.selectedMember = this.panel.members().get(index).nickname();
            if (this.nicknameBox != null) {
               this.nicknameBox.setValue(this.selectedMember);
            }
         }
         default -> {
            return false;
         }
      }

      return true;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (super.mouseClicked(mouseX, mouseY, button)) {
         return true;
      } else if (button == 0 && this.clickRow(mouseX, mouseY)) {
         return true;
      } else if (button == 0
         && this.needsScrollbar()
         && mouseX >= (double)this.trackLeft()
         && mouseX <= (double)(this.trackLeft() + 5)
         && mouseY >= (double)this.listTop()
         && mouseY <= (double)(this.listTop() + this.trackHeight())) {
         this.draggingScroll = true;
         this.scrollToMouse(mouseY);
         return true;
      } else {
         return false;
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.draggingScroll && button == 0) {
         this.scrollToMouse(mouseY);
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (button == 0) {
         this.draggingScroll = false;
      }

      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.sub == MissileTerminalScreen.Sub.DEFENSE && this.panel != null && !this.needsLogin() && this.defenseView.scroll(mouseX, mouseY, this.panelLeft(), this.listTop(), scrollY)) {
         return true;
      }

      if (this.needsScrollbar()) {
         int next = Mth.clamp(this.scrollOffset - (int)Math.signum(scrollY), 0, this.maxScroll());
         if (next != this.scrollOffset) {
            this.scrollOffset = next;
            this.scrollChanged();
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      }
   }

   public void tick() {
      super.tick();
      this.syncPanel();
      if (this.sub == MissileTerminalScreen.Sub.LAUNCHERS) {
         if (++this.refreshTimer >= 40) {
            this.refreshTimer = 0;
            TerminalMissileNetwork.CHANNEL.sendToServer(new TerminalMissileNetwork.Request(this.terminalPos));
            this.send("refresh", new CompoundTag());
         }
      } else if (++this.panelTimer >= 20) {
         this.panelTimer = 0;
         this.send("refresh", new CompoundTag());
      }
   }

   /** Picks up a newly received panel snapshot; widgets are rebuilt only when the login state or the role changed. */
   private void syncPanel() {
      CompoundTag tag = latestPanel(this.terminalPos);
      if (tag != this.seenPanel) {
         this.seenPanel = tag;
         this.defenseView.accept(tag);
         boolean loginBefore = this.needsLogin();
         int roleBefore = this.role();
         boolean hadPanel = this.panel != null;
         this.panel = tag == null ? null : PanelData.parse(tag);
         if (this.forceLogin && this.panel != null && this.panel.network() && this.loginBaseline != null && tag != this.loginBaseline && this.panel.error().isEmpty()) {
            this.forceLogin = false;
         }

         this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll());
         if (loginBefore != this.needsLogin() || roleBefore != this.role() || hadPanel != (this.panel != null)) {
            this.rebuildWidgets();
         }
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
      this.renderBlurredBackground(partialTick);
      int left = this.panelLeft();
      int listTop = this.listTop();
      int top = listTop - 44;
      int screenTop = listTop - 20;
      int screenBottom = listTop + 132;
      int bottom = screenBottom + 21;
      ConsoleStyle.panel(graphics, left - 10, top, left + 340 + 10, bottom);
      ConsoleStyle.plate(graphics, this.font, this.width / 2, top + 6, this.title);
      ConsoleStyle.crt(graphics, left - 2, screenTop, left + 340 + 2, screenBottom);
      if (this.sub != MissileTerminalScreen.Sub.LAUNCHERS) {
         this.renderPanelView(graphics, left, listTop);
      } else {
         this.renderLaunchers(graphics, left, listTop, screenTop, screenBottom);
      }

      ConsoleStyle.lamps(graphics, left - 2, bottom - 13, ConsoleStyle.LAMP_GREEN, ConsoleStyle.LAMP_GREEN, ConsoleStyle.LAMP_AMBER);
      Component count = Component.translatable("gui.deepstrike.terminal.count", new Object[]{this.rows.size(), TerminalMissileData.MAX_LAUNCHERS});
      ConsoleStyle.plate(graphics, this.font, left + 340 - this.font.width(count) / 2 - 4, bottom - 16, count);
      graphics.drawString(this.font, Component.translatable("gui.deepstrike.terminal.hint"), left + 22, bottom - 15, -4275040);
      this.renderScrollbar(graphics, mouseX, mouseY);
      this.renderFooter(graphics, left, listTop);
      super.render(graphics, mouseX, mouseY, partialTick);
   }

   /** The footer line of the CRT: the server's last error (red), else who is logged in. */
   private void renderFooter(GuiGraphics graphics, int left, int listTop) {
      if (this.panel == null) {
         return;
      }

      int y = listTop + 122;
      int room = this.sub == MissileTerminalScreen.Sub.LAUNCHERS || !this.panel.network() || this.forceLogin ? 316 : 238;
      if (!this.panel.error().isEmpty()) {
         String text = CommandTabletScreen.tr("error." + this.panel.error()).getString();
         graphics.drawString(this.font, this.font.plainSubstrByWidth(text, room), left + 4, y, ConsoleStyle.AMBER_RED, false);
      } else if (this.panel.network() && this.sub != MissileTerminalScreen.Sub.LAUNCHERS) {
         String text = this.panel.nickname() + " / " + CommandTabletScreen.tr("role." + this.panel.role()).getString();
         graphics.drawString(this.font, this.font.plainSubstrByWidth(text, room), left + 4, y, ConsoleStyle.PHOSPHOR, false);
      }
   }

   private void renderLaunchers(GuiGraphics graphics, int left, int listTop, int screenTop, int screenBottom) {
      for (int k = 0; k < this.bindButtons.size(); k++) {
         TerminalMissileNetwork.Row r = this.bindRows.get(k);
         boolean bound = this.panel != null && this.panel.hasUnitAt(r.launcher().asLong());
         this.bindButtons.get(k).active = this.panel != null && this.panel.network() && this.panel.role() >= 2 && !bound;
      }

      if (this.rows.isEmpty()) {
         Component message = Component.translatable(this.received ? "gui.deepstrike.terminal.empty" : "gui.deepstrike.terminal.loading");
         List<FormattedCharSequence> lines = this.font.split(message, 316);
         int y = (screenTop + screenBottom) / 2 - lines.size() * 11 / 2;

         for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, ConsoleStyle.PHOSPHOR);
            y += 11;
         }
      }

      int shown = Math.min(5, this.rows.size() - this.scrollOffset);
      int textRight = left + 340 - 5 - 4 - 20 - 2 - 52 - 2 - 52 - 2 - 30 - 6;

      for (int i = 0; i < shown; i++) {
         TerminalMissileNetwork.Row row = this.rows.get(this.scrollOffset + i);
         int y = listTop + i * 24;
         Component first = Component.translatable("gui.deepstrike.terminal.row", new Object[]{row.number(), familyName(row.family()), coords(row.launcher())});
         boolean bound = this.panel != null && this.panel.hasUnitAt(row.launcher().asLong());
         int tagWidth = 0;
         if (bound) {
            Component tag = Component.translatable("gui.deepstrike.panel.bound_tag");
            tagWidth = this.font.width(tag) + 4;
            graphics.drawString(this.font, tag, textRight - tagWidth + 4, y + 3, -11141291);
         }

         graphics.drawString(this.font, this.font.plainSubstrByWidth(first.getString(), textRight - (left + 4) - tagWidth), left + 4, y + 3, -2039584);
         String second = missileText(row).getString();
         Component status = statusText(row.status());
         int statusWidth = this.font.width(status);
         graphics.drawString(this.font, this.font.plainSubstrByWidth(second, textRight - (left + 4) - statusWidth - 8), left + 4, y + 13, ConsoleStyle.AMBER_DIM);
         graphics.drawString(this.font, status, textRight - statusWidth, y + 13, statusColor(row.status()));
         if (i < shown - 1) {
            graphics.fill(left + 2, y + 23, left + 340 - 5 - 4, y + 24, ConsoleStyle.PHOSPHOR_DIM);
         }
      }

   }

   private static int taskColor(String state) {
      return switch (state) {
         case "ERROR" -> -1076610;
         case "DUTY", "DONE" -> -6696818;
         case "PREPARING", "RECOVERING", "COUNTDOWN" -> -1000096;
         case "OFFLINE" -> -5196890;
         default -> -4668269;
      };
   }

   private void line(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
      graphics.drawString(this.font, this.font.plainSubstrByWidth(text, Math.max(0, maxWidth)), x, y, color, false);
   }

   private void renderPanelView(GuiGraphics graphics, int left, int listTop) {
      if (this.panel == null) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.deepstrike.terminal.loading"), this.width / 2, listTop + 50, ConsoleStyle.PHOSPHOR);
         return;
      }

      if (this.needsLogin()) {
         graphics.drawCenteredString(this.font, Component.translatable("gui.deepstrike.panel.login.title"), this.width / 2, listTop + 4, ConsoleStyle.AMBER);
         int y = listTop + 16;

         for (FormattedCharSequence hint : this.font.split(CommandTabletScreen.tr("login_hint"), 316)) {
            graphics.drawCenteredString(this.font, hint, this.width / 2, y, ConsoleStyle.PHOSPHOR);
            y += 10;
         }

         return;
      }

      if (this.sub == MissileTerminalScreen.Sub.DEFENSE) {
         this.defenseView.render(graphics, this.font, left, listTop);
         return;
      }

      int right = left + 331;
      int count = this.rowCount();
      if (count == 0 && this.sub != MissileTerminalScreen.Sub.ACCESS) {
         String key = switch (this.sub) {
            case TASKS -> "gui.deepstrike.panel.tasks.empty";
            case FLIGHTS -> "gui.deepstrike.panel.flights.empty";
            default -> "gui.deepstrike.panel.journal.empty";
         };
         graphics.drawCenteredString(this.font, Component.translatable(key), this.width / 2, listTop + 30, ConsoleStyle.PHOSPHOR);
      }

      int shown = Math.min(this.visibleRows(), count - this.scrollOffset);

      for (int i = 0; i < shown; i++) {
         int index = this.scrollOffset + i;
         int y = listTop + i * 24;
         switch (this.sub) {
            case TASKS -> this.renderTaskRow(graphics, this.panel.tasks().get(index), left, right, y);
            case FLIGHTS -> {
               PanelData.Flight f = this.panel.flights().get(index);
               this.line(graphics, f.name() + " / " + modelName(f.model()), left + 4, y + 3, right - left - 8, -2039584);
               String eta = f.eta() >= 0 ? " / ETA ~" + f.eta() + "s" : "";
               this.line(
                  graphics,
                  String.format(Locale.ROOT, "X %.0f  Y %.0f  Z %.0f / %.0f km/h%s%s", f.x(), f.y(), f.z(), f.speed(), eta, f.camera() ? " / CAM" : ""),
                  left + 4,
                  y + 13,
                  right - left - 8,
                  ConsoleStyle.AMBER_DIM
               );
            }
            case JOURNAL -> {
               PanelData.JournalEntry e = this.panel.journal().get(this.panel.journal().size() - 1 - index);
               String text = "T+" + e.tick() + "  " + this.journalText(e);
               int ly = y + 3;

               for (FormattedCharSequence part : this.font.split(Component.literal(text), right - left - 8)) {
                  if (ly > y + 13) {
                     break;
                  }

                  graphics.drawString(this.font, part, left + 4, ly, ly == y + 3 ? -2039584 : ConsoleStyle.AMBER_DIM, false);
                  ly += 10;
               }
            }
            default -> {
               PanelData.Member m = this.panel.members().get(index);
               if (m.nickname().equalsIgnoreCase(this.selectedMember)) {
                  graphics.fill(left + 2, y, right, y + 23, 0x4044AA44);
               }

               this.line(graphics, m.nickname() + " / " + CommandTabletScreen.tr("role." + m.role()).getString(), left + 4, y + 8, right - left - 8, -2039584);
            }
         }

         if (i < shown - 1) {
            graphics.fill(left + 2, y + 23, right, y + 24, ConsoleStyle.PHOSPHOR_DIM);
         }
      }

      if (this.sub == MissileTerminalScreen.Sub.TASKS) {
         boolean has = this.panel.task(this.selectedTask) != null;
         PanelData.Task task = this.panel.task(this.selectedTask);
         int role = this.panel.role();
         boolean operator = role >= 1;
         if (this.taskArm != null) {
            this.taskArm.active = has && operator;
            this.taskLaunch.active = has && operator;
            this.taskDisarm.active = has && operator;
            this.taskEdit.active = has && operator && !task.active();
            this.taskCopy.active = has && operator;
            this.taskDelete.active = has && role >= 2;
         }
      }
   }

   private void renderTaskRow(GuiGraphics graphics, PanelData.Task t, int left, int right, int y) {
      if (t.id().equals(this.selectedTask)) {
         graphics.fill(left + 2, y, right, y + 23, 0x4044AA44);
      }

      String state = CommandTabletScreen.tr("state." + t.state().toLowerCase(Locale.ROOT)).getString();
      long secs = t.countdownSeconds(this.panel.now());
      if (secs >= 0L) {
         state = state + " " + secs + "s";
      }

      int stateWidth = this.font.width(state);
      graphics.drawString(this.font, state, right - 4 - stateWidth, y + 3, taskColor(t.state()), false);
      this.line(graphics, t.name(), left + 4, y + 3, right - left - 16 - stateWidth, -2039584);
      String second = t.totalLaunched() + "/" + t.totalCount();
      int ready = 0;

      for (PanelData.Assignment a : t.assignments()) {
         if (a.phase().equals("READY")) {
            ready++;
         }
      }

      second = CommandTabletScreen.tr("ready_count", ready, t.assignments().size()).getString() + "  / " + second;
      boolean failed = !t.error().isEmpty();
      if (failed) {
         second = second + " / " + CommandTabletScreen.tr("error." + t.error()).getString();
      }

      this.line(graphics, second, left + 4, y + 13, right - left - 8, failed ? ConsoleStyle.AMBER_RED : ConsoleStyle.AMBER_DIM);
   }

   private String journalText(PanelData.JournalEntry e) {
      if (!PanelData.structured(e)) {
         return e.text();
      } else {
         Object object = e.key().equals("flight")
            ? Component.translatable("journal.deepstrike.outcome." + e.object().toLowerCase(Locale.ROOT))
            : e.object();
         return CommandTabletScreen.tr("journal." + e.key(), e.actor(), object).getString();
      }
   }

   private static String modelName(String id) {
      for (MissileKind kind : MissileKind.values()) {
         if (kind.id.equals(id)) {
            return kind.title().getString();
         }
      }

      return "—";
   }

   private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
      if (this.needsScrollbar()) {
         int x0 = this.trackLeft();
         int x1 = x0 + 5;
         int top = this.listTop();
         int bottom = top + this.trackHeight();
         graphics.fill(x0, top, x1, bottom, -16445689);
         graphics.fill(x0, top, x1, top + 1, ConsoleStyle.PHOSPHOR_DIM);
         graphics.fill(x0, bottom - 1, x1, bottom, ConsoleStyle.PHOSPHOR_DIM);
         int thumbTop = this.thumbY();
         int thumbBottom = thumbTop + this.thumbHeight();
         boolean hovered = this.draggingScroll || mouseX >= x0 && mouseX <= x1 && mouseY >= thumbTop && mouseY <= thumbBottom;
         graphics.fill(x0, thumbTop, x1, thumbBottom, hovered ? ConsoleStyle.PHOSPHOR : ConsoleStyle.PHOSPHOR_DIM);
         graphics.fill(x0, thumbTop, x0 + 1, thumbBottom, ConsoleStyle.PHOSPHOR_BRIGHT);
      }
   }

   static Component familyName(int family) {
      return Component.translatable(family >= 0 && family <= 2 ? "gui.deepstrike.terminal.family." + family : "gui.deepstrike.terminal.family.unknown");
   }

   static String coords(BlockPos pos) {
      return pos.getX() + " " + pos.getY() + " " + pos.getZ();
   }

   static Component missileText(TerminalMissileNetwork.Row row) {
      if (row.family() < 0) {
         return Component.translatable("gui.deepstrike.terminal.missile.unknown");
      } else if (row.kind() < 0 || row.missileKey().isEmpty()) {
         return Component.translatable("gui.deepstrike.terminal.missile.none");
      } else {
         return Component.translatable("gui.deepstrike.terminal.missile.loaded", new Object[]{Component.translatable(row.missileKey()), row.loaded(), row.capacity()});
      }
   }

   static Component statusText(int status) {
      return Component.translatable("gui.deepstrike.terminal.status." + Mth.clamp(status, 0, 3));
   }

   static int statusColor(int status) {
      return switch (status) {
         case TerminalMissiles.STATUS_READY -> -11141291;
         case TerminalMissiles.STATUS_BUSY -> -11141121;
         case TerminalMissiles.STATUS_EMPTY -> ConsoleStyle.AMBER;
         default -> -7697782;
      };
   }

   /** Esc closes the whole terminal, like on the Shahed tab; the tabs are for switching. */
   public void onClose() {
      this.minecraft.setScreen(null);
   }

   public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
   }

   public boolean isPauseScreen() {
      return false;
   }
}
