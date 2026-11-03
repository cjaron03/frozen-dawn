package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** CFU-7 shell, 3x3x3 around the core. The Controller sits on a middle-layer face centre, FACING outward.
 * Top: 8 casings around the Control Rod Drive. Middle: core, Controller, 2 Coolant Ports and 5 casings.
 * Bottom: 9 casings. 22 casings in all. */
public final class ReactorMultiblock {
    public enum Problem { NONE, CASING, CORE, ROD_DRIVE, PORTS, UNLOADED }
    /** offset is the index of the first wrong cell around the core: (dx+1)*9+(dy+1)*3+(dz+1). */
    public record Result(Problem problem, int offset, BlockPos core, List<BlockPos> ports) {
        public boolean valid() { return problem == Problem.NONE; }
    }
    private ReactorMultiblock() {}
    public static BlockPos core(BlockPos controller, Direction facing) { return controller.relative(facing.getOpposite()); }
    public static int offsetIndex(int dx, int dy, int dz) { return (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1); }
    public static BlockPos offsetPos(BlockPos core, int index) {
        return core.offset(index / 9 - 1, index / 3 % 3 - 1, index % 3 - 1);
    }
    public static boolean casing(Block block) { return block instanceof ReactorCasingBlock; }

    public static Result validate(Level level, BlockPos controller, Direction facing) {
        var core = core(controller, facing);
        var ports = new ArrayList<BlockPos>();
        int extraCasing = -1;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            var pos = core.offset(dx, dy, dz); int index = offsetIndex(dx, dy, dz);
            if (!level.isLoaded(pos)) return new Result(Problem.UNLOADED, index, core, List.of());
            if (pos.equals(controller)) continue;
            var block = level.getBlockState(pos).getBlock();
            if (dx == 0 && dz == 0 && dy == 1) {
                if (block != ModBlocks.CONTROL_ROD_DRIVE.get()) return new Result(Problem.ROD_DRIVE, index, core, List.of());
            } else if (dx == 0 && dy == 0 && dz == 0) {
                if (block != ModBlocks.REACTOR_CORE.get()) return new Result(Problem.CORE, index, core, List.of());
            } else if (dy == 0 && Math.abs(dx) + Math.abs(dz) == 1) {
                // The three free middle faces hold exactly two Coolant Ports; the third is a casing.
                if (block instanceof CoolantPortBlock) ports.add(pos.immutable());
                else if (casing(block) && extraCasing < 0) extraCasing = index;
                else return new Result(casing(block) ? Problem.PORTS : Problem.CASING, index, core, List.of());
            } else if (!casing(block)) return new Result(Problem.CASING, index, core, List.of());
        }
        if (ports.size() != 2) return new Result(Problem.PORTS, extraCasing, core, List.of());
        return new Result(Problem.NONE, -1, core, List.copyOf(ports));
    }
}
