package com.frozendawn.defense;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WhipBendTest {
    private static Vec3 yaw(double degrees) {
        double r = Math.toRadians(degrees);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }
    private static double yawOf(Vec3 v) { return Math.toDegrees(Math.atan2(-v.x, v.z)); }

    /** Sweeps at a steady rate from yaw 0 and returns the whip after the last tick. */
    private static WhipBend sweep(double degreesPerTick, int ticks) {
        var whip = new WhipBend();
        for (int t = 0; t <= ticks; t++) whip.step(yaw(t * degreesPerTick));
        return whip;
    }

    @Test
    void aNewBeamStartsStraightAndSilent() {
        var whip = new WhipBend();
        assertFalse(whip.step(yaw(30)));
        assertEquals(0, whip.lag(), 1e-9);
        assertTrue(whip.far().distanceTo(yaw(30)) < 1e-9);
    }

    @Test
    void aSweepBendsTheFarEndBackTowardWhereItCameFrom() {
        var whip = sweep(8, 6);
        assertEquals(48, yawOf(whip.aim()), 1e-6, "The near end follows the aim at once");
        double far = yawOf(whip.far());
        assertTrue(far < 48 && far > 0, "The far end trails the sweep: " + far);
        assertTrue(sweep(12, 6).lag() > sweep(4, 6).lag(), "A faster sweep bends further");
    }

    @Test
    void theBendIsCapped() {
        var whip = sweep(40, 10);
        assertTrue(whip.lag() <= WhipBend.MAX_LAG + 1e-9, "Lag " + Math.toDegrees(whip.lag()));
    }

    @Test
    void stoppingAFastSweepSnapsStraightWithACrack() {
        var whip = sweep(12, 6);
        assertTrue(whip.lag() >= WhipBend.CRACK_LAG);
        assertTrue(whip.step(yaw(72)), "The snap cracks");
        assertEquals(0, whip.lag(), 1e-9, "and the beam is straight again");
    }

    @Test
    void stoppingASlowSweepSnapsWithoutACrack() {
        var whip = sweep(2, 6);
        assertFalse(whip.step(yaw(12)));
        assertEquals(0, whip.lag(), 1e-9);
    }

    @Test
    void aFlickCracksOncePerCooldown() {
        var whip = new WhipBend();
        whip.step(yaw(0));
        assertTrue(whip.step(yaw(WhipBend.FLICK_DEGREES + 1)), "A flick cracks");
        assertFalse(whip.step(yaw(2 * WhipBend.FLICK_DEGREES + 2)), "A second flick at once does not");
        assertFalse(whip.step(yaw(2 * WhipBend.FLICK_DEGREES + 2)), "nor the snap straight after it");
    }

    @Test
    void theCurveRunsFromTheMuzzleToTheTrailingFarEnd() {
        var eye = new Vec3(0, 1.6, 0);
        var muzzle = eye.add(.3, -.25, .4);
        var aim = yaw(0);
        var far = yaw(-20);
        assertTrue(WhipBend.point(eye, muzzle, aim, far, 12, 0).distanceTo(muzzle) < 1e-9, "Starts at the muzzle");
        assertTrue(WhipBend.point(eye, muzzle, aim, far, 12, 1).distanceTo(eye.add(far.scale(12))) < 1e-9,
                "Ends on the trailing far direction at full length");
        var first = WhipBend.point(eye, eye, aim, far, 12, .05).subtract(eye).normalize();
        assertTrue(Math.toDegrees(Math.acos(first.dot(aim))) < 1, "Near the emitter the beam points along the aim");
        var middle = WhipBend.point(eye, eye, aim, far, 12, .5).subtract(eye).normalize();
        double bowed = yawOf(middle);
        assertTrue(bowed < 0 && bowed > -10, "Halfway out it has bent only partly toward the far end: " + bowed);
    }
}
