package com.frozendawn.uplink;

import java.util.List;

/** Pure ORSA Uplink rules: modes, cooldown, reward order, signal pressure and approximate regions. */
public final class UplinkPolicy {
    private UplinkPolicy() { }

    public enum Mode {
        OFF(0, 0), LISTEN(2_000, 0), TRANSMIT(40_000, 3 * 60 * 20), BOOST(60_000, 90 * 20);
        public final int watts, ticks;
        Mode(int watts, int ticks) { this.watts = watts; this.ticks = ticks; }
        public boolean transmitting() { return ticks > 0; }
        public static Mode byId(int id) { var all = values(); return id >= 0 && id < all.length ? all[id] : OFF; }
    }

    public enum Reward { ARCHIVE, SUPPLY_DROP, STATION_MAP }

    /** One transmission per in-game day, counted in game time so a stopped daylight cycle cannot stall it. */
    public static final long COOLDOWN_TICKS = 24_000;
    /** The dish must stand within this many blocks of its console. */
    public static final int DISH_RANGE = 8;
    public static final int ARCHIVE_SIZE = 3;

    // Signal pressure: Uplink-local, outside MACS (owner decision 2026-10-17).
    public static final double STANDARD_SIGNAL = 1.0, BOOST_SIGNAL = 1.6, MAX_SIGNAL = 4.0;
    /** The reaction is lagged: a transmission adds no pressure until this much game time has passed. */
    public static final long SIGNAL_LAG_TICKS = 2_400;
    public static final long SIGNAL_HALF_LIFE_TICKS = 3 * 24_000;
    public static final int SIGNAL_MERGE_RADIUS = 32, SIGNAL_REACH = 96, MAX_SITES = 16;
    /** Natural Architect spawn chance multiplier at full pressure is 1 + MAX_SIGNAL * SPAWN_GAIN. */
    public static final double SPAWN_GAIN = 0.75;

    // Supply drops and station maps.
    public static final int DROP_MIN_DISTANCE = 150, DROP_MAX_DISTANCE = 300, DROP_CIRCLE_MIN = 60, DROP_CIRCLE_MAX = 80;
    public static final int BEACON_RANGE = 30, RETRIEVE_RANGE = 4, MAX_DROPS = 8;
    public static final int SITE_MIN_DISTANCE = 200, SITE_MAX_DISTANCE = 900, SITE_REGION_RADIUS = 96, SITE_REGION_JITTER = 48;

    // Listen broadcasts and storm wear.
    public static final int BROADCAST_INTERVAL = 1_800, LOG_LINES = 8, LISTEN_RANGE = 12;
    public static final int STORM_CHECK_INTERVAL = 1_200;
    public static final double STORM_DISH_LOSS = 0.02;

    public static boolean cooledDown(long now, long nextTransmit) { return now >= nextTransmit; }
    public static long cooldownRemaining(long now, long nextTransmit) { return Math.max(0, nextTransmit - now); }

    /**
     * Archive pacing (Uplink spec): #1 on the first transmission; #2 only once Maeve has activated;
     * #3 in Phase 6, delivered as the human reply. A due fragment always outranks other rewards.
     */
    public static boolean archiveDue(int delivered, boolean maeveActivated, int phase) {
        return switch (delivered) {
            case 0 -> true;
            case 1 -> maeveActivated;
            case 2 -> maeveActivated && phase >= 6;
            default -> false;
        };
    }

    /** True for the fragment delivered as a reply from a person rather than an automated carrier. */
    public static boolean humanReply(int fragmentIndex) { return fragmentIndex == ARCHIVE_SIZE - 1; }

    /**
     * Weighted and rotating: a supply drop weighs 3 and a station map 2, and whichever came last is halved,
     * so neither repeats indefinitely. A map with no undiscovered site to mark falls back to a supply drop.
     * {@code roll} is uniform in [0, 1).
     */
    public static Reward choose(int archiveDelivered, boolean maeveActivated, int phase, Reward last, boolean siteAvailable, double roll) {
        if (archiveDelivered < ARCHIVE_SIZE && archiveDue(archiveDelivered, maeveActivated, phase)) return Reward.ARCHIVE;
        if (!siteAvailable) return Reward.SUPPLY_DROP;
        double supply = last == Reward.SUPPLY_DROP ? 1.5 : 3, map = last == Reward.STATION_MAP ? 1 : 2;
        return roll * (supply + map) < supply ? Reward.SUPPLY_DROP : Reward.STATION_MAP;
    }

    public static double signal(boolean boost) { return boost ? BOOST_SIGNAL : STANDARD_SIGNAL; }

    /** Pressure contributed by a site whose score was last written at {@code at} and armed (lag elapsed) at {@code armed}. */
    public static double pressure(double score, long at, long armed, long now) {
        if (now < armed || !(score > 0)) return 0;
        double halfLives = Math.max(0, now - at) / (double) SIGNAL_HALF_LIFE_TICKS;
        return Math.min(MAX_SIGNAL, score * Math.pow(0.5, halfLives));
    }

    /** Score after a new transmission at the same site: decayed old score plus the new signal, capped. */
    public static double accumulate(double score, long at, long now, boolean boost) {
        double decayed = Math.max(0, score) * Math.pow(0.5, Math.max(0, now - at) / (double) SIGNAL_HALF_LIFE_TICKS);
        return Math.min(MAX_SIGNAL, decayed + signal(boost));
    }

    public static double spawnMultiplier(double pressure) { return 1 + Math.clamp(pressure, 0, MAX_SIGNAL) * SPAWN_GAIN; }

    private static final List<String> SECTORS = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");

    /** Compass sector of a horizontal offset (+Z is south, as in Minecraft). */
    public static String sector(double dx, double dz) {
        if (dx == 0 && dz == 0) return "N";
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.floorMod(Math.round(degrees / 45.0), 8L);
        return SECTORS.get(index);
    }

    /** Distance as the console announces it: rounded to 50 m, never below 50. */
    public static int announcedDistance(double distance) { return (int) Math.max(50, Math.round(distance / 50.0) * 50); }

    /**
     * Offset of the drawn circle's center from the true point. {@code angle} in radians, {@code fraction} in [0, 1).
     * The true point always lies inside the circle with at least {@code margin} blocks to spare.
     */
    public static int[] jitter(int radius, int margin, double angle, double fraction) {
        double reach = Math.max(0, radius - margin) * Math.sqrt(Math.clamp(fraction, 0, 1));
        return new int[] {(int) Math.round(Math.sin(angle) * reach), (int) Math.round(-Math.cos(angle) * reach)};
    }

    /** Vanilla map scale (0..4) whose 128-pixel span covers every point within {@code reach} of the map center. */
    public static int mapScale(int reach) {
        for (int scale = 0; scale < 4; scale++) if (64 << scale >= reach + 4) return scale;
        return 4;
    }
}
