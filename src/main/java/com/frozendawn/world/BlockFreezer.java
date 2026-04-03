package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.RoofCollapseSnowTracker;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Handles block freezing chains driven by apocalypse phase:
 *
 * Water → Ice → Packed Ice → Blue Ice
 * Lava → Magma Block → Obsidian → Frozen Obsidian
 * Grass Block → Dead Grass → Dirt → Frozen Dirt
 * Sand → Frozen Sand (permafrost)
 *
 * Surface checks now scan below canopy (not just heightmap) to freeze
 * blocks under trees.
 */
public final class BlockFreezer {

    private BlockFreezer() {}

    private static final int BASE_SURFACE_CHECKS = 24;
    private static final int BASE_VOLUME_CHECKS = 12;
    private static final int BASE_MASONRY_CHECKS = 8;
    private static final int RADIUS = 64;

    public static void tick(ServerLevel level, int phase, float progress) {
        if (phase < 2) return;

        int surfaceChecks = switch (phase) {
            case 2 -> BASE_SURFACE_CHECKS;
            case 3 -> BASE_SURFACE_CHECKS * 2;
            case 4 -> BASE_SURFACE_CHECKS * 5;
            default -> BASE_SURFACE_CHECKS * 10;
        };
        int volumeChecks = switch (phase) {
            case 2 -> BASE_VOLUME_CHECKS;
            case 3 -> BASE_VOLUME_CHECKS * 2;
            case 4 -> BASE_VOLUME_CHECKS * 5;
            default -> BASE_VOLUME_CHECKS * 10;
        };
        int masonryChecks = switch (phase) {
            case 4 -> BASE_MASONRY_CHECKS * 3;
            case 5 -> BASE_MASONRY_CHECKS * 6;
            default -> phase >= 6 ? BASE_MASONRY_CHECKS * 8 : 0;
        };

        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();

            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            // Surface pass: now scans downward from heightmap to find freezable blocks
            // under tree canopies, not just the top-level surface block
            for (int i = 0; i < surfaceChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                BlockPos groundPos = SurfaceColumnScanner.findGroundBelowCover(
                        level, x, z, SurfaceColumnScanner.DEFAULT_MAX_SCAN_DEPTH);
                if (groundPos == null) continue;

                mutable.set(groundPos);
                BlockState state = level.getBlockState(mutable);
                transformSurface(level, mutable.immutable(), state, phase, progress);
            }

            // Volume pass: water, lava, ice chains
            for (int i = 0; i < volumeChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int y = random.nextIntBetweenInclusive(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                mutable.set(x, y, z);
                if (!level.isLoaded(mutable)) continue;

                BlockState volumeState = level.getBlockState(mutable);
                BlockPos immutable = mutable.immutable();
                transformVolume(level, immutable, volumeState, phase, progress);
                transformSurfaceCoalOre(level, immutable, volumeState, phase);
            }

            for (int i = 0; i < masonryChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (top < level.getMinBuildHeight()) continue;

                int minY = Math.max(level.getMinBuildHeight(), top - 18);
                int y = random.nextIntBetweenInclusive(minY, top);
                mutable.set(x, y, z);
                if (!level.isLoaded(mutable)) continue;

                BlockPos immutable = mutable.immutable();
                transformExposedStructure(level, immutable, level.getBlockState(immutable), phase, progress);
            }
        }
    }

    private static void transformSurface(ServerLevel level, BlockPos pos, BlockState state, int phase, float progress) {
        // Phase 6 late: exposed snow/snow blocks slowly compact into ice
        // 10% chance per check — gradual transformation, not instant
        // (existing ice → packed ice → blue ice chain handles the rest)
        if (PhaseManager.isVacuumActive(phase, progress) && level.canSeeSky(pos.above())) {
            if ((state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK))
                    && level.getRandom().nextFloat() < 0.10f) {
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
                return;
            }
        }

