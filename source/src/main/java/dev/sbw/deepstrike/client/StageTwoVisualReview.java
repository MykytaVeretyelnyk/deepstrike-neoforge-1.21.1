package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import com.atsuishio.superbwarfare.init.ModEntities;
import com.atsuishio.superbwarfare.tools.EntityFindUtil;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.sbw.deepstrike.game.ActiveFlights;
import dev.sbw.deepstrike.game.CruisePropulsion;
import dev.sbw.deepstrike.game.FlightTerrain;
import dev.sbw.deepstrike.game.FlightVisualMotion;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.ManpadPresentation;
import dev.sbw.deepstrike.game.MissileData;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.model.FlightOrientation;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.network.MissileCameraStream;
import dev.sbw.deepstrike.network.SpikeNlosNetwork;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import dev.sbw.deepstrike.compat.TickEvent.LevelTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class StageTwoVisualReview {
   private static final Logger LOGGER = LoggerFactory.getLogger(StageTwoVisualReview.class);
   private static final BlockPos PAD = new BlockPos(0, 245, 0);
   private static int ticks;
   private static volatile long sceneWorldTime = Long.MIN_VALUE;
   private static long lastSceneWorldTime = Long.MIN_VALUE;
   private static volatile boolean prepared;
   private static volatile boolean spikeControlVerified;
   private static volatile UUID mission;
   private static volatile int jagmId = -1;
   private static volatile int spikeId = -1;
   private static final int[] networkManpadIds = new int[]{-1, -1};
   private static final Vec3[] networkManpadPositions = new Vec3[]{null, null};
   private static final int[] networkManpadSamples = new int[]{0, 0};
   private static volatile UUID networkManpadTarget;
   private static boolean engineDepletionArmed;
   private static boolean trackedEngineSeenOff;
   private static final int[] engineFlightIds = new int[]{-1, -1};
   private static final boolean[] engineSeenOn = new boolean[]{false, false};
   private static final boolean[] engineSeenOff = new boolean[]{false, false};
   private static final int[] modelFlightIds = new int[]{-1, -1, -1, -1, -1};
   private static final Set<String> cameraChecks = new HashSet<>();
   private static final Set<String> modelChecks = new HashSet<>();
   private static final Map<MissileKind, Integer> reverseSamples = new EnumMap<>(MissileKind.class);
   private static GuidedMissileEntity observer;
   private static StageTwoVisualReview.OpticalScreen optical;
   private static String pendingCapture;

   static void inspectMotion(GuidedMissileEntity missile, Vec3 confirmedVelocity, double sampleTime, long latestTick) {
      if (ticks >= 730 && ticks < 930 && !(missile.getDeltaMovement().lengthSqr() < 1.0E-4) && !(confirmedVelocity.lengthSqr() < 1.0E-4)) {
         double alignment = missile.getDeltaMovement().normalize().dot(confirmedVelocity.normalize());
         if (alignment < 0.0) {
            int count = reverseSamples.merge(missile.kind(), 1, Integer::sum);
            if (count <= 3) {
               LOGGER.warn(
                  "STAGE2_REVERSED_VISUAL_MOTION kind={} alignment={} rendered={} confirmed={} sampleTime={} latestTick={}",
                  new Object[]{missile.kind(), alignment, missile.getDeltaMovement(), confirmedVelocity, sampleTime, latestTick}
               );
            }
         }
      }
   }

   static void renderTick(RenderTickEvent event) {
      if (Boolean.getBoolean("deepstrike.stage2Visual")) {
         Minecraft mc = Minecraft.getInstance();
         if (event.phase == Phase.START && ticks >= 730 && ticks < 830) {
            GuidedMissileEntity missile = RemoteFlights.get(modelFlightIds[(ticks - 730) / 20]);
            if (missile != null) {
               Vec3 center = MissileGeometry.worldCenter(missile.kind(), missile.position(), missile.getYRot(), missile.getXRot());
               double distance = missile.kind() == MissileKind.PEACEKEEPER ? 40.0 : 12.0;
               observe(mc, center.add(distance * 0.7, distance * 0.35, distance), center);
            }
         }

         if (event.phase == Phase.END && pendingCapture != null) {
            String name = pendingCapture;
            pendingCapture = null;
            captureFrame(mc, name);
         }
      }
   }

   static void serverTick(LevelTickEvent event) {
      if (Boolean.getBoolean("deepstrike.stage2Visual")
         && prepared
         && event.phase == Phase.END
         && !event.level.isClientSide
         && event.level.dimension().equals(Level.OVERWORLD)) {
         sceneWorldTime = event.level.getGameTime();
      }
   }

   static void tick(Minecraft mc) {
      if (ticks == 0) {
         ticks = 1;
         mc.options.guiScale().set(3);
         mc.resizeDisplay();
         mc.options.hideGui = true;
         mc.options.pauseOnLostFocus = false;
         mc.options.fov().set(48);
         mc.setScreen(null);
         mc.getSingleplayerServer()
            .execute(
               () -> {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  if (player != null) {
                     ArrayList<Entity> remove = new ArrayList<>();

                     for (Entity entity : level.getAllEntities()) {
                        if (entity instanceof GuidedMissileEntity || entity instanceof PatriotInterceptorEntity) {
                           remove.add(entity);
                        }
                     }

                     remove.forEach(Entity::discard);
                     level.setDayTime(6000L);
                     level.setWeatherParameters(6000, 0, false, false);
                     ActiveFlights flights = ActiveFlights.get(level);

                     for (Tag raw : flights.save(new CompoundTag()).getList("Missions", 10)) {
                        CompoundTag record = (CompoundTag)raw;
                        if (record.hasUUID("MissionId")
                           && record.contains("Origin")
                           && record.getLong("Origin") == PAD.asLong()
                           && !record.hasUUID("FlightId")
                           && !record.contains("Outcome")) {
                           flights.cancelLaunch(record.getUUID("MissionId"));
                        }
                     }

                     level.setBlockAndUpdate(PAD, Blocks.AIR.defaultBlockState());
                     level.setBlockAndUpdate(PAD, ((LauncherBlock)BFBlocks.PEACEKEEPER_PLATFORM.get()).defaultBlockState());
                     player.setGameMode(GameType.SPECTATOR);
                     player.teleportTo(25.0, 263.0, 25.0);
                     LauncherBlockEntity launcher = (LauncherBlockEntity)level.getBlockEntity(PAD);
                     launcher.load(ammo(MissileKind.PEACEKEEPER), player);

                     for (int i = 0; i < 3; i++) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
                        launcher.refuel(player, InteractionHand.MAIN_HAND);
                     }

                     prepared = true;
                  }
               }
            );
      } else if (prepared) {
         if (ticks >= 5 && ticks < 180) {
            inspectEngineFlights(mc);
         }

         if (ticks >= 5 && ticks < 65) {
            inspectNetworkManpads(mc);
         }

         long worldTime = sceneWorldTime;
         if (worldTime != Long.MIN_VALUE && worldTime != lastSceneWorldTime) {
            lastSceneWorldTime = worldTime;
            int t = ++ticks;
            if (t % 100 == 0 || t == 930) {
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        FlightTerrain.Diagnostics stats = FlightTerrain.diagnostics(level);
                        Runtime runtime = Runtime.getRuntime();
                        LOGGER.info(
                           "STAGE2_TERRAIN_MEMORY tick={} instances={} retained={} queued={} requests={} summedPeak={} loadedChunks={} usedMiB={} maxMiB={}",
                           new Object[]{
                              t,
                              stats.instances(),
                              stats.retained(),
                              stats.queued(),
                              stats.requests(),
                              stats.summedPeakRetained(),
                              level.getChunkSource().getLoadedChunksCount(),
                              (runtime.totalMemory() - runtime.freeMemory()) / 1048576L,
                              runtime.maxMemory() / 1048576L
                           }
                        );
                     }
                  );
            }

            if (t == 5) {
               checkManpadClientMotion(mc);
               spawnNetworkManpads(mc);
               spawnEngineFlights(mc);
            }

            if (!engineDepletionArmed && engineSeenOn[0] && engineSeenOn[1]) {
               engineDepletionArmed = true;
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        for (int id : engineFlightIds) {
                           if (!(mc.getSingleplayerServer().overworld().getEntity(id) instanceof GuidedMissileEntity missilex)) {
                              throw new IllegalStateException("Engine fixture expired before client acknowledgement");
                           }

                           CompoundTag saved = missilex.saveWithoutId(new CompoundTag());
                           CruisePropulsion.State fuel = CruisePropulsion.load(saved.getCompound("CruisePropulsion"));
                           saved.put(
                              "CruisePropulsion",
                              CruisePropulsion.save(
                                 new CruisePropulsion.State(
                                    fuel.initial() / fuel.endurance() * 0.025, fuel.initial(), fuel.capacity(), fuel.seconds(), fuel.endurance()
                                 )
                              )
                           );
                           missilex.load(saved);
                        }

                        LOGGER.info("STAGE2_ENGINE_DEPLETION_ARMED acknowledgedBoth=true remainingPhysicalSeconds=.025");
                     }
                  );
            }

            if (t == 180) {
               for (int i = 0; i < 2; i++) {
                  if (!engineSeenOn[i] || !engineSeenOff[i]) {
                     throw new IllegalStateException(
                        "Missing network engine depletion transition: "
                           + i
                           + " id="
                           + engineFlightIds[i]
                           + " on="
                           + engineSeenOn[i]
                           + " off="
                           + engineSeenOff[i]
                     );
                  }
               }

               if (!trackedEngineSeenOff) {
                  throw new IllegalStateException("Native tracked engine was never observed switched off before removal");
               }

               LOGGER.info("STAGE2_ENGINE_CLIENT_VERIFIED replicas=near,3000m nativeTrackedOff=true plumeVerticesOff=0 lightingOff=true");
               mc.getSingleplayerServer().execute(() -> {
                  for (int id : engineFlightIds) {
                     Entity entity = mc.getSingleplayerServer().overworld().getEntity(id);
                     if (entity != null) {
                        entity.discard();
                     }
                  }
               });
            }

            if (t == 80) {
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();

                        for (int id : engineFlightIds) {
                           Entity entity = level.getEntity(id);
                           LOGGER.info(
                              "STAGE2_ENGINE_SERVER_SAMPLE id={} live={} state={}",
                              new Object[]{id, entity != null, entity == null ? null : entity.saveWithoutId(new CompoundTag()).getCompound("CruisePropulsion")}
                           );
                        }
                     }
                  );
            }

            if (t == 65) {
               for (int ix = 0; ix < 2; ix++) {
                  if (networkManpadSamples[ix] < 20) {
                     throw new IllegalStateException("Missing real tracked MANPADS client movement: " + ix + " samples=" + networkManpadSamples[ix]);
                  }
               }

               LOGGER.info("STAGE2_MANPAD_NETWORK_MOTION_VERIFIED samples={} actualServerSpawn=true nativeTracking=true", Arrays.toString(networkManpadSamples));
               mc.getSingleplayerServer().execute(() -> {
                  ServerLevel level = mc.getSingleplayerServer().overworld();

                  for (int id : networkManpadIds) {
                     Entity entity = level.getEntity(id);
                     if (entity != null) {
                        entity.discard();
                     }
                  }

                  if (networkManpadTarget != null) {
                     Entity target = level.getEntity(networkManpadTarget);
                     if (target != null) {
                        target.discard();
                     }
                  }
               });
            }

            if (observer == null) {
               observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            }

            if (t < 220) {
               observe(mc, new Vec3(25.0, 263.0, 25.0), new Vec3(0.5, 256.0, 0.5));
            }

            if (t == 100) {
               capture(mc, "stage2-peacekeeper-mounted-world");
            }

            if (t == 120) {
               mc.getSingleplayerServer().execute(() -> {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  LauncherBlockEntity launcher = (LauncherBlockEntity)level.getBlockEntity(PAD);
                  mission = UUID.randomUUID();
                  if (!launcher.fire(player, List.of(new RoutePoint(3000.0, 280.0, 0.0, 2400.0)), true, "0002400", mission)) {
                     throw new IllegalStateException(launcher.getLastFireError());
                  }
               });
            }

            if (t >= 220 && t < 320) {
               GuidedMissileEntity missile = RemoteFlights.all()
                  .stream()
                  .filter(m -> mission != null && mission.equals(m.missionId()))
                  .findFirst()
                  .orElse(null);
               if (missile != null) {
                  Vec3 centre = missile.position().add(0.0, -10.0, 0.0);
                  observe(mc, centre.add(22.0, 5.0, 22.0), centre);
               }

               if (t == 245 || t == 285) {
                  capture(mc, "stage2-peacekeeper-vertical-" + t);
               }
            }

            if (t == 320) {
               mc.getSingleplayerServer().execute(() -> {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  GuidedMissileEntity missilex = new GuidedMissileEntity(level, MissileKind.JAGM);
                  missilex.setPos(80.0, 1000.0, 0.0);
                  missilex.setMissionId(UUID.randomUUID());
                  missilex.setWarhead("none");
                  missilex.setMission(List.of(new RoutePoint(3000.0, 1030.0, 800.0, 450.0)), false);
                  missilex.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 12);
                  missilex.setDeltaMovement(new Vec3(4.0, 0.0, 0.0));
                  level.addFreshEntity(missilex);
                  jagmId = missilex.getId();
               });
            }

            GuidedMissileEntity jagm = RemoteFlights.get(jagmId);
            if (t > 325 && t < 400 && jagm != null) {
               observe(mc, jagm.position().add(5.0, 3.0, 8.0), jagm.position());
               if (t == 360) {
                  capture(mc, "stage2-jagm-world-turn");
               }
            }

            if (t == 400 && jagm != null) {
               if (mc.level.getEntity(jagmId) != null) {
                  mc.level.removeEntity(jagmId, RemovalReason.UNLOADED_TO_CHUNK);
               }

               optical = new StageTwoVisualReview.OpticalScreen(jagmId, jagm.position().add(0.0, -100.0, -3000.0));
               mc.setScreen(optical);
               LOGGER.info("STAGE2_VISUAL_REPLICA_ONLY native={} replica={}", mc.level.getEntity(jagmId) != null, RemoteFlights.get(jagmId) != null);
            }

            if (t == 425) {
               capture(mc, "stage2-jagm-replica-thermal-3000m");
            }

            if (t == 445 && optical != null) {
               optical.camera.setMode(PatriotThermalCamera.CameraMode.NORMAL);
            }

            if (t == 465) {
               capture(mc, "stage2-jagm-replica-optical-3000m");
            }

            if (t == 475) {
               capture(mc, "stage2-jagm-occluded-3000m");
            }

            if (t == 480) {
               mc.setScreen(null);
               ClientCamera.view(jagmId);
            }

            if (t == 505) {
               capture(mc, "stage2-jagm-nose-camera");
               LOGGER.info("STAGE2_VISUAL_NOSE_CAMERA followed={}", jagm != null && ClientCamera.isFollowed(jagm));
            }

            if (t == 520) {
               mc.getSingleplayerServer().execute(() -> {
                  ServerLevel level = mc.getSingleplayerServer().overworld();
                  ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                  SpikeNlosEntity vehicle = (SpikeNlosEntity)((EntityType)BFEntities.SPIKE_NLOS.get()).create(level);
                  vehicle.setPos(80.0, 250.0, 0.0);
                  vehicle.setNoGravity(true);
                  level.addFreshEntity(vehicle);
                  player.setGameMode(GameType.CREATIVE);
                  if (!player.startRiding(vehicle, true)) {
                     throw new IllegalStateException("SPIKE camera fixture seat rejected");
                  } else {
                     GuidedMissileEntity missilex = new GuidedMissileEntity(level, MissileKind.SPIKE_NLOS);
                     missilex.setPos(150.0, 300.0, 150.0);
                     missilex.setMissionId(UUID.randomUUID());
                     missilex.setOwnerId(player.getUUID());
                     missilex.setWarhead("none");
                     missilex.setMission(List.of(new RoutePoint(2000.0, 280.0, 150.0, 200.0)), false);
                     missilex.configureSpike(306.0, 200.0);
                     missilex.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 10);
                     level.addFreshEntity(missilex);
                     spikeId = missilex.getId();
                     MissileCameraStream.begin(player, missilex);
                  }
               });
            }

            if (t == 540) {
               ClientCamera.restore();
               ClientCamera.view(spikeId);
               mc.options.hideGui = false;
            }

            if (t == 560) {
               capture(mc, "stage2-spike-nose-camera");
            }

            if (t == 570) {
               mc.options.keyUp.setDown(true);
            }

            if (t == 580) {
               SpikeNlosNetwork.CHANNEL.sendToServer(new SpikeNlosNetwork.Control(spikeId, 0, true, 1000.0, 280.0, 1000.0, -1));
            }

            if (t == 585) {
               mc.player.setYRot(mc.player.getYRot() + 30.0F);
               mc.player.setXRot(mc.player.getXRot() + 10.0F);
            }

            if (t == 595) {
               mc.options.keyUp.setDown(false);
            }

            if (t == 610) {
               capture(mc, "stage2-spike-freelook-camera");
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        GuidedMissileEntity missilex = (GuidedMissileEntity)level.getEntity(spikeId);
                        CompoundTag tag = missilex.saveWithoutId(new CompoundTag());
                        CompoundTag point = tag.getList("Route", 10).getCompound(0);
                        if (point.getDouble("X") == 1000.0
                           && point.getDouble("Y") == 280.0
                           && point.getDouble("Z") == 1000.0
                           && !(tag.getDouble("CommandedSpeed") <= 200.0)) {
                           spikeControlVerified = true;
                           LOGGER.info("STAGE2_SPIKE_CLIENT_CONTROL_VERIFIED speed={} target=1000,280,1000", tag.getDouble("CommandedSpeed"));
                        } else {
                           throw new IllegalStateException("SPIKE TV control packets not applied: " + tag);
                        }
                     }
                  );
            }

            if (t == 640) {
               ClientCamera.restore();
            }

            if (t == 700) {
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        player.stopRiding();
                        player.setGameMode(GameType.SPECTATOR);

                        for (MissileKind kind : MissileKind.values()) {
                           int indexx = kind.ordinal();
                           GuidedMissileEntity missilex = new GuidedMissileEntity(level, kind);
                           Vec3 start = new Vec3(500.0, 1000.0, indexx * 1200);
                           missilex.setPos(start);
                           missilex.setMissionId(UUID.randomUUID());
                           missilex.setWarhead("none");
                           missilex.setMission(
                              List.of(new RoutePoint(start.x + 3000.0, 1000.0, start.z + 400.0, kind.defaultSpeed)),
                              kind == MissileKind.PEACEKEEPER
                           );
                           if (kind == MissileKind.SPIKE_NLOS) {
                              missilex.configureSpike(1006.0, kind.defaultSpeed);
                           }

                           missilex.setLaunchVector(new Vec3(1.0, 0.0, 0.0), 10);
                           level.addFreshEntity(missilex);
                           modelFlightIds[indexx] = missilex.getId();
                        }
                     }
                  );
            }

            if (t >= 730 && t < 830) {
               int index = (t - 730) / 20;
               GuidedMissileEntity missilex = RemoteFlights.get(modelFlightIds[index]);
               if (missilex != null) {
                  double distance = missilex.kind() == MissileKind.PEACEKEEPER ? 40.0 : 12.0;
                  observe(mc, missilex.position().add(distance * 0.7, distance * 0.35, distance), missilex.position());
                  if ((t - 730) % 20 == 10) {
                     capture(mc, "stage2-" + missilex.kind().id + "-moving-model");
                  }
               }
            }

            if (t >= 830 && t < 930) {
               int index = (t - 830) / 20;
               GuidedMissileEntity missilex = RemoteFlights.get(modelFlightIds[index]);
               if ((t - 830) % 20 == 0 && missilex != null) {
                  optical = new StageTwoVisualReview.OpticalScreen(missilex.getId(), missilex.position().add(0.0, -100.0, -3000.0));
                  mc.setScreen(optical);
               }

               if ((t - 830) % 20 == 10 && missilex != null) {
                  capture(mc, "stage2-" + missilex.kind().id + "-moving-optical-3000m");
               }
            }

            if (t == 940) {
               if (!spikeControlVerified) {
                  throw new IllegalStateException("Server SPIKE control gate did not pass");
               }

               if (modelChecks.size() != 10) {
                  throw new IllegalStateException("Missing model frame checks: " + modelChecks);
               }

               LOGGER.info("STAGE2_VISUAL_MOTION_DIAGNOSTIC reversals={}", reverseSamples);
               if (!reverseSamples.isEmpty()) {
                  throw new IllegalStateException("Presentation reversed confirmed missile motion: " + reverseSamples);
               }

               LOGGER.info("STAGE2_VISUAL_SCENES_COMPLETE");
               mc.stop();
            }
         }
      }
   }

   private static void checkManpadClientMotion(Minecraft mc) {
      ClientLevel level = mc.level;
      ArmorStand target = new ArmorStand(level, 200.0, 500.0, 40.0);
      int targetId = -31001;
      level.addEntity(target);
      int cases = 0;

      try {
         if (EntityFindUtil.findEntity(level, target.getUUID().toString()) != target) {
            throw new IllegalStateException("Client MANPADS guidance target not visible to native lookup");
         }

         label121:
         for (EntityType type : List.of((EntityType)ModEntities.FIM_92_MISSILE.get(), (EntityType)ModEntities.IGLA_MISSILE.get())) {
            MissileProjectile missile = (MissileProjectile)type.create(level);
            missile.setPos(0.0, 500.0, 0.0);
            missile.setTargetUuid(target.getUUID().toString());
            missile.setSyncedTick(210);
            missile.tickCount = 210;
            Vec3 visible = null;
            int i = 0;

            while (true) {
               if (i < 30) {
                  Vec3 motion = new Vec3(0.5 + i * 0.02, 0.02, 0.1);
                  Vec3 before = missile.position();
                  ManpadPresentation.position(missile, before);
                  missile.lerpMotion(motion.x, motion.y, motion.z);
                  FlightVisualMotion.Step expected = FlightVisualMotion.step(
                     before, visible == null ? motion.scale(20.0) : visible, before.add(motion), motion.scale(20.0), 0.05, 0.15
                  );
                  missile.tickCount++;
                  missile.tick();
                  visible = expected.velocity();
                  if (missile.isAlive()
                     && !(missile.position().distanceTo(expected.position()) > 1.0E-7)
                     && !(missile.getDeltaMovement().distanceTo(expected.position().subtract(before)) > 1.0E-8)) {
                     if (missile.getLookAngle().normalize().dot(missile.getDeltaMovement().normalize()) < 0.999999) {
                        throw new IllegalStateException("Client MANPADS model look does not follow movement");
                     }

                     i++;
                     continue;
                  }

                  throw new IllegalStateException(
                     "Client native guidance overwrote reconciled MANPADS motion: "
                        + type
                        + " tick="
                        + i
                        + " expected="
                        + expected
                        + " actualPos="
                        + missile.position()
                        + " actualVelocity="
                        + missile.getDeltaMovement()
                  );
               }

               Vec3 held = null;
               int ix = 0;

               while (true) {
                  if (ix < 20) {
                     missile.tickCount++;
                     missile.tick();
                     if (ix == 6) {
                        held = missile.position();
                     }

                     if (ix <= 6 || !(missile.position().distanceTo(held) > 1.0E-8) && !(missile.getDeltaMovement().lengthSqr() > 1.0E-16)) {
                        ix++;
                        continue;
                     }

                     throw new IllegalStateException("Client MANPADS drifted on stale packets: " + type);
                  }

                  missile.discard();
                  cases++;
                  continue label121;
               }
            }
         }

         LOGGER.info(
            "STAGE2_MANPAD_CLIENT_TICKS_VERIFIED types={} ticks=100 validTarget=true reconciledVelocityPreserved=true stalePacketsHold=true noseAligned=true",
            cases
         );
      } finally {
         level.removeEntity(targetId, RemovalReason.DISCARDED);
      }
   }

   private static void spawnEngineFlights(Minecraft mc) {
      mc.getSingleplayerServer()
         .execute(
            () -> {
               ServerLevel level = mc.getSingleplayerServer().overworld();

               for (int i = 0; i < 2; i++) {
                  GuidedMissileEntity missile = new GuidedMissileEntity(level, MissileKind.JAGM);
                  Vec3 origin = new Vec3(i == 0 ? 25.0 : 3025.0, 310.0, 25.0);
                  missile.setPos(origin);
                  missile.setMission(List.of(new RoutePoint(origin.x, origin.y, origin.z + 200.0, 200.0)), false);
                  missile.setLaunchVector(new Vec3(0.0, 0.0, 1.0), 10);
                  missile.setDeltaMovement(0.0, 0.0, 1.0);
                  missile.setWarhead("none");
                  CruisePropulsion.State fuel = CruisePropulsion.create(MissileKind.JAGM, 1000.0, 1000.0, MissileKind.JAGM.maxRange);
                  CompoundTag saved = missile.saveWithoutId(new CompoundTag());
                  saved.put("CruisePropulsion", CruisePropulsion.save(fuel));
                  missile.load(saved);
                  boolean accepted = level.addFreshEntity(missile);
                  engineFlightIds[i] = missile.getId();
                  LOGGER.info(
                     "STAGE2_ENGINE_FIXTURE_SPAWN id={} accepted={} powered={} fuel={}",
                     new Object[]{missile.getId(), accepted, missile.engineRunning(), saved.getCompound("CruisePropulsion")}
                  );
               }
            }
         );
   }

   private static void inspectEngineFlights(Minecraft mc) {
      for (int i = 0; i < 2; i++) {
         if (i == 0 && mc.level.getEntity(engineFlightIds[0]) instanceof GuidedMissileEntity tracked && !tracked.engineRunning()) {
            trackedEngineSeenOff = true;
         }

         GuidedMissileEntity missile = RemoteFlights.get(engineFlightIds[i]);
         if (missile != null) {
            if (missile.engineRunning()) {
               if (!engineSeenOn[i]) {
                  LOGGER.info("STAGE2_ENGINE_FIXTURE_ON id={} tick={}", missile.getId(), ticks);
               }

               engineSeenOn[i] = true;
            } else {
               if (!engineSeenOff[i]) {
                  LOGGER.info("STAGE2_ENGINE_FIXTURE_OFF id={} tick={}", missile.getId(), ticks);
               }

               engineSeenOff[i] = true;
               AtomicInteger vertices = new AtomicInteger();
               VertexConsumer consumer = (VertexConsumer)Proxy.newProxyInstance(
                  VertexConsumer.class.getClassLoader(), new Class[]{VertexConsumer.class}, (proxy, method, args) -> {
                     if (method.getName().equals("vertex")) {
                        vertices.incrementAndGet();
                     }

                     return method.getReturnType() == void.class ? null : proxy;
                  }
               );
               MultiBufferSource buffers = type -> consumer;
               ClientSetup.renderExhaust(missile, 0.0F, new PoseStack(), buffers);
               if (vertices.get() != 0) {
                  throw new IllegalStateException("Depleted exhaust emitted vertices");
               }

               missile.setEngineRunning(true);

               try {
                  ClientSetup.renderExhaust(missile, 0.0F, new PoseStack(), buffers);
                  if (vertices.get() == 0) {
                     throw new IllegalStateException("Exhaust vertex probe was inactive");
                  }
               } finally {
                  missile.setEngineRunning(false);
               }

               try {
                  Method engine = FlightLighting.class.getDeclaredMethod("engine", GuidedMissileEntity.class);
                  engine.setAccessible(true);
                  if ((Boolean)engine.invoke(null, missile)) {
                     throw new IllegalStateException("Depleted lighting remains enabled");
                  }
               } catch (ReflectiveOperationException var9) {
                  throw new IllegalStateException(var9);
               }
            }
         }
      }
   }

   private static void spawnNetworkManpads(Minecraft mc) {
      mc.getSingleplayerServer().execute(() -> {
         ServerLevel level = mc.getSingleplayerServer().overworld();
         ArmorStand target = new ArmorStand(level, 25.0, 265.0, 220.0);
         target.setInvulnerable(true);
         level.addFreshEntity(target);
         networkManpadTarget = target.getUUID();
         int index = 0;

         for (EntityType type : List.of((EntityType)ModEntities.FIM_92_MISSILE.get(), (EntityType)ModEntities.IGLA_MISSILE.get())) {
            MissileProjectile missile = (MissileProjectile)type.create(level);
            missile.setPos(20 + index * 10, 265.0, 25.0);
            missile.setYRot(0.0F);
            missile.setXRot(0.0F);
            missile.setTargetUuid(target.getUUID().toString());
            missile.setDeltaMovement(new Vec3(0.0, 0.0, 0.5));
            missile.setDamage(0.0F);
            missile.setExplosionDamage(0.0F);
            missile.setExplosionRadius(0.0F);
            level.addFreshEntity(missile);
            networkManpadIds[index++] = missile.getId();
         }
      });
   }

   private static void inspectNetworkManpads(Minecraft mc) {
      for (int i = 0; i < 2; i++) {
         if (mc.level.getEntity(networkManpadIds[i]) instanceof MissileProjectile missile) {
            Vec3 previous = networkManpadPositions[i];
            Vec3 position = missile.position();
            Vec3 velocity = missile.getDeltaMovement();
            if (velocity.lengthSqr() > 1.0E-8) {
               double launchEnvelope = (10.0 + (i == 0 ? 75 : 60) * (Math.max(missile.tickCount, missile.getSyncedTick()) + 5) * 0.05) / 20.0;
               if (velocity.length() > Math.min(8.5, launchEnvelope)) {
                  throw new IllegalStateException("Native network MANPADS exceeded launch acceleration envelope: " + i + " " + velocity);
               }

               if (missile.getLookAngle().normalize().dot(velocity.normalize()) < 0.99999) {
                  throw new IllegalStateException("Tracked MANPADS nose differs from received motion: " + i);
               }

               if (previous != null) {
                  Vec3 displacement = position.subtract(previous);
                  if (displacement.lengthSqr() > 1.0E-8) {
                     if (displacement.normalize().dot(velocity.normalize()) < 0.98 || displacement.length() > 9.0) {
                        throw new IllegalStateException(
                           "Tracked MANPADS jumped or reversed: "
                              + i
                              + " displacement="
                              + displacement
                              + " velocity="
                              + velocity
                              + " age="
                              + missile.tickCount
                              + " syncedAge="
                              + missile.getSyncedTick()
                              + " previous="
                              + previous
                              + " position="
                              + position
                        );
                     }

                     networkManpadSamples[i]++;
                  }
               }
            }

            networkManpadPositions[i] = position;
         }
      }
   }

   static void inspectModelFrame(GuidedMissileEntity missile, boolean sensorPass) {
      if (ticks >= 730 && ticks < 930) {
         boolean opticalView = ticks >= 830;
         int index = (ticks - (opticalView ? 830 : 730)) / 20;
         if (missile.getId() == modelFlightIds[index] && sensorPass == opticalView) {
            if (!(missile.getDeltaMovement().lengthSqr() < 1.0E-4)) {
               String view = opticalView ? "optical" : "near";
               String key = missile.kind() + ":" + view;
               Vec3 local = MissileGeometry.localNose(missile.kind());
               Vector3f direction = MissileGeometry.flight(missile.kind(), missile.getYRot(), missile.getXRot())
                  .transformDirection(new Vector3f((float)local.x, (float)local.y, (float)local.z));
               double alignment = new Vec3(direction).normalize().dot(missile.getDeltaMovement().normalize());
               if (alignment < 0.99999) {
                  throw new IllegalStateException(
                     "Moving model axis disagrees with motion: "
                        + missile.kind()
                        + " "
                        + alignment
                        + " yaw="
                        + missile.getYRot()
                        + " pitch="
                        + missile.getXRot()
                        + " velocity="
                        + missile.getDeltaMovement()
                        + " local="
                        + local
                        + " transformed="
                        + direction
                  );
               } else {
                  if (modelChecks.add(key)) {
                     LOGGER.info("STAGE2_MODEL_AXIS_VERIFIED kind={} view={} alignment={}", new Object[]{missile.kind(), view, alignment});
                  }
               }
            }
         }
      }
   }

   static void inspectCamera(Camera camera, Entity followed, float yaw, float pitch) {
      if (followed instanceof GuidedMissileEntity missile && ticks >= 490) {
         String phase = missile.kind() == MissileKind.SPIKE_NLOS ? (ticks >= 590 ? "freelook" : "nose") : "nose";
         String key = missile.kind() + ":" + phase;
         if (!cameraChecks.contains(key)) {
            Vec3 expected = missile.position().add(missile.getLookAngle().scale(MissileGeometry.noseDistance(missile.kind()) + 0.05));
            double error = camera.getPosition().distanceTo(expected);
            double alignment = new Vec3(camera.getLookVector()).dot(FlightOrientation.direction(yaw, pitch));
            if (error > 1.0E-4 || alignment < 0.999999) {
               throw new IllegalStateException("Nose camera disagrees with frame pose: error=" + error + " alignment=" + alignment);
            } else if (phase.equals("freelook") && new Vec3(camera.getLookVector()).dot(missile.getLookAngle()) > 0.98) {
               throw new IllegalStateException("Freelook did not separate viewing direction from missile nose");
            } else {
               cameraChecks.add(key);
               LOGGER.info("STAGE2_NOSE_FRAME_VERIFIED kind={} phase={} positionError={} alignment={}", new Object[]{missile.kind(), phase, error, alignment});
            }
         }
      }
   }

   static void drawOccluder(RenderLevelStageEvent event, BufferSource buffers, Vec3 camera) {
      if (ticks >= 470 && ticks < 480 && optical != null) {
         GuidedMissileEntity target = RemoteFlights.get(jagmId);
         if (target != null) {
            Vec3 direction = target.position().subtract(camera).normalize();
            double distance = Minecraft.getInstance().gameRenderer.getDepthFar() * 0.85;
            Vec3 center = direction.scale(distance);
            Vec3 right = new Vec3(direction.z, 0.0, -direction.x).normalize();
            Vec3 up = right.cross(direction).normalize();
            VertexConsumer out = buffers.getBuffer(StageTwoVisualReview.DepthFixture.TYPE);
            Matrix4f matrix = event.getPoseStack().last().pose();

            for (int i = 0; i < 4; i++) {
               Vec3 point = center.add(right.scale(i != 0 && i != 3 ? 8.0 : -8.0)).add(up.scale(i < 2 ? -8.0 : 8.0));
               out.addVertex(matrix, (float)point.x, (float)point.y, (float)point.z).setColor(83, 107, 130, 255);
            }

            buffers.endBatch();
         }
      }
   }

   private static ItemStack ammo(MissileKind kind) {
      ItemStack stack = new ItemStack(kind == MissileKind.PEACEKEEPER ? (ItemLike)BFItems.PEACEKEEPER.get() : (ItemLike)BFItems.JAGM.get());
      new MissileData(kind, "none", "rocket", List.of(), MissileData.defaultCamo(kind), 1000, 1000).writeToItemStack(stack);
      return stack;
   }

   private static void observe(Minecraft mc, Vec3 position, Vec3 target) {
      observer.setPos(position);
      Vec3 direction = target.subtract(observer.getEyePosition());
      observer.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
      observer.setXRot((float)(-Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()))));
      observer.xo = position.x;
      observer.yo = position.y;
      observer.zo = position.z;
      observer.yRotO = observer.getYRot();
      observer.xRotO = observer.getXRot();
      mc.setCameraEntity(observer);
   }

   private static void capture(Minecraft mc, String name) {
      pendingCapture = name;
   }

   private static void captureFrame(Minecraft mc, String name) {
      try {
         NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

         try {
            Path dir = mc.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            shot.writeToFile(dir.resolve(name + ".png"));
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
         throw new RuntimeException(var7);
      }
   }

   private static final class DepthFixture extends RenderType {
      private static final RenderType TYPE = create(
         "deepstrike_stage2_wall",
         DefaultVertexFormat.POSITION_COLOR,
         Mode.QUADS,
         256,
         false,
         false,
         CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false)
      );

      private DepthFixture() {
         super("stage2_wall", DefaultVertexFormat.POSITION_COLOR, Mode.QUADS, 256, false, false, () -> {}, () -> {});
      }
   }

   private static final class OpticalScreen extends Screen {
      final PatriotThermalCamera camera = new PatriotThermalCamera();
      final int id;
      final Vec3 origin;

      OpticalScreen(int id, Vec3 origin) {
         super(Component.literal("Stage 2 optical verification"));
         this.id = id;
         this.origin = origin;
         GuidedMissileEntity missile = RemoteFlights.get(id);
         if (missile == null || missile.kind() != MissileKind.PEACEKEEPER) {
            for (int i = 0; i < 5; i++) {
               this.camera.zoomIn();
            }
         }
      }

      public void render(GuiGraphics graphics, int x, int y, float partial) {
         GuidedMissileEntity missile = RemoteFlights.get(this.id);
         if (missile == null) {
            throw new IllegalStateException("Optical target replica missing");
         } else {
            this.camera.track(this.origin, missile.getX(), missile.getY(), missile.getZ(), this.id);
            this.camera.capture(graphics);
            graphics.fill(0, 0, this.width, this.height, -15722464);
            this.camera.render(graphics, 8, 24, this.width - 16, this.height - 32);
            graphics.drawString(this.font, "Replica only / 3000 m / " + this.camera.getMode().title, 8, 8, -1118482);
         }
      }

      public boolean isPauseScreen() {
         return false;
      }

      public void removed() {
         this.camera.close();
      }
   }
}
