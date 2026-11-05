package com.frozendawn.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.shepherd.ShepherdAffinity.Deed;
import com.frozendawn.shepherd.ShepherdAffinity.Tier;
import org.junit.jupiter.api.Test;

class ShepherdAffinityTest {
    @Test
    void tiersBreakAtTheSpecsLines() {
        assertEquals(Tier.WARY, ShepherdAffinity.of(-1000));
        assertEquals(Tier.WARY, ShepherdAffinity.of(-251));
        assertEquals(Tier.NEUTRAL, ShepherdAffinity.of(-250));
        assertEquals(Tier.NEUTRAL, ShepherdAffinity.of(249));
        assertEquals(Tier.FRIENDLY, ShepherdAffinity.of(250));
        assertEquals(Tier.FRIENDLY, ShepherdAffinity.of(499));
        assertEquals(Tier.TRUSTED, ShepherdAffinity.of(500));
        assertEquals(Tier.TRUSTED, ShepherdAffinity.of(849));
        assertEquals(Tier.CLOSE, ShepherdAffinity.of(850));
        assertEquals(Tier.CLOSE, ShepherdAffinity.of(1000));
    }

    @Test
    void aTierIsAtLeastItselfAndWhatIsBelow() {
        assertTrue(Tier.TRUSTED.atLeast(Tier.FRIENDLY));
        assertTrue(Tier.FRIENDLY.atLeast(Tier.FRIENDLY));
        assertFalse(Tier.NEUTRAL.atLeast(Tier.FRIENDLY));
    }

    @Test
    void everyDeedScoresWithinTheSpecsRange() {
        for (Deed deed : Deed.values()) {
            assertTrue(Math.abs(deed.score()) >= 5 && Math.abs(deed.score()) <= 25, deed.name());
            assertEquals(deed.score() > 0, deed.liked(), deed.name());
        }
    }
}
