package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The Terminus cone: 24 blocks from the eyes along the look, 30 degrees across, round rather than a fan. */
class TerminusConeTest {
    private static final Vec3 EYE = new Vec3(0, 64, 0), LOOK = new Vec3(0, 0, 1);

    private static Vec3 at(double yawDegrees, double pitchDegrees, double distance) {
        double yaw = Math.toRadians(yawDegrees), pitch = Math.toRadians(pitchDegrees);
        return EYE.add(-Math.sin(yaw) * Math.cos(pitch) * distance, -Math.sin(pitch) * distance,
                Math.cos(yaw) * Math.cos(pitch) * distance);
    }

    @Test
    void straightAheadIsInside() {
        assertTrue(TerminusCone.contains(EYE, LOOK, at(0, 0, 6)));
    }

    @Test
    void fifteenDegreesEitherSideIsTheEdge() {
        assertTrue(TerminusCone.contains(EYE, LOOK, at(14, 0, 10)));
        assertTrue(TerminusCone.contains(EYE, LOOK, at(-14, 0, 10)));
        assertFalse(TerminusCone.contains(EYE, LOOK, at(16, 0, 10)));
        assertFalse(TerminusCone.contains(EYE, LOOK, at(-16, 0, 10)));
    }

    @Test
    void theConeIsRoundSoPitchCountsLikeYaw() {
        assertTrue(TerminusCone.contains(EYE, LOOK, at(0, 14, 8)));
        assertFalse(TerminusCone.contains(EYE, LOOK, at(0, -16, 8)));
        assertFalse(TerminusCone.contains(EYE, LOOK, at(12, 12, 8)), "Fourteen degrees each way is seventeen off the axis");
    }

    @Test
    void twentyFourBlocksIsTheReach() {
        assertTrue(TerminusCone.contains(EYE, LOOK, at(0, 0, 24)));
        assertFalse(TerminusCone.contains(EYE, LOOK, at(0, 0, 24.01)));
    }

    @Test
    void nothingBehindOrAtTheEye() {
        assertFalse(TerminusCone.contains(EYE, LOOK, at(180, 0, 4)));
        assertFalse(TerminusCone.contains(EYE, LOOK, EYE));
    }

    @Test
    void theLookNeedNotBeUnitLength() {
        assertTrue(TerminusCone.contains(EYE, LOOK.scale(3), at(10, 0, 6)));
    }
}
