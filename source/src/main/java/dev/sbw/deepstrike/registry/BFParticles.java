package dev.sbw.deepstrike.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class BFParticles {
   public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.PARTICLE_TYPE, "deepstrike");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXHAUST = TYPES.register("exhaust", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VAPOR = TYPES.register("vapor", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FP5_LAUNCH_SMOKE = TYPES.register("fp5_launch_smoke", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LAUNCH_SMOKE = TYPES.register("launch_smoke", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_FIRE = TYPES.register("blast_fire", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_SMOKE = TYPES.register("blast_smoke", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SOOT_SMOKE = TYPES.register("soot_smoke", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_DUST = TYPES.register("blast_dust", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_FLASH = TYPES.register("blast_flash", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_RING = TYPES.register("blast_ring", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_EMBER = TYPES.register("blast_ember", () -> new SimpleParticleType(true));
}
