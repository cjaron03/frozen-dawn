package com.frozendawn.entity;

import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Owns D* Lite initialization, incremental planning, and step retrieval for approach mode.
 */
final class ArchitectApproachPlanningSupport {

    private static final Logger LOGGER = LogUtils.getLogger();
    // Keep D* planning responsive without allowing large single-tick bursts that hitch the server.
    private static final int APPROACH_REINIT_COMPUTE_BUDGET = 900;
    private static final int APPROACH_INCOMPLETE_COMPUTE_BUDGET = 360;
    private static final int APPROACH_STEADY_COMPUTE_BUDGET = 180;
    private static final int APPROACH_REINIT_FOLLOWUP_COMPUTE_BUDGET = 160;
    private static final int PLAN_BLOCKED_LOG_INTERVAL_TICKS = 40;
    private static final int PLAN_DISTANCE_GATED_LOG_INTERVAL_TICKS = 40;
    private static final double APPROACH_DSTAR_ENGAGE_RANGE = 48.0;
    private static final double APPROACH_DSTAR_DISENGAGE_RANGE = 56.0;

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;
    private final double planningFallbackSpeed;
    private final int planningFallbackRepathTicks;
    private boolean planBlockedActive;
    private int planBlockedTicks;
    private String lastPlanBlockedReason = "NONE";
    private boolean planDistanceGatedActive;
    private int planDistanceGatedTicks;

    ArchitectApproachPlanningSupport(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            double planningFallbackSpeed,
            int planningFallbackRepathTicks
    ) {
        this.architect = architect;
        this.approachState = approachState;
        this.planningFallbackSpeed = planningFallbackSpeed;
        this.planningFallbackRepathTicks = planningFallbackRepathTicks;
    }

