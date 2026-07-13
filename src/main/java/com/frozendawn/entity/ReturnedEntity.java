package com.frozendawn.entity;

import com.frozendawn.entity.ai.ReturnedBreakLightGoal;
import com.frozendawn.entity.ai.ReturnedExtinguishHeaterGoal;
import com.frozendawn.entity.ai.ReturnedHearthWatchGoal;
import com.frozendawn.entity.ai.ReturnedHostileStrollGoal;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthWatcherPolicy;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public class ReturnedEntity extends Monster {

    private static final EntityDataAccessor<Integer> DATA_TEXTURE_VARIANT =
            SynchedEntityData.defineId(ReturnedEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEATH_TICKS =
            SynchedEntityData.defineId(ReturnedEntity.class, EntityDataSerializers.INT);

    private int despawnTimer = 0;
    private static final int DESPAWN_TIMEOUT = 6000; // 5 minutes
    @Nullable
    private UUID hearthId;
    @Nullable
    private BlockPos hearthCenter;

    public ReturnedEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // Enable door interaction for BreakDoorGoal
        this.getNavigation().getNodeEvaluator().setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TEXTURE_VARIANT, 0);
        builder.define(DATA_DEATH_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReturnedExtinguishHeaterGoal(this));
        this.goalSelector.addGoal(2, new OpenDoorGoal(this, false));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(4, new ReturnedHearthWatchGoal(this));
        this.goalSelector.addGoal(5, new ReturnedBreakLightGoal(this));
        this.goalSelector.addGoal(6, new ReturnedHostileStrollGoal(this, 1.0, 40));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true,
                candidate -> canProactivelyTargetPlayer(candidate)));
    }

    // --- Spawn Setup ---

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setTextureVariant(level.getRandom().nextInt(5));
        return data;
    }

    // --- Texture Variant ---

    public int getTextureVariant() {
        return entityData.get(DATA_TEXTURE_VARIANT);
    }

    public void setTextureVariant(int variant) {
        entityData.set(DATA_TEXTURE_VARIANT, variant);
    }

    public void bindToHearth(UUID id, BlockPos center, int textureVariant) {
        hearthId = id;
        hearthCenter = center.immutable();
        setTextureVariant(textureVariant);
        setPersistenceRequired();
        restrictTo(hearthCenter, HearthWatcherPolicy.HOME_RADIUS);
        setTarget(null);
        getNavigation().stop();
        despawnTimer = 0;
    }

    public boolean isHearthBound() {
        return hearthId != null && hearthCenter != null;
    }

    public boolean isBoundToHearth(UUID id) {
        return id != null && id.equals(hearthId);
    }

    public Optional<UUID> getHearthId() {
        return Optional.ofNullable(hearthId);
    }

    public Optional<BlockPos> getHearthCenter() {
        return Optional.ofNullable(hearthCenter);
    }

    // --- Death Animation ---

    public int getDeathTicks() {
        return entityData.get(DATA_DEATH_TICKS);
    }

    @Override
    protected void tickDeath() {
        int ticks = getDeathTicks() + 1;
        entityData.set(DATA_DEATH_TICKS, ticks);
        if (ticks >= 30) {
            remove(RemovalReason.KILLED);
            if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        getX(), getY() + 1.0, getZ(), 20,
                        0.4, 0.8, 0.4, 0.1);
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                        getX(), getY() + 0.5, getZ(), 10,
                        0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    // --- Combat ---

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1)); // Slowness II for 3s
            living.setTicksFrozen(living.getTicksFrozen() + 60);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FREEZING)) return false;
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 1.5f;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && isHearthBound()
                && level() instanceof ServerLevel serverLevel
                && source.getEntity() instanceof ServerPlayer attacker) {
            HearthMemoryManager.recordWatcherAttack(serverLevel, this, attacker);
        }
        return hurt;
    }

    private boolean canProactivelyTargetPlayer(LivingEntity candidate) {
        if (!isHearthBound()) {
            return true;
        }
        if (!(candidate instanceof ServerPlayer player)
                || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return HearthWatcherPolicy.canProactivelyTargetPlayer(true,
                HearthMemoryManager.relationship(serverLevel, player.getUUID()));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && source.getEntity() instanceof ServerPlayer killer) {
            WorldTickHandler.grantAdvancement(killer, "returned_killed");
        }
    }

    // --- AI Step (heater burn, despawn timer) ---

    @Override
    public void aiStep() {
        clearDeescalatedHearthAggression();
        super.aiStep();

        if (!level().isClientSide()) {
            long gameTick = level().getGameTime();

            // Heater burn: same as Frostbitten — 3 damage/sec within 4 blocks, check every 20 ticks
            if (gameTick % 20 == 0) {
                Set<BlockPos> heaters = HeaterRegistry.getHeaters(level());
                for (BlockPos heaterPos : heaters) {
                    if (blockPosition().closerToCenterThan(heaterPos.getCenter(), 4.0)) {
                        hurt(damageSources().onFire(), 3.0f);
                        setRemainingFireTicks(40);
                        break;
                    }
                }
            }

            // Hearth watchers are persistent. Ordinary Returned retain the custom timer.
            if (isHearthBound()) {
                despawnTimer = 0;
            } else if (getTarget() == null) {
                boolean playerNearby = !level().getEntitiesOfClass(Player.class,
                        getBoundingBox().inflate(48.0),
                        p -> !p.isSpectator()).isEmpty();
                if (playerNearby) {
                    despawnTimer = 0;
                } else {
                    despawnTimer++;
                    if (despawnTimer >= DESPAWN_TIMEOUT) {
                        discard();
                    }
                }
            } else {
                despawnTimer = 0;
            }
        }
    }

    private void clearDeescalatedHearthAggression() {
        if (!isHearthBound() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean navigationStopped = false;
        if (getTarget() instanceof ServerPlayer player
                && !HearthMemoryManager.isPermanentOrsathae(serverLevel, player.getUUID())) {
            setTarget(null);
            navigationStopped = true;
        }
        if (getLastHurtByMob() instanceof ServerPlayer player
                && !HearthMemoryManager.isPermanentOrsathae(serverLevel, player.getUUID())) {
            setLastHurtByMob(null);
            navigationStopped = true;
        }
        if (navigationStopped) {
            getNavigation().stop();
        }
    }

    // --- Immunities ---

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public int getTicksFrozen() {
        return 0;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (effectInstance.getEffect() == MobEffects.MOVEMENT_SLOWDOWN) return false;
        return super.canBeAffected(effectInstance);
    }

    // --- Sounds ---

    @Override
    public float getVoicePitch() {
        return 0.5f + random.nextFloat() * 0.15f;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.RETURNED_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RETURNED_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RETURNED_DEATH.get();
    }

    // --- NBT Persistence ---

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TextureVariant", getTextureVariant());
        tag.putInt("DespawnTimer", despawnTimer);
        if (hearthId != null && hearthCenter != null) {
            tag.putUUID("HearthId", hearthId);
            tag.putLong("HearthCenter", hearthCenter.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTextureVariant(tag.getInt("TextureVariant"));
        despawnTimer = tag.getInt("DespawnTimer");
        if (tag.hasUUID("HearthId") && tag.contains("HearthCenter")) {
            hearthId = tag.getUUID("HearthId");
            hearthCenter = BlockPos.of(tag.getLong("HearthCenter"));
            setPersistenceRequired();
            restrictTo(hearthCenter, HearthWatcherPolicy.HOME_RADIUS);
            despawnTimer = 0;
        } else {
            hearthId = null;
            hearthCenter = null;
        }
    }

    // --- Prevent natural despawn (custom timer handles it) ---

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void checkDespawn() {
        // Custom despawn in aiStep(), skip vanilla despawn logic
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return true;
    }
}
