package com.frozendawn.world;

import net.minecraft.util.Mth;

/** Pure probability and cooldown policy for post-Maeve encounter variety. */
public final class PostMaeveEncounterPolicy {
    public static final long GLOBAL_ENCOUNTER_COOLDOWN_TICKS = 2_400L;
    public static final long REPEAT_DAMPING_TICKS = 12_000L;
    public static final double REPEAT_MULTIPLIER = 0.35D;
    public static final double FAILURE_BONUS_PER_ATTEMPT = 0.025D;
    public static final double MAX_FAILURE_BONUS = 0.25D;

    private PostMaeveEncounterPolicy() {
    }

    public static boolean typeCooldownReady(PostMaeveEncounterType type,
                                            long now, long lastSuccessTick) {
        return lastSuccessTick < 0L
                || now - lastSuccessTick >= type.minimumIntervalTicks();
    }

    public static boolean globalCooldownReady(long now, long lastEncounterTick) {
        return lastEncounterTick < 0L
                || now - lastEncounterTick >= GLOBAL_ENCOUNTER_COOLDOWN_TICKS;
    }

    public static double effectiveChance(PostMaeveEncounterType type,
                                         double baseChance,
                                         long now,
                                         long windowStartTick,
                                         int failedAttempts,
                                         boolean repeatedRecently) {
        double clampedBase = Mth.clamp(baseChance, 0.0D, 1.0D);
        long elapsed = windowStartTick < 0L ? 0L
                : Math.max(0L, now - windowStartTick);
        if (elapsed >= type.guaranteedIntervalTicks()) return 1.0D;

        long rampStart = type.minimumIntervalTicks();
        long rampDuration = Math.max(1L,
                type.guaranteedIntervalTicks() - rampStart);
        double progress = Mth.clamp(
                (double) (elapsed - rampStart) / rampDuration, 0.0D, 1.0D);
        double eased = progress * progress * (3.0D - 2.0D * progress);
        double chance = clampedBase + (1.0D - clampedBase) * eased;
        chance += progress * Math.min(MAX_FAILURE_BONUS,
                Math.max(0, failedAttempts) * FAILURE_BONUS_PER_ATTEMPT);
        if (repeatedRecently) chance *= REPEAT_MULTIPLIER;
        return Mth.clamp(chance, 0.0D, 1.0D);
    }

    public static boolean isGuaranteed(PostMaeveEncounterType type, long now,
                                       long windowStartTick) {
        return windowStartTick >= 0L
                && now - windowStartTick >= type.guaranteedIntervalTicks();
    }
}
