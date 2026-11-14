package com.frozendawn.client;

/**
 * Ending B's cut from the welcome to the compliance line: over the compliance beat's first ticks the welcome panel
 * tears into shifted slices, its letters corrupt and it bleeds from teal to red, blinking out just before the cut.
 * Everything here is a pure function of the tick, so a frame looks the same however it is reached.
 */
public final class EndingGlitch {
    /** The glitch fills the compliance beat's opening, where the static used to be. */
    public static final int GLITCH_TICKS = 34;
    /** The widest a slice tears sideways, in GUI pixels. */
    public static final int MAX_TEAR = 13;
    /** The slices' height, in GUI pixels. */
    public static final int SLICE_HEIGHT = 3;
    private static final String GLYPHS = "#%&@$/\\<>?!*";

    private EndingGlitch() {
    }

    /** How far the glitch has run, 0 at its first tick to 1 at its last. */
    public static float progress(int tick) {
        return Math.max(0.0F, Math.min(1.0F, tick / (float) (GLITCH_TICKS - 1)));
    }

    /** The text with letters swapped for glyphs, more of them as the glitch runs; spaces stay. */
    public static String corrupt(String text, float progress, int tick) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ' && hash01(tick * 17 + i * 7) < progress * 0.95F) {
                out.append(GLYPHS.charAt((int) (hash01(tick * 31 + i) * GLYPHS.length()) % GLYPHS.length()));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** How far one slice of the panel tears sideways this tick: none at the start, up to MAX_TEAR at the end. */
    public static int sliceOffset(int slice, int tick, float progress) {
        if (hash01(tick * 53 + slice) >= 0.5F + progress / 2.0F) {
            return 0;
        }
        return Math.round((hash01(tick * 97 + slice) - 0.5F) * 2.0F * progress * MAX_TEAR);
    }

    /** Whether the panel's title flips to the corrupt access-control header this tick. */
    public static boolean titleCorrupted(int tick, float progress) {
        return hash01(tick * 13 + 1) < progress;
    }

    /** Near the cut the panel drops out on every third tick. */
    public static boolean blankFrame(int tick) {
        return progress(tick) > 0.85F && tick % 3 == 0;
    }

    /** Each ARGB channel from a toward b. */
    public static int mix(int a, int b, float t) {
        float k = Math.max(0.0F, Math.min(1.0F, t));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int from = (a >>> shift) & 0xFF;
            int to = (b >>> shift) & 0xFF;
            out |= Math.round(from + (to - from) * k) << shift;
        }
        return out;
    }

    /** A scan line's height on the screen, as a fraction of it. */
    public static float scanLine(int tick, int line) {
        return hash01(tick * 41 + line);
    }

    private static float hash01(int seed) {
        int x = seed * 1664525 + 1013904223;
        x ^= x >>> 16;
        x *= 0x45D9F3B;
        x ^= x >>> 16;
        return (x & 0x00FFFFFF) / (float) 0x01000000;
    }
}
