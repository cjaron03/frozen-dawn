package com.frozendawn.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TickableAlarmSound extends AbstractTickableSoundInstance {

    private static final float FADE_RATE = 0.04f;
    private static final float PITCH_RATE = 0.015f;
    private float targetVolume;
    private float targetPitch;

    public TickableAlarmSound(SoundEvent sound, BlockPos pos, float initialVolume) {
        super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.LINEAR;
        this.pitch = 1.0f;
        this.volume = initialVolume;
        this.targetVolume = initialVolume;
        this.targetPitch = 1.0f;
        moveTo(pos);
    }

    public void moveTo(BlockPos pos) {
        this.x = pos.getX() + 0.5;
        this.y = pos.getY() + 0.75;
        this.z = pos.getZ() + 0.5;
    }

    public void setTargetVolume(float targetVolume) {
        this.targetVolume = targetVolume;
    }

    public void setTargetPitch(float targetPitch) {
        this.targetPitch = targetPitch;
    }

    @Override
    public void tick() {
        if (volume < targetVolume) {
            volume = Math.min(targetVolume, volume + FADE_RATE);
        } else if (volume > targetVolume) {
            volume = Math.max(targetVolume, volume - FADE_RATE);
        }

        if (pitch < targetPitch) {
            pitch = Math.min(targetPitch, pitch + PITCH_RATE);
        } else if (pitch > targetPitch) {
            pitch = Math.max(targetPitch, pitch - PITCH_RATE);
        }

        if (volume <= 0.001f && targetVolume <= 0f) {
            stop();
        }
    }
}
