package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectObservationMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Owns the Architect's smaller tactical action handlers that don't fit into
 * the main approach or combat controllers.
 */
final class ArchitectTacticsController {

    private final ArchitectEntity architect;
    private final ArchitectObservationMemory observationMemory;
    private final ArchitectBlockBreaker blockBreaker;
    private static final int FORTIFY_INTERVAL = 120;
    private static final int PEEK_INTERVAL = 100;
    private int nextFortifyTick;
    private int nextPeekTick;
    private Vec3 lastFortifyPosition;
    private Vec3 lastPeekPosition;

    ArchitectTacticsController(
            ArchitectEntity architect,
            ArchitectObservationMemory observationMemory,
            ArchitectBlockBreaker blockBreaker
    ) {
        this.architect = architect;
        this.observationMemory = observationMemory;
        this.blockBreaker = blockBreaker;
    }

    void executeFortify(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();
        if (target == null || !canFortify()) {
            architect.triggerReeval();
            return;
        }

        BlockPos wallPos = architect.hasLineOfSight(target)
                ? com.frozendawn.entity.architect.ArchitectCoverGeometry.find(
                        architect, architect.position(), target.getEyePosition()) : null;
        int placed = 0;
        if (wallPos != null && architect.placeTacticalIce(wallPos)) {
            placed++;
            if (architect.placeTacticalIce(wallPos.above())) placed++;
        }
        // One short construction attempt, then move before building again. An
        // occupied cell must not turn a stronger cover preference into idle spam.
        nextFortifyTick = architect.tickCount + FORTIFY_INTERVAL;
        lastFortifyPosition = architect.position();
        architect.recordDecision("FORTIFY_FINISHED", null, "blocks=" + placed + " retryTicks=" + FORTIFY_INTERVAL
                + " screenedLane=" + (wallPos != null) + " wall=" + wallPos);
        architect.getLookControl().setLookAt(target, 30f, 30f);
        architect.triggerReeval();
    }

    boolean canFortify() {
        return architect.tickCount >= nextFortifyTick && movedFrom(lastFortifyPosition);
    }

    boolean canPeek() {
        return architect.tickCount >= nextPeekTick && movedFrom(lastPeekPosition);
    }

    private boolean movedFrom(@Nullable Vec3 position) {
        return position == null || architect.position().subtract(position).horizontalDistanceSqr() >= 4;
    }

    void executeTrapSet(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();
        if (target == null || observationMemory.entrancePositions().isEmpty()) {
            architect.triggerReeval();
            return;
        }

        BlockPos bestEntrance = null;
        double bestDist = 0;
        for (BlockPos entrance : observationMemory.entrancePositions()) {
            double d = architect.distanceToSqr(entrance.getX(), entrance.getY(), entrance.getZ());
            if (d > bestDist) {
                bestDist = d;
                bestEntrance = entrance;
            }
        }

        if (bestEntrance != null) {
            if (architect.distanceToSqr(bestEntrance.getX(), bestEntrance.getY(), bestEntrance.getZ()) > 4) {
                architect.getNavigation().moveTo(
                        bestEntrance.getX() + 0.5,
                        bestEntrance.getY(),
                        bestEntrance.getZ() + 0.5,
                        1.0
                );
            } else {
                architect.placeTacticalIce(bestEntrance);
                architect.placeTacticalIce(bestEntrance.above());
                architect.setTrapCooldown(400);
                architect.triggerReeval();
            }
        }
    }

    void executePeek(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();
        architect.getNavigation().stop();
        if (target != null) {
            architect.getLookControl().setLookAt(target, 30f, 30f);
        }
        if (architect.incrementPeekTicks() >= 30) {
            nextPeekTick = architect.tickCount + PEEK_INTERVAL;
            lastPeekPosition = architect.position();
            architect.resetPeekTicks();
            architect.recordDecision("PEEK_FINISHED", null, "retryTicks=" + PEEK_INTERVAL + " requiresMovement=2");
            architect.triggerReeval();
        }
    }
}
