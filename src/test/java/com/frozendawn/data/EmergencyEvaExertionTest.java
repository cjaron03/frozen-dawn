package com.frozendawn.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmergencyEvaExertionTest {
    @Test
    void sustainedRunningRampsToSteadyDrawAndRecoveryReturnsToBaseline() {
        var effort = new EmergencyEvaExertion();
        int cost = 0;
        for (int tick = 0; tick < 50; tick++) {
            effort.advance(true);
            cost += effort.debit();
        }
        assertEquals(200, effort.load());
        assertTrue(cost > 50 && cost < 63, "Ramp costs less than immediate maximum draw");
        int steadyCost = 0;
        for (int tick = 0; tick < 400; tick++) { effort.advance(true); steadyCost += effort.debit(); }
        assertEquals(500, steadyCost);
        int recoveringCost = 0;
        for (int tick = 0; tick < 200; tick++) { effort.advance(false); recoveringCost += effort.debit(); }
        assertEquals(0, effort.load());
        assertTrue(recoveringCost > 200 && recoveringCost < 250);
        for (int tick = 0; tick < 200; tick++) { effort.advance(false); assertEquals(1, effort.debit()); }
    }

    @Test
    void interruptedRunsCannotDiscardFractionalOxygenCost() {
        var continuous = new EmergencyEvaExertion();
        var resumed = new EmergencyEvaExertion();
        int continuousCost = 0;
        int resumedCost = 0;
        for (int tick = 0; tick < 1000; tick++) {
            boolean running = tick % 120 < 70;
            continuous.advance(running); continuousCost += continuous.debit();
            resumed.advance(running); resumedCost += resumed.debit();
            // Simulate repeated save/restore between individual debit updates.
            if (tick % 7 == 0) resumed = new EmergencyEvaExertion(resumed.load(), resumed.fractionalDebit());
        }
        assertEquals(continuousCost, resumedCost);
        assertEquals(continuous.load(), resumed.load());
        assertEquals(continuous.fractionalDebit(), resumed.fractionalDebit());
    }
}
