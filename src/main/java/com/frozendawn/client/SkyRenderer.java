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
 * Fog: closes in during phases 3+ (visibility 256 -> 12 blocks at phase 5).
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class SkyRenderer {

    private static final float[][] PHASE_COLORS = {
            {0.85f, 0.55f, 0.35f},   // Phase 1: Warm amber
            {0.60f, 0.62f, 0.65f},   // Phase 2: Pale desaturated
            {0.25f, 0.35f, 0.55f},   // Phase 3: Cold blue
            {0.15f, 0.12f, 0.35f},   // Phase 4: Deep blue-purple
            {0.05f, 0.03f, 0.10f}    // Phase 5: Near-black purple
    };

    private static final float[] PHASE_BLEND = {0.2f, 0.4f, 0.7f, 0.9f, 1.0f};
    private static final float[] PHASE_FLOOR = {0.15f, 0.15f, 0.10f, 0.08f, 0.04f};

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!FrozenDawnConfig.ENABLE_SKY_DARKENING.get()) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 1) return;

        float skyLight = ApocalypseClientData.getSkyLight();
        float sunBrightness = ApocalypseClientData.getSunBrightness();

        if (FrozenDawnConfig.ENABLE_SKY_COLOR_SHIFT.get() && phase >= 1) {
            int idx = Math.min(phase - 1, 4);
            float blend = PHASE_BLEND[idx];
            float floor = PHASE_FLOOR[idx];

            float brightness = Math.max(floor, skyLight * (0.3f + 0.7f * sunBrightness));

            float targetR = PHASE_COLORS[idx][0] * brightness;
            float targetG = PHASE_COLORS[idx][1] * brightness;
            float targetB = PHASE_COLORS[idx][2] * brightness;

            // Phase 5: blend fog color toward white-grey for blizzard whiteout
            if (phase >= 5) {
                float whiteout = 0.4f;
                targetR = Mth.lerp(whiteout, targetR, 0.15f);
                targetG = Mth.lerp(whiteout, targetG, 0.15f);
                targetB = Mth.lerp(whiteout, targetB, 0.18f);
            }

            event.setRed(Mth.lerp(blend, event.getRed() * skyLight, targetR));
            event.setGreen(Mth.lerp(blend, event.getGreen() * skyLight, targetG));
            event.setBlue(Mth.lerp(blend, event.getBlue() * skyLight, targetB));
        } else {
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

        // Phase 3 starts at 0.35, phase 5 ends at 1.0
        // Fog ramps: 256 -> 80 (phase 3-4), then drops hard to 12 blocks at phase 5
        float fogProgress = Math.min(1f, (progress - 0.35f) / 0.65f);

        float visibility;
        if (phase >= 5) {
            // Phase 5: extreme blizzard, visibility drops to 12 blocks
            float phase5Progress = Math.min(1f, (progress - 0.75f) / 0.25f);
            visibility = Mth.lerp(phase5Progress, 48f, 12f);
        } else if (phase >= 4) {
            float phase4Progress = Math.min(1f, (progress - 0.55f) / 0.20f);
            visibility = Mth.lerp(phase4Progress, 128f, 48f);
        } else {
            float phase3Progress = Math.min(1f, (progress - 0.35f) / 0.20f);
            visibility = Mth.lerp(phase3Progress, 256f, 128f);
        }

        float currentFar = event.getFarPlaneDistance();
        if (visibility < currentFar) {
            event.setFarPlaneDistance(visibility);
            event.setNearPlaneDistance(visibility * 0.05f);
            event.setCanceled(true);
        }
    }
}
