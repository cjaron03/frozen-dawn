package com.frozendawn.entity.ai;

import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.HeavySnowballEntity;
import com.frozendawn.init.ModSounds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FrostbittenRangedAttackGoal extends Goal {

    private final FrostbittenEntity mob;
    private int cooldown;

    private static final double MIN_RANGE_SQ = 8.0 * 8.0;   // 64
    private static final double MAX_RANGE_SQ = 16.0 * 16.0;  // 256

    public FrostbittenRangedAttackGoal(FrostbittenEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.isEmerging()) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        double distSq = mob.distanceToSqr(target);
        return distSq >= MIN_RANGE_SQ && distSq <= MAX_RANGE_SQ && cooldown <= 0;
    }

    @Override
    public boolean canContinueToUse() {
        return false; // Single-shot: fire once then re-evaluate
    }

    @Override
    public void start() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);

        HeavySnowballEntity snowball = new HeavySnowballEntity(mob.level(), mob);
        Vec3 toTarget = target.position().subtract(mob.position());
        double horizDist = toTarget.horizontalDistance();
        snowball.shoot(toTarget.x, toTarget.y + horizDist * 0.2, toTarget.z, 1.2f, 8.0f);
        mob.level().addFreshEntity(snowball);

        mob.playSound(ModSounds.FROSTBITTEN_THROW.get(), 1.0f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
        cooldown = 60; // 3 second cooldown
    }

    @Override
    public void tick() {
        // no-op, single-shot goal
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    public void tickCooldown() {
        if (cooldown > 0) cooldown--;
    }
}
