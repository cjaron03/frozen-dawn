package com.frozendawn.client;

/** Pure geometric readings: no terrain searches, live destination checks or pathfinding. */
public final class ContinuityNavigationPolicy {
    private static final String[] BEARINGS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    private ContinuityNavigationPolicy() {}

    public static double horizontalDistance(double x, double z, double targetX, double targetZ) {
        return Math.hypot(targetX - x, targetZ - z);
    }
    public static int approximateDistance(double distance) {
        return Math.max(25, (int) Math.round(distance / 25.0) * 25);
    }
    public static String bearing(double dx, double dz) {
        double angle = Math.atan2(dx, -dz);
        return BEARINGS[Math.floorMod((int) Math.round(angle / (Math.PI / 4)), BEARINGS.length)];
    }
    public static String directionArrow(double dx, double dz, float minecraftYaw) {
        double relative = Math.toDegrees(Math.atan2(dx, -dz)) - (minecraftYaw + 180.0);
        return new String[] {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[
                Math.floorMod((int) Math.round(relative / 45.0), 8)];
    }
}
