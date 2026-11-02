package com.frozendawn.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** SMS-1 Hyperion silo: 3 by 3, 4 to 8 tall. Exactly one Controller at the bottom centre of a side, facing out: its
 * facing is the front. The front-centre column above it and the centre column below the top are cells; the top centre
 * is the one Quench Vent, facing up. Every other position is a cell, a Port, or (bottom row only, facing out) a
 * Cryocooler; at least one of each. */
public final class HyperionMultiblock {
    public enum Problem { NONE, UNLOADED, FOOTPRINT, TOO_SHORT, TOO_TALL, CONTROLLERS, GAUGE, INTERIOR, WALL, PORTS, COOLERS, VENTS }
    /** wrong is the first wrong position in x, y, z order; null when only a count is wrong. front is null unless valid. */
    public record Result(Problem problem, BlockPos wrong, BlockPos min, BlockPos max, Direction front, int cells, List<BlockPos> ports, List<BlockPos> coolers, List<BlockPos> vents) {
        public boolean valid() { return problem == Problem.NONE; }
        public int height() { return max.getY() - min.getY() + 1; }
        static Result failed(Problem problem, BlockPos wrong, BlockPos min, BlockPos max) { return new Result(problem, wrong, min, max, null, 0, List.of(), List.of(), List.of()); }
    }
    public static final int WIDTH = 3, MIN_HEIGHT = 4, MAX_HEIGHT = 8, MAX_PARTS = WIDTH * WIDTH * MAX_HEIGHT;
    private HyperionMultiblock() {}

    /** Flood fill over connected parts from the Controller, then validate their bounding box. Bounded by the silo's size. */
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
            if (x1 - x0 >= WIDTH || z1 - z0 >= WIDTH || parts > MAX_PARTS) return Result.failed(Problem.FOOTPRINT, null, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
            if (y1 - y0 >= MAX_HEIGHT) return Result.failed(Problem.TOO_TALL, null, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
            for (var d : Direction.values()) { var n = p.relative(d); if (!seen.contains(n)) queue.add(n); }
        }
        return validate(level, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
    }

    public static Result validate(Level level, BlockPos min, BlockPos max) {
        if (max.getX() - min.getX() != WIDTH - 1 || max.getZ() - min.getZ() != WIDTH - 1) return Result.failed(Problem.FOOTPRINT, null, min, max);
        int height = max.getY() - min.getY() + 1;
        if (height < MIN_HEIGHT) return Result.failed(Problem.TOO_SHORT, null, min, max);
        if (height > MAX_HEIGHT) return Result.failed(Problem.TOO_TALL, null, min, max);
        for (var p : BlockPos.betweenClosed(min, max)) if (!level.isLoaded(p)) return Result.failed(Problem.UNLOADED, p.immutable(), min, max);

        BlockPos controller = null, wrongController = null; int controllers = 0;
        for (var p : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(p);
            if (!(state.getBlock() instanceof HyperionControllerBlock)) continue;
            controllers++;
            if (wrongController == null && (controllers > 1 || !placed(p, min, max, state))) wrongController = p.immutable();
            if (controller == null) controller = p.immutable();
        }
        if (controllers != 1 || wrongController != null) return Result.failed(Problem.CONTROLLERS, wrongController, min, max);

        int cx = min.getX() + 1, cz = min.getZ() + 1, top = max.getY();
        int cells = 0; var ports = new ArrayList<BlockPos>(); var coolers = new ArrayList<BlockPos>(); var vents = new ArrayList<BlockPos>();
        for (int x = min.getX(); x <= max.getX(); x++) for (int y = min.getY(); y <= top; y++) for (int z = min.getZ(); z <= max.getZ(); z++) {
            var pos = new BlockPos(x, y, z);
            if (pos.equals(controller)) continue;
            var state = level.getBlockState(pos); Block block = state.getBlock();
            boolean cell = block instanceof SuperconductingCellBlock;
            if (x == cx && z == cz && y == top) {
                if (!(block instanceof QuenchVentBlock) || state.getValue(HyperionFacingPartBlock.FACING) != Direction.UP) return Result.failed(Problem.VENTS, pos, min, max);
                vents.add(pos); continue;
            }
            if (x == cx && z == cz) { if (!cell) return Result.failed(Problem.INTERIOR, pos, min, max); }
            else if (x == controller.getX() && z == controller.getZ()) { if (!cell) return Result.failed(Problem.GAUGE, pos, min, max); }
            else if (block instanceof CryocoolerBlock) {
                if (y != min.getY() || !horizontalOut(pos, min, max, state)) return Result.failed(Problem.COOLERS, pos, min, max);
                coolers.add(pos); continue;
            } else if (block instanceof QuenchVentBlock) return Result.failed(Problem.VENTS, pos, min, max);
            else if (block instanceof HyperionPortBlock) { ports.add(pos); continue; }
            else if (!cell) return Result.failed(Problem.WALL, pos, min, max);
            cells++;
        }
        if (ports.isEmpty()) return Result.failed(Problem.PORTS, null, min, max);
        if (coolers.isEmpty()) return Result.failed(Problem.COOLERS, null, min, max);
        var front = level.getBlockState(controller).getValue(HyperionFacingPartBlock.FACING);
        return new Result(Problem.NONE, null, min, max, front, cells, List.copyOf(ports), List.copyOf(coolers), List.copyOf(vents));
    }
    /** A Controller's one valid place: the bottom layer, the centre of a side (not a corner), facing out of that side. */
    static boolean placed(BlockPos p, BlockPos min, BlockPos max, BlockState state) {
        boolean xCentre = p.getX() == min.getX() + 1, zCentre = p.getZ() == min.getZ() + 1;
        return p.getY() == min.getY() && xCentre != zCentre && horizontalOut(p, min, max, state);
    }
    static boolean horizontalOut(BlockPos p, BlockPos min, BlockPos max, BlockState state) {
        var d = state.getValue(HyperionFacingPartBlock.FACING);
        return d.getAxis().isHorizontal() && outward(p, min, max, d);
    }
    /** True when the neighbor in that direction lies outside the box. */
    static boolean outward(BlockPos p, BlockPos min, BlockPos max, Direction d) {
        var n = p.relative(d);
        return n.getX() < min.getX() || n.getX() > max.getX() || n.getY() < min.getY() || n.getY() > max.getY() || n.getZ() < min.getZ() || n.getZ() > max.getZ();
    }
}
