package com.frozendawn.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders a heat/sweat overlay when the player is overheating.
 * Orange-red vignette with "drip" effect. Intensity scales with temperature.
 * Kicks in above 50°C.
 */
public class HeatOverlay {

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        float temp = TemperatureHud.getDisplayedTemp();
        if (temp <= 50f) return;

        // Intensity ramps from 0 at 50C to full at 120C
        float intensity = Math.min(1f, (temp - 50f) / 70f);

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // Full-screen orange-red tint
        int tintAlpha = (int) (intensity * 50);
        if (tintAlpha > 0) {
            graphics.fill(0, 0, width, height, (tintAlpha << 24) | 0xFF4400);
        }

        // Edge vignette — warm orange from edges
        int edgeAlpha = (int) (intensity * 110);
        if (edgeAlpha > 0) {
            int edgeColor = (edgeAlpha << 24) | 0xDD4400;
            int transparent = 0x00DD4400;
            int borderSize = (int) (height * 0.14f);

            graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
            graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
            // Side vignettes
            int sideBorder = (int) (width * 0.08f);
            graphics.fillGradient(0, 0, sideBorder, height, edgeColor, transparent);
            graphics.fillGradient(width - sideBorder, 0, width, height, transparent, edgeColor);
        }

        // Sweat drip effect — animated vertical streaks at high heat
        if (intensity > 0.3f) {
            long time = mc.level != null ? mc.level.getGameTime() : 0;
            float dripIntensity = (intensity - 0.3f) / 0.7f;
            int dripAlpha = (int) (dripIntensity * 60);
            int dripColor = (dripAlpha << 24) | 0xCCFFFF; // water-colored drips

            // Several drip streaks across the screen
            for (int i = 0; i < 6; i++) {
                // Each drip has its own phase offset and position
                float phase = (time + i * 37) % 80 / 80f;
                int dripX = (int) (width * (0.1f + i * 0.15f + Math.sin(i * 2.7) * 0.05f));
                int dripTop = (int) (phase * height * 0.6f);
                int dripLen = (int) (height * 0.08f * dripIntensity);

                if (phase < 0.9f) { // brief gap between drips
                    graphics.fillGradient(dripX, dripTop, dripX + 1, dripTop + dripLen,
                            0x00CCFFFF, dripColor);
                }
            }
        }
    }
}
