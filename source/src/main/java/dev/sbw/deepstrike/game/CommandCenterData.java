package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.mojang.authlib.GameProfile;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component.Serializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent;

public final class CommandCenterData extends SavedData {
   private final Map<UUID, CompoundTag> networks = new LinkedHashMap<>();
   private final Map<UUID, UUID> sessions = new HashMap<>();
   private final Map<String, CommandCenterData.Lease> leases = new HashMap<>();
   private static final TicketType<String> TICKET = TicketType.create("deepstrike_command", Comparator.<String>naturalOrder());

   public static CommandCenterData get(ServerLevel level) {
      return (CommandCenterData)level.getServer().overworld().getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedData.Factory<>(CommandCenterData::new, (tag, registries) -> CommandCenterData.load(tag)), "deepstrike_command");
   }

   public static CommandCenterData load(CompoundTag source) {
      CommandCenterData data = new CommandCenterData();

      for (Tag raw : source.getList("Networks", 10)) {
         CompoundTag n = ((CompoundTag)raw).copy();
         if (n.hasUUID("Id") && n.hasUUID("Owner")) {
            for (Tag tr : tasks(n)) {
               CompoundTag t = (CompoundTag)tr;
               if (active(t)) {
                  t.putString("State", "RECOVERING");
                  t.putBoolean("Resume", true);
                  t.remove("StartAt");
               }
            }

            data.networks.put(n.getUUID("Id"), n);
         }
      }

      return data;
   }

   public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
      return this.save(tag);
   }

   public CompoundTag save(CompoundTag tag) {
      ListTag list = new ListTag();
      this.networks.values().forEach(n -> list.add(n.copy()));
      tag.put("Networks", list);
      return tag;
   }

   private static ListTag list(CompoundTag n, String key) {
      if (!n.contains(key, 9)) {
         n.put(key, new ListTag());
      }

      return n.getList(key, 10);
   }

   private static ListTag units(CompoundTag n) {
      return list(n, "Units");
   }

   private static ListTag tasks(CompoundTag n) {
      return list(n, "Tasks");
   }

   private static CompoundTag find(ListTag list, UUID id) {
      for (Tag raw : list) {
         CompoundTag t = (CompoundTag)raw;
         if (t.hasUUID("Id") && t.getUUID("Id").equals(id)) {
            return t;
         }
      }

      return null;
   }

   public static boolean active(CompoundTag t) {
      return Set.of("PREPARING", "DUTY", "COUNTDOWN", "RUNNING", "RECOVERING").contains(t.getString("State"));
   }

   private static boolean terminal(CompoundTag t) {
      return Set.of("DONE", "ERROR", "DRAFT").contains(t.getString("State"));
   }

   private static ServerLevel dimension(MinecraftServer server, CompoundTag unit) {
      ResourceLocation id = ResourceLocation.tryParse(unit.getString("Dimension"));
      return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
   }

   private static LauncherBlockEntity launcher(MinecraftServer server, CompoundTag unit) {
      ServerLevel level = dimension(server, unit);
      BlockPos pos = BlockPos.of(unit.getLong("Position"));
      if (level != null && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null) {
         return level.getBlockEntity(pos) instanceof LauncherBlockEntity be && be.journalId().equals(unit.getUUID("Id")) ? be : null;
      } else {
         return null;
      }
   }

   private static boolean allowed(CompoundTag n, UUID player, int required) {
      if (n.getUUID("Owner").equals(player)) {
         return true;
      } else {
         for (Tag raw : list(n, "Members")) {
            CompoundTag m = (CompoundTag)raw;
            if (m.hasUUID("Player") && m.getUUID("Player").equals(player)) {
               return m.getInt("Role") >= required;
            }
         }

         return false;
      }
   }

   public int leaseCount(UUID network) {
      return (int)this.leases.keySet().stream().filter(key -> key.startsWith(network.toString() + ":")).count();
   }

   public boolean canViewNetwork(ServerPlayer player, UUID id) {
      CompoundTag n = this.session(player);
      return n != null && n.getUUID("Id").equals(id) && allowed(n, player.getUUID(), 0);
   }

   public boolean canViewFlight(ServerPlayer player, UUID missile) {
      CompoundTag n = this.session(player);
      if (n != null && allowed(n, player.getUUID(), 0)) {
         for (Tag raw : list(n, "Flights")) {
            if (((CompoundTag)raw).getUUID("Mission").equals(missile)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean may(CompoundTag network, ServerPlayer player, int role) {
      return allowed(network, player.getUUID(), role);
   }

   public boolean claimDefense(UUID unit, UUID network) {
      for (CompoundTag n : this.networks.values()) {
         if (!n.getUUID("Id").equals(network)) {
            for (Tag raw : n.getList("Defense", 10)) {
               if (((CompoundTag)raw).getUUID("Id").equals(unit)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   public CompoundTag session(ServerPlayer player) {
      UUID id = this.sessions.get(player.getUUID());
      return id == null ? null : this.networks.get(id);
   }

   private static String clean(String value, int max) {
      return value.replaceAll("[\\p{Cntrl}§]", "").strip().substring(0, Math.min(value.replaceAll("[\\p{Cntrl}§]", "").strip().length(), max));
   }

   private void log(CompoundTag network, String key, String actor, String object, long now) {
      ListTag entries = list(network, "Journal");
      CompoundTag e = new CompoundTag();
      e.putLong("Tick", now);
      e.putString("Key", key);
      e.putString("Actor", actor);
      e.putString("Object", object);
      entries.add(0, e);

      while (entries.size() > 64) {
         entries.remove(entries.size() - 1);
      }

      this.setDirty();
   }

   private Optional<GameProfile> knownProfile(MinecraftServer server, String nickname) {
      ServerPlayer online = server.getPlayerList().getPlayerByName(nickname);
      if (online != null) {
         return Optional.of(online.getGameProfile());
      } else {
         for (CompoundTag n : this.networks.values()) {
            if (n.getString("Nickname").equalsIgnoreCase(nickname)) {
               return Optional.of(new GameProfile(n.getUUID("Owner"), n.getString("Nickname")));
            }

            for (Tag raw : list(n, "Members")) {
               CompoundTag member = (CompoundTag)raw;
               if (member.getString("Nickname").equalsIgnoreCase(nickname)) {
                  return Optional.of(new GameProfile(member.getUUID("Player"), member.getString("Nickname")));
               }
            }
         }

         return Optional.empty();
      }
   }

   public String login(ServerPlayer player, String nickname) {
      nickname = clean(nickname, 16);
      if (!nickname.matches("[A-Za-z0-9_]{1,16}")) {
         return "nickname";
      } else {
         CompoundTag target = null;

         for (CompoundTag n : this.networks.values()) {
            if (n.getString("Nickname").equalsIgnoreCase(nickname) && allowed(n, player.getUUID(), 0)) {
               target = n;
               break;
            }
         }

         boolean own = player.getGameProfile().getName().equalsIgnoreCase(nickname);
         if (own) {
            target = null;

            for (CompoundTag nx : this.networks.values()) {
               if (nx.getUUID("Owner").equals(player.getUUID())) {
                  target = nx;
                  break;
               }
            }

            if (target == null) {
               target = new CompoundTag();
               target.putUUID("Id", UUID.randomUUID());
               target.putUUID("Owner", player.getUUID());
               this.networks.put(target.getUUID("Id"), target);
            }

            target.putString("Nickname", player.getGameProfile().getName());
            this.setDirty();
         }

         if (target != null && allowed(target, player.getUUID(), 0)) {
            this.sessions.put(player.getUUID(), target.getUUID("Id"));
            return "";
         } else {
            return "access";
         }
      }
   }

   public String bind(ServerPlayer player, LauncherBlockEntity be) {
      CompoundTag n = this.session(player);
      if (n == null) {
         String err = this.login(player, player.getGameProfile().getName());
         if (!err.isEmpty()) {
            return err;
         }

         n = this.session(player);
      }

      if (allowed(n, player.getUUID(), 2) && be.canBindCommand(player)) {
         if (be.getLevel() == player.level() && !(player.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 64.0)) {
            for (CompoundTag other : this.networks.values()) {
               for (Tag raw : units(other)) {
                  if (((CompoundTag)raw).getUUID("Id").equals(be.journalId())) {
                     return other == n ? "already_bound" : "access";
                  }
               }
            }

            if (units(n).size() >= (Integer)DefenseRuntime.MAX_COMMAND_UNITS.get()) {
               return "unit_limit";
            } else {
               CompoundTag u = new CompoundTag();
               u.putUUID("Id", be.journalId());
               u.putString("Dimension", player.level().dimension().location().toString());
               u.putLong("Position", be.getBlockPos().asLong());
               u.putString("Name", be.family().name() + " " + (units(n).size() + 1));
               u.putString("Group", "");
               u.putString("Family", be.family().name());
               u.putString("State", "IDLE");
               units(n).add(u);
               this.log(n, "bind", player.getGameProfile().getName(), u.getString("Name"), player.level().getGameTime());
               return "";
            }
         } else {
            return "distance";
         }
      } else {
         return "access";
      }
   }

   public String action(ServerPlayer player, String action, CompoundTag p) {
      if (action.equals("login")) {
         return this.login(player, p.getString("Nickname"));
      } else if (action.equals("refresh")) {
         return "";
      } else {
         CompoundTag n = this.session(player);
         if (n == null) {
            return "login";
         } else if (!allowed(n, player.getUUID(), 0)) {
            return "access";
         } else if (action.equals("camera")) {
            if (!p.hasUUID("Mission")) {
               return "camera";
            } else {
               for (Tag raw : list(n, "Flights")) {
                  CompoundTag r = (CompoundTag)raw;
                  if (r.getUUID("Mission").equals(p.getUUID("Mission"))) {
                     ServerLevel level = dimension(player.getServer(), r);
                     if (level != player.serverLevel()) {
                        return "dimension";
                     }

                     if (level.getEntity(r.getUUID("Mission")) instanceof GuidedMissileEntity missile && missile.cameraAvailable()) {
                        DeepStrikeNetwork.flight(missile, true);
                        DeepStrikeNetwork.sendCameraTarget(player, missile.getId());
                        return "";
                     }
                  }
               }

               return "camera";
            }
         } else {
            int required = action.equals("defense_select") ? 0 : (Set.of("member", "rename", "unbind", "delete").contains(action) ? 2 : 1);
            if (!allowed(n, player.getUUID(), required)) {
               return "access";
            } else if (action.startsWith("defense_")) {
               return CommandDefense.action(player, n, action, p);
            } else {
               long now = player.serverLevel().getGameTime();
               if (action.equals("member")) {
                  String nick = p.getString("Nickname");
                  Optional<GameProfile> profile = this.knownProfile(player.getServer(), nick);
                  if (profile.isEmpty()) {
                     return "player";
                  } else {
                     UUID id = profile.get().getId();
                     if (id.equals(n.getUUID("Owner"))) {
                        return "access";
                     } else {
                        ListTag ms = list(n, "Members");
                        ms.removeIf(rawx -> ((CompoundTag)rawx).getUUID("Player").equals(id));
                        int role = p.getInt("Role");
                        if (role >= 0 && ms.size() >= 32) {
                           return "member_limit";
                        } else {
                           if (role >= 0) {
                              CompoundTag m = new CompoundTag();
                              m.putUUID("Player", id);
                              m.putString("Nickname", profile.get().getName());
                              m.putInt("Role", Math.min(role, 2));
                              ms.add(m);
                           }

                           this.log(n, "access", player.getGameProfile().getName(), nick, now);
                           return "";
                        }
                     }
                  }
               } else if (!action.equals("rename") && !action.equals("unbind")) {
                  if (action.equals("save")) {
                     return this.saveTask(player, n, p, now);
                  } else if (action.equals("select_relay")) {
                     if (allowed(n, player.getUUID(), 2) && p.hasUUID("Task") && find(tasks(n), p.getUUID("Task")) != null) {
                        ItemStack stack = player.getMainHandItem().is((Item)BFItems.JAGM_CAMERA_TABLET.get()) ? player.getMainHandItem() : player.getOffhandItem();
                        NbtCompat.update(stack, t -> t.putUUID("CommandNetwork", n.getUUID("Id")));
                        NbtCompat.update(stack, t -> t.putUUID("CommandTask", p.getUUID("Task")));
                        return "";
                     } else {
                        return "task";
                     }
                  } else {
                     CompoundTag t = p.hasUUID("Task") ? find(tasks(n), p.getUUID("Task")) : null;
                     if (t == null) {
                        return "task";
                     } else if (action.equals("delete")) {
                        if (active(t)) {
                           return "busy";
                        } else {
                           tasks(n).remove(t);
                           this.setDirty();
                           return "";
                        }
                     } else if (action.equals("arm")) {
                        return this.arm(player, n, t, now);
                     } else if (action.equals("disarm")) {
                        this.stop(player.getServer(), n, t, "DRAFT", "");
                        this.log(n, "disarm", player.getGameProfile().getName(), t.getString("Name"), now);
                        return "";
                     } else {
                        return action.equals("launch")
                           ? this.trigger(
                              player.getServer(),
                              n,
                              t,
                              now,
                              p.contains("Delay") ? p.getLong("Delay") : t.getInt("LaunchDelay"),
                              player.getGameProfile().getName()
                           )
                           : "action";
                     }
                  }
               } else {
                  CompoundTag u = p.hasUUID("Unit") ? find(units(n), p.getUUID("Unit")) : null;
                  if (u == null) {
                     return "unit";
                  } else {
                     for (Tag rawx : tasks(n)) {
                        CompoundTag task = (CompoundTag)rawx;
                        if (active(task) && containsUnit(task, u.getUUID("Id"))) {
                           return "busy";
                        }
                     }

                     if (action.equals("unbind")) {
                        units(n).remove(u);
                     } else {
                        u.putString("Name", clean(p.getString("Name"), 32));
                        u.putString("Group", clean(p.getString("Group"), 24));
                     }

                     this.log(n, action, player.getGameProfile().getName(), u.getString("Name"), now);
                     return "";
                  }
               }
            }
         }
      }
   }

   private static boolean containsUnit(CompoundTag t, UUID unit) {
      for (Tag r : list(t, "Assignments")) {
         if (((CompoundTag)r).getUUID("Unit").equals(unit)) {
            return true;
         }
      }

      return false;
   }

   private String saveTask(ServerPlayer player, CompoundTag n, CompoundTag p, long now) {
      CompoundTag t = p.hasUUID("Task") ? find(tasks(n), p.getUUID("Task")) : null;
      if (t != null && active(t)) {
         return "busy";
      } else if (t == null && tasks(n).size() >= 16) {
         return "task_limit";
      } else {
         ListTag route = p.getList("Route", 10);
         ListTag assignments = p.getList("Assignments", 10);
         if (!route.isEmpty() && route.size() <= 32 && !assignments.isEmpty() && assignments.size() <= (Integer)DefenseRuntime.MAX_COMMAND_DUTY.get()) {
            for (Tag raw : route) {
               CompoundTag point = (CompoundTag)raw;
               double x = point.getDouble("X");
               double y = point.getDouble("Y");
               double z = point.getDouble("Z");
               double s = point.getDouble("Speed");
               if (!Double.isFinite(x)
                  || !Double.isFinite(y)
                  || !Double.isFinite(z)
                  || !Double.isFinite(s)
                  || Math.abs(x) > 2.9999984E7
                  || Math.abs(z) > 2.9999984E7
                  || s <= 0.0
                  || s > 15000.0) {
                  return "route";
               }
            }

            Set<UUID> seen = new HashSet<>();
            String dim = null;
            int total = 0;

            for (Tag rawx : assignments) {
               CompoundTag a = (CompoundTag)rawx;
               if (a.hasUUID("Unit") && seen.add(a.getUUID("Unit"))) {
                  CompoundTag u = find(units(n), a.getUUID("Unit"));
                  int count = a.getInt("Count");
                  if (u != null && count >= 1 && count <= 6) {
                     if (dim == null) {
                        dim = u.getString("Dimension");
                     } else if (!dim.equals(u.getString("Dimension"))) {
                        return "dimension";
                     }

                     ServerLevel level = dimension(player.getServer(), u);
                     if (level == null) {
                        return "dimension";
                     }

                     for (Tag rp : route) {
                        double y = ((CompoundTag)rp).getDouble("Y");
                        if (y < level.getMinBuildHeight() + 2 || y > level.getMaxBuildHeight() - 2) {
                           return "height";
                        }
                     }

                     total += count;
                     continue;
                  }

                  return "unit";
               }

               return "unit";
            }

            if (total > (Integer)DefenseRuntime.MAX_COMMAND_SALVO.get()) {
               return "salvo_limit";
            } else {
               if (t == null) {
                  t = new CompoundTag();
                  t.putUUID("Id", UUID.randomUUID());
                  tasks(n).add(t);
               }

               t.putString("Name", clean(p.getString("Name"), 32));
               t.put("Route", route.copy());
               t.put("Assignments", assignments.copy());
               t.putString("State", "DRAFT");
               t.putBoolean("Ballistic", p.getBoolean("Ballistic"));
               String frequency = p.getString("Frequency");
               if (!RadioFrequency.valid(frequency)) {
                  frequency = "";
               }

               t.putString("Frequency", frequency);
               t.putInt("LaunchDelay", Math.max(20, Math.min(72000, p.getInt("LaunchDelay"))));
               t.putBoolean("ReadyOnly", p.getBoolean("ReadyOnly"));
               t.putInt("WaveInterval", Math.max(120, Math.min(1200, p.getInt("WaveInterval"))));
               t.putBoolean("Redstone", p.getBoolean("Redstone"));
               t.putBoolean("Ripple", p.getBoolean("Ripple"));
               t.putString("Error", "");
               this.log(n, "task", player.getGameProfile().getName(), t.getString("Name"), now);
               return "";
            }
         } else {
            return "route";
         }
      }
   }

   private String arm(ServerPlayer actor, CompoundTag n, CompoundTag t, long now) {
      if (active(t)) {
         return "busy";
      } else {
         int used = 0;

         for (Tag raw : tasks(n)) {
            CompoundTag other = (CompoundTag)raw;
            if (active(other)) {
               used += list(other, "Assignments").size();
            }
         }

         if (used + list(t, "Assignments").size() > (Integer)DefenseRuntime.MAX_COMMAND_DUTY.get()) {
            return "duty_limit";
         } else {
            for (Tag rawx : list(t, "Assignments")) {
               CompoundTag a = (CompoundTag)rawx;

               for (Tag tr : tasks(n)) {
                  CompoundTag other = (CompoundTag)tr;
                  if (active(other) && containsUnit(other, a.getUUID("Unit"))) {
                     return "busy";
                  }
               }
            }

            t.putString("State", "PREPARING");
            t.putLong("Since", now);
            t.putString("Error", "");
            t.putUUID("Operator", actor.getUUID());
            t.putString("OperatorName", actor.getGameProfile().getName());
            t.putBoolean("Resume", false);

            for (Tag rawx : list(t, "Assignments")) {
               CompoundTag a = (CompoundTag)rawx;
               a.putInt("Remaining", a.getInt("Count"));
               a.putInt("Launched", 0);
               a.remove("Mission");
               a.remove("Error");
               a.remove("ErrorDetail");
               a.remove("Next");
               a.remove("NextStart");
               a.putString("Phase", "WAITING");
            }

            this.log(n, "duty", actor.getGameProfile().getName(), t.getString("Name"), now);
            return "";
         }
      }
   }

   private String trigger(MinecraftServer server, CompoundTag n, CompoundTag t, long now, long delay, String who) {
      if (!t.getString("State").equals("DUTY")) {
         return "not_ready";
      } else {
         boolean any = false;

         for (Tag raw : list(t, "Assignments")) {
            CompoundTag a = (CompoundTag)raw;
            CompoundTag u = find(units(n), a.getUUID("Unit"));
            LauncherBlockEntity be = u == null ? null : launcher(server, u);
            if (a.getString("Phase").equals("READY") && be != null && be.commandReady(n.getUUID("Id")) && be.commandExitClear()) {
               any = true;
            } else {
               if (!t.getBoolean("ReadyOnly")) {
                  return "not_ready";
               }

               a.putString("Phase", "EXCLUDED");
               if (be != null) {
                  be.cancelCommand(n.getUUID("Id"));
               }

               a.putString("Error", "not_ready");
            }
         }

         if (!any) {
            return "not_ready";
         } else {
            t.putString("State", "COUNTDOWN");
            t.putLong("StartAt", now + Math.max(20L, Math.min(72000L, delay)));
            t.putBoolean("Released", false);
            int index = 0;

            for (Tag rawx : list(t, "Assignments")) {
               CompoundTag a = (CompoundTag)rawx;
               if (a.getString("Phase").equals("READY")) {
                  a.putLong("NextStart", t.getLong("StartAt") + (t.getBoolean("Ripple") ? (long)index * t.getInt("WaveInterval") : 0L));
                  index++;
               }
            }

            this.log(n, "launch", who, t.getString("Name"), now);
            return "";
         }
      }
   }

   public boolean redstone(ServerLevel level, UUID unit) {
      for (CompoundTag n : this.networks.values()) {
         for (Tag raw : tasks(n)) {
            CompoundTag t = (CompoundTag)raw;
            if (t.getBoolean("Redstone") && active(t) && containsUnit(t, unit)) {
               if (list(t, "Assignments").size() != 1) {
                  return false;
               }

               return this.trigger(level.getServer(), n, t, level.getGameTime(), 20L, "REDSTONE").isEmpty();
            }
         }
      }

      return false;
   }

   public boolean relay(ServerLevel level, UUID network, UUID task, BlockPos source) {
      CompoundTag n = this.networks.get(network);
      CompoundTag t = n == null ? null : find(tasks(n), task);
      return t != null
         && t.contains("RelayPosition")
         && t.getLong("RelayPosition") == source.asLong()
         && t.getString("RelayDimension").equals(level.dimension().location().toString())
         && t.getBoolean("Redstone")
         && this.trigger(level.getServer(), n, t, level.getGameTime(), 20L, "RELAY").isEmpty();
   }

   public int relayOutput(UUID network, UUID task) {
      CompoundTag n = this.networks.get(network);
      CompoundTag t = n != null && task != null ? find(tasks(n), task) : null;
      return t == null ? 0 : output(t);
   }

   public int unitOutput(UUID unit) {
      for (CompoundTag n : this.networks.values()) {
         for (Tag raw : tasks(n)) {
            CompoundTag t = (CompoundTag)raw;
            if (active(t) && containsUnit(t, unit)) {
               return output(t);
            }
         }
      }

      return 0;
   }

   private static int output(CompoundTag t) {
      String var1 = t.getString("State");

      return switch (var1) {
         case "ERROR" -> 1;
         case "PREPARING", "RECOVERING" -> 5;
         case "DUTY" -> 15;
         case "COUNTDOWN", "RUNNING" -> 10;
         default -> 0;
      };
   }

   public void rememberRelay(ServerPlayer p, UUID network, UUID task, BlockPos position) {
      if (this.bindRelay(p, network, task)) {
         CompoundTag t = find(tasks(this.networks.get(network)), task);
         t.putLong("RelayPosition", position.asLong());
         t.putString("RelayDimension", p.level().dimension().location().toString());
         this.setDirty();
      }
   }

   public boolean bindRelay(ServerPlayer p, UUID network, UUID task) {
      CompoundTag n = this.networks.get(network);
      return n != null && allowed(n, p.getUUID(), 2) && find(tasks(n), task) != null;
   }

   private void stop(MinecraftServer server, CompoundTag n, CompoundTag t, String state, String error) {
      for (Tag raw : list(t, "Assignments")) {
         CompoundTag a = (CompoundTag)raw;
         CompoundTag u = find(units(n), a.getUUID("Unit"));
         LauncherBlockEntity be = u == null ? null : launcher(server, u);
         if (be != null) {
            be.cancelCommand(n.getUUID("Id"));
         }
      }

      t.putString("State", state);
      t.putString("Error", error);
      t.remove("StartAt");
      this.setDirty();
   }

   private static List<RoutePoint> route(CompoundTag t) {
      List<RoutePoint> points = new ArrayList<>();

      for (Tag raw : t.getList("Route", 10)) {
         CompoundTag p = (CompoundTag)raw;
         points.add(new RoutePoint(p.getDouble("X"), p.getDouble("Y"), p.getDouble("Z"), p.getDouble("Speed")));
      }

      return points;
   }

   private void tick(MinecraftServer server) {
      long now = server.overworld().getGameTime();
      Set<String> wanted = new HashSet<>();
      int held = 0;

      for (CompoundTag n : this.networks.values()) {
         for (Tag tr : tasks(n)) {
            CompoundTag t = (CompoundTag)tr;
            if (active(t)) {
               if (t.getString("State").equals("RECOVERING")) {
                  t.putString("State", "PREPARING");
                  t.putLong("Since", now);

                  for (Tag raw : list(t, "Assignments")) {
                     CompoundTag a = (CompoundTag)raw;
                     if (a.getInt("Remaining") <= 0) {
                        a.putString("Phase", "DONE");
                     } else {
                        CompoundTag unit = find(units(n), a.getUUID("Unit"));
                        ServerLevel level = unit == null ? null : dimension(server, unit);
                        if (a.hasUUID("Mission") && level != null && ActiveFlights.get(level).wasLaunched(a.getUUID("Mission"))) {
                           a.putInt("Remaining", Math.max(0, a.getInt("Remaining") - 1));
                           a.putInt("Launched", a.getInt("Launched") + 1);
                        }

                        a.putString("Phase", a.getInt("Remaining") > 0 ? "WAITING" : "DONE");
                        a.remove("Mission");
                     }
                  }

                  this.setDirty();
               }

               if (t.contains("RelayPosition") && t.getBoolean("Redstone")) {
                  CompoundTag tag = new CompoundTag();
                  tag.putString("Dimension", t.getString("RelayDimension"));
                  ServerLevel level = dimension(server, tag);
                  ChunkPos cp = new ChunkPos(BlockPos.of(t.getLong("RelayPosition")));
                  String key = n.getUUID("Id") + ":relay:" + t.getString("RelayDimension") + ":" + cp.toLong();
                  if (level != null && (wanted.contains(key) || held < (Integer)DefenseRuntime.MAX_COMMAND_CHUNKS.get())) {
                     if (wanted.add(key)) {
                        held++;
                     }

                     if (!this.leases.containsKey(key)) {
                        level.getChunkSource().addRegionTicket(TICKET, cp, 2, key);
                        this.leases.put(key, new CommandCenterData.Lease(level, cp, key));
                     }
                  }
               }

               for (Tag rawx : list(t, "Assignments")) {
                  CompoundTag a = (CompoundTag)rawx;
                  if (!Set.of("DONE", "EXCLUDED", "ERROR").contains(a.getString("Phase"))) {
                     CompoundTag u = find(units(n), a.getUUID("Unit"));
                     if (u != null) {
                        ServerLevel level = dimension(server, u);
                        if (level != null) {
                           ChunkPos cp = new ChunkPos(BlockPos.of(u.getLong("Position")));
                           String key = n.getUUID("Id") + ":" + u.getString("Dimension") + ":" + cp.toLong();
                           if (!wanted.contains(key) && held >= (Integer)DefenseRuntime.MAX_COMMAND_CHUNKS.get()) {
                              a.putString("Error", "chunk_limit");
                              a.putString("Phase", "ERROR");
                           } else {
                              if (wanted.add(key)) {
                                 held++;
                              }

                              if (!this.leases.containsKey(key)) {
                                 level.getChunkSource().addRegionTicket(TICKET, cp, 2, key);
                                 this.leases.put(key, new CommandCenterData.Lease(level, cp, key));
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      Iterator<Entry<String, CommandCenterData.Lease>> it = this.leases.entrySet().iterator();

      while (it.hasNext()) {
         Entry<String, CommandCenterData.Lease> e = it.next();
         if (!wanted.contains(e.getKey())) {
            CommandCenterData.Lease l = e.getValue();
            l.level.getChunkSource().removeRegionTicket(TICKET, l.chunk, 2, l.key);
            it.remove();
         }
      }

      for (CompoundTag n : this.networks.values()) {
         for (Tag rawxx : units(n)) {
            CompoundTag u = (CompoundTag)rawxx;
            LauncherBlockEntity be = launcher(server, u);
            if (be != null) {
               u.putInt("Ammo", be.loadCount());
               ListTag slots = new ListTag();

               for (int i = 0; i < be.capacity(); i++) {
                  CompoundTag slot = new CompoundTag();
                  slot.putBoolean("Loaded", !be.slot(i).isEmpty());
                  if (!be.slot(i).isEmpty()) {
                     slot.putString("Name", Serializer.toJson(be.slot(i).getHoverName(), net.minecraft.core.RegistryAccess.EMPTY));
                  }

                  slots.add(slot);
               }

               u.put("Slots", slots);
               u.putLong("Seen", now);
               u.putString("State", be.commandBusy() ? "BUSY" : "IDLE");
               u.putString("Missile", be.loaded().getItem() instanceof MissileItem item ? item.kind().id : "");
            } else {
               u.putString("State", "OFFLINE");
            }
         }

         for (Tag rawxxx : tasks(n)) {
            CompoundTag t = (CompoundTag)rawxxx;
            if (active(t)) {
               this.step(server, n, t, now);
            }
         }

         if (now % 20L == 0L) {
            for (Tag rawxxxx : list(n, "Flights")) {
               CompoundTag f = (CompoundTag)rawxxxx;
               if (!f.getBoolean("ResultLogged")) {
                  ServerLevel level = dimension(server, f);
                  if (level != null) {
                     CompoundTag result = ActiveFlights.get(level).report(f.getUUID("Mission"));
                     if (result.hasUUID("FlightId") && result.contains("Outcome")) {
                        f.putBoolean("ResultLogged", true);
                        this.log(n, "flight", f.getString("Name"), result.getString("Outcome"), now);
                     }
                  }
               }
            }
         }
      }

      if (now % 20L == 0L) {
         this.setDirty();
      }
   }

   private void step(MinecraftServer server, CompoundTag n, CompoundTag t, long now) {
      if (t.hasUUID("Operator") && allowed(n, t.getUUID("Operator"), 1)) {
         String state = t.getString("State");
         boolean allReady = true;
         boolean allDone = true;
         boolean failed = false;

         for (Tag raw : list(t, "Assignments")) {
            CompoundTag a = (CompoundTag)raw;
            String phase = a.getString("Phase");
            if (Set.of("EXCLUDED", "ERROR").contains(phase)) {
               failed |= phase.equals("ERROR");
               CompoundTag unit = find(units(n), a.getUUID("Unit"));
               LauncherBlockEntity dead = unit == null ? null : launcher(server, unit);
               if (dead != null) {
                  dead.cancelCommand(n.getUUID("Id"));
               }
            } else if (!phase.equals("DONE")) {
               allDone = false;
               CompoundTag u = find(units(n), a.getUUID("Unit"));
               LauncherBlockEntity be = u == null ? null : launcher(server, u);
               if (be == null) {
                  allReady = false;
                  if (now - t.getLong("Since") >= 100L) {
                     a.putString("Phase", "ERROR");
                     a.putString("Error", "offline");
                     failed = true;
                  }
               } else {
                  UUID mission = a.hasUUID("Mission") ? a.getUUID("Mission") : null;
                  if (mission != null && ActiveFlights.get((ServerLevel)be.getLevel()).wasLaunched(mission)) {
                     a.putInt("Remaining", Math.max(0, a.getInt("Remaining") - 1));
                     a.putInt("Launched", a.getInt("Launched") + 1);
                     a.remove("Mission");
                     if (a.getInt("Remaining") == 0) {
                        be.cancelCommand(n.getUUID("Id"));
                        a.putString("Phase", "DONE");
                        continue;
                     }

                     a.putString("Phase", "WAITING");
                     a.putLong("Next", now);
                     a.putLong("NextStart", now + t.getInt("WaveInterval"));
                     phase = "WAITING";
                     mission = null;
                     this.setDirty();
                  }

                  if (phase.equals("WAITING")) {
                     if (now < a.getLong("Next")) {
                        allReady = false;
                        continue;
                     }

                     if (be.loadCount() < a.getInt("Remaining")) {
                        a.putString("Phase", "ERROR");
                        a.putString("Error", "ammo");
                        failed = true;
                        allReady = false;
                        continue;
                     }

                     if (!t.hasUUID("Operator")) {
                        a.putString("Phase", "ERROR");
                        a.putString("Error", "access");
                        failed = true;
                        continue;
                     }

                     UUID operator = t.getUUID("Operator");
                     if (!allowed(n, operator, 1)) {
                        this.stop(server, n, t, "ERROR", "access");
                        return;
                     }

                     FakePlayer actor = FakePlayerFactory.get((ServerLevel)be.getLevel(), new GameProfile(operator, t.getString("OperatorName")));
                     UUID next = UUID.randomUUID();
                     if (!be.prepareCommand(n.getUUID("Id"), actor, route(t), t.getBoolean("Ballistic"), t.getString("Frequency"), next)) {
                        a.putString("Phase", "ERROR");
                        a.putString("Error", "launcher");
                        a.putString("ErrorDetail", Serializer.toJson(be.getLastFireErrorComponent(), net.minecraft.core.RegistryAccess.EMPTY));
                        failed = true;
                        allReady = false;
                        continue;
                     }

                     CompoundTag record = new CompoundTag();
                     record.putUUID("Mission", next);
                     record.putUUID("Task", t.getUUID("Id"));
                     record.putUUID("Unit", u.getUUID("Id"));
                     record.putString("Dimension", u.getString("Dimension"));
                     record.putString("Name", u.getString("Name"));
                     RoutePoint destination = route(t).get(route(t).size() - 1);
                     record.putDouble("TargetX", destination.x());
                     record.putDouble("TargetY", destination.y());
                     record.putDouble("TargetZ", destination.z());
                     ListTag records = list(n, "Flights");
                     records.add(record);

                     while (records.size() > 128) {
                        records.remove(0);
                     }

                     a.putUUID("Mission", next);
                     a.putString("Phase", "PREPARING");
                     a.putLong("Since", now);
                     phase = "PREPARING";
                     this.setDirty();
                  }

                  if (phase.equals("PREPARING")) {
                     if (!be.commandReady(n.getUUID("Id"))) {
                        allReady = false;
                        if (now - a.getLong("Since") > 100L) {
                           a.putString("Phase", "ERROR");
                           a.putString("Error", "timeout");
                           failed = true;
                        }
                        continue;
                     }

                     String issue = be.commandExitIssue();
                     if (!issue.isEmpty()) {
                        allReady = false;
                        if (!issue.equals("terrain") || now - a.getLong("Since") > 100L) {
                           a.putString("Phase", "ERROR");
                           a.putString("Error", issue);
                           failed = true;
                        }
                        continue;
                     }

                     a.putString("Phase", "READY");
                     phase = "READY";
                     this.setDirty();
                  }

                  if (phase.equals("READY") && !be.commandReady(n.getUUID("Id"))) {
                     a.putString("Phase", "ERROR");
                     a.putString("Error", "not_ready");
                     failed = true;
                     allReady = false;
                  } else {
                     if ((state.equals("RUNNING") || state.equals("COUNTDOWN")) && phase.equals("READY")) {
                        long start = a.getLong("NextStart");
                        if (now < start - 20L) {
                           allReady = false;
                           continue;
                        }

                        if (!be.releaseCommand(n.getUUID("Id"), Math.max(now + 20L, start))) {
                           a.putString("Phase", "ERROR");
                           a.putString("Error", "not_ready");
                           failed = true;
                        } else {
                           a.putString("Phase", "LAUNCHING");
                        }
                     }

                     if (!phase.equals("READY")) {
                        allReady = false;
                     }

                     if (phase.equals("LAUNCHING") && !be.commandBusy()) {
                        a.putString("Phase", "ERROR");
                        a.putString("Error", "launch_failed");
                        failed = true;
                     }
                  }
               }
            }
         }

         if (failed && !t.getBoolean("ReadyOnly")) {
            this.stop(server, n, t, "ERROR", "participant_failed");
            this.log(n, "error", "", t.getString("Name"), now);
         } else if (allDone && state.equals("PREPARING") && !failed) {
            this.stop(server, n, t, "DONE", "");
         } else if (allDone && state.equals("DUTY")) {
            this.stop(server, n, t, "ERROR", "not_ready");
         } else {
            if (state.equals("PREPARING")) {
               boolean pending = false;
               boolean ready = false;

               for (Tag rawx : list(t, "Assignments")) {
                  String phase = ((CompoundTag)rawx).getString("Phase");
                  pending |= Set.of("WAITING", "PREPARING").contains(phase);
                  ready |= phase.equals("READY");
               }

               if (!pending) {
                  if (!ready) {
                     this.stop(server, n, t, "ERROR", "not_ready");
                     return;
                  }

                  t.putString("State", "DUTY");
                  this.setDirty();
               }
            }

            if (state.equals("COUNTDOWN") && now >= t.getLong("StartAt")) {
               t.putString("State", "RUNNING");
               this.setDirty();
            }

            if (allDone && (state.equals("RUNNING") || state.equals("COUNTDOWN"))) {
               this.stop(server, n, t, "DONE", failed ? "partial" : "");
               this.log(n, "done", "", t.getString("Name"), now);
            }
         }
      } else {
         this.stop(server, n, t, "ERROR", "access");
      }
   }

   public CompoundTag snapshot(ServerPlayer player) {
      CompoundTag result = new CompoundTag();
      CompoundTag n = this.session(player);
      result.putString("Player", player.getGameProfile().getName());
      result.putLong("Now", player.serverLevel().getGameTime());
      if (n != null && allowed(n, player.getUUID(), 0)) {
         result.put("Defense", CommandDefense.snapshot(player, n));
         CompoundTag publicNetwork = n.copy();
         publicNetwork.remove("Flights");
         result.put("Network", publicNetwork);
         ListTag telemetry = new ListTag();

         for (Tag raw : list(n, "Flights")) {
            CompoundTag r = (CompoundTag)raw;
            ServerLevel level = dimension(player.getServer(), r);
            if (level != null
               && level == player.serverLevel()
               && level.getEntity(r.getUUID("Mission")) instanceof GuidedMissileEntity missile
               && missile.isAlive()) {
               CompoundTag f = new CompoundTag();
               f.putUUID("Mission", missile.getUUID());
               if (r.hasUUID("Task")) {
                  f.putUUID("Task", r.getUUID("Task"));
               }

               f.putDouble("TargetX", r.getDouble("TargetX"));
               f.putDouble("TargetZ", r.getDouble("TargetZ"));
               f.putInt("Entity", missile.getId());
               f.putString("Name", r.getString("Name"));
               f.putString("Model", missile.kind().id);
               f.putDouble("X", missile.getX());
               f.putDouble("Y", missile.getY());
               f.putDouble("Z", missile.getZ());
               f.putDouble("Speed", FlightUnits.kilometresPerHour(missile.getDeltaMovement().length()));
               double speed = missile.getDeltaMovement().length() * 20.0;
               f.putInt(
                  "Eta",
                  r.contains("TargetX") && speed > 0.01
                     ? (int)Math.ceil(missile.position().distanceTo(new Vec3(r.getDouble("TargetX"), r.getDouble("TargetY"), r.getDouble("TargetZ"))) / speed)
                     : -1
               );
               f.putBoolean("Camera", missile.cameraAvailable());
               telemetry.add(f);
            }
         }

         result.put("Flights", telemetry);
         result.putInt("Role", allowed(n, player.getUUID(), 2) ? 2 : (allowed(n, player.getUUID(), 1) ? 1 : 0));
      }

      return result;
   }

   public static void tick(ServerTickEvent event) {
      if (event.phase == Phase.END) {
         get(event.getServer().overworld()).tick(event.getServer());
      }
   }

   private record Lease(ServerLevel level, ChunkPos chunk, String key) {
   }
}
