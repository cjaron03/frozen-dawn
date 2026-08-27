package com.frozendawn.entity.ai;

import com.frozendawn.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared block-break policy for Architect pathing and mining.
 *
 * Keeps "never break" and "last resort break" rules centralized so A*, D*,
 * and runtime breaker behavior stay aligned.
 */
public final class ArchitectBreakPolicy {

    // Large but finite: crystal stays breakable, but route planners strongly
    // prefer non-crystal alternatives when they exist.
    private static final float CRYSTAL_LAST_RESORT_PENALTY = 35.0F;
    // Treat half-height-and-up collisions as path-blocking obstructions that
    // should be eligible for unstick mining (slabs, tall snow layers, etc.).
    private static final double MIN_OBSTRUCTION_HEIGHT = 0.5D;

    private ArchitectBreakPolicy() {
    }

    public static boolean isProtectedBlock(BlockState state) {
        return state.is(ModBlocks.ACHERONITE_BLOCK.get())
                || state.is(ModBlocks.TRANSPONDER.get());
    }

    public static boolean isLastResortBreakBlock(BlockState state) {
        return state.is(ModBlocks.ACHERONITE_CRYSTAL.get());
    }

    public static float applyLastResortPenalty(BlockState state, float baseCost) {
        if (isLastResortBreakBlock(state)) {
            return baseCost + CRYSTAL_LAST_RESORT_PENALTY;
        }
        return baseCost;
    }

    public static boolean isObstructiveForArchitect(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isSolid()) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        return shape.max(Direction.Axis.Y) >= MIN_OBSTRUCTION_HEIGHT;
    }

    /**
     * Space the Architect can physically occupy without mining. Thin deposits
     * remain perceptible terrain, but they are not promoted to breach targets.
     */
    public static boolean isDryPassableForArchitect(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getFluidState().isEmpty()
                && !isObstructiveForArchitect(state, level, pos);
    }

    public static boolean isThinTraversableSurface(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        double height = shape.max(Direction.Axis.Y);
        return height > 0.0D && height < MIN_OBSTRUCTION_HEIGHT;
    }

    public static double traversableSurfaceOffset(BlockState state, BlockGetter level, BlockPos pos) {
        if (!isThinTraversableSurface(state, level, pos)) {
            return 0.0D;
        }
        return state.getCollisionShape(level, pos).max(Direction.Axis.Y);
    }
}
