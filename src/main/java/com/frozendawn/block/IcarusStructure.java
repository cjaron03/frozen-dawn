package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/** AMR-1 Icarus, an open 17x17x8 cross around the Halsey Core. Cells are written in a canonical frame with the Controller
 * facing SOUTH, the core at the origin and y up; (x, z) turn with the Controller's FACING. Parts are listed in build-stage
 * order (frame, injector, stabilizers, core, support), which is also the order validate reports problems in. The live
 * envelope is the space the shield fills: it must be air at Start and is vaporized while the reactor runs. */
public final class IcarusStructure {
    public enum Problem { OK, UNLOADED, FRAME, INJECTOR, STABILIZER, CORE, SUPPORT, ENVELOPE }
    /** wrong is the first wrong cell in stage order, then the envelope; parts and envelope are empty unless valid. */
    public record Result(Problem problem, @Nullable BlockPos wrong, BlockPos core, BlockPos powerPort, BlockPos matterPort,
                         BlockPos capsuleDock, BlockPos reserveBank, List<BlockPos> parts, List<BlockPos> envelope) {
        public boolean valid() { return problem == Problem.OK; }
    }
    /** A canonical part cell; facing, when set, is the canonical direction its HORIZONTAL_FACING must point. */
    private record Part(int x, int y, int z, Problem stage, Supplier<? extends Block> block, @Nullable Direction facing) {}

    public static final int HALF = 8, BOTTOM = -6, TOP = 4;
    private static final double ENVELOPE_RADIUS = 4.5;
    private static final List<Part> PARTS = parts();
    private static final List<BlockPos> ENVELOPE = envelope();
    private static final Set<Long> PART_CELLS = new HashSet<>();
    static { for (var p : PARTS) PART_CELLS.add(BlockPos.asLong(p.x, p.y, p.z)); }
    private IcarusStructure() {}

