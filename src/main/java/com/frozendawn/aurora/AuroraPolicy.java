package com.frozendawn.aurora;

/** Pure, replayable nightly activity. Weather affects sight, never the event lottery. */
public final class AuroraPolicy {
    public static final long DAY_TICKS = 24_000L;
    public static final int FADE_TICKS = 1_000;
    public record Display(long seed, long startTick, int durationTicks, float strength) {
        public static final Display NONE = new Display(0L, 0L, 0, 0.0F);
    }

    private AuroraPolicy() { }

    public static Display nightly(long worldSeed, long dayTime, int phase, float progress) {
        if (phase < 0 || phase > 3 || dayTime < 0) return Display.NONE;
        long day = Math.floorDiv(dayTime, DAY_TICKS);
        long seed = mix(worldSeed ^ mix(day + 0x4155524f52414cL));
        double chance = switch (phase) {
            case 0 -> 0.18D;
            case 1 -> 0.40D;
            case 2 -> 0.25D;
            default -> 0.12D;
        };
        if (unit(seed) >= chance) return Display.NONE;
        long start = day * DAY_TICKS + 13_300L + (long) (unit(mix(seed + 1)) * 1_500L);
        int duration = 4_600 + (int) (unit(mix(seed + 2)) * 2_600);
        float strength = (float) (0.65D + unit(mix(seed + 3)) * 0.35D);
        return new Display(seed, start, duration, strength * phaseStrength(phase, progress));
    }

    public static float phaseStrength(int phase, float progress) {
        return switch (phase) {
            case 0 -> 0.55F;
            case 1 -> 1.0F;
            case 2 -> 0.75F;
            case 3 -> 0.50F * (1.0F - smooth(0.29F, 0.34F, progress));
            default -> 0.0F;
        };
    }

    public static float envelope(double age, int duration) {
        if (duration <= 0 || age <= 0 || age >= duration) return 0.0F;
        float fade = Math.min(FADE_TICKS, duration * 0.25F);
        return smooth(0, fade, (float) age) * smooth(0, fade, (float) (duration - age));
    }

    /** Celestial time, rather than a storm's reduced skylight, decides night visibility. */
    public static float darkness(long dayTime, float partialTick) {
        float time = Math.floorMod(dayTime, DAY_TICKS) + partialTick;
        return smooth(12_500F, 14_000F, time) * (1.0F - smooth(22_000F, 23_500F, time));
    }

    public static float weatherVisibility(float rain, float thunder) {
        return (1.0F - 0.65F * clamp(rain)) * (1.0F - 0.88F * clamp(thunder));
    }

    public static float smooth(float from, float to, float value) {
        float x = clamp((value - from) / (to - from));
        return x * x * (3.0F - 2.0F * x);
    }

    public static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
    private static float clamp(float value) { return Math.max(0.0F, Math.min(1.0F, value)); }
}
