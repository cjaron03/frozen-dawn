package com.frozendawn.entity;

import com.frozendawn.init.ModSounds;
import com.frozendawn.aggregate.AggregateCombatPolicy;
import com.frozendawn.aggregate.AggregatePhase;
import com.frozendawn.aggregate.AggregatePressureHandler;
import com.frozendawn.aggregate.AggregateReinforcementManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/** One temporary representative shed by the Frostwrithe lineage. */
public final class AggregateFragmentEntity extends Monster {
    private @Nullable UUID ownerId;
    private int index;
    private int returnTicks;

    public AggregateFragmentEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.12D, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true, living -> living instanceof Player player
                && !player.isCreative() && !player.isSpectator()));
    }

    public void initialize(AggregateEntity owner, int index) {
        ownerId = owner.getUUID();
        this.index = index;
        returnTicks = owner.hasDominantTrait(
                com.frozendawn.aggregate.AggregateLineage.FROSTWRITHE) ? 180 : 240;
        AggregatePressureHandler.markIgnored(this);
        setPersistenceRequired();
    }

    @Nullable
    public UUID ownerId() {
        return ownerId;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (ownerId == null || !(level() instanceof ServerLevel server)
                || !(server.getEntity(ownerId) instanceof AggregateEntity owner)
                || !owner.isAlive()) {
            discard();
            return;
        }
        boolean dischargeChild = AggregateReinforcementManager.isChild(this);
        if ((!dischargeChild && owner.phase() == AggregatePhase.CONVERGENCE_FAILURE)
                || owner.phase() == AggregatePhase.DYING
                || owner.phase() == AggregatePhase.DEAD) {
            discard();
            return;
        }
        if (dischargeChild) return;
        if (returnTicks-- > 0) return;
        setTarget(null);
        getNavigation().moveTo(owner, 1.35D);
        if (tickCount % 4 == 0) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.WHITE_ASH,
                    getX(), getY() + 0.2D, getZ(), 3,
                    0.2D, 0.1D, 0.2D, 0.02D);
        }
        if (distanceToSqr(owner) <= 7.0D) {
            returnToOwner(owner);
        }
    }

    private void returnToOwner(AggregateEntity owner) {
        float healed = owner.getPersistentData().getFloat("fragmentHealing");
        float amount = AggregateCombatPolicy.fragmentReturnHeal(
                owner.getMaxHealth(), healed, owner.phase());
        if (amount > 0.0F) {
            owner.heal(amount);
            owner.getPersistentData().putFloat("fragmentHealing", healed + amount);
        }
        discard();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.AGGREGATE_FRAGMENT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.AGGREGATE_FRAGMENT_BREAK.get();
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source,
                                       boolean recentlyHit) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("owner", ownerId);
        tag.putInt("index", index);
        tag.putInt("returnTicks", returnTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        index = tag.getInt("index");
        returnTicks = Math.max(0, tag.getInt("returnTicks"));
        AggregatePressureHandler.markIgnored(this);
    }
}
