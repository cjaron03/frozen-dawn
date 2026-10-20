package com.frozendawn.client;
import com.frozendawn.cyclone.WindAudioPolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;
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
}
