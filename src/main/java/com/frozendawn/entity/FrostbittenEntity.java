package com.frozendawn.entity;

import com.frozendawn.entity.ai.FrostbittenRangedAttackGoal;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.Set;

public class FrostbittenEntity extends Monster {

    private static final EntityDataAccessor<Boolean> DATA_EMERGING =
            SynchedEntityData.defineId(FrostbittenEntity.class, EntityDataSerializers.BOOLEAN);

    private int emergeTicks = 0;
    private static final int EMERGE_DURATION = 30; // 1.5 seconds

    private FrostbittenRangedAttackGoal rangedGoal;

    public FrostbittenEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_EMERGING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
        this.rangedGoal = new FrostbittenRangedAttackGoal(this);
        this.goalSelector.addGoal(2, rangedGoal);
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // --- Spawn Setup ---

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL));
        return data;
    }

    // --- Emerge Animation ---

    public boolean isEmerging() {
        return entityData.get(DATA_EMERGING);
    }

    public void setEmerging(boolean emerging) {
        entityData.set(DATA_EMERGING, emerging);
        if (emerging) {
            emergeTicks = 0;
            playSound(ModSounds.FROSTBITTEN_EMERGE.get(), 1.0f, 0.8f + random.nextFloat() * 0.4f);
        }
    }

    public int getEmergeTicks() {
        return emergeTicks;
    }

    @Override
    protected void customServerAiStep() {
        if (isEmerging()) {
            emergeTicks++;
            if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                        getX(), getY() + 0.5, getZ(), 2,
                        0.3, 0.5, 0.3, 0.02);
            }
            if (emergeTicks >= EMERGE_DURATION) {
                setEmerging(false);
            }
            return; // Suppress AI during emerge
        }
        super.customServerAiStep();

        if (rangedGoal != null) {
            rangedGoal.tickCooldown();
        }
    }

    // --- Combat ---

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0)); // Slowness I for 2s
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Immune to freeze damage
        if (source.is(DamageTypeTags.IS_FREEZING)) return false;
        // 2x fire damage
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 2.0f;
        }
        return super.hurt(source, amount);
    }

    // --- AI Step (heater burn, water freeze) ---

    @Override
    public void aiStep() {
        super.aiStep();

        if (!level().isClientSide()) {
            long gameTick = level().getGameTime();

            // Heater burn: check every 20 ticks
            if (gameTick % 20 == 0) {
                Set<BlockPos> heaters = HeaterRegistry.getHeaters(level());
                for (BlockPos heaterPos : heaters) {
                    if (blockPosition().closerToCenterThan(heaterPos.getCenter(), 4.0)) {
                        hurt(damageSources().onFire(), 2.0f);
                        setRemainingFireTicks(40);
                        break;
                    }
                }
            }

            // Water behavior: sink and freeze surrounding water every 10 ticks
            if (isInWater() && gameTick % 10 == 0) {
                BlockPos pos = blockPosition();
                for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                    if (level().getBlockState(nearby).is(Blocks.WATER)) {
                        level().setBlock(nearby, Blocks.ICE.defaultBlockState(), 3);
                    }
                }
            }

            // Ensure snowball in hand (backup for /summon which may skip finalizeSpawn)
            if (gameTick % 100 == 0 && getMainHandItem().isEmpty()) {
                setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SNOWBALL));
            }
        }
    }

    // --- Death ---

    @Override
    protected void tickDeath() {
        remove(RemovalReason.KILLED);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    getX(), getY() + 1.0, getZ(), 30,
                    0.4, 0.8, 0.4, 0.1);
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 1.0, getZ(), 15,
                    0.5, 0.8, 0.5, 0.05);
        }
    }

    // --- Sounds ---

    @Override
    public float getVoicePitch() {
        // Lower, more guttural than normal zombie — frozen vocal cords
        return 0.6f + random.nextFloat() * 0.2f;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FROSTBITTEN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.FROSTBITTEN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FROSTBITTEN_DEATH.get();
    }

    // --- Misc ---

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public int getTicksFrozen() {
        return 0; // Never accumulate freeze ticks
    }
}
