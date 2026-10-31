package com.frozendawn.entity.blind;

/** Local encounter tuning; these thresholds do not change Maeve's historical learning. */
public final class BlindArchitectPolicy {
    public static final int ECHO_RANGE = 24, QUERY_LIMIT = 512, ACTOR_WORK = 512, LEVEL_WORK = 8192;
    public static final int MAX_TRACKS = 4, MAX_RETURNS = 8, FRESH_TICKS = 120, SEARCH_TICKS = 240;
    public static final int NOISE_COOLDOWN = 240, STUN_TICKS = 25, FLEE_TICKS = 100;
    public static final double INVESTIGATE = .35, LOCK = .75;
    private BlindArchitectPolicy() { }
    /** Escape direction is horizontal; a hot block's center must not pull the route below the floor. */
    public static net.minecraft.world.phys.Vec3 retreatGoal(net.minecraft.world.phys.Vec3 position,
            net.minecraft.world.phys.Vec3 origin, net.minecraft.world.phys.Vec3 fallback) {
        var delta = position.subtract(origin);
        var away = new net.minecraft.world.phys.Vec3(delta.x, 0, delta.z);
        if (away.horizontalDistanceSqr() < .01) away = new net.minecraft.world.phys.Vec3(-fallback.x, 0, -fallback.z);
        return position.add(away.normalize().scale(8));
    }
    public static int uncertainty(double distance) { return distance > 16 ? 4 : distance > 8 ? 2 : 1; }
    public static double confidence(double distance) { return distance > 16 ? .40 : distance > 8 ? .60 : .85; }
    public static int cadence(boolean pursuit, boolean investigating, double distance, int jitter) {
        return pursuit ? (distance <= 4 ? 10 + jitter % 5 : 18 + jitter % 7)
                : investigating ? 30 + jitter % 11 : 50 + jitter % 21;
    }
}