        if (state.is(Blocks.FARMLAND) && phase >= 2) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.DIRT_PATH) && phase >= 2) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.GRASS_BLOCK) && phase >= 2) {
            level.setBlock(pos, ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState(), 3);
            return;
        }
        if ((state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) && phase >= 2) {
            level.setBlock(pos, ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.MOSS_BLOCK) && phase >= 2) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        if (state.is(ModBlocks.DEAD_GRASS_BLOCK.get()) && phase >= 3) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        if ((state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MUD)) && phase >= 4) {
            level.setBlock(pos, ModBlocks.FROZEN_DIRT.get().defaultBlockState(), 3);
            return;
        }
        if ((state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) && phase >= 3) {
            level.setBlock(pos, ModBlocks.FROZEN_SAND.get().defaultBlockState(), 3);
        }
    }

    private static void transformSurfaceCoalOre(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        if (!FrozenDawnConfig.ENABLE_FUEL_SCARCITY.get()) return;
        if (phase < FrozenDawnConfig.FUEL_SCARCITY_PHASE.get()) return;
        if (pos.getY() < 0) return;

        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            level.setBlock(pos, ModBlocks.FROZEN_COAL_ORE.get().defaultBlockState(), 3);
        }
    }

    private static void transformExposedStructure(ServerLevel level, BlockPos pos, BlockState state,
                                                  int phase, float progress) {
        if (phase < 4) return;
        if (!hasOutdoorFace(level, pos)) return;

        if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)) {
            level.setBlock(pos, ModBlocks.FROZEN_COBBLESTONE.get().defaultBlockState(), 3);
            state = level.getBlockState(pos);
        }

        if (state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.CHISELED_STONE_BRICKS)) {
            level.setBlock(pos, ModBlocks.FROZEN_STONE_BRICKS.get().defaultBlockState(), 3);
            state = level.getBlockState(pos);
        }

        if (state.is(BlockTags.PLANKS)) {
            level.setBlock(pos, ModBlocks.FROZEN_PLANKS.get().defaultBlockState(), 3);
            state = level.getBlockState(pos);
        }

        applyStructuralStress(level, pos, state, phase, progress);
    }

    public static void refreshStructuralStress(ServerLevel level, BlockPos pos, int phase, float progress) {
        if (!level.isLoaded(pos)) {
            return;
        }
        applyStructuralStress(level, pos, level.getBlockState(pos), phase, progress);
    }

    private static boolean hasOutdoorFace(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }

            BlockPos adjacent = pos.relative(direction);
            if (!level.isLoaded(adjacent) || !level.getBlockState(adjacent).isAir()) {
                continue;
            }

            if (level.canSeeSky(adjacent) || level.canSeeSky(adjacent.above())) {
                return true;
            }
        }
        return false;
    }

    private static void applyStructuralStress(ServerLevel level, BlockPos pos, BlockState state,
                                              int phase, float progress) {
        boolean wood = isWoodStructure(state);
        boolean masonry = isMasonryStructure(state);
        if (!wood && !masonry) {
            StructureStressTracker.clear(level, pos);
            return;
        }

        int snowLoad = getSnowLoadAbove(level, pos);
        boolean weak = isStructurallyWeak(level, pos);

        int pressure = 0;
        if (weak) {
            pressure += wood ? 2 : 1;
        }
        if (phase >= 5) {
            pressure += wood ? 1 : 0;
        }
        if (phase >= 6 && progress >= 0.65f) {
            pressure += wood ? 2 : 1;
        }
        if (snowLoad >= 4) {
            pressure += wood ? 1 : 0;
        }
        if (snowLoad >= 8) {
            pressure += wood ? 2 : 1;
        }
        if (snowLoad >= 16) {
            pressure += wood ? 3 : 2;
        }

        int crackStage = crackStageFor(wood, pressure, snowLoad);
        if (crackStage >= 0) {
            StructureStressTracker.update(level, pos, crackStage);
        } else {
            StructureStressTracker.clear(level, pos);
        }

        if (phase < 5) {
            return;
        }

        float collapseChance = collapseChanceFor(wood, phase, progress, pressure, snowLoad, weak);
        if (collapseChance <= 0.0f) {
            return;
        }

        if (level.getRandom().nextFloat() < collapseChance) {
            StructureStressTracker.clear(level, pos);
            if (wood) {
                collapseRoofSnowIntoInterior(level, pos);
            }
            level.destroyBlock(pos, false);
        }
    }

    private static boolean isStructurallyWeak(ServerLevel level, BlockPos pos) {
        int supports = 0;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.isLoaded(neighbor)) {
                continue;
            }

            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isFaceSturdy(level, neighbor, direction.getOpposite())) {
                supports++;
            }
        }

        return supports <= 2 || (supports <= 3 && level.getBlockState(pos.below()).isAir());
    }

    private static boolean isWoodStructure(BlockState state) {
        return state.is(ModBlocks.FROZEN_PLANKS.get())
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.WOODEN_TRAPDOORS);
    }

    private static boolean isMasonryStructure(BlockState state) {
        return state.is(ModBlocks.FROZEN_COBBLESTONE.get())
                || state.is(ModBlocks.FROZEN_STONE_BRICKS.get())
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.STONE_BRICKS)
                || state.is(Blocks.MOSSY_STONE_BRICKS)
                || state.is(Blocks.CRACKED_STONE_BRICKS)
                || state.is(Blocks.CHISELED_STONE_BRICKS);
    }

    private static int getSnowLoadAbove(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.above().mutable();
        int load = 0;

        for (int depth = 0; depth < 4; depth++) {
            BlockState above = level.getBlockState(cursor);
            if (above.is(Blocks.SNOW)) {
                load += above.getValue(SnowLayerBlock.LAYERS);
            } else if (above.is(Blocks.SNOW_BLOCK)) {
                load += 8;
            } else {
                break;
            }
            cursor.move(Direction.UP);
        }

        return load;
    }

    private static void collapseRoofSnowIntoInterior(ServerLevel level, BlockPos collapsedPos) {
        BlockPos supportPos = findSnowLandingSupport(level, collapsedPos);
        if (supportPos == null) {
            return;
        }

        RoofCollapseSnowTracker tracker = RoofCollapseSnowTracker.get(level.getServer());
        int snowUnits = collectSnowUnits(level, collapsedPos.above(), tracker);
        if (snowUnits <= 0) {
            return;
        }

        BlockPos landingPos = supportPos.above();
        snowUnits += collectSnowUnits(level, landingPos, tracker);
        placeSnowUnits(level, landingPos, snowUnits, tracker);
    }

    private static int collectSnowUnits(ServerLevel level, BlockPos startPos, RoofCollapseSnowTracker tracker) {
        BlockPos.MutableBlockPos cursor = startPos.mutable();
        int total = 0;

        while (true) {
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.SNOW)) {
                total += state.getValue(SnowLayerBlock.LAYERS);
            } else if (state.is(Blocks.SNOW_BLOCK)) {
                total += 8;
            } else {
                break;
            }

            level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
            tracker.clear(cursor);
            cursor.move(Direction.UP);
        }

        return total;
    }

    private static BlockPos findSnowLandingSupport(ServerLevel level, BlockPos collapsedPos) {
        BlockPos.MutableBlockPos cursor = collapsedPos.below().mutable();

        while (cursor.getY() >= level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                cursor.move(Direction.DOWN);
                continue;
            }

            if (SurfaceColumnScanner.canSupportSnow(level, cursor, state)) {
                return cursor.immutable();
            }
            return null;
        }

        return null;
    }

    private static void placeSnowUnits(ServerLevel level, BlockPos startPos, int snowUnits,
                                       RoofCollapseSnowTracker tracker) {
        BlockPos.MutableBlockPos cursor = startPos.mutable();

        while (snowUnits > 0) {
            BlockState at = level.getBlockState(cursor);
            if (!at.isAir()) {
                return;
            }

            if (snowUnits >= 8) {
                level.setBlock(cursor, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
                snowUnits -= 8;
            } else {
                level.setBlock(cursor, Blocks.SNOW.defaultBlockState()
                        .setValue(SnowLayerBlock.LAYERS, snowUnits), 3);
                snowUnits = 0;
            }

            tracker.mark(cursor);
            cursor.move(Direction.UP);
        }
    }

    private static int crackStageFor(boolean wood, int pressure, int snowLoad) {
        if (wood) {
            if (snowLoad >= 16 || pressure >= 7) return 8;
            if (snowLoad >= 8 || pressure >= 5) return 6;
            if (pressure >= 3) return 3;
            return -1;
        }

        if (snowLoad >= 16 || pressure >= 6) return 7;
        if (snowLoad >= 8 || pressure >= 4) return 4;
        return pressure >= 3 ? 1 : -1;
    }

    private static float collapseChanceFor(boolean wood, int phase, float progress, int pressure,
                                           int snowLoad, boolean weak) {
        if (!wood) {
            return 0.0f;
        }
        if (!weak && snowLoad < 16) {
            return 0.0f;
        }

        float chance = 0.0f;
        if (pressure >= 5) chance += 0.05f;
        if (pressure >= 7) chance += 0.06f;
        if (snowLoad >= 8) chance += 0.04f;
        if (snowLoad >= 16) chance += 0.12f;
        if (phase >= 6) chance += progress >= 0.75f ? 0.10f : 0.04f;

        return Math.min(chance, 0.30f);
    }

    private static void transformVolume(ServerLevel level, BlockPos pos, BlockState state, int phase, float progress) {
        // Phase 6 late: surface ice sublimates (solid → gas in vacuum)
        // Water also boils off instantly. Underground ice is unaffected.
        if (PhaseManager.isVacuumActive(phase, progress) && level.canSeeSky(pos.above())) {
            if (state.is(Blocks.WATER) || state.is(Blocks.ICE)
                    || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                return;
            }
        }

        if (state.is(Blocks.WATER) && phase >= 2) {
            level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.ICE) && phase >= 3) {
            level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.PACKED_ICE) && phase >= 4) {
            level.setBlock(pos, Blocks.BLUE_ICE.defaultBlockState(), 3);
            return;
        }

        if (!FrozenDawnConfig.ENABLE_LAVA_FREEZING.get()) return;

        if (state.is(Blocks.LAVA) && phase >= 3) {
            level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.MAGMA_BLOCK) && phase >= 4) {
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.OBSIDIAN) && phase >= 4) {
            level.setBlock(pos, ModBlocks.FROZEN_OBSIDIAN.get().defaultBlockState(), 3);
        }
    }
}
