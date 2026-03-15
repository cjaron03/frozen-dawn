package com.frozendawn.world;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Shared helpers for scanning outdoor columns through canopy cover and snow.
 *
 * This lets world systems affect the actual ground under trees instead of
 * getting stuck on leaves or snow piles sitting on the canopy.
 */
public final class SurfaceColumnScanner {

    public static final int DEFAULT_MAX_SCAN_DEPTH = 20;

    private SurfaceColumnScanner() {}

    /**
     * Finds the first ground-like block in a column, skipping air, tree canopy,
     * and snow buildup above it.
     */
    public static BlockPos findGroundBelowCover(ServerLevel level, int x, int z, int maxDepth) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (topY < level.getMinBuildHeight()) {
            return null;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int floorY = Math.max(level.getMinBuildHeight(), topY - maxDepth);

        for (int y = topY; y >= floorY; y--) {
            mutable.set(x, y, z);
            if (!level.isLoaded(mutable)) {
                return null;
            }

            BlockState state = level.getBlockState(mutable);
            if (shouldSkipForGroundScan(state)) {
                continue;
            }

            return mutable.immutable();
        }

        return null;
    }

    /**
     * Finds the first block in a column that can actually support snow.
     *
     * This differs from {@link #findGroundBelowCover} by skipping partial
     * overhang blocks like upside-down stairs that show up in the heightmap
     * but should not stop snow from reaching the ground below.
     */
    public static BlockPos findSnowSupportBelowCover(ServerLevel level, int x, int z, int maxDepth) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        if (topY < level.getMinBuildHeight()) {
            return null;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int floorY = Math.max(level.getMinBuildHeight(), topY - maxDepth);

        for (int y = topY; y >= floorY; y--) {
            mutable.set(x, y, z);
            if (!level.isLoaded(mutable)) {
                return null;
            }

            BlockState state = level.getBlockState(mutable);
            if (shouldSkipForGroundScan(state)) {
                continue;
            }

            if (canSupportSnow(level, mutable, state)) {
                return mutable.immutable();
            }
        }

        return null;
    }

    public static boolean shouldSkipForGroundScan(BlockState state) {
        return state.isAir()
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || isCanopyBlock(state);
    }

    public static boolean isDetachedSnow(ServerLevel level, BlockPos snowPos) {
        BlockPos.MutableBlockPos cursor = snowPos.mutable();

        while (cursor.getY() >= level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                cursor.move(Direction.DOWN);
                continue;
            }

            if (state.isAir() || isCanopyBlock(state)) {
                return true;
            }

            if (state.is(Blocks.DIRT_PATH)) {
                return false;
            }

            return !state.isFaceSturdy(level, cursor, Direction.UP);
        }

        return true;
    }

    public static boolean canSupportSnow(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Blocks.DIRT_PATH)) {
            return true;
        }

        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean isCanopyBlock(BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(ModBlocks.DEAD_LEAVES.get())
                || state.is(ModBlocks.FROZEN_LEAVES.get())
                || state.is(ModBlocks.DEAD_LOG.get())
                || state.is(ModBlocks.FROZEN_LOG.get());
    }
}
