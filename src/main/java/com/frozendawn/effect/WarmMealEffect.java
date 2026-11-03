package com.frozendawn.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** From a Steaming Seafood Boil; the body temperature bonus is applied in PlayerTickHandler. */
public final class WarmMealEffect extends MobEffect {
    public WarmMealEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xE8823A);
    }
}
