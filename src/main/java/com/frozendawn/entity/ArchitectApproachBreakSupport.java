package com.frozendawn.entity;

import com.frozendawn.entity.architect.BreakChoice;
import com.frozendawn.entity.architect.BreakReason;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectBreachPlanner;
import com.frozendawn.init.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Break/mining helpers used by approach orchestration.
 */
final class ArchitectApproachBreakSupport {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FALLBACK_BREAK_COOLDOWN_TICKS = 10;

    private ArchitectApproachBreakSupport() {
    }

    static boolean tryStartContactBreach(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker,
            LivingEntity target
    ) {
        double horizontalDistance = architect.horizontalDistanceTo(target);
        double verticalDistance = architect.verticalDistanceTo(target);
        if (!ArchitectBreachPlanner.shouldAttemptContactBreach(
                architect.hasLineOfSight(target),
                blockBreaker.hasTarget(),
                horizontalDistance,
                verticalDistance)) {
            return false;
        }

        BlockPos wallBlock = ArchitectBreachPlanner.findDirectWallBlock(
                architect,
                target,
                architect::isBreakableBlock);
        if (wallBlock == null || architect.position().distanceToSqr(
                wallBlock.getX() + 0.5D,
                wallBlock.getY() + 0.5D,
                wallBlock.getZ() + 0.5D) > 4.5D * 4.5D) {
            return false;
        }

        approachState.scaffoldTarget = null;
        approachState.scaffoldDelay = 0;
        architect.clearWalkNavigationState(true);
        architect.clearCommittedWalk();
        architect.resetWalkStuckTracker();
        blockBreaker.setChoice(new BreakChoice(wallBlock, BreakReason.CONTACT_BREACH));
        LOGGER.info("[Architect] CONTACT BREACH at {} toward enclosed target", wallBlock);
        architect.walkToBreakTarget();
        return true;
    }

    /** Let an accepted detour run instead of dispatching the old D* breach on the next tick. */
    static boolean continueOpenRoute(ArchitectEntity architect, ArchitectApproachState state,
            ArchitectBlockBreaker breaker, LivingEntity target) {
        var path = state.openRouteAfterBreak;
        if (path == null) return false;
        var navigation = architect.getNavigation();
        boolean targetChanged = !target.getUUID().equals(state.openRouteSubject)
                || !target.blockPosition().equals(path.getTarget());
        if (breaker.hasTarget() || targetChanged || navigation.getPath() != path || !navigation.isInProgress()) {
            state.openRouteAfterBreak = null;
            state.openRouteSubject = null;
            if (targetChanged) {
                if (navigation.getPath() == path) navigation.stop();
                state.dstar.initialize(target.blockPosition(), architect.blockPosition(), architect.level());
                state.dstarPrecomputed = false;
            }
            return false;
        }
        architect.getLookControl().setLookAt(target, 30f, 30f);
        return true;
    }

    /** A reachable walking route can make a previously valid breach obsolete. */
    static boolean cancelBreakForOpenRoute(ArchitectEntity architect, ArchitectApproachState state,
            ArchitectBlockBreaker breaker, LivingEntity target) {
        // Bound path searches while retaining an opportunity to cancel before a block breaks.
        if (!breaker.hasTarget() || (architect.tickCount % 5 != 0 && !breaker.willFinishNextTick())
                || architect.horizontalDistanceTo(target) > 8.0
                || architect.verticalDistanceTo(target) > 6.0) {
            return false;
        }
        var navigation = architect.getNavigation();
        navigation.stop(); // Force a fresh route; a cached path may predate the terrain change.
        var path = navigation.createPath(target, 0);
        if (path == null || !path.canReach() || path.getEndNode() == null
                || !path.getEndNode().asBlockPos().equals(target.blockPosition())
                || !ArchitectApproachMovementSupport.isSafeWalkingPath(architect, path)) {
            return false;
        }
        if (!navigation.moveTo(path, 1.0)) return false;
        architect.recordDecision("BREAK_CANCEL", breaker.getChoice(), "OPEN_ROUTE nodes=" + path.getNodeCount());
        breaker.clearTarget();
        state.ceilingBreachPos = null;
        architect.clearCommittedWalk();
        architect.resetWalkStuckTracker();
        // setBlock callers need not emit a Forge placement/break event. Rebuild from
        // current terrain so the old plan cannot immediately select the same breach.
        state.dstar.setSurfaceY(state.surfaceY);
        state.dstar.initialize(target.blockPosition(), architect.blockPosition(), architect.level());
        state.dstarPrecomputed = false;
        architect.recordDecision("REINIT", null, "OPEN_ROUTE_AFTER_BREAK_CANCEL");
        state.openRouteAfterBreak = path;
        state.openRouteSubject = target.getUUID();
        architect.getLookControl().setLookAt(target, 30f, 30f);
        return true;
    }

