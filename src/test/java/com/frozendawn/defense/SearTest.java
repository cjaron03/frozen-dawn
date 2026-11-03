package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SearTest {
    @Test
    void aFirstCutSearsForTwoSeconds() {
        assertEquals(Sear.BASE_TICKS, Sear.duration(0));
        assertEquals(0, Sear.amplifier(Sear.duration(0), -1));
    }

    @Test
    void aHeldBeamDeepensTheSearUpToTheCap() {
        assertEquals(Sear.BASE_TICKS + Sear.ADD_PER_HIT, Sear.duration(Sear.BASE_TICKS));
        assertEquals(Sear.MAX_TICKS, Sear.duration(Sear.MAX_TICKS - 1));
        assertEquals(Sear.MAX_TICKS, Sear.duration(Sear.MAX_TICKS));
    }

    @Test
    void aSecondOfContactReachesSearedThree() {
        // The effect ticks down once between beam ticks, so contact nets ADD_PER_HIT - 1 a tick.
        int held = 0, amplifier = -1;
        for (int t = 0; t < 20; t++) {
            held = Sear.duration(held);
            amplifier = Sear.amplifier(held, amplifier);
            if (t == 9) assertEquals(1, amplifier, "Half a second is Seared II");
            held--;
        }
        assertEquals(2, amplifier, "A second is Seared III");
    }

    @Test
    void aFadingSearKeepsItsLevelWhenCutAgain() {
        assertEquals(2, Sear.amplifier(Sear.duration(41), 2));
    }
}
