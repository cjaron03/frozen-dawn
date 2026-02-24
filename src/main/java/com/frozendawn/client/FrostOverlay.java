package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders a frost vignette overlay on the HUD.
 * Only appears when temperature is below 0C.
 * Intensity scales with how cold it is.
 */
public class FrostOverlay {

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!FrozenDawnConfig.ENABLE_FROST_OVERLAY.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && (mc.player.isCreative() || mc.player.isSpectator())) return;

        float temp = TemperatureHud.getDisplayedTemp();
        if (temp >= 0f) return;

        // Intensity ramps from 0 at 0C to full at -60C
        float intensity = Math.min(1f, -temp / 60f);

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

            graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
            graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        }
    }
}
