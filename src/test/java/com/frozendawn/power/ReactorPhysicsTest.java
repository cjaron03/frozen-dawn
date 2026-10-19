package com.frozendawn.power;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ReactorPhysicsTest {
    private static List<Double> radiators(int count) { return Collections.nCopies(count, ReactorPhysics.DEEP_VACUUM_C); }
    private static ReactorPhysics.Inputs manual(int rods, int radiators) {
        return new ReactorPhysics.Inputs(rods, radiators(radiators), 20, false, Double.NaN);
    }
    private static ReactorPhysics.Readout run(ReactorPhysics core, ReactorPhysics.Inputs in, int seconds) {
        ReactorPhysics.Readout last = null;
        for (int i = 0; i < seconds; i++) last = core.step(in);
        return last;
    }

    @Test void deepVacuumEfficiencyMatchesSpec() {
        assertEquals(0.873, ReactorPhysics.efficiency(300, ReactorPhysics.DEEP_VACUUM_C), 0.002);
        assertEquals(0, ReactorPhysics.efficiency(-250, -200));
        assertTrue(ReactorPhysics.efficiency(300, -50) < ReactorPhysics.efficiency(300, -200), "Warmer outside, less efficient");
    }

    @Test void twelveRadiatorsHoldFourRodsAtFullOutput() {
        var core = new ReactorPhysics(); core.setInsertion(1);
        var out = run(core, manual(4, 12), 3000);
        assertEquals(ReactorPhysics.State.NOMINAL, out.state());
        assertTrue(core.coreC() > 240 && core.coreC() < 300, "Settles below the warning band: " + core.coreC());
        assertTrue(out.powerKw() > 110 && out.powerKw() < 125, "About 120 kW: " + out.powerKw());
        assertEquals(ReactorPhysics.Coolant.FLOWING, out.coolant());
        assertEquals(1, out.burn(), 1e-9);
    }

    @Test void undersizedRadiatorsTripAutomaticScramWithoutCoreDamage() {
        var core = new ReactorPhysics(); core.setInsertion(1);
        double peak = 0;
        for (int i = 0; i < 6000; i++) { core.step(manual(4, 4)); peak = Math.max(peak, core.coreC()); }
        assertTrue(core.scrammed());
        assertTrue(peak < ReactorPhysics.DAMAGE_C, "Auto SCRAM holds the core below damage: " + peak);
        assertEquals(0, core.damage());
        assertTrue(core.coreC() < ReactorPhysics.AUTO_SCRAM_C, "Radiators cool the scrammed core");
    }

    @Test void loadFollowMatchesDemandAndNeverOverheats() {
        var core = new ReactorPhysics(); core.setLoadFollow(true);
        var greedy = run(core, new ReactorPhysics.Inputs(4, radiators(4), 20, false, 1000), 4000);
        assertFalse(core.scrammed());
        assertTrue(core.coreC() <= ReactorPhysics.NOMINAL_C + 5, "Capped at radiator capacity: " + core.coreC());
        assertTrue(greedy.powerKw() > 36 && greedy.powerKw() < 44, "4 radiators give about 40 kW: " + greedy.powerKw());
        var small = run(core, new ReactorPhysics.Inputs(4, radiators(4), 20, false, 20), 4000);
        assertEquals(20, small.powerKw(), 2);
        assertTrue(core.insertion() < 0.5, "Lower demand lowers insertion and stretches rod life");
    }

    @Test void lossOfCoolantWindowIsAboutOneMinute() {
        var core = new ReactorPhysics(); core.setAutoScram(false); core.setInsertion(1); core.setCoreC(ReactorPhysics.LOSS_OF_COOLANT_C);
        int seconds = 0; ReactorPhysics.Readout out;
        do { out = core.step(manual(4, 12)); seconds++; } while (!core.meltdown() && seconds < 1000);
        assertTrue(core.meltdown());
        assertTrue(seconds >= 45 && seconds <= 90, "Run window: " + seconds + " s");
        assertEquals(ReactorPhysics.State.MELTDOWN, core.step(manual(4, 12)).state());
    }

    @Test void excursionCoversTheLastTwentyDegreesInSeconds() {
        var core = new ReactorPhysics(); core.setAutoScram(false); core.setInsertion(1); core.setCoreC(ReactorPhysics.EXCURSION_C);
        int seconds = 0; while (!core.meltdown() && seconds < 60) { core.step(manual(4, 0)); seconds++; }
        assertTrue(core.meltdown() && seconds <= 3, "Prompt excursion: " + seconds + " s");
        var safe = new ReactorPhysics(); safe.setInsertion(1); safe.setCoreC(ReactorPhysics.EXCURSION_C);
        safe.step(manual(4, 0));
        assertTrue(safe.scrammed() && !safe.meltdown(), "With automatic SCRAM on there is no excursion");
    }

    @Test void manualScramAlwaysSavesTheCoreBeforeEightHundred() {
        var core = new ReactorPhysics(); core.setAutoScram(false); core.setInsertion(1); core.setCoreC(ReactorPhysics.LOSS_OF_COOLANT_C);
        var out = run(core, manual(4, 0), 40);
        assertEquals(ReactorPhysics.State.LOSS_OF_COOLANT, out.state());
        assertTrue(out.secondsToFailure() > 0 && Double.isFinite(out.secondsToFailure()));
        core.scram();
        double peak = 0;
        for (int i = 0; i < 3000; i++) { core.step(manual(4, 0)); peak = Math.max(peak, core.coreC()); }
        assertFalse(core.meltdown());
        assertTrue(peak < ReactorPhysics.MELTDOWN_C, "Peak " + peak);
        assertTrue(core.coreC() < ReactorPhysics.LOSS_OF_COOLANT_C, "Casing loss beats decay heat");
        assertTrue(core.damage() > 0, "Time above 600C is permanent");
    }

    @Test void damageDeratesPowerButNeverSlowsARunaway() {
        var healthy = new ReactorPhysics(); healthy.setInsertion(1);
        var damaged = new ReactorPhysics(); damaged.setInsertion(1);
        var tag = new CompoundTag(); damaged.save(tag); tag.putDouble("Damage", 0.5); damaged.load(tag);
        var a = run(healthy, manual(4, 12), 3000); var b = run(damaged, manual(4, 12), 3000);
        assertEquals(a.heatInKw(), b.heatInKw(), 1e-9, "Fission heat is unchanged by damage");
        assertEquals(a.powerKw() * 0.5, b.powerKw(), 0.5, "Half damage halves electrical output");
        damaged.setAutoScram(false); damaged.setCoreC(ReactorPhysics.LOSS_OF_COOLANT_C);
        int seconds = 0; while (!damaged.meltdown() && seconds < 1000) { damaged.step(manual(4, 0)); seconds++; }
        assertTrue(seconds >= 45 && seconds <= 90, "Damaged core keeps the one-minute window: " + seconds);
        assertTrue(damaged.damage() <= ReactorPhysics.MAX_DAMAGE);
    }

    @Test void decayHeatFadesFromSevenToOnePercentOverADay() {
        var core = new ReactorPhysics(); core.setInsertion(1);
        run(core, manual(4, 12), 600);
        core.scram();
        assertEquals(140 * 0.07, core.step(manual(4, 12)).heatInKw(), 0.2);
        var day = run(core, manual(4, 12), 1199);
        assertEquals(140 * 0.01, day.heatInKw(), 0.15);
    }

    @Test void coldCoreFreezesCoolantAndNeedsAWarmRoomToRestart() {
        var core = new ReactorPhysics(); core.setCoreC(-50); core.setInsertion(1); core.scram();
        var cold = new ReactorPhysics.Inputs(4, radiators(4), -150, false, Double.NaN);
        run(core, cold, ReactorPhysics.FREEZE_SECONDS + 5);
        assertTrue(core.frozen());
        assertFalse(core.reset(false) && core.step(cold).heatInKw() > 0.5, "No fission while frozen");
        core.setInsertion(1);
        var stillFrozen = core.step(cold);
        assertEquals(ReactorPhysics.Coolant.FROZEN, stillFrozen.coolant());
        assertEquals(0, stillFrozen.radiatorKw());
        run(core, new ReactorPhysics.Inputs(4, radiators(4), 20, false, Double.NaN), ReactorPhysics.THAW_SECONDS + 1);
        assertFalse(core.frozen());
    }

    @Test void idleCoreStopsPumpingAndSettlesToItsRoom() {
        var core = new ReactorPhysics();
        var idle = run(core, new ReactorPhysics.Inputs(4, radiators(12), 15, false, Double.NaN), 30000);
        assertEquals(ReactorPhysics.Coolant.IDLE, idle.coolant());
        assertEquals(15, core.coreC(), 0.5);
        assertFalse(core.frozen(), "A warm reactor room keeps an idle core from freezing");
    }

    @Test void flowControlHoldsARunningCoreHotWithOversizedRadiators() {
        var core = new ReactorPhysics(); core.setLoadFollow(true); core.setCoreC(-20);
        var small = new ReactorPhysics.Inputs(4, radiators(12), -100, false, 20);
        double coldest = Double.MAX_VALUE; ReactorPhysics.Readout out = null;
        for (int i = 0; i < 6000; i++) { out = core.step(small); if (i > 600) coldest = Math.min(coldest, core.coreC()); }
        assertTrue(coldest >= ReactorPhysics.HOLD_LOW_C - 10, "Running core stays in its working band: " + coldest);
        assertEquals(20, out.powerKw(), 2.5);
        assertTrue(out.efficiency() > 0.85, "Hot core keeps its efficiency: " + out.efficiency());
        assertFalse(core.frozen());
        var cold = new ReactorPhysics(); cold.setInsertion(0.2); cold.setCoreC(20);
        assertEquals(ReactorPhysics.Coolant.WARMING, cold.step(manual(4, 12)).coolant(), "Below the band the pumps hold while it warms");
    }

    @Test void redstoneTripsAndBlocksReset() {
        var core = new ReactorPhysics(); core.setInsertion(0.5);
        core.step(new ReactorPhysics.Inputs(4, radiators(12), 20, true, Double.NaN));
        assertTrue(core.scrammed());
        assertFalse(core.reset(true), "Reset refused while the trip signal is present");
        assertTrue(core.reset(false));
        assertEquals(0, core.insertion(), "Reset restarts from fully withdrawn fuel");
        core.setCoreC(460); core.scram();
        assertFalse(core.reset(false), "Reset refused at the automatic trip point");
        assertTrue(core.rodsLocked());
        core.setCoreC(400);
        assertTrue(core.reset(false), "Below the trip point a hot core can restart");
        core.scram(); core.setCoreC(600); core.setAutoScram(false);
        assertTrue(core.reset(false), "With automatic SCRAM disabled, restarting hot is the operator's call");
    }

    @Test void stateRoundTripsAndOldSavesKeepAutoScram() {
        var core = new ReactorPhysics(); core.setInsertion(0.4); core.setLoadFollow(true); core.setAutoScram(false); core.setCoreC(123);
        var tag = new CompoundTag(); core.save(tag);
        var copy = new ReactorPhysics(); copy.load(tag);
        assertEquals(0.4, copy.insertion(), 1e-9); assertEquals(123, copy.coreC(), 1e-9);
        assertTrue(copy.loadFollow()); assertFalse(copy.autoScram());
        var fresh = new ReactorPhysics(); fresh.load(new CompoundTag());
        assertTrue(fresh.autoScram()); assertEquals(20, fresh.coreC());
    }

    @Test void spinningReserveCyclesAndPersists() {
        var core = new ReactorPhysics();
        assertEquals(0, core.reserveKw());
        int[] seen = new int[ReactorPhysics.RESERVE_STEPS_KW.length];
        for (int i = 0; i < seen.length; i++) { core.cycleReserve(); seen[i] = core.reserveKw(); }
        assertArrayEquals(new int[] {10, 20, 40, 60, 0}, seen, "Reserve steps wrap back to zero");
        core.cycleReserve(); core.cycleReserve();
        var tag = new CompoundTag(); core.save(tag);
        var restored = new ReactorPhysics(); restored.load(tag);
        assertEquals(20, restored.reserveKw(), "Reserve survives save and load");
        tag.putInt("ReserveKw", 37); restored.load(tag);
        assertEquals(0, restored.reserveKw(), "An unknown reserve value loads as zero");
    }
}
