package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The wand's log turns from transfers to terminations once, on the CH-18 trigger, and never in an E11 world. */
class TerminusLedgerTest {
    @Test
    void aDeathWhileMaeveStandsChangesNothing() {
        assertFalse(TerminusLedger.turnsOnPlayerDeath(false, false, false));
    }

    @Test
    void theFirstDeathAfterTheHeartFallsTurnsIt() {
        assertTrue(TerminusLedger.turnsOnPlayerDeath(true, false, false));
    }

    @Test
    void anE11WorldNeverTurns() {
        assertFalse(TerminusLedger.turnsOnPlayerDeath(true, true, false));
    }

    @Test
    void onceTurnedItNeverReverts() {
        assertTrue(TerminusLedger.turnsOnPlayerDeath(false, false, true));
        assertTrue(TerminusLedger.turnsOnPlayerDeath(false, true, true));
    }
}
