package com.frozendawn.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Visible status marker; mechanics remain server-authoritative in HearthrotManager. */
public final class HearthrotEffect extends MobEffect {
    public HearthrotEffect() {
        super(MobEffectCategory.HARMFUL, 0xB9D8D2);
    }
}
