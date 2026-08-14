package com.frozendawn.entity;

import com.frozendawn.aggregate.AggregateAction;
import com.frozendawn.aggregate.AggregateCombatController;
import com.frozendawn.aggregate.AggregateEncounterManager;
import com.frozendawn.aggregate.AggregateLineage;
import com.frozendawn.aggregate.AggregatePhase;
import com.frozendawn.aggregate.AggregateSavedData;
import com.frozendawn.init.ModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The once-per-world body assembled from post-Maeve convergence pressure. */
public final class AggregateEntity extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_TICK =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_DURATION =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PRIMARY =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SECONDARY =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TERTIARY =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DOMINANT =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_DEATH_TICK =
            SynchedEntityData.defineId(AggregateEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.aggregate.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.aggregate.walk");
    private static final RawAnimation SWEEP = RawAnimation.begin().thenPlay("animation.aggregate.sweep");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.aggregate.slam");
    private static final RawAnimation LURCH = RawAnimation.begin().thenPlay("animation.aggregate.lurch");
    private static final RawAnimation LANCE = RawAnimation.begin().thenPlay("animation.aggregate.lance");
    private static final RawAnimation AWAKEN = RawAnimation.begin().thenPlayAndHold("animation.aggregate.awaken");
    private static final RawAnimation REALLOCATE = RawAnimation.begin().thenPlay("animation.aggregate.reallocate");
    private static final RawAnimation SHED = RawAnimation.begin().thenPlay("animation.aggregate.shed");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.aggregate.death");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final AggregateCombatController combatController = new AggregateCombatController();
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.frozendawn.aggregate"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int awakeningTicks;
    private int phaseTransitionTicks;
    private int deathPresentationTicks;

    public AggregateEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
        setPersistenceRequired();
        bossBar.setDarkenScreen(true);
        bossBar.setCreateWorldFog(true);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        // The navigational body stays narrow; only culling covers the dragged rear mass.
        return getBoundingBox().inflate(2.6D, 0.8D, 2.6D);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 1_300.0D)
                .add(Attributes.ATTACK_DAMAGE, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.STEP_HEIGHT, 1.5D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, AggregatePhase.AWAKENING.ordinal());
        builder.define(DATA_ACTION, AggregateAction.NONE.ordinal());
        builder.define(DATA_ACTION_TICK, 0);
        builder.define(DATA_ACTION_DURATION, 0);
        builder.define(DATA_PRIMARY, -1);
        builder.define(DATA_SECONDARY, -1);
        builder.define(DATA_TERTIARY, -1);
        builder.define(DATA_DOMINANT, false);
        builder.define(DATA_DEATH_TICK, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(4, new MoveTowardsTargetGoal(this, 1.0D, 64.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true, AggregateEntity::combatPlayer));
    }

    public void initialize(float maxHealth, List<AggregateLineage> traits,
                           @Nullable AggregateLineage dominant) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        setHealth(maxHealth);
        writeTraits(traits, dominant != null);
        combatController.configure(traits);
        setPhase(AggregatePhase.AWAKENING);
        awakeningTicks = 0;
        setNoAi(true);
    }

    public AggregatePhase phase() {
        return enumValue(AggregatePhase.values(), entityData.get(DATA_PHASE),
                AggregatePhase.AWAKENING);
    }

    public AggregateAction action() {
        return enumValue(AggregateAction.values(), entityData.get(DATA_ACTION),
                AggregateAction.NONE);
    }

    public int actionTick() {
        return entityData.get(DATA_ACTION_TICK);
    }

    public List<AggregateLineage> traits() {
        List<AggregateLineage> values = new ArrayList<>(3);
        addTrait(values, entityData.get(DATA_PRIMARY));
        addTrait(values, entityData.get(DATA_SECONDARY));
        addTrait(values, entityData.get(DATA_TERTIARY));
        return List.copyOf(values);
    }

    public boolean dominantUpgrade() {
        return entityData.get(DATA_DOMINANT);
    }

    public boolean hasDominantTrait(AggregateLineage lineage) {
        return dominantUpgrade() && !traits().isEmpty() && traits().getFirst() == lineage;
    }

    public int aggregateDeathTick() {
        return entityData.get(DATA_DEATH_TICK);
    }

    public int activeTraitCount() {
        return com.frozendawn.aggregate.AggregateCombatPolicy.activeTraitCount(
                phase(), traits().size(), dominantUpgrade());
    }

    public void restorePhase(AggregatePhase phase) {
        AggregatePhase safe = phase == AggregatePhase.DYING
                || phase == AggregatePhase.DEAD ? AggregatePhase.CONVERGENCE_FAILURE : phase;
        setPhase(safe);
        setNoAi(safe == AggregatePhase.AWAKENING
                || safe == AggregatePhase.REALLOCATION
                || safe == AggregatePhase.FAILURE_TRANSITION);
    }

    public void beginAction(AggregateAction action, int duration) {
        if (this.action() != AggregateAction.NONE || phase() == AggregatePhase.DYING) return;
        entityData.set(DATA_ACTION, action.ordinal());
        entityData.set(DATA_ACTION_TICK, 0);
        entityData.set(DATA_ACTION_DURATION, Math.max(1, duration));
        getNavigation().stop();
    }

    public void debugForceAction(AggregateAction action) {
        entityData.set(DATA_ACTION, AggregateAction.NONE.ordinal());
        beginAction(action, switch (action) {
            case SWEEP -> 42;
            case SLAM -> 54;
            case LURCH, RIMEBOUND_RUSH, RIMEBOUND_LANCE -> 48;
            case RESONANCE_PULSE -> 54;
            case FALSE_OPENING -> 64;
            case DISASSEMBLY -> 88;
            case ACCRETION_CONSTRUCTION -> 72;
            case REALLOCATION_BEAT -> 20;
            case NONE -> 1;
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel server = (ServerLevel) level();
        clearInvalidTarget();
        updateBossBar();

        if (phase() == AggregatePhase.DYING || phase() == AggregatePhase.DEAD) {
            return;
        }

        if (phase() == AggregatePhase.AWAKENING) {
            tickAwakening(server);
            snapshot(server);
            return;
        }
        if (phase() == AggregatePhase.REALLOCATION
                || phase() == AggregatePhase.FAILURE_TRANSITION) {
            tickPhaseTransition(server);
            snapshot(server);
            return;
        }
        updateCombatPhase(server);
        tickFailureShedding(server);
        tickActionClock();
        combatController.tick(server, this);
        snapshot(server);
    }

    private void tickAwakening(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (++awakeningTicks == 1) {
            playSound(ModSounds.AGGREGATE_AWAKEN.get(), 3.4F, 0.62F);
        }
        if (awakeningTicks % 6 == 0) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                            level.getBlockState(blockPosition().below())),
                    getX(), getY() + 1.0D, getZ(), 22,
                    2.7D, 1.2D, 2.7D, 0.12D);
        }
        if (awakeningTicks >= 100) {
            setNoAi(false);
            setPhase(AggregatePhase.COHERENT);
        }
    }

    private void updateCombatPhase(ServerLevel level) {
        float healthFraction = getHealth() / Math.max(1.0F, getMaxHealth());
        if (phase() == AggregatePhase.COHERENT && healthFraction <= 0.70F) {
            beginPhaseTransition(AggregatePhase.REALLOCATION);
            playSound(ModSounds.AGGREGATE_REALLOCATION.get(), 3.0F, 0.58F);
        } else if (phase() == AggregatePhase.REALLOCATED && healthFraction <= 0.35F) {
            beginPhaseTransition(AggregatePhase.FAILURE_TRANSITION);
            playSound(ModSounds.AGGREGATE_SHEDDING.get(), 3.0F, 0.67F);
        } else if (phase() == AggregatePhase.REALLOCATED && healthFraction <= 0.55F
                && !getPersistentData().getBoolean("midfightReallocationPlayed")
                && action() == AggregateAction.NONE) {
            getPersistentData().putBoolean("midfightReallocationPlayed", true);
            beginAction(AggregateAction.REALLOCATION_BEAT, 20);
            playSound(ModSounds.AGGREGATE_REALLOCATION.get(), 2.7F, 0.72F);
        }
    }

    private void tickFailureShedding(ServerLevel level) {
        if (phase() != AggregatePhase.CONVERGENCE_FAILURE || tickCount % 5 != 0) return;
        level.sendParticles(ParticleTypes.WHITE_ASH,
                getX(), getY() + 1.1D, getZ(), 5,
                1.7D, 0.9D, 1.7D, 0.035D);
    }

    private void beginPhaseTransition(AggregatePhase phase) {
        setPhase(phase);
        phaseTransitionTicks = 0;
        setNoAi(true);
        getNavigation().stop();
        entityData.set(DATA_ACTION, AggregateAction.NONE.ordinal());
    }

    private void tickPhaseTransition(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        phaseTransitionTicks++;
        if (phaseTransitionTicks % 4 == 0) {
            level.sendParticles(ParticleTypes.WHITE_ASH,
                    getX(), getY() + 1.4D, getZ(), 16,
                    2.3D, 1.4D, 2.3D, 0.06D);
        }
        if (hasDominantTrait(AggregateLineage.UNDONE)
                && phaseTransitionTicks == 10) {
            level.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + 1.2D, getZ(), 72,
                    3.5D, 1.8D, 3.5D, 0.16D);
            for (Player player : level.getEntitiesOfClass(Player.class,
                    getBoundingBox().inflate(7.0D), AggregateEntity::combatPlayer)) {
                Vec3 shove = player.position().subtract(position())
                        .multiply(1.0D, 0.0D, 1.0D).normalize().scale(0.65D);
                player.push(shove.x, 0.2D, shove.z);
            }
        }
        if (phaseTransitionTicks < 20) return;
        if (phase() == AggregatePhase.REALLOCATION) {
            setPhase(AggregatePhase.REALLOCATED);
        } else {
            setPhase(AggregatePhase.CONVERGENCE_FAILURE);
            getAttribute(Attributes.ARMOR).setBaseValue(6.0D);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.31D);
        }
        setNoAi(false);
    }

    private void tickActionClock() {
        if (action() == AggregateAction.NONE) return;
        AggregateAction active = action();
        int tick = actionTick() + 1;
        entityData.set(DATA_ACTION_TICK, tick);
        if (tick >= entityData.get(DATA_ACTION_DURATION)) {
            entityData.set(DATA_ACTION, AggregateAction.NONE.ordinal());
            entityData.set(DATA_ACTION_TICK, 0);
            entityData.set(DATA_ACTION_DURATION, 0);
            if (active == AggregateAction.RIMEBOUND_RUSH) {
                setRimeboundSubmerged(false);
            }
        }
    }

    public void setRimeboundSubmerged(boolean submerged) {
        noPhysics = submerged;
        setInvisible(submerged);
        if (!submerged) setDeltaMovement(Vec3.ZERO);
    }

    private void snapshot(ServerLevel level) {
        if (tickCount % 10 != 0) return;
        AggregateSavedData.get(level.getServer()).snapshotFight(
                getUUID(), blockPosition(), getHealth(), getMaxHealth(), phase());
    }

    private void updateBossBar() {
        bossBar.setProgress(Mth.clamp(getHealth() / Math.max(1.0F, getMaxHealth()),
                0.0F, 1.0F));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (phase() == AggregatePhase.AWAKENING
                || phase() == AggregatePhase.REALLOCATION
                || phase() == AggregatePhase.FAILURE_TRANSITION
                || phase() == AggregatePhase.DYING) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (phase() == AggregatePhase.DYING || phase() == AggregatePhase.DEAD) return;
        setPhase(AggregatePhase.DYING);
        setNoAi(true);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        playSound(ModSounds.AGGREGATE_DEATH.get(), 4.0F, 0.58F);
        if (level() instanceof ServerLevel server) {
            AggregateSavedData.get(server.getServer()).snapshotFight(
                    getUUID(), blockPosition(), 0.0F, getMaxHealth(), AggregatePhase.DYING);
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        deathPresentationTicks++;
        entityData.set(DATA_DEATH_TICK, deathPresentationTicks);
        deathTime = deathPresentationTicks;
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            if (deathPresentationTicks == 24) {
                playSound(ModSounds.AGGREGATE_DEATH_LINEAGE.get(), 3.0F, 0.74F);
            }
            if (deathPresentationTicks == 48) {
                playSound(ModSounds.AGGREGATE_DEATH_LINEAGE.get(), 2.8F, 0.56F);
            }
            if (deathPresentationTicks % 4 == 0) {
                server.sendParticles(deathPresentationTicks < 55
                                ? ParticleTypes.WHITE_ASH : ParticleTypes.POOF,
                        getX(), getY() + 1.2D, getZ(), 18,
                        2.6D, 1.5D, 2.6D, 0.08D);
            }
            if (deathPresentationTicks >= 90) {
                setPhase(AggregatePhase.DEAD);
                AggregateEncounterManager.resolve(server, this);
                remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source,
                                       boolean recentlyHit) {
        // The staged resolution owns the unique reward and XP exactly once.
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(5) == 0 ? ModSounds.AGGREGATE_AMBIENT.get() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.AGGREGATE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && !effect.is(MobEffects.WITHER)
                && super.canBeAffected(effect);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        bossBar.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("aggregatePhase", phase().ordinal());
        tag.putInt("aggregateAction", action().ordinal());
        tag.putInt("actionTick", actionTick());
        tag.putInt("actionDuration", entityData.get(DATA_ACTION_DURATION));
        tag.putInt("primary", entityData.get(DATA_PRIMARY));
        tag.putInt("secondary", entityData.get(DATA_SECONDARY));
        tag.putInt("tertiary", entityData.get(DATA_TERTIARY));
        tag.putBoolean("dominant", dominantUpgrade());
        tag.putInt("awakeningTicks", awakeningTicks);
        tag.putInt("phaseTransitionTicks", phaseTransitionTicks);
        tag.putInt("deathPresentationTicks", aggregateDeathTick());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPhase(enumValue(AggregatePhase.values(), tag.getInt("aggregatePhase"),
                AggregatePhase.COHERENT));
        entityData.set(DATA_ACTION, tag.getInt("aggregateAction"));
        entityData.set(DATA_ACTION_TICK, Math.max(0, tag.getInt("actionTick")));
        entityData.set(DATA_ACTION_DURATION, Math.max(0, tag.getInt("actionDuration")));
        entityData.set(DATA_PRIMARY, tag.getInt("primary"));
        entityData.set(DATA_SECONDARY, tag.getInt("secondary"));
        entityData.set(DATA_TERTIARY, tag.getInt("tertiary"));
        entityData.set(DATA_DOMINANT, tag.getBoolean("dominant"));
        awakeningTicks = Math.max(0, tag.getInt("awakeningTicks"));
        phaseTransitionTicks = Math.max(0, tag.getInt("phaseTransitionTicks"));
        deathPresentationTicks = Math.max(0, tag.getInt("deathPresentationTicks"));
        entityData.set(DATA_DEATH_TICK, deathPresentationTicks);
        combatController.configure(traits());
        setRimeboundSubmerged(false);
        if (action() == AggregateAction.RIMEBOUND_RUSH) {
            entityData.set(DATA_ACTION, AggregateAction.NONE.ordinal());
            entityData.set(DATA_ACTION_TICK, 0);
            entityData.set(DATA_ACTION_DURATION, 0);
        }
        if (phase() == AggregatePhase.AWAKENING
                || phase() == AggregatePhase.REALLOCATION
                || phase() == AggregatePhase.FAILURE_TRANSITION) setNoAi(true);
    }

    private void writeTraits(List<AggregateLineage> traits, boolean dominant) {
        entityData.set(DATA_PRIMARY, ordinalAt(traits, 0));
        entityData.set(DATA_SECONDARY, ordinalAt(traits, 1));
        entityData.set(DATA_TERTIARY, ordinalAt(traits, 2));
        entityData.set(DATA_DOMINANT, dominant);
    }

    private void setPhase(AggregatePhase phase) {
        entityData.set(DATA_PHASE, phase.ordinal());
    }

    private void clearInvalidTarget() {
        if (getTarget() instanceof Player player && !combatPlayer(player)) setTarget(null);
    }

    private static boolean combatPlayer(LivingEntity entity) {
        return entity instanceof Player player && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static int ordinalAt(List<AggregateLineage> values, int index) {
        return index < values.size() ? values.get(index).ordinal() : -1;
    }

    private static void addTrait(List<AggregateLineage> output, int ordinal) {
        if (ordinal >= 0 && ordinal < AggregateLineage.values().length) {
            output.add(AggregateLineage.values()[ordinal]);
        }
    }

    private static <T> T enumValue(T[] values, int ordinal, T fallback) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }

    private PlayState animationPredicate(AnimationState<AggregateEntity> state) {
        if (phase() == AggregatePhase.DYING || phase() == AggregatePhase.DEAD) {
            return state.setAndContinue(DEATH);
        }
        if (phase() == AggregatePhase.AWAKENING) return state.setAndContinue(AWAKEN);
        if (phase() == AggregatePhase.REALLOCATION) return state.setAndContinue(REALLOCATE);
        if (phase() == AggregatePhase.FAILURE_TRANSITION) return state.setAndContinue(SHED);
        return switch (action()) {
            case SWEEP -> state.setAndContinue(SWEEP);
            case SLAM -> state.setAndContinue(SLAM);
            case LURCH, RIMEBOUND_RUSH -> state.setAndContinue(LURCH);
            case RIMEBOUND_LANCE -> state.setAndContinue(LANCE);
            case RESONANCE_PULSE -> state.setAndContinue(REALLOCATE);
            case FALSE_OPENING -> state.setAndContinue(SWEEP);
            case DISASSEMBLY -> state.setAndContinue(SHED);
            case ACCRETION_CONSTRUCTION -> state.setAndContinue(SLAM);
            case REALLOCATION_BEAT -> state.setAndContinue(REALLOCATE);
            default -> state.setAndContinue(state.isMoving() ? WALK : IDLE);
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "aggregate_controller", 4,
                this::animationPredicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
