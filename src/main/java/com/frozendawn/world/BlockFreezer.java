package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 */
public final class BlockFreezer {

    private BlockFreezer() {}

    // Checks per player per tick
    private static final int SURFACE_CHECKS = 24;
    private static final int VOLUME_CHECKS = 12;
    // Horizontal radius around player (blocks)
    private static final int RADIUS = 64;

    public static void tick(ServerLevel level, int phase) {
        if (phase < 2) return;

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();

            // Surface pass: grass, sand, dirt
            for (int i = 0; i < SURFACE_CHECKS; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                // getHeight returns Y of first non-solid above surface; below() is the surface block
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, surfaceY, z);
                if (!level.isLoaded(pos)) continue;

                transformSurface(level, pos, level.getBlockState(pos), phase);
            }

            // Volume pass: water, lava, ice chains
            for (int i = 0; i < VOLUME_CHECKS; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int y = random.nextIntBetweenInclusive(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) continue;

                BlockState volumeState = level.getBlockState(pos);
                transformVolume(level, pos, volumeState, phase);
                transformSurfaceCoalOre(level, pos, volumeState, phase);
            }
        }
    }

    private static void transformSurface(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        // Grass Block → Dead Grass Block (phase 2+)
        if (state.is(Blocks.GRASS_BLOCK) && phase >= 2) {
            level.setBlock(pos, ModBlocks.DEAD_GRASS_BLOCK.get().defaultBlockState(), 3);
            return;
        }
        // Dead Grass Block → Dirt (phase 3+)
        if (state.is(ModBlocks.DEAD_GRASS_BLOCK.get()) && phase >= 3) {
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            return;
        }
        // Dirt → Frozen Dirt (phase 4+)
        if (state.is(Blocks.DIRT) && phase >= 4) {
            level.setBlock(pos, ModBlocks.FROZEN_DIRT.get().defaultBlockState(), 3);
            return;
        }
        // Sand / Red Sand → Frozen Sand (phase 3+)
        if ((state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) && phase >= 3) {
            level.setBlock(pos, ModBlocks.FROZEN_SAND.get().defaultBlockState(), 3);
        }
    }

    private static void transformSurfaceCoalOre(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        if (!FrozenDawnConfig.ENABLE_FUEL_SCARCITY.get()) return;
        if (phase < FrozenDawnConfig.FUEL_SCARCITY_PHASE.get()) return;
        if (pos.getY() < 0) return; // Only surface coal

        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) {
            level.setBlock(pos, ModBlocks.FROZEN_COAL_ORE.get().defaultBlockState(), 3);
        }
    }

    private static void transformVolume(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        // --- Water freezing chain ---
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

        // --- Lava freezing chain (config-gated) ---
        if (!FrozenDawnConfig.ENABLE_LAVA_FREEZING.get()) return;

        if (state.is(Blocks.LAVA) && phase >= 3) {
            level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.MAGMA_BLOCK) && phase >= 4) {
            level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 3);
            return;
        }
        if (state.is(Blocks.OBSIDIAN) && phase >= 5) {
            level.setBlock(pos, ModBlocks.FROZEN_OBSIDIAN.get().defaultBlockState(), 3);
        }
    }
}
