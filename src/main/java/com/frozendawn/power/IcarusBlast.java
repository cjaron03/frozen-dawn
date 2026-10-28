package com.frozendawn.power;

/** Pure blast geometry for an Icarus detonation: crater, melt ring, damage and the boom's travel time. */
public final class IcarusBlast {
    public static final double FULL_RADIUS = 400;
    public static final double SPEED_OF_SOUND = 343;
    public static final float EDGE_DAMAGE = 40;
    public static final double EDGE_KNOCKBACK = 4;

    private IcarusBlast() {}

    public static double radius(long coreCharge) {
        if (coreCharge <= 0) return 0;
        return FULL_RADIUS * Math.cbrt((double) coreCharge / (IcarusPhysics.MAX_CAPSULES * IcarusPhysics.CAPSULE));
    }

    /** The crater's rim distance at an angle: a seeded sum of low-frequency sines, always within [0.9r, r]. */
    public static double edge(double radius, long seed, double angle) {
        long h = mix(seed);
        int waves = 3 + (int) Long.remainderUnsigned(h, 3);
        double sum = 0, amplitude = 0;
        for (int i = 0; i < waves; i++) {
            h = mix(h + i);
            int frequency = 1 + (int) Long.remainderUnsigned(h, 5);
            double phase = (h >>> 11) * 0x1.0p-53 * 2 * Math.PI, weight = 1.0 / frequency;
            sum += weight * Math.sin(frequency * angle + phase);
            amplitude += weight;
        }
        return radius * (0.95 + 0.05 * sum / amplitude);
    }

    public static int depth(double distance, double radius, long seed, int x, int z) {
        double edge = edge(radius, seed, Math.atan2(z, x));
        if (distance > edge) return 0;
        double t = distance / edge;
        return Math.max(rimDepth(seed, x, z), (int) Math.round(radius / 5 * (1 - t * t)));
    }

    public static int rimDepth(long seed, int x, int z) {
        long h = mix(seed ^ mix(x * 0x9E3779B97F4A7C15L + z));
        return 4 + (int) Long.remainderUnsigned(h, 4);
    }

    public static double meltRadius(double radius) { return 1.5 * radius; }

    public static double soundDelaySeconds(double distance) { return distance / SPEED_OF_SOUND; }

    public static float damage(double distance, double radius) {
        if (distance <= radius) return Float.MAX_VALUE;
        if (distance >= 2 * radius) return 0;
        return (float) (EDGE_DAMAGE * (1 - (distance - radius) / radius));
    }

    public static double knockback(double distance, double radius) {
        if (distance <= radius || distance >= 2 * radius) return 0;
        return EDGE_KNOCKBACK * (1 - (distance - radius) / radius);
    }

    /** One-off radiation dose at the crater edge for a player standing at the centre; it falls to nothing at 2r. */
    public static final double BURST_DOSE = 600;
    public static double burstDose(double distance, double radius) {
        return distance >= 2 * radius ? 0 : BURST_DOSE * (1 - distance / (2 * radius));
    }

    /** The white flash's peak opacity: full at the centre, still 0.15 at 6r, gone by 12r. Reduced flash caps it at 0.35. */
    public static double flashStrength(double distance, double radius, boolean reduced) {
        double t = distance / (6 * radius);
        double full = t <= 1 ? 1 - 0.85 * t : Math.max(0, 0.15 * (2 - t));
        return reduced ? Math.min(0.35, full) : full;
    }

    public static boolean witness(double distance, double radius) {
        return distance > radius && distance <= 3 * radius;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
