package com.frozendawn.client;
import com.frozendawn.cyclone.WindAudioPolicy;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class WindBedSoundTest {
    private static WindBedSound voice(){return new WindBedSound(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("frozendawn","ambient.cyclone.snow")),1280,true,120,1);}
    // The unresolved asset has no Minecraft Sound object in this headless test. Inspect only
    // the instance envelope; sound resolution and perceived mix remain client acceptance.
    private static float volume(WindBedSound sound) {
        try {var field=net.minecraft.client.resources.sounds.AbstractSoundInstance.class.getDeclaredField("volume");
            field.setAccessible(true);return field.getFloat(sound);
        } catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
    }
    @Test void realVoiceOverlapDoesNotApplyAFadeTwice() {
        var old=voice();var next=voice();
        for(int i=0;i<1160;i++)old.tick();
        for(int i=0;i<120;i++) {
            old.tick();next.tick();float a=volume(old),b=volume(next);
            assertEquals(1,a*a+b*b,1e-6,"Actual sound voice at overlap tick "+i);
        }
        assertTrue(old.isStopped());assertFalse(next.isStopped());assertTrue(next.canStartSilent());
    }
    @Test void enteringAndLeavingShelterCanResumeTheSameFadingVoice() {
        var sound=voice();for(int i=0;i<500;i++)sound.tick();sound.retire();
        for(int i=0;i<60;i++)sound.tick();float quiet=volume(sound);
        assertTrue(quiet>.4f && quiet<.6f);assertFalse(sound.isStopped());
        sound.resume();sound.target(1);sound.tick();assertTrue(volume(sound)>quiet);
        for(int i=0;i<120;i++)sound.tick();assertEquals(1,volume(sound),1e-6);
        sound.retire();for(int i=0;i<121;i++)sound.tick();assertTrue(sound.isStopped());
    }
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void thermalSeverDucksNormalAndRecordedWindBeforeRelease(boolean shaped) {
        var sound=new WindBedSound(SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath("frozendawn","ambient.wind_strong")),1280,shaped,120,.9f);
        for(int i=0;i<500;i++)sound.tick();
        sound.target(.9f*.12f);sound.fadeRate(.20f);
        for(int i=0;i<5;i++)sound.tick();
        assertEquals(.108f,volume(sound),1e-6,"Wind must reach the warning duck in 250ms");
        for(int i=5;i<MasterArchitectCombatPolicy.THERMAL_RELEASE_TICK;i++) {
            sound.tick();assertEquals(.108f,volume(sound),1e-6,"Keep the attack warning audible");
        }
        // Ending the warning restores the ordinary gentle transition, without a volume jump.
        sound.fadeRate(1f/WindAudioPolicy.CROSSFADE_TICKS);sound.target(.9f);sound.tick();
        assertEquals(.108f+1f/120,volume(sound),1e-6);
        for(int i=0;i<120;i++)sound.tick();assertEquals(.9f,volume(sound),1e-6);
    }
    @Test void thermalSeverAlsoClearsRetiringSourceTails() {
        var sound=voice();for(int i=0;i<500;i++)sound.tick();sound.retire();
        sound.fadeRate(.20f);
        for(int i=0;i<6;i++)sound.tick();
        assertEquals(0,volume(sound),1e-6);assertTrue(sound.isStopped());
    }

}
