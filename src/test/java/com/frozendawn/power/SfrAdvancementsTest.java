package com.frozendawn.power;

import static com.frozendawn.power.SfrAdvancements.*;
import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.power.FusionPhysics.Readout;
import com.frozendawn.power.FusionPhysics.State;
import com.frozendawn.power.FusionPhysics.Status;
import org.junit.jupiter.api.Test;

class SfrAdvancementsTest {
    private static Readout r(State state, Status status, double netKw) {
        return new Readout(state, status, 900, 0, 0, 0, netKw, 0, 0, 0, 0, false, false);
    }
    private static final Readout IGNITING = r(State.IGNITION, Status.IGNITION, -150), LIT = r(State.BURNING, Status.BURNING, -150);
    private static Context quiet() { return new Context(Double.NaN, 0, false, 0); }
    private static Context roomAt(double c) { return new Context(c, 0, false, 0); }

    @Test void ignitionGrantsSecondSunAndColdFusionOnlyBelowMinus50() {
        assertEquals(java.util.List.of(SECOND_SUN, COLD_FUSION), earned(IGNITING, LIT, roomAt(-50.5)));
        assertEquals(java.util.List.of(SECOND_SUN), earned(IGNITING, LIT, roomAt(-50)));
        assertEquals(java.util.List.of(SECOND_SUN), earned(IGNITING, LIT, quiet()), "An unknown room temperature is not cold");
        assertTrue(earned(null, LIT, roomAt(-80)).isEmpty(), "The first second after a load is not an ignition");
        assertTrue(earned(LIT, LIT, roomAt(-80)).isEmpty(), "Only the second the plasma lights counts");
    }

    @Test void anIgnitionThatEndsWithoutBurningIsTwentyYearsAway() {
        assertEquals(java.util.List.of(TWENTY_YEARS), earned(IGNITING, r(State.READY, Status.ABORTED, -20), quiet()));
        assertEquals(java.util.List.of(TWENTY_YEARS), earned(IGNITING, r(State.READY, Status.NO_VACUUM, -20), quiet()));
        assertEquals(java.util.List.of(TWENTY_YEARS), earned(IGNITING, r(State.OFF, Status.IDLE, 0), quiet()), "Stop during the pulse");
        assertTrue(earned(IGNITING, IGNITING, quiet()).isEmpty());
        assertTrue(earned(r(State.READY, Status.READY, -20), r(State.READY, Status.ABORTED, -20), quiet()).isEmpty());
    }

    @Test void breakevenNeedsALitPlasmaWithPositiveNet() {
        assertEquals(java.util.List.of(BREAKEVEN), earned(LIT, r(State.BURNING, Status.BURNING, 0.1), quiet()));
        assertEquals(java.util.List.of(BREAKEVEN), earned(LIT, r(State.RAMP_DOWN, Status.RAMP_DOWN, 3), quiet()));
        assertTrue(earned(LIT, r(State.BURNING, Status.BURNING, 0), quiet()).isEmpty());
        assertTrue(earned(LIT, r(State.OFF, Status.DISRUPTION, 5), quiet()).isEmpty(), "A disrupted plasma exports nothing");
    }

    @Test void lawsonCriterionTakesAFullDayOfUnbrokenBurn() {
        assertEquals(1200, LAWSON_SECONDS, "One in-game day");
        assertTrue(earned(LIT, LIT, new Context(Double.NaN, LAWSON_SECONDS - 1, false, 0)).isEmpty());
        assertEquals(java.util.List.of(LAWSON), earned(LIT, LIT, new Context(Double.NaN, LAWSON_SECONDS, false, 0)));
        assertEquals(1, burnSeconds(0, LIT));
        assertEquals(LAWSON_SECONDS, burnSeconds(LAWSON_SECONDS - 1, r(State.RAMP_DOWN, Status.RAMP_DOWN, 0)));
        assertEquals(0, burnSeconds(LAWSON_SECONDS - 1, r(State.OFF, Status.DISRUPTION, 0)), "A disruption breaks the run");
        assertEquals(0, burnSeconds(500, r(State.OFF, Status.SHUT_DOWN, 0)), "So does a clean shutdown");
    }

    @Test void fissionFusionFireNeedsALitPlasmaOnAMixedNetwork() {
        assertEquals(java.util.List.of(FISSION_FUSION_FIRE), earned(LIT, LIT, new Context(Double.NaN, 0, true, 0)));
        assertTrue(earned(IGNITING, IGNITING, new Context(Double.NaN, 0, true, 0)).isEmpty());
    }

    @Test void massFusionNeeds100kWActuallyExported() {
        assertEquals(java.util.List.of(MASS_FUSION), earned(LIT, LIT, new Context(Double.NaN, 0, false, MASS_FUSION_KW)));
        assertTrue(earned(LIT, LIT, new Context(Double.NaN, 0, false, 99.9)).isEmpty());
        assertTrue(earned(LIT, r(State.OFF, Status.DISRUPTION, 0), new Context(Double.NaN, 0, false, 150)).isEmpty(),
                "Last second's export does not count once the plasma is gone");
    }

    @Test void exportIsTheReactorsShareOfWhatTheNetworkAbsorbed() {
        assertEquals(120_000, exportedWatts(200_000, 200_000, 120_000), "Alone, it exports what the loads and banks took");
        assertEquals(100_000, exportedWatts(200_000, 250_000, 125_000), "Shared pro rata with other generators");
        assertEquals(0, exportedWatts(0, 50_000, 50_000));
        assertEquals(0, exportedWatts(200_000, 0, 0));
        assertEquals(200_000, exportedWatts(200_000, 200_000, 300_000), "Battery discharge is not the reactor's export");
    }
}
