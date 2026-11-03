package com.frozendawn.phase;

/** Phase 1 timing in simulation ticks; independent of daylight speed and weather visibility. */
public final class PhaseOneWeatherPolicy {
    private PhaseOneWeatherPolicy() { }

    public static float localProgress(float progress) {
        return Math.clamp((progress - 0.05F) / 0.07F, 0.0F, 1.0F);
    }

    public static int durationTicks(float progress, boolean rain, double roll) {
        float local = localProgress(progress);
        double minimum = rain ? 2 + local : 8 - 4 * local;
        double maximum = rain ? 4 + 2 * local : 12 - 5 * local;
        return (int) Math.round((minimum + (maximum - minimum) * Math.clamp(roll, 0, 1)) * 1200);
    }

    /** A small minority of rain windows carry thunder; phase 1 never locks a storm. */
    public static boolean thunder(double roll) { return roll < 0.05D; }

    public static double roll(long seed, long sequence, long salt) {
        long value = seed ^ (sequence * 0x9e3779b97f4a7c15L) ^ salt;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }

    /** Quiet clear intervals and broad, soft gusts during rain; never reaches phase 3 volume. */
    public static float windVolume(float progress, float rain, double ticks) {
        double wave = 0.5 - 0.5 * Math.cos(ticks * Math.PI * 2 / 800.0);
        return (float) ((0.025 + 0.10 * wave * wave) * (0.7 + 0.3 * localProgress(progress))
                * Math.clamp(rain, 0, 1));
    }
}
