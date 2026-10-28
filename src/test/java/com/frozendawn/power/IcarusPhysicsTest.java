package com.frozendawn.power;

import static com.frozendawn.power.IcarusPhysics.CAPSULE;
import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.power.IcarusPhysics.Inputs;
import com.frozendawn.power.IcarusPhysics.Readout;
import com.frozendawn.power.IcarusPhysics.State;
import com.frozendawn.power.IcarusPhysics.Status;
import org.junit.jupiter.api.Test;

class IcarusPhysicsTest {
    private static Inputs ok(long dockCharge) { return new Inputs(true, true, dockCharge, 8, 64); }
    private static Inputs ok() { return ok(2 * CAPSULE); }
    private static Readout run(IcarusPhysics p, Inputs in, int seconds) {
        Readout last = null;
        for (int i = 0; i < seconds; i++) last = p.step(in);
        return last;
    }
    private static IcarusPhysics commissioned() {
        var p = new IcarusPhysics(); p.setCommissionedForTest(true); return p;
    }

    @Test void coldStartTakesTwoMinutes() {
        var p = commissioned(); assertTrue(p.start());
        State[] expected = new State[121];
        for (int s = 1; s <= 120; s++) expected[s] = s <= 10 ? State.POWER_CHECK : s <= 30 ? State.RESERVE_CHARGE
                : s <= 60 ? State.STABILIZER_SPINUP : s <= 80 ? State.INJECTION : s <= 95 ? State.PRIMING : s <= 100 ? State.IGNITION : State.RAMP;
        for (int s = 1; s <= 120; s++) assertEquals(expected[s], p.step(ok()).state(), "second " + s);
        assertEquals(State.RUNNING, p.step(ok()).state());
    }

    @Test void firstBootAddsOneMinuteOfCommissioning() {
        var p = new IcarusPhysics(); p.start();
        assertEquals(State.STABILIZER_SPINUP, run(p, ok(), 60).state());
        assertEquals(State.COMMISSIONING, p.step(ok()).state());
        assertEquals(State.COMMISSIONING, run(p, ok(), 59).state());
        assertEquals(State.INJECTION, p.step(ok()).state());
        assertEquals(State.RAMP, run(p, ok(), 59).state());
        assertEquals(State.RUNNING, p.step(ok()).state());
        assertTrue(p.commissioned());
    }

    @Test void startupDrawsAboutOneMegawattDuringReserveAndStabilizerSteps() {
        var p = new IcarusPhysics(); p.start();
        for (int s = 1; s <= 120; s++) {
            Readout r = p.step(ok());
            if (r.state() == State.RESERVE_CHARGE || r.state() == State.STABILIZER_SPINUP || r.state() == State.COMMISSIONING)
                assertEquals(IcarusPhysics.STARTUP_DRAW_KW, r.requestKw(), 1e-9, "second " + s);
        }
    }

    @Test void reserveHoldsBootChargeAfterStepTwo() {
        var p = commissioned(); p.start();
        Readout r = run(p, ok(), 30);
        assertEquals(State.RESERVE_CHARGE, r.state());
        assertEquals(IcarusPhysics.RESERVE_BOOT_MJ, r.reserveMJ(), 0.5);
    }

    @Test void unpaidStartupHoldsInsteadOfFailing() {
        var p = commissioned(); p.start();
        Readout before = run(p, ok(), 15);
        var unpaid = new Inputs(true, false, 2 * CAPSULE, 8, 64);
        for (int i = 0; i < 30; i++) {
            Readout r = p.step(unpaid);
            assertEquals(State.RESERVE_CHARGE, r.state()); assertEquals(Status.HOLD_POWER, r.status());
            assertEquals(before.stepSecondsLeft(), r.stepSecondsLeft());
        }
        Readout resumed = p.step(ok());
        assertEquals(Status.OK, resumed.status()); assertEquals(before.stepSecondsLeft() - 1, resumed.stepSecondsLeft());
    }

    @Test void invalidStructureHoldsAtPowerCheck() {
        var p = commissioned(); p.start();
        Readout r = run(p, new Inputs(false, true, 2 * CAPSULE, 8, 64), 30);
        assertEquals(State.POWER_CHECK, r.state()); assertEquals(Status.HOLD_STRUCTURE, r.status());
    }

    @Test void injectionHoldsBelowTwoCapsules() {
        var p = commissioned(); p.start();
        Readout r = run(p, ok(CAPSULE + CAPSULE / 2), 90);
        assertEquals(State.INJECTION, r.state()); assertEquals(Status.HOLD_CAPSULES, r.status());
        assertEquals(0, r.coreCharge());
    }

    @Test void injectionMovesDockChargeIntoCore() {
        var p = commissioned(); p.start();
        long dock = 3 * CAPSULE;
        Readout r = run(p, ok(dock), 79);
        assertEquals(State.INJECTION, r.state()); assertEquals(0, r.coreCharge()); assertEquals(0, r.injected());
        r = p.step(ok(dock));
        assertEquals(State.INJECTION, r.state()); assertEquals(dock, r.injected()); assertEquals(dock, r.coreCharge());
        assertEquals(0, p.step(ok(dock)).injected(), "Injection happens once");
    }

    @Test void injectionStopsAtTheCapsuleCap() {
        var p = commissioned(); p.start();
        Readout r = run(p, ok(80 * CAPSULE), 80);
        assertEquals(IcarusPhysics.MAX_CAPSULES * CAPSULE, r.coreCharge());
    }

    @Test void primingHoldsWithoutMatter() {
        var p = commissioned(); p.start();
        run(p, ok(), 80);
        Readout r = run(p, new Inputs(true, true, 2 * CAPSULE, 8, 0), 30);
        assertEquals(State.PRIMING, r.state()); assertEquals(Status.HOLD_MATTER, r.status());
    }

    @Test void stopBeforeInjectionReturnsCold() {
        var p = commissioned(); p.start();
        run(p, ok(), 40);
        assertTrue(p.cole());
        Readout r = p.step(ok());
        assertEquals(State.COLD, r.state()); assertEquals(0, r.coreCharge()); assertEquals(0, r.requestKw(), 1e-9);
    }

    @Test void startOnlyFromCold() {
        var p = commissioned(); assertTrue(p.start()); assertFalse(p.start());
    }
}
