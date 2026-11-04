package com.frozendawn.vault;

/** The clock over the last door. It runs back a second at a time from a number the seed sets, and past zero starts again
 * from the top. Never explained. */
public final class VaultClock {
    /** One hundred hours on the display. */
    public static final long CYCLE = 100 * 3600;
    private VaultClock() { }

    public static long start(long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        return Math.floorMod(h ^ h >>> 29, CYCLE);
    }

    public static String text(long start, long gameTime) {
        long left = Math.floorMod(start - gameTime / 20, CYCLE);
        return String.format("%02d:%02d:%02d", left / 3600, left / 60 % 60, left % 60);
    }
}
