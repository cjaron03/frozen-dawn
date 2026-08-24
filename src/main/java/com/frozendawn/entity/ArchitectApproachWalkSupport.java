package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectBlockEnvironment;
import com.frozendawn.entity.architect.ArchitectMeleeEngagement;
import com.frozendawn.entity.architect.ArchitectWalkBreakPlanner;
import com.frozendawn.entity.architect.ArchitectWalkCorridorState;
import com.frozendawn.entity.architect.ArchitectWalkMotionPlanner;
import com.frozendawn.entity.architect.ArchitectWalkProgress;
import com.frozendawn.entity.architect.ArchitectWalkTracking;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Owns committed walk-corridor execution, stuck recovery, and walk-step dispatch for approach mode.
 */
final class ArchitectApproachWalkSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int WALK_COMMIT_TICKS = 12;
    private static final int WALK_COMMIT_NO_PROGRESS_TICKS = 6;
    private static final int WALK_COMMIT_DEADMAN_TICKS = 8;
    private static final double WALK_COMMIT_PROGRESS_EPSILON = 0.10;
    private static final double WALK_COMMIT_DEADMAN_DISPLACEMENT_SQR = 0.20;
    private static final double WALK_WAYPOINT_REACH_HORIZONTAL_SQR = 0.64;
    private static final double WALK_WAYPOINT_REACH_UPWARD_VERTICAL = 0.60;
    private static final double WALK_WAYPOINT_REACH_DOWNWARD_VERTICAL = 1.05;
    private static final double WALK_AUTO_JUMP_MIN_VERTICAL_DELTA = 0.90;
    private static final double WALK_AUTO_JUMP_MAX_HORIZONTAL_SQR = 0.90;
    private static final int WALK_TARGET_SHIFT_GRACE_TICKS = 4;
    private static final double WALK_TARGET_SHIFT_HORIZONTAL_SQR = 36.0;
    private static final int WALK_TARGET_SHIFT_VERTICAL = 2;
    private static final int WALK_CORRIDOR_LOOKAHEAD_STEPS = 2;
    private static final int WALK_SPRINT_STRAIGHT_STEPS = 2;
    private static final double APPROACH_SPRINT_SPEED = 1.15;
    private static final double DIRECT_APPROACH_PATH_HORIZONTAL_RANGE = 8.0;
    private static final double DIRECT_APPROACH_PATH_VERTICAL_RANGE = 4.0;

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;
    private final ArchitectBlockBreaker blockBreaker;
    private final ArchitectApproachCorridorSupport corridorSupport;
    private final ArchitectApproachUnstickSupport unstickSupport;

    ArchitectApproachWalkSupport(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker
    ) {
        this.architect = architect;
        this.approachState = approachState;
        this.blockBreaker = blockBreaker;
        this.corridorSupport = new ArchitectApproachCorridorSupport(architect, approachState);
        this.unstickSupport = new ArchitectApproachUnstickSupport(
                architect,
                approachState,
                blockBreaker,
                corridorSupport);
    }

    void trackWalkStep(BlockPos stepPos) {
        ArchitectWalkTracking.trackWalkStep(
                approachState,
                architect.blockPosition(),
                stepPos,
                architect.getDeltaMovement(),
                architect.onGround());
    }

    void resetWalkStuckTracker() {
        ArchitectWalkTracking.resetWalkStuckTracker(approachState);
    }

    void recordWalkCellHistory() {
        ArchitectWalkTracking.recordWalkCellHistory(approachState, architect.blockPosition());
    }

    void resetWalkCellHistory() {
        ArchitectWalkTracking.resetWalkCellHistory(approachState);
    }

    void resetUnstickBreakTracker() {
        ArchitectWalkTracking.resetUnstickBreakTracker(approachState);
    }

    void clearCommittedWalk() {
        ArchitectWalkCorridorState.clear(approachState);
    }

    @Nullable
    BlockPos getCommittedWalkSteeringTarget() {
        return ArchitectWalkCorridorState.getSteeringTarget(approachState);
    }

    @Nullable
    BlockPos getImmediateBacktrackPos() {
        return ArchitectWalkCorridorState.getImmediateBacktrackPos(approachState, architect.blockPosition());
    }

    boolean tryContinueCommittedWalk(@Nullable LivingEntity target) {
        approachState.sprintRequested = shouldSprintCommittedWalk(target);
        if (!shouldContinueCommittedWalk(target)) {
            approachState.sprintRequested = false;
            return false;
        }

        if (!advanceCommittedWalkProgress()) {
            approachState.sprintRequested = false;
            return false;
        }

        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null) {
            approachState.sprintRequested = false;
            return false;
        }

        double distSqr = ArchitectWalkProgress.distanceToWaypointSqr(
                architect.getX(),
                architect.getY(),
                architect.getZ(),
                steeringTarget);
        if (distSqr + WALK_COMMIT_PROGRESS_EPSILON < approachState.committedWalkLastDistSqr) {
            approachState.committedWalkLastDistSqr = distSqr;
            approachState.committedWalkNoProgressTicks = 0;
        } else {
            approachState.committedWalkNoProgressTicks++;
        }

        if (approachState.committedWalkNoProgressTicks >= WALK_COMMIT_NO_PROGRESS_TICKS) {
            approachState.walkStuckTicks = Math.max(approachState.walkStuckTicks, unstickSupport.walkStuckBreakTicks());
            BlockPos stuckTarget = steeringTarget;
            invalidateCommittedWalk("STUCK", target);
            approachState.sprintRequested = false;
            if (stuckTarget != null && handleWalkStuck(stuckTarget, target)) {
                return true;
            }
            return false;
        }

        if (approachState.committedWalkStartVec != null
                && approachState.committedWalkAgeTicks >= WALK_COMMIT_DEADMAN_TICKS
                && architect.position().distanceToSqr(approachState.committedWalkStartVec) < WALK_COMMIT_DEADMAN_DISPLACEMENT_SQR) {
            approachState.walkStuckTicks = Math.max(approachState.walkStuckTicks, unstickSupport.walkStuckBreakTicks());
            BlockPos stuckTarget = steeringTarget;
            invalidateCommittedWalk("DEADMAN", target);
            approachState.sprintRequested = false;
            if (stuckTarget != null && handleWalkStuck(stuckTarget, target)) {
                return true;
            }
            return false;
        }

        if (!continueCommittedWalk()) {
            approachState.sprintRequested = false;
            return false;
        }

        return true;
    }

    void invalidateStaleApproachBreakTarget(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
        BlockPos breakTarget = blockBreaker.getTarget();
        if (breakTarget == null) {
            return;
        }

        BlockPos desiredBreak = step.breakTarget();
        boolean continuingBreak = (step.type() == DStarLitePathfinder.StepType.BREACH
                || step.type() == DStarLitePathfinder.StepType.DIG_DOWN)
                && desiredBreak != null
                && breakTarget.equals(desiredBreak);

        double dxToTarget = target != null ? target.getX() - architect.getX() : 0.0;
        double dzToTarget = target != null ? target.getZ() - architect.getZ() : 0.0;
        double horizontalTargetDelta = Math.sqrt(dxToTarget * dxToTarget + dzToTarget * dzToTarget);
        boolean continuingDropInBreak = target != null
                && step.type() == DStarLitePathfinder.StepType.SCAFFOLD_BRIDGE
                && target.getY() < architect.getY() - 1.0
                && horizontalTargetDelta <= 2.5
                && breakTarget.getY() == architect.blockPosition().getY() - 1
                && Math.abs(breakTarget.getX() - architect.blockPosition().getX()) <= 3
                && Math.abs(breakTarget.getZ() - architect.blockPosition().getZ()) <= 3;

        boolean continuingWalkBreak = step.type() == DStarLitePathfinder.StepType.WALK
                && corridorSupport.shouldContinueWalkObstructionBreak(
                        step,
                        breakTarget,
                        architect::isBreakableBlock,
                        this::isLastResortBreakBlock);

        if (!continuingBreak && !continuingDropInBreak && !continuingWalkBreak) {
            blockBreaker.clearTarget();
            if (breakTarget.equals(approachState.ceilingBreachPos)) {
                approachState.ceilingBreachPos = null;
            }
        }
    }

    boolean shouldContinueApproachBreak(@Nullable LivingEntity target, BlockPos expectedBreakTarget) {
        if (architect.getBrainAction() != ArchitectEntity.ACTION_APPROACH || target == null) {
            return false;
        }

        BlockPos targetPos = target.blockPosition();
        if (approachState.dstar.needsReinitialize(targetPos)) {
            return false;
        }

        if (!approachState.dstar.isSearchComplete()) {
            approachState.dstar.computePartial(300, architect.level());
            if (!approachState.dstar.isSearchComplete()) {
                return false;
            }
        }

        approachState.dstar.updateStart(architect.blockPosition());
        if (!approachState.dstar.isSearchComplete()) {
            approachState.dstar.computePartial(200, architect.level());
            if (!approachState.dstar.isSearchComplete()) {
                return false;
            }
        }

        DStarLitePathfinder.NextStep nextStep = approachState.dstar.getNextStep(architect.blockPosition(), architect.level());
        return nextStep.type() == DStarLitePathfinder.StepType.BREACH
                && expectedBreakTarget.equals(nextStep.breakTarget());
    }

    boolean handleWalkStuck(BlockPos stepPos, @Nullable LivingEntity target) {
        return unstickSupport.handleWalkStuck(stepPos, target, this::isLastResortBreakBlock);
    }

    void executeVanillaWalkStep(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
        BlockPos startPos = architect.blockPosition();
        BlockPos stepPos = step.pos();
        List<BlockPos> corridorNodes = corridorSupport.buildWalkCorridorNodes(startPos, step);
        if (corridorNodes.isEmpty()) {
            corridorNodes = List.of(stepPos.immutable());
        }
        if (corridorSupport.isReverseOnlyWalkCorridor(startPos, step, corridorNodes)) {
            handleReverseOnlyWalkCorridor(stepPos, target);
            return;
        }
        BlockPos corridorBreakTarget = ArchitectWalkBreakPlanner.findCorridorBreakTarget(
                corridorNodes,
                architect::isBreakableBlock,
                this::isLastResortBreakBlock);
        if (corridorBreakTarget != null) {
            startWalkCorridorBreak(corridorBreakTarget);
            return;
        }
        commitWalkStep(corridorNodes, target);
        approachState.sprintRequested = shouldSprintCommittedWalk(target);
        if (!continueCommittedWalk()) {
            approachState.sprintRequested = false;
            clearCommittedWalk();
        }
    }

    boolean canDirectChaseApproach(@Nullable LivingEntity target) {
        if (target == null || blockBreaker.hasTarget() || !architect.hasLineOfSight(target)) {
            return false;
        }
        if (ArchitectMeleeEngagement.horizontalDistanceTo(architect, target) > DIRECT_APPROACH_PATH_HORIZONTAL_RANGE
                || ArchitectMeleeEngagement.verticalDistanceTo(architect, target) > DIRECT_APPROACH_PATH_VERTICAL_RANGE) {
            return false;
        }
        return ArchitectMeleeEngagement.hasCleanReachableApproachPath(architect.getNavigation(), target);
    }

    void executeDirectApproachChase(LivingEntity target) {
        clearCommittedWalk();
        resetWalkStuckTracker();
        approachState.unreachableTicks = 0;
        approachState.sprintRequested = shouldSprintDirectApproach(target);
        architect.getNavigation().moveTo(target, getApproachTravelSpeed());
        architect.getLookControl().setLookAt(target, 30f, 30f);
    }

    void clearWalkNavigationState(boolean stopNavigation) {
        if (stopNavigation) {
            architect.getNavigation().stop();
        }
    }

    private void commitWalkStep(List<BlockPos> corridorNodes, @Nullable LivingEntity target) {
        ArchitectWalkCorridorState.commit(
                approachState,
                corridorNodes,
                architect.blockPosition(),
                architect.position(),
                target != null ? target.blockPosition() : null,
                WALK_COMMIT_TICKS,
                waypoint -> ArchitectWalkProgress.distanceToWaypointSqr(
                        architect.getX(), architect.getY(), architect.getZ(), waypoint));
        resetWalkStuckTracker();
    }

    private void invalidateCommittedWalk(String reason, @Nullable LivingEntity target) {
        if (approachState.committedWalkWaypoint == null) {
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] WALK corridor invalidated: reason={} current={} firstStep={} waypoint={} age={} ttlLeft={} targetSnapshot={} targetNow={}",
                    reason,
                    architect.blockPosition(),
                    approachState.committedWalkFirstStepPos,
                    approachState.committedWalkWaypoint,
                    approachState.committedWalkAgeTicks,
                    approachState.committedWalkTicks,
                    approachState.committedWalkTargetSnapshot,
                    target != null ? target.blockPosition() : null);
        }
        clearCommittedWalk();
    }

    private boolean canSprintApproachBase(@Nullable LivingEntity target) {
        if (target == null || architect.getBrainAction() != ArchitectEntity.ACTION_APPROACH) {
            return false;
        }
        if (blockBreaker.hasTarget()
                || blockBreaker.isMining()
                || approachState.scaffoldTarget != null
                || approachState.stepOffTarget != null
                || approachState.ceilingBreachPos != null) {
            return false;
        }
        return !architect.isTargetWithinMeleeEngageGeometry(target);
    }

    private boolean hasStraightCommittedWalkSprintLane() {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null || approachState.committedWalkCorridor.isEmpty()) {
            return false;
        }

        int corridorIndex = Math.max(0, Math.min(
                approachState.committedWalkCorridorIndex,
                approachState.committedWalkCorridor.size() - 1));
        BlockPos cursor = steeringTarget;
        Direction runDirection = null;
        int straightSteps = 0;

        for (int i = corridorIndex + 1; i < approachState.committedWalkCorridor.size(); i++) {
            BlockPos candidate = approachState.committedWalkCorridor.get(i);
            if (candidate.getY() != cursor.getY()) {
                break;
            }

            Direction segmentDirection = corridorSupport.getPrimaryHorizontalDirection(cursor, candidate);
            if (segmentDirection == null) {
                break;
            }

            if (runDirection == null) {
                runDirection = segmentDirection;
            } else if (segmentDirection != runDirection) {
                break;
            }

            cursor = candidate;
            straightSteps++;
        }

        return straightSteps >= WALK_SPRINT_STRAIGHT_STEPS;
    }

    private boolean shouldSprintCommittedWalk(@Nullable LivingEntity target) {
        return canSprintApproachBase(target) && hasStraightCommittedWalkSprintLane();
    }

    private boolean shouldSprintDirectApproach(@Nullable LivingEntity target) {
        return canSprintApproachBase(target) && target != null && architect.hasLineOfSight(target);
    }

    private double getApproachTravelSpeed() {
        return approachState.sprintRequested ? APPROACH_SPRINT_SPEED : 1.0;
    }

    private boolean advanceCommittedWalkProgress() {
        return ArchitectWalkProgress.advanceCommittedWalkProgress(
                approachState,
                architect.blockPosition(),
                architect.getX(),
                architect.getY(),
                architect.getZ(),
                WALK_WAYPOINT_REACH_HORIZONTAL_SQR,
                WALK_WAYPOINT_REACH_UPWARD_VERTICAL,
                WALK_WAYPOINT_REACH_DOWNWARD_VERTICAL);
    }

    private boolean continueCommittedWalk() {
        ArchitectWalkMotionPlanner.MotionStep motion = ArchitectWalkMotionPlanner.planCommittedWalkStep(
                approachState,
                architect.getX(),
                architect.getY(),
                architect.getZ(),
                architect.getEyeY(),
                architect.onGround(),
                WALK_CORRIDOR_LOOKAHEAD_STEPS,
                WALK_AUTO_JUMP_MIN_VERTICAL_DELTA,
                WALK_AUTO_JUMP_MAX_HORIZONTAL_SQR,
                corridorSupport::getPrimaryHorizontalDirection,
                this::walkSurfaceY);
        if (motion == null) {
            return false;
        }
        if (motion.shouldJump()) {
            architect.getJumpControl().jump();
        }

        // Follow D* corridors with raw MoveControl so edge/scaffold approach cells
        // do not get vetoed by vanilla navigation before the scaffold action can fire.
        architect.getNavigation().stop();
        Vec3 moveTarget = motion.moveTarget();
        Vec3 lookTarget = motion.lookTarget();
        architect.getMoveControl().setWantedPosition(moveTarget.x, moveTarget.y, moveTarget.z, getApproachTravelSpeed());
        architect.getLookControl().setLookAt(lookTarget.x, lookTarget.y, lookTarget.z, 40f, 30f);
        return true;
    }

    private boolean shouldContinueCommittedWalk(@Nullable LivingEntity target) {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null) {
            return false;
        }
        if (approachState.committedWalkTicks <= 0) {
            invalidateCommittedWalk("TTL", target);
            return false;
        }

        BlockState feetState = architect.level().getBlockState(steeringTarget);
        BlockState headState = architect.level().getBlockState(steeringTarget.above());
        if (isPathObstructingState(feetState, steeringTarget)
                || isPathObstructingState(headState, steeringTarget.above())) {
            invalidateCommittedWalk("BLOCKED", target);
            return false;
        }

        if (target != null
                && approachState.committedWalkTargetSnapshot != null
                && approachState.committedWalkAgeTicks >= WALK_TARGET_SHIFT_GRACE_TICKS) {
            BlockPos targetPos = target.blockPosition();
            if (ArchitectWalkProgress.horizontalDistanceSqr(targetPos, approachState.committedWalkTargetSnapshot)
                    > WALK_TARGET_SHIFT_HORIZONTAL_SQR
                    || Math.abs(targetPos.getY() - approachState.committedWalkTargetSnapshot.getY()) > WALK_TARGET_SHIFT_VERTICAL) {
                invalidateCommittedWalk("TARGET_SHIFT", target);
                return false;
            }
        }

        return true;
    }

    private boolean isLastResortBreakBlock(BlockPos pos) {
        return ArchitectBreakPolicy.isLastResortBreakBlock(architect.level().getBlockState(pos));
    }

    private double walkSurfaceY(BlockPos pos) {
        BlockState state = architect.level().getBlockState(pos);
        return pos.getY() + ArchitectBreakPolicy.traversableSurfaceOffset(
                state, architect.level(), pos);
    }

    private void startWalkCorridorBreak(BlockPos breakTarget) {
        clearWalkNavigationState(true);
        clearCommittedWalk();
        resetWalkStuckTracker();
        resetUnstickBreakTracker();
        blockBreaker.setTarget(breakTarget.immutable());
        LOGGER.info("[Architect] WALK corridor requires breach at {}", breakTarget);
        architect.walkToBreakTarget();
    }

    private boolean handleReverseOnlyWalkCorridor(BlockPos stepPos, @Nullable LivingEntity target) {
        clearWalkNavigationState(true);
        clearCommittedWalk();
        trackWalkStep(stepPos);
        return handleWalkStuck(stepPos, target);
    }

    private boolean isPathObstructingState(BlockState state, BlockPos pos) {
        return ArchitectBlockEnvironment.isPathObstructingState(architect.level(), state, pos);
    }
}
