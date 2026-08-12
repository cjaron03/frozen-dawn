package com.frozendawn.entity;

import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** A dodgeable projectile that becomes a temporary preferred burrow exit. */
public final class RimeLanceEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> DATA_EMBEDDED =
            SynchedEntityData.defineId(RimeLanceEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private int embeddedTicks;
    private int missingOwnerTicks;

    public RimeLanceEntity(EntityType<? extends RimeLanceEntity> type, Level level) {
        super(type, level);
    }

    public RimeLanceEntity(Level level, LivingEntity owner) {
        super(ModEntities.RIME_LANCE.get(), owner, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EMBEDDED, false);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.ICE_SHARD.get();
    }

    @Override
    protected double getDefaultGravity() {
        return isEmbedded() ? 0.0D : 0.018D;
    }

    public boolean isEmbedded() {
        return entityData.get(DATA_EMBEDDED);
    }

    @Override
    public void tick() {
        if (isEmbedded()) {
            embeddedTicks++;
            if (!level().isClientSide()) {
                missingOwnerTicks = getOwner() == null ? missingOwnerTicks + 1 : 0;
                if (missingOwnerTicks >= 40) {
                    discard();
                    return;
                }
            }
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            if (level() instanceof ServerLevel serverLevel && embeddedTicks % 8 == 0) {
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                        getX(), getY() + 0.12D, getZ(),
                        2, 0.08D, 0.14D, 0.08D, 0.005D);
            }
            if (embeddedTicks >= 1_200) {
                discard();
            }
            return;
        }
        super.tick();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (isEmbedded()) {
            return;
        }
        super.onHitEntity(result);
        if (result.getEntity() instanceof LivingEntity living) {
            if (living.hurt(damageSources().thrown(this, getOwner()), 5.0F)) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                if (living instanceof ServerPlayer player) {
                    RimeboundEncasement.apply(player, 20.0F);
                }
            }
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (isEmbedded()) {
            return;
        }
        entityData.set(DATA_EMBEDDED, true);
        embeddedTicks = 0;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        Vec3 normal = Vec3.atLowerCornerOf(result.getDirection().getNormal())
                .scale(0.06D);
        setPos(result.getLocation().add(normal));
        playSound(ModSounds.RIMEBOUND_LANCE_EMBED.get(), 1.15F, 0.92F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Embedded", isEmbedded());
        tag.putInt("EmbeddedTicks", embeddedTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_EMBEDDED, tag.getBoolean("Embedded"));
        embeddedTicks = Math.max(0, tag.getInt("EmbeddedTicks"));
        if (isEmbedded()) {
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
        }
    }
}
