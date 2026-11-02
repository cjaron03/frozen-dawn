package com.frozendawn.power;

import com.frozendawn.block.*;
import com.frozendawn.init.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Builds complete SMS-1 Hyperion silos for GameTests, front to the north (low z): the Controller at the bottom of the
 * front-centre column, the Vent at top centre, Ports up the back-centre column from the bottom, Cryocoolers in the
 * bottom row (west, east, back centre, then the back corners, skipping any slot a Port took), cells everywhere else. */
public final class HyperionScenes {
    public record Built(BlockPos controller, List<BlockPos> ports, List<BlockPos> coolers, List<BlockPos> vents, BlockPos min, BlockPos max) {}
    private record Slot(int x, int z, Direction facing) {}
    private static final Slot[] COOLERS = {new Slot(0, 1, Direction.WEST), new Slot(2, 1, Direction.EAST), new Slot(1, 2, Direction.SOUTH),
            new Slot(0, 2, Direction.WEST), new Slot(2, 2, Direction.EAST)};
    private HyperionScenes() {}

    /** min is relative to the test; returned positions are absolute. */
    public static Built silo(GameTestHelper h, BlockPos min, int height, int ports, int coolers) {
        var lo = h.absolutePos(min); var hi = lo.offset(2, height - 1, 2); var level = h.getLevel();
        for (var p : BlockPos.betweenClosed(lo, hi)) level.setBlock(p, ModBlocks.SUPERCONDUCTING_CELL.get().defaultBlockState(), 3);
        var controller = lo.offset(1, 0, 0); place(h, controller, ModBlocks.HYPERION_CONTROLLER.get(), Direction.NORTH);
        var vent = lo.offset(1, height - 1, 1); place(h, vent, ModBlocks.QUENCH_VENT.get(), Direction.UP);
        var portList = new ArrayList<BlockPos>(); var coolerList = new ArrayList<BlockPos>();
        for (int y = 0; y < ports; y++) { var p = lo.offset(1, y, 2); place(h, p, ModBlocks.HYPERION_PORT.get(), Direction.SOUTH); portList.add(p); }
        for (int i = 0; coolerList.size() < coolers; i++) {
            var s = COOLERS[i]; var p = lo.offset(s.x(), 0, s.z());
            if (portList.contains(p)) continue;
            place(h, p, ModBlocks.CRYOCOOLER.get(), s.facing()); coolerList.add(p);
        }
        // Deep cold by default, so storage tests never wait on a cool-down; thermal tests set their own surroundings.
        if (level.getBlockEntity(controller) instanceof HyperionControllerBlockEntity c) c.setSurroundingsForTest(-250.0);
        return new Built(controller, List.copyOf(portList), List.copyOf(coolerList), List.of(vent), lo, hi);
    }
    static void place(GameTestHelper h, BlockPos p, Block block, Direction facing) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(HyperionFacingPartBlock.FACING)) state = state.setValue(HyperionFacingPartBlock.FACING, facing);
        h.getLevel().setBlock(p, state, 3);
    }
}
