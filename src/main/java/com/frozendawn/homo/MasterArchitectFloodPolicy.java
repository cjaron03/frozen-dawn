package com.frozendawn.homo;

import net.minecraft.util.Mth;

/** Shared, deterministic tuning for the Master Architect's final Flood. */
public final class MasterArchitectFloodPolicy {
    public static final double RADIUS = 15.0D;
    public static final double MELEE_DISTANCE = 1.5D;
    public static final double MEMORY_LOCK_DISTANCE = 4.0D;
    public static final int RETREAT_GRACE_TICKS = 40;
    public static final int RETREAT_RECOVERY_TICKS = 200;
    public static final int RETREAT_REARM_TICKS = 500;
    public static final int RUSH_GRACE_TICKS = 40;
    public static final int RUSH_DAMAGE_INTERVAL_TICKS = 30;
    public static final int MOTE_STAGGER_TICKS = 10;
    public static final int IVEN_STACK_CAP = 5;
    public static final int IVEN_STACK_DECAY_TICKS = 240;
    public static final int BRUTAL_IVEN_STACK_DECAY_TICKS = 180;
    public static final int MOTE_RESPAWN_TICKS = 300;
    public static final int CORE_EXPOSURE_TICKS = 100;
    public static final int CORE_REVEAL_TICKS = 28;
    public static final int MIND_DEATH_DISINTEGRATION_TICKS = 100;
    public static final int REQUIRED_EXPOSURES = 3;
    public static final int COPY_HEAL_INTERVAL_TICKS = 200;
    public static final int SURGE_TELEGRAPH_TICKS = 50;
    public static final float ENTRY_HEALTH_FRACTION = 0.10F;
    public static final float RETREAT_HEALTH_FRACTION = 0.12F;
    public static final float THRONE_EJECTION_HEALTH_FRACTION = 0.50F;
    public static final float MINIMUM_HEALING_STRENGTH = 0.15F;
    public static final double MEMORY_LOCK_EPSILON = 0.06D;

    private MasterArchitectFloodPolicy() {
    }

    public static float strength(int survivingResidents, int maximumResidents) {
        if (maximumResidents <= 0) {
            return 0.0F;
        }
        return Mth.clamp(survivingResidents / (float) maximumResidents, 0.0F, 1.0F);
    }

    public static float proximity(double distance) {
        double clamped = Mth.clamp(distance, MELEE_DISTANCE, RADIUS);
        return (float) (1.0D
                - (clamped - MELEE_DISTANCE) / (RADIUS - MELEE_DISTANCE));
    }

    /**
     * ADD_MULTIPLIED_TOTAL amount. A full congregation reaches a 90% reduction
     * at melee; even a devastated Hearth still produces a readable drag.
     */
    public static double movementModifier(float proximity, float floodStrength) {
        float t = Mth.clamp(proximity, 0.0F, 1.0F);
        float strength = Mth.clamp(floodStrength, 0.0F, 1.0F);
        double slowProgress = Mth.clamp((t - 0.25D) / 0.75D, 0.0D, 1.0D);
        double maximumDrag = Mth.lerp(strength, 0.72D, 0.94D);
        return -Math.pow(slowProgress, 1.4D) * maximumDrag;
    }

    public static double staggerMovementModifier(float floodStrength) {
        return -Mth.clamp(0.92D + floodStrength * 0.06D, 0.92D, 0.98D);
    }

    public static int moteCount(float floodStrength) {
        return Mth.clamp(Math.round(2.0F + 5.0F * floodStrength), 2, 7);
    }

    public static boolean isInsideMemoryLock(double playerDistance) {
        return playerDistance < MEMORY_LOCK_DISTANCE - MEMORY_LOCK_EPSILON;
    }

    public static float overlayAlpha(float proximity, float floodStrength) {
        float t = Mth.clamp(proximity, 0.0F, 1.0F);
        float strength = Mth.clamp(floodStrength, 0.0F, 1.0F);
        return Mth.clamp(t * (0.22F + 0.46F * strength), 0.0F, 0.68F);
    }

