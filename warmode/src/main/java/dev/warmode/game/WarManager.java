package dev.warmode.game;

import dev.warmode.WarMode;
import dev.warmode.net.WarNet;
import dev.warmode.shop.ShopCatalog;
import dev.warmode.shop.ShopEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.network.PacketDistributor;

/** Round logic: team membership, bank regeneration, rewards, win conditions, shop purchases. */
public final class WarManager {
   private static final double TERMINAL_REACH = 10.0;

   // ---------------------------------------------------------------- teams

   public static void ensureScoreboardTeams(MinecraftServer server) {
      WarData data = WarData.get(server);
      Scoreboard board = server.getScoreboard();

      for (WarTeam team : WarTeam.values()) {
         PlayerTeam playerTeam = board.getPlayerTeam(team.scoreboardName());
         if (playerTeam == null) {
            playerTeam = board.addPlayerTeam(team.scoreboardName());
         }

         playerTeam.setDisplayName(Component.translatable("team.warmode." + team.id));
         playerTeam.setColor(team.format);
         playerTeam.setAllowFriendlyFire(data.setting("friendly_fire") > 0.5);
         playerTeam.setSeeFriendlyInvisibles(true);
      }
   }

   public static void join(MinecraftServer server, ServerPlayer player, WarTeam team) {
      WarData data = WarData.get(server);
      data.members.put(player.getUUID(), team);
      data.setDirty();
      ensureScoreboardTeams(server);
      Scoreboard board = server.getScoreboard();
      board.addPlayerToTeam(player.getScoreboardName(), board.getPlayerTeam(team.scoreboardName()));
      player.sendSystemMessage(Component.translatable("msg.warmode.joined", team.title()));
      syncAll(server);
   }

   public static void leave(MinecraftServer server, ServerPlayer player) {
      WarData data = WarData.get(server);
      WarTeam old = data.members.remove(player.getUUID());
      if (old != null) {
         data.setDirty();
         Scoreboard board = server.getScoreboard();
         PlayerTeam playerTeam = board.getPlayerTeam(old.scoreboardName());
         if (playerTeam != null) {
            board.removePlayerFromTeam(player.getScoreboardName(), playerTeam);
         }

         player.sendSystemMessage(Component.translatable("msg.warmode.left"));
         syncAll(server);
      }
   }

   /** Splits all online players without a team so that the sizes stay as even as possible. */
   public static int autoAssign(MinecraftServer server) {
      WarData data = WarData.get(server);
      List<ServerPlayer> free = new ArrayList<>();

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (data.teamOf(player.getUUID()) == null) {
            free.add(player);
         }
      }

      java.util.Collections.shuffle(free);

      for (ServerPlayer player : free) {
         join(server, player, data.count(WarTeam.BLUE) <= data.count(WarTeam.RED) ? WarTeam.BLUE : WarTeam.RED);
      }

