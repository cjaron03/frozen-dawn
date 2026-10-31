package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frozendawn.power.IcarusPhysics.Alarm;
import com.frozendawn.power.IcarusPhysics.State;
import org.junit.jupiter.api.Test;

class IcarusLookTest {
    private static int r(int rgb) { return rgb >> 16 & 0xFF; }
    private static int g(int rgb) { return rgb >> 8 & 0xFF; }
    private static int b(int rgb) { return rgb & 0xFF; }

    @Test void stabilizersLightOneAtATimeAndRetractOneByOne() {
        assertEquals(0, IcarusLook.beamsLit(State.COLD, 0));
        assertEquals(0, IcarusLook.beamsLit(State.RESERVE_CHARGE, 5));
        // Spin-up counts down from 30 s: the first lights at once, the fourth by the last quarter.
        assertEquals(1, IcarusLook.beamsLit(State.STABILIZER_SPINUP, IcarusPhysics.SPINUP_S));
        assertEquals(2, IcarusLook.beamsLit(State.STABILIZER_SPINUP, 22));
        assertEquals(3, IcarusLook.beamsLit(State.STABILIZER_SPINUP, 15));
        assertEquals(4, IcarusLook.beamsLit(State.STABILIZER_SPINUP, 1));
        assertEquals(4, IcarusLook.beamsLit(State.RUNNING, 0));
        assertEquals(4, IcarusLook.beamsLit(State.CLIPPED, 0));
        assertEquals(4, IcarusLook.beamsLit(State.COLE_RECOVER, 3));
        // The discharge withdraws them in turn over its 20 s.
        assertEquals(4, IcarusLook.beamsLit(State.COLE_DISCHARGE, IcarusPhysics.DISCHARGE_S));
        assertEquals(3, IcarusLook.beamsLit(State.COLE_DISCHARGE, 14));
        assertEquals(1, IcarusLook.beamsLit(State.COLE_DISCHARGE, 1));
        assertEquals(0, IcarusLook.beamsLit(State.DETONATED, 0));
    }

    @Test void coreColourFollowsTemperature() {
        int cold = IcarusLook.coreColor(20, Alarm.NONE);
        assertTrue(r(cold) < 0x40 && g(cold) < 0x40 && b(cold) < 0x40, "cold core is dark");
        int running = IcarusLook.coreColor(3000, Alarm.NONE);
        assertTrue(r(running) > 0xC0 && r(running) > g(running) && g(running) > b(running) && g(running) > 0x50, "running core is orange");
        int hot = IcarusLook.coreColor(IcarusPhysics.RED_LINE_C, Alarm.WARNING);
        assertTrue(r(hot) > 0x90 && g(hot) < 0x40, "hot core is deep red");
        int critical = IcarusLook.coreColor(IcarusPhysics.CRITICAL_C, Alarm.CRITICAL);
        assertTrue(r(critical) > 0xC0 && g(critical) < 0x40, "critical core is red");
    }

    @Test void shieldColourFollowsStrain() {
        assertTrue(IcarusLook.shieldAlpha(State.COLD, 0) < 0.1, "cold shield is off or faint");
        int running = IcarusLook.shieldColor(3000, 100, Alarm.NONE);
        assertTrue(b(running) > r(running) && g(running) > r(running), "running shield is cyan");
        int hot = IcarusLook.shieldColor(IcarusPhysics.RED_LINE_C, 60, Alarm.WARNING);
        assertTrue(r(hot) > g(hot) && g(hot) > b(hot) && g(hot) > 0x60, "hot shield is amber");
        int critical = IcarusLook.shieldColor(IcarusPhysics.CRITICAL_C, 10, Alarm.CRITICAL);
        assertTrue(r(critical) > 0xC0 && g(critical) < 0x50, "critical shield is red");
        assertTrue(IcarusLook.shieldAlpha(State.RUNNING, 100) > IcarusLook.shieldAlpha(State.RUNNING, 20), "a weak shield thins");
    }

    @Test void theCoreBlockGivesWayToThePlasmaFromInjectionUntilTheReactorIsColdAgain() {
        for (var s : State.values()) {
            boolean hidden = switch (s) {
                case INJECTION, PRIMING, IGNITION, RAMP, RUNNING, CLIPPED, COLE_RAMP_DOWN, COLE_COOL, COLE_RECOVER, COLE_DISCHARGE -> true;
                default -> false;
            };
            assertEquals(hidden, IcarusLook.coreHidden(s), s.name());
        }
    }

    @Test void churnRisesWithOutputAndHeat() {
        assertEquals(0, IcarusLook.churn(0, 20));
        assertTrue(IcarusLook.churn(2.5, 3000) > IcarusLook.churn(1, 3000));
        assertTrue(IcarusLook.churn(1, 7000) > IcarusLook.churn(1, 3000));
    }
    @Test void aStabilizerFailsOnlyWhenARunningShieldRunsLowNotWhileItChargesOrDischarges() {
        for (var state : State.values()) {
            boolean running = state == State.RAMP || state == State.RUNNING;
            assertEquals(running ? 0.5 : 0, IcarusLook.stabilizerFailing(state, 15), 1e-9, state + " at 15%");
        }
        assertEquals(0, IcarusLook.stabilizerFailing(State.RUNNING, 30));
        assertEquals(0, IcarusLook.stabilizerFailing(State.RUNNING, 0));
        assertTrue(IcarusLook.stabilizerFailing(State.RUNNING, 3) > IcarusLook.stabilizerFailing(State.RUNNING, 25));
    }
    @Test void dangerClimbsFromCalmThroughWarningToCertainLossAsTheShieldEmpties() {
        assertEquals(0, IcarusLook.danger(7000, 10, Alarm.NONE));
        double warning = IcarusLook.danger(6600, 40, Alarm.WARNING), critical = IcarusLook.danger(5000, 14, Alarm.CRITICAL);
        assertTrue(warning > 0 && warning < critical, "a warning is tense, a critical more so");
        assertTrue(IcarusLook.danger(5000, 5, Alarm.CRITICAL) > critical, "a falling shield raises it");
        assertTrue(IcarusLook.danger(7800, 14, Alarm.CRITICAL) > critical, "so does heat past the red line");
        assertEquals(1, IcarusLook.danger(5000, 0, Alarm.CRITICAL), 1e-9);
    }
}
