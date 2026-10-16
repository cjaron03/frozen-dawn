package com.frozendawn.effect;

import java.util.Set;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/** Display and identity of radiation sickness. Symptoms are driven from stored dose by RadiationHandler,
 * which reapplies this effect every second, so milk or any other cure cannot clear it. */
public final class RadiationSicknessEffect extends MobEffect {
    public RadiationSicknessEffect() { super(MobEffectCategory.HARMFUL, 0xE6C229); }
    @Override public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) { cures.clear(); }
}
