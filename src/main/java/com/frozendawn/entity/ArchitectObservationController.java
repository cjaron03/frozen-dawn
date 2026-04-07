package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectObservationMemory;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Owns the Architect's observation and roam lifecycle while the entity continues
 * to provide low-level navigation, targeting, and environment helpers.
 */
final class ArchitectObservationController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float OBSERVE_MIN_STANDOFF = 28.0f;
    private static final float OBSERVE_MAX_STANDOFF = 42.0f;

    private final ArchitectEntity architect;
    private final ArchitectObservationMemory observationMemory;
    private final ArchitectApproachState approachState;
    private final ArchitectApproachController approachController;
    private final ArchitectBlockBreaker blockBreaker;

    ArchitectObservationController(
            ArchitectEntity architect,
            ArchitectObservationMemory observationMemory,
            ArchitectApproachState approachState,
            ArchitectApproachController approachController,
            ArchitectBlockBreaker blockBreaker
    ) {
        this.architect = architect;
        this.observationMemory = observationMemory;
        this.approachState = approachState;
        this.approachController = approachController;
        this.blockBreaker = blockBreaker;
    }

    void executeObserve(@Nullable LivingEntity target) {
        if (target == null) {
            approachState.dstarPrecomputed = false;
            return;
        }

        approachController.precomputeDStarDuringObserve(target);

        float dist = architect.distanceTo(target);
        boolean hasLineOfSight = target.hasLineOfSight(architect);
        if (shouldHoldObservePosition(dist, hasLineOfSight)) {
            architect.getNavigation().stop();
        }

        if (architect.isPathRecalcReady()) {
            if (dist < OBSERVE_MIN_STANDOFF) {
                Vec3 away = architect.position().subtract(target.position()).normalize().scale(0.8);
                architect.getNavigation().moveTo(
                        architect.getX() + away.x * 10,
                        architect.getY(),
                        architect.getZ() + away.z * 10,
                        0.8
                );
            } else if (dist > OBSERVE_MAX_STANDOFF) {
                architect.getNavigation().moveTo(target, 0.8);
            } else {
                architect.getNavigation().stop();
            }
            architect.setPathRecalcCooldown(20);
        }
        architect.decrementPathRecalcCooldown();
        architect.getLookControl().setLookAt(target, 30f, 30f);

        observationMemory.incrementObserveTicks();

        if (architect.level() instanceof ServerLevel serverLevel && architect.tickCount % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    architect.getX(), architect.getY() + 1.8, architect.getZ(),
                    1, 0.15, 0.1, 0.15, 0.01);
            if (architect.tickCount % 20 == 0) {
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        architect.getX(), architect.getY() + 1.65, architect.getZ(),
                        2, 0.25, 0.15, 0.25, 0.05);
            }
        }

        if (architect.tickCount % 60 == 0) {
            architect.playSound(
                    ModSounds.ARCHITECT_OBSERVE.get(),
                    0.6f,
                    0.8f + architect.nextRandomFloat() * 0.3f
            );
        }

        if (observationMemory.getObserveTicks() % 40 == 0 && architect.level() instanceof ServerLevel serverLevel) {
            BlockPos playerPos = target.blockPosition();
            architect.scanEntrances(serverLevel, playerPos);
            observationMemory.setLastObservedPos(playerPos);
        }

        if (observationMemory.getObserveTicks() == 60 || observationMemory.getObserveTicks() == 300) {
            awardObserveProbeAdvancement(target);
        }

        if (dist < 20 && hasLineOfSight && architect.isPlayerFacing(target)) {
            markObserveComplete();
            return;
        }

        int targetDuration = ArchitectEntity.MIN_OBSERVE_TICKS
                + architect.nextRandomInt(ArchitectEntity.MAX_OBSERVE_TICKS - ArchitectEntity.MIN_OBSERVE_TICKS);
        if (observationMemory.getObserveTicks() >= targetDuration) {
            markObserveComplete();
        }
    }

    private boolean shouldHoldObservePosition(float dist, boolean hasLineOfSight) {
        return hasLineOfSight && dist >= OBSERVE_MIN_STANDOFF && dist <= OBSERVE_MAX_STANDOFF;
    }

    void executeRoamAndRuin() {
        architect.keepNearbyWoodenDoorsOpen();
        blockBreaker.clearTarget();

        if (architect.isPathRecalcReady() || !architect.getNavigation().isInProgress()) {
            Vec3 roamPos = DefaultRandomPos.getPos(architect, 12, 4);
            if (roamPos != null) {
                architect.getNavigation().moveTo(roamPos.x, roamPos.y, roamPos.z, 0.9);
                architect.getLookControl().setLookAt(roamPos.x, roamPos.y, roamPos.z);
            } else {
                double dx = architect.nextRandomCenteredDouble() * 12.0;
                double dz = architect.nextRandomCenteredDouble() * 12.0;
                architect.getNavigation().moveTo(
                        architect.getX() + dx,
                        architect.getY(),
                        architect.getZ() + dz,
                        0.9
                );
            }
            architect.setPathRecalcCooldown(
                    ArchitectEntity.ROAM_REPATH_MIN_TICKS + architect.nextRandomInt(ArchitectEntity.ROAM_REPATH_VARIANCE_TICKS)
            );
        }
        architect.decrementPathRecalcCooldown();
    }

    void maybeTriggerSpawnObserveCue(@Nullable LivingEntity target) {
        if (observationMemory.isPendingSpawnCuePlayed()
                || observationMemory.getPendingSpawnCuePlayerId() == null
                || architect.getBrainAction() != ArchitectEntity.ACTION_OBSERVE) {
            return;
        }
        if (!(target instanceof ServerPlayer player)) {
            return;
        }
        if (!observationMemory.getPendingSpawnCuePlayerId().equals(player.getUUID())) {
            return;
        }
        if (architect.distanceToSqr(player) > ArchitectEntity.SPAWN_OBSERVE_CUE_RANGE_SQR) {
            return;
        }

        observationMemory.setPendingSpawnCuePlayed(true);
        observationMemory.setPendingSpawnCuePlayerId(null);
        architect.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.ARCHITECT_WATCHED.get(), SoundSource.HOSTILE,
                1.0f, 0.9f + architect.nextRandomFloat() * 0.2f);
        player.displayClientMessage(Component.translatable("message.frozendawn.architect_watched"), true);
    }

    void enterRoamModeAfterTargetLoss() {
        architect.setRoamingAfterTargetLoss(true);
        resetObserveCycle();
        architect.resetRetreatState();
        architect.clearWalkNavigationState(true);
        blockBreaker.clearTarget();
        architect.setPathRecalcCooldown(0);
        LOGGER.info("[Architect] Lost target — entering roam/ruin mode");
    }

    void restartObserveForPlayer(Player player) {
        resetObserveCycle();
        architect.transitionToObserveAction();
        architect.resetActionHoldTicks();
        architect.resetReevalCooldown();
        LOGGER.info("[Architect] Player reacquired at {} blocks — restarting OBSERVE",
                String.format("%.1f", architect.distanceTo(player)));
    }

    private void resetObserveCycle() {
        observationMemory.setHasObserved(false);
        observationMemory.setObserveDirty(false);
        observationMemory.setObserveTicks(0);
        observationMemory.setLastObservedPos(null);
        approachState.dstarPrecomputed = false;
    }

    private void awardObserveProbeAdvancement(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            WorldTickHandler.grantAdvancement(player, "architect_noticed");
        }
    }

    private void markObserveComplete() {
        observationMemory.setHasObserved(true);
        observationMemory.setObserveDirty(false);
        if (architect.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL, architect.getX(), architect.getY() + 1.8, architect.getZ(),
                    8, 0.3, 0.2, 0.3, 0.03);
            serverLevel.sendParticles(ParticleTypes.ENCHANT, architect.getX(), architect.getY() + 1.6, architect.getZ(),
                    10, 0.35, 0.25, 0.35, 0.08);
        }
        architect.triggerReeval();
    }
}
