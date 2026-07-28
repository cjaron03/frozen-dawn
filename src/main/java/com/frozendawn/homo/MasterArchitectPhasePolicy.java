package com.frozendawn.homo;

/** Pure transition rules for the persisted Master Architect fight phase. */
public final class MasterArchitectPhasePolicy {
    public static final float CONSTRUCTION_THRESHOLD = 0.75F;
    public static final float TETHER_THRESHOLD = 0.50F;
    public static final float ASCENT_THRESHOLD = 0.30F;
    public static final float FLOOD_THRESHOLD = 0.10F;
    private static final float THRESHOLD_EPSILON = 0.000001F;
    // The boss bar can visually read as 10% slightly above the exact float
    // threshold. This single tolerance owns both phase entry and Flood startup.
    private static final float FLOOD_THRESHOLD_EPSILON = 0.001F;

    private MasterArchitectPhasePolicy() {
    }

    public static MasterArchitectCombatPhase phaseForHealth(
            float health, float maxHealth) {
        if (maxHealth <= 0.0F || !Float.isFinite(maxHealth)) {
            return MasterArchitectCombatPhase.KIT;
        }
        float safeHealth = Math.max(0.0F, health);
        float fraction = safeHealth / maxHealth;
        if (isAtFloodEntry(safeHealth, maxHealth)) {
            return MasterArchitectCombatPhase.FLOOD;
        }
        if (fraction <= ASCENT_THRESHOLD + THRESHOLD_EPSILON) {
            return MasterArchitectCombatPhase.ASCENT;
        }
        if (fraction <= TETHER_THRESHOLD + THRESHOLD_EPSILON) {
            return MasterArchitectCombatPhase.TETHER;
        }
        if (fraction <= CONSTRUCTION_THRESHOLD + THRESHOLD_EPSILON) {
            return MasterArchitectCombatPhase.CONSTRUCTION;
        }
        return MasterArchitectCombatPhase.KIT;
    }

    public static MasterArchitectCombatPhase advance(
            MasterArchitectCombatPhase current, float health, float maxHealth) {
        MasterArchitectCombatPhase safeCurrent = current == null
                ? MasterArchitectCombatPhase.KIT
                : current;
        MasterArchitectCombatPhase healthPhase = phaseForHealth(health, maxHealth);
        return safeCurrent.isBefore(healthPhase) ? healthPhase : safeCurrent;
    }

    public static boolean isAtFloodEntry(float health, float maxHealth) {
        if (maxHealth <= 0.0F || !Float.isFinite(maxHealth)) {
            return false;
        }
        return Math.max(0.0F, health) / maxHealth
                <= FLOOD_THRESHOLD + FLOOD_THRESHOLD_EPSILON;
    }

    public static MasterArchitectCombatPhase migrateLegacyState(
            float health,
            float maxHealth,
            boolean tetherUsed,
            boolean lastWallUsed) {
        MasterArchitectCombatPhase phase = phaseForHealth(health, maxHealth);
        if (lastWallUsed && phase.isBefore(MasterArchitectCombatPhase.ASCENT)) {
            return MasterArchitectCombatPhase.ASCENT;
        }
        if (tetherUsed && phase.isBefore(MasterArchitectCombatPhase.TETHER)) {
            return MasterArchitectCombatPhase.TETHER;
        }
        return phase;
    }

    public static float clampFloodEntryDamage(
            MasterArchitectCombatPhase current,
            float health,
            float maxHealth,
            float incomingDamage,
            boolean bypassesInvulnerability) {
        if (incomingDamage <= 0.0F
                || maxHealth <= 0.0F) {
            return Math.max(0.0F, incomingDamage);
        }
        float floodHealth = maxHealth * FLOOD_THRESHOLD;
        if (health <= floodHealth) {
            return 0.0F;
        }
        if (health - incomingDamage >= floodHealth) {
            return incomingDamage;
        }
        return Math.max(0.0F, health - floodHealth);
    }

    public static float nextThreshold(MasterArchitectCombatPhase phase) {
        return switch (phase) {
            case KIT -> CONSTRUCTION_THRESHOLD;
            case CONSTRUCTION -> TETHER_THRESHOLD;
            case TETHER -> ASCENT_THRESHOLD;
            case ASCENT -> FLOOD_THRESHOLD;
            case FLOOD -> 0.0F;
        };
    }
}
