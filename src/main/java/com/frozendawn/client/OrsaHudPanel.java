package com.frozendawn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;

/** The existing EVA frame and badge, shared by air telemetry and Continuity navigation. */
final class OrsaHudPanel {
    static final int BACKGROUND = 0xAA0B1217;
    static final int LABEL_COLOR = 0xFF8A9AA4;
    static final int MUTED_COLOR = 0xFF6F7F89;
    static final int VALUE_COLOR = 0xFFCDEFFF;
    static final int WARNING_COLOR = 0xFFFFE0A8;
    static final int CRITICAL_COLOR = 0xFFFFB1B1;
    static final int TEXT_INSET = 20;
    private OrsaHudPanel() {}

    static int draw(GuiGraphics graphics, int x, int y, int width, int height,
            int accent, int border, int badge) {
        graphics.fill(x + 1, y, x + width - 1, y + height, BACKGROUND);
        graphics.fill(x, y + 1, x + width, y + height - 1, BACKGROUND);
        graphics.fill(x, y + 1, x + 2, y + height - 1, accent);
        graphics.fill(x + 1, y, x + width - 1, y + 1, alpha(border, 210));
        graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, alpha(border, 160));
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, alpha(border, 185));
        int badgeX = x + 8;
        OrsaLogoRenderer.drawTinted(graphics, badgeX, y + (height - 8) / 2, 8,
                FastColor.ARGB32.red(badge) / 255.0F,
                FastColor.ARGB32.green(badge) / 255.0F,
                FastColor.ARGB32.blue(badge) / 255.0F,
                FastColor.ARGB32.alpha(badge) / 255.0F);
        return x + TEXT_INSET;
    }

    private static int alpha(int color, int alpha) {
        return color & 0x00FFFFFF | alpha << 24;
    }
}
