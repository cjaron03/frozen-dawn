package com.frozendawn.power;

import static com.frozendawn.power.FusionPhysics.State.*;
import static com.frozendawn.power.SfrAudioCues.Cue;
import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class SfrAudioCuesTest {
    private static FusionPhysics.Readout r(FusionPhysics.State state, FusionPhysics.Status status, double loopC, double netKw, boolean disrupted) {
        return new FusionPhysics.Readout(state, status, loopC, 0, 0, 0, netKw, 0, 0, 0, 0, disrupted);
    }
    private static FusionPhysics.Readout r(FusionPhysics.State state, FusionPhysics.Status status) { return r(state, status, 20, 0, false); }

    @Test void cooldownToReadyChimes() {
        assertEquals(EnumSet.of(Cue.READY), SfrAudioCues.cues(r(COOLDOWN, FusionPhysics.Status.COOLDOWN), r(READY, FusionPhysics.Status.READY), false));
    }
    @Test void refusedStartAndAbortBuzz() {
        assertEquals(EnumSet.of(Cue.REFUSE), SfrAudioCues.cues(r(READY, FusionPhysics.Status.TORUS_OPEN), r(READY, FusionPhysics.Status.TORUS_OPEN), true));
        assertEquals(EnumSet.of(Cue.REFUSE), SfrAudioCues.cues(r(IGNITION, FusionPhysics.Status.IGNITION), r(READY, FusionPhysics.Status.ABORTED), false));
    }
    @Test void aRefusalNobodyPressedIsSilent() {
        assertEquals(EnumSet.noneOf(Cue.class), SfrAudioCues.cues(r(READY, FusionPhysics.Status.READY), r(READY, FusionPhysics.Status.TORUS_OPEN), false));
    }
    @Test void disruptionCracksButQuenchVents() {
        assertEquals(EnumSet.of(Cue.DISRUPTION), SfrAudioCues.cues(r(BURNING, FusionPhysics.Status.BURNING, 1050, 300, false), r(OFF, FusionPhysics.Status.DISRUPTION, 1050, 0, true), false));
        assertEquals(EnumSet.of(Cue.QUENCH), SfrAudioCues.cues(r(BURNING, FusionPhysics.Status.BURNING, 900, 300, false), r(OFF, FusionPhysics.Status.MAGNET_QUENCH, 900, 0, true), false));
    }
    @Test void ashPoisonedCracksWithoutWear() {
        assertEquals(EnumSet.of(Cue.DISRUPTION), SfrAudioCues.cues(r(BURNING, FusionPhysics.Status.BURNING), r(OFF, FusionPhysics.Status.ASH_POISONED, 600, 0, false), false));
    }
    @Test void steadyBurnIsSilent() {
        var burn = r(BURNING, FusionPhysics.Status.BURNING, 848, 329, false);
        assertEquals(EnumSet.noneOf(Cue.class), SfrAudioCues.cues(burn, burn, false));
        assertFalse(SfrAudioCues.alarm(burn, true));
    }
    @Test void firstSecondAfterLoadIsSilent() {
        assertEquals(EnumSet.noneOf(Cue.class), SfrAudioCues.cues(null, r(READY, FusionPhysics.Status.ABORTED), false));
    }
    @Test void ignitionIntoBurnCelebrates() {
        assertEquals(EnumSet.of(Cue.IGNITED), SfrAudioCues.cues(r(IGNITION, FusionPhysics.Status.IGNITION), r(BURNING, FusionPhysics.Status.BURNING, 20, -45, false), false));
    }
    @Test void alarmAboveAThousandOrPumpOff() {
        assertTrue(SfrAudioCues.alarm(r(BURNING, FusionPhysics.Status.BURNING, 1000, 300, false), true));
        // Warm-up runs on network power and an uncovered shortfall quenches the same second, so a negative net never warns.
        assertFalse(SfrAudioCues.alarm(r(BURNING, FusionPhysics.Status.BURNING, 238, -15.3, false), true));
        assertTrue(SfrAudioCues.alarm(r(BURNING, FusionPhysics.Status.BURNING, 900, 300, false), false));
        assertFalse(SfrAudioCues.alarm(r(OFF, FusionPhysics.Status.DISRUPTION, 1050, 0, false), true));
    }
}
