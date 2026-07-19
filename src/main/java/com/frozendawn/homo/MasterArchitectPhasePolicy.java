package com.frozendawn.homo;

/** Pure transition rules for the persisted Master Architect fight phase. */
public final class MasterArchitectPhasePolicy {
    public static final float CONSTRUCTION_THRESHOLD = 0.75F;
    public static final float TETHER_THRESHOLD = 0.50F;
    public static final float ASCENT_THRESHOLD = 0.30F;
    public static final float FLOOD_THRESHOLD = 0.10F;

    private MasterArchitectPhasePolicy() {
    }

    public static MasterArchitectCombatPhase phaseForHealth(
            float health, float maxHealth) {
        if (maxHealth <= 0.0F || !Float.isFinite(maxHealth)) {
            return MasterArchitectCombatPhase.KIT;
        }
        float fraction = Math.max(0.0F, health) / maxHealth;
        if (fraction <= FLOOD_THRESHOLD) {
            return MasterArchitectCombatPhase.FLOOD;
        }
        if (fraction <= ASCENT_THRESHOLD) {
            return MasterArchitectCombatPhase.ASCENT;
        }
        if (fraction <= TETHER_THRESHOLD) {
            return MasterArchitectCombatPhase.TETHER;
        }
        if (fraction <= CONSTRUCTION_THRESHOLD) {
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
                || maxHealth <= 0.0F
                || bypassesInvulnerability
                || current == MasterArchitectCombatPhase.FLOOD) {
            return Math.max(0.0F, incomingDamage);
        }
        float floodHealth = maxHealth * FLOOD_THRESHOLD;
        if (health <= floodHealth || health - incomingDamage >= floodHealth) {
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
