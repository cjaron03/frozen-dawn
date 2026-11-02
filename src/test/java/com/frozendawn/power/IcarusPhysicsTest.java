package com.frozendawn.power;

import static com.frozendawn.power.IcarusPhysics.CAPSULE;
import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.power.IcarusPhysics.Alarm;
import com.frozendawn.power.IcarusPhysics.Cascade;
import com.frozendawn.power.IcarusPhysics.Inputs;
import com.frozendawn.power.IcarusPhysics.Readout;
import com.frozendawn.power.IcarusPhysics.Settings;
import com.frozendawn.power.IcarusPhysics.State;
import com.frozendawn.power.IcarusPhysics.Status;
import net.minecraft.nbt.CompoundTag;
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

    @Test void aColdStartDrawsOnlyDuringTheReserveAndStabilizerSteps() {
        var p = commissioned(); p.start();
        double kJ = 0;
        for (int s = 1; s <= 100; s++) {
            Readout r = p.step(ok());
            boolean draws = r.state() == State.RESERVE_CHARGE || r.state() == State.STABILIZER_SPINUP;
            double expected = draws ? IcarusPhysics.STARTUP_DRAW_KW : r.state() == State.POWER_CHECK ? IcarusPhysics.POWER_PROBE_KW : 0;
            assertEquals(expected, r.requestKw(), 1e-9, "second " + s + " " + r.state());
            kJ += r.requestKw();
        }
        assertEquals(50_100, kJ, 1e-6);
    }

    @Test void aHeldStartupDrawsNothingUnlessItWaitsForPower() {
        var p = commissioned(); p.start();
        run(p, ok(), 80);
        var noMatter = new Inputs(true, true, 0, 8, 0);
        for (int i = 0; i < 60; i++) {
            Readout r = p.step(noMatter);
            assertEquals(Status.HOLD_MATTER, r.status()); assertEquals(0, r.requestKw(), 1e-9);
        }
        var q = commissioned(); q.start();
        run(q, ok(), 15);
        Readout broken = q.step(new Inputs(false, true, 2 * CAPSULE, 8, 64));
        assertEquals(Status.HOLD_STRUCTURE, broken.status()); assertEquals(0, broken.requestKw(), 1e-9);
        Readout unpaid = q.step(new Inputs(true, false, 2 * CAPSULE, 8, 64));
        assertEquals(Status.HOLD_POWER, unpaid.status()); assertEquals(IcarusPhysics.STARTUP_DRAW_KW, unpaid.requestKw(), 1e-9);
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

    private static final double INF = Double.POSITIVE_INFINITY, FOUNDRY_MJ = 1440;
    private static final Settings FULL = new Settings(2.5, 1.0, 30);
    private static final Inputs STARVED = new Inputs(true, true, 0, 8, 0), STARVED_UNPAID = new Inputs(true, false, 0, 8, 0);
    /** Returns a reactor whose next step is its first RUNNING second. */
    private static IcarusPhysics runToRunning(Settings settings, int capsules) {
        var p = commissioned(); p.settings(settings); p.start();
        run(p, ok(capsules * CAPSULE), 120);
        assertEquals(State.RUNNING, p.state());
        return p;
    }
    private record Run(long used, double netMJ) {}
    private static Run burn(IcarusPhysics p, Inputs in, int seconds) {
        long before = p.coreCharge(); double net = 0;
        for (int i = 0; i < seconds; i++) net += p.step(in).netMw();
        return new Run(before - p.coreCharge(), net);
    }

    @Test void antimatterIsUsedPerEnergyNotTime() {
        assertEquals(7200, IcarusPhysics.mjPerCapsule(1.0), 1e-9);
        assertEquals(4500, IcarusPhysics.mjPerCapsule(2.5), 1e-9);
        assertEquals(7200, IcarusPhysics.mjPerCapsule(0.3), 1e-9);
    }

    @Test void minimumRunsAboutTwoHoursPerCapsule() {
        assertEquals(CAPSULE, burn(runToRunning(Settings.MINIMUM, 3), ok(0), 7200).used(), CAPSULE * 0.03);
    }

    @Test void minimumReturnsAtLeastTwoPointSevenTimesTheFoundryCost() {
        assertTrue(burn(runToRunning(Settings.MINIMUM, 3), ok(0), 7200).netMJ() >= 2.7 * FOUNDRY_MJ);
    }

    @Test void fullOutputBurnsACapsuleInAboutThirtyMinutes() {
        assertEquals(CAPSULE, burn(runToRunning(FULL, 3), ok(0), 1800).used(), CAPSULE * 0.05);
    }

    @Test void fullOutputReturnsAboutOnePointFiveTimes() {
        double net = burn(runToRunning(FULL, 3), ok(0), 1800).netMJ();
        assertTrue(net >= 1.3 * FOUNDRY_MJ && net <= 1.7 * FOUNDRY_MJ, "net " + net);
        assertTrue(net > FOUNDRY_MJ);
    }

    @Test void minimumNetIsAboutSixHundredKilowatts() {
        var p = runToRunning(Settings.MINIMUM, 3);
        Readout r = run(p, ok(0), 1200);
        assertEquals(IcarusPhysics.RESERVE_MJ, r.reserveMJ(), 1e-6);
        assertEquals(0.60, r.netMw(), 0.06); assertEquals(0.40, r.shieldDrawMw(), 0.05);
    }

    @Test void fullNetIsAboutOnePointTwoMegawatts() {
        Readout r = run(runToRunning(FULL, 3), ok(0), 1200);
        assertEquals(IcarusPhysics.RESERVE_MJ, r.reserveMJ(), 1e-6);
        assertEquals(1.2, r.netMw(), 0.18); assertEquals(1.3, r.shieldDrawMw(), 0.2);
    }

    @Test void minimumNeverRaisesMoreThanCaution() {
        var p = runToRunning(Settings.MINIMUM, 2);
        for (int s = 0; s < 20_000 && p.state() != State.COLD; s++) {
            Readout r = p.step(ok(0));
            assertTrue(r.alarm().compareTo(Alarm.CAUTION) <= 0, "second " + s + " " + r.alarm());
            assertFalse(r.detonate());
        }
        assertEquals(State.COLD, p.state(), "The load burns out to a safe end");
        assertEquals(Status.SAFE_TO_DISMANTLE, p.step(ok(0)).status());
    }

    @Test void depletionDriftForcesARetuneAtFullOutput() {
        var p = runToRunning(FULL, 4);
        boolean warned = false;
        for (int s = 0; s < 20_000 && p.coreCharge() > 0 && !warned; s++) {
            Readout r = p.step(ok(0));
            assertFalse(r.detonate());
            warned = r.alarm().compareTo(Alarm.WARNING) >= 0;
        }
        assertTrue(warned && p.coreCharge() > 0);
    }

    @Test void shieldDrainClimbsSteeplyAboveTheRedLine() {
        double below = IcarusPhysics.shieldDrainPerSecond(IcarusPhysics.RED_LINE_C - 500);
        double above = IcarusPhysics.shieldDrainPerSecond(IcarusPhysics.RED_LINE_C + 500);
        assertTrue(above >= 3 * below, below + " -> " + above);
    }

    @Test void starvedMatterCutsOutputButNotAntimatter() {
        var p = runToRunning(Settings.MINIMUM, 3);
        run(p, ok(0), 10);
        assertEquals(Status.MATTER_STARVED, p.step(STARVED).status());
        Readout r = run(p, STARVED, 60);
        assertEquals(0, r.grossMw(), 1e-9);
        long charge = r.coreCharge();
        assertEquals(charge, run(p, STARVED, 30).coreCharge());
    }

    @Test void underfeedCapsOutput() {
        assertTrue(run(runToRunning(new Settings(2.5, 1.0, 12), 3), ok(0), 200).grossMw() <= 1.0 + 0.05);
    }

    @Test void reserveCoversAGridFailure() {
        var p = runToRunning(Settings.MINIMUM, 3);
        run(p, ok(0), 1200); run(p, STARVED, 60);
        Readout last = p.step(STARVED);
        for (int i = 0; i < 60; i++) {
            Readout r = p.step(STARVED_UNPAID);
            assertEquals(Status.RESERVE_IN_USE, r.status());
            assertEquals(last.shieldPct(), r.shieldPct(), 1e-9);
            assertEquals(last.reserveMJ() - r.shieldDrawMw(), r.reserveMJ(), 1e-9);
            last = r;
        }
    }

    @Test void reserveDoesNotRechargeFromGridWhileBacking() {
        var p = runToRunning(Settings.MINIMUM, 3);
        run(p, STARVED, 60);
        double reserve = p.step(STARVED).reserveMJ();
        for (int i = 0; i < 120; i++) {
            Readout r = p.step(i % 2 == 0 ? STARVED_UNPAID : STARVED);
            assertTrue(r.reserveMJ() <= reserve + 1e-12); reserve = r.reserveMJ();
        }
    }

    @Test void shieldFailureWithChargeDetonates() {
        var p = runToRunning(Settings.MINIMUM, 3);
        run(p, STARVED, 30);
        Readout r = null;
        for (int s = 0; s < 5000 && (r == null || !r.detonate()); s++) r = p.step(STARVED_UNPAID);
        assertTrue(r.detonate()); assertEquals(State.DETONATED, r.state()); assertTrue(r.coreCharge() > 0);
        assertEquals(0, r.shieldPct(), 1e-9);
        p.detonated();
        assertEquals(0, p.coreCharge()); assertFalse(p.step(STARVED_UNPAID).detonate());
    }

    @Test void predictionsCountDown() {
        var p = runToRunning(Settings.MINIMUM, 3);
        Readout stable = run(p, ok(0), 30);
        assertEquals(INF, stable.shieldEtaS()); assertEquals(INF, stable.reserveEtaS()); assertEquals(INF, stable.redLineEtaS());
        assertEquals(stable.coreCharge() / (CAPSULE / 7200.0), stable.antimatterEtaS(), 1);
        run(p, STARVED, 30);
        Readout backing = p.step(STARVED_UNPAID);
        assertEquals(backing.reserveMJ() / backing.shieldDrawMw(), backing.reserveEtaS(), 1e-6);
        Readout r = backing;
        while (r.reserveMJ() > 0 || r.shieldEtaS() == INF) r = p.step(STARVED_UNPAID);
        Readout next = p.step(STARVED_UNPAID);
        assertTrue(Double.isFinite(r.shieldEtaS()));
        assertEquals(r.shieldEtaS() - 1, next.shieldEtaS(), 0.5);
    }

    @Test void alarmTiers() {
        assertEquals(Alarm.NONE, IcarusPhysics.alarm(Status.OK, 3000, 100, INF, INF, INF, INF));
        assertEquals(Alarm.CAUTION, IcarusPhysics.alarm(Status.OK, 3000, 100, 599, INF, INF, INF));
        assertEquals(Alarm.CAUTION, IcarusPhysics.alarm(Status.OK, 3000, 100, INF, 599, INF, INF));
        assertEquals(Alarm.CAUTION, IcarusPhysics.alarm(Status.MATTER_STARVED, 3000, 100, INF, INF, INF, INF));
        assertEquals(Alarm.CAUTION, IcarusPhysics.alarm(Status.RESERVE_IN_USE, 3000, 100, INF, INF, INF, INF));
        assertEquals(Alarm.CAUTION, IcarusPhysics.alarm(Status.OK, 3000, 100, INF, INF, INF, 10), "Running out of fuel is a safe end");
        assertEquals(Alarm.WARNING, IcarusPhysics.alarm(Status.OK, 3000, 100, 179, INF, INF, INF));
        assertEquals(Alarm.WARNING, IcarusPhysics.alarm(Status.OK, 3000, 100, INF, 10, INF, INF));
        assertEquals(Alarm.WARNING, IcarusPhysics.alarm(Status.OK, IcarusPhysics.RED_LINE_C, 100, INF, INF, INF, INF));
        assertEquals(Alarm.WARNING, IcarusPhysics.alarm(Status.RESERVE_IN_USE, 3000, 100, INF, INF, 179, INF));
        assertEquals(Alarm.CRITICAL, IcarusPhysics.alarm(Status.OK, 3000, 14, INF, INF, INF, INF));
        assertEquals(Alarm.CRITICAL, IcarusPhysics.alarm(Status.OK, 3000, 100, 59, INF, INF, INF));
        assertEquals(Alarm.CRITICAL, IcarusPhysics.alarm(Status.RESERVE_IN_USE, 3000, 100, INF, INF, 59, INF));
    }

    @Test void cascadeReadout() {
        assertEquals(Cascade.STABLE, IcarusPhysics.cascade(3000, 100, false));
        assertEquals(Cascade.STRAINED, IcarusPhysics.cascade(3000, 90, true));
        assertEquals(Cascade.STRAINED, IcarusPhysics.cascade(IcarusPhysics.RED_LINE_C, 100, false));
        assertEquals(Cascade.RUNAWAY, IcarusPhysics.cascade(IcarusPhysics.CRITICAL_C, 100, false));
        assertEquals(Cascade.RUNAWAY, IcarusPhysics.cascade(3000, 14, true));
    }

    private static long clipped(IcarusPhysics p) {
        long before = p.coreCharge(); assertTrue(p.clip()); return before;
    }

    @Test void clipStopsOutputWithinSeconds() {
        var p = runToRunning(FULL, 3); run(p, ok(0), 60);
        long before = clipped(p);
        Readout r = p.step(ok(0));
        assertEquals(State.CLIPPED, r.state()); assertEquals(0, r.grossMw(), 1e-9);
        assertEquals(before - IcarusPhysics.CLIP_LOSS, r.coreCharge());
        assertFalse(p.clip(), "Already clipped");
    }

    @Test void clipKeepsTheShieldOnGridPower() {
        var p = runToRunning(Settings.MINIMUM, 3); run(p, ok(0), 30); clipped(p);
        for (int i = 0; i < 60; i++) {
            Readout r = p.step(ok(0));
            assertEquals(r.shieldDrawMw() * 1000, r.requestKw(), 1e-9); assertEquals(100, r.shieldPct(), 1e-9);
        }
    }

    @Test void clipWithLessThanTheLossEndsSafely() {
        var p = runToRunning(Settings.MINIMUM, 3);
        p.setCoreChargeForTest(IcarusPhysics.CLIP_LOSS / 2); clipped(p);
        assertEquals(0, p.coreCharge());
        Readout r = run(p, ok(0), 20);
        assertEquals(State.COLE_DISCHARGE, r.state()); assertFalse(r.detonate());
        r = p.step(ok(0));
        assertEquals(State.COLD, r.state()); assertEquals(Status.SAFE_TO_DISMANTLE, r.status());
    }

    @Test void restartAfterClipTakesFortySeconds() {
        var p = runToRunning(Settings.MINIMUM, 3); run(p, ok(0), 30); clipped(p); run(p, ok(0), 10);
        assertTrue(p.start());
        assertEquals(State.INJECTION, p.step(ok(0)).state());
        assertEquals(State.RAMP, run(p, ok(0), 39).state());
        assertEquals(State.RUNNING, p.step(ok(0)).state());
    }

    @Test void restartAfterClipInjectsNewDockCapsules() {
        var p = runToRunning(Settings.MINIMUM, 3); run(p, ok(0), 30); clipped(p); p.step(ok(0));
        long before = p.coreCharge();
        p.start();
        Readout r = run(p, ok(3 * CAPSULE), 10);
        assertEquals(3 * CAPSULE, r.injected()); assertEquals(before + 3 * CAPSULE, r.coreCharge());
    }

    @Test void coleProtocolTakesThreeMinutesAndRecoversEverything() {
        var p = runToRunning(Settings.MINIMUM, 3); run(p, ok(0), 30);
        assertTrue(p.cole());
        long atRecover = -1, recovered = 0;
        for (int s = 1; s <= 180; s++) {
            Readout r = p.step(ok(0));
            assertNotEquals(State.COLD, r.state(), "second " + s);
            if (r.state() == State.COLE_RECOVER && atRecover < 0) atRecover = r.coreCharge();
            recovered += r.recovered();
        }
        Readout end = p.step(ok(0));
        assertEquals(State.COLD, end.state()); assertEquals(Status.SAFE_TO_DISMANTLE, end.status());
        assertTrue(atRecover > 2 * CAPSULE); assertEquals(atRecover, recovered); assertEquals(0, end.coreCharge());
    }

    @Test void coleHoldsWithoutCapsuleSpace() {
        var p = runToRunning(Settings.MINIMUM, 3); run(p, ok(0), 30); p.cole();
        var noSpace = new Inputs(true, true, 0, 0, 64);
        Readout r = run(p, noSpace, 220);
        assertEquals(State.COLE_RECOVER, r.state()); assertEquals(Status.HOLD_CAPSULE_SPACE, r.status()); assertTrue(r.coreCharge() > 0);
        assertEquals(State.COLD, run(p, ok(0), 61).state());
    }

    @Test void coleFromClippedSkipsTheRampDown() {
        var p = runToRunning(Settings.MINIMUM, 3); run(p, ok(0), 30); clipped(p); p.step(ok(0));
        assertTrue(p.cole());
        assertEquals(State.COLE_COOL, p.step(ok(0)).state());
    }

    @Test void antimatterIsConserved() {
        var p = runToRunning(FULL, 3);
        long injected = 3 * CAPSULE, recovered = 0;
        run(p, ok(0), 500); clipped(p); run(p, ok(0), 30);
        p.start();
        for (int i = 0; i < 400; i++) injected += p.step(ok(2 * CAPSULE)).injected();
        p.cole();
        for (int i = 0; i < 300; i++) recovered += p.step(ok(0)).recovered();
        assertEquals(State.COLD, p.state());
        assertEquals(injected, p.used() + p.lost() + recovered + p.coreCharge());
        assertEquals(IcarusPhysics.CLIP_LOSS, p.lost());
    }

    private static IcarusPhysics copy(IcarusPhysics p) {
        CompoundTag t = new CompoundTag(); p.save(t);
        var q = new IcarusPhysics(); q.load(t); return q;
    }
    private static void assertSameFuture(IcarusPhysics p, Inputs in) {
        var q = copy(p);
        for (int i = 0; i < 300; i++) assertEquals(p.step(in), q.step(in), "second " + i);
    }

    @Test void saveLoadRoundTripMatchesUninterruptedRun() {
        var p = commissioned(); p.settings(FULL); p.start();
        run(p, ok(), 15);
        assertEquals(State.RESERVE_CHARGE, p.state());
        assertSameFuture(p, ok());
        var running = runToRunning(FULL, 3); run(running, ok(0), 100);
        assertSameFuture(running, ok(0));
        running.cole(); run(running, ok(0), 70);
        assertEquals(State.COLE_COOL, running.state());
        assertSameFuture(running, ok(0));
    }

    @Test void firstSecondAfterLoadNeverFails() {
        var p = runToRunning(FULL, 3);
        Readout saved = run(p, ok(0), 300);
        Readout r = copy(p).step(new Inputs(true, false, 0, 8, 64));
        assertFalse(r.detonate()); assertEquals(saved.shieldPct(), r.shieldPct(), 1);
    }
}
