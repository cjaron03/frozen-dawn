package com.frozendawn.power;

import static com.frozendawn.power.IcarusAdvancements.*;
import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.power.IcarusPhysics.Readout;
import com.frozendawn.power.IcarusPhysics.State;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcarusAdvancementsTest {
    private static Readout r(State state, double grossMw) {
        return new Readout(state, IcarusPhysics.Status.OK, 5, grossMw, 0, grossMw, 0, 20, 100, 0, 0, 0, 0, 0,
                0, 0, 0, 0, IcarusPhysics.Alarm.NONE, IcarusPhysics.Cascade.STABLE, false);
    }
    private static final Readout PRIMING = r(State.PRIMING, 0), IGNITING = r(State.IGNITION, 0);

    @Test void ignitionEarnsEmc2() {
        assertEquals(List.of(E_EQUALS_MC2), earned(PRIMING, IGNITING, false));
        assertTrue(earned(null, IGNITING, false).isEmpty(), "The first second after a load is not an ignition");
        assertTrue(earned(IGNITING, IGNITING, false).isEmpty(), "Only the second the core lights counts");
    }

    @Test void fullOutputEarnsTooClose() {
        assertEquals(List.of(TOO_CLOSE), earned(r(State.RAMP, 2.4), r(State.RUNNING, 2.5), false));
        assertEquals(List.of(TOO_CLOSE), earned(r(State.RAMP, 2.4), r(State.RUNNING, 2.4999999), false), "Rounding is not short of full");
        assertTrue(earned(r(State.RUNNING, 2.0), r(State.RUNNING, 2.45), false).isEmpty());
        assertTrue(earned(r(State.RUNNING, 2.5), r(State.RUNNING, 2.5), false).isEmpty(), "Granted once on reaching it");
    }

    @Test void clipEarnsClippedWings() {
        assertEquals(List.of(CLIPPED_WINGS), earned(r(State.RUNNING, 1.5), r(State.CLIPPED, 1.5), false));
        assertTrue(earned(r(State.CLIPPED, 1.5), r(State.CLIPPED, 1.5), false).isEmpty());
        assertTrue(earned(null, r(State.CLIPPED, 1.5), false).isEmpty(), "A load into a clip is not a clip");
    }

    @Test void coleRestartEarnsWakeMe() {
        assertEquals(List.of(E_EQUALS_MC2, WAKE_ME), earned(PRIMING, IGNITING, true));
        assertTrue(earned(r(State.COLE_DISCHARGE, 0), r(State.COLD, 0), true).isEmpty(), "Finishing the Cole Protocol is not the restart");
    }
}
