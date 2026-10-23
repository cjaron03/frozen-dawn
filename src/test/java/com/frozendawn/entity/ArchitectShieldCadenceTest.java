package com.frozendawn.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectShieldCadenceTest {
    @Test void waitsForVisibleCloseContactAndKeepsMinimumOpenings() {
        var cadence = new ArchitectShieldCadence();
        assertFalse(cadence.tick(0, true, 100, () -> .9));
        assertFalse(cadence.tick(10, false, 4, () -> .9));
        assertFalse(cadence.tick(11, true, 4, () -> .9));
        assertTrue(cadence.tick(20, true, 4, () -> .9));
        assertTrue(cadence.tick(54, true, 4, () -> .9));
        for (int t = 55; t < 80; t++) assertFalse(cadence.tick(t, true, 4, () -> .9));
        assertTrue(cadence.tick(80, true, 4, () -> .9));
    }

    @Test void canChooseAttackWithoutRerollingEveryTickOrSkippingForever() {
        var cadence = new ArchitectShieldCadence();
        int[] rolls = {0};
        java.util.function.DoubleSupplier attack = () -> { rolls[0]++; return .1; };
        assertTrue(cadence.tick(0, true, 4, attack));
        assertFalse(cadence.tick(35, true, 4, attack));
        for (int t = 60; t < 85; t++) assertFalse(cadence.tick(t, true, 4, attack));
        assertEquals(1, rolls[0]);
        assertTrue(cadence.tick(85, true, 4, attack));
        assertEquals(1, rolls[0]);
    }

    @Test void sightLossAndWithdrawalLowerWithoutRangeBoundaryFlicker() {
        var cadence = new ArchitectShieldCadence();
        assertTrue(cadence.tick(0, true, 20, () -> .9));
        assertTrue(cadence.tick(1, true, 25, () -> .9));
        assertFalse(cadence.tick(2, true, 37, () -> .9));
        assertFalse(cadence.tick(26, true, 4, () -> .9));
        assertTrue(cadence.tick(27, true, 4, () -> .9));
        assertFalse(cadence.tick(28, false, 4, () -> .9));
    }

    @Test void eachHitProvidesAnExposedBeatAndClearDropsTheOldCadence() {
        var cadence = new ArchitectShieldCadence();
        assertTrue(cadence.tick(0, true, 4, () -> .9));
        cadence.stagger(7);
        for (int t = 7; t < 27; t++) assertFalse(cadence.tick(t, true, 4, () -> .9));
        cadence.stagger(26);
        assertFalse(cadence.tick(45, true, 4, () -> .9));
        assertTrue(cadence.tick(46, true, 4, () -> .9));
        cadence.clear();
        assertTrue(cadence.tick(0, true, 4, () -> .1));
    }
}
