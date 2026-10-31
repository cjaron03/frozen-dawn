package com.frozendawn.client;

/** Phase fog targets lit by the actual sun position, independently of storm darkness. */
public final class PhaseFogColorPolicy {
    private static final float[][] COLORS = {
            {0.85F, 0.55F, 0.35F}, // Phase 1: warm amber by day
            {0.60F, 0.62F, 0.65F},
            {0.25F, 0.35F, 0.55F},
            {0.15F, 0.12F, 0.35F},
            {0.05F, 0.03F, 0.10F},
            {0.0F, 0.0F, 0.02F}
    };
    private static final float[] FLOORS = {0.15F, 0.15F, 0.10F, 0.08F, 0.04F, 0.01F};
    private static final float NIGHT_ILLUMINATION = 0.04F;

    public record Color(float red, float green, float blue) { }

    private PhaseFogColorPolicy() { }

    public static Color target(int phase, float skyLight, float sunBrightness, float sunAngle) {
        int index = Math.clamp(phase - 1, 0, 5);
        float brightness = Math.max(FLOORS[index], skyLight * (0.3F + 0.7F * sunBrightness));
        if (phase == 5) brightness = Math.min(brightness, 0.06F);

        float red = COLORS[index][0];
        float green = COLORS[index][1];
        float blue = COLORS[index][2];
        if (phase >= 1 && phase <= 4) {
            // Vanilla's solar-light factor: smooth twilight, zero once the sun is well below the horizon.
            float daylight = Math.clamp((float) Math.cos(sunAngle) * 2.0F + 0.5F, 0.0F, 1.0F);
            brightness *= NIGHT_ILLUMINATION + (1.0F - NIGHT_ILLUMINATION) * daylight;
            if (phase == 1) {
                // Amber belongs to sunlight; the unlit atmosphere uses a cool, dim target.
                red = 0.20F + (red - 0.20F) * daylight;
                green = 0.25F + (green - 0.25F) * daylight;
                blue = 0.40F + (blue - 0.40F) * daylight;
            }
        }
        return new Color(red * brightness, green * brightness, blue * brightness);
    }
}
