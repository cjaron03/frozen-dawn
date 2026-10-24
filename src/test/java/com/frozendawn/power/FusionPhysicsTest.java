package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class FusionPhysicsTest {
    private static List<Double> sinks(int count) { return Collections.nCopies(count, ReactorPhysics.DEEP_VACUUM_C); }
    private static FusionPhysics.Inputs ok(int radiators) {
        return new FusionPhysics.Inputs(sinks(radiators), true, 0, true, true, false, 64, true, 0, false, Double.NaN);
    }
    private static FusionPhysics.Inputs with(int radiators, boolean powered, double pressure, boolean hatchClosed, boolean roomVacuum,
            int deuterium, double wear) {
        return new FusionPhysics.Inputs(sinks(radiators), powered, pressure, hatchClosed, true, roomVacuum, deuterium, true, wear, false, Double.NaN);
    }
    private static FusionPhysics.Readout run(FusionPhysics f, FusionPhysics.Inputs in, int seconds) {
        FusionPhysics.Readout last = null;
        for (int i = 0; i < seconds; i++) last = f.step(in);
        return last;
    }

    @Test void cooldownTakesTwoMinutesOnTwentyKw() {
        var f = new FusionPhysics(); f.start();
        var r = run(f, ok(14), 119);
        assertEquals(FusionPhysics.State.COOLDOWN, r.state()); assertEquals(20, r.requestKw(), 1e-9);
        assertEquals(FusionPhysics.State.READY, f.step(ok(14)).state());
    }

    @Test void cooldownHalvesInAVacuumRoom() {
        var f = new FusionPhysics(); f.start();
        var vacuum = with(14, true, 0, true, true, 64, 0);
        assertEquals(FusionPhysics.State.COOLDOWN, run(f, vacuum, 59).state());
        assertEquals(FusionPhysics.State.READY, f.step(vacuum).state());
    }

    @Test void heliumHalvesCooldown() {
        var f = new FusionPhysics(); f.start(); assertTrue(f.boostCooldown()); assertTrue(f.cooldownBoosted());
        assertEquals(FusionPhysics.State.COOLDOWN, run(f, ok(14), 59).state());
        assertEquals(FusionPhysics.State.READY, f.step(ok(14)).state());
        assertFalse(f.cooldownBoosted(), "The boost is spent on reaching READY");
    }

    @Test void heliumAndVacuumQuarterCooldown() {
        var f = new FusionPhysics(); f.start(); f.boostCooldown();
        var vacuum = with(14, true, 0, true, true, 64, 0);
        assertEquals(FusionPhysics.State.COOLDOWN, run(f, vacuum, 29).state());
        assertEquals(FusionPhysics.State.READY, f.step(vacuum).state());
    }

    @Test void heliumCannotBoostACooldownAlreadyUnderway() {
        var f = new FusionPhysics(); f.start(); f.step(ok(14));
        assertFalse(f.boostCooldown()); assertFalse(f.cooldownBoosted());
    }

    @Test void heliumBoostSurvivesAnUnpaidStart() {
        var f = new FusionPhysics(); f.start(); assertTrue(f.boostCooldown());
        assertEquals(FusionPhysics.State.OFF, f.step(with(14, false, 0, true, false, 64, 0)).state());
        assertTrue(f.cooldownBoosted(), "A cooldown that never counted keeps its canister's boost");
        assertFalse(f.boostCooldown(), "An already boosted reactor takes no second canister");
        f.start();
        assertEquals(FusionPhysics.State.COOLDOWN, run(f, ok(14), 59).state());
        assertEquals(FusionPhysics.State.READY, f.step(ok(14)).state());
    }

    @Test void boostSurvivesReload() {
        var f = new FusionPhysics(); f.start(); f.boostCooldown(); run(f, ok(14), 10);
        var tag = new CompoundTag(); f.save(tag);
        var g = new FusionPhysics(); g.load(tag);
        assertTrue(g.cooldownBoosted());
        assertEquals(FusionPhysics.State.READY, run(g, ok(14), 50).state());
    }

    @Test void unpaidCooldownOrReadyFallsBackToOff() {
        var f = new FusionPhysics(); f.start();
        run(f, ok(14), 30);
        var r = f.step(with(14, false, 0, true, false, 64, 0));
        assertEquals(FusionPhysics.State.OFF, r.state()); assertFalse(r.disrupted());
        f = new FusionPhysics(); f.setReadyForTest();
        r = f.step(with(14, false, 0, true, false, 64, 0));
        assertEquals(FusionPhysics.State.OFF, r.state()); assertFalse(r.disrupted());
    }

    private static FusionPhysics.Readout refusal(FusionPhysics.Inputs in, double tritium) {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(tritium); f.start();
        return f.step(in);
    }

    @Test void ignitionChecksRefuseWithTheirStatus() {
        var r = refusal(with(14, true, 0, false, false, 64, 0), 4);
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.TORUS_OPEN, r.status());
        r = refusal(with(14, true, 0.06, true, false, 64, 0), 4);
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.NO_VACUUM, r.status());
        r = refusal(with(14, true, 0, true, false, 0, 0), 4);
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.NO_FUEL, r.status());
        r = refusal(ok(14), 0);
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.NO_FUEL, r.status());
        r = refusal(with(14, true, 0, true, false, 64, 0.81), 4);
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.WALL_WORN, r.status());
        r = refusal(with(14, true, 0.05, true, false, 64, 0), 4);
        assertEquals(FusionPhysics.State.IGNITION, r.state());
    }

    @Test void ignitionAt150KwBurnsAfterTenSeconds() {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(4); f.start();
        var r = run(f, ok(14), 9);
        assertEquals(FusionPhysics.State.IGNITION, r.state()); assertEquals(150, r.requestKw(), 1e-9);
        r = f.step(ok(14));
        assertEquals(FusionPhysics.State.BURNING, r.state()); assertEquals(3, f.tritium(), 1e-9);
    }

    @Test void unpaidIgnitionAbortsWithoutDamageOrTritium() {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(4); f.start();
        run(f, ok(14), 4);
        var r = f.step(with(14, false, 0, true, false, 64, 0));
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.ABORTED, r.status());
        assertFalse(r.disrupted()); assertEquals(4, f.tritium(), 1e-9);
    }

    @Test void saveLoadRoundTripKeepsABurn() {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(4); f.start();
        run(f, ok(14), 40);
        assertEquals(FusionPhysics.State.BURNING, f.state());
        var tag = new CompoundTag(); f.save(tag);
        var g = new FusionPhysics(); g.load(tag);
        assertEquals(f.state(), g.state()); assertEquals(f.loopC(), g.loopC(), 1e-9); assertEquals(f.tritium(), g.tritium(), 1e-9);
        var r = g.step(ok(14));
        assertEquals(FusionPhysics.State.BURNING, r.state()); assertFalse(r.disrupted());
    }

    @Test void tankCapsAndExtractsWholeUnits() {
        var f = new FusionPhysics();
        assertEquals(8, f.addTritium(10), 1e-9);
        assertTrue(f.extractTritium()); assertEquals(7, f.tritium(), 1e-9);
        f = new FusionPhysics(); f.addTritium(0.5);
        assertFalse(f.extractTritium()); assertEquals(0.5, f.tritium(), 1e-9);
    }

    private static FusionPhysics.Inputs burn(int radiators, boolean powered, boolean hatchClosed, boolean shellIntact, double pressure,
            int deuterium, boolean pumping, double wear, boolean redstone, double targetKw) {
        return new FusionPhysics.Inputs(sinks(radiators), powered, pressure, hatchClosed, shellIntact, false, deuterium, pumping, wear, redstone, targetKw);
    }
    private static FusionPhysics burning() {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(4); f.start();
        run(f, ok(14), 10);
        assertEquals(FusionPhysics.State.BURNING, f.state());
        return f;
    }
    private static FusionPhysics steady() { var f = burning(); run(f, ok(14), 3000); return f; }
    private static void assertDisrupts(FusionPhysics.Readout r, FusionPhysics.Status status) {
        assertEquals(FusionPhysics.State.OFF, r.state()); assertEquals(status, r.status()); assertTrue(r.disrupted());
    }

    @Test void fourteenRadiatorsCarryAFullBurn() {
        var f = burning();
        var r = run(f, ok(14), 3000);
        assertEquals(FusionPhysics.State.BURNING, r.state());
        assertTrue(r.loopC() > 820 && r.loopC() < 880, "About 848C: " + r.loopC());
        assertTrue(r.netKw() > 315 && r.netKw() < 340, "About 329 kW net: " + r.netKw());
        assertEquals(0, r.requestKw(), 1e-9);
    }

    @Test void tenRadiatorsOverheatPast1100AndDisrupt() {
        var f = burning(); int disruptions = 0; FusionPhysics.Readout r = null;
        for (int i = 0; i < 3000; i++) { r = f.step(ok(10)); if (r.disrupted()) disruptions++; }
        assertEquals(FusionPhysics.State.OFF, r.state()); assertEquals(FusionPhysics.Status.DISRUPTION, r.status());
        assertEquals(1, disruptions);
    }

    @Test void everyHardTriggerDisruptsAndWears() {
        assertDisrupts(steady().step(burn(14, true, true, true, 0, 64, true, 0, true, Double.NaN)), FusionPhysics.Status.DISRUPTION);
        assertDisrupts(steady().step(burn(14, true, false, true, 0, 64, true, 0, false, Double.NaN)), FusionPhysics.Status.DISRUPTION);
        assertDisrupts(steady().step(burn(14, true, true, false, 0, 64, true, 0, false, Double.NaN)), FusionPhysics.Status.DISRUPTION);
        assertDisrupts(steady().step(burn(14, true, true, true, 0.06, 64, true, 0, false, Double.NaN)), FusionPhysics.Status.DISRUPTION);
        var stopped = steady(); stopped.stop();
        assertDisrupts(stopped.step(ok(14)), FusionPhysics.Status.DISRUPTION);
        var starved = steady(); FusionPhysics.Readout r = null;
        for (int i = 0; i < 601 && (r == null || r.state() == FusionPhysics.State.BURNING); i++)
            r = starved.step(burn(14, true, true, true, 0, 0, true, 0, false, Double.NaN));
        assertDisrupts(r, FusionPhysics.Status.DISRUPTION);
    }

    @Test void mitigationHoldsDriveAtFull() {
        var f = burning(); f.setDrive(FusionPhysics.MAX_DRIVE);
        assertEquals(1, f.drive(), 1e-9);
        assertEquals(FusionPhysics.RATED_KW, run(f, ok(14), 3000).heatKw(), 1e-6);
    }

    @Test void enablingMitigationDropsDriveToFull() {
        var f = new FusionPhysics(); f.setMitigation(false); f.setDrive(1.5);
        assertEquals(1.5, f.drive(), 1e-9);
        f.setMitigation(true);
        assertEquals(1, f.drive(), 1e-9);
    }

    @Test void overdriveRunsHotterAndConvertsMore() {
        var full = run(burning(), ok(20), 3000);
        var f = burning(); f.setMitigation(false); f.setDrive(1.5);
        var r = run(f, ok(20), 3000);
        assertEquals(FusionPhysics.State.BURNING, r.state());
        assertEquals(1.5 * FusionPhysics.RATED_KW, r.heatKw(), 1e-6);
        assertTrue(r.loopC() > full.loopC() && r.loopC() < 1000, "About 900C: " + r.loopC());
        assertTrue(r.grossKw() / r.removedKw() > full.grossKw() / full.removedKw(), "A hotter loop converts more");
        assertTrue(r.netKw() > 500, "About 518 kW net: " + r.netKw());
    }

    @Test void overdriveWearsTheWallEveryFiveMinutesAtFullOverdrive() {
        var f = burning(); f.setMitigation(false); f.setDrive(1.5);
        run(f, ok(20), 100);
        int worn = 0;
        for (int i = 0; i < 1200; i++) if (f.step(ok(20)).worn()) worn++;
        assertEquals(4, worn);
        var full = burning(); worn = 0;
        for (int i = 0; i < 1200; i++) if (full.step(ok(20)).worn()) worn++;
        assertEquals(0, worn, "Rated drive adds no wear");
    }

    @Test void unmitigatedOverTemperatureMeltsTheWall() {
        for (double drive : new double[] {1, 1.5}) {
            var f = burning(); f.setMitigation(false); f.setDrive(drive); int disruptions = 0; FusionPhysics.Readout r = null;
            for (int i = 0; i < 3000; i++) { r = f.step(ok(drive > 1 ? 14 : 10)); if (r.disrupted()) disruptions++; }
            assertEquals(FusionPhysics.State.OFF, r.state()); assertEquals(FusionPhysics.Status.WALL_MELT, r.status());
            assertEquals(1, disruptions);
        }
    }

    @Test void saveLoadKeepsDriveAndMitigation() {
        var f = new FusionPhysics(); f.setMitigation(false); f.setDrive(1.3);
        var tag = new CompoundTag(); f.save(tag);
        var g = new FusionPhysics(); g.load(tag);
        assertFalse(g.mitigation()); assertEquals(1.3, g.drive(), 1e-9);
        var old = new FusionPhysics(); old.load(new CompoundTag());
        assertTrue(old.mitigation(), "A save from before the setting keeps mitigation on"); assertEquals(1, old.drive(), 1e-9);
    }

    @Test void unpaidHouseLoadShortfallQuenches() {
        var f = burning(); f.setLoopC(ReactorPhysics.DEEP_VACUUM_C);
        var r = f.step(ok(14));
        assertTrue(r.netKw() < 0, "A cold loop cannot carry the house load: " + r.netKw());
        assertEquals(-r.netKw(), r.requestKw(), 1e-9);
        assertDisrupts(f.step(burn(14, false, true, true, 0, 64, true, 0, false, Double.NaN)), FusionPhysics.Status.MAGNET_QUENCH);
    }

    @Test void paidShortfallKeepsBurning() {
        var f = burning(); f.setLoopC(ReactorPhysics.DEEP_VACUUM_C);
        var r = run(f, ok(14), 30);
        assertEquals(FusionPhysics.State.BURNING, r.state());
    }

    @Test void stoppedAshPumpFadesToAshPoisonedWithoutWear() {
        var f = steady(); double full = f.step(ok(14)).heatKw();
        var unpumped = burn(14, true, true, true, 0, 64, false, 0, false, Double.NaN);
        var half = run(f, unpumped, 30);
        assertEquals(full / 2, half.heatKw(), full * 0.1);
        var r = run(f, unpumped, 29);
        assertEquals(FusionPhysics.State.BURNING, r.state());
        r = f.step(unpumped);
        assertEquals(FusionPhysics.State.OFF, r.state()); assertEquals(FusionPhysics.Status.ASH_POISONED, r.status());
        assertFalse(r.disrupted());
    }

    @Test void breedingKeepsTheTankAndCapsAtEight() {
        var f = steady(); double before = f.tritium();
        run(f, ok(14), 1200);
        assertTrue(f.tritium() >= before, "Breeding at 1.05 sustains the tank: " + before + " -> " + f.tritium());
        run(f, ok(14), 200000);
        assertEquals(8, f.tritium(), 1e-9);
    }

    @Test void deuteriumBurnsOneCanisterPerTenMinutesAtFullOutput() {
        var f = steady(); int used = 0;
        for (int i = 0; i < 1200; i++) used += f.step(ok(14)).deuteriumUsed();
        assertEquals(2, used);
    }

    @Test void wearDeratesGrossOutput() {
        var f = steady(); var tag = new CompoundTag(); f.save(tag);
        var g = new FusionPhysics(); g.load(tag);
        double clean = f.step(ok(14)).grossKw();
        double worn = g.step(burn(14, true, true, true, 0, 64, true, 0.5, false, Double.NaN)).grossKw();
        assertTrue(clean > 300, "Full output first: " + clean);
        assertEquals(clean / 2, worn, clean * 0.01);
    }

    @Test void loadFollowAtZeroDemandStaysLit() {
        var f = burning(); f.setLoadFollow(true);
        var zero = burn(14, true, true, true, 0, 64, true, 0, false, 0);
        FusionPhysics.Readout r = null;
        for (int i = 0; i < 3000; i++) { r = f.step(zero); assertFalse(r.disrupted(), "second " + i); }
        assertEquals(FusionPhysics.State.BURNING, r.state());
        assertTrue(r.netKw() >= -1, "Idles near zero net: " + r.netKw());
    }

    @Test void loadFollowTracksAModestTarget() {
        var f = burning(); f.setLoadFollow(true);
        var r = run(f, burn(14, true, true, true, 0, 64, true, 0, false, 150), 4000);
        assertEquals(150, r.netKw(), 15);
    }

    private static FusionPhysics.Inputs ignitionWith(boolean hatchClosed, double pressure, boolean redstone) {
        return new FusionPhysics.Inputs(sinks(14), true, pressure, hatchClosed, true, false, 64, true, 0, redstone, Double.NaN);
    }
    private static FusionPhysics igniting(double tritium) {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(tritium); f.start();
        assertEquals(FusionPhysics.State.IGNITION, f.step(ok(14)).state());
        return f;
    }

    @Test void extractingTheLastTritiumMidPulseAbortsIgnition() {
        var f = igniting(1);
        run(f, ok(14), 3);
        assertTrue(f.extractTritium());
        var r = run(f, ok(14), 7);
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.NO_FUEL, r.status());
        assertEquals(0, r.tritium(), 1e-9); assertFalse(r.disrupted());
    }

    @Test void blockersDuringThePulseAbortIgnitionWithoutWear() {
        for (var in : List.of(ignitionWith(false, 0, false), ignitionWith(true, 0.2, false), ignitionWith(true, 0, true))) {
            var f = igniting(2);
            run(f, ok(14), 3);
            var r = f.step(in);
            assertEquals(FusionPhysics.State.READY, r.state(), in.toString()); assertFalse(r.disrupted());
            assertEquals(2, f.tritium(), 1e-9, "No tritium is spent on an aborted pulse");
        }
    }

    @Test void heldRedstoneStopRefusesIgnition() {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(2); f.start();
        var r = f.step(ignitionWith(true, 0, true));
        assertEquals(FusionPhysics.State.READY, r.state()); assertEquals(FusionPhysics.Status.ABORTED, r.status());
    }

    /** The SFR-1 alone on its network: a second's shortfall goes unpaid once warm-up is over. */
    private static void assertIdlesAloneAtZeroDemand(List<Double> sinks) {
        var f = burning(); f.setLoadFollow(true);
        FusionPhysics.Readout r = null;
        for (int i = 0; i < 4000; i++) {
            boolean powered = i < 1000 || r.requestKw() == 0;
            r = f.step(new FusionPhysics.Inputs(sinks, powered, 0, true, true, false, 64, true, 0, false, 0));
            assertFalse(r.disrupted(), "second " + i + " net " + r.netKw());
        }
        assertEquals(FusionPhysics.State.BURNING, r.state()); assertEquals(0, r.requestKw(), 1e-9);
    }
    @Test void loadFollowAtZeroDemandCarriesItsOwnHouseLoadWithAWideField() { assertIdlesAloneAtZeroDemand(sinks(40)); }
    @Test void loadFollowAtZeroDemandCarriesItsOwnHouseLoadWithWarmSinks() { assertIdlesAloneAtZeroDemand(Collections.nCopies(14, -30.0)); }

    @Test void aRefusedStartIsFlaggedForOneSecond() {
        var f = new FusionPhysics(); f.setReadyForTest(); f.addTritium(2); f.start();
        f.step(ignitionWith(false, 0, false));
        assertTrue(f.refusedStart());
        f.step(ignitionWith(false, 0, false));
        assertFalse(f.refusedStart());
    }
}
