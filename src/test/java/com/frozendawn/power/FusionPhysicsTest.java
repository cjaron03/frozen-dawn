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
}
