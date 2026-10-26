package com.frozendawn.block;

import com.frozendawn.init.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** SFR-1 torus, 9x5x9 around the Confinement Core; r = max(|dx|, |dz|), dy from -2 to +2.
 * r <= 1: coil column with the core at the origin. r = 2: First Wall. r = 3: the air channel, capped by coils at dy = +-2.
 * r = 4: alternating coils and Lithium Blanket; any non-corner cell may hold a port instead. The Controller sits on
 * that ring at dy = 0, FACING outward. Cells outside these bands are not part of the shell. */
public final class SfrMultiblock {
    public enum Problem { NONE, COIL, FIRST_WALL, BLANKET, CORE, CHANNEL, PORTS, UNLOADED }
    /** wrong is the first wrong cell in x, y, z order; null when only the port counts are wrong. */
    public record Result(Problem problem, BlockPos wrong, BlockPos center, List<BlockPos> coolantPorts, BlockPos fuelPort,
                         BlockPos powerPort, BlockPos vacuumPort, BlockPos hatch, Set<BlockPos> channel, List<BlockPos> firstWall,
                         List<BlockPos> coils) {
        public boolean valid() { return problem == Problem.NONE; }
        static Result failed(Problem problem, BlockPos wrong, BlockPos center) {
            return new Result(problem, wrong, center, List.of(), null, null, null, null, Set.of(), List.of(), List.of());
        }
    }
    public static final int RADIUS = 4, CHANNEL_RADIUS = 3;
    private SfrMultiblock() {}

    public static BlockPos center(BlockPos controller, Direction facing) { return controller.relative(facing.getOpposite(), RADIUS); }

    /** The block a cell must hold, or null for channel cells, port-eligible cells and cells outside the shell. */
    public static Block expected(int dx, int dy, int dz) {
        int r = Math.max(Math.abs(dx), Math.abs(dz));
        if (r > RADIUS || Math.abs(dy) > 2) return null;
        if (Math.abs(dy) == 2) return r == CHANNEL_RADIUS ? ModBlocks.MAGNET_COIL.get() : null;
        if (r <= 1) return dx == 0 && dy == 0 && dz == 0 ? ModBlocks.CONFINEMENT_CORE.get() : ModBlocks.MAGNET_COIL.get();
        if (r == 2) return ModBlocks.FIRST_WALL.get();
        if (r == CHANNEL_RADIUS) return null;
        return corner(dx, dz) ? shell(dx, dy, dz) : null;
    }
    /** The outer-ring block a cell holds when it holds no port, or null off the outer ring. */
    public static Block shell(int dx, int dy, int dz) {
        if (Math.max(Math.abs(dx), Math.abs(dz)) != RADIUS || Math.abs(dy) > 1) return null;
        return Math.floorMod(dx + dy + dz, 2) == 0 ? ModBlocks.MAGNET_COIL.get() : ModBlocks.LITHIUM_BLANKET.get();
    }
    public static boolean channel(int dx, int dy, int dz) { return Math.max(Math.abs(dx), Math.abs(dz)) == CHANNEL_RADIUS && Math.abs(dy) <= 1; }
    private static boolean corner(int dx, int dz) { return Math.abs(dx) == RADIUS && Math.abs(dz) == RADIUS; }

