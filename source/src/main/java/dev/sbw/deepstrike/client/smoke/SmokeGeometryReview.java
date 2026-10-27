package dev.sbw.deepstrike.client.smoke;

import dev.sbw.deepstrike.game.CruiseDynamics;
import dev.sbw.deepstrike.game.FlightUnits;
import dev.sbw.deepstrike.game.ManpadDynamics;
import dev.sbw.deepstrike.game.MissileKind;
import dev.sbw.deepstrike.game.PeacekeeperDynamics;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

public final class SmokeGeometryReview {
   public static void run(Minecraft mc) {
      StringBuilder report = new StringBuilder("case=" + System.getProperty("deepstrike.reviewCase", "unspecified") + "\n");

      try {
         List<Vec3> cloud = List.of(new Vec3(-3.0, -3.0, 0.0), new Vec3(3.0, -3.0, 0.0), new Vec3(3.0, 3.0, 0.0), new Vec3(-3.0, 3.0, 0.0));
         Vec3 source = new Vec3(-2.0, 0.0, 0.0);
         AABB obstacle = new AABB(-0.5, -0.5, -1.0, 0.5, 0.5, 1.0);
         List<List<Vec3>> free = SmokePolygonClip.freeAir(cloud, source, List.of(obstacle));
         require(free.size() > 1, "local block retains cloud around its sides");
         require(free.stream().anyMatch(p -> SmokePolygonClip.bounds((List<Vec3>)p).maxX > 2.9), "smoke survives on far side of finite block");

         for (List<Vec3> piece : free) {
            require(!SmokePolygonClip.bounds(piece).intersects(obstacle), "local fragments exclude solid block");
         }

         AABB wall = new AABB(-0.5, -5.0, -1.0, 0.5, 5.0, 1.0);
         List<List<Vec3>> wallPieces = SmokePolygonClip.freeAir(cloud, source, List.of(wall));
         require(
            !wallPieces.isEmpty() && wallPieces.stream().allMatch(p -> SmokePolygonClip.bounds((List<Vec3>)p).maxX < -0.5),
            "complete wall retains only source side"
         );
         require(Math.abs(FlightUnits.blocksPerTick(1000.0) - 6.944444444444445) < 1.0E-10, "half nominal flight displacement");
         require(Math.abs(FlightUnits.kilometresPerHour(6.944444444444445) - 1000.0) < 1.0E-10, "nominal speed display preserved");
         report.append("local-block wrap / complete-wall occlusion / half-speed nominal conversion PASS\n");

         for (MissileKind kind : MissileKind.values()) {
            double assigned = kind.defaultSpeed;
            double movement = FlightUnits.blocksPerTick(assigned);
            require(Math.abs(movement * 20.0 * 3.6 - assigned * 0.5) < 1.0E-8, "actual half speed " + kind);
            require(Math.abs(FlightUnits.kilometresPerHour(movement) - assigned) < 1.0E-8, "unchanged nominal speed " + kind);
            if (kind != MissileKind.PEACEKEEPER) {
               CruiseDynamics.State state = new CruiseDynamics.State(new Vec3(0.0, 200.0, 0.0), new Vec3(assigned / 3.6, 0.0, 0.0));
               CruiseDynamics.Command command = new CruiseDynamics.Command(
                  new Vec3(10000.0, 200.0, 0.0), new Vec3(1.0, 0.0, 0.0), assigned, false, false, kind, false, true
               );

               for (int i = 0; i < 4; i++) {
                  state = CruiseDynamics.step(state, command);
               }

               require(Math.abs(state.position().x - movement) < 1.0E-6, "live cruise displacement " + kind);
            }
         }

         for (ManpadDynamics.Profile profile : ManpadDynamics.Profile.values()) {
            ManpadDynamics.State state = new ManpadDynamics.State(new Vec3(0.0, 200.0, 0.0), new Vec3(168.42105263157893, 0.0, 0.0));

            for (int i = 0; i < 4; i++) {
               state = ManpadDynamics.step(profile, state, new Vec3(10000.0, 200.0, 0.0), true);
            }

            require(Math.abs(state.position().x - 168.42105263157893 / 40.0) < 1.0E-6, "native MANPADS half displacement " + profile);
         }

         PeacekeeperDynamics.Selection selected = PeacekeeperDynamics.select(new Vec3(0.0, 101.0, 0.0), new Vec3(3000.0, 101.0, 0.0), 2400.0);
         require(selected.valid(), "half-speed ballistic profile remains reachable");
         PeacekeeperDynamics.Prediction prediction = PeacekeeperDynamics.predict(selected.plan());
         require(prediction != null && prediction.impact().distanceTo(new Vec3(3000.0, 101.0, 0.0)) <= 1.0, "half-speed ballistic reaches target");
         report.append("all missile nominal speeds / cruise and MANPADS half displacement / ballistic reach PASS\n");
         Random random = new Random(357779327813L);

         for (int test = 0; test < 520; test++) {
            long mask = test == 0 ? 0L : (test == 1 ? -1L : (test == 2 ? 65535L : (test == 3 ? 6148914691236517205L : random.nextLong())));
            List<AABB> packed = SmokeFlow.mergeFullBoxes(SmokeFlow.solidCuboids(mask));

            for (int cell = 0; cell < 64; cell++) {
               Vec3 center = new Vec3((cell & 3) + 0.5, (cell >> 4) + 0.5, (cell >> 2 & 3) + 0.5);
               int covering = 0;

               for (AABB cube : packed) {
                  if (cube.contains(center)) {
                     covering++;
                  }
               }

               require(covering == ((mask & 1L << cell) != 0L ? 1 : 0), "full-cube packing occupancy case=" + test + " cell=" + cell);
            }

            if (test == 1 || test == 2) {
               require(packed.size() == 1, "contiguous full cubes merged to one collider");
            }
         }

         ArrayList<AABB> sectionFloor = new ArrayList<>();

         for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
               sectionFloor.add(new AABB(x * 4, 0.0, z * 4, x * 4 + 4, 1.0, z * 4 + 4));
            }
         }

