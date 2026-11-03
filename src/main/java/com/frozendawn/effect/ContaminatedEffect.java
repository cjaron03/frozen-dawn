package com.frozendawn.effect;

import java.util.Set;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

/** Radioactive dust on the player. Lasts until washed off in water; nothing else cures it. */
public final class ContaminatedEffect extends MobEffect {
    public ContaminatedEffect() { super(MobEffectCategory.HARMFUL, 0x4A4744); }
    @Override public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) { cures.clear(); }
}
