package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The discharge's silence holds, then the world comes back all the way. */
class TerminusHushTest {
    @Test
    void silentThroughTheHoldThenWholeAgain() {
        assertEquals(0f, TerminusHush.gain(0));
        assertEquals(0f, TerminusHush.gain(TerminusHush.HOLD_TICKS));
        assertEquals(1f, TerminusHush.gain(TerminusHush.HOLD_TICKS + TerminusHush.FADE_TICKS));
    }
}
