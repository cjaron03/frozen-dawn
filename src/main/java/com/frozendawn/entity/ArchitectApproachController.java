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
 * Owns the top-level approach/path orchestration flow while {@link ArchitectEntity}
 * continues to provide the lower-level walk, breach, and interaction helpers.
 */
final class ArchitectApproachController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double PLANNING_FALLBACK_SPEED = 0.95;
    private static final int PLANNING_FALLBACK_REPATH_TICKS = 8;
    private static final double LIQUID_ESCAPE_SPEED = 1.05;
    private static final int LIQUID_ESCAPE_REPATH_TICKS = 5;
    private static final double LIQUID_ASCEND_ACCEL = 0.06;
    private static final double LIQUID_ASCEND_CAP = 0.16;
    private static final double CLIMB_VERTICAL_UP_SPEED = 0.18;
    private static final double CLIMB_VERTICAL_DOWN_SPEED = -0.12;
    private static final double CLIMB_HORIZONTAL_ACCEL = 0.08;
    private static final double CLIMB_HORIZONTAL_CAP = 0.12;
    private static final double MAX_DIRECT_CHASE_VERTICAL_DELTA = 1.5;

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;
    private final ArchitectBlockBreaker blockBreaker;

    ArchitectApproachController(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker
    ) {
        this.architect = architect;
        this.approachState = approachState;
        this.blockBreaker = blockBreaker;
    }

    void precomputeDStarDuringObserve(LivingEntity target) {
        BlockPos targetPos = target.blockPosition();

        if (approachState.dstar.needsReinitialize(targetPos)) {
            approachState.dstar.setSurfaceY(approachState.surfaceY);
            approachState.dstar.initialize(targetPos, architect.blockPosition(), architect.level());
            approachState.dstarPrecomputed = false;
            approachState.dstar.computePartial(800, architect.level());
        } else {
            approachState.dstar.updateStart(architect.blockPosition());
            approachState.dstar.computePartial(approachState.dstarPrecomputed ? 120 : 250, architect.level());
        }

        if (approachState.dstar.isSearchComplete()) {
            approachState.dstarPrecomputed = true;
        }
    }

    void executeApproach(@Nullable LivingEntity target) {
        approachState.sprintRequested = false;
        if (target == null) {
            approachState.unreachableTicks = 0;
            architect.approachLastKnownPos();
            return;
        }

        architect.recordWalkCellHistory();

        // Proactively open nearby wooden doors before movement dispatch.
        architect.keepNearbyWoodenDoorsOpen();
        // Snow/surface state can change around the Architect each tick; reseed a
        // local incremental update periodically so D* tracks dynamic obstructions.
        if (architect.tickCount % 10 == 0) {
            approachState.dstar.onLocalBlockChanged(architect.blockPosition(), architect.level());
        }

        // Avoid committed-walk churn while submerged: switch to direct water egress chase.
        if (architect.isInWaterOrBubble()) {
            executeFallbackChase(target, LIQUID_ESCAPE_SPEED, LIQUID_ESCAPE_REPATH_TICKS, true);
            return;
        }

        // Scaffold pacing: wait after placing ice, then jump up
        if (approachState.scaffoldTarget != null) {
            approachState.scaffoldDelay--;
            BlockPos scaffoldTarget = approachState.scaffoldTarget;
            architect.getLookControl().setLookAt(
                    scaffoldTarget.getX() + 0.5,
                    scaffoldTarget.getY() - 0.5,
                    scaffoldTarget.getZ() + 0.5
            );
            if (approachState.scaffoldDelay <= 0) {
                resolveScaffoldStep(scaffoldTarget);
                approachState.scaffoldTarget = null;
            }
            return;
        }

        // Handle smooth step-off lerp
        if (approachState.stepOffTarget != null) {
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
            return;
        }

        if (blockBreaker.isMining() && architect.continueBreaking(target)) {
            return;
        }

        if (blockBreaker.hasTarget()) {
            BlockPos bt = blockBreaker.getTarget();
            if (bt != null && architect.level().getBlockState(bt).isAir()) {
                blockBreaker.clearTarget();
                architect.resetUnstickBreakTracker();
                if (bt.equals(approachState.ceilingBreachPos)) {
                    approachState.ceilingBreachPos = null;
                }
            }
        }

        if (architect.shouldPreferMeleeOverApproach(target)) {
            architect.primeMeleeHandoff();
            architect.resetReevalCooldown();
        }

        if (architect.tryContinueCommittedWalk(target)) {
            return;
        }

        BlockPos targetPos = target.blockPosition();
        if (approachState.dstar.needsReinitialize(targetPos)) {
            approachState.dstar.setSurfaceY(approachState.surfaceY);
            approachState.dstar.initialize(targetPos, architect.blockPosition(), architect.level());
            approachState.dstar.computePartial(2000, architect.level());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "[Architect] D* Lite initialized: goal={} start={} cells={} complete={}",
                        targetPos,
                        architect.blockPosition(),
                        approachState.dstar.getCellCount(),
                        approachState.dstar.isSearchComplete()
                );
            }
        }

        if (!approachState.dstar.isSearchComplete()) {
            approachState.dstar.computePartial(500, architect.level());
            if (!approachState.dstar.isSearchComplete()) {
                executeFallbackChase(target, PLANNING_FALLBACK_SPEED, PLANNING_FALLBACK_REPATH_TICKS, false);
                return;
            }
        }

        approachState.dstar.updateStart(architect.blockPosition());
        if (!approachState.dstar.computePartial(220, architect.level())) {
            executeFallbackChase(target, PLANNING_FALLBACK_SPEED, PLANNING_FALLBACK_REPATH_TICKS, false);
            return;
        }

        BlockPos avoidImmediateBacktrack = architect.getImmediateBacktrackPos();
        DStarLitePathfinder.NextStep step =
                approachState.dstar.getNextStep(architect.blockPosition(), architect.level(), avoidImmediateBacktrack);
        architect.keepDoorOpenNear(step.pos());

        if (shouldUseDirectChase(target, step)) {
            architect.executeDirectApproachChase(target);
            return;
        }

        architect.invalidateStaleApproachBreakTarget(step, target);

        if (step.type() == DStarLitePathfinder.StepType.WALK && isVerticalClimbStep(step)) {
            executeVerticalClimbStep(step);
            return;
        }

        if (step.type() == DStarLitePathfinder.StepType.UNREACHABLE) {
            BlockPos stuckWalkStep = architect.getCommittedWalkSteeringTarget();
            architect.clearWalkNavigationState(true);
            if (stuckWalkStep != null) {
                architect.trackWalkStep(stuckWalkStep);
                if (architect.handleWalkStuck(stuckWalkStep, target)) {
                    return;
                }
            }
            architect.clearCommittedWalk();
            approachState.unreachableTicks++;
            if (approachState.unreachableTicks >= ArchitectEntity.UNREACHABLE_BREAK_DELAY_TICKS) {
                architect.fallbackWallBreak(target);
            }
            if (architect.tickCount % 20 == 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "[Architect] D* Lite: UNREACHABLE, g={} cells={}",
                        String.format("%.1f", approachState.dstar.getStartG()),
                        approachState.dstar.getCellCount()
                );
            }
            if (approachState.unreachableTicks >= 40) {
                approachState.dstar.setSurfaceY(approachState.surfaceY);
                approachState.dstar.initialize(targetPos, architect.blockPosition(), architect.level());
                approachState.dstar.computePartial(1200, architect.level());
                approachState.unreachableTicks = 0;
                LOGGER.info("[Architect] D* Lite hard refresh after prolonged UNREACHABLE");
            }
            if (!blockBreaker.hasTarget()) {
                executeFallbackChase(target, PLANNING_FALLBACK_SPEED, PLANNING_FALLBACK_REPATH_TICKS, false);
            }
            return;
        }
        approachState.unreachableTicks = 0;

        if (blockBreaker.hasTarget()) {
            architect.clearWalkNavigationState(true);
            architect.clearCommittedWalk();
            if (architect.walkToBreakTarget()) {
                return;
            }
        }

        switch (step.type()) {
            case WALK -> architect.executeVanillaWalkStep(step, target);
            case BREACH -> {
                architect.clearWalkNavigationState(true);
                architect.clearCommittedWalk();
                architect.resetWalkStuckTracker();
                BlockPos breakTarget = step.breakTarget();
                if (breakTarget == null) {
                    breakTarget = architect.findBreakableWallBlock(target);
                }
                if (breakTarget != null) {
                    double blockDist = architect.position().distanceToSqr(
                            breakTarget.getX() + 0.5,
                            breakTarget.getY() + 0.5,
                            breakTarget.getZ() + 0.5
                    );
                    if (blockDist <= 4.5 * 4.5) {
                        blockBreaker.setTarget(breakTarget);
                        architect.getNavigation().stop();
                        LOGGER.info("[Architect] D* BREACH at {} ({})",
                                breakTarget,
                                architect.level().getBlockState(breakTarget).getBlock());
                    } else {
                        Vec3 toBlock = new Vec3(
                                breakTarget.getX() + 0.5 - architect.getX(),
                                0,
                                breakTarget.getZ() + 0.5 - architect.getZ()
                        ).normalize();
                        architect.getNavigation().moveTo(
                                breakTarget.getX() + 0.5 - toBlock.x * 1.5,
                                breakTarget.getY(),
                                breakTarget.getZ() + 0.5 - toBlock.z * 1.5,
                                1.0
                        );
                    }
                }
            }
            case SCAFFOLD_UP -> {
                architect.clearWalkNavigationState(true);
                architect.clearCommittedWalk();
                architect.resetWalkStuckTracker();
                if (architect.onGround() && architect.getScaffoldIceCount() < architect.getMaxScaffoldIce()) {
                    approachState.scaffoldTarget = step.pos();
                    approachState.scaffoldDelay = ArchitectEntity.SCAFFOLD_PLACE_TICKS;
                    LOGGER.info("[Architect] D* SCAFFOLD queued support={} step={} (waiting {}t)",
                            step.pos().below(),
                            step.pos(),
                            ArchitectEntity.SCAFFOLD_PLACE_TICKS);
                }
            }
            case SCAFFOLD_BRIDGE -> {
                architect.clearWalkNavigationState(true);
                architect.clearCommittedWalk();
                architect.resetWalkStuckTracker();
                double horizontalTargetDelta = Math.sqrt(
                        (target.getX() - architect.getX()) * (target.getX() - architect.getX())
                                + (target.getZ() - architect.getZ()) * (target.getZ() - architect.getZ()));
                boolean targetDirectlyBelow = target.getY() < architect.getY() - 1.0
                        && horizontalTargetDelta <= 2.5;
                if (targetDirectlyBelow) {
                    BlockPos dropInTarget = architect.findDropInBreakTarget(target, step.pos());
                    if (dropInTarget != null) {
                        blockBreaker.setTarget(dropInTarget);
                        approachState.ceilingBreachPos = dropInTarget;
                        architect.getNavigation().stop();
                        LOGGER.info("[Architect] Prefer drop-in over bridge: digging {}", dropInTarget);
                        break;
                    }
                }

                BlockPos supportPos = step.pos().below();
                boolean descendingBridgeStep = step.pos().getY() < architect.blockPosition().getY();
                if (!architect.level().getBlockState(supportPos).isSolid()
                        && !targetDirectlyBelow
                        && !descendingBridgeStep
                        && architect.onGround()
                        && architect.getScaffoldIceCount() < architect.getMaxScaffoldIce()) {
                    if (architect.placeScaffoldIce(supportPos)) {
                        LOGGER.info("[Architect] D* BRIDGE ice at {} for {}", supportPos, step.pos());
                    }
                }
                architect.getMoveControl().setWantedPosition(
                        step.pos().getX() + 0.5,
                        step.pos().getY(),
                        step.pos().getZ() + 0.5,
                        1.0
                );
            }
            case DIG_DOWN -> {
                architect.clearWalkNavigationState(true);
                architect.clearCommittedWalk();
                architect.resetWalkStuckTracker();
                BlockPos digTarget = step.breakTarget();
                if (digTarget != null && architect.isBreakableBlock(digTarget)) {
                    blockBreaker.setTarget(digTarget);
                    architect.teleportTo(digTarget.getX() + 0.5, architect.getY(), digTarget.getZ() + 0.5);
                    architect.getNavigation().stop();
                    approachState.ceilingBreachPos = digTarget;
                    LOGGER.info("[Architect] D* DIG DOWN at {} ({})",
                            digTarget,
                            architect.level().getBlockState(digTarget).getBlock());
                }
            }
        }

        if (architect.tickCount % 20 == 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] action=APPROACH dist={} mining={} ice={}/{} pos={} step={} cells={}",
                    String.format("%.1f", architect.distanceTo(target)),
                    blockBreaker.isMining(),
                    architect.getScaffoldIceCount(),
                    architect.getMaxScaffoldIce(),
                    architect.blockPosition(),
                    step.type(),
                    approachState.dstar.getCellCount());
        }
    }

    private void executeFallbackChase(LivingEntity target, double speed, int repathCooldownTicks, boolean assistLiquidAscent) {
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

    private boolean isVerticalClimbStep(DStarLitePathfinder.NextStep step) {
        BlockPos current = architect.blockPosition();
        BlockPos next = step.pos();
        if (next.getX() != current.getX() || next.getZ() != current.getZ() || next.getY() == current.getY()) {
            return false;
        }
        BlockState currentState = architect.level().getBlockState(current);
        BlockState nextState = architect.level().getBlockState(next);
        return currentState.is(BlockTags.CLIMBABLE) || nextState.is(BlockTags.CLIMBABLE);
    }

    private void resolveScaffoldStep(BlockPos scaffoldTarget) {
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

    private boolean isPassableForStand(BlockPos pos, Level level) {
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.WOODEN_DOORS)) {
            return true;
        }
        return !ArchitectBreakPolicy.isObstructiveForArchitect(state, level, pos);
    }

    @Nullable
    private BlockPos selectScaffoldObstruction(BlockPos scaffoldTarget, Level level) {
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

    private boolean shouldUseDirectChase(LivingEntity target, DStarLitePathfinder.NextStep step) {
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
        if (isVerticalClimbStep(step)) {
            return false;
        }

        BlockState currentState = architect.level().getBlockState(current);
        BlockState nextState = architect.level().getBlockState(next);
        return !currentState.is(BlockTags.CLIMBABLE) && !nextState.is(BlockTags.CLIMBABLE);
    }

    private void executeVerticalClimbStep(DStarLitePathfinder.NextStep step) {
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
}
