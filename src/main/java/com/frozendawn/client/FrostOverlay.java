package com.frozendawn.client;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.event.MobFreezeHandler;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Renders a frost vignette overlay on the HUD.
 * Only appears when temperature is below 0C.
 * Intensity scales with how cold it is.
 * Phase 6: full frost with darker grey-black tint (atmosphere frozen).
 */
public class FrostOverlay {

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!FrozenDawnConfig.ENABLE_FROST_OVERLAY.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        float temp = TemperatureHud.getDisplayedTemp();
        // Factor in armor cold resistance — no frost overlay when protected
        float effectiveTemp = temp + MobFreezeHandler.getArmorColdResistance(mc.player);
        if (effectiveTemp >= 0f) return;

        // Intensity ramps from 0 at 0C to full at -60C (based on effective temp)
        float intensity = Math.min(1f, -effectiveTemp / 60f);

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        // Phase 6: transition from blue-white frost to grey-black (frozen atmosphere)
        if (phase >= 6) {
            // Full intensity in phase 6
            intensity = 1f;

            // Transition from blue-white to grey-black as atmosphere collapses
            float darkTransition = Math.min(1f, (progress - 0.60f) / 0.25f);

            int tintAlpha = (int) Mth.lerp(darkTransition, 40f, 60f);
            int tintR = (int) Mth.lerp(darkTransition, 0xCC, 0x22);
            int tintG = (int) Mth.lerp(darkTransition, 0xDD, 0x22);
            int tintB = (int) Mth.lerp(darkTransition, 0xFF, 0x33);
            graphics.fill(0, 0, width, height, (tintAlpha << 24) | (tintR << 16) | (tintG << 8) | tintB);

            int edgeAlpha = (int) Mth.lerp(darkTransition, 100f, 140f);
            int edgeR = (int) Mth.lerp(darkTransition, 0xAA, 0x11);
            int edgeG = (int) Mth.lerp(darkTransition, 0xBB, 0x11);
            int edgeB = (int) Mth.lerp(darkTransition, 0xEE, 0x22);
            int edgeColor = (edgeAlpha << 24) | (edgeR << 16) | (edgeG << 8) | edgeB;
            int transparent = (0x00 << 24) | (edgeR << 16) | (edgeG << 8) | edgeB;
            int borderSize = (int) (height * 0.15f);

            graphics.fillGradient(0, 0, width, borderSize, edgeColor, transparent);
            graphics.fillGradient(0, height - borderSize, width, height, transparent, edgeColor);
        } else {
            // Normal frost overlay (phases 1-5)
            int tintAlpha = (int) (intensity * 40);
            if (tintAlpha > 0) {
                graphics.fill(0, 0, width, height, (tintAlpha << 24) | 0xCCDDFF);
            }

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
}
