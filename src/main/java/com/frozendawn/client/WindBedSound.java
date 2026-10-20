package com.frozendawn.client;

import com.frozendawn.cyclone.WindAudioPolicy;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** One finite clip. All voices receive mix updates, including the retiring overlap. */
final class WindBedSound extends AbstractTickableSoundInstance {
    private final int duration;
    private final boolean shaped;
    private final int overlap;
    private int age;
    private float gain,target;
    private float fadeRate=1f/WindAudioPolicy.CROSSFADE_TICKS;
    private boolean retiring;
    WindBedSound(SoundEvent event,int duration,boolean shaped,int overlap,float initialGain) {
        super(event,SoundSource.AMBIENT,SoundInstance.createUnseededRandom());
        this.duration=duration;this.shaped=shaped;this.overlap=overlap;
        gain=target=Math.max(0,initialGain);volume=0;pitch=1;relative=true;attenuation=Attenuation.NONE;looping=false;delay=0;
    }
    @Override public boolean canStartSilent(){return true;}
    void target(float gain){if(!retiring)target=Math.max(0,gain);}
    void fadeRate(float rate){fadeRate=rate;}
    void retire(){retiring=true;target=0;}
    boolean retired(){return retiring;}
    void resume(){retiring=false;}
    boolean fullyQuiet(){return gain<.0001f && target==0;}
    void stopNow(){stop();}
    @Override public void tick() {
        gain=WindAudioPolicy.approach(gain,target,fadeRate);
        volume=gain*(shaped?WindAudioPolicy.envelope(age,duration,overlap):1);
        age++;
        if(age>=duration || (retiring&&fullyQuiet()))stop();
    }
}