         List<AABB> joinedFloor = SmokeFlow.mergeFullBoxes(sectionFloor);
         require(joinedFloor.size() == 1 && joinedFloor.get(0).equals(new AABB(0.0, 0.0, 0.0, 16.0, 1.0, 16.0)), "section floor exact union");
         report.append("section floor exact union PASS\n");
         report.append("full-cube packing 520 exact occupancy cases PASS\n");
         AABB floor = new AABB(-20.0, -1.0, -20.0, 20.0, 0.0, 20.0);
         SmokeFlow.Hit hit = SmokeFlow.hit(new Vec3(0.0, 2.0, 0.0), new Vec3(0.0, -100.0, 0.0), floor);
         require(hit != null && Math.abs(hit.fraction() - 0.0199609375) < 1.0E-8, "fast floor sweep");
         require(SmokeFlow.hit(new Vec3(2.0, 2.0, 2.0), new Vec3(0.0, -100.0, 0.0), new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)) == null, "sweep clear lane");
         require(
            Math.abs(SmokeFlow.clearance(new Vec3(0.5, 2.0, 0.5), List.of(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)), 10.0) - 1.0) < 1.0E-8, "render clearance"
         );
         List<AABB> obstruction = SmokeGeometry.local(mc.level, new BlockPos(10, 100, 8));
         require(obstruction.size() == 1 && obstruction.get(0).equals(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)), "simple full-block obstruction");
         BlockPos barsPos = new BlockPos(-4, 101, 4);
         require(mc.level.getBlockState(barsPos).is(Blocks.IRON_BARS), "loaded bars fixture");
         List<AABB> bars = SmokeGeometry.local(mc.level, barsPos);
         require(bars.size() == 1 && bars.get(0).equals(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)), "bars use opaque local voxel, no texture apertures");
         require(SmokeFlow.hit(new Vec3(0.25, 0.5, -1.0), new Vec3(0.0, 0.0, 3.0), bars.get(0)) != null, "smoke cannot pass through bar apertures");
         report.append("simple local voxel geometry / bars without texture holes PASS\n");
         require(mc.level.getBlockState(new BlockPos(10, 100, 8)).is(Blocks.LIGHT_GRAY_CONCRETE), "loaded ground fixture");
         SmokeFlow.Parcel enclosed = new SmokeFlow.Parcel(new Vec3(10.5, 100.5, 8.5), new Vec3(80.0, 0.0, 0.0));
         SmokeFlow.advance(mc.level, enclosed, 1.0, 0);
         require(enclosed.position.equals(new Vec3(10.5, 100.5, 8.5)), "smoke born inside solid must stay occluded");
         SmokeFlow.Parcel groundJet = new SmokeFlow.Parcel(new Vec3(10.5, 104.0, 8.5), new Vec3(0.0, -35.0, 0.0));
         SmokeFlow.advance(mc.level, groundJet, 2.0, 42);
         require(groundJet.touched && groundJet.position.y >= 101.00389625, "ground confinement " + groundJet.position);
         require(groundJet.position.subtract(new Vec3(10.5, groundJet.position.y, 8.5)).length() > 4.0, "ground spread " + groundJet.position);
         SmokeFlow.Parcel wallJet = new SmokeFlow.Parcel(new Vec3(0.0, 105.0, -4.0), new Vec3(0.0, 0.0, -90.0));
         SmokeFlow.advance(mc.level, wallJet, 0.2, 37);
         require(wallJet.touched && wallJet.position.z >= -5.99610375, "fast wall confinement " + wallJet.position);
         Vec3 radialBirth = SmokeFlow.constrainDrift(mc.level, new Vec3(0.5, 105.0, -5.8), new Vec3(0.5, 105.0, -7.84));
         require(radialBirth.z >= -5.99610375, "radial birth offset must not cross full wall " + radialBirth);
         report.append("radial birth sweep PASS ").append(radialBirth).append('\n');
         report.append("world floor/spread/wall PASS ground=").append(groundJet.position).append(" wall=").append(wallJet.position).append('\n');
         report.append("PASS\n");
         Files.writeString(mc.gameDirectory.toPath().resolve("smoke-geometry-review.txt"), report);
         LoggerFactory.getLogger(SmokeGeometryReview.class).info("SMOKE_GEOMETRY_REVIEW {}", report);
      } catch (Exception var23) {
         Exception e = var23;

         try {
            Files.writeString(mc.gameDirectory.toPath().resolve("smoke-geometry-review.txt"), report + "FAIL " + e);
         } catch (Exception var22) {
         }

         throw new IllegalStateException("Smoke geometry review failed", var23);
      }
   }

   private static boolean meshHit(Vec3 start, Vec3 delta, BakedQuad quad) {
      int[] data = quad.getVertices();
      int stride = data.length / 4;
      Vec3[] p = new Vec3[4];

      for (int i = 0; i < 4; i++) {
         p[i] = new Vec3(Float.intBitsToFloat(data[i * stride]), Float.intBitsToFloat(data[i * stride + 1]), Float.intBitsToFloat(data[i * stride + 2]));
      }

      for (int tri = 0; tri < 2; tri++) {
         int b = tri == 0 ? 1 : 2;
         int c = tri == 0 ? 2 : 3;
         Vec3 e1 = p[b].subtract(p[0]);
         Vec3 e2 = p[c].subtract(p[0]);
         Vec3 h = delta.cross(e2);
         double determinant = e1.dot(h);
         if (!(Math.abs(determinant) < 1.0E-10)) {
            Vec3 s = start.subtract(p[0]);
            double u = s.dot(h) / determinant;
            Vec3 q = s.cross(e1);
            double v = delta.dot(q) / determinant;
            double t = e2.dot(q) / determinant;
            if (!(u < 0.0) && !(v < 0.0) && !(u + v > 1.0) && !(t < 0.0) && !(t > 1.0)) {
               TextureAtlasSprite sprite = quad.getSprite();
               double tu = (1.0 - u - v) * Float.intBitsToFloat(data[4])
                  + u * Float.intBitsToFloat(data[b * stride + 4])
                  + v * Float.intBitsToFloat(data[c * stride + 4]);
               double tv = (1.0 - u - v) * Float.intBitsToFloat(data[5])
                  + u * Float.intBitsToFloat(data[b * stride + 5])
                  + v * Float.intBitsToFloat(data[c * stride + 5]);
               int x = Math.max(
                  0,
                  Math.min(
                     sprite.contents().width() - 1,
                     (int)((tu - sprite.getU0()) / (sprite.getU1() - sprite.getU0()) * sprite.contents().width())
                  )
               );
               int y = Math.max(
                  0,
                  Math.min(
                     sprite.contents().height() - 1,
                     (int)((tv - sprite.getV0()) / (sprite.getV1() - sprite.getV0()) * sprite.contents().height())
                  )
               );
               if (sprite.getPixelRGBA(0, x, y) >>> 24 != 0) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static void require(boolean condition, String message) {
      if (!condition) {
         throw new IllegalStateException(message);
      }
   }

   private SmokeGeometryReview() {
   }
}
