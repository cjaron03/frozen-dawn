package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Owns walk tracking/stuck tracking state mutations for approach movement.
 */
public final class ArchitectWalkTracking {

    private ArchitectWalkTracking() {
    }

    public static void trackWalkStep(
            ArchitectApproachState approachState,
            BlockPos from,
            BlockPos stepPos,
            Vec3 deltaMovement,
            boolean onGround
    ) {
        boolean repeated = stepPos.equals(approachState.lastWalkStepPos)
                && from.equals(approachState.lastWalkFromPos);
        // Detect A<->B ping-pong as stuck too, not just exact same edge.
        boolean pingPong = stepPos.equals(approachState.lastWalkFromPos)
                && from.equals(approachState.lastWalkStepPos);
        double horizontalMotionSqr = deltaMovement.x * deltaMovement.x
                + deltaMovement.z * deltaMovement.z;
        boolean actuallyStalled = onGround && horizontalMotionSqr < 0.0025;
        boolean lowProgress = onGround && horizontalMotionSqr < 0.04;
        if (repeated && actuallyStalled) {
            approachState.walkStuckTicks++;
        } else if (pingPong && lowProgress) {
            approachState.walkStuckTicks += 2;
        } else if (pingPong && onGround) {
            approachState.walkStuckTicks++;
        } else {
            approachState.walkStuckTicks = 0;
        }
        approachState.lastWalkStepPos = stepPos.immutable();
        approachState.lastWalkFromPos = from.immutable();
    }

    public static void resetWalkStuckTracker(ArchitectApproachState approachState) {
        approachState.walkStuckTicks = 0;
        approachState.lastWalkStepPos = null;
        approachState.lastWalkFromPos = null;
    }

    public static void recordWalkCellHistory(ArchitectApproachState approachState, BlockPos current) {
        if (approachState.currentWalkCellPos == null) {
            approachState.currentWalkCellPos = current.immutable();
            return;
        }
        if (!current.equals(approachState.currentWalkCellPos)) {
            approachState.previousWalkCellPos = approachState.currentWalkCellPos;
            approachState.currentWalkCellPos = current.immutable();
        }
    }

    public static void resetWalkCellHistory(ArchitectApproachState approachState) {
        approachState.currentWalkCellPos = null;
        approachState.previousWalkCellPos = null;
        approachState.lastCompletedWalkWaypointPos = null;
        approachState.lastCompletedWalkBacktrackPos = null;
    }

    public static void resetUnstickBreakTracker(ArchitectApproachState approachState) {
        approachState.lastUnstickBreakCandidate = null;
        approachState.repeatedUnstickBreakAttempts = 0;
    }
}
