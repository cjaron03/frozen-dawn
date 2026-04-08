package com.frozendawn.entity.architect;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Shared per-tick state maintenance helpers for Architect server AI.
 */
public final class ArchitectTickSupport {

    private ArchitectTickSupport() {
    }

    public static void applyPerTickCooldowns(
            ArchitectCombatState combatState,
            ArchitectApproachState approachState,
            ArchitectBrainState brainState
    ) {
        if (combatState.healCooldown > 0) {
            combatState.healCooldown--;
        }
        if (approachState.fallbackBreakCooldown > 0) {
            approachState.fallbackBreakCooldown--;
        }
        if (brainState.getMeleeCommitTicks() > 0) {
            brainState.setMeleeCommitTicks(brainState.getMeleeCommitTicks() - 1);
        }
    }

    public static float decayRecentDamageOutsideBurst(
            int tickCount,
            int lastDamageTick,
            int burstWindow,
            float recentDamage
    ) {
        if (tickCount - lastDamageTick > burstWindow) {
            return 0f;
        }
        return recentDamage;
    }

    public static int nextDespawnTimer(
            Level level,
            AABB actorBounds,
            boolean towerEncounter,
            boolean hasTarget,
            int currentDespawnTimer,
            int despawnTimeout
    ) {
        if (hasTarget || towerEncounter) {
            return 0;
        }

        boolean playerNearby = !level.getEntitiesOfClass(
                Player.class,
                actorBounds.inflate(48.0),
                p -> !p.isSpectator()).isEmpty();
        if (playerNearby) {
            return 0;
        }

        int next = currentDespawnTimer + 1;
        if (next >= despawnTimeout) {
            return -1;
        }
        return next;
    }
}
