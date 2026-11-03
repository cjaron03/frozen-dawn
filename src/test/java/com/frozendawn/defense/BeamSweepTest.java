package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class BeamSweepTest {
    private static Vec3 yaw(double degrees) {
        double r = Math.toRadians(degrees);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    @Test
    void aStillAimIsOneRay() {
        var rays = BeamSweep.rays(yaw(10), yaw(10), 12);
        assertEquals(1, rays.size());
        assertTrue(rays.getFirst().distanceTo(yaw(10)) < 1e-9);
    }

    @Test
    void noPreviousAimIsOneRay() {
        var rays = BeamSweep.rays(null, yaw(10), 12);
        assertEquals(1, rays.size());
    }

    @Test
    void aSweepLeavesNoGapWiderThanAQuarterBlockAtFullReach() {
        var rays = BeamSweep.rays(yaw(-20), yaw(20), 12);
        assertTrue(rays.getFirst().distanceTo(yaw(-20)) < 1e-9, "Starts on last tick's aim");
        assertTrue(rays.getLast().distanceTo(yaw(20)) < 1e-9, "Ends on this tick's aim");
        for (int i = 1; i < rays.size(); i++) {
            assertEquals(1, rays.get(i).length(), 1e-9, "Every ray is a unit direction");
            assertTrue(rays.get(i).scale(12).distanceTo(rays.get(i - 1).scale(12)) <= BeamSweep.SPACING + 1e-9,
                    "Adjacent rays at 12 blocks stay within the spacing");
        }
    }

    @Test
    void aWildFlickIsCappedButStillSpansTheWholeArc() {
        var rays = BeamSweep.rays(yaw(-80), yaw(80), 12);
        assertEquals(BeamSweep.MAX_RAYS, rays.size());
        assertTrue(rays.getFirst().distanceTo(yaw(-80)) < 1e-9);
        assertTrue(rays.getLast().distanceTo(yaw(80)) < 1e-9);
    }

    @Test
    void anAboutFaceDoesNotInventAnArc() {
        var rays = BeamSweep.rays(yaw(0), yaw(180), 12);
        assertEquals(1, rays.size(), "Opposite aims have no unique arc between them");
        assertTrue(rays.getFirst().distanceTo(yaw(180)) < 1e-9);
    }
}
