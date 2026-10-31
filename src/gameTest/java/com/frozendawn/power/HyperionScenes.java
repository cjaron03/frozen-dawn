package com.frozendawn.power;

import com.frozendawn.block.*;
import com.frozendawn.init.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Builds complete SMS-1 Hyperion boxes for GameTests. Fittings go on non-corner edge cells in a fixed order
 * (Controller, Ports, Cryocoolers, two Vents), so every face stays cells. */
public final class HyperionScenes {
    public record Built(BlockPos controller, List<BlockPos> ports, List<BlockPos> coolers, List<BlockPos> vents, BlockPos min, BlockPos max) {}
    private HyperionScenes() {}

    /** min is relative to the test; returned positions are absolute. */
    public static Built build(GameTestHelper h, BlockPos min, int sx, int sy, int sz, int ports, int coolers) {
        var lo = h.absolutePos(min); var hi = lo.offset(sx - 1, sy - 1, sz - 1); var level = h.getLevel();
        for (var p : BlockPos.betweenClosed(lo, hi)) {
            int extremes = extremes(p, lo, hi);
            level.setBlock(p, (extremes >= 2 ? ModBlocks.HYPERION_FRAME : ModBlocks.SUPERCONDUCTING_CELL).get().defaultBlockState(), 3);
        }
        var edges = edges(lo, hi); int i = 0;
        var controller = edges.get(i++); place(h, controller, ModBlocks.HYPERION_CONTROLLER.get().defaultBlockState(), lo, hi);
        var portList = new ArrayList<BlockPos>(); var coolerList = new ArrayList<BlockPos>(); var ventList = new ArrayList<BlockPos>();
        for (int k = 0; k < ports; k++) { var p = edges.get(i++); place(h, p, ModBlocks.HYPERION_PORT.get().defaultBlockState(), lo, hi); portList.add(p); }
        for (int k = 0; k < coolers; k++) { var p = edges.get(i++); place(h, p, ModBlocks.CRYOCOOLER.get().defaultBlockState(), lo, hi); coolerList.add(p); }
        for (int k = 0; k < 2; k++) { var p = edges.get(i++); place(h, p, ModBlocks.QUENCH_VENT.get().defaultBlockState(), lo, hi); ventList.add(p); }
        // Deep cold by default, so storage tests never wait on a cool-down; thermal tests set their own surroundings.
        if (level.getBlockEntity(controller) instanceof HyperionControllerBlockEntity c) c.setSurroundingsForTest(-250.0);
        return new Built(controller, List.copyOf(portList), List.copyOf(coolerList), List.copyOf(ventList), lo, hi);
    }
    public static int extremes(BlockPos p, BlockPos lo, BlockPos hi) {
        return (p.getX() == lo.getX() || p.getX() == hi.getX() ? 1 : 0) + (p.getY() == lo.getY() || p.getY() == hi.getY() ? 1 : 0)
                + (p.getZ() == lo.getZ() || p.getZ() == hi.getZ() ? 1 : 0);
    }
    /** Non-corner edge cells, bottom edges first, then top, then the vertical ones. */
    static List<BlockPos> edges(BlockPos lo, BlockPos hi) {
        var out = new ArrayList<BlockPos>();
        for (int y : new int[]{lo.getY(), hi.getY()}) {
            for (int x = lo.getX() + 1; x < hi.getX(); x++) { out.add(new BlockPos(x, y, lo.getZ())); out.add(new BlockPos(x, y, hi.getZ())); }
            for (int z = lo.getZ() + 1; z < hi.getZ(); z++) { out.add(new BlockPos(lo.getX(), y, z)); out.add(new BlockPos(hi.getX(), y, z)); }
        }
        for (int y = lo.getY() + 1; y < hi.getY(); y++) for (int x : new int[]{lo.getX(), hi.getX()}) for (int z : new int[]{lo.getZ(), hi.getZ()}) out.add(new BlockPos(x, y, z));
        return out;
    }
    /** An outward direction for a shell cell: y first, then x, then z. */
    public static Direction outward(BlockPos p, BlockPos lo, BlockPos hi) {
        if (p.getY() == lo.getY()) return Direction.DOWN; if (p.getY() == hi.getY()) return Direction.UP;
        if (p.getX() == lo.getX()) return Direction.WEST; if (p.getX() == hi.getX()) return Direction.EAST;
        return p.getZ() == lo.getZ() ? Direction.NORTH : Direction.SOUTH;
    }
    private static void place(GameTestHelper h, BlockPos p, BlockState state, BlockPos lo, BlockPos hi) {
        if (state.hasProperty(BlockStateProperties.FACING)) state = state.setValue(BlockStateProperties.FACING, outward(p, lo, hi));
        h.getLevel().setBlock(p, state, 3);
    }
}
