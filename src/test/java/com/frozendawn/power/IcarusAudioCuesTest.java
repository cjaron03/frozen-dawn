package com.frozendawn.power;

import static com.frozendawn.power.IcarusAudioCues.Cue;
import static com.frozendawn.power.IcarusPhysics.State.*;
import static org.junit.jupiter.api.Assertions.*;

import com.frozendawn.power.IcarusPhysics.Alarm;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class IcarusAudioCuesTest {
    private static final double INF = Double.POSITIVE_INFINITY;
    private static IcarusPhysics.Readout r(IcarusPhysics.State state, Alarm alarm, long injected) {
        return new IcarusPhysics.Readout(state, IcarusPhysics.Status.OK, 5, 0, 0, 0, 0, 20, 50, 0, 0, injected, 0, 0,
                INF, INF, INF, INF, alarm, IcarusPhysics.Cascade.STABLE, false);
    }
    private static IcarusPhysics.Readout r(IcarusPhysics.State state) { return r(state, Alarm.NONE, 0); }
    private static EnumSet<Cue> none() { return EnumSet.noneOf(Cue.class); }

    @Test void cuesFollowTransitions() {
        assertEquals(EnumSet.of(Cue.FRAME_HUM_ON), IcarusAudioCues.cues(r(COLD), r(POWER_CHECK)));
        assertEquals(EnumSet.of(Cue.STABILIZER_SPINUP), IcarusAudioCues.cues(r(RESERVE_CHARGE), r(STABILIZER_SPINUP)));
        assertEquals(EnumSet.of(Cue.SHIELD_FORM), IcarusAudioCues.cues(r(STABILIZER_SPINUP), r(COMMISSIONING)));
        assertEquals(EnumSet.of(Cue.CAPSULE_DOCK), IcarusAudioCues.cues(r(INJECTION), r(INJECTION, Alarm.NONE, 2 * IcarusPhysics.CAPSULE)));
        assertEquals(EnumSet.of(Cue.INJECTOR_FEED), IcarusAudioCues.cues(r(INJECTION), r(PRIMING)));
        assertEquals(EnumSet.of(Cue.IGNITION), IcarusAudioCues.cues(r(PRIMING), r(IGNITION)));
        assertEquals(EnumSet.of(Cue.RUNNING_CHIME), IcarusAudioCues.cues(r(RAMP), r(RUNNING)));
        assertEquals(EnumSet.of(Cue.CLIP_SLAM), IcarusAudioCues.cues(r(RUNNING), r(CLIPPED)));
        assertEquals(EnumSet.of(Cue.COLE_COMPLETE), IcarusAudioCues.cues(r(COLE_DISCHARGE), r(COLD)));
        assertEquals(EnumSet.of(Cue.WIND_DOWN), IcarusAudioCues.cues(r(RUNNING), r(COLE_RAMP_DOWN)));
        assertEquals(EnumSet.of(Cue.WIND_DOWN), IcarusAudioCues.cues(r(CLIPPED), r(COLE_RAMP_DOWN)));
        assertEquals(EnumSet.of(Cue.CORE_COLLAPSE), IcarusAudioCues.cues(r(COLE_RECOVER), r(COLE_DISCHARGE)));
        assertEquals(EnumSet.of(Cue.ALARM_WARNING), IcarusAudioCues.cues(r(RUNNING, Alarm.NONE, 0), r(RUNNING, Alarm.WARNING, 0)));
    }
    @Test void steadyStatesAndFallingAlarmsAreSilent() {
        assertEquals(none(), IcarusAudioCues.cues(r(RUNNING), r(RUNNING)));
        assertEquals(none(), IcarusAudioCues.cues(r(STABILIZER_SPINUP), r(STABILIZER_SPINUP)));
        assertEquals(none(), IcarusAudioCues.cues(r(RUNNING, Alarm.CRITICAL, 0), r(RUNNING, Alarm.CAUTION, 0)));
        // Aborting the spin-up is not the shield forming.
        assertEquals(none(), IcarusAudioCues.cues(r(STABILIZER_SPINUP), r(COLD)));
        assertEquals(none(), IcarusAudioCues.cues(r(COLE_RAMP_DOWN), r(COLE_RAMP_DOWN)));
        assertEquals(none(), IcarusAudioCues.cues(r(COLE_DISCHARGE), r(COLE_DISCHARGE)));
    }
    @Test void firstSecondAfterLoadIsSilent() {
        assertEquals(none(), IcarusAudioCues.cues(null, r(RUNNING, Alarm.CRITICAL, 0)));
    }
    @Test void aCriticalRiseSoundsOnlyTheCriticalTier() {
        assertEquals(EnumSet.of(Cue.ALARM_CRITICAL), IcarusAudioCues.cues(r(RUNNING, Alarm.CAUTION, 0), r(RUNNING, Alarm.CRITICAL, 0)));
    }

    @Test void suitSpeaksEachRiseAndRepeatsCriticalEveryMinute() {
        assertEquals(Alarm.CAUTION, IcarusAudioCues.suitLine(r(RUNNING), r(RUNNING, Alarm.CAUTION, 0), 0));
        assertEquals(Alarm.CRITICAL, IcarusAudioCues.suitLine(r(RUNNING, Alarm.CAUTION, 0), r(RUNNING, Alarm.CRITICAL, 0), 0));
        var critical = r(RUNNING, Alarm.CRITICAL, 0);
        assertNull(IcarusAudioCues.suitLine(critical, critical, 30));
        assertEquals(Alarm.CRITICAL, IcarusAudioCues.suitLine(critical, critical, 60));
        assertEquals(Alarm.CRITICAL, IcarusAudioCues.suitLine(critical, critical, 120));
        assertNull(IcarusAudioCues.suitLine(critical, r(RUNNING, Alarm.WARNING, 0), 60));
        assertNull(IcarusAudioCues.suitLine(r(RUNNING, Alarm.WARNING, 0), r(RUNNING, Alarm.WARNING, 0), 60));
        assertNull(IcarusAudioCues.suitLine(null, critical, 60));
    }
}
