package com.frozendawn.power;

import com.frozendawn.block.IcarusControllerBlock;
import com.frozendawn.block.IcarusStabilizerBlock;
import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Builds the AMR-1 Icarus from the plan's Geometry table, around a relative core position. Canonical cells face SOUTH;
 * (x, z) turn with the Controller's facing. Each stage is its own method so tests can build part of it. */
final class IcarusScenes {
    private IcarusScenes() {}

    /** The world cell at canonical (x, y, z) around the core. */
    static BlockPos cell(GameTestHelper h, BlockPos center, Direction facing, int x, int y, int z) {
        var across = facing.getCounterClockWise();
        return h.absolutePos(center).offset(x * across.getStepX() + z * facing.getStepX(), y, x * across.getStepZ() + z * facing.getStepZ());
    }
    static BlockPos controller(GameTestHelper h, BlockPos center, Direction facing) { return cell(h, center, facing, -1, -5, 8); }

    static BlockPos build(GameTestHelper h, BlockPos center) { return build(h, center, Direction.SOUTH); }
    static BlockPos build(GameTestHelper h, BlockPos center, Direction facing) {
        frame(h, center, facing); injector(h, center, facing); stabilizers(h, center, facing); core(h, center, facing);
        return support(h, center, facing);
    }

    static void frame(GameTestHelper h, BlockPos center, Direction facing) {
        var frame = ModBlocks.ICARUS_FRAME.get();
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++)
            if (Math.max(Math.abs(x), Math.abs(z)) == 8) set(h, cell(h, center, facing, x, -6, z), frame);
        for (int n = 2; n <= 7; n++) for (int s = -1; s <= 1; s += 2) {
            set(h, cell(h, center, facing, s * n, -6, 0), frame);
            set(h, cell(h, center, facing, 0, -6, s * n), frame);
        }
        for (int y = -5; y <= -2; y++) for (int s = -1; s <= 1; s += 2) {
            set(h, cell(h, center, facing, s * 8, y, 0), frame);
            set(h, cell(h, center, facing, 0, y, s * 8), frame);
        }
    }

    static void injector(GameTestHelper h, BlockPos center, Direction facing) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) set(h, cell(h, center, facing, x, -6, z), ModBlocks.ICARUS_INJECTOR_CASING.get());
        set(h, cell(h, center, facing, 0, -5, 0), ModBlocks.ICARUS_INJECTOR.get());
    }

    static void stabilizers(GameTestHelper h, BlockPos center, Direction facing) {
        int[][] axes = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (var a : axes) {
            var emitter = cell(h, center, facing, 7 * a[0], 0, 7 * a[1]);
            var inward = Direction.getNearest(-(emitter.getX() - h.absolutePos(center).getX()), 0, -(emitter.getZ() - h.absolutePos(center).getZ()));
            set(h, emitter, ModBlocks.ICARUS_STABILIZER.get().defaultBlockState().setValue(IcarusStabilizerBlock.FACING, inward));
            for (int t = -1; t <= 1; t++) for (int y = -1; y <= 1; y++)
                set(h, cell(h, center, facing, 8 * a[0] + t * a[1], y, 8 * a[1] + t * a[0]), ModBlocks.ICARUS_STABILIZER_HOUSING.get());
        }
    }

    static void core(GameTestHelper h, BlockPos center, Direction facing) { set(h, cell(h, center, facing, 0, 0, 0), ModBlocks.HALSEY_CORE.get()); }

    /** Places the support row and returns the Controller. */
    static BlockPos support(GameTestHelper h, BlockPos center, Direction facing) {
        set(h, cell(h, center, facing, -3, -5, 8), ModBlocks.ICARUS_MATTER_PORT.get());
        set(h, cell(h, center, facing, -2, -5, 8), ModBlocks.ICARUS_CAPSULE_DOCK.get());
        set(h, cell(h, center, facing, 1, -5, 8), ModBlocks.ICARUS_POWER_PORT.get());
        set(h, cell(h, center, facing, 2, -5, 8), ModBlocks.ICARUS_RESERVE_BANK.get());
        return placeController(h, center, facing);
    }
    static BlockPos placeController(GameTestHelper h, BlockPos center, Direction facing) {
        var controller = controller(h, center, facing);
        set(h, controller, ModBlocks.ICARUS_CONTROLLER.get().defaultBlockState().setValue(IcarusControllerBlock.FACING, facing));
        return controller;
    }

    private static void set(GameTestHelper h, BlockPos absolute, Block block) { set(h, absolute, block.defaultBlockState()); }
    static void set(GameTestHelper h, BlockPos absolute, BlockState state) {
        h.getLevel().setBlock(absolute, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }
}