    void precomputeDuringObserve(LivingEntity target) {
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

    boolean ensurePlanReadyOrFallback(LivingEntity target, BlockPos targetPos) {
        double targetDistance = architect.distanceTo(target);
        if (!shouldRunDStarPlanning(targetDistance)) {
            boolean hadPlannerState = approachState.dstar.isInitialized();
            if (hadPlannerState) {
                approachState.dstar.cleanup();
                approachState.dstarPrecomputed = false;
            }
            clearPlanBlockedTracking();
            logPlanDistanceGated(targetDistance, hadPlannerState);
            executePlanningFallbackChase(target);
            return false;
        }
        logPlanDistanceGateReleased(targetDistance);

        boolean reinitializedThisTick = false;
        if (approachState.dstar.needsReinitialize(targetPos)) {
            approachState.dstar.setSurfaceY(approachState.surfaceY);
            approachState.dstar.initialize(targetPos, architect.blockPosition(), architect.level());
            approachState.dstar.computePartial(APPROACH_REINIT_COMPUTE_BUDGET, architect.level());
            reinitializedThisTick = true;
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
            int incompleteBudget = reinitializedThisTick
                    ? APPROACH_REINIT_FOLLOWUP_COMPUTE_BUDGET
                    : APPROACH_INCOMPLETE_COMPUTE_BUDGET;
            approachState.dstar.computePartial(incompleteBudget, architect.level());
            if (!approachState.dstar.isSearchComplete()) {
                logPlanBlocked(
                        reinitializedThisTick
                                ? "SEARCH_INCOMPLETE_AFTER_REINIT"
                                : "SEARCH_INCOMPLETE",
                        target,
                        incompleteBudget,
                        reinitializedThisTick);
                executePlanningFallbackChase(target);
                return false;
            }
        }

        // Reinitialize already seeds start to current position, so skip an extra
        // maintenance pass in that same tick to avoid compute spikes.
        if (reinitializedThisTick) {
            return true;
        }

        approachState.dstar.updateStart(architect.blockPosition());
        if (!approachState.dstar.computePartial(APPROACH_STEADY_COMPUTE_BUDGET, architect.level())) {
            logPlanBlocked(
                    "SEARCH_NOT_STABLE_AFTER_START_UPDATE",
                    target,
                    APPROACH_STEADY_COMPUTE_BUDGET,
                    reinitializedThisTick);
            executePlanningFallbackChase(target);
            return false;
        }

        logPlanReady(target);
        return true;
    }

    DStarLitePathfinder.NextStep getNextStep(@Nullable BlockPos avoidImmediateBacktrack) {
        return approachState.dstar.getNextStep(architect.blockPosition(), architect.level(), avoidImmediateBacktrack);
    }

    private void executePlanningFallbackChase(LivingEntity target) {
        ArchitectApproachMovementSupport.executeFallbackChase(
                architect,
                approachState,
                target,
                planningFallbackSpeed,
                planningFallbackRepathTicks,
                false);
    }

    private void logPlanBlocked(
            String reason,
            LivingEntity target,
            int budgetUsed,
            boolean reinitializedThisTick
    ) {
        planBlockedTicks++;
        boolean shouldLog = !planBlockedActive
                || !reason.equals(lastPlanBlockedReason)
                || planBlockedTicks % PLAN_BLOCKED_LOG_INTERVAL_TICKS == 0;
        if (shouldLog) {
            LOGGER.info("[Architect][DStarDiag] event=APPROACH_PLAN_BLOCKED reason={} blockedTicks={} cellCount={} searchComplete={} initialized={} targetDistance={} reinitializedThisTick={} budget={} action={} transitionSource={}",
                    reason,
                    planBlockedTicks,
                    approachState.dstar.getCellCount(),
                    approachState.dstar.isSearchComplete(),
                    approachState.dstar.isInitialized(),
                    String.format("%.2f", architect.distanceTo(target)),
                    reinitializedThisTick,
                    budgetUsed,
                    ArchitectEntity.actionName(architect.getBrainAction()),
                    approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE");
        }
        planBlockedActive = true;
        lastPlanBlockedReason = reason;
    }

    private void logPlanReady(LivingEntity target) {
        if (!planBlockedActive) {
            return;
        }
        LOGGER.info("[Architect][DStarDiag] event=APPROACH_PLAN_READY blockedTicks={} cellCount={} searchComplete={} initialized={} targetDistance={} action={} transitionSource={}",
                planBlockedTicks,
                approachState.dstar.getCellCount(),
                approachState.dstar.isSearchComplete(),
                approachState.dstar.isInitialized(),
                String.format("%.2f", architect.distanceTo(target)),
                ArchitectEntity.actionName(architect.getBrainAction()),
                approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE");
        planBlockedActive = false;
        planBlockedTicks = 0;
        lastPlanBlockedReason = "NONE";
    }

    private boolean shouldRunDStarPlanning(double targetDistance) {
        if (approachState.dstar.isInitialized()) {
            return targetDistance <= APPROACH_DSTAR_DISENGAGE_RANGE;
        }
        return targetDistance <= APPROACH_DSTAR_ENGAGE_RANGE;
    }

    private void logPlanDistanceGated(double targetDistance, boolean hadPlannerState) {
        planDistanceGatedTicks++;
        boolean shouldLog = !planDistanceGatedActive
                || planDistanceGatedTicks % PLAN_DISTANCE_GATED_LOG_INTERVAL_TICKS == 0;
        if (shouldLog) {
            LOGGER.info("[Architect][DStarDiag] event=APPROACH_PLAN_DISTANCE_GATED gatedTicks={} targetDistance={} engageRange={} disengageRange={} initialized={} hadPlannerState={} action={} transitionSource={}",
                    planDistanceGatedTicks,
                    String.format("%.2f", targetDistance),
                    String.format("%.1f", APPROACH_DSTAR_ENGAGE_RANGE),
                    String.format("%.1f", APPROACH_DSTAR_DISENGAGE_RANGE),
                    approachState.dstar.isInitialized(),
                    hadPlannerState,
                    ArchitectEntity.actionName(architect.getBrainAction()),
                    approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE");
        }
        planDistanceGatedActive = true;
    }

    private void logPlanDistanceGateReleased(double targetDistance) {
        if (!planDistanceGatedActive) {
            return;
        }
        LOGGER.info("[Architect][DStarDiag] event=APPROACH_PLAN_DISTANCE_GATE_RELEASED gatedTicks={} targetDistance={} engageRange={} disengageRange={} action={} transitionSource={}",
                planDistanceGatedTicks,
                String.format("%.2f", targetDistance),
                String.format("%.1f", APPROACH_DSTAR_ENGAGE_RANGE),
                String.format("%.1f", APPROACH_DSTAR_DISENGAGE_RANGE),
                ArchitectEntity.actionName(architect.getBrainAction()),
                approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE");
        planDistanceGatedActive = false;
        planDistanceGatedTicks = 0;
    }

    private void clearPlanBlockedTracking() {
        planBlockedActive = false;
        planBlockedTicks = 0;
        lastPlanBlockedReason = "NONE";
    }
}
