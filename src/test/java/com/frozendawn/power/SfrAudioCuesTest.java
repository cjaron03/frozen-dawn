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
        assertEquals(EnumSet.of(Cue.READY), SfrAudioCues.cues(r(COOLDOWN, FusionPhysics.Status.COOLDOWN), r(READY, FusionPhysics.Status.READY)));
    }
    @Test void refusedStartAndAbortBuzz() {
        assertEquals(EnumSet.of(Cue.REFUSE), SfrAudioCues.cues(r(READY, FusionPhysics.Status.READY), r(READY, FusionPhysics.Status.TORUS_OPEN)));
        assertEquals(EnumSet.of(Cue.REFUSE), SfrAudioCues.cues(r(IGNITION, FusionPhysics.Status.IGNITION), r(READY, FusionPhysics.Status.ABORTED)));
    }
    @Test void aStandingRefusalBuzzesOnce() {
        assertEquals(EnumSet.noneOf(Cue.class), SfrAudioCues.cues(r(READY, FusionPhysics.Status.TORUS_OPEN), r(READY, FusionPhysics.Status.TORUS_OPEN)));
    }
    @Test void disruptionCracksButQuenchVents() {
        assertEquals(EnumSet.of(Cue.DISRUPTION), SfrAudioCues.cues(r(BURNING, FusionPhysics.Status.BURNING, 1050, 300, false), r(OFF, FusionPhysics.Status.DISRUPTION, 1050, 0, true)));
        assertEquals(EnumSet.of(Cue.QUENCH), SfrAudioCues.cues(r(BURNING, FusionPhysics.Status.BURNING, 900, 300, false), r(OFF, FusionPhysics.Status.MAGNET_QUENCH, 900, 0, true)));
    }
    @Test void ashPoisonedCracksWithoutWear() {
        assertEquals(EnumSet.of(Cue.DISRUPTION), SfrAudioCues.cues(r(BURNING, FusionPhysics.Status.BURNING), r(OFF, FusionPhysics.Status.ASH_POISONED, 600, 0, false)));
    }
    @Test void steadyBurnIsSilent() {
        var burn = r(BURNING, FusionPhysics.Status.BURNING, 848, 329, false);
        assertEquals(EnumSet.noneOf(Cue.class), SfrAudioCues.cues(burn, burn));
        assertFalse(SfrAudioCues.alarm(burn, true));
    }
    @Test void firstSecondAfterLoadIsSilent() {
        assertEquals(EnumSet.noneOf(Cue.class), SfrAudioCues.cues(null, r(READY, FusionPhysics.Status.ABORTED)));
    }
    @Test void alarmAboveAThousandOrShortfall() {
        assertTrue(SfrAudioCues.alarm(r(BURNING, FusionPhysics.Status.BURNING, 1000, 300, false), true));
        assertTrue(SfrAudioCues.alarm(r(BURNING, FusionPhysics.Status.BURNING, 900, -10, false), true));
        assertTrue(SfrAudioCues.alarm(r(BURNING, FusionPhysics.Status.BURNING, 900, 300, false), false));
        assertFalse(SfrAudioCues.alarm(r(OFF, FusionPhysics.Status.DISRUPTION, 1050, 0, false), true));
    }
}
