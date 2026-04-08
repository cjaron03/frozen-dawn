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
 * Owns D* step dispatch and prolonged-unreachable recovery while approach orchestration
 * remains in {@link ArchitectApproachController}.
 */
final class ArchitectApproachStepDispatchSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ArchitectEntity architect;
    private final ArchitectApproachState approachState;
    private final ArchitectBlockBreaker blockBreaker;
    private final double planningFallbackSpeed;
    private final int planningFallbackRepathTicks;

    ArchitectApproachStepDispatchSupport(
            ArchitectEntity architect,
            ArchitectApproachState approachState,
            ArchitectBlockBreaker blockBreaker,
            double planningFallbackSpeed,
            int planningFallbackRepathTicks
    ) {
        this.architect = architect;
        this.approachState = approachState;
        this.blockBreaker = blockBreaker;
        this.planningFallbackSpeed = planningFallbackSpeed;
        this.planningFallbackRepathTicks = planningFallbackRepathTicks;
    }

    boolean handleUnreachableStep(LivingEntity target, BlockPos targetPos) {
        BlockPos stuckWalkStep = architect.getCommittedWalkSteeringTarget();
        architect.clearWalkNavigationState(true);
        if (stuckWalkStep != null) {
            architect.trackWalkStep(stuckWalkStep);
            if (architect.handleWalkStuck(stuckWalkStep, target)) {
                return true;
            }
        }
        architect.clearCommittedWalk();
        approachState.unreachableTicks++;
        if (approachState.unreachableTicks >= ArchitectEntity.UNREACHABLE_BREAK_DELAY_TICKS) {
            ArchitectApproachBreakSupport.fallbackWallBreak(architect, approachState, blockBreaker, target);
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
                    planningFallbackSpeed,
                    planningFallbackRepathTicks,
                    false);
        }
        return true;
    }

    boolean continueQueuedBreakTarget() {
        if (!blockBreaker.hasTarget()) {
            return false;
        }

        architect.clearWalkNavigationState(true);
        architect.clearCommittedWalk();
        return architect.walkToBreakTarget();
    }

    void executeStep(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
        if (target == null) {
            return;
        }

        switch (step.type()) {
            case WALK -> architect.executeVanillaWalkStep(step, target);
            case BREACH -> executeBreachStep(step, target);
            case SCAFFOLD_UP -> executeScaffoldUpStep(step);
            case SCAFFOLD_BRIDGE -> executeScaffoldBridgeStep(step, target);
            case DIG_DOWN -> executeDigDownStep(step);
        }
    }

    private void executeBreachStep(DStarLitePathfinder.NextStep step, LivingEntity target) {
        architect.clearWalkNavigationState(true);
        architect.clearCommittedWalk();
        architect.resetWalkStuckTracker();

        BlockPos breakTarget = step.breakTarget();
        if (breakTarget == null) {
            breakTarget = ArchitectApproachBreakSupport.findBreakableWallBlock(architect, target);
        }
        if (breakTarget == null) {
            return;
        }

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
            return;
        }

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

    private void executeScaffoldUpStep(DStarLitePathfinder.NextStep step) {
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

    private void executeScaffoldBridgeStep(DStarLitePathfinder.NextStep step, LivingEntity target) {
        architect.clearWalkNavigationState(true);
        architect.clearCommittedWalk();
        architect.resetWalkStuckTracker();

        double horizontalTargetDelta = Math.sqrt(
                (target.getX() - architect.getX()) * (target.getX() - architect.getX())
                        + (target.getZ() - architect.getZ()) * (target.getZ() - architect.getZ()));
        boolean targetDirectlyBelow = target.getY() < architect.getY() - 1.0
                && horizontalTargetDelta <= 2.5;
        if (targetDirectlyBelow) {
            BlockPos dropInTarget = ArchitectApproachBreakSupport.findDropInBreakTarget(architect, target, step.pos());
            if (dropInTarget != null) {
                blockBreaker.setTarget(dropInTarget);
                approachState.ceilingBreachPos = dropInTarget;
                architect.getNavigation().stop();
                LOGGER.info("[Architect] Prefer drop-in over bridge: digging {}", dropInTarget);
                return;
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

    private void executeDigDownStep(DStarLitePathfinder.NextStep step) {
        architect.clearWalkNavigationState(true);
        architect.clearCommittedWalk();
        architect.resetWalkStuckTracker();

        BlockPos digTarget = step.breakTarget();
        if (digTarget == null || !architect.isBreakableBlock(digTarget)) {
            return;
        }

        blockBreaker.setTarget(digTarget);
        architect.teleportTo(digTarget.getX() + 0.5, architect.getY(), digTarget.getZ() + 0.5);
        architect.getNavigation().stop();
        approachState.ceilingBreachPos = digTarget;
        LOGGER.info("[Architect] D* DIG DOWN at {} ({})",
                digTarget,
                architect.level().getBlockState(digTarget).getBlock());
    }
}
