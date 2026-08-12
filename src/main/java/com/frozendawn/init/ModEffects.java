package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.effect.HearthrotEffect;
import com.frozendawn.effect.MarkedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, FrozenDawn.MOD_ID);

    public static final DeferredHolder<MobEffect, HearthrotEffect> HEARTHROT =
            EFFECTS.register("hearthrot", HearthrotEffect::new);
    public static final DeferredHolder<MobEffect, MarkedEffect> MARKED =
            EFFECTS.register("marked", MarkedEffect::new);

    private ModEffects() {
    }
}
