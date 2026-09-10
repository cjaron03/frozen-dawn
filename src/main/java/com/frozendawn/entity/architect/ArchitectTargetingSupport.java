package com.frozendawn.entity.architect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.Predicate;

/**
 * Target selection rules for Architect target acquisition.
 */
public final class ArchitectTargetingSupport {

    private ArchitectTargetingSupport() {
    }

    @Nullable
    public static LivingEntity findTarget(
            Level level,
            LivingEntity actor,
            boolean roamingAfterTargetLoss,
            double baseRange,
            double observeReacquireRange,
            ToDoubleFunction<LivingEntity> distanceToSqr,
            Predicate<LivingEntity> canTarget
    ) {
        double playerRange = roamingAfterTargetLoss ? observeReacquireRange : baseRange;
        // Find nearest survival/adventure player (exclude creative & spectator).
        Player nearestPlayer = level.getNearestPlayer(
                actor.getX(), actor.getY(), actor.getZ(), playerRange,
                candidate -> candidate instanceof Player player
                        && player.isAlive()
                        && !player.isCreative()
                        && !player.isSpectator()
                        && canTarget.test(player));
        if (nearestPlayer != null) {
            return nearestPlayer;
        }

        // Fallback: target nearest villager (useful for testing & gameplay).
        AABB queryBox = actor.getBoundingBox().inflate(baseRange);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, queryBox, v -> v.isAlive() && canTarget.test(v));
        if (villagers.isEmpty()) {
            return null;
        }
        villagers.sort(Comparator.comparingDouble(distanceToSqr::applyAsDouble));
        return villagers.get(0);
    }
}
