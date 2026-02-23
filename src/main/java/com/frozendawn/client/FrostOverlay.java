package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders a frost vignette overlay on the HUD.
 * Intensity increases from phase 2 through phase 5.
 * Registered as a GUI layer via ClientEvents.
 */
public class FrostOverlay {

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!FrozenDawnConfig.ENABLE_FROST_OVERLAY.get()) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 2) return;

        float progress = ApocalypseClientData.getProgress();
        // Intensity ramps from 0 at phase 2 start (0.15) to 1 at phase 5 end (1.0)
        float intensity = Math.min(1f, (progress - 0.15f) / 0.85f);
        if (intensity <= 0f) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // Subtle full-screen blue-white frost tint
        int tintAlpha = (int) (intensity * 40);
        if (tintAlpha > 0) {
            graphics.fill(0, 0, width, height, (tintAlpha << 24) | 0xCCDDFF);
        }

        // Stronger frost gradients at top and bottom edges
        int edgeAlpha = (int) (intensity * 100);
        if (edgeAlpha > 0) {
            int edgeColor = (edgeAlpha << 24) | 0xAABBEE;
            int transparent = 0x00AABBEE;
            int borderSize = (int) (height * 0.12f);

            // Top: opaque frost fading down
            graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
            // Bottom: transparent fading to opaque frost
            graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        }
    }
}
