package com.frozendawn.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

final class LateThreatSpawnHelper {
    private static final int SEA_LEVEL = 60;
    private static final int VERTICAL_SCAN_UP = 12;
    private static final int VERTICAL_SCAN_DOWN = 16;

    private LateThreatSpawnHelper() {}

    static BlockPos findSurfaceSpawn(ServerLevel level, ServerPlayer player, RandomSource random,
                                     int minDistance, int maxDistance, int attempts,
                                     boolean requireDark) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos sample = randomXZAround(player, random, minDistance, maxDistance);
            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(sample.getX(), 0, sample.getZ()));

            if (isValidSurfaceSpawn(level, surface, requireDark)) {
                return surface;
            }
        }
        return null;
    }

    static BlockPos findHybridSpawn(ServerLevel level, ServerPlayer player, RandomSource random,
                                    int minDistance, int maxDistance, int attempts,
                                    boolean requireDark) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            BlockPos sample = randomXZAround(player, random, minDistance, maxDistance);
            BlockPos vertical = findValidVerticalSpawn(level, sample.getX(), sample.getZ(),
                    player.getBlockY(), requireDark);
            if (vertical != null) {
                return vertical;
            }
        }

        return findSurfaceSpawn(level, player, random, minDistance, maxDistance, attempts, requireDark);
    }

    private static BlockPos randomXZAround(ServerPlayer player, RandomSource random,
                                           int minDistance, int maxDistance) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int distance = minDistance + random.nextInt(maxDistance - minDistance + 1);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        return new BlockPos(x, player.getBlockY(), z);
    }

    private static BlockPos findValidVerticalSpawn(ServerLevel level, int x, int z, int playerY,
                                                   boolean requireDark) {
        int topY = Math.min(level.getMaxBuildHeight() - 2, playerY + VERTICAL_SCAN_UP);
        int bottomY = Math.max(level.getMinBuildHeight() + 1, playerY - VERTICAL_SCAN_DOWN);

        for (int y = topY; y >= bottomY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isValidHybridSpawn(level, pos, requireDark)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isValidSurfaceSpawn(ServerLevel level, BlockPos pos, boolean requireDark) {
        return pos.getY() >= SEA_LEVEL
                && hasSkyAccess(level, pos)
                && hasValidMobSpace(level, pos, requireDark);
    }

    private static boolean isValidHybridSpawn(ServerLevel level, BlockPos pos, boolean requireDark) {
        if (pos.getY() >= SEA_LEVEL && !hasSkyAccess(level, pos)) {
            return false;
        }
        return hasValidMobSpace(level, pos, requireDark);
    }

    private static boolean hasValidMobSpace(ServerLevel level, BlockPos pos, boolean requireDark) {
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above()) || !level.isLoaded(pos.below())) {
            return false;
        }

        BlockPos below = pos.below();
        BlockState groundState = level.getBlockState(below);
        if (!groundState.isSolidRender(level, below)) {
            return false;
        }

        if (!isEmptyCollision(level, pos) || !isEmptyCollision(level, pos.above())) {
            return false;
        }

        return !requireDark || level.getMaxLocalRawBrightness(pos) <= 7;
    }

    private static boolean isEmptyCollision(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty()
                && state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean hasSkyAccess(ServerLevel level, BlockPos pos) {
        return level.canSeeSky(pos) || level.canSeeSky(pos.above());
    }
}
