package dev.sbw.deepstrike.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sbw.deepstrike.client.smoke.SmokeFlow;
import dev.sbw.deepstrike.client.smoke.SmokeGeometry;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.MissileKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.sbw.deepstrike.compat.TickEvent.Phase;
import dev.sbw.deepstrike.compat.TickEvent.RenderTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SmokeApertureReview {
   private static final Block[] BLOCKS = new Block[]{
      Blocks.IRON_BARS, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE, Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.CHEST, Blocks.ENCHANTING_TABLE
   };
   private static final String[] NAMES = new String[]{
      "bars", "fence", "gate", "door-holes", "door-solid", "trapdoor-holes", "trapdoor-solid", "chest", "enchanting-table"
   };
   private static int age;
   private static int fixture;
   private static int sequence;
   private static boolean active;
   private static final List<SmokeFlow.Parcel> tracked = new ArrayList<>();
   private static int crossed;
   private static int inside;
   private static double maxGapSpeed;
   private static GuidedMissileEntity observer;
   private static final Logger LOG = LoggerFactory.getLogger(SmokeApertureReview.class);
   private static int lastCapture = -1;

   private static BlockPos center(int i) {
      return new BlockPos(-24 + i * 6, 105, 12);
   }

   static void build(ServerLevel level) {
      for (int i = 0; i < BLOCKS.length; i++) {
         BlockPos p = center(i);

         for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
               for (int z = -2; z <= 2; z++) {
                  level.setBlock(p.offset(x, y, z), z == 0 ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
               }
            }
         }

         BlockState state = BLOCKS[i].defaultBlockState();
         if (BLOCKS[i] instanceof IronBarsBlock) {
            state = (BlockState)((BlockState)state.setValue(IronBarsBlock.EAST, true)).setValue(IronBarsBlock.WEST, true);
         }

         if (BLOCKS[i] instanceof FenceBlock) {
            state = (BlockState)((BlockState)state.setValue(FenceBlock.EAST, true)).setValue(FenceBlock.WEST, true);
         }

         if (BLOCKS[i] instanceof FenceGateBlock) {
            state = (BlockState)state.setValue(FenceGateBlock.FACING, Direction.NORTH);
         }

         if (BLOCKS[i] instanceof DoorBlock) {
            state = (BlockState)state.setValue(DoorBlock.FACING, Direction.NORTH);
            level.setBlock(p.below(), (BlockState)state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
            state = (BlockState)state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
         }

         if (BLOCKS[i] instanceof TrapDoorBlock) {
            state = (BlockState)((BlockState)state.setValue(TrapDoorBlock.FACING, Direction.NORTH)).setValue(TrapDoorBlock.OPEN, true);
         }

         level.setBlock(p, state, 3);
      }
   }

   static void tick(Minecraft mc) {
      active = true;
      if (fixture >= BLOCKS.length) {
         LOG.info("FP5_OPTIONS_FINISHED apertureFixtures={}", fixture);
         mc.stop();
      } else {
         BlockPos p = center(fixture);
         if (age == 0) {
            SmokeFlow.clear();
            tracked.clear();
            crossed = 0;
            inside = 0;
            maxGapSpeed = 0.0;
            mc.getSingleplayerServer().execute(() -> {
               ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
               player.teleportTo(p.getX() + 2, 106.0, p.getZ() + 5);
            });
         }

         if (mc.level.getBlockState(p).is(BLOCKS[fixture])) {
            age++;
            if (age <= 160) {
               for (int j = 0; j < 8; j++) {
                  int point = sequence++ % 256;
                  double x = (point % 16 + 0.5) / 16.0;
                  double y = (point / 16 + 0.5) / 16.0;
                  SmokeFlow.Parcel parcel = ContrailRenderer.reviewApertureSmoke(
                     new Vec3(p.getX() + x, p.getY() + y, p.getZ() - 0.12), new Vec3(0.0, 0.0, 3.0), sequence
                  );
                  if (parcel != null) {
                     tracked.add(parcel);
                  }
               }
            }

            if (age == 1) {
               LOG.info(
                  "SMOKE_APERTURE_START fixture={} state={} solids={}",
                  new Object[]{NAMES[fixture], mc.level.getBlockState(p), SmokeGeometry.local(mc.level, p).size()}
               );
            }

            crossed = 0;
            List<AABB> local = SmokeGeometry.local(mc.level, p);

            for (SmokeFlow.Parcel parcel : tracked) {
               Vec3 q = parcel.position.subtract(p.getX(), p.getY(), p.getZ());
               if (q.z > 1.0) {
                  crossed++;
               }

               if (q.x > 0.0 && q.x < 1.0 && q.y > 0.0 && q.y < 1.0 && q.z > 0.0 && q.z < 1.0) {
                  maxGapSpeed = Math.max(maxGapSpeed, parcel.velocity.length());

                  for (AABB box : local) {
                     if (box.contains(q)) {
                        inside++;
                     }
                  }
               }
            }

            if (age >= 240) {
               LOG.info(
                  "SMOKE_APERTURE_TRANSPORT fixture={} emitted={} crossed={} inside={} maxGapSpeed={}",
                  new Object[]{NAMES[fixture], tracked.size(), crossed, inside, maxGapSpeed}
               );
               if (inside != 0) {
                  throw new IllegalStateException("Smoke entered solid fixture " + NAMES[fixture]);
               }

               if (fixture != 4 && fixture != 6 ? crossed == 0 : crossed != 0) {
                  throw new IllegalStateException("Unexpected aperture transit " + NAMES[fixture] + " crossed=" + crossed);
               }

               fixture++;
               age = 0;
            }
         }
      }
   }

   static void render(Minecraft mc, RenderTickEvent event) {
      if (active && fixture < BLOCKS.length && age != 0) {
         BlockPos p = center(fixture);
         if (event.phase == Phase.START) {
            if (observer == null) {
               observer = new GuidedMissileEntity(mc.level, MissileKind.JAGM);
            }

            Vec3 position = new Vec3(p.getX() + 2.1, p.getY() + 1.35, p.getZ() + 4.3);
            Vec3 target = Vec3.atCenterOf(p);
            observer.setPos(position);
            Vec3 d = target.subtract(observer.getEyePosition());
            observer.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            observer.setXRot((float)(-Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))));
            observer.xo = position.x;
            observer.yo = position.y;
            observer.zo = position.z;
            observer.yRotO = observer.getYRot();
            observer.xRotO = observer.getXRot();
            mc.options.fov().set(45);
            mc.setCameraEntity(observer);
         } else {
            int capture = -1;

            for (int at : new int[]{20, 80, 140, 220}) {
               if (age >= at && lastCapture < fixture * 1000 + at) {
                  capture = at;
                  break;
               }
            }

            if (capture < 0) {
               return;
            }

            lastCapture = fixture * 1000 + capture;

            try {
               NativeImage shot = Screenshot.takeScreenshot(mc.getMainRenderTarget());

               try {
                  Path dir = mc.gameDirectory.toPath().resolve("screenshots/deepstrike-review/" + System.getProperty("deepstrike.reviewCase"));
                  Files.createDirectories(dir);
                  shot.writeToFile(dir.resolve(NAMES[fixture] + "-" + capture + ".png"));
                  LOG.info("SMOKE_APERTURE_CAPTURE fixture={} age={} fps={}", new Object[]{NAMES[fixture], age, mc.getFps()});
               } catch (Throwable var9) {
                  if (shot != null) {
                     try {
                        shot.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (shot != null) {
                  shot.close();
               }
            } catch (Exception var10) {
               throw new IllegalStateException(var10);
            }
         }
      }
   }

   private SmokeApertureReview() {
   }
}
