package dev.warmode.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.warmode.WarMode;
import dev.warmode.block.HeadquartersBlock;
import dev.warmode.game.GameMode;
import dev.warmode.game.WarData;
import dev.warmode.game.WarManager;
import dev.warmode.game.WarTeam;
import dev.warmode.shop.ShopCatalog;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class WarCommands {
   private static RequiredArgumentBuilder<CommandSourceStack, String> teamArg() {
      return Commands.argument("team", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"blue", "red"}, b));
   }

   private static WarTeam team(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c) {
      return WarTeam.byId(StringArgumentType.getString(c, "team"));
   }

   private static int fail(CommandSourceStack source, String key, Object... args) {
      source.sendFailure(Component.translatable(key, args));
      return 0;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
      return Commands.literal(name).requires(s -> s.hasPermission(2));
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         Commands.literal("war")
            .then(
               Commands.literal("join").then(teamArg().executes(c -> {
                  WarTeam team = team(c);
                  ServerPlayer player = c.getSource().getPlayerOrException();
                  if (team == null) {
                     return fail(c.getSource(), "msg.warmode.unknown_team");
                  }

                  WarData data = WarData.get(c.getSource().getServer());
                  if (data.teamOf(player.getUUID()) == team) {
                     return fail(c.getSource(), "msg.warmode.already_in", team.title());
                  }

                  if (data.count(team) > data.count(team.other())) {
                     return fail(c.getSource(), "msg.warmode.unbalanced");
                  }

                  WarManager.join(c.getSource().getServer(), player, team);
                  return 1;
               }))
            )
            .then(Commands.literal("leave").executes(c -> {
               WarManager.leave(c.getSource().getServer(), c.getSource().getPlayerOrException());
               return 1;
            }))
            .then(Commands.literal("status").executes(c -> status(c.getSource())))
            .then(
               admin("team")
                  .then(
                     Commands.literal("add")
                        .then(
                           Commands.argument("players", EntityArgument.players()).then(teamArg().executes(c -> {
                              WarTeam team = team(c);
                              if (team == null) {
                                 return fail(c.getSource(), "msg.warmode.unknown_team");
                              }

                              Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");

                              for (ServerPlayer player : players) {
                                 WarManager.join(c.getSource().getServer(), player, team);
                              }

                              return players.size();
                           }))
                        )
                  )
                  .then(Commands.literal("remove").then(Commands.argument("players", EntityArgument.players()).executes(c -> {
                     Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "players");

                     for (ServerPlayer player : players) {
                        WarManager.leave(c.getSource().getServer(), player);
                     }

                     return players.size();
                  })))
                  .then(Commands.literal("auto").executes(c -> {
                     int n = WarManager.autoAssign(c.getSource().getServer());
                     c.getSource().sendSuccess(() -> Component.translatable("msg.warmode.auto", n), true);
                     return n;
                  }))
            )
            .then(admin("start").executes(c -> {
               Component error = WarManager.start(c.getSource().getServer());
               if (error != null) {
                  c.getSource().sendFailure(error);
                  return 0;
               }

               return 1;
            }))
            .then(admin("stop").executes(c -> {
               WarManager.stop(c.getSource().getServer());
               return 1;
            }))
            .then(
               admin("mode")
                  .then(
                     Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"hq", "score", "timed", "endless"}, b))
                        .executes(c -> {
                           GameMode mode = GameMode.byId(StringArgumentType.getString(c, "mode"));
                           if (mode == null) {
                              return fail(c.getSource(), "msg.warmode.unknown_mode");
                           }

                           WarData data = WarData.get(c.getSource().getServer());
                           if (data.running) {
                              return fail(c.getSource(), "msg.warmode.already_running");
                           }

                           data.mode = mode;
                           data.setDirty();
                           WarManager.syncAll(c.getSource().getServer());
                           c.getSource().sendSuccess(() -> Component.translatable("msg.warmode.mode_set", Component.translatable("mode.warmode." + mode.id)), true);
                           return 1;
                        })
                  )
            )
            .then(
               admin("set")
                  .then(
                     Commands.argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(WarData.DEFAULTS.keySet(), b))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0)).executes(c -> {
                           String key = StringArgumentType.getString(c, "key");
                           if (!WarData.DEFAULTS.containsKey(key)) {
                              return fail(c.getSource(), "msg.warmode.unknown_setting", key);
                           }

                           double value = DoubleArgumentType.getDouble(c, "value");
                           WarData data = WarData.get(c.getSource().getServer());
                           data.settings.put(key, value);
                           data.setDirty();
                           WarManager.ensureScoreboardTeams(c.getSource().getServer());
                           WarManager.syncAll(c.getSource().getServer());
                           c.getSource().sendSuccess(() -> Component.literal(key + " = " + trim(value)), true);
                           return 1;
                        }))
                  )
            )
            .then(admin("settings").executes(c -> {
               WarData data = WarData.get(c.getSource().getServer());
               c.getSource().sendSuccess(() -> Component.translatable("msg.warmode.settings_header", Component.translatable("mode.warmode." + data.mode.id)), false);
               WarData.DEFAULTS.keySet().forEach(key -> c.getSource().sendSuccess(() -> Component.literal(" " + key + " = " + trim(data.setting(key))), false));
               return 1;
            }))
            .then(
               admin("bank")
                  .then(
                     teamArg()
                        .then(
                           Commands.literal("add").then(Commands.argument("amount", DoubleArgumentType.doubleArg()).executes(c -> bank(c, false)))
                        )
                        .then(
                           Commands.literal("set").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(c -> bank(c, true)))
                        )
                  )
            )
            .then(
               admin("hq")
                  .then(Commands.literal("set").then(teamArg().executes(c -> setHq(c.getSource(), team(c)))))
                  .then(Commands.literal("remove").then(teamArg().executes(c -> removeHq(c.getSource(), team(c)))))
            )
            .then(
               admin("reward")
                  .then(
                     teamArg()
                        .then(
                           Commands.argument("kind", StringArgumentType.word())
                              .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[] {"kill_player", "kill_vehicle", "intercept_missile", "intercept_aircraft", "intercept_ordnance"}, b))
                              .executes(c -> {
                                 WarTeam team = team(c);
                                 String kind = StringArgumentType.getString(c, "kind");
                                 if (team == null) {
                                    return fail(c.getSource(), "msg.warmode.unknown_team");
                                 }

                                 if (!WarData.DEFAULTS.containsKey("reward_" + kind)) {
                                    return fail(c.getSource(), "msg.warmode.unknown_setting", kind);
                                 }

                                 WarManager.reward(c.getSource().getServer(), team, kind, Component.literal(kind));
                                 return 1;
                              })
                        )
                  )
            )
            .then(admin("reload").executes(c -> {
               ShopCatalog.load();
               c.getSource().sendSuccess(() -> Component.translatable("msg.warmode.reloaded"), true);
               return 1;
            }))
      );
   }

   private static String hq(WarData data, int i) {
      return data.hqPos[i] == null ? "" : "  HQ " + Math.round(data.hqHealth[i]) + "/" + Math.round(data.setting("hq_health"));
   }

   private static String trim(double value) {
      return value == Math.rint(value) ? Long.toString((long)value) : Double.toString(value);
   }

   private static int bank(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean set) {
      WarTeam team = team(c);
      if (team == null) {
         return fail(c.getSource(), "msg.warmode.unknown_team");
      }

      MinecraftServer server = c.getSource().getServer();
      WarData data = WarData.get(server);
      double amount = DoubleArgumentType.getDouble(c, "amount");
      int i = team.ordinal();
      data.bank[i] = Math.max(0.0, Math.min(data.setting("balance_cap"), set ? amount : data.bank[i] + amount));
      data.setDirty();
      WarManager.syncAll(server);
      c.getSource().sendSuccess(() -> Component.translatable("msg.warmode.bank", team.title(), (int)data.bank[i]), true);
      return 1;
   }

   private static int setHq(CommandSourceStack source, WarTeam team) {
      if (team == null) {
         return fail(source, "msg.warmode.unknown_team");
      }

      ServerLevel level = source.getLevel();
      BlockPos pos = BlockPos.containing(source.getPosition());
      WarData data = WarData.get(source.getServer());
      removeHq(source, team);
      level.setBlockAndUpdate(pos, WarMode.HEADQUARTERS.get().defaultBlockState().setValue(HeadquartersBlock.TEAM, HeadquartersBlock.Side.of(team)));
      int i = team.ordinal();
      data.hqPos[i] = pos;
      data.hqDimension[i] = level.dimension().location();
      data.hqHealth[i] = data.setting("hq_health");
      data.setDirty();
      WarManager.syncAll(source.getServer());
      source.sendSuccess(() -> Component.translatable("msg.warmode.hq_set", team.title(), pos.getX(), pos.getY(), pos.getZ()), true);
      return 1;
   }

   private static int removeHq(CommandSourceStack source, WarTeam team) {
      if (team == null) {
         return fail(source, "msg.warmode.unknown_team");
      }

      WarData data = WarData.get(source.getServer());
      int i = team.ordinal();
      BlockPos old = data.hqPos[i];
      if (old != null) {
         ServerLevel level = source.getServer().getLevel(data.hqLevel(team));
         if (level != null && level.getBlockState(old).getBlock() instanceof HeadquartersBlock) {
            level.setBlockAndUpdate(old, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
         }

         data.hqPos[i] = null;
         data.hqHealth[i] = 0.0;
         data.setDirty();
         WarManager.syncAll(source.getServer());
      }

      return 1;
   }

   private static int status(CommandSourceStack source) {
      WarData data = WarData.get(source.getServer());
      source.sendSuccess(
         () -> Component.translatable(
            "msg.warmode.status",
            Component.translatable("mode.warmode." + data.mode.id),
            data.running ? Component.translatable("msg.warmode.state_running") : Component.translatable("msg.warmode.state_idle"),
            Component.empty().append(WarTeam.BLUE.title()).append(" " + (int)data.bank[0] + " / " + data.score[0] + " (" + data.count(WarTeam.BLUE) + ")" + hq(data, 0)),
            Component.empty().append(WarTeam.RED.title()).append(" " + (int)data.bank[1] + " / " + data.score[1] + " (" + data.count(WarTeam.RED) + ")" + hq(data, 1))
         ),
         false
      );
      return 1;
   }

   private WarCommands() {
   }
}
