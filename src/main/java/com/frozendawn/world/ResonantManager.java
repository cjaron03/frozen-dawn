package com.frozendawn.world;

import com.frozendawn.aggregate.AggregatePressureHandler;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.ResonantPhaseController;
import com.frozendawn.entity.ResonantState;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;

import javax.annotation.Nullable;
import java.util.Comparator;

/** Debug surface and monotonic post-erasure age for Resonant evolution. */
public final class ResonantManager {
    private static long debugAgeTicks = -1L;

    private ResonantManager() {
    }

    public static long ticksSinceErasure(ServerLevel level) {
        if (debugAgeTicks >= 0L) return debugAgeTicks;
        long erasedAt = ReturnedHearthSavedData.get(level.getServer()).maeveErasedGameTime();
        return erasedAt < 0L ? 0L : Math.max(0L, level.getGameTime() - erasedAt);
    }

    public static void debugSetAgeDays(int days) {
        debugAgeTicks = Math.max(0L, days) * 24_000L;
    }

    public static String statusLine(ServerLevel level) {
        int loaded = 0;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof ResonantEntity) loaded++;
        }
        return "loaded=" + loaded + " events="
                + ResonanceEventManager.activeEventCount(level)
                + " ageDays=" + String.format("%.2f", ticksSinceErasure(level) / 24_000.0D)
                + (debugAgeTicks >= 0L ? " (debug)" : "");
    }

    @Nullable
    public static ResonantEntity debugSpawn(ServerPlayer player, ResonantState state) {
        ServerLevel level = player.serverLevel();
        BlockPos concealed = ResonantPhaseController.findConcealedSpawn(
                level, player.blockPosition().offset(5, 0, 0));
        if (concealed == null) return null;
        ResonantEntity entity = ModEntities.RESONANT.get().create(
                level, null, concealed, MobSpawnType.COMMAND, true, false);
        if (entity == null) return null;
        AggregatePressureHandler.markIgnored(entity);
        entity.setActivityState(state);
        if (state == ResonantState.STALKING) {
            entity.forceMarkedTarget(player);
            entity.setActivityState(ResonantState.STALKING);
        }
        if (!level.addFreshEntity(entity)) {
            entity.discard();
            return null;
        }
        return entity;
    }

    @Nullable
    public static ResonantEntity nearest(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(ResonantEntity.class,
                        player.getBoundingBox().inflate(96.0D))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    public static int purgeLoaded(ServerLevel level, BlockPos center, double radius) {
        int removed = 0;
        for (ResonantEntity entity : level.getEntitiesOfClass(
                ResonantEntity.class, new net.minecraft.world.phys.AABB(center).inflate(radius))) {
            entity.discard();
            removed++;
        }
        ResonanceEventManager.clear(level);
        return removed;
    }
}
