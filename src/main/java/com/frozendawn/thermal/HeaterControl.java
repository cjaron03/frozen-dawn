package com.frozendawn.thermal;

/** Shared heater demand. Values are game heat units; target control never removes stored heat. */
public final class HeaterControl {
    public static final double DEFAULT_TARGET = 20.0;
    private HeaterControl() {}

    /** Energy needed to reach target after finite air/wall exchange, capped by available fuel power. */
    public static double airGrant(double maximum, double ca, double cs, double air, double walls,
            double exchangeK, double seconds, double target) {
        if (maximum <= 0 || ca <= 0) return 0;
        double fraction = cs > 0 ? RoomHeatMath.exchange(ca, cs, 1, 0, exchangeK, seconds) / ca : 0;
        double unheated = air - fraction * (air - walls);
        return Math.clamp(ca * (target - unheated) / (1 - fraction), 0, maximum);
    }
    public static double wallGrant(double maximum, double capacity, double walls, double target) {
        return Math.clamp(capacity * (target - walls) / RoomHeatMath.VACUUM_HEATER_EFFICIENCY, 0, maximum);
    }
    public static double openCampFraction(double background, double warmth) {
        return warmth > 0 ? Math.clamp((DEFAULT_TARGET - background) / warmth, 0, 1) : 0;
    }
}
