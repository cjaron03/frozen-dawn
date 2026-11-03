package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The discharge's silence holds, then the world comes back all the way. */
class TerminusHushTest {
    @Test
    void silentThroughTheHoldThenWholeAgain() {
        assertTrue(TerminusHush.gain(0) < 1e-3f);
        assertTrue(TerminusHush.gain(TerminusHush.HOLD_TICKS) < 1e-3f);
        assertEquals(1f, TerminusHush.gain(TerminusHush.HOLD_TICKS + TerminusHush.FADE_TICKS));
    }

    /** At a gain of exactly zero vanilla skips new sounds outright: loops and voice lines started then never come back. */
    @Test
    void neverQuiteZeroSoSoundsStillStart() {
        for (int age = 0; age <= TerminusHush.HOLD_TICKS + TerminusHush.FADE_TICKS; age++) assertTrue(TerminusHush.gain(age) > 0, "age " + age);
    }
}
