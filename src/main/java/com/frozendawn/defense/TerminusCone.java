package com.frozendawn.defense;

import net.minecraft.world.phys.Vec3;

/** The Terminus cone: 24 blocks from the eyes along the look, 30 degrees across, round. */
public final class TerminusCone {
    public static final double REACH = 24, HALF_ANGLE_DEGREES = 15;
    private static final double COS_HALF = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));

    public static boolean contains(Vec3 eye, Vec3 look, Vec3 point) {
        var to = point.subtract(eye);
        double distance = to.length();
        if (distance < 1e-6 || distance > REACH) return false;
        return to.dot(look.normalize()) / distance >= COS_HALF;
    }

    private TerminusCone() { }
}
