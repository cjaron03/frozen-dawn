package com.frozendawn.entity.architect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.ToDoubleFunction;

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
            BiFunction<BlockPos, BlockPos, Direction> primaryHorizontalDirection,
            ToDoubleFunction<BlockPos> surfaceY
    ) {
        BlockPos steeringTarget = ArchitectWalkCorridorState.getSteeringTarget(approachState);
        if (steeringTarget == null || approachState.committedWalkTicks <= 0) {
            return null;
        }

        Vec3 moveTarget = ArchitectWalkCorridorState.getMoveTarget(
                approachState,
                steeringTarget,
                lookAheadSteps,
                primaryHorizontalDirection,
                surfaceY);
        Vec3 lookTarget = ArchitectWalkCorridorState.getLookTarget(
                moveTarget,
                currentX,
                currentEyeY,
                currentY,
                currentZ,
                1.25,
                -0.15,
                0.35);

        // Keep smooth lookahead steering, but decide when to jump from the
        // immediate route cell. Measuring against moveTarget suppresses every
        // step-up when a straight upper corridor extends beyond the ledge.
        double steeringX = steeringTarget.getX() + 0.5D;
        double steeringZ = steeringTarget.getZ() + 0.5D;
        double horizontalDistSqr = (steeringX - currentX) * (steeringX - currentX)
                + (steeringZ - currentZ) * (steeringZ - currentZ);
        double verticalDelta = surfaceY.applyAsDouble(steeringTarget) - currentY;
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
