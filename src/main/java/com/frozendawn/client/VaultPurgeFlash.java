package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** The purge's white-out: solid for half a second, over the throw back to the ledge, then fading. Reduced flash softens it. */
public final class VaultPurgeFlash {
    private static final float HOLD = 10, FADE = 50;
    private static long at = Long.MIN_VALUE;
    private VaultPurgeFlash() { }

    public static void receive() { var level = Minecraft.getInstance().level; if (level != null) at = level.getGameTime(); }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || at == Long.MIN_VALUE) return;
        float age = mc.level.getGameTime() - at + delta.getGameTimeDeltaPartialTick(false);
        if (age < 0 || age >= HOLD + FADE) return;
        float k = age < HOLD ? 1 : 1 - (age - HOLD) / FADE;
        float peak = FrozenDawnConfig.ICARUS_REDUCED_FLASH.get() ? .25f : 1;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (int) (255 * peak * k) << 24 | 0xF4FAFF);
    }
}
