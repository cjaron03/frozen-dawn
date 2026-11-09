package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.lore.PayoffState;
import org.junit.jupiter.api.Test;

/** The wand's log turns from transfers to terminations once, on the CH-18 trigger, and never in an E11 world. */
class TerminusLedgerTest {
    @Test
    void aDeathWhileMaeveStandsChangesNothing() {
        assertFalse(PayoffState.firesOnDeath(false, false, false));
    }

    @Test
    void theFirstDeathAfterTheHeartFallsTurnsIt() {
        assertTrue(PayoffState.firesOnDeath(true, false, false));
    }

    @Test
    void anE11WorldNeverTurns() {
        assertFalse(PayoffState.firesOnDeath(true, true, false));
    }

    @Test
    void itTurnsOnlyOnce() {
        assertFalse(PayoffState.firesOnDeath(true, false, true));
    }
}
