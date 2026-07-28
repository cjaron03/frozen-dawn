package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartFormationPolicyTest {
    @Test
    void fullStrengthTimelineIsExactlyFiftyThreeSeconds() {
        HeartFormationPolicy.Timeline timeline = HeartFormationPolicy.timeline(1.0F);
        assertEquals(300, timeline.shakeStart());
        assertEquals(460, timeline.gatherStart());
        assertEquals(860, timeline.holdStart());
        assertEquals(1060, timeline.liveStart());
        assertEquals(40, timeline.fragmentCount());
    }

    @Test
    void zeroStrengthKeepsFixedDeadAirAndScalesTheRest() {
        HeartFormationPolicy.Timeline timeline = HeartFormationPolicy.timeline(0.0F);
        assertEquals(300, timeline.shakeStart());
        assertEquals(88, timeline.shakeTicks());
        assertEquals(220, timeline.gatherTicks());
        assertEquals(110, timeline.holdTicks());
        assertEquals(718, timeline.liveStart());
        assertEquals(16, timeline.fragmentCount());
    }

    @Test
    void boundariesDoNotLeakCuesIntoDeadAir() {
        assertEquals(HeartFormationStage.DEAD_AIR,
                HeartFormationPolicy.snapshot(299, 1.0F).stage());
        assertEquals(HeartFormationStage.SHAKE,
                HeartFormationPolicy.snapshot(300, 1.0F).stage());
        assertEquals(HeartFormationStage.GATHER,
                HeartFormationPolicy.snapshot(460, 1.0F).stage());
        assertEquals(HeartFormationStage.HOLD,
                HeartFormationPolicy.snapshot(860, 1.0F).stage());
        assertEquals(HeartFormationStage.LIVE,
                HeartFormationPolicy.snapshot(1060, 1.0F).stage());
    }
}
