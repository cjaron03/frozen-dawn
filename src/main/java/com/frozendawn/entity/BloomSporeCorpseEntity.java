package com.frozendawn.entity;

import com.frozendawn.bloom.BloomSporeManager;
import com.frozendawn.bloom.BloomSporePolicy;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** The colonized body left behind to maintain one finite satellite patch. */
public final class BloomSporeCorpseEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> DATA_NODE_ID =
            SynchedEntityData.defineId(BloomSporeCorpseEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_STRIKES =
            SynchedEntityData.defineId(BloomSporeCorpseEntity.class,
                    EntityDataSerializers.INT);
    private long nextStrikeGameTime;

    public BloomSporeCorpseEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        xpReward = 0;
        setNoGravity(true);
        noPhysics = true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NODE_ID, Optional.empty());
        builder.define(DATA_STRIKES, 0);
    }

    public void bindNode(UUID nodeId) {
        entityData.set(DATA_NODE_ID, Optional.of(nodeId));
    }

    public Optional<UUID> nodeId() {
        return entityData.get(DATA_NODE_ID);
    }

    public int acceptedStrikes() {
        return entityData.get(DATA_STRIKES);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        setDeltaMovement(Vec3.ZERO);
        setYHeadRot(getYRot());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(level() instanceof ServerLevel server)
                || !(source.getEntity() instanceof Player player)) {
            return false;
        }
        ItemStack tool = player.getMainHandItem();
        if (!tool.is(ItemTags.PICKAXES)
                && !(tool.getItem() instanceof PickaxeItem)) {
            return false;
        }
        if (server.getGameTime() < nextStrikeGameTime) {
            return false;
        }
        nextStrikeGameTime = server.getGameTime()
                + BloomSporePolicy.CORPSE_STRIKE_COOLDOWN_TICKS;
        if (!player.getAbilities().instabuild) {
            tool.hurtAndBreak(1, player,
                    LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        }
        int strikes = Math.min(BloomSporePolicy.CORPSE_PICKAXE_HITS,
                acceptedStrikes() + 1);
        entityData.set(DATA_STRIKES, strikes);
        playSound(ModSounds.BLOOM_SPORE_CORPSE_STRIKE.get(), 0.82F,
                0.88F + strikes * 0.012F);
        server.sendParticles(ParticleTypes.WAX_OFF,
                getX(), getY() + 0.35D, getZ(),
                8, 0.35D, 0.18D, 0.55D, 0.035D);
        if (strikes >= BloomSporePolicy.CORPSE_PICKAXE_HITS) {
            shatter(server);
        }
        return true;
    }

    private void shatter(ServerLevel level) {
        nodeId().ifPresent(id -> BloomSporeManager.removeNode(level, id));
        spawnAtLocation(new ItemStack(ModItems.SPENT_LATTICE.get(), 3));
        playSound(ModSounds.BLOOM_SPORE_CORPSE_BREAK.get(), 1.15F, 0.82F);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                getX(), getY() + 0.35D, getZ(),
                52, 0.62D, 0.35D, 0.82D, 0.10D);
        discard();
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        nodeId().ifPresent(id -> tag.putUUID("nodeId", id));
        tag.putInt("acceptedStrikes", acceptedStrikes());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_NODE_ID, tag.hasUUID("nodeId")
                ? Optional.of(tag.getUUID("nodeId")) : Optional.empty());
        entityData.set(DATA_STRIKES, Math.max(0, Math.min(
                BloomSporePolicy.CORPSE_PICKAXE_HITS,
                tag.getInt("acceptedStrikes"))));
        setNoGravity(true);
        noPhysics = true;
    }
}
