package com.frozendawn.bloom;

import net.minecraft.util.Mth;

/** Pure timing policy for the first post-Maeve Bloom breakthrough. */
public final class BloomEruptionPolicy {
    public static final int RUMBLE_TICKS = 60;
    public static final int COMPLETE_TICKS = 84;

    private BloomEruptionPolicy() {
    }

    public static Stage stage(long elapsedTicks) {
        if (elapsedTicks < 0L) {
            return Stage.WAITING;
        }
        if (elapsedTicks < RUMBLE_TICKS) {
            return Stage.RUMBLING;
        }
        if (elapsedTicks < COMPLETE_TICKS) {
            return Stage.ERUPTING;
        }
        return Stage.COMPLETE;
    }

    public static float rumbleProgress(long elapsedTicks) {
        return Mth.clamp(elapsedTicks / (float) RUMBLE_TICKS, 0.0F, 1.0F);
    }

    public enum Stage {
        WAITING,
        RUMBLING,
        ERUPTING,
        COMPLETE
    }
}
