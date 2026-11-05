package com.frozendawn.miller;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FadingTest {
    @Test
    void aValueHalvesInOneHalfLife() {
        assertEquals(0.4, new Fading(0.8, 100).toward(0, 1000, 1100), 1e-12);
    }

    @Test
    void aValueRisesTowardItsTarget() {
        assertEquals(0.75, new Fading(0.5, 0).toward(1, 1000, 1000), 1e-12);
    }

    @Test
    void readingChangesNothing() {
        var value = new Fading(0.8, 100);
        for (int i = 0; i < 1000; i++) value.toward(0, 1000, 100 + i);
        assertEquals(new Fading(0.8, 100), value);
    }

    @Test
    void twoStepsEqualOne() {
        var start = new Fading(0.8, 0);
        var halfway = start.set(start.toward(0.2, 1000, 50), 50);
        assertEquals(start.toward(0.2, 1000, 100), halfway.toward(0.2, 1000, 100), 1e-12);
    }

    @Test
    void aClockThatRunsBackwardsChangesNothing() {
        assertEquals(0.8, new Fading(0.8, 5000).toward(0, 1000, 10), 0);
    }

    @Test
    void aVeryLongGapStaysFinite() {
        assertEquals(0.3, new Fading(0.8, 0).toward(0.3, 1000, Long.MAX_VALUE / 2), 1e-12);
    }

    @Test
    void ageingMovesTheClockBack() {
        assertEquals(0.4, new Fading(0.8, 1000).aged(1000).toward(0, 1000, 1000), 1e-12);
    }
}
