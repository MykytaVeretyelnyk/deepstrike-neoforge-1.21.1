package dev.sbw.deepstrike.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/** Builds the launcher screen's preset routes (direct, left flank, high approach) towards a target. */
public final class PresetRoutes {
   public static final int DIRECT = 0;
   public static final int LEFT_FLANK = 1;
   public static final int HIGH = 2;

   private PresetRoutes() {
   }

   /** Default aim point straight ahead of the launcher, inside the missile's allowed range. */
   public static RoutePoint defaultTarget(BlockPos launcher, Direction facing, MissileKind kind, DoubleBinaryOperator ground) {
      double reach = Mth.clamp(Math.max(kind.minRange * 1.5, 160.0), kind.minRange + 16.0, (double)kind.maxRange);
      double x = Math.floor(launcher.getX() + 0.5 + facing.getStepX() * reach) + 0.5;
      double z = Math.floor(launcher.getZ() + 0.5 + facing.getStepZ() * reach) + 0.5;
      double y = ground.applyAsDouble(x, z);
      return new RoutePoint(x, Double.isFinite(y) ? y : launcher.getY(), z, kind.defaultSpeed);
   }

   /**
    * @param ground terrain height at (x, z), or NaN where unknown
    */
   public static List<RoutePoint> build(
      int preset, BlockPos launcher, Direction facing, RoutePoint target, MissileKind kind, int ceiling, DoubleBinaryOperator ground
   ) {
      double ox = launcher.getX() + 0.5;
      double oz = launcher.getZ() + 0.5;
      double dx = target.x() - ox;
      double dz = target.z() - oz;
      double distance = Math.sqrt(dx * dx + dz * dz);
      double ux = distance < 1.0E-3 ? facing.getStepX() : dx / distance;
      double uz = distance < 1.0E-3 ? facing.getStepZ() : dz / distance;
      double lateral = preset == LEFT_FLANK ? Mth.clamp(distance * 0.3, 40.0, 400.0) : 0.0;
      double leftX = uz;
      double leftZ = -ux;
      double highest = Math.max(launcher.getY(), target.y());

      for (int i = 0; i <= 64; i++) {
         double t = i / 64.0;
         double offset = lateral * Math.min(1.0, Math.min(t, 1.0 - t) * 3.0);
         double h = ground.applyAsDouble(ox + dx * t + leftX * offset, oz + dz * t + leftZ * offset);
         if (Double.isFinite(h)) {
            highest = Math.max(highest, h);
         }
      }

      double cruise = Math.min(ceiling, highest + (preset == HIGH ? 90.0 : 35.0));
      double[] fractions = preset == HIGH ? new double[]{0.25, 0.75} : new double[]{1.0 / 3.0, 2.0 / 3.0};
      List<RoutePoint> route = new ArrayList<>();

      for (double t : fractions) {
         route.add(new RoutePoint(ox + dx * t + leftX * lateral, cruise, oz + dz * t + leftZ * lateral, kind.defaultSpeed));
      }

      route.add(new RoutePoint(target.x(), Math.min(ceiling, target.y()), target.z(), target.speed() > 0.0 ? target.speed() : kind.defaultSpeed));
      return route;
   }
}
