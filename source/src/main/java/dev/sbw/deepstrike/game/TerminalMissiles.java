package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import dev.sbw.deepstrike.network.TerminalMissileNetwork;
import dev.sbw.deepstrike.registry.BFAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;

/** Server logic of the "Missiles" function added to the Skyfront shahed terminal. */
public final class TerminalMissiles {
   public static final ResourceLocation TERMINAL_ID = ResourceLocation.fromNamespaceAndPath("skyfront", "shahed_terminal");
   public static final int STATUS_NO_SIGNAL = 0;
   public static final int STATUS_EMPTY = 1;
   public static final int STATUS_READY = 2;
   public static final int STATUS_BUSY = 3;
   public static final double MAX_COORDINATE = 2.9999984E7;
   public static final double REACH_SQ = 64.0;

   private TerminalMissiles() {
   }

   public static boolean isTerminal(BlockState state) {
      return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(TERMINAL_ID);
   }

   public static TerminalMissileData data(BlockEntity terminal) {
      return terminal.getExistingData(BFAttachments.TERMINAL_MISSILES.get()).orElse(TerminalMissileData.EMPTY);
   }

   public static void store(BlockEntity terminal, TerminalMissileData data) {
      if (data.entries().isEmpty()) {
         terminal.removeData(BFAttachments.TERMINAL_MISSILES.get());
      } else {
         terminal.setData(BFAttachments.TERMINAL_MISSILES.get(), data);
      }

      terminal.setChanged();
   }

   /**
    * Checks the common preconditions of every client request and returns the terminal block entity, or null.
    * The terminal is always looked up in the sender's own level.
    */
   public static BlockEntity access(ServerPlayer player, BlockPos terminal) {
      if (player == null || !player.isAlive() || terminal == null) {
         return null;
      } else if (player.distanceToSqr(Vec3.atCenterOf(terminal)) > 64.0) {
         return null;
      } else {
         ServerLevel level = player.serverLevel();
         if (!level.isLoaded(terminal) || !isTerminal(level.getBlockState(terminal))) {
            return null;
         } else {
            return level.getBlockEntity(terminal);
         }
      }
   }

   /**
    * Validates a route received from a client. Returns the clamped copy or null when it must be rejected:
    * too many points, non finite numbers or coordinates outside the world.
    */
   public static List<RoutePoint> sanitize(List<RoutePoint> route, int maxPoints, int minY, int maxY) {
      if (route == null || route.size() > maxPoints || route.size() > 32) {
         return null;
      } else {
         List<RoutePoint> result = new ArrayList<>(route.size());

         for (RoutePoint p : route) {
            if (p == null
               || !Double.isFinite(p.x())
               || !Double.isFinite(p.y())
               || !Double.isFinite(p.z())
               || !Double.isFinite(p.speed())
               || Math.abs(p.x()) > 2.9999984E7
               || Math.abs(p.z()) > 2.9999984E7) {
               return null;
            }

            RoutePoint clamped = p.clamped();
            result.add(new RoutePoint(clamped.x(), Math.max((double)minY, Math.min((double)maxY, clamped.y())), clamped.z(), clamped.speed()));
         }

         return result;
      }
   }

   public static int maxPoints(LauncherBlock.Family family) {
      return family == LauncherBlock.Family.PEACEKEEPER ? 1 : 32;
   }

   /** Point limit for a launcher at a position: strict only when the launcher block is actually loaded. */
   public static int maxPoints(ServerLevel level, BlockPos launcher) {
      if (level.isLoaded(launcher) && level.getBlockState(launcher).getBlock() instanceof LauncherBlock block) {
         return maxPoints(block.family());
      } else {
         return 32;
      }
   }

   public static int status(ServerLevel level, BlockPos launcher) {
      if (!level.isLoaded(launcher)) {
         return STATUS_NO_SIGNAL;
      } else if (level.getBlockEntity(launcher) instanceof LauncherBlockEntity be && level.getBlockState(launcher).getBlock() instanceof LauncherBlock) {
         if (be.commandBusy()) {
            return STATUS_BUSY;
         } else {
            return hasMissile(be) ? STATUS_READY : STATUS_EMPTY;
         }
      } else {
         return STATUS_NO_SIGNAL;
      }
   }

