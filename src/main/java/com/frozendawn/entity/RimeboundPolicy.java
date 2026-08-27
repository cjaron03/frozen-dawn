package com.frozendawn.entity;

import net.minecraft.util.Mth;

/** Pure tuning and spawn policy kept separate from entity ticking. */
public final class RimeboundPolicy {
    public static final int EMERGENCE_TICKS = 30;
    public static final int ERUPTION_TELEGRAPH_TICKS = 15;
    public static final int LANCE_WINDUP_TICKS = 24;
    public static final int LEAP_WINDUP_TICKS = 14;
    public static final int LEAP_RECOVERY_TICKS = 30;
    public static final int ARMOR_REBUILD_TICKS = 30;
    public static final int DEATH_FREEZE_TICKS = 20;
    public static final int SHELL_MAX_INTEGRITY = 10;

    private RimeboundPolicy() {
    }

    public static float baseEvolutionChance(long ticksSinceErasure) {
        long day = Math.max(0L, ticksSinceErasure) / 24_000L;
        if (day < 1L) {
            return 0.0F;
        }
        if (day < 3L) {
            return 0.05F;
        }
        if (day < 7L) {
            return 0.12F;
        }
        return 0.22F;
    }

    public static float evolutionChance(long ticksSinceErasure, float bloomPressure,
                                         double multiplier) {
        float bloomBonus = Mth.clamp((bloomPressure - 1.0F) / 1.25F, 0.0F, 1.0F)
                * 0.08F;
        return Mth.clamp((baseEvolutionChance(ticksSinceErasure) + bloomBonus)
                * (float) Math.max(0.0D, multiplier), 0.0F, 0.30F);
    }

    public static int shellDamage(float incomingDamage, boolean projectile, boolean heavyMelee) {
        float factor = projectile ? 0.45F : heavyMelee ? 1.7F : 1.0F;
        return Math.max(1, Mth.ceil(incomingDamage * factor));
    }
}
