package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure timing and density policy for one deterministic Heart formation. */
public final class HeartFormationPolicy {
    public static final int DEAD_AIR_TICKS = 300;
    public static final int BASE_SHAKE_TICKS = 160;
    public static final int BASE_GATHER_TICKS = 400;
    public static final int BASE_HOLD_TICKS = 200;
    public static final int MIN_FRAGMENTS = 16;
    public static final int MAX_FRAGMENTS = 40;
    public static final double AURA_RADIUS = 112.0D;

    private HeartFormationPolicy() {
    }

    public static Timeline timeline(float fieldStrength) {
        float field = Mth.clamp(fieldStrength, 0.0F, 1.0F);
        float scale = 0.55F + 0.45F * field;
        int shakeTicks = Math.round(BASE_SHAKE_TICKS * scale);
        int gatherTicks = Math.round(BASE_GATHER_TICKS * scale);
        int holdTicks = Math.round(BASE_HOLD_TICKS * scale);
        int fragments = Math.round(MIN_FRAGMENTS
                + (MAX_FRAGMENTS - MIN_FRAGMENTS) * field);
        int shakeStart = DEAD_AIR_TICKS;
        int gatherStart = shakeStart + shakeTicks;
        int holdStart = gatherStart + gatherTicks;
        int liveStart = holdStart + holdTicks;
        return new Timeline(field, scale, shakeTicks, gatherTicks, holdTicks,
                fragments, shakeStart, gatherStart, holdStart, liveStart);
    }

    public static Snapshot snapshot(long elapsedTicks, float fieldStrength) {
        Timeline timeline = timeline(fieldStrength);
        long elapsed = Math.max(0L, elapsedTicks);
        if (elapsed < timeline.shakeStart()) {
            return new Snapshot(HeartFormationStage.DEAD_AIR,
                    progress(elapsed, 0, timeline.shakeStart()), elapsed, timeline);
        }
        if (elapsed < timeline.gatherStart()) {
            return new Snapshot(HeartFormationStage.SHAKE,
                    progress(elapsed, timeline.shakeStart(), timeline.gatherStart()),
                    elapsed, timeline);
        }
        if (elapsed < timeline.holdStart()) {
            return new Snapshot(HeartFormationStage.GATHER,
                    progress(elapsed, timeline.gatherStart(), timeline.holdStart()),
                    elapsed, timeline);
        }
        if (elapsed < timeline.liveStart()) {
            return new Snapshot(HeartFormationStage.HOLD,
                    progress(elapsed, timeline.holdStart(), timeline.liveStart()),
                    elapsed, timeline);
        }
        return new Snapshot(HeartFormationStage.LIVE, 1.0F, elapsed, timeline);
    }

    public static long elapsedAtStageStart(HeartFormationStage stage, float fieldStrength) {
        Timeline timeline = timeline(fieldStrength);
        return switch (stage) {
            case NONE, DEAD_AIR -> 0L;
            case SHAKE -> timeline.shakeStart();
            case GATHER -> timeline.gatherStart();
            case HOLD -> timeline.holdStart();
            case LIVE -> timeline.liveStart();
        };
    }

    private static float progress(long elapsed, int start, int end) {
        if (end <= start) {
            return 1.0F;
        }
        return Mth.clamp((float) (elapsed - start) / (end - start), 0.0F, 1.0F);
    }

    public record Timeline(
            float fieldStrength,
            float durationScale,
            int shakeTicks,
            int gatherTicks,
            int holdTicks,
            int fragmentCount,
            int shakeStart,
            int gatherStart,
            int holdStart,
            int liveStart) {
    }

    public record Snapshot(
            HeartFormationStage stage,
            float stageProgress,
            long elapsedTicks,
            Timeline timeline) {
    }
}
