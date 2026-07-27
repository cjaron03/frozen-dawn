package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure geometry and calm-eye rules for the Master Architect's local storm wall. */
public final class MasterArchitectEyeWallPolicy {
    public static final float HOSTILE_RADIUS = 28.0F;
    public static final float HOSTILE_HEIGHT = 38.0F;
    public static final float COLLAPSED_RADIUS = 3.5F;
    public static final float NEAR_FULL_DISTANCE = 48.0F;
    public static final float NEAR_END_DISTANCE = 64.0F;
    public static final float FAR_START_DISTANCE = 104.0F;
    public static final float FAR_FULL_DISTANCE = 128.0F;
    private static final float NEAR_BATCHED_SHELL_WEIGHT = 0.55F;
    private static final float OPEN_EYE_FOG_FLOOR = 0.18F;

    private MasterArchitectEyeWallPolicy() {
    }

    public static boolean isVisible(
            float visualTier,
            int aftermathTicks,
            float aftermathStrength) {
        if (visualTier < MasterArchitectAuraTier.NOTICED) {
            return false;
        }
        if (aftermathTicks <= 0) {
            return true;
        }
        MasterArchitectStormAftermathPolicy.Stage stage =
                MasterArchitectStormAftermathPolicy.stage(
                        aftermathTicks, aftermathStrength);
        return stage == MasterArchitectStormAftermathPolicy.Stage.CORE
                || stage == MasterArchitectStormAftermathPolicy.Stage.EYE
                || aftermathStrength <= 0.001F
                && stage == MasterArchitectStormAftermathPolicy.Stage.FADE;
    }

    public static float radius(
            float visualTier,
            int aftermathTicks,
            float aftermathStrength) {
        float collapse = collapseProgress(aftermathTicks, aftermathStrength);
        return Mth.lerp(collapse, HOSTILE_RADIUS, COLLAPSED_RADIUS);
    }

    public static float height(float visualTier) {
        return HOSTILE_HEIGHT;
    }

    public static float collapseProgress(
            int aftermathTicks,
            float aftermathStrength) {
        if (aftermathTicks <= 0 || aftermathStrength <= 0.001F) {
            return 0.0F;
        }
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(aftermathStrength);
        return Mth.clamp(
                (aftermathTicks - timeline.coreEndTick())
                        / (float) Math.max(
                        1, timeline.eyeEndTick() - timeline.coreEndTick()),
                0.0F,
                1.0F);
    }

    public static float emptyFade(
            int aftermathTicks,
            float aftermathStrength) {
        if (aftermathTicks <= 0 || aftermathStrength > 0.001F) {
            return 1.0F;
        }
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(aftermathStrength);
        return 1.0F - Mth.clamp(
                (aftermathTicks - timeline.coreEndTick())
                        / (float) Math.max(
                        1, timeline.collapseEndTick() - timeline.coreEndTick()),
                0.0F,
                1.0F);
    }

    /** 1 is the hostile storm inside the eye, 0 is beyond its outer wall. */
    public static float localStormFactor(
            double horizontalDistance,
            float visualTier,
            int aftermathTicks,
            float aftermathStrength) {
        if (!isVisible(visualTier, aftermathTicks, aftermathStrength)) {
            return 0.0F;
        }
        float radius = radius(visualTier, aftermathTicks, aftermathStrength);
        float inner = Math.max(1.75F, radius * 0.90F);
        float outer = Math.max(inner + 1.0F, radius * 1.04F);
        float linear = Mth.clamp(
                (float) ((horizontalDistance - inner) / (outer - inner)),
                0.0F,
                1.0F);
        float smooth = linear * linear * (3.0F - 2.0F * linear);
        return 1.0F - smooth;
    }

    /** Keeps horizontal whiteout strong while leaving a light haze in the open eye. */
    public static float directionalFogFactor(float cameraPitchDegrees) {
        float lookingUp = Mth.clamp(
                (-cameraPitchDegrees - 30.0F) / 38.0F,
                0.0F,
                1.0F);
        float smooth = lookingUp * lookingUp * (3.0F - 2.0F * lookingUp);
        return Mth.lerp(smooth, 1.0F, OPEN_EYE_FOG_FLOOR);
    }

    public static float nearParticleWeight(double horizontalDistance) {
        return 1.0F - smoothRange(
                horizontalDistance, NEAR_FULL_DISTANCE, NEAR_END_DISTANCE);
    }

    public static float midRenderWeight(double horizontalDistance) {
        float nearBlend = smoothRange(
                horizontalDistance, NEAR_FULL_DISTANCE, NEAR_END_DISTANCE);
        float farBlend = 1.0F - smoothRange(
                horizontalDistance, FAR_START_DISTANCE, FAR_FULL_DISTANCE);
        return Math.min(nearBlend, farBlend);
    }

    public static float batchedRenderWeight(
            double horizontalDistance, float particleWarmupProgress) {
        float warmup = Mth.clamp(particleWarmupProgress, 0.0F, 1.0F);
        float warmupFallback = nearParticleWeight(horizontalDistance)
                * Mth.lerp(warmup, 1.0F, NEAR_BATCHED_SHELL_WEIGHT);
        return Math.max(midRenderWeight(horizontalDistance), warmupFallback);
    }

    public static float distantRenderWeight(double horizontalDistance) {
        return smoothRange(
                horizontalDistance, FAR_START_DISTANCE, FAR_FULL_DISTANCE);
    }

    private static float smoothRange(double value, float start, float end) {
        float linear = Mth.clamp(
                (float) ((value - start) / (end - start)), 0.0F, 1.0F);
        return linear * linear * (3.0F - 2.0F * linear);
    }
}
