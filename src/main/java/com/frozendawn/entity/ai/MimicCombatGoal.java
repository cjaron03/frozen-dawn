package com.frozendawn.entity.ai;

import com.frozendawn.entity.MimicEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Custom combat goal for the Mimic entity.
 * Handles strafing combat and sprint attacks instead of standing and trading hits.
 */
public class MimicCombatGoal extends Goal {

    private final MimicEntity mimic;
    private int strafeTimer = 0;
    private int strafeDirection = 1; // 1 = right, -1 = left
    private int attackCooldown = 0;
    private int pathRecalcTimer = 0;
    private boolean strafingBackward = false;

    public MimicCombatGoal(MimicEntity mimic) {
        this.mimic = mimic;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mimic.getMimicPhase() == MimicEntity.PHASE_COMBAT && mimic.getTarget() != null
                && mimic.getTarget().isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        strafeTimer = 0;
        strafeDirection = mimic.getRandom().nextBoolean() ? 1 : -1;
        attackCooldown = 0;
        pathRecalcTimer = 0;
        strafingBackward = false;
        mimic.setSprinting(true);
    }

    @Override
    public void stop() {
        strafeTimer = 0;
        attackCooldown = 0;
        pathRecalcTimer = 0;
        mimic.setSprinting(false);
        mimic.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mimic.getTarget();
        if (target == null) return;

        double dist = mimic.distanceTo(target);
        mimic.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (dist <= 2.5) {
            // In melee range: attack and strafe
            mimic.setSprinting(false);
            mimic.getNavigation().stop();

            if (attackCooldown <= 0) {
                mimic.doHurtTarget(target);
                attackCooldown = 15; // Slightly faster than before
            }

            // Strafe sideways — alternate direction with occasional backward dodge
            strafeTimer++;
            if (strafeTimer >= 30 + mimic.getRandom().nextInt(20)) {
                strafeDirection *= -1;
                strafeTimer = 0;
                // 25% chance to briefly back off after direction change
                strafingBackward = mimic.getRandom().nextFloat() < 0.25f;
            }

            float forward = strafingBackward ? -0.4f : 0.15f;
            mimic.getMoveControl().strafe(forward, strafeDirection * 0.6f);

            // Reset backward after a short dodge
            if (strafingBackward && strafeTimer > 8) {
                strafingBackward = false;
            }
        } else if (dist <= 5.0) {
            // Close range: sprint directly at target, recalc path frequently
            mimic.setSprinting(true);
            pathRecalcTimer++;
            if (pathRecalcTimer >= 4) { // Recalc every 4 ticks when close
                mimic.getNavigation().moveTo(target, 1.6);
                pathRecalcTimer = 0;
            }
            strafeTimer = 0;
        } else {
            // Long range: sprint toward target, standard path recalc
            mimic.setSprinting(true);
            pathRecalcTimer++;
            if (pathRecalcTimer >= 10) { // Recalc every 10 ticks when far
                mimic.getNavigation().moveTo(target, 1.5);
                pathRecalcTimer = 0;
            }
            strafeTimer = 0;
        }
    }
}
