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
        if (target == null) {
            architect.triggerReeval();
            return;
        }

        Vec3 toPlayer = target.position().subtract(architect.position()).normalize();
        BlockPos wallPos = architect.blockPosition().offset(
                (int) Math.round(toPlayer.x * 2),
                0,
                (int) Math.round(toPlayer.z * 2)
        );
        if (architect.placeTacticalIce(wallPos)) {
            architect.placeTacticalIce(wallPos.above());
        }
        architect.getLookControl().setLookAt(target, 30f, 30f);
        if (architect.tickCount % 60 == 0) {
            architect.triggerReeval();
        }
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
            architect.resetPeekTicks();
            architect.triggerReeval();
        }
    }
}
