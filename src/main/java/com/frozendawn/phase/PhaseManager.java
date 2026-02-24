package com.frozendawn.phase;

import net.minecraft.util.Mth;

/**
 * Pure calculation class for apocalypse phase progression.
 * All methods are stateless - they derive values from (currentDay, totalDays).
 *
 * Phase boundaries (as fraction of totalDays):
 *   Phase 1: 0.00 - 0.15  (days 0-15   @ 100 total)
 *   Phase 2: 0.15 - 0.35  (days 15-35  @ 100 total)
 *   Phase 3: 0.35 - 0.55  (days 35-55  @ 100 total)
 *   Phase 4: 0.55 - 0.75  (days 55-75  @ 100 total)
 *   Phase 5: 0.75 - 1.00  (days 75-100 @ 100 total)
 */
public final class PhaseManager {

    private PhaseManager() {}

    // Phase boundary fractions (6 entries = 5 segments)
    private static final float[] PHASE_BOUNDS = {0.0f, 0.15f, 0.35f, 0.55f, 0.75f, 1.0f};

    // Sun distance multiplier at each boundary
    private static final float[] SUN_DISTANCE = {1.0f, 0.8f, 0.5f, 0.25f, 0.1f, 0.02f};

    // Temperature offset (Celsius) at each boundary
    private static final float[] TEMP_OFFSET = {0f, -10f, -25f, -45f, -70f, -120f};

    // Sun brightness at each boundary
    private static final float[] SUN_BRIGHTNESS = {1.0f, 0.9f, 0.7f, 0.45f, 0.2f, 0.05f};

    // Sky light multiplier at each boundary
    private static final float[] SKY_LIGHT = {1.0f, 1.0f, 0.85f, 0.6f, 0.3f, 0.05f};

    // Day length multiplier at each boundary
    private static final float[] DAY_LENGTH = {1.0f, 0.95f, 0.8f, 0.6f, 0.4f, 0.2f};

    /**
     * Returns overall progress as a 0.0-1.0 float.
     */
    public static float getProgress(int currentDay, int totalDays) {
        if (totalDays <= 0) return 1.0f;
        return Mth.clamp((float) currentDay / totalDays, 0.0f, 1.0f);
    }

    /**
     * Returns the current phase (1-5).
     */
    public static int getPhase(int currentDay, int totalDays) {
        float progress = getProgress(currentDay, totalDays);
        for (int i = 1; i < PHASE_BOUNDS.length; i++) {
            if (progress < PHASE_BOUNDS[i]) {
                return i;
            }
        }
        return 5;
    }

    /**
     * Returns the temperature offset for the current progression.
     * Ranges from 0 (phase 1 start) to BASE_PHASE5_TEMP (phase 5 end).
     * Includes false calm rebounds: +3C warmth spikes within 3% of each phase boundary.
     */
    public static float getTemperatureOffset(int currentDay, int totalDays) {
        float baseTemp = interpolate(TEMP_OFFSET, currentDay, totalDays);
        float rebound = getFalseCalmRebound(currentDay, totalDays);
        return baseTemp + rebound;
    }

    /**
     * Returns a small warmth spike (+3C max) near phase boundaries.
     * Creates "false calm" moments where the temperature briefly seems to stabilize.
     */
    public static float getFalseCalmRebound(int currentDay, int totalDays) {
        float progress = getProgress(currentDay, totalDays);
        float reboundRange = 0.03f; // 3% of total progression

        // Check proximity to each phase boundary (skip first at 0.0)
        for (int i = 1; i < PHASE_BOUNDS.length - 1; i++) {
            float boundary = PHASE_BOUNDS[i];
            float dist = Math.abs(progress - boundary);
            if (dist < reboundRange) {
                // Smooth spike: peaks at boundary, fades to zero at reboundRange
                float intensity = 1.0f - (dist / reboundRange);
                return 3.0f * intensity;
            }
        }
        return 0.0f;
    }

    /**
     * Returns the sun visual scale.
     * Ranges from 1.0 (normal) to 0.02 (tiny dot).
     */
    public static float getSunScale(int currentDay, int totalDays) {
        return interpolate(SUN_DISTANCE, currentDay, totalDays);
    }

    /**
     * Returns the sun brightness.
     * Ranges from 1.0 (normal) to 0.05 (barely visible).
     */
    public static float getSunBrightness(int currentDay, int totalDays) {
        return interpolate(SUN_BRIGHTNESS, currentDay, totalDays);
    }

    /**
     * Returns the sky light multiplier.
     * Ranges from 1.0 (normal) to 0.05 (near darkness).
     */
    public static float getSkyLight(int currentDay, int totalDays) {
        return interpolate(SKY_LIGHT, currentDay, totalDays);
    }

    /**
     * Returns the day length multiplier.
     * Ranges from 1.0 (normal) to 0.2 (very short days).
     */
    public static float getDayLength(int currentDay, int totalDays) {
        return interpolate(DAY_LENGTH, currentDay, totalDays);
    }

    /**
     * Returns the depth-based temperature modifier for a given Y level.
     * Higher Y = colder, deeper = warmer (geothermal).
     */
    public static float getDepthModifier(int y) {
        // Piecewise linear: surface is cold, deep underground is warm enough to survive
        // At Y=-64: +80C (counteracts most of phase 5's -120C)
        if (y >= 256) return -10.0f;
        if (y >= 128) return Mth.lerp((y - 128) / 128.0f, -5.0f, -10.0f);
        if (y >= 64)  return Mth.lerp((y - 64) / 64.0f, 0.0f, -5.0f);
        if (y >= 32)  return Mth.lerp((y - 32) / 32.0f, 10.0f, 0.0f);
        if (y >= 0)   return Mth.lerp(y / 32.0f, 25.0f, 10.0f);
        if (y >= -32) return Mth.lerp((y + 32) / 32.0f, 55.0f, 25.0f);
        if (y >= -64) return Mth.lerp((y + 64) / 32.0f, 80.0f, 55.0f);
        return 80.0f;
    }

    /**
     * Returns the starting day of a given phase.
     */
    public static int getPhaseStartDay(int phase, int totalDays) {
        if (phase < 1) return 0;
        if (phase > 5) return totalDays;
        return (int) (PHASE_BOUNDS[phase - 1] * totalDays);
    }

    /**
     * Linearly interpolates a value array across phase boundaries.
     */
    private static float interpolate(float[] values, int currentDay, int totalDays) {
        float progress = getProgress(currentDay, totalDays);

        for (int i = 1; i < PHASE_BOUNDS.length; i++) {
            if (progress <= PHASE_BOUNDS[i]) {
                float segStart = PHASE_BOUNDS[i - 1];
                float segEnd = PHASE_BOUNDS[i];
                float segProgress = (progress - segStart) / (segEnd - segStart);
                return Mth.lerp(segProgress, values[i - 1], values[i]);
            }
        }

        return values[values.length - 1];
    }
}
