package com.frozendawn.entity.architect;

import net.minecraft.util.Mth;

/**
 * Timeline for the Blind Architect's wound-down death, cut to
 * {@code entity/blind_architect/death.ogg}: nine throat clicks that slow like a clock
 * running out, the last one catching at 2.07 s, then a slack exhale from 2.22 s.
 * It sags while the clicks slow, crumples straight down on the exhale, its hood slips
 * off, and the body sheds away as black dust.
 */
public final class ArchitectWoundDownDeath {

    /** Ticks of each click in the death sound. */
    private static final float[] CLICK_TICKS = {1.0F, 3.2F, 5.9F, 9.2F, 13.2F, 18.0F, 24.0F, 31.2F, 40.1F};
    /** The exhale: its legs give and it drops where it stands. */
    public static final int EXHALE_TICK = 44;
    public static final int HOOD_SLIP_START = 52;
    public static final int HOOD_SLIP_END = 61;
    /** Dust starts shedding off the slumped body. */
    public static final int SHED_START = 56;
    public static final int FADE_START = 62;
    public static final int FADE_END = 84;
    public static final int TICKS = 92;

    private ArchitectWoundDownDeath() {
    }

    /**
     * Head twitch toward each click: a quick jerk that settles within three ticks, weaker as it
     * winds down. Signed so successive clicks turn it to alternate sides, still listening.
     */
    public static float twitch(float elapsed) {
        int index = -1;
        for (int i = 0; i < CLICK_TICKS.length && CLICK_TICKS[i] <= elapsed; i++) index = i;
        if (index < 0) return 0.0F;
        float since = elapsed - CLICK_TICKS[index];
        if (since > 3.0F) return 0.0F;
        float shape = since < 0.6F ? since / 0.6F : 1.0F - (since - 0.6F) / 2.4F;
        float strength = 1.0F - 0.55F * index / (CLICK_TICKS.length - 1);
        return (index % 2 == 0 ? 1.0F : -1.0F) * strength * shape;
    }

    /** Slow sag while the clicks slow down. */
    public static float wind(float elapsed) {
        return smoothStep(elapsed, 0.0F, EXHALE_TICK);
    }

    /** Legs give on the exhale and it drops into a sit. */
    public static float crumple(float elapsed) {
        float t = Mth.clamp((elapsed - (EXHALE_TICK - 1)) / 7.0F, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Folds forward over its knees as the breath runs out. */
    public static float slump(float elapsed) {
        return smoothStep(elapsed, EXHALE_TICK + 4, SHED_START + 4);
    }

    /** Whole-body forward lean about the hips, in radians. */
    public static float lean(float elapsed) {
        return 0.08F * wind(elapsed) * (1.0F - crumple(elapsed)) + 0.3F * crumple(elapsed) + 0.32F * slump(elapsed);
    }

    /** Hood sliding off the hanging head, accelerating as it drops. */
    public static float hoodSlip(float elapsed) {
        float t = Mth.clamp((elapsed - HOOD_SLIP_START) / (HOOD_SLIP_END - HOOD_SLIP_START), 0.0F, 1.0F);
        return t * t;
    }

    /** 0 to 1 as the body darkens to ash. */
    public static float ash(float elapsed) {
        return smoothStep(elapsed, SHED_START, FADE_END);
    }

    public static float alpha(float elapsed) {
        return 1.0F - Mth.clamp((elapsed - FADE_START) / (FADE_END - FADE_START), 0.0F, 1.0F);
    }

    /** The fallen hood outlasts the body, then goes in the last few ticks. */
    public static float hoodAlpha(float elapsed) {
        return 1.0F - Mth.clamp((elapsed - (TICKS - 6)) / 6.0F, 0.0F, 1.0F);
    }

    private static float smoothStep(float elapsed, float start, float end) {
        float t = Mth.clamp((elapsed - start) / (end - start), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
