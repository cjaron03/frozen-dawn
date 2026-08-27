package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartCollapsePolicyTest {
    @Test
    void stageBoundariesAreStable() {
        assertEquals(HeartCollapseStage.RUPTURE,
                HeartCollapsePolicy.snapshot(79L, 1.0F).stage());
        assertEquals(HeartCollapseStage.FALL,
                HeartCollapsePolicy.snapshot(80L, 1.0F).stage());
        assertEquals(HeartCollapseStage.SETTLE,
                HeartCollapsePolicy.snapshot(220L, 1.0F).stage());
        assertEquals(HeartCollapseStage.DORMANT,
                HeartCollapsePolicy.snapshot(320L, 1.0F).stage());
    }

    @Test
    void fieldStrengthOnlyScalesVisualFragmentCount() {
        assertEquals(10, HeartCollapsePolicy.timeline(0.0F).fragmentCount());
        assertEquals(17, HeartCollapsePolicy.timeline(0.5F).fragmentCount());
        assertEquals(24, HeartCollapsePolicy.timeline(1.0F).fragmentCount());
        assertEquals(HeartCollapsePolicy.DORMANT_START,
                HeartCollapsePolicy.elapsedAtStageStart(HeartCollapseStage.DORMANT));
    }

    @Test
    void maeveAssemblesOnlyDuringSettleAndPersistsAfterward() {
        assertEquals(0.0F, HeartCollapsePolicy.maeveFormationProgress(
                HeartCollapseStage.FALL, 1.0F));
        assertEquals(0.5F, HeartCollapsePolicy.maeveFormationProgress(
                HeartCollapseStage.SETTLE, 0.5F));
        assertEquals(1.0F, HeartCollapsePolicy.maeveFormationProgress(
                HeartCollapseStage.DORMANT, 1.0F));
    }
}
