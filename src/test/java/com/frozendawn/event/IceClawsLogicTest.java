package com.frozendawn.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IceClawsLogicTest {

    @Test
    void keepsNormalClimbSpeedAtBaseMovement() {
        assertEquals(0.2D, IceClawsLogic.getClimbVelocity(0.1D), 1.0E-6D);
    }

    @Test
    void slowsClimbVelocityWhenMovementSpeedDrops() {
        assertTrue(IceClawsLogic.getClimbVelocity(0.04D) < IceClawsLogic.getClimbVelocity(0.1D));
    }

    @Test
    void clampsToMinimumUsableClimbSpeed() {
        assertEquals(0.07D, IceClawsLogic.getClimbVelocity(0.0D), 1.0E-6D);
    }
}
