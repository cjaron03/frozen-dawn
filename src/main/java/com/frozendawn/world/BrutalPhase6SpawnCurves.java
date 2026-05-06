package com.frozendawn.world;

import com.frozendawn.config.ConfigPresets;
import net.minecraft.util.Mth;

final class BrutalPhase6SpawnCurves {

    private static final float START = 0.60F;
    private static final float EARLY_PEAK = 0.66F;
    private static final float MID_START = 0.72F;
    private static final float MID_DEEP = 0.78F;
    private static final float LATE_START = 0.82F;
    private static final float VACUUM_START = 0.85F;

    private BrutalPhase6SpawnCurves() {}

    static boolean isActive() {
        return ConfigPresets.detectCurrentPreset() == ConfigPresets.BRUTAL;
    }

    static float returnedHunterChance(float progress) {
        if (progress < START) {
            return 0.0F;
        }
        if (progress < EARLY_PEAK) {
            return lerp(progress, START, EARLY_PEAK, 0.012F, 0.024F);
        }
        if (progress < MID_START) {
            return lerp(progress, EARLY_PEAK, MID_START, 0.024F, 0.035F);
        }
        if (progress < MID_DEEP) {
            return 0.035F;
        }
        if (progress < LATE_START) {
            return lerp(progress, MID_DEEP, LATE_START, 0.035F, 0.010F);
        }
        if (progress < VACUUM_START) {
            return lerp(progress, LATE_START, VACUUM_START, 0.010F, 0.0F);
        }
        return 0.0F;
    }

    static float hollowChance(float progress) {
        if (progress < START) {
            return 0.0F;
        }
        if (progress < EARLY_PEAK) {
            return lerp(progress, START, EARLY_PEAK, 0.20F, 0.30F);
        }
        if (progress < MID_START) {
            return lerp(progress, EARLY_PEAK, MID_START, 0.30F, 0.40F);
        }
        if (progress < MID_DEEP) {
            return lerp(progress, MID_START, MID_DEEP, 0.30F, 0.15F);
        }
        if (progress < LATE_START) {
            return lerp(progress, MID_DEEP, LATE_START, 0.15F, 0.0F);
        }
        return 0.0F;
    }

    static float mimicChance(float progress) {
        if (progress < EARLY_PEAK) {
            return 0.0F;
        }
        if (progress < MID_START) {
            return lerp(progress, EARLY_PEAK, MID_START, 0.0F, 0.002F);
        }
        if (progress < MID_DEEP) {
            return lerp(progress, MID_START, MID_DEEP, 0.002F, 0.008F);
        }
        if (progress < LATE_START) {
            return lerp(progress, MID_DEEP, LATE_START, 0.008F, 0.025F);
        }
        if (progress < VACUUM_START) {
            return lerp(progress, LATE_START, VACUUM_START, 0.025F, 0.050F);
        }
        return 0.050F;
    }

    static float architectChance(float progress) {
        if (progress < MID_START) {
            return 0.0F;
        }
        if (progress < MID_DEEP) {
            return lerp(progress, MID_START, MID_DEEP, 0.002F, 0.008F);
        }
        if (progress < LATE_START) {
            return lerp(progress, MID_DEEP, LATE_START, 0.008F, 0.025F);
        }
        if (progress < VACUUM_START) {
            return lerp(progress, LATE_START, VACUUM_START, 0.025F, 0.050F);
        }
        return 0.050F;
    }

    private static float lerp(float progress, float start, float end, float from, float to) {
        return Mth.lerp(Mth.clamp((progress - start) / (end - start), 0.0F, 1.0F), from, to);
    }
}
