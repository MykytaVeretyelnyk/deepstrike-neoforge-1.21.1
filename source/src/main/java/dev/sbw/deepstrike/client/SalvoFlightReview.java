package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.CommandCenterData;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;

final class SalvoFlightReview {
   private static volatile boolean queued;
   private static volatile boolean done;
   private static volatile boolean capture;
   private static volatile Throwable failure;
   private static UUID task;
   private static long start = -1L;
   private static long last = -1L;
   private static long ignition = -1L;
   private static final Map<UUID, GuidedMissileEntity> live = new HashMap<>();
   private static final Set<UUID> arrived = new HashSet<>();
   private static final Map<UUID, Vec3> previous = new HashMap<>();
   private static final Map<UUID, Double> distances = new HashMap<>();
   private static final Vec3 TARGET = new Vec3(961.5, 250.0, 820.5);

   private static Path dir(Minecraft mc) {
      return mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
   }

   static void tick(Minecraft mc) {
      if (failure != null) {
         throw new IllegalStateException("Salvo flight review failed", failure);
      } else if (done) {
         LogUtils.getLogger().info("COMMAND_TABLET_NATIVE_PASS salvoArrival=8");
         mc.stop();
      } else if (!queued) {
         queued = true;
         mc.getSingleplayerServer().execute(() -> {
            try {
               server(mc);
            } catch (Throwable var5) {
               failure = var5;
            } finally {
               queued = false;
            }
         });
      }
   }

   private static void require(boolean ok, String reason) {
      if (!ok) {
         throw new IllegalStateException(reason);
      }
   }

   private static void server(Minecraft mc) throws Exception {
      ServerPlayer p = (ServerPlayer)mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
      ServerLevel level = p.serverLevel();
      long now = level.getGameTime();
      if (now != last) {
         last = now;
         CommandCenterData data = CommandCenterData.get(level);
         if (start < 0L) {
            start = now;
            p.setInvulnerable(true);
            p.setNoGravity(true);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)BFItems.JAGM_CAMERA_TABLET.get()));
            int[] xs = new int[]{45, 35, 30, 24, 20, 16, 10, 0};
            int[] zs = new int[]{-2, -1, 1, 3, 6, 8, 14, 21};
            ListTag assignments = new ListTag();

            for (int i = 0; i < 8; i++) {
               BlockPos pad = new BlockPos(xs[i], 250, zs[i]);
               level.setBlockAndUpdate(pad, (BlockState)((LauncherBlock)BFBlocks.FP5_LAUNCHER.get()).defaultBlockState().setValue(LauncherBlock.FACING, Direction.NORTH));
               LauncherBlockEntity be = (LauncherBlockEntity)level.getBlockEntity(pad);
               be.placed(p, ItemStack.EMPTY);
               ItemStack ammo = new ItemStack((ItemLike)BFItems.FP5.get());
               new MissileData(MissileKind.FP5, "none", "turbojet", List.of(), "olive", 1000, 1000).writeToItemStack(ammo);
               be.setItem(0, ammo);
               p.setPos(Vec3.atCenterOf(pad));
               require(data.bind(p, be).isEmpty(), "Bind failed");
               CompoundTag a = new CompoundTag();
               a.putUUID("Unit", be.journalId());
               a.putInt("Count", 1);
               assignments.add(a);
            }

            CompoundTag request = new CompoundTag();
            request.putString("Name", "Reported FP5 reversal");
            request.put("Assignments", assignments);
            request.putInt("LaunchDelay", 20);
            request.putInt("WaveInterval", 120);
            ListTag route = new ListTag();

            for (RoutePoint point : List.of(
               new RoutePoint(26.5, 314.0, -1645.5, 850.0),
               new RoutePoint(712.5, 282.0, 306.5, 1650.0),
               new RoutePoint(TARGET.x, TARGET.y, TARGET.z, 850.0)
            )) {
               CompoundTag r = new CompoundTag();
               r.putDouble("X", point.x());
               r.putDouble("Y", point.y());
               r.putDouble("Z", point.z());
               r.putDouble("Speed", point.speed());
               route.add(r);
            }

