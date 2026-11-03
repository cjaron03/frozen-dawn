package com.frozendawn.power;

import com.frozendawn.block.SfrControllerBlock;
import com.frozendawn.block.SfrMultiblock;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Builds a complete NORTH-facing SFR-1 torus around a relative center. */
final class SfrScenes {
    private SfrScenes() {}

    static BlockPos cell(GameTestHelper h, BlockPos center, int dx, int dy, int dz) { return h.absolutePos(center.offset(dx, dy, dz)); }

    /** Coolant Ports at (+-4,0,0); Fuel and Power Ports beside the controller; Vacuum Port and Hatch on the far side. */
    static BlockPos build(GameTestHelper h, BlockPos center) {
        for (int dx = -4; dx <= 4; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -4; dz <= 4; dz++) {
            set(h, cell(h, center, dx, dy, dz), expected(dx, dy, dz));
        }
        set(h, cell(h, center, -4, 0, 0), ModBlocks.COOLANT_PORT.get().defaultBlockState());
        set(h, cell(h, center, 4, 0, 0), ModBlocks.COOLANT_PORT.get().defaultBlockState());
        set(h, cell(h, center, -2, 0, -4), ModBlocks.SFR_FUEL_PORT.get().defaultBlockState());
        set(h, cell(h, center, 2, 0, -4), ModBlocks.SFR_POWER_PORT.get().defaultBlockState());
        set(h, cell(h, center, 0, 0, 4), ModBlocks.SFR_VACUUM_PORT.get().defaultBlockState());
        placeHatch(h, center);
        var controller = cell(h, center, 0, 0, -4);
        set(h, controller, ModBlocks.SFR_CONTROLLER.get().defaultBlockState().setValue(SfrControllerBlock.FACING, Direction.NORTH));
        return controller;
    }

    static void placeHatch(GameTestHelper h, BlockPos center) {
        var hatch = ModBlocks.SFR_MAINTENANCE_HATCH.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        set(h, cell(h, center, 2, -1, 4), hatch.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(h, cell(h, center, 2, 0, 4), hatch.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Replaces the hatch with the shell blocks the table expects there. */
    static void removeHatch(GameTestHelper h, BlockPos center) {
        set(h, cell(h, center, 2, -1, 4), expected(2, -1, 4));
        set(h, cell(h, center, 2, 0, 4), expected(2, 0, 4));
    }

    static BlockState expected(int dx, int dy, int dz) {
        Block block = SfrMultiblock.expected(dx, dy, dz);
        if (block == null) block = SfrMultiblock.shell(dx, dy, dz);
        return block == null ? Blocks.AIR.defaultBlockState() : block.defaultBlockState();
    }

    /** No neighbour or shape updates, so a door half never pops while its partner is placed. */
    static void set(GameTestHelper h, BlockPos absolute, BlockState state) {
        h.getLevel().setBlock(absolute, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
