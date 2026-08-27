package com.frozendawn.homo;

/** Pure timing and orbital policy for the post-Maeve Moon. */
public final class PostMaeveMoonPolicy {
    public static final long DAY_TICKS = 24_000L;
    public static final long FIRST_RISE_TICKS = 12_000L;
    public static final long CALVING_AGE_TICKS = DAY_TICKS;
    public static final long RAGGED_AGE_TICKS = 5L * DAY_TICKS;
    public static final long RING_AGE_TICKS = 15L * DAY_TICKS;
    public static final long RING_FADE_TICKS = 2L * DAY_TICKS;
    public static final long ORBIT_PERIOD_TICKS = 96L * DAY_TICKS;
    public static final float ORBIT_INCLINATION_DEGREES = 37.0F;
    public static final float ORBIT_ECCENTRICITY = 0.52F;
    // Begin with the vanilla-sized disc fully below the terrain horizon.
    public static final float FIRST_RISE_START_ELEVATION = -14.0F;
    public static final float FIRST_RISE_END_ELEVATION = 22.0F;
    public static final float MINIMUM_DISC_INTEGRITY = 0.65F;
    public static final float MAXIMUM_APPARENT_SIZE_SCALE = 1.75F;

    private static final double TWO_PI = Math.PI * 2.0D;
    private static final double START_TRUE_ANOMALY = -Math.asin(
            Math.sin(Math.toRadians(FIRST_RISE_END_ELEVATION))
                    / Math.sin(Math.toRadians(ORBIT_INCLINATION_DEGREES)));

    private PostMaeveMoonPolicy() {
    }

    public static long nextDusk(long dayTime) {
        long day = Math.floorDiv(dayTime, DAY_TICKS);
        long timeOfDay = Math.floorMod(dayTime, DAY_TICKS);
        if (timeOfDay < DAY_TICKS / 2L) {
            return day * DAY_TICKS + DAY_TICKS / 2L;
        }
        return (day + 1L) * DAY_TICKS + DAY_TICKS / 2L;
    }

    public static long positiveDayTimeAdvance(long previousDayTime, long currentDayTime) {
        return currentDayTime > previousDayTime ? currentDayTime - previousDayTime : 0L;
    }

    public static Snapshot snapshot(long elapsedTicks, long visualSeed) {
        if (elapsedTicks < 0L) {
            return hidden();
        }

        long clampedElapsed = Math.max(0L, elapsedTicks);
        if (clampedElapsed < FIRST_RISE_TICKS) {
            float rise = smooth(clampedElapsed / (float) FIRST_RISE_TICKS);
            return new Snapshot(
                    PostMaeveMoonStage.FIRST_RISE,
                    clampedElapsed,
                    0L,
                    rise,
                    lerp(FIRST_RISE_START_ELEVATION,
                            FIRST_RISE_END_ELEVATION, rise),
                    seededAscendingNode(visualSeed),
                    60.0F,
                    0.18F,
                    1.0F,
                    0,
                    0.0F,
                    0.0F);
        }

        long damageAge = clampedElapsed - FIRST_RISE_TICKS;
        PostMaeveMoonStage stage = stageForDamageAge(damageAge);
        float calving = smooth(progressBetween(
                damageAge, CALVING_AGE_TICKS, RAGGED_AGE_TICKS));
        float ragged = smooth(progressBetween(
                damageAge, RAGGED_AGE_TICKS, RING_AGE_TICKS));
        float ring = smooth(progressBetween(
                damageAge, RING_AGE_TICKS, RING_AGE_TICKS + RING_FADE_TICKS));
        float late = damageAge <= RING_AGE_TICKS ? 0.0F
                : 1.0F - (float) Math.exp(
                -(damageAge - RING_AGE_TICKS) / (30.0D * DAY_TICKS));

        float fracture = clamp(0.22F + calving * 0.22F
                + ragged * 0.24F + late * 0.20F, 0.0F, 0.88F);
        float integrity = clamp(1.0F - calving * 0.08F
                - ragged * 0.14F - late * 0.13F,
                MINIMUM_DISC_INTEGRITY, 1.0F);
        int debris = switch (stage) {
            case CALVING -> Math.round(8.0F + 20.0F * calving);
            case RAGGED -> Math.round(28.0F + 44.0F * ragged);
            case RINGING -> 72;
            default -> 0;
        };

        Orbit orbit = orbit(damageAge, visualSeed);
        return new Snapshot(
                stage,
                clampedElapsed,
                damageAge,
                1.0F,
                orbit.elevationDegrees(),
                orbit.azimuthDegrees(),
                orbit.illuminationAngleDegrees(),
                fracture,
                integrity,
                debris,
                ring,
                clamp(calving * 0.55F + ragged * 0.45F, 0.0F, 1.0F));
    }

    public static PostMaeveMoonStage stageForDamageAge(long damageAgeTicks) {
        if (damageAgeTicks < 0L) {
            return PostMaeveMoonStage.FIRST_RISE;
        }
        if (damageAgeTicks < CALVING_AGE_TICKS) {
            return PostMaeveMoonStage.STRESSED;
        }
        if (damageAgeTicks < RAGGED_AGE_TICKS) {
            return PostMaeveMoonStage.CALVING;
        }
        if (damageAgeTicks < RING_AGE_TICKS) {
            return PostMaeveMoonStage.RAGGED;
        }
        return PostMaeveMoonStage.RINGING;
    }

