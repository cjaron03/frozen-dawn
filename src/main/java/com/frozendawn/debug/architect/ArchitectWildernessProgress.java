package com.frozendawn.debug.architect;

import net.minecraft.world.phys.Vec3;

/** Target locomotion evidence: jumping against an obstacle is not route progress. */
final class ArchitectWildernessProgress {
    private Vec3 anchor;
    private long stalled;

    void reset(Vec3 position) { anchor = position; stalled = 0; }

    long update(Vec3 position) {
        // Accumulate real displacement, rather than accepting collision jitter each tick.
        if (anchor == null || horizontalDistance(anchor, position) >= 0.25) reset(position);
        else stalled++;
        return stalled;
    }

    static double horizontalDistance(Vec3 from, Vec3 to) {
        return Math.hypot(to.x - from.x, to.z - from.z);
    }
}
