package com.frozendawn.entity;

import com.frozendawn.maeve.MaeveDirector;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded local fallback to an inspection vantage, never a source of belief evidence. */
final class ArchitectAccessInspection {
    private final ArchitectEntity actor;
    private final MaeveDirector.PositionDirective directive;
    private final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
    private final Map<BlockPos, BlockPos> parents = new HashMap<>();
    private final ArrayDeque<BlockPos> route = new ArrayDeque<>();
    private boolean complete;

    ArchitectAccessInspection(ArchitectEntity actor, MaeveDirector.PositionDirective directive) {
        this.actor = actor; this.directive = directive;
        BlockPos start = actor.blockPosition();
        parents.put(start, start); frontier.add(start);
    }

    boolean compute() {
        for (int i = 0; i < 80 && !complete; i++) {
            BlockPos pos = frontier.poll();
            if (pos == null) { complete = true; break; }
            if (pos.distSqr(directive.position()) <= 16 && seesCrossing(pos)) {
                for (BlockPos p = pos; !parents.get(p).equals(p); p = parents.get(p)) route.addFirst(p);
                complete = true; break;
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                if (parents.size() < 2048 && next.distSqr(directive.position()) <= 24 * 24
                        && !parents.containsKey(next) && walkable(next)) {
                    parents.put(next, pos); frontier.add(next);
                }
            }
        }
        return complete;
    }

    BlockPos next() {
        while (!route.isEmpty() && actor.position().distanceToSqr(Vec3.atBottomCenterOf(route.peek())) <= .09) route.remove();
        return route.isEmpty() || !walkable(route.peek()) ? null : route.peek();
    }

    private boolean walkable(BlockPos pos) {
        var level = actor.level();
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        var ground = level.getBlockState(pos.below());
        if (ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS)) return false;
        for (int y = 0; y < 3; y++) {
            var p = pos.above(y); var state = level.getBlockState(p);
            if (!state.getCollisionShape(level, p).isEmpty() || !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE)) return false;
        }
        return true;
    }

    private boolean seesCrossing(BlockPos pos) {
        Vec3 eye = Vec3.atBottomCenterOf(pos).add(0, actor.getEyeHeight(), 0);
        Vec3 inside = Vec3.atBottomCenterOf(directive.spatial().inside());
        Vec3 outside = Vec3.atBottomCenterOf(directive.spatial().outside());
        int steps = Math.min(8, Math.max(1, (int) Math.ceil(inside.distanceTo(outside) * 2)));
        for (int i = 0; i <= steps; i++) for (int y = 0; y < 2; y++) {
            BlockPos block = BlockPos.containing(inside.lerp(outside, (double) i / steps)).above(y);
            boolean loaded = true;
            for (int x = Math.min(pos.getX(), block.getX()) >> 4; x <= Math.max(pos.getX(), block.getX()) >> 4; x++) {
                for (int z = Math.min(pos.getZ(), block.getZ()) >> 4; z <= Math.max(pos.getZ(), block.getZ()) >> 4; z++) {
                    if (!actor.level().hasChunk(x, z)) loaded = false;
                }
            }
            if (!loaded) continue;
            var hit = actor.level().clip(new ClipContext(eye, block.getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(block)) return true;
        }
        return false;
    }
}
