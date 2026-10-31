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

    /** The crater afterwards, at the owner's request: activated ground, a hot floor, smoke and ash, all by age in ticks. */
    public static final double DAY_TICKS = 24000, CRATER_RATE = 3, FLOOR_COOL_TICKS = 6000, FLOOR_BURNS_ABOVE = 0.2,
            SMOKE_COOL_TICKS = 18000, ASH_TICKS = 6000;
    /** A crater is forgotten after six days, when its radiation is below background and its smoke is gone. */
    public static final long SITE_TICKS = 6 * 24000;
    /** Dose rate, RU/s, in the bowl: the ground the blast activated halves every in-game day, and the rim is far cooler
     * than the centre. Below background it is nothing. */
    public static double craterRate(double ageTicks, double distance, double radius) {
        if (ageTicks < 0 || distance > radius) return 0;
        double t = distance / radius, rate = CRATER_RATE * (1 - 0.9 * t * t) * Math.pow(0.5, ageTicks / DAY_TICKS);
        return rate < 0.05 ? 0 : rate;
    }
    /** How hot the floor still is: 1 at the blast, burning anything on it while at least FLOOR_BURNS_ABOVE, about eight
     * minutes. */
    public static double floorHeat(double ageTicks) { return ageTicks < 0 ? 0 : Math.exp(-ageTicks / FLOOR_COOL_TICKS); }
    /** Smoke and embers rising off the floor, for most of an hour. */
    public static double smokeHeat(double ageTicks) { return ageTicks < 0 ? 0 : Math.exp(-ageTicks / SMOKE_COOL_TICKS); }
    /** Ash fall: thickest at the centre, still half at twice the radius, and over after five minutes. */
    public static double ash(double ageTicks, double distance, double radius) {
        if (ageTicks < 0 || ageTicks >= ASH_TICKS || distance > 2 * radius) return 0;
        return (1 - ageTicks / ASH_TICKS) * (1 - 0.5 * distance / (2 * radius));
    }

    /** The soot pall, at the owner's request in air and vacuum alike: a ceiling of soot spreading out from the crater at
     * PALL_SPREAD blocks a second, up to PALL_REACH radii, thick once formed, then thinning from half a day until it is
     * gone at a day and a half. Returns how thickly it covers the sky at that distance, 0 to 1, with a soft edge. */
    public static final double PALL_REACH = 8, PALL_SPREAD = 8, PALL_FORM_TICKS = 1200, PALL_TICKS = 36000;
    public static double pall(double ageTicks, double distance, double radius) {
        if (ageTicks < 0 || ageTicks >= PALL_TICKS) return 0;
        double reach = Math.min(PALL_REACH * radius, radius + PALL_SPREAD * ageTicks / 20);
        if (distance >= reach) return 0;
        double edge = Math.min(1, (reach - distance) / (0.15 * reach)), form = Math.min(1, ageTicks / PALL_FORM_TICKS);
        double thin = ageTicks < PALL_TICKS / 3 ? 1 : 1 - (ageTicks - PALL_TICKS / 3) / (PALL_TICKS * 2 / 3);
        return form * thin * edge;
    }

    /** The white flash's peak opacity: full at the centre, still 0.15 at 6r, gone by 12r. Reduced flash caps it at 0.35. */
    public static double flashStrength(double distance, double radius, boolean reduced) {
        double t = distance / (6 * radius);
        double full = t <= 1 ? 1 - 0.85 * t : Math.max(0, 0.15 * (2 - t));
        return reduced ? Math.min(0.35, full) : full;
    }

    /** The flash for a player in air facing the blast: facing is the cosine between the view and the blast, 0 when it is
     * out of sight. Looking straight at it triples the strength, up to full white; looking away leaves the base flash. */
    public static double gazeFlash(double strength, double facing, boolean reduced) {
        double gaze = Math.pow(Math.max(0, facing), 3), flash = Math.min(1, strength * (1 + 2 * gaze));
        return reduced ? Math.min(0.35, flash) : flash;
    }

    public static final double MAX_RINGING_SECONDS = 12;
    /** How long the ears ring after the boom in air: the longest at the crater edge, fading to nothing at 3r. Vacuum carries
     * no boom, so nothing rings. */
    public static double ringingSeconds(double distance, double radius, boolean vacuum) {
        if (vacuum || distance >= 3 * radius) return 0;
        return MAX_RINGING_SECONDS * Math.min(1, 1 - (distance - radius) / (2 * radius));
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
