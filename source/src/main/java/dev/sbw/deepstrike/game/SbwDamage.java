package dev.sbw.deepstrike.game;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.entity.vehicle.damage.DamageModifier;
import com.atsuishio.superbwarfare.init.ModDamageTypes;
import com.atsuishio.superbwarfare.tools.DamageHandler;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.ExplosionEvent.Detonate;

/** Routes missile blast damage to Superb Warfare vehicles through DamageHandler.doDamage with SBW's custom_explosion damage type. */
public final class SbwDamage {
   private static final ExplosionDamageCalculator DEFAULT_CALCULATOR = new ExplosionDamageCalculator();

   private SbwDamage() {
   }

   public static void hurt(ServerLevel level, GuidedMissileEntity missile, VehicleEntity vehicle, float amount) {
      if (!(amount <= 0.0F)) {
         UUID ownerId = missile.ownerId();
         Entity owner = ownerId == null ? null : level.getEntity(ownerId);
         DamageSource source = ModDamageTypes.causeCustomExplosionDamage(level.registryAccess(), missile, owner);
         DamageHandler.doDamage(vehicle, source, amount * compensation(level, vehicle, source));
      }
   }

   /**
    * Warhead damage was tuned against the vanilla explosion type, so the custom_explosion damage is scaled by the ratio of what this
    * vehicle's own modifiers do with a vanilla explosion and with the custom one; the vehicle then takes the same damage as before.
    */
   private static float compensation(ServerLevel level, VehicleEntity vehicle, DamageSource custom) {
      DamageModifier modifier = vehicle.getDamageModifier();
      float vanilla = modifier.compute(vehicle, level.damageSources().explosion((Explosion)null), 1.0F);
      float modded = modifier.compute(vehicle, custom, 1.0F);
      if (!(modded > 1.0E-6F)) {
         return 1.0F;
      }

      float factor = vanilla / modded;
      return Float.isFinite(factor) ? Math.max(0.25F, Math.min(8.0F, factor)) : 1.0F;
   }

   /**
    * The vanilla pass of a missile explosion would hurt SBW vehicles with the plain explosion damage type. Vehicles are taken out of
    * the affected list here and damaged through {@link #hurt} using the same vanilla damage amount instead (no double damage).
    */
   public static void detonate(Detonate event) {
      if (event.getLevel() instanceof ServerLevel level && event.getExplosion() instanceof MissileExplosion blast && blast.vanillaPhase()) {
         Vec3 center = blast.center();
         double diameter = (double)(blast.radius() * 2.0F);
         Iterator<Entity> iterator = event.getAffectedEntities().iterator();

         while (iterator.hasNext()) {
            if (iterator.next() instanceof VehicleEntity vehicle) {
               iterator.remove();
               if (!vehicle.ignoreExplosion(blast) && Math.sqrt(vehicle.distanceToSqr(center)) / diameter <= 1.0) {
                  hurt(level, blast.missile(), vehicle, DEFAULT_CALCULATOR.getEntityDamageAmount(blast, vehicle));
               }
            }
         }
      }
   }
}
