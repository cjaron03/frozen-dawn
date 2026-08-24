package com.frozendawn.entity;

import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.architect.ArchitectCombatState;
import com.frozendawn.entity.architect.ArchitectRetreatPolicy;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;

/**
 * Owns the top-level melee and retreat execution flow while {@link ArchitectEntity}
 * continues to provide the lower-level motion, item, and placement helpers.
 */
final class ArchitectCombatController {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final ArchitectEntity architect;
    private final ArchitectCombatState combatState;
    private final ArchitectBlockBreaker blockBreaker;

    ArchitectCombatController(
            ArchitectEntity architect,
            ArchitectCombatState combatState,
            ArchitectBlockBreaker blockBreaker
    ) {
        this.architect = architect;
        this.combatState = combatState;
        this.blockBreaker = blockBreaker;
    }

    void executeAttackMelee(@Nullable LivingEntity target) {
        double hDist = target != null ? architect.horizontalDistanceTo(target) : Double.MAX_VALUE;
        double vDist = target != null ? architect.verticalDistanceTo(target) : Double.MAX_VALUE;
        float dist3d = target != null ? architect.distanceTo(target) : Float.MAX_VALUE;
        if (target == null
                || hDist > ArchitectEntity.MELEE_COMMIT_HORIZONTAL_RANGE
                || vDist > ArchitectEntity.MELEE_COMMIT_VERTICAL_RANGE
                || !architect.canCommitToMelee(target)) {
            architect.clearMeleeCommit();
            architect.triggerReeval();
            return;
        }

        blockBreaker.clearTarget();
        architect.getLookControl().setLookAt(target, 30f, 30f);
        if (architect.getHealth() > architect.getMaxHealth() * 0.35f) {
            architect.refreshMeleeCommit();
        }

        if (combatState.backoffTicks > 0) {
            combatState.backoffTicks--;
            Vec3 away = architect.position().subtract(target.position()).normalize();
            BlockPos behind = architect.blockPosition().offset(
                    (int) Math.round(away.x * 2),
                    0,
                    (int) Math.round(away.z * 2)
            );
            boolean groundBehind = architect.level().getBlockState(behind.below()).isSolid();
            if (groundBehind) {
                architect.applyCombatHorizontalMotion(
                        away.x * ArchitectEntity.MELEE_BACKOFF_SPEED,
                        away.z * ArchitectEntity.MELEE_BACKOFF_SPEED
                );
            } else {
                double dodgeX = -away.z * combatState.strafeDir * ArchitectEntity.MELEE_DODGE_SPEED;
                double dodgeZ = away.x * combatState.strafeDir * ArchitectEntity.MELEE_DODGE_SPEED;
                architect.applyCombatHorizontalMotion(dodgeX, dodgeZ);
            }
            architect.getNavigation().stop();
            return;
        }

        combatState.strafeChangeCooldown--;
        if (combatState.strafeChangeCooldown <= 0) {
            combatState.strafeDir = -combatState.strafeDir;
            combatState.strafeChangeCooldown = 30 + architect.nextRandomInt(30);
        }

        if (hDist > 3.0) {
            architect.getNavigation().moveTo(target, 1.0);
        } else {
            architect.getNavigation().stop();
            Vec3 toTarget = target.position().subtract(architect.position()).normalize();
            double strafeX = -toTarget.z * combatState.strafeDir * ArchitectEntity.MELEE_STRAFE_SPEED;
            double strafeZ = toTarget.x * combatState.strafeDir * ArchitectEntity.MELEE_STRAFE_SPEED;
            double pullStrength = hDist < 2.0
                    ? ArchitectEntity.MELEE_PULL_SPEED_NEAR
                    : ArchitectEntity.MELEE_PULL_SPEED_FAR;
            architect.applyCombatHorizontalMotion(
                    strafeX + toTarget.x * pullStrength,
                    strafeZ + toTarget.z * pullStrength
            );

            if (dist3d < 2.8 && architect.attackAnim == 0 && architect.hasLineOfSight(target)) {
                architect.swing(InteractionHand.MAIN_HAND);
                architect.doHurtTarget(target);
                combatState.backoffTicks = 6 + architect.nextRandomInt(4);
            }
        }
    }

