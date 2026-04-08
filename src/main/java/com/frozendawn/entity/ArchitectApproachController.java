package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectBreachPlanner;
import com.frozendawn.init.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
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
    private static final int FALLBACK_BREAK_COOLDOWN_TICKS = 10;

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
            ArchitectApproachMovementSupport.executeFallbackChase(
                    architect,
                    approachState,
                    target,
                    LIQUID_ESCAPE_SPEED,
                    LIQUID_ESCAPE_REPATH_TICKS,
                    true);
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
                ArchitectApproachMovementSupport.resolveScaffoldStep(
                        architect,
                        approachState,
                        blockBreaker,
                        scaffoldTarget);
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

        if (blockBreaker.isMining() && continueBreaking(target)) {
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
                ArchitectApproachMovementSupport.executeFallbackChase(
                        architect,
                        approachState,
                        target,
                        PLANNING_FALLBACK_SPEED,
                        PLANNING_FALLBACK_REPATH_TICKS,
                        false);
                return;
            }
        }

        approachState.dstar.updateStart(architect.blockPosition());
        if (!approachState.dstar.computePartial(220, architect.level())) {
            ArchitectApproachMovementSupport.executeFallbackChase(
                    architect,
                    approachState,
                    target,
                    PLANNING_FALLBACK_SPEED,
                    PLANNING_FALLBACK_REPATH_TICKS,
                    false);
            return;
        }

        BlockPos avoidImmediateBacktrack = architect.getImmediateBacktrackPos();
        DStarLitePathfinder.NextStep step =
                approachState.dstar.getNextStep(architect.blockPosition(), architect.level(), avoidImmediateBacktrack);
        architect.keepDoorOpenNear(step.pos());

        if (ArchitectApproachMovementSupport.shouldUseDirectChase(architect, target, step)) {
            architect.executeDirectApproachChase(target);
            return;
        }

        architect.invalidateStaleApproachBreakTarget(step, target);

        if (step.type() == DStarLitePathfinder.StepType.WALK
                && ArchitectApproachMovementSupport.isVerticalClimbStep(architect, step)) {
            ArchitectApproachMovementSupport.executeVerticalClimbStep(architect, approachState, step);
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
                fallbackWallBreak(target);
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
                ArchitectApproachMovementSupport.executeFallbackChase(
                        architect,
                        approachState,
                        target,
                        PLANNING_FALLBACK_SPEED,
                        PLANNING_FALLBACK_REPATH_TICKS,
                        false);
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
                    breakTarget = findBreakableWallBlock(target);
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
                    BlockPos dropInTarget = findDropInBreakTarget(target, step.pos());
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

    private boolean continueBreaking(LivingEntity target) {
        BlockPos bt = blockBreaker.getTarget();
        if (bt == null) {
            return false;
        }

        double blockDist = architect.position().distanceToSqr(
                bt.getX() + 0.5, bt.getY() + 0.5, bt.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            architect.getNavigation().stop();
            architect.getLookControl().setLookAt(bt.getX() + 0.5, bt.getY() + 0.5, bt.getZ() + 0.5);
            boolean broke = blockBreaker.tick();
            if (broke) {
                architect.resetUnstickBreakTracker();

                // Ceiling breach drop-through: teleport into the new opening.
                if (approachState.ceilingBreachPos != null && bt.equals(approachState.ceilingBreachPos)) {
                    architect.teleportTo(bt.getX() + 0.5, bt.getY(), bt.getZ() + 0.5);
                    architect.getNavigation().stop();
                    approachState.ceilingBreachPos = null;
                    architect.clearCommittedWalk();
                    architect.playSound(ModSounds.ARCHITECT_LAND.get(), 0.8f, 0.7f + architect.nextRandomFloat() * 0.3f);
                    LOGGER.info("[Architect] Ceiling breach complete — dropping through {}", bt);
                    architect.setPathRecalcCooldown(0);
                    approachState.dstar.onLocalBlockChanged(bt, architect.level());
                    architect.triggerReeval();
                    return true;
                }

                architect.setPathRecalcCooldown(0);
                approachState.dstar.onLocalBlockChanged(bt, architect.level());

                // Chain headroom breach to restore 2-block clearance.
                BlockPos above = bt.above();
                if (above.getY() <= architect.blockPosition().getY() + 1
                        && architect.isBreakableBlock(above)
                        && architect.shouldContinueApproachBreak(target, above)) {
                    blockBreaker.setTarget(above);
                    LOGGER.info("[Architect] Chaining headroom break at {}", above);
                    return true;
                }

                architect.triggerReeval();
            }
            return true;
        }

        blockBreaker.clearTarget();
        return false;
    }

    private void fallbackWallBreak(LivingEntity target) {
        if (approachState.fallbackBreakCooldown > 0) {
            return;
        }
        BlockPos wallBlock = findBreakableWallBlock(target);
        if (wallBlock == null) {
            return;
        }

        if (wallBlock.equals(approachState.lastFallbackBreakPos)
                && !architect.level().getBlockState(wallBlock).isAir()
                && !blockBreaker.hasTarget()) {
            approachState.fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
            return;
        }

        double blockDist = architect.position().distanceToSqr(
                wallBlock.getX() + 0.5, wallBlock.getY() + 0.5, wallBlock.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            blockBreaker.setTarget(wallBlock);
            approachState.lastFallbackBreakPos = wallBlock.immutable();
            approachState.fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
            return;
        }

        architect.getNavigation().moveTo(
                wallBlock.getX() + 0.5,
                wallBlock.getY(),
                wallBlock.getZ() + 0.5,
                1.0);
        architect.setPathRecalcCooldown(5);
        approachState.lastFallbackBreakPos = wallBlock.immutable();
        approachState.fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
    }

    @Nullable
    private BlockPos findDropInBreakTarget(@Nullable LivingEntity target, BlockPos stepPos) {
        return ArchitectBreachPlanner.findDropInBreakTarget(
                architect,
                target,
                stepPos,
                architect::isBreakableBlock);
    }

    @Nullable
    private BlockPos findBreakableWallBlock(@Nullable LivingEntity target) {
        return ArchitectBreachPlanner.findBreakableWallBlock(
                architect,
                target,
                architect::isBreakableBlock);
    }
}
