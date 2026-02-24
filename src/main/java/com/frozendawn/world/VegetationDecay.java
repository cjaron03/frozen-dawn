package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Handles vegetation death driven by apocalypse phase:
 *
 * Leaves → Dead Leaves (gradual) → Air
 * Logs → Dead Logs → Frozen Logs
 * Crops → Air (instant death phase 3+)
 * Flowers → Dead Bush → Air
 * Saplings → Dead Bush
 * Short Grass/Ferns → Dead Bush → Air
 *
 * Phase 3+: Trees collapse via flood-fill.
 * Phase 5: Trees snap at a random height, leaving stumps.
 */
public final class VegetationDecay {

    private VegetationDecay() {}

    private static final int BASE_SURFACE_CHECKS = 16;
    private static final int BASE_VOLUME_CHECKS = 16;
    private static final int RADIUS = 64;
    private static final int MAX_COLLAPSE_BLOCKS = 64;
    private static final int MAX_SNAP_BLOCKS = 128;

    public static void tick(ServerLevel level, int phase) {
        if (phase < 2) return;
        if (!FrozenDawnConfig.ENABLE_VEGETATION_DECAY.get()) return;

        int surfaceChecks = switch (phase) {
            case 2 -> BASE_SURFACE_CHECKS;
            case 3 -> BASE_SURFACE_CHECKS * 2;
            case 4 -> BASE_SURFACE_CHECKS * 5;
            default -> BASE_SURFACE_CHECKS * 10;
        };
        // Volume checks scan the tree zone (Y 50-130) — much denser sampling
        int volumeChecks = switch (phase) {
            case 2 -> BASE_VOLUME_CHECKS;
            case 3 -> BASE_VOLUME_CHECKS * 4;
            case 4 -> BASE_VOLUME_CHECKS * 15;
            default -> BASE_VOLUME_CHECKS * 40; // phase 5: 640 checks/player/tick
        };

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();

            for (int i = 0; i < surfaceChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, surfaceY, z);
                if (!level.isLoaded(pos)) continue;

                decaySurface(level, pos, level.getBlockState(pos), phase);
            }

            for (int i = 0; i < volumeChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                // Focus Y-range on tree zone (50-130) instead of entire world height
                int y = 50 + random.nextInt(80);
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) continue;