    private static List<Part> parts() {
        var parts = new ArrayList<Part>();
        for (int x = -HALF; x <= HALF; x++) for (int z = -HALF; z <= HALF; z++)
            if (Math.max(Math.abs(x), Math.abs(z)) == HALF) parts.add(new Part(x, BOTTOM, z, Problem.FRAME, ModBlocks.ICARUS_FRAME, null));
        for (int n = 2; n <= 7; n++) for (int s = -1; s <= 1; s += 2) {
            parts.add(new Part(s * n, BOTTOM, 0, Problem.FRAME, ModBlocks.ICARUS_FRAME, null));
            parts.add(new Part(0, BOTTOM, s * n, Problem.FRAME, ModBlocks.ICARUS_FRAME, null));
        }
        for (int y = BOTTOM + 1; y <= -2; y++) for (int s = -1; s <= 1; s += 2) {
            parts.add(new Part(s * HALF, y, 0, Problem.FRAME, ModBlocks.ICARUS_FRAME, null));
            parts.add(new Part(0, y, s * HALF, Problem.FRAME, ModBlocks.ICARUS_FRAME, null));
        }
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) parts.add(new Part(x, BOTTOM, z, Problem.INJECTOR, ModBlocks.ICARUS_INJECTOR_CASING, null));
        parts.add(new Part(0, BOTTOM + 1, 0, Problem.INJECTOR, ModBlocks.ICARUS_INJECTOR, null));
        for (var axis : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH)) {
            int ax = axis.getStepX(), az = axis.getStepZ();
            parts.add(new Part(7 * ax, 0, 7 * az, Problem.STABILIZER, ModBlocks.ICARUS_STABILIZER, axis.getOpposite()));
            for (int t = -1; t <= 1; t++) for (int y = -1; y <= 1; y++)
                parts.add(new Part(HALF * ax + t * az, y, HALF * az + t * ax, Problem.STABILIZER, ModBlocks.ICARUS_STABILIZER_HOUSING, null));
        }
        parts.add(new Part(0, 0, 0, Problem.CORE, ModBlocks.HALSEY_CORE, null));
        parts.add(new Part(-3, BOTTOM + 1, HALF, Problem.SUPPORT, ModBlocks.ICARUS_MATTER_PORT, null));
        parts.add(new Part(-2, BOTTOM + 1, HALF, Problem.SUPPORT, ModBlocks.ICARUS_CAPSULE_DOCK, null));
        parts.add(new Part(-1, BOTTOM + 1, HALF, Problem.SUPPORT, ModBlocks.ICARUS_CONTROLLER, Direction.SOUTH));
        parts.add(new Part(1, BOTTOM + 1, HALF, Problem.SUPPORT, ModBlocks.ICARUS_POWER_PORT, null));
        parts.add(new Part(2, BOTTOM + 1, HALF, Problem.SUPPORT, ModBlocks.ICARUS_RESERVE_BANK, null));
        return List.copyOf(parts);
    }
    /** Every cell within 4.5 of the core but the core itself, and the beam cells at 5 and 6 on each stabilizer axis. The set
     * is symmetric under quarter turns, so it needs no rotation. */
    private static List<BlockPos> envelope() {
        var cells = new ArrayList<BlockPos>();
        int r = (int) ENVELOPE_RADIUS;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++)
            if ((x | y | z) != 0 && x * x + y * y + z * z <= ENVELOPE_RADIUS * ENVELOPE_RADIUS) cells.add(new BlockPos(x, y, z));
        for (int d = 5; d <= 6; d++) for (var axis : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH))
            cells.add(new BlockPos(d * axis.getStepX(), 0, d * axis.getStepZ()));
        return List.copyOf(cells);
    }

    private static BlockPos at(BlockPos core, Direction facing, int x, int y, int z) {
        var across = facing.getCounterClockWise();
        return core.offset(x * across.getStepX() + z * facing.getStepX(), y, x * across.getStepZ() + z * facing.getStepZ());
    }
    private static Direction turn(Direction canonical, Direction facing) {
        var across = facing.getCounterClockWise();
        int x = canonical.getStepX(), z = canonical.getStepZ();
        return Direction.getNearest(x * across.getStepX() + z * facing.getStepX(), 0, x * across.getStepZ() + z * facing.getStepZ());
    }
    private static BlockState ghost(Part part, Direction facing) {
        var state = part.block.get().defaultBlockState();
        return part.facing == null ? state : state.setValue(BlockStateProperties.HORIZONTAL_FACING, turn(part.facing, facing));
    }
    private static boolean holds(BlockState state, Part part, Direction facing) {
        if (!state.is(part.block.get())) return false;
        return part.facing == null || state.getValue(BlockStateProperties.HORIZONTAL_FACING) == turn(part.facing, facing);
    }

    public static BlockPos core(BlockPos controller, Direction facing) {
        var across = facing.getCounterClockWise();
        return controller.offset(across.getStepX() - 8 * facing.getStepX(), 5, across.getStepZ() - 8 * facing.getStepZ());
    }

    /** Places every part around core, as the ghost guide shows it, and returns the Controller. The envelope is left as it is. */
    public static BlockPos place(Level l, BlockPos core, Direction facing) {
        for (var part : PARTS) l.setBlock(at(core, facing, part.x, part.y, part.z), ghost(part, facing), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return at(core, facing, -1, BOTTOM + 1, HALF);
    }
    /** The world cell at canonical (x, y, z) around core. */
    public static BlockPos cell(BlockPos core, Direction facing, int x, int y, int z) { return at(core, facing, x, y, z); }

    public static Result validate(Level l, BlockPos controller, Direction facing, boolean checkEnvelope) {
        var core = core(controller, facing);
        BlockPos matter = at(core, facing, -3, BOTTOM + 1, HALF), dock = at(core, facing, -2, BOTTOM + 1, HALF);
        BlockPos power = at(core, facing, 1, BOTTOM + 1, HALF), reserve = at(core, facing, 2, BOTTOM + 1, HALF);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
            if (!l.isLoaded(core.offset(sx * HALF, 0, sz * HALF)))
                return new Result(Problem.UNLOADED, null, core, power, matter, dock, reserve, List.of(), List.of());
        var parts = new ArrayList<BlockPos>(PARTS.size());
        for (var part : PARTS) {
            var pos = at(core, facing, part.x, part.y, part.z);
            if (!holds(l.getBlockState(pos), part, facing)) return new Result(part.stage, pos, core, power, matter, dock, reserve, List.of(), List.of());
            parts.add(pos);
        }
        var envelope = new ArrayList<BlockPos>(ENVELOPE.size());
        for (var offset : ENVELOPE) {
            var pos = core.offset(offset);
            if (checkEnvelope && !l.getBlockState(pos).isAir()) return new Result(Problem.ENVELOPE, pos, core, power, matter, dock, reserve, List.of(), List.of());
            envelope.add(pos);
        }
        return new Result(Problem.OK, null, core, power, matter, dock, reserve, List.copyOf(parts), List.copyOf(envelope));
    }

    /** Whether pos is one of the reactor's part cells (not an envelope cell). */
    public static boolean contains(BlockPos controller, Direction facing, BlockPos pos) {
        var d = pos.subtract(core(controller, facing)); var across = facing.getCounterClockWise();
        int x = d.getX() * across.getStepX() + d.getZ() * across.getStepZ(), z = d.getX() * facing.getStepX() + d.getZ() * facing.getStepZ();
        return PART_CELLS.contains(BlockPos.asLong(x, d.getY(), z));
    }

    /** The first stage with a wrong cell, ENVELOPE when only the shield space holds blocks, or OK when nothing is left. */
    public static Problem stage(Level l, BlockPos controller, Direction facing) {
        var cells = wrongCells(l, controller, facing, 1);
        if (cells.isEmpty()) return Problem.OK;
        var first = cells.get(0);
        if (first.ghost() == null) return Problem.ENVELOPE;
        var core = core(controller, facing);
        for (var part : PARTS) if (at(core, facing, part.x, part.y, part.z).equals(first.pos())) return part.stage;
        return Problem.OK;
    }

    /** Up to limit wrong cells of the first incomplete stage only; once every part is in place, the envelope cells that
     * still hold a block, as cells to clear. */
    public static List<GhostCell> wrongCells(Level l, BlockPos controller, Direction facing, int limit) {
        var core = core(controller, facing); var wrong = new ArrayList<GhostCell>();
        Problem stage = null;
        for (var part : PARTS) {
            if (stage != null && part.stage != stage) break;
            if (wrong.size() >= limit) return wrong;
            var pos = at(core, facing, part.x, part.y, part.z);
            if (holds(l.getBlockState(pos), part, facing)) continue;
            stage = part.stage;
            wrong.add(new GhostCell(pos, ghost(part, facing)));
        }
        if (stage != null) return wrong;
        for (var offset : ENVELOPE) {
            if (wrong.size() >= limit) break;
            var pos = core.offset(offset);
            if (!l.getBlockState(pos).isAir()) wrong.add(new GhostCell(pos, null));
        }
        return wrong;
    }

    /** Blocks still missing from every stage, by block, in stage order. A part placed facing the wrong way is not missing. */
    public static Map<Block, Integer> remaining(Level l, BlockPos controller, Direction facing) {
        var core = core(controller, facing); var counts = new LinkedHashMap<Block, Integer>();
        for (var part : PARTS) {
            var block = part.block.get();
            if (!l.getBlockState(at(core, facing, part.x, part.y, part.z)).is(block)) counts.merge(block, 1, Integer::sum);
        }
        return counts;
    }
}
