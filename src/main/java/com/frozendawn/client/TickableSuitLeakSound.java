package com.frozendawn.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Quiet, vacuum-safe suit-internal hiss that fades with active O2 venting. */
final class TickableSuitLeakSound extends AbstractTickableSoundInstance {

    private static final float FADE_RATE = 0.012F;
    private float targetVolume;

    TickableSuitLeakSound(SoundEvent sound, float targetVolume) {
        super(sound, SoundSource.MASTER, SoundInstance.createUnseededRandom());
        this.volume = 0.0F;
        this.targetVolume = targetVolume;
        this.pitch = 1.0F;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    void setTargetVolume(float targetVolume) {
        this.targetVolume = Math.max(0.0F, targetVolume);
    }

    void fadeOut() {
        targetVolume = 0.0F;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (volume < targetVolume) {
            volume = Math.min(targetVolume, volume + FADE_RATE);
        } else if (volume > targetVolume) {
            volume = Math.max(targetVolume, volume - FADE_RATE);
        }
        if (volume <= 0.001F && targetVolume <= 0.0F) {
            stop();
        }
    }
}
