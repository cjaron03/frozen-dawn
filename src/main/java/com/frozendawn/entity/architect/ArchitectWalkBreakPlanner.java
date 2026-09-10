package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure walk-break planning helpers kept out of ArchitectEntity so movement
 * orchestration and break-target selection stay separated.
 */
public final class ArchitectWalkBreakPlanner {

    private ArchitectWalkBreakPlanner() {
    }

    public static Set<BlockPos> collectUnstickBreakCandidates(
            BlockPos from,
            BlockPos stepPos,
            @Nullable Direction toward
    ) {
        boolean steppingUp = stepPos.getY() > from.getY();
        boolean steppingDown = stepPos.getY() < from.getY();

        Set<BlockPos> candidates = new LinkedHashSet<>(10);
        candidates.add(from.above());
        if (toward != null) {
            BlockPos front = from.relative(toward);
            // During a step-up, front is the ledge supporting the destination.
            // Mining it converts a valid jump into a trench and repeats forever.
            if (!steppingUp) {
                candidates.add(front);
            }
            candidates.add(front.above());
            if (steppingDown) {
                candidates.add(front.above().above());
            }
        }
        if (Math.abs(stepPos.getX() - from.getX()) <= 1
                && Math.abs(stepPos.getY() - from.getY()) <= 1
                && Math.abs(stepPos.getZ() - from.getZ()) <= 1) {
            candidates.add(stepPos);
            candidates.add(stepPos.above());
        }

        // Nothing below stepPos is ever a candidate: that column is the surface the
        // destination stands on, so it is solid on any walkable path and would always
        // win selection once the real obstructions turn out to be air. Mining it drops
        // the Architect into a pit, which re-triggers the stuck check one block lower
        // and digs a trench. A slab or snow layer at the destination is stepPos itself,
        // which is already covered above.
        return candidates;
    }

    @Nullable
    public static BlockPos selectPreferredBreakCandidate(
            Iterable<BlockPos> candidates,
            @Nullable BlockPos blockedCandidate,
            Predicate<BlockPos> isBreakable,
            Predicate<BlockPos> isLastResortBreakBlock
    ) {
        BlockPos fallback = null;
        for (BlockPos candidate : candidates) {
            if (isBlockedUnstickCandidate(candidate, blockedCandidate)) {
                continue;
            }
            if (!isBreakable.test(candidate)) {
                continue;
            }
            if (!isLastResortBreakBlock.test(candidate)) {
                return candidate.immutable();
            }
            if (fallback == null) {
                fallback = candidate.immutable();
            }
        }
        return fallback;
    }

    @Nullable
    public static BlockPos findCorridorBreakTarget(
            List<BlockPos> corridorNodes,
            Predicate<BlockPos> isBreakable,
            Predicate<BlockPos> isLastResortBreakBlock
    ) {
        BlockPos fallback = null;
        for (BlockPos node : corridorNodes) {
            if (isBreakable.test(node)) {
                if (!isLastResortBreakBlock.test(node)) {
                    return node.immutable();
                }
                if (fallback == null) {
                    fallback = node.immutable();
                }
            }

            BlockPos headroom = node.above();
            if (isBreakable.test(headroom)) {
                if (!isLastResortBreakBlock.test(headroom)) {
                    return headroom.immutable();
                }
                if (fallback == null) {
                    fallback = headroom.immutable();
                }
            }
        }
        return fallback;
    }

    public static boolean isBlockedUnstickCandidate(BlockPos candidate, @Nullable BlockPos blockedCandidate) {
        if (blockedCandidate == null) {
            return false;
        }
        if (candidate.equals(blockedCandidate)) {
            return true;
        }
        return candidate.getX() == blockedCandidate.getX()
                && candidate.getZ() == blockedCandidate.getZ()
                && Math.abs(candidate.getY() - blockedCandidate.getY()) <= 1;
    }
}
