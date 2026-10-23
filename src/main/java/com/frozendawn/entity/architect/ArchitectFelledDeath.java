package com.frozendawn.entity.architect;

import net.minecraft.util.Mth;

/**
 * Timeline for the normal Architect's felled-beam death, cut to the beats of
 * {@code entity/architect/death.ogg}: the crack at 0 s, the groan, debris from 1 s
 * and the slab thud at 2.45 s.
 */
public final class ArchitectFelledDeath {

    /** Body snaps rigid on the crack. */
    public static final int JOLT_END = 4;
    /** Arms lock stiff at its sides. */
    public static final int ARMS_LOCK = 9;
    /** Knees give way. */
    public static final int KNEES_BUCKLE = 17;
    public static final int TOPPLE_START = 22;
    /** Its placed ice starts coming down with the debris in the sound. */
    public static final int ICE_COLLAPSE_START = 20;
    /** Body hits the ground on the slab thud. */
    public static final int IMPACT_TICK = 49;
    public static final int TICKS = 53;

    private ArchitectFelledDeath() {
    }

    /** 1 at the peak of the crack, easing back to 0 by {@link #JOLT_END}. */
    public static float jolt(float elapsed) {
        if (elapsed >= JOLT_END) return 0.0F;
        return Mth.sin(Mth.clamp(elapsed / JOLT_END, 0.0F, 1.0F) * Mth.PI);
    }

    /** Groan tremor amplitude, 0 outside the groan. */
    public static float tremor(float elapsed) {
        if (elapsed < JOLT_END || elapsed > TOPPLE_START + 8) return 0.0F;
        return Mth.sin((elapsed - JOLT_END) / (TOPPLE_START + 8 - JOLT_END) * Mth.PI);
    }

    public static float armsLocked(float elapsed) {
        return smoothStep(elapsed, ARMS_LOCK, ARMS_LOCK + 2);
    }

    public static float buckle(float elapsed) {
        return smoothStep(elapsed, KNEES_BUCKLE, KNEES_BUCKLE + 4);
    }

    /** 0 to 1 over the fall, accelerating like a felled beam. */
    public static float topple(float elapsed) {
        float t = Mth.clamp((elapsed - TOPPLE_START) / (IMPACT_TICK - TOPPLE_START), 0.0F, 1.0F);
        return t * t * t;
    }

    /** Forward lean in degrees, including a small bounce on impact. */
    public static float fallDegrees(float elapsed) {
        float lean = 6.0F * buckle(elapsed);
        float degrees = lean + (90.0F - lean) * topple(elapsed);
        if (elapsed > IMPACT_TICK) {
            float bounce = Mth.clamp((elapsed - IMPACT_TICK) / 3.0F, 0.0F, 1.0F);
            degrees -= 5.0F * Mth.sin(bounce * Mth.PI);
        }
        return degrees;
    }

    /** Body opacity: solid until just after impact, then gone as it breaks apart. */
    public static float alpha(float elapsed) {
        return 1.0F - Mth.clamp((elapsed - IMPACT_TICK - 1) / (TICKS - IMPACT_TICK - 1), 0.0F, 1.0F);
    }

    private static float smoothStep(float elapsed, float start, float end) {
        float t = Mth.clamp((elapsed - start) / (end - start), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
