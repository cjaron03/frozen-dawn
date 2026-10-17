package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectApproachRecovery;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
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

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;
    private final ArchitectBlockBreaker blockBreaker;
    private final ArchitectApproachPlanningSupport planningSupport;
    private final ArchitectApproachStepDispatchSupport stepDispatchSupport;

    ArchitectApproachController(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker
    ) {
        this.architect = architect;
        this.approachState = approachState;
        this.blockBreaker = blockBreaker;
        this.planningSupport = new ArchitectApproachPlanningSupport(
                architect,
                approachState,
                PLANNING_FALLBACK_SPEED,
                PLANNING_FALLBACK_REPATH_TICKS);
        this.stepDispatchSupport = new ArchitectApproachStepDispatchSupport(
                architect,
                approachState,
                blockBreaker,
                PLANNING_FALLBACK_SPEED,
                PLANNING_FALLBACK_REPATH_TICKS);
    }

    void precomputeDStarDuringObserve(LivingEntity target) {
        planningSupport.precomputeDuringObserve(target);
    }

    void executeApproach(@Nullable LivingEntity target) {
        approachState.sprintRequested = false;
        if (target == null) {
            approachState.openRouteAfterBreak = null;
            approachState.openRouteSubject = null;
            approachState.unreachableTicks = 0;
            architect.approachLastKnownPos();
            return;
        }

        String giveUpReason = ArchitectApproachRecovery.tick(approachState, target.getUUID(), architect.position());
        if (giveUpReason != null) {
            architect.abandonApproach(target, giveUpReason);
            return;
        }
        if (approachState.approachNoProgressTicks % 100 == 0) {
            LOGGER.info("[Architect] APPROACH_NO_PROGRESS entity={} pos={} ticks={} reinits={} mining={} onGround={} collision={} planComplete={} walkStuckTicks={}",
                    architect.getId(), architect.blockPosition(), approachState.approachNoProgressTicks,
                    approachState.unstickReinitAttempts, blockBreaker.isMining(), architect.onGround(),
                    architect.horizontalCollision, approachState.dstar.isSearchComplete(), approachState.walkStuckTicks);
        }

        logApproachEntryIfNeeded(target);

        architect.recordWalkCellHistory();

        // Proactively open nearby doors and fence gates before movement dispatch.
        architect.keepNearbyPassagesOpen();

        if (architect.tryApproachProgressRecovery(target)) {
            return;
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

        if (ArchitectApproachBreakSupport.continueOpenRoute(architect, approachState, blockBreaker, target)
                || ArchitectApproachBreakSupport.cancelBreakForOpenRoute(architect, approachState, blockBreaker, target)) {
            return;
        }

        if (blockBreaker.isMining() && ArchitectApproachBreakSupport.continueBreaking(
                architect,
                approachState,
                blockBreaker,
                target)) {
            return;
        }

        if (blockBreaker.hasTarget()) {
            BlockPos bt = blockBreaker.getTarget();
            if (bt != null && architect.level().getBlockState(bt).isAir()) {
                blockBreaker.clearTarget();
                if (bt.equals(approachState.ceilingBreachPos)) {
                    approachState.ceilingBreachPos = null;
                }
            }
        }

        if (!blockBreaker.hasTarget() && approachState.scaffoldTarget == null && approachState.stepOffTarget == null
                && ArchitectApproachMovementSupport.tryFollowOpenDescent(architect, target)) {
            return;
        }

        // A target pressed against the far side of a wall must produce a doorway,
        // not a climb-over route or a no-LOS melee handoff.
        if (ArchitectApproachBreakSupport.tryStartContactBreach(
                architect, approachState, blockBreaker, target)) {
            return;
        }

        // Scaffold pacing: wait after placing ice, then jump up.
        if (ArchitectApproachMovementSupport.tickPendingScaffold(architect, approachState, blockBreaker)) {
            return;
        }

        // Handle smooth step-off lerp.
        if (ArchitectApproachMovementSupport.tickStepOffLerp(architect, approachState)) {
            return;
        }

        if (architect.shouldPreferMeleeOverApproach(target)) {
            architect.primeMeleeHandoff();
            architect.resetReevalCooldown();
        }

        BlockPos targetPos = target.blockPosition();
        if (planningSupport.needsGoalRefresh(targetPos)) {
            // Retire the old target's corridor before it can turn us around that spot.
            architect.clearCommittedWalk();
            architect.clearWalkNavigationState(true);
            architect.getLookControl().setLookAt(target, 35.0F, 25.0F);
        } else if (architect.tryContinueCommittedWalk(target)) {
            return;
        }

        if (!planningSupport.ensurePlanReadyOrFallback(target, targetPos)) {
            return;
        }

        BlockPos avoidImmediateBacktrack = architect.getImmediateBacktrackPos();
        DStarLitePathfinder.NextStep step = planningSupport.getNextStep(avoidImmediateBacktrack);
        // Reusing an approximate goal is cheap while walking. Before changing terrain,
        // resolve the route to the current locally perceived target instead of building
        // toward a position the player already left.
        if (planningSupport.hasOutdatedConstructionGoal(step, targetPos)) {
            if (!planningSupport.refreshConstructionPlan(target, targetPos)) return;
            step = planningSupport.getNextStep(avoidImmediateBacktrack);
        }
        architect.recordStep(step);
        architect.keepPassageOpenNear(step.pos());

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
            stepDispatchSupport.handleUnreachableStep(target, targetPos);
            return;
        }
        approachState.unreachableTicks = 0;

        if (stepDispatchSupport.continueQueuedBreakTarget()) {
            return;
        }

        stepDispatchSupport.executeStep(step, target);

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

    private void logApproachEntryIfNeeded(LivingEntity target) {
        if (approachState.dstarApproachEntryLogged) {
            return;
        }

        BlockPos targetPos = target.blockPosition();
        boolean initialized = approachState.dstar.isInitialized();
        boolean willReinitialize = approachState.dstar.needsReinitialize(targetPos);
        boolean reuseExisting = initialized && !willReinitialize;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect][DStarDiag] event=APPROACH_ENTRY cellCount={} searchComplete={} targetDistance={} initialized={} action={} transitionSource={} reuseExistingSearch={} willReinitialize={}",
                    approachState.dstar.getCellCount(),
                    approachState.dstar.isSearchComplete(),
                    String.format("%.2f", architect.distanceTo(target)),
                    initialized,
                    ArchitectEntity.actionName(architect.getBrainAction()),
                    resolveTransitionSource(),
                    reuseExisting,
                    willReinitialize);
        }

        approachState.dstarApproachEntryLogged = true;
    }

    private String resolveTransitionSource() {
        return approachState.dstarTransitionSource != null
                ? approachState.dstarTransitionSource
                : "UNKNOWN_OR_NON_OBSERVE";
    }
}
