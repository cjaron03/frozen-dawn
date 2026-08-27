package com.frozendawn.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded concealed-spawn and breach-surface searches. Never edits terrain. */
public final class ResonantPhaseController {
    private static final int MAX_BREACH_CHECKS = 32;

    private ResonantPhaseController() {
    }

    public static BlockPos findConcealedSpawn(ServerLevel level, BlockPos near) {
        for (int radius = 3; radius <= 12; radius += 3) {
            for (int attempt = 0; attempt < 20; attempt++) {
                int dx = level.random.nextIntBetweenInclusive(-radius, radius);
                int dy = level.random.nextIntBetweenInclusive(-5, 5);
                int dz = level.random.nextIntBetweenInclusive(-radius, radius);
                BlockPos candidate = near.offset(dx, dy, dz);
                if (!level.hasChunkAt(candidate) || !isPhaseable(level.getBlockState(candidate))) {
                    continue;
                }
                if (findBreach(level, candidate, near) != null) return candidate;
            }
        }
        return null;
    }

    public static BreachCandidate findBreach(ServerLevel level, BlockPos from,
                                             BlockPos playerPosition) {
        List<BlockPos> walls = new ArrayList<>();
        for (int radius = 1; radius <= 4 && walls.size() < MAX_BREACH_CHECKS * 2; radius++) {
            for (int y = -2; y <= 2; y++) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                        walls.add(playerPosition.offset(x, y, z));
                    }
                }
            }
        }
        walls.sort(Comparator.comparingDouble(pos -> pos.distSqr(from)));
        int checked = 0;
        for (BlockPos wall : walls) {
            if (checked++ >= MAX_BREACH_CHECKS || !level.hasChunkAt(wall)
                    || !isPhaseable(level.getBlockState(wall))) {
                continue;
            }
            for (Direction normal : Direction.values()) {
                BlockPos outside = wall.relative(normal);
                if (!level.hasChunkAt(outside) || !isSafeStandingSpace(level, outside)) continue;
                if (outside.distSqr(playerPosition) > 16.0D) continue;
                BlockPos inside = wall.relative(normal.getOpposite());
                if (!level.hasChunkAt(inside) || !isPhaseable(level.getBlockState(inside))) {
                    continue;
                }
                return new BreachCandidate(wall, outside, inside, normal);
            }
        }
        return null;
    }

    public static boolean isDenselyEnclosed(ServerLevel level, BlockPos center) {
        int solid = 0;
        int checked = 0;
        for (int x = -3; x <= 3; x += 2) {
            for (int y = -2; y <= 2; y += 2) {
                for (int z = -3; z <= 3; z += 2) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    checked++;
                    if (level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) solid++;
                }
            }
        }
        return checked > 0 && solid >= Math.ceil(checked * 0.58D);
    }

    public static boolean canPhaseTo(ServerLevel level, BlockPos anchor, BlockPos next) {
        return level.hasChunkAt(next) && next.distSqr(anchor) <= 64.0D * 64.0D
                && !level.getBlockState(next).is(Blocks.BEDROCK)
                && level.getFluidState(next).isEmpty();
    }

    private static boolean isSafeStandingSpace(ServerLevel level, BlockPos feet) {
        AABB body = new AABB(feet).expandTowards(0.0D, 1.0D, 0.0D)
                .inflate(-0.18D, 0.0D, -0.18D);
        return level.noCollision(body)
                && level.getBlockState(feet.below()).isFaceSturdy(
                        level, feet.below(), Direction.UP);
    }

    private static boolean isPhaseable(BlockState state) {
        return !state.isAir() && !state.is(Blocks.BEDROCK)
                && !state.liquid() && state.blocksMotion();
    }

    public record BreachCandidate(BlockPos wall, BlockPos outside, BlockPos inside,
                                  Direction normal) {
    }
}
