package com.frozendawn.entity;

import net.minecraft.util.Mth;

/**
 * Shared clock for the Scribe's writing presentation (§9.4b): a glance up at the subject, then three bursts of
 * strokes on the slate. Client-only timing; it carries no evidence and changes no behavior.
 */
public final class ScribeWriting {
    static final int CYCLE = 120, GLANCE = 24, BURST = 22;
    private static final int[] BURSTS = {28, 58, 88};

    private ScribeWriting() { }

    private static float at(float age, int seed) { return Mth.positiveModulo(age + seed * 37, CYCLE); }

    /** 1 while it looks up at the subject, 0 while it looks down at the slate. */
    public static float glance(float age, int seed) {
        float t = at(age, seed);
        return t >= GLANCE ? 0 : smooth(Math.min(t, GLANCE - t) / 6F);
    }

    /** 1 during a burst of strokes, easing in and out. */
    public static float stroke(float age, int seed) {
        float t = at(age, seed);
        for (int start : BURSTS)
            if (t >= start && t < start + BURST) return smooth(Math.min(t - start, start + BURST - t) / 3F);
        return 0;
    }

    /** One scratch per burst, on its first tick. */
    static boolean strokeStarts(int tick, int seed) {
        int t = Math.floorMod(tick + seed * 37, CYCLE);
        for (int start : BURSTS) if (t == start) return true;
        return false;
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