            request.put("Route", route);
            require(data.action(p, "save", request).isEmpty(), "Save failed");
            ListTag tasks = data.session(p).getList("Tasks", 10);
            task = tasks.getCompound(tasks.size() - 1).getUUID("Id");
            CompoundTag id = new CompoundTag();
            id.putUUID("Task", task);
            require(data.action(p, "arm", id).isEmpty(), "Arm failed");
            p.teleportTo(26.0, 650.0, -1645.0);
            p.setXRot(90.0F);
            Files.createDirectories(dir(mc));
         } else {
            CompoundTag t = null;

            for (Tag raw : data.session(p).getList("Tasks", 10)) {
               if (((CompoundTag)raw).getUUID("Id").equals(task)) {
                  t = (CompoundTag)raw;
               }
            }

            require(t != null && !t.getString("State").equals("ERROR"), "Task failed: " + t);
            if (t.getString("State").equals("DUTY")) {
               CompoundTag id = new CompoundTag();
               id.putUUID("Task", task);
               require(data.action(p, "launch", id).isEmpty(), "Launch failed");
            }

            for (Tag rawx : data.session(p).getList("Flights", 10)) {
               CompoundTag a = (CompoundTag)rawx;
               if (a.hasUUID("Mission")) {
                  UUID mission = a.getUUID("Mission");
                  CompoundTag report = ActiveFlights.get(level).report(mission);
                  if (report.hasUUID("FlightId")) {
                     long tick = report.getLong("LaunchTick");
                     if (ignition < 0L) {
                        ignition = tick;
                     }

                     require(ignition == tick, "Salvo ignition split across ticks");
                     if (level.getEntity(report.getUUID("FlightId")) instanceof GuidedMissileEntity missile) {
                        live.put(mission, missile);
                     }
                  }
               }
            }

            for (Entry<UUID, GuidedMissileEntity> entry : live.entrySet()) {
               UUID id = entry.getKey();
               if (!arrived.contains(id)) {
                  GuidedMissileEntity missile = entry.getValue();
                  Vec3 at = missile.position();
                  Vec3 before = previous.put(id, at);
                  if (before != null) {
                     distances.merge(id, at.distanceTo(before), Double::sum);
                  }

                  require(distances.getOrDefault(id, 0.0) < 6500.0, "Missile circled: " + id + " at=" + at + " cursor=" + missile.getRouteIndex());
                  if (now % 10L == 0L) {
                     Files.writeString(
                        dir(mc).resolve("flight.csv"),
                        now + "," + id + "," + at.x + "," + at.y + "," + at.z + "," + missile.getRouteIndex() + "\n",
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                     );
                  }

                  if (missile.getRouteIndex() > 290 && !capture) {
                     capture = true;
                  }

                  if (!missile.isAlive()) {
                     double error = missile.impactPosition().distanceTo(TARGET);
                     require(error < 2.0, "Missile missed end: " + error + " at=" + missile.impactPosition());
                     arrived.add(id);
                     LogUtils.getLogger().info("SALVO_ARRIVAL_PASS count={} error={} distance={}", new Object[]{arrived.size(), error, distances.get(id)});
                  }
               }
            }

            require(now - start < 3500L, "Salvo flight timeout live=" + live.size() + " arrived=" + arrived.size());
            if (arrived.size() == 8) {
               Files.writeString(
                  dir(mc).resolve("PASS.txt"),
                  "Eight real FP5 command launches, identical ignition tick, reported reversal route, every final impact <2 blocks, every flight <6500 blocks.\n"
               );
               done = true;
            }
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent e) {
      if (capture && e.phase == Phase.END) {
         try {
            Path p = dir(mc).resolve("reversal.png");
            if (Files.exists(p)) {
               return;
            }

            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Files.createDirectories(dir(mc));
               shot.writeToFile(p);
            } catch (Throwable var7) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var8) {
            failure = var8;
         }
      }
   }
}
