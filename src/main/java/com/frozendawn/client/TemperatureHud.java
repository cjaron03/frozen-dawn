package com.frozendawn.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * TaN-style temperature HUD overlay.
 * Displays current temperature as a colored bar + numeric value
 * above the hotbar on the left side.
 */
public class TemperatureHud {

    private static float displayedTemp = 0f;
    private static float targetTemp = 0f;

    /** Called from network handler when server sends temperature. */
    public static void setTemperature(float temp) {
        targetTemp = temp;
    }

    /** Reset all state for world transitions. */
    public static void reset() {
        displayedTemp = 0f;
        targetTemp = 0f;
    }

    /** Current smoothed display temperature, used by FrostOverlay and cold effects. */
    public static float getDisplayedTemp() {
        return displayedTemp;
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        int phase = ApocalypseClientData.getPhase();
        if (phase < 1) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.options.hideGui) return;

        // Smooth the display temperature
        float lerpSpeed = 0.1f;
        displayedTemp += (targetTemp - displayedTemp) * lerpSpeed;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // Position: above hotbar, left side
        int barWidth = 60;
        int barHeight = 5;
        int x = screenWidth / 2 - 91; // Align with left edge of hotbar
        int y = screenHeight - 48;     // Above hotbar

        // Background
        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0x80000000);

        // Temperature bar fill
        // Map temp range: +35 (hot) to -120 (cold), clamp to 0-1
        float normalized = Math.clamp((displayedTemp + 120f) / 155f, 0f, 1f);
        int fillWidth = (int) (barWidth * normalized);

        int barColor = getTemperatureColor(displayedTemp);
        if (fillWidth > 0) {
            graphics.fill(x, y, x + fillWidth, y + barHeight, barColor);
        }

        // Temperature text
        String tempText = String.format("%.0f\u00B0C", displayedTemp);
        int textColor = getTemperatureColor(displayedTemp) | 0xFF000000;
        graphics.drawString(mc.font, tempText, x, y - 10, textColor, true);
    }

    /**
     * Returns an ARGB color based on temperature.
     * Hot (>0): red/orange
     * Cool (0 to -25): yellow/white
     * Cold (-25 to -70): cyan/blue
     * Deadly (<-70): deep blue/purple
     */
    private static int getTemperatureColor(float temp) {
        int r, g, b;
        if (temp > 0) {
            // Warm: orange to red
            float t = Math.min(temp / 35f, 1f);
            r = 255;
            g = (int) (200 * (1 - t));
            b = 50;
        } else if (temp > -25) {
            // Cool: white to cyan
            float t = Math.min(-temp / 25f, 1f);
            r = (int) (255 * (1 - t * 0.6f));
            g = (int) (255 * (1 - t * 0.1f));
            b = 255;
        } else if (temp > -70) {
            // Cold: cyan to blue
            float t = Math.min((-temp - 25f) / 45f, 1f);
            r = (int) (100 * (1 - t));
            g = (int) (230 * (1 - t * 0.7f));
            b = 255;
        } else {
            // Deadly: deep blue-purple
            float t = Math.min((-temp - 70f) / 50f, 1f);
            r = (int) (80 * t);
            g = (int) (70 * (1 - t));
            b = (int) (255 * (1 - t * 0.3f));
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
