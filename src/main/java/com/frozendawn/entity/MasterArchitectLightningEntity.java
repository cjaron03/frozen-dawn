package com.frozendawn.entity;

import com.frozendawn.init.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A blue-black visual bolt with no vanilla lightning side effects. */
public final class MasterArchitectLightningEntity extends Entity {
    private static final EntityDataAccessor<Long> DATA_SEED =
            SynchedEntityData.defineId(
                    MasterArchitectLightningEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(
                    MasterArchitectLightningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_INTENSITY =
            SynchedEntityData.defineId(
                    MasterArchitectLightningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(
                    MasterArchitectLightningEntity.class, EntityDataSerializers.INT);

    public MasterArchitectLightningEntity(
            EntityType<? extends MasterArchitectLightningEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public static MasterArchitectLightningEntity spawn(
            ServerLevel level,
            double x,
            double y,
            double z,
            float height,
            float intensity,
            long seed) {
        MasterArchitectLightningEntity bolt = ModEntities.MASTER_ARCHITECT_LIGHTNING
                .get().create(level);
        if (bolt == null) {
            return null;
        }
        bolt.moveTo(x, y, z);
        bolt.entityData.set(DATA_SEED, seed);
        bolt.entityData.set(DATA_HEIGHT, Mth.clamp(height, 16.0F, 160.0F));
        bolt.entityData.set(DATA_INTENSITY, Mth.clamp(intensity, 0.1F, 2.0F));
        bolt.entityData.set(DATA_LIFETIME, 8);
        level.addFreshEntity(bolt);
        return bolt;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SEED, 0L);
        builder.define(DATA_HEIGHT, 96.0F);
        builder.define(DATA_INTENSITY, 1.0F);
        builder.define(DATA_LIFETIME, 8);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide() && tickCount >= getLifetime()) {
            discard();
        }
    }

    public long getBoltSeed() {
        return entityData.get(DATA_SEED);
    }

    public float getBoltHeight() {
        return entityData.get(DATA_HEIGHT);
    }

    public float getBoltIntensity() {
        return entityData.get(DATA_INTENSITY);
    }

    public int getLifetime() {
        return entityData.get(DATA_LIFETIME);
    }

    public float getLifeAlpha(float partialTick) {
        float age = tickCount + partialTick;
        return Mth.clamp(1.0F - age / Math.max(1.0F, getLifetime()), 0.0F, 1.0F);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 1024.0D * 1024.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
