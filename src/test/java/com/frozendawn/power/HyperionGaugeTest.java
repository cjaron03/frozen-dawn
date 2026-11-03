package com.frozendawn.power;

import static com.frozendawn.power.HyperionGauge.*;
import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.power.HyperionPhysics.Stage;
import org.junit.jupiter.api.Test;

/** One test per row of the silo design's state table. */
class HyperionGaugeTest {
    private static final long CAP = 1_200_000_000L, RATE = 500_000;
    private static State gauge(Stage stage, long stored, long in, long out, int height, boolean scarred, long sinceQuench) {
        return of(new Inputs(stage, true, stored, CAP, in, out, RATE, height, scarred, sinceQuench));
    }
    private static State cold(long stored, long in, long out) { return gauge(Stage.COLD, stored, in, out, 6, false, Long.MAX_VALUE); }

    @Test void coldAndFullShimmers() {
        var s = cold(CAP, 0, 0);
        assertEquals(6, s.bars()); assertEquals(6.0, s.lit(), 1e-9); assertEquals(Colour.BLUE, s.colour());
        assertEquals(Rings.FLOATING, s.rings()); assertEquals(Pipes.FROSTED, s.pipes()); assertEquals(Lamps.BLUE, s.lamps());
        assertEquals(Vent.BREATH, s.vent()); assertEquals(Motion.CALM, s.motion()); assertEquals(0.5, s.spin(), 1e-9);
        assertEquals(Flow.NONE, s.flow()); assertEquals(-1, s.breathing()); assertEquals(-1, s.flickering()); assertEquals(-1, s.collapseShown());
        assertFalse(s.ember());
    }

    @Test void chargingBreathesTheNextBar() {
        var s = cold(CAP / 2, 250_000, 0);
        assertEquals(3, s.breathing()); assertEquals(Flow.IN, s.flow()); assertEquals(2.25, s.spin(), 1e-9); assertEquals(-1, s.flickering());
        assertEquals(-1, cold(CAP, 250_000, 0).breathing(), "A full silo has no next bar");
        assertEquals(-1, gauge(Stage.CHIRP, CAP / 2, 250_000, 0, 6, false, Long.MAX_VALUE).breathing(), "Only a cold silo breathes");
    }

    @Test void dischargingFlickersTheTopBar() {
        var s = cold(CAP * 5 / 12, 0, 100_000);
        assertEquals(2.5, s.lit(), 1e-9); assertEquals(2, s.flickering()); assertEquals(Flow.OUT, s.flow()); assertEquals(-1, s.breathing());
        assertEquals(-1, cold(0, 0, 100_000).flickering(), "Nothing left to flicker");
    }

    @Test void overheatingIsAmber() {
        for (var stage : new Stage[]{Stage.CHIRP, Stage.WARNING}) {
            var s = gauge(stage, CAP / 2, 0, 0, 6, false, Long.MAX_VALUE);
            assertEquals(Colour.AMBER, s.colour()); assertEquals(Pipes.DRIPPING, s.pipes()); assertEquals(Lamps.AMBER, s.lamps());
            assertEquals(Vent.WISPS, s.vent()); assertEquals(Motion.WOBBLE, s.motion()); assertEquals(Rings.FLOATING, s.rings());
        }
    }

    @Test void finalSecondsAreRed() {
        var s = gauge(Stage.FINAL, CAP / 2, 0, 0, 6, false, Long.MAX_VALUE);
        assertEquals(Colour.RED, s.colour()); assertEquals(Motion.SHUDDER, s.motion()); assertEquals(Vent.HEAVY_WISPS, s.vent());
        assertEquals(Lamps.RED, s.lamps()); assertEquals(Pipes.DRIPPING, s.pipes());
    }

    @Test void quenchCollapsesTopDown() {
        var s = gauge(Stage.WARM, 0, 0, 0, 8, true, 6);
        assertEquals(6, s.collapseShown()); assertEquals(Colour.RED, s.colour()); assertEquals(Rings.RESTING, s.rings());
        var after = gauge(Stage.WARM, 0, 0, 0, 8, true, 24);
        assertEquals(-1, after.collapseShown()); assertEquals(Colour.DEAD, after.colour()); assertTrue(after.ember());
        assertEquals(Rings.RESTING, after.rings()); assertEquals(Pipes.BARE, after.pipes()); assertEquals(Lamps.OFF, after.lamps()); assertEquals(Vent.NONE, after.vent());
    }

    @Test void freshWarmBuildIsDark() {
        var s = gauge(Stage.WARM, 0, 0, 0, 6, false, Long.MAX_VALUE);
        assertEquals(Colour.OFF, s.colour()); assertEquals(Pipes.BARE, s.pipes()); assertEquals(Rings.RESTING, s.rings());
        assertEquals(Vent.NONE, s.vent()); assertEquals(Lamps.OFF, s.lamps()); assertFalse(s.ember());
    }

    @Test void unformedIsOff() {
        var s = of(new Inputs(Stage.COLD, false, CAP / 2, CAP, 250_000, 0, RATE, 0, false, Long.MAX_VALUE));
        assertEquals(Colour.OFF, s.colour()); assertEquals(Rings.RESTING, s.rings()); assertEquals(0, s.bars());
        assertEquals(Pipes.BARE, s.pipes()); assertEquals(Lamps.OFF, s.lamps()); assertEquals(Vent.NONE, s.vent()); assertEquals(Flow.NONE, s.flow());
    }
}
