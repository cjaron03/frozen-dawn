package com.frozendawn.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Looping EVA breathing sound with per-tick volume interpolation.
 * Loops indefinitely until stopped; volume fades smoothly.
 */
@OnlyIn(Dist.CLIENT)
public class TickableBreathingSound extends AbstractTickableSoundInstance {

    private float targetVolume;
    private float targetPitch = 1.0f;
    private float fadeRate = 0.02f; // ~1s for 0→0.5 transition

    public TickableBreathingSound(SoundEvent sound, float initialVolume) {
        super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.volume = initialVolume;
        this.targetVolume = initialVolume;
        this.pitch = 1.0f;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public boolean canStartSilent() { return true; }

    public void setTargetVolume(float target) {
        this.targetVolume = target;
    }

    public void setTargetVolume(float target, float transitionRate) {
        this.targetVolume = target;
        this.fadeRate = Math.max(0.001f, transitionRate);
    }

    public void setTargetPitch(float target) {
        this.targetPitch = target;
    }

    @Override
    public void tick() {
        if (pitch < targetPitch) {
            pitch = Math.min(targetPitch, pitch + 0.01f);
        } else if (pitch > targetPitch) {
            pitch = Math.max(targetPitch, pitch - 0.01f);
        }
        if (volume < targetVolume) {
            volume = Math.min(targetVolume, volume + fadeRate);
        } else if (volume > targetVolume) {
            volume = Math.max(targetVolume, volume - fadeRate);
        }

        if (volume <= 0.001f && targetVolume <= 0f) {
            stop();
        }
    }
}
