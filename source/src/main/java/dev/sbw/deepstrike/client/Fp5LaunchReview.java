package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.sbw.deepstrike.client.smoke.SmokeGeometryReview;
import dev.sbw.deepstrike.game.Fp5BoosterEntity;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LauncherBlock;
import dev.sbw.deepstrike.game.LauncherBlockEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.MissileLaunchEffects;
import dev.sbw.deepstrike.game.PeacekeeperDynamics;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.model.LauncherLayout;
import dev.sbw.deepstrike.model.MissileGeometry;
import dev.sbw.deepstrike.registry.BFBlocks;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.validation.FlightRealismChecks;
import dev.sbw.deepstrike.vehicle.DeepStrikeVehicleEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent.Pre;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Post;
import dev.sbw.deepstrike.compat.TickEvent.ClientTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Fp5LaunchReview {
   private static final Logger LOG = LoggerFactory.getLogger(Fp5LaunchReview.class);
   private static boolean opening;
   private static boolean requested;
   private static boolean shaderStateReady;
   private static volatile boolean ready;
   private static volatile int missileId = -1;
   private static volatile int boosterId = -1;
   private static volatile int age;
   private static volatile int variant;
   private static volatile Vec3 lastTarget = new Vec3(0.0, 1000.0, 0.0);
   private static GuidedMissileEntity observer;
   private static GuidedMissileEntity serverMissile;
   private static Fp5BoosterEntity serverBooster;
   private static int lastCapture = -1;
   private static int finishedAge;
   private static int warmupTicks;
   private static int measuredAge = -1;
   private static long releaseTime;
   private static String pending;
   private static int lastReviewTickLog = -1;
   private static final boolean POROUS = Boolean.getBoolean("deepstrike.porousSmokeReview");
   private static final boolean INSTALLED = Boolean.getBoolean("deepstrike.installedSmokeReview");
   private static final String REVIEW_WORLD = "DeepStrike Smoke Shader Lab";
   private static final boolean NIGHT = Boolean.getBoolean("deepstrike.reviewNight");
   private static final boolean FAR = Boolean.getBoolean("deepstrike.reviewFar");
   private static final boolean FLIGHT = Boolean.getBoolean("deepstrike.flightRealismReview");
   private static int flightCut = -1;
   private static int flightEnd = -1;
   private static boolean flightWasPowered;
   private static Vec3 flightAim = Vec3.ZERO;
   private static GuidedMissileEntity companion;
   private static Vec3 companionAim = Vec3.ZERO;
   private static Vec3 companionVelocity = Vec3.ZERO;
   private static int companionCut = -1;
   private static boolean companionFinished = false;
   private static double companionPath = 0.0;
   private static Vec3 companionPrevious = Vec3.ZERO;
   private static final int[] FLIGHT_CAPTURES = new int[]{10, 60, 120, 180, 230, 300, 450, 600, 900, 1200, 1600, 2000, 2600, 3200, 4000};
   private static final boolean SURFACE = Boolean.getBoolean("deepstrike.reviewSurface");
   private static final MissileKind KIND = MissileKind.valueOf(
      System.getProperty("deepstrike.reviewMissile", Boolean.getBoolean("deepstrike.largeExhaustReview") ? "peacekeeper" : "fp5").toUpperCase(Locale.ROOT)
   );
   private static final boolean LARGE = KIND == MissileKind.PEACEKEEPER;
   private static final LauncherBlock.Family FAMILY = LARGE
      ? LauncherBlock.Family.PEACEKEEPER
      : (KIND == MissileKind.JAGM ? LauncherBlock.Family.JAGM : LauncherBlock.Family.FP5);
   private static final float ELEVATION = LARGE ? 90.0F : 18.0F;
   private static final boolean FLOW = Boolean.getBoolean("deepstrike.fp5FlowReview");
   private static final boolean VIDEO = Boolean.getBoolean("deepstrike.launchVideoReview");
   private static int lastFlowAge = -2;
   private static int flowFrame;
   private static long flowStart;
   private static Vec3 frozenFlowTarget;
   private static final AtomicInteger flowWrites = new AtomicInteger();
   private static final ExecutorService FLOW_IO = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "fp5-flow-capture");
      t.setDaemon(true);
      return t;
   });
   private static final BlockPos PAD = new BlockPos(0, 101, 0);
   private static final int[] CAPTURES = new int[]{10, 30, 50, 80, 90, 102, 110, 120, 130, 160, 200, 380, 410};
   private static final int[] SURFACE_CAPTURES = new int[]{10, 30, 60, 100, 160, 200, 240, 280, 320, 360, 390, 410, 500, 600};
   private static final int[] LARGE_CAPTURES = new int[]{10, 30, 50, 80, 100, 130, 180, 240};
   private static final Set<String> GUI_PROBES = new HashSet<>();

   private static boolean enabled(Minecraft mc) {
      return Boolean.getBoolean("deepstrike.fp5Review")
         && (
            mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("fp5-launch-options")
               || POROUS && mc.gameDirectory.toPath().getFileName().toString().equals("smoke-porosity-lab")
               || INSTALLED
                  && mc.gameDirectory
                     .toPath()
                     .toAbsolutePath()
                     .normalize()
                     .equals(Path.of(System.getProperty("user.home"), "Library", "Application Support", "minecraft"))
         );
   }

   static void tick(ClientTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase == Phase.END && enabled(mc)) {
         mc.options.pauseOnLostFocus = false;
         if (mc.level == null) {
            if (!opening && mc.screen instanceof TitleScreen) {
               opening = true;
               mc.createWorldOpenFlows().openWorld(INSTALLED ? "DeepStrike Smoke Shader Lab" : "New World", () -> {});
            }
         } else if (mc.player != null && mc.getSingleplayerServer() != null) {
            if (!requested) {
               requested = true;
               mc.setScreen(null);
               mc.options.hideGui = !FLIGHT;
               mc.options.fov().set(55);
               mc.getSingleplayerServer()
                  .execute(
                     () -> {
                        ServerLevel level = mc.getSingleplayerServer().overworld();
                        level.setDayTime(NIGHT ? 18000L : 6000L);
                        ((BooleanValue)level.getGameRules().getRule(GameRules.RULE_DAYLIGHT)).set(false, mc.getSingleplayerServer());
                        level.setWeatherParameters(100000, 0, false, false);
                        ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        p.setGameMode(GameType.SPECTATOR);
                        p.connection.teleport(0.0, 104.0, 20.0, 0.0F, 0.0F);

                        for (int x = -24; x <= 24; x++) {
                           for (int z = -24; z <= 24; z++) {
                              level.setBlock(new BlockPos(x, 100, z), Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), 3);
                           }
                        }

                        if (LARGE && VIDEO) {
                           for (int x = -8; x <= 8; x++) {
                              for (int z = -8; z <= 8; z++) {
                                 for (int y = PAD.getY() + 45; y < level.getMaxBuildHeight(); y++) {
                                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                                 }
                              }
                           }
                        }

                        for (int x = -14; x <= 14; x++) {
                           for (int z = -14; z <= 14; z++) {
                              for (int y = 101; y <= 114; y++) {
                                 level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                              }
                           }
                        }

                        if (SURFACE) {
                           level.setBlock(new BlockPos(-4, 101, 4), Blocks.IRON_BARS.defaultBlockState(), 2);
                           level.setBlock(new BlockPos(4, 102, 4), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);

                           for (int x = -12; x <= 12; x++) {
                              for (int y = 101; y <= 109; y++) {
                                 level.setBlock(new BlockPos(x, y, -7), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
                              }
                           }

                           for (int x = -12; x <= -9; x++) {
                              for (int z = -6; z <= 6; z++) {
                                 level.setBlock(new BlockPos(x, 101, z), Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), 2);
                              }
                           }
                        }

                        level.setBlock(
                           PAD,
                           (BlockState)((LauncherBlock)(LARGE
                                    ? BFBlocks.PEACEKEEPER_PLATFORM
                                    : (KIND == MissileKind.JAGM ? BFBlocks.JAGM_LAUNCHER : BFBlocks.FP5_LAUNCHER))
                                 .get())
                              .defaultBlockState()
                              .setValue(LauncherBlock.FACING, Direction.EAST),
                           3
                        );
                        LauncherBlockEntity be = (LauncherBlockEntity)level.getBlockEntity(PAD);
                        CompoundTag tag = NbtCompat.saveBE(be);
                        tag.putFloat("Elevation", ELEVATION);
                        tag.putFloat("TargetElevation", ELEVATION);
                        NbtCompat.loadBE(be, tag);
                        be.setChanged();
                        level.sendBlockUpdated(PAD, be.getBlockState(), be.getBlockState(), 3);
                        ArrayList<Entity> remove = new ArrayList<>();

                        for (Entity e : level.getAllEntities()) {
                           if (e instanceof GuidedMissileEntity || e instanceof Fp5BoosterEntity || e instanceof DeepStrikeVehicleEntity) {
                              remove.add(e);
                           }
                        }

                        remove.forEach(Entity::discard);
                        if (KIND == MissileKind.SPIKE_NLOS) {
                           level.setBlock(PAD, Blocks.AIR.defaultBlockState(), 3);
                           SpikeNlosEntity vehicle = (SpikeNlosEntity)((EntityType)BFEntities.SPIKE_NLOS.get()).create(level);
                           vehicle.setPos(0.5, 101.0, 0.5);
                           vehicle.setYRot(0.0F);
                           level.addFreshEntity(vehicle);
                        }

                        if (Boolean.getBoolean("deepstrike.apertureReview")) {
                           SmokeApertureReview.build(level);
                        }

                        if (Boolean.getBoolean("deepstrike.smokeColumnReview") && POROUS) {
                           for (Entity entity : level.getAllEntities()) {
                              if (entity instanceof ItemEntity) {
                                 entity.discard();
                              }
                           }
                        }

                        ready = true;
                     }
                  );
            } else if (ready) {
               if (!VIDEO || variant != 0 || warmupTicks++ >= 100) {
                  if (INSTALLED && !shaderStateReady) {
                     shaderStateReady = true;

                     try {
                        Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                        if (Boolean.getBoolean("deepstrike.reviewDisableShadersAfterLoad")) {
                           Object config = iris.getMethod("getIrisConfig").invoke(null);
                           config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, false);
                           config.getClass().getMethod("save").invoke(config);
                           iris.getMethod("reload").invoke(null);
                           warmupTicks = 0;
                        }

                        Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                        Object api = apiClass.getMethod("getInstance").invoke(null);
                        LOG.info(
                           "SMOKE_REVIEW_SHADER_STATE active={} night={} far={} case={}",
                           new Object[]{apiClass.getMethod("isShaderPackInUse").invoke(api), NIGHT, FAR, System.getProperty("deepstrike.reviewCase")}
                        );
                     } catch (ReflectiveOperationException var7) {
                        throw new IllegalStateException("Cannot establish installed review shader state", var7);
                     }
                  } else if (System.getProperty("deepstrike.reviewCase", "").startsWith("model-seams")) {
                     ModelSeamReview.tick(mc);
                  } else if (System.getProperty("deepstrike.reviewCase", "").startsWith("fp5-dorsal")) {
                     Fp5DorsalReview.tick(mc);
                  } else if (System.getProperty("deepstrike.reviewCase", "").startsWith("ground-dud")) {
                     GroundDudReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.patriotCanisterReview")) {
                     PatriotCanisterReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.opticalVisibilityReview")) {
                     OpticalVisibilityReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.blastAcousticReview")) {
                     AcousticVisualReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.commandTabletReview")) {
                     if (Boolean.getBoolean("deepstrike.commandSalvoReview")) {
                        SalvoFlightReview.tick(mc);
                     } else if (Boolean.getBoolean("deepstrike.commandRemoteReview")) {
                        CommandRemoteCameraReview.tick(mc);
                     } else {
                        CommandTabletReview.tick(mc);
                     }
                  } else if (Boolean.getBoolean("deepstrike.guiCaptureReview")) {
                     GuiCaptureReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.routeEffectsReview")) {
                     RouteEffectsReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.blastVisualReview")) {
                     BlastVisualReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.airDefenseReview")) {
                     AirDefenseVisualReview.tick(mc);
                  } else if (Boolean.getBoolean("deepstrike.apertureReview")) {
                     SmokeApertureReview.tick(mc);
                  } else if (variant != 0 || !SURFACE || mc.level.getBlockState(new BlockPos(10, 100, 8)).is(Blocks.LIGHT_GRAY_CONCRETE)) {
                     if (variant == 0) {
                        float previous = 1.0F;

                        for (int i = 0; i <= 400; i++) {
                           float alpha = SmokeAppearance.dissipation(i / 400.0F);
                           if (alpha > previous || alpha < 0.0F || alpha > 1.0F) {
                              throw new IllegalStateException("Smoke dissipation must decrease continuously");
                           }

                           previous = alpha;
                        }

                        if (SmokeAppearance.dissipation(0.0F) == 1.0F && SmokeAppearance.dissipation(1.0F) == 0.0F) {
                           LOG.info(
                              "SMOKE_DISSIPATION seconds=0,5,10,15,18,20 alpha=1,{},{},{},{},0",
                              new Object[]{
                                 SmokeAppearance.dissipation(0.25F),
                                 SmokeAppearance.dissipation(0.5F),
                                 SmokeAppearance.dissipation(0.75F),
                                 SmokeAppearance.dissipation(0.9F)
                              }
                           );
                           if (FLIGHT) {
                              FlightRealismChecks.run();
                           }

                           if (POROUS && SURFACE) {
                              SmokeGeometryReview.run(mc);
                              FlightLighting.reviewVisibility();
                           }

                           variant = Math.max(1, Integer.getInteger("deepstrike.fp5ReviewVariant", 0));
                           spawn(mc);
                        } else {
                           throw new IllegalStateException("Smoke dissipation lifetime endpoints");
                        }
                     } else {
                        Fp5LaunchEffects.previewVariant = variant;
                        if (LARGE && age != lastReviewTickLog && mc.level.getGameTime() % 40L == 0L) {
                           lastReviewTickLog = age;
                           LOG.info("EXHAUST_REVIEW_CLIENT age={} variant={} pending={}", new Object[]{age, variant, pending});
                        }

                        int[] captures = FLIGHT ? FLIGHT_CAPTURES : (SURFACE ? SURFACE_CAPTURES : (LARGE ? LARGE_CAPTURES : CAPTURES));
                        if (Boolean.getBoolean("deepstrike.reviewNoCaptures")) {
                           if (age / 20 != measuredAge) {
                              measuredAge = age / 20;
                              LOG.info("SMOKE_FPS_NO_READBACK age={} fps={}", age, mc.getFps());
                           }
                        } else {
                           for (int capture : captures) {
                              if (pending == null && age >= capture && lastCapture < capture) {
                                 lastCapture = capture;
                                 pending = KIND.id + "-v" + variant + "-t" + capture;
                                 break;
                              }
                           }
                        }

                        if ((FLIGHT ? flightEnd >= 0 && age >= flightEnd + 50 || age > 6000 : age >= (SURFACE ? 620 : (LARGE ? 260 : 420)))
                           && pending == null
                           && flowWrites.get() == 0) {
                           if (FLIGHT && (flightEnd < 0 || flightCut < 0 || LARGE && !companionFinished)) {
                              throw new IllegalStateException("Flight review did not complete cutoff/impact");
                           }

                           if (variant == 5 || Integer.getInteger("deepstrike.fp5ReviewVariant", 0) > 0) {
                              FlightAuthorityReview.requireComplete();
                              FlightMotionReview.finish();
                              LOG.info("FP5_OPTIONS_FINISHED selectedVariant={} flowFrames={}", variant, flowFrame);
                              mc.stop();
                              return;
                           }

                           variant++;
                           lastCapture = -1;
                           spawn(mc);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void spawn(Minecraft mc) {
      age = 0;
      boosterId = -1;
      finishedAge = 0;
      mc.getSingleplayerServer()
         .execute(
            () -> {
               if (serverMissile != null) {
                  if (serverMissile.isAlive()) {
                     serverMissile.discard();
                  }

                  Fp5BoosterEntity.forget(serverMissile);
               }

               serverBooster = null;
               ServerLevel level = mc.getSingleplayerServer().overworld();
               GuidedMissileEntity missile = new GuidedMissileEntity(level, KIND);
               Matrix4f mount = LauncherLayout.mount(FAMILY, Direction.EAST.toYRot(), 0.0F, ELEVATION, 0);
               Vec3 start = LARGE && !VIDEO ? new Vec3(PAD.getX() + 0.5, 1000.0, PAD.getZ() + 0.5) : LauncherLayout.worldPosition(PAD, mount);
               Vec3 direction = LauncherLayout.forward(FAMILY, mount);
               if (KIND == MissileKind.SPIKE_NLOS) {
                  start = new Vec3(0.5, 145.0, -0.65);
                  direction = new Vec3(0.0, 1.0, 0.0);
               }

               ServerPlayer player = (ServerPlayer)level.players().stream().findFirst().orElse(null);
               if (LARGE && player != null) {
                  player.teleportTo(start.x, start.y - 30.0, start.z + 24.0);
               }

               missile.setPos(start.x, start.y, start.z);
               missile.setLaunchMaster(PAD);
               missile.setMissionId(UUID.randomUUID());
               missile.setWarhead("none");
               Vec3 target = LARGE ? start.add(3000.0, 0.0, 0.0) : (FLIGHT ? start.add(1200.0, 30.0, 650.0) : new Vec3(12000.0, 1000.0, 0.0));
               flightAim = target;
               flightWasPowered = true;
               flightCut = -1;
               flightEnd = -1;
               double speed = LARGE ? 5500.0 : (KIND == MissileKind.FP5 ? 600.0 : KIND.defaultSpeed);
               missile.setMission(
                  FLIGHT && !LARGE
                     ? List.of(
                        new RoutePoint(start.x + 650.0, start.y + 30.0, start.z, speed),
                        new RoutePoint(start.x + 650.0, start.y + 30.0, start.z + 650.0, speed),
                        new RoutePoint(target.x, target.y, target.z, speed)
                     )
                     : List.of(new RoutePoint(target.x, target.y, target.z, speed)),
                  LARGE
               );
               if (LARGE) {
                  PeacekeeperDynamics.Selection selection = PeacekeeperDynamics.select(start, target, speed);
                  if (!selection.valid()) {
                     throw new IllegalStateException("Peacekeeper review route invalid: " + selection.reason());
                  }

                  missile.setPeacekeeperPlan(selection.plan());
               }

               if (SURFACE || VIDEO) {
                  MissileLaunchEffects.spawnLaunchPlume(level, start, direction, LARGE);
               }

               if (KIND == MissileKind.SPIKE_NLOS) {
                  missile.configureSpike(start.y + 60.0, speed);
               }

               missile.setLaunchVector(direction, KIND == MissileKind.SPIKE_NLOS ? 20 : 12);
               missile.setDeltaMovement(direction.scale(0.08));
               missile.setYRot((float)Math.toDegrees(Math.atan2(-direction.x, direction.z)));
               missile.setXRot((float)(-Math.toDegrees(Math.asin(direction.y))));
               level.addFreshEntity(missile);
               missileId = missile.getId();
               serverMissile = missile;
               if (FLIGHT && LARGE) {
                  companion = new GuidedMissileEntity(level, MissileKind.FP5);
                  Vec3 a = start.add(80.0, 20.0, 80.0);
                  Vec3 d = new Vec3(Math.cos(Math.toRadians(18.0)), Math.sin(Math.toRadians(18.0)), 0.0);
                  companionAim = a.add(1200.0, 30.0, 650.0);
                  companionPrevious = a;
                  companion.setPos(a);
                  companion.setMissionId(UUID.randomUUID());
                  companion.setWarhead("none");
                  companion.setMission(
                     List.of(
                        new RoutePoint(a.x + 650.0, a.y + 30.0, a.z, 600.0),
                        new RoutePoint(a.x + 650.0, a.y + 30.0, a.z + 650.0, 600.0),
                        new RoutePoint(companionAim.x, companionAim.y, companionAim.z, 600.0)
                     ),
                     false
                  );
                  companion.setLaunchVector(d, 12);
                  companion.setDeltaMovement(d.scale(0.08));
                  companion.setYRot(-90.0F);
                  companion.setXRot(-18.0F);
                  level.addFreshEntity(companion);
                  LOG.info("FLIGHT_COMPANION_LAUNCH FP5 {} -> {}", a, companionAim);
               }

               LOG.info(
                  "EXHAUST_REVIEW_LAUNCH kind={} large={} variant={} id={} start={} target={} speed={}",
                  new Object[]{KIND, LARGE, variant, missileId, start, target, speed}
               );
            }
         );
   }

   static void serverTick(ServerTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.phase == Phase.END && enabled(mc) && ready && Boolean.getBoolean("deepstrike.routeEffectsReview")) {
         RouteEffectsReview.serverTick(mc);
      } else if (event.phase == Phase.END && enabled(mc) && ready && serverMissile != null) {
         ServerLevel level = mc.getSingleplayerServer().overworld();
         if (FLIGHT && companion != null && !companionFinished) {
            if (!companion.isAlive()) {
               double lead = (companion.tickCount - companionCut) / 20.0;
               LOG.info(
                  "FLIGHT_COMPANION_END age={} lead={} error={} path={}",
                  new Object[]{companion.tickCount, lead, companion.position().distanceTo(companionAim), companionPath}
               );
               if (companionCut < 0 || lead < 1.5 || lead > 2.5 || companion.position().distanceTo(companionAim) > 12.0 || companionPath > 2200.0) {
                  throw new IllegalStateException("FP5 companion route/cutoff failed");
               }

               companionFinished = true;
            } else {
               companionPath = companionPath + companion.position().distanceTo(companionPrevious);
               companionPrevious = companion.position();
               if (!companion.engineRunning() && companionCut < 0) {
                  companionCut = companion.tickCount;
                  LOG.info("FLIGHT_COMPANION_CUTOFF age={}", companionCut);
               }

               if (companionVelocity.lengthSqr() > 1.0E-8 && companion.getDeltaMovement().lengthSqr() > 1.0E-8) {
                  double angle = Math.toDegrees(Math.acos(Mth.clamp(companionVelocity.normalize().dot(companion.getDeltaMovement().normalize()), -1.0, 1.0)));
                  if (angle > 3.51) {
                     throw new IllegalStateException("FP5 live sudden turn " + angle);
                  }
               }

               companionVelocity = companion.getDeltaMovement();
            }
         }

         if (!serverMissile.isAlive()) {
            Entity restored = level.getEntity(serverMissile.getUUID());
            if (restored instanceof GuidedMissileEntity live && live.isAlive()) {
               LOG.info("EXHAUST_REVIEW_RESTORED oldId={} newId={} age={}", new Object[]{missileId, live.getId(), live.tickCount});
               serverMissile = live;
               missileId = live.getId();
            } else if (level.getGameTime() % 40L == 0L) {
               LOG.error("EXHAUST_REVIEW_MISSING age={} reason={} restored={}", new Object[]{serverMissile.tickCount, serverMissile.getRemovalReason(), restored});
            }
         }

         if (FLIGHT && !serverMissile.isAlive()) {
            if (flightEnd < 0) {
               flightEnd = serverMissile.tickCount;
               double lead = (flightEnd - flightCut) / 20.0;
               LOG.info(
                  "FLIGHT_LIVE_END kind={} age={} cutoffLead={} error={} reason={}",
                  new Object[]{KIND, flightEnd, lead, serverMissile.position().distanceTo(flightAim), serverMissile.getRemovalReason()}
               );
               if (flightCut < 0 || (LARGE ? lead < 10.0 : Math.abs(lead - 2.0) > 0.55) || serverMissile.position().distanceTo(flightAim) > 12.0) {
                  throw new IllegalStateException("Flight live impact/cutoff mismatch");
               }
            }

            age++;
         } else {
            age = serverMissile.tickCount;
            if (FLIGHT) {
               if (flightWasPowered && !serverMissile.engineRunning()) {
                  flightCut = age;
                  LOG.info("FLIGHT_LIVE_CUTOFF kind={} age={} pos={}", new Object[]{KIND, age, serverMissile.position()});
                  if (pending == null) {
                     pending = KIND.id + "-cutoff-" + age;
                  }
               }

               flightWasPowered = serverMissile.engineRunning();
               lastTarget = serverMissile.position();
            }

            if (KIND == MissileKind.STORM_SHADOW && (age == 1 || age == 99 || age == 100 || age == 101)) {
               LOG.info(
                  "STORM_SHADOW_LAUNCH_PHASE age={} boostBurning={} detachableBooster={} engine={}",
                  new Object[]{age, serverMissile.launchBoostBurning(), serverMissile.fp5BoosterAttached(), serverMissile.engineRunning()}
               );
            }

            if (serverBooster == null && age >= 130) {
               serverBooster = Fp5BoosterEntity.releasedFrom(serverMissile);
               if (serverBooster != null) {
                  boosterId = serverBooster.getId();
               }

               if (serverBooster != null) {
                  releaseTime = level.getGameTime();
                  LOG.info("FP5_REVIEW_RELEASE variant={} missileAge={} boosterAge={}", new Object[]{variant, age, serverBooster.tickCount});
               }
            }

            if (serverBooster != null && serverBooster.isRemoved() && finishedAge == 0) {
               finishedAge = age;
               LOG.info(
                  "FP5_REVIEW_EXPIRED variant={} missileAge={} boosterAge={} elapsedWorldTicks={}",
                  new Object[]{variant, age, serverBooster.tickCount, level.getGameTime() - releaseTime}
               );
            }

            if (FLIGHT || age < 100 || KIND != MissileKind.FP5) {
               lastTarget = serverMissile.position();
            } else if (serverBooster != null && serverBooster.isAlive()) {
               lastTarget = serverBooster.position();
            }

            if (age % 20 == 0) {
               LOG.info(
                  "EXHAUST_REVIEW_STATE kind={} age={} alive={} engine={} pos={}",
                  new Object[]{KIND, age, serverMissile.isAlive(), serverMissile.engineRunning(), serverMissile.position()}
               );
               ServerPlayer player = (ServerPlayer)level.players().stream().findFirst().orElse(null);
               if (player != null) {
                  player.teleportTo(
                     SURFACE && !FLIGHT ? 20.0 : lastTarget.x,
                     SURFACE && !FLIGHT ? 109.0 : lastTarget.y + 4.0,
                     SURFACE && !FLIGHT ? 28.0 : lastTarget.z + 18.0
                  );
               }
            }

            if (age == 89
               || age == 90
               || age == 91
               || age == 99
               || age == 101
               || age == 110
               || age == 129
               || age == 130
               || age == 131
               || age == 140
               || age == 160
               || age == 200
               || age == 399
               || age == 401) {
               LOG.info(
                  "FP5_REVIEW_STATE v={} age={} attached={} boosterAlive={} boosterY={} burning={} missileVelocity={} boosterVelocity={}",
                  new Object[]{
                     variant,
                     age,
                     serverMissile.fp5BoosterAttached(),
                     serverBooster != null && serverBooster.isAlive(),
                     serverBooster == null ? 0.0 : serverBooster.getY(),
                     serverMissile.fp5BoosterBurning(),
                     serverMissile.getDeltaMovement(),
                     serverBooster == null ? Vec3.ZERO : serverBooster.getDeltaMovement()
                  }
               );
            }
         }
      }
   }

   static void render(RenderTickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (enabled(mc) && mc.level != null && mc.player != null) {
         if (System.getProperty("deepstrike.reviewCase", "").startsWith("model-seams")) {
            ModelSeamReview.render(mc, event);
         } else if (System.getProperty("deepstrike.reviewCase", "").startsWith("fp5-dorsal")) {
            Fp5DorsalReview.render(mc, event);
         } else if (System.getProperty("deepstrike.reviewCase", "").startsWith("ground-dud")) {
            GroundDudReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.patriotCanisterReview")) {
            PatriotCanisterReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.opticalVisibilityReview")) {
            OpticalVisibilityReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.blastAcousticReview")) {
            AcousticVisualReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.commandTabletReview")) {
            if (Boolean.getBoolean("deepstrike.commandSalvoReview")) {
               SalvoFlightReview.render(mc, event);
            } else if (Boolean.getBoolean("deepstrike.commandRemoteReview")) {
               CommandRemoteCameraReview.render(mc, event);
            } else {
               CommandTabletReview.render(mc, event);
            }
         } else if (Boolean.getBoolean("deepstrike.guiCaptureReview")) {
            GuiCaptureReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.routeEffectsReview")) {
            RouteEffectsReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.blastVisualReview")) {
            BlastVisualReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.airDefenseReview")) {
            AirDefenseVisualReview.render(mc, event);
         } else if (Boolean.getBoolean("deepstrike.apertureReview")) {
            SmokeApertureReview.render(mc, event);
         } else if (variant != 0) {
            if (event.phase == Phase.START) {
               if (observer == null) {
                  observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
               }

               Vec3 target = lastTarget;
               GuidedMissileEntity missile = RemoteFlights.get(missileId);
               if ((age < 100 || age >= 140 && age < 300) && missile != null) {
                  target = missile.position().add(-1.0, 0.0, 0.0);
               } else if (mc.level.getEntity(boosterId) instanceof Fp5BoosterEntity booster) {
                  target = booster.position();
                  if (age <= 115 && missile != null) {
                     target = target.add(missile.position()).scale(0.5);
                  }
               }

               if (VIDEO && missile != null) {
                  target = missile.position();
               }

               if (FLOW && age <= 220) {
                  Vec3 anchor = ContrailRenderer.flowReviewAnchor(missileId);
                  if (age < 80) {
                     if (anchor != null) {
                        target = anchor.add(missile != null ? missile.getDeltaMovement().normalize().scale(15.0) : Vec3.ZERO);
                     }
                  } else {
                     if (frozenFlowTarget == null) {
                        frozenFlowTarget = anchor != null ? anchor : target;
                     }

                     target = frozenFlowTarget;
                  }
               }

               double distance = 18.0;
               if (age >= 100 && age <= 115 && missile != null) {
                  distance = Math.max(18.0, missile.position().distanceTo(target) * 2.3);
               }

               Vec3 position = target.add(3.0, age < 20 ? 7.0 : 2.0, age < 20 ? 50.0 : distance);
               if (FLOW && age <= 220) {
                  position = target.add(3.0, 8.0, age < 80 ? 30.0 : 55.0);
               }

               if (LARGE && missile != null) {
                  Vec3 var13 = MissileGeometry.worldCenter(KIND, missile.position(), missile.getYRot(), missile.getXRot());
                  target = var13.subtract(missile.getDeltaMovement().normalize().scale(12.0));
                  position = target.add(12.0, 6.0, 42.0);
               }

               if (INSTALLED && Boolean.getBoolean("deepstrike.reviewCloudBackdrop")) {
                  position = target.add(3.0, -10.0, FAR ? 350.0 : distance);
               }

               if (FAR) {
                  position = target.add(
                     30.0,
                     Boolean.getBoolean("deepstrike.reviewCloudBackdrop") ? -100.0 : 15.0,
                     Double.parseDouble(System.getProperty("deepstrike.reviewDistance", "350"))
                  );
                  mc.options.fov().set(30);
               }

               if (INSTALLED) {
                  position = new Vec3(position.x, Math.max(FAR ? 180.0 : 105.0, position.y), position.z);
               }

               if (SURFACE) {
                  target = new Vec3(0.0, 104.0, 0.0);
                  position = new Vec3(20.0, 111.0, 28.0);
                  mc.options.fov().set(60);
               }

               if (FLIGHT) {
                  target = lastTarget;
                  position = FAR
                     ? target.add(30.0, 15.0, Double.parseDouble(System.getProperty("deepstrike.reviewDistance", "350")))
                     : target.add(age < 100 ? 20.0 : (age < 200 ? 350.0 : (age < 260 ? -350.0 : 45.0)), 12.0, 40.0);
                  mc.options.fov().set(60);
               }

               observer.setPos(position);
               Vec3 d = target.subtract(observer.getEyePosition());
               observer.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
               observer.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
               observer.xo = position.x;
               observer.yo = position.y;
               observer.zo = position.z;
               observer.yRotO = observer.getYRot();
               observer.xRotO = observer.getXRot();
               mc.setCameraEntity(observer);
            } else {
               if (!Boolean.getBoolean("deepstrike.reviewNoMotion")
                  && (FLOW && age <= 220 && age >= lastFlowAge + 2 || VIDEO && age <= (SURFACE ? 410 : (LARGE ? 250 : 410)) && age > lastFlowAge)
                  && flowWrites.get() < 6) {
                  captureFlow(mc);
               }

               if (pending != null) {
                  String name = pending;
                  pending = null;

                  try {
                     NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

                     try {
                        Path dir = mc.gameDirectory
                           .toPath()
                           .resolve(INSTALLED ? "screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase", "review") : "screenshots");
                        Files.createDirectories(dir);
                        if (FLIGHT) {
                           int dark = 0;
                           int total = 0;

                           for (int px = 4; px < shot.getWidth(); px += 16) {
                              for (int py = 4; py < shot.getHeight(); py += 16) {
                                 int rgb = shot.getPixelRGBA(px, py);
                                 if ((rgb & 0xFF) < 5 && (rgb >> 8 & 0xFF) < 5 && (rgb >> 16 & 0xFF) < 5) {
                                    dark++;
                                 }

                                 total++;
                              }
                           }

                           LOG.info("FLIGHT_SCREEN_CHECK name={} blackFraction={}", name, (double)dark / total);
                           if (dark > total * (NIGHT ? 0.98 : 0.9)) {
                              throw new IllegalStateException("Black screen during flight " + name);
                           }
                        }

                        shot.writeToFile(dir.resolve(name + ".png"));
                        LOG.info("FP5_REVIEW_CAPTURE {} actualAge={} fps={}", new Object[]{name, age, mc.getFps()});
                     } catch (Throwable var11) {
                        if (shot != null) {
                           try {
                              shot.close();
                           } catch (Throwable var10) {
                              var11.addSuppressed(var10);
                           }
                        }

                        throw var11;
                     }

                     if (shot != null) {
                        shot.close();
                     }
                  } catch (Exception var12) {
                     throw new RuntimeException(var12);
                  }
               }
            }
         }
      }
   }

   private static void captureFlow(Minecraft mc) {
      int capturedAge = age;
      lastFlowAge = capturedAge;
      int index = flowFrame++;
      long now = System.nanoTime();
      if (flowStart == 0L) {
         flowStart = now;
      }

      double seconds = (now - flowStart) * 1.0E-9;
      NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

      try {
         NativeImage small = new NativeImage(960, 540, false);
         shot.resizeSubRectTo(0, 0, shot.getWidth(), shot.getHeight(), small);
         Path dir = mc.gameDirectory
            .toPath()
            .resolve(INSTALLED ? "screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase", "review") + "/motion" : "motion");
         flowWrites.incrementAndGet();
         FLOW_IO.execute(() -> {
            try {
               NativeImage e = small;

               try {
                  Files.createDirectories(dir);
                  small.writeToFile(dir.resolve(String.format("flow-%05d.png", index)));
                  LOG.info("FP5_FLOW_FRAME index={} age={} seconds={}", new Object[]{index, capturedAge, seconds});
               } catch (Throwable var15) {
                  if (small != null) {
                     try {
                        e.close();
                     } catch (Throwable var14) {
                        var15.addSuppressed(var14);
                     }
                  }

                  throw var15;
               }

               if (small != null) {
                  small.close();
               }
            } catch (Exception var16) {
               LOG.error("FP5_FLOW_CAPTURE_FAILED", var16);
            } finally {
               flowWrites.decrementAndGet();
            }
         });
      } catch (Throwable var11) {
         if (shot != null) {
            try {
               shot.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }
         }

         throw var11;
      }

      if (shot != null) {
         shot.close();
      }
   }

   private Fp5LaunchReview() {
   }

   static void guiProbe(Pre event) {
      if (FLIGHT && age >= 60 && age <= 120) {
         guiProbeFrame("before-hud");
      }
   }

   static void vignetteProbe(Post event) {
      if (FLIGHT && age >= 60 && age <= 120 && event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CAMERA_OVERLAYS)) {
         guiProbeFrame("after-vignette");
      }
   }

   private static void guiProbeFrame(String stage) {
      if (GUI_PROBES.add(stage)) {
         Minecraft mc = Minecraft.getInstance();
         LOG.info(
            "FLIGHT_GUI_PROBE stage={} age={} blend={} src={} dst={} shader={}",
            new Object[]{stage, age, GL30.glIsEnabled(3042), GL30.glGetInteger(32969), GL30.glGetInteger(32968), RenderSystem.getShader()}
         );

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(dir);
               shot.writeToFile(dir.resolve(stage + ".png"));
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
