package com.frozendawn.client;

import com.frozendawn.cyclone.*;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CycloneMobSoundMaskTest {
    @Test void stormMasksVanillaAndModMobsButLeavesPlayerAndProtectedCuesAlone() {
        assertTrue(CycloneMobSoundMask.eligible(SoundSource.HOSTILE,false,"minecraft","entity.zombie.ambient"));
        assertTrue(CycloneMobSoundMask.eligible(SoundSource.NEUTRAL,false,"minecraft","entity.cow.ambient"));
        assertTrue(CycloneMobSoundMask.eligible(SoundSource.HOSTILE,false,"frozendawn","entity.blind_architect.hurt"));
        assertTrue(CycloneMobSoundMask.eligible(SoundSource.AMBIENT,false,"frozendawn","entity.hollow.ambient"));
        for(var source:new SoundSource[]{SoundSource.MASTER,SoundSource.MUSIC,SoundSource.VOICE,SoundSource.PLAYERS})
            assertFalse(CycloneMobSoundMask.eligible(source,false,"frozendawn","entity.hollow.ambient"));
        assertFalse(CycloneMobSoundMask.eligible(SoundSource.AMBIENT,false,"frozendawn","ambient.cyclone.roar"));
        assertFalse(CycloneMobSoundMask.eligible(SoundSource.BLOCKS,false,"frozendawn","block.airlock.ready"));
        assertFalse(CycloneMobSoundMask.eligible(SoundSource.HOSTILE,true,"frozendawn","entity.architect.hurt"));
    }
    @Test void distantSoundsAreDrownedOutWhileCloseSoundsRemainAndCalmRestoresHearing() {
        assertEquals(.1,WindAudioPolicy.mobGain(1,12),1e-6);
        assertEquals(.45,WindAudioPolicy.mobGain(1,1),1e-6);
        for(int distance=0;distance<50;distance++)assertEquals(1,WindAudioPolicy.mobGain(0,distance));
        var mix=new WindAudioPolicy.Mix();var peak=new CycloneSample(CycloneStage.PEAK,1,0,-19,933);
        float previous=1;
        for(int i=0;i<120;i++) {
            mix.tick(peak,CycloneSeverity.SEVERE,1);
            float gain=WindAudioPolicy.mobGain(mix.blend(),20);
            assertTrue(gain<=previous);assertTrue(previous-gain<.01);previous=gain;
        }
        assertEquals(.1,previous,1e-6);
        for(int i=0;i<120;i++)mix.tick(CycloneSample.NONE,CycloneSeverity.SEVERE,1);
        assertEquals(1,WindAudioPolicy.mobGain(mix.blend(),20),1e-6);
    }
}
