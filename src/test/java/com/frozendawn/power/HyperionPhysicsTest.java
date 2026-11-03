package com.frozendawn.power;

import static com.frozendawn.power.HyperionPhysics.*;
import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class HyperionPhysicsTest {
    private static Inputs in(int cells, int coolers, double surroundingsC, int hot) { return new Inputs(cells, coolers, surroundingsC, hot, 0); }
    private static HyperionPhysics.Readout run(HyperionPhysics p, Inputs in, long stored, int seconds) {
        HyperionPhysics.Readout last = null;
        for (int i = 0; i < seconds; i++) last = p.step(in, stored);
        return last;
    }

    @Test void selfDischargeMatchesTheSpec() {
        assertEquals(2.0, selfDischargePercentPerHour(20), 1e-9);
        assertEquals(0.5, selfDischargePercentPerHour(-45), 1e-9);
        assertEquals(0.1, selfDischargePercentPerHour(-120), 1e-9);
        assertEquals(0, selfDischargePercentPerHour(-180), 1e-9);
        assertEquals(0, selfDischargePercentPerHour(-273), 1e-9);
        assertEquals(2.0 + 1.5 * 20 / 65, selfDischargePercentPerHour(40), 1e-9);
        assertEquals(778, coolerJoules(capacity(7), 20));
    }

    @Test void coolerSizingMatchesTheSpec() {
        assertTrue(heatLoad(32, 20, 0) <= COOLER_CAPACITY, "One cooler holds a 3x3x4 in a warm room");
        assertTrue(heatLoad(67, 20, 0) > COOLER_CAPACITY && heatLoad(67, 20, 0) <= 2 * COOLER_CAPACITY, "A 3x3x8 in a warm room needs two");
        assertTrue(heatLoad(68, 0, 0) <= COOLER_CAPACITY, "One cooler holds any height outdoors below 0C");
        assertTrue(heatLoad(32, 20, 1) > 4 * COOLER_CAPACITY, "A running heat source overwhelms a normal cooler count");
    }

    @Test void heatQuenchTakesTheWarningTime() {
        double[][] cases = {{-10, 0, 900}, {20, 0, 300}, {10, 0, 600}, {-10, 1, 90}};
        for (var c : cases) {
            var p = new HyperionPhysics(); var input = in(275, 1, c[0], (int) c[1]); int n = (int) c[2];
            for (int i = 1; i < n; i++) assertFalse(p.step(input, 1_000_000_000L).quenched(), "No quench before " + n + " at " + c[0] + "C, step " + i);
            assertTrue(p.step(input, 1_000_000_000L).quenched(), "Quench on step " + n + " at " + c[0] + "C");
        }
    }

    @Test void stagesFollowTheLadder() {
        var p = new HyperionPhysics(); var input = in(275, 1, 20, 0);
        var first = p.step(input, 1_000_000_000L);
        assertEquals(Stage.CHIRP, first.stage());
        double before = first.secondsToQuench(); boolean warned = false, finals = false;
        for (int i = 0; i < 298; i++) {
            var r = p.step(input, 1_000_000_000L);
            if (r.stage() == Stage.WARNING && !warned) { warned = true; assertTrue(r.secondsToQuench() <= 100 + 1e-6 && before > 100 + 1e-6, "Warning at a third left"); }
            if (r.stage() == Stage.FINAL && !finals) { finals = true; assertTrue(r.secondsToQuench() <= FINAL_S + 1e-6 && before > FINAL_S + 1e-6, "Final seconds"); }
            before = r.secondsToQuench();
        }
        assertTrue(warned && finals, "Ladder reached warning and final");
    }

    @Test void hotWarningStillLastsItsFloor() {
        // A third of the hot warning time is only 5 s before the final seconds; the alarm gets MIN_WARNING_S instead.
        var p = new HyperionPhysics(); var input = in(275, 1, -10, 1); int warning = 0;
        for (int i = 0; i < WARN_HOT_S; i++) if (p.step(input, 1_000_000_000L).stage() == Stage.WARNING) warning++;
        assertEquals(MIN_WARNING_S, warning, "Seconds of warning beside a heat source");
    }

    @Test void recoveryPlaysAllClearOnce() {
        var p = new HyperionPhysics(); int clears = 0;
        for (int i = 0; i < 100; i++) if (p.step(in(67, 1, 20, 0), 1_000_000_000L).allClear()) clears++;
        double last = p.cellC();
        HyperionPhysics.Readout r = null;
        for (int i = 0; i < 20; i++) {
            r = p.step(in(67, 2, 20, 0), 1_000_000_000L);
            if (r.allClear()) clears++;
            assertEquals(Math.max(OPERATING_C, last - RECOVER_C_PER_S), r.cellC(), 1e-9, "Cools 1C per second");
            last = r.cellC();
        }
        assertEquals(1, clears);
        assertEquals(Stage.COLD, r.stage());
        assertEquals(OPERATING_C, r.cellC(), 1e-9);
    }

    @Test void emptyBatteryWarmsWithoutQuench() {
        var p = new HyperionPhysics(); HyperionPhysics.Readout r = null;
        for (int i = 0; i < 300; i++) { r = p.step(in(7, 1, 20, 0), 0); assertFalse(r.quenched()); }
        assertEquals(Stage.WARM, r.stage());
    }

    @Test void unpaidCoolerCountsAsOverload() {
        var r = new HyperionPhysics().step(in(7, 4, 20, 0), 100);
        assertEquals(Stage.CHIRP, r.stage());
        assertEquals(100, r.coolerJoules());
    }

    @Test void drainingToEmptyNeverQuenches() {
        var p = new HyperionPhysics(); long stored = 5_000; HyperionPhysics.Readout r = null;
        for (int i = 0; i < 600; i++) { r = p.step(in(7, 1, 20, 0), stored); stored -= r.coolerJoules(); assertFalse(r.quenched(), "Step " + i); }
        assertEquals(0, stored);
        assertEquals(Stage.WARM, r.stage());
    }

    @Test void quenchThenCoolDown() {
        var p = new HyperionPhysics(); var warm = in(7, 0, 20, 0);
        assertTrue(run(p, warm, 1_000_000L, 300).quenched());
        assertEquals(20, p.cellC(), 1e-9);
        int steps = 0; HyperionPhysics.Readout r;
        do { r = p.step(new Inputs(7, 0, 20, 0, cooldownWatts(7)), 0); steps++; } while (r.stage() == Stage.WARM && steps < 100);
        assertTrue(steps <= 55, "Full grant cools in under a minute: " + steps);

        var half = new HyperionPhysics(); run(half, warm, 1_000_000L, 300);
        half.step(new Inputs(7, 0, 20, 0, cooldownWatts(7) / 2), 0);
        assertEquals(18, half.cellC(), 1e-9);

        var free = new HyperionPhysics(); run(free, warm, 1_000_000L, 300);
        free.step(new Inputs(7, 0, -250, 0, 0), 0);
        assertEquals(16, free.cellC(), 1e-9);
    }

    @Test void nbtRoundTripKeepsWarning() {
        var p = new HyperionPhysics(); var input = in(275, 1, 20, 0);
        HyperionPhysics.Readout r;
        do r = p.step(input, 1_000_000_000L); while (r.stage() != Stage.WARNING);
        p.step(input, 1_000_000_000L);
        var tag = new CompoundTag(); p.save(tag);
        var copy = new HyperionPhysics(); copy.load(tag);
        assertEquals(p.cellC(), copy.cellC(), 1e-12);
        var a = p.step(input, 1_000_000_000L); var b = copy.step(input, 1_000_000_000L);
        assertEquals(a, b);
        assertEquals(Stage.WARNING, b.stage());
        assertTrue(b.secondsInStage() > 0);
    }
}