    public static float hudAlpha(float proximity, float floodStrength) {
        float obscuration = Mth.clamp(
                proximity * (0.45F + 0.53F * floodStrength), 0.0F, 0.98F);
        return 1.0F - obscuration;
    }

    public static float rushDamage(
            int ticksSinceLastMote, float floodStrength, float proximity) {
        if (ticksSinceLastMote <= RUSH_GRACE_TICKS) {
            return 0.0F;
        }
        int elapsed = ticksSinceLastMote - RUSH_GRACE_TICKS;
        float proximityPressure = Mth.clamp((proximity - 0.20F) / 0.65F, 0.0F, 1.0F);
        return Mth.clamp(
                0.35F + elapsed / 240.0F
                        + floodStrength * 0.35F
                        + proximityPressure * 2.0F,
                0.35F,
                3.25F);
    }

    public static int stackDecayTicks(boolean brutal) {
        return brutal ? BRUTAL_IVEN_STACK_DECAY_TICKS : IVEN_STACK_DECAY_TICKS;
    }

    public static float stackDamageMultiplier(int stacks) {
        int clamped = Mth.clamp(stacks, 0, IVEN_STACK_CAP);
        return 0.15F + 0.17F * clamped;
    }

    public static float copyHealRate(String presetName) {
        if ("brutal".equalsIgnoreCase(presetName)) {
            return 0.08F;
        }
        if ("cinematic".equalsIgnoreCase(presetName)) {
            return 0.04F;
        }
        return 0.06F;
    }

    /**
     * A destroyed congregation leaves the throne badly weakened, not inert.
     * This keeps the Fold mechanically complete even when no residents survive.
     */
    public static float healingStrength(float floodStrength) {
        return Mth.clamp(Math.max(MINIMUM_HEALING_STRENGTH, floodStrength),
                0.0F, 1.0F);
    }

    public static int healingTier(
            String presetName,
            int pressureTicks,
            int normalTierTwoTicks,
            int normalTierThreeTicks,
            int brutalTierTwoTicks,
            int brutalTierThreeTicks) {
        if ("cinematic".equalsIgnoreCase(presetName)) {
            return 1;
        }
        int tierTwoTicks = "brutal".equalsIgnoreCase(presetName)
                ? brutalTierTwoTicks : normalTierTwoTicks;
        int tierThreeTicks = "brutal".equalsIgnoreCase(presetName)
                ? brutalTierThreeTicks : normalTierThreeTicks;
        if (pressureTicks >= Math.max(tierThreeTicks, tierTwoTicks)) {
            return 3;
        }
        return pressureTicks >= tierTwoTicks ? 2 : 1;
    }

    public static float healingTierMultiplier(
            String presetName,
            int healingTier,
            float tierTwoMultiplier,
            float tierThreeMultiplier,
            float brutalTierThreeMultiplier) {
        if (healingTier <= 1 || "cinematic".equalsIgnoreCase(presetName)) {
            return 1.0F;
        }
        if (healingTier == 2) {
            return Math.max(1.0F, tierTwoMultiplier);
        }
        return Math.max(1.0F, "brutal".equalsIgnoreCase(presetName)
                ? brutalTierThreeMultiplier : tierThreeMultiplier);
    }

    public static float failedFoldHealthCap(String presetName) {
        if ("brutal".equalsIgnoreCase(presetName)) {
            return 0.60F;
        }
        if ("cinematic".equalsIgnoreCase(presetName)) {
            return 0.50F;
        }
        return 0.55F;
    }

    public static int surgeDelayTicks(int exposureCycle, int randomOffset) {
        return Math.max(220, 400 + Mth.clamp(randomOffset, 0, 100)
                - 40 * Math.max(0, exposureCycle));
    }

    public static float exposureIntensity(int exposureCycle) {
        return 1.0F + 0.15F * Mth.clamp(exposureCycle, 0, REQUIRED_EXPOSURES);
    }
}