                decayVolume(level, pos, level.getBlockState(pos), phase, random);
            }
        }
    }

    private static void decaySurface(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        if (state.is(BlockTags.FLOWERS) && phase >= 2) {
            if (state.getBlock() instanceof DoublePlantBlock) {
                boolean isUpper = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
                BlockPos upperPos = isUpper ? pos : pos.above();
                BlockPos lowerPos = isUpper ? pos.below() : pos;
                level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(lowerPos, Blocks.DEAD_BUSH.defaultBlockState(), 3);
            } else {
                level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), 3);
            }
            return;
        }

        if ((state.is(Blocks.SHORT_GRASS) || state.is(Blocks.FERN)) && phase >= 2) {
            level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), 3);
            return;
        }

        if ((state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN)) && phase >= 2) {
            if (state.getBlock() instanceof DoublePlantBlock) {
                boolean isUpper = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
                BlockPos upperPos = isUpper ? pos : pos.above();
                BlockPos lowerPos = isUpper ? pos.below() : pos;
                level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(lowerPos, Blocks.DEAD_BUSH.defaultBlockState(), 3);
            }
            return;
        }

        if (state.is(BlockTags.SAPLINGS) && phase >= 2) {
            level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), 3);
            return;
        }

        if (state.getBlock() instanceof CropBlock && phase >= 3) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }

        if (state.is(Blocks.DEAD_BUSH) && phase >= 3) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void decayVolume(ServerLevel level, BlockPos pos, BlockState state, int phase, RandomSource random) {
        // --- Leaf decay chain: gradual, phase-dependent chance ---
        if (state.is(BlockTags.LEAVES)) {
            float leafDeathChance = switch (phase) {
                case 2 -> 0.05f;  // very slow start
                case 3 -> 0.15f;
                case 4 -> 0.40f;
                default -> 0.80f; // phase 5: rapid defoliation
            };
            if (random.nextFloat() < leafDeathChance) {
                level.setBlock(pos, ModBlocks.DEAD_LEAVES.get().defaultBlockState(), 3);
            }
            return;
        }

        // Dead leaves → air: lingers a bit before falling off
        if (state.is(ModBlocks.DEAD_LEAVES.get()) && phase >= 3) {
            float fallChance = switch (phase) {
                case 3 -> 0.10f;
                case 4 -> 0.30f;
                default -> 0.60f;
            };
            if (random.nextFloat() < fallChance) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            return;
        }

        // --- Log decay chain ---
        if (state.is(BlockTags.LOGS) && phase >= 3) {
            Direction.Axis axis = state.hasProperty(RotatedPillarBlock.AXIS)
                    ? state.getValue(RotatedPillarBlock.AXIS)
                    : Direction.Axis.Y;
            level.setBlock(pos, ModBlocks.DEAD_LOG.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, axis), 3);
            return;
        }

        // Dead Logs → collapse or freeze
        if (state.is(ModBlocks.DEAD_LOG.get()) && phase >= 3) {
            // Phase 5: trees snap — break at this log and destroy everything above
            if (phase >= 5 && random.nextFloat() < 0.60f) {
                snapTree(level, pos);
                return;
            }

            // Phase 3-4: flood-fill collapse
            float collapseChance = switch (phase) {
                case 3 -> 0.05f;
                case 4 -> 0.40f;
                default -> 0.80f;
            };
            if (random.nextFloat() < collapseChance) {
                collapseTree(level, pos);
                return;
            }

            // Otherwise just freeze in phase 4+
            if (phase >= 4) {
                Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
                level.setBlock(pos, ModBlocks.FROZEN_LOG.get().defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, axis), 3);
            }
        }
    }

    /**
     * Collapse a dead tree: flood-fill from the given log position,
     * removing all connected tree blocks. Drops items naturally.
     */
    private static void collapseTree(ServerLevel level, BlockPos start) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);
        visited.add(start);
        int removed = 0;

        while (!queue.isEmpty() && removed < MAX_COLLAPSE_BLOCKS) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);

            boolean isTreeBlock = state.is(ModBlocks.DEAD_LOG.get())
                    || state.is(ModBlocks.FROZEN_LOG.get())
                    || state.is(ModBlocks.DEAD_LEAVES.get())
                    || state.is(ModBlocks.FROZEN_LEAVES.get())
                    || state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.LOGS);

            if (!isTreeBlock && !current.equals(start)) continue;

            level.destroyBlock(current, true);
            removed++;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && level.isLoaded(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
    }

    /**
     * Snap a tree at the given position: scan upward from this log,
     * destroy everything above it. The log itself and anything below
     * it remains as a stump. Creates a broken-tree look.
     */
    private static void snapTree(ServerLevel level, BlockPos snapPoint) {
        // First, break the snap point itself
        level.destroyBlock(snapPoint, true);

        // Then destroy everything above by scanning upward and outward
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        int removed = 0;

        // Start from the block above the snap point
        BlockPos above = snapPoint.above();
        queue.add(above);
        visited.add(above);
        visited.add(snapPoint); // don't go back down through snap point

        while (!queue.isEmpty() && removed < MAX_SNAP_BLOCKS) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);

            boolean isTreeBlock = state.is(ModBlocks.DEAD_LOG.get())
                    || state.is(ModBlocks.FROZEN_LOG.get())
                    || state.is(ModBlocks.DEAD_LEAVES.get())
                    || state.is(ModBlocks.FROZEN_LEAVES.get())
                    || state.is(BlockTags.LEAVES)
                    || state.is(BlockTags.LOGS);

            if (!isTreeBlock) continue;

            level.destroyBlock(current, false); // no drops — they shatter
            removed++;

            // Spread upward and sideways (not downward past snap point)
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) continue; // don't go below snap
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor) && level.isLoaded(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
            // Also check directly below for branches that extend down from canopy
            BlockPos below = current.below();
            if (!visited.contains(below) && level.isLoaded(below) && current.getY() > snapPoint.getY()) {
                visited.add(below);
                queue.add(below);
            }
        }
    }
}
