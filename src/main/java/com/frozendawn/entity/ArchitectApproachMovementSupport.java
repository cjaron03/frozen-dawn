package com.frozendawn.entity;

import com.frozendawn.entity.architect.BreakChoice;
import com.frozendawn.entity.architect.BreakReason;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectBlockEnvironment;
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
    private static final double FALLBACK_SPRINT_MIN_SPEED = 1.10;
    private static final double OPEN_DESCENT_HORIZONTAL_RANGE = 8.0;
    private static final double OPEN_DESCENT_VERTICAL_RANGE = 6.0;
    private static final int OPEN_DESCENT_MAX_NODES = 32;

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
        approachState.unreachableTicks = 0;
        boolean canFallbackSprint = !assistLiquidAscent
                && architect.hasLineOfSight(target)
                && !architect.isTargetWithinMeleeEngageGeometry(target);
        approachState.sprintRequested = canFallbackSprint;
        double chaseSpeed = canFallbackSprint ? Math.max(speed, FALLBACK_SPRINT_MIN_SPEED) : speed;

        if (architect.isPathRecalcReady() || !architect.getNavigation().isInProgress()) {
            architect.getNavigation().moveTo(target, chaseSpeed);
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

    /** Prefer a bounded, complete walking route to a lower target before choosing excavation. */
    static boolean tryFollowOpenDescent(ArchitectEntity architect, LivingEntity target) {
        double drop = architect.getY() - target.getY();
        if (drop <= 1.0 || drop > OPEN_DESCENT_VERTICAL_RANGE
                || architect.horizontalDistanceTo(target) > OPEN_DESCENT_HORIZONTAL_RANGE) {
            return false;
        }
        var navigation = architect.getNavigation();
        var path = navigation.createPath(target, 1);
        if (path == null || !path.canReach() || path.getNodeCount() > OPEN_DESCENT_MAX_NODES || path.getEndNode() == null) {
            return false;
        }
        // A path that merely gets horizontally near the target on the rim is incomplete.
        if (Math.abs(path.getEndNode().y - target.getY()) > 1.0) {
            return false;
        }
        if (!isSafeWalkingPath(architect, path)) {
            return false;
        }
        boolean changed = navigation.getPath() != path;
        architect.clearCommittedWalk();
        architect.resetWalkStuckTracker();
        if (!navigation.moveTo(path, 1.0)) {
            return false;
        }
        if (changed) {
            architect.recordDecision("OPEN_DESCENT", null, "nodes=" + path.getNodeCount());
        }
        architect.getLookControl().setLookAt(target, 30f, 30f);
        return true;
    }

    static boolean isSafeWalkingPath(ArchitectEntity architect, net.minecraft.world.level.pathfinder.Path path) {
        if (path.getNodeCount() > OPEN_DESCENT_MAX_NODES) return false;
        int previousY = architect.blockPosition().getY();
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            if (node.type == net.minecraft.world.level.pathfinder.PathType.BLOCKED || node.costMalus > 0
                    || previousY - node.y > architect.getMaxFallDistance()) {
                return false;
            }
            previousY = node.y;
        }
        return true;
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
            architect.getDStarPathfinder().onLocalBlockChanged(
                    supportPos,
                    level,
                    "APPROACH_LOCAL_RESEED",
                    ArchitectEntity.actionName(architect.getBrainAction()),
                    approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE",
                    -1.0
            );
            return;
        }

        if (!isPassableForStand(scaffoldTarget, level) || !isPassableForStand(scaffoldTarget.above(), level)) {
            BlockPos obstruction = selectScaffoldObstruction(scaffoldTarget, level);
            if (obstruction != null && architect.isBreakableBlock(obstruction)) {
                blockBreaker.setChoice(new BreakChoice(obstruction, BreakReason.SCAFFOLD));
                architect.getNavigation().stop();
                LOGGER.info("[Architect] Scaffold-up blocked, breaching {} before retrying step {}", obstruction, scaffoldTarget);
            }
            architect.getDStarPathfinder().onLocalBlockChanged(
                    scaffoldTarget,
                    level,
                    "APPROACH_LOCAL_RESEED",
                    ArchitectEntity.actionName(architect.getBrainAction()),
                    approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE",
                    -1.0
            );
            architect.getDStarPathfinder().onLocalBlockChanged(
                    scaffoldTarget.above(),
                    level,
                    "APPROACH_LOCAL_RESEED",
                    ArchitectEntity.actionName(architect.getBrainAction()),
                    approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE",
                    -1.0
            );
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
        if (ArchitectBlockEnvironment.isOpenablePassage(state)) {
            return true;
        }
        return !ArchitectBreakPolicy.isObstructiveForArchitect(state, level, pos);
    }

    @Nullable
    private static BlockPos selectScaffoldObstruction(BlockPos scaffoldTarget, Level level) {
        BlockState feet = level.getBlockState(scaffoldTarget);
        if (!ArchitectBlockEnvironment.isOpenablePassage(feet)
                && ArchitectBreakPolicy.isObstructiveForArchitect(feet, level, scaffoldTarget)) {
            return scaffoldTarget;
        }
        BlockPos headPos = scaffoldTarget.above();
        BlockState head = level.getBlockState(headPos);
        if (!ArchitectBlockEnvironment.isOpenablePassage(head)
                && ArchitectBreakPolicy.isObstructiveForArchitect(head, level, headPos)) {
            return headPos;
        }
        return null;
    }
}
