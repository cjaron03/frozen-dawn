package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Shared movement/execution primitives used by approach orchestration.
 */
final class ArchitectApproachMovementSupport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double LIQUID_ASCEND_ACCEL = 0.06;
    private static final double LIQUID_ASCEND_CAP = 0.16;
    private static final double CLIMB_VERTICAL_UP_SPEED = 0.18;
    private static final double CLIMB_VERTICAL_DOWN_SPEED = -0.12;
    private static final double CLIMB_HORIZONTAL_ACCEL = 0.08;
    private static final double CLIMB_HORIZONTAL_CAP = 0.12;
    private static final double MAX_DIRECT_CHASE_VERTICAL_DELTA = 1.5;

    private ArchitectApproachMovementSupport() {
    }

    static void executeFallbackChase(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            LivingEntity target,
            double speed,
            int repathCooldownTicks,
            boolean assistLiquidAscent
    ) {
        architect.clearCommittedWalk();
        architect.resetWalkStuckTracker();
        architect.resetUnstickBreakTracker();
        approachState.unreachableTicks = 0;
        approachState.sprintRequested = false;

        if (architect.isPathRecalcReady() || !architect.getNavigation().isInProgress()) {
            architect.getNavigation().moveTo(target, speed);
            architect.setPathRecalcCooldown(repathCooldownTicks);
        }
        architect.decrementPathRecalcCooldown();
        architect.getLookControl().setLookAt(target, 30f, 30f);

        if (assistLiquidAscent && architect.isInWaterOrBubble()) {
            Vec3 motion = architect.getDeltaMovement();
            if (motion.y < LIQUID_ASCEND_CAP) {
                architect.setDeltaMovement(motion.x, Math.min(LIQUID_ASCEND_CAP, motion.y + LIQUID_ASCEND_ACCEL), motion.z);
            }
        }
    }

    static boolean tickPendingScaffold(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker
    ) {
        if (approachState.scaffoldTarget == null) {
            return false;
        }

        approachState.scaffoldDelay--;
        BlockPos scaffoldTarget = approachState.scaffoldTarget;
        architect.getLookControl().setLookAt(
                scaffoldTarget.getX() + 0.5,
                scaffoldTarget.getY() - 0.5,
                scaffoldTarget.getZ() + 0.5
        );
        if (approachState.scaffoldDelay <= 0) {
            resolveScaffoldStep(
                    architect,
                    approachState,
                    blockBreaker,
                    scaffoldTarget);
            approachState.scaffoldTarget = null;
        }
        return true;
    }

    static boolean tickStepOffLerp(
            ArchitectEntity architect,
            ArchitectApproachState approachState
    ) {
        if (approachState.stepOffTarget == null) {
            return false;
        }

        approachState.stepOffProgress++;
        double t = Math.min(1.0, (double) approachState.stepOffProgress / ArchitectEntity.STEP_OFF_DURATION);
        double smooth = 1.0 - (1.0 - t) * (1.0 - t);
        double lx = approachState.stepOffStart.x
                + (approachState.stepOffTarget.getX() + 0.5 - approachState.stepOffStart.x) * smooth;
        double ly = approachState.stepOffStart.y
                + (approachState.stepOffTarget.getY() - approachState.stepOffStart.y) * smooth;
        double lz = approachState.stepOffStart.z
                + (approachState.stepOffTarget.getZ() + 0.5 - approachState.stepOffStart.z) * smooth;
        architect.setPos(lx, ly, lz);
        architect.getNavigation().stop();
        architect.getLookControl().setLookAt(
                approachState.stepOffTarget.getX() + 0.5,
                approachState.stepOffTarget.getY(),
                approachState.stepOffTarget.getZ() + 0.5
        );
        if (approachState.stepOffProgress >= ArchitectEntity.STEP_OFF_DURATION) {
            approachState.stepOffTarget = null;
            approachState.stepOffStart = null;
        }
        return true;
    }

    static boolean isVerticalClimbStep(ArchitectEntity architect, DStarLitePathfinder.NextStep step) {
        BlockPos current = architect.blockPosition();
        BlockPos next = step.pos();
        if (next.getX() != current.getX() || next.getZ() != current.getZ() || next.getY() == current.getY()) {
            return false;
        }
        BlockState currentState = architect.level().getBlockState(current);
        BlockState nextState = architect.level().getBlockState(next);
        return currentState.is(BlockTags.CLIMBABLE) || nextState.is(BlockTags.CLIMBABLE);
    }

    static boolean shouldUseDirectChase(
            ArchitectEntity architect,
            LivingEntity target,
            DStarLitePathfinder.NextStep step
    ) {
        if (step.type() != DStarLitePathfinder.StepType.WALK || !architect.canDirectChaseApproach(target)) {
            return false;
        }
        if (Math.abs(target.getY() - architect.getY()) > MAX_DIRECT_CHASE_VERTICAL_DELTA) {
            return false;
        }

        BlockPos current = architect.blockPosition();
        BlockPos next = step.pos();
        if (next.getY() != current.getY()) {
            return false;
        }
        if (isVerticalClimbStep(architect, step)) {
            return false;
        }

        BlockState currentState = architect.level().getBlockState(current);
        BlockState nextState = architect.level().getBlockState(next);
        return !currentState.is(BlockTags.CLIMBABLE) && !nextState.is(BlockTags.CLIMBABLE);
    }

    static void executeVerticalClimbStep(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            DStarLitePathfinder.NextStep step
    ) {
        architect.clearWalkNavigationState(true);
        architect.clearCommittedWalk();
        approachState.unreachableTicks = 0;
        approachState.sprintRequested = false;

        BlockPos current = architect.blockPosition();
        BlockPos next = step.pos();
        int yDir = Integer.compare(next.getY(), current.getY());

        double targetX = next.getX() + 0.5;
        double targetZ = next.getZ() + 0.5;
        architect.getMoveControl().setWantedPosition(targetX, architect.getY(), targetZ, 1.0);
        architect.getLookControl().setLookAt(targetX, next.getY() + 0.5, targetZ, 35f, 30f);

        Vec3 motion = architect.getDeltaMovement();
        double nx = Mth.clamp(targetX - architect.getX(), -CLIMB_HORIZONTAL_ACCEL, CLIMB_HORIZONTAL_ACCEL);
        double nz = Mth.clamp(targetZ - architect.getZ(), -CLIMB_HORIZONTAL_ACCEL, CLIMB_HORIZONTAL_ACCEL);
        double vx = Mth.clamp(motion.x + nx, -CLIMB_HORIZONTAL_CAP, CLIMB_HORIZONTAL_CAP);
        double vz = Mth.clamp(motion.z + nz, -CLIMB_HORIZONTAL_CAP, CLIMB_HORIZONTAL_CAP);

        double vy = motion.y;
        if (yDir > 0) {
            if (architect.onGround()) {
                architect.getJumpControl().jump();
            }
            vy = Math.max(vy, CLIMB_VERTICAL_UP_SPEED);
        } else if (yDir < 0) {
            vy = Math.min(vy, CLIMB_VERTICAL_DOWN_SPEED);
        }

        architect.setDeltaMovement(vx, vy, vz);
        architect.setPathRecalcCooldown(0);
    }

    static void resolveScaffoldStep(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker,
            BlockPos scaffoldTarget
    ) {
        Level level = architect.level();
        BlockPos supportPos = scaffoldTarget.below();
        BlockState supportState = level.getBlockState(supportPos);
        boolean supportReady = supportState.is(Blocks.PACKED_ICE) || supportState.isSolid() || architect.placeScaffoldIce(supportPos);
        if (!supportReady) {
            architect.getDStarPathfinder().onLocalBlockChanged(supportPos, level);
            return;
        }

        if (!isPassableForStand(scaffoldTarget, level) || !isPassableForStand(scaffoldTarget.above(), level)) {
            BlockPos obstruction = selectScaffoldObstruction(scaffoldTarget, level);
            if (obstruction != null && architect.isBreakableBlock(obstruction)) {
                blockBreaker.setTarget(obstruction);
                architect.getNavigation().stop();
                LOGGER.info("[Architect] Scaffold-up blocked, breaching {} before retrying step {}", obstruction, scaffoldTarget);
            }
            architect.getDStarPathfinder().onLocalBlockChanged(scaffoldTarget, level);
            architect.getDStarPathfinder().onLocalBlockChanged(scaffoldTarget.above(), level);
            return;
        }

        architect.teleportTo(
                scaffoldTarget.getX() + 0.5,
                scaffoldTarget.getY(),
                scaffoldTarget.getZ() + 0.5
        );
        architect.getNavigation().stop();
    }

    private static boolean isPassableForStand(BlockPos pos, Level level) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.WOODEN_DOORS)) {
            return true;
        }
        return !ArchitectBreakPolicy.isObstructiveForArchitect(state, level, pos);
    }

    @Nullable
    private static BlockPos selectScaffoldObstruction(BlockPos scaffoldTarget, Level level) {
        BlockState feet = level.getBlockState(scaffoldTarget);
        if (!feet.is(BlockTags.WOODEN_DOORS)
                && ArchitectBreakPolicy.isObstructiveForArchitect(feet, level, scaffoldTarget)) {
            return scaffoldTarget;
        }
        BlockPos headPos = scaffoldTarget.above();
        BlockState head = level.getBlockState(headPos);
        if (!head.is(BlockTags.WOODEN_DOORS)
                && ArchitectBreakPolicy.isObstructiveForArchitect(head, level, headPos)) {
            return headPos;
        }
        return null;
    }
}
