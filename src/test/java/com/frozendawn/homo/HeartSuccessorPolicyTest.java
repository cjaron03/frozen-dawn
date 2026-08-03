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

    @Test
    void injuredSupportInterruptsConductingUntilStable() {
        assertTrue(HeartSuccessorPolicy.needsEmergencyHealing(7.0F, 20.0F));
        assertFalse(HeartSuccessorPolicy.needsEmergencyHealing(7.1F, 20.0F));
        assertTrue(HeartSuccessorPolicy.shouldContinueHealing(14.3F, 20.0F));
        assertFalse(HeartSuccessorPolicy.shouldContinueHealing(14.5F, 20.0F));
        assertEquals(3, HeartSuccessorPolicy.MAX_SUPPORT_LINKS);
    }

    @Test
    void supportTetherReducesIncomingDamageByOneQuarter() {
        assertEquals(15.0F,
                HeartSuccessorPolicy.mitigateSupportedDamage(20.0F), 0.0001F);
        assertEquals(0.0F,
                HeartSuccessorPolicy.mitigateSupportedDamage(-4.0F), 0.0001F);
    }
}
