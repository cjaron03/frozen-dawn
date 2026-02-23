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

/**
 * Handles vegetation death driven by apocalypse phase:
 *
 * Leaves → Dead Leaves → Air (fall off)
 * Logs → Dead Logs → Frozen Logs
 * Crops → Air (instant death phase 3+)
 * Flowers → Dead Bush → Air
 * Saplings → Dead Bush
 */
public final class VegetationDecay {

    private VegetationDecay() {}

    private static final int SURFACE_CHECKS = 16;
    private static final int VOLUME_CHECKS = 16;
    private static final int RADIUS = 64;

    public static void tick(ServerLevel level, int phase) {
        if (phase < 2) return;
        if (!FrozenDawnConfig.ENABLE_VEGETATION_DECAY.get()) return;

        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            BlockPos origin = player.blockPosition();

            // Surface pass: flowers, saplings, crops, dead bushes
            for (int i = 0; i < SURFACE_CHECKS; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos pos = new BlockPos(x, surfaceY, z);
                if (!level.isLoaded(pos)) continue;

                decaySurface(level, pos, level.getBlockState(pos), phase);
            }

            // Volume pass: leaves, logs
            for (int i = 0; i < VOLUME_CHECKS; i++) {
                int x = origin.getX() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int z = origin.getZ() + random.nextInt(RADIUS * 2 + 1) - RADIUS;
                int y = random.nextIntBetweenInclusive(level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) continue;

                decayVolume(level, pos, level.getBlockState(pos), phase);
            }
        }
    }

    private static void decaySurface(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        // Flowers → Dead Bush (phase 2+)
        if (state.is(BlockTags.FLOWERS) && phase >= 2) {
            if (state.getBlock() instanceof DoublePlantBlock) {
                // Tall flower: clear upper half, replace lower with dead bush
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

        // Saplings → Dead Bush (phase 2+)
        if (state.is(BlockTags.SAPLINGS) && phase >= 2) {
            level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), 3);
            return;
        }

        // Crops → Air (phase 3+)
        if (state.getBlock() instanceof CropBlock && phase >= 3) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }

        // Dead Bush → Air (phase 3+)
        if (state.is(Blocks.DEAD_BUSH) && phase >= 3) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void decayVolume(ServerLevel level, BlockPos pos, BlockState state, int phase) {
        // --- Leaf decay chain ---
        // Leaves → Dead Leaves (phase 2+)
        if (state.is(BlockTags.LEAVES) && phase >= 2) {
            level.setBlock(pos, ModBlocks.DEAD_LEAVES.get().defaultBlockState(), 3);
            return;
        }
        // Dead Leaves → Air (phase 3+)
        if (state.is(ModBlocks.DEAD_LEAVES.get()) && phase >= 3) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return;
        }

        // --- Log decay chain (preserve axis) ---
        // Logs → Dead Logs (phase 3+)
        if (state.is(BlockTags.LOGS) && phase >= 3) {
            Direction.Axis axis = state.hasProperty(RotatedPillarBlock.AXIS)
                    ? state.getValue(RotatedPillarBlock.AXIS)
                    : Direction.Axis.Y;
            level.setBlock(pos, ModBlocks.DEAD_LOG.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, axis), 3);
            return;
        }
        // Dead Logs → Frozen Logs (phase 4+)
        if (state.is(ModBlocks.DEAD_LOG.get()) && phase >= 4) {
            Direction.Axis axis = state.getValue(RotatedPillarBlock.AXIS);
            level.setBlock(pos, ModBlocks.FROZEN_LOG.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, axis), 3);
        }
    }
}
