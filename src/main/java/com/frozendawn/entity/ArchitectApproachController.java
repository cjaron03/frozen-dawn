package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
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
            if (!approachState.dstar.isSearchComplete()) {
                approachState.dstar.computePartial(approachState.dstarPrecomputed ? 120 : 250, architect.level());
            }
        }

        if (approachState.dstar.isSearchComplete()) {
            approachState.dstarPrecomputed = true;
        }
    }

    void executeApproach(@Nullable LivingEntity target) {
        if (target == null) {
            approachState.unreachableTicks = 0;
            architect.approachLastKnownPos();
            return;
        }

        architect.recordWalkCellHistory();

        // Proactively open nearby wooden doors before movement dispatch.
        architect.keepNearbyWoodenDoorsOpen();

        // Scaffold pacing: wait after placing ice, then jump up
        if (approachState.scaffoldTarget != null) {
            approachState.scaffoldDelay--;
            architect.getLookControl().setLookAt(architect.getX(), architect.getY() - 1, architect.getZ());
            if (approachState.scaffoldDelay <= 0) {
                architect.teleportTo(
                        approachState.scaffoldTarget.getX() + 0.5,
                        approachState.scaffoldTarget.getY(),
                        approachState.scaffoldTarget.getZ() + 0.5
                );
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

        if (architect.canDirectChaseApproach(target)) {
            architect.executeDirectApproachChase(target);
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
                return;
            }
        }

        approachState.dstar.updateStart(architect.blockPosition());
        if (!approachState.dstar.isSearchComplete()) {
            approachState.dstar.computePartial(200, architect.level());
            if (!approachState.dstar.isSearchComplete()) {
                return;
            }
        }

        BlockPos avoidImmediateBacktrack = architect.getImmediateBacktrackPos();
        DStarLitePathfinder.NextStep step =
                approachState.dstar.getNextStep(architect.blockPosition(), architect.level(), avoidImmediateBacktrack);
        architect.keepDoorOpenNear(step.pos());

        architect.invalidateStaleApproachBreakTarget(step, target);

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
                    BlockPos feetPos = architect.blockPosition();
                    architect.placeScaffoldIce(feetPos);
                    approachState.scaffoldTarget = step.pos();
                    approachState.scaffoldDelay = ArchitectEntity.SCAFFOLD_PLACE_TICKS;
                    LOGGER.info("[Architect] D* SCAFFOLD ice at {} -> {} (waiting {}t)",
                            feetPos,
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
}
