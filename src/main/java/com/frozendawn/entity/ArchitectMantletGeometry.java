package com.frozendawn.entity;

import com.frozendawn.aggregate.StillpointPolicy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Fixed ten-block corridor. All probes are local, loaded, and capped; no navigation search. */
final class ArchitectMantletGeometry {
    static final int SCREENS = 5;
    static final int PLACEMENTS = SCREENS * 4;
    record Screen(List<BlockPos> cells, Vec3 stand) { }
    record Plan(List<Screen> screens) { }
    private ArchitectMantletGeometry() { }

    static Plan plan(ArchitectEntity actor, Vec3 anchor) {
        Vec3 delta = anchor.subtract(actor.position());
        if (delta.horizontalDistanceSqr() < 100 || actor.getMantletIceCount() != 0)
            return reject(actor, "rangeOrBudget distance=" + delta.horizontalDistance() + " used=" + actor.getMantletIceCount());
        Direction forward = Direction.getNearest(delta.x, 0, delta.z);
        Direction right = forward.getClockWise();
        BlockPos origin = actor.blockPosition();
        var screens = new ArrayList<Screen>();
        Vec3 previous = actor.position();
        for (int step = 0; step < SCREENS; step++) {
            Vec3 center = Vec3.atBottomCenterOf(origin.relative(forward, step * 2))
                    .add(right.getStepX() * .5, 0, right.getStepZ() * .5);
            Vec3 stand = surface(actor, center, actor.getY());
            if (stand == null || !walk(actor, previous, stand)) return reject(actor, "walk step=" + step + " from=" + previous + " to=" + stand);
            var cells = new ArrayList<BlockPos>();
            for (int column = 0; column < 2; column++) {
                BlockPos projected = origin.relative(forward, 2 + step * 2).relative(right, column);
                BlockPos base = base(actor, projected, stand.y);
                if (base == null) return reject(actor, "wall step=" + step + " column=" + column);
                cells.add(base); cells.add(base.above());
            }
            if (cells.stream().noneMatch(p -> new AABB(p).clip(anchor.add(0, 1.62, 0), stand.add(0, .9, 0)).isPresent())) return reject(actor, "lane step=" + step);
            screens.add(new Screen(List.copyOf(cells), stand));
            previous = stand;
        }
        return new Plan(List.copyOf(screens));
    }

    private static Plan reject(ArchitectEntity actor, String reason) {
        actor.recordDecision("MANTLET_PLAN_UNAVAILABLE", null, reason); return null;
    }

    private static BlockPos base(ArchitectEntity actor, BlockPos projected, double standingY) {
        for (int dy : new int[]{0, -1, 1}) {
            BlockPos p = projected.offset(0, dy, 0);
            if (Math.abs(p.getY() - standingY) > 1 || !actor.level().hasChunkAt(p)) continue;
            if (!actor.level().getBlockState(p.below()).isFaceSturdy(actor.level(), p.below(), Direction.UP)) continue;
            if (placeable(actor, p) && placeable(actor, p.above())) return p;
        }
        return null;
    }

    static boolean placeable(ArchitectEntity actor, BlockPos p) {
        if (!(actor.level() instanceof net.minecraft.server.level.ServerLevel level) || !level.isInWorldBounds(p)
                || !level.getWorldBorder().isWithinBounds(p) || !level.hasChunkAt(p) || StillpointPolicy.isSuppressed(level, p)) return false;
        var state = actor.level().getBlockState(p);
        if ((!state.isAir() && !state.is(Blocks.SNOW)) || !state.getFluidState().isEmpty()) return false;
        if (new AABB(p).intersects(actor.getBoundingBox().inflate(.15))) return false;
        var occupants = new ArrayList<net.minecraft.world.entity.Entity>();
        ((net.minecraft.server.level.ServerLevel) actor.level()).getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                new AABB(p), e -> !e.isRemoved() && !e.isSpectator() && e.blocksBuilding, occupants, 1);
        return occupants.isEmpty();
    }

    /** A full block, dirt path, or snow collision surface; never assumes integer feet Y. */
    static Vec3 surface(ArchitectEntity actor, Vec3 point, double nearY) {
        var level = actor.level();
        int top = (int) Math.floor(nearY) + 1;
        for (int y = top; y >= top - 3; y--) {
            BlockPos p = BlockPos.containing(point.x, y, point.z);
            if (!level.hasChunkAt(p)) return null;
            var state = level.getBlockState(p);
            if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)) return null;
            var shape = state.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;
            double feetY = y + shape.max(Direction.Axis.Y);
            if (Math.abs(feetY - nearY) > .6) continue;
            return new Vec3(point.x, feetY, point.z);
        }
        return null;
    }

    static boolean walk(ArchitectEntity actor, Vec3 from, Vec3 to) {
        double length = from.distanceTo(to);
        if (length > 3 || Math.abs(from.y - to.y) > .6) return false;
        int samples = Math.max(1, (int) Math.ceil(length * 4));
        double height = from.y;
        for (int i = 0; i <= samples; i++) {
            Vec3 p = surface(actor, from.lerp(to, i / (double) samples), height);
            if (p == null || Math.abs(p.y - height) > .6) return false;
            height = p.y;
            AABB body = actor.getBoundingBox().move(p.subtract(actor.position())).deflate(.01);
            for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY, body.minZ),
                    BlockPos.containing(body.maxX, body.maxY, body.maxZ))) if (!actor.level().hasChunkAt(cell)) return false;
            if (!actor.level().noCollision(actor, body)) return false;
        }
        return Math.abs(height - to.y) <= .15;
    }
}
