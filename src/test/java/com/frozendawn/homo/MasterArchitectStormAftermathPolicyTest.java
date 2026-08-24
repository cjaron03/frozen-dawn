package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MasterArchitectStormAftermathPolicyTest {

    @Test
    void fullCongregationUsesTheCompleteFiveStageTimeline() {
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(1.0F);

        assertEquals(20, timeline.coreEndTick());
        assertEquals(100, timeline.eyeEndTick());
        assertEquals(400, timeline.ruptureEndTick());
        assertEquals(500, timeline.collapseEndTick());
        assertEquals(560, timeline.completeTick());
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.CORE,
                MasterArchitectStormAftermathPolicy.stage(19, 1.0F));
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.EYE,
                MasterArchitectStormAftermathPolicy.stage(20, 1.0F));
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.RUPTURE,
                MasterArchitectStormAftermathPolicy.stage(100, 1.0F));
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.BASE_COLLAPSE,
                MasterArchitectStormAftermathPolicy.stage(400, 1.0F));
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.STILLNESS,
                MasterArchitectStormAftermathPolicy.stage(500, 1.0F));
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.COMPLETE,
                MasterArchitectStormAftermathPolicy.stage(560, 1.0F));
    }

    @Test
    void emptyCongregationFaceFadesButWeatherKeepsFullStrength() {
        MasterArchitectStormAftermathPolicy.Timeline timeline =
                MasterArchitectStormAftermathPolicy.timeline(0.0F);

        assertEquals(200, timeline.collapseEndTick());
        assertEquals(260, timeline.completeTick());
        assertEquals(0, MasterArchitectStormAftermathPolicy.detachedChunkCount(0.0F));
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.FADE,
                MasterArchitectStormAftermathPolicy.stage(20, 0.0F));
        assertEquals(MasterArchitectStormAftermathPolicy.Stage.STILLNESS,
                MasterArchitectStormAftermathPolicy.stage(200, 0.0F));
        assertEquals(1.0F,
                MasterArchitectStormAftermathPolicy.spectacleStrength(0.0F), 0.0001F);
    }
}
