package com.frozendawn.entity;

import com.frozendawn.entity.architect.BreakChoice;
import com.frozendawn.entity.architect.BreakReason;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectApproachRecovery;
import com.frozendawn.entity.architect.ArchitectWalkBreakPlanner;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Owns approach stuck recovery and unstick break candidate handling.
 */
final class ArchitectApproachUnstickSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int WALK_STUCK_BREAK_TICKS = 16;
    private static final int WALK_STUCK_REINIT_TICKS = 48;

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;
    private final ArchitectBlockBreaker blockBreaker;
    private final ArchitectApproachCorridorSupport corridorSupport;

    ArchitectApproachUnstickSupport(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker,
            ArchitectApproachCorridorSupport corridorSupport
    ) {
        this.architect = architect;
        this.approachState = approachState;
        this.blockBreaker = blockBreaker;
        this.corridorSupport = corridorSupport;
    }

    int walkStuckBreakTicks() {
        return WALK_STUCK_BREAK_TICKS;
    }

    boolean tryProgressRecovery(LivingEntity target, Predicate<BlockPos> isLastResortBreakBlock) {
        if (!ArchitectApproachRecovery.shouldTryLocalRecovery(approachState, blockBreaker.hasTarget())) {
            return false;
        }
        boolean forceReplan = approachState.approachNoProgressTicks
                % ArchitectApproachRecovery.LOCAL_REPLAN_INTERVAL_TICKS == 0;
        // Allow short travel below the two-block progress anchor. After the
        // replan deadline, velocity can be a circle rather than actual escape.
        if (!forceReplan && !architect.horizontalCollision
                && architect.getDeltaMovement().horizontalDistanceSqr() >= 0.0025) {
            return false;
        }
        BlockPos stepPos = architect.getCommittedWalkSteeringTarget();
        if (stepPos == null && approachState.dstar.isSearchComplete()) {
            DStarLitePathfinder.NextStep step = approachState.dstar.peekNextStep(
                    architect.blockPosition(), architect.level());
            if (step.type() != DStarLitePathfinder.StepType.UNREACHABLE) {
                stepPos = step.pos();
            }
        }
        if (stepPos == null) {
            stepPos = target.blockPosition();
        }
        approachState.walkStuckTicks = Math.max(approachState.walkStuckTicks,
                forceReplan ? WALK_STUCK_REINIT_TICKS : WALK_STUCK_BREAK_TICKS);
        LOGGER.info("[Architect] APPROACH_LOCAL_RECOVERY entity={} pos={} step={} noProgressTicks={} replan={}",
                architect.getId(), architect.blockPosition(), stepPos, approachState.approachNoProgressTicks, forceReplan);
        architect.recordDecision("LOCAL_RECOVERY", null, "forceReplan=" + forceReplan);
        boolean handled = handleWalkStuck(stepPos, target, isLastResortBreakBlock);
        if (blockBreaker.hasTarget()) {
            architect.clearWalkNavigationState(true);
            architect.clearCommittedWalk();
            // Start mining now, before a fallback/planning early return can discard it.
            architect.walkToBreakTarget();
        }
        return handled;
    }

    boolean handleWalkStuck(
            BlockPos stepPos,
            @Nullable LivingEntity target,
            Predicate<BlockPos> isLastResortBreakBlock
    ) {
        if (approachState.walkStuckTicks < WALK_STUCK_BREAK_TICKS || blockBreaker.hasTarget()) {
            return false;
        }
        if (approachState.walkStuckTicks >= WALK_STUCK_REINIT_TICKS && target != null) {
            approachState.dstar.onLocalBlockChanged(
                    architect.blockPosition(),
                    architect.level(),
                    "APPROACH_LOCAL_RESEED",
                    ArchitectEntity.actionName(architect.getBrainAction()),
                    approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE",
                    architect.distanceTo(target)
            );
            approachState.dstar.setSurfaceY(approachState.surfaceY);
            approachState.dstar.initialize(target.blockPosition(), architect.blockPosition(), architect.level());
            approachState.dstar.computePartial(1000, architect.level());
            architect.clearCommittedWalk();
            architect.resetWalkStuckTracker();
            ArchitectApproachRecovery.recordReinit(approachState);
            architect.recordDecision("REINIT", null, "WALK_STUCK");
            LOGGER.info("[Architect] WALK stuck-trigger replan: refreshed D* around {}", architect.blockPosition());
            return true;
        }
        return attemptWalkUnstickBreak(stepPos, isLastResortBreakBlock);
    }

    private boolean attemptWalkUnstickBreak(
            BlockPos stepPos,
            Predicate<BlockPos> isLastResortBreakBlock
    ) {
        BlockPos from = architect.blockPosition();
        Direction toward = corridorSupport.getPrimaryHorizontalDirection(from, stepPos);
        BreakChoice candidate = architect.chooseBreak("UNSTICK",
                ArchitectWalkBreakPlanner.unstickChoices(from, stepPos, toward), isLastResortBreakBlock);
        if (candidate != null) {
            blockBreaker.setChoice(candidate);
            LOGGER.info("[Architect] WALK stuck: breaking {} to unjam move toward {}", candidate, stepPos);
            return true;
        }

        if (!approachState.committedWalkCorridor.isEmpty()) {
            int fromIndex = Math.max(0, Math.min(approachState.committedWalkCorridorIndex, approachState.committedWalkCorridor.size()));
            BreakChoice corridorBreakTarget = architect.chooseBreak("UNSTICK_CORRIDOR",
                    ArchitectWalkBreakPlanner.corridorChoices(approachState.committedWalkCorridor.subList(
                            fromIndex, approachState.committedWalkCorridor.size())), isLastResortBreakBlock);
            if (corridorBreakTarget != null) {
                blockBreaker.setChoice(corridorBreakTarget);
                LOGGER.info("[Architect] WALK stuck: breaking corridor obstruction {} while following {}",
                        corridorBreakTarget, stepPos);
                return true;
            }
        }
        return false;
    }
}
