package com.frozendawn.homo;

/** Pure combat timing and balance rules for the Master Architect. */
public final class MasterArchitectCombatPolicy {
    public static final double STAFF_RANGE = 3.35D;
    public static final double CONTINUITY_RANGE = 16.0D;
    public static final double THERMAL_RANGE = 14.0D;

    public static final int STAFF_STRIKE_TICK = 6;
    public static final int STAFF_ACTION_TICKS = 12;
    public static final int CONTINUITY_RELEASE_TICK = 22;
    public static final int CONTINUITY_ACTION_TICKS = 28;
    public static final int CONTINUITY_EFFECT_TICKS = 50;
    public static final int THERMAL_RELEASE_TICK = 28;
    public static final int THERMAL_ACTION_TICKS = 34;
    public static final int THERMAL_ACTIVE_TICKS = 100;
    public static final int THERMAL_RECOVERY_TICKS = 60;
    public static final int LAST_WALL_CAST_TICKS = 16;
    public static final int LAST_WALL_HEAL_TICKS = 100;
    public static final int LAST_WALL_LIFETIME_TICKS = 240;
    public static final int STORM_MAINTENANCE_ACTION_TICKS = 52;
    public static final int DEATH_CHARGE_START_TICK = 14;
    public static final int DEATH_LOCK_TICK = 60;
    public static final int DEATH_DETONATION_TICK = 70;

    public static final int CONTINUITY_COOLDOWN_MIN = 360;
    public static final int CONTINUITY_COOLDOWN_VARIANCE = 120;
    public static final int THERMAL_COOLDOWN_MIN = 500;
    public static final int THERMAL_COOLDOWN_VARIANCE = 140;
    public static final int SHARED_SPELL_COOLDOWN_TICKS = 60;
    public static final int STAFF_COOLDOWN_TICKS = 24;
    public static final int STORM_MAINTENANCE_COOLDOWN_MIN = 300;
    public static final int STORM_MAINTENANCE_COOLDOWN_VARIANCE = 200;

    public static final float THERMAL_SINK_CELSIUS = -220.0F;
    public static final float THERMAL_PULSE_DAMAGE = 4.0F;
    public static final int THERMAL_PULSE_COUNT = 4;
    public static final float LAST_WALL_TRIGGER_HEALTH_FRACTION = 0.30F;
    public static final float LAST_WALL_MAX_HEAL_FRACTION = 0.25F;
    public static final double DEATH_BLAST_RADIUS = 5.0D;
    public static final float DEATH_BLAST_MIN_DAMAGE = 2.0F;
    public static final float DEATH_BLAST_MAX_DAMAGE = 6.0F;

    private MasterArchitectCombatPolicy() {
    }

    public static boolean canCast(
            double distanceSquared, double range, boolean hasLineOfSight, int cooldown) {
        return cooldown <= 0
                && hasLineOfSight
                && distanceSquared <= range * range;
    }

    public static boolean shouldUseLastWall(
            float health, float maxHealth, boolean alreadyUsed) {
        return !alreadyUsed
                && maxHealth > 0.0F
                && health > 0.0F
                && health / maxHealth <= LAST_WALL_TRIGGER_HEALTH_FRACTION;
    }

    public static boolean shouldMaintainStorm(
            double distanceSquared,
            int sharedSpellCooldown,
            int maintenanceCooldown) {
        return maintenanceCooldown <= 0
                && sharedSpellCooldown > 20
                && distanceSquared > STAFF_RANGE * STAFF_RANGE;
    }

    public static float deathChargeProgress(float deathTicks) {
        return clamp01((deathTicks - DEATH_CHARGE_START_TICK)
                / (DEATH_LOCK_TICK - (float) DEATH_CHARGE_START_TICK));
    }

    public static float deathShakeStrength(float deathTicks) {
        float charge = deathChargeProgress(deathTicks);
        float lock = clamp01((deathTicks - DEATH_LOCK_TICK)
                / (DEATH_DETONATION_TICK - (float) DEATH_LOCK_TICK));
        return charge * (1.0F - lock);
    }

    public static float deathBlastDamage(double distance) {
        float falloff = 1.0F - clamp01((float) (distance / DEATH_BLAST_RADIUS));
        return DEATH_BLAST_MIN_DAMAGE
                + (DEATH_BLAST_MAX_DAMAGE - DEATH_BLAST_MIN_DAMAGE) * falloff;
    }

    public static int thermalPulseCountAt(int elapsedTicks) {
        if (elapsedTicks < 20) {
            return 0;
        }
        return Math.min(THERMAL_PULSE_COUNT, elapsedTicks / 20);
    }

    public static int thermalSlownessAmplifierAt(int elapsedTicks) {
        if (elapsedTicks < 0 || elapsedTicks >= THERMAL_ACTIVE_TICKS) {
            return -1;
        }
        return elapsedTicks < 40 ? 3 : 2;
    }

    public static float adjustedTemperature(
            float normalTemperature, int elapsedTicks) {
        if (elapsedTicks < 0) {
            return normalTemperature;
        }
        if (elapsedTicks < THERMAL_ACTIVE_TICKS) {
            return Math.min(normalTemperature, THERMAL_SINK_CELSIUS);
        }
        int recoveryTicks = elapsedTicks - THERMAL_ACTIVE_TICKS;
        if (recoveryTicks >= THERMAL_RECOVERY_TICKS) {
            return normalTemperature;
        }
        float recovery = recoveryTicks / (float) THERMAL_RECOVERY_TICKS;
        float sink = Math.min(normalTemperature, THERMAL_SINK_CELSIUS);
        return sink + (normalTemperature - sink) * recovery;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
