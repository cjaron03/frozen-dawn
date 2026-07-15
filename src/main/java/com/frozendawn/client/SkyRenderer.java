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
    private static final float PHASE5_MAX_SKY_BRIGHTNESS = 0.06f;
    private static final float MASTER_ARCHITECT_WHITEOUT_MIX = 0.4F;
    private static final float MASTER_ARCHITECT_VISIBILITY = 12.0F;

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!FrozenDawnConfig.ENABLE_SKY_DARKENING.get()) return;

        int phase = ApocalypseClientData.getPhase();
        if (phase < 1) return;

        Minecraft mc = Minecraft.getInstance();
        float exposure = phase >= 3 ? StormExposureController.getExposure() : (ClientStormVisibility.isUndergroundOrCovered(mc) ? 0.0F : 1.0F);
        float skyColorExposure = phase >= 5 ? 1.0F : exposure;
        if (skyColorExposure <= 0.0F) return;

        float skyLight = ApocalypseClientData.getSkyLight();
        float sunBrightness = ApocalypseClientData.getSunBrightness();
        float progress = ApocalypseClientData.getProgress();

        if (FrozenDawnConfig.ENABLE_SKY_COLOR_SHIFT.get() && phase >= 1) {
            int idx = Math.min(phase - 1, 5);
            float blend = PHASE_BLEND[idx] * skyColorExposure;
            float floor = PHASE_FLOOR[idx];

            float brightness = Math.max(floor, skyLight * (0.3f + 0.7f * sunBrightness));
            if (phase == 5) {
                brightness = Math.min(brightness, PHASE5_MAX_SKY_BRIGHTNESS);
            }

            float targetR = PHASE_COLORS[idx][0] * brightness;
            float targetG = PHASE_COLORS[idx][1] * brightness;
            float targetB = PHASE_COLORS[idx][2] * brightness;

            float whiteoutMix = getWhiteoutMix(phase, progress) * skyColorExposure;
            if (whiteoutMix > 0.0f) {
                targetR = Mth.lerp(whiteoutMix, targetR, getStormHazeRed(phase));
                targetG = Mth.lerp(whiteoutMix, targetG, getStormHazeGreen(phase));
                targetB = Mth.lerp(whiteoutMix, targetB, getStormHazeBlue(phase));
            }

            // Phase 6 mid+: transition from whiteout to pure black
            if (PhaseManager.isPhase6MidOrLater(phase, progress)) {
                float blackTransition = PhaseManager.getPhase6MidFadeProgress(progress);
                targetR = Mth.lerp(blackTransition, targetR, 0.0f);
                targetG = Mth.lerp(blackTransition, targetG, 0.0f);
                targetB = Mth.lerp(blackTransition, targetB, 0.005f);
            }

            float masterWhiteout = MasterArchitectWeather.getStrength()
                    * exposure * MASTER_ARCHITECT_WHITEOUT_MIX;
            if (masterWhiteout > 0.0F) {
                targetR = Mth.lerp(masterWhiteout, targetR, getStormHazeRed(6));
                targetG = Mth.lerp(masterWhiteout, targetG, getStormHazeGreen(6));
                targetB = Mth.lerp(masterWhiteout, targetB, getStormHazeBlue(6));
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

        float exposure = StormExposureController.getExposure();
        if (exposure <= 0.0F) return;

        float progress = ApocalypseClientData.getProgress();

        float targetVisibility = getTargetVisibility(phase, progress);
        if (SurveyorLensVision.getActiveVisionMode() == VisionMode.BLIZZARD
                && BlizzardGogglesLogic.isVisionActive(phase, progress)) {
            targetVisibility = BlizzardGogglesHandler.BLIZZARD_FOG_DISTANCE_BLOCKS;
        }

        float currentFar = event.getFarPlaneDistance();
        float visibility = Mth.lerp(exposure, currentFar, targetVisibility);
        float masterStrength = MasterArchitectWeather.getStrength() * exposure;
        if (masterStrength > 0.0F) {
            visibility = Math.min(visibility, Mth.lerp(
                    masterStrength, currentFar, MASTER_ARCHITECT_VISIBILITY));
        }
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

    private static float getStormHazeRed(int phase) {
        return phase == 5 ? 0.055f : 0.15f;
    }

    private static float getStormHazeGreen(int phase) {
        return phase == 5 ? 0.060f : 0.15f;
    }

    private static float getStormHazeBlue(int phase) {
        return phase == 5 ? 0.080f : 0.18f;
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

}
