package com.frozendawn.entity.architect;

import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Shared ice placement/eviction rules for scaffold and tactical ice pools.
 */
public final class ArchitectIcePlacement {

    private ArchitectIcePlacement() {
    }

    public static boolean placeScaffoldIce(
            Level level,
            BlockPos pos,
            List<BlockPos> scaffoldIce,
            int maxScaffoldIce,
            BlockPos actorPos
    ) {
        if (!canPlaceIce(level, pos)) {
            return false;
        }

        while (scaffoldIce.size() >= maxScaffoldIce) {
            BlockPos oldest = scaffoldIce.get(0);
            // Never evict the block the entity occupies / stands on.
            if (oldest.equals(actorPos.below()) || oldest.equals(actorPos)) {
                if (scaffoldIce.size() > 1) {
                    BlockPos secondOldest = scaffoldIce.get(1);
                    level.removeBlock(secondOldest, false);
                    scaffoldIce.remove(1);
                } else {
                    return false;
                }
            } else {
                level.removeBlock(oldest, false);
                scaffoldIce.remove(0);
            }
        }

        level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 3);
        scaffoldIce.add(pos);
        return true;
    }

    public static boolean placeTacticalIce(
            Level level,
            BlockPos pos,
            List<BlockPos> tacticalIce,
            int maxTacticalIce
    ) {
        if (!canPlaceIce(level, pos)) {
            return false;
        }

        while (tacticalIce.size() >= maxTacticalIce) {
            BlockPos oldest = tacticalIce.remove(0);
            level.removeBlock(oldest, false);
        }

        level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 3);
        tacticalIce.add(pos);
        return true;
    }

    public static void cleanupAllIce(Level level, List<BlockPos> scaffoldIce, List<BlockPos> tacticalIce) {
        for (BlockPos pos : scaffoldIce) {
            if (level.getBlockState(pos).is(Blocks.PACKED_ICE)) {
                level.removeBlock(pos, false);
            }
        }
        scaffoldIce.clear();
        for (BlockPos pos : tacticalIce) {
            if (level.getBlockState(pos).is(Blocks.PACKED_ICE)) {
                level.removeBlock(pos, false);
            }
        }
        tacticalIce.clear();
    }

    private static boolean canPlaceIce(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel
                && StillpointPolicy.isSuppressed(serverLevel, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()
                || state.canBeReplaced()
                || state.is(ModBlocks.FROZEN_ATMOSPHERE.get())) {
            return true;
        }
        // Destroy small plants/flowers (instant-break blocks) to make room for ice.
        if (state.getDestroySpeed(level, pos) == 0) {
            level.destroyBlock(pos, true);
            return true;
        }
        return false;
    }
}
