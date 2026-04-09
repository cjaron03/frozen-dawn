package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectApproachState;
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
    private static final int MAX_REPEAT_UNSTICK_BREAK_ATTEMPTS = 3;

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

    boolean handleWalkStuck(
            BlockPos stepPos,
            @Nullable LivingEntity target,
            Predicate<BlockPos> isLastResortBreakBlock
    ) {
        if (approachState.walkStuckTicks < WALK_STUCK_BREAK_TICKS || blockBreaker.hasTarget()) {
            return false;
        }
        if (attemptWalkUnstickBreak(stepPos, isLastResortBreakBlock)) {
            return true;
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
            approachState.walkStuckTicks = 0;
            LOGGER.info("[Architect] WALK stuck-trigger replan: refreshed D* around {}", architect.blockPosition());
            return true;
        }
        return false;
    }

    private boolean attemptWalkUnstickBreak(
            BlockPos stepPos,
            Predicate<BlockPos> isLastResortBreakBlock
    ) {
        BlockPos blockedCandidate = approachState.repeatedUnstickBreakAttempts >= MAX_REPEAT_UNSTICK_BREAK_ATTEMPTS
                ? approachState.lastUnstickBreakCandidate
                : null;

        BlockPos from = architect.blockPosition();
        Direction toward = corridorSupport.getPrimaryHorizontalDirection(from, stepPos);
        Set<BlockPos> immediateCandidates = ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, toward);
        BlockPos candidate = ArchitectWalkBreakPlanner.selectPreferredBreakCandidate(
                immediateCandidates,
                blockedCandidate,
                architect::isBreakableBlock,
                isLastResortBreakBlock);
        if (candidate != null) {
            if (candidate.equals(approachState.lastUnstickBreakCandidate)) {
                approachState.repeatedUnstickBreakAttempts++;
            } else {
                approachState.lastUnstickBreakCandidate = candidate.immutable();
                approachState.repeatedUnstickBreakAttempts = 1;
            }
            blockBreaker.setTarget(candidate);
            LOGGER.info("[Architect] WALK stuck: breaking {} to unjam move toward {}", candidate, stepPos);
            return true;
        }

        if (!approachState.committedWalkCorridor.isEmpty()) {
            int fromIndex = Math.max(0, Math.min(approachState.committedWalkCorridorIndex, approachState.committedWalkCorridor.size()));
            BlockPos corridorBreakTarget = ArchitectWalkBreakPlanner.findCorridorBreakTarget(
                    approachState.committedWalkCorridor.subList(fromIndex, approachState.committedWalkCorridor.size()),
                    architect::isBreakableBlock,
                    isLastResortBreakBlock);
            if (corridorBreakTarget != null
                    && (blockedCandidate == null || !blockedCandidate.equals(corridorBreakTarget))) {
                if (corridorBreakTarget.equals(approachState.lastUnstickBreakCandidate)) {
                    approachState.repeatedUnstickBreakAttempts++;
                } else {
                    approachState.lastUnstickBreakCandidate = corridorBreakTarget.immutable();
                    approachState.repeatedUnstickBreakAttempts = 1;
                }
                blockBreaker.setTarget(corridorBreakTarget);
                LOGGER.info("[Architect] WALK stuck: breaking corridor obstruction {} while following {}",
                        corridorBreakTarget, stepPos);
                return true;
            }
        }
        return false;
    }
}
