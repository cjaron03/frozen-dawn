package com.frozendawn.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Compact temperature HUD in the top-left corner.
 * Shows a small color-coded thermometer bar with temperature text.
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

        String tempText = String.format("%.0f\u00B0C", displayedTemp);
        int textWidth = mc.font.width(tempText);

        // Layout: [4px bar] [2px gap] [text] — all inside a padded background
        int barWidth = 3;
        int gap = 3;
        int padding = 4;
        int innerWidth = barWidth + gap + textWidth;
        int innerHeight = 9; // font height
        int totalWidth = innerWidth + padding * 2;
        int totalHeight = innerHeight + padding * 2;

        // Position: top-left, offset from corner
        int x = 6;
        int y = 6;

        // Background — rounded look via layered fills
        int bgColor = 0xAA0E0E0E;
        // Main body
        graphics.fill(x + 1, y, x + totalWidth - 1, y + totalHeight, bgColor);
        // Top/bottom rounded edges
        graphics.fill(x, y + 1, x + totalWidth, y + totalHeight - 1, bgColor);

        // Thermometer bar — vertical color strip on the left
        int barX = x + padding;
        int barY = y + padding;
        int barColor = getTemperatureColor(displayedTemp);
        graphics.fill(barX, barY, barX + barWidth, barY + innerHeight, barColor);

        // Temperature text
        int textX = barX + barWidth + gap;
        int textY = barY + 1;
        int textColor = getTextColor(displayedTemp);
        graphics.drawString(mc.font, tempText, textX, textY, textColor, false);
    }

    /**
     * Returns bar color based on temperature.
     */
    private static int getTemperatureColor(float temp) {
        int r, g, b;
        if (temp > 0) {
            float t = Math.min(temp / 35f, 1f);
            r = 255; g = (int) (180 * (1 - t)); b = 50;
        } else if (temp > -25) {
            float t = Math.min(-temp / 25f, 1f);
            r = (int) (255 * (1 - t * 0.5f)); g = (int) (255 * (1 - t * 0.1f)); b = 255;
        } else if (temp > -70) {
            float t = Math.min((-temp - 25f) / 45f, 1f);
            r = (int) (130 * (1 - t)); g = (int) (230 * (1 - t * 0.6f)); b = 255;
        } else {
            float t = Math.min((-temp - 70f) / 50f, 1f);
            r = (int) (100 * t); g = (int) (90 * (1 - t)); b = (int) (255 * (1 - t * 0.2f));
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Returns text color — white-ish but tinted toward the temperature color.
     */
    private static int getTextColor(float temp) {
        if (temp > 10) return 0xFFFFCC99;  // warm cream
        if (temp > 0) return 0xFFE8E8E8;   // neutral
        if (temp > -25) return 0xFFD0DDFF;  // cool blue-white
        if (temp > -70) return 0xFF99BBFF;  // cold blue
        return 0xFF8888DD;                   // deadly purple
    }
}
