package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

/**
 * Holds committed-walk state transitions and steering math so ArchitectEntity
 * can focus on high-level behavior flow.
 */
public final class ArchitectWalkCorridorState {

    private ArchitectWalkCorridorState() {
    }

    public static void commit(
            ArchitectApproachState approachState,
            List<BlockPos> corridorNodes,
            BlockPos currentPos,
            Vec3 currentPosVec,
            @Nullable BlockPos targetPos,
            int commitTicks,
            ToDoubleFunction<BlockPos> distanceToWaypointSqr
    ) {
        if (corridorNodes.isEmpty()) {
            return;
        }

        approachState.committedWalkCorridor.clear();
        for (BlockPos node : corridorNodes) {
            approachState.committedWalkCorridor.add(node.immutable());
        }
        approachState.committedWalkCorridorIndex = 0;
        approachState.committedWalkFirstStepPos = approachState.committedWalkCorridor.get(0);
        approachState.committedWalkWaypoint =
                approachState.committedWalkCorridor.get(approachState.committedWalkCorridor.size() - 1);
        approachState.committedWalkStartPos = currentPos.immutable();
        approachState.committedWalkBacktrackPos = approachState.pendingWalkBacktrackPos != null
                ? approachState.pendingWalkBacktrackPos.immutable()
                : approachState.committedWalkStartPos;
        approachState.committedWalkStartVec = currentPosVec;
        approachState.committedWalkTargetSnapshot = targetPos != null ? targetPos.immutable() : null;
        approachState.committedWalkTicks = commitTicks;
        approachState.committedWalkAgeTicks = 0;
        approachState.committedWalkNoProgressTicks = 0;
        BlockPos steeringTarget = getSteeringTarget(approachState);
        approachState.committedWalkLastDistSqr = steeringTarget != null
                ? distanceToWaypointSqr.applyAsDouble(steeringTarget)
                : Double.MAX_VALUE;
        approachState.pendingWalkBacktrackPos = null;
    }

    public static void clear(ArchitectApproachState approachState) {
        approachState.committedWalkWaypoint = null;
        approachState.committedWalkFirstStepPos = null;
        approachState.committedWalkStartPos = null;
        approachState.committedWalkBacktrackPos = null;
        approachState.committedWalkTargetSnapshot = null;
        approachState.committedWalkStartVec = null;
        approachState.committedWalkCorridor.clear();
        approachState.committedWalkCorridorIndex = 0;
        approachState.committedWalkTicks = 0;
        approachState.committedWalkAgeTicks = 0;
        approachState.committedWalkNoProgressTicks = 0;
        approachState.committedWalkLastDistSqr = Double.MAX_VALUE;
        approachState.pendingWalkBacktrackPos = null;
    }

    @Nullable
    public static BlockPos getSteeringTarget(ArchitectApproachState approachState) {
        if (approachState.committedWalkCorridor.isEmpty()) {
            return approachState.committedWalkWaypoint;
        }
        if (approachState.committedWalkCorridorIndex < 0) {
            approachState.committedWalkCorridorIndex = 0;
        }
        if (approachState.committedWalkCorridorIndex >= approachState.committedWalkCorridor.size()) {
            approachState.committedWalkCorridorIndex = approachState.committedWalkCorridor.size() - 1;
        }
        return approachState.committedWalkCorridor.get(approachState.committedWalkCorridorIndex);
    }

    public static Vec3 getMoveTarget(
            ArchitectApproachState approachState,
            BlockPos steeringTarget,
            int lookAheadSteps,
            BiFunction<BlockPos, BlockPos, Direction> primaryHorizontalDirection
    ) {
        BlockPos moveTarget = steeringTarget;
        if (!approachState.committedWalkCorridor.isEmpty()) {
            int corridorIndex = Math.max(0, Math.min(
                    approachState.committedWalkCorridorIndex,
                    approachState.committedWalkCorridor.size() - 1));
            BlockPos cursor = steeringTarget;
            Direction runDirection = null;
            int lookahead = 0;

            for (int i = corridorIndex + 1;
                 i < approachState.committedWalkCorridor.size() && lookahead < lookAheadSteps;
                 i++) {
                BlockPos candidate = approachState.committedWalkCorridor.get(i);
                if (candidate.getY() != cursor.getY()) {
                    break;
                }

                Direction segmentDirection = primaryHorizontalDirection.apply(cursor, candidate);
                if (segmentDirection == null) {
                    break;
                }
                if (runDirection == null) {
                    runDirection = segmentDirection;
                } else if (segmentDirection != runDirection) {
                    break;
                }

                moveTarget = candidate;
                cursor = candidate;
                lookahead++;
            }
        }

        return new Vec3(
                moveTarget.getX() + 0.5,
                steeringTarget.getY(),
                moveTarget.getZ() + 0.5);
    }

    public static Vec3 getLookTarget(
            Vec3 moveTarget,
            double currentX,
            double currentEyeY,
            double currentY,
            double currentZ,
            double maxForwardReach,
            double minVerticalDelta,
            double maxVerticalDelta
    ) {
        Vec3 horizontalDelta = new Vec3(moveTarget.x - currentX, 0.0, moveTarget.z - currentZ);
        if (horizontalDelta.lengthSqr() < 1.0E-4) {
            return new Vec3(currentX, currentEyeY, currentZ);
        }

        Vec3 forward = horizontalDelta.normalize().scale(Math.min(maxForwardReach, horizontalDelta.length()));
        double lookY = currentEyeY + Mth.clamp(moveTarget.y - currentY, minVerticalDelta, maxVerticalDelta);
        return new Vec3(currentX + forward.x, lookY, currentZ + forward.z);
    }

    @Nullable
    public static BlockPos getImmediateBacktrackPos(ArchitectApproachState approachState, BlockPos currentPos) {
        if (approachState.lastCompletedWalkWaypointPos != null
                && approachState.lastCompletedWalkBacktrackPos != null
                && currentPos.equals(approachState.lastCompletedWalkWaypointPos)) {
            return approachState.lastCompletedWalkBacktrackPos;
        }
        if (approachState.currentWalkCellPos == null || approachState.previousWalkCellPos == null) {
            return null;
        }
        if (!currentPos.equals(approachState.currentWalkCellPos)) {
            return null;
        }
        return approachState.previousWalkCellPos;
    }
}
