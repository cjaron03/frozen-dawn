package com.frozendawn.client;

import com.frozendawn.phase.PhaseManager;
import net.minecraft.util.Mth;

/**
 * Shared surface-blizzard wind math used by client-side ambience and rendering.
 * Keeps particle drift, flag orientation, and storm-driven motion in sync.
 */
public final class BlizzardWindHelper {

    private BlizzardWindHelper() {}

    public static boolean hasSurfaceStorm(int phase, float progress) {
        PhaseManager.Phase6Stage phase6Stage = PhaseManager.getPhase6Stage(phase, progress);
        return phase == 5 || phase6Stage == PhaseManager.Phase6Stage.EARLY || phase6Stage == PhaseManager.Phase6Stage.MID;
    }

    public static boolean hasWhiteoutBlizzard(int phase, float progress) {
        return PhaseManager.isBlizzardActive(phase, progress);
    }

    public static float getWindAngleRad(long gameTime) {
        return gameTime * 0.005f;
    }

    public static float getSurfaceStormFade(int phase, float progress) {
        if (phase < 5) {
            return 0.0f;
        }
        if (hasWhiteoutBlizzard(phase, progress)) {
            return 1.0f;
        }
        if (PhaseManager.isVacuumActive(phase, progress)) {
            return 0.0f;
        }
        return 1.0f - PhaseManager.getPhase6MidFadeProgress(progress);
    }

    public static float getSurfaceWindSpeed(int phase, float progress, long gameTime) {
        if (phase < 5) {
            return 0.0f;
        }

        float windSpeed = 1.5f + 0.5f * Mth.sin(gameTime * 0.015f);
        if (PhaseManager.isPhase6Early(phase, progress)) {
            windSpeed *= 1.3f;
        }
        return windSpeed * getSurfaceStormFade(phase, progress);
    }

    public static float getNormalizedSurfaceWindStrength(int phase, float progress, long gameTime) {
        if (phase < 5) {
            return 0.0f;
        }
        float maxWindSpeed = PhaseManager.isPhase6Early(phase, progress) ? 2.6f : 2.0f;
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
