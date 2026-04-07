package com.frozendawn.entity.architect;

/**
 * Server-authoritative mutable combat, retreat, and healing state for the Architect.
 */
public final class ArchitectCombatState {

    public int strafeDir = 1;
    public int strafeChangeCooldown;
    public int backoffTicks;
    public int healCooldown;
    public boolean isDrinkingPotion;
    public int drinkTicks;
    public int retreatPhase;
    public int retreatCoverBuilt;
    public float recentDamage;
    public int lastDamageTick;
    public int rangedHitsReceived;
}