   private static boolean hasMissile(LauncherBlockEntity be) {
      for (int i = 0; i < be.capacity(); i++) {
         if (be.slot(i).getItem() instanceof MissileItem) {
            return true;
         }
      }

      return false;
   }

   /** Builds the rows for a terminal. Never loads chunks. */
   public static TerminalMissileNetwork.Snapshot snapshot(ServerLevel level, BlockPos terminalPos, BlockEntity terminal) {
      List<TerminalMissileNetwork.Row> rows = new ArrayList<>();

      for (TerminalMissileData.Entry entry : data(terminal).entries()) {
         BlockPos pos = entry.launcher();
         int status = status(level, pos);
         int family = -1;
         int kind = -1;
         String key = "";
         int loaded = 0;
         int capacity = 0;
         if (status != STATUS_NO_SIGNAL
            && level.getBlockState(pos).getBlock() instanceof LauncherBlock block
            && level.getBlockEntity(pos) instanceof LauncherBlockEntity be) {
            family = block.family().ordinal();
            loaded = be.loadCount();
            capacity = be.capacity();
            ItemStack stack = be.loaded();
            if (stack.getItem() instanceof MissileItem item) {
               kind = item.kind().ordinal();
               key = item.getDescriptionId();
            }
         }

         rows.add(new TerminalMissileNetwork.Row(pos, entry.number(), family, kind, key, loaded, capacity, status, entry.ballistic(), entry.route()));
      }

      return new TerminalMissileNetwork.Snapshot(terminalPos, rows);
   }

   public static void sendSnapshot(ServerPlayer player, BlockPos terminalPos, BlockEntity terminal) {
      TerminalMissileNetwork.sendToPlayer(player, snapshot(player.serverLevel(), terminalPos, terminal));
   }

   public static final String KEY_TERMINAL = "DsTerminal";
   public static final String KEY_DIM = "DsTerminalDim";

   /** Items that bind to a terminal: DeepStrike missiles and the launcher block items. */
   public static boolean isBindable(ItemStack stack) {
      return stack.getItem() instanceof MissileItem || stack.getItem() instanceof BlockItem block && block.getBlock() instanceof LauncherBlock;
   }

   /** The terminal position stored on the item, or null. */
   public static BlockPos boundTerminal(ItemStack stack) {
      CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
      if (custom == null) {
         return null;
      }

      CompoundTag tag = custom.getUnsafe();
      if (!tag.contains(KEY_TERMINAL, 11)) {
         return null;
      }

      int[] xyz = tag.getIntArray(KEY_TERMINAL);
      return xyz.length == 3 ? new BlockPos(xyz[0], xyz[1], xyz[2]) : null;
   }

   public static String boundDimension(ItemStack stack) {
      CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
      return custom == null ? "" : custom.getUnsafe().getString(KEY_DIM);
   }

   /**
    * Adds the launcher to the terminal the item is bound to. Server side, never loads chunks: the terminal must be in the same
    * dimension, loaded, still a terminal, below the entry cap and not already linked. Returns true when a new entry was added.
    */
   public static boolean linkLauncher(Level level, BlockPos launcher, ItemStack stack) {
      return linkLauncher(level, launcher, stack, null);
   }

   /**
    * Same as above, with an access rule: the launcher is linked only when anybody may operate it (public or without an owner)
    * or when the acting player, if known, may operate it. Otherwise a bound item could push a foreign launcher into the terminal.
    */
   public static boolean linkLauncher(Level level, BlockPos launcher, ItemStack stack, ServerPlayer actor) {
      if (!(level instanceof ServerLevel server) || stack.isEmpty()) {
         return false;
      }

      if (!server.isLoaded(launcher)
         || !(server.getBlockEntity(launcher) instanceof LauncherBlockEntity target)
         || !(target.openAccess() || actor != null && target.canOperate(actor))) {
         return false;
      }

      BlockPos terminalPos = boundTerminal(stack);
      if (terminalPos == null || !boundDimension(stack).equals(server.dimension().location().toString())) {
         return false;
      } else if (!server.isLoaded(terminalPos) || !isTerminal(server.getBlockState(terminalPos))) {
         return false;
      } else if (!server.isLoaded(launcher) || !(server.getBlockState(launcher).getBlock() instanceof LauncherBlock)) {
         return false;
      }

      BlockEntity terminal = server.getBlockEntity(terminalPos);
      if (terminal == null) {
         return false;
      }

      TerminalMissileData data = data(terminal);
      if (data.find(launcher) != null || data.entries().size() >= TerminalMissileData.MAX_LAUNCHERS) {
         return false;
      }

      store(terminal, data.with(new TerminalMissileData.Entry(launcher, List.of(), false)));
      return true;
   }

