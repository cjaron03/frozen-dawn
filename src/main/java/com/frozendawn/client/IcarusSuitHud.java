package com.frozendawn.client;

import com.frozendawn.block.HalseyCoreBlockEntity;
import com.frozendawn.event.SuitIntegrityHandler;
import com.frozendawn.power.IcarusPhysics;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The sealed suit's reactor telemetry: while a lit Icarus is loaded within RANGE, a small panel in the top right shows
 * the nearest core's state, shield and core temperature, tinted by its alarm. The client ticker reports every lit core
 * each tick and the nearest is kept; the panel goes once none reports. */
public final class IcarusSuitHud {
    public static final double RANGE = 64;
    private static final int MARGIN = 6, PAD_Y = 4, HEIGHT = 2 * PAD_Y + 18;
    private static HalseyCoreBlockEntity.Visual visual;
    private static double distance;
    private static long seen = Long.MIN_VALUE;
    private IcarusSuitHud() {}

    static void report(long now, double d, HalseyCoreBlockEntity.Visual v) {
        if (d > RANGE) return;
        if (now != seen || d < distance) { visual = v; distance = d; seen = now; }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || visual == null || mc.level.getGameTime() - seen > 2
                || !SuitIntegrityHandler.isWearingSealedSuit(mc.player)) return;
        var v = visual;
        int tone = switch (v.alarm()) {
            case NONE -> OrsaHudPanel.VALUE_COLOR;
            case CAUTION, WARNING -> OrsaHudPanel.WARNING_COLOR;
            case CRITICAL -> OrsaHudPanel.CRITICAL_COLOR;
        };
        int accent = switch (v.alarm()) {
            case NONE -> 0xFF5FD7FF;
            case CAUTION -> 0xFFFFC040;
            case WARNING -> 0xFFFF8A30;
            case CRITICAL -> (mc.level.getGameTime() / 6) % 2 == 0 ? 0xFFFF3A2A : 0xFF801810;
        };
        var title = Component.translatable("hud.frozendawn.icarus.suit_title");
        var state = Component.translatable("icarus.frozendawn.state." + v.state().name().toLowerCase(Locale.ROOT));
        var line = Component.translatable("hud.frozendawn.icarus.suit_line",
                String.format(Locale.ROOT, "%.0f", v.shieldPct()), String.format(Locale.ROOT, "%,d", v.coreC()));
        int titleW = mc.font.width(title);
        int width = OrsaHudPanel.TEXT_INSET + Math.max(titleW + 4 + mc.font.width(state), mc.font.width(line)) + 8;
        int x = g.guiWidth() - MARGIN - width, y = MARGIN;
        int textX = OrsaHudPanel.draw(g, x, y, width, HEIGHT, accent, accent, accent);
        g.drawString(mc.font, title, textX, y + PAD_Y + 1, OrsaHudPanel.LABEL_COLOR, false);
        g.drawString(mc.font, state, textX + titleW + 4, y + PAD_Y + 1, tone, false);
        g.drawString(mc.font, line, textX, y + PAD_Y + 10, v.coreC() >= IcarusPhysics.RED_LINE_C ? OrsaHudPanel.CRITICAL_COLOR : OrsaHudPanel.VALUE_COLOR, false);
    }
}
