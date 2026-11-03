package com.frozendawn.data;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EmergencyEvaAirIntakeTest {
    @Test
    void unstableBoundaryCannotOpenAndUnsafeObservationImmediatelyCloses() {
        var intake = new EmergencyEvaAirIntake(0);
        for (int repetition = 0; repetition < 5; repetition++) {
            for (int tick = 0; tick < 39; tick++) intake.tick(true);
            assertFalse(intake.isOpen());
            intake.tick(false);
        }
        for (int tick = 0; tick < 40; tick++) intake.tick(true);
        assertTrue(intake.isOpen());
        intake = new EmergencyEvaAirIntake(intake.stableTicks());
        assertTrue(intake.isOpen());
        intake.tick(false);
        assertFalse(intake.isOpen(), "Resuming outside cannot retain a saved open intake");
    }
}
