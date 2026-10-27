package dev.sbw.deepstrike.client;

import dev.sbw.deepstrike.compat.NbtCompat;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LaunchCoverEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.model.PatriotCanisterLayout;
import dev.sbw.deepstrike.network.PatriotDefenseNetwork;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PatriotCanisterReview {
   private static final Logger LOG = LoggerFactory.getLogger(PatriotCanisterReview.class);
   private static volatile PatriotTruckEntity truck;
   private static volatile PatriotInterceptorEntity current;
   private static volatile int age;
   private static volatile int shot = -1;
   private static volatile Vec3 mouth = Vec3.ZERO;
   private static volatile Vec3 axis = new Vec3(0.0, 1.0, 0.0);
   private static volatile Throwable failure;
   private static long start;
   private static GuidedMissileEntity observer;
   private static final Set<String> captures = new HashSet<>();
   private static GuidedMissileEntity pendingTarget;
   private static Vec3 previousDirection;
   private static double travelled;
   private static double maxElevation;
   private static double maxStep;
   private static double firstTurnAt = Double.NaN;
   private static double departureStepMax;
   private static int expectedCell;
   private static boolean boot;
   private static volatile boolean finished;
   private static volatile boolean clientReady;
   private static final List<PatriotInterceptorEntity> missiles = new ArrayList<>();
   private static final List<GuidedMissileEntity> targets = new ArrayList<>();
   private static final double[] closest = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
   private static final ExecutorService VIDEO_IO = Executors.newFixedThreadPool(2, r -> {
      Thread t = new Thread(r, "PAC review images");
      t.setDaemon(true);
      return t;
   });
   private static final AtomicInteger writes = new AtomicInteger();
   private static int frame;
   private static long lastFrame;
   private static final StringBuilder frames = new StringBuilder("frame,nanos,shot,launchAge\n");

   static void tick(Minecraft mc) {
      if (failure != null) {
         throw new IllegalStateException("Patriot canister review", failure);
      } else if (finished && writes.get() == 0) {
         try {
            Path path = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase") + "/frames.csv");
            Files.createDirectories(path.getParent());
            Files.writeString(path, frames);
         } catch (Exception var2) {
            throw new IllegalStateException(var2);
         }

         LOG.info("PAC_CANISTER_FINISHED videoFrames={} FP5_OPTIONS_FINISHED", frame);
         mc.stop();
      } else if (boot) {
         if (truck != null && mc.level.getEntity(truck.getId()) != null) {
            clientReady = true;
         }
      } else {
         boot = true;
         NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) -> {
            if (observer != null) {
               event.setYaw(observer.getYRot());
               event.setPitch(observer.getXRot());
               event.setRoll(0.0F);
               ClientCamera.moveCameraToNose(event.getCamera(), observer.getEyePosition(), observer.getYRot(), observer.getXRot());
            }
         });
         mc.getSingleplayerServer()
            .execute(
               () -> {
                  try {
                     ServerLevel level = mc.getSingleplayerServer().overworld();
                     ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                     player.teleportTo(level, 0.0, 101.0, 12.0, 0.0F, 0.0F);
                     level.getChunk(0, 0);
                     truck = (PatriotTruckEntity)((EntityType)BFEntities.PATRIOT_TRUCK.get()).create(level);
                     truck.setPos(0.0, 101.0, 0.0);
                     truck.setYRot(0.0F);
                     truck.setNoGravity(true);
                     CompoundTag tag = truck.saveWithoutId(new CompoundTag());
                     tag.putInt("DeployState", 2);
                     tag.putInt("DeployTicks", 160);
                     tag.putInt("ReadyPAC2", 2);
                     tag.putInt("ReadyPAC3", 2);
                     truck.load(tag);
                     level.addFreshEntity(truck);
                     IronDomeEntity radar = (IronDomeEntity)((EntityType)BFEntities.IRON_DOME.get()).create(level);
                     radar.setPos(10.0, 101.0, 0.0);
                     radar.setAutoDefense(false);
                     radar.setNoGravity(true);
                     level.addFreshEntity(radar);
                     ItemStack terminal = new ItemStack((ItemLike)BFItems.AIR_DEFENSE_TERMINAL.get());
                     NbtCompat.update(terminal, t -> t.putUUID("TargetUUID", radar.getUUID()));
                     NbtCompat.update(terminal, t -> t.putString("TargetName", "Iron Dome"));
                     truck.setItem(0, terminal);
                     truck.refreshDefenseInventory();
                     start = level.getGameTime();
                     LOG.info(
                        "PAC_CANISTER_REVIEW_READY cells={},{},{},{}",
                        new Object[]{truck.loadedCellType(0), truck.loadedCellType(1), truck.loadedCellType(2), truck.loadedCellType(3)}
                     );
                  } catch (Throwable var6) {
                     failure = var6;
                  }
               }
            );
      }
   }

   static void serverTick(ServerTickEvent event) {
      if (Boolean.getBoolean("deepstrike.patriotCanisterReview") && event.phase == Phase.END && truck != null && failure == null && !finished) {
         try {
            ServerLevel level = (ServerLevel)truck.level();
            if (!clientReady) {
               start = level.getGameTime();
               return;
            }

            age = (int)(level.getGameTime() - start);

            for (int i = 0; i < missiles.size(); i++) {
               closest[i] = Math.min(closest[i], missiles.get(i).position().distanceTo(targets.get(i).position()));
            }

            if (current != null && current.isAlive()) {
               Vec3 velocity = current.getDeltaMovement();
               if (velocity.lengthSqr() > 1.0E-8) {
                  Vec3 direction = velocity.normalize();
                  double nose = PatriotCanisterLayout.nose(current.warhead());
                  double clearance = travelled - nose - 0.08;
                  if (clearance < 8.0 && direction.distanceTo(axis) > 1.0E-6) {
                     throw new IllegalStateException("PAC turned inside 8m clearance: " + clearance);
                  }

                  if (clearance < 8.0 && velocity.length() > 1.25001) {
                     throw new IllegalStateException("PAC departure exceeded 25m/s: " + velocity.length() * 20.0);
                  }

                  double step = previousDirection == null
                     ? 0.0
                     : Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, previousDirection.dot(direction)))));
                  if (clearance < 80.0) {
                     departureStepMax = Math.max(departureStepMax, step);
                  }

                  if (Double.isNaN(firstTurnAt) && direction.distanceTo(axis) > 1.0E-5) {
                     firstTurnAt = clearance;
                  }

                  maxStep = Math.max(maxStep, step);
                  maxElevation = Math.max(maxElevation, Math.toDegrees(Math.asin(direction.y)));
                  travelled = travelled + velocity.length();
                  previousDirection = direction;
               }
            }

            if (age >= 40 && age < 680 && (age - 40) % 160 == 0) {
               pendingTarget = new GuidedMissileEntity(level, MissileKind.JAGM);
               pendingTarget.setPos(150.0, 360.0, 800.0);
               pendingTarget.setWarhead("none");
               pendingTarget.setMissionId(UUID.randomUUID());
               pendingTarget.setMission(List.of(new RoutePoint(150.0, 360.0, 5000.0, 150.0)), false);
               pendingTarget.setLaunchVector(new Vec3(0.0, 0.0, 1.0), 0);
               level.getChunkAt(pendingTarget.blockPosition());
               level.addFreshEntity(pendingTarget);
            }

            if (age >= 60 && age < 700 && (age - 60) % 160 == 0) {
               if (shot >= 0) {
                  verifyDeparture();
               }

               shot++;
               int type = shot % 2 == 0 ? 1 : 0;
               expectedCell = truck.launchCell(type);
               GuidedMissileEntity target = pendingTarget;
               long coversBefore = StreamSupport.<Entity>stream(level.getAllEntities().spliterator(), false).filter(ex -> ex instanceof LaunchCoverEntity).count();
               ServerPlayer player = (ServerPlayer)level.getServer().getPlayerList().getPlayers().get(0);
               if (!PatriotDefenseNetwork.fireFromCommand(player, truck, target.getUUID(), type)) {
                  throw new IllegalStateException(
                     "Actual radar/command launch rejected radar="
                        + truck.radarConnected()
                        + " deployed="
                        + truck.isDeployed()
                        + " ready="
                        + truck.totalReady()
                        + " contacts="
                        + truck.scan(level).size()
                        + " target="
                        + target.getUUID()
                  );
               }

               current = null;

               for (Entity entity : level.getAllEntities()) {
                  if (entity instanceof PatriotInterceptorEntity pac && pac.targetId().equals(target.getUUID())) {
                     current = pac;
                  }
               }

               if (current == null) {
                  throw new IllegalStateException("No actual server interceptor");
               }

               long coversAfter = StreamSupport.<Entity>stream(level.getAllEntities().spliterator(), false).filter(ex -> ex instanceof LaunchCoverEntity).count();
               if (coversAfter != coversBefore) {
                  throw new IllegalStateException("Patriot ejected a whole cover");
               }

               missiles.add(current);
               targets.add(target);
               CompoundTag saved = truck.saveWithoutId(new CompoundTag());
               PatriotTruckEntity restored = (PatriotTruckEntity)((EntityType)BFEntities.PATRIOT_TRUCK.get()).create(level);
               restored.load(saved);

               for (int cell = 0; cell < 4; cell++) {
                  if (truck.loadedCellType(cell) != restored.loadedCellType(cell) || truck.tornCell(cell) != restored.tornCell(cell)) {
                     throw new IllegalStateException("Cell state lost in NBT");
                  }
               }

               if (truck.loadedCellType(expectedCell) != -1 || !truck.tornCell(expectedCell)) {
                  throw new IllegalStateException("Wrong cell opened");
               }

               Vec3 local = PatriotCanisterLayout.elevatedMouth(expectedCell);
               Matrix4d transform = truck.getVehicleTransform(1.0F);
               Vector4d p = truck.transformPosition(transform, local.x, local.y, local.z);
               mouth = new Vec3(p.x, p.y, p.z);
               Vec3 localAxis = PatriotCanisterLayout.axis();
               Vector3d transformedAxis = transform.transformDirection(new Vector3d(localAxis.x, localAxis.y, localAxis.z));
               axis = new Vec3(transformedAxis.x, transformedAxis.y, transformedAxis.z).normalize();
               Vec3 expected = mouth.subtract(axis.scale(PatriotCanisterLayout.nose(type) + 0.08));
               if (expected.distanceTo(current.position()) > 1.0E-5) {
                  throw new IllegalStateException("Missile did not start inside its cell: expected=" + expected + " actual=" + current.position());
               }

               travelled = 0.0;
               maxElevation = 0.0;
               maxStep = 0.0;
               firstTurnAt = Double.NaN;
               departureStepMax = 0.0;
               previousDirection = null;
               LOG.info("PAC_CANISTER_LAUNCH shot={} type={} cell={} mouth={} origin={}", new Object[]{shot, type, expectedCell, mouth, current.position()});
            }

            if (age > 730) {
               verifyDeparture();
               if (truck.totalReady() != 0) {
                  throw new IllegalStateException("Mixed load was not fully spent");
               }

               for (int cellx = 0; cellx < 4; cellx++) {
                  if (!truck.tornCell(cellx)) {
                     throw new IllegalStateException("Missing torn membrane " + cellx);
                  }
               }

               for (int i = 0; i < 4; i++) {
                  LOG.info("PAC_CANISTER_TARGET_APPROACH shot={} minimumCentreDistance={}", i, closest[i]);
                  if (closest[i] > 30.0) {
                     throw new IllegalStateException("PAC did not approach target: shot=" + i + " distance=" + closest[i]);
                  }
               }

               LOG.info("PAC_CANISTER_REVIEW_PASS shots=4 mixedCells=true attachedPetals=true persistence=true wholeCovers=0");
               finished = true;
            }
         } catch (Throwable var17) {
            failure = var17;
            LOG.error("PAC_CANISTER_REVIEW_FAILED", var17);
         }
      }
   }

   private static void verifyDeparture() {
      if (maxElevation < 55.0 || maxStep > 12.01) {
         throw new IllegalStateException("Departure not smooth/upward: elevation=" + maxElevation + " step=" + maxStep);
      } else if (!Double.isNaN(firstTurnAt) && !(firstTurnAt < 8.0) && !(firstTurnAt > 10.0)) {
         LOG.info(
            "PAC_CANISTER_DEPARTURE_PASS shot={} maximumElevation={} maximumTickTurn={} firstTurnMetres={} departureMaxStep={}",
            new Object[]{shot, maxElevation, maxStep, firstTurnAt, departureStepMax}
         );
      } else {
         throw new IllegalStateException("Wrong clearance before first turn: " + firstTurnAt);
      }
   }

   static void renderLate(RenderTickEvent event) {
      if (Boolean.getBoolean("deepstrike.patriotCanisterReview") && event.phase == Phase.START) {
         render(Minecraft.getInstance(), event);
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (truck != null) {
         int launchAge = shot < 0 ? -1 : age - 60 - shot * 160;
         if (event.phase == Phase.START) {
            if (observer == null) {
               observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            }

            Vec3 target = shot < 0 ? truck.position().add(0.0, 4.0, -3.0) : mouth;
            Vec3 position = target.add(7.0, 3.0, 12.0);
            if (shot < 0 && age >= 24 && age <= 30) {
               Vec3 p = PatriotCanisterLayout.elevatedMouth(0);
               Matrix4d transform = truck.getVehicleTransform(1.0F);
               Vector4d world = truck.transformPosition(transform, p.x, p.y, p.z);
               Vec3 m = new Vec3(world.x, world.y, world.z);
               Vec3 a = PatriotCanisterLayout.axis();
               target = m.subtract(a.scale(2.7));
               position = m.subtract(a.scale(0.65)).add(0.31, -observer.getEyeHeight(), 0.0);
            } else if (shot >= 2 && current != null) {
               Vec3 rendered = PatriotRemoteEffects.opticalPoint(current.getUUID(), current.getId());
               if (rendered == null) {
                  rendered = current.position();
               }

               double mix = Mth.clamp((launchAge - 10) / 20.0, 0.0, 1.0);
               mix = mix * mix * (3.0 - 2.0 * mix);
               target = mouth.lerp(rendered, 0.55 * mix);
               position = target.add(24.0, 12.0, 28.0);
            }

            observer.setPos(position);
            Vec3 delta = target.subtract(observer.getEyePosition());
            observer.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            observer.setXRot((float)(-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()))));
            observer.xo = position.x;
            observer.yo = position.y;
            observer.zo = position.z;
            observer.yRotO = observer.getYRot();
            observer.xRotO = observer.getXRot();
            mc.setCameraEntity(observer);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            mc.options.fov().set(45);
         } else {
            if (mc.level.getEntity(truck.getId()) == null || age < 24) {
               return;
            }

            if (!Boolean.getBoolean("deepstrike.reviewNoMotion")
               && shot >= 0
               && launchAge < 70
               && writes.get() < 4
               && System.nanoTime() - lastFrame > 33333333L) {
               recordFrame(mc, launchAge);
            }

            String key = shot < 0
               ? (age <= 30 ? "stored-inside" : "loaded")
               : (launchAge < 3 ? "rupture" : (launchAge < 8 ? "petals" : (launchAge < 20 ? "exit" : (launchAge < 45 ? "climb" : "open"))));
            String name = "shot-" + shot + "-" + key;
            if (!captures.add(name)) {
               return;
            }

            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            Path directory = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
            writes.incrementAndGet();
            VIDEO_IO.execute(() -> {
               try {
                  NativeImage error = image;

                  try {
                     Files.createDirectories(directory);
                     image.writeToFile(directory.resolve(name + ".png"));
                  } catch (Throwable var12x) {
                     if (image != null) {
                        try {
                           error.close();
                        } catch (Throwable var11x) {
                           var12x.addSuppressed(var11x);
                        }
                     }

                     throw var12x;
                  }

                  if (image != null) {
                     image.close();
                  }
               } catch (Exception var13x) {
                  failure = var13x;
               } finally {
                  writes.decrementAndGet();
               }
            });
            LOG.info("PAC_CANISTER_FRAME name={} camera={} observer={}", new Object[]{name, mc.gameRenderer.getMainCamera().getPosition(), observer.position()});
         }
      }
   }

   private static void recordFrame(Minecraft mc, int launchAge) {
      lastFrame = System.nanoTime();
      int index = frame++;
      frames.append(index).append(',').append(lastFrame).append(',').append(shot).append(',').append(launchAge).append('\n');
      NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());

      try {
         NativeImage small = new NativeImage(960, 540, false);
         image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), small);
         Path directory = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase") + "/motion");
         writes.incrementAndGet();
         VIDEO_IO.execute(() -> {
            try {
               NativeImage e = small;

               try {
                  Files.createDirectories(directory);
                  small.writeToFile(directory.resolve(String.format("frame-%05d.png", index)));
               } catch (Throwable var12) {
                  if (small != null) {
                     try {
                        e.close();
                     } catch (Throwable var11) {
                        var12.addSuppressed(var11);
                     }
                  }

                  throw var12;
               }

               if (small != null) {
                  small.close();
               }
            } catch (Exception var13) {
               failure = var13;
            } finally {
               writes.decrementAndGet();
            }
         });
      } catch (Throwable var7) {
         if (image != null) {
            try {
               image.close();
            } catch (Throwable var6) {
               var7.addSuppressed(var6);
            }
         }

         throw var7;
      }

      if (image != null) {
         image.close();
      }
   }

   private PatriotCanisterReview() {
   }
}
