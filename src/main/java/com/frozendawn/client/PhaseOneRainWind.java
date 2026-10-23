package com.frozendawn.client;

import com.frozendawn.init.ModSounds;
import com.frozendawn.phase.PhaseOneWeatherPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Preserves the accepted phase-1 rainy playback independently of later cyclone beds. */
final class PhaseOneRainWind {
    private static final int LIGHT_DURATION = 1140;
    private static final int OVERLAP = 100;
    private static TickableWindSound currentSound = null;
    private static TickableWindSound previousSound = null;
    private static int ticksUntilNext = 0;
    private static float currentBasePitch = 1.0f;
    private static float previousBasePitch = 1.0f;
    private static final PhaseOneWindExposure PHASE_ONE_EXPOSURE = new PhaseOneWindExposure();

    private PhaseOneRainWind() { }

    static void tick(Minecraft mc, float progress, float masterStrength) {
        if (mc.player.blockPosition().getY() < 50 || mc.level.getRainLevel(1) <= .01F) {
            stop(mc);
            return;
        }
        float targetVolume = Math.max(PhaseOneWeatherPolicy.windVolume(progress, mc.level.getRainLevel(1), mc.level.getGameTime()),
                        0.9F * masterStrength);

        float exposure = PHASE_ONE_EXPOSURE.tick(ClientStormVisibility.isStormExposed(mc));
        targetVolume *= Mth.lerp(exposure, 0.08f, 1.0f);
        targetVolume *= MasterArchitectFourthWallMoment.weatherAudioMultiplier();
        targetVolume *= MasterArchitectSeverTelegraph.weatherAudioMultiplier();
        float heartQuiet = HeartQuietClient.environmentMultiplier();
        targetVolume *= heartQuiet;
        targetVolume *= BloomClientState.windMultiplier();
        float targetPitch = currentBasePitch * Mth.lerp(exposure, 0.82f, 1.0f);

        // Update volume on the currently playing sound — it fades smoothly per-frame
        if (currentSound != null && !currentSound.isStopped()) {
            currentSound.setTargetVolume(
                    targetVolume, MasterArchitectSeverTelegraph.windFadeRate());
            currentSound.setTargetPitch(targetPitch);
        }

        // The outgoing phase-1 clip must follow cover too during its five-second overlap.
        if (previousSound != null && !previousSound.isStopped()) {
            previousSound.setTargetVolume(targetVolume, MasterArchitectSeverTelegraph.windFadeRate());
            previousSound.setTargetPitch(previousBasePitch * Mth.lerp(exposure, 0.82f, 1.0f));
        }

        // If target is near-zero and no sound playing, bail
        if (targetVolume < 0.01f && (currentSound == null || currentSound.isStopped())) {
            stop(mc);
            return;
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            if (ticksUntilNext > 0) return;
        }

        // Start next clip — old one still playing for 5s overlap
        previousBasePitch = currentBasePitch;
        currentBasePitch = 0.97f + mc.level.random.nextFloat() * 0.06f;
        targetPitch = currentBasePitch * Mth.lerp(exposure, 0.82f, 1.0f);
        int clipDuration = LIGHT_DURATION;

        previousSound = currentSound;
        currentSound = new TickableWindSound(
                ModSounds.WIND_PHASE_ONE.get(),
                targetVolume, targetPitch, clipDuration);
        mc.getSoundManager().play(currentSound);

        ticksUntilNext = clipDuration - OVERLAP;
    }

    static void stop(Minecraft mc) {
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        if (previousSound != null) {
            mc.getSoundManager().stop(previousSound);
            previousSound = null;
        }
        ticksUntilNext = 0;
        currentBasePitch = 1.0f;
        previousBasePitch = 1.0f;
        PHASE_ONE_EXPOSURE.reset();
    }

}
