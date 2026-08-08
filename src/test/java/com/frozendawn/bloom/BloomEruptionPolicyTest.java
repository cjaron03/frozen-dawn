package com.frozendawn.bloom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BloomEruptionPolicyTest {
    @Test
    void transitionsFromRumbleThroughEruption() {
        assertEquals(BloomEruptionPolicy.Stage.WAITING,
                BloomEruptionPolicy.stage(-1L));
        assertEquals(BloomEruptionPolicy.Stage.RUMBLING,
                BloomEruptionPolicy.stage(0L));
        assertEquals(BloomEruptionPolicy.Stage.RUMBLING,
                BloomEruptionPolicy.stage(BloomEruptionPolicy.RUMBLE_TICKS - 1L));
        assertEquals(BloomEruptionPolicy.Stage.ERUPTING,
                BloomEruptionPolicy.stage(BloomEruptionPolicy.RUMBLE_TICKS));
        assertEquals(BloomEruptionPolicy.Stage.COMPLETE,
                BloomEruptionPolicy.stage(BloomEruptionPolicy.COMPLETE_TICKS));
    }

    @Test
    void rumbleProgressIsClamped() {
        assertEquals(0.0F, BloomEruptionPolicy.rumbleProgress(-20L), 0.001F);
        assertEquals(0.5F, BloomEruptionPolicy.rumbleProgress(30L), 0.001F);
        assertEquals(1.0F, BloomEruptionPolicy.rumbleProgress(90L), 0.001F);
    }
}
