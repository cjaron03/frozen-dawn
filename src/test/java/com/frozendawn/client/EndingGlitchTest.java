package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EndingGlitchTest {
    private static final String WELCOME = "Welcome home.";

    @Test
    void theWelcomeStartsWhole() {
        assertEquals(WELCOME, EndingGlitch.corrupt(WELCOME, 0.0F, 7));
        assertEquals(0, EndingGlitch.sliceOffset(3, 7, 0.0F));
        assertFalse(EndingGlitch.titleCorrupted(7, 0.0F));
        assertEquals(0xFF44E6A8, EndingGlitch.mix(0xFF44E6A8, 0xFFFF5C5C, 0.0F));
    }

    @Test
    void itEndsAlmostWhollyCorruptedAndRed() {
        String corrupted = EndingGlitch.corrupt(WELCOME, 1.0F, 7);
        assertEquals(WELCOME.length(), corrupted.length(), "the line keeps its length");
        int changed = 0;
        for (int i = 0; i < WELCOME.length(); i++) {
            if (WELCOME.charAt(i) == ' ') {
                assertEquals(' ', corrupted.charAt(i), "spaces stay, so the line still reads as words");
            } else if (WELCOME.charAt(i) != corrupted.charAt(i)) {
                changed++;
            }
        }
        assertTrue(changed >= 8, "most letters corrupted, got " + changed);
        assertEquals(0xFFFF5C5C, EndingGlitch.mix(0xFF44E6A8, 0xFFFF5C5C, 1.0F));
    }

    @Test
    void theTearStaysWithinTheSlice() {
        for (int band = 0; band < 20; band++) {
            for (int tick = 0; tick < EndingGlitch.GLITCH_TICKS; tick++) {
                assertTrue(Math.abs(EndingGlitch.sliceOffset(band, tick, 1.0F)) <= EndingGlitch.MAX_TEAR);
            }
        }
    }

    @Test
    void itIsTheSameEveryTime() {
        assertEquals(EndingGlitch.corrupt(WELCOME, 0.6F, 19), EndingGlitch.corrupt(WELCOME, 0.6F, 19));
        assertEquals(EndingGlitch.sliceOffset(4, 19, 0.6F), EndingGlitch.sliceOffset(4, 19, 0.6F));
    }

    @Test
    void itBlinksOutOnlyNearTheCut() {
        for (int tick = 0; tick < EndingGlitch.GLITCH_TICKS; tick++) {
            float progress = EndingGlitch.progress(tick);
            if (progress < 0.85F) {
                assertFalse(EndingGlitch.blankFrame(tick), "tick " + tick);
            }
        }
        assertTrue(java.util.stream.IntStream.range(0, EndingGlitch.GLITCH_TICKS).anyMatch(EndingGlitch::blankFrame));
    }

    @Test
    void theNarrationKeepsItsTiming() {
        assertEquals(34, EndingGlitch.GLITCH_TICKS, "the glitch takes the old static's place");
        assertEquals(44, EndingEpilogue.COMPLIANCE_NARRATION_DELAY_TICKS, "the narration starts where it always did");
    }

    @Test
    void onlyEndingBHoldsItsWelcome() {
        assertTrue(EndingEpilogue.signalHolds(true), "Ending B's welcome stays up until it glitches");
        assertFalse(EndingEpilogue.signalHolds(false), "Ending A's welcome fades out as before");
    }
}
