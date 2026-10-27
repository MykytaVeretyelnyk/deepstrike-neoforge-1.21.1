package dev.sbw.deepstrike.client;

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BoneState;
import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.registry.BFEntities;
import dev.sbw.deepstrike.registry.BFItems;
import dev.sbw.deepstrike.vehicle.AnMpq65Entity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import dev.sbw.deepstrike.compat.TickEvent.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class VehicleDesignReview {
   private static final Logger LOG = LoggerFactory.getLogger(VehicleDesignReview.class);
   private static volatile Su24mEntity plane;
   private static volatile PatriotTruckEntity truck;
   private static volatile AnMpq65Entity radar;
   private static volatile Throwable failure;
   private static volatile boolean ready;
   private static boolean boot;
   private static boolean down;
   private static boolean up;
   private static boolean folding;
   private static boolean extending;
   private static int ticks;
   private static String pending;
   private static GuidedMissileEntity observer;
   private static Vec3 camera = new Vec3(225.0, 505.0, 245.0);
   private static Vec3 aim = new Vec3(200.0, 502.0, 217.0);

   static void tick(Minecraft mc) {
      if (!mc.gameDirectory.toPath().toString().contains("/build/")) {
         throw new IllegalStateException("Disposable lab required");
      } else if (failure != null) {
         throw new IllegalStateException("Vehicle design review", failure);
      } else if (!boot) {
         boot = true;
         mc.options.hideGui = true;
         mc.options.fov().set(65);
         mc.setScreen(null);
         NeoForge.EVENT_BUS.addListener(VehicleDesignReview::serverTick);
         NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles e) -> {
            Vec3 d = aim.subtract(camera);
            float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
            e.setYaw(yaw);
            e.setPitch(pitch);
            e.setRoll(0.0F);
            ClientCamera.moveCameraToNose(e.getCamera(), camera, yaw, pitch);
         });
         mc.getSingleplayerServer().execute(() -> setup(mc));
      } else if (ready && mc.level.getEntity(plane.getId()) != null) {
         if (observer == null) {
            observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
         }

         observer.setPos(camera);
         observer.xo = camera.x;
         observer.yo = camera.y;
         observer.zo = camera.z;
         mc.setCameraEntity(observer);
         Su24mEntity aircraft = (Su24mEntity)mc.level.getEntity(plane.getId());
         aircraft.setPos(200.0, 500.0, 217.0);
         aircraft.xo = 200.0;
         aircraft.yo = 500.0;
         aircraft.zo = 217.0;
         aircraft.setDeltaMovement(Vec3.ZERO);
         aircraft.setOnGround(false);
         if (ticks < 180) {
            camera = aircraft.position().add(25.0, 5.0, 28.0);
            aim = aircraft.position().add(0.0, 2.0, 0.0);
         }

         int t = ++ticks;
         if (t == 35) {
            pending = "gear-down.png";
         }

         if (t == 45) {
            mc.getSingleplayerServer().execute(() -> plane.setUpInputDown(true));
         }

         if (t == 65) {
            pending = "gear-folding.png";
         }

         if (t == 100) {
            pending = "gear-up.png";
         }

         if (t == 110) {
            mc.getSingleplayerServer().execute(() -> plane.setUpInputDown(true));
         }

         if (t == 130) {
            pending = "gear-extending.png";
         }

         if (t == 165) {
            pending = "gear-restored.png";
         }

         if (t >= 180 && t < 250) {
            Entity entity = mc.level.getEntity(radar.getId());
            if (entity != null) {
               camera = entity.position().add(t < 215 ? new Vec3(12.0, 5.0, 13.0) : new Vec3(-12.0, 4.0, -11.0));
               aim = entity.position().add(0.0, 2.3, 0.0);
            }
         }

         if (t == 205) {
            pending = "mpq-front.png";
         }

         if (t == 240) {
            pending = "mpq-back.png";
         }

         if (t == 250) {
            mc.getSingleplayerServer().execute(() -> {
               ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
               p.startRiding(truck, true);
            });
         }

         if (t >= 255) {
            Entity live = mc.level.getEntity(truck.getId());
            if (live != null) {
               camera = live.position().add(0.625, 2.48, 6.3);
               aim = camera.add(0.0, -0.8, 1.0);
            }
         }

         if (t == 290) {
            pending = "patriot-cab.png";
         }

         if (t >= 295 && t < 305) {
            Entity live = mc.level.getEntity(truck.getId());
            if (live != null) {
               camera = live.position().add(-0.625, 2.48, 6.3);
               aim = camera.add(0.0, -0.8, 1.0);
            }
         }

         if (t == 300) {
            pending = "patriot-operator.png";
         }

         if (t == 305) {
            Entity live = mc.level.getEntity(truck.getId());
            camera = live.position().add(0.625, 2.48, 6.3);
            aim = camera.add(0.0, -1.8, 1.0);
         }

         if (t >= 305) {
            Entity live = mc.level.getEntity(truck.getId());
            if (live != null) {
               aim = camera.add(0.0, -1.8, 1.0);
            }
         }

         if (t == 325) {
            pending = "patriot-footwell.png";
         }

         if (t == 345) {
            if (!down || !up || !folding || !extending) {
               throw new IllegalStateException("Missing rendered gear states " + down + "/" + up + "/" + folding + "/" + extending);
            }

            LOG.info("VEHICLE_DESIGN_PASS deployment=4_headings blocked_space_preserves_item gear=down_fold_up_extend_down FP5_OPTIONS_FINISHED");
            mc.stop();
         }
      }
   }

   private static void setup(Minecraft mc) {
      try {
         ServerLevel level = mc.getSingleplayerServer().overworld();
         ServerPlayer p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
         ArrayList<Entity> stale = new ArrayList<>();

         for (Entity e : level.getAllEntities()) {
            if (e instanceof VehicleEntity) {
               stale.add(e);
            }
         }

         stale.forEach(Entity::discard);

         for (int x = 160; x <= 250; x++) {
            for (int z = 160; z <= 250; z++) {
               level.setBlockAndUpdate(new BlockPos(x, 100, z), Blocks.STONE.defaultBlockState());
            }
         }

         level.setDayTime(6000L);
         level.setWeatherParameters(6000, 0, false, false);
         p.getAbilities().instabuild = false;

         for (float heading : new float[]{0.0F, 90.0F, 180.0F, -90.0F}) {
            p.teleportTo(200.0, 101.0, 200.0);
            p.setYRot(heading);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)BFItems.SU24M.get(), 2));
            UseOnContext context = new UseOnContext(
               p, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(200.5, 101.0, 202.5), Direction.UP, new BlockPos(200, 100, 202), false)
            );
            InteractionResult result = ((Item)BFItems.SU24M.get()).useOn(context);
            Su24mEntity spawned = null;

            for (Entity ex : level.getAllEntities()) {
               if (ex instanceof Su24mEntity s && s.isAlive()) {
                  if (spawned != null) {
                     throw new IllegalStateException("Duplicate deployed plane");
                  }

                  spawned = s;
               }
            }

            if (!result.consumesAction()
               || spawned == null
               || p.getMainHandItem().getCount() != 1
               || spawned.position().distanceTo(p.position()) < 15.0
               || spawned.getBoundingBox().inflate(1.5).intersects(p.getBoundingBox())) {
               throw new IllegalStateException("Unsafe deployment at yaw " + heading);
            }

            LOG.info("VEHICLE_DEPLOY_PASS yaw={} plane={} player={} count={}", new Object[]{heading, spawned.position(), p.position(), p.getMainHandItem().getCount()});
            spawned.discard();
         }

         p.teleportTo(200.0, 101.0, 200.0);
         p.setYRot(0.0F);
         p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack((ItemLike)BFItems.SU24M.get(), 2));
         UseOnContext context = new UseOnContext(
            p, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(200.5, 101.0, 202.5), Direction.UP, new BlockPos(200, 100, 202), false)
         );

         for (int step = 0; step < 4; step++) {
            for (int side : new int[]{0, 1, -1}) {
               level.setBlockAndUpdate(new BlockPos(200 + side * 20, 103, 217 + step * 3), Blocks.STONE.defaultBlockState());
            }
         }

         InteractionResult blocked = ((Item)BFItems.SU24M.get()).useOn(context);
         if (blocked != InteractionResult.FAIL || p.getMainHandItem().getCount() != 2) {
            throw new IllegalStateException("Blocked deployment consumed item");
         }

         for (Entity exx : level.getAllEntities()) {
            if (exx instanceof Su24mEntity && exx.isAlive()) {
               throw new IllegalStateException("Plane spawned inside obstruction");
            }
         }

         for (int step = 0; step < 4; step++) {
            for (int side : new int[]{0, 1, -1}) {
               level.setBlockAndUpdate(new BlockPos(200 + side * 20, 103, 217 + step * 3), Blocks.AIR.defaultBlockState());
            }
         }

         if (!((Item)BFItems.SU24M.get()).useOn(context).consumesAction()) {
            throw new IllegalStateException("Cleared deployment failed");
         }

         for (Entity exxx : level.getAllEntities()) {
            if (exxx instanceof Su24mEntity s && s.isAlive()) {
               plane = s;
            }
         }

         plane.discard();
         plane = (Su24mEntity)((EntityType)BFEntities.SU24M.get()).create(level);
         plane.moveTo(200.0, 500.0, 217.0, 0.0F, 0.0F);
         plane.setGearUp(false);
         plane.setNoGravity(true);
         level.addFreshEntity(plane);
         radar = (AnMpq65Entity)((EntityType)BFEntities.AN_MPQ_65.get()).create(level);
         radar.moveTo(235.0, 101.0, 220.0, 0.0F, 0.0F);
         radar.setNoGravity(true);
         level.addFreshEntity(radar);
         truck = (PatriotTruckEntity)((EntityType)BFEntities.PATRIOT_TRUCK.get()).create(level);
         truck.moveTo(215.0, 101.0, 180.0, 0.0F, 0.0F);
         truck.setNoGravity(true);
         level.addFreshEntity(truck);
         ready = true;
      } catch (Throwable var14) {
         LOG.error("VEHICLE_DESIGN_SETUP_FAILED", var14);
         failure = var14;
      }
   }

   private static void serverTick(ServerTickEvent event) {
      if (event.phase == Phase.END && ready && plane != null) {
         plane.moveTo(200.0, 500.0, 217.0, 0.0F, 0.0F);
         plane.setDeltaMovement(Vec3.ZERO);
         plane.setOnGround(false);
      }
   }

   static void observeGear(Su24mEntity entity, VehicleModelInstance instance, float progress) {
      if (plane != null && entity.getId() == plane.getId()) {
         for (String n : new String[]{"gear_nose", "gear_main_r", "gear_main_l"}) {
            BoneState bone = instance.getBone(n);
            if (bone == null) {
               throw new IllegalStateException("Missing " + n);
            }

            if (bone.visible != progress < 0.999F) {
               throw new IllegalStateException("Wrong gear visibility");
            }

            if (progress > 0.15 && progress < 0.85 && Math.abs(bone.rotation.w) > 0.999999) {
               throw new IllegalStateException("Gear bone did not rotate");
            }
         }

         if (progress < 0.001) {
            down = true;
         }

         if (progress > 0.999) {
            up = true;
         }

         if (progress > 0.1 && progress < 0.9) {
            if (ticks < 110) {
               folding = true;
            } else {
               extending = true;
            }
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent e) {
      if (e.phase == Phase.END && pending != null) {
         String name = pending;
         pending = null;
         if (name.startsWith("gear")) {
            Su24mEntity live = (Su24mEntity)mc.level.getEntity(plane.getId());
            LOG.info(
               "GEAR_CAPTURE name={} serverUp={} clientUp={} clientRot={} progress={} clientY={} grounded={}",
               new Object[]{
                  name, plane.getGearUp(), live.getGearUp(), live.getSynchedGearRot(), live.landingGearProgress(1.0F), live.getY(), live.onGround()
               }
            );
         }

         try {
            NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

            try {
               Path path = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
               Files.createDirectories(path);
               shot.writeToFile(path.resolve(name));
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
            throw new IllegalStateException(var8);
         }
      }
   }
}
