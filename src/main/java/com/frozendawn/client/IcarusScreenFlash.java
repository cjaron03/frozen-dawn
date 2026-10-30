package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** A full-screen flash from an Icarus event, as strong as the viewer is close, fading over about a second: white as the
 * core ignites, red as CLIP dumps it. Reduced flash keeps it to a soft wash. */
public final class IcarusScreenFlash {
    /** Blocks from the core within which an event flashes the screen. */
    public static final double RANGE = 48;
    public static final int IGNITION = 0xF4FAFF, CLIP = 0xFF2A1A;
    private static final float FADE_TICKS = 24;
    private static float strength;
    private static int color;
    private static long at = Long.MIN_VALUE;
    private IcarusScreenFlash() {}

    static void trigger(long now, float level, int rgb) {
        if (now - at > FADE_TICKS || level > strength) { strength = level; color = rgb; at = now; }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float age = (mc.level.getGameTime() - at) + delta.getGameTimeDeltaPartialTick(false);
        if (age < 0 || age >= FADE_TICKS) return;
        float k = 1 - age / FADE_TICKS;
        float peak = FrozenDawnConfig.ICARUS_REDUCED_FLASH.get() ? 0.25f : 0.9f;
        int alpha = (int) (255 * peak * strength * k * k);
        if (alpha > 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | color);
    }
}
