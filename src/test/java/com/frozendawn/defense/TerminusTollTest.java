package com.frozendawn.defense;

import static com.frozendawn.defense.TerminusToll.HALF_HEART_TICKS;
import static com.frozendawn.defense.TerminusToll.NONE;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** A discharge takes two hearts; they come back half a heart each half Minecraft day, whatever else happens. */
class TerminusTollTest {
    @Test
    void aDischargeTakesTwoHeartsAndStartsTheClock() {
        var toll = NONE.discharge(100);
        assertEquals(4, toll.taken());
        assertEquals(100 + HALF_HEART_TICKS, toll.returnsAt());
    }

    @Test
    void anotherDischargeLeavesTheClockRunning() {
        var toll = NONE.discharge(100).discharge(5000);
        assertEquals(8, toll.taken());
        assertEquals(100 + HALF_HEART_TICKS, toll.returnsAt());
    }

    @Test
    void halfAHeartComesBackEachHalfDay() {
        var toll = NONE.discharge(0);
        assertEquals(toll, toll.tick(HALF_HEART_TICKS - 1));
        toll = toll.tick(HALF_HEART_TICKS);
        assertEquals(3, toll.taken());
        assertEquals(2 * HALF_HEART_TICKS, toll.returnsAt());
    }

    @Test
    void oneDischargeIsBackInTwoDays() {
        var toll = NONE.discharge(0);
        for (long now = 1; now < 4 * HALF_HEART_TICKS; now++) toll = toll.tick(now);
        assertEquals(1, toll.taken());
        assertEquals(0, toll.tick(4 * HALF_HEART_TICKS).taken());
    }

    /** On a server the world runs while a player is away: what came due meanwhile comes back, half a heart a tick. */
    @Test
    void timeAwayCountsWhenThePlayerReturns() {
        var toll = new TerminusToll(8, HALF_HEART_TICKS);
        long now = 3 * HALF_HEART_TICKS;
        for (int i = 0; i < 10; i++) toll = toll.tick(now);
        assertEquals(5, toll.taken());
        assertEquals(4 * HALF_HEART_TICKS, toll.returnsAt());
    }

    @Test
    void nothingTakenNothingReturns() {
        assertEquals(NONE, NONE.tick(1_000_000));
    }
}
