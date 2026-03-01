package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;

public class FrostbittenSpawner {

    private FrostbittenSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        if (currentPhase < 4) return;
        if (!FrozenDawnConfig.ENABLE_FROSTBITTEN.get()) return;
        if (currentPhase >= 6 && progress >= 0.92f) return;

        long gameTick = level.getGameTime();
        if (gameTick % 100 != 0) return; // Every 5 seconds

        RandomSource random = level.random;

        // Phase-based spawn chance and density
        float spawnChance;
        int maxNearby;
        int groupSize;
        if (currentPhase == 4) {
            spawnChance = 0.25f;
            maxNearby = 4;
            groupSize = 2; // 1-2
        } else if (currentPhase == 5) {
            spawnChance = 0.65f;
            maxNearby = 12;
            groupSize = 4; // 1-4, like vanilla zombies
        } else {
            spawnChance = 0.12f;
            maxNearby = 3;
            groupSize = 2;
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            if (random.nextFloat() > spawnChance) continue;

            int nearbyCount = level.getEntitiesOfClass(FrostbittenEntity.class,
                    player.getBoundingBox().inflate(48.0)).size();
            if (nearbyCount >= maxNearby) continue;

            int spawnCount = 1 + random.nextInt(groupSize);
            spawnCount = Math.min(spawnCount, maxNearby - nearbyCount);
            if (spawnCount <= 0) continue;

            // Find one group spawn point, then cluster the group around it
            BlockPos groupCenter = findSpawnPos(level, player, random);
            if (groupCenter == null) continue;

            int spawned = 0;
            for (int i = 0; i < spawnCount; i++) {
                // First mob at center, rest offset 1-3 blocks nearby
                BlockPos spawnPos;
                if (i == 0) {
                    spawnPos = groupCenter;
                } else {
                    spawnPos = findNearbySpawnPos(level, groupCenter, random);
                    if (spawnPos == null) spawnPos = groupCenter;
                }

                FrostbittenEntity mob = ModEntities.FROSTBITTEN.get().create(level, null, spawnPos,
                        MobSpawnType.NATURAL, true, false);
                if (mob != null) {
                    mob.setEmerging(true);
                    level.addFreshEntity(mob);
                    spawned++;
                }
            }
            if (spawned > 0) {
                FrozenDawn.LOGGER.info("[Frostbitten] Spawned group of {} near {} at phase {}", spawned, player.getName().getString(), currentPhase);
            }
        }
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 24 + random.nextInt(25); // 24-48 blocks
            int x = (int) (player.getX() + Math.cos(angle) * dist);
            int z = (int) (player.getZ() + Math.sin(angle) * dist);

            BlockPos surface = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));

            // Surface only — must be above Y=60 (sea level)
            if (surface.getY() < 60) continue;

            BlockPos below = surface.below();
            BlockState groundState = level.getBlockState(below);
            if (!groundState.isSolidRender(level, below)) continue;

            if (!level.getBlockState(surface).isAir()) continue;
            if (!level.getBlockState(surface.above()).isAir()) continue;

            return surface;
        }
        return null;
    }

    /** Find a spawn position 1-3 blocks from the group center. */
    private static BlockPos findNearbySpawnPos(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < 5; attempt++) {
            int dx = random.nextIntBetweenInclusive(-3, 3);
            int dz = random.nextIntBetweenInclusive(-3, 3);
            BlockPos candidate = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset(dx, 0, dz));

            if (candidate.getY() < 60) continue;
            if (!level.getBlockState(candidate).isAir()) continue;
            if (!level.getBlockState(candidate.above()).isAir()) continue;
            BlockPos below = candidate.below();
            if (!level.getBlockState(below).isSolidRender(level, below)) continue;

            return candidate;
        }
        return null;
    }

    public static void reset() {}
}
