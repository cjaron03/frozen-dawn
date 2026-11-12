package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartMemoryRevealTest {
    @Test
    void oneLineEverySeventyTicksThenAHoldAndAFade() {
        assertEquals(6 * 70 + 100 + 35, HeartMemoryReveal.duration(6));
        assertEquals(0, HeartMemoryReveal.linesShown(69.9F, 6));
        assertEquals(1, HeartMemoryReveal.linesShown(70.0F, 6));
        assertEquals(6, HeartMemoryReveal.linesShown(420.0F, 6));
        assertEquals(6, HeartMemoryReveal.linesShown(5000.0F, 6));
    }

    @Test
    void eachLineFadesInOverEighteenTicks() {
        assertEquals(0.0F, HeartMemoryReveal.lineAlpha(140.0F, 1), 1.0E-4F);
        assertEquals(0.5F, HeartMemoryReveal.lineAlpha(149.0F, 1), 1.0E-4F);
        assertEquals(1.0F, HeartMemoryReveal.lineAlpha(158.0F, 1), 1.0E-4F);
    }

    @Test
    void thePanelFadesOutOverItsLastThirtyFiveTicks() {
        int duration = HeartMemoryReveal.duration(2);
        assertEquals(1.0F, HeartMemoryReveal.panelAlpha(duration - 35.0F, duration), 1.0E-4F);
        assertEquals(0.0F, HeartMemoryReveal.panelAlpha(duration, duration), 1.0E-4F);
        assertEquals(0.0F, HeartMemoryReveal.panelAlpha(0.0F, duration), 1.0E-4F);
    }

    @Test
    void theMusicLeavesInTwentyTicksAndReturnsInForty() {
        float volume = HeartMemoryReveal.MUSIC_VOLUME;
        for (int tick = 0; tick < 20; tick++) {
            volume = HeartMemoryReveal.musicVolume(volume, true);
        }
        assertEquals(0.0F, volume, 1.0E-4F);
        for (int tick = 0; tick < 39; tick++) {
            volume = HeartMemoryReveal.musicVolume(volume, false);
        }
        assertTrue(volume < HeartMemoryReveal.MUSIC_VOLUME);
        volume = HeartMemoryReveal.musicVolume(volume, false);
        assertEquals(HeartMemoryReveal.MUSIC_VOLUME, volume, 1.0E-4F);
    }

    @Test
    void aNearlyClearLineIsNotDrawnSoTheFontCannotForceItOpaque() {
        assertFalse(HeartMemoryReveal.visible(HeartMemoryReveal.lineAlpha(140.1F, 1)));
        assertFalse(HeartMemoryReveal.visible(3.0F / 255.0F));
        assertTrue(HeartMemoryReveal.visible(4.0F / 255.0F));
        assertTrue(HeartMemoryReveal.visible(1.0F));
    }

    @Test
    void thePanelWaitsWhileTheGameIsPaused() {
        assertEquals(10, HeartMemoryReveal.countDown(10, true));
        assertEquals(9, HeartMemoryReveal.countDown(10, false));
        assertEquals(0, HeartMemoryReveal.countDown(0, false));
    }
}
