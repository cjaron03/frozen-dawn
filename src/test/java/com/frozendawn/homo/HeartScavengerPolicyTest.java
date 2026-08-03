package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeartScavengerPolicyTest {
    @Test
    void wavesEscalateWithoutBecomingUnbounded() {
        HeartScavengerPolicy.Profile opening = HeartScavengerPolicy.profile(0, 1.0F);
        HeartScavengerPolicy.Profile first = HeartScavengerPolicy.profile(1, 1.0F);
        HeartScavengerPolicy.Profile third = HeartScavengerPolicy.profile(3, 1.0F);
        HeartScavengerPolicy.Profile fourth = HeartScavengerPolicy.profile(4, 1.0F);

        assertTrue(opening.active());
        assertTrue(first.active());
        assertTrue(fourth.active());
        assertTrue(fourth.waveSize() > first.waveSize());
        assertTrue(fourth.intervalTicks() < first.intervalTicks());
        assertEquals(22, fourth.concurrentCap());
        assertEquals(0.0F, opening.returnedChance());
        assertEquals(0.0F, first.mimicChance());
        assertTrue(third.returnedChance() > 0.0F);
        assertTrue(third.mimicChance() > 0.0F);
        assertTrue(third.architectChance() > 0.0F);
        assertTrue(fourth.swarm());
        assertFalse(HeartScavengerPolicy.profile(5, 1.0F).active());
    }

    @Test
    void higherReturnedFormsStayLockedUntilThirdErasure() {
        HeartScavengerPolicy.Profile early = HeartScavengerPolicy.profile(2, 1.0F);
        HeartScavengerPolicy.Profile late = HeartScavengerPolicy.profile(3, 1.0F);

        for (float roll : new float[] {0.01F, 0.18F, 0.44F, 0.79F, 0.98F}) {
            HeartScavengerPolicy.SpawnKind kind =
                    HeartScavengerPolicy.selectKind(early, roll);
            assertTrue(kind == HeartScavengerPolicy.SpawnKind.FROSTBITTEN
                    || kind == HeartScavengerPolicy.SpawnKind.HOLLOW);
        }
        assertEquals(HeartScavengerPolicy.SpawnKind.ARCHITECT,
                HeartScavengerPolicy.selectKind(late, 0.01F));
        assertEquals(HeartScavengerPolicy.SpawnKind.MIMIC,
                HeartScavengerPolicy.selectKind(late, 0.12F));
        assertEquals(HeartScavengerPolicy.SpawnKind.RETURNED,
                HeartScavengerPolicy.selectKind(late, 0.30F));
    }
}
