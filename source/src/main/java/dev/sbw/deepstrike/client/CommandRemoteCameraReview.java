package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.sbw.deepstrike.game.CameraTerrainServer;
import dev.sbw.deepstrike.game.CommandCenterData;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;

final class CommandRemoteCameraReview {
   private static int step;
   private static int age;
   private static volatile boolean seeded;
   private static volatile Throwable failure;
   private static UUID task;
   private static UUID mission;
   private static final BlockPos PAD = new BlockPos(0, 150, 0);
   private static String report;
   private static boolean captured;
   private static volatile boolean released;

   private static Path dir(Minecraft mc) {
      return mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
   }

   private static void next() {
      step++;
      age = 0;
   }

   static void tick(Minecraft mc) {
      if (failure != null) {
         throw new IllegalStateException("Remote camera server failure", failure);
      } else if (++age > 1200) {
         throw new IllegalStateException("Remote camera review timed out step=" + step + " state=" + CommandTabletScreen.latestSnapshot);
      } else {
         if (step == 0) {
            if (seeded) {
               next();
               return;
            }

            if (age != 1) {
               return;
            }

            mc.options.pauseOnLostFocus = false;
            mc.options.renderDistance().set(2);
            mc.options.simulationDistance().set(2);
            mc.getSingleplayerServer().execute(() -> {
               try {
                  IntegratedServer server = mc.getSingleplayerServer();
                  ServerPlayer p = (ServerPlayer)server.getPlayerList().getPlayers().get(0);
                  ServerLevel level = p.serverLevel();
                  server.getPlayerList().setViewDistance(2);
                  server.getPlayerList().setSimulationDistance(2);
                  p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)BFItems.JAGM_CAMERA_TABLET.get()));
                  p.setNoGravity(true);
                  p.setInvulnerable(true);

                  for (int x = 0; x <= 48; x++) {
                     for (int z = -8; z <= 8; z++) {
                        level.setBlock(new BlockPos(x, 149, z), Blocks.YELLOW_CONCRETE.defaultBlockState(), 2);
                     }
                  }

                  level.setBlockAndUpdate(PAD, (BlockState)((LauncherBlock)BFBlocks.FP5_LAUNCHER.get()).defaultBlockState().setValue(LauncherBlock.FACING, Direction.EAST));
                  LauncherBlockEntity be = (LauncherBlockEntity)level.getBlockEntity(PAD);
                  be.placed(p, ItemStack.EMPTY);
                  ItemStack ammo = new ItemStack((ItemLike)BFItems.FP5.get());
                  new MissileData(MissileKind.FP5, "none", "turbojet", List.of("camera"), "olive", 1000, 1000).writeToItemStack(ammo);
                  be.setItem(0, ammo);
                  p.setPos(Vec3.atCenterOf(PAD));
                  CommandCenterData data = CommandCenterData.get(level);
                  String bind = data.bind(p, be);
                  if (!bind.isEmpty()) {
                     throw new IllegalStateException(bind);
                  }

                  CompoundTag request = new CompoundTag();
                  request.putString("Name", "Remote camera");
                  request.putInt("WaveInterval", 120);
                  request.putInt("LaunchDelay", 20);
                  ListTag assignments = new ListTag();
                  CompoundTag a = new CompoundTag();
                  a.putUUID("Unit", be.journalId());
                  a.putInt("Count", 1);
                  assignments.add(a);
                  request.put("Assignments", assignments);
                  ListTag route = new ListTag();
                  CompoundTag r = new CompoundTag();
                  r.putDouble("X", 1800.0);
                  r.putDouble("Y", 180.0);
                  r.putDouble("Z", 0.0);
                  r.putDouble("Speed", 850.0);
                  route.add(r);
                  request.put("Route", route);
                  String saved = data.action(p, "save", request);
                  if (!saved.isEmpty()) {
                     throw new IllegalStateException(saved);
                  }

                  ListTag ts = data.session(p).getList("Tasks", 10);
                  task = ts.getCompound(ts.size() - 1).getUUID("Id");
                  p.teleportTo(6000.0, 170.0, 0.0);
                  seeded = true;
               } catch (Throwable var15x) {
                  failure = var15x;
               }
            });
         } else if (step == 1) {
            if (age == 1) {
               CommandTabletScreen.open();
            }

            if (mc.player.getX() < 5000.0 || age < 50) {
               return;
            }

            CompoundTag p = new CompoundTag();
            p.putUUID("Task", task);
            CommandTabletNetwork.request("arm", p);
            next();
         } else if (step != 2) {
            if (step == 3) {
               boolean live = false;

               for (Tag raw : CommandTabletScreen.latestSnapshot.getList("Flights", 10)) {
                  if (((CompoundTag)raw).getUUID("Mission").equals(mission)) {
                     live = true;
                  }
               }

               if (!live) {
                  return;
               }

               CompoundTag p = new CompoundTag();
               p.putUUID("Mission", mission);
               CommandTabletNetwork.request("camera", p);
               next();
            } else if (step == 4) {
               if (age == 20) {
                  ClientCamera.turn(0.0, 650.0);
                  mc.options.keyUp.setDown(true);
               }

               if (age == 30) {
                  mc.options.keyUp.setDown(false);
               }

               if (age < 30 || mc.screen != null || mc.getCameraEntity() == null) {
                  return;
               }

               Entity camera = mc.getCameraEntity();
               boolean terrain = mc.level.getBlockState(new BlockPos((int)Math.floor(camera.getX()), 149, 0)).is(Blocks.YELLOW_CONCRETE);
               boolean body = Math.abs(mc.player.getX() - 6000.0) < 0.1 && Math.abs(mc.player.getZ()) < 0.1;
               boolean connected = camera.distanceToSqr(mc.player) > 1.6777216E7;
               report = "{\"viewer_x\":"
                  + mc.player.getX()
                  + ",\"camera_x\":"
                  + camera.getX()
                  + ",\"camera_y\":"
                  + camera.getY()
                  + ",\"camera_chunk_resident\":"
                  + terrain
                  + ",\"body_preserved\":"
                  + body
                  + ",\"remote_pose\":"
                  + connected
                  + "}";
               LogUtils.getLogger().info("COMMAND_REMOTE_CAMERA_RESULT {}", report);
               next();
            } else if (step == 5 && captured) {
               try {
                  Files.writeString(dir(mc).resolve("remote-camera.json"), report);
               } catch (Exception var6) {
                  throw new IllegalStateException(var6);
               }

               if (report.contains("\"camera_chunk_resident\":true") && report.contains("\"body_preserved\":true") && report.contains("\"remote_pose\":true")) {
                  LogUtils.getLogger().info("COMMAND_TABLET_NATIVE_PASS remoteCamera=true");
               }

               ClientCamera.restore();
               next();
            } else if (step == 6) {
               if (age < 15) {
                  return;
               }

               if (RemoteCameraTerrain.size() != 0) {
                  throw new IllegalStateException("Remote client chunks retained after camera exit");
               }

               mc.getSingleplayerServer().execute(() -> {
                  if (CameraTerrainServer.viewers() != 0) {
                     failure = new IllegalStateException("Remote server viewer retained after camera exit");
                  } else {
                     released = true;
                  }
               });
               next();
            } else if (step == 7 && released) {
               LogUtils.getLogger().info("COMMAND_REMOTE_CAMERA_RELEASED clientChunks=0 serverViewers=0");
               mc.stop();
            }
         } else {
            String error = CommandTabletScreen.latestSnapshot.getString("Error");
            if (!error.isEmpty()) {
               throw new IllegalStateException(error);
            }

            CompoundTag current = null;

            for (Tag rawx : CommandTabletScreen.latestSnapshot.getCompound("Network").getList("Tasks", 10)) {
               CompoundTag t = (CompoundTag)rawx;
               if (t.getUUID("Id").equals(task)) {
                  current = t;
               }
            }

            if (current == null || !current.getString("State").equals("DUTY")) {
               return;
            }

            mission = current.getList("Assignments", 10).getCompound(0).getUUID("Mission");
            CompoundTag p = new CompoundTag();
            p.putUUID("Task", task);
            CommandTabletNetwork.request("launch", p);
            next();
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (step == 5 && !captured && event.phase == Phase.END) {
         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Files.createDirectories(dir(mc));
               shot.writeToFile(dir(mc).resolve("remote-camera.png"));
               captured = true;
            } catch (Throwable var6) {
               if (shot != null) {
                  try {
                     shot.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (shot != null) {
               shot.close();
            }
         } catch (Exception var7) {
            throw new IllegalStateException(var7);
         }
      }
   }
}
