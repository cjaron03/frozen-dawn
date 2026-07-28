package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartEchoPolicyTest {
    @Test
    void echoBeginsAtMemoryFailureWithoutRequiringTerminalLoad() {
        assertFalse(HeartEchoPolicy.canSpawn(49.99F, true, 0));
        assertTrue(HeartEchoPolicy.canSpawn(50.0F, true, 0));
        assertFalse(HeartEchoPolicy.canSpawn(100.0F, false, 0));
        assertFalse(HeartEchoPolicy.canSpawn(100.0F, true, -1));
    }

    @Test
    void survivingArchiveProvidesMoreFrequentEchoes() {
        assertTrue(HeartEchoPolicy.respawnCooldownTicks(1.0F, 0)
                < HeartEchoPolicy.respawnCooldownTicks(0.0F, 0));
        assertTrue(HeartEchoPolicy.respawnCooldownTicks(1.0F, 4)
                < HeartEchoPolicy.respawnCooldownTicks(1.0F, 0));
    }

    @Test
    void violenceFloorAccumulatesButIsBounded() {
        assertEquals(5.0F, HeartEchoPolicy.nextViolenceFloor(0.0F));
        assertEquals(30.0F, HeartEchoPolicy.nextViolenceFloor(29.0F));
        assertEquals(30.0F, HeartEchoPolicy.nextViolenceFloor(30.0F));
    }
}
