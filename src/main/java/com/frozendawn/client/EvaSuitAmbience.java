package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import com.frozendawn.event.EmergencyEvaHandler;
import com.frozendawn.init.ModSounds;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays EVA suit breathing ambience in phase 6 late (vacuum) only while the
 * suit has usable O2. Also plays a suffocation gasp when the player enters or
 * falls into unprotected vacuum.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class EvaSuitAmbience {

    private static final int CLIP_DURATION = 300;  // 15s in ticks (matches the ogg length)
    private static final int OVERLAP = 40;          // 2s overlap for seamless loop
    private static final float TARGET_VOLUME = 0.5f;

    private static TickableWindSound currentSound = null;
    private static TickableWindSound previousSound = null;
    private static TickableBreathingSound emergencySound = null;
    private static TickableBreathingSound emergencyFan = null;
    private static SimpleSoundInstance suffocateSound = null;
    private static int ticksUntilNext = 0;
    private static boolean wasSuffocating = false;
    private static float currentBasePitch = 1.0F;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || mc.isPaused()) {
            stopAll(mc);
            resetSuffocationState(mc);
            return;
        }
        if (mc.level.dimension() != Level.OVERWORLD) {
            stopAll(mc);
            resetSuffocationState(mc);
            return;
        }
        if (mc.player.isCreative() || mc.player.isSpectator()) {
            stopAll(mc);
            resetSuffocationState(mc);
            return;
        }

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();

        boolean inVacuum = PhaseManager.isVacuumActive(phase, progress);
        AirStatusTelemetry.State airState = AirStatusTelemetry.resolve(mc.player);
        boolean vacuumExposure = inVacuum && !ApocalypseClientData.isBreathable();
        boolean canUseO2 = vacuumExposure && airState == AirStatusTelemetry.State.EVA_SUPPLY;
        boolean suffocating = vacuumExposure && !canUseO2;

        if (suffocating) {
            if (!wasSuffocating) {
                suffocateSound = SimpleSoundInstance.forUI(
                        ModSounds.EVA_SUFFOCATE.get(), 1.0f, 0.8f);
                mc.getSoundManager().play(suffocateSound);
            }
            wasSuffocating = true;
        } else {
            resetSuffocationState(mc);
        }

        if (!canUseO2) {
            stopAll(mc);
            return;
        }

        if (EmergencyEvaHandler.hasLifeSupport(mc.player)) {
            stopNormal(mc);
            if (emergencySound == null || emergencySound.isStopped()) {
                emergencySound = new TickableBreathingSound(ModSounds.EVA_EMERGENCY_BREATHING.get(),
                        TARGET_VOLUME * HearthrotClientState.breathingVolumeMultiplier());
                mc.getSoundManager().play(emergencySound);
            }
            float breathingMultiplier = HearthrotClientState.breathingVolumeMultiplier();
            emergencySound.setTargetVolume(TARGET_VOLUME * breathingMultiplier,
                    breathingMultiplier < 1.0F ? 0.10F : 0.035F);
            emergencySound.setTargetPitch(MasterArchitectSeverTelegraph.evaPitchMultiplier());
            if (emergencyFan == null || emergencyFan.isStopped()) {
                emergencyFan = new TickableBreathingSound(ModSounds.EVA_EMERGENCY_FAN.get(), 0.035F);
                mc.getSoundManager().play(emergencyFan);
            }
            float remaining = EmergencyEvaHandler.remainingTicks(mc.player)
                    / (float) com.frozendawn.data.EmergencyEvaState.SERVICE_TICKS;
            emergencyFan.setTargetVolume((0.035F + (1.0F - remaining) * 0.005F) * breathingMultiplier,
                    breathingMultiplier < 1.0F ? 0.10F : 0.025F);
            emergencyFan.setTargetPitch(0.96F + remaining * 0.04F);
            return;
        }
        stopEmergency(mc);

        // Update volume on current sound
        if (currentSound != null && !currentSound.isStopped()) {
            float breathingMultiplier = HearthrotClientState.breathingVolumeMultiplier();
            currentSound.setTargetVolume(
                    TARGET_VOLUME * breathingMultiplier,
                    breathingMultiplier < 1.0F ? 0.10F : 0.035F);
            currentSound.setTargetPitch(
                    currentBasePitch * MasterArchitectSeverTelegraph.evaPitchMultiplier());
        }

        if (ticksUntilNext > 0) {
            ticksUntilNext--;
            if (ticksUntilNext > 0) return;
        }

        // Start next clip — old one may still be playing for overlap
        if (previousSound != null) mc.getSoundManager().stop(previousSound);
        previousSound = currentSound;
        currentBasePitch = 0.98f + mc.level.random.nextFloat() * 0.04f;
        currentSound = new TickableWindSound(
                ModSounds.EVA_BREATHING.get(),
                TARGET_VOLUME,
                currentBasePitch * MasterArchitectSeverTelegraph.evaPitchMultiplier(),
                CLIP_DURATION);
        mc.getSoundManager().play(currentSound);

        ticksUntilNext = CLIP_DURATION - OVERLAP;
    }

    private static void stopAll(Minecraft mc) {
        stopNormal(mc);
        stopEmergency(mc);
    }

    private static void stopNormal(Minecraft mc) {
        if (currentSound != null) {
            mc.getSoundManager().stop(currentSound);
            currentSound = null;
        }
        if (previousSound != null) {
            mc.getSoundManager().stop(previousSound);
            previousSound = null;
        }
        ticksUntilNext = 0;
        currentBasePitch = 1.0F;
    }

    private static void stopEmergency(Minecraft mc) {
        if (emergencyFan != null) {
            mc.getSoundManager().stop(emergencyFan);
            emergencyFan = null;
        }
        if (emergencySound != null) {
            mc.getSoundManager().stop(emergencySound);
            emergencySound = null;
        }
    }

    private static void resetSuffocationState(Minecraft mc) {
        wasSuffocating = false;
        if (suffocateSound != null) {
            mc.getSoundManager().stop(suffocateSound);
            suffocateSound = null;
        }
    }
}
