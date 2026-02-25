package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
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

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();

            // Surface pass: now scans downward from heightmap to find freezable blocks
            // under tree canopies, not just the top-level surface block
            for (int i = 0; i < surfaceChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;

                // Scan downward up to 12 blocks to find freezable ground under trees
                for (int dy = 0; dy <= 12; dy++) {
                    BlockPos pos = new BlockPos(x, topY - dy, z);
                    if (!level.isLoaded(pos)) break;
                    BlockState state = level.getBlockState(pos);

                    // Skip air, leaves, logs (canopy blocks) - keep scanning down
                    if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                            || state.is(ModBlocks.DEAD_LEAVES.get()) || state.is(ModBlocks.FROZEN_LEAVES.get())
                            || state.is(ModBlocks.DEAD_LOG.get()) || state.is(ModBlocks.FROZEN_LOG.get())) {
                        continue;
                    }

                    // Found a solid block - try to freeze it
                    transformSurface(level, pos, state, phase, progress);
                    break;
                }
            }

            // Volume pass: water, lava, ice chains
            for (int i = 0; i < volumeChecks; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int y = random.nextIntBetweenInclusive(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) continue;

                BlockState volumeState = level.getBlockState(pos);
                transformVolume(level, pos, volumeState, phase, progress);
                transformSurfaceCoalOre(level, pos, volumeState, phase);
            }
        }
    }

    private static void transformSurface(ServerLevel level, BlockPos pos, BlockState state, int phase, float progress) {
        // Phase 6 late: exposed snow/snow blocks slowly compact into ice
        // 10% chance per check — gradual transformation, not instant
        // (existing ice → packed ice → blue ice chain handles the rest)
        if (phase >= 6 && progress >= 0.85f && level.canSeeSky(pos.above())) {
            if ((state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK))
                    && level.getRandom().nextFloat() < 0.10f) {
                level.setBlock(pos, Blocks.ICE.defaultBlockState(), 3);
                return;
            }
        }

        if (state.is(Blocks.GRASS_BLOCK) && phase >= 2) {
            level.setBlock(pos, ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState(), 3);
            return;
        }
        if (state.is(ModBlocks.DEAD_GRASS_BLOCK.get()) && phase >= 3) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.DIRT) && phase >= 4) {
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

    private static void transformVolume(ServerLevel level, BlockPos pos, BlockState state, int phase, float progress) {
        // Phase 6 late: surface ice sublimates (solid → gas in vacuum)
        // Water also boils off instantly. Underground ice is unaffected.
        if (phase >= 6 && progress >= 0.85f && level.canSeeSky(pos.above())) {
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
