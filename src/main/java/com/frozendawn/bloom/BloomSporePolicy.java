package com.frozendawn.bloom;

import net.minecraft.util.Mth;

/** Pure timing and layout policy for frontier Spores and their finite satellite nodes. */
public final class BloomSporePolicy {
    public static final int SPAWN_CHECK_INTERVAL = 200;
    public static final double SPAWN_CHANCE = 0.025D;
    public static final int HEARTH_ACTIVE_CAP = 2;
    public static final int SATELLITE_ACTIVE_CAP = 1;
    public static final int GLOBAL_ACTIVE_CAP = 4;
    public static final double ESCAPE_DISTANCE = 100.0D;
    public static final int COLLAPSE_TICKS = 48;
    public static final int COLLAPSE_IMPACT_TICKS = 10;
    public static final int ROOT_SHOCK_TICKS = 10;
    public static final int IMMEDIATE_ROOT_TIPS = 3;
    public static final int ROOT_PATCH_INTERVAL = 12;
    public static final int CONTACT_COOLDOWN_TICKS = 40;
    public static final int CONTACT_SLOW_TICKS = 60;
    public static final int SATELLITE_RADIUS = 16;
    public static final int SATELLITE_DIAMETER = SATELLITE_RADIUS * 2 + 1;
    public static final int SATELLITE_COLUMNS = SATELLITE_DIAMETER * SATELLITE_DIAMETER;
    public static final long FORMATION_TICKS = BloomGrowthPolicy.DAY_TICKS;
    public static final long RELAY_TICKS = 3L * BloomGrowthPolicy.DAY_TICKS;
    public static final int MAX_SATELLITE_HEIGHT = 4;
    public static final int CORPSE_PICKAXE_HITS = 16;
    public static final int CORPSE_STRIKE_COOLDOWN_TICKS = 8;

    private BloomSporePolicy() {
    }

    public static boolean shouldSpawn(double roll) {
        return roll >= 0.0D && roll < SPAWN_CHANCE;
    }

    public static int sourceActiveCap(boolean satellite) {
        return satellite ? SATELLITE_ACTIVE_CAP : HEARTH_ACTIVE_CAP;
    }

    public static boolean escaped(double distanceFromSourceCenter, double sourceEdgeRadius) {
        return distanceFromSourceCenter - sourceEdgeRadius >= ESCAPE_DISTANCE;
    }

    public static int desiredGrowthCursor(long loadedTicks) {
        double progress = Mth.clamp(loadedTicks / (double) FORMATION_TICKS, 0.0D, 1.0D);
        return Math.min(SATELLITE_COLUMNS,
                (int) Math.floor(SATELLITE_COLUMNS * progress));
    }

    public static int permutedColumn(int cursor, long layoutSeed) {
        int offset = (int) Math.floorMod(layoutSeed, SATELLITE_COLUMNS);
        return Math.floorMod(cursor * 31 + offset, SATELLITE_COLUMNS);
    }

    public static int columnX(int permuted) {
        return permuted % SATELLITE_DIAMETER - SATELLITE_RADIUS;
    }

    public static int columnZ(int permuted) {
        return permuted / SATELLITE_DIAMETER - SATELLITE_RADIUS;
    }

    public static boolean acceptsColumn(long layoutSeed, int x, int z) {
        if (x * x + z * z > SATELLITE_RADIUS * SATELLITE_RADIUS) {
            return false;
        }
        long hash = BloomGrowthPolicy.mix(layoutSeed ^ x * 0x9E3779B97F4A7C15L
                ^ z * 0xC2B2AE3D27D4EB4FL);
        return Math.floorMod(hash, 100L) < 20L;
    }

    public static int columnHeight(long layoutSeed, int x, int z) {
        long hash = BloomGrowthPolicy.mix(layoutSeed + x * 71L + z * 131L);
        return 1 + (int) Math.floorMod(hash, MAX_SATELLITE_HEIGHT);
    }
}
