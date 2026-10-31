package com.frozendawn.client;

/** Pure presentation controls shared by the exhale emitter and its translucent wisps. */
public final class ColdBreathPolicy {
    private ColdBreathPolicy() { }
    public static boolean visible(float temperature, boolean creative, boolean submerged, boolean vacuum) {
        return Float.isFinite(temperature) && temperature < 0 && !creative && !submerged && !vacuum;
    }
    public static int interval(float temperature) {
        return Math.round(60 - 20 * Math.clamp(-temperature / 40, 0, 1));
    }
    public static int wisps(float temperature) { return temperature < -20 ? 4 : 3; }
    public static int wisps(float temperature, float exertion) {
        return wisps(temperature) + (exertion >= 0.5F ? 1 : 0);
    }
    public static float cadence(float exertion) {
        return 1 + 0.30F * Math.clamp(exertion, 0, 1);
    }
    public static float opacity(float life) {
        // Briefly gather into an exhale, then dissolve with a gentle, flat-ended tail.
        float rise = smoothstep(Math.clamp(life / 0.20F, 0, 1));
        float fall = smoothstep(Math.clamp((1 - life) / 0.76F, 0, 1));
        return 0.30F * rise * fall;
    }
    private static float smoothstep(float value) { return value * value * (3 - 2 * value); }
    public static float size(float life) { return 0.035F + 0.12F * Math.clamp(life, 0, 1); }
}
