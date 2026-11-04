package com.frozendawn.vault;

import com.frozendawn.vault.TwoPersonRule.Pose;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * The pale figure: a recording walking the drill hall, driven tick by tick by {@link VaultRecorderBlockEntity}. There is nothing
 * to it: it stops nothing, takes no harm and is no mind, so it freezes no door. It is never saved, and it goes if the recorder
 * stops driving it.
 */
public final class VaultRecordingEntity extends Mob {
    private static final int UNDRIVEN_TICKS = 5;
    private int undriven;

    public VaultRecordingEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setNoAi(true);
        setSilent(true);
        setInvulnerable(true);
    }

    public void drive(Pose pose) {
        undriven = 0;
        moveTo(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
        setYHeadRot(pose.yaw());
        setYBodyRot(pose.yaw());
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide() && ++undriven > UNDRIVEN_TICKS) discard();
    }

    @Override public boolean hurt(DamageSource source, float amount) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluid() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
}
