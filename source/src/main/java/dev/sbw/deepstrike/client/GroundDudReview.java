package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.DudMissileEntity;
import dev.sbw.deepstrike.game.GroundContact;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.RoutePoint;
import dev.sbw.deepstrike.network.DeepStrikeNetwork;
import dev.sbw.deepstrike.registry.BFEntities;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import net.neoforged.bus.api.EventPriority;
import org.slf4j.LoggerFactory;

final class GroundDudReview {
   private static boolean started;
   private static boolean captured;
   private static volatile boolean ready;
   private static volatile Throwable failure;
   private static int age;
   private static GuidedMissileEntity observer;
   private static final Set<String> shots = new HashSet<>();
   private static final List<GuidedMissileEntity> flights = new ArrayList<>();

   static void tick(Minecraft mc) {
      if (failure != null) {
         throw new IllegalStateException("Ground dud review", failure);
      } else if (!started) {
         started = true;
         NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) -> {
            if (observer != null) {
               event.setYaw(observer.getYRot());
               event.setPitch(observer.getXRot());
               event.setRoll(0.0F);
               ClientCamera.moveCameraToNose(event.getCamera(), observer.getEyePosition(), observer.getYRot(), observer.getXRot());
            }
         });
         NeoForge.EVENT_BUS
            .addListener(
               (dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent e) -> {
                  if (e.phase == Phase.END && ready) {
                     try {
                        if (++age == 3) {
                           for (GuidedMissileEntity missile : flights) {
                              if (!missile.isAlive() || missile.engineRunning()) {
                                 throw new IllegalStateException("Airborne dud stopped or engine active");
                              }
                           }
                        }

                        if (age == 180) {
                           ServerLevel level = mc.getSingleplayerServer().overworld();
                           List<? extends DudMissileEntity> duds = level.getEntities(
                              BFEntities.DUD_MISSILE.get(), d -> d.getZ() >= -5.0 && d.getZ() <= 5.0
                           );
                           if (duds.size() != 5) {
                              throw new IllegalStateException("Expected five grounded wrecks, got " + duds.size());
                           }

                           for (DudMissileEntity dud : duds) {
                              if (dud.material().isAir() || dud.level().getBlockState(dud.contactBlock()).isAir()) {
                                 throw new IllegalStateException("Floating wreck");
                              }
                           }

                           for (DudMissileEntity dudx : duds) {
                              DudMissileEntity copy = (DudMissileEntity)((EntityType)BFEntities.DUD_MISSILE.get()).create(level);
                              copy.load(dudx.saveWithoutId(new CompoundTag()));
                              if (copy.impactPoint().distanceTo(dudx.impactPoint()) > 1.0E-6 || copy.material() != dudx.material()) {
                                 throw new IllegalStateException("Wreck impact persistence mismatch");
                              }

                              if (dudx.kind() == MissileKind.JAGM && System.getProperty("deepstrike.reviewCase", "").contains("steps")) {
                                 BlockPos b = dudx.contactBlock();
                                 int step = dudx.impactPoint().x - b.getX() > 0.5 ? 1 : -1;

                                 for (int z = -2; z <= 2; z++) {
                                    for (int x = 1; x <= 2; x++) {
                                       level.setBlock(b.offset(step * x, 0, z), Blocks.STONE_SLAB.defaultBlockState(), 3);
                                    }
                                 }
                              }
                           }

                           LoggerFactory.getLogger(GroundDudReview.class)
                              .info("GROUND_DUD_SERVER_PASS wrecks=5 airborneCoast=true enginesOff=true solidContact=true");
                        }
                     } catch (Throwable var11) {
                        failure = var11;
                     }
                  }
               }
            );
         mc.getSingleplayerServer().execute(() -> {
            try {
               ServerLevel level = mc.getSingleplayerServer().overworld();
               ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
               player.teleportTo(level, 0.0, 104.0, 20.0, 180.0F, 15.0F);

               for (int x = -32; x < 40; x++) {
                  for (int z = -8; z < 9; z++) {
                     level.setBlock(new BlockPos(x, 100, z), x < 0 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
                  }
               }

               for (DudMissileEntity old : level.getEntities(BFEntities.DUD_MISSILE.get(), d -> true)) {
                  old.discard();
               }

               int i = 0;

               for (MissileKind kind : MissileKind.values()) {
                  GuidedMissileEntity missile = new GuidedMissileEntity(level, kind);
                  missile.setPos(-24 + i * 12, kind == MissileKind.PEACEKEEPER ? 150.0 : 112.0, 0.0);
                  missile.setMission(List.of(new RoutePoint(200.0, 112.0, 0.0, 100.0)), kind == MissileKind.PEACEKEEPER);
                  CompoundTag tag = missile.saveWithoutId(new CompoundTag());
                  tag.putBoolean("DudCoasting", true);
                  missile.load(tag);
                  missile.setDeltaMovement(kind == MissileKind.PEACEKEEPER ? new Vec3(0.0, -1.4, 0.0) : new Vec3(0.32, -0.04, 0.0));
                  level.addFreshEntity(missile);
                  flights.add(missile);
                  i++;
               }

               ready = true;
            } catch (Throwable var10) {
               failure = var10;
            }
         });
      } else {
         if (age > 440 && !captured) {
            MissileExplosionEffects.accept(new DeepStrikeNetwork.BlastEffect(MissileKind.JAGM.ordinal(), -12.0, 110.0, 3.0, 731L, "he"));

            try {
               Field field = ContrailRenderer.class.getDeclaredField("COLUMNS");
               field.setAccessible(true);
               List<?> columns = (List<?>)field.get(null);
               boolean found = false;

               for (Object column : columns) {
                  Field point = column.getClass().getDeclaredField("position");
                  point.setAccessible(true);
                  Vec3 pos = (Vec3)point.get(column);
                  if (Math.abs(pos.x + 12.0) < 0.01 && Math.abs(pos.z - 3.0) < 0.01) {
                     if (Math.abs(pos.y - 101.035) > 0.01) {
                        throw new IllegalStateException("Airborne smoke source: " + pos);
                     }

                     found = true;
                  }
               }

               if (!found) {
                  throw new IllegalStateException("Missing grounded smoke column");
               }

               LoggerFactory.getLogger(GroundDudReview.class).info("GROUND_SMOKE_PASS explosionY=110 sourceY=101.035");
            } catch (ReflectiveOperationException var8) {
               throw new IllegalStateException(var8);
            }

            BlockHitResult ground = GroundContact.below(mc.level, new Vec3(-12.0, 110.0, 3.0));
            if (Math.abs(ground.getLocation().y - 101.0) > 0.01) {
               throw new IllegalStateException("Wrong smoke ground");
            }

            captured = true;
         }

         if (age > 490) {
            LoggerFactory.getLogger(GroundDudReview.class).info("GROUND_DUD_FINISHED FP5_OPTIONS_FINISHED");
            mc.stop();
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent e) {
      if (ready && age >= 190) {
         if (e.phase == Phase.START) {
            if (observer == null) {
               observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            }

            List<DudMissileEntity> duds = mc.level
               .getEntities(
                  BFEntities.DUD_MISSILE.get(), new AABB(-50.0, 80.0, -8.0, 55.0, 170.0, 8.0), d -> d.getZ() >= -5.0 && d.getZ() <= 5.0
               );
            int index = Math.max(0, Math.min(4, (age - 200) / 45));
            MissileKind kind = MissileKind.values()[index];
            DudMissileEntity dud = duds.stream().filter(d -> d.kind() == kind).findFirst().orElse(null);
            if (dud == null) {
               return;
            }

            boolean close = (age - 200) % 45 >= 25;
            AABB bounds = DudWreckRenderer.worldBounds(dud);
            Vec3 target = close ? dud.impactPoint().add(0.0, 0.1, 0.0) : bounds.getCenter();
            double size = close ? 1.8 : Math.max(2.0, Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())));
            Vec3 position = target.add(size * 0.85, close ? 1.05 : size * 0.45, size * 1.05);
            observer.setPos(position.subtract(0.0, observer.getEyeHeight(), 0.0));
            Vec3 delta = target.subtract(position);
            observer.setYRot((float)Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            observer.setXRot((float)(-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()))));
            observer.xo = observer.getX();
            observer.yo = observer.getY();
            observer.zo = observer.getZ();
            observer.yRotO = observer.getYRot();
            observer.xRotO = observer.getXRot();
            mc.setCameraEntity(observer);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            mc.options.fov().set(48);
         } else if (age >= 200 && age < 425 && ((age - 200) % 45 == 18 || (age - 200) % 45 == 35)) {
            int index = (age - 200) / 45;
            String key = MissileKind.values()[index].name() + ((age - 200) % 45 >= 25 ? "-ground" : "-body");
            if (!shots.add(key)) {
               return;
            }

            try {
               NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());

               try {
                  Path path = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
                  Files.createDirectories(path);
                  image.writeToFile(path.resolve(key + ".png"));
               } catch (Throwable var14) {
                  if (image != null) {
                     try {
                        image.close();
                     } catch (Throwable var13) {
                        var14.addSuppressed(var13);
                     }
                  }

                  throw var14;
               }

               if (image != null) {
                  image.close();
               }
            } catch (Exception var15) {
               failure = var15;
            }
         }
      }
   }
}
