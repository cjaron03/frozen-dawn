package com.frozendawn.entity;

import com.frozendawn.world.ArchivistManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** A fixed, persistent projection of one SavedData-owned collection relic. */
public final class ArchivistRelicEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> DATA_STACK =
            SynchedEntityData.defineId(ArchivistRelicEntity.class,
                    EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SITE_ID =
            SynchedEntityData.defineId(ArchivistRelicEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> DATA_RELIC_ID =
            SynchedEntityData.defineId(ArchivistRelicEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_SLOT =
            SynchedEntityData.defineId(ArchivistRelicEntity.class,
                    EntityDataSerializers.INT);

    public ArchivistRelicEntity(EntityType<? extends ArchivistRelicEntity> type,
                                Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STACK, ItemStack.EMPTY);
        builder.define(DATA_SITE_ID, Optional.empty());
        builder.define(DATA_RELIC_ID, Optional.empty());
        builder.define(DATA_SLOT, -1);
    }

    public void bind(UUID siteId, UUID relicId, int slot, ItemStack stack) {
        entityData.set(DATA_SITE_ID, Optional.of(siteId));
        entityData.set(DATA_RELIC_ID, Optional.of(relicId));
        entityData.set(DATA_SLOT, slot);
        entityData.set(DATA_STACK, stack.copy());
    }

    public ItemStack getItem() {
        return entityData.get(DATA_STACK);
    }

    public Optional<UUID> siteId() {
        return entityData.get(DATA_SITE_ID);
    }

    public Optional<UUID> relicId() {
        return entityData.get(DATA_RELIC_ID);
    }

    public int slot() {
        return entityData.get(DATA_SLOT);
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        return ArchivistManager.claimRelic(serverPlayer, this)
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("item", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            entityData.set(DATA_STACK, ItemStack.parseOptional(
                    registryAccess(), tag.getCompound("item")));
        }
        entityData.set(DATA_SITE_ID, tag.hasUUID("siteId")
                ? Optional.of(tag.getUUID("siteId")) : Optional.empty());
        entityData.set(DATA_RELIC_ID, tag.hasUUID("relicId")
                ? Optional.of(tag.getUUID("relicId")) : Optional.empty());
        entityData.set(DATA_SLOT, tag.getInt("slot"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!getItem().isEmpty()) {
            tag.put("item", getItem().save(registryAccess()));
        }
        siteId().ifPresent(id -> tag.putUUID("siteId", id));
        relicId().ifPresent(id -> tag.putUUID("relicId", id));
        tag.putInt("slot", slot());
    }
}
