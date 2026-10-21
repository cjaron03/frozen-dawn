package com.frozendawn.client;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickableNatureSoundTest {
    private TickableNatureSound phrase() {
        var sound = new TickableNatureSound(SoundEvent.createVariableRangeEvent(
                SoundManager.INTENTIONALLY_EMPTY_SOUND_LOCATION));
        // Resolve vanilla unit-gain metadata without opening an audio device.
        sound.resolve(null);
        return sound;
    }

    @Test void silentStartIsAdmittedAndTheActualSoundFadesInAndOut() {
        var sound = phrase();
        assertEquals(0, sound.getVolume());
        // SoundEngine.play skips a zero-volume sound unless this contract is true.
        assertTrue(sound.getVolume() > 0 || sound.canStartSilent());
        sound.setTargetVolume(.35F, .0125F);
        for (int i = 0; i < 30; i++) sound.tick();
        assertEquals(.35F, sound.getVolume(), .0001F);
        assertFalse(sound.isStopped());
        sound.setTargetVolume(0, .0125F);
        for (int i = 0; i < 30; i++) sound.tick();
        assertEquals(0, sound.getVolume(), .0001F);
        assertTrue(sound.isStopped());
    }

    @Test void anAudiblePhraseFinishesSoTheControllerCanScheduleItsNextGap() {
        var sound = phrase();
        sound.setTargetVolume(.35F, .0125F);
        for (int i = 0; i < 601; i++) sound.tick();
        assertFalse(sound.isStopped());
        sound.tick();
        assertTrue(sound.isStopped());
    }
}
