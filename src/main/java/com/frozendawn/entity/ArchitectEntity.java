package com.frozendawn.entity;

import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectBrainState;
import com.frozendawn.entity.architect.ArchitectCombatState;
import com.frozendawn.entity.architect.ArchitectDecisionEngine;
import com.frozendawn.entity.architect.ArchitectFxController;
import com.frozendawn.entity.architect.ArchitectObservationMemory;
import com.frozendawn.entity.architect.ArchitectPersistence;
import com.frozendawn.entity.architect.ArchitectRenderFlags;
import com.frozendawn.entity.architect.ArchitectActionTransitionSupport;
import com.frozendawn.entity.architect.ArchitectBlockEnvironment;
import com.frozendawn.entity.architect.ArchitectDeathFx;
import com.frozendawn.entity.architect.ArchitectIcePlacement;
import com.frozendawn.entity.architect.ArchitectMeleeEngagement;
import com.frozendawn.entity.architect.ArchitectObservationSupport;
import com.frozendawn.entity.architect.ArchitectTargetingSupport;
import com.frozendawn.entity.architect.ArchitectTickSupport;
import com.frozendawn.entity.master.MasterArchitectDeathFx;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import com.frozendawn.entity.ai.ArchitectMoveControl;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.data.ApocalypseState;
import com.frozendawn.data.PlayerEndStats;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.homo.HearthArchitectPolicy;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMasterArchitectManager;
import com.frozendawn.homo.HearthMasterArchitectPolicy;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthPopulationPolicy;
import com.frozendawn.homo.HearthPopulationRole;
import com.frozendawn.homo.MasterArchitectBossBarPolicy;
import com.frozendawn.homo.MasterArchitectCombatPhase;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TowerEncounterController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Returned Variant C: The Architect.
 * Endgame mob that disassembles player structures using custom A* pathfinding
 * (routes through breakable walls) and a utility AI system.
 *
 * Actions: OBSERVE, APPROACH, ATTACK_MELEE, RETREAT, FORTIFY, TRAP_SET, PEEK
 * APPROACH reads A* path node type to determine break/scaffold/walk.
 */
public class ArchitectEntity extends Monster {

    private static final Logger LOGGER = LogUtils.getLogger();

