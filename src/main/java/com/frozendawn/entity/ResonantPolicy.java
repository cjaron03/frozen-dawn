package com.frozendawn.entity;

import net.minecraft.util.Mth;

/** Pure tuning for Resonant sensing, progression, and spawning. */
public final class ResonantPolicy {
    public static final int EVENT_LIFETIME_TICKS = 40;
    public static final int EVENT_CAP = 256;
    public static final int QUERY_INTERVAL_TICKS = 6;
    public static final int BREACH_WINDUP_TICKS = 20;
    public static final int BREACH_MISS_RECOVERY_TICKS = 40;
    public static final int DISORIENTED_TICKS = 60;
    public static final int PULSE_WINDUP_TICKS = 30;
    public static final int GRAB_MAX_TICKS = 100;
    public static final int GRAB_ESCAPE_SWINGS = 5;
    public static final int GRAB_COOLDOWN_TICKS = 600;

    private ResonantPolicy() {
    }

    public static float baseEvolutionChance(long ticksSinceErasure) {
        long day = Math.max(0L, ticksSinceErasure) / 24_000L;
        if (day < 1L) return 0.0F;
        if (day < 3L) return 0.04F;
        if (day < 7L) return 0.10F;
        return 0.18F;
    }

    public static float evolutionChance(long ticksSinceErasure, float bloomPressure,
                                        double multiplier) {
        float bloomBonus = Mth.clamp((bloomPressure - 1.0F) / 1.25F, 0.0F, 1.0F)
                * 0.07F;
        return Mth.clamp((baseEvolutionChance(ticksSinceErasure) + bloomBonus)
                * (float) multiplier, 0.0F, 0.25F);
    }

    public static float signalConfidence(float strength, double distance,
                                         int ageTicks, boolean repeated) {
        float age = Mth.clamp(ageTicks / (float) EVENT_LIFETIME_TICKS, 0.0F, 1.0F);
        float score = strength * 4.4F - (float) distance * 0.24F - age * 5.0F;
        if (repeated) score += 8.0F;
        return Math.max(0.0F, score);
    }

    public static float decayConfidence(float confidence, int quietTicks) {
        if (quietTicks <= 0) return confidence;
        return Math.max(0.0F, confidence - quietTicks * (2.5F / 20.0F));
    }

    public static int sensingRadius(boolean denselyEnclosed) {
        return denselyEnclosed ? 48 : 32;
    }

    public static boolean pulseReveals(boolean moving) {
        return moving;
    }

    public static float movementStrength(boolean sprinting, boolean sneaking) {
        float strength = sprinting ? 3.0F : 1.0F;
        return sneaking ? strength * 0.2F : strength;
    }
}
