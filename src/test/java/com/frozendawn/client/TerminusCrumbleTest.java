package com.frozendawn.client;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.defense.Terminus;
import org.junit.jupiter.api.Test;

/** The client's crumble has to be over before the server takes the body, or the last of it would just vanish. */
class TerminusCrumbleTest {
    @Test
    void theBodyIsGreyAndStillBeforeItCrumbles() {
        assertEquals(1f, TerminusCrumble.grey(TerminusCrumble.GREY_TICKS));
        assertEquals(0f, TerminusCrumble.crumbled(TerminusCrumble.CRUMBLE_START));
    }

    @Test
    void theCrumbleEndsBeforeTheServerTakesTheBody() {
        assertTrue(TerminusCrumble.crumbled(Terminus.DEATH_TICKS - 1) >= 1 + TerminusCrumble.RAGGED);
    }

    /** The body is held until the server removes it; drawn past the crumble's end it must stay gone, not wrap back. */
    @Test
    void theCrumbleStaysCompleteWhileTheBodyIsHeld() {
        assertEquals(1 + TerminusCrumble.RAGGED, TerminusCrumble.crumbled(Terminus.DEATH_TICKS + 40));
    }
}
