package com.frozendawn.gpr;

/** Pure ORSA GPR-4 "Plumb Line" rules: settings, penetration, blur and triangulation (Notion spec, 2026-10-14). */
public final class PlumbLinePolicy {
    private PlumbLinePolicy() { }

    public enum Setting {
        SURVEY(30_000, 48, 9_000), DEEP(50_000, 80, 16_000);
        public final int watts, radius, rays;
        Setting(int watts, int radius, int rays) { this.watts = watts; this.radius = radius; this.rays = rays; }
        public static Setting byId(int id) { var all = values(); return id >= 0 && id < all.length ? all[id] : SURVEY; }
    }

    /** One pulse lasts 10 s; the scan runs while it draws power. Then 60 s before the next. */
    public static final int PULSE_TICKS = 200, COOLDOWN_TICKS = 1200;
    /** Overlay: players within 16 blocks of the scanner when the pulse completes see it for 30 s. */
    public static final int VIEW_RANGE = 16, OVERLAY_TICKS = 600;
    /** Triangulation: a return remembered for 10 minutes tightens when a scanner at least 8 blocks away hits it again. */
    public static final long MEMORY_TICKS = 12_000;
    public static final int TRIANGULATION_BASELINE = 8;
    /** Returns padded 2 blocks near the scanner, growing with distance and with how much rock the ray crossed. */
    public static final double BASE_PAD = 2, DISTANCE_PAD = 1.0 / 24, ROCK_PAD = 1.0 / 32, DEEP_PAD = 1;
    public static final int MIN_VOID_CELLS = 24, MAX_RETURNS = 96;
    /** Frozen ground (below 0C in the ground temperature model) lets the signal reach 25% further. */
    public static final double FROZEN_BONUS = 1.25;

    public enum Medium { STONE, DEEPSLATE, ICE, OPEN, WATER, METAL }

    /**
     * Range cost of crossing one block of a medium, in blocks of baseline stone range. Deepslate allows 75% range,
     * ice and snow 150%, open air and caves cost as stone (the ground around them dominates), water stops the ray
     * and metal reflects it.
     */
    public static double cost(Medium medium, boolean frozen) {
        double c = switch (medium) {
            case STONE, OPEN -> 1.0;
            case DEEPSLATE -> 1 / 0.75;
            case ICE -> 1 / 1.5;
            case WATER, METAL -> Double.POSITIVE_INFINITY;
        };
        return frozen && Double.isFinite(c) ? c / FROZEN_BONUS : c;
    }

    /** Blur padding for a return at {@code distance} reached after crossing {@code rock} blocks of stone or deepslate. */
    public static double pad(double distance, double rock, Setting setting) {
        return BASE_PAD + distance * DISTANCE_PAD + rock * ROCK_PAD + (setting == Setting.DEEP ? DEEP_PAD : 0);
    }

    /** Evenly spread unit directions (Fibonacci sphere); {@code i} in [0, n). */
    public static double[] direction(int i, int n) {
        double golden = Math.PI * (3 - Math.sqrt(5));
        double y = 1 - (i + 0.5) * 2.0 / n, r = Math.sqrt(Math.max(0, 1 - y * y)), theta = golden * i;
        return new double[] {Math.cos(theta) * r, y, Math.sin(theta) * r};
    }

    /** Two scanner positions far enough apart to triangulate. */
    public static boolean baseline(int ax, int ay, int az, int bx, int by, int bz) {
        long dx = ax - bx, dy = ay - by, dz = az - bz;
        return dx * dx + dy * dy + dz * dz >= (long) TRIANGULATION_BASELINE * TRIANGULATION_BASELINE;
    }
}