    public static Result validate(Level level, BlockPos controller, Direction facing) {
        var center = center(controller, facing);
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) for (int sz = -1; sz <= 1; sz += 2)
            if (!level.isLoaded(center.offset(sx * RADIUS, sy * 2, sz * RADIUS))) return Result.failed(Problem.UNLOADED, null, center);
        var coolant = new ArrayList<BlockPos>(); var channel = new HashSet<BlockPos>();
        var firstWall = new ArrayList<BlockPos>(); var coils = new ArrayList<BlockPos>();
        BlockPos fuel = null, power = null, vacuum = null, hatch = null;
        var coil = ModBlocks.MAGNET_COIL.get();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            var pos = center.offset(dx, dy, dz);
            if (pos.equals(controller)) continue;
            var state = level.getBlockState(pos); var block = state.getBlock();
            if (channel(dx, dy, dz)) {
                if (!state.isAir()) return Result.failed(Problem.CHANNEL, pos, center);
                channel.add(pos);
                continue;
            }
            var expected = expected(dx, dy, dz);
            if (expected == null && shell(dx, dy, dz) != null) {
                // Port-eligible: a port, or the shell block the ring has there.
                if (block instanceof CoolantPortBlock) {
                    if (coolant.size() == 2) return Result.failed(Problem.PORTS, pos, center);
                    coolant.add(pos);
                } else if (block instanceof SfrFuelPortBlock) {
                    if (fuel != null) return Result.failed(Problem.PORTS, pos, center);
                    fuel = pos;
                } else if (block instanceof SfrPowerPortBlock) {
                    if (power != null) return Result.failed(Problem.PORTS, pos, center);
                    power = pos;
                } else if (block instanceof SfrVacuumPortBlock) {
                    if (vacuum != null) return Result.failed(Problem.PORTS, pos, center);
                    vacuum = pos;
                } else if (block instanceof SfrHatchBlock) {
                    var half = state.getValue(DoorBlock.HALF);
                    if (half == DoubleBlockHalf.LOWER && dy == -1 && level.getBlockState(pos.above()).is(block)) {
                        if (hatch != null) return Result.failed(Problem.PORTS, pos, center);
                        hatch = pos;
                    } else if (!(half == DoubleBlockHalf.UPPER && dy == 0 && level.getBlockState(pos.below()).is(block))) {
                        return Result.failed(Problem.PORTS, pos, center);
                    }
                } else expected = shell(dx, dy, dz);
            }
            if (expected == null) continue;
            if (block != expected) return Result.failed(problem(expected), pos, center);
            if (block == coil) coils.add(pos);
            else if (block instanceof FirstWallBlock) firstWall.add(pos);
        }
        if (coolant.size() != 2 || fuel == null || power == null || vacuum == null || hatch == null)
            return Result.failed(Problem.PORTS, null, center);
        return new Result(Problem.NONE, null, center, List.copyOf(coolant), fuel, power, vacuum, hatch, Set.copyOf(channel),
                List.copyOf(firstWall), List.copyOf(coils));
    }

    /** A cell that does not hold what the torus needs there, with the state its ghost shows; null for a channel cell that must be air. */
    public record Wrong(BlockPos pos, @org.jetbrains.annotations.Nullable BlockState ghost) {
        public @org.jetbrains.annotations.Nullable Block expected() { return ghost == null ? null : ghost.getBlock(); }
    }
    /** Up to limit wrong cells, in validate's scan order, by the same table. Port-eligible cells accept any port or the hatch;
     * while a part is still missing from the whole ring, its suggested cell asks for it instead of shell. */
    public static List<Wrong> wrongCells(Level level, BlockPos controller, Direction facing, int limit) {
        var center = center(controller, facing); var wrong = new ArrayList<Wrong>();
        var missing = missingParts(level, center);
        for (int dx = -RADIUS; dx <= RADIUS && wrong.size() < limit; dx++) for (int dy = -2; dy <= 2 && wrong.size() < limit; dy++)
            for (int dz = -RADIUS; dz <= RADIUS && wrong.size() < limit; dz++) {
                var pos = center.offset(dx, dy, dz);
                if (pos.equals(controller)) continue;
                var block = level.getBlockState(pos).getBlock();
                if (channel(dx, dy, dz)) { if (!level.getBlockState(pos).isAir()) wrong.add(new Wrong(pos, null)); continue; }
                var expected = expected(dx, dy, dz);
                var ghost = expected == null ? null : expected.defaultBlockState();
                if (expected == null && shell(dx, dy, dz) != null) {
                    if (port(block)) continue;
                    var suggested = suggestion(dx, dy, dz, facing);
                    ghost = suggested != null && missing.contains(suggested.getBlock()) ? suggested : shell(dx, dy, dz).defaultBlockState();
                }
                if (ghost != null && block != ghost.getBlock()) wrong.add(new Wrong(pos, ghost));
            }
        return wrong;
    }

    private static boolean port(Block block) {
        return block instanceof CoolantPortBlock || block instanceof SfrFuelPortBlock || block instanceof SfrPowerPortBlock
                || block instanceof SfrVacuumPortBlock || block instanceof SfrHatchBlock;
    }
    /** Port and hatch blocks the outer ring is still short of, Coolant Port until it holds two. */
    private static Set<Block> missingParts(Level level, BlockPos center) {
        int coolant = 0; var present = new HashSet<Block>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            if (shell(dx, dy, dz) == null) continue;
            var block = level.getBlockState(center.offset(dx, dy, dz)).getBlock();
            if (block instanceof CoolantPortBlock) coolant++; else if (port(block)) present.add(block);
        }
        var missing = new HashSet<Block>();
        for (var b : List.of(ModBlocks.SFR_FUEL_PORT.get(), ModBlocks.SFR_POWER_PORT.get(), ModBlocks.SFR_VACUUM_PORT.get(), ModBlocks.SFR_MAINTENANCE_HATCH.get()))
            if (!present.contains(b)) missing.add(b);
        if (coolant < 2) missing.add(ModBlocks.COOLANT_PORT.get());
        return missing;
    }
    /** The ghost the blueprint suggests for a port-eligible cell, or null. Suggestions sit on the Controller's face around
     * it: hatch two cells to one side, a Coolant Port at each end, Fuel over Power on the other side, Vacuum Port above.
     * Any non-corner outer-ring cell works, so these are a layout, not a rule. */
    static @org.jetbrains.annotations.Nullable BlockState suggestion(int dx, int dy, int dz, Direction facing) {
        var across = facing.getClockWise();
        if (dx * facing.getStepX() + dz * facing.getStepZ() != RADIUS) return null;
        int a = dx * across.getStepX() + dz * across.getStepZ();
        if (Math.abs(a) == 3 && dy == 0) return ModBlocks.COOLANT_PORT.get().defaultBlockState();
        if (a == -2 && dy <= 0) return ModBlocks.SFR_MAINTENANCE_HATCH.get().defaultBlockState().setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, dy == 0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        if (a == 2 && dy == 0) return ModBlocks.SFR_FUEL_PORT.get().defaultBlockState();
        if (a == 2 && dy == -1) return ModBlocks.SFR_POWER_PORT.get().defaultBlockState();
        if (a == 0 && dy == 1) return ModBlocks.SFR_VACUUM_PORT.get().defaultBlockState().setValue(SfrVacuumPortBlock.FACING, facing);
        return null;
    }

    private static Problem problem(Block expected) {
        if (expected == ModBlocks.FIRST_WALL.get()) return Problem.FIRST_WALL;
        if (expected == ModBlocks.LITHIUM_BLANKET.get()) return Problem.BLANKET;
        if (expected == ModBlocks.CONFINEMENT_CORE.get()) return Problem.CORE;
        return Problem.COIL;
    }
}
