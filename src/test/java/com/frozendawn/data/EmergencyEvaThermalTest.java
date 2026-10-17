package com.frozendawn.data;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EmergencyEvaThermalTest {
    @Test
    void fiveMinuteClockIsWornTimeEvenUnderHigherOxygenDraw() {
        var thermal = new EmergencyEvaThermal(0, 0);
        var effort = new EmergencyEvaExertion();
        int oxygenCost = 0;
        for (int i = 0; i < 5999; i++) {
            thermal.tickWorn(true, true);
            effort.advance(true);
            oxygenCost += effort.debit();
        }
        assertTrue(oxygenCost > 6000);
        assertFalse(thermal.coolingDegraded());
        assertEquals(0, thermal.heat());
        thermal.tickWorn(true, true);
        assertTrue(thermal.coolingDegraded());
        assertEquals(0, thermal.heat(), "Crossing the warning threshold does not inject heat");
        thermal.tickWorn(true, true);
        assertEquals(1, thermal.heat());
    }

    @Test
    void escapeBurstIsBoundedAndWalkingFullyCoolsSustainedHeat() {
        var thermal = new EmergencyEvaThermal(7200, 0);
        for (int i = 0; i < 100; i++) thermal.tickWorn(true, true);
        assertFalse(thermal.highHeat(), "Five-second escape from a cool suit remains below high heat");
        for (int i = 0; i < 100; i++) thermal.tickWorn(true, true);
        assertTrue(thermal.highHeat());
        for (int i = 0; i < 1000; i++) thermal.tickWorn(true, true);
        assertEquals(EmergencyEvaThermal.MAX_HEAT, thermal.heat());
        for (int i = 0; i < 600; i++) thermal.tickWorn(false, true);
        assertEquals(0, thermal.heat());
        assertTrue(thermal.coolingDegraded(), "Rest restores heat headroom, not consumable coolant");
    }

    @Test
    void removingSuitPausesItsAgeAndUnsealedMovementCannotAccumulateHeat() {
        var thermal = new EmergencyEvaThermal(8000, 500);
        for (int i = 0; i < 300; i++) thermal.recover();
        assertEquals(8000, thermal.wornTicks());
        assertEquals(0, thermal.heat());
        for (int i = 0; i < 100; i++) thermal.tickWorn(true, false);
        assertEquals(0, thermal.heat());
        assertEquals(8100, thermal.wornTicks());
    }
}
