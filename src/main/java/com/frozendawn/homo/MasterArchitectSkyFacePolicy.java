package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure visibility and storm-collapse rules for the Major Hearth sky face. */
public final class MasterArchitectSkyFacePolicy {
    public static final float ALTITUDE = 34.0F;
    public static final float EYE_RADIUS = 28.0F;
    public static final float DISTANT_RENDER_ANCHOR = 700.0F;
    private static final float DISSOLVE_END_RADIUS = 40.0F;
    private static final float MIN_EDGE_FADE_BLOCKS = 400.0F;
    private static final float MAX_EDGE_FADE_BLOCKS = 800.0F;

    private MasterArchitectSkyFacePolicy() {
    }

    public static float targetFeatureOpacity(
            int auraTier, boolean fightActive, boolean awakened) {
        if (auraTier <= MasterArchitectAuraTier.PASSIVE) {
            return awakened ? 0.045F : 0.0F;
        }
        if (auraTier == MasterArchitectAuraTier.NOTICED) {
            return 0.22F;
        }
        return fightActive ? 1.0F : 0.84F;
    }

    public static float targetEyeStrength(
            int auraTier, boolean fightActive, float ignition) {
        if (auraTier < MasterArchitectAuraTier.NOTICED) {
            return 0.0F;
        }
        if (auraTier == MasterArchitectAuraTier.NOTICED) {
            return 0.035F;
        }
        return Mth.clamp(ignition * (fightActive ? 1.0F : 0.86F), 0.0F, 1.0F);
    }

    public static float proximityVisibility(double horizontalDistance) {
        float linear = Mth.clamp(
                (float) ((horizontalDistance - EYE_RADIUS)
                        / (DISSOLVE_END_RADIUS - EYE_RADIUS)),
                0.0F,
                1.0F);
        return linear * linear * (3.0F - 2.0F * linear);
    }

    public static double renderedHorizontalDistance(double actualDistance) {
        return Math.min(actualDistance, DISTANT_RENDER_ANCHOR);
    }

    public static float rangeVisibility(double actualDistance, double maximumDistance) {
        if (maximumDistance <= 0.0D || actualDistance >= maximumDistance) {
            return 0.0F;
        }
        float fadeBlocks = Mth.clamp(
                (float) maximumDistance * 0.20F,
                MIN_EDGE_FADE_BLOCKS,
                MAX_EDGE_FADE_BLOCKS);
        double fadeStart = Math.max(0.0D, maximumDistance - fadeBlocks);
        if (actualDistance <= fadeStart) {
            return 1.0F;
        }
        float linear = Mth.clamp(
                (float) ((actualDistance - fadeStart)
                        / Math.max(1.0D, maximumDistance - fadeStart)),
                0.0F,
                1.0F);
        float smooth = linear * linear * (3.0F - 2.0F * linear);
        return 1.0F - smooth;
    }

    public static float apparentSize(
            double actualDistance,
            double renderedDistance,
            float configuredScale) {
        float localScale = Mth.clamp(
                (float) renderedDistance / 220.0F,
                0.38F,
                1.0F);
        float recession = actualDistance <= DISTANT_RENDER_ANCHOR
                ? 1.0F
                : Mth.clamp(
                        DISTANT_RENDER_ANCHOR / (float) actualDistance,
                        0.18F,
                        1.0F);
        return localScale * recession * 36.0F
                * Mth.clamp(configuredScale, 0.25F, 3.0F);
    }

    public static AftermathFace aftermath(
            int elapsedTicks, float fieldStrength) {
        if (elapsedTicks <= 0) {
            return AftermathFace.ACTIVE;
        }
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(fieldStrength);
        MasterArchitectStormAftermathPolicy.Stage stage =
                MasterArchitectStormAftermathPolicy.stage(elapsedTicks, fieldStrength);
        if (stage == MasterArchitectStormAftermathPolicy.Stage.CORE) {
            return AftermathFace.ACTIVE;
        }
        if (stage == MasterArchitectStormAftermathPolicy.Stage.FADE) {
            float fade = Mth.clamp(elapsedTicks
                    / (float) Math.max(1, timeline.collapseEndTick()), 0.0F, 1.0F);
            return new AftermathFace(1.0F - fade, 1.0F, 0.0F, fade * 0.18F, 0.0F);
        }
        if (stage == MasterArchitectStormAftermathPolicy.Stage.EYE) {
            float progress = Mth.clamp(
                    (elapsedTicks - timeline.coreEndTick())
                            / (float) Math.max(1,
                            timeline.eyeEndTick() - timeline.coreEndTick()),
                    0.0F,
                    1.0F);
            return new AftermathFace(1.0F, 1.0F - progress * 0.08F,
                    -progress * 4.0F, progress, 0.0F);
        }
        if (stage == MasterArchitectStormAftermathPolicy.Stage.RUPTURE) {
            float progress = Mth.clamp(
                    (elapsedTicks - timeline.eyeEndTick())
                            / (float) Math.max(1,
                            timeline.ruptureEndTick() - timeline.eyeEndTick()),
                    0.0F,
                    1.0F);
            return new AftermathFace(1.0F - progress * 0.48F,
                    0.92F - progress * 0.12F,
                    -4.0F - progress * 10.0F,
                    1.0F,
                    progress);
        }
        if (stage == MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE) {
            float progress = Mth.clamp(
                    (elapsedTicks - timeline.ruptureEndTick())
                            / (float) Math.max(1,
                            timeline.collapseEndTick() - timeline.ruptureEndTick()),
                    0.0F,
                    1.0F);
            return new AftermathFace((1.0F - progress) * 0.52F,
                    Mth.lerp(progress, 0.80F, 0.24F),
                    Mth.lerp(progress, -14.0F, -88.0F),
                    1.0F,
                    1.0F);
        }
        return AftermathFace.HIDDEN;
    }

    public record AftermathFace(
            float opacity,
            float scale,
            float verticalOffset,
            float distortion,
            float tearProgress) {
        private static final AftermathFace ACTIVE =
                new AftermathFace(1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
        private static final AftermathFace HIDDEN =
                new AftermathFace(0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    }
}
