package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.AggregateAction;
import com.frozendawn.aggregate.AggregateCombatController;
import com.frozendawn.aggregate.AggregateEncounterManager;
import com.frozendawn.aggregate.AggregateLineage;
import com.frozendawn.aggregate.AggregatePhase;
import com.frozendawn.aggregate.AggregateSavedData;
import com.frozendawn.aggregate.AggregateDischargePolicy;
import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.init.ModParticles;
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
import java.util.UUID;

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
    private static final EntityDataAccessor<Integer> DATA_DISCHARGE_SCARS =
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
    private static final RawAnimation DISCHARGE = RawAnimation.begin()
            .thenPlay("animation.aggregate.discharge");
    private static final String ACTION_CONTROLLER = "aggregate_actions";

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final AggregateCombatController combatController = new AggregateCombatController();
    private final ServerBossEvent bossBar = new ServerBossEvent(
            Component.translatable("entity.frozendawn.aggregate"),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
    private int awakeningTicks;
    private int phaseTransitionTicks;
    private int deathPresentationTicks;
    private int currentDischargeWave = -1;
    private float dischargeInterruptDamage;
    private int armorVulnerabilityTicks;
    private int ambientRoarCooldown = 80;
    @Nullable
    private UUID coreMeteorId;
    private boolean coreMeteorReleased;
    private boolean coreMeteorImpactPlayed;

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
        builder.define(DATA_DISCHARGE_SCARS, 0);
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

    public int dischargeScars() {
        return entityData.get(DATA_DISCHARGE_SCARS);
    }

    public void restoreDischargeScars(int scars) {
        entityData.set(DATA_DISCHARGE_SCARS, Math.clamp(scars, 0, 2));
    }

    public int currentDischargeWave() {
        return currentDischargeWave;
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

    public boolean debugForceAction(AggregateAction action) {
        entityData.set(DATA_ACTION, AggregateAction.NONE.ordinal());
        if (action == AggregateAction.CONVERGENCE_DISCHARGE
                && level() instanceof ServerLevel server) {
            AggregateSavedData data = AggregateSavedData.get(server.getServer());
            ensureDebugDischargeTraits();
            if (data.dischargeSpent(AggregateDischargePolicy.PRIMARY_WAVE)
                    && data.dischargeSpent(AggregateDischargePolicy.SECONDARY_WAVE)) {
                AggregateReinforcementManager.cleanupLoaded(server, data);
                data.debugResetDischarges();
            }
            int wave = data.dischargeSpent(AggregateDischargePolicy.PRIMARY_WAVE)
                    ? AggregateDischargePolicy.SECONDARY_WAVE
                    : AggregateDischargePolicy.PRIMARY_WAVE;
            if (beginConvergenceDischarge(server, wave)) return true;
            if (wave == AggregateDischargePolicy.SECONDARY_WAVE) {
                AggregateReinforcementManager.cleanupLoaded(server, data);
                data.debugResetDischarges();
                return beginConvergenceDischarge(server,
                        AggregateDischargePolicy.PRIMARY_WAVE);
            }
            return false;
        }
        beginAction(action, switch (action) {
            case SWEEP -> 42;
            case SLAM -> 54;
            case LURCH, RIMEBOUND_RUSH, RIMEBOUND_LANCE -> 48;
            case RESONANCE_PULSE -> 54;
            case FALSE_OPENING -> 64;
            case DISASSEMBLY -> 88;
            case ACCRETION_CONSTRUCTION -> 72;
            case REALLOCATION_BEAT -> 60;
            case CONVERGENCE_DISCHARGE -> AggregateDischargePolicy.WINDUP_TICKS;
            case NONE -> 1;
        });
        return this.action() == action;
    }

    private void ensureDebugDischargeTraits() {
        List<AggregateLineage> current = new ArrayList<>(traits());
        for (AggregateLineage fallback : List.of(
                AggregateLineage.RIMEBOUND,
                AggregateLineage.RESONANT,
                AggregateLineage.REMNANT)) {
            if (current.size() >= 3) break;
            if (!current.contains(fallback)) current.add(fallback);
        }
        writeTraits(current, dominantUpgrade());
        combatController.configure(traits());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel server = (ServerLevel) level();
        clearInvalidTarget();
        updateBossBar();
        tickAmbientRoar();

        if (phase() == AggregatePhase.DYING || phase() == AggregatePhase.DEAD) {
            return;
        }

        // Discharge is an overriding body state. Debug forcing and save recovery may
        // begin it while another phase owns the normal tick path, so it must tick first.
        if (action() == AggregateAction.CONVERGENCE_DISCHARGE) {
            tickArmorVulnerability();
            tickActionClock();
            tickActionPresentation(server);
            snapshot(server);
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
        tickArmorVulnerability();
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
        boolean failureRupture = phase() == AggregatePhase.FAILURE_TRANSITION;
        boolean primaryReallocation = phase() == AggregatePhase.REALLOCATION;
        if (primaryReallocation && phaseTransitionTicks <= 44
                && phaseTransitionTicks % 2 == 0) {
            double progress = phaseTransitionTicks / 44.0D;
            emitInwardStreams(level, ModParticles.AGGREGATE_CONVERGENCE.get(),
                    Mth.lerp(progress, 7.5D, 1.25D), 1.1D + progress * 0.55D,
                    14, 0.16D + progress * 0.12D);
        }
        if (primaryReallocation && phaseTransitionTicks == 45) {
            level.sendParticles(ParticleTypes.FLASH,
                    getX(), getY() + 1.5D, getZ(), 1,
                    0.0D, 0.0D, 0.0D, 0.0D);
            emitRing(level, ModParticles.AGGREGATE_EXPULSION.get(),
                    2.4D, 1.25D, 54);
            sendLocalEffect(level, HearthBoundaryEffectPayload.aggregateImpact(), 88.0D);
            playSound(ModSounds.AGGREGATE_REALLOCATION.get(), 3.4F, 0.48F);
        }
        if (failureRupture && phaseTransitionTicks == 1) {
            sendLocalEffect(level,
                    HearthBoundaryEffectPayload.aggregateFormationRumble(), 96.0D);
        }
        if (failureRupture
                && (phaseTransitionTicks == 5
                || phaseTransitionTicks == 10
                || phaseTransitionTicks == 15)) {
            float angle = phaseTransitionTicks * 53.0F;
            AggregateShedChunkEntity.spawn(level, this,
                    phaseTransitionTicks / 5, angle, 1.15F);
            level.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                    getX(), getY() + 1.45D, getZ(), 48,
                    2.0D, 1.2D, 2.0D, 0.48D);
            playSound(ModSounds.AGGREGATE_FRAGMENT_BREAK.get(), 3.0F,
                    0.56F + phaseTransitionTicks * 0.006F);
        }
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
        int transitionDuration = primaryReallocation ? 60 : 20;
        if (phaseTransitionTicks < transitionDuration) return;
        AggregatePhase completed = phase();
        if (completed == AggregatePhase.REALLOCATION) {
            setPhase(AggregatePhase.REALLOCATED);
        } else {
            setPhase(AggregatePhase.CONVERGENCE_FAILURE);
            getAttribute(Attributes.ARMOR).setBaseValue(6.0D);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.31D);
        }
        setNoAi(false);
        beginConvergenceDischarge(level,
                completed == AggregatePhase.REALLOCATION
                        ? AggregateDischargePolicy.PRIMARY_WAVE
                        : AggregateDischargePolicy.SECONDARY_WAVE);
    }

    private boolean beginConvergenceDischarge(ServerLevel level, int wave) {
        if (!AggregateReinforcementManager.beginDischarge(level, this, wave)) {
            entityData.set(DATA_DISCHARGE_SCARS,
                    AggregateSavedData.get(level.getServer()).dischargeScars());
            return false;
        }
        currentDischargeWave = wave;
        dischargeInterruptDamage = 0.0F;
        setNoAi(true);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_DISCHARGE_SCARS,
                AggregateSavedData.get(level.getServer()).dischargeScars());
        beginAction(AggregateAction.CONVERGENCE_DISCHARGE,
                AggregateDischargePolicy.WINDUP_TICKS);
        if (action() == AggregateAction.CONVERGENCE_DISCHARGE) {
            int chunks = wave == AggregateDischargePolicy.SECONDARY_WAVE ? 2 : 1;
            for (int index = 0; index < chunks; index++) {
                AggregateShedChunkEntity.spawn(
                        level, this, wave + index, index == 0 ? -62.0F : 68.0F);
            }
            FrozenDawn.LOGGER.info("[Aggregate] Convergence discharge wave {} started with {} reserved reinforcement(s)",
                    wave, AggregateSavedData.get(level.getServer())
                            .pendingReinforcements(wave).size());
        }
        return action() == AggregateAction.CONVERGENCE_DISCHARGE;
    }

    private void tickArmorVulnerability() {
        if (armorVulnerabilityTicks > 0) armorVulnerabilityTicks--;
        double phaseArmor = phase() == AggregatePhase.CONVERGENCE_FAILURE ? 6.0D : 12.0D;
        double targetArmor = armorVulnerabilityTicks > 0
                ? Math.max(2.0D, phaseArmor - 4.0D) : phaseArmor;
        if (getAttribute(Attributes.ARMOR) != null
                && Double.compare(getAttributeBaseValue(Attributes.ARMOR), targetArmor) != 0) {
            getAttribute(Attributes.ARMOR).setBaseValue(targetArmor);
        }
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
            } else if (active == AggregateAction.CONVERGENCE_DISCHARGE) {
                currentDischargeWave = -1;
                dischargeInterruptDamage = 0.0F;
                restoreAiAfterDischarge();
            }
        }
    }

    private void restoreAiAfterDischarge() {
        setNoAi(phase() == AggregatePhase.AWAKENING
                || phase() == AggregatePhase.REALLOCATION
                || phase() == AggregatePhase.FAILURE_TRANSITION);
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
            case CONVERGENCE_DISCHARGE -> tickConvergenceDischarge(level, tick);
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
                setDeltaMovement(Vec3.ZERO);
                getNavigation().stop();
                if (tick <= 42 && tick % 2 == 0) {
                    double progress = tick / 42.0D;
                    emitInwardStreams(level, ModParticles.AGGREGATE_CONVERGENCE.get(),
                            Mth.lerp(progress, 5.5D, 1.0D), 1.2D,
                            10, 0.14D + progress * 0.1D);
                }
                if (tick == 44) {
                    level.sendParticles(ParticleTypes.FLASH,
                            getX(), getY() + 1.45D, getZ(), 1,
                            0.0D, 0.0D, 0.0D, 0.0D);
                    emitRing(level, ModParticles.AGGREGATE_EXPULSION.get(),
                            2.0D, 1.15D, 40);
                    playSound(ModSounds.AGGREGATE_REALLOCATION.get(), 3.0F, 0.63F);
                }
            }
            case NONE -> {
            }
        }
    }

    private void tickConvergenceDischarge(ServerLevel level, int tick) {
        setDeltaMovement(Vec3.ZERO);
        getNavigation().stop();
        if (tick == 1) {
            playSound(ModSounds.AGGREGATE_DISCHARGE_CHARGE.get(), 4.2F, 0.92F);
            sendLocalEffect(level, HearthBoundaryEffectPayload.aggregateFormationRumble(), 88.0D);
        }
        if (tick <= 42 && tick % 2 == 0) {
            double progress = tick / 42.0D;
            emitInwardStreams(level,
                    ModParticles.AGGREGATE_CONVERGENCE.get(),
                    Mth.lerp(progress, 8.0D, 2.3D), 1.0D + progress * 0.8D,
                    16, 0.20D + progress * 0.11D);
        }
        if (tick >= AggregateDischargePolicy.CORE_EXPOSED_TICK
                && tick < AggregateDischargePolicy.EJECTION_TICK) {
            if (tick % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD,
                        getX(), getY() + 1.72D, getZ(), 8,
                        0.55D, 0.48D, 0.55D, 0.035D);
                emitRing(level, ParticleTypes.ELECTRIC_SPARK,
                        1.4D + (tick - AggregateDischargePolicy.CORE_EXPOSED_TICK) * 0.035D,
                        1.55D, 18);
                emitRing(level, ModParticles.AGGREGATE_CONVERGENCE.get(),
                        1.1D + (tick - AggregateDischargePolicy.CORE_EXPOSED_TICK) * 0.022D,
                        1.62D, 14);
            }
            if (tick == AggregateDischargePolicy.CORE_EXPOSED_TICK) {
                playSound(ModSounds.AGGREGATE_ROAR.get(), 4.6F, 0.86F);
                level.sendParticles(ParticleTypes.FLASH,
                        getX(), getY() + 1.72D, getZ(), 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (tick == AggregateDischargePolicy.EJECTION_TICK - 4) {
            AggregateReinforcementManager.telegraphLandings(
                    level, this, currentDischargeWave);
        }
        if (tick == AggregateDischargePolicy.EJECTION_TICK) {
            int spawned = AggregateReinforcementManager.eject(
                    level, this, currentDischargeWave);
            armorVulnerabilityTicks = AggregateDischargePolicy.VULNERABILITY_TICKS;
            playSound(ModSounds.AGGREGATE_DISCHARGE_BURST.get(), 4.8F, 0.9F);
            FrozenDawn.LOGGER.info(
                    "[Aggregate] Convergence discharge wave {} ejected {} reinforcement(s)",
                    currentDischargeWave, spawned);
            sendLocalEffect(level, HearthBoundaryEffectPayload.aggregateImpact(), 88.0D);
            level.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + 1.25D, getZ(), 110,
                    2.8D, 1.5D, 2.8D, 0.24D);
            level.sendParticles(ParticleTypes.WHITE_ASH,
                    getX(), getY() + 1.35D, getZ(), 90,
                    3.5D, 1.8D, 3.5D, 0.18D);
            level.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                    getX(), getY() + 1.55D, getZ(), 120,
                    2.1D, 1.25D, 2.1D, 0.68D);
            emitRing(level, ParticleTypes.SNOWFLAKE, 5.8D, 0.8D, 48);
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

    private void tickAmbientRoar() {
        if (phase() != AggregatePhase.COHERENT
                && phase() != AggregatePhase.REALLOCATED
                && phase() != AggregatePhase.CONVERGENCE_FAILURE) return;
        if (action() != AggregateAction.NONE) return;
        if (--ambientRoarCooldown > 0) return;
        playSound(ModSounds.AGGREGATE_ROAR.get(), 4.0F,
                0.82F + random.nextFloat() * 0.14F);
        ambientRoarCooldown = 240 + random.nextInt(241);
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
            case CONVERGENCE_DISCHARGE -> "discharge";
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
        boolean exposed = action() == AggregateAction.CONVERGENCE_DISCHARGE
                && actionTick() >= AggregateDischargePolicy.CORE_EXPOSED_TICK
                && source.getEntity() instanceof Player;
        float applied = exposed ? amount * AggregateDischargePolicy.exposedDamageMultiplier(
                currentDischargeWave) : amount;
        boolean hurt = super.hurt(source, applied);
        if (hurt && exposed && isAlive()) {
            dischargeInterruptDamage += applied;
            if (dischargeInterruptDamage >= AggregateDischargePolicy.interruptThreshold(
                    getMaxHealth(), currentDischargeWave)) {
                if (level() instanceof ServerLevel server) interruptDischarge(server);
            }
        }
        return hurt;
    }

    private void interruptDischarge(ServerLevel level) {
        AggregateReinforcementManager.cancel(level, currentDischargeWave);
        entityData.set(DATA_ACTION, AggregateAction.NONE.ordinal());
        entityData.set(DATA_ACTION_TICK, 0);
        entityData.set(DATA_ACTION_DURATION, 0);
        level.sendParticles(ParticleTypes.POOF,
                getX(), getY() + 1.55D, getZ(), 72,
                1.8D, 1.0D, 1.8D, 0.16D);
        level.sendParticles(ParticleTypes.CRIT,
                getX(), getY() + 1.65D, getZ(), 42,
                1.1D, 0.8D, 1.1D, 0.12D);
        playSound(ModSounds.AGGREGATE_HURT.get(), 3.0F, 0.42F);
        currentDischargeWave = -1;
        dischargeInterruptDamage = 0.0F;
        restoreAiAfterDischarge();
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
        playSound(ModSounds.AGGREGATE_DEATH_LINEAGE.get(), 3.4F, 0.48F);
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
        hurtTime = 0;
        hurtDuration = 0;
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            if (deathPresentationTicks == 1) {
                sendLocalEffect(server,
                        HearthBoundaryEffectPayload.aggregateFormationRumble(), 112.0D);
            }
            if (deathPresentationTicks == 30
                    || deathPresentationTicks == 64
                    || deathPresentationTicks == 90) {
                playSound(ModSounds.AGGREGATE_DEATH_LINEAGE.get(),
                        deathPresentationTicks == 30 ? 3.2F : 3.8F,
                        deathPresentationTicks == 30 ? 0.72F
                                : deathPresentationTicks == 64 ? 0.56F : 0.43F);
            }
            if (deathPresentationTicks >= 12 && deathPresentationTicks <= 94
                    && deathPresentationTicks % 6 == 0) {
                int chunks = deathPresentationTicks < 54 ? 1 : 2;
                for (int index = 0; index < chunks; index++) {
                    float angle = deathPresentationTicks * 31.0F + index * 137.0F;
                    AggregateShedChunkEntity.spawn(server, this,
                            deathPresentationTicks / 6 + index, angle, 1.1F);
                }
                server.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                        getX(), getY() + 1.35D, getZ(), 36,
                        1.6D, 1.0D, 1.6D, 0.42D);
            }
            if (deathPresentationTicks % 2 == 0 && deathPresentationTicks < 100) {
                double collapseProgress = deathPresentationTicks / 100.0D;
                emitInwardStreams(server, ModParticles.AGGREGATE_CONVERGENCE.get(),
                        Mth.lerp(collapseProgress, 7.2D, 0.8D),
                        0.65D + collapseProgress * 0.8D, 12,
                        0.16D + collapseProgress * 0.18D);
                server.sendParticles(ParticleTypes.WHITE_ASH,
                        getX(), getY() + 1.2D, getZ(), 22,
                        Mth.lerp(collapseProgress, 2.8D, 0.45D),
                        Mth.lerp(collapseProgress, 1.6D, 0.35D),
                        Mth.lerp(collapseProgress, 2.8D, 0.45D), 0.11D);
            }
            if (deathPresentationTicks == 100) {
                for (int index = 0; index < 7; index++) {
                    AggregateShedChunkEntity.spawn(
                            server, this, index, index * 51.4F, 1.3F);
                }
                playSound(ModSounds.AGGREGATE_DEATH.get(), 5.0F, 0.55F);
                sendLocalEffect(server, HearthBoundaryEffectPayload.aggregateImpact(), 128.0D);
                server.explode(this, getX(), getY() + 0.2D, getZ(), 4.25F,
                        Level.ExplosionInteraction.TNT);
                server.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                        getX(), getY() + 1.4D, getZ(), 320,
                        3.8D, 2.4D, 3.8D, 0.85D);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                com.frozendawn.init.ModBlocks.AGGREGATE_MASS.get()
                                        .defaultBlockState()),
                        getX(), getY() + 1.2D, getZ(), 210,
                        3.4D, 2.1D, 3.4D, 0.58D);
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                        getX(), getY() + 1.25D, getZ(), 3,
                        1.2D, 0.8D, 1.2D, 0.0D);
                server.sendParticles(ParticleTypes.FLASH,
                        getX(), getY() + 1.3D, getZ(), 4,
                        0.8D, 0.5D, 0.8D, 0.0D);
            }
            if (deathPresentationTicks == 130 && !coreMeteorReleased) {
                coreMeteorReleased = true;
                coreMeteorId = AggregateEncounterManager.releaseCoreFromSky(server, this);
                playSound(ModSounds.AGGREGATE_DISCHARGE_BURST.get(), 4.8F, 0.48F);
                sendLocalEffect(server, HearthBoundaryEffectPayload.aggregateImpact(), 128.0D);
            }
            if (coreMeteorId != null && !coreMeteorImpactPlayed) {
                net.minecraft.world.entity.Entity meteor = server.getEntity(coreMeteorId);
                if (meteor != null) {
                    server.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                            meteor.getX(), meteor.getY() + 0.5D, meteor.getZ(), 24,
                            0.42D, 0.7D, 0.42D, 0.22D);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                                    com.frozendawn.init.ModBlocks.INERT_CONVERGENCE_CORE.get()
                                            .defaultBlockState()),
                            meteor.getX(), meteor.getY() + 0.5D, meteor.getZ(), 12,
                            0.3D, 0.35D, 0.3D, 0.12D);
                } else {
                    coreMeteorImpactPlayed = true;
                    int impactY = server.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types
                                    .MOTION_BLOCKING_NO_LEAVES,
                            blockPosition().getX(), blockPosition().getZ());
                    server.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                            getX(), impactY + 0.2D, getZ(), 2,
                            0.4D, 0.2D, 0.4D, 0.0D);
                    server.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                            getX(), impactY + 0.3D, getZ(), 180,
                            2.2D, 1.4D, 2.2D, 0.62D);
                    playSound(ModSounds.AGGREGATE_SLAM.get(), 4.6F, 0.46F);
                    sendLocalEffect(server,
                            HearthBoundaryEffectPayload.aggregateImpact(), 128.0D);
                }
            }
            if (deathPresentationTicks >= 170) {
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
        return ModSounds.AGGREGATE_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
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
        tag.putInt("dischargeScars", dischargeScars());
        tag.putInt("currentDischargeWave", currentDischargeWave);
        tag.putFloat("dischargeInterruptDamage", dischargeInterruptDamage);
        tag.putInt("armorVulnerabilityTicks", armorVulnerabilityTicks);
        tag.putInt("ambientRoarCooldown", ambientRoarCooldown);
        tag.putBoolean("coreMeteorReleased", coreMeteorReleased);
        tag.putBoolean("coreMeteorImpactPlayed", coreMeteorImpactPlayed);
        if (coreMeteorId != null) tag.putUUID("coreMeteorId", coreMeteorId);
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
        entityData.set(DATA_DISCHARGE_SCARS, Math.max(0, tag.getInt("dischargeScars")));
        currentDischargeWave = tag.contains("currentDischargeWave", net.minecraft.nbt.Tag.TAG_INT)
                ? tag.getInt("currentDischargeWave") : -1;
        dischargeInterruptDamage = Math.max(0.0F, tag.getFloat("dischargeInterruptDamage"));
        armorVulnerabilityTicks = Math.max(0, tag.getInt("armorVulnerabilityTicks"));
        ambientRoarCooldown = tag.contains("ambientRoarCooldown", net.minecraft.nbt.Tag.TAG_INT)
                ? Math.max(20, tag.getInt("ambientRoarCooldown")) : 80;
        coreMeteorReleased = tag.getBoolean("coreMeteorReleased");
        coreMeteorImpactPlayed = tag.getBoolean("coreMeteorImpactPlayed");
        coreMeteorId = tag.hasUUID("coreMeteorId") ? tag.getUUID("coreMeteorId") : null;
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
                .triggerableAnim("discharge", DISCHARGE)
                .triggerableAnim("reallocate", REALLOCATE)
                .triggerableAnim("shed", SHED)
                .triggerableAnim("death", DEATH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
