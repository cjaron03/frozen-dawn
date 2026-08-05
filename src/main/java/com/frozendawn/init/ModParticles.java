package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, FrozenDawn.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            BLOOM_SPORE_ROOTING = PARTICLES.register("bloom_spore_rooting",
                    () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType>
            BLOOM_DRIFT = PARTICLES.register("bloom_drift",
                    () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
