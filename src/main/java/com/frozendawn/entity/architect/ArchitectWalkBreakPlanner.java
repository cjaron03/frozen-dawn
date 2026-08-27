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

        // Slab/snow transitions can alternate between the same X/Z at Y and Y+1.
        // Include lower cells for flat/descending moves, but avoid undercutting
        // support while trying to climb upward.
        if (!steppingUp) {
            candidates.add(stepPos.below());
            if (Math.abs(stepPos.getY() - from.getY()) >= 2) {
                candidates.add(stepPos.below(2));
            }
        }
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
