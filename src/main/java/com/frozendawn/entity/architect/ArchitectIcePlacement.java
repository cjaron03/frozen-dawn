package com.frozendawn.entity.architect;

import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
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
        // Check occupancy before removing vegetation or evicting an older wall.
        // A rounded retreat offset can still overlap the builder's actual body.
        if (!(level instanceof ServerLevel server) || !server.hasChunkAt(pos)) return false;
        var occupants = new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        server.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                new net.minecraft.world.phys.AABB(pos),
                entity -> !entity.isRemoved() && !entity.isSpectator() && entity.blocksBuilding, occupants, 1);
        if (!occupants.isEmpty() || !canPlaceIce(level, pos)) {
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

    /** Every ice block the Architect placed, nearest first, for its collapse as the Architect dies. */
    @SafeVarargs
    public static List<BlockPos> collapseOrder(BlockPos origin, List<BlockPos>... placed) {
        List<BlockPos> order = new ArrayList<>();
        for (List<BlockPos> pool : placed) {
            order.addAll(pool);
        }
        order.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        return order;
    }

    /**
     * Breaks the next {@code count} ice blocks in the collapse order. Only the first each tick
     * plays its shatter, so the death sound stays on top.
     */
    public static void collapseNext(ServerLevel level, List<BlockPos> order, int count) {
        boolean shattered = false;
        int broken = 0;
        while (broken < count && !order.isEmpty()) {
            BlockPos pos = order.remove(0);
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.PACKED_ICE)) {
                continue;
            }
            if (shattered) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.05);
            } else {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
                shattered = true;
            }
            level.removeBlock(pos, false);
            broken++;
        }
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
