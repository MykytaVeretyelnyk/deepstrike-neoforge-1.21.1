package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.sbw.deepstrike.game.CommandCenterData;
import dev.sbw.deepstrike.game.CommandDefense;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.CommandTabletNetwork;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;

final class CommandTabletReview {
   private static final Logger LOG = LogUtils.getLogger();
   private static int step;
   private static int age;
   private static int matrixIndex;
   private static int matrixAge;
   private static CommandTabletScreen matrixParent;
   private static final int MATRIX_STATES = 8;
   private static UUID defenseId;
   private static volatile UUID radarTarget;
   private static volatile boolean defenseVerified;
   private static boolean matrixFinished;
   private static volatile boolean seeded;
   private static volatile boolean bound;
   private static volatile boolean verified;
   private static volatile Throwable failure;
   private static final BlockPos FIRST = new BlockPos(0, 150, 0);
   private static final BlockPos SECOND = new BlockPos(24, 150, 0);
   private static String pending;
   private static boolean captured;

   private static Path dir(Minecraft mc) {
      return mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
   }

   private static void next(String capture) {
      step++;
      age = 0;
      pending = capture;
      captured = capture == null;
   }

   private static void press(Screen screen, String key) {
      String label = CommandTabletScreen.tr(key).getString();

      for (GuiEventListener child : screen.children()) {
         if (child instanceof Button b && b.getMessage().getString().equals(label)) {
            if (!b.active) {
               throw new IllegalStateException("Disabled button " + key);
            }

            b.onPress();
            return;
         }
      }

      throw new IllegalStateException("Missing button " + key + " / " + screen.getClass().getName());
   }

   private static CompoundTag network() {
      return CommandTabletScreen.latestSnapshot.getCompound("Network");
   }

   private static CompoundTag task() {
      return network().getList("Tasks", 10).getCompound(0);
   }

