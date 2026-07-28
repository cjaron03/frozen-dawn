package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Pure timing and survivor-scaling rules for the Master Architect storm collapse. */
public final class MasterArchitectStormAftermathPolicy {
    public static final int FULL_EYE_END_TICK = 100;
    public static final int FULL_RUPTURE_END_TICK = 400;
    public static final int FULL_COLLAPSE_END_TICK = 500;
    public static final int STILLNESS_TICKS = 60;
    public static final int EMPTY_FADE_TICKS = 200;

    private MasterArchitectStormAftermathPolicy() {
    }

    public static Timeline timeline(float fieldStrength) {
        float strength = Mth.clamp(fieldStrength, 0.0F, 1.0F);
        if (strength <= 0.001F) {
            return new Timeline(20, EMPTY_FADE_TICKS, EMPTY_FADE_TICKS,
                    EMPTY_FADE_TICKS, EMPTY_FADE_TICKS + STILLNESS_TICKS);
        }
        int eyeEnd = Math.round(Mth.lerp(strength, 60.0F, FULL_EYE_END_TICK));
        int ruptureEnd = Math.round(Mth.lerp(strength, 220.0F, FULL_RUPTURE_END_TICK));
        int collapseEnd = Math.round(Mth.lerp(strength, 300.0F, FULL_COLLAPSE_END_TICK));
        return new Timeline(20, eyeEnd, ruptureEnd, collapseEnd,
                collapseEnd + STILLNESS_TICKS);
    }

    public static Stage stage(int elapsedTicks, float fieldStrength) {
        Timeline timeline = timeline(fieldStrength);
        if (elapsedTicks < timeline.coreEndTick()) {
            return Stage.CORE;
        }
        if (fieldStrength <= 0.001F) {
            return elapsedTicks < timeline.collapseEndTick()
                    ? Stage.FADE : elapsedTicks < timeline.completeTick()
                    ? Stage.STILLNESS : Stage.COMPLETE;
        }
        if (elapsedTicks < timeline.eyeEndTick()) {
            return Stage.EYE;
        }
        if (elapsedTicks < timeline.ruptureEndTick()) {
            return Stage.RUPTURE;
        }
        if (elapsedTicks < timeline.collapseEndTick()) {
            return Stage.BASE_COLLAPSE;
        }
        return elapsedTicks < timeline.completeTick()
                ? Stage.STILLNESS : Stage.COMPLETE;
    }

    public static float collapseProgress(int elapsedTicks, float fieldStrength) {
        Timeline timeline = timeline(fieldStrength);
        if (fieldStrength <= 0.001F) {
            return Mth.clamp(elapsedTicks / (float) timeline.collapseEndTick(), 0.0F, 1.0F);
        }
        return Mth.clamp((elapsedTicks - timeline.coreEndTick())
                / (float) Math.max(1, timeline.collapseEndTick() - timeline.coreEndTick()),
                0.0F, 1.0F);
    }

    public static int detachedChunkCount(float fieldStrength) {
        return fieldStrength <= 0.001F
                ? 0 : Mth.clamp(Math.round(1.0F + fieldStrength * 3.0F), 1, 4);
    }

    public enum Stage {
        CORE,
        EYE,
        RUPTURE,
        BASE_COLLAPSE,
        FADE,
        STILLNESS,
        COMPLETE
    }

    public record Timeline(
            int coreEndTick,
            int eyeEndTick,
            int ruptureEndTick,
            int collapseEndTick,
            int completeTick) {
    }
}
