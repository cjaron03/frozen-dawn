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
import com.frozendawn.entity.architect.ArchitectWalkBreakPlanner;
import com.frozendawn.entity.architect.ArchitectWalkCorridorState;
import com.frozendawn.entity.architect.ArchitectWalkMotionPlanner;
import com.frozendawn.entity.architect.ArchitectWalkProgress;
import com.frozendawn.entity.architect.ArchitectWalkTracking;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.ArchitectBreakPolicy;
import com.frozendawn.entity.ai.ArchitectMoveControl;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TowerEncounterController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.BlockState;
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

    // --- Action Constants ---
    public static final int ACTION_OBSERVE = 0;
    public static final int ACTION_APPROACH = 1;
    public static final int ACTION_ATTACK_MELEE = 2;
    public static final int ACTION_RETREAT = 3;
    public static final int ACTION_FORTIFY = 4;
    public static final int ACTION_TRAP_SET = 5;
    public static final int ACTION_PEEK = 6;

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
    static final int MIN_OBSERVE_TICKS = 600;
    static final int MAX_OBSERVE_TICKS = 1200;
    static final double SPAWN_OBSERVE_CUE_RANGE_SQR = 72.0 * 72.0;

    private static final int PLAYER_MEMORY_TICKS = 200;
    private boolean towerEncounter = false;
    private long towerEncounterId = Long.MIN_VALUE;

    private static final int HEAL_COOLDOWN_TICKS = 1200;
    private static final int DRINK_DURATION = 32;
    static final double RETREAT_DISTANCE = 16.0;
    private static final int MAX_SAFE_FALL_DISTANCE = 10;

    // --- Burst Damage Tracking ---
    /** Damage taken in the last BURST_WINDOW ticks. Used to boost retreat scoring. */
    private static final int BURST_WINDOW = 60; // 3 seconds

    // --- Block Breaker ---
    private final ArchitectBlockBreaker blockBreaker = new ArchitectBlockBreaker(this);
    private final ArchitectApproachController approachController =
            new ArchitectApproachController(this, approachState, blockBreaker);
    private final ArchitectCombatController combatController =
            new ArchitectCombatController(this, combatState, blockBreaker);
    private final ArchitectObservationController observationController =
            new ArchitectObservationController(this, observationMemory, approachState, approachController, blockBreaker);
    private final ArchitectTacticsController tacticsController =
            new ArchitectTacticsController(this, observationMemory, blockBreaker);
    private final ArchitectDecisionEngine decisionEngine = new ArchitectDecisionEngine();
    private final ArchitectFxController fxController = new ArchitectFxController(this, blockBreaker);

    // --- Despawn ---
    private int despawnTimer = 0;
    private static final int DESPAWN_TIMEOUT = 6000;

    // --- Misc ---
    private int peekTicks = 0;
    private int trapCooldown = 0;
    private int pathRecalcCooldown = 0;
    private static final int WALK_STUCK_BREAK_TICKS = 16;
    private static final int WALK_STUCK_REINIT_TICKS = 48;
    private static final int WALK_COMMIT_TICKS = 12;
    private static final int WALK_COMMIT_NO_PROGRESS_TICKS = 6;
    private static final int WALK_COMMIT_DEADMAN_TICKS = 8;
    private static final double WALK_COMMIT_PROGRESS_EPSILON = 0.10;
    private static final double WALK_COMMIT_DEADMAN_DISPLACEMENT_SQR = 0.20;
    private static final double WALK_WAYPOINT_REACH_HORIZONTAL_SQR = 0.64;
    private static final double WALK_WAYPOINT_REACH_UPWARD_VERTICAL = 0.60;
    private static final double WALK_WAYPOINT_REACH_DOWNWARD_VERTICAL = 1.05;
    private static final double WALK_AUTO_JUMP_MIN_VERTICAL_DELTA = 0.90;
    private static final double WALK_AUTO_JUMP_MAX_HORIZONTAL_SQR = 0.90;
    private static final int WALK_TARGET_SHIFT_GRACE_TICKS = 4;
    private static final double WALK_TARGET_SHIFT_HORIZONTAL_SQR = 36.0;
    private static final int WALK_TARGET_SHIFT_VERTICAL = 2;
    private static final int WALK_CORRIDOR_LOOKAHEAD_STEPS = 2;
    private static final int WALK_SPRINT_STRAIGHT_STEPS = 2;
    private static final float WALK_MAX_ROTATE = 35.0F;
    private static final double APPROACH_SPRINT_SPEED = 1.15;
    private static final int MAX_REPEAT_UNSTICK_BREAK_ATTEMPTS = 3;
    private static final int WALK_NAV_CORRIDOR_MAX_STEPS = 8;
    private static final double WALK_NAV_MAX_DISTANCE = 10.0;
    private static final double DIRECT_APPROACH_PATH_HORIZONTAL_RANGE = 8.0;
    private static final double DIRECT_APPROACH_PATH_VERTICAL_RANGE = 4.0;
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
        if (level().isClientSide()) return;

        // Defensive: fix surfaceY if it wasn't set (NBT load before positioning)
        if (approachState.surfaceY == 0) approachState.surfaceY = blockPosition().getY();
        ensureAmbientHelmet();

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
        ArchitectWalkTracking.trackWalkStep(
                approachState,
                blockPosition(),
                stepPos,
                getDeltaMovement(),
                onGround());
    }

    void resetWalkStuckTracker() {
        ArchitectWalkTracking.resetWalkStuckTracker(approachState);
    }

    void recordWalkCellHistory() {
        ArchitectWalkTracking.recordWalkCellHistory(approachState, blockPosition());
    }

    private void resetWalkCellHistory() {
        ArchitectWalkTracking.resetWalkCellHistory(approachState);
    }

    void resetUnstickBreakTracker() {
        ArchitectWalkTracking.resetUnstickBreakTracker(approachState);
    }

    private void commitWalkStep(List<BlockPos> corridorNodes, @Nullable LivingEntity target) {
        ArchitectWalkCorridorState.commit(
                approachState,
                corridorNodes,
                blockPosition(),
                position(),
                target != null ? target.blockPosition() : null,
                WALK_COMMIT_TICKS,
                waypoint -> ArchitectWalkProgress.distanceToWaypointSqr(getX(), getY(), getZ(), waypoint));
        resetWalkStuckTracker();
    }

    void clearCommittedWalk() {
        ArchitectWalkCorridorState.clear(approachState);
    }

    private void invalidateCommittedWalk(String reason, @Nullable LivingEntity target) {
        if (approachState.committedWalkWaypoint == null) {
            return;
        }
        LOGGER.info("[Architect] WALK corridor invalidated: reason={} current={} firstStep={} waypoint={} age={} ttlLeft={} targetSnapshot={} targetNow={}",
                reason, blockPosition(), approachState.committedWalkFirstStepPos, approachState.committedWalkWaypoint,
                approachState.committedWalkAgeTicks, approachState.committedWalkTicks, approachState.committedWalkTargetSnapshot,
                target != null ? target.blockPosition() : null);
        clearCommittedWalk();
    }

    @Nullable
    BlockPos getCommittedWalkSteeringTarget() {
        return ArchitectWalkCorridorState.getSteeringTarget(approachState);
    }

    private boolean shouldSprintRetreat(@Nullable LivingEntity target) {
        return target != null && getBrainAction() == ACTION_RETREAT && combatState.retreatPhase == 0;
    }

    private boolean canSprintApproachBase(@Nullable LivingEntity target) {
        if (target == null || getBrainAction() != ACTION_APPROACH) {
            return false;
        }
        if (blockBreaker.hasTarget()
                || blockBreaker.isMining()
                || approachState.scaffoldTarget != null
                || approachState.stepOffTarget != null
                || approachState.ceilingBreachPos != null) {
            return false;
        }
        return !isTargetWithinMeleeEngageGeometry(target);
    }

    private boolean hasStraightCommittedWalkSprintLane() {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null || approachState.committedWalkCorridor.isEmpty()) {
            return false;
        }

        int corridorIndex = Math.max(0, Math.min(
                approachState.committedWalkCorridorIndex,
                approachState.committedWalkCorridor.size() - 1));
        BlockPos cursor = steeringTarget;
        Direction runDirection = null;
        int straightSteps = 0;

        for (int i = corridorIndex + 1; i < approachState.committedWalkCorridor.size(); i++) {
            BlockPos candidate = approachState.committedWalkCorridor.get(i);
            if (candidate.getY() != cursor.getY()) {
                break;
            }

            Direction segmentDirection = getPrimaryHorizontalDirection(cursor, candidate);
            if (segmentDirection == null) {
                break;
            }

            if (runDirection == null) {
                runDirection = segmentDirection;
            } else if (segmentDirection != runDirection) {
                break;
            }

            cursor = candidate;
            straightSteps++;
        }

        return straightSteps >= WALK_SPRINT_STRAIGHT_STEPS;
    }

    private boolean shouldSprintCommittedWalk(@Nullable LivingEntity target) {
        return canSprintApproachBase(target) && hasStraightCommittedWalkSprintLane();
    }

    private boolean shouldSprintDirectApproach(@Nullable LivingEntity target) {
        return canSprintApproachBase(target) && target != null && hasLineOfSight(target);
    }

    private double getApproachTravelSpeed() {
        return approachState.sprintRequested ? APPROACH_SPRINT_SPEED : 1.0;
    }

    private boolean advanceCommittedWalkProgress() {
        return ArchitectWalkProgress.advanceCommittedWalkProgress(
                approachState,
                blockPosition(),
                getX(),
                getY(),
                getZ(),
                WALK_WAYPOINT_REACH_HORIZONTAL_SQR,
                WALK_WAYPOINT_REACH_UPWARD_VERTICAL,
                WALK_WAYPOINT_REACH_DOWNWARD_VERTICAL);
    }

    private boolean continueCommittedWalk() {
        ArchitectWalkMotionPlanner.MotionStep motion = ArchitectWalkMotionPlanner.planCommittedWalkStep(
                approachState,
                getX(),
                getY(),
                getZ(),
                getEyeY(),
                onGround(),
                WALK_CORRIDOR_LOOKAHEAD_STEPS,
                WALK_AUTO_JUMP_MIN_VERTICAL_DELTA,
                WALK_AUTO_JUMP_MAX_HORIZONTAL_SQR,
                this::getPrimaryHorizontalDirection);
        if (motion == null) {
            return false;
        }
        if (motion.shouldJump()) {
            getJumpControl().jump();
        }

        // Follow D* corridors with raw MoveControl so edge/scaffold approach cells
        // do not get vetoed by vanilla navigation before the scaffold action can fire.
        getNavigation().stop();
        Vec3 moveTarget = motion.moveTarget();
        Vec3 lookTarget = motion.lookTarget();
        getMoveControl().setWantedPosition(moveTarget.x, moveTarget.y, moveTarget.z, getApproachTravelSpeed());
        getLookControl().setLookAt(lookTarget.x, lookTarget.y, lookTarget.z, 40f, 30f);
        return true;
    }

    private boolean shouldContinueCommittedWalk(@Nullable LivingEntity target) {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null) return false;
        if (approachState.committedWalkTicks <= 0) {
            invalidateCommittedWalk("TTL", target);
            return false;
        }

        BlockState feetState = level().getBlockState(steeringTarget);
        BlockState headState = level().getBlockState(steeringTarget.above());
        if (isPathObstructingState(feetState, steeringTarget)
                || isPathObstructingState(headState, steeringTarget.above())) {
            invalidateCommittedWalk("BLOCKED", target);
            return false;
        }

        if (target != null
                && approachState.committedWalkTargetSnapshot != null
                && approachState.committedWalkAgeTicks >= WALK_TARGET_SHIFT_GRACE_TICKS) {
            BlockPos targetPos = target.blockPosition();
            if (ArchitectWalkProgress.horizontalDistanceSqr(targetPos, approachState.committedWalkTargetSnapshot)
                    > WALK_TARGET_SHIFT_HORIZONTAL_SQR
                    || Math.abs(targetPos.getY() - approachState.committedWalkTargetSnapshot.getY()) > WALK_TARGET_SHIFT_VERTICAL) {
                invalidateCommittedWalk("TARGET_SHIFT", target);
                return false;
            }
        }

        return true;
    }

    @Nullable
    BlockPos getImmediateBacktrackPos() {
        return ArchitectWalkCorridorState.getImmediateBacktrackPos(approachState, blockPosition());
    }

    boolean tryContinueCommittedWalk(@Nullable LivingEntity target) {
        approachState.sprintRequested = shouldSprintCommittedWalk(target);
        if (!shouldContinueCommittedWalk(target)) {
            approachState.sprintRequested = false;
            return false;
        }

        if (!advanceCommittedWalkProgress()) {
            approachState.sprintRequested = false;
            return false;
        }

        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null) {
            approachState.sprintRequested = false;
            return false;
        }

        double distSqr = ArchitectWalkProgress.distanceToWaypointSqr(getX(), getY(), getZ(), steeringTarget);
        if (distSqr + WALK_COMMIT_PROGRESS_EPSILON < approachState.committedWalkLastDistSqr) {
            approachState.committedWalkLastDistSqr = distSqr;
            approachState.committedWalkNoProgressTicks = 0;
        } else {
            approachState.committedWalkNoProgressTicks++;
        }

        if (approachState.committedWalkNoProgressTicks >= WALK_COMMIT_NO_PROGRESS_TICKS) {
            approachState.walkStuckTicks = Math.max(approachState.walkStuckTicks, WALK_STUCK_BREAK_TICKS);
            BlockPos stuckTarget = steeringTarget;
            invalidateCommittedWalk("STUCK", target);
            approachState.sprintRequested = false;
            if (stuckTarget != null && handleWalkStuck(stuckTarget, target)) {
                return true;
            }
            return false;
        }

        if (approachState.committedWalkStartVec != null
                && approachState.committedWalkAgeTicks >= WALK_COMMIT_DEADMAN_TICKS
                && position().distanceToSqr(approachState.committedWalkStartVec) < WALK_COMMIT_DEADMAN_DISPLACEMENT_SQR) {
            approachState.walkStuckTicks = Math.max(approachState.walkStuckTicks, WALK_STUCK_BREAK_TICKS);
            BlockPos stuckTarget = steeringTarget;
            invalidateCommittedWalk("DEADMAN", target);
            approachState.sprintRequested = false;
            if (stuckTarget != null && handleWalkStuck(stuckTarget, target)) {
                return true;
            }
            return false;
        }

        if (!continueCommittedWalk()) {
            approachState.sprintRequested = false;
            return false;
        }

        return true;
    }

    void invalidateStaleApproachBreakTarget(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
        BlockPos bt = blockBreaker.getTarget();
        if (bt == null) return;

        BlockPos desiredBreak = step.breakTarget();
        boolean continuingBreak = (step.type() == DStarLitePathfinder.StepType.BREACH
                || step.type() == DStarLitePathfinder.StepType.DIG_DOWN)
                && desiredBreak != null
                && bt.equals(desiredBreak);

        double dxToTarget = target != null ? target.getX() - getX() : 0.0;
        double dzToTarget = target != null ? target.getZ() - getZ() : 0.0;
        double horizontalTargetDelta = Math.sqrt(dxToTarget * dxToTarget + dzToTarget * dzToTarget);
        boolean continuingDropInBreak = target != null
                && step.type() == DStarLitePathfinder.StepType.SCAFFOLD_BRIDGE
                && target.getY() < getY() - 1.0
                && horizontalTargetDelta <= 2.5
                && bt.getY() == blockPosition().getY() - 1
                && Math.abs(bt.getX() - blockPosition().getX()) <= 3
                && Math.abs(bt.getZ() - blockPosition().getZ()) <= 3;

        boolean continuingWalkBreak = step.type() == DStarLitePathfinder.StepType.WALK
                && shouldContinueWalkObstructionBreak(step, bt);

        if (!continuingBreak && !continuingDropInBreak && !continuingWalkBreak) {
            blockBreaker.clearTarget();
            if (bt.equals(approachState.ceilingBreachPos)) {
                approachState.ceilingBreachPos = null;
            }
        }
    }

    boolean shouldContinueApproachBreak(@Nullable LivingEntity target, BlockPos expectedBreakTarget) {
        if (getBrainAction() != ACTION_APPROACH || target == null) return false;

        BlockPos targetPos = target.blockPosition();
        if (approachState.dstar.needsReinitialize(targetPos)) return false;

        if (!approachState.dstar.isSearchComplete()) {
            approachState.dstar.computePartial(300, level());
            if (!approachState.dstar.isSearchComplete()) return false;
        }

        approachState.dstar.updateStart(blockPosition());
        if (!approachState.dstar.isSearchComplete()) {
            approachState.dstar.computePartial(200, level());
            if (!approachState.dstar.isSearchComplete()) return false;
        }

        DStarLitePathfinder.NextStep nextStep = approachState.dstar.getNextStep(blockPosition(), level());
        return nextStep.type() == DStarLitePathfinder.StepType.BREACH
                && expectedBreakTarget.equals(nextStep.breakTarget());
    }

    private boolean attemptWalkUnstickBreak(BlockPos stepPos) {
        BlockPos blockedCandidate = approachState.repeatedUnstickBreakAttempts >= MAX_REPEAT_UNSTICK_BREAK_ATTEMPTS
                ? approachState.lastUnstickBreakCandidate
                : null;

        BlockPos from = blockPosition();
        Direction toward = getPrimaryHorizontalDirection(from, stepPos);
        Set<BlockPos> immediateCandidates = ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, stepPos, toward);
        BlockPos candidate = ArchitectWalkBreakPlanner.selectPreferredBreakCandidate(
                immediateCandidates,
                blockedCandidate,
                this::isBreakableBlock,
                this::isLastResortBreakBlock);
        if (candidate != null) {
            if (candidate.equals(approachState.lastUnstickBreakCandidate)) {
                approachState.repeatedUnstickBreakAttempts++;
            } else {
                approachState.lastUnstickBreakCandidate = candidate.immutable();
                approachState.repeatedUnstickBreakAttempts = 1;
            }
            blockBreaker.setTarget(candidate);
            LOGGER.info("[Architect] WALK stuck: breaking {} to unjam move toward {}", candidate, stepPos);
            return true;
        }

        if (!approachState.committedWalkCorridor.isEmpty()) {
            int fromIndex = Math.max(0, Math.min(approachState.committedWalkCorridorIndex, approachState.committedWalkCorridor.size()));
            BlockPos corridorBreakTarget = ArchitectWalkBreakPlanner.findCorridorBreakTarget(
                    approachState.committedWalkCorridor.subList(fromIndex, approachState.committedWalkCorridor.size()),
                    this::isBreakableBlock,
                    this::isLastResortBreakBlock);
            if (corridorBreakTarget != null
                    && (blockedCandidate == null || !blockedCandidate.equals(corridorBreakTarget))) {
                if (corridorBreakTarget.equals(approachState.lastUnstickBreakCandidate)) {
                    approachState.repeatedUnstickBreakAttempts++;
                } else {
                    approachState.lastUnstickBreakCandidate = corridorBreakTarget.immutable();
                    approachState.repeatedUnstickBreakAttempts = 1;
                }
                blockBreaker.setTarget(corridorBreakTarget);
                LOGGER.info("[Architect] WALK stuck: breaking corridor obstruction {} while following {}",
                        corridorBreakTarget, stepPos);
                return true;
            }
        }
        return false;
    }

    boolean handleWalkStuck(BlockPos stepPos, @Nullable LivingEntity target) {
        if (approachState.walkStuckTicks < WALK_STUCK_BREAK_TICKS || blockBreaker.hasTarget()) {
            return false;
        }
        if (attemptWalkUnstickBreak(stepPos)) {
            return true;
        }
        if (approachState.walkStuckTicks >= WALK_STUCK_REINIT_TICKS && target != null) {
            approachState.dstar.onLocalBlockChanged(blockPosition(), level());
            approachState.dstar.setSurfaceY(approachState.surfaceY);
            approachState.dstar.initialize(target.blockPosition(), blockPosition(), level());
            approachState.dstar.computePartial(1000, level());
            approachState.walkStuckTicks = 0;
            LOGGER.info("[Architect] WALK stuck-trigger replan: refreshed D* around {}", blockPosition());
            return true;
        }
        return false;
    }

    void executeVanillaWalkStep(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
        BlockPos startPos = blockPosition();
        BlockPos stepPos = step.pos();
        List<BlockPos> corridorNodes = buildWalkCorridorNodes(startPos, step);
        if (corridorNodes.isEmpty()) {
            corridorNodes = List.of(stepPos.immutable());
        }
        if (isReverseOnlyWalkCorridor(startPos, step, corridorNodes)) {
            handleReverseOnlyWalkCorridor(stepPos, target);
            return;
        }
        BlockPos corridorBreakTarget = ArchitectWalkBreakPlanner.findCorridorBreakTarget(
                corridorNodes,
                this::isBreakableBlock,
                this::isLastResortBreakBlock);
        if (corridorBreakTarget != null) {
            startWalkCorridorBreak(corridorBreakTarget);
            return;
        }
        BlockPos waypoint = corridorNodes.get(corridorNodes.size() - 1);
        commitWalkStep(corridorNodes, target);
        approachState.sprintRequested = shouldSprintCommittedWalk(target);
        if (!continueCommittedWalk()) {
            approachState.sprintRequested = false;
            clearCommittedWalk();
            return;
        }

    }

    boolean canDirectChaseApproach(@Nullable LivingEntity target) {
        if (target == null || blockBreaker.hasTarget() || !hasLineOfSight(target)) {
            return false;
        }
        if (ArchitectMeleeEngagement.horizontalDistanceTo(this, target) > DIRECT_APPROACH_PATH_HORIZONTAL_RANGE
                || ArchitectMeleeEngagement.verticalDistanceTo(this, target) > DIRECT_APPROACH_PATH_VERTICAL_RANGE) {
            return false;
        }
        return ArchitectMeleeEngagement.hasCleanReachableApproachPath(getNavigation(), target);
    }

    void executeDirectApproachChase(LivingEntity target) {
        clearCommittedWalk();
        resetWalkStuckTracker();
        approachState.unreachableTicks = 0;
        approachState.sprintRequested = shouldSprintDirectApproach(target);
        getNavigation().moveTo(target, getApproachTravelSpeed());
        getLookControl().setLookAt(target, 30f, 30f);
    }

    private boolean hasCleanReachableApproachPath(LivingEntity target) {
        return ArchitectMeleeEngagement.hasCleanReachableApproachPath(getNavigation(), target);
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

    private List<BlockPos> buildWalkCorridorNodes(BlockPos startPos, DStarLitePathfinder.NextStep firstStep) {
        return buildWalkCorridorNodes(startPos, firstStep, true);
    }

    private List<BlockPos> previewWalkCorridorNodes(BlockPos startPos, DStarLitePathfinder.NextStep firstStep) {
        return buildWalkCorridorNodes(startPos, firstStep, false);
    }

    private List<BlockPos> buildWalkCorridorNodes(BlockPos startPos, DStarLitePathfinder.NextStep firstStep,
                                                  boolean updatePendingWalkBacktrack) {
        List<BlockPos> corridor = new ArrayList<>(WALK_NAV_CORRIDOR_MAX_STEPS);
        if (firstStep.type() != DStarLitePathfinder.StepType.WALK) {
            if (updatePendingWalkBacktrack) {
                approachState.pendingWalkBacktrackPos = startPos.immutable();
            }
            corridor.add(firstStep.pos().immutable());
            return corridor;
        }

        BlockPos waypoint = firstStep.pos().immutable();
        corridor.add(waypoint);
        BlockPos current = waypoint;
        BlockPos previous = startPos;
        if (updatePendingWalkBacktrack) {
            approachState.pendingWalkBacktrackPos = startPos.immutable();
        }
        Set<Long> visited = new HashSet<>();
        visited.add(startPos.asLong());
        visited.add(waypoint.asLong());
        double maxDistSqr = WALK_NAV_MAX_DISTANCE * WALK_NAV_MAX_DISTANCE;

        for (int steps = 1; steps < WALK_NAV_CORRIDOR_MAX_STEPS; steps++) {
            double dx = waypoint.getX() - startPos.getX();
            double dy = waypoint.getY() - startPos.getY();
            double dz = waypoint.getZ() - startPos.getZ();
            if (dx * dx + dy * dy + dz * dz >= maxDistSqr) {
                break;
            }

            DStarLitePathfinder.NextStep next = approachState.dstar.peekNextStep(current, level(), previous);
            if (next.type() != DStarLitePathfinder.StepType.WALK) {
                break;
            }

            BlockPos nextPos = next.pos();
            if (nextPos.equals(current) || visited.contains(nextPos.asLong())) {
                break;
            }

            double nextDx = nextPos.getX() - startPos.getX();
            double nextDy = nextPos.getY() - startPos.getY();
            double nextDz = nextPos.getZ() - startPos.getZ();
            if (nextDx * nextDx + nextDy * nextDy + nextDz * nextDz > maxDistSqr) {
                break;
            }

            previous = current;
            current = nextPos.immutable();
            waypoint = current;
            if (updatePendingWalkBacktrack) {
                approachState.pendingWalkBacktrackPos = previous.immutable();
            }
            corridor.add(current);
            visited.add(current.asLong());
        }

        return corridor;
    }

    private boolean shouldContinueWalkObstructionBreak(DStarLitePathfinder.NextStep step, BlockPos breakTarget) {
        if (isBreakableBlock(breakTarget)) {
            BlockPos from = blockPosition();
            Direction toward = getPrimaryHorizontalDirection(from, step.pos());
            Set<BlockPos> immediateCandidates =
                    ArchitectWalkBreakPlanner.collectUnstickBreakCandidates(from, step.pos(), toward);
            if (immediateCandidates.contains(breakTarget)) {
                return true;
            }
        }

        List<BlockPos> corridorNodes = previewWalkCorridorNodes(blockPosition(), step);
        if (corridorNodes.isEmpty()) {
            corridorNodes = List.of(step.pos().immutable());
        }
        BlockPos corridorCandidate = ArchitectWalkBreakPlanner.findCorridorBreakTarget(
                corridorNodes,
                this::isBreakableBlock,
                this::isLastResortBreakBlock);
        return corridorCandidate != null && breakTarget.equals(corridorCandidate);
    }

    private boolean isLastResortBreakBlock(BlockPos pos) {
        return ArchitectBreakPolicy.isLastResortBreakBlock(level().getBlockState(pos));
    }

    private void startWalkCorridorBreak(BlockPos breakTarget) {
        clearWalkNavigationState(true);
        clearCommittedWalk();
        resetWalkStuckTracker();
        resetUnstickBreakTracker();
        blockBreaker.setTarget(breakTarget.immutable());
        LOGGER.info("[Architect] WALK corridor requires breach at {}", breakTarget);
        walkToBreakTarget();
    }

    private boolean isReverseOnlyWalkCorridor(BlockPos startPos, DStarLitePathfinder.NextStep firstStep,
                                              List<BlockPos> corridorNodes) {
        if (firstStep.type() != DStarLitePathfinder.StepType.WALK || corridorNodes.size() != 1) {
            return false;
        }

        BlockPos firstNode = corridorNodes.get(0);
        DStarLitePathfinder.NextStep continuation = approachState.dstar.peekNextStep(firstNode, level(), startPos);
        return continuation.type() == DStarLitePathfinder.StepType.WALK
                && continuation.pos().equals(startPos);
    }

    private boolean handleReverseOnlyWalkCorridor(BlockPos stepPos, @Nullable LivingEntity target) {
        clearWalkNavigationState(true);
        clearCommittedWalk();
        trackWalkStep(stepPos);

        return handleWalkStuck(stepPos, target);
    }

    void clearWalkNavigationState(boolean stopNavigation) {
        if (stopNavigation) {
            getNavigation().stop();
        }
    }

    @Nullable
    private Direction getPrimaryHorizontalDirection(BlockPos from, BlockPos to) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dz = Integer.compare(to.getZ(), from.getZ());
        if (Math.abs(dx) >= Math.abs(dz) && dx != 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }
        if (dz != 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return null;
    }

    void keepNearbyWoodenDoorsOpen() {
        ArchitectBlockEnvironment.keepNearbyWoodenDoorsOpen(this);
    }

    void keepDoorOpenNear(BlockPos center) {
        ArchitectBlockEnvironment.keepDoorOpenNear(this, center);
    }

    private boolean isPathObstructingState(BlockState state, BlockPos pos) {
        return ArchitectBlockEnvironment.isPathObstructingState(level(), state, pos);
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

    private boolean isTargetWithinMeleeEngageGeometry(LivingEntity target) {
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
        if (source.is(DamageTypeTags.IS_FIRE)) amount *= 1.5f;

        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide()) {
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

    // ========================
    //  HEALING POTION
    // ========================

    void startDrinking() {
        combatState.isDrinkingPotion = true;
        combatState.drinkTicks = 0;
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
        if (!getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return;
        }
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        setDropChance(EquipmentSlot.HEAD, 0.0F);
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
        // Notify D* Lite of world changes so it updates costs incrementally
        approachState.dstar.onBlockChanged(changedPos, level());
    }

    // ========================
    //  HELPERS
    // ========================

    private double getDetectionRange() {
        return 96.0;
    }

    @Nullable
    private LivingEntity findTarget() {
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

    // ========================
    //  SYNCHED DATA ACCESSORS
    // ========================

    public int getTextureVariant() { return entityData.get(DATA_TEXTURE_VARIANT); }
    public void setTextureVariant(int variant) { entityData.set(DATA_TEXTURE_VARIANT, variant); }
    public int getDeathTicks() { return entityData.get(DATA_DEATH_TICKS); }
    public int getCurrentAction() { return entityData.get(DATA_ACTION); }
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
    public float getVoicePitch() { return 0.5f + random.nextFloat() * 0.15f; }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.ARCHITECT_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.ARCHITECT_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return ModSounds.ARCHITECT_DEATH.get(); }

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
        if (approachState.surfaceY == 0) approachState.surfaceY = blockPosition().getY(); // migration for existing entities
        syncRenderState();
    }

    // ========================
    //  DESPAWN OVERRIDES
    // ========================

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public void checkDespawn() { }

    @Override
    public boolean shouldDespawnInPeaceful() { return true; }
}
