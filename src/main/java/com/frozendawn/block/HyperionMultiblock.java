package com.frozendawn.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** SMS-1 Hyperion box, each side 3 to 7. Corners are Frame; other edge cells are Frame or a fitting; face cells are
 * Superconducting Cells or a fitting; the interior is all cells. Fittings: exactly one Controller, one or more Ports,
 * one or more Cryocoolers and exactly two Quench Vents facing out of the box. */
public final class HyperionMultiblock {
    public enum Problem { NONE, UNLOADED, TOO_LARGE, TOO_SMALL, CORNER, EDGE, FACE, INTERIOR, CONTROLLERS, PORTS, COOLERS, VENTS }
    /** wrong is the first wrong cell in x, y, z order; null when only a count is wrong. */
    public record Result(Problem problem, BlockPos wrong, BlockPos min, BlockPos max, int cells, List<BlockPos> ports, List<BlockPos> coolers, List<BlockPos> vents) {
        public boolean valid() { return problem == Problem.NONE; }
        static Result failed(Problem problem, BlockPos wrong, BlockPos min, BlockPos max) { return new Result(problem, wrong, min, max, 0, List.of(), List.of(), List.of()); }
    }
    public static final int MIN_SIDE = 3, MAX_SIDE = 7, MAX_PARTS = MAX_SIDE * MAX_SIDE * MAX_SIDE;
    private HyperionMultiblock() {}

    /** Flood fill over connected parts from the Controller, then validate their bounding box. Bounded by MAX_PARTS. */
    public static Result discover(Level level, BlockPos controller) {
        var seen = new HashSet<BlockPos>(); var queue = new ArrayDeque<BlockPos>(); queue.add(controller.immutable()); int parts = 0;
        int x0 = controller.getX(), y0 = controller.getY(), z0 = controller.getZ(), x1 = x0, y1 = y0, z1 = z0;
        while (!queue.isEmpty()) {
            var p = queue.removeFirst();
            if (!seen.add(p)) continue;
            if (!level.isLoaded(p)) return Result.failed(Problem.UNLOADED, p, controller, controller);
            if (!(level.getBlockState(p).getBlock() instanceof HyperionPartBlock)) continue;
            parts++;
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
            if (parts > MAX_PARTS || x1 - x0 >= MAX_SIDE || y1 - y0 >= MAX_SIDE || z1 - z0 >= MAX_SIDE)
                return Result.failed(Problem.TOO_LARGE, null, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
            for (var d : Direction.values()) { var n = p.relative(d); if (!seen.contains(n)) queue.add(n); }
        }
        return validate(level, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
    }

    public static Result validate(Level level, BlockPos min, BlockPos max) {
        if (max.getX() - min.getX() >= MAX_SIDE || max.getY() - min.getY() >= MAX_SIDE || max.getZ() - min.getZ() >= MAX_SIDE)
            return Result.failed(Problem.TOO_LARGE, null, min, max);
        if (max.getX() - min.getX() < MIN_SIDE - 1 || max.getY() - min.getY() < MIN_SIDE - 1 || max.getZ() - min.getZ() < MIN_SIDE - 1)
            return Result.failed(Problem.TOO_SMALL, null, min, max);
        for (var c : new BlockPos[]{min, max, new BlockPos(min.getX(), max.getY(), min.getZ()), new BlockPos(max.getX(), min.getY(), max.getZ())})
            if (!level.isLoaded(c)) return Result.failed(Problem.UNLOADED, c, min, max);
        int cells = 0, controllers = 0; var ports = new ArrayList<BlockPos>(); var coolers = new ArrayList<BlockPos>(); var vents = new ArrayList<BlockPos>();
        boolean ventsOut = true;
        for (int x = min.getX(); x <= max.getX(); x++) for (int y = min.getY(); y <= max.getY(); y++) for (int z = min.getZ(); z <= max.getZ(); z++) {
            var pos = new BlockPos(x, y, z);
            if (!level.isLoaded(pos)) return Result.failed(Problem.UNLOADED, pos, min, max);
            var state = level.getBlockState(pos); Block block = state.getBlock();
            int extremes = extremes(pos, min, max);
            boolean fitting = block instanceof HyperionPortBlock || block instanceof CryocoolerBlock || block instanceof QuenchVentBlock || block instanceof HyperionControllerBlock;
            if (extremes == 3 && !(block instanceof HyperionFrameBlock)) return Result.failed(Problem.CORNER, pos, min, max);
            if (extremes == 2 && !(block instanceof HyperionFrameBlock) && !fitting) return Result.failed(Problem.EDGE, pos, min, max);
            if (extremes == 1 && !(block instanceof SuperconductingCellBlock) && !fitting) return Result.failed(Problem.FACE, pos, min, max);
            if (extremes == 0 && !(block instanceof SuperconductingCellBlock)) return Result.failed(Problem.INTERIOR, pos, min, max);
            if (block instanceof SuperconductingCellBlock) cells++;
            else if (block instanceof HyperionControllerBlock) controllers++;
            else if (block instanceof HyperionPortBlock) ports.add(pos);
            else if (block instanceof CryocoolerBlock) coolers.add(pos);
            else if (block instanceof QuenchVentBlock) { vents.add(pos); ventsOut &= outward(pos, min, max, state.getValue(HyperionFacingPartBlock.FACING)); }
        }
        if (controllers != 1) return Result.failed(Problem.CONTROLLERS, null, min, max);
        if (ports.isEmpty()) return Result.failed(Problem.PORTS, null, min, max);
        if (coolers.isEmpty()) return Result.failed(Problem.COOLERS, null, min, max);
        if (vents.size() != 2 || !ventsOut) return Result.failed(Problem.VENTS, null, min, max);
        return new Result(Problem.NONE, null, min, max, cells, List.copyOf(ports), List.copyOf(coolers), List.copyOf(vents));
    }
    static int extremes(BlockPos p, BlockPos min, BlockPos max) {
        return (p.getX() == min.getX() || p.getX() == max.getX() ? 1 : 0) + (p.getY() == min.getY() || p.getY() == max.getY() ? 1 : 0)
                + (p.getZ() == min.getZ() || p.getZ() == max.getZ() ? 1 : 0);
    }
    /** True when the neighbor in that direction lies outside the box. */
    static boolean outward(BlockPos p, BlockPos min, BlockPos max, Direction d) {
        var n = p.relative(d);
        return n.getX() < min.getX() || n.getX() > max.getX() || n.getY() < min.getY() || n.getY() > max.getY() || n.getZ() < min.getZ() || n.getZ() > max.getZ();
    }
}
