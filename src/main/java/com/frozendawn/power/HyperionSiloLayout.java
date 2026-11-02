package com.frozendawn.power;

/** Where the silo's rendered-only parts sit, in blocks: the render box, the rings and the pipes. The silo axis is the
 * centre of the 3x3, one block behind the Controller. Pure, so the geometry is tested without a client. */
public final class HyperionSiloLayout {
    /** Blocks from the axis to the render box's sides; covers the rings and the pipes' elbows. */
    public static final double REACH = 3.5;
    /** Below the Controller for the deepest pipe, above the top for the vent's steam and the highest ring's bob. */
    public static final int BELOW = 4, ABOVE = 2;
    /** Each ring: segments round a circle of inner radius RING_INNER_PX about the axis, RING_WIDTH_PX deep and RING_TALL_PX tall. */
    public static final int RING_SEGMENTS = 32, RING_INNER_PX = 36, RING_WIDTH_PX = 2, RING_TALL_PX = 2;
    private HyperionSiloLayout() {}

    /** One ring per two blocks of height. */
    public static int ringCount(int height) { return height / 2; }
    /** Ring k of n, floating: blocks above the silo floor, evenly spread up its height. */
    public static double ringFloatY(int k, int n, int height) { return (k + 1) * height / (n + 1.0); }
    /** Ring k resting: stacked on the base, the lowest 2 px up. */
    public static double ringRestY(int k) { return (2 + 2 * k) / 16.0; }

    /** Each cooler's pipe: 6 px square, centred PIPE_Y above the cooler's floor, out to an elbow whose outer edge is PIPE_OUT_PX
     * from the face (inside the resting rings), then down. */
    public static final int PIPE_PX = 6, PIPE_OUT_PX = 10, MAX_PIPE_AIR = 2;
    public static final double PIPE_Y = 5 / 16.0;
    /** How far the pipe runs down from the elbow, given the air blocks under the elbow before the first solid one (-1 for
     * none): flush with that floor, or, with no floor near enough to keep the pipe within 3 blocks, one block to a flange. */
    public static double pipeDrop(int solidBelow) { return solidBelow < 0 || solidBelow > MAX_PIPE_AIR ? 1.0 : solidBelow + PIPE_Y; }
    /** How far a pipe runs out from its face, given how far its centre sits to the side of the axis (0 at a side's centre,
     * 16 at a corner): the full PIPE_OUT_PX, shortened until its outer corner stays a pixel inside the resting rings. */
    public static int pipeOutPx(int lateralPx) {
        int out = PIPE_OUT_PX;
        while (out > 0 && Math.hypot(24 + out, lateralPx + PIPE_PX / 2.0) >= RING_INNER_PX - 1) out--;
        return out;
    }

    /** Depth between the flat layers drawn over a face (window, rungs, bars, glow): enough that each holds its depth at 48
     * blocks with a 24-bit depth buffer, and six together still under half a pixel. */
    public static final double OVERLAY_STEP = 0.005;
    /** Every eighth ring segment glows brighter, so a ring's turn can be seen. */
    public static boolean ringMarker(int segment) { return segment % 8 == 0; }

    /** {minX, minY, minZ, maxX, maxY, maxZ} for a Controller at (cx, cy, cz) whose front steps (frontX, frontZ). */
    public static double[] renderBox(int cx, int cy, int cz, int frontX, int frontZ, int height) {
        double ax = cx - frontX + 0.5, az = cz - frontZ + 0.5;
        return new double[]{ax - REACH, cy - BELOW, az - REACH, ax + REACH, cy + height + ABOVE, az + REACH};
    }
}
