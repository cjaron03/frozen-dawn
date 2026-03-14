package com.frozendawn.client;

import net.minecraft.util.Mth;
import net.neoforged.fml.earlydisplay.ColourScheme;

public final class StartupFreezeVisuals {

    private static long startupTime = -1L;
    private static final int VANILLA_RED = 0xFFEF323D;
    private static final int FROZEN_BLUE = 0xFF122640;

    private StartupFreezeVisuals() {
    }

    public static float transition(float progressBias) {
        if (startupTime < 0L) {
            startupTime = System.currentTimeMillis();
        }

        float elapsed = (System.currentTimeMillis() - startupTime) / 1000.0f;
        float raw = Mth.clamp(Math.max(elapsed / 2.4f, progressBias), 0.0f, 1.0f);
        return raw * raw * (3.0f - 2.0f * raw);
    }

    public static float tint(float progressBias) {
        return transition(progressBias);
    }

    public static float icicleProgress(float tint) {
        return Mth.clamp((tint - 0.28f) / 0.72f, 0.0f, 1.0f);
    }

    public static int loadingBackground(float progressBias) {
        return lerpArgb(VANILLA_RED, FROZEN_BLUE, transition(progressBias));
    }

    public static ColourScheme.Colour loadingBackgroundColour(float progressBias) {
        int color = loadingBackground(progressBias);
        return new ColourScheme.Colour((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
    }

    private static int lerpArgb(int from, int to, float progress) {
        int a = lerp((from >> 24) & 0xFF, (to >> 24) & 0xFF, progress);
        int r = lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, progress);
        int g = lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, progress);
        int b = lerp(from & 0xFF, to & 0xFF, progress);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerp(int from, int to, float progress) {
        return from + Math.round((to - from) * progress);
    }
}
