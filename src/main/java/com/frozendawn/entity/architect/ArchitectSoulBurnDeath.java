package com.frozendawn.entity.architect;

import net.minecraft.util.Mth;

/**
 * Timeline for the Pyro's soul-burn death, cut to {@code entity/architect/pyro/death.ogg}:
 * it clutches its burning eyes as they flare soul-blue, soul fire takes it at the feet with
 * a whump at 0.5 s and climbs to its head, then it throws its arms wide and is lifted off
 * the ground, charred, into a rising column of soul flames.
 */
public final class ArchitectSoulBurnDeath {

    /** Hands at the eyes. */
    public static final int CLUTCH_END = 8;
    /** Soul fire catches at the feet. */
    public static final int IGNITE_TICK = 10;
    /** The fire reaches the top of its head. */
    public static final int CLIMB_END = 44;
    /** It lets go of its eyes and starts to rise. */
    public static final int LIFT_START = 40;
    public static final int FADE_START = 58;
    public static final int TICKS = 84;
    /** Body height the fire climbs, in blocks. */
    public static final float HEIGHT = 2.0F;
    /** How far the body rises before it is gone, in blocks. */
    public static final float LIFT = 1.1F;

    private ArchitectSoulBurnDeath() {
    }

    /** Hands rising to cover its eyes. */
    public static float clutch(float elapsed) {
        return smoothStep(elapsed, 0.0F, CLUTCH_END);
    }

    /** Eyes turning from orange to soul blue. */
    public static float flare(float elapsed) {
        return smoothStep(elapsed, 2.0F, IGNITE_TICK + 2);
    }

    /** 0 to 1 as the fire front climbs from its feet to the top of its head. */
    public static float climb(float elapsed) {
        return smoothStep(elapsed, IGNITE_TICK, CLIMB_END);
    }

    /** Fire front height above its feet, in blocks. */
    public static float frontHeight(float elapsed) {
        return HEIGHT * climb(elapsed);
    }

    /** How charred a part spanning {@code low}..{@code high} blocks is once the fire has passed it. */
    public static float charred(float elapsed, float low, float high) {
        return Mth.clamp((frontHeight(elapsed) - low) / (high - low), 0.0F, 1.0F);
    }

    /** Arms flung wide and head thrown back as it lets go. */
    public static float release(float elapsed) {
        return smoothStep(elapsed, LIFT_START - 2, LIFT_START + 10);
    }

    /** Height above the ground, easing in like it is being drawn up. */
    public static float lift(float elapsed) {
        float t = Mth.clamp((elapsed - LIFT_START) / (TICKS - LIFT_START), 0.0F, 1.0F);
        return LIFT * t * t;
    }

    /** Writhing while it burns, gone once it is lifted. */
    public static float writhe(float elapsed) {
        return smoothStep(elapsed, IGNITE_TICK, IGNITE_TICK + 6) * (1.0F - release(elapsed));
    }

    public static float alpha(float elapsed) {
        return 1.0F - smoothStep(elapsed, FADE_START, TICKS - 2);
    }

    private static float smoothStep(float elapsed, float start, float end) {
        float t = Mth.clamp((elapsed - start) / (end - start), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
