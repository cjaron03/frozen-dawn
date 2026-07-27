package com.frozendawn.homo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThaevenTransmissionTypeTest {

    @Test
    void assessmentSelectsTheExpectedFirstContactFragment() {
        assertEquals(ThaevenTransmissionType.VEL_THAE,
                ThaevenTransmissionType.fromAssessment(false));
        assertEquals(ThaevenTransmissionType.ORSHA_RECOGNITION,
                ThaevenTransmissionType.fromAssessment(true));
    }

    @Test
    void networkIdsRoundTripAndCompletionAllowsOnlySmallTimingTolerance() {
        for (ThaevenTransmissionType type : ThaevenTransmissionType.values()) {
            assertEquals(type, ThaevenTransmissionType.fromNetworkId(type.networkId()));
            assertTrue(type.minimumCompletionTicks() > 0);
            assertTrue(type.minimumCompletionTicks() <= type.durationTicks());
            assertTrue(type.durationTicks() - type.minimumCompletionTicks() <= 20);
        }
        assertEquals(ThaevenTransmissionType.HEARTH_MYTH,
                ThaevenTransmissionType.fromNetworkId(2));
    }
}
