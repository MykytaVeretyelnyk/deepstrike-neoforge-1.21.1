package dev.sbw.deepstrike.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LauncherShapeCache {
   private static final Map<VoxelShape, List<AABB>> BOXES = Collections.synchronizedMap(new WeakHashMap<>());
   private static final boolean REVIEW = Boolean.getBoolean("deepstrike.installedSmokeReview");
   private static final AtomicLong HITS = new AtomicLong();

   public static VoxelShape register(VoxelShape shape) {
      if (!shape.isEmpty()) {
         BOXES.put(shape, List.copyOf(shape.toAabbs()));
      }

      return shape;
   }

   public static List<AABB> boxes(VoxelShape shape) {
      List<AABB> cached = BOXES.get(shape);
      if (cached == null) {
         return null;
      } else {
         if (REVIEW) {
            HITS.incrementAndGet();
         }

         return new ArrayList<>(cached);
      }
   }

   public static long reviewHits() {
      return HITS.get();
   }

   private LauncherShapeCache() {
   }
}
