package com.frozendawn.defense;

/**
 * How the Lash's Seared effect builds while the beam holds on a target. Each cut adds a little burn time;
 * the effect ticks one away between cuts, so contact nets two ticks a tick. A deeper burn is a higher level,
 * and a fading burn keeps its level when cut again. Heat, not fire: it needs no air.
 */
public final class Sear {
    /** A single cut sears for two seconds. */
    public static final int BASE_TICKS = 40;
    public static final int ADD_PER_HIT = 3;
    /** Five seconds: the longest a burn outlasts the beam. */
    public static final int MAX_TICKS = 100;
    /** About half a second of contact. */
    public static final int SEARED_II_AT = 55;
    /** About a second of contact. */
    public static final int SEARED_III_AT = 75;
    /** Per level, once a second. */
    public static final float PULSE_DAMAGE = 1F;
    public static final double SLOW_PER_LEVEL = .15;
    private Sear() {}

    /** The burn time after one more cut on a target with {@code held} ticks left. */
    public static int duration(int held) { return held <= 0 ? BASE_TICKS : Math.min(MAX_TICKS, held + ADD_PER_HIT); }

    /** The level for that burn time, never below the level the target already holds ({@code -1} for none). */
    public static int amplifier(int duration, int held) {
        return Math.max(held, duration >= SEARED_III_AT ? 2 : duration >= SEARED_II_AT ? 1 : 0);
    }
}
