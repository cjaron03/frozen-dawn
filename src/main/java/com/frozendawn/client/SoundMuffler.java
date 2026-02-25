package com.frozendawn.client;

import com.frozendawn.FrozenDawn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Muffles game sounds when temperature drops below -15C.
 * Simulates sound dampening from heavy snow and frozen air.
 * Volume decreases and pitch lowers as temperature drops.
 * Skips music, UI sounds, and our own wind ambience.
 *
 * Phase 6 late (progress >= 0.85): vacuum — ALL sounds cancelled (no air to carry them).
 * Only music and UI sounds survive.
 */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID, value = Dist.CLIENT)
public class SoundMuffler {

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        SoundInstance original = event.getSound();
        if (original == null) return;

        int phase = ApocalypseClientData.getPhase();
        float progress = ApocalypseClientData.getProgress();

        // Phase 6 late: vacuum — cancel or muffle sounds based on depth
        // Surface (Y >= 0): total silence — no air to carry sound
        // Underground (Y 0 to -32): sound gradually returns — rock insulates from vacuum
        // Deep underground (Y <= -32): full volume — geothermal warmth means breathable air
        if (phase >= 6 && progress >= 0.85f) {
            if (original.getSource() == SoundSource.MUSIC) return;
            if (original.getSource() == SoundSource.MASTER) return;

            int playerY = mc.player.blockPosition().getY();
            if (playerY >= 0) {
                // Surface: complete vacuum silence
                event.setSound(null);
            } else if (playerY > -32) {
                // Transition zone: sound fades in from 0% at Y=0 to 100% at Y=-32
                float volumeMult = (float) -playerY / 32f;
                event.setSound(new MuffledSound(original, volumeMult, 1.0f));
            }
            // Below Y=-32: sounds play normally
            return;
        }

        // Cold-based muffling is a survival mechanic — skip for creative/spectator
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        // Normal cold-based muffling below -15C
        float temp = TemperatureHud.getDisplayedTemp();
        if (temp >= -15f) return;

        // Don't muffle music, UI, or our wind ambience
        if (original.getSource() == SoundSource.MUSIC) return;
        if (original.getSource() == SoundSource.MASTER) return;
        String path = original.getLocation().getPath();
        if (path.startsWith("ambient/wind")) return;

        // Muffle intensity: 0 at -15C, full at -45C
        float intensity = Math.min(1f, (-temp - 15f) / 30f);

        float volumeMult = 1f - (intensity * 0.8f);   // down to 20% volume
        float pitchMult = 1f - (intensity * 0.25f);    // noticeable pitch drop

        event.setSound(new MuffledSound(original, volumeMult, pitchMult));
    }

    /**
     * Wraps a SoundInstance with modified volume and pitch.
     */
    private static class MuffledSound implements SoundInstance {
        private final SoundInstance wrapped;
        private final float volumeMult;
        private final float pitchMult;

        MuffledSound(SoundInstance wrapped, float volumeMult, float pitchMult) {
            this.wrapped = wrapped;
            this.volumeMult = volumeMult;
            this.pitchMult = pitchMult;
        }

        @Override public ResourceLocation getLocation() { return wrapped.getLocation(); }
        @Override public @Nullable WeighedSoundEvents resolve(net.minecraft.client.sounds.SoundManager manager) { return wrapped.resolve(manager); }
        @Override public net.minecraft.client.resources.sounds.Sound getSound() { return wrapped.getSound(); }
        @Override public SoundSource getSource() { return wrapped.getSource(); }
        @Override public boolean isLooping() { return wrapped.isLooping(); }
        @Override public boolean isRelative() { return wrapped.isRelative(); }
        @Override public int getDelay() { return wrapped.getDelay(); }
        @Override public float getVolume() { return wrapped.getVolume() * volumeMult; }
        @Override public float getPitch() { return wrapped.getPitch() * pitchMult; }
        @Override public double getX() { return wrapped.getX(); }
        @Override public double getY() { return wrapped.getY(); }
        @Override public double getZ() { return wrapped.getZ(); }
        @Override public Attenuation getAttenuation() { return wrapped.getAttenuation(); }
    }
}
