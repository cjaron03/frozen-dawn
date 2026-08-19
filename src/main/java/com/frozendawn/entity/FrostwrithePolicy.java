package com.frozendawn.entity;

import net.minecraft.util.Mth;

/** Pure Frostwrithe tuning, progression, and colony-lifecycle policy. */
public final class FrostwrithePolicy {
    public static final int AMBIENT_CLUSTER_MIN_MITES = 6;
    public static final int AMBIENT_CLUSTER_MAX_MITES = 10;
    public static final double AMBIENT_CLUSTER_RADIUS = 6.0D;
    public static final int AMBIENT_CLUSTER_DWELL_TICKS = 400;
    public static final int AMBIENT_CLUSTER_DWELL_VARIANCE = 80;
    public static final int AMBIENT_CLUSTER_RETRY_TICKS = 200;
    public static final int AMBIENT_FAILURE_BACKOFF_TICKS = 1_200;
    public static final float AMBIENT_CLUSTER_FORMATION_CHANCE = 0.40F;
    public static final int ASSEMBLY_RETRY_TICKS = 60;
    public static final int ASSEMBLY_WARNING_INTERVAL_TICKS = 200;
    public static final int ASSEMBLY_TICKS = 60;
    public static final int DISASSEMBLY_TICKS = 18;
    public static final int BURROW_MAX_TICKS = 100;
    public static final int ERUPTION_TICKS = 18;
    public static final int SHELL_TICKS = 80;
    public static final int CLIMB_TICKS = 42;
    public static final int BRIDGE_TICKS = 30;
    public static final int OVERRUN_TICKS = 30;
    public static final int MIN_REPRESENTATIVES = 4;
    public static final int MIN_REASSEMBLY_BIOMASS = 40;
    public static final int MAX_REPRESENTATIVES = 10;
    public static final int MAX_BIOMASS = 100;
    public static final double MIMIC_RADIUS = 80.0D;
    public static final int PATROL_MIN_DISTANCE = 9;
    public static final int PATROL_MAX_DISTANCE = 17;
    public static final int PATROL_REPLAN_TICKS = 20;
    public static final int PATROL_MEMORY_SIZE = 8;

    private FrostwrithePolicy() {
    }

    public static boolean ambientClusterForms(float roll) {
        return roll < AMBIENT_CLUSTER_FORMATION_CHANCE;
    }

    public static boolean mayRetryAssembly(long now, long retryAt) {
        return now >= retryAt;
    }

    public static boolean shouldLogAssemblyFailure(long now, long lastWarningTick) {
        return lastWarningTick < 0L
                || now - lastWarningTick >= ASSEMBLY_WARNING_INTERVAL_TICKS;
    }

    public static float baseEvolutionChance(long ticksSinceErasure) {
        long day = Math.max(0L, ticksSinceErasure) / 24_000L;
        if (day < 1L) return 0.0F;
        if (day < 3L) return 0.05F;
        if (day < 7L) return 0.12F;
        return 0.22F;
    }

    public static float evolutionChance(long ticksSinceErasure, float bloomPressure,
                                         double multiplier, boolean infestedBreak) {
        float bloomBonus = Mth.clamp((bloomPressure - 1.0F) / 1.25F,
                0.0F, 1.0F) * 0.08F;
        float chance = (baseEvolutionChance(ticksSinceErasure) + bloomBonus)
                * (float) Math.max(0.0D, multiplier);
        if (infestedBreak) chance *= 1.5F;
        return Mth.clamp(chance, 0.0F, infestedBreak ? 0.35F : 0.30F);
    }

    public static float cohesionDamage(float incomingDamage, boolean fire,
                                       boolean explosion, boolean sweeping,
                                       boolean projectile) {
        float multiplier = fire ? 3.0F
                : explosion || sweeping ? 2.0F
                : 1.0F;
        return Math.max(1.0F, incomingDamage * multiplier);
    }

    public static int cohesionBand(float cohesion) {
        if (cohesion < 15.0F) return 3;
        if (cohesion < 40.0F) return 2;
        if (cohesion < 70.0F) return 1;
        return 0;
    }

    public static int visibleBodies(float cohesion) {
        return switch (cohesionBand(cohesion)) {
            case 1 -> 7;
            case 2 -> 4;
            case 3 -> 2;
            default -> 10;
        };
    }

    public static int representativeCount(int biomass) {
        return Mth.clamp(Math.round(MAX_REPRESENTATIVES
                * Mth.clamp(biomass, 0, MAX_BIOMASS) / 100.0F),
                MIN_REPRESENTATIVES, MAX_REPRESENTATIVES);
    }

    public static boolean mayReassemble(int representatives, int biomass,
                                        boolean rallyLoaded, boolean repelled) {
        return representatives >= MIN_REPRESENTATIVES
                && biomass >= MIN_REASSEMBLY_BIOMASS
                && rallyLoaded && !repelled;
    }

    public static float reformedHealth(int biomass, float maxHealth) {
        return Math.max(1.0F, maxHealth
                * Mth.clamp(biomass, MIN_REASSEMBLY_BIOMASS, MAX_BIOMASS)
                / MAX_BIOMASS);
    }

    public static int splitBiomass(int totalBiomass, int representativeCount,
                                   int index) {
        if (representativeCount <= 0 || index < 0 || index >= representativeCount) {
            return 0;
        }
        int base = totalBiomass / representativeCount;
        return base + (index < totalBiomass % representativeCount ? 1 : 0);
    }

    /** Parrot-like variation around a deliberately sharper colony voice. */
    public static float mimicPitch(float firstRandom, float secondRandom) {
        return 1.65F + (firstRandom - secondRandom) * 0.2F;
    }

    static double patrolScore(double forwardAlignment, double distance,
                              double recentDistance, double colonyDistance,
                              double randomNoise) {
        return forwardAlignment * 7.0D
                + Math.min(distance, PATROL_MAX_DISTANCE) * 0.25D
                + Math.min(recentDistance, 12.0D) * 0.45D
                + Math.min(colonyDistance, 24.0D) * 0.18D
                + randomNoise * 1.5D;
    }
}
