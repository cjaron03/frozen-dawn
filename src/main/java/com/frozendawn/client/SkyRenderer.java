package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.event.BlizzardGogglesHandler;
import com.frozendawn.event.BlizzardGogglesLogic;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.vision.VisionMode;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Modifies fog color and distance based on apocalypse progression.
 *
 * Sky color shift: phase-dependent color targets with brightness floors.
 * Fog: closes in during phases 3-5 (visibility 256 -> 12 blocks at phase 5).
 * Phase 6: sky goes pure black, fog lifts as atmosphere thins (visibility returns).
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class SkyRenderer {

    private static final float[][] PHASE_COLORS = {
            {0.85f, 0.55f, 0.35f},   // Phase 1: Warm amber
            {0.60f, 0.62f, 0.65f},   // Phase 2: Pale desaturated
            {0.25f, 0.35f, 0.55f},   // Phase 3: Cold blue
            {0.15f, 0.12f, 0.35f},   // Phase 4: Deep blue-purple
            {0.05f, 0.03f, 0.10f},   // Phase 5: Near-black purple
            {0.0f,  0.0f,  0.02f}    // Phase 6: Pure black
    };

    private static final float[] PHASE_BLEND = {0.2f, 0.4f, 0.7f, 0.9f, 1.0f, 1.0f};
    private static final float[] PHASE_FLOOR = {0.15f, 0.15f, 0.10f, 0.08f, 0.04f, 0.01f};

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!FrozenDawnConfig.ENABLE_SKY_DARKENING.get()) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 1) return;

        Minecraft mc = Minecraft.getInstance();
        boolean covered = ClientStormVisibility.isUndergroundOrCovered(mc);
        ClientStormVisibility.WindowView windowView = covered ? ClientStormVisibility.findWindowView(mc) : null;
        if (covered && windowView == null) return;

        float skyLight = ApocalypseClientData.getSkyLight();
        float sunBrightness = ApocalypseClientData.getSunBrightness();
        float progress = ApocalypseClientData.getProgress();

        if (FrozenDawnConfig.ENABLE_SKY_COLOR_SHIFT.get() && phase >= 1) {
            int idx = Math.min(phase - 1, 5);
            float blend = PHASE_BLEND[idx];
            float floor = PHASE_FLOOR[idx];

            float brightness = Math.max(floor, skyLight * (0.3f + 0.7f * sunBrightness));

            float targetR = PHASE_COLORS[idx][0] * brightness;
            float targetG = PHASE_COLORS[idx][1] * brightness;
            float targetB = PHASE_COLORS[idx][2] * brightness;

            float whiteoutMix = getWhiteoutMix(phase, progress);
            if (windowView != null) {
                whiteoutMix *= 0.9F;
            }
            if (whiteoutMix > 0.0f) {
                targetR = Mth.lerp(whiteoutMix, targetR, 0.15f);
                targetG = Mth.lerp(whiteoutMix, targetG, 0.15f);
                targetB = Mth.lerp(whiteoutMix, targetB, 0.18f);
            }

            // Phase 6 mid+: transition from whiteout to pure black
            if (PhaseManager.isPhase6MidOrLater(phase, progress)) {
                float blackTransition = PhaseManager.getPhase6MidFadeProgress(progress);
                targetR = Mth.lerp(blackTransition, targetR, 0.0f);
                targetG = Mth.lerp(blackTransition, targetG, 0.0f);
                targetB = Mth.lerp(blackTransition, targetB, 0.005f);
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

        // Don't reduce visibility underground or under a real roof.
        Minecraft mc = Minecraft.getInstance();
        boolean covered = ClientStormVisibility.isUndergroundOrCovered(mc);
        ClientStormVisibility.WindowView windowView = covered ? ClientStormVisibility.findWindowView(mc) : null;
        if (covered && windowView == null) return;

        float progress = ApocalypseClientData.getProgress();

        float visibility = getTargetVisibility(phase, progress);
        if (windowView != null) {
            visibility = getWindowTargetVisibility(visibility);
        }
        if (SurveyorLensVision.getActiveVisionMode() == VisionMode.BLIZZARD
                && BlizzardGogglesLogic.isVisionActive(phase, progress)) {
            visibility = BlizzardGogglesHandler.BLIZZARD_FOG_DISTANCE_BLOCKS;
        }

        float currentFar = event.getFarPlaneDistance();
        if (visibility < currentFar) {
            event.setFarPlaneDistance(visibility);
            event.setNearPlaneDistance(visibility * 0.05f);
            event.setCanceled(true);
        }
    }

    private static float getWhiteoutMix(int phase, float progress) {
        if (phase == 5) {
            return 0.4f;
        }
        if (!PhaseManager.isPhase6Active(phase)) {
            return 0.0f;
        }
        return 0.4f * BlizzardWindHelper.getSurfaceStormFade(phase, progress);
    }

    private static float getTargetVisibility(int phase, float progress) {
        if (phase >= 6) {
            return switch (PhaseManager.getPhase6Stage(phase, progress)) {
                case EARLY -> 12f;
                case MID -> Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 12f, 256f);
                case VACUUM, INACTIVE -> 256f;
            };
        }
        if (phase >= 5) {
            return 12f;
        }
        if (phase >= 4) {
            float phase4Progress = Math.min(1f, (progress - 0.34f) / 0.12f);
            return Mth.lerp(phase4Progress, 128f, 48f);
        }

        float phase3Progress = Math.min(1f, (progress - 0.22f) / 0.12f);
        return Mth.lerp(phase3Progress, 256f, 128f);
    }

    private static float getWindowTargetVisibility(float exposedVisibility) {
        if (exposedVisibility <= 16.0F) {
            return 18.0F;
        }
        if (exposedVisibility <= 64.0F) {
            return 40.0F;
        }
        return Math.min(160.0F, exposedVisibility * 0.75F);
    }
}
