package com.frozendawn.world;

import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.FrostwritheState;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Debug surface and monotonic post-erasure age for Frostwrithe evolution. */
public final class FrostwritheManager {
    private static long debugAgeTicks = -1L;

    private FrostwritheManager() {
    }

    public static long ticksSinceErasure(ServerLevel level) {
        if (debugAgeTicks >= 0L) return debugAgeTicks;
        long erasedAt = ReturnedHearthSavedData.get(level.getServer())
                .maeveErasedGameTime();
        return erasedAt < 0L ? 0L : Math.max(0L, level.getGameTime() - erasedAt);
    }

    public static void debugSetAgeDays(int days) {
        debugAgeTicks = Math.max(0L, days) * 24_000L;
    }

    public static void reset() {
        debugAgeTicks = -1L;
        FrostwritheColonyManager.reset();
    }

    public static String statusLine(ServerLevel level) {
        int assembled = 0;
        int representatives = 0;
        for (var entity : level.getAllEntities()) {
            if (entity instanceof FrostwritheEntity) assembled++;
            else if (entity instanceof FrostmiteEntity mite && mite.hasColony()) {
                representatives++;
            }
        }
        return "loaded=" + assembled + " representatives=" + representatives
                + " ageDays=" + String.format("%.2f",
                ticksSinceErasure(level) / 24_000.0D)
                + (debugAgeTicks >= 0L ? " (debug)" : "");
    }

    @Nullable
    public static FrostwritheEntity debugSpawn(ServerPlayer player,
                                                FrostwritheState state) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        int[][] directions = {
                {1, 0}, {1, 1}, {0, 1}, {-1, 1},
                {-1, 0}, {-1, -1}, {0, -1}, {1, -1}
        };
        for (int radius = 4; radius <= 16; radius += 3) {
            for (int[] direction : directions) {
                int x = origin.getX() + direction[0] * radius;
                int z = origin.getZ() + direction[1] * radius;
                BlockPos sample = new BlockPos(x, origin.getY(), z);
                if (!level.isLoaded(sample)) continue;

                FrostwritheEntity entity = tryDebugColumn(
                        level, x, z, origin.getY(), state);
                if (entity == null) continue;
                if (state == FrostwritheState.CRAWLER) entity.setTarget(player);
                return entity;
            }
        }
        return null;
    }

    @Nullable
    private static FrostwritheEntity tryDebugColumn(
            ServerLevel level, int x, int z, int playerY,
            FrostwritheState state) {
        int top = Math.min(level.getMaxBuildHeight() - 2, playerY + 4);
        int bottom = Math.max(level.getMinBuildHeight() + 1, playerY - 8);
        for (int y = top; y >= bottom; y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (!hasGround(level, candidate)) continue;
            FrostwritheEntity entity = createAt(level, candidate,
                    MobSpawnType.COMMAND, UUID.randomUUID(), 100, state);
            if (entity != null) return entity;
        }

        BlockPos surface = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, 0, z));
        if (!hasGround(level, surface)) return null;
        return createAt(level, surface, MobSpawnType.COMMAND,
                UUID.randomUUID(), 100, state);
    }

    private static boolean hasGround(ServerLevel level, BlockPos spawn) {
        if (!level.isLoaded(spawn) || !level.isLoaded(spawn.below())) return false;
        BlockState ground = level.getBlockState(spawn.below());
        return !ground.getCollisionShape(level, spawn.below()).isEmpty()
                && level.getBlockState(spawn).getFluidState().isEmpty();
    }

    @Nullable
    public static FrostwritheEntity createAt(ServerLevel level, BlockPos spawn,
                                              MobSpawnType spawnType, UUID colonyId,
                                              int biomass, FrostwritheState state) {
        if (!level.isLoaded(spawn)) return null;
        FrostwritheEntity entity = ModEntities.FROSTWRITHE.get().create(
                level, null, spawn, spawnType, true, false);
        if (entity == null) return null;
        entity.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(entity)) return null;
        entity.initializeColony(colonyId, biomass, state);
        if (!level.addFreshEntity(entity)) {
            entity.discard();
            return null;
        }
        return entity;
    }

    @Nullable
    public static FrostwritheEntity nearest(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(FrostwritheEntity.class,
                        player.getBoundingBox().inflate(96.0D))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    public static boolean forceRegroup(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<FrostmiteEntity> colonyMites = level.getEntitiesOfClass(
                FrostmiteEntity.class, player.getBoundingBox().inflate(96.0D),
                FrostmiteEntity::hasColony);
        FrostmiteEntity nearest = colonyMites.stream()
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (nearest == null || nearest.colonyId() == null) return false;
        UUID colonyId = nearest.colonyId();
        BlockPos rally = nearest.colonyRallyPos() == null
                ? nearest.blockPosition() : nearest.colonyRallyPos();
        long now = level.getGameTime();
        for (FrostmiteEntity mite : colonyMites) {
            if (colonyId.equals(mite.colonyId())) {
                mite.forceColonyRally(colonyId, mite.colonyBiomassUnits(), rally,
                        now, now + 240L);
            }
        }
        FrostwritheColonyManager.forceDecisionNow(colonyId, now);
        return true;
    }

    public static int purgeLoaded(ServerLevel level, BlockPos center,
                                  double radius) {
        AABB bounds = new AABB(center).inflate(radius);
        int removed = 0;
        for (FrostwritheEntity entity : level.getEntitiesOfClass(
                FrostwritheEntity.class, bounds)) {
            entity.discard();
            removed++;
        }
        for (FrostmiteEntity mite : level.getEntitiesOfClass(
                FrostmiteEntity.class, bounds, FrostmiteEntity::hasColony)) {
            mite.discard();
            removed++;
        }
        FrostwritheColonyManager.reset();
        return removed;
    }
}
