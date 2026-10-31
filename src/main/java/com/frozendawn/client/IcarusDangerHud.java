package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The screen's own warning as an Icarus nears the end: red edges that pulse with the core's heartbeat, deeper the worse it
 * gets and the closer the player stands, and at critical a blinking banner with the shield reading and what to do. The
 * client ticker reports the nearest, worst core each tick; the overlay fades out once no core reports. */
public final class IcarusDangerHud {
    /** Blocks from the core within which the overlay shows, fading with distance. */
    public static final double RANGE = 64;
    private static final int EDGE = 0xC01008, TITLE = 0xFFFF3A2A, HINT = 0xFFFFC0B0;
    private static float danger, beat;
    private static double shieldPct;
    private static long seen = Long.MIN_VALUE;
    private IcarusDangerHud() {}

    static void report(long now, float level, double shield, float beatValue) {
        if (now != seen || level > danger) { danger = level; shieldPct = shield; beat = beatValue; seen = now; }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui || mc.level.getGameTime() - seen > 2 || danger <= 0.15f) return;
        boolean reduced = FrozenDawnConfig.ICARUS_REDUCED_FLASH.get();
        float pulse = reduced ? 0.3f : IcarusCoreAnim.heartbeat((beat % IcarusCoreAnim.BEAT) / IcarusCoreAnim.BEAT);
        float strength = Math.min(1, danger * (0.45f + 0.35f * pulse));
        int w = g.guiWidth(), h = g.guiHeight(), band = Math.max(24, h / 5);
        for (int i = 0; i < band; i += 2) {
            float f = 1 - i / (float) band;
            int color = (int) (strength * f * f * 200) << 24 | EDGE;
            g.fill(0, i, w, i + 2, color); g.fill(0, h - i - 2, w, h - i, color);
            g.fill(i, 0, i + 2, h, color); g.fill(w - i - 2, 0, w - i, h, color);
        }
        if (danger < 0.55f) return;
        boolean lit = reduced || (mc.level.getGameTime() / 8) % 3 != 0;
        int y = (int) (h * 0.18f);
        if (lit) {
            var title = Component.translatable("hud.frozendawn.icarus.critical");
            g.pose().pushPose();
            g.pose().translate(w / 2f, y, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(mc.font, title, -mc.font.width(title) / 2, 0, TITLE, true);
            g.pose().popPose();
        }
        var hint = Component.translatable("hud.frozendawn.icarus.critical_hint", String.format(Locale.ROOT, "%.1f", shieldPct));
        g.drawString(mc.font, hint, (w - mc.font.width(hint)) / 2, y + 22, HINT, true);
    }
}
