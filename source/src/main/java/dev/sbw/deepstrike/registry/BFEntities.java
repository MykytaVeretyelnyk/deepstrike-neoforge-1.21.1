package dev.sbw.deepstrike.registry;

import dev.sbw.deepstrike.game.DudMissileEntity;
import dev.sbw.deepstrike.game.Fp5BoosterEntity;
import dev.sbw.deepstrike.game.GuidedMissileEntity;
import dev.sbw.deepstrike.game.LaunchCoverEntity;
import dev.sbw.deepstrike.game.PatriotInterceptorEntity;
import dev.sbw.deepstrike.vehicle.AnMpq65Entity;
import dev.sbw.deepstrike.vehicle.IronDomeEntity;
import dev.sbw.deepstrike.vehicle.PatriotTruckEntity;
import dev.sbw.deepstrike.vehicle.SpikeNlosEntity;
import dev.sbw.deepstrike.vehicle.Su24mEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class BFEntities {
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, "deepstrike");
   public static final DeferredHolder<EntityType<?>, EntityType<GuidedMissileEntity>> GUIDED_MISSILE = ENTITY_TYPES.register(
      "guided_missile",
      () -> Builder.<GuidedMissileEntity>of(GuidedMissileEntity::new, MobCategory.MISC).sized(0.24F, 0.24F).clientTrackingRange(128).updateInterval(1).build("guided_missile")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PatriotInterceptorEntity>> PATRIOT_INTERCEPTOR = ENTITY_TYPES.register(
      "patriot_interceptor",
      () -> Builder.<PatriotInterceptorEntity>of(PatriotInterceptorEntity::new, MobCategory.MISC)
         .sized(0.35F, 0.35F)
         .clientTrackingRange(128)
         .updateInterval(1)
         .fireImmune()
         .build("patriot_interceptor")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<LaunchCoverEntity>> LAUNCH_COVER = ENTITY_TYPES.register(
      "launch_cover", () -> Builder.<LaunchCoverEntity>of(LaunchCoverEntity::new, MobCategory.MISC).sized(0.35F, 0.12F).clientTrackingRange(64).updateInterval(1).build("launch_cover")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<PatriotTruckEntity>> PATRIOT_TRUCK = ENTITY_TYPES.register(
      "mim_104_patriot_truck",
      () -> Builder.<PatriotTruckEntity>of(PatriotTruckEntity::new, MobCategory.MISC)
         .sized(3.4F, 3.6F)
         .clientTrackingRange(128)
         .updateInterval(1)
         .fireImmune()
         .build("mim_104_patriot_truck")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<IronDomeEntity>> IRON_DOME = ENTITY_TYPES.register(
      "iron_dome",
      () -> Builder.<IronDomeEntity>of(IronDomeEntity::new, MobCategory.MISC).sized(3.5F, 4.4F).clientTrackingRange(128).updateInterval(1).fireImmune().build("iron_dome")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<AnMpq65Entity>> AN_MPQ_65 = ENTITY_TYPES.register(
      "an_mpq_65", () -> Builder.<AnMpq65Entity>of(AnMpq65Entity::new, MobCategory.MISC).sized(2.8F, 2.8F).clientTrackingRange(128).updateInterval(1).fireImmune().build("an_mpq_65")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<DudMissileEntity>> DUD_MISSILE = ENTITY_TYPES.register(
      "dud_missile", () -> Builder.<DudMissileEntity>of(DudMissileEntity::new, MobCategory.MISC).sized(1.1F, 1.1F).clientTrackingRange(128).fireImmune().build("dud_missile")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<Fp5BoosterEntity>> FP5_BOOSTER = ENTITY_TYPES.register(
      "fp5_booster",
      () -> Builder.<Fp5BoosterEntity>of(Fp5BoosterEntity::new, MobCategory.MISC).sized(0.6F, 0.35F).clientTrackingRange(512).updateInterval(1).fireImmune().build("fp5_booster")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<SpikeNlosEntity>> SPIKE_NLOS = ENTITY_TYPES.register(
      "spike_nlos_jltv",
      () -> Builder.<SpikeNlosEntity>of(SpikeNlosEntity::new, MobCategory.MISC).sized(2.95F, 4.52F).clientTrackingRange(128).updateInterval(1).fireImmune().build("spike_nlos_jltv")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<Su24mEntity>> SU24M = ENTITY_TYPES.register(
      "su24m", () -> Builder.<Su24mEntity>of(Su24mEntity::new, MobCategory.MISC).sized(8.0F, 6.5F).clientTrackingRange(128).updateInterval(1).fireImmune().build("su24m")
   );

   private BFEntities() {
   }
}