   static void tick(Minecraft mc) {
      if (failure != null) {
         throw new IllegalStateException("Command review server failure", failure);
      } else if (step == 0 && seeded) {
         next(null);
      } else if (!CommandTabletScreen.latestSnapshot.getString("Error").isEmpty()) {
         throw new IllegalStateException("Native tablet command error: " + CommandTabletScreen.latestSnapshot.getString("Error"));
      } else if (++age > 500) {
         throw new IllegalStateException("Command review timed out step=" + step + " snapshot=" + CommandTabletScreen.latestSnapshot);
      } else {
         if (step == 0) {
            if (age != 1) {
               return;
            }

            mc.options.pauseOnLostFocus = false;
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            mc.getSingleplayerServer().execute(() -> {
               try {
                  IntegratedServer server = mc.getSingleplayerServer();
                  ServerPlayer p = (ServerPlayer)server.getPlayerList().getPlayers().get(0);
                  ServerLevel level = p.serverLevel();
                  level.setDayTime(6000L);
                  p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)BFItems.JAGM_CAMERA_TABLET.get()));
                  p.setNoGravity(true);
                  p.setInvulnerable(true);

                  for (BlockPos pos : List.of(FIRST, SECOND)) {
                     level.setBlockAndUpdate(pos, (BlockState)((LauncherBlock)BFBlocks.JAGM_LAUNCHER.get()).defaultBlockState().setValue(LauncherBlock.FACING, Direction.SOUTH));
                     LauncherBlockEntity be = (LauncherBlockEntity)level.getBlockEntity(pos);
                     be.placed(p, ItemStack.EMPTY);
                     ItemStack ammo = new ItemStack((ItemLike)BFItems.JAGM.get());
                     new MissileData(MissileKind.JAGM, "none", "rocket", List.of(), "olive", 1000, 1000).writeToItemStack(ammo);
                     be.setItem(0, ammo);
                  }

                  if (Boolean.getBoolean("deepstrike.commandScaleReview")) {
                     for (int x = -40; x <= -20; x++) {
                        for (int z = -10; z <= 10; z++) {
                           level.setBlock(new BlockPos(x, 149, z), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 2);
                        }
                     }

                     IronDomeEntity dome = (IronDomeEntity)((EntityType)BFEntities.IRON_DOME.get()).create(level);
                     dome.setNoGravity(true);
                     dome.setSkinId("desert");
                     dome.setPos(-30.0, 150.0, 0.0);
                     dome.setAutoDefense(false);
                     dome.setReadyTamir(20);
                     level.addFreshEntity(dome);
                     p.setPos(dome.position());
                     String result = CommandDefense.bind(p, dome);
                     if (!result.isEmpty()) {
                        throw new IllegalStateException(result);
                     }

                     defenseId = dome.getUUID();
                     GuidedMissileEntity target = new GuidedMissileEntity(level, MissileKind.SPIKE_NLOS);
                     target.setPos(100.0, 180.0, 200.0);
                     target.setOwnerId(UUID.randomUUID());
                     target.setMissionId(UUID.randomUUID());
                     target.setWarhead("none");
                     target.setFrequency("909090");
                     target.setMission(List.of(new RoutePoint(100.0, 180.0, 1700.0, 80.0)), false);
                     target.configureSpike(180.0, 80.0);
                     level.addFreshEntity(target);
                     radarTarget = target.getUUID();
                  }

                  p.teleportTo(12.0, 154.0, 18.0);
                  seeded = true;
               } catch (Throwable var8x) {
                  failure = var8x;
               }
            });
         } else if (step == 1) {
            if (age == 1) {
               CommandTabletScreen.open();
               pending = "01-login";
               captured = false;
            }

            if (age > 20 && captured) {
               if (!network().hasUUID("Id")) {
                  press(mc.screen, "login");
               }

               next(null);
            }
         } else if (step == 2) {
            if (!CommandTabletScreen.latestSnapshot.contains("Network")) {
               return;
            }

            if (age == 1 || !bound && age % 20 == 0) {
               mc.getSingleplayerServer().execute(() -> {
                  try {
                     if (bound) {
                        return;
                     }

                     ServerPlayer p = (ServerPlayer)mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                     ServerLevel level = p.serverLevel();

                     for (BlockPos pos : List.of(FIRST, SECOND)) {
                        p.setPos(Vec3.atCenterOf(pos));
                        BlockState state = level.getBlockState(pos);
                        state.useWithoutItem(level, p, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                     }

                     p.teleportTo(12.0, 154.0, 18.0);
                     bound = true;
                  } catch (Throwable var6x) {
                     failure = var6x;
                  }
               });
            }

            if (network().getList("Units", 10).size() == 2) {
               next("02-units");
            }
         } else if (step == 3) {
            if (age < 15 || !captured) {
               return;
            }

            mc.screen.mouseClicked(70.0, 180.0, 0);
            mc.screen.mouseClicked(70.0, 228.0, 0);
            press(mc.screen, "route_selected");
            next("03-task-editor");
         } else if (step == 4) {
            if (age < 15 || !captured) {
               return;
            }

            press(mc.screen, "edit_route");
            if (!(mc.screen instanceof DeepStrikeMapScreen)) {
               throw new IllegalStateException("Xaero task editor did not open");
            }

            ((DeepStrikeMapScreen)mc.screen).setMapScale(0.8);
            next("04-xaero-route");
         } else if (step == 5) {
            if (age < 15 || !captured) {
               return;
            }

            DeepStrikeMapScreen map = (DeepStrikeMapScreen)mc.screen;
            double x = map.screenX(0.5);
            double y = map.screenY(180.5);
            map.mouseClicked(x, y, 0);
            map.mouseReleased(x, y, 0);
            next(null);
         } else if (step == 6) {
            if (age < 5) {
               return;
            }

            DeepStrikeMapScreen map = (DeepStrikeMapScreen)mc.screen;
            map.mouseClicked(map.width - 90, map.height - 22, 0);
            if (!(mc.screen instanceof CommandTaskScreen)) {
               throw new IllegalStateException("Route was not applied");
            }

            press(mc.screen, "save");
            if (!(mc.screen instanceof CommandTabletScreen)) {
               throw new IllegalStateException("Save did not return to tablet");
            }

            next(null);
         } else if (step == 7) {
            if (network().getList("Tasks", 10).isEmpty()) {
               return;
            }

            press(mc.screen, "tasks");
            mc.screen.mouseClicked(70.0, 140.0, 0);
            press(mc.screen, "arm");
            next(null);
         } else if (step == 8) {
            if (task().getString("State").equals("ERROR")) {
               throw new IllegalStateException("Native duty failed: " + task());
            }

            if (task().getString("State").equals("DUTY")) {
               next("05-combat-duty");
            }
         } else if (step == 9) {
            if (Boolean.getBoolean("deepstrike.commandScaleReview") && !matrixFinished) {
               if (age < 15) {
                  return;
               }

               matrix(mc);
               age = 20;
               return;
            }

            if (age < 15 || !captured) {
               return;
            }

            press(mc.screen, "launch");
            next("06-countdown");
         } else if (step == 10) {
            if (task().getString("State").equals("ERROR")) {
               throw new IllegalStateException("Native launch failed: " + task());
            }

            if (!task().getString("State").equals("DONE")) {
               return;
            }

            press(mc.screen, "flights");
            next("07-flight-monitor");
         } else if (step == 11) {
            if (age < 15 || !captured) {
               return;
            }

            mc.getSingleplayerServer().execute(() -> {
               try {
                  ServerPlayer p = (ServerPlayer)mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                  CommandCenterData data = CommandCenterData.get(p.serverLevel());
                  CompoundTag n = data.session(p);
                  CompoundTag task = n.getList("Tasks", 10).getCompound(0);
                  if (!task.getString("State").equals("DONE")) {
                     throw new IllegalStateException("Task not done");
                  }

                  if (task.getList("Assignments", 10).size() != 2) {
                     throw new IllegalStateException("Wrong number of launchers");
                  }

                  for (Tag raw : task.getList("Assignments", 10)) {
                     if (((CompoundTag)raw).getInt("Launched") != 1) {
                        throw new IllegalStateException("Wrong ammunition accounting");
                     }
                  }

                  verified = true;
               } catch (Throwable var7x) {
                  failure = var7x;
               }
            });
            next(null);
         } else if (step == 12 && verified) {
            if (Boolean.getBoolean("deepstrike.commandScaleReview")) {
               next(null);
               return;
            }

            finish(mc);
         } else if (step == 13) {
            if (age == 1) {
               CompoundTag payload = new CompoundTag();
               payload.putUUID("Unit", defenseId);
               CommandTabletNetwork.request("defense_select", payload);
            }

            CompoundTag radar = CommandTabletScreen.latestSnapshot.getCompound("Defense").getCompound("Radar");
            if (!radar.hasUUID("Id") || !radar.getUUID("Id").equals(defenseId)) {
               return;
            }

            if (!"desert".equals(radar.getString("Skin"))) {
               throw new IllegalStateException("Remote radar lost authoritative vehicle camouflage");
            }

            CompoundTag contact = radar.getList("Contacts", 10)
               .stream()
               .map(raw -> (CompoundTag)raw)
               .filter(c -> c.getUUID("Id").equals(radarTarget))
               .findFirst()
               .orElse(null);
            if (contact == null) {
               return;
            }

            CommandDefenseScreen screen = new CommandDefenseScreen(mc.screen, radar);
            mc.setScreen(screen);
            screen.mouseClicked(screen.mapX(contact.getDouble("X")), screen.mapZ(contact.getDouble("Z")), 0);
            next("08-remote-radar");
         } else if (step != 14) {
            if (step == 15) {
               if (age < 10) {
                  return;
               }

               mc.getSingleplayerServer().execute(() -> {
                  try {
                     ServerPlayer p = (ServerPlayer)mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                     IronDomeEntity dome = (IronDomeEntity)p.serverLevel().getEntity(defenseId);
                     if (dome.getReadyTamir() != 19) {
                        throw new IllegalStateException("Remote PVO/duplicate command spent wrong count: " + dome.getReadyTamir());
                     }

                     defenseVerified = true;
                  } catch (Throwable var3x) {
                     failure = var3x;
                  }
               });
               next(null);
            } else if (step == 16 && defenseVerified) {
               finish(mc);
            }
         } else {
            if (age < 15 || !captured) {
               return;
            }

            Iterator duplicate = mc.screen.children().iterator();

            while (true) {
               if (duplicate.hasNext()) {
                  GuiEventListener child = (GuiEventListener)duplicate.next();
                  if (!(child instanceof Button button) || !button.getMessage().getString().equals("TAMIR")) {
                     continue;
                  }

                  button.onPress();
               }

               CommandTabletNetwork.Action duplicatex = CommandTabletNetwork.lastReviewAction();
               if (duplicatex == null || !duplicatex.action().equals("defense_fire")) {
                  throw new IllegalStateException("PVO UI did not send a fire command");
               }

               CommandTabletNetwork.CHANNEL.sendToServer(duplicatex);
               next(null);
               break;
            }
         }

         if (step == 0 && seeded) {
            next(null);
         }
      }
   }

   private static void finish(Minecraft mc) {
      try {
         Files.createDirectories(dir(mc));
         Files.writeString(
            dir(mc).resolve("PASS.txt"),
            "Native login packet, actual block binding, UI selection, Xaero route editing, save/arm/launch packets, two real missiles and exact ammunition accounting passed.\n"
         );
      } catch (Exception var2) {
         throw new IllegalStateException(var2);
      }

      LOG.info("COMMAND_TABLET_NATIVE_PASS scaleMatrix={} remotePvo={}", matrixFinished, defenseVerified);
      mc.stop();
   }

   private static void matrix(Minecraft mc) {
      if (matrixParent == null) {
         matrixParent = (CommandTabletScreen)mc.screen;
      }

      if (++matrixAge == 1) {
         if (matrixIndex >= 56) {
            matrixFinished = true;
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
            mc.setScreen(matrixParent);
            press(mc.screen, "tasks");
            mc.screen.mouseClicked(70.0, 140.0, 0);
            return;
         }

         int scale = matrixIndex / 8;
         int state = matrixIndex % 8;
         mc.options.guiScale().set(scale);
         mc.resizeDisplay();
         mc.setScreen(matrixParent);
         switch (state) {
            case 0:
               press(mc.screen, "units");
               break;
            case 1:
               press(mc.screen, "tasks");
               break;
            case 2:
               press(mc.screen, "access");
               break;
            case 3:
               mc.setScreen(new CommandTaskScreen(matrixParent, network(), task(), Set.of()));
               break;
            case 4:
               DeepStrikeMapScreen.openOverview(matrixParent, network().getList("Units", 10).getCompound(0));
               break;
            case 5:
               mc.setScreen(new CommandTaskDetailsScreen(matrixParent, network(), task()));
               break;
            case 6:
               CompoundTag p = new CompoundTag();
               p.putUUID("Unit", defenseId);
               CommandTabletNetwork.request("defense_select", p);
               mc.setScreen(new CommandDefenseScreen(matrixParent, CommandTabletScreen.latestSnapshot.getCompound("Defense").getList("Units", 10).getCompound(0)));
               break;
            case 7:
               mc.setScreen(new CommandUnitScreen(matrixParent, network().getList("Units", 10).getCompound(0)));
         }

         pending = "matrix-" + state + "-scale-" + scale;
         captured = false;
      }

      if (matrixAge >= 10 && captured) {
         for (GuiEventListener child : mc.screen.children()) {
            if (child instanceof AbstractWidget widget
               && widget.visible
               && (
                  widget.getX() < 0
                     || widget.getY() < 0
                     || widget.getX() + widget.getWidth() > mc.screen.width
                     || widget.getY() + widget.getHeight() > mc.screen.height
               )) {
               throw new IllegalStateException("Command widget outside viewport: " + widget.getMessage());
            }
         }

         matrixIndex++;
         matrixAge = 0;
      }

      if (matrixAge > 100) {
         throw new IllegalStateException("Scale capture did not render");
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (event.phase == Phase.END && step >= 1) {
         CommandReviewRecorder.frame(mc);
      }

      if (event.phase == Phase.END && pending != null && !captured && age >= 5 && (step != 9 || matrixParent == null || matrixFinished || matrixAge >= 5)) {
         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Files.createDirectories(dir(mc));
               shot.writeToFile(dir(mc).resolve(pending + ".png"));
               captured = true;
               LOG.info("COMMAND_TABLET_CAPTURE {} {}", pending, mc.screen == null ? "HUD" : mc.screen.getClass().getName());
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
