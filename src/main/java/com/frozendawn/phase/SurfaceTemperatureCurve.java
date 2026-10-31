package com.frozendawn.phase;

/** The canonical surface curve, kept independent of config and world queries. */
public final class SurfaceTemperatureCurve {
    private static final float[] BOUNDS = {0f, .05f, .12f, .22f, .34f, .46f, .60f, 1f};
    private static final float[] OFFSETS = {0f, 0f, -10f, -25f, -45f, -70f, -120f, -273f};

    private SurfaceTemperatureCurve() {}

    public static float[] phaseBounds() {
        return BOUNDS.clone();
    }

    public static float base(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        for (int i = 1; i < BOUNDS.length; i++) {
            if (progress <= BOUNDS[i]) {
                float fraction = (progress - BOUNDS[i - 1]) / (BOUNDS[i] - BOUNDS[i - 1]);
                return OFFSETS[i - 1] + fraction * (OFFSETS[i] - OFFSETS[i - 1]);
            }
        }
        return OFFSETS[OFFSETS.length - 1];
    }

    /** False calm is deliberately not multiplied by the difficulty's cold scale. */
    public static float rebound(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        for (int i = 1; i < BOUNDS.length - 1; i++) {
            float distance = Math.abs(progress - BOUNDS[i]);
            if (distance < .03f) return 3f * (1f - distance / .03f);
        }
        return 0f;
    }

    public static float temperature(float progress, float scale) {
        return base(progress) * scale + rebound(progress);
    }
}
