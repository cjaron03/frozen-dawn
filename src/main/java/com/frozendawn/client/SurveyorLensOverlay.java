package com.frozendawn.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class SurveyorLensOverlay {

    private SurveyorLensOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!SurveyorLensVision.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        float strength = SurveyorLensVision.getOverlayStrength();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        int tintAlpha = (int) (strength * 42.0F);
        graphics.fill(0, 0, width, height, (tintAlpha << 24) | 0x243444);

        int edgeAlpha = (int) (strength * 92.0F);
        int edgeColor = (edgeAlpha << 24) | 0x0F1B24;
        int transparent = 0x000F1B24;
        int borderSize = (int) (height * 0.12F);
        int sideBorder = (int) (width * 0.08F);

        graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
        graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        graphics.fillGradient(0, 0, sideBorder, height, edgeColor, transparent);
        graphics.fillGradient(width - sideBorder, 0, width, height, transparent, edgeColor);

    }
}
