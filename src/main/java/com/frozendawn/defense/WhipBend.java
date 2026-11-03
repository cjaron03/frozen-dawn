package com.frozendawn.defense;

import net.minecraft.world.phys.Vec3;

/**
 * How the beam looks, never what it cuts: the near end follows the aim at once and the far end trails it, so a fast
 * sweep bends the beam into a whip that snaps straight, with a crack, when the sweep stops. Cutting follows
 * {@link BeamSweep}. One tick of aim per {@link #step}.
 */
public final class WhipBend {
    /** The share of the far end's lag it closes each tick: a steady sweep trails by about one and a half ticks. */
    public static final double FOLLOW = 0.4;
    public static final double MAX_LAG = Math.toRadians(35);
    /** Below this turn in a tick the sweep has stopped and the beam snaps straight. */
    public static final double STILL = Math.toRadians(1.5);
    /** A snap from at least this much bend cracks. */
    public static final double CRACK_LAG = Math.toRadians(10);
    /** A turn this fast in one tick cracks on its own. */
    public static final double FLICK_DEGREES = 25;
    public static final int CRACK_COOLDOWN = 6;
    private Vec3 aim, far;
    private int sinceCrack = CRACK_COOLDOWN;

    /** Moves the near end to this tick's aim and lets the far end follow; true when the whip cracks. */
    public boolean step(Vec3 aim) {
        var to = aim.normalize();
        if (this.aim == null) { this.aim = far = to; return false; }
        double turn = angle(this.aim, to);
        this.aim = to;
        sinceCrack++;
        boolean ready = sinceCrack >= CRACK_COOLDOWN;
        if (turn < STILL) {
            boolean crack = ready && angle(far, to) >= CRACK_LAG;
            far = to;
            return crack(crack);
        }
        far = slerp(far, to, FOLLOW);
        double lag = angle(far, to);
        if (lag > MAX_LAG) far = slerp(to, far, MAX_LAG / lag);
        return crack(ready && turn >= Math.toRadians(FLICK_DEGREES));
    }

    private boolean crack(boolean crack) {
        if (crack) sinceCrack = 0;
        return crack;
    }

    public Vec3 aim() { return aim; }
    public Vec3 far() { return far; }
    /** The angle between the near end's aim and the far end, in radians. */
    public double lag() { return aim == null ? 0 : angle(far, aim); }

    /**
     * A point {@code t} of the way along the beam, 0 at the muzzle and 1 at its far end. The direction turns from the
     * aim toward the far end with the square of {@code t}, so the bend gathers toward the tip; the muzzle's offset
     * from the eye fades out along the length.
     */
    public static Vec3 point(Vec3 eye, Vec3 muzzle, Vec3 aim, Vec3 far, double length, double t) {
        return eye.add(slerp(aim, far, t * t).scale(length * t)).add(muzzle.subtract(eye).scale(1 - t));
    }

    static double angle(Vec3 a, Vec3 b) { return Math.acos(Math.clamp(a.dot(b), -1, 1)); }

    static Vec3 slerp(Vec3 a, Vec3 b, double t) {
        double angle = angle(a, b);
        if (angle < 1e-6) return b;
        // Opposite directions have no unique arc; jump rather than pick one.
        if (Math.PI - angle < 1e-3) return t < .5 ? a : b;
        double sin = Math.sin(angle);
        return a.scale(Math.sin((1 - t) * angle) / sin).add(b.scale(Math.sin(t * angle) / sin));
    }
}
