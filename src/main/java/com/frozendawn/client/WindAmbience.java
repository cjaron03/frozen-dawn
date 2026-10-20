package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.init.ModSounds;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.phase.PhaseManager;
import com.frozendawn.phase.PhaseOneWeatherPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays long (~65-70s) ambient wind clips with overlapping crossfade.
 * Uses TickableWindSound for smooth per-frame volume transitions (no hard cuts).
 * Next clip starts 5s before the current one ends for seamless overlap.
 *
 * Phase 6 early: maximum volume (1.0). Mid: wind dies down. Late: silence,
 * except inside a living Master Architect's local Hearth storm.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class WindAmbience {

    private static final int LIGHT_DURATION = 1140; // 57s in ticks
    private static final int STRONG_DURATION = 1260; // 63s in ticks
    private static final int OVERLAP = 100;          // 5s overlap (matches file fade-out)

    private static TickableWindSound currentSound = null;
    private static TickableWindSound previousSound = null;
    private static int ticksUntilNext = 0;
    private static int creakCooldown = 0;
    private static float currentBasePitch = 1.0f;
    private static float previousBasePitch = 1.0f;
    private static final PhaseOneWindExposure PHASE_ONE_EXPOSURE = new PhaseOneWindExposure();
    private static boolean currentPostMaeve;
    private static boolean currentPhaseOne;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (mc.level.dimension() != Level.OVERWORLD) return;

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();
        boolean underground = mc.player.blockPosition().getY() < 50;
        float masterStrength = MasterArchitectWeather.getStrength();
        boolean postMaeve = PostMaeveClientState.isMaeveErased();

        boolean phaseOneWeather = phase == 1 && !postMaeve && masterStrength <= 0.01F;
        if (currentSound != null && (currentPostMaeve != postMaeve || currentPhaseOne != phaseOneWeather)) {
            stopAll(mc);
        }
        boolean phaseOneRain = phaseOneWeather && mc.level.getRainLevel(1) > 0.01F;
        boolean shouldStop = shouldStopWind(
                phase, progress, underground, masterStrength, postMaeve, phaseOneRain);
        if (shouldStop) {
            stopAll(mc);
            return;
        }

        float targetVolume = postMaeve
                ? FrozenDawnConfig.POST_MAEVE_AMBIENT_VOLUME_MULTIPLIER.get().floatValue()
                : Math.max(phaseOneWeather ? PhaseOneWeatherPolicy.windVolume(progress, mc.level.getRainLevel(1), mc.level.getGameTime())
                        : getTargetWindVolume(phase, progress),
                        0.9F * masterStrength);

        float exposure = phaseOneWeather ? PHASE_ONE_EXPOSURE.tick(ClientStormVisibility.isStormExposed(mc))
                : StormExposureController.getExposure();
        boolean exposedToStorm = ClientStormVisibility.isStormExposed(mc);
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
        if (phaseOneWeather && previousSound != null && !previousSound.isStopped()) {
            previousSound.setTargetVolume(targetVolume, MasterArchitectSeverTelegraph.windFadeRate());
            previousSound.setTargetPitch(previousBasePitch * Mth.lerp(exposure, 0.82f, 1.0f));
        }

        // If target is near-zero and no sound playing, bail
        if (targetVolume < 0.01f && (currentSound == null || currentSound.isStopped())) {
            stopAll(mc);
            return;
        }

        // Occasional creaking when sheltered in phase 4+ (structure stress from wind/snow)
        if (!exposedToStorm && phase >= 4 && heartQuiet > 0.15F) {
            if (creakCooldown > 0) {
                creakCooldown--;
            } else if (mc.level.random.nextFloat() < 0.015f) {
                float pitch = 0.7f + mc.level.random.nextFloat() * 0.4f;
                float vol = 0.3f + mc.level.random.nextFloat() * 0.2f;
                mc.level.playLocalSound(
                        mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                        ModSounds.SHELTER_CREAK.get(), SoundSource.AMBIENT,
                        vol, pitch, false);
                creakCooldown = 80 + mc.level.random.nextInt(160); // 4-12s between creaks
            }
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            if (ticksUntilNext > 0) return;
        }

        // Start next clip — old one still playing for 5s overlap
        boolean strong = phase >= 4 || masterStrength > 0.05F;
        previousBasePitch = currentBasePitch;
        currentBasePitch = 0.97f + mc.level.random.nextFloat() * 0.06f;
        targetPitch = currentBasePitch * Mth.lerp(exposure, 0.82f, 1.0f);
        int clipDuration = strong ? STRONG_DURATION : LIGHT_DURATION;

        previousSound = currentSound;
        currentPostMaeve = postMaeve;
        currentPhaseOne = phaseOneWeather;
        currentSound = new TickableWindSound(
                postMaeve
                        ? strong ? ModSounds.WIND_POST_MAEVE_STRONG.get()
                                : ModSounds.WIND_POST_MAEVE_LIGHT.get()
                        : phaseOneWeather ? ModSounds.WIND_PHASE_ONE.get()
                                : strong ? ModSounds.WIND_STRONG.get()
                                        : ModSounds.WIND_LIGHT.get(),
                targetVolume, targetPitch, clipDuration);
        mc.getSoundManager().play(currentSound);

        ticksUntilNext = clipDuration - OVERLAP;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        stopAll(mc);
        ApocalypseClientData.reset();
        TemperatureHud.reset();
        AirStatusHud.reset();
        SanityClientData.reset();
        BloomClientState.reset();
    }

    private static void stopAll(Minecraft mc) {
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        if (previousSound != null) {
            mc.getSoundManager().stop(previousSound);
            previousSound = null;
        }
        ticksUntilNext = 0;
        creakCooldown = 0;
        currentBasePitch = 1.0f;
        previousBasePitch = 1.0f;
        PHASE_ONE_EXPOSURE.reset();
        currentPostMaeve = false;
        currentPhaseOne = false;
    }

    private static boolean shouldStopWind(
            int phase, float progress, boolean underground, float masterStrength,
            boolean postMaeve, boolean phaseOneRain) {
        return underground || (!postMaeve && masterStrength <= 0.01F
                && ((!phaseOneRain && phase < 3) || PhaseManager.isVacuumActive(phase, progress)));
    }

    private static float getTargetWindVolume(int phase, float progress) {
        if (phase < 6) {
            return switch (phase) {
                case 3 -> 0.2f;
                case 4 -> 0.45f;
                default -> 0.85f;
            };
        }

        return switch (PhaseManager.getPhase6Stage(phase, progress)) {
            case EARLY -> 1.0f;
            case MID -> Mth.lerp(PhaseManager.getPhase6MidFadeProgress(progress), 1.0f, 0.0f);
            case VACUUM, INACTIVE -> 0.0f;
        };
    }
}
