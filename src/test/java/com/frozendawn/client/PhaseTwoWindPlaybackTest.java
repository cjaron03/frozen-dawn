package com.frozendawn.client;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTwoWindPlaybackTest {
    private WindBedSound voice() {
        var voice = new WindBedSound(SoundEvent.createVariableRangeEvent(
                SoundManager.INTENTIONALLY_EMPTY_SOUND_LOCATION),
                PhaseTwoWindAmbience.CLIP_TICKS, true, PhaseTwoWindAmbience.OVERLAP_TICKS, 0);
        voice.resolve(null);
        return voice;
    }

    @Test void actualVoiceStartsSilentlyAndSurvivesShelterAndDoorwayReversals() {
        var cover = new PhaseOneWindExposure();
        var voice = voice();
        assertEquals(0, voice.getVolume());
        assertTrue(voice.canStartSilent(), "The real engine must admit the fade-in");
        var column = new PhaseTwoWindCoverTest.Column();
        float outside = 0, inside = 0, previous = 0;
        for (int tick = 0; tick < 650; tick++) {
            boolean outdoors = tick < 300 || tick >= 450 && tick < 465 || tick >= 480;
            column.at(66, (outdoors ? net.minecraft.world.level.block.Blocks.AIR
                    : net.minecraft.world.level.block.Blocks.GLASS).defaultBlockState());
            boolean open = PhaseTwoWindCover.isExposed(column, PhaseTwoWindCoverTest.EYE, 67);
            voice.target(PhaseTwoWindPolicy.gain(2, .14F, tick, cover.tick(open), false));
            voice.tick();
            float current = voice.getVolume();
            assertTrue(Math.abs(current - previous) < .02F, "No doorway volume jump at " + tick);
            assertFalse(voice.isStopped());
            previous = current;
            if (tick == 299) outside = current;
            if (tick == 449) inside = current;
        }
        assertTrue(outside > .23F);
        assertTrue(inside > 0 && inside < outside * .10F);
        assertTrue(voice.getVolume() > .23F);
    }

    @Test void leavingPhaseTwoFadesTheActualVoiceAndStopsIt() {
        var voice = voice();
        voice.target(.30F);
        for (int i = 0; i < 300; i++) voice.tick();
        voice.target(PhaseTwoWindPolicy.gain(3, .23F, 300, 1, false));
        voice.retire();
        float previous = voice.getVolume();
        for (int i = 0; i < 50; i++) {
            voice.tick();
            assertTrue(voice.getVolume() <= previous + .00001F);
            previous = voice.getVolume();
        }
        assertEquals(0, voice.getVolume());
        assertTrue(voice.isStopped());
    }
}