    void executeRetreat(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();

        if (target == null && !combatState.isDrinkingPotion) {
            combatState.retreatPhase = 0;
            combatState.retreatCoverBuilt = 0;
            return;
        }

        float dist = target != null ? architect.distanceTo(target) : 999;

        switch (combatState.retreatPhase) {
            case 0 -> {
                if (combatState.retreatStartPosition == null) {
                    combatState.retreatStartPosition = architect.position();
                    combatState.retreatRunTicks = 0;
                }
                Vec3 retreatStart = combatState.retreatStartPosition;
                double committedTravel = Math.hypot(
                        architect.getX() - retreatStart.x,
                        architect.getZ() - retreatStart.z
                );
                ArchitectRetreatPolicy.RunEndReason runEndReason = ArchitectRetreatPolicy.runEndReason(
                        dist,
                        committedTravel,
                        combatState.retreatRunTicks
                );
                if (runEndReason != ArchitectRetreatPolicy.RunEndReason.CONTINUE) {
                    LOGGER.info(
                            "[Architect] RETREAT: run ended by {} after {} blocks / {} ticks "
                                    + "(target distance {}), building cover",
                            runEndReason,
                            String.format("%.1f", committedTravel),
                            combatState.retreatRunTicks,
                            String.format("%.1f", dist)
                    );
                    architect.getNavigation().stop();
                    combatState.retreatPhase = 1;
                    combatState.retreatCoverBuilt = 0;
                    return;
                }

                Vec3 away = architect.position().subtract(target.position());
                if (away.lengthSqr() < 1.0e-4) {
                    away = new Vec3(
                            architect.nextRandomCenteredDouble(),
                            0.0,
                            architect.nextRandomCenteredDouble()
                    );
                }
                away = away.normalize();
                architect.getMoveControl().setWantedPosition(
                        architect.getX() + away.x * 2.0,
                        architect.getY(),
                        architect.getZ() + away.z * 2.0,
                        1.3
                );
                if (architect.isPathRecalcReady()) {
                    architect.getNavigation().moveTo(
                            architect.getX() + away.x * ArchitectRetreatPolicy.SAFE_TARGET_DISTANCE,
                            architect.getY(),
                            architect.getZ() + away.z * ArchitectRetreatPolicy.SAFE_TARGET_DISTANCE,
                            1.3
                    );
                    architect.setPathRecalcCooldown(10);
                }
                architect.decrementPathRecalcCooldown();
                combatState.retreatRunTicks++;
            }
            case 1 -> {
                architect.getNavigation().stop();
                if (target != null
                        && combatState.retreatCoverBuilt < 3
                        && architect.getTacticalIceCount() < architect.getMaxTacticalIce()) {
                    Vec3 towardPlayer = target.position().subtract(architect.position()).normalize();
                    BlockPos wallPos = architect.blockPosition().offset(
                            (int) Math.round(towardPlayer.x * (1 + combatState.retreatCoverBuilt)),
                            0,
                            (int) Math.round(towardPlayer.z * (1 + combatState.retreatCoverBuilt))
                    );
                    if (architect.placeTacticalIce(wallPos)) {
                        architect.placeTacticalIce(wallPos.above());
                        LOGGER.info("[Architect] RETREAT: placed ice wall #{} at {}",
                                combatState.retreatCoverBuilt,
                                wallPos);
                        combatState.retreatCoverBuilt++;
                    } else {
                        combatState.retreatCoverBuilt++;
                    }
                } else {
                    LOGGER.info("[Architect] RETREAT: cover complete ({} walls), entering heal phase",
                            combatState.retreatCoverBuilt);
                    combatState.retreatPhase = 2;
                }
            }
            case 2 -> {
                architect.getNavigation().stop();
                if (combatState.healCooldown <= 0
                        && !combatState.isDrinkingPotion
                        && architect.getHealth() < architect.getMaxHealth() * 0.75f) {
                    LOGGER.info("[Architect] RETREAT: starting to drink healing potion (HP={})",
                            String.format("%.1f", architect.getHealth()));
                    architect.startDrinking();
                } else if (!combatState.isDrinkingPotion) {
                    LOGGER.info("[Architect] RETREAT: heal phase complete (HP={}), re-evaluating",
                            String.format("%.1f", architect.getHealth()));
                    architect.triggerReeval();
                }
            }
        }
    }
}
