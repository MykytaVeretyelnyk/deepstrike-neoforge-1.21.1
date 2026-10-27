package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileAssemblyBlock;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldVisualReview {
   private static final Logger LOGGER = LoggerFactory.getLogger(WorldVisualReview.class);
   private static int ticks;
   private static final BlockPos JAGM = new BlockPos(0, 245, 0);
   private static final BlockPos FP5 = new BlockPos(0, 245, 9);
   private static volatile boolean prepared;
   private static float originalFov;

   static void tick(ClientTickEvent event) {
      if (event.phase == Phase.END && Boolean.getBoolean("deepstrike.visualCheck")) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
            if (mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-qa")) {
               if (Boolean.getBoolean("deepstrike.stage2Visual")) {
                  StageTwoVisualReview.tick(mc);
               } else if (Boolean.getBoolean("deepstrike.stage1Visual")) {
                  stageOne(mc);
               } else {
                  int t = ++ticks;
                  if (t == 1) {
                     mc.options.guiScale().set(4);
                     mc.resizeDisplay();
                     originalFov = ((Integer)mc.options.fov().get()).intValue();
                     mc.options.fov().set(48);
                     mc.options.hideGui = true;
                     mc.options.pauseOnLostFocus = false;
                     mc.setScreen(null);
                     mc.getSingleplayerServer()
                        .execute(
                           () -> {
                              IntegratedServer server = mc.getSingleplayerServer();
                              ServerLevel level = server.overworld();
                              ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
                              if (player != null) {
                                 for (int x = -9; x <= 30; x++) {
                                    for (int z = -9; z <= 19; z++) {
                                       level.setBlock(new BlockPos(x, 244, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);

                                       for (int y = 245; y <= 252; y++) {
                                          level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                                       }
                                    }
                                 }

                                 level.setDayTime(6000L);
                                 level.setWeatherParameters(6000, 0, false, false);
                                 level.setBlock(
                                    JAGM,
                                    (BlockState)((LauncherBlock)BFBlocks.JAGM_LAUNCHER.get()).defaultBlockState().setValue(LauncherBlock.FACING, Direction.EAST),
                                    3
                                 );
                                 level.setBlock(
                                    FP5,
                                    (BlockState)((LauncherBlock)BFBlocks.FP5_LAUNCHER_GRAPHITE.get()).defaultBlockState().setValue(LauncherBlock.FACING, Direction.EAST),
                                    3
                                 );
                                 player.setGameMode(GameType.SPECTATOR);
                                 player.teleportTo(12.0, 250.0, 13.0);
                                 prepared = true;
                              }
                           }
                        );
                  }

                  if (prepared) {
                     if (t < 380) {
                        if (mc.screen != null) {
                           mc.setScreen(null);
                        }

                        mc.player.setPos(12.0, 250.0, 13.0);
                        Vec3 target = new Vec3(2.0, 247.0, 2.0);
                        Vec3 d = target.subtract(mc.player.getEyePosition());
                        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
                        mc.player.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
                        mc.player.yRotO = mc.player.getYRot();
                        mc.player.xRotO = mc.player.getXRot();
                     }

                     if (t == 45) {
                        action(mc, () -> {
                           ServerLevel level = mc.getSingleplayerServer().overworld();
                           ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                           if (level.getBlockEntity(JAGM) instanceof LauncherBlockEntity be) {
                              for (int ix = 0; ix < 6; ix++) {
                                 be.load(new ItemStack((ItemLike)BFItems.JAGM.get()), player);
                              }

                              be.adjust(0.0F, 30.0F);
                           }

                           if (level.getBlockEntity(FP5) instanceof LauncherBlockEntity be) {
                              be.load(new ItemStack((ItemLike)BFItems.FP5_GRAPHITE.get()), player);
                           }
                        });
                     }

                     if (t == 100) {
                        capture(mc, "world-loaded");
                     }

                     if (t == 130) {
                        action(mc, () -> {
                           ServerLevel level = mc.getSingleplayerServer().overworld();
                           ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                           if (level.getBlockEntity(JAGM) instanceof LauncherBlockEntity be) {
                              be.fire(p, List.of(new RoutePoint(150.0, 290.0, 0.0, 180.0)), false);
                           }
                        });
                     }

                     if (t == 142) {
                        capture(mc, "world-cover-ejected");
                     }

                     if (t == 155) {
                        ClientCamera.restore();
                     }

                     if (t == 157) {
                        capture(mc, "world-missile-emerging");
                     }

                     if (t == 170) {
                        capture(mc, "world-missile-trail");
                     }

                     if (t == 185) {
                        capture(mc, "world-missile-departed");
                     }

                     if (t == 240) {
                        action(mc, () -> {
                           ServerLevel level = mc.getSingleplayerServer().overworld();
                           ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                           if (level.getBlockEntity(FP5) instanceof LauncherBlockEntity be) {
                              be.fire(p, List.of(new RoutePoint(150.0, 285.0, 9.0, 180.0)), false);
                           }
                        });
                     }

                     if (t == 260) {
                        capture(mc, "world-fp5-launch");
                     }

                     if (t == 390) {
                        mc.options.hideGui = false;
                        DeepStrikeScreen.open(JAGM, Direction.EAST, LauncherBlock.Family.JAGM);
                     }

                     if (t == 405) {
                        capture(mc, "world-route-map");
                     }

                     if (t == 410 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(150.0, 110.0, 0);
                        s.mouseReleased(150.0, 110.0, 0);
                        s.mouseClicked(200.0, 145.0, 0);
                        s.mouseReleased(200.0, 145.0, 0);
                        s.mouseClicked(250.0, 175.0, 0);
                        s.mouseReleased(250.0, 175.0, 0);
                     }

                     if (t == 415) {
                        capture(mc, "world-route-points");
                     }

                     if (t == 420 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(28.0, 68.0, 0);
                     }

                     if (t == 425) {
                        capture(mc, "world-route-config");
                     }

                     if (t == 430 && mc.screen instanceof DeepStrikeScreen s) {
                        List<EditBox> fields = s.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox)w).toList();
                        fields.get(0).setValue("123.5");
                        fields.get(1).setValue("42.5");
                        fields.get(2).setValue("-22.5");
                        fields.get(3).setValue("180");
                        s.mouseScrolled(100.0, 180.0, 0.0, -5.0);
                     }

                     if (t == 435 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(100.0, 219.0, 0);
                     }

                     if (t == 440 && mc.screen instanceof DeepStrikeScreen s) {
                        capture(mc, "world-config-edited");
                        System.out.println("BF UI CONFIG RESULT " + s.routeSnapshot());
                     }

                     if (t == 445 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(210.0, 98.0, 0);
                     }

                     if (t == 450 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(110.0, 166.0, 0);
                     }

                     if (t == 455) {
                        capture(mc, "world-presets");
                     }

                     if (t == 460 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(110.0, 289.0, 0);
                     }

                     if (t == 465 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(110.0, 238.0, 0);
                     }

                     if (t == 470 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(110.0, 325.0, 0);
                     }

                     if (t == 475 && mc.screen instanceof DeepStrikeScreen s) {
                        System.out.println("BF UI PRESET ROUNDTRIP " + s.routeSnapshot());
                        s.mouseClicked(300.0, 98.0, 0);
                     }

                     if (t == 480) {
                        capture(mc, "world-journal");
                     }

                     if (t == 485 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(28.0, 68.0, 0);
                        s.mouseClicked(s.width / 2 + 119, s.height / 2, 0);
                     }

                     if (t == 490) {
                        capture(mc, "world-point-editor");
                     }

                     if (t == 495 && mc.screen instanceof RoutePointScreen s) {
                        List<EditBox> fields = s.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox)w).toList();
                        fields.get(1).setValue("-12.5");
                        s.keyPressed(257, 0, 0);
                     }

                     if (t == 500 && mc.screen instanceof DeepStrikeScreen s) {
                        capture(mc, "world-point-negative-height");
                        System.out.println("BF UI NEGATIVE HEIGHT " + s.routeSnapshot());
                     }

                     if (t == 505 && mc.screen instanceof DeepStrikeScreen s) {
                        for (int i = 0; i < 6; i++) {
                           s.mouseScrolled(s.width * 0.6, s.height * 0.35, 0.0, 1.0);
                        }
                     }

                     if (t == 512) {
                        capture(mc, "world-map-block-zoom");
                     }

                     if (t == 520) {
                        mc.options.guiScale().set(6);
                        mc.resizeDisplay();
                     }

                     if (t == 525 && mc.screen instanceof DeepStrikeScreen s) {
                        s.mouseClicked(28.0, 68.0, 0);
                        s.mouseClicked(105.0, 98.0, 0);
                     }

                     if (t == 532) {
                        capture(mc, "world-config-small-screen");
                     }

                     if (t == 540) {
                        mc.options.guiScale().set(4);
                        mc.resizeDisplay();
                     }

                     if (t == 365) {
                        capture(mc, "world-cover-expired");
                        mc.options.hideGui = false;
                        mc.options.fov().set((int)originalFov);
                     }
                  }
               }
            }
         }
      }
   }

   private static void stageOne(Minecraft mc) {
      if (ticks == 0) {
         ticks = 1;
         mc.options.guiScale().set(3);
         mc.resizeDisplay();
         mc.options.hideGui = false;
         mc.options.pauseOnLostFocus = false;
         mc.setScreen(null);
         action(mc, () -> {
            ServerLevel level = mc.getSingleplayerServer().overworld();
            ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if (player != null) {
               for (int x = -8; x <= 15; x++) {
                  for (int z = -8; z <= 15; z++) {
                     level.setBlock(new BlockPos(x, 244, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);

                     for (int y = 245; y <= 253; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                     }
                  }
               }

               level.setDayTime(6000L);
               level.setWeatherParameters(6000, 0, false, false);
               level.setBlock(JAGM, (BlockState)((LauncherBlock)BFBlocks.JAGM_LAUNCHER.get()).defaultBlockState().setValue(LauncherBlock.FACING, Direction.EAST), 3);
               level.setBlock(new BlockPos(5, 245, 5), ((MissileAssemblyBlock)BFBlocks.MISSILE_ASSEMBLY_STATION.get()).defaultBlockState(), 3);
               if (level.getBlockEntity(JAGM) instanceof LauncherBlockEntity be) {
                  be.load(new ItemStack((ItemLike)BFItems.JAGM.get()), player);
                  be.load(new ItemStack((ItemLike)BFItems.JAGM.get()), player);
               }

               player.setGameMode(GameType.SURVIVAL);
               player.teleportTo(10.0, 245.0, 10.0);
               prepared = true;
            }
         });
      } else if (prepared) {
         int t = ++ticks;
         if (t < 155) {
            Vec3 d = new Vec3(2.0, 247.0, 2.0).subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            mc.player.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
            mc.player.yRotO = mc.player.getYRot();
            mc.player.xRotO = mc.player.getXRot();
         }

         if (t == 35) {
            capture(mc, "stage1-survival-world-assembly-and-launcher");
         }

         if (t == 45) {
            DeepStrikeMapScreen.open(JAGM, Direction.EAST, LauncherBlock.Family.JAGM);
         }

         if (t == 55) {
            capture(mc, "stage1-xaero-map-before-launch");
         }

         if (t == 60) {
            LOGGER.info("STAGE1_CLIENT_LAUNCH_SCREEN={}", mc.screen == null ? "null" : mc.screen.getClass().getName());
            DeepStrikeNetwork.LaunchMission command = new DeepStrikeNetwork.LaunchMission(
               JAGM, false, List.of(new RoutePoint(150.0, 290.0, 0.0, 180.0)), "00123"
            );
            DeepStrikeNetwork.CHANNEL.sendToServer(command);
            DeepStrikeNetwork.CHANNEL.sendToServer(command);
         }

         if (t == 75) {
            LOGGER.info("STAGE1_CLIENT_MAP_CLOSED={}", mc.screen == null);
            capture(mc, "stage1-survival-countdown-early");
         }

         if (t == 115) {
            capture(mc, "stage1-survival-countdown-middle");
         }

         if (t == 155) {
            capture(mc, "stage1-survival-countdown-final");
         }

         if (t == 180) {
            ClientCamera.restore();
            capture(mc, "stage1-survival-after-launch");
            action(mc, () -> {
               ServerLevel level = mc.getSingleplayerServer().overworld();
               if (level.getBlockEntity(JAGM) instanceof LauncherBlockEntity be) {
                  LOGGER.info("STAGE1_CLIENT_AFTER_LAUNCH={}", NbtCompat.saveFullBE(be));
               }
            });
         }

         if (t == 220) {
            mc.stop();
         }
      }
   }

   private static void action(Minecraft mc, Runnable task) {
      mc.getSingleplayerServer().execute(task);
   }

   private static void capture(Minecraft mc, String name) {
      mc.execute(() -> {
         try {
            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path p = mc.gameDirectory.toPath().resolve("screenshots");
               Files.createDirectories(p);
               image.writeToFile(p.resolve(name + ".png"));
            } catch (Throwable var6) {
               if (image != null) {
                  try {
                     image.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (image != null) {
               image.close();
            }
         } catch (Exception var7) {
            throw new RuntimeException(var7);
         }
      });
   }
}
