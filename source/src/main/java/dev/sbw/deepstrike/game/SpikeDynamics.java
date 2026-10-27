package dev.sbw.deepstrike.game;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SpikeDynamics {
   public static SpikeDynamics.Step step(SpikeDynamics.State state, Vec3 finalTarget, Vec3 launchDirection, double speedKmh, double cruiseHeight) {
      return step(state, finalTarget, launchDirection, speedKmh, cruiseHeight, false);
   }

   public static SpikeDynamics.Step step(
      SpikeDynamics.State state, Vec3 finalTarget, Vec3 launchDirection, double speedKmh, double cruiseHeight, boolean direct
   ) {
      Vec3 from = state.motion().position();
      double horizontal = finalTarget.subtract(from).horizontalDistance();
      double velocity = state.motion().velocity().length();
      double lookAhead = Mth.clamp(
         velocity * velocity / FlightDynamics.lateralAcceleration(MissileKind.SPIKE_NLOS, false) * 1.5 + velocity * 0.5, 24.0, 500.0
      );
      double usefulHeight = direct ? finalTarget.y : Math.min(cruiseHeight, finalTarget.y + horizontal * 0.15);
      Vec3 target = finalTarget;
      boolean terminal = direct || horizontal <= lookAhead;
      if (!terminal) {
         double fraction = lookAhead / horizontal;
         double y = from.y + Mth.clamp(usefulHeight - from.y, -lookAhead * 0.65, lookAhead * 0.65);
         target = new Vec3(Mth.lerp(fraction, from.x, finalTarget.x), y, Mth.lerp(fraction, from.z, finalTarget.z));
      }

      boolean climbing = usefulHeight > from.y + 1.5 && !terminal;
      CruiseDynamics.State next = CruiseDynamics.step(
         state.motion(),
         new CruiseDynamics.Command(target, launchDirection, speedKmh / 1.5, state.launchDistance() < 3.0, terminal, MissileKind.SPIKE_NLOS, false, true)
      );
      AABB fuse = new AABB(finalTarget, finalTarget).inflate(0.25);
      Optional<Vec3> contact = fuse.clip(from, next.position());
      boolean finished = terminal && (fuse.contains(from) || contact.isPresent());
      Vec3 end = finished ? contact.orElse(from) : next.position();
      return new SpikeDynamics.Step(
         new SpikeDynamics.State(new CruiseDynamics.State(end, next.velocity(), next.propulsion()), state.launchDistance() + end.distanceTo(from)),
         climbing,
         finished
      );
   }

   private SpikeDynamics() {
   }

   public record State(CruiseDynamics.State motion, double launchDistance) {
   }

   public record Step(SpikeDynamics.State state, boolean climbing, boolean finished) {
   }
}
