package com.frozendawn.world;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.RimeLanceEntity;
import com.frozendawn.entity.RimeboundBurrowController;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.RimeboundState;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.Comparator;

/** Debug surface and monotonic post-erasure age used by spawn evolution. */
public final class RimeboundManager {
    private static long debugAgeTicks = -1L;

    private RimeboundManager() {
    }

    public static long ticksSinceErasure(ServerLevel level) {
        if (debugAgeTicks >= 0L) {
            return debugAgeTicks;
        }
        long erasedAt = ReturnedHearthSavedData.get(level.getServer())
                .maeveErasedGameTime();
        return erasedAt < 0L ? 0L : Math.max(0L, level.getGameTime() - erasedAt);
    }

    public static void debugSetAgeDays(int days) {
        debugAgeTicks = Math.max(0L, days) * 24_000L;
    }

    public static void reset() {
        debugAgeTicks = -1L;
    }

    public static String statusLine(ServerLevel level) {
        int rimebound = 0;
        int lances = 0;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof RimeboundEntity) {
                rimebound++;
            } else if (entity instanceof RimeLanceEntity) {
                lances++;
            }
        }
        return "loaded=" + rimebound
                + " lances=" + lances
                + " ageDays=" + String.format("%.2f",
                ticksSinceErasure(level) / 24_000.0D)
                + (debugAgeTicks >= 0L ? " (debug)" : "");
    }

    @Nullable
    public static RimeboundEntity debugSpawn(ServerPlayer player, boolean dormant) {
        ServerLevel level = player.serverLevel();
        BlockPos probe = player.blockPosition().offset(6, 0, 3);
        BlockPos spawn = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(probe.getX(), 0, probe.getZ()));
        if (!level.isLoaded(spawn) || !level.getBlockState(spawn).getCollisionShape(level, spawn).isEmpty()
                || !level.getBlockState(spawn.above()).getCollisionShape(level, spawn.above()).isEmpty()) {
            return null;
        }
        RimeboundEntity entity = ModEntities.RIMEBOUND.get().create(
                level, null, spawn, MobSpawnType.COMMAND, true, false);
        if (entity == null) {
            return null;
        }
        entity.setActivityState(dormant && RimeboundBurrowController.validDormantTerrain(level, spawn)
                ? RimeboundState.DORMANT : RimeboundState.STALKING);
        level.addFreshEntity(entity);
        if (!dormant) {
            entity.setTarget(player);
        }
        return entity;
    }

    @Nullable
    public static RimeboundEntity nearest(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(RimeboundEntity.class,
                        player.getBoundingBox().inflate(96.0D))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    public static int purgeLoaded(ServerLevel level, BlockPos center, double radius) {
        int removed = 0;
        var box = new net.minecraft.world.phys.AABB(center).inflate(radius);
        for (RimeboundEntity entity : level.getEntitiesOfClass(RimeboundEntity.class, box)) {
            entity.discard();
            removed++;
        }
        for (RimeLanceEntity lance : level.getEntitiesOfClass(RimeLanceEntity.class, box)) {
            lance.discard();
            removed++;
        }
        return removed;
    }
}
