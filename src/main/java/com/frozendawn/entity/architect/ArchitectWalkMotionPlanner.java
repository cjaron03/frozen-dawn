package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Plans one committed-walk movement step and advances committed-walk tick
 * counters in approach state.
 */
public final class ArchitectWalkMotionPlanner {

    private ArchitectWalkMotionPlanner() {
    }

    @Nullable
    public static MotionStep planCommittedWalkStep(
            ArchitectApproachState approachState,
            double currentX,
            double currentY,
            double currentZ,
            double currentEyeY,
            boolean onGround,
            int lookAheadSteps,
            double autoJumpMinVerticalDelta,
            double autoJumpMaxHorizontalSqr,
            BiFunction<BlockPos, BlockPos, Direction> primaryHorizontalDirection
    ) {
        BlockPos steeringTarget = ArchitectWalkCorridorState.getSteeringTarget(approachState);
        if (steeringTarget == null || approachState.committedWalkTicks <= 0) {
            return null;
        }

        Vec3 moveTarget = ArchitectWalkCorridorState.getMoveTarget(
                approachState,
                steeringTarget,
                lookAheadSteps,
                primaryHorizontalDirection);
        Vec3 lookTarget = ArchitectWalkCorridorState.getLookTarget(
                moveTarget,
                currentX,
                currentEyeY,
                currentY,
                currentZ,
                1.25,
                -0.15,
                0.35);

        double horizontalDistSqr = (moveTarget.x - currentX) * (moveTarget.x - currentX)
                + (moveTarget.z - currentZ) * (moveTarget.z - currentZ);
        double verticalDelta = steeringTarget.getY() - currentY;
        boolean shouldJump = verticalDelta >= autoJumpMinVerticalDelta
                && horizontalDistSqr <= autoJumpMaxHorizontalSqr
                && onGround;

        approachState.committedWalkTicks--;
        approachState.committedWalkAgeTicks++;
        return new MotionStep(moveTarget, lookTarget, shouldJump);
    }

    public record MotionStep(Vec3 moveTarget, Vec3 lookTarget, boolean shouldJump) {
    }
}
