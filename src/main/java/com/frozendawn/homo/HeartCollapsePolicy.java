package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure timeline policy for the one-time archive collapse. */
public final class HeartCollapsePolicy {
    public static final int RUPTURE_TICKS = 80;
    public static final int FALL_TICKS = 140;
    public static final int SETTLE_TICKS = 100;
    public static final int DORMANT_START = RUPTURE_TICKS + FALL_TICKS + SETTLE_TICKS;

    private HeartCollapsePolicy() {
    }

    public static Snapshot snapshot(long elapsedTicks, float fieldStrength) {
        long elapsed = Math.max(0L, elapsedTicks);
        Timeline timeline = timeline(fieldStrength);
        if (elapsed < RUPTURE_TICKS) {
            return new Snapshot(HeartCollapseStage.RUPTURE,
                    progress(elapsed, RUPTURE_TICKS), elapsed, timeline);
        }
        if (elapsed < RUPTURE_TICKS + FALL_TICKS) {
            return new Snapshot(HeartCollapseStage.FALL,
                    progress(elapsed - RUPTURE_TICKS, FALL_TICKS), elapsed, timeline);
        }
        if (elapsed < DORMANT_START) {
            return new Snapshot(HeartCollapseStage.SETTLE,
                    progress(elapsed - RUPTURE_TICKS - FALL_TICKS, SETTLE_TICKS),
                    elapsed, timeline);
        }
        return new Snapshot(HeartCollapseStage.DORMANT, 1.0F, elapsed, timeline);
    }

    public static Timeline timeline(float fieldStrength) {
        float strength = Mth.clamp(fieldStrength, 0.0F, 1.0F);
        return new Timeline(Math.round(10.0F + 14.0F * strength));
    }

    public static long elapsedAtStageStart(HeartCollapseStage stage) {
        return switch (stage) {
            case NONE, RUPTURE -> 0L;
            case FALL -> RUPTURE_TICKS;
            case SETTLE -> RUPTURE_TICKS + FALL_TICKS;
            case DORMANT -> DORMANT_START;
        };
    }

    private static float progress(long elapsed, int duration) {
        return Mth.clamp(elapsed / (float) Math.max(1, duration), 0.0F, 1.0F);
    }

    public record Timeline(int fragmentCount) {
    }

    public record Snapshot(
            HeartCollapseStage stage,
            float stageProgress,
            long elapsedTicks,
            Timeline timeline) {
    }
}
