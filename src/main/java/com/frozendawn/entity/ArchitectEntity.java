package com.frozendawn.entity;

import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectBrainState;
import com.frozendawn.entity.architect.ArchitectCombatState;
import com.frozendawn.entity.architect.ArchitectDecisionEngine;
import com.frozendawn.entity.architect.ArchitectFxController;
import com.frozendawn.entity.architect.ArchitectObservationMemory;
import com.frozendawn.entity.architect.ArchitectPersistence;
import com.frozendawn.entity.architect.ArchitectRenderFlags;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.ArchitectMoveControl;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlocks;
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
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
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
    private static final int MIN_OBSERVE_TICKS = 600;
    private static final int MAX_OBSERVE_TICKS = 1200;
    private static final double SPAWN_OBSERVE_CUE_RANGE_SQR = 72.0 * 72.0;

    private static final int PLAYER_MEMORY_TICKS = 200;
    private boolean towerEncounter = false;
    private long towerEncounterId = Long.MIN_VALUE;

    private static final int HEAL_COOLDOWN_TICKS = 1200;
    private static final int DRINK_DURATION = 32;
    static final double RETREAT_DISTANCE = 16.0;

    // --- Burst Damage Tracking ---
    /** Damage taken in the last BURST_WINDOW ticks. Used to boost retreat scoring. */
    private static final int BURST_WINDOW = 60; // 3 seconds

    // --- Block Breaker ---
    private final ArchitectBlockBreaker blockBreaker = new ArchitectBlockBreaker(this);
    private final ArchitectApproachController approachController =
            new ArchitectApproachController(this, approachState, blockBreaker);
    private final ArchitectCombatController combatController =
            new ArchitectCombatController(this, combatState, blockBreaker);
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
    private static final double WALK_TARGET_SHIFT_HORIZONTAL_SQR = 9.0;
    private static final int WALK_TARGET_SHIFT_VERTICAL = 1;
    private static final float WALK_MAX_ROTATE = 35.0F;
    private static final int MAX_REPEAT_UNSTICK_BREAK_ATTEMPTS = 3;
    private static final int WALK_NAV_CORRIDOR_MAX_STEPS = 8;
    private static final double WALK_NAV_MAX_DISTANCE = 10.0;
    private static final double DIRECT_APPROACH_PATH_HORIZONTAL_RANGE = 8.0;
    private static final double DIRECT_APPROACH_PATH_VERTICAL_RANGE = 4.0;
    static final int UNREACHABLE_BREAK_DELAY_TICKS = 8;
    private static final int FALLBACK_BREAK_COOLDOWN_TICKS = 10;
    static final int MELEE_COMMIT_TICKS = 12;
    private static final float MELEE_COMMIT_KEEP_RANGE = 5.8f;
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
    private static final int ROAM_REPATH_MIN_TICKS = 25;
    private static final int ROAM_REPATH_VARIANCE_TICKS = 30;
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
        return new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(this, level);
    }

    @Override
    public int getMaxFallDistance() {
        return 8;
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

    int nextRandomInt(int bound) {
        return random.nextInt(bound);
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

        long gameTick = level().getGameTime();

        // --- Target acquisition ---
        // Architect senses through blocks — always knows target position
        LivingEntity target = findTarget();

        if (target == null) {
            if (!brainState.isRoamingAfterTargetLoss()) {
                enterRoamModeAfterTargetLoss();
            }
        } else {
            if (brainState.isRoamingAfterTargetLoss() && target instanceof Player player) {
                restartObserveForPlayer(player);
            }
            brainState.setRoamingAfterTargetLoss(false);
            observationMemory.setLastKnownPlayerPos(target.blockPosition());
            observationMemory.setLastSeenTick(tickCount);
        }

        maybeTriggerSpawnObserveCue(target);

        if (combatState.healCooldown > 0) combatState.healCooldown--;
        if (trapCooldown > 0) trapCooldown--;
        if (approachState.fallbackBreakCooldown > 0) approachState.fallbackBreakCooldown--;
        if (brainState.getMeleeCommitTicks() > 0) {
            brainState.setMeleeCommitTicks(brainState.getMeleeCommitTicks() - 1);
        }
        // Decay burst damage tracker outside window
        if (tickCount - combatState.lastDamageTick > BURST_WINDOW) combatState.recentDamage = 0f;

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
        if (target == null) {
            if (towerEncounter) {
                despawnTimer = 0;
            } else {
            boolean playerNearby = !level().getEntitiesOfClass(Player.class,
                    getBoundingBox().inflate(48.0), p -> !p.isSpectator()).isEmpty();
            if (playerNearby) {
                despawnTimer = 0;
            } else {
                despawnTimer++;
                if (despawnTimer >= DESPAWN_TIMEOUT) {
                    cleanupAllIce();
                    discard();
                    return;
                }
            }
            }
        } else {
            despawnTimer = 0;
        }

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

        long actionStart = System.nanoTime();
        executeAction(target);
        long actionUs = (System.nanoTime() - actionStart) / 1000;
        if (actionUs > SLOW_EXEC_ACTION_LOG_US && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] executeAction({}) took {}us", getBrainAction(), actionUs);
        }

        emitActionTelegraphParticles(target);

        // Only sprint when fleeing
        setSprinting(target != null && getBrainAction() == ACTION_RETREAT && combatState.retreatPhase == 0);

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
            executeRoamAndRuin();
            return;
        }

        switch (getBrainAction()) {
            case ACTION_OBSERVE -> executeObserve(target);
            case ACTION_APPROACH -> approachController.executeApproach(target);
            case ACTION_ATTACK_MELEE -> combatController.executeAttackMelee(target);
            case ACTION_RETREAT -> combatController.executeRetreat(target);
            case ACTION_FORTIFY -> executeFortify(target);
            case ACTION_TRAP_SET -> executeTrapSet(target);
            case ACTION_PEEK -> executePeek(target);
        }
    }

    private void executeObserve(@Nullable LivingEntity target) {
        if (target == null) {
            approachState.dstarPrecomputed = false;
            return;
        }

        // Keep D* Lite warm during observation so APPROACH can react immediately.
        approachController.precomputeDStarDuringObserve(target);

        float dist = distanceTo(target);
        // Only recalculate path every 20 ticks
        if (pathRecalcCooldown <= 0) {
            if (dist < 28) {
                Vec3 away = position().subtract(target.position()).normalize().scale(0.8);
                getNavigation().moveTo(getX() + away.x * 10, getY(), getZ() + away.z * 10, 0.8);
            } else if (dist > 42) {
                getNavigation().moveTo(target, 0.8);
            } else {
                getNavigation().stop();
            }
            pathRecalcCooldown = 20;
        }
        pathRecalcCooldown--;
        // Always stare at the target during OBSERVE — the creep factor
        getLookControl().setLookAt(target, 30f, 30f);

        observationMemory.incrementObserveTicks();

        // OBSERVE particles: soul particles drift up from head — "it's thinking"
        if (level() instanceof ServerLevel serverLevel && tickCount % 10 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    getX(), getY() + 1.8, getZ(),
                    1, 0.15, 0.1, 0.15, 0.01);
            if (tickCount % 20 == 0) {
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        getX(), getY() + 1.65, getZ(),
                        2, 0.25, 0.15, 0.25, 0.05);
            }
        }

        // Slow ticking sound during observation — unsettling metronome
        if (tickCount % 60 == 0) {
            playSound(ModSounds.ARCHITECT_OBSERVE.get(), 0.6f, 0.8f + random.nextFloat() * 0.3f);
        }

        // Environment scan every 40 ticks
        if (observationMemory.getObserveTicks() % 40 == 0 && level() instanceof ServerLevel serverLevel) {
            BlockPos playerPos = target.blockPosition();
            scanEntrances(serverLevel, playerPos);
            observationMemory.setLastObservedPos(playerPos);
        }

        // Raycast probe: run at tick 60 (3s in) and tick 300 (15s in, mid-observe)
        if (observationMemory.getObserveTicks() == 60 || observationMemory.getObserveTicks() == 300) {
            awardObserveProbeAdvancement(target);
        }

        if (dist < 20 && target.hasLineOfSight(this) && isPlayerFacing(target)) {
            observationMemory.setHasObserved(true);
            observationMemory.setObserveDirty(false);
            // Particle burst — "decision made"
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.8, getZ(),
                        8, 0.3, 0.2, 0.3, 0.03);
                sl.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.6, getZ(),
                        10, 0.35, 0.25, 0.35, 0.08);
            }
            triggerReeval();
            return;
        }

        int targetDuration = MIN_OBSERVE_TICKS + random.nextInt(MAX_OBSERVE_TICKS - MIN_OBSERVE_TICKS);
        if (observationMemory.getObserveTicks() >= targetDuration) {
            observationMemory.setHasObserved(true);
            observationMemory.setObserveDirty(false);
            // Particle burst — "decision made"
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.8, getZ(),
                        8, 0.3, 0.2, 0.3, 0.03);
                sl.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 1.6, getZ(),
                        10, 0.35, 0.25, 0.35, 0.08);
            }
            triggerReeval();
        }
    }

    /**
     * No active target: prowl nearby between encounters.
     */
    private void executeRoamAndRuin() {
        keepNearbyWoodenDoorsOpen();
        blockBreaker.clearTarget();

        // Wander to nearby random positions.
        if (pathRecalcCooldown <= 0 || !getNavigation().isInProgress()) {
            Vec3 roamPos = DefaultRandomPos.getPos(this, 12, 4);
            if (roamPos != null) {
                getNavigation().moveTo(roamPos.x, roamPos.y, roamPos.z, 0.9);
                getLookControl().setLookAt(roamPos.x, roamPos.y, roamPos.z);
            } else {
                double dx = (random.nextDouble() - 0.5) * 12.0;
                double dz = (random.nextDouble() - 0.5) * 12.0;
                getNavigation().moveTo(getX() + dx, getY(), getZ() + dz, 0.9);
            }
            pathRecalcCooldown = ROAM_REPATH_MIN_TICKS + random.nextInt(ROAM_REPATH_VARIANCE_TICKS);
        }
        pathRecalcCooldown--;
    }

    private void awardObserveProbeAdvancement(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            WorldTickHandler.grantAdvancement(player, "architect_noticed");
        }
    }

    private void maybeTriggerSpawnObserveCue(@Nullable LivingEntity target) {
        if (observationMemory.isPendingSpawnCuePlayed()
                || observationMemory.getPendingSpawnCuePlayerId() == null
                || getBrainAction() != ACTION_OBSERVE) {
            return;
        }
        if (!(target instanceof ServerPlayer player)) {
            return;
        }
        if (!observationMemory.getPendingSpawnCuePlayerId().equals(player.getUUID())) {
            return;
        }
        if (distanceToSqr(player) > SPAWN_OBSERVE_CUE_RANGE_SQR) {
            return;
        }

        observationMemory.setPendingSpawnCuePlayed(true);
        observationMemory.setPendingSpawnCuePlayerId(null);
        level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.ARCHITECT_WATCHED.get(), SoundSource.HOSTILE,
                1.0f, 0.9f + random.nextFloat() * 0.2f);
        player.displayClientMessage(Component.translatable("message.frozendawn.architect_watched"), true);
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

    boolean continueBreaking(LivingEntity target) {
        BlockPos bt = blockBreaker.getTarget();
        if (bt == null) return false;

        double blockDist = position().distanceToSqr(
                bt.getX() + 0.5, bt.getY() + 0.5, bt.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            getNavigation().stop();
            // Look at the block being mined
            getLookControl().setLookAt(bt.getX() + 0.5, bt.getY() + 0.5, bt.getZ() + 0.5);
            boolean broke = blockBreaker.tick();
            if (broke) {
                resetUnstickBreakTracker();
                // Ceiling breach drop-through: teleport mob into the hole so it
                // falls inside the structure, not off the edge.
                if (approachState.ceilingBreachPos != null && bt.equals(approachState.ceilingBreachPos)) {
                    teleportTo(bt.getX() + 0.5, bt.getY(), bt.getZ() + 0.5);
                    getNavigation().stop();
                    approachState.ceilingBreachPos = null;
                    clearCommittedWalk();
                    playSound(ModSounds.ARCHITECT_LAND.get(), 0.8f, 0.7f + random.nextFloat() * 0.3f);
                    LOGGER.info("[Architect] Ceiling breach complete — dropping through " + bt);
                    pathRecalcCooldown = 0;
                    approachState.dstar.onLocalBlockChanged(bt, level());
                    triggerReeval();
                    return true;
                }

                pathRecalcCooldown = 0;
                approachState.dstar.onLocalBlockChanged(bt, level());

                // Chain headroom: clear the block above for 2-high clearance.
                BlockPos above = bt.above();
                if (above.getY() <= blockPosition().getY() + 1
                        && isBreakableBlock(above)
                        && shouldContinueApproachBreak(target, above)) {
                    blockBreaker.setTarget(above);
                    LOGGER.info("[Architect] Chaining headroom break at " + above);
                    return true; // Continue mining before repathing
                }

                triggerReeval();
            }
            return true;
        }

        blockBreaker.clearTarget();
        return false;
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

    @Nullable
    BlockPos findDropInBreakTarget(@Nullable LivingEntity target, BlockPos stepPos) {
        BlockPos below = blockPosition().below();
        if (isBreakableBlock(below)) {
            return below;
        }

        BlockPos stepBelow = stepPos.below();
        if (isBreakableBlock(stepBelow)) {
            return stepBelow;
        }

        BlockPos fallback = findBreakableWallBlock(target);
        if (fallback != null && fallback.getY() == blockPosition().getY() - 1) {
            return fallback;
        }

        return null;
    }


    void fallbackWallBreak(LivingEntity target) {
        if (approachState.fallbackBreakCooldown > 0) return;
        BlockPos wallBlock = findBreakableWallBlock(target);
        if (wallBlock == null) return;

        if (wallBlock.equals(approachState.lastFallbackBreakPos)
                && !level().getBlockState(wallBlock).isAir()
                && !blockBreaker.hasTarget()) {
            approachState.fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
            return;
        }

        double blockDist = position().distanceToSqr(
                wallBlock.getX() + 0.5, wallBlock.getY() + 0.5, wallBlock.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            blockBreaker.setTarget(wallBlock);
            approachState.lastFallbackBreakPos = wallBlock.immutable();
            approachState.fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
        } else {
            getNavigation().moveTo(wallBlock.getX() + 0.5,
                    wallBlock.getY(), wallBlock.getZ() + 0.5, 1.0);
            pathRecalcCooldown = 5;
            approachState.lastFallbackBreakPos = wallBlock.immutable();
            approachState.fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
        }
    }

    /**
     * Find the best breakable block between the mob and the target.
     *
     * Priority order:
     * 1. Block directly below (if target is below us) — critical for dig-down
     * 2. Block at feet level toward target (horizontal dig)
     * 3. Raycast from mob toward target
     */
    @Nullable
    BlockPos findBreakableWallBlock(@Nullable LivingEntity target) {
        if (target == null) return null;

        // --- Priority 1: Dig-down when target is below AND we're close enough
        // to be in a true drop-in context (roof/shaft), not long-range pursuit.
        double dxToTarget = target.getX() - getX();
        double dzToTarget = target.getZ() - getZ();
        double horizontalDistToTarget = Math.sqrt(dxToTarget * dxToTarget + dzToTarget * dzToTarget);
        double verticalDropToTarget = getY() - target.getY();
        if (verticalDropToTarget >= 2.0 && horizontalDistToTarget <= 6.0) {
            BlockPos below = blockPosition().below();
            if (isBreakableBlock(below)) {
                return below;
            }
            // Scan nearby at Y-1 for any breakable block. Handles "on roof"
            // scenarios where mob is on an acheronite rim but breakable roof
            // blocks (planks, etc.) are 1-3 blocks inward.
            BlockPos closest = null;
            double closestDist = Double.MAX_VALUE;
            for (int ox = -3; ox <= 3; ox++) {
                for (int oz = -3; oz <= 3; oz++) {
                    if (ox == 0 && oz == 0) continue; // Already checked
                    BlockPos candidate = below.offset(ox, 0, oz);
                    if (isBreakableBlock(candidate)) {
                        double d = position().distanceToSqr(
                                candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
                        if (d < closestDist) {
                            closestDist = d;
                            closest = candidate;
                        }
                    }
                }
            }
            if (closest != null) return closest;
        }

        // --- Priority 2: Block at feet level in direction of target ---
        {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            BlockPos feet = blockPosition();
            BlockPos toward;
            if (Math.abs(dx) > Math.abs(dz)) {
                toward = feet.offset(dx > 0 ? 1 : -1, 0, 0);
            } else {
                toward = feet.offset(0, 0, dz > 0 ? 1 : -1);
            }
            if (isBreakableBlock(toward)) return toward;
            // Also check head height
            BlockPos towardHead = toward.above();
            if (isBreakableBlock(towardHead)) return towardHead;
        }

        // --- Priority 3: Raycast fallback ---
        Vec3 start = position().add(0, getEyeHeight() * 0.5, 0);
        Vec3 dir = target.position().add(0, target.getEyeHeight() * 0.5, 0).subtract(start).normalize();

        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= 10; i++) {
            Vec3 point = start.add(dir.scale(i));
            probe.set((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
            if (isBreakableBlock(probe)) {
                return probe.immutable();
            }
        }
        return null;
    }

    void trackWalkStep(BlockPos stepPos) {
        BlockPos from = blockPosition();
        boolean repeated = stepPos.equals(approachState.lastWalkStepPos) && from.equals(approachState.lastWalkFromPos);
        // Detect A<->B ping-pong as stuck too, not just exact same edge.
        boolean pingPong = stepPos.equals(approachState.lastWalkFromPos) && from.equals(approachState.lastWalkStepPos);
        double horizontalMotionSqr = getDeltaMovement().x * getDeltaMovement().x
                + getDeltaMovement().z * getDeltaMovement().z;
        boolean actuallyStalled = onGround() && horizontalMotionSqr < 0.0025;
        boolean lowProgress = onGround() && horizontalMotionSqr < 0.04;
        if (repeated && actuallyStalled) {
            approachState.walkStuckTicks++;
        } else if (pingPong && lowProgress) {
            approachState.walkStuckTicks += 2;
        } else if (pingPong && onGround()) {
            approachState.walkStuckTicks++;
        } else {
            approachState.walkStuckTicks = 0;
        }
        approachState.lastWalkStepPos = stepPos.immutable();
        approachState.lastWalkFromPos = from.immutable();
    }

    void resetWalkStuckTracker() {
        approachState.walkStuckTicks = 0;
        approachState.lastWalkStepPos = null;
        approachState.lastWalkFromPos = null;
    }

    void recordWalkCellHistory() {
        BlockPos current = blockPosition();
        if (approachState.currentWalkCellPos == null) {
            approachState.currentWalkCellPos = current.immutable();
            return;
        }
        if (!current.equals(approachState.currentWalkCellPos)) {
            approachState.previousWalkCellPos = approachState.currentWalkCellPos;
            approachState.currentWalkCellPos = current.immutable();
        }
    }

    private void resetWalkCellHistory() {
        approachState.currentWalkCellPos = null;
        approachState.previousWalkCellPos = null;
        approachState.lastCompletedWalkWaypointPos = null;
        approachState.lastCompletedWalkBacktrackPos = null;
    }

    void resetUnstickBreakTracker() {
        approachState.lastUnstickBreakCandidate = null;
        approachState.repeatedUnstickBreakAttempts = 0;
    }

    private void commitWalkStep(List<BlockPos> corridorNodes, @Nullable LivingEntity target) {
        if (corridorNodes.isEmpty()) {
            return;
        }

        approachState.committedWalkCorridor.clear();
        for (BlockPos node : corridorNodes) {
            approachState.committedWalkCorridor.add(node.immutable());
        }
        approachState.committedWalkCorridorIndex = 0;
        approachState.committedWalkFirstStepPos = approachState.committedWalkCorridor.get(0);
        approachState.committedWalkWaypoint = approachState.committedWalkCorridor.get(approachState.committedWalkCorridor.size() - 1);
        approachState.committedWalkStartPos = blockPosition().immutable();
        approachState.committedWalkBacktrackPos = approachState.pendingWalkBacktrackPos != null
                ? approachState.pendingWalkBacktrackPos.immutable()
                : approachState.committedWalkStartPos;
        approachState.committedWalkStartVec = position();
        approachState.committedWalkTargetSnapshot = target != null ? target.blockPosition().immutable() : null;
        approachState.committedWalkTicks = WALK_COMMIT_TICKS;
        approachState.committedWalkAgeTicks = 0;
        approachState.committedWalkNoProgressTicks = 0;
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        approachState.committedWalkLastDistSqr = steeringTarget != null
                ? distanceToWaypointSqr(steeringTarget)
                : Double.MAX_VALUE;
        approachState.pendingWalkBacktrackPos = null;
        resetWalkStuckTracker();
    }

    void clearCommittedWalk() {
        approachState.committedWalkWaypoint = null;
        approachState.committedWalkFirstStepPos = null;
        approachState.committedWalkStartPos = null;
        approachState.committedWalkBacktrackPos = null;
        approachState.committedWalkTargetSnapshot = null;
        approachState.committedWalkStartVec = null;
        approachState.committedWalkCorridor.clear();
        approachState.committedWalkCorridorIndex = 0;
        approachState.committedWalkTicks = 0;
        approachState.committedWalkAgeTicks = 0;
        approachState.committedWalkNoProgressTicks = 0;
        approachState.committedWalkLastDistSqr = Double.MAX_VALUE;
        approachState.pendingWalkBacktrackPos = null;
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
        if (approachState.committedWalkCorridor.isEmpty()) {
            return approachState.committedWalkWaypoint;
        }
        if (approachState.committedWalkCorridorIndex < 0) {
            approachState.committedWalkCorridorIndex = 0;
        }
        if (approachState.committedWalkCorridorIndex >= approachState.committedWalkCorridor.size()) {
            approachState.committedWalkCorridorIndex = approachState.committedWalkCorridor.size() - 1;
        }
        return approachState.committedWalkCorridor.get(approachState.committedWalkCorridorIndex);
    }

    private boolean advanceCommittedWalkProgress() {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        while (steeringTarget != null) {
            double distSqr = distanceToWaypointSqr(steeringTarget);
            if (!hasReachedWalkWaypoint(steeringTarget, distSqr)) {
                return true;
            }

            if (approachState.committedWalkCorridorIndex < approachState.committedWalkCorridor.size() - 1) {
                approachState.committedWalkCorridorIndex++;
                approachState.committedWalkNoProgressTicks = 0;
                approachState.committedWalkLastDistSqr = Double.MAX_VALUE;
                steeringTarget = getCommittedWalkSteeringTarget();
                continue;
            }

            if (approachState.committedWalkWaypoint != null) {
                approachState.lastCompletedWalkWaypointPos = approachState.committedWalkWaypoint.immutable();
            }
            approachState.lastCompletedWalkBacktrackPos = approachState.committedWalkBacktrackPos != null
                    ? approachState.committedWalkBacktrackPos.immutable()
                    : approachState.committedWalkStartPos != null ? approachState.committedWalkStartPos.immutable() : null;
            clearCommittedWalk();
            return false;
        }
        return false;
    }

    private boolean continueCommittedWalk() {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null || approachState.committedWalkTicks <= 0) return false;

        double tx = steeringTarget.getX() + 0.5;
        double ty = steeringTarget.getY();
        double tz = steeringTarget.getZ() + 0.5;
        double horizontalDistSqr = (tx - getX()) * (tx - getX()) + (tz - getZ()) * (tz - getZ());
        if (steeringTarget.getY() > getY() + 0.1 && horizontalDistSqr <= 1.25 && onGround()) {
            getJumpControl().jump();
        }

        approachState.committedWalkTicks--;
        approachState.committedWalkAgeTicks++;
        // Follow D* corridors with raw MoveControl so edge/scaffold approach cells
        // do not get vetoed by vanilla navigation before the scaffold action can fire.
        getNavigation().stop();
        getMoveControl().setWantedPosition(tx, ty, tz, 1.0);
        getLookControl().setLookAt(tx, ty + 1.0, tz, 30f, 30f);
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
        if ((feetState.isSolid() && !feetState.is(BlockTags.WOODEN_DOORS))
                || (headState.isSolid() && !headState.is(BlockTags.WOODEN_DOORS))) {
            invalidateCommittedWalk("BLOCKED", target);
            return false;
        }

        if (target != null && approachState.committedWalkTargetSnapshot != null) {
            BlockPos targetPos = target.blockPosition();
            if (horizontalDistanceSqr(targetPos, approachState.committedWalkTargetSnapshot) > WALK_TARGET_SHIFT_HORIZONTAL_SQR
                    || Math.abs(targetPos.getY() - approachState.committedWalkTargetSnapshot.getY()) > WALK_TARGET_SHIFT_VERTICAL) {
                invalidateCommittedWalk("TARGET_SHIFT", target);
                return false;
            }
        }

        return true;
    }

    @Nullable
    BlockPos getImmediateBacktrackPos() {
        BlockPos current = blockPosition();
        if (approachState.lastCompletedWalkWaypointPos != null
                && approachState.lastCompletedWalkBacktrackPos != null
                && current.equals(approachState.lastCompletedWalkWaypointPos)) {
            return approachState.lastCompletedWalkBacktrackPos;
        }
        if (approachState.currentWalkCellPos == null || approachState.previousWalkCellPos == null) return null;
        if (!current.equals(approachState.currentWalkCellPos)) return null;
        return approachState.previousWalkCellPos;
    }

    boolean tryContinueCommittedWalk(@Nullable LivingEntity target) {
        if (!shouldContinueCommittedWalk(target)) {
            return false;
        }

        if (!advanceCommittedWalkProgress()) {
            return false;
        }

        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null) {
            return false;
        }

        double distSqr = distanceToWaypointSqr(steeringTarget);
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
            if (stuckTarget != null && handleWalkStuck(stuckTarget, target)) {
                return true;
            }
            return false;
        }

        if (!continueCommittedWalk()) {
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

        BlockPos candidate = findWalkUnstickBreakCandidate(stepPos, blockedCandidate);
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
            BlockPos corridorBreakTarget = findWalkCorridorBreakTarget(
                    approachState.committedWalkCorridor.subList(fromIndex, approachState.committedWalkCorridor.size()));
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
        BlockPos corridorBreakTarget = findWalkCorridorBreakTarget(corridorNodes);
        if (corridorBreakTarget != null) {
            startWalkCorridorBreak(corridorBreakTarget);
            return;
        }
        BlockPos waypoint = corridorNodes.get(corridorNodes.size() - 1);
        commitWalkStep(corridorNodes, target);
        if (!continueCommittedWalk()) {
            clearCommittedWalk();
            return;
        }

    }

    boolean canDirectChaseApproach(@Nullable LivingEntity target) {
        if (target == null || blockBreaker.hasTarget() || !hasLineOfSight(target)) {
            return false;
        }
        if (horizontalDistanceTo(target) > DIRECT_APPROACH_PATH_HORIZONTAL_RANGE
                || verticalDistanceTo(target) > DIRECT_APPROACH_PATH_VERTICAL_RANGE) {
            return false;
        }
        return hasCleanReachableApproachPath(target);
    }

    void executeDirectApproachChase(LivingEntity target) {
        clearCommittedWalk();
        resetWalkStuckTracker();
        approachState.unreachableTicks = 0;
        getNavigation().moveTo(target, 1.0);
        getLookControl().setLookAt(target, 30f, 30f);
    }

    private boolean hasCleanReachableApproachPath(LivingEntity target) {
        Path path = getNavigation().createPath(target, 1);
        if (path == null || !path.canReach()) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (path.getNode(i).type == PathType.BLOCKED) {
                return false;
            }
        }
        return true;
    }

    private boolean canStartMelee(LivingEntity target) {
        if (!hasLineOfSight(target) || !isTargetWithinMeleeEngageGeometry(target)) {
            return false;
        }
        if (horizontalDistanceTo(target) <= 2.25 && verticalDistanceTo(target) <= 1.25) {
            return true;
        }
        return hasCleanReachableApproachPath(target);
    }

    private boolean canCommitToMelee(LivingEntity target) {
        if (!isTargetWithinMeleeCommitGeometry(target)) {
            return false;
        }
        if (!hasLineOfSight(target) && distanceTo(target) >= MELEE_COMMIT_LOS_GRACE_RANGE) {
            return false;
        }
        if (horizontalDistanceTo(target) <= 2.25 && verticalDistanceTo(target) <= 1.25) {
            return true;
        }
        return hasCleanReachableApproachPath(target);
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
        BlockPos immediateCandidate = findWalkUnstickBreakCandidate(step.pos(), null);
        if (immediateCandidate != null && breakTarget.equals(immediateCandidate)) {
            return true;
        }

        List<BlockPos> corridorNodes = previewWalkCorridorNodes(blockPosition(), step);
        if (corridorNodes.isEmpty()) {
            corridorNodes = List.of(step.pos().immutable());
        }
        BlockPos corridorCandidate = findWalkCorridorBreakTarget(corridorNodes);
        return corridorCandidate != null && breakTarget.equals(corridorCandidate);
    }

    @Nullable
    private BlockPos findWalkUnstickBreakCandidate(BlockPos stepPos, @Nullable BlockPos blockedCandidate) {
        BlockPos from = blockPosition();
        Direction toward = getPrimaryHorizontalDirection(from, stepPos);
        boolean steppingDown = stepPos.getY() < from.getY();

        Set<BlockPos> candidates = new LinkedHashSet<>(8);
        candidates.add(from.above());
        if (toward != null) {
            BlockPos front = from.relative(toward);
            candidates.add(front);
            candidates.add(front.above());
            if (steppingDown) {
                candidates.add(front.above().above());
            }
        }
        if (Math.abs(stepPos.getX() - from.getX()) <= 1
                && Math.abs(stepPos.getY() - from.getY()) <= 1
                && Math.abs(stepPos.getZ() - from.getZ()) <= 1) {
            candidates.add(stepPos);
            candidates.add(stepPos.above());
        }

        for (BlockPos candidate : candidates) {
            if (blockedCandidate != null && blockedCandidate.equals(candidate)) {
                continue;
            }
            if (isBreakableBlock(candidate)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    @Nullable
    private BlockPos findWalkCorridorBreakTarget(List<BlockPos> corridorNodes) {
        for (BlockPos node : corridorNodes) {
            if (isBreakableBlock(node)) {
                return node.immutable();
            }

            BlockPos headroom = node.above();
            if (isBreakableBlock(headroom)) {
                return headroom.immutable();
            }
        }
        return null;
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

    private double distanceToWaypointSqr(BlockPos waypoint) {
        return position().distanceToSqr(
                waypoint.getX() + 0.5,
                waypoint.getY(),
                waypoint.getZ() + 0.5);
    }

    private double horizontalDistanceSqr(BlockPos from, BlockPos to) {
        double dx = from.getX() - to.getX();
        double dz = from.getZ() - to.getZ();
        return dx * dx + dz * dz;
    }

    private boolean hasReachedWalkWaypoint(BlockPos waypoint, double distSqr) {
        return blockPosition().equals(waypoint);
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
        keepDoorOpenNear(blockPosition());
        keepDoorOpenNear(blockPosition().above());
    }

    void keepDoorOpenNear(BlockPos center) {
        openWoodenDoorAt(center);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            openWoodenDoorAt(center.relative(dir));
        }
    }

    private void openWoodenDoorAt(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock) || !state.is(BlockTags.WOODEN_DOORS)) return;

        // Normalize to lower half so both halves stay in sync.
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below();
            state = level().getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.is(BlockTags.WOODEN_DOORS)) return;
        }

        if (!state.getValue(DoorBlock.OPEN)) {
            ((DoorBlock) state.getBlock()).setOpen(this, level(), state, pos, true);
        }
    }

    boolean isBreakableBlock(BlockPos pos) {
        if (scaffoldIce.contains(pos)) return false; // Don't break our own ice
        BlockState state = level().getBlockState(pos);
        if (state.is(BlockTags.WOODEN_DOORS)) return false; // Prefer opening wooden doors over mining them
        if (!state.isSolid()) return false;
        float hardness = state.getDestroySpeed(level(), pos);
        return hardness >= 0 && hardness < 25.0f
                && !state.is(ModBlocks.ACHERONITE_BLOCK.get())
                && !state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                && !state.is(ModBlocks.TRANSPONDER.get())
                && !wouldExposeHazard(pos);
    }

    private boolean wouldExposeHazard(BlockPos breakPos) {
        for (Direction dir : Direction.values()) {
            BlockState adjacent = level().getBlockState(breakPos.relative(dir));
            if (isHazardousState(adjacent)) return true;
        }
        return false;
    }

    private boolean isHazardousState(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE);
    }

    void applyCombatHorizontalMotion(double x, double z) {
        double scale = onGround() ? 1.0 : MELEE_AIR_CONTROL_SCALE;
        Vec3 desired = new Vec3(x * scale, 0, z * scale);
        double desiredLen = desired.horizontalDistance();
        if (desiredLen > MELEE_MAX_HORIZONTAL_SPEED) {
            desired = desired.scale(MELEE_MAX_HORIZONTAL_SPEED / desiredLen);
        }

        Vec3 current = getDeltaMovement();
        Vec3 currentHorizontal = new Vec3(current.x, 0, current.z).scale(0.35);
        Vec3 blended = currentHorizontal.add(desired.scale(0.65));
        double blendedLen = blended.horizontalDistance();
        if (blendedLen > MELEE_MAX_HORIZONTAL_SPEED) {
            blended = blended.scale(MELEE_MAX_HORIZONTAL_SPEED / blendedLen);
        }

        setDeltaMovement(blended.x, current.y, blended.z);
    }

    private void executeFortify(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();
        if (target == null) { triggerReeval(); return; }

        Vec3 toPlayer = target.position().subtract(position()).normalize();
        BlockPos wallPos = blockPosition().offset(
                (int) Math.round(toPlayer.x * 2), 0,
                (int) Math.round(toPlayer.z * 2));
        if (placeTacticalIce(wallPos)) {
            placeTacticalIce(wallPos.above());
        }
        getLookControl().setLookAt(target, 30f, 30f);
        if (tickCount % 60 == 0) triggerReeval();
    }

    private void executeTrapSet(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();
        if (target == null || observationMemory.entrancePositions().isEmpty()) { triggerReeval(); return; }

        BlockPos bestEntrance = null;
        double bestDist = 0;
        for (BlockPos entrance : observationMemory.entrancePositions()) {
            double d = distanceToSqr(entrance.getX(), entrance.getY(), entrance.getZ());
            if (d > bestDist) { bestDist = d; bestEntrance = entrance; }
        }

        if (bestEntrance != null) {
            if (distanceToSqr(bestEntrance.getX(), bestEntrance.getY(), bestEntrance.getZ()) > 4) {
                getNavigation().moveTo(bestEntrance.getX() + 0.5,
                        bestEntrance.getY(), bestEntrance.getZ() + 0.5, 1.0);
            } else {
                placeTacticalIce(bestEntrance);
                placeTacticalIce(bestEntrance.above());
                trapCooldown = 400;
                triggerReeval();
            }
        }
    }

    private void executePeek(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();
        getNavigation().stop();
        if (target != null) getLookControl().setLookAt(target, 30f, 30f);
        peekTicks++;
        if (peekTicks >= 30) { peekTicks = 0; triggerReeval(); }
    }

    // ========================
    //  ACTION TRANSITIONS
    // ========================

    private void onActionChange(int oldAction, int newAction) {
        if (oldAction == ACTION_OBSERVE) observationMemory.setObserveTicks(0);
        if (oldAction == ACTION_PEEK) peekTicks = 0;
        if (oldAction == ACTION_APPROACH) approachState.unreachableTicks = 0;
        clearWalkNavigationState(true);
        clearCommittedWalk();
        resetWalkStuckTracker();
        resetWalkCellHistory();
        resetUnstickBreakTracker();
        if (oldAction != ACTION_APPROACH) blockBreaker.clearTarget();
        if (oldAction == ACTION_APPROACH) {
            approachState.ceilingBreachPos = null;
            approachState.stepOffTarget = null;
            approachState.stepOffStart = null;
        }
        if (oldAction == ACTION_RETREAT && combatState.isDrinkingPotion) cancelDrinking();
        if (newAction == ACTION_RETREAT) { combatState.retreatPhase = 0; combatState.retreatCoverBuilt = 0; }
        if (newAction == ACTION_ATTACK_MELEE) {
            primeMeleeHandoff();
        }
        pathRecalcCooldown = 0; // Force path recalc on action change
    }

    void primeMeleeHandoff() {
        brainState.setMeleeCommitTicks(Math.max(brainState.getMeleeCommitTicks(), MELEE_COMMIT_TICKS));
        clearWalkNavigationState(true);
        clearCommittedWalk();
        blockBreaker.clearTarget();
        approachState.ceilingBreachPos = null;
    }

    double horizontalDistanceTo(LivingEntity target) {
        double dx = getX() - target.getX();
        double dz = getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    double verticalDistanceTo(LivingEntity target) {
        return Math.abs(getY() - target.getY());
    }

    private boolean isTargetWithinMeleeEngageGeometry(LivingEntity target) {
        return horizontalDistanceTo(target) <= MELEE_ENGAGE_HORIZONTAL_RANGE
                && verticalDistanceTo(target) <= MELEE_ENGAGE_VERTICAL_RANGE;
    }

    private boolean isTargetWithinMeleeCommitGeometry(LivingEntity target) {
        return horizontalDistanceTo(target) <= MELEE_COMMIT_HORIZONTAL_RANGE
                && verticalDistanceTo(target) <= MELEE_COMMIT_VERTICAL_RANGE;
    }

    boolean shouldPreferMeleeOverApproach(LivingEntity target) {
        return canStartMelee(target);
    }

    private void enterRoamModeAfterTargetLoss() {
        brainState.setRoamingAfterTargetLoss(true);
        resetObserveCycle();
        combatState.retreatPhase = 0;
        combatState.retreatCoverBuilt = 0;
        clearWalkNavigationState(true);
        blockBreaker.clearTarget();
        pathRecalcCooldown = 0;
        LOGGER.info("[Architect] Lost target — entering roam/ruin mode");
    }

    private void restartObserveForPlayer(Player player) {
        resetObserveCycle();
        transitionToAction(ACTION_OBSERVE);
        brainState.setActionHoldTicks(0);
        brainState.setReevalCooldown(0);
        LOGGER.info("[Architect] Player reacquired at "
                + String.format("%.1f", distanceTo(player))
                + " blocks — restarting OBSERVE");
    }

    private void resetObserveCycle() {
        observationMemory.setHasObserved(false);
        observationMemory.setObserveDirty(false);
        observationMemory.setObserveTicks(0);
        observationMemory.setLastObservedPos(null);
        approachState.dstarPrecomputed = false;
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

    // ========================
    //  ICE PLACEMENT
    // ========================

    /**
     * Place scaffold ice. Evicts oldest BEHIND the entity, never beneath.
     */
    boolean placeScaffoldIce(BlockPos pos) {
        if (!canPlaceIce(pos)) return false;

        while (scaffoldIce.size() >= MAX_SCAFFOLD_ICE) {
            BlockPos oldest = scaffoldIce.get(0);
            // Never evict the block we're standing on
            if (oldest.equals(blockPosition().below()) || oldest.equals(blockPosition())) {
                if (scaffoldIce.size() > 1) {
                    BlockPos secondOldest = scaffoldIce.get(1);
                    level().removeBlock(secondOldest, false);
                    scaffoldIce.remove(1);
                } else {
                    return false;
                }
            } else {
                level().removeBlock(oldest, false);
                scaffoldIce.remove(0);
            }
        }

        level().setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 3);
        scaffoldIce.add(pos);
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
        return true;
    }

    boolean placeTacticalIce(BlockPos pos) {
        if (!canPlaceIce(pos)) return false;

        while (tacticalIce.size() >= MAX_TACTICAL_ICE) {
            BlockPos oldest = tacticalIce.remove(0);
            level().removeBlock(oldest, false);
        }

        level().setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), 3);
        tacticalIce.add(pos);
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
        return true;
    }

    private boolean canPlaceIce(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) return true;
        // Destroy small plants/flowers (instant-break blocks) to make room for ice
        if (state.getDestroySpeed(level(), pos) == 0) {
            level().destroyBlock(pos, true);
            return true;
        }
        return false;
    }

    private void cleanupAllIce() {
        for (BlockPos pos : scaffoldIce) {
            if (level().getBlockState(pos).is(Blocks.PACKED_ICE)) {
                level().removeBlock(pos, false);
            }
        }
        scaffoldIce.clear();
        for (BlockPos pos : tacticalIce) {
            if (level().getBlockState(pos).is(Blocks.PACKED_ICE)) {
                level().removeBlock(pos, false);
            }
        }
        tacticalIce.clear();
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

    private void scanEntrances(ServerLevel level, BlockPos center) {
        observationMemory.entrancePositions().clear();
        int radius = 16;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (Math.abs(dx) < radius - 2 && Math.abs(dz) < radius - 2) continue;
                BlockPos pos = center.offset(dx, 0, dz);
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos check = pos.offset(0, dy, 0);
                    if (level.getBlockState(check).isAir()
                            && level.getBlockState(check.above()).isAir()
                            && level.getBlockState(check.below()).isSolid()) {
                        boolean nearWall = false;
                        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            if (level.getBlockState(check.relative(dir)).isSolid()) {
                                nearWall = true;
                                break;
                            }
                        }
                        if (nearWall) observationMemory.entrancePositions().add(check.immutable());
                        break;
                    }
                }
            }
        }
    }

    /**
     * Called when player places blocks near last observed position.
     * Threshold: 5+ changes within 16 blocks.
     */
    public void onNearbyBlockChange(BlockPos changedPos, int changeCount) {
        BlockPos lastObservedPos = observationMemory.getLastObservedPos();
        if (lastObservedPos != null && changeCount >= 5
                && changedPos.closerToCenterThan(lastObservedPos.getCenter(), 16.0)) {
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
        double range = getDetectionRange();
        double playerRange = brainState.isRoamingAfterTargetLoss() ? OBSERVE_REACQUIRE_RANGE : range;
        // Find nearest survival/adventure player (exclude creative & spectator)
        Player nearestPlayer = level().getNearestPlayer(this, playerRange);
        if (nearestPlayer != null && !nearestPlayer.isCreative() && !nearestPlayer.isSpectator()) {
            return nearestPlayer;
        }
        // Fallback: target nearest villager (useful for testing & gameplay)
        List<net.minecraft.world.entity.npc.Villager> villagers = level().getEntitiesOfClass(
                net.minecraft.world.entity.npc.Villager.class,
                getBoundingBox().inflate(range), v -> v.isAlive());
        if (!villagers.isEmpty()) {
            villagers.sort(Comparator.comparingDouble(this::distanceToSqr));
            return villagers.get(0);
        }
        return null;
    }

    private boolean isPlayerFacing(LivingEntity entity) {
        Vec3 lookVec = entity.getLookAngle().normalize();
        Vec3 toMob = position().subtract(entity.position()).normalize();
        return lookVec.dot(toMob) > 0.5;
    }

    private boolean isPlayerInsideBase(LivingEntity player) {
        BlockPos pos = player.blockPosition();
        int solidSides = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (level().getBlockState(pos.relative(dir)).isSolid()) solidSides++;
        }
        return solidSides >= 3;
    }

    private boolean isNearCorner() {
        BlockPos pos = blockPosition();
        boolean n = level().getBlockState(pos.north()).isSolid();
        boolean s = level().getBlockState(pos.south()).isSolid();
        boolean e = level().getBlockState(pos.east()).isSolid();
        boolean w = level().getBlockState(pos.west()).isSolid();
        return (n && e) || (n && w) || (s && e) || (s && w);
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
        }
        if (ticks >= 30) {
            cleanupAllIce();
            blockBreaker.onDeath();
            if (towerEncounter && level() instanceof ServerLevel serverLevel) {
                TowerEncounterController.markResolved(serverLevel, towerEncounterId);
            }
            remove(RemovalReason.KILLED);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        getX(), getY() + 1.4, getZ(), 28, 0.18, 0.65, 0.18, 0.02);
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        getX(), getY() + 0.9, getZ(), 40, 0.25, 0.95, 0.25, 0.02);
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
        tag.putInt("TextureVariant", getTextureVariant());
        tag.putInt("DespawnTimer", despawnTimer);
        tag.putInt("CurrentAction", getBrainAction());
        tag.putBoolean("HasObserved", observationMemory.hasObserved());
        tag.putBoolean("ObserveDirty", observationMemory.isObserveDirty());
        tag.putInt("RangedHitsReceived", combatState.rangedHitsReceived);
        tag.putInt("HealCooldown", combatState.healCooldown);
        tag.putInt("SurfaceY", approachState.surfaceY);
        tag.putBoolean("TowerEncounter", towerEncounter);
        tag.putLong("TowerEncounterId", towerEncounterId);

        ArchitectPersistence.putBlockPosList(tag, "ScaffoldIce", scaffoldIce);
        ArchitectPersistence.putBlockPosList(tag, "TacticalIce", tacticalIce);
        ArchitectPersistence.putOptionalBlockPos(tag, "LastKnownPlayerPos", observationMemory.getLastKnownPlayerPos());
        ArchitectPersistence.putOptionalBlockPos(tag, "LastObservedPos", observationMemory.getLastObservedPos());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTextureVariant(tag.getInt("TextureVariant"));
        despawnTimer = tag.getInt("DespawnTimer");
        setBrainAction(tag.getInt("CurrentAction"));
        // Delay first pathfinding after world load to prevent freeze
        pathRecalcCooldown = 40;
        brainState.setReevalCooldown(40);
        brainState.setActionHoldTicks(0);
        observationMemory.setHasObserved(tag.getBoolean("HasObserved"));
        observationMemory.setObserveDirty(tag.getBoolean("ObserveDirty"));
        observationMemory.setObserveTicks(0);
        combatState.rangedHitsReceived = tag.getInt("RangedHitsReceived");
        combatState.healCooldown = tag.getInt("HealCooldown");
        approachState.surfaceY = tag.getInt("SurfaceY");
        towerEncounter = tag.getBoolean("TowerEncounter");
        towerEncounterId = tag.contains("TowerEncounterId") ? tag.getLong("TowerEncounterId") : Long.MIN_VALUE;
        if (approachState.surfaceY == 0) approachState.surfaceY = blockPosition().getY(); // migration for existing entities

        ArchitectPersistence.readBlockPosList(tag, "ScaffoldIce", scaffoldIce);
        ArchitectPersistence.readBlockPosList(tag, "TacticalIce", tacticalIce);
        observationMemory.setLastKnownPlayerPos(ArchitectPersistence.getOptionalBlockPos(tag, "LastKnownPlayerPos"));
        observationMemory.setLastObservedPos(ArchitectPersistence.getOptionalBlockPos(tag, "LastObservedPos"));
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
