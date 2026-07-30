package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartSuccessorPolicyTest {
    @Test
    void successorBindsOnlyToTheRemainingLateNodes() {
        int threeDestroyed = 0b00111;
        int fourDestroyed = 0b01111;

        assertTrue(HeartSuccessorPolicy.shouldExist(threeDestroyed));
        assertEquals(3, HeartSuccessorPolicy.boundNode(threeDestroyed));
        assertEquals(0, HeartSuccessorPolicy.generationForNode(3));
        assertEquals(4, HeartSuccessorPolicy.boundNode(fourDestroyed));
        assertEquals(1, HeartSuccessorPolicy.generationForNode(4));
        assertFalse(HeartSuccessorPolicy.shouldExist(0b11111));
    }

    @Test
    void conductingAndHealingNeverOverlap() {
        assertEquals(HeartSuccessorPolicy.Mode.CONDUCTING,
                HeartSuccessorPolicy.mode(0));
        assertEquals(HeartSuccessorPolicy.Mode.HEALING,
                HeartSuccessorPolicy.mode(HeartSuccessorPolicy.CONDUCT_TICKS));
    }
}
