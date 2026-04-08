package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;

/**
 * Committed-walk progress and waypoint reach math isolated from ArchitectEntity
 * to keep path-following rules cohesive and testable.
 */
public final class ArchitectWalkProgress {

    private ArchitectWalkProgress() {
    }

    public static boolean advanceCommittedWalkProgress(
            ArchitectApproachState approachState,
            BlockPos currentBlockPos,
            double currentX,
            double currentY,
            double currentZ,
            double waypointHorizontalReachSqr,
            double waypointUpwardVerticalReach,
            double waypointDownwardVerticalReach
    ) {
        BlockPos steeringTarget = ArchitectWalkCorridorState.getSteeringTarget(approachState);
        while (steeringTarget != null) {
            int furthestReachedIndex = getFurthestReachedCommittedWalkIndex(
                    approachState,
                    currentBlockPos,
                    currentX,
                    currentY,
                    currentZ,
                    waypointHorizontalReachSqr,
                    waypointUpwardVerticalReach,
                    waypointDownwardVerticalReach);
            if (furthestReachedIndex > approachState.committedWalkCorridorIndex) {
                approachState.committedWalkCorridorIndex = furthestReachedIndex;
                approachState.committedWalkNoProgressTicks = 0;
                approachState.committedWalkLastDistSqr = Double.MAX_VALUE;
                steeringTarget = ArchitectWalkCorridorState.getSteeringTarget(approachState);
                continue;
            }

            double distSqr = distanceToWaypointSqr(currentX, currentY, currentZ, steeringTarget);
            if (!hasReachedWalkWaypoint(
                    currentBlockPos,
                    currentX,
                    currentY,
                    currentZ,
                    steeringTarget,
                    distSqr,
                    waypointHorizontalReachSqr,
                    waypointUpwardVerticalReach,
                    waypointDownwardVerticalReach)) {
                return true;
            }

            if (approachState.committedWalkCorridorIndex < approachState.committedWalkCorridor.size() - 1) {
                approachState.committedWalkCorridorIndex++;
                approachState.committedWalkNoProgressTicks = 0;
                approachState.committedWalkLastDistSqr = Double.MAX_VALUE;
                steeringTarget = ArchitectWalkCorridorState.getSteeringTarget(approachState);
                continue;
            }

            if (approachState.committedWalkWaypoint != null) {
                approachState.lastCompletedWalkWaypointPos = approachState.committedWalkWaypoint.immutable();
            }
            approachState.lastCompletedWalkBacktrackPos = approachState.committedWalkBacktrackPos != null
                    ? approachState.committedWalkBacktrackPos.immutable()
                    : approachState.committedWalkStartPos != null ? approachState.committedWalkStartPos.immutable() : null;
            ArchitectWalkCorridorState.clear(approachState);
            return false;
        }
        return false;
    }

    public static double distanceToWaypointSqr(double currentX, double currentY, double currentZ, BlockPos waypoint) {
        double dx = currentX - (waypoint.getX() + 0.5);
        double dy = currentY - waypoint.getY();
        double dz = currentZ - (waypoint.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    public static double horizontalDistanceSqr(BlockPos from, BlockPos to) {
        double dx = from.getX() - to.getX();
        double dz = from.getZ() - to.getZ();
        return dx * dx + dz * dz;
    }

    private static int getFurthestReachedCommittedWalkIndex(
            ArchitectApproachState approachState,
            BlockPos currentBlockPos,
            double currentX,
            double currentY,
            double currentZ,
            double waypointHorizontalReachSqr,
            double waypointUpwardVerticalReach,
            double waypointDownwardVerticalReach
    ) {
        if (approachState.committedWalkCorridor.isEmpty()) {
            return approachState.committedWalkCorridorIndex;
        }

        int startIndex = Math.max(0, Math.min(
                approachState.committedWalkCorridorIndex,
                approachState.committedWalkCorridor.size() - 1));
        int furthest = startIndex;
        for (int i = startIndex; i < approachState.committedWalkCorridor.size(); i++) {
            BlockPos candidate = approachState.committedWalkCorridor.get(i);
            double distSqr = distanceToWaypointSqr(currentX, currentY, currentZ, candidate);
            if (!hasReachedWalkWaypoint(
                    currentBlockPos,
                    currentX,
                    currentY,
                    currentZ,
                    candidate,
                    distSqr,
                    waypointHorizontalReachSqr,
                    waypointUpwardVerticalReach,
                    waypointDownwardVerticalReach)) {
                break;
            }
            furthest = i;
        }
        return furthest;
    }

    private static boolean hasReachedWalkWaypoint(
            BlockPos currentBlockPos,
            double currentX,
            double currentY,
            double currentZ,
            BlockPos waypoint,
            double distSqr,
            double waypointHorizontalReachSqr,
            double waypointUpwardVerticalReach,
            double waypointDownwardVerticalReach
    ) {
        if (currentBlockPos.equals(waypoint)) {
            return true;
        }
        if (!isWithinWaypointVerticalReach(currentY, waypoint, waypointUpwardVerticalReach, waypointDownwardVerticalReach)) {
            return false;
        }
        if (distSqr <= waypointHorizontalReachSqr) {
            return true;
        }

        double dx = currentX - (waypoint.getX() + 0.5);
        double dz = currentZ - (waypoint.getZ() + 0.5);
        double horizontalDistSqr = dx * dx + dz * dz;
        if (horizontalDistSqr > waypointHorizontalReachSqr) {
            return false;
        }
        return isWithinWaypointVerticalReach(currentY, waypoint, waypointUpwardVerticalReach, waypointDownwardVerticalReach);
    }

    private static boolean isWithinWaypointVerticalReach(
            double currentY,
            BlockPos waypoint,
            double waypointUpwardVerticalReach,
            double waypointDownwardVerticalReach
    ) {
        double verticalDelta = waypoint.getY() - currentY;
        if (verticalDelta > 0.0) {
            return verticalDelta <= waypointUpwardVerticalReach;
        }
        return -verticalDelta <= waypointDownwardVerticalReach;
    }
}
