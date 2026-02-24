package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Modifies fog color and distance based on apocalypse progression.
 *
 * Sky color shift: phase-dependent color targets with brightness floors.
 * Fog: closes in during phases 3+ (visibility 256 -> 48 blocks).
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class SkyRenderer {

    // Phase color targets (R, G, B) for sky hue shift
    private static final float[][] PHASE_COLORS = {
            {0.85f, 0.55f, 0.35f},   // Phase 1: Warm amber
            {0.60f, 0.62f, 0.65f},   // Phase 2: Pale desaturated
            {0.25f, 0.35f, 0.55f},   // Phase 3: Cold blue
            {0.15f, 0.12f, 0.35f},   // Phase 4: Deep blue-purple
            {0.05f, 0.03f, 0.10f}    // Phase 5: Near-black purple
    };

    // Blend strength per phase (how much the phase color overrides vanilla)
    private static final float[] PHASE_BLEND = {0.2f, 0.4f, 0.7f, 0.9f, 1.0f};

    // Brightness floor per phase (prevents night = phase 5)
    private static final float[] PHASE_FLOOR = {0.15f, 0.15f, 0.10f, 0.08f, 0.04f};

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!FrozenDawnConfig.ENABLE_SKY_DARKENING.get()) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 1) return;

        float skyLight = ApocalypseClientData.getSkyLight();
        float sunBrightness = ApocalypseClientData.getSunBrightness();

        // Apply sky color shift if enabled
        if (FrozenDawnConfig.ENABLE_SKY_COLOR_SHIFT.get() && phase >= 1) {
            int idx = Math.min(phase - 1, 4);
            float blend = PHASE_BLEND[idx];
            float floor = PHASE_FLOOR[idx];

            // Brightness calculation with floor
            float brightness = Math.max(floor, skyLight * (0.3f + 0.7f * sunBrightness));

            float targetR = PHASE_COLORS[idx][0] * brightness;
            float targetG = PHASE_COLORS[idx][1] * brightness;
            float targetB = PHASE_COLORS[idx][2] * brightness;

            event.setRed(Mth.lerp(blend, event.getRed() * skyLight, targetR));
            event.setGreen(Mth.lerp(blend, event.getGreen() * skyLight, targetG));
            event.setBlue(Mth.lerp(blend, event.getBlue() * skyLight, targetB));
        } else {
            // Fallback: simple sky darkening
            if (skyLight < 1f) {
                event.setRed(event.getRed() * skyLight);
                event.setGreen(event.getGreen() * skyLight);
                event.setBlue(event.getBlue() * skyLight);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        int phase = ApocalypseClientData.getPhase();
        if (phase < 3) return;

        float progress = ApocalypseClientData.getProgress();
        // Phase 3 starts at progress 0.35; fog closes from 256 -> 48 blocks by phase 5 end
        float fogProgress = Math.min(1f, (progress - 0.35f) / 0.65f);
        float visibility = Mth.lerp(fogProgress, 256f, 48f);

        float currentFar = event.getFarPlaneDistance();
        if (visibility < currentFar) {
            event.setFarPlaneDistance(visibility);
            event.setNearPlaneDistance(visibility * 0.05f);
            event.setCanceled(true);
        }
    }
}
