package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.entity.projectile.Fim92MissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.IglaMissileEntity;
import com.atsuishio.superbwarfare.entity.projectile.MissileProjectile;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class ManpadPresentation {
   private static final WeakHashMap<Entity, ManpadPresentation.Sample> SAMPLES = new WeakHashMap<>();

   public static boolean controls(Entity entity) {
      return entity.level().isClientSide && (entity instanceof Fim92MissileEntity || entity instanceof IglaMissileEntity);
   }

   public static void position(Entity entity, Vec3 target) {
      ManpadPresentation.Sample sample = SAMPLES.computeIfAbsent(entity, key -> new ManpadPresentation.Sample());
      sample.target = target;
      sample.snapshotAge = 0;
      if (sample.reference == null) {
         sample.reference = entity.getDeltaMovement().scale(20.0);
      }
   }

   public static void velocity(Entity entity, Vec3 velocity) {
      ManpadPresentation.Sample sample = SAMPLES.computeIfAbsent(entity, key -> new ManpadPresentation.Sample());
      sample.reference = velocity.scale(20.0);
   }

   public static void prepare(MissileProjectile missile) {
      ManpadPresentation.Sample sample = SAMPLES.computeIfAbsent(missile, key -> new ManpadPresentation.Sample());
      if (sample.target == null) {
         sample.target = missile.position();
      }

      if (sample.reference == null) {
         sample.reference = missile.getDeltaMovement().scale(20.0);
      }

      if (!missile.isAlive()) {
         SAMPLES.remove(missile);
      } else {
         Vec3 visible = sample.visible == null ? missile.getDeltaMovement().scale(20.0) : sample.visible;
         if (sample.snapshotAge < 2) {
            sample.target = sample.target.add(sample.reference.scale(0.05));
         }

         Vec3 reference = sample.reference.scale(FlightVisualMotion.packetPredictionWeight(sample.snapshotAge++));
         FlightVisualMotion.Step next = FlightVisualMotion.step(missile.position(), visible, sample.target, reference, 0.05, 0.15);
         ManpadDynamics.Profile profile = missile instanceof Fim92MissileEntity ? ManpadDynamics.Profile.FIM92 : ManpadDynamics.Profile.IGLA;
         double ageSeconds = (Math.max(missile.tickCount, missile.getSyncedTick()) + 1) * 0.05;
         double maximum = Math.min(168.42105263157893, 10.0 + profile.acceleration * ageSeconds) * 1.0;
         sample.visible = next.velocity().length() > maximum ? next.velocity().normalize().scale(maximum) : next.velocity();
         missile.setDeltaMovement(sample.visible.scale(0.05));
      }
   }

   private ManpadPresentation() {
   }

   private static final class Sample {
      Vec3 target;
      Vec3 reference;
      Vec3 visible;
      int snapshotAge;
   }
}
