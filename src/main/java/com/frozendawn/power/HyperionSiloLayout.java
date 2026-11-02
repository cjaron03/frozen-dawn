package com.frozendawn.power;

/** Where the silo's rendered-only parts sit, in blocks: the render box, the rings and the pipes. The silo axis is the
 * centre of the 3x3, one block behind the Controller. Pure, so the geometry is tested without a client. */
public final class HyperionSiloLayout {
    /** Blocks from the axis to the render box's sides; covers the rings and the pipes' elbows. */
    public static final double REACH = 3.5;
    /** Below the Controller for the deepest pipe, above the top for the vent's steam and the highest ring's bob. */
    public static final int BELOW = 4, ABOVE = 2;
    private HyperionSiloLayout() {}

    /** {minX, minY, minZ, maxX, maxY, maxZ} for a Controller at (cx, cy, cz) whose front steps (frontX, frontZ). */
    public static double[] renderBox(int cx, int cy, int cz, int frontX, int frontZ, int height) {
        double ax = cx - frontX + 0.5, az = cz - frontZ + 0.5;
        return new double[]{ax - REACH, cy - BELOW, az - REACH, ax + REACH, cy + height + ABOVE, az + REACH};
    }
}
