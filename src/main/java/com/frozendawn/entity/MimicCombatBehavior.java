package com.frozendawn.entity;

import com.frozendawn.init.ModSounds;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

final class MimicCombatBehavior {

    private MimicCombatBehavior() {
    }

    static void onSuccessfulAttack(MimicEntity mimic, Entity target, boolean hit) {
        if (!hit) {
            return;
        }

        mimic.level().playSound(
                null,
                mimic.getX(),
                mimic.getY(),
                mimic.getZ(),
                ModSounds.MIMIC_ATTACK.get(),
                net.minecraft.sounds.SoundSource.HOSTILE,
                1.0f,
                0.5f + mimic.getRandom().nextFloat() * 0.1f
        );

        if (!mimic.hasLandedFirstHit()) {
            if (target instanceof LivingEntity living) {
                living.hurt(mimic.damageSources().mobAttack(mimic), 2.0f);
            }
            mimic.setHasLandedFirstHitInternal(true);
        }
    }

    static HurtDecision beforeHurt(MimicEntity mimic, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return HurtDecision.cancelled();
        }

        float adjustedAmount = amount;
        if (source.is(DamageTypeTags.IS_FIRE)) {
            adjustedAmount *= 1.5f;
        }

        if (mimic.getMimicPhase() == MimicEntity.PHASE_OBSERVATION && source.getEntity() instanceof Player attacker) {
            mimic.setEngagedInternal(true);
            mimic.setMimicTargetUUIDInternal(Optional.of(attacker.getUUID()));
            mimic.forceTransitionToPhase(MimicEntity.PHASE_COMBAT);
        }

        return HurtDecision.applied(adjustedAmount);
    }

    /**
     * Enderman-style crosshair check: does the player's look ray intersect this entity's hitbox?
     */
    static boolean isPlayerLookingAtMe(MimicEntity mimic, Player player) {
        Vec3 lookVec = player.getViewVector(1.0f).normalize();
        Vec3 eyePos = player.getEyePosition();
        double range = 64.0;
        Vec3 rayEnd = eyePos.add(lookVec.scale(range));
        AABB box = mimic.getBoundingBox().inflate(0.1); // slight padding like Enderman
        return box.clip(eyePos, rayEnd).isPresent();
    }

    record HurtDecision(boolean cancel, float amount) {
        static HurtDecision cancelled() {
            return new HurtDecision(true, 0.0f);
        }

        static HurtDecision applied(float amount) {
            return new HurtDecision(false, amount);
        }
    }
}