    // --- Synched Data ---
    private static final EntityDataAccessor<Integer> DATA_TEXTURE_VARIANT =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEATH_TICKS =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BUILDING_ICE =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_RENDER_FLAGS =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_MINING_PROGRESS =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MASTER_COMBAT_ACTION =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MASTER_COMBAT_TICKS =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_MASTER_THERMAL_CHARGE =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MASTER_COMBAT_PHASE =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MASTER_ARCHITECT =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_MASTER_MIND_COPY =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_MASTER_AURA_TIER =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);

    // --- Action Constants ---
    public static final int ACTION_OBSERVE = 0;
    public static final int ACTION_APPROACH = 1;
    public static final int ACTION_ATTACK_MELEE = 2;
    public static final int ACTION_RETREAT = 3;
    public static final int ACTION_FORTIFY = 4;
    public static final int ACTION_TRAP_SET = 5;
    public static final int ACTION_PEEK = 6;

    public static String actionName(int action) {
        return switch (action) {
            case ACTION_OBSERVE -> "OBSERVE";
            case ACTION_APPROACH -> "APPROACH";
            case ACTION_ATTACK_MELEE -> "ATTACK_MELEE";
            case ACTION_RETREAT -> "RETREAT";
            case ACTION_FORTIFY -> "FORTIFY";
            case ACTION_TRAP_SET -> "TRAP_SET";
            case ACTION_PEEK -> "PEEK";
            default -> "UNKNOWN(" + action + ")";
        };
    }

    // --- Ice Budgets (separate to prevent conflicts) ---
    private final List<BlockPos> scaffoldIce = new ArrayList<>();
    private final List<BlockPos> tacticalIce = new ArrayList<>();
    private static final int MAX_SCAFFOLD_ICE = 64;
    private static final int MAX_TACTICAL_ICE = 6;

    // --- Authoritative Server State ---
    private final ArchitectBrainState brainState = new ArchitectBrainState(ACTION_OBSERVE);
    private final ArchitectObservationMemory observationMemory = new ArchitectObservationMemory();
    private final ArchitectApproachState approachState = new ArchitectApproachState();
    private final ArchitectCombatState combatState = new ArchitectCombatState();

    // --- Observation Data ---
    static final int MIN_OBSERVE_TICKS = 200;
    static final int MAX_OBSERVE_TICKS = 200;
    static final double SPAWN_OBSERVE_CUE_RANGE_SQR = 72.0 * 72.0;

    private static final int PLAYER_MEMORY_TICKS = 200;
    private boolean towerEncounter = false;
    private long towerEncounterId = Long.MIN_VALUE;
    @Nullable
    private UUID hearthAssessorId;
    @Nullable
    private BlockPos hearthAssessorCenter;
    @Nullable
    private UUID hearthPopulationId;
    @Nullable
    private BlockPos hearthPopulationHome;
    @Nullable
    private UUID hearthMasterArchitectId;
    @Nullable
    private BlockPos hearthMasterArchitectHome;
    @Nullable
    private UUID mindCopyRealMasterId;
    @Nullable
    private ServerBossEvent masterBossEvent;
    private boolean masterBossBarEmptyOverride;

    private static final int HEAL_COOLDOWN_TICKS = 1200;
    private static final int DRINK_DURATION = 32;
    static final double RETREAT_DISTANCE = 16.0;
    private static final int MAX_SAFE_FALL_DISTANCE = 10;

    // --- Burst Damage Tracking ---
    /** Damage taken in the last BURST_WINDOW ticks. Used to boost retreat scoring. */
    private static final int BURST_WINDOW = 60; // 3 seconds

    // --- Block Breaker ---
    private final ArchitectBlockBreaker blockBreaker = new ArchitectBlockBreaker(this);
    private final ArchitectApproachWalkSupport walkSupport =
            new ArchitectApproachWalkSupport(this, approachState, blockBreaker);
    private final ArchitectApproachController approachController =
            new ArchitectApproachController(this, approachState, blockBreaker);
    private final ArchitectCombatController combatController =
            new ArchitectCombatController(this, combatState, blockBreaker);
    private final ArchitectObservationController observationController =
            new ArchitectObservationController(this, observationMemory, approachState, approachController, blockBreaker);
    private final ArchitectTacticsController tacticsController =
            new ArchitectTacticsController(this, observationMemory, blockBreaker);
    private final ArchitectHearthAssessmentController hearthAssessmentController =
            new ArchitectHearthAssessmentController(this);
    private final ArchitectHearthResidentController hearthResidentController =
            new ArchitectHearthResidentController(this);
    private final ArchitectHearthMasterController hearthMasterController =
            new ArchitectHearthMasterController(this);
    private final ArchitectDecisionEngine decisionEngine = new ArchitectDecisionEngine();
    private final ArchitectFxController fxController = new ArchitectFxController(this, blockBreaker);

    // --- Despawn ---
    private int despawnTimer = 0;
    private static final int DESPAWN_TIMEOUT = 6000;

    // --- Misc ---
    private int peekTicks = 0;
    private int trapCooldown = 0;
    private int pathRecalcCooldown = 0;
    private boolean suppressMasterHurtSound;
    private int clientMasterTetherHurtSuppressionTicks;
    private boolean mindReturnDeathDetonatesImmediately;
    private boolean foldedDeathPresentationAlreadyPlayed;
    private boolean mindReturnDeathSoundOnly;
    private boolean mindCopyDefeatReported;
    private DamageSource mindCopyDeathSource;
    private static final float WALK_MAX_ROTATE = 35.0F;
    static final int UNREACHABLE_BREAK_DELAY_TICKS = 8;
    static final int MELEE_COMMIT_TICKS = 12;
    static final float MELEE_COMMIT_LOS_GRACE_RANGE = 1.5f;
    private static final double MELEE_ENGAGE_HORIZONTAL_RANGE = 4.75;
    static final double MELEE_COMMIT_HORIZONTAL_RANGE = 5.25;
    private static final double MELEE_ENGAGE_VERTICAL_RANGE = 1.75;
    static final double MELEE_COMMIT_VERTICAL_RANGE = 2.25;
    static final double MELEE_STRAFE_SPEED = 0.10;
    static final double MELEE_PULL_SPEED_NEAR = -0.04;
    static final double MELEE_PULL_SPEED_FAR = 0.025;
    static final double MELEE_BACKOFF_SPEED = 0.11;
    static final double MELEE_DODGE_SPEED = 0.10;
    private static final double MELEE_AIR_CONTROL_SCALE = 0.35;
    private static final double MELEE_MAX_HORIZONTAL_SPEED = 0.12;
    /** Ticks since last action change. Prevents rapid flip-flopping. */
    private static final int MIN_ACTION_HOLD = 5;
    private static final double OBSERVE_REACQUIRE_RANGE = 72.0;
    static final int ROAM_REPATH_MIN_TICKS = 25;
    static final int ROAM_REPATH_VARIANCE_TICKS = 30;
    private static final long SLOW_SUPER_AISTEP_LOG_US = 50_000;
    private static final long SLOW_EXEC_ACTION_LOG_US = 50_000;
    // --- Smooth step-off (lerp instead of teleport) ---
    static final int STEP_OFF_DURATION = 4;

    // --- Scaffold pacing (player-like delay between place + jump) ---
    static final int SCAFFOLD_PLACE_TICKS = 12; // ~0.6s pause after placing before stepping up

    public ArchitectEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new ArchitectMoveControl(this, WALK_MAX_ROTATE);
        setCustomName(Component.literal("The Architect"));
        setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 96.0)
                .add(Attributes.STEP_HEIGHT, 1.0); // Player-like step-up for navigating terrain
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        // Standard navigation for OBSERVE/RETREAT/TRAP etc.
        // APPROACH uses DStarLitePathfinder directly.
        net.minecraft.world.entity.ai.navigation.GroundPathNavigation navigation =
                new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    public int getMaxFallDistance() {
        return MAX_SAFE_FALL_DISTANCE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TEXTURE_VARIANT, 0);
        builder.define(DATA_DEATH_TICKS, 0);
        builder.define(DATA_ACTION, ACTION_OBSERVE);
        builder.define(DATA_BUILDING_ICE, false);
        builder.define(DATA_RENDER_FLAGS, 0);
        builder.define(DATA_MINING_PROGRESS, 0.0f);
        builder.define(DATA_MASTER_COMBAT_ACTION, MasterArchitectCombatAction.IDLE);
        builder.define(DATA_MASTER_COMBAT_TICKS, 0);
        builder.define(DATA_MASTER_THERMAL_CHARGE, 0.0F);
        builder.define(DATA_MASTER_COMBAT_PHASE, MasterArchitectCombatPhase.KIT.id());
        builder.define(DATA_MASTER_ARCHITECT, false);
        builder.define(DATA_MASTER_MIND_COPY, false);
        builder.define(DATA_MASTER_AURA_TIER, 0);
    }

    @Override
    protected void registerGoals() {
        // No goals: all behavior driven by utility AI in aiStep()
    }

    public int getBrainAction() {
        return brainState.getCurrentAction();
    }

    private void setBrainAction(int action) {
        brainState.setCurrentAction(action);
        entityData.set(DATA_ACTION, action);
    }

    private void transitionToAction(int newAction) {
        int oldAction = getBrainAction();
        if (oldAction != newAction) {
            onActionChange(oldAction, newAction);
            brainState.setActionHoldTicks(0);
        }
        setBrainAction(newAction);
    }

    private boolean isBuildingIceNow() {
        return getBrainAction() == ACTION_FORTIFY
                || getBrainAction() == ACTION_TRAP_SET
                || (getBrainAction() == ACTION_RETREAT && combatState.retreatPhase == 1)
                || (getBrainAction() == ACTION_APPROACH && !scaffoldIce.isEmpty());
    }

    private int getRenderFlagsNow() {
        return fxController.buildRenderFlags(
                getBrainAction(),
                combatState.isDrinkingPotion,
                combatState.retreatPhase,
                approachState.scaffoldTarget,
                approachState.scaffoldDelay
        );
    }

    private void syncRenderState() {
        entityData.set(DATA_ACTION, getBrainAction());
        entityData.set(DATA_BUILDING_ICE, isBuildingIceNow());
        entityData.set(DATA_RENDER_FLAGS, getRenderFlagsNow());
        entityData.set(DATA_MINING_PROGRESS, blockBreaker.getMiningProgress());
    }

    void resetReevalCooldown() {
        brainState.setReevalCooldown(0);
    }

    int getScaffoldIceCount() {
        return scaffoldIce.size();
    }

    int getMaxScaffoldIce() {
        return MAX_SCAFFOLD_ICE;
    }

    int getTacticalIceCount() {
        return tacticalIce.size();
    }

    int getMaxTacticalIce() {
        return MAX_TACTICAL_ICE;
    }

    boolean isPathRecalcReady() {
        return pathRecalcCooldown <= 0;
    }

    void setPathRecalcCooldown(int ticks) {
        pathRecalcCooldown = ticks;
    }

    void decrementPathRecalcCooldown() {
        pathRecalcCooldown--;
    }

    void clearMeleeCommit() {
        brainState.setMeleeCommitTicks(0);
    }

    void refreshMeleeCommit() {
        brainState.setMeleeCommitTicks(Math.max(brainState.getMeleeCommitTicks(), MELEE_COMMIT_TICKS));
    }

    void transitionToObserveAction() {
        transitionToAction(ACTION_OBSERVE);
    }

    void resetActionHoldTicks() {
        brainState.setActionHoldTicks(0);
    }

    void setRoamingAfterTargetLoss(boolean roamingAfterTargetLoss) {
        brainState.setRoamingAfterTargetLoss(roamingAfterTargetLoss);
    }

    void resetRetreatState() {
        combatState.retreatPhase = 0;
        combatState.retreatCoverBuilt = 0;
    }

    void setTrapCooldown(int ticks) {
        trapCooldown = ticks;
    }

    int incrementPeekTicks() {
        return ++peekTicks;
    }

    void resetPeekTicks() {
        peekTicks = 0;
    }

    int nextRandomInt(int bound) {
        return random.nextInt(bound);
    }

    float nextRandomFloat() {
        return random.nextFloat();
    }

    double nextRandomCenteredDouble() {
        return random.nextDouble() - 0.5;
    }

    // ========================
    //  SPAWN SETUP
    // ========================

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setTextureVariant(level.getRandom().nextInt(5));
        approachState.surfaceY = blockPosition().getY();
        ensureAmbientHelmet();
        return data;
    }

    /**
     * Pre-seed observation data on spawn. Called by ArchitectSpawner.
     * Gives partial knowledge so even short encounters show purposeful behavior.
     */
    public void preSeedObservation(ServerLevel level, Player nearestPlayer) {
        BlockPos playerPos = nearestPlayer.blockPosition();
        observationMemory.setLastObservedPos(playerPos);
        scanEntrances(level, playerPos);
    }

    public void armSpawnObserveCue(ServerPlayer player) {
        observationMemory.setPendingSpawnCuePlayerId(player.getUUID());
        observationMemory.setPendingSpawnCuePlayed(false);
    }

    // ========================
    //  UTILITY AI
    // ========================

    @Override
    public void aiStep() {
        if (level().isClientSide() && clientMasterTetherHurtSuppressionTicks > 0) {
            clientMasterTetherHurtSuppressionTicks--;
        }
        if (!level().isClientSide() && isMasterArchitectVisual()) {
            updateMasterBossBarProgress();
        }
        if (level() instanceof ServerLevel serverLevel) {
            if (isHearthAssessor() && hearthAssessorId != null) {
                HearthCombatRosterManager.enforcePassiveRole(
                        serverLevel, hearthAssessorId, this);
            } else if (isHearthPopulationResident() && hearthPopulationId != null) {
                HearthCombatRosterManager.enforcePassiveRole(
                        serverLevel, hearthPopulationId, this);
            }
        }
        if (isMasterMindCopy()) {
            super.aiStep();
            if (!level().isClientSide()) {
                getNavigation().stop();
                setTarget(null);
                setSprinting(false);
                setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
                int visualTicks = getMasterCombatActionTicks();
                if (visualTicks > 0) {
                    entityData.set(DATA_MASTER_COMBAT_TICKS, visualTicks - 1);
                } else {
                    setMasterCombatVisual(MasterArchitectCombatAction.FLOOD_CHANNEL, 0);
                }
            }
            return;
        }
        // Warmup: skip all AI for first 2 seconds after spawn/load
        // Prevents pathfinding freeze when entity loads before chunks are ready
        if (tickCount < 40) {
            getNavigation().stop();
            super.aiStep();
            return;
        }

        long superStart = System.nanoTime();
        super.aiStep();
        long superUs = (System.nanoTime() - superStart) / 1000;
        if (superUs > SLOW_SUPER_AISTEP_LOG_US && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] super.aiStep() took {}us (nav recompute?)", superUs);
        }
        if (!isAlive() || getDeathTicks() > 0) {
            return;
        }
        if (level().isClientSide()) return;

        // Defensive: fix surfaceY if it wasn't set (NBT load before positioning)
        if (approachState.surfaceY == 0) approachState.surfaceY = blockPosition().getY();
        ensureAmbientHelmet();

        if (isHearthAssessor() && level() instanceof ServerLevel serverLevel
                && hearthAssessmentController.tick(serverLevel)) {
            updateHeldItem();
            syncRenderState();
            return;
        }
        if (isHearthPopulationResident() && level() instanceof ServerLevel serverLevel
                && hearthResidentController.tick(serverLevel)) {
            updateHeldItem();
            syncRenderState();
            return;
        }
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel
                && hearthMasterController.tick(serverLevel)) {
            equipMasterArchitectStaff();
            syncRenderState();
            return;
        }

        long gameTick = level().getGameTime();

        // --- Target acquisition ---
        // Architect senses through blocks — always knows target position
        LivingEntity target = findTarget();

        if (target == null) {
            if (!brainState.isRoamingAfterTargetLoss()) {
                observationController.enterRoamModeAfterTargetLoss();
            }
        } else {
            if (brainState.isRoamingAfterTargetLoss() && target instanceof Player player) {
                observationController.restartObserveForPlayer(player);
            }
            brainState.setRoamingAfterTargetLoss(false);
            observationMemory.setLastKnownPlayerPos(target.blockPosition());
            observationMemory.setLastSeenTick(tickCount);
        }

        observationController.maybeTriggerSpawnObserveCue(target);

        ArchitectTickSupport.applyPerTickCooldowns(combatState, approachState, brainState);
        if (trapCooldown > 0) {
            trapCooldown--;
        }
        combatState.recentDamage = ArchitectTickSupport.decayRecentDamageOutsideBurst(
                tickCount,
                combatState.lastDamageTick,
                BURST_WINDOW,
                combatState.recentDamage);

        // --- Potion drinking ---
        if (combatState.isDrinkingPotion) {
            combatState.drinkTicks++;
            if (combatState.drinkTicks >= DRINK_DURATION) {
                finishDrinking();
            }
            // Fully commits to drinking — no cancellation. Player can punish this.
            syncRenderState();
            return;
        }

        // --- Keep wooden doors open while pushing toward a target ---
        if (getBrainAction() == ACTION_APPROACH
                || getBrainAction() == ACTION_ATTACK_MELEE
                || horizontalCollision) {
            keepNearbyWoodenDoorsOpen();
        }

        // --- Heater burn ---
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

        // --- Despawn timer ---
        int nextDespawnTimer = ArchitectTickSupport.nextDespawnTimer(
                level(),
                getBoundingBox(),
                towerEncounter,
                target != null,
                despawnTimer,
                DESPAWN_TIMEOUT);
        if (nextDespawnTimer < 0) {
            cleanupAllIce();
            discard();
            return;
        }
        despawnTimer = nextDespawnTimer;

        // --- Utility AI scoring ---
        // Don't re-evaluate while actively mining — commit to the block
        // Only interrupt for critical HP (retreat needed)
        boolean miningLock = blockBreaker.isMining()
                && getHealth() > getMaxHealth() * 0.3f;

        brainState.setReevalCooldown(brainState.getReevalCooldown() - 1);
        brainState.setActionHoldTicks(brainState.getActionHoldTicks() + 1);
        if (brainState.getReevalCooldown() <= 0 && !miningLock) {
            // Prevent rapid flip-flopping: hold current action for at least MIN_ACTION_HOLD ticks.
            // Retreat bypasses this — survival is always urgent.
            boolean holdLock = brainState.getActionHoldTicks() < MIN_ACTION_HOLD
                    && getHealth() > getMaxHealth() * 0.5f;
            if (!holdLock) {
                evaluateActions(target);
                brainState.setReevalCooldown(5);
            }
        }

        approachState.sprintRequested = false;
        long actionStart = System.nanoTime();
        executeAction(target);
        long actionUs = (System.nanoTime() - actionStart) / 1000;
        if (actionUs > SLOW_EXEC_ACTION_LOG_US && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] executeAction({}) took {}us", getBrainAction(), actionUs);
        }

        emitActionTelegraphParticles(target);

        setSprinting(shouldSprintRetreat(target) || approachState.sprintRequested);

        updateHeldItem();
        syncRenderState();
    }

    private void evaluateActions(@Nullable LivingEntity target) {
        if (target != null
                && brainState.getMeleeCommitTicks() > 0
                && getBrainAction() != ACTION_RETREAT
                && getHealth() > getMaxHealth() * 0.35f
                && canCommitToMelee(target)) {
            transitionToAction(ACTION_ATTACK_MELEE);
            primeMeleeHandoff();
            return;
        }

        ArchitectDecisionEngine.Decision decision = decisionEngine.evaluate(
                new ArchitectDecisionEngine.Context(
                        getBrainAction(),
                        target != null,
                        target != null ? distanceTo(target) : Float.MAX_VALUE,
                        observationMemory.hasObserved(),
                        observationMemory.isObserveDirty(),
                        getHealth(),
                        getMaxHealth(),
                        target != null && shouldPreferMeleeOverApproach(target),
                        target != null && target.hasLineOfSight(this),
                        target != null && canStartMelee(target),
                        combatState.rangedHitsReceived,
                        combatState.healCooldown,
                        combatState.recentDamage,
                        tacticalIce.size(),
                        MAX_TACTICAL_ICE,
                        trapCooldown,
                        !observationMemory.entrancePositions().isEmpty(),
                        target != null && isPlayerInsideBase(target),
                        target != null && isNearCorner()
                ),
                random
        );
        int bestAction = decision.bestAction();
        float[] scores = decision.scores();

        if (bestAction != getBrainAction()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Architect] SCORING: observe={} approach={} melee={} retreat={} HP={}/{} winner={} (was {})",
                        String.format("%.2f", scores[ACTION_OBSERVE]),
                        String.format("%.2f", scores[ACTION_APPROACH]),
                        String.format("%.2f", scores[ACTION_ATTACK_MELEE]),
                        String.format("%.2f", scores[ACTION_RETREAT]),
                        String.format("%.1f", getHealth()),
                        String.format("%.0f", getMaxHealth()),
                        bestAction,
                        getBrainAction());
            }
            transitionToAction(bestAction);
        }
        if (bestAction == ACTION_ATTACK_MELEE) {
            primeMeleeHandoff();
        }
    }

    void triggerReeval() {
        brainState.setReevalCooldown(0);
        pathRecalcCooldown = 0;
    }

    // --- ACTION EXECUTION ---

    private void executeAction(@Nullable LivingEntity target) {
        if (target == null) {
            observationController.executeRoamAndRuin();
            return;
        }

        switch (getBrainAction()) {
            case ACTION_OBSERVE -> observationController.executeObserve(target);
            case ACTION_APPROACH -> approachController.executeApproach(target);
            case ACTION_ATTACK_MELEE -> combatController.executeAttackMelee(target);
            case ACTION_RETREAT -> combatController.executeRetreat(target);
            case ACTION_FORTIFY -> tacticsController.executeFortify(target);
            case ACTION_TRAP_SET -> tacticsController.executeTrapSet(target);
            case ACTION_PEEK -> tacticsController.executePeek(target);
        }
    }

    void approachLastKnownPos() {
        BlockPos lastKnownPlayerPos = observationMemory.getLastKnownPlayerPos();
        if (lastKnownPlayerPos != null && tickCount - observationMemory.getLastSeenTick() < PLAYER_MEMORY_TICKS) {
            if (pathRecalcCooldown <= 0) {
                getNavigation().moveTo(lastKnownPlayerPos.getX() + 0.5,
                        lastKnownPlayerPos.getY(), lastKnownPlayerPos.getZ() + 0.5, 1.0);
                pathRecalcCooldown = 5;
            }
            pathRecalcCooldown--;
        }
    }

    boolean walkToBreakTarget() {
        BlockPos bt = blockBreaker.getTarget();
        if (bt == null) return false;

        double blockDist = position().distanceToSqr(
                bt.getX() + 0.5, bt.getY() + 0.5, bt.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            getNavigation().stop();
            blockBreaker.tick();
            return true;
        }

        if (getNavigation().isInProgress()) return true;

        getNavigation().moveTo(bt.getX() + 0.5, bt.getY(), bt.getZ() + 0.5, 1.0);
        pathRecalcCooldown = 5;
        return true;
    }

    void trackWalkStep(BlockPos stepPos) {
        walkSupport.trackWalkStep(stepPos);
    }

    void resetWalkStuckTracker() {
        walkSupport.resetWalkStuckTracker();
    }

    void recordWalkCellHistory() {
        walkSupport.recordWalkCellHistory();
    }

    private void resetWalkCellHistory() {
        walkSupport.resetWalkCellHistory();
    }

    void resetUnstickBreakTracker() {
        walkSupport.resetUnstickBreakTracker();
    }

    void clearCommittedWalk() {
        walkSupport.clearCommittedWalk();
    }

    @Nullable
    BlockPos getCommittedWalkSteeringTarget() {
        return walkSupport.getCommittedWalkSteeringTarget();
    }

    private boolean shouldSprintRetreat(@Nullable LivingEntity target) {
        return target != null && getBrainAction() == ACTION_RETREAT && combatState.retreatPhase == 0;
    }

    @Nullable
    BlockPos getImmediateBacktrackPos() {
        return walkSupport.getImmediateBacktrackPos();
    }

    boolean tryContinueCommittedWalk(@Nullable LivingEntity target) {
        return walkSupport.tryContinueCommittedWalk(target);
    }

    void invalidateStaleApproachBreakTarget(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
        walkSupport.invalidateStaleApproachBreakTarget(step, target);
    }

    boolean shouldContinueApproachBreak(@Nullable LivingEntity target, BlockPos expectedBreakTarget) {
        return walkSupport.shouldContinueApproachBreak(target, expectedBreakTarget);
    }

    boolean handleWalkStuck(BlockPos stepPos, @Nullable LivingEntity target) {
        return walkSupport.handleWalkStuck(stepPos, target);
    }

    void executeVanillaWalkStep(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
        walkSupport.executeVanillaWalkStep(step, target);
    }

    boolean canDirectChaseApproach(@Nullable LivingEntity target) {
        return walkSupport.canDirectChaseApproach(target);
    }

    void executeDirectApproachChase(LivingEntity target) {
        walkSupport.executeDirectApproachChase(target);
    }

    private boolean canStartMelee(LivingEntity target) {
        return ArchitectMeleeEngagement.canStartMelee(
                this,
                target,
                hasLineOfSight(target),
                getNavigation(),
                MELEE_ENGAGE_HORIZONTAL_RANGE,
                MELEE_ENGAGE_VERTICAL_RANGE,
                2.25,
                1.25);
    }

    private boolean canCommitToMelee(LivingEntity target) {
        return ArchitectMeleeEngagement.canCommitToMelee(
                this,
                target,
                hasLineOfSight(target),
                getNavigation(),
                MELEE_COMMIT_HORIZONTAL_RANGE,
                MELEE_COMMIT_VERTICAL_RANGE,
                MELEE_COMMIT_LOS_GRACE_RANGE,
                2.25,
                1.25);
    }

    void clearWalkNavigationState(boolean stopNavigation) {
        walkSupport.clearWalkNavigationState(stopNavigation);
    }

    void keepNearbyWoodenDoorsOpen() {
        ArchitectBlockEnvironment.keepNearbyWoodenDoorsOpen(this);
    }

    void keepDoorOpenNear(BlockPos center) {
        ArchitectBlockEnvironment.keepDoorOpenNear(this, center);
    }

    boolean isBreakableBlock(BlockPos pos) {
        return ArchitectBlockEnvironment.isBreakableBlock(level(), pos, scaffoldIce);
    }

    void applyCombatHorizontalMotion(double x, double z) {
        Vec3 current = getDeltaMovement();
        Vec3 blended = ArchitectMeleeEngagement.blendCombatHorizontalMotion(
                current,
                onGround(),
                x,
                z,
                MELEE_AIR_CONTROL_SCALE,
                MELEE_MAX_HORIZONTAL_SPEED);
        setDeltaMovement(blended.x, current.y, blended.z);
    }

    // ========================
    //  ACTION TRANSITIONS
    // ========================

    private void onActionChange(int oldAction, int newAction) {
        if (oldAction == ACTION_OBSERVE) {
            ArchitectActionTransitionSupport.onLeaveObserve(observationMemory);
        }
        if (oldAction == ACTION_PEEK) peekTicks = 0;
        if (oldAction == ACTION_APPROACH) {
            ArchitectActionTransitionSupport.onLeaveApproach(approachState);
        }
        clearWalkNavigationState(true);
        clearCommittedWalk();
        resetWalkStuckTracker();
        resetWalkCellHistory();
        resetUnstickBreakTracker();
        if (oldAction != ACTION_APPROACH) blockBreaker.clearTarget();
        if (oldAction == ACTION_RETREAT && combatState.isDrinkingPotion) cancelDrinking();
        if (newAction == ACTION_RETREAT) {
            ArchitectActionTransitionSupport.onEnterRetreat(combatState);
        }
        if (newAction == ACTION_ATTACK_MELEE) {
            primeMeleeHandoff();
        }
        if (newAction == ACTION_APPROACH) {
            approachState.dstarApproachEntryLogged = false;
            if (oldAction != ACTION_OBSERVE || approachState.dstarTransitionSource == null) {
                approachState.dstarTransitionSource = "ACTION_" + actionName(oldAction);
            }
        }
        if (newAction == ACTION_OBSERVE) {
            approachState.dstarObserveHandoffLogged = false;
            approachState.dstarApproachEntryLogged = false;
            approachState.dstarTransitionSource = null;
            approachState.dstar.resetOverflowInvestigationEvents();
        }
        pathRecalcCooldown = 0; // Force path recalc on action change
    }

    void primeMeleeHandoff() {
        ArchitectActionTransitionSupport.primeMeleeHandoffState(brainState, approachState, MELEE_COMMIT_TICKS);
        clearWalkNavigationState(true);
        clearCommittedWalk();
        blockBreaker.clearTarget();
    }

    double horizontalDistanceTo(LivingEntity target) {
        return ArchitectMeleeEngagement.horizontalDistanceTo(this, target);
    }

    double verticalDistanceTo(LivingEntity target) {
        return ArchitectMeleeEngagement.verticalDistanceTo(this, target);
    }

    boolean isTargetWithinMeleeEngageGeometry(LivingEntity target) {
        return ArchitectMeleeEngagement.isWithinMeleeGeometry(
                this,
                target,
                MELEE_ENGAGE_HORIZONTAL_RANGE,
                MELEE_ENGAGE_VERTICAL_RANGE);
    }

    private boolean isTargetWithinMeleeCommitGeometry(LivingEntity target) {
        return ArchitectMeleeEngagement.isWithinMeleeGeometry(
                this,
                target,
                MELEE_COMMIT_HORIZONTAL_RANGE,
                MELEE_COMMIT_VERTICAL_RANGE);
    }

    boolean shouldPreferMeleeOverApproach(LivingEntity target) {
        return canStartMelee(target);
    }

    // ========================
    //  COMBAT
    // ========================

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isMasterMindCopy() && mindCopyDefeatReported) {
            return false;
        }
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            living.setTicksFrozen(living.getTicksFrozen() + 60);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FREEZING)) return false;
        if (isMasterMindCopy()) {
            amount = MasterArchitectCombatPolicy.adjustedIncomingDamage(
                    amount,
                    source.is(DamageTypeTags.IS_FIRE),
                    source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
            if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
                amount = MasterArchitectMindSessionBridge.prepareCopyDamage(
                        serverLevel, this, source, amount);
            }
            if (!level().isClientSide()
                    && !mindCopyDefeatReported
                    && amount + 0.001F >= getHealth()
                    && level() instanceof ServerLevel serverLevel) {
                mindCopyDefeatReported = true;
                mindCopyDeathSource = source;
                setHealth(Math.min(1.0F, getHealth()));
                setInvulnerable(true);
                setNoAi(true);
                getNavigation().stop();
                setTarget(null);
                setMasterCombatVisual(MasterArchitectCombatAction.MIND_RETURN_STAGGER, 0);
                updateMasterBossBarProgress();
                MasterArchitectMindSessionBridge.onCopyDefeated(
                        serverLevel, this, source);
                return true;
            }
            boolean hurt = super.hurt(source, amount);
            if (hurt && !level().isClientSide() && !isRemoved()
                    && level() instanceof ServerLevel serverLevel) {
                MasterArchitectMindSessionBridge.onCopyHurt(
                        serverLevel, this, source, amount);
            }
            return hurt;
        }
        if (isHearthMasterArchitect()) {
            amount = MasterArchitectCombatPolicy.adjustedIncomingDamage(
                    amount,
                    source.is(DamageTypeTags.IS_FIRE),
                    source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
            if (level() instanceof ServerLevel serverLevel) {
                MasterArchitectCombatController.TetherDamageResult tetherResult =
                        hearthMasterController.redistributeIncomingDamage(
                                serverLevel, amount);
                amount = tetherResult.masterDamage();
                suppressMasterHurtSound = tetherResult.suppressNormalHitSound();
                amount = hearthMasterController.prepareIncomingDamage(
                        amount, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
            }
        } else if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 1.5F;
        }

        boolean hurt;
        try {
            hurt = super.hurt(source, amount);
        } finally {
            suppressMasterHurtSound = false;
        }
        if (hurt && !level().isClientSide()) {
            if (isHearthAssessor()
                    && level() instanceof ServerLevel serverLevel
                    && source.getEntity() instanceof ServerPlayer attacker
                    && hearthAssessorId != null) {
                HearthMemoryManager.recordHearthEntityAttack(
                        serverLevel, hearthAssessorId, attacker, "Architect assessor");
            } else if (isHearthPopulationResident()
                    && level() instanceof ServerLevel serverLevel
                    && source.getEntity() instanceof ServerPlayer attacker
                    && hearthPopulationId != null) {
                HearthMemoryManager.recordHearthEntityAttack(
                        serverLevel, hearthPopulationId, attacker, "Architect resident");
            } else if (isHearthMasterArchitect()
                    && level() instanceof ServerLevel serverLevel
                    && source.getEntity() instanceof ServerPlayer attacker
                    && hearthMasterArchitectId != null) {
                HearthMemoryManager.recordHearthEntityAttack(
                        serverLevel, hearthMasterArchitectId, attacker, "Master Architect");
            }
            if (isHearthMasterArchitect()) {
                hearthMasterController.onHurt(
                        (ServerLevel) level(),
                        source.getEntity() instanceof ServerPlayer attacker
                                ? attacker : null);
                if (MasterArchitectBossBarPolicy.shouldReveal(
                        false, source.getEntity() instanceof ServerPlayer)) {
                    setMasterBossBarProvoked(true);
                }
            }
            if (source.getDirectEntity() != null && source.getDirectEntity() != source.getEntity()) {
                combatState.rangedHitsReceived++;
            }
            // Track burst damage for retreat scoring
            if (tickCount - combatState.lastDamageTick > BURST_WINDOW) {
                combatState.recentDamage = 0f; // Reset if outside burst window
            }
            combatState.recentDamage += amount;
            combatState.lastDamageTick = tickCount;
            // Don't cancel potion or re-evaluate during heal — fully commits
            if (!combatState.isDrinkingPotion) {
                triggerReeval();
            }
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (isMasterMindCopy()) {
            mindCopyDeathSource = source;
            super.die(source);
            updateMasterBossBarProgress();
            return;
        }
        boolean masterArchitect = isHearthMasterArchitect();
        super.die(source);
        if (masterArchitect) {
            updateMasterBossBarProgress();
        }
        if (isHearthPopulationResident() && level() instanceof ServerLevel serverLevel
                && hearthPopulationId != null) {
            HearthCombatRosterManager.recordResidentDeath(
                    serverLevel, hearthPopulationId, getUUID(),
                    HearthPopulationRole.ARCHITECT, source);
        } else if (isHearthAssessor() && level() instanceof ServerLevel serverLevel
                && hearthAssessorId != null) {
            HearthCombatRosterManager.recordResidentDeath(
                    serverLevel, hearthAssessorId, getUUID(), null, source);
        }
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel
                && hearthMasterArchitectId != null) {
            hearthMasterController.onDeath(
                    serverLevel,
                    source.getEntity() instanceof ServerPlayer killer ? killer : null);
            HearthMasterArchitectManager.recordDefeat(
                    serverLevel, hearthMasterArchitectId, getUUID());
        }
        if (!level().isClientSide() && source.getEntity() instanceof ServerPlayer killer) {
            WorldTickHandler.grantAdvancement(
                    killer, masterArchitect ? "decoherence" : "disassembled");
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        return !isMasterArchitectVisual() && super.shouldDropLoot();
    }

    @Override
    protected void dropEquipment() {
        if (!isMasterArchitectVisual()) {
            super.dropEquipment();
        }
    }

    // ========================
    //  HEALING POTION
    // ========================

    void startDrinking() {
        combatState.isDrinkingPotion = true;
        combatState.drinkTicks = 0;
        if (level() instanceof ServerLevel serverLevel) {
            PlayerEndStats.incrementArchitectRetreatedToHealNearby(serverLevel, blockPosition());
        }
        ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING);
        setItemSlot(EquipmentSlot.MAINHAND, potion);
        getNavigation().stop();
        playSound(ModSounds.ARCHITECT_DRINK.get(), 0.8f, 0.95f + random.nextFloat() * 0.1f);
    }

    private void finishDrinking() {
        combatState.isDrinkingPotion = false;
        combatState.drinkTicks = 0;
        combatState.healCooldown = HEAL_COOLDOWN_TICKS;
        float targetHealth = getMaxHealth() * 0.75f;
        if (getHealth() < targetHealth) setHealth(targetHealth);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        triggerReeval();
    }

    private void cancelDrinking() {
        combatState.isDrinkingPotion = false;
        combatState.drinkTicks = 0;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void emitActionTelegraphParticles(@Nullable LivingEntity target) {
        fxController.emitActionTelegraphParticles(
                target,
                getBrainAction(),
                combatState.isDrinkingPotion,
                combatState.retreatPhase,
                approachState.scaffoldTarget
        );
    }

    private void ensureAmbientHelmet() {
        if (isMasterArchitectVisual()) {
            clearMasterAmbientHelmet();
            return;
        }
        if (!getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return;
        }
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        setDropChance(EquipmentSlot.HEAD, 0.0F);
    }

    private void clearMasterAmbientHelmet() {
        if (getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)) {
            setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }

    // ========================
    //  ICE PLACEMENT
    // ========================

    /**
     * Place scaffold ice. Evicts oldest BEHIND the entity, never beneath.
     */
    boolean placeScaffoldIce(BlockPos pos) {
        if (ArchitectIcePlacement.placeScaffoldIce(
                level(),
                pos,
                scaffoldIce,
                MAX_SCAFFOLD_ICE,
                blockPosition())) {
            emitIcePlacementFx(pos);
            return true;
        }
        return false;
    }

    boolean placeTacticalIce(BlockPos pos) {
        if (ArchitectIcePlacement.placeTacticalIce(
                level(),
                pos,
                tacticalIce,
                MAX_TACTICAL_ICE)) {
            emitIcePlacementFx(pos);
            return true;
        }
        return false;
    }

    private void emitIcePlacementFx(BlockPos pos) {
        entityData.set(DATA_BUILDING_ICE, true);
        swing(InteractionHand.MAIN_HAND);
        playSound(ModSounds.ARCHITECT_ICE_PLACE.get(), 0.7f, 0.9f + random.nextFloat() * 0.2f);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    8, 0.22, 0.18, 0.22, 0.02);
            serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                    4, 0.18, 0.12, 0.18, 0.03);
        }
    }

    private void cleanupAllIce() {
        ArchitectIcePlacement.cleanupAllIce(level(), scaffoldIce, tacticalIce);
    }

    // ========================
    //  HELD ITEM / TOOL
    // ========================

    private void updateHeldItem() {
        fxController.updateHeldItem(
                getBrainAction(),
                combatState.isDrinkingPotion,
                isBuildingIceNow(),
                combatState.retreatPhase
        );
    }

    // ========================
    //  OBSERVATION HELPERS
    // ========================

    void scanEntrances(ServerLevel level, BlockPos center) {
        ArchitectObservationSupport.scanEntrances(level, center, observationMemory.entrancePositions());
    }

    /**
     * Called when player places blocks near last observed position.
     * Threshold: 5+ changes within 16 blocks.
     */
    public void onNearbyBlockChange(BlockPos changedPos, int changeCount) {
        BlockPos lastObservedPos = observationMemory.getLastObservedPos();
        if (ArchitectObservationSupport.shouldMarkObserveDirty(lastObservedPos, changedPos, changeCount)) {
            observationMemory.setObserveDirty(true);
        }
        LivingEntity target = getTarget();
        double targetDistance = target != null ? distanceTo(target) : -1.0;
        // Notify D* Lite of world changes so it updates costs incrementally
        approachState.dstar.onBlockChanged(
                changedPos,
                level(),
                "REAL_BLOCK_CHANGE",
                actionName(getBrainAction()),
                approachState.dstarTransitionSource != null ? approachState.dstarTransitionSource : "UNKNOWN_OR_NON_OBSERVE",
                targetDistance
        );
    }

    // ========================
    //  HELPERS
    // ========================

    private double getDetectionRange() {
        return 96.0;
    }

    @Nullable
    private LivingEntity findTarget() {
        if (isHearthAssessor() && level() instanceof ServerLevel serverLevel) {
            return hearthAssessmentController.findHostileTarget(serverLevel);
        }
        if (isHearthPopulationResident() && level() instanceof ServerLevel serverLevel) {
            return hearthResidentController.findHostileTarget(serverLevel);
        }
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel) {
            return hearthMasterController.findHostileTarget(serverLevel);
        }
        return ArchitectTargetingSupport.findTarget(
                level(),
                this,
                brainState.isRoamingAfterTargetLoss(),
                getDetectionRange(),
                OBSERVE_REACQUIRE_RANGE,
                this::distanceToSqr);
    }

    boolean isPlayerFacing(LivingEntity entity) {
        return ArchitectObservationSupport.isPlayerFacing(entity.getLookAngle(), entity.position(), position());
    }

    private boolean isPlayerInsideBase(LivingEntity player) {
        return ArchitectObservationSupport.isPlayerInsideBase(level(), player);
    }

    private boolean isNearCorner() {
        return ArchitectObservationSupport.isNearCorner(level(), blockPosition());
    }

    // ========================
    //  DEATH
    // ========================

    @Override
    protected void tickDeath() {
        int ticks = getDeathTicks() + 1;
        entityData.set(DATA_DEATH_TICKS, ticks);
        if (isMasterArchitectVisual()) {
            tickMasterArchitectDeath(ticks);
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            double smokeY = getY() + 0.4 + (ticks / 30.0) * 1.8;
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    getX(), smokeY, getZ(), 4, 0.08, 0.12, 0.08, 0.01);
            if (ticks % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), smokeY - 0.15, getZ(), 2, 0.06, 0.08, 0.06, 0.005);
            }
            if (ticks >= 22) {
                ArchitectDeathFx.emitDeathSoulRise(serverLevel, ticks, getX(), getY(), getZ());
            }
        }
        if (ticks >= 30) {
            cleanupAllIce();
            blockBreaker.onDeath();
            if (towerEncounter && level() instanceof ServerLevel serverLevel) {
                TowerEncounterController.markResolved(serverLevel, towerEncounterId);
            }
            remove(RemovalReason.KILLED);
            if (level() instanceof ServerLevel serverLevel) {
                ArchitectDeathFx.emitDeathSmokeBurst(serverLevel, random, getX(), getY(), getZ());
                ArchitectDeathFx.emitDeathSoulRelease(serverLevel, random, getX(), getY(), getZ());
            }
        }
    }

    private void tickMasterArchitectDeath(int ticks) {
        setDeltaMovement(Vec3.ZERO);
        if (mindReturnDeathDetonatesImmediately) {
            detonateMasterArchitectDeath();
            return;
        }
        if (level() instanceof ServerLevel serverLevel) {
            MasterArchitectDeathFx.tickCharge(serverLevel, this, ticks);
        }
        if (ticks < MasterArchitectCombatPolicy.DEATH_DETONATION_TICK) {
            return;
        }

        detonateMasterArchitectDeath();
    }

    private void detonateMasterArchitectDeath() {
        cleanupAllIce();
        blockBreaker.onDeath();
        if (towerEncounter && level() instanceof ServerLevel serverLevel) {
            TowerEncounterController.markResolved(serverLevel, towerEncounterId);
        }
        if (level() instanceof ServerLevel serverLevel
                && !foldedDeathPresentationAlreadyPlayed) {
            MasterArchitectDeathFx.detonate(serverLevel, this);
        }
        if (isMasterMindCopy() && !mindCopyDefeatReported
                && level() instanceof ServerLevel serverLevel) {
            mindCopyDefeatReported = true;
            DamageSource source = mindCopyDeathSource == null
                    ? serverLevel.damageSources().generic()
                    : mindCopyDeathSource;
            MasterArchitectMindSessionBridge.onCopyDefeated(
                    serverLevel, this, source);
        }
        remove(RemovalReason.KILLED);
    }

    // ========================
    //  SYNCHED DATA ACCESSORS
    // ========================

    public int getTextureVariant() { return entityData.get(DATA_TEXTURE_VARIANT); }
    public void setTextureVariant(int variant) { entityData.set(DATA_TEXTURE_VARIANT, variant); }
    public int getDeathTicks() { return entityData.get(DATA_DEATH_TICKS); }
    public int getCurrentAction() { return entityData.get(DATA_ACTION); }
    public int getMasterCombatAction() {
        return entityData.get(DATA_MASTER_COMBAT_ACTION);
    }
    public int getMasterCombatActionTicks() {
        return entityData.get(DATA_MASTER_COMBAT_TICKS);
    }
    public float getMasterThermalCharge() {
        return entityData.get(DATA_MASTER_THERMAL_CHARGE);
    }
    public MasterArchitectCombatPhase getMasterCombatPhase() {
        return MasterArchitectCombatPhase.fromId(
                entityData.get(DATA_MASTER_COMBAT_PHASE));
    }
    public boolean isMiningBlock() {
        return ArchitectRenderFlags.has(entityData.get(DATA_RENDER_FLAGS), ArchitectRenderFlags.MINING);
    }
    public float getMiningProgress() { return entityData.get(DATA_MINING_PROGRESS); }
    public boolean hasQueuedScaffoldStep() {
        return ArchitectRenderFlags.has(entityData.get(DATA_RENDER_FLAGS), ArchitectRenderFlags.QUEUED_SCAFFOLD);
    }
    public boolean isRetreatRecovering() {
        return ArchitectRenderFlags.has(entityData.get(DATA_RENDER_FLAGS), ArchitectRenderFlags.RETREAT_RECOVERING);
    }
    public boolean isTowerEncounter() { return towerEncounter; }
    public long getTowerEncounterId() { return towerEncounterId; }
    public void setTowerEncounter(long towerId) {
        towerEncounter = true;
        towerEncounterId = towerId;
        despawnTimer = 0;
    }

    public void bindToHearthAssessor(UUID hearthId, BlockPos center, int textureVariant) {
        hearthAssessorId = hearthId;
        hearthAssessorCenter = center.immutable();
        setTextureVariant(textureVariant);
        setPersistenceRequired();
        restrictTo(hearthAssessorCenter, HearthArchitectPolicy.HOME_RADIUS);
        setTarget(null);
        getNavigation().stop();
        despawnTimer = 0;
        approachState.surfaceY = blockPosition().getY();
        transitionToObserveAction();
    }

    public boolean isHearthAssessor() {
        return hearthAssessorId != null && hearthAssessorCenter != null;
    }

    public boolean isBoundToHearthAssessor(UUID hearthId) {
        return hearthId != null && hearthId.equals(hearthAssessorId);
    }

    public Optional<UUID> getHearthAssessorId() {
        return Optional.ofNullable(hearthAssessorId);
    }

    public Optional<BlockPos> getHearthAssessorCenter() {
        return Optional.ofNullable(hearthAssessorCenter);
    }

    public void bindToHearthPopulation(UUID hearthId, BlockPos home, int textureVariant) {
        hearthPopulationId = hearthId;
        hearthPopulationHome = home.immutable();
        setTextureVariant(textureVariant);
        setPersistenceRequired();
        restrictTo(hearthPopulationHome, HearthPopulationPolicy.ARCHITECT_HOME_RADIUS);
        setTarget(null);
        getNavigation().stop();
        despawnTimer = 0;
        approachState.surfaceY = blockPosition().getY();
        transitionToObserveAction();
    }

    public boolean isHearthPopulationResident() {
        return hearthPopulationId != null && hearthPopulationHome != null;
    }

    public boolean isBoundToHearthPopulation(UUID hearthId) {
        return hearthId != null && hearthId.equals(hearthPopulationId)
                && hearthPopulationHome != null;
    }

    public Optional<UUID> getHearthPopulationId() {
        return Optional.ofNullable(hearthPopulationId);
    }

    public Optional<BlockPos> getHearthPopulationHome() {
        return Optional.ofNullable(hearthPopulationHome);
    }

    public void bindToHearthMasterArchitect(
            UUID hearthId, BlockPos home, int textureVariant) {
        hearthMasterArchitectId = hearthId;
        hearthMasterArchitectHome = home.immutable();
        entityData.set(DATA_MASTER_ARCHITECT, true);
        entityData.set(DATA_MASTER_AURA_TIER,
                com.frozendawn.homo.MasterArchitectAuraTier.PASSIVE);
        applyMasterArchitectStats(true);
        setTextureVariant(textureVariant);
        setPersistenceRequired();
        restrictTo(hearthMasterArchitectHome, HearthMasterArchitectPolicy.HOME_RADIUS);
        setTarget(null);
        getNavigation().stop();
        despawnTimer = 0;
        approachState.surfaceY = blockPosition().getY();
        transitionToObserveAction();
        setCustomName(Component.literal("The Master Architect"));
        equipMasterArchitectStaff();
    }

    public boolean isHearthMasterArchitect() {
        return entityData.get(DATA_MASTER_ARCHITECT)
                || (hearthMasterArchitectId != null && hearthMasterArchitectHome != null);
    }

    public boolean isMasterMindCopy() {
        return entityData.get(DATA_MASTER_MIND_COPY);
    }

    public boolean isMasterArchitectVisual() {
        return isHearthMasterArchitect() || isMasterMindCopy();
    }

    public int getMasterAuraTier() {
        return entityData.get(DATA_MASTER_AURA_TIER);
    }

    void setMasterAuraTier(int tier) {
        entityData.set(DATA_MASTER_AURA_TIER,
                com.frozendawn.homo.MasterArchitectAuraTier.clamp(tier));
    }

    public Optional<UUID> getMindCopyRealMasterId() {
        return Optional.ofNullable(mindCopyRealMasterId);
    }

    public void onMindCopyHurt(
            ArchitectEntity copy, DamageSource source, float amount) {
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel) {
            hearthMasterController.onMindCopyHurt(
                    serverLevel, copy, source, amount);
        }
    }

    float prepareMindCopyDamage(
            ArchitectEntity copy, DamageSource source, float amount) {
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel) {
            return hearthMasterController.prepareMindCopyDamage(
                    serverLevel, copy, source, amount);
        }
        return amount;
    }

    public void onMindCopyDefeated(
            ArchitectEntity copy, @Nullable ServerPlayer killer) {
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel) {
            hearthMasterController.onMindCopyDefeated(serverLevel, copy, killer);
        }
    }

    public void onMindParticipantFailed(ServerPlayer player, String reason) {
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel) {
            hearthMasterController.onMindParticipantFailed(serverLevel, player, reason);
        }
    }

    void executeMindReturnDeath(DamageSource source) {
        if (!isHearthMasterArchitect() || !isAlive()) {
            return;
        }
        mindReturnDeathSoundOnly = true;
        setHealth(0.0F);
        die(source);
    }

    public boolean isMindReturnDeathSoundOnly() {
        return mindReturnDeathSoundOnly;
    }

    void executeFoldedCanonicalDeath(DamageSource source) {
        if (!isHearthMasterArchitect() || !isAlive()) {
            return;
        }
        foldedDeathPresentationAlreadyPlayed = true;
        mindReturnDeathDetonatesImmediately = true;
        setHealth(0.0F);
        die(source);
    }

    public void initializeMasterMindCopy(
            UUID realMasterId,
            float realMaximumHealth,
            float remainingHealth,
            int textureVariant) {
        mindCopyRealMasterId = realMasterId;
        entityData.set(DATA_MASTER_MIND_COPY, true);
        entityData.set(DATA_MASTER_AURA_TIER,
                com.frozendawn.homo.MasterArchitectAuraTier.FIGHT);
        entityData.set(DATA_MASTER_COMBAT_PHASE, MasterArchitectCombatPhase.FLOOD.id());
        entityData.set(DATA_MASTER_COMBAT_ACTION, MasterArchitectCombatAction.FLOOD_CHANNEL);
        entityData.set(DATA_MASTER_COMBAT_TICKS, 0);
        setTextureVariant(textureVariant);
        setPersistenceRequired();
        setCustomName(Component.literal("The Master Architect"));
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.max(1.0F, realMaximumHealth));
        }
        var armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(HearthMasterArchitectPolicy.ARMOR);
        }
        var knockback = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(HearthMasterArchitectPolicy.KNOCKBACK_RESISTANCE);
        }
        setHealth(Mth.clamp(remainingHealth, 1.0F, getMaxHealth()));
        masterBossBarEmptyOverride = false;
        equipMasterArchitectStaff();
        setMasterBossBarProvoked(true);
    }

    public boolean isBoundToHearthMasterArchitect(UUID hearthId) {
        return hearthId != null && hearthId.equals(hearthMasterArchitectId)
                && hearthMasterArchitectHome != null;
    }

    public Optional<UUID> getHearthMasterArchitectId() {
        return Optional.ofNullable(hearthMasterArchitectId);
    }

    public Optional<BlockPos> getHearthMasterArchitectHome() {
        return Optional.ofNullable(hearthMasterArchitectHome);
    }

    void prepareHearthAssessmentMode() {
        if (getBrainAction() != ACTION_OBSERVE) {
            transitionToObserveAction();
        }
        setTarget(null);
        setSprinting(false);
        blockBreaker.clearTarget();
        approachState.sprintRequested = false;
        if (combatState.isDrinkingPotion) {
            cancelDrinking();
        }
    }

    void setMasterCombatVisual(int action, int ticks) {
        if (!isMasterArchitectVisual()) {
            return;
        }
        entityData.set(DATA_MASTER_COMBAT_ACTION, action);
        entityData.set(DATA_MASTER_COMBAT_TICKS, Math.max(0, ticks));
    }

    void setMasterThermalCharge(float charge) {
        if (isMasterArchitectVisual()) {
            entityData.set(DATA_MASTER_THERMAL_CHARGE,
                    Mth.clamp(charge, 0.0F, 1.0F));
        }
    }

    void setMasterCombatPhase(MasterArchitectCombatPhase phase) {
        if (isMasterArchitectVisual()) {
            entityData.set(DATA_MASTER_COMBAT_PHASE, phase.id());
        }
    }

    void equipMasterArchitectStaff() {
        if (!isMasterArchitectVisual()) {
            return;
        }
        clearMasterAmbientHelmet();
        if (!getMainHandItem().is(ModItems.MASTER_ARCHITECT_STAFF.get())) {
            setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(ModItems.MASTER_ARCHITECT_STAFF.get()));
        }
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public void refreshMasterArchitectStats() {
        if (isHearthMasterArchitect()) {
            applyMasterArchitectStats(false);
        }
    }

    void setMasterBossBarProvoked(boolean provoked) {
        if (!isMasterArchitectVisual()) {
            return;
        }
        if (masterBossEvent == null && !provoked) {
            return;
        }
        ServerBossEvent bossEvent = getOrCreateMasterBossEvent();
        bossEvent.setProgress(masterBossBarProgress());
        bossEvent.setVisible(provoked);
    }

    void setMasterBossBarEmptyOverride(boolean empty) {
        if (!isMasterMindCopy()) {
            return;
        }
        masterBossBarEmptyOverride = empty;
        if (masterBossEvent != null) {
            masterBossEvent.setProgress(masterBossBarProgress());
        }
    }

    private void updateMasterBossBarProgress() {
        if (masterBossEvent != null) {
            masterBossEvent.setProgress(masterBossBarProgress());
        }
    }

    private float masterBossBarProgress() {
        return masterBossBarEmptyOverride
                ? 0.0F
                : MasterArchitectBossBarPolicy.progress(getHealth(), getMaxHealth());
    }

    private ServerBossEvent getOrCreateMasterBossEvent() {
        if (masterBossEvent == null) {
            masterBossEvent = new ServerBossEvent(
                    Component.translatable(MasterArchitectBossBarPolicy.NAME_KEY),
                    BossEvent.BossBarColor.BLUE,
                    BossEvent.BossBarOverlay.PROGRESS);
            masterBossEvent.setProgress(masterBossBarProgress());
            masterBossEvent.setVisible(false);
        }
        return masterBossEvent;
    }

    private void applyMasterArchitectStats(boolean healToFull) {
        var maxHealthAttribute = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute == null) {
            return;
        }
        String presetName = level() instanceof ServerLevel serverLevel
                ? ApocalypseState.get(serverLevel.getServer()).getPresetName()
                : "default";
        double targetMaxHealth = HearthMasterArchitectPolicy.maxHealthForPreset(presetName);
        float previousMax = getMaxHealth();
        float healthFraction = previousMax > 0.0F ? getHealth() / previousMax : 1.0F;
        boolean maxHealthChanged = Double.compare(
                maxHealthAttribute.getBaseValue(), targetMaxHealth) != 0;
        if (maxHealthChanged) {
            maxHealthAttribute.setBaseValue(targetMaxHealth);
        }
        var armorAttribute = getAttribute(Attributes.ARMOR);
        if (armorAttribute != null) {
            armorAttribute.setBaseValue(HearthMasterArchitectPolicy.ARMOR);
        }
        var knockbackAttribute = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttribute != null) {
            knockbackAttribute.setBaseValue(
                    HearthMasterArchitectPolicy.KNOCKBACK_RESISTANCE);
        }
        if (healToFull) {
            setHealth(getMaxHealth());
        } else if (maxHealthChanged) {
            setHealth(Mth.clamp(
                    getMaxHealth() * healthFraction, 0.0F, getMaxHealth()));
        }
    }

    public int getSurfaceY() { return approachState.surfaceY; }
    public DStarLitePathfinder getDStarPathfinder() { return approachState.dstar; }
    public boolean isBuildingIce() { return entityData.get(DATA_BUILDING_ICE); }

    // ========================
    //  IMMUNITIES
    // ========================

    @Override
    public boolean canFreeze() { return false; }

    @Override
    public int getTicksFrozen() { return 0; }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (effectInstance.getEffect() == MobEffects.MOVEMENT_SLOWDOWN) return false;
        return super.canBeAffected(effectInstance);
    }

    // ========================
    //  SOUNDS
    // ========================

    @Override
    public float getVoicePitch() {
        return isMasterArchitectVisual()
                ? 0.38F + random.nextFloat() * 0.06F
                : 0.5F + random.nextFloat() * 0.15F;
    }

    @Override
    protected float getSoundVolume() {
        return isMasterArchitectVisual() ? 1.35F : super.getSoundVolume();
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isMasterArchitectVisual()
                ? ModSounds.MASTER_ARCHITECT_AMBIENT.get()
                : ModSounds.ARCHITECT_AMBIENT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (isMasterArchitectVisual()
                && (suppressMasterHurtSound
                || clientMasterTetherHurtSuppressionTicks > 0)) {
            return null;
        }
        return isMasterArchitectVisual()
                ? ModSounds.MASTER_ARCHITECT_HURT.get()
                : ModSounds.ARCHITECT_HURT.get();
    }

    public void setClientMasterTetherFeedback(int feedbackStateId) {
        MasterArchitectCombatPolicy.TetherFeedbackState feedback =
                MasterArchitectCombatPolicy.TetherFeedbackState.fromId(feedbackStateId);
        clientMasterTetherHurtSuppressionTicks = feedback.suppressesNormalHitSound()
                ? 4
                : 0;
    }

    @Override
    protected SoundEvent getDeathSound() {
        if (foldedDeathPresentationAlreadyPlayed || mindReturnDeathSoundOnly) {
            return null;
        }
        return isMasterArchitectVisual()
                ? ModSounds.MASTER_ARCHITECT_DEATH.get()
                : ModSounds.ARCHITECT_DEATH.get();
    }

    // ========================
    //  NBT
    // ========================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ArchitectPersistence.writeCoreState(
                tag,
                getTextureVariant(),
                despawnTimer,
                getBrainAction(),
                towerEncounter,
                towerEncounterId
        );
        ArchitectPersistence.writeObservationMemory(tag, observationMemory);
        ArchitectPersistence.writeCombatState(tag, combatState);
        ArchitectPersistence.writeApproachState(tag, approachState, scaffoldIce, tacticalIce);
        if (hearthAssessorId != null && hearthAssessorCenter != null) {
            tag.putUUID("HearthAssessorId", hearthAssessorId);
            tag.putLong("HearthAssessorCenter", hearthAssessorCenter.asLong());
        }
        if (hearthPopulationId != null && hearthPopulationHome != null) {
            tag.putUUID("HearthPopulationId", hearthPopulationId);
            tag.putLong("HearthPopulationHome", hearthPopulationHome.asLong());
        }
        if (hearthMasterArchitectId != null && hearthMasterArchitectHome != null) {
            tag.putUUID("HearthMasterArchitectId", hearthMasterArchitectId);
            tag.putLong("HearthMasterArchitectHome", hearthMasterArchitectHome.asLong());
            tag.putInt("MasterAuraTier", getMasterAuraTier());
            hearthMasterController.addSaveData(tag);
        }
        if (isMasterMindCopy() && mindCopyRealMasterId != null) {
            tag.putUUID("MasterMindCopyRealId", mindCopyRealMasterId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ArchitectPersistence.CoreState coreState = ArchitectPersistence.readCoreState(tag);
        setTextureVariant(coreState.textureVariant());
        despawnTimer = coreState.despawnTimer();
        setBrainAction(coreState.currentAction());
        // Delay first pathfinding after world load to prevent freeze
        pathRecalcCooldown = 40;
        brainState.setReevalCooldown(40);
        brainState.setActionHoldTicks(0);
        ArchitectPersistence.readObservationMemory(tag, observationMemory);
        ArchitectPersistence.readCombatState(tag, combatState);
        ArchitectPersistence.readApproachState(tag, approachState, scaffoldIce, tacticalIce);
        towerEncounter = coreState.towerEncounter();
        towerEncounterId = coreState.towerEncounterId();
        if (tag.hasUUID("HearthAssessorId") && tag.contains("HearthAssessorCenter")) {
            hearthAssessorId = tag.getUUID("HearthAssessorId");
            hearthAssessorCenter = BlockPos.of(tag.getLong("HearthAssessorCenter"));
            setPersistenceRequired();
            restrictTo(hearthAssessorCenter, HearthArchitectPolicy.HOME_RADIUS);
            despawnTimer = 0;
        } else {
            hearthAssessorId = null;
            hearthAssessorCenter = null;
        }
        if (tag.hasUUID("HearthPopulationId") && tag.contains("HearthPopulationHome")) {
            hearthPopulationId = tag.getUUID("HearthPopulationId");
            hearthPopulationHome = BlockPos.of(tag.getLong("HearthPopulationHome"));
            setPersistenceRequired();
            restrictTo(hearthPopulationHome, HearthPopulationPolicy.ARCHITECT_HOME_RADIUS);
            despawnTimer = 0;
        } else {
            hearthPopulationId = null;
            hearthPopulationHome = null;
        }
        if (tag.hasUUID("HearthMasterArchitectId")
                && tag.contains("HearthMasterArchitectHome")) {
            hearthMasterArchitectId = tag.getUUID("HearthMasterArchitectId");
            hearthMasterArchitectHome = BlockPos.of(
                    tag.getLong("HearthMasterArchitectHome"));
            entityData.set(DATA_MASTER_ARCHITECT, true);
            entityData.set(DATA_MASTER_AURA_TIER,
                    com.frozendawn.homo.MasterArchitectAuraTier.clamp(
                            tag.getInt("MasterAuraTier")));
            if (getMasterAuraTier() == com.frozendawn.homo.MasterArchitectAuraTier.NONE) {
                entityData.set(DATA_MASTER_AURA_TIER,
                        com.frozendawn.homo.MasterArchitectAuraTier.PASSIVE);
            }
            setPersistenceRequired();
            restrictTo(hearthMasterArchitectHome, HearthMasterArchitectPolicy.HOME_RADIUS);
            despawnTimer = 0;
            setCustomName(Component.literal("The Master Architect"));
            hearthMasterController.readSaveData(tag);
            applyMasterArchitectStats(false);
            equipMasterArchitectStaff();
        } else {
            hearthMasterArchitectId = null;
            hearthMasterArchitectHome = null;
            entityData.set(DATA_MASTER_ARCHITECT, false);
        }
        if (tag.hasUUID("MasterMindCopyRealId")) {
            mindCopyRealMasterId = tag.getUUID("MasterMindCopyRealId");
            entityData.set(DATA_MASTER_MIND_COPY, true);
            entityData.set(DATA_MASTER_AURA_TIER,
                    com.frozendawn.homo.MasterArchitectAuraTier.FIGHT);
            entityData.set(DATA_MASTER_COMBAT_PHASE, MasterArchitectCombatPhase.FLOOD.id());
            setPersistenceRequired();
            setCustomName(Component.literal("The Master Architect"));
            equipMasterArchitectStaff();
            setMasterBossBarProvoked(true);
        } else {
            mindCopyRealMasterId = null;
            entityData.set(DATA_MASTER_MIND_COPY, false);
        }
        if (approachState.surfaceY == 0) approachState.surfaceY = blockPosition().getY(); // migration for existing entities
        syncRenderState();
    }

    // ========================
    //  DESPAWN OVERRIDES
    // ========================

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public boolean shouldBeSaved() {
        return !isMasterMindCopy() && super.shouldBeSaved();
    }

    @Override
    public void checkDespawn() { }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return !isHearthPopulationResident() && !isMasterArchitectVisual();
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (isMasterArchitectVisual()) {
            ServerBossEvent event = getOrCreateMasterBossEvent();
            event.addPlayer(player);
            if (isMasterMindCopy()) {
                event.setVisible(true);
            }
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        if (masterBossEvent != null) {
            masterBossEvent.removePlayer(player);
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (masterBossEvent != null) {
            masterBossEvent.removeAllPlayers();
        }
        super.remove(reason);
    }
}
