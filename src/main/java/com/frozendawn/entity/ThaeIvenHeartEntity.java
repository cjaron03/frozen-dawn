package com.frozendawn.entity;

import com.frozendawn.homo.HeartFormationStage;
import com.frozendawn.homo.HeartCollapseStage;
import com.frozendawn.homo.HeartCollapsePolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** Persistent visual authority for the non-interactive Thae Iven Heart. */
public final class ThaeIvenHeartEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_HEARTH_ID =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> DATA_LAYOUT_SEED =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_ANCHOR =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_FIELD_STRENGTH =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_STAGE =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_STAGE_PROGRESS =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_DESTROYED_NODE_MASK =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_NODE_DAMAGE =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLLAPSE_STAGE =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_COLLAPSE_PROGRESS =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_MAEVE_EXPOSED =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_MAEVE_ERASURE_PROGRESS =
            SynchedEntityData.defineId(ThaeIvenHeartEntity.class,
                    EntityDataSerializers.FLOAT);

    public ThaeIvenHeartEntity(
            EntityType<? extends ThaeIvenHeartEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEARTH_ID, Optional.empty());
        builder.define(DATA_LAYOUT_SEED, 0L);
        builder.define(DATA_ANCHOR, 0L);
        builder.define(DATA_FIELD_STRENGTH, 0.0F);
        builder.define(DATA_STAGE, HeartFormationStage.NONE.ordinal());
        builder.define(DATA_STAGE_PROGRESS, 0.0F);
        builder.define(DATA_DESTROYED_NODE_MASK, 0);
        builder.define(DATA_ACTIVE_NODE_DAMAGE, 0);
        builder.define(DATA_COLLAPSE_STAGE, HeartCollapseStage.NONE.ordinal());
        builder.define(DATA_COLLAPSE_PROGRESS, 0.0F);
        builder.define(DATA_MAEVE_EXPOSED, false);
        builder.define(DATA_MAEVE_ERASURE_PROGRESS, 0.0F);
    }

    public void configure(
            UUID hearthId,
            long layoutSeed,
            long anchor,
            float fieldStrength,
            HeartFormationStage stage,
            float progress,
            int destroyedNodeMask,
            int activeNodeDamage,
            HeartCollapseStage collapseStage,
            float collapseProgress,
            boolean maeveExposed,
            float maeveErasureProgress) {
        entityData.set(DATA_HEARTH_ID, Optional.ofNullable(hearthId));
        entityData.set(DATA_LAYOUT_SEED, layoutSeed);
        entityData.set(DATA_ANCHOR, anchor);
        entityData.set(DATA_FIELD_STRENGTH,
                net.minecraft.util.Mth.clamp(fieldStrength, 0.0F, 1.0F));
        entityData.set(DATA_STAGE, stage.ordinal());
        entityData.set(DATA_STAGE_PROGRESS,
                net.minecraft.util.Mth.clamp(progress, 0.0F, 1.0F));
        entityData.set(DATA_DESTROYED_NODE_MASK, destroyedNodeMask & 0x1F);
        entityData.set(DATA_ACTIVE_NODE_DAMAGE,
                net.minecraft.util.Mth.clamp(activeNodeDamage, 0, 2));
        entityData.set(DATA_COLLAPSE_STAGE, collapseStage.ordinal());
        entityData.set(DATA_COLLAPSE_PROGRESS,
                net.minecraft.util.Mth.clamp(collapseProgress, 0.0F, 1.0F));
        entityData.set(DATA_MAEVE_EXPOSED, maeveExposed);
        entityData.set(DATA_MAEVE_ERASURE_PROGRESS,
                net.minecraft.util.Mth.clamp(maeveErasureProgress, 0.0F, 1.0F));
    }

    public Optional<UUID> hearthId() {
        return entityData.get(DATA_HEARTH_ID);
    }

    public long layoutSeed() {
        return entityData.get(DATA_LAYOUT_SEED);
    }

    public long anchor() {
        return entityData.get(DATA_ANCHOR);
    }

    public float fieldStrength() {
        return entityData.get(DATA_FIELD_STRENGTH);
    }

    public HeartFormationStage formationStage() {
        int value = entityData.get(DATA_STAGE);
        HeartFormationStage[] stages = HeartFormationStage.values();
        return stages[Math.max(0, Math.min(stages.length - 1, value))];
    }

    public float stageProgress() {
        return entityData.get(DATA_STAGE_PROGRESS);
    }

    public int destroyedNodeMask() {
        return entityData.get(DATA_DESTROYED_NODE_MASK);
    }

    public int activeNodeDamage() {
        return entityData.get(DATA_ACTIVE_NODE_DAMAGE);
    }

    public HeartCollapseStage collapseStage() {
        int value = entityData.get(DATA_COLLAPSE_STAGE);
        HeartCollapseStage[] stages = HeartCollapseStage.values();
        return stages[Math.max(0, Math.min(stages.length - 1, value))];
    }

    public float collapseProgress() {
        return entityData.get(DATA_COLLAPSE_PROGRESS);
    }

    public boolean maeveExposed() {
        return entityData.get(DATA_MAEVE_EXPOSED);
    }

    public float maeveFormationProgress() {
        if (maeveExposed()) {
            return 1.0F;
        }
        return HeartCollapsePolicy.maeveFormationProgress(
                collapseStage(), collapseProgress());
    }

    public float maeveErasureProgress() {
        return entityData.get(DATA_MAEVE_ERASURE_PROGRESS);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance <= 512.0D * 512.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        UUID hearthId = tag.hasUUID("HearthId") ? tag.getUUID("HearthId") : null;
        configure(
                hearthId,
                tag.getLong("LayoutSeed"),
                tag.getLong("Anchor"),
                tag.getFloat("FieldStrength"),
                HeartFormationStage.fromName(tag.getString("FormationStage")),
                tag.getFloat("StageProgress"),
                tag.getInt("DestroyedNodeMask"),
                tag.getInt("ActiveNodeDamage"),
                HeartCollapseStage.fromName(tag.getString("CollapseStage")),
                tag.getFloat("CollapseProgress"),
                tag.getBoolean("MaeveExposed"),
                tag.getFloat("MaeveErasureProgress"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        hearthId().ifPresent(id -> tag.putUUID("HearthId", id));
        tag.putLong("LayoutSeed", layoutSeed());
        tag.putLong("Anchor", anchor());
        tag.putFloat("FieldStrength", fieldStrength());
        tag.putString("FormationStage", formationStage().name());
        tag.putFloat("StageProgress", stageProgress());
        tag.putInt("DestroyedNodeMask", destroyedNodeMask());
        tag.putInt("ActiveNodeDamage", activeNodeDamage());
        tag.putString("CollapseStage", collapseStage().name());
        tag.putFloat("CollapseProgress", collapseProgress());
        tag.putBoolean("MaeveExposed", maeveExposed());
        tag.putFloat("MaeveErasureProgress", maeveErasureProgress());
    }
}
