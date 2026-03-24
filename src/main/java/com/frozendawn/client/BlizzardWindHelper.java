package com.frozendawn.client;

import net.minecraft.util.Mth;

/**
 * Shared surface-blizzard wind math used by client-side ambience and rendering.
 * Keeps particle drift, flag orientation, and storm-driven motion in sync.
 */
public final class BlizzardWindHelper {

    public static final float PHASE6_FULL_BLIZZARD_END = 0.72f;
    public static final float PHASE6_WIND_END = 0.85f;

    private BlizzardWindHelper() {}

    public static boolean hasSurfaceBlizzard(int phase, float progress) {
        return phase >= 5 && !(phase >= 6 && progress >= PHASE6_WIND_END);
    }

    public static float getWindAngleRad(long gameTime) {
        return gameTime * 0.005f;
    }

    public static float getWindFade(int phase, float progress) {
        if (phase < 5) {
            return 0.0f;
        }
        if (phase < 6 || progress <= PHASE6_FULL_BLIZZARD_END) {
            return 1.0f;
        }
        if (progress >= PHASE6_WIND_END) {
            return 0.0f;
        }
        float fadeProgress = (progress - PHASE6_FULL_BLIZZARD_END) / (PHASE6_WIND_END - PHASE6_FULL_BLIZZARD_END);
        return 1.0f - Mth.clamp(fadeProgress, 0.0f, 1.0f);
    }

    public static float getSurfaceWindSpeed(int phase, float progress, long gameTime) {
        if (phase < 5) {
            return 0.0f;
        }

        float windSpeed = 1.5f + 0.5f * Mth.sin(gameTime * 0.015f);
        if (phase >= 6 && progress <= PHASE6_FULL_BLIZZARD_END) {
            windSpeed *= 1.3f;
        }
        return windSpeed * getWindFade(phase, progress);
    }

    public static float getNormalizedSurfaceWindStrength(int phase, float progress, long gameTime) {
        if (phase < 5) {
            return 0.0f;
        }
        float maxWindSpeed = phase >= 6 && progress <= PHASE6_FULL_BLIZZARD_END ? 2.6f : 2.0f;
        return Mth.clamp(getSurfaceWindSpeed(phase, progress, gameTime) / maxWindSpeed, 0.0f, 1.0f);
    }

    public static float getWindX(int phase, float progress, long gameTime) {
        return getSurfaceWindSpeed(phase, progress, gameTime) * Mth.sin(getWindAngleRad(gameTime));
    }

    public static float getWindZ(int phase, float progress, long gameTime) {
        return getSurfaceWindSpeed(phase, progress, gameTime) * Mth.cos(getWindAngleRad(gameTime));
    }

    /**
     * Returns the Y rotation needed to rotate a +X-aligned flag so it points along
     * the same world-space direction as the surface snow drift.
     */
    public static float getFlagYawDegrees(int phase, float progress, long gameTime) {
        float windX = getWindX(phase, progress, gameTime);
        float windZ = getWindZ(phase, progress, gameTime);
        if (Math.abs(windX) < 1.0e-4f && Math.abs(windZ) < 1.0e-4f) {
            return 0.0f;
        }
        return (float) Math.toDegrees(Math.atan2(-windZ, windX));
    }
}
