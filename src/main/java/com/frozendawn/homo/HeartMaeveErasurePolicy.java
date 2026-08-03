package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure timing policy for the deliberate final erasure of Maeve. */
public final class HeartMaeveErasurePolicy {
    public static final int CHANNEL_TICKS = 80;
    public static final int UNMAKING_TICKS = 120;
    public static final int FORGING_TICKS = 100;
    public static final int TOTAL_TICKS = UNMAKING_TICKS + FORGING_TICKS;

    private HeartMaeveErasurePolicy() {
    }

    public static float progress(long elapsedTicks) {
        return Mth.clamp(elapsedTicks / (float) TOTAL_TICKS, 0.0F, 1.0F);
    }

    public static float unmakingProgress(long elapsedTicks) {
        return Mth.clamp(elapsedTicks / (float) UNMAKING_TICKS, 0.0F, 1.0F);
    }

    public static float forgingProgress(long elapsedTicks) {
        return Mth.clamp((elapsedTicks - UNMAKING_TICKS) / (float) FORGING_TICKS,
                0.0F, 1.0F);
    }

    public static boolean forging(long elapsedTicks) {
        return elapsedTicks >= UNMAKING_TICKS && elapsedTicks < TOTAL_TICKS;
    }

    public static boolean complete(long elapsedTicks) {
        return elapsedTicks >= TOTAL_TICKS;
    }
}
