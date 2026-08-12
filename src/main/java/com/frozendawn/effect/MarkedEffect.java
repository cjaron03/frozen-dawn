package com.frozendawn.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Visible warning for the Archivist's temporary world-wide pursuit command. */
public final class MarkedEffect extends MobEffect {
    public MarkedEffect() {
        super(MobEffectCategory.HARMFUL, 0x8FD8E8);
    }
}