    static boolean continueBreaking(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker,
            LivingEntity target
    ) {
        BlockPos breakTarget = blockBreaker.getTarget();
        if (breakTarget == null) {
            return false;
        }

        double blockDist = architect.position().distanceToSqr(
                breakTarget.getX() + 0.5,
                breakTarget.getY() + 0.5,
                breakTarget.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            architect.getNavigation().stop();
            architect.getLookControl().setLookAt(
                    breakTarget.getX() + 0.5,
                    breakTarget.getY() + 0.5,
                    breakTarget.getZ() + 0.5);
            boolean broke = blockBreaker.tick();
            if (broke) {
                // Ceiling breach drop-through: teleport into the new opening.
                if (approachState.ceilingBreachPos != null && breakTarget.equals(approachState.ceilingBreachPos)) {
                    architect.teleportTo(
                            breakTarget.getX() + 0.5,
                            breakTarget.getY(),
                            breakTarget.getZ() + 0.5);
                    architect.getNavigation().stop();
                    approachState.ceilingBreachPos = null;
                    architect.clearCommittedWalk();
                    architect.playSound(
                            ModSounds.ARCHITECT_LAND.get(),
                            0.8f,
                            0.7f + architect.nextRandomFloat() * 0.3f);
                    LOGGER.info("[Architect] Ceiling breach complete — dropping through {}", breakTarget);
                    architect.setPathRecalcCooldown(0);
                    approachState.dstar.onLocalBlockChanged(
                            breakTarget,
                            architect.level(),
                            "APPROACH_LOCAL_RESEED",
                            ArchitectEntity.actionName(architect.getBrainAction()),
                            approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE",
                            architect.distanceTo(target)
                    );
                    architect.triggerReeval();
                    return true;
                }

                architect.setPathRecalcCooldown(0);
                approachState.dstar.onLocalBlockChanged(
                        breakTarget,
                        architect.level(),
                        "APPROACH_LOCAL_RESEED",
                        ArchitectEntity.actionName(architect.getBrainAction()),
                        approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE",
                        architect.distanceTo(target)
                );

                // Chain headroom breach to restore 2-block clearance.
                BlockPos above = breakTarget.above();
                if (above.getY() <= architect.blockPosition().getY() + 1
                        && architect.isBreakableBlock(above)
                        && architect.shouldContinueApproachBreak(target, above)) {
                    blockBreaker.setChoice(new BreakChoice(above, BreakReason.HEAD_CLEARANCE));
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

    static void fallbackWallBreak(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker,
            @Nullable LivingEntity target
    ) {
        if (approachState.fallbackBreakCooldown > 0) {
            return;
        }
        BlockPos wallBlock = findBreakableWallBlock(architect, target);
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
                wallBlock.getX() + 0.5,
                wallBlock.getY() + 0.5,
                wallBlock.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            blockBreaker.setChoice(new BreakChoice(wallBlock, BreakReason.LAST_RESORT));
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
    static BlockPos findBreakableWallBlock(
            ArchitectEntity architect,
            @Nullable LivingEntity target
    ) {
        return ArchitectBreachPlanner.findBreakableWallBlock(
                architect,
                target,
                architect::isBreakableBlock);
    }
}