    /** Reaches its final apparent size on the last canonical aging day. */
    public static float apparentSizeScale(long damageAgeTicks) {
        if (damageAgeTicks <= 0L) {
            return 1.0F;
        }
        float agingProgress = clamp(
                damageAgeTicks / (float) RING_AGE_TICKS, 0.0F, 1.0F);
        return 1.0F + agingProgress * (MAXIMUM_APPARENT_SIZE_SCALE - 1.0F);
    }

    /**
     * Preserves a continuous once-per-day arc while varying angular velocity.
     * The derivative remains positive, so the Moon drifts and surges but never
     * visibly reverses direction.
     */
    public static float brokenOrbitDegrees(double dayTime, long visualSeed) {
        double turns = Math.max(0.0D, dayTime) / DAY_TICKS;
        double phaseA = seededPhase(visualSeed ^ 0x6A09E667F3BCC909L);
        double phaseB = seededPhase(visualSeed ^ 0xBB67AE8584CAA73BL);
        double phaseC = seededPhase(visualSeed ^ 0x3C6EF372FE94F82BL);
        double warpedTurns = turns
                + 0.055D * (Math.sin(TWO_PI * turns + phaseA)
                - Math.sin(phaseA))
                + 0.015D * (Math.sin(TWO_PI * 2.0D * turns + phaseB)
                - Math.sin(phaseB))
                + 0.070D * (Math.sin(TWO_PI * turns / 5.7D + phaseC)
                - Math.sin(phaseC));
        return wrapDegrees((float) (warpedTurns * 360.0D));
    }

    private static Snapshot hidden() {
        return new Snapshot(PostMaeveMoonStage.HIDDEN, -1L, -1L,
                0.0F, FIRST_RISE_START_ELEVATION, 0.0F, 60.0F,
                0.0F, 1.0F, 0, 0.0F, 0.0F);
    }

    private static Orbit orbit(long damageAge, long visualSeed) {
        double meanAnomaly = Math.floorMod(damageAge, ORBIT_PERIOD_TICKS)
                / (double) ORBIT_PERIOD_TICKS * TWO_PI;
        double eccentricAnomaly = meanAnomaly;
        for (int iteration = 0; iteration < 7; iteration++) {
            eccentricAnomaly -= (eccentricAnomaly
                    - ORBIT_ECCENTRICITY * Math.sin(eccentricAnomaly)
                    - meanAnomaly)
                    / (1.0D - ORBIT_ECCENTRICITY * Math.cos(eccentricAnomaly));
        }
        double trueAnomaly = 2.0D * Math.atan2(
                Math.sqrt(1.0D + ORBIT_ECCENTRICITY)
                        * Math.sin(eccentricAnomaly / 2.0D),
                Math.sqrt(1.0D - ORBIT_ECCENTRICITY)
                        * Math.cos(eccentricAnomaly / 2.0D));
        double orbitalAngle = START_TRUE_ANOMALY - trueAnomaly;
        double inclination = Math.toRadians(ORBIT_INCLINATION_DEGREES);
        double x = Math.cos(orbitalAngle);
        double y = -Math.sin(orbitalAngle) * Math.sin(inclination);
        double z = Math.sin(orbitalAngle) * Math.cos(inclination);
        double node = Math.toRadians(seededAscendingNode(visualSeed));
        double rotatedX = x * Math.cos(node) - z * Math.sin(node);
        double rotatedZ = x * Math.sin(node) + z * Math.cos(node);
        float elevation = (float) Math.toDegrees(Math.asin(clamp(y, -1.0D, 1.0D)));
        float azimuth = wrapDegrees((float) Math.toDegrees(
                Math.atan2(rotatedZ, rotatedX)));
        float illumination = wrapDegrees(60.0F
                + (float) Math.toDegrees(trueAnomaly));
        return new Orbit(azimuth, elevation, illumination);
    }

    private static float seededAscendingNode(long seed) {
        long mixed = seed ^ (seed >>> 33);
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return (float) Math.floorMod(mixed, 360L);
    }

    private static double seededPhase(long seed) {
        long mixed = seed;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53 * TWO_PI;
    }

    private static float progressBetween(long value, long start, long end) {
        if (end <= start) {
            return value >= end ? 1.0F : 0.0F;
        }
        return clamp((value - start) / (float) (end - start), 0.0F, 1.0F);
    }

    private static float smooth(float value) {
        float clamped = clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Snapshot(
            PostMaeveMoonStage stage,
            long elapsedTicks,
            long damageAgeTicks,
            float riseProgress,
            float elevationDegrees,
            float azimuthDegrees,
            float illuminationAngleDegrees,
            float fractureProgress,
            float discIntegrity,
            int debrisCount,
            float ringAlpha,
            float separationProgress) {
    }

    private record Orbit(
            float azimuthDegrees,
            float elevationDegrees,
            float illuminationAngleDegrees) {
    }
}