   /** Right-click on the terminal with the laptop (links the laptop's bound launcher) or with a missile / launcher item (binds the item). */
   public static void rightClick(RightClickBlock event) {
      ItemStack stack = event.getItemStack();
      boolean laptop = stack.getItem() instanceof DeepStrikeLaptopItem;
      if (!laptop && !isBindable(stack)) {
         return;
      }

      Level level = event.getLevel();
      BlockPos pos = event.getPos();
      if (!isTerminal(level.getBlockState(pos))) {
         return;
      }

      // Cancel on both sides. MultiPlayerGameMode.useItemOn still sends the use-item-on packet when the client cancels the event, so
      // the server handler runs anyway; not cancelling on the client would let BlockItem.useOn place a ghost launcher (sneaking skips the block).
      event.setCanceled(true);
      event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
      if (!(event.getEntity() instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
         return;
      }

      BlockEntity terminal = server.getBlockEntity(pos);
      if (terminal == null) {
         return;
      }

      if (!laptop) {
         bindItem(player, server, pos, stack);
         return;
      }

      CompoundTag tag = NbtCompat.getTag(stack);
      if (tag == null || !tag.contains("BoundLauncher", 4)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.bind_hint"), true);
         return;
      }

      BlockPos launcher = BlockPos.of(tag.getLong("BoundLauncher"));
      if (!server.isLoaded(launcher) || !(server.getBlockState(launcher).getBlock() instanceof LauncherBlock block)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.bind_hint"), true);
         return;
      }

      if (server.getBlockEntity(launcher) instanceof LauncherBlockEntity be && !be.canOperate(player)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.no_access"), true);
         return;
      }

      TerminalMissileData data = data(terminal);
      Component name = block.getName();
      if (data.find(launcher) != null) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.already", new Object[]{name}), true);
      } else if (data.entries().size() >= 12) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.full", new Object[]{12}), true);
      } else {
         store(terminal, data.with(new TerminalMissileData.Entry(launcher, List.of(), false)));
         player.displayClientMessage(
            Component.translatable("message.deepstrike.terminal.missile.bound", new Object[]{name, launcher.getX(), launcher.getY(), launcher.getZ()}), true
         );
      }
   }

   private static void bindItem(ServerPlayer player, ServerLevel server, BlockPos pos, ItemStack stack) {
      BlockPos old = boundTerminal(stack);
      if (old != null
         && !old.equals(pos)
         && boundDimension(stack).equals(server.dimension().location().toString())
         && server.isLoaded(old)
         && isTerminal(server.getBlockState(old))) {
         player.displayClientMessage(
            Component.translatable("message.deepstrike.terminal.missile.item_already_other", new Object[]{old.getX(), old.getY(), old.getZ()}), true
         );
         return;
      }

      NbtCompat.update(stack, t -> {
         t.putIntArray(KEY_TERMINAL, new int[]{pos.getX(), pos.getY(), pos.getZ()});
         t.putString(KEY_DIM, server.dimension().location().toString());
      });
      player.displayClientMessage(
         Component.translatable("message.deepstrike.terminal.missile.item_bound", new Object[]{pos.getX(), pos.getY(), pos.getZ()}), true
      );
   }

   /** Minimum number of ticks between two snapshot requests of one player; the screens poll every 40 ticks. */
   private static final long REQUEST_COOLDOWN_TICKS = 10L;
   private static final java.util.Map<UUID, Long> LAST_REQUEST = new java.util.concurrent.ConcurrentHashMap<>();

   private static final java.util.Map<UUID, long[]> PANEL_RATE = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.util.Map<UUID, java.util.LinkedHashSet<UUID>> PANEL_SEEN = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.util.Map<UUID, String> PANEL_ERROR = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.util.Set<String> PANEL_ACTIONS = java.util.Set.of(
      "login", "refresh", "save", "arm", "disarm", "launch", "delete", "member", "camera", "bind_unit",
      "defense_select", "defense_reload", "defense_toggle", "defense_fire"
   );

   public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
      UUID id = event.getEntity().getUUID();
      LAST_REQUEST.remove(id);
      PANEL_RATE.remove(id);
      PANEL_SEEN.remove(id);
      PANEL_ERROR.remove(id);
   }

   /** True when the panel protocol accepts this action. */
   public static boolean panelAction(String action) {
      return action != null && PANEL_ACTIONS.contains(action);
   }

   /** Command of the Skyfront panel (laptop and command tablet functions). Reach is checked by {@link #access}. */
   public static void handlePanel(ServerPlayer player, BlockPos terminalPos, String action, UUID commandId, CompoundTag payload) {
      if (player == null || action == null || commandId == null) {
         return;
      }

      BlockEntity terminal = access(player, terminalPos);
      if (terminal == null) {
         return;
      }

      long tick = player.getServer().getTickCount();
      long[] rate = PANEL_RATE.get(player.getUUID());
      if (rate == null || tick - rate[0] >= 20L) {
         rate = new long[]{tick, 0L};
         PANEL_RATE.put(player.getUUID(), rate);
      }

      if (rate[1] >= 24L) {
         return;
      }

      rate[1]++;
      if (!panelAction(action)) {
         dev.sbw.deepstrike.DeepStrikeAddon.LOGGER.debug("Ignored panel action '{}' from {}", action, player.getGameProfile().getName());
         return;
      }

      ServerLevel level = player.serverLevel();
      CommandCenterData center = CommandCenterData.get(level);
      java.util.LinkedHashSet<UUID> seen = PANEL_SEEN.computeIfAbsent(player.getUUID(), key -> new java.util.LinkedHashSet<>());
      if (!seen.add(commandId)) {
         CompoundTag state = center.snapshot(player);
         state.putString("Error", PANEL_ERROR.getOrDefault(player.getUUID(), ""));
         TerminalMissileNetwork.sendPanel(player, terminalPos, state);
         return;
      }

      while (seen.size() > 128) {
         seen.remove(seen.iterator().next());
      }

      String error;
      if (payload == null) {
         error = "action";
      } else if (action.equals("bind_unit")) {
         error = bindUnit(player, level, terminal, center, payload);
      } else {
         error = center.action(player, action, payload);
      }

      if (!action.equals("refresh")) {
         PANEL_ERROR.put(player.getUUID(), error);
      }

      CompoundTag snapshot = center.snapshot(player);
      snapshot.putString("Error", action.equals("refresh") ? PANEL_ERROR.getOrDefault(player.getUUID(), "") : error);
      TerminalMissileNetwork.sendPanel(player, terminalPos, snapshot);
   }

   private static String bindUnit(ServerPlayer player, ServerLevel level, BlockEntity terminal, CommandCenterData center, CompoundTag payload) {
      if (!payload.contains("Launcher", 4)) {
         return "unit";
      }

      BlockPos launcher = BlockPos.of(payload.getLong("Launcher"));
      if (data(terminal).find(launcher) == null) {
         return "unit";
      } else if (!level.isLoaded(launcher)
         || !(level.getBlockState(launcher).getBlock() instanceof LauncherBlock)
         || !(level.getBlockEntity(launcher) instanceof LauncherBlockEntity be)) {
         return "offline";
      } else {
         return center.bindFromTerminal(player, be);
      }
   }

   public static void handleRequest(ServerPlayer player, BlockPos terminalPos) {
      long now = player.serverLevel().getGameTime();
      Long last = LAST_REQUEST.get(player.getUUID());
      if (last != null && now >= last && now - last < REQUEST_COOLDOWN_TICKS) {
         return;
      }

      LAST_REQUEST.put(player.getUUID(), now);

      BlockEntity terminal = access(player, terminalPos);
      if (terminal != null) {
         sendSnapshot(player, terminalPos, terminal);
      }
   }

   /**
    * The launcher of an entry when the player may operate it, else null (a message is sent). A launcher that is not loaded,
    * or is no longer a launcher, is never writable: its route cannot be validated against the family and access cannot be checked.
    */
   private static LauncherBlockEntity operable(ServerPlayer player, ServerLevel level, BlockPos launcher) {
      if (!level.isLoaded(launcher)
         || !(level.getBlockState(launcher).getBlock() instanceof LauncherBlock)
         || !(level.getBlockEntity(launcher) instanceof LauncherBlockEntity be)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.no_signal"), true);
         return null;
      } else if (!be.canOperate(player)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.no_access"), true);
         return null;
      } else {
         return be;
      }
   }

   public static void handleSetRoute(ServerPlayer player, BlockPos terminalPos, BlockPos launcher, List<RoutePoint> route, boolean ballistic) {
      BlockEntity terminal = access(player, terminalPos);
      if (terminal == null) {
         return;
      }

      ServerLevel level = player.serverLevel();
      TerminalMissileData data = data(terminal);
      if (data.find(launcher) == null) {
         return;
      }

      if (operable(player, level, launcher) == null) {
         sendSnapshot(player, terminalPos, terminal);
         return;
      }

      List<RoutePoint> clean = sanitize(route, maxPoints(level, launcher), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
      if (clean == null) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.invalid_route"), true);
         return;
      }

      store(terminal, data.with(new TerminalMissileData.Entry(launcher, clean, ballistic)));
      sendSnapshot(player, terminalPos, terminal);
   }

   public static void handleUnbind(ServerPlayer player, BlockPos terminalPos, BlockPos launcher) {
      BlockEntity terminal = access(player, terminalPos);
      if (terminal == null) {
         return;
      }

      TerminalMissileData data = data(terminal);
      // Unlinking is allowed for any entry of this terminal (the player is in range, see access): terminals have no owner, and a foreign
      // launcher must never be able to occupy the 12 slots for good. Routes and launches still require canOperate.
      if (data.find(launcher) != null) {
         store(terminal, data.without(launcher));
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.unbound"), true);
      }

      sendSnapshot(player, terminalPos, terminal);
   }

   public static void handleLaunch(ServerPlayer player, BlockPos terminalPos, BlockPos launcher, List<RoutePoint> route, boolean ballistic) {
      BlockEntity terminal = access(player, terminalPos);
      if (terminal == null) {
         return;
      }

      ServerLevel level = player.serverLevel();
      TerminalMissileData data = data(terminal);
      if (data.find(launcher) == null) {
         return;
      }

      // The chunk must be checked before getBlockEntity: an unloaded launcher must never be loaded by this request.
      if (!level.isLoaded(launcher)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.no_signal"), true);
         sendSnapshot(player, terminalPos, terminal);
         return;
      }

      if (!(level.getBlockState(launcher).getBlock() instanceof LauncherBlock block) || !(level.getBlockEntity(launcher) instanceof LauncherBlockEntity be)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.no_signal"), true);
         sendSnapshot(player, terminalPos, terminal);
         return;
      }

      if (!be.canOperate(player)) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.no_access"), true);
         sendSnapshot(player, terminalPos, terminal);
         return;
      }

      List<RoutePoint> clean = sanitize(route, maxPoints(block.family()), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
      if (clean == null) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.invalid_route"), true);
         return;
      }

      store(terminal, data.with(new TerminalMissileData.Entry(launcher, clean, ballistic)));
      if (clean.isEmpty()) {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.empty_route"), true);
      } else if (!be.fire(player, clean, ballistic, be.transmitFrequency(), UUID.randomUUID())) {
         player.displayClientMessage(Component.translatable("message.deepstrike.launch.rejected", new Object[]{be.getLastFireErrorComponent()}), false);
      } else {
         player.displayClientMessage(Component.translatable("message.deepstrike.terminal.missile.launched"), true);
      }

      sendSnapshot(player, terminalPos, terminal);
   }
}
