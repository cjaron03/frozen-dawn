package com.frozendawn.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MorgueGateTest {
    private final MorgueGate gate = new MorgueGate();

    @Test void anEmptyPlateReadsNothing() {
        gate.tick(MorgueGate.NOBODY);
        assertEquals(0, gate.hearts());
        assertFalse(gate.open());
    }

    @Test void theLivingAreCountedAndKeptOut() {
        gate.tick(20);
        assertEquals(10, gate.hearts());
        assertFalse(gate.open());
        gate.tick(5);
        assertEquals(3, gate.hearts(), "A part heart is a heart");
        gate.tick(2.5F);
        assertFalse(gate.open(), "Just over the line is alive");
    }

    @Test void moreThanTenHeartsStayOnThePanel() {
        gate.tick(40);
        assertEquals(10, gate.hearts());
    }

    @Test void atOneHeartItReadsNobodyAndOpens() {
        gate.tick(2);
        assertTrue(gate.open());
        assertEquals(1, gate.hearts());
        gate.tick(.5F);
        assertTrue(gate.open());
        assertEquals(1, gate.hearts());
    }

    @Test void itOpensForAFewSecondsAfterYouStepOff() {
        gate.tick(2);
        for (int i = 1; i < MorgueGate.OPEN_TICKS; i++) { gate.tick(MorgueGate.NOBODY); assertTrue(gate.open(), "At " + i); }
        assertEquals(1, gate.hearts(), "The reading stands while the door does");
        gate.tick(MorgueGate.NOBODY);
        assertFalse(gate.open());
        assertEquals(0, gate.hearts());
    }

    @Test void healedOverTheLineItShutsAtOnce() {
        gate.tick(2);
        gate.tick(3);
        assertFalse(gate.open());
        assertEquals(2, gate.hearts());
    }

    @Test void theVentBitesEveryTwoSeconds() {
        int bites = 0;
        for (long t = 1; t <= 400; t++) if (MorgueGate.bites(t)) bites++;
        assertEquals(10, bites);
        assertEquals(2, MorgueGate.VENT_DAMAGE, "About a heart");
    }

    @Test void resetClearsTheReading() {
        gate.tick(2);
        gate.reset();
        assertFalse(gate.open());
        assertEquals(0, gate.hearts());
    }
}
