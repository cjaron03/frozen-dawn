package com.frozendawn.effect;

import com.frozendawn.FrozenDawn;
import com.frozendawn.defense.Sear;
import com.frozendawn.init.ModDamageTypes;
import com.frozendawn.init.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The Sever Lash's lingering burn: a pulse of damage a second and a slowdown, both scaling with level. */
public final class SearedEffect extends MobEffect {
    public SearedEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF6A2B);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID, "seared"),
                -Sear.SLOW_PER_LEVEL, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return duration % 20 == 0; }

    @Override public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().source(ModDamageTypes.SEARED), Sear.PULSE_DAMAGE * (amplifier + 1));
        return true;
    }

    /** Embers instead of the vanilla swirl. Looked up late: particle types register after effects. */
    @Override public ParticleOptions createParticleOptions(MobEffectInstance instance) { return ModParticles.SEAR_EMBER.get(); }
}
