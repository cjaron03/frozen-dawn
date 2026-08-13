package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.RemnantLureSavedData;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /** Reusable collections for flood-fill operations (server thread only). */
    private static final Queue<BlockPos> fillQueue = new ArrayDeque<>();
    private static final Set<BlockPos> fillVisited = new HashSet<>();

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
        // Phase 5 reduced from 40x to 20x since most trees are already dead
        int volumeChecks = switch (phase) {
            case 2 -> BASE_VOLUME_CHECKS;
            case 3 -> BASE_VOLUME_CHECKS * 4;
            case 4 -> BASE_VOLUME_CHECKS * 15;
            default -> BASE_VOLUME_CHECKS * 20; // phase 5: 320 checks/player/tick
        };

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            for (int i = 0; i < surfaceChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                BlockPos pos = SurfaceColumnScanner.findGroundBelowCover(
                        level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
                if (pos == null) continue;
                decaySurface(level, pos, level.getBlockState(pos), phase);
            }

            for (int i = 0; i < volumeChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                // Focus Y-range on tree zone (50-130) instead of entire world height
                int y = 50 + random.nextInt(80);
                mutable.set(x, y, z);
                if (!level.isLoaded(mutable)) continue;

                BlockPos pos = mutable.immutable();
                decayVolume(level, pos, level.getBlockState(pos), phase, random);
            }
        }
    }

    private static void decaySurface(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        if (isRemnantShelterBlock(level, pos)) return;

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

        if (state.is(Blocks.SUGAR_CANE) && phase >= 2) {
            destroyVerticalPlantColumn(level, pos, Blocks.SUGAR_CANE);
            return;
        }

        if (state.is(Blocks.BAMBOO) && phase >= 2) {
            destroyVerticalPlantColumn(level, pos, Blocks.BAMBOO);
            return;
        }

        if (state.is(Blocks.CACTUS) && phase >= 2) {
            destroyVerticalPlantColumn(level, pos, Blocks.CACTUS);
            return;
        }

        if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)) {
            if (phase >= 2) {
                destroyKelpColumn(level, pos);
            }
            return;
        }

        if (state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS) || state.is(Blocks.LILY_PAD)) {
            if (phase >= 2) {
                level.destroyBlock(pos, false);
            }
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
        if (isRemnantShelterBlock(level, pos)) return;

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

        if ((state.is(Blocks.VINE)
                || state.is(Blocks.CAVE_VINES)
                || state.is(Blocks.CAVE_VINES_PLANT)
                || state.is(Blocks.HANGING_ROOTS)) && phase >= 3) {
            level.destroyBlock(pos, false);
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
        fillQueue.clear();
        fillVisited.clear();
        fillQueue.add(start);
        fillVisited.add(start);
        int removed = 0;
        List<BlockPos> removedTreeBlocks = new ArrayList<>();

        while (!fillQueue.isEmpty() && removed < MAX_COLLAPSE_BLOCKS) {
            BlockPos current = fillQueue.poll();
            if (isRemnantShelterBlock(level, current)) continue;
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
            removedTreeBlocks.add(current.immutable());

            if (fillVisited.size() > MAX_COLLAPSE_BLOCKS * 8) break;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!fillVisited.contains(neighbor) && level.isLoaded(neighbor)) {
                    fillVisited.add(neighbor);
                    fillQueue.add(neighbor);
                }
            }
        }

        clearDetachedSnow(level, removedTreeBlocks, MAX_COLLAPSE_BLOCKS * 6);
    }

    /**
     * Snap a tree at the given position: scan upward from this log,
     * destroy everything above it. The log itself and anything below
     * it remains as a stump. Creates a broken-tree look.
     */
    private static void snapTree(ServerLevel level, BlockPos snapPoint) {
        if (isRemnantShelterBlock(level, snapPoint)) return;

        // First, break the snap point itself
        level.destroyBlock(snapPoint, true);

        // Then destroy everything above by scanning upward and outward
        fillQueue.clear();
        fillVisited.clear();
        int removed = 0;
        List<BlockPos> removedTreeBlocks = new ArrayList<>();
        removedTreeBlocks.add(snapPoint.immutable());

        // Start from the block above the snap point
        BlockPos above = snapPoint.above();
        fillQueue.add(above);
        fillVisited.add(above);
        fillVisited.add(snapPoint); // don't go back down through snap point

        while (!fillQueue.isEmpty() && removed < MAX_SNAP_BLOCKS) {
            BlockPos current = fillQueue.poll();
            if (isRemnantShelterBlock(level, current)) continue;
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
            removedTreeBlocks.add(current.immutable());

            if (fillVisited.size() > MAX_SNAP_BLOCKS * 8) break;
            // Spread upward and sideways (not downward past snap point)
            for (Direction dir : Direction.values()) {
                if (dir == Direction.DOWN) continue; // don't go below snap
                BlockPos neighbor = current.relative(dir);
                if (!fillVisited.contains(neighbor) && level.isLoaded(neighbor)) {
                    fillVisited.add(neighbor);
                    fillQueue.add(neighbor);
                }
            }
            // Also check directly below for branches that extend down from canopy
            BlockPos below = current.below();
            if (!fillVisited.contains(below) && level.isLoaded(below) && current.getY() > snapPoint.getY()) {
                fillVisited.add(below);
                fillQueue.add(below);
            }
        }

        clearDetachedSnow(level, removedTreeBlocks, MAX_SNAP_BLOCKS * 6);
    }

    private static void clearDetachedSnow(ServerLevel level, List<BlockPos> removedTreeBlocks, int maxSnowBlocks) {
        if (removedTreeBlocks.isEmpty()) {
            return;
        }

        Queue<BlockPos> snowQueue = new ArrayDeque<>();
        Set<BlockPos> visitedSnow = new HashSet<>();

        for (BlockPos treePos : removedTreeBlocks) {
            enqueueSnowSeed(level, snowQueue, visitedSnow, treePos.above());
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                enqueueSnowSeed(level, snowQueue, visitedSnow, treePos.above().relative(dir));
            }
        }

        int removedSnow = 0;
        while (!snowQueue.isEmpty() && removedSnow < maxSnowBlocks) {
            BlockPos current = snowQueue.poll();
            BlockState state = level.getBlockState(current);
            if (!state.is(Blocks.SNOW) && !state.is(Blocks.SNOW_BLOCK)) {
                continue;
            }
            if (!SurfaceColumnScanner.isDetachedSnow(level, current)) {
                continue;
            }

            level.destroyBlock(current, false);
            removedSnow++;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                enqueueSnowSeed(level, snowQueue, visitedSnow, neighbor);
            }
        }
    }

    private static void enqueueSnowSeed(ServerLevel level, Queue<BlockPos> snowQueue,
                                        Set<BlockPos> visitedSnow, BlockPos pos) {
        if (!level.isLoaded(pos) || !visitedSnow.add(pos.immutable())) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
            snowQueue.add(pos.immutable());
        }
    }

    private static void destroyVerticalPlantColumn(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block plant) {
        BlockPos.MutableBlockPos cursor = pos.mutable();

        while (level.getBlockState(cursor.below()).is(plant)) {
            cursor.move(Direction.DOWN);
        }

        while (level.getBlockState(cursor).is(plant)) {
            level.destroyBlock(cursor, true);
            cursor.move(Direction.UP);
        }
    }

    private static void destroyKelpColumn(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();

        while (isKelp(level.getBlockState(cursor.below()))) {
            cursor.move(Direction.DOWN);
        }

        while (isKelp(level.getBlockState(cursor))) {
            level.destroyBlock(cursor, false);
            cursor.move(Direction.UP);
        }
    }

    private static boolean isKelp(BlockState state) {
        return state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }

    private static boolean isRemnantShelterBlock(ServerLevel level, BlockPos pos) {
        return RemnantLureSavedData.get(level.getServer())
                .protectsFromEnvironmentalMutation(pos);
    }
}
