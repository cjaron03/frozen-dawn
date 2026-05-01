package com.frozendawn.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Wind sound instance with per-tick volume interpolation.
 * The sound engine reads volume each frame, so changes are smooth — no hard cuts.
 */
@OnlyIn(Dist.CLIENT)
public class TickableWindSound extends AbstractTickableSoundInstance {

    private float targetVolume;
    private float targetPitch;
    private final float fadeRate;
    private final float pitchFadeRate;
    private int ticksRemaining;

    public TickableWindSound(SoundEvent sound, float initialVolume, float pitch, int durationTicks) {
        super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.volume = initialVolume;
        this.targetVolume = initialVolume;
        this.pitch = pitch;
        this.targetPitch = pitch;
        this.ticksRemaining = durationTicks;
        this.fadeRate = 0.025f; // ~2s for full 0→1 transition (smooth but responsive)
        this.pitchFadeRate = 0.01f;
        this.looping = false;
        this.delay = 0;
        this.relative = true; // global, no positional attenuation
        this.attenuation = Attenuation.NONE;
    }

    public void setTargetVolume(float target) {
        this.targetVolume = target;
    }

    public void setTargetPitch(float target) {
        this.targetPitch = target;
    }

    public void fadeOut() {
        this.targetVolume = 0f;
    }

    @Override
    public void tick() {
        // Smooth volume interpolation
        if (volume < targetVolume) {
            volume = Math.min(targetVolume, volume + fadeRate);
        } else if (volume > targetVolume) {
            volume = Math.max(targetVolume, volume - fadeRate);
        }

        if (pitch < targetPitch) {
            pitch = Math.min(targetPitch, pitch + pitchFadeRate);
        } else if (pitch > targetPitch) {
            pitch = Math.max(targetPitch, pitch - pitchFadeRate);
        }

        // Stop when faded out completely
        if (volume <= 0.001f && targetVolume <= 0f) {
            stop();
            return;
        }

        ticksRemaining--;
        if (ticksRemaining <= 0) {
            stop();
        }
    }
}
