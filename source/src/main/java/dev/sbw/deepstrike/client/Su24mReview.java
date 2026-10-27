package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.entity.vehicle.A10Entity;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModEntities;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Su24mReview {
   private static final Logger LOG = LoggerFactory.getLogger(Su24mReview.class);
   private static boolean opening;
   private static boolean requested;
   private static volatile boolean ready;
   private static int ticks;
   private static Su24mEntity plane;
   private static String pending;
   private static Vec3 flightStart;

   private static boolean enabled(Minecraft mc) {
      return Boolean.getBoolean("deepstrike.su24mReview") && mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().contains("/build/");
   }

   static void tick(ClientTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase == Phase.END && enabled(mc)) {
         if (mc.level == null) {
            if (!opening && mc.screen instanceof TitleScreen) {
               opening = true;
               mc.createWorldOpenFlows().openWorld("DeepStrike Smoke Shader Lab", () -> {});
            }
         } else if (mc.player != null && mc.getSingleplayerServer() != null) {
            if (!requested) {
               requested = true;
               mc.setScreen(null);
               mc.options.hideGui = false;
               mc.options.pauseOnLostFocus = false;
               mc.options.setCameraType(CameraType.FIRST_PERSON);
               mc.options.guiScale().set(2);
               mc.resizeDisplay();
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        level.setDayTime(6000L);
                        level.setWeatherParameters(6000, 0, false, false);
                        ArrayList<Entity> old = new ArrayList<>();

                        for (Entity entity : level.getAllEntities()) {
                           if (entity instanceof VehicleEntity) {
                              old.add(entity);
                           }
                        }

                        old.forEach(Entity::discard);

                        for (int x = -18; x <= 45; x++) {
                           for (int z = -18; z <= 18; z++) {
                              level.setBlockAndUpdate(new BlockPos(x + 200, 100, z + 200), Blocks.STONE.defaultBlockState());
                           }
                        }

                        plane = (Su24mEntity)((EntityType)BFEntities.SU24M.get()).create(level);
                        plane.moveTo(200.0, 101.1, 200.0, 0.0F, 0.0F);
                        plane.setNoGravity(true);
                        plane.setEnergy(plane.getMaxEnergy());
                        level.addFreshEntity(plane);
                        A10Entity reference = (A10Entity)((EntityType)ModEntities.A_10A.get()).create(level);
                        reference.moveTo(230.0, 101.1, 200.0, 0.0F, 0.0F);
                        reference.setNoGravity(true);
                        level.addFreshEntity(reference);
                        if (plane.computed().seats().size() == 2
                           && plane.computed().getHudType().equals(reference.computed().getHudType())
                           && plane.computed().getEngineType() == reference.computed().getEngineType()) {
                           LOG.info(
                              "SU24_CONFIG_PASS seats={} hud={} engine={} pilot={} navigator={}",
                              new Object[]{
                                 plane.computed().seats().size(),
                                 plane.computed().getHudType(),
                                 plane.computed().getEngineType(),
                                 plane.getSeat(0).getPosition(),
                                 plane.getSeat(1).getPosition()
                              }
                           );
                           player.setGameMode(GameType.CREATIVE);
                           player.teleportTo(200.0, 104.0, 204.0);
                           player.startRiding(plane, true);
                           plane.setOnGround(true);
                           player.getInventory().add(new ItemStack((ItemLike)BFItems.STORM_SHADOW.get(), 2));
                           if (plane.loadStore(player, 0) && plane.loadStore(player, 1)) {
                              List<RoutePoint> route = List.of(new RoutePoint(400.0, 180.0, 450.0, 1000.0), new RoutePoint(650.0, 100.0, 700.0, 1000.0));
                              plane.prepare(player, 0, route);
                              plane.prepare(player, 1, route);
                              ready = true;
                           } else {
                              throw new IllegalStateException("Review load failed");
                           }
                        } else {
                           throw new IllegalStateException("SU24 native aircraft configuration mismatch");
                        }
                     }
                  );
            } else if (ready) {
               if (System.getProperty("deepstrike.reviewCase", "").startsWith("restored-full-su24-fire")) {
                  if (mc.level.getEntity(plane.getId()) != null) {
                     if (EngineGroundReview.tick(mc, plane)) {
                        LOG.info("SU24_ORIGINAL_COMPLETE_MOTOR_PASS originalVolume=true profiles=11");
                        LOG.info("FP5_OPTIONS_FINISHED");
                        mc.stop();
                     }
                  }
               } else {
                  int t = ++ticks;
                  if (t == 50) {
                     mc.options.setCameraType(CameraType.FIRST_PERSON);
                     mc.player.setYRot(0.0F);
                     mc.player.setXRot(0.0F);
                  }

                  if (t == 90) {
                     pending = "su24-cockpit-scale2.png";
                  }

                  if (t == 105) {
                     mc.options.guiScale().set(4);
                     mc.resizeDisplay();
                  }

                  if (t == 140) {
                     pending = "su24-cockpit-scale4.png";
                  }

                  if (t == 160) {
                     mc.options.guiScale().set(2);
                     mc.resizeDisplay();
                     mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                  }

                  if (t == 170) {
                     mc.getSingleplayerServer().execute(() -> {
                        plane.setEngineStart(true);
                        plane.setEngineStartOver(true);
                        plane.setPower(0.12F);
                     });
                  }

                  if (t == 190) {
                     pending = "su24-third-person.png";
                  }

                  if (t == 210) {
                     mc.options.setCameraType(CameraType.FIRST_PERSON);
                     mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        p.stopRiding();
                        p.teleportTo(220.0, 111.0, 224.0);
                        Vec3 direction = new Vec3(208.0, 104.0, 200.0).subtract(p.getEyePosition());
                        p.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
                        p.setXRot((float)(-Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()))));
                     });
                  }

                  if (t == 230) {
                     Vec3 d = new Vec3(208.0, 104.0, 200.0).subtract(mc.player.getEyePosition());
                     mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
                     mc.player.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
                  }

                  if (t == 245) {
                     pending = "su24-scale-comparison.png";
                  }

                  if (t == 270) {
                     mc.getSingleplayerServer().execute(() -> {
                        ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        plane.moveTo(0.0, 1500.0, 0.0, 0.0F, 0.0F);
                        plane.setNoGravity(false);
                        plane.setEngineStart(true);
                        plane.setEngineStartOver(true);
                        plane.setPower(1.0F);
                        plane.setDeltaMovement(new Vec3(0.0, 0.0, 1.5));
                        p.startRiding(plane, true);
                        p.setYRot(0.0F);
                        p.setXRot(0.0F);
                        flightStart = plane.position();
                     });
                  }

                  if (t == 285) {
                     mc.player.setYRot(0.0F);
                     mc.player.setXRot(0.0F);
                  }

                  if (t == 390) {
                     mc.getSingleplayerServer()
                        .execute(
                           () -> {
                              if (Double.isFinite(plane.position().lengthSqr()) && !(plane.position().distanceTo(flightStart) < 20.0)) {
                                 LOG.info(
                                    "SU24_FLIGHT_PASS travel={} speed={} yaw={} pitch={}",
                                    new Object[]{plane.position().distanceTo(flightStart), plane.getDeltaMovement().length(), plane.getYRot(), plane.getXRot()}
                                 );
                              } else {
                                 throw new IllegalStateException("SU24 flight failed");
                              }
                           }
                        );
                  }

                  if (t == 395) {
                     Entity clientPlane = mc.level.getEntity(plane.getId());
                     if (!(clientPlane instanceof Su24mEntity aircraft) || aircraft.speedKmh() < 120.0 || aircraft.wingSweepProgress(1.0F) < 0.45F) {
                        throw new IllegalStateException("SU24 high-speed wing sweep missing: " + clientPlane);
                     }

                     LOG.info("SU24_WING_SWEEP_PASS speedKmh={} progress={}", aircraft.speedKmh(), aircraft.wingSweepProgress(1.0F));
                  }

                  if (t == 335) {
                     mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                  }

                  if (t == 345) {
                     pending = "su24-jet-full.png";
                  }

                  if (t == 350) {
                     mc.getSingleplayerServer().execute(() -> {
                        mc.getSingleplayerServer().overworld().setDayTime(18000L);
                        plane.setPower(0.35F);
                     });
                  }

                  if (t == 365) {
                     mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                  }

                  if (t == 380) {
                     pending = "su24-jet-low-night.png";
                  }

                  if (t == 385) {
                     mc.getSingleplayerServer().execute(() -> plane.setPower(1.0F));
                  }

                  if (t == 425) {
                     Su24mExhaust.verifyAnchorEvidence();
                     ArrayList<FlightLighting.Glow> lights = new ArrayList<>();
                     FlightLighting.collectEngineGlows(lights, 1.0F, 0.0F);
                     FlightLighting.Glow jet = lights.stream()
                        .filter(g -> Su24mExhaust.aircraftLight(g.id()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Su24 exhaust light missing"));
                     Entity local = mc.player.getVehicle();
                     if (local == null || jet.position().distanceTo(local.position()) > 13.0) {
                        throw new IllegalStateException("Su24 light detached");
                     }

                     if (Fp5BoosterFire.aircraftLength(0.35F) >= Fp5BoosterFire.aircraftLength(1.0F)) {
                        throw new IllegalStateException("Su24 throttle profile invalid");
                     }

                     LOG.info(
                        "SU24_FP5_EXHAUST_PASS light={} radius={} strength={} lengthLow={} lengthFull={}",
                        new Object[]{jet.position(), jet.radius(), jet.strength(), Fp5BoosterFire.aircraftLength(0.35F), Fp5BoosterFire.aircraftLength(1.0F)}
                     );
                     pending = "su24-jet-night.png";
                  }

                  if (t == 430) {
                     mc.options.setCameraType(CameraType.FIRST_PERSON);
                     mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setDayTime(6000L));
                  }

                  if (t == 435) {
                     Su24mScreen.open(plane.getId());
                  }

                  if (t == 450) {
                     pending = "su24-tablet-loadout.png";
                  }

                  if (t == 455) {
                     pressStep(mc, 1);
                  }

                  if (t == 460) {
                     press(mc, "add");
                  }

                  if (t == 465) {
                     pressStep(mc, 2);
                     if (findButton(mc, "fire_selected").active) {
                        throw new IllegalStateException("Unsaved route allowed launch");
                     }
                  }

                  if (t == 466) {
                     pressStep(mc, 1);
                     press(mc, "save_route");
                  }

                  if (t == 480) {
                     pending = "su24-tablet-route.png";
                  }

                  if (t == 485) {
                     pressStep(mc, 2);
                  }

                  if (t == 490) {
                     if (!findButton(mc, "fire_selected").active) {
                        throw new IllegalStateException("Saved route still blocked");
                     }

                     assertFields(mc, false, false);
                     press(mc, "mode_short.1");
                     assertFields(mc, true, false);
                  }

                  if (t == 495) {
                     press(mc, "mode_short.2");
                     assertFields(mc, false, true);
                  }

                  if (t == 500) {
                     press(mc, "mode_short.3");
                     assertFields(mc, true, true);
                  }

                  if (t == 510) {
                     pending = "su24-tablet-launch.png";
                  }

                  if (t == 520) {
                     mc.options.guiScale().set(4);
                     mc.resizeDisplay();
                  }

                  if (t == 540) {
                     pending = "su24-tablet-launch-scale4.png";
                  }

                  if (t == 545) {
                     EditBox f = findField(mc, "threshold");
                     f.setValue("invalid");
                     if (findButton(mc, "fire_selected").active) {
                        throw new IllegalStateException("Invalid condition allowed launch");
                     }

                     f.setValue("200");
                     LOG.info("SU24_UX_PASS dirty-route gate, save acknowledgement, all four modes and numeric validation");
                  }

                  if (t == 548 && mc.screen instanceof Su24mScreen screen) {
                     screen.keyPressed(72, 0, 0);
                  }

                  if (t == 552) {
                     pending = "su24-tablet-map-only.png";
                  }

                  if (t >= 560 && EngineGroundReview.tick(mc, plane)) {
                     LOG.info("SU24_REVIEW_FINISHED");
                     LOG.info("FP5_OPTIONS_FINISHED");
                     mc.stop();
                  }
               }
            }
         }
      }
   }

   private static Button findButton(Minecraft mc, String key) {
      if (mc.screen instanceof Su24mScreen screen) {
         for (GuiEventListener widget : screen.children()) {
            if (widget instanceof Button b && b.getMessage().getString().equals(Component.translatable("su24." + key).getString())) {
               return b;
            }
         }
      }

      throw new IllegalStateException("SU24 review could not find button " + key);
   }

   private static EditBox findField(Minecraft mc, String key) {
      if (mc.screen instanceof Su24mScreen screen) {
         for (GuiEventListener widget : screen.children()) {
            if (widget instanceof EditBox b && b.getMessage().getString().equals(Component.translatable("su24." + key).getString())) {
               return b;
            }
         }
      }

      throw new IllegalStateException("SU24 review could not find field " + key);
   }

   private static void press(Minecraft mc, String key) {
      Button button = findButton(mc, key);
      if (!button.active) {
         throw new IllegalStateException("Disabled button " + key);
      } else {
         button.onPress();
      }
   }

   private static void pressStep(Minecraft mc, int step) {
      press(mc, "step." + step);
   }

   private static void assertFields(Minecraft mc, boolean delay, boolean speed) {
      if (findField(mc, "delay").visible != delay || findField(mc, "threshold").visible != speed) {
         throw new IllegalStateException("Incorrect mode fields");
      }
   }

   static void render(RenderTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (enabled(mc)) {
         EngineGroundReview.render(mc, event);
         if (event.phase == Phase.END && pending != null && mc.level != null) {
            String name = pending;
            pending = null;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
         }
      }
   }
}
