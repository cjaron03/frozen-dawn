package com.frozendawn.init;

import com.frozendawn.FrozenDawn;
import com.frozendawn.effect.HearthrotEffect;
import com.frozendawn.effect.MarkedEffect;
import com.frozendawn.effect.RimeboundEncasementEffect;
import com.frozendawn.effect.ResonantGraspEffect;
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
    public static final DeferredHolder<MobEffect, RimeboundEncasementEffect>
            RIMEBOUND_ENCASEMENT = EFFECTS.register(
                    "rimebound_encasement", RimeboundEncasementEffect::new);
    public static final DeferredHolder<MobEffect, ResonantGraspEffect> RESONANT_GRASP =
            EFFECTS.register("resonant_grasp", ResonantGraspEffect::new);
    public static final DeferredHolder<MobEffect, com.frozendawn.effect.RadiationSicknessEffect> RADIATION_SICKNESS =
            EFFECTS.register("radiation_sickness", com.frozendawn.effect.RadiationSicknessEffect::new);
    public static final DeferredHolder<MobEffect, com.frozendawn.effect.ContaminatedEffect> CONTAMINATED =
            EFFECTS.register("contaminated", com.frozendawn.effect.ContaminatedEffect::new);

    private ModEffects() {
    }
}
