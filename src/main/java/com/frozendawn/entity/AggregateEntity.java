package com.frozendawn.entity;

import com.frozendawn.aggregate.AggregateAction;
import com.frozendawn.aggregate.AggregateCombatController;
import com.frozendawn.aggregate.AggregateEncounterManager;
import com.frozendawn.aggregate.AggregateLineage;
import com.frozendawn.aggregate.AggregatePhase;
import com.frozendawn.aggregate.AggregateSavedData;
import com.frozendawn.init.ModSounds;
import com.frozendawn.network.HearthBoundaryEffectPayload;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
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
import net.neoforged.neoforge.network.PacketDistributor;
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
    private static final RawAnimation RUSH = RawAnimation.begin().thenPlay("animation.aggregate.rush");
    private static final RawAnimation PULSE = RawAnimation.begin().thenPlay("animation.aggregate.pulse");
    private static final RawAnimation FALSE_OPENING = RawAnimation.begin()
            .thenPlay("animation.aggregate.false_opening");
    private static final RawAnimation DISASSEMBLE = RawAnimation.begin()
            .thenPlay("animation.aggregate.disassembly");
    private static final RawAnimation CONSTRUCT = RawAnimation.begin()
            .thenPlay("animation.aggregate.construction");
    private static final String ACTION_CONTROLLER = "aggregate_actions";

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
        setNoGravity(true);
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
        triggerActionAnimation(action);
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
        tickActionPresentation(server);
        combatController.tick(server, this);
        snapshot(server);
    }

    private void tickAwakening(ServerLevel level) {
        setDeltaMovement(Vec3.ZERO);
        if (++awakeningTicks == 1) {
            triggerAnim(ACTION_CONTROLLER, "awaken");
            playSound(ModSounds.AGGREGATE_AWAKEN.get(), 3.4F, 0.62F);
            sendLocalEffect(level, HearthBoundaryEffectPayload.aggregateFormationRumble(), 96.0D);
        }
        if (awakeningTicks <= 84 && awakeningTicks % 2 == 0) {
            double progress = awakeningTicks / 84.0D;
            double radius = Mth.lerp(progress, 10.5D, 2.4D);
            ParticleOptions material = awakeningTicks % 6 == 0
                    ? new BlockParticleOption(ParticleTypes.BLOCK,
                            com.frozendawn.init.ModBlocks.AGGREGATE_RIB.get()
                                    .defaultBlockState())
                    : new BlockParticleOption(ParticleTypes.BLOCK,
                            com.frozendawn.init.ModBlocks.AGGREGATE_MASS.get()
                                    .defaultBlockState());
            emitInwardStreams(level, material, radius,
                    0.4D + progress * 1.15D, 12, 0.16D + progress * 0.13D);
        }
        if (awakeningTicks % 5 == 0 && awakeningTicks < 88) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                            level.getBlockState(blockPosition().below())),
                    getX(), getY() + 0.25D, getZ(), 18,
                    3.8D, 0.25D, 3.8D, 0.09D);
        }
        if (awakeningTicks == 84) {
            sendLocalEffect(level, HearthBoundaryEffectPayload.aggregateImpact(), 96.0D);
            emitGroundImpact(level, 7.5D, 128);
            level.sendParticles(ParticleTypes.FLASH,
                    getX(), getY() + 1.1D, getZ(), 2,
                    0.4D, 0.25D, 0.4D, 0.0D);
        }
        if (awakeningTicks >= 84 && awakeningTicks <= 94
                && awakeningTicks % 2 == 0) {
            emitRing(level, ParticleTypes.POOF,
                    1.5D + (awakeningTicks - 84) * 0.72D, 0.2D, 30);
        }
        if (awakeningTicks >= 100) {
            setNoGravity(false);
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
        triggerAnim(ACTION_CONTROLLER, phase == AggregatePhase.REALLOCATION
                ? "reallocate" : "shed");
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

    private void tickActionPresentation(ServerLevel level) {
        int tick = actionTick();
        switch (action()) {
            case SWEEP -> {
                if (tick >= 8 && tick <= 24 && tick % 2 == 0) {
                    emitForwardArc(level, ParticleTypes.SNOWFLAKE,
                            2.2D + (tick - 8) * 0.18D, 0.8D, 13);
                }
            }
            case SLAM -> {
                if (tick == 12) {
                    playSound(ModSounds.AGGREGATE_SLAM_WINDUP.get(), 2.1F, 0.58F);
                }
                if (tick >= 9 && tick <= 31 && tick % 3 == 0) {
                    double radius = 7.2D - (tick - 9) * 0.25D;
                    emitRing(level, ParticleTypes.WHITE_ASH, radius, 0.18D, 22);
                }
                if (tick >= 12 && tick <= 31 && tick % 2 == 0) {
                    emitInwardStreams(level,
                            new BlockParticleOption(ParticleTypes.BLOCK,
                                    level.getBlockState(blockPosition().below())),
                            7.0D - (tick - 12) * 0.22D, 0.25D,
                            10, 0.2D);
                }
                if (tick == 32) {
                    playSound(ModSounds.AGGREGATE_SLAM.get(), 3.2F, 0.54F);
                    sendLocalEffect(level, HearthBoundaryEffectPayload.aggregateImpact(), 72.0D);
                    emitGroundImpact(level, 9.0D, 144);
                    level.sendParticles(ParticleTypes.FLASH,
                            getX(), getY() + 0.35D, getZ(), 1,
                            0.0D, 0.0D, 0.0D, 0.0D);
                }
                if (tick >= 32 && tick <= 40 && tick % 2 == 0) {
                    emitRing(level, ParticleTypes.POOF,
                            1.2D + (tick - 32) * 1.1D, 0.18D, 34);
                }
            }
            case LURCH -> {
                if (tick >= 10 && tick <= 25) {
                    level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.45D, getZ(),
                            5, 1.5D, 0.25D, 1.5D, 0.025D);
                }
            }
            case RIMEBOUND_RUSH -> {
                if (tick >= 8 && tick <= 36 && tick % 2 == 0) {
                    emitRing(level, ParticleTypes.ITEM_SNOWBALL, 1.8D, 0.08D, 16);
                }
            }
            case RIMEBOUND_LANCE -> {
                if (tick >= 8 && tick <= 27 && tick % 2 == 0) {
                    level.sendParticles(ParticleTypes.SNOWFLAKE,
                            getX(), getY() + 2.25D, getZ() - 0.4D,
                            10, 1.2D, 0.8D, 1.2D, 0.035D);
                }
            }
            case RESONANCE_PULSE -> {
                if (tick >= 8 && tick <= 30 && tick % 4 == 2) {
                    emitRing(level, ParticleTypes.ELECTRIC_SPARK,
                            1.0D + tick * 0.16D, 1.25D, 28);
                }
            }
            case FALSE_OPENING -> {
                if (tick >= 8 && tick <= 34 && tick % 3 == 0) {
                    emitRing(level, ParticleTypes.LARGE_SMOKE,
                            2.1D + tick * 0.035D, 0.9D, 18);
                }
            }
            case DISASSEMBLY -> {
                if (tick >= 9 && tick <= 35 && tick % 3 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                    com.frozendawn.init.ModBlocks.AGGREGATE_MASS.get()
                                            .defaultBlockState()),
                            getX(), getY() + 1.3D, getZ(), 18,
                            2.2D, 1.1D, 2.2D, 0.11D);
                }
            }
            case ACCRETION_CONSTRUCTION -> {
                if (tick >= 8 && tick <= 34 && tick % 3 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                    com.frozendawn.init.ModBlocks.AGGREGATE_RIB.get()
                                            .defaultBlockState()),
                            getX(), getY() + 0.35D, getZ(), 14,
                            2.6D, 0.35D, 2.6D, 0.07D);
                }
            }
            case REALLOCATION_BEAT -> {
                if (tick % 3 == 0) {
                    emitRing(level, ParticleTypes.WHITE_ASH,
                            1.4D + tick * 0.12D, 1.1D, 18);
                }
            }
            case NONE -> {
            }
        }
    }

    private void emitRing(ServerLevel level, ParticleOptions particle, double radius,
                          double height, int points) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            level.sendParticles(particle,
                    getX() + Math.cos(angle) * radius,
                    getY() + height,
                    getZ() + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void emitInwardStreams(ServerLevel level, ParticleOptions particle,
                                   double radius, double targetHeight, int points,
                                   double speed) {
        double phase = tickCount * 0.19D;
        Vec3 target = new Vec3(getX(), getY() + targetHeight, getZ());
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2.0D * i / points;
            double vertical = getY() + 0.2D + ((i * 7) % 11) * 0.23D;
            Vec3 source = new Vec3(
                    getX() + Math.cos(angle) * radius,
                    vertical,
                    getZ() + Math.sin(angle) * radius);
            Vec3 velocity = target.subtract(source).normalize().scale(speed);
            level.sendParticles(particle, source.x, source.y, source.z,
                    0, velocity.x, velocity.y, velocity.z, 1.0D);
        }
    }

    private void emitGroundImpact(ServerLevel level, double radius, int debrisCount) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        level.getBlockState(blockPosition().below())),
                getX(), getY() + 0.12D, getZ(), debrisCount,
                radius * 0.52D, 0.35D, radius * 0.52D, 0.24D);
        level.sendParticles(ParticleTypes.CLOUD,
                getX(), getY() + 0.12D, getZ(), Math.max(24, debrisCount / 3),
                radius * 0.45D, 0.18D, radius * 0.45D, 0.12D);
        for (int ring = 0; ring < 3; ring++) {
            emitRing(level, ring == 1 ? ParticleTypes.WHITE_ASH : ParticleTypes.POOF,
                    radius * (0.36D + ring * 0.23D), 0.14D + ring * 0.05D,
                    30 + ring * 8);
        }
    }

    private void sendLocalEffect(ServerLevel level, HearthBoundaryEffectPayload payload,
                                 double radius) {
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) <= radiusSqr) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private void emitForwardArc(ServerLevel level, ParticleOptions particle, double radius,
                                double height, int points) {
        double facing = Math.toRadians(getYRot()) + Math.PI / 2.0D;
        for (int i = 0; i < points; i++) {
            double spread = Mth.lerp(i / (double) (points - 1), -1.9D, 1.9D);
            double angle = facing + spread;
            level.sendParticles(particle,
                    getX() + Math.cos(angle) * radius,
                    getY() + height,
                    getZ() + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void triggerActionAnimation(AggregateAction action) {
        if (level().isClientSide || action == AggregateAction.NONE) return;
        String trigger = switch (action) {
            case SWEEP -> "sweep";
            case SLAM -> "slam";
            case LURCH -> "lurch";
            case RIMEBOUND_RUSH -> "rush";
            case RIMEBOUND_LANCE -> "lance";
            case RESONANCE_PULSE -> "pulse";
            case FALSE_OPENING -> "false_opening";
            case DISASSEMBLY -> "disassembly";
            case ACCRETION_CONSTRUCTION -> "construction";
            case REALLOCATION_BEAT -> "reallocate";
            case NONE -> "";
        };
        triggerAnim(ACTION_CONTROLLER, trigger);
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
        setNoGravity(true);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        triggerAnim(ACTION_CONTROLLER, "death");
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
                || phase() == AggregatePhase.FAILURE_TRANSITION) {
            setNoAi(true);
        }
        setNoGravity(phase() == AggregatePhase.AWAKENING
                || phase() == AggregatePhase.DYING);
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

    private PlayState locomotionPredicate(AnimationState<AggregateEntity> state) {
        if (phase() == AggregatePhase.AWAKENING
                || phase() == AggregatePhase.REALLOCATION
                || phase() == AggregatePhase.FAILURE_TRANSITION
                || phase() == AggregatePhase.DYING
                || phase() == AggregatePhase.DEAD
                || action() != AggregateAction.NONE) {
            return PlayState.STOP;
        }
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "aggregate_locomotion", 5,
                this::locomotionPredicate));
        controllers.add(new AnimationController<>(this, ACTION_CONTROLLER, 1,
                state -> PlayState.STOP)
                .triggerableAnim("awaken", AWAKEN)
                .triggerableAnim("sweep", SWEEP)
                .triggerableAnim("slam", SLAM)
                .triggerableAnim("lurch", LURCH)
                .triggerableAnim("rush", RUSH)
                .triggerableAnim("lance", LANCE)
                .triggerableAnim("pulse", PULSE)
                .triggerableAnim("false_opening", FALSE_OPENING)
                .triggerableAnim("disassembly", DISASSEMBLE)
                .triggerableAnim("construction", CONSTRUCT)
                .triggerableAnim("reallocate", REALLOCATE)
                .triggerableAnim("shed", SHED)
                .triggerableAnim("death", DEATH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
