package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Collision-aware standing positions for walking on partial blocks. */
public final class ArchitectWalkGeometry {
    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.95;
    private static final double EPSILON = 1.0e-6;

    private ArchitectWalkGeometry() { }

    /** A partial surface must support the entire centered footprint, not just a fence post. */
    public static double partialSurfaceOffset(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return 0;
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return 0;
        double top = shape.max(Direction.Axis.Y);
        if (top <= 0 || top >= 1) return 0;
        for (AABB box : shape.toAabbs()) {
            if (Math.abs(box.maxY - top) < EPSILON && box.minX <= 0.5 - HALF_WIDTH
                    && box.maxX >= 0.5 + HALF_WIDTH && box.minZ <= 0.5 - HALF_WIDTH
                    && box.maxZ >= 0.5 + HALF_WIDTH) return top;
        }
        return 0;
    }

    private static double standingY(Level level, BlockPos pos) {
        double offset = partialSurfaceOffset(level, pos);
        if (offset > 0) return pos.getY() + offset;
        var below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP)) return Double.NaN;
        return pos.getY();
    }

    public static boolean canStandOnPartialSurface(Level level, BlockPos pos) {
        double offset = partialSurfaceOffset(level, pos);
        return offset > 0 && clear(level, body(pos, pos.getY() + offset));
    }

    /** Conservative step sweep: rise at departure, traverse above both supports, then land. */
    public static boolean canWalkPartialTransition(Level level, BlockPos from, BlockPos to) {
        if (Math.abs(from.getX() - to.getX()) + Math.abs(from.getZ() - to.getZ()) != 1
                || Math.abs(from.getY() - to.getY()) > 1) return false;
        if (partialSurfaceOffset(level, from) == 0 && partialSurfaceOffset(level, to) == 0) return false;
        double fromY = standingY(level, from), toY = standingY(level, to);
        if (!Double.isFinite(fromY) || !Double.isFinite(toY) || Math.abs(toY - fromY) > 0.6) return false;
        double top = Math.max(fromY, toY);
        return clear(level, body(from, fromY).minmax(body(from, top)))
                && clear(level, body(from, top).minmax(body(to, top)))
                && clear(level, body(to, top).minmax(body(to, toY)));
    }

    private static AABB body(BlockPos pos, double y) {
        return new AABB(pos.getX() + 0.5 - HALF_WIDTH, y + EPSILON, pos.getZ() + 0.5 - HALF_WIDTH,
                pos.getX() + 0.5 + HALF_WIDTH, y + HEIGHT, pos.getZ() + 0.5 + HALF_WIDTH);
    }

    private static boolean clear(Level level, AABB body) {
        for (var collision : level.getBlockCollisions(null, body)) {
            if (!collision.isEmpty()) return false;
        }
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) return false;
        }
        return true;
    }
}
