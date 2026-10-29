package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The white-out as an Icarus ignites: the screen flashes white, as strong as the viewer is close, and fades over about a
 * second. Reduced flash keeps it to a soft wash. */
public final class IcarusIgnitionFlash {
    /** Blocks from the core within which ignition flashes the screen. */
    public static final double RANGE = 48;
    private static final float FADE_TICKS = 24;
    private static float strength;
    private static long at = Long.MIN_VALUE;
    private IcarusIgnitionFlash() {}

    static void trigger(long now, float level) {
        if (now - at > FADE_TICKS || level > strength) { strength = level; at = now; }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float age = (mc.level.getGameTime() - at) + delta.getGameTimeDeltaPartialTick(false);
        if (age < 0 || age >= FADE_TICKS) return;
        float k = 1 - age / FADE_TICKS;
        float peak = FrozenDawnConfig.ICARUS_REDUCED_FLASH.get() ? 0.25f : 0.9f;
        int alpha = (int) (255 * peak * strength * k * k);
        if (alpha > 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24 | 0xF4FAFF);
    }
}
