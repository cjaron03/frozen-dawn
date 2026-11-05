package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.miller.Refusal.Kind;
import org.junit.jupiter.api.Test;

class RefusalTest {
    @Test
    void sheNeverRefusesWhenNotCalm() {
        for (int bits = 0; bits < 8; bits++) {
            assertNull(Refusal.check(false, (bits & 1) != 0, 1, (bits & 2) != 0, (bits & 4) != 0));
        }
    }

    @Test
    void aClearNightHoldsHerOnlyWhenSheWantsIt() {
        assertNull(Refusal.check(true, true, 0.69, false, false));
        assertEquals(Kind.CLEAR_NIGHT, Refusal.check(true, true, 0.70, false, false));
        assertNull(Refusal.check(true, false, 1, false, false), "Not on any other night");
    }

    @Test
    void theArchivistHoldsHerOnlyFromSomeoneWhoKilledOne() {
        assertEquals(Kind.ARCHIVIST, Refusal.check(true, false, 0, true, true));
        assertNull(Refusal.check(true, false, 0, true, false), "Nothing against you");
        assertNull(Refusal.check(true, false, 0, false, true), "None in sight");
    }
}
