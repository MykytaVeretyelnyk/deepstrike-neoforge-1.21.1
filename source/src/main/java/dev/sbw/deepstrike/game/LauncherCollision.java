package dev.sbw.deepstrike.game;

import dev.sbw.deepstrike.model.LauncherLayout;
import dev.sbw.deepstrike.model.ModelAssets;
import dev.sbw.deepstrike.model.ModelGeometry;
import dev.sbw.deepstrike.registry.BFBlocks;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class LauncherCollision {
   public static Map<BlockPos, BitSet> voxels(LauncherBlockEntity be) {
      ModelGeometry model = ModelGeometry.get(ModelAssets.launcher(be.family()));
      Map<String, Matrix4f> transforms = model.transforms(be.yaw(), be.elevation());
      Matrix4f root = LauncherLayout.root(be.family(), be.facingYaw());
      Map<BlockPos, BitSet> result = new HashMap<>();

      for (ModelGeometry.Bone bone : model.bones) {
         if (!bone.name().equals("booster") && (!bone.name().startsWith("cover_") || be.coverVisible(Integer.parseInt(bone.name().substring(6))))) {
            for (ModelGeometry.Cube cube : bone.cubes()) {
               Matrix4f matrix = new Matrix4f(root).mul((Matrix4fc)transforms.get(bone.name())).mul(cube.transform());
               Matrix4f inverse = new Matrix4f(matrix).invert();
               Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
               Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);

               for (int i = 0; i < 8; i++) {
                  Vector3f v = matrix.transformPosition(
                     new Vector3f(cube.min())
                        .add((i & 1) != 0 ? cube.size().x : 0.0F, (i & 2) != 0 ? cube.size().y : 0.0F, (i & 4) != 0 ? cube.size().z : 0.0F)
                  );
                  min.min(v);
                  max.max(v);
               }

               float margin = 0.072F;
               Vector3f high = new Vector3f(cube.min()).add(cube.size());

               for (int y = (int)Math.floor(min.y * 8.0F); y <= Math.floor(max.y * 8.0F); y++) {
                  for (int z = (int)Math.floor(min.z * 8.0F); z <= Math.floor(max.z * 8.0F); z++) {
                     for (int x = (int)Math.floor(min.x * 8.0F); x <= Math.floor(max.x * 8.0F); x++) {
                        Vector3f p = inverse.transformPosition(new Vector3f((x + 0.5F) / 8.0F, (y + 0.5F) / 8.0F, (z + 0.5F) / 8.0F));
                        if (!(p.x < cube.min().x - margin)
                           && !(p.x > high.x + margin)
                           && !(p.y < cube.min().y - margin)
                           && !(p.y > high.y + margin)
                           && !(p.z < cube.min().z - margin)
                           && !(p.z > high.z + margin)) {
                           BlockPos cell = new BlockPos(Math.floorDiv(x, 8), Math.floorDiv(y, 8), Math.floorDiv(z, 8));
                           int index = Math.floorMod(y, 8) * 64 + Math.floorMod(z, 8) * 8 + Math.floorMod(x, 8);
                           result.computeIfAbsent(cell, k -> new BitSet(512)).set(index);
                        }
                     }
                  }
               }
            }
         }
      }

      return result;
   }

   public static VoxelShape shape(long[] packed) {
      BitSet bits = BitSet.valueOf(packed);
      List<VoxelShape> boxes = new ArrayList<>();

      for (int y = 0; y < 8; y++) {
         for (int z = 0; z < 8; z++) {
            int x = 0;

            while (x < 8) {
               if (!bits.get(y * 64 + z * 8 + x)) {
                  x++;
               } else {
                  int start = x;

                  while (x < 8 && bits.get(y * 64 + z * 8 + x)) {
                     x++;
                  }

                  boxes.add(Shapes.box(start / 8.0, y / 8.0, z / 8.0, x / 8.0, (y + 1) / 8.0, (z + 1) / 8.0));
               }
            }
         }
      }

      return boxes.isEmpty() ? Shapes.empty() : LauncherShapeCache.register(Shapes.or(Shapes.empty(), boxes.toArray(VoxelShape[]::new)).optimize());
   }

   public static void refresh(LauncherBlockEntity be) {
      Level level = be.getLevel();
      if (level != null && !level.isClientSide) {
         BlockPos master = be.getBlockPos();
         Map<BlockPos, BitSet> cells = voxels(be);
         Set<BlockPos> next = new HashSet<>();
         be.setCollisionBits(cells.getOrDefault(BlockPos.ZERO, new BitSet()).toLongArray());

         for (Entry<BlockPos, BitSet> entry : cells.entrySet()) {
            if (!entry.getKey().equals(BlockPos.ZERO)) {
               BlockPos pos = master.offset((Vec3i)entry.getKey());
               if (level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part && part.master().equals(master)) {
                  part.configure(master, entry.getValue().toLongArray());
                  next.add(pos);
               } else if (level.getBlockState(pos).canBeReplaced()) {
                  level.setBlock(pos, ((Block)BFBlocks.LAUNCHER_PART.get()).defaultBlockState(), 3);
                  if (level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part) {
                     part.configure(master, entry.getValue().toLongArray());
                     next.add(pos);
                  }
               }
            }
         }

         for (BlockPos old : be.collisionParts) {
            if (!next.contains(old) && level.getBlockEntity(old) instanceof LauncherPartBlockEntity part && part.master().equals(master)) {
               level.removeBlock(old, false);
            }
         }

         be.collisionParts = next;
         be.setChanged();
      }
   }

   public static void remove(LauncherBlockEntity be) {
      Level level = be.getLevel();
      if (level != null && !level.isClientSide) {
         for (BlockPos pos : be.collisionParts) {
            if (level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part && part.master().equals(be.getBlockPos())) {
               level.removeBlock(pos, false);
            }
         }
      }
   }

   public static boolean belongsTo(BlockGetter level, BlockPos pos, BlockPos master) {
      return pos.equals(master) || level.getBlockEntity(pos) instanceof LauncherPartBlockEntity part && part.master().equals(master);
   }
}
