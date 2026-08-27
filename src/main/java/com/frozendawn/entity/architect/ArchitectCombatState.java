package com.frozendawn.entity.architect;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

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
    @Nullable
    public Vec3 retreatStartPosition;
    public int retreatRunTicks;
    public float recentDamage;
    public int lastDamageTick;
    public int rangedHitsReceived;
}