      return free.size();
   }

   public static void onLogin(ServerPlayer player) {
      MinecraftServer server = player.getServer();
      WarData data = WarData.get(server);
      WarTeam team = data.teamOf(player.getUUID());
      ensureScoreboardTeams(server);
      if (team != null) {
         Scoreboard board = server.getScoreboard();
         board.addPlayerToTeam(player.getScoreboardName(), board.getPlayerTeam(team.scoreboardName()));
      }

      syncTo(player, data);
   }

   // ---------------------------------------------------------------- round

   /** Returns an error message or null when the round has started. */
   public static Component start(MinecraftServer server) {
      WarData data = WarData.get(server);
      if (data.running) {
         return Component.translatable("msg.warmode.already_running");
      } else if (data.mode == GameMode.HQ && (data.hqPos[0] == null || data.hqPos[1] == null)) {
         return Component.translatable("msg.warmode.need_hq");
      } else if (data.mode == GameMode.TIMED && data.setting("time_limit_min") <= 0.0) {
         return Component.translatable("msg.warmode.need_timer");
      } else {
         ensureScoreboardTeams(server);
         long now = server.overworld().getGameTime();
         data.running = true;

         for (int i = 0; i < 2; i++) {
            data.bank[i] = data.setting("start_balance");
            data.score[i] = 0;
            data.hqHealth[i] = data.setting("hq_health");
         }

         data.endTick = data.mode != GameMode.ENDLESS && data.setting("time_limit_min") > 0.0 ? now + (long)(data.setting("time_limit_min") * 1200.0) : -1L;
         data.setDirty();
         broadcast(server, Component.translatable("msg.warmode.started", Component.translatable("mode.warmode." + data.mode.id)));
         syncAll(server);
         return null;
      }
   }

   public static void stop(MinecraftServer server) {
      WarData data = WarData.get(server);
      if (data.running) {
         end(server, data, null, false);
      }
   }

   private static void end(MinecraftServer server, WarData data, WarTeam winner, boolean draw) {
      data.running = false;
      data.endTick = -1L;
      data.setDirty();
      if (winner != null) {
         broadcast(server, Component.translatable("msg.warmode.won", winner.title(), data.score[0], data.score[1]));
      } else if (draw) {
         broadcast(server, Component.translatable("msg.warmode.draw", data.score[0], data.score[1]));
      } else {
         broadcast(server, Component.translatable("msg.warmode.stopped"));
      }

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         WarTeam team = data.teamOf(player.getUUID());
         if (winner != null && team != null) {
            player.playNotifySound(team == winner ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.WITHER_DEATH, SoundSource.MASTER, 0.8F, 1.0F);
         }
      }

      syncAll(server);
   }

   public static void tick(MinecraftServer server) {
      ServerLevel overworld = server.overworld();
      long now = overworld.getGameTime();
      if (now % 20L != 0L) {
         return;
      }

      WarData data = WarData.get(server);
      if (data.running) {
         double cap = data.setting("balance_cap");

         for (WarTeam team : WarTeam.values()) {
            int online = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
               if (data.teamOf(player.getUUID()) == team) {
                  online++;
               }
            }

            double gain = data.setting("regen_base") + data.setting("regen_per_player") * online;
            data.bank[team.ordinal()] = Math.min(cap, data.bank[team.ordinal()] + gain);
         }

         data.setDirty();
         checkEnd(server, data, now);
      }

      syncAll(server);
   }

   private static void checkEnd(MinecraftServer server, WarData data, long now) {
      if (!data.running) {
         return;
      }

      if (data.mode == GameMode.HQ) {
         for (WarTeam team : WarTeam.values()) {
            if (data.hqPos[team.ordinal()] != null && data.hqHealth[team.ordinal()] <= 0.0) {
               end(server, data, team.other(), false);
               return;
            }
         }
      }

      if (data.mode == GameMode.SCORE) {
         int limit = data.settingInt("score_limit");
         for (WarTeam team : WarTeam.values()) {
            if (limit > 0 && data.score[team.ordinal()] >= limit) {
               end(server, data, team, false);
               return;
            }
         }
      }

      if (data.endTick >= 0L && now >= data.endTick) {
         double blue;
         double red;
         if (data.mode == GameMode.HQ) {
            blue = data.hqHealth[0];
            red = data.hqHealth[1];
         } else {
            blue = data.score[0];
            red = data.score[1];
         }

         if (blue == red) {
            end(server, data, null, true);
         } else {
            end(server, data, blue > red ? WarTeam.BLUE : WarTeam.RED, false);
         }
      }
   }

   // ---------------------------------------------------------------- rewards and damage

   /**
    * Pays the team for a deed. {@code kind} is one of kill_player, kill_vehicle, intercept_missile, intercept_aircraft,
    * intercept_ordnance; the amounts come from the reward_ and points_ settings.
    */
   public static void reward(MinecraftServer server, WarTeam team, String kind, Component what) {
      WarData data = WarData.get(server);
      if (!data.running || team == null) {
         return;
      }

      int i = team.ordinal();
      double money = data.setting("reward_" + kind);
      int points = data.settingInt("points_" + kind);
      data.bank[i] = Math.min(data.setting("balance_cap"), data.bank[i] + money);
      data.score[i] += points;
      data.setDirty();
      Component line = Component.translatable("msg.warmode.reward", what, (int)money, points);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (data.teamOf(player.getUUID()) == team) {
            player.displayClientMessage(line, true);
         }
      }

      WarMode.LOGGER.info("War reward: {} +{} supply, +{} points for {}", kind, (int)money, points, team.id);
      syncAll(server);
      checkEnd(server, data, server.overworld().getGameTime());
   }

   /** Damages a headquarters. {@code attacker} may be null when the source is unknown. */
   public static void damageHq(MinecraftServer server, WarTeam owner, double amount, WarTeam attacker) {
      WarData data = WarData.get(server);
      int i = owner.ordinal();
      if (!data.running || data.mode != GameMode.HQ || data.hqPos[i] == null || attacker == owner || amount <= 0.0 || data.hqHealth[i] <= 0.0) {
         return;
      }

      data.hqHealth[i] = Math.max(0.0, data.hqHealth[i] - amount);
      data.setDirty();
      if (data.hqHealth[i] <= 0.0) {
         ServerLevel level = server.getLevel(data.hqLevel(owner));
         if (level != null) {
            BlockPos pos = data.hqPos[i];
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0F, Level.ExplosionInteraction.NONE);
            level.removeBlock(pos, false);
         }
      }

      checkEnd(server, data, server.overworld().getGameTime());
      syncAll(server);
   }

   // ---------------------------------------------------------------- shop

   public static void openShop(ServerPlayer player, BlockPos terminal) {
      WarData data = WarData.get(player.getServer());
      WarTeam team = data.teamOf(player.getUUID());
      if (team == null) {
         player.displayClientMessage(Component.translatable("msg.warmode.no_team"), true);
      } else if (!data.running) {
         player.displayClientMessage(Component.translatable("msg.warmode.not_running"), true);
      } else {
         PacketDistributor.sendToPlayer(player, new WarNet.OpenShop(terminal, ShopCatalog.forTeam(team)));
         syncTo(player, data);
      }
   }

   public static void buy(ServerPlayer player, BlockPos terminal, String entryId) {
      MinecraftServer server = player.getServer();
      WarData data = WarData.get(server);
      WarTeam team = data.teamOf(player.getUUID());
      ShopEntry entry = ShopCatalog.get(entryId);
      if (team == null || !data.running || entry == null || !entry.availableTo(team)) {
         return;
      }

      if (terminal.distToCenterSqr(player.position()) > TERMINAL_REACH * TERMINAL_REACH
         || !(player.level().getBlockState(terminal).getBlock() instanceof dev.warmode.block.ShopTerminalBlock)) {
         player.displayClientMessage(Component.translatable("msg.warmode.too_far"), true);
         return;
      }

      ResourceLocation id = ResourceLocation.tryParse(entry.item());
      if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
         return;
      }

      int i = team.ordinal();
      if (data.bank[i] < entry.price()) {
         player.displayClientMessage(Component.translatable("msg.warmode.not_enough", entry.price(), (int)data.bank[i]), true);
         player.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
         return;
      }

      data.bank[i] -= entry.price();
      data.setDirty();
      ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), entry.count());
      Component name = stack.getHoverName();
      if (!player.getInventory().add(stack) && !stack.isEmpty()) {
         ItemEntity drop = player.drop(stack, false);
         if (drop != null) {
            drop.setNoPickUpDelay();
            drop.setTarget(player.getUUID());
         }
      }

      player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.2F);
      player.displayClientMessage(Component.translatable("msg.warmode.bought", entry.count(), name, entry.price()), true);
      WarMode.LOGGER.info("War shop: {} bought {} x{} for {} ({})", player.getGameProfile().getName(), entry.item(), entry.count(), entry.price(), team.id);
      syncAll(server);
   }

   // ---------------------------------------------------------------- sync

   public static void syncAll(MinecraftServer server) {
      WarData data = WarData.get(server);

      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         syncTo(player, data);
      }
   }

   public static void syncTo(ServerPlayer player, WarData data) {
      long now = player.getServer().overworld().getGameTime();
      WarTeam team = data.teamOf(player.getUUID());
      int seconds = data.running && data.endTick >= 0L ? (int)Math.max(0L, (data.endTick - now) / 20L) : -1;
      PacketDistributor.sendToPlayer(
         player,
         new WarNet.State(
            team == null ? -1 : team.ordinal(),
            data.mode.ordinal(),
            data.running,
            (int)data.bank[0],
            (int)data.bank[1],
            data.score[0],
            data.score[1],
            seconds,
            hqPercent(data, 0),
            hqPercent(data, 1),
            data.settingInt("balance_cap")
         )
      );
   }

   private static int hqPercent(WarData data, int i) {
      if (data.hqPos[i] == null) {
         return -1;
      }

      double max = Math.max(1.0, data.setting("hq_health"));
      return (int)Math.round(Math.min(100.0, Math.max(0.0, data.hqHealth[i] / max * 100.0)));
   }

   public static void broadcast(MinecraftServer server, Component message) {
      server.getPlayerList().broadcastSystemMessage(message, false);
   }

   public static WarTeam teamOf(MinecraftServer server, UUID player) {
      return player == null ? null : WarData.get(server).teamOf(player);
   }

   private WarManager() {
   }
}
