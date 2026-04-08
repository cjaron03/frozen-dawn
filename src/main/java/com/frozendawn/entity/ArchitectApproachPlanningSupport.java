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

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;
    private final double planningFallbackSpeed;
    private final int planningFallbackRepathTicks;

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
                executePlanningFallbackChase(target);
                return false;
            }
        }

        approachState.dstar.updateStart(architect.blockPosition());
        if (!approachState.dstar.computePartial(220, architect.level())) {
            executePlanningFallbackChase(target);
            return false;
        }

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
}
