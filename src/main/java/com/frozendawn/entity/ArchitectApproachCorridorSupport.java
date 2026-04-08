package com.frozendawn.entity;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectWalkBreakPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Owns walk corridor construction and corridor-level analysis used by approach movement.
 */
final class ArchitectApproachCorridorSupport {

    private static final int WALK_NAV_CORRIDOR_MAX_STEPS = 8;
    private static final double WALK_NAV_MAX_DISTANCE = 10.0;

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;

    ArchitectApproachCorridorSupport(
            ArchitectEntity architect,
            ArchitectApproachState approachState
    ) {
        this.architect = architect;
        this.approachState = approachState;
    }

    List<BlockPos> buildWalkCorridorNodes(BlockPos startPos, DStarLitePathfinder.NextStep firstStep) {
        return buildWalkCorridorNodes(startPos, firstStep, true);
    }

    List<BlockPos> previewWalkCorridorNodes(BlockPos startPos, DStarLitePathfinder.NextStep firstStep) {
        return buildWalkCorridorNodes(startPos, firstStep, false);
    }

    boolean shouldContinueWalkObstructionBreak(
            DStarLitePathfinder.NextStep step,
            BlockPos breakTarget,
            Predicate<BlockPos> isBreakableBlock,
            Predicate<BlockPos> isLastResortBreakBlock
    ) {
        if (isBreakableBlock.test(breakTarget)) {
            BlockPos from = architect.blockPosition();
            Direction toward = getPrimaryHorizontalDirection(from, step.pos());
            Set<BlockPos> immediateCandidates =
                    ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, step.pos(), toward);
            if (immediateCandidates.contains(breakTarget)) {
                return true;
            }
        }

        List<BlockPos> corridorNodes = previewWalkCorridorNodes(architect.blockPosition(), step);
        if (corridorNodes.isEmpty()) {
            corridorNodes = List.of(step.pos().immutable());
        }
        BlockPos corridorCandidate = ArchitectWalkBreakPlanner.findCorridorBreakTarget(
                corridorNodes,
                isBreakableBlock,
                isLastResortBreakBlock);
        return corridorCandidate != null && breakTarget.equals(corridorCandidate);
    }

    boolean isReverseOnlyWalkCorridor(
            BlockPos startPos,
            DStarLitePathfinder.NextStep firstStep,
            List<BlockPos> corridorNodes
    ) {
        if (firstStep.type() != DStarLitePathfinder.StepType.WALK || corridorNodes.size() != 1) {
            return false;
        }

        BlockPos firstNode = corridorNodes.get(0);
        DStarLitePathfinder.NextStep continuation =
                approachState.dstar.peekNextStep(firstNode, architect.level(), startPos);
        return continuation.type() == DStarLitePathfinder.StepType.WALK
                && continuation.pos().equals(startPos);
    }

    @Nullable
    Direction getPrimaryHorizontalDirection(BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (dz != 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return null;
    }

    private List<BlockPos> buildWalkCorridorNodes(
            BlockPos startPos,
            DStarLitePathfinder.NextStep firstStep,
            boolean updatePendingWalkBacktrack
    ) {
        List<BlockPos> corridor = new ArrayList<>(WALK_NAV_CORRIDOR_MAX_STEPS);
        if (firstStep.type() != DStarLitePathfinder.StepType.WALK) {
            if (updatePendingWalkBacktrack) {
                approachState.pendingWalkBacktrackPos = startPos.immutable();
            }
            corridor.add(firstStep.pos().immutable());
            return corridor;
        }

        BlockPos waypoint = firstStep.pos().immutable();
        corridor.add(waypoint);
        BlockPos current = waypoint;
        BlockPos previous = startPos;
        if (updatePendingWalkBacktrack) {
            approachState.pendingWalkBacktrackPos = startPos.immutable();
        }
        Set<Long> visited = new HashSet<>();
        visited.add(startPos.asLong());
        visited.add(waypoint.asLong());
        double maxDistSqr = WALK_NAV_MAX_DISTANCE * WALK_NAV_MAX_DISTANCE;

        for (int steps = 1; steps < WALK_NAV_CORRIDOR_MAX_STEPS; steps++) {
            double dx = waypoint.getX() - startPos.getX();
            double dy = waypoint.getY() - startPos.getY();
            double dz = waypoint.getZ() - startPos.getZ();
            if (dx * dx + dy * dy + dz * dz >= maxDistSqr) {
                break;
            }

            DStarLitePathfinder.NextStep next = approachState.dstar.peekNextStep(current, architect.level(), previous);
            if (next.type() != DStarLitePathfinder.StepType.WALK) {
                break;
            }

            BlockPos nextPos = next.pos();
            if (nextPos.equals(current) || visited.contains(nextPos.asLong())) {
                break;
            }

            double nextDx = nextPos.getX() - startPos.getX();
            double nextDy = nextPos.getY() - startPos.getY();
            double nextDz = nextPos.getZ() - startPos.getZ();
            if (nextDx * nextDx + nextDy * nextDy + nextDz * nextDz > maxDistSqr) {
                break;
            }

            previous = current;
            current = nextPos.immutable();
            waypoint = current;
            if (updatePendingWalkBacktrack) {
                approachState.pendingWalkBacktrackPos = previous.immutable();
            }
            corridor.add(current);
            visited.add(current.asLong());
        }

        return corridor;
    }
}
