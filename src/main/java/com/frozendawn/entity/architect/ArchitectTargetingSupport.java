package com.frozendawn.entity.architect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

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
            ToDoubleFunction<LivingEntity> distanceToSqr
    ) {
        double playerRange = roamingAfterTargetLoss ? observeReacquireRange : baseRange;
        // Find nearest survival/adventure player (exclude creative & spectator).
        Player nearestPlayer = level.getNearestPlayer(
                actor.getX(), actor.getY(), actor.getZ(), playerRange,
                candidate -> candidate instanceof Player player
                        && player.isAlive()
                        && !player.isCreative()
                        && !player.isSpectator());
        if (nearestPlayer != null) {
            return nearestPlayer;
        }

        // Fallback: target nearest villager (useful for testing & gameplay).
        AABB queryBox = actor.getBoundingBox().inflate(baseRange);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, queryBox, v -> v.isAlive());
        if (villagers.isEmpty()) {
            return null;
        }
        villagers.sort(Comparator.comparingDouble(distanceToSqr::applyAsDouble));
        return villagers.get(0);
    }

    /**
     * Whether a player is someone an Architect may target at all.
     *
     * <p>Creative and spectator players are not participants. Creative also means no
     * armor, which wins {@code HearthTargetPolicy.BY_VULNERABILITY} outright, so an
     * admin or builder standing nearby would take the commitment and hold it while
     * every survival player in range went unassessed.
     */
    public static boolean isTargetablePlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    /**
     * Every player still targetable anywhere on the server, in or out of range.
     *
     * <p>Feeds the commitment's presence check. A committed target missing from this set
     * has died, logged out or switched to creative and is forgotten; one that is present
     * but out of range is only suspended.
     *
     * <p>Creative is a forget rather than a suspend on purpose. A bookmark lets its owner
     * reclaim the commitment the instant it returns, without being re-scored against
     * whoever the Architect picked up meanwhile. Someone who toggled into creative and
     * back should not jump that queue.
     */
    public static Set<UUID> targetablePlayerIds(ServerLevel level) {
        Set<UUID> ids = new HashSet<>();
        for (ServerLevel dimension : level.getServer().getAllLevels()) {
            for (ServerPlayer player : dimension.players()) {
                if (isTargetablePlayer(player)) {
                    ids.add(player.getUUID());
                }
            }
        }
        return ids;
    }
}
