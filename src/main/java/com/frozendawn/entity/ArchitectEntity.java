package com.frozendawn.entity;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.frozendawn.entity.ai.ArchitectBlockBreaker;
import com.frozendawn.entity.ai.ArchitectMoveControl;
import com.frozendawn.entity.ai.DStarLitePathfinder;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
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

    // --- Utility AI State ---
    private int reevalCooldown = 0;
    private int currentAction = ACTION_OBSERVE;

    // --- Observation Data ---
    private boolean hasObserved = false;
    private boolean observeDirty = false;
    private int observeTicks = 0;
    private static final int MIN_OBSERVE_TICKS = 600;
    private static final int MAX_OBSERVE_TICKS = 1200;
    private static final double SPAWN_OBSERVE_CUE_RANGE_SQR = 72.0 * 72.0;
    @Nullable private BlockPos weakestWallDirection;
    private final List<BlockPos> entrancePositions = new ArrayList<>();
    private final List<BlockPos> lightSources = new ArrayList<>();
    private boolean playerUnderground = false;
    @Nullable private BlockPos lastObservedPos;
    @Nullable private BlockPos preferredEntryPoint;
    private boolean probing = false;
    @Nullable private UUID pendingSpawnCuePlayerId = null;
    private boolean pendingSpawnCuePlayed = false;

    // --- Combat State ---
    private int strafeDir = 1;
    private int strafeChangeCooldown = 0;
    private int backoffTicks = 0;
    @Nullable private BlockPos lastKnownPlayerPos;
    private int lastSeenTick = 0;
    private static final int PLAYER_MEMORY_TICKS = 200;

    // --- Retreat / Healing ---
    private int healCooldown = 0;
    private boolean isDrinkingPotion = false;
    private int drinkTicks = 0;
    private static final int HEAL_COOLDOWN_TICKS = 1200;
    private static final int DRINK_DURATION = 32;
    private static final double RETREAT_DISTANCE = 16.0;
    private int retreatPhase = 0; // 0=running, 1=building cover, 2=healing
    private int retreatCoverBuilt = 0;

    // --- Burst Damage Tracking ---
    /** Damage taken in the last BURST_WINDOW ticks. Used to boost retreat scoring. */
    private float recentDamage = 0f;
    private int lastDamageTick = 0;
    private static final int BURST_WINDOW = 60; // 3 seconds

    // --- Adaptive Learning (persisted in NBT) ---
    private int rangedHitsReceived = 0;
    private int wallBreakAttempts = 0;
    private boolean acheroniteEncountered = false;

    // --- Block Breaker ---
    private final ArchitectBlockBreaker blockBreaker = new ArchitectBlockBreaker(this);

    // --- Despawn ---
    private int despawnTimer = 0;
    private static final int DESPAWN_TIMEOUT = 6000;

    // --- Misc ---
    private int peekTicks = 0;
    private int trapCooldown = 0;
    private int pathRecalcCooldown = 0;
    /** Consecutive APPROACH ticks where D* reports UNREACHABLE. */
    private int unreachableTicks = 0;
    /** Consecutive APPROACH ticks where movement toward the committed WALK corridor stalls. */
    private int walkStuckTicks = 0;
    @Nullable private BlockPos lastWalkStepPos = null;
    @Nullable private BlockPos lastWalkFromPos = null;
    @Nullable private BlockPos currentWalkCellPos = null;
    @Nullable private BlockPos previousWalkCellPos = null;
    @Nullable private BlockPos committedWalkWaypoint = null;
    @Nullable private BlockPos committedWalkFirstStepPos = null;
    @Nullable private BlockPos committedWalkStartPos = null;
    @Nullable private BlockPos committedWalkBacktrackPos = null;
    @Nullable private BlockPos committedWalkTargetSnapshot = null;
    @Nullable private Vec3 committedWalkStartVec = null;
    private final List<BlockPos> committedWalkCorridor = new ArrayList<>();
    private int committedWalkCorridorIndex = 0;
    @Nullable private BlockPos pendingWalkBacktrackPos = null;
    @Nullable private BlockPos lastCompletedWalkWaypointPos = null;
    @Nullable private BlockPos lastCompletedWalkBacktrackPos = null;
    @Nullable private BlockPos lastUnstickBreakCandidate = null;
    private int repeatedUnstickBreakAttempts = 0;
    private int committedWalkTicks = 0;
    private int committedWalkAgeTicks = 0;
    private int committedWalkNoProgressTicks = 0;
    private double committedWalkLastDistSqr = Double.MAX_VALUE;
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
    private static final int UNREACHABLE_BREAK_DELAY_TICKS = 8;
    private static final int FALLBACK_BREAK_COOLDOWN_TICKS = 10;
    private int fallbackBreakCooldown = 0;
    @Nullable private BlockPos lastFallbackBreakPos = null;
    private static final int MELEE_COMMIT_TICKS = 12;
    private static final float MELEE_COMMIT_KEEP_RANGE = 5.8f;
    private static final float MELEE_COMMIT_LOS_GRACE_RANGE = 1.5f;
    private static final double MELEE_ENGAGE_HORIZONTAL_RANGE = 4.75;
    private static final double MELEE_COMMIT_HORIZONTAL_RANGE = 5.25;
    private static final double MELEE_ENGAGE_VERTICAL_RANGE = 1.75;
    private static final double MELEE_COMMIT_VERTICAL_RANGE = 2.25;
    private static final double MELEE_STRAFE_SPEED = 0.10;
    private static final double MELEE_PULL_SPEED_NEAR = -0.04;
    private static final double MELEE_PULL_SPEED_FAR = 0.025;
    private static final double MELEE_BACKOFF_SPEED = 0.11;
    private static final double MELEE_DODGE_SPEED = 0.10;
    private static final double MELEE_AIR_CONTROL_SCALE = 0.35;
    private static final double MELEE_MAX_HORIZONTAL_SPEED = 0.12;
    private int meleeCommitTicks = 0;
    /** Ticks since last action change. Prevents rapid flip-flopping. */
    private int actionHoldTicks = 0;
    private static final int MIN_ACTION_HOLD = 5;
    /** While true, no current target; Architect should roam/ruin and only reacquire players at observe radius. */
    private boolean roamingAfterTargetLoss = false;
    private static final double OBSERVE_REACQUIRE_RANGE = 72.0;
    private static final int ROAM_REPATH_MIN_TICKS = 25;
    private static final int ROAM_REPATH_VARIANCE_TICKS = 30;
    private static final long SLOW_SUPER_AISTEP_LOG_US = 50_000;
    private static final long SLOW_EXEC_ACTION_LOG_US = 50_000;


    /** Fixed surface Y from spawn. Used for dig-down depth penalty.
     *  Set once in finalizeSpawn(), persisted in NBT. Never reset. */
    private int surfaceY = 64;

    /** Position of block being mined during a ceiling breach. Used to teleport
     *  the mob into the hole after the block breaks so it falls inside. */
    @Nullable private BlockPos ceilingBreachPos = null;

    // --- Smooth step-off (lerp instead of teleport) ---
    @Nullable private Vec3 stepOffStart = null;
    @Nullable private BlockPos stepOffTarget = null;
    private int stepOffProgress = 0;
    private static final int STEP_OFF_DURATION = 4;
    private boolean stepOffInterior = false;

    // --- Scaffold pacing (player-like delay between place + jump) ---
    private int scaffoldDelay = 0;
    @Nullable private BlockPos scaffoldTarget = null;
    private static final int SCAFFOLD_PLACE_TICKS = 12; // ~0.6s pause after placing before stepping up

    // --- D* Lite Pathfinder (replaces A* for APPROACH action) ---
    private final DStarLitePathfinder dstar = new DStarLitePathfinder();
    /** True once OBSERVE has warmed D* Lite to a complete search at least once. */
    private boolean dstarPrecomputed = false;

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
    }

    @Override
    protected void registerGoals() {
        // No goals: all behavior driven by utility AI in aiStep()
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
        surfaceY = blockPosition().getY();
        return data;
    }

    /**
     * Pre-seed observation data on spawn. Called by ArchitectSpawner.
     * Gives partial knowledge so even short encounters show purposeful behavior.
     */
    public void preSeedObservation(ServerLevel level, Player nearestPlayer) {
        BlockPos playerPos = nearestPlayer.blockPosition();
        lastObservedPos = playerPos;
        scanEntrances(level, playerPos);
        scanLightSources(level, playerPos);
        playerUnderground = playerPos.getY() < level.getSeaLevel() - 10;
        findWeakestWall(level, playerPos);
    }

    public void armSpawnObserveCue(ServerPlayer player) {
        pendingSpawnCuePlayerId = player.getUUID();
        pendingSpawnCuePlayed = false;
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
        if (surfaceY == 0) surfaceY = blockPosition().getY();

        long gameTick = level().getGameTime();

        // --- Target acquisition ---
        // Architect senses through blocks — always knows target position
        LivingEntity target = findTarget();

        if (target == null) {
            if (!roamingAfterTargetLoss) {
                enterRoamModeAfterTargetLoss();
            }
        } else {
            if (roamingAfterTargetLoss && target instanceof Player player) {
                restartObserveForPlayer(player);
            }
            roamingAfterTargetLoss = false;
            lastKnownPlayerPos = target.blockPosition();
            lastSeenTick = tickCount;
        }

        maybeTriggerSpawnObserveCue(target);

        if (healCooldown > 0) healCooldown--;
        if (trapCooldown > 0) trapCooldown--;
        if (fallbackBreakCooldown > 0) fallbackBreakCooldown--;
        if (meleeCommitTicks > 0) meleeCommitTicks--;
        // Decay burst damage tracker outside window
        if (tickCount - lastDamageTick > BURST_WINDOW) recentDamage = 0f;

        // --- Potion drinking ---
        if (isDrinkingPotion) {
            drinkTicks++;
            if (drinkTicks >= DRINK_DURATION) {
                finishDrinking();
            }
            // Fully commits to drinking — no cancellation. Player can punish this.
            return;
        }

        // --- Keep wooden doors open while pushing toward a target ---
        if (currentAction == ACTION_APPROACH
                || currentAction == ACTION_ATTACK_MELEE
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
        } else {
            despawnTimer = 0;
        }

        // --- Utility AI scoring ---
        // Don't re-evaluate while actively mining — commit to the block
        // Only interrupt for critical HP (retreat needed)
        boolean miningLock = blockBreaker.isMining()
                && getHealth() > getMaxHealth() * 0.3f;

        reevalCooldown--;
        actionHoldTicks++;
        if (reevalCooldown <= 0 && !miningLock) {
            // Prevent rapid flip-flopping: hold current action for at least MIN_ACTION_HOLD ticks.
            // Retreat bypasses this — survival is always urgent.
            boolean holdLock = actionHoldTicks < MIN_ACTION_HOLD
                    && getHealth() > getMaxHealth() * 0.5f;
            if (!holdLock) {
                evaluateActions(target);
                reevalCooldown = 5;
            }
        }

        long actionStart = System.nanoTime();
        executeAction(target);
        long actionUs = (System.nanoTime() - actionStart) / 1000;
        if (actionUs > SLOW_EXEC_ACTION_LOG_US && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] executeAction({}) took {}us", currentAction, actionUs);
        }

        emitActionTelegraphParticles(target);

        // Only sprint when fleeing
        setSprinting(target != null && currentAction == ACTION_RETREAT && retreatPhase == 0);

        updateHeldItem();
    }

    private void evaluateActions(@Nullable LivingEntity target) {
        if (target != null
                && meleeCommitTicks > 0
                && currentAction != ACTION_RETREAT
                && getHealth() > getMaxHealth() * 0.35f
                && canCommitToMelee(target)) {
            if (currentAction != ACTION_ATTACK_MELEE) {
                onActionChange(currentAction, ACTION_ATTACK_MELEE);
                actionHoldTicks = 0;
            }
            primeMeleeHandoff();
            currentAction = ACTION_ATTACK_MELEE;
            entityData.set(DATA_ACTION, currentAction);
            return;
        }

        float bestScore = -1;
        int bestAction = ACTION_OBSERVE;
        float[] scores = new float[7];

        scores[ACTION_OBSERVE] = scoreObserve(target);
        scores[ACTION_APPROACH] = scoreApproach(target);
        scores[ACTION_ATTACK_MELEE] = scoreAttackMelee(target);
        scores[ACTION_RETREAT] = scoreRetreat(target);
        scores[ACTION_FORTIFY] = scoreFortify(target);
        scores[ACTION_TRAP_SET] = scoreTrapSet(target);
        scores[ACTION_PEEK] = scorePeek(target);

        for (int i = 0; i < scores.length; i++) {
            // Keep some unpredictability, but lower variance to reduce action thrash.
            scores[i] *= 0.95f + random.nextFloat() * 0.1f;
            // Hysteresis: current action gets a bonus to prevent flip-flopping
            if (i == currentAction) scores[i] *= 1.2f;
            if (scores[i] > bestScore) {
                bestScore = scores[i];
                bestAction = i;
            }
        }

        if (bestAction != currentAction) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Architect] SCORING: observe={} approach={} melee={} retreat={} HP={}/{} winner={} (was {})",
                        String.format("%.2f", scores[ACTION_OBSERVE]),
                        String.format("%.2f", scores[ACTION_APPROACH]),
                        String.format("%.2f", scores[ACTION_ATTACK_MELEE]),
                        String.format("%.2f", scores[ACTION_RETREAT]),
                        String.format("%.1f", getHealth()),
                        String.format("%.0f", getMaxHealth()),
                        bestAction,
                        currentAction);
            }
            onActionChange(currentAction, bestAction);
            actionHoldTicks = 0;
        }
        if (bestAction == ACTION_ATTACK_MELEE) {
            primeMeleeHandoff();
        }
        currentAction = bestAction;
        entityData.set(DATA_ACTION, currentAction);
    }

    private void triggerReeval() {
        reevalCooldown = 0;
        pathRecalcCooldown = 0;
    }

    // --- SCORING ---

    private float scoreObserve(@Nullable LivingEntity target) {
        if (target == null) return 0.1f;
        if (hasObserved && !observeDirty) return 0f;
        float score = 0.9f;
        if (!hasObserved) score *= 2.0f;
        if (getHealth() < getMaxHealth() * 0.7f) return 0f;
        if (observeDirty) score *= 0.7f;
        // Don't observe if already close — commit to attack
        float dist = distanceTo(target);
        if (dist < 16) return 0f;
        return score;
    }

    private float scoreApproach(@Nullable LivingEntity target) {
        if (target == null) return 0.1f;
        float dist = distanceTo(target);
        float score = 0.6f;
        if (dist > 16) score *= 1.2f;
        // Hand off to ATTACK_MELEE only when close AND we can see the target.
        // If close but behind a wall, keep approaching (mining through).
        if (shouldPreferMeleeOverApproach(target)) score *= 0.3f;
        if (getHealth() < getMaxHealth() * 0.5f) score *= 0.5f;
        if (target.hasLineOfSight(this)) score *= 0.8f;
        if (hasObserved) score *= 1.2f;
        if (rangedHitsReceived > 3) score *= 1.3f;
        return score;
    }

    private float scoreAttackMelee(@Nullable LivingEntity target) {
        if (target == null) return 0f;
        if (!canStartMelee(target)) return 0f;
        float dist = distanceTo(target);
        float score = 0.8f;
        // Smooth falloff instead of hard cutoff — commits to melee within 6 blocks
        if (dist < 3) score *= 1.5f;
        else if (dist < 4.75f) score *= 1.0f;
        else score *= 0.2f;
        // Low HP: increasingly hesitant to fight
        if (getHealth() < getMaxHealth() * 0.5f) score *= 0.6f;
        if (getHealth() < getMaxHealth() * 0.3f) score *= 0.6f;
        return score;
    }

    private float scoreRetreat(@Nullable LivingEntity target) {
        if (target == null) return 0f;
        float score = 0.2f;
        float healthPct = getHealth() / getMaxHealth();
        // Only retreat if we can actually heal — otherwise fight it out
        if (healCooldown > 0) return 0f;
        if (healthPct < 0.5f) score *= 2.0f;
        if (healthPct < 0.3f) score *= 1.5f;
        if (healthPct < 0.6f) score *= 1.3f;
        // Burst damage: taking heavy hits recently strongly favors retreat
        if (recentDamage > getMaxHealth() * 0.3f) score *= 1.5f;
        return score;
    }

    private float scoreFortify(@Nullable LivingEntity target) {
        float score = 0.15f;
        if (rangedHitsReceived > 3) score *= 1.5f;
        if (tacticalIce.size() >= MAX_TACTICAL_ICE) score *= 0.3f;
        if (target != null && target.hasLineOfSight(this) && distanceTo(target) > 8) score *= 1.3f;
        return score;
    }

    private float scoreTrapSet(@Nullable LivingEntity target) {
        if (target == null) return 0f;
        if (trapCooldown > 0) return 0f;
        if (entrancePositions.isEmpty()) return 0f;
        float score = 0.35f;
        if (isPlayerInsideBase(target)) score *= 1.3f;
        if (tacticalIce.size() >= MAX_TACTICAL_ICE) return 0f;
        return score;
    }

    private float scorePeek(@Nullable LivingEntity target) {
        if (target == null) return 0f;
        float score = 0.3f;
        if (isNearCorner()) score *= 1.5f;
        else return 0f;
        return score;
    }

    // --- ACTION EXECUTION ---

    private void executeAction(@Nullable LivingEntity target) {
        if (target == null) {
            executeRoamAndRuin();
            return;
        }

        switch (currentAction) {
            case ACTION_OBSERVE -> executeObserve(target);
            case ACTION_APPROACH -> executeApproach(target);
            case ACTION_ATTACK_MELEE -> executeAttackMelee(target);
            case ACTION_RETREAT -> executeRetreat(target);
            case ACTION_FORTIFY -> executeFortify(target);
            case ACTION_TRAP_SET -> executeTrapSet(target);
            case ACTION_PEEK -> executePeek(target);
        }
    }

    private void executeObserve(@Nullable LivingEntity target) {
        if (target == null) {
            dstarPrecomputed = false;
            return;
        }

        // Keep D* Lite warm during observation so APPROACH can react immediately.
        precomputeDStarDuringObserve(target);

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

        observeTicks++;

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
        if (observeTicks % 40 == 0 && level() instanceof ServerLevel serverLevel) {
            BlockPos playerPos = target.blockPosition();
            scanEntrances(serverLevel, playerPos);
            scanLightSources(serverLevel, playerPos);
            playerUnderground = playerPos.getY() < level().getSeaLevel() - 10;
            findWeakestWall(serverLevel, playerPos);
            lastObservedPos = playerPos;
        }

        // Raycast probe: run at tick 60 (3s in) and tick 300 (15s in, mid-observe)
        if ((observeTicks == 60 || observeTicks == 300)) {
            awardObserveProbeAdvancement(target);
            probeOptimalEntry(target);
        }

        if (dist < 20 && target.hasLineOfSight(this) && isPlayerFacing(target)) {
            hasObserved = true;
            observeDirty = false;
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
        if (observeTicks >= targetDuration) {
            hasObserved = true;
            observeDirty = false;
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
        if (pendingSpawnCuePlayed || pendingSpawnCuePlayerId == null || currentAction != ACTION_OBSERVE) {
            return;
        }
        if (!(target instanceof ServerPlayer player)) {
            return;
        }
        if (!pendingSpawnCuePlayerId.equals(player.getUUID())) {
            return;
        }
        if (distanceToSqr(player) > SPAWN_OBSERVE_CUE_RANGE_SQR) {
            return;
        }

        pendingSpawnCuePlayed = true;
        pendingSpawnCuePlayerId = null;
        level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.ARCHITECT_WATCHED.get(), SoundSource.HOSTILE,
                1.0f, 0.9f + random.nextFloat() * 0.2f);
        player.displayClientMessage(Component.translatable("message.frozendawn.architect_watched"), true);
    }

    private void precomputeDStarDuringObserve(LivingEntity target) {
        BlockPos targetPos = target.blockPosition();

        if (dstar.needsReinitialize(targetPos)) {
            dstar.setSurfaceY(surfaceY);
            dstar.initialize(targetPos, blockPosition(), level());
            dstarPrecomputed = false;
            dstar.computePartial(800, level());
        } else {
            dstar.updateStart(blockPosition());
            if (!dstar.isSearchComplete()) {
                dstar.computePartial(dstarPrecomputed ? 120 : 250, level());
            }
        }

        if (dstar.isSearchComplete()) {
            dstarPrecomputed = true;
        }
    }

    /**
     * Raycast probe during OBSERVE: for each cardinal direction, cast a ray
     * from outside the base inward toward the target. Sum the tool-aware breach
     * cost of every solid block in the ray. Add walk distance from mob to the
     * approach point. Pick the cheapest total.
     *
     * No A*, no heuristic bias. Four raycasts, exact optimal answer every time.
     */
    private void probeOptimalEntry(LivingEntity target) {
        BlockPos targetPos = target.blockPosition();

        float bestTotalCost = Float.MAX_VALUE;
        BlockPos bestApproach = null;

        Direction[] dirs = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };

        for (Direction dir : dirs) {
            // Start 20 blocks out from target in this direction
            BlockPos start = targetPos.relative(dir, 20);
            Direction inward = dir.getOpposite();

            float breachCost = 0;
            int breachBlocks = 0;
            boolean reachedInside = false;

            // Walk the ray inward toward target
            for (int i = 0; i < 20; i++) {
                BlockPos check = start.relative(inward, i);
                BlockState state = level().getBlockState(check);

                if (state.isAir()) {
                    // If we've breached at least one block, we're inside
                    if (breachBlocks > 0) {
                        reachedInside = true;
                        break;
                    }
                    continue; // Still outside, keep walking
                }

                if (isImmuneBlock(state, check)) {
                    breachCost = Float.MAX_VALUE; // Impassable
                    break;
                }

                float hardness = state.getDestroySpeed(level(), check);
                if (hardness < 0 || hardness >= 25.0F) {
                    breachCost = Float.MAX_VALUE; // Unbreakable
                    break;
                }

                // Tool-aware breach cost — ×2 for feet + headroom blocks
                float blockCost = ArchitectBlockBreaker.getEffectiveBreakTime(
                        state, check, level()) * BREACH_COST_MULTIPLIER;
                breachCost += blockCost * 2;
                breachBlocks++;
            }

            if (!reachedInside || breachCost >= Float.MAX_VALUE) continue;

            // Walk cost: Manhattan distance from mob to approach point
            BlockPos approachPoint = targetPos.relative(dir, breachBlocks + 1);
            float walkCost = (float)(Math.abs(getX() - approachPoint.getX())
                    + Math.abs(getZ() - approachPoint.getZ()));

            float totalCost = walkCost + breachCost;

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Architect] PROBE {}: breach={} walk={} total={} blocks={}",
                        dir,
                        String.format("%.1f", breachCost),
                        String.format("%.1f", walkCost),
                        String.format("%.1f", totalCost),
                        breachBlocks);
            }

            if (totalCost < bestTotalCost) {
                bestTotalCost = totalCost;
                bestApproach = approachPoint;
            }
        }

        if (bestApproach != null) {
            preferredEntryPoint = bestApproach;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Architect] OBSERVE probe: best entry at {} totalCost={}",
                        preferredEntryPoint,
                        String.format("%.1f", bestTotalCost));
            }
        }
    }

    private static final float BREACH_COST_MULTIPLIER = 5.0F;

    private boolean isImmuneBlock(BlockState state, BlockPos pos) {
        return state.is(ModBlocks.ACHERONITE_BLOCK.get())
                || state.is(ModBlocks.ACHERONITE_CRYSTAL.get())
                || state.is(ModBlocks.TRANSPONDER.get())
                || state.getDestroySpeed(level(), pos) < 0;
    }

    // ========================
    //  APPROACH — D* Lite step-by-step dispatch
    // ========================

    private void executeApproach(@Nullable LivingEntity target) {
        if (target == null) {
            unreachableTicks = 0;
            approachLastKnownPos();
            return;
        }

        recordWalkCellHistory();

        // Proactively open nearby wooden doors before movement dispatch.
        keepNearbyWoodenDoorsOpen();

        // Scaffold pacing: wait after placing ice, then jump up
        if (scaffoldTarget != null) {
            scaffoldDelay--;
            // Look down at the ice we just placed — like a player pillaring
            getLookControl().setLookAt(getX(), getY() - 1, getZ());
            if (scaffoldDelay <= 0) {
                teleportTo(scaffoldTarget.getX() + 0.5,
                        scaffoldTarget.getY(), scaffoldTarget.getZ() + 0.5);
                scaffoldTarget = null;
            }
            return; // Commit to the scaffold — no other actions during delay
        }


        // Handle smooth step-off lerp
        if (stepOffTarget != null) {
            stepOffProgress++;
            double t = Math.min(1.0, (double) stepOffProgress / STEP_OFF_DURATION);
            double smooth = 1.0 - (1.0 - t) * (1.0 - t);
            double lx = stepOffStart.x + (stepOffTarget.getX() + 0.5 - stepOffStart.x) * smooth;
            double ly = stepOffStart.y + (stepOffTarget.getY() - stepOffStart.y) * smooth;
            double lz = stepOffStart.z + (stepOffTarget.getZ() + 0.5 - stepOffStart.z) * smooth;
            setPos(lx, ly, lz);
            getNavigation().stop();
            getLookControl().setLookAt(stepOffTarget.getX() + 0.5,
                    stepOffTarget.getY(), stepOffTarget.getZ() + 0.5);
            if (stepOffProgress >= STEP_OFF_DURATION) {
                stepOffTarget = null;
                stepOffStart = null;
            }
            return;
        }

        // If actively mining, keep mining
        if (blockBreaker.isMining()) {
            if (continueBreaking(target)) return;
        }

        // Clean up stale break targets
        if (blockBreaker.hasTarget()) {
            BlockPos bt = blockBreaker.getTarget();
            if (bt != null && level().getBlockState(bt).isAir()) {
                blockBreaker.clearTarget();
                resetUnstickBreakTracker();
                if (bt.equals(ceilingBreachPos)) ceilingBreachPos = null;
            }
        }

        if (shouldPreferMeleeOverApproach(target)) {
            primeMeleeHandoff();
            reevalCooldown = 0;
        }

        if (tryContinueCommittedWalk(target)) {
            return;
        }

        if (canDirectChaseApproach(target)) {
            executeDirectApproachChase(target);
            return;
        }

        // --- D* Lite initialization / goal update ---
        BlockPos targetPos = target.blockPosition();
        if (dstar.needsReinitialize(targetPos)) {
            dstar.setSurfaceY(surfaceY);
            dstar.initialize(targetPos, blockPosition(), level());
            dstar.computePartial(2000, level());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Architect] D* Lite initialized: goal={} start={} cells={} complete={}",
                        targetPos, blockPosition(), dstar.getCellCount(), dstar.isSearchComplete());
            }
        }

        // Ensure search is complete
        if (!dstar.isSearchComplete()) {
            dstar.computePartial(500, level());
            if (!dstar.isSearchComplete()) return; // Still computing
        }

        // Update mob position in D* Lite
        dstar.updateStart(blockPosition());
        // Re-check consistency after start moved
        if (!dstar.isSearchComplete()) {
            dstar.computePartial(200, level());
            if (!dstar.isSearchComplete()) return;
        }

        // Query next step
        BlockPos avoidImmediateBacktrack = getImmediateBacktrackPos();
        DStarLitePathfinder.NextStep step = dstar.getNextStep(blockPosition(), level(), avoidImmediateBacktrack);
        // Pre-open wooden doors at the next cell so MoveControl doesn't stall on contact.
        keepDoorOpenNear(step.pos());

        invalidateStaleApproachBreakTarget(step, target);

        if (step.type() == DStarLitePathfinder.StepType.UNREACHABLE) {
            BlockPos stuckWalkStep = getCommittedWalkSteeringTarget();
            clearWalkNavigationState(true);
            if (stuckWalkStep != null) {
                trackWalkStep(stuckWalkStep);
                if (handleWalkStuck(stuckWalkStep, target)) {
                    return;
                }
            }
            clearCommittedWalk();
            unreachableTicks++;
            // Let D* settle briefly before falling back to manual wall breaking.
            if (unreachableTicks >= UNREACHABLE_BREAK_DELAY_TICKS) {
                fallbackWallBreak(target);
            }
            if (tickCount % 20 == 0 && LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Architect] D* Lite: UNREACHABLE, g={} cells={}",
                        String.format("%.1f", dstar.getStartG()), dstar.getCellCount());
            }
            // Safety valve: if we stay unreachable for too long, hard-refresh from current start.
            if (unreachableTicks >= 40) {
                dstar.setSurfaceY(surfaceY);
                dstar.initialize(targetPos, blockPosition(), level());
                dstar.computePartial(1200, level());
                unreachableTicks = 0;
                LOGGER.info("[Architect] D* Lite hard refresh after prolonged UNREACHABLE");
            }
            return;
        }
        unreachableTicks = 0;

        // If walking toward a still-valid break target, keep going.
        if (blockBreaker.hasTarget()) {
            clearWalkNavigationState(true);
            clearCommittedWalk();
            if (walkToBreakTarget()) return;
        }

        // Dispatch based on step type
        switch (step.type()) {
            case WALK -> {
                executeVanillaWalkStep(step, target);
            }
            case BREACH -> {
                clearWalkNavigationState(true);
                clearCommittedWalk();
                resetWalkStuckTracker();
                BlockPos breakTarget = step.breakTarget();
                if (breakTarget == null) breakTarget = findBreakableWallBlock(target);
                if (breakTarget != null) {
                    double blockDist = position().distanceToSqr(
                            breakTarget.getX() + 0.5, breakTarget.getY() + 0.5, breakTarget.getZ() + 0.5);
                    if (blockDist <= 4.5 * 4.5) {
                        blockBreaker.setTarget(breakTarget);
                        getNavigation().stop();
                        wallBreakAttempts++;
                        LOGGER.info("[Architect] D* BREACH at " + breakTarget
                                + " (" + level().getBlockState(breakTarget).getBlock() + ")");
                    } else {
                        // Walk toward the block first
                        Vec3 toBlock = new Vec3(
                                breakTarget.getX() + 0.5 - getX(), 0,
                                breakTarget.getZ() + 0.5 - getZ()).normalize();
                        getNavigation().moveTo(
                                breakTarget.getX() + 0.5 - toBlock.x * 1.5,
                                breakTarget.getY(),
                                breakTarget.getZ() + 0.5 - toBlock.z * 1.5, 1.0);
                    }
                }
            }
            case SCAFFOLD_UP -> {
                clearWalkNavigationState(true);
                clearCommittedWalk();
                resetWalkStuckTracker();
                if (onGround() && scaffoldIce.size() < MAX_SCAFFOLD_ICE) {
                    BlockPos feetPos = blockPosition();
                    placeScaffoldIce(feetPos);
                    // Queue the step-up with a delay — player-like pacing
                    scaffoldTarget = step.pos();
                    scaffoldDelay = SCAFFOLD_PLACE_TICKS;
                    LOGGER.info("[Architect] D* SCAFFOLD ice at " + feetPos
                            + " → " + step.pos() + " (waiting " + SCAFFOLD_PLACE_TICKS + "t)");
                }
            }
            case SCAFFOLD_BRIDGE -> {
                clearWalkNavigationState(true);
                clearCommittedWalk();
                resetWalkStuckTracker();
                // If the target is below us, avoid "capping the hole" with bridge ice.
                // Prefer descending (or digging down) into the structure instead.
                double horizontalTargetDelta = Math.sqrt(
                        (target.getX() - getX()) * (target.getX() - getX())
                                + (target.getZ() - getZ()) * (target.getZ() - getZ()));
                boolean targetDirectlyBelow = target.getY() < getY() - 1.0
                        && horizontalTargetDelta <= 2.5;
                if (targetDirectlyBelow) {
                    BlockPos dropInTarget = findDropInBreakTarget(target, step.pos());
                    if (dropInTarget != null) {
                        blockBreaker.setTarget(dropInTarget);
                        ceilingBreachPos = dropInTarget;
                        getNavigation().stop();
                        LOGGER.info("[Architect] Prefer drop-in over bridge: digging " + dropInTarget);
                        break;
                    }
                }
                // Horizontal bridge needs support under the destination before moving.
                BlockPos supportPos = step.pos().below();
                boolean descendingBridgeStep = step.pos().getY() < blockPosition().getY();
                if (!level().getBlockState(supportPos).isSolid()
                        && !targetDirectlyBelow
                        && !descendingBridgeStep
                        && onGround() && scaffoldIce.size() < MAX_SCAFFOLD_ICE) {
                    if (placeScaffoldIce(supportPos)) {
                        LOGGER.info("[Architect] D* BRIDGE ice at " + supportPos + " for " + step.pos());
                    }
                }
                getMoveControl().setWantedPosition(
                        step.pos().getX() + 0.5, step.pos().getY(),
                        step.pos().getZ() + 0.5, 1.0);
            }
            case DIG_DOWN -> {
                clearWalkNavigationState(true);
                clearCommittedWalk();
                resetWalkStuckTracker();
                BlockPos digTarget = step.breakTarget();
                if (digTarget != null && isBreakableBlock(digTarget)) {
                    blockBreaker.setTarget(digTarget);
                    teleportTo(digTarget.getX() + 0.5, getY(), digTarget.getZ() + 0.5);
                    getNavigation().stop();
                    ceilingBreachPos = digTarget;
                    LOGGER.info("[Architect] D* DIG DOWN at " + digTarget
                            + " (" + level().getBlockState(digTarget).getBlock() + ")");
                }
            }
        }

        // Periodic logging
        if (tickCount % 20 == 0 && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] action=APPROACH dist={} mining={} ice={}/{} pos={} step={} cells={}",
                    String.format("%.1f", distanceTo(target)),
                    blockBreaker.isMining(),
                    scaffoldIce.size(),
                    MAX_SCAFFOLD_ICE,
                    blockPosition(),
                    step.type(),
                    dstar.getCellCount());
        }
    }

    private void approachLastKnownPos() {
        if (lastKnownPlayerPos != null && tickCount - lastSeenTick < PLAYER_MEMORY_TICKS) {
            if (pathRecalcCooldown <= 0) {
                getNavigation().moveTo(lastKnownPlayerPos.getX() + 0.5,
                        lastKnownPlayerPos.getY(), lastKnownPlayerPos.getZ() + 0.5, 1.0);
                pathRecalcCooldown = 5;
            }
            pathRecalcCooldown--;
        }
    }

    private boolean continueBreaking(LivingEntity target) {
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
                if (ceilingBreachPos != null && bt.equals(ceilingBreachPos)) {
                    teleportTo(bt.getX() + 0.5, bt.getY(), bt.getZ() + 0.5);
                    getNavigation().stop();
                    ceilingBreachPos = null;
                    clearCommittedWalk();
                    playSound(ModSounds.ARCHITECT_LAND.get(), 0.8f, 0.7f + random.nextFloat() * 0.3f);
                    LOGGER.info("[Architect] Ceiling breach complete — dropping through " + bt);
                    pathRecalcCooldown = 0;
                    dstar.onLocalBlockChanged(bt, level());
                    triggerReeval();
                    return true;
                }

                pathRecalcCooldown = 0;
                dstar.onLocalBlockChanged(bt, level());

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

    private boolean walkToBreakTarget() {
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
    private BlockPos findDropInBreakTarget(@Nullable LivingEntity target, BlockPos stepPos) {
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


    private void fallbackWallBreak(LivingEntity target) {
        if (fallbackBreakCooldown > 0) return;
        BlockPos wallBlock = findBreakableWallBlock(target);
        if (wallBlock == null) return;

        if (wallBlock.equals(lastFallbackBreakPos)
                && !level().getBlockState(wallBlock).isAir()
                && !blockBreaker.hasTarget()) {
            fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
            return;
        }

        double blockDist = position().distanceToSqr(
                wallBlock.getX() + 0.5, wallBlock.getY() + 0.5, wallBlock.getZ() + 0.5);

        if (blockDist <= 4.5 * 4.5) {
            blockBreaker.setTarget(wallBlock);
            wallBreakAttempts++;
            lastFallbackBreakPos = wallBlock.immutable();
            fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
        } else {
            getNavigation().moveTo(wallBlock.getX() + 0.5,
                    wallBlock.getY(), wallBlock.getZ() + 0.5, 1.0);
            pathRecalcCooldown = 5;
            lastFallbackBreakPos = wallBlock.immutable();
            fallbackBreakCooldown = FALLBACK_BREAK_COOLDOWN_TICKS;
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
    private BlockPos findBreakableWallBlock(@Nullable LivingEntity target) {
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

    private void trackWalkStep(BlockPos stepPos) {
        BlockPos from = blockPosition();
        boolean repeated = stepPos.equals(lastWalkStepPos) && from.equals(lastWalkFromPos);
        // Detect A<->B ping-pong as stuck too, not just exact same edge.
        boolean pingPong = stepPos.equals(lastWalkFromPos) && from.equals(lastWalkStepPos);
        double horizontalMotionSqr = getDeltaMovement().x * getDeltaMovement().x
                + getDeltaMovement().z * getDeltaMovement().z;
        boolean actuallyStalled = onGround() && horizontalMotionSqr < 0.0025;
        boolean lowProgress = onGround() && horizontalMotionSqr < 0.04;
        if (repeated && actuallyStalled) {
            walkStuckTicks++;
        } else if (pingPong && lowProgress) {
            walkStuckTicks += 2;
        } else if (pingPong && onGround()) {
            walkStuckTicks++;
        } else {
            walkStuckTicks = 0;
        }
        lastWalkStepPos = stepPos.immutable();
        lastWalkFromPos = from.immutable();
    }

    private void resetWalkStuckTracker() {
        walkStuckTicks = 0;
        lastWalkStepPos = null;
        lastWalkFromPos = null;
    }

    private void recordWalkCellHistory() {
        BlockPos current = blockPosition();
        if (currentWalkCellPos == null) {
            currentWalkCellPos = current.immutable();
            return;
        }
        if (!current.equals(currentWalkCellPos)) {
            previousWalkCellPos = currentWalkCellPos;
            currentWalkCellPos = current.immutable();
        }
    }

    private void resetWalkCellHistory() {
        currentWalkCellPos = null;
        previousWalkCellPos = null;
        lastCompletedWalkWaypointPos = null;
        lastCompletedWalkBacktrackPos = null;
    }

    private void resetUnstickBreakTracker() {
        lastUnstickBreakCandidate = null;
        repeatedUnstickBreakAttempts = 0;
    }

    private void commitWalkStep(List<BlockPos> corridorNodes, @Nullable LivingEntity target) {
        if (corridorNodes.isEmpty()) {
            return;
        }

        committedWalkCorridor.clear();
        for (BlockPos node : corridorNodes) {
            committedWalkCorridor.add(node.immutable());
        }
        committedWalkCorridorIndex = 0;
        committedWalkFirstStepPos = committedWalkCorridor.get(0);
        committedWalkWaypoint = committedWalkCorridor.get(committedWalkCorridor.size() - 1);
        committedWalkStartPos = blockPosition().immutable();
        committedWalkBacktrackPos = pendingWalkBacktrackPos != null
                ? pendingWalkBacktrackPos.immutable()
                : committedWalkStartPos;
        committedWalkStartVec = position();
        committedWalkTargetSnapshot = target != null ? target.blockPosition().immutable() : null;
        committedWalkTicks = WALK_COMMIT_TICKS;
        committedWalkAgeTicks = 0;
        committedWalkNoProgressTicks = 0;
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        committedWalkLastDistSqr = steeringTarget != null
                ? distanceToWaypointSqr(steeringTarget)
                : Double.MAX_VALUE;
        pendingWalkBacktrackPos = null;
        resetWalkStuckTracker();
    }

    private void clearCommittedWalk() {
        committedWalkWaypoint = null;
        committedWalkFirstStepPos = null;
        committedWalkStartPos = null;
        committedWalkBacktrackPos = null;
        committedWalkTargetSnapshot = null;
        committedWalkStartVec = null;
        committedWalkCorridor.clear();
        committedWalkCorridorIndex = 0;
        committedWalkTicks = 0;
        committedWalkAgeTicks = 0;
        committedWalkNoProgressTicks = 0;
        committedWalkLastDistSqr = Double.MAX_VALUE;
        pendingWalkBacktrackPos = null;
    }

    private void invalidateCommittedWalk(String reason, @Nullable LivingEntity target) {
        if (committedWalkWaypoint == null) {
            return;
        }
        LOGGER.info("[Architect] WALK corridor invalidated: reason={} current={} firstStep={} waypoint={} age={} ttlLeft={} targetSnapshot={} targetNow={}",
                reason, blockPosition(), committedWalkFirstStepPos, committedWalkWaypoint,
                committedWalkAgeTicks, committedWalkTicks, committedWalkTargetSnapshot,
                target != null ? target.blockPosition() : null);
        clearCommittedWalk();
    }

    @Nullable
    private BlockPos getCommittedWalkSteeringTarget() {
        if (committedWalkCorridor.isEmpty()) {
            return committedWalkWaypoint;
        }
        if (committedWalkCorridorIndex < 0) {
            committedWalkCorridorIndex = 0;
        }
        if (committedWalkCorridorIndex >= committedWalkCorridor.size()) {
            committedWalkCorridorIndex = committedWalkCorridor.size() - 1;
        }
        return committedWalkCorridor.get(committedWalkCorridorIndex);
    }

    private boolean advanceCommittedWalkProgress() {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        while (steeringTarget != null) {
            double distSqr = distanceToWaypointSqr(steeringTarget);
            if (!hasReachedWalkWaypoint(steeringTarget, distSqr)) {
                return true;
            }

            if (committedWalkCorridorIndex < committedWalkCorridor.size() - 1) {
                committedWalkCorridorIndex++;
                committedWalkNoProgressTicks = 0;
                committedWalkLastDistSqr = Double.MAX_VALUE;
                steeringTarget = getCommittedWalkSteeringTarget();
                continue;
            }

            if (committedWalkWaypoint != null) {
                lastCompletedWalkWaypointPos = committedWalkWaypoint.immutable();
            }
            lastCompletedWalkBacktrackPos = committedWalkBacktrackPos != null
                    ? committedWalkBacktrackPos.immutable()
                    : committedWalkStartPos != null ? committedWalkStartPos.immutable() : null;
            clearCommittedWalk();
            return false;
        }
        return false;
    }

    private boolean continueCommittedWalk() {
        BlockPos steeringTarget = getCommittedWalkSteeringTarget();
        if (steeringTarget == null || committedWalkTicks <= 0) return false;

        double tx = steeringTarget.getX() + 0.5;
        double ty = steeringTarget.getY();
        double tz = steeringTarget.getZ() + 0.5;
        double horizontalDistSqr = (tx - getX()) * (tx - getX()) + (tz - getZ()) * (tz - getZ());
        if (steeringTarget.getY() > getY() + 0.1 && horizontalDistSqr <= 1.25 && onGround()) {
            getJumpControl().jump();
        }

        committedWalkTicks--;
        committedWalkAgeTicks++;
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
        if (committedWalkTicks <= 0) {
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

        if (target != null && committedWalkTargetSnapshot != null) {
            BlockPos targetPos = target.blockPosition();
            if (horizontalDistanceSqr(targetPos, committedWalkTargetSnapshot) > WALK_TARGET_SHIFT_HORIZONTAL_SQR
                    || Math.abs(targetPos.getY() - committedWalkTargetSnapshot.getY()) > WALK_TARGET_SHIFT_VERTICAL) {
                invalidateCommittedWalk("TARGET_SHIFT", target);
                return false;
            }
        }

        return true;
    }

    @Nullable
    private BlockPos getImmediateBacktrackPos() {
        BlockPos current = blockPosition();
        if (lastCompletedWalkWaypointPos != null
                && lastCompletedWalkBacktrackPos != null
                && current.equals(lastCompletedWalkWaypointPos)) {
            return lastCompletedWalkBacktrackPos;
        }
        if (currentWalkCellPos == null || previousWalkCellPos == null) return null;
        if (!current.equals(currentWalkCellPos)) return null;
        return previousWalkCellPos;
    }

    private boolean tryContinueCommittedWalk(@Nullable LivingEntity target) {
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
        if (distSqr + WALK_COMMIT_PROGRESS_EPSILON < committedWalkLastDistSqr) {
            committedWalkLastDistSqr = distSqr;
            committedWalkNoProgressTicks = 0;
        } else {
            committedWalkNoProgressTicks++;
        }

        if (committedWalkNoProgressTicks >= WALK_COMMIT_NO_PROGRESS_TICKS) {
            walkStuckTicks = Math.max(walkStuckTicks, WALK_STUCK_BREAK_TICKS);
            BlockPos stuckTarget = steeringTarget;
            invalidateCommittedWalk("STUCK", target);
            if (stuckTarget != null && handleWalkStuck(stuckTarget, target)) {
                return true;
            }
            return false;
        }

        if (committedWalkStartVec != null
                && committedWalkAgeTicks >= WALK_COMMIT_DEADMAN_TICKS
                && position().distanceToSqr(committedWalkStartVec) < WALK_COMMIT_DEADMAN_DISPLACEMENT_SQR) {
            walkStuckTicks = Math.max(walkStuckTicks, WALK_STUCK_BREAK_TICKS);
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

    private void invalidateStaleApproachBreakTarget(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
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
            if (bt.equals(ceilingBreachPos)) {
                ceilingBreachPos = null;
            }
        }
    }

    private boolean shouldContinueApproachBreak(@Nullable LivingEntity target, BlockPos expectedBreakTarget) {
        if (currentAction != ACTION_APPROACH || target == null) return false;

        BlockPos targetPos = target.blockPosition();
        if (dstar.needsReinitialize(targetPos)) return false;

        if (!dstar.isSearchComplete()) {
            dstar.computePartial(300, level());
            if (!dstar.isSearchComplete()) return false;
        }

        dstar.updateStart(blockPosition());
        if (!dstar.isSearchComplete()) {
            dstar.computePartial(200, level());
            if (!dstar.isSearchComplete()) return false;
        }

        DStarLitePathfinder.NextStep nextStep = dstar.getNextStep(blockPosition(), level());
        return nextStep.type() == DStarLitePathfinder.StepType.BREACH
                && expectedBreakTarget.equals(nextStep.breakTarget());
    }

    private boolean attemptWalkUnstickBreak(BlockPos stepPos) {
        BlockPos blockedCandidate = repeatedUnstickBreakAttempts >= MAX_REPEAT_UNSTICK_BREAK_ATTEMPTS
                ? lastUnstickBreakCandidate
                : null;

        BlockPos candidate = findWalkUnstickBreakCandidate(stepPos, blockedCandidate);
        if (candidate != null) {
            if (candidate.equals(lastUnstickBreakCandidate)) {
                repeatedUnstickBreakAttempts++;
            } else {
                lastUnstickBreakCandidate = candidate.immutable();
                repeatedUnstickBreakAttempts = 1;
            }
            blockBreaker.setTarget(candidate);
            LOGGER.info("[Architect] WALK stuck: breaking {} to unjam move toward {}", candidate, stepPos);
            return true;
        }

        if (!committedWalkCorridor.isEmpty()) {
            int fromIndex = Math.max(0, Math.min(committedWalkCorridorIndex, committedWalkCorridor.size()));
            BlockPos corridorBreakTarget = findWalkCorridorBreakTarget(
                    committedWalkCorridor.subList(fromIndex, committedWalkCorridor.size()));
            if (corridorBreakTarget != null
                    && (blockedCandidate == null || !blockedCandidate.equals(corridorBreakTarget))) {
                if (corridorBreakTarget.equals(lastUnstickBreakCandidate)) {
                    repeatedUnstickBreakAttempts++;
                } else {
                    lastUnstickBreakCandidate = corridorBreakTarget.immutable();
                    repeatedUnstickBreakAttempts = 1;
                }
                blockBreaker.setTarget(corridorBreakTarget);
                LOGGER.info("[Architect] WALK stuck: breaking corridor obstruction {} while following {}",
                        corridorBreakTarget, stepPos);
                return true;
            }
        }
        return false;
    }

    private boolean handleWalkStuck(BlockPos stepPos, @Nullable LivingEntity target) {
        if (walkStuckTicks < WALK_STUCK_BREAK_TICKS || blockBreaker.hasTarget()) {
            return false;
        }
        if (attemptWalkUnstickBreak(stepPos)) {
            return true;
        }
        if (walkStuckTicks >= WALK_STUCK_REINIT_TICKS && target != null) {
            dstar.onLocalBlockChanged(blockPosition(), level());
            dstar.setSurfaceY(surfaceY);
            dstar.initialize(target.blockPosition(), blockPosition(), level());
            dstar.computePartial(1000, level());
            walkStuckTicks = 0;
            LOGGER.info("[Architect] WALK stuck-trigger replan: refreshed D* around {}", blockPosition());
            return true;
        }
        return false;
    }

    private void executeVanillaWalkStep(DStarLitePathfinder.NextStep step, @Nullable LivingEntity target) {
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

    private boolean canDirectChaseApproach(@Nullable LivingEntity target) {
        if (target == null || blockBreaker.hasTarget() || !hasLineOfSight(target)) {
            return false;
        }
        if (horizontalDistanceTo(target) > DIRECT_APPROACH_PATH_HORIZONTAL_RANGE
                || verticalDistanceTo(target) > DIRECT_APPROACH_PATH_VERTICAL_RANGE) {
            return false;
        }
        return hasCleanReachableApproachPath(target);
    }

    private void executeDirectApproachChase(LivingEntity target) {
        clearCommittedWalk();
        resetWalkStuckTracker();
        unreachableTicks = 0;
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
                pendingWalkBacktrackPos = startPos.immutable();
            }
            corridor.add(firstStep.pos().immutable());
            return corridor;
        }

        BlockPos waypoint = firstStep.pos().immutable();
        corridor.add(waypoint);
        BlockPos current = waypoint;
        BlockPos previous = startPos;
        if (updatePendingWalkBacktrack) {
            pendingWalkBacktrackPos = startPos.immutable();
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

            DStarLitePathfinder.NextStep next = dstar.peekNextStep(current, level(), previous);
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
                pendingWalkBacktrackPos = previous.immutable();
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
        wallBreakAttempts++;
        blockBreaker.setTarget(breakTarget.immutable());
        LOGGER.info("[Architect] WALK corridor requires breach at {}", breakTarget);
        walkToBreakTarget();
    }

    @Nullable
    private BlockPos buildWalkCorridorWaypoint(BlockPos startPos, DStarLitePathfinder.NextStep firstStep) {
        List<BlockPos> corridor = buildWalkCorridorNodes(startPos, firstStep);
        if (corridor.isEmpty()) {
            return null;
        }
        return corridor.get(corridor.size() - 1);
    }

    private boolean isReverseOnlyWalkCorridor(BlockPos startPos, DStarLitePathfinder.NextStep firstStep,
                                              List<BlockPos> corridorNodes) {
        if (firstStep.type() != DStarLitePathfinder.StepType.WALK || corridorNodes.size() != 1) {
            return false;
        }

        BlockPos firstNode = corridorNodes.get(0);
        DStarLitePathfinder.NextStep continuation = dstar.peekNextStep(firstNode, level(), startPos);
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

    private void clearWalkNavigationState(boolean stopNavigation) {
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

    private void keepNearbyWoodenDoorsOpen() {
        keepDoorOpenNear(blockPosition());
        keepDoorOpenNear(blockPosition().above());
    }

    private void keepDoorOpenNear(BlockPos center) {
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

    private boolean isBreakableBlock(BlockPos pos) {
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

    private void applyCombatHorizontalMotion(double x, double z) {
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

    private void executeAttackMelee(@Nullable LivingEntity target) {
        double hDist = target != null ? horizontalDistanceTo(target) : Double.MAX_VALUE;
        double vDist = target != null ? verticalDistanceTo(target) : Double.MAX_VALUE;
        float dist3d = target != null ? distanceTo(target) : Float.MAX_VALUE;
        boolean hasLos = target != null && hasLineOfSight(target);
        if (target == null
                || hDist > MELEE_COMMIT_HORIZONTAL_RANGE
                || vDist > MELEE_COMMIT_VERTICAL_RANGE
                || (!hasLos && hDist > MELEE_COMMIT_LOS_GRACE_RANGE)) {
            meleeCommitTicks = 0;
            triggerReeval();
            return;
        }

        blockBreaker.clearTarget();
        getLookControl().setLookAt(target, 30f, 30f);
        if (getHealth() > getMaxHealth() * 0.35f) {
            meleeCommitTicks = Math.max(meleeCommitTicks, MELEE_COMMIT_TICKS);
        }

        // Backoff after landing a hit — sprint backwards briefly
        // Check ground behind before backing off to avoid walking off edges/bridges
        if (backoffTicks > 0) {
            backoffTicks--;
            Vec3 away = position().subtract(target.position()).normalize();
            BlockPos behind = blockPosition().offset(
                    (int) Math.round(away.x * 2), 0, (int) Math.round(away.z * 2));
            boolean groundBehind = level().getBlockState(behind.below()).isSolid();
            if (groundBehind) {
                // Safe to back off
                applyCombatHorizontalMotion(away.x * MELEE_BACKOFF_SPEED, away.z * MELEE_BACKOFF_SPEED);
            } else {
                // No ground behind — sideways dodge instead of suicidal backoff
                double dodgeX = -away.z * strafeDir * MELEE_DODGE_SPEED;
                double dodgeZ = away.x * strafeDir * MELEE_DODGE_SPEED;
                applyCombatHorizontalMotion(dodgeX, dodgeZ);
            }
            getNavigation().stop();
            return;
        }

        // Strafe direction change
        strafeChangeCooldown--;
        if (strafeChangeCooldown <= 0) {
            strafeDir = -strafeDir;
            strafeChangeCooldown = 30 + random.nextInt(30);
        }

        if (hDist > 3.0) {
            // Close in — path directly toward target (handles step-ups via navigation)
            getNavigation().moveTo(target, 1.0);
        } else {
            // In melee range — circle strafe using movement control (smoother than moveTo)
            getNavigation().stop();
            Vec3 toTarget = target.position().subtract(position()).normalize();
            // Perpendicular strafe vector
            double strafeX = -toTarget.z * strafeDir * MELEE_STRAFE_SPEED;
            double strafeZ = toTarget.x * strafeDir * MELEE_STRAFE_SPEED;
            // Slight pull toward target to maintain distance
            double pullStrength = hDist < 2.0 ? MELEE_PULL_SPEED_NEAR : MELEE_PULL_SPEED_FAR;
            applyCombatHorizontalMotion(
                    strafeX + toTarget.x * pullStrength,
                    strafeZ + toTarget.z * pullStrength);

            // Attack when close enough — require LOS to prevent hitting through walls
            if (dist3d < 2.8 && attackAnim == 0 && hasLineOfSight(target)) {
                swing(InteractionHand.MAIN_HAND);
                doHurtTarget(target);
                backoffTicks = 6 + random.nextInt(4);
            }
        }
    }

    private void executeRetreat(@Nullable LivingEntity target) {
        blockBreaker.clearTarget();

        // No target — nothing to retreat from. Reset state so next evaluation picks a new action.
        if (target == null && !isDrinkingPotion) {
            retreatPhase = 0;
            retreatCoverBuilt = 0;
            return;
        }

        float dist = target != null ? distanceTo(target) : 999;

        switch (retreatPhase) {
            case 0 -> { // Phase 0: Run away until RETREAT_DISTANCE reached
                if (dist >= RETREAT_DISTANCE || target == null) {
                    LOGGER.info("[Architect] RETREAT: reached safe distance (" + String.format("%.1f", dist) + "), building cover");
                    getNavigation().stop();
                    retreatPhase = 1;
                    retreatCoverBuilt = 0;
                    return;
                }
                Vec3 away = position().subtract(target.position());
                if (away.lengthSqr() < 1.0e-4) {
                    away = new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5);
                }
                away = away.normalize();
                getMoveControl().setWantedPosition(
                        getX() + away.x * 2.0,
                        getY(),
                        getZ() + away.z * 2.0,
                        1.3
                );
                if (pathRecalcCooldown <= 0) {
                    getNavigation().moveTo(
                            getX() + away.x * RETREAT_DISTANCE,
                            getY(),
                            getZ() + away.z * RETREAT_DISTANCE,
                            1.3
                    );
                    pathRecalcCooldown = 10;
                }
                pathRecalcCooldown--;
            }
            case 1 -> { // Phase 1: Build ice cover between self and player
                getNavigation().stop();
                if (target != null && retreatCoverBuilt < 3 && tacticalIce.size() < MAX_TACTICAL_ICE) {
                    Vec3 towardPlayer = target.position().subtract(position()).normalize();
                    BlockPos wallPos = blockPosition().offset(
                            (int) Math.round(towardPlayer.x * (1 + retreatCoverBuilt)),
                            0,
                            (int) Math.round(towardPlayer.z * (1 + retreatCoverBuilt)));
                    if (placeTacticalIce(wallPos)) {
                        placeTacticalIce(wallPos.above());
                        LOGGER.info("[Architect] RETREAT: placed ice wall #" + retreatCoverBuilt + " at " + wallPos);
                        retreatCoverBuilt++;
                    } else {
                        retreatCoverBuilt++;
                    }
                } else {
                    LOGGER.info("[Architect] RETREAT: cover complete (" + retreatCoverBuilt + " walls), entering heal phase");
                    retreatPhase = 2;
                }
            }
            case 2 -> { // Phase 2: Heal — commits fully, no cancellation
                getNavigation().stop();
                if (healCooldown <= 0 && !isDrinkingPotion && getHealth() < getMaxHealth() * 0.75f) {
                    LOGGER.info("[Architect] RETREAT: starting to drink healing potion (HP=" + String.format("%.1f", getHealth()) + ")");
                    startDrinking();
                } else if (isDrinkingPotion) {
                    // Let drinking continue (handled in aiStep) — never cancel
                } else {
                    LOGGER.info("[Architect] RETREAT: heal phase complete (HP=" + String.format("%.1f", getHealth()) + "), re-evaluating");
                    triggerReeval();
                }
            }
        }
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
        if (target == null || entrancePositions.isEmpty()) { triggerReeval(); return; }

        BlockPos bestEntrance = null;
        double bestDist = 0;
        for (BlockPos entrance : entrancePositions) {
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
        if (oldAction == ACTION_OBSERVE) observeTicks = 0;
        if (oldAction == ACTION_PEEK) peekTicks = 0;
        if (oldAction == ACTION_APPROACH) unreachableTicks = 0;
        clearWalkNavigationState(true);
        clearCommittedWalk();
        resetWalkStuckTracker();
        resetWalkCellHistory();
        resetUnstickBreakTracker();
        if (oldAction != ACTION_APPROACH) blockBreaker.clearTarget();
        if (oldAction == ACTION_APPROACH) {
            ceilingBreachPos = null;
            stepOffTarget = null;
            stepOffStart = null;
        }
        if (oldAction == ACTION_RETREAT && isDrinkingPotion) cancelDrinking();
        if (newAction == ACTION_RETREAT) { retreatPhase = 0; retreatCoverBuilt = 0; }
        if (newAction == ACTION_ATTACK_MELEE) {
            primeMeleeHandoff();
        }
        pathRecalcCooldown = 0; // Force path recalc on action change
    }

    private void primeMeleeHandoff() {
        meleeCommitTicks = Math.max(meleeCommitTicks, MELEE_COMMIT_TICKS);
        clearWalkNavigationState(true);
        clearCommittedWalk();
        blockBreaker.clearTarget();
        ceilingBreachPos = null;
    }

    private double horizontalDistanceTo(LivingEntity target) {
        double dx = getX() - target.getX();
        double dz = getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double verticalDistanceTo(LivingEntity target) {
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

    private boolean shouldPreferMeleeOverApproach(LivingEntity target) {
        return canStartMelee(target);
    }

    private void enterRoamModeAfterTargetLoss() {
        roamingAfterTargetLoss = true;
        resetObserveCycle();
        retreatPhase = 0;
        retreatCoverBuilt = 0;
        clearWalkNavigationState(true);
        blockBreaker.clearTarget();
        pathRecalcCooldown = 0;
        LOGGER.info("[Architect] Lost target — entering roam/ruin mode");
    }

    private void restartObserveForPlayer(Player player) {
        resetObserveCycle();
        if (currentAction != ACTION_OBSERVE) {
            onActionChange(currentAction, ACTION_OBSERVE);
        }
        currentAction = ACTION_OBSERVE;
        entityData.set(DATA_ACTION, currentAction);
        actionHoldTicks = 0;
        reevalCooldown = 0;
        LOGGER.info("[Architect] Player reacquired at "
                + String.format("%.1f", distanceTo(player))
                + " blocks — restarting OBSERVE");
    }

    private void resetObserveCycle() {
        hasObserved = false;
        observeDirty = false;
        observeTicks = 0;
        preferredEntryPoint = null;
        lastObservedPos = null;
        dstarPrecomputed = false;
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
                rangedHitsReceived++;
            }
            // Track burst damage for retreat scoring
            if (tickCount - lastDamageTick > BURST_WINDOW) {
                recentDamage = 0f; // Reset if outside burst window
            }
            recentDamage += amount;
            lastDamageTick = tickCount;
            // Don't cancel potion or re-evaluate during heal — fully commits
            if (!isDrinkingPotion) {
                triggerReeval();
            }
        }
        return hurt;
    }

    // ========================
    //  HEALING POTION
    // ========================

    private void startDrinking() {
        isDrinkingPotion = true;
        drinkTicks = 0;
        ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING);
        setItemSlot(EquipmentSlot.MAINHAND, potion);
        getNavigation().stop();
        playSound(ModSounds.ARCHITECT_DRINK.get(), 0.8f, 0.95f + random.nextFloat() * 0.1f);
    }

    private void finishDrinking() {
        isDrinkingPotion = false;
        drinkTicks = 0;
        healCooldown = HEAL_COOLDOWN_TICKS;
        float targetHealth = getMaxHealth() * 0.75f;
        if (getHealth() < targetHealth) setHealth(targetHealth);
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        triggerReeval();
    }

    private void cancelDrinking() {
        isDrinkingPotion = false;
        drinkTicks = 0;
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void emitActionTelegraphParticles(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        switch (currentAction) {
            case ACTION_APPROACH -> {
                if (blockBreaker.isMining() && tickCount % 4 == 0) {
                    BlockPos targetPos = blockBreaker.getTarget();
                    if (targetPos != null) {
                        BlockState state = level().getBlockState(targetPos);
                        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                                5, 0.25, 0.25, 0.25, 0.02);
                        serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                                targetPos.getX() + 0.5, targetPos.getY() + 0.6, targetPos.getZ() + 0.5,
                                2, 0.18, 0.12, 0.18, 0.01);
                    }
                } else if (scaffoldTarget != null && tickCount % 4 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                            getX(), getY() + 0.2, getZ(),
                            3, 0.2, 0.05, 0.2, 0.01);
                }
            }
            case ACTION_ATTACK_MELEE -> {
                if (target != null
                        && hasLineOfSight(target)
                        && distanceTo(target) < 5.0f
                        && tickCount % 8 == 0) {
                    Vec3 toward = target.position().subtract(position());
                    Vec3 forward = toward.lengthSqr() > 1.0e-4 ? toward.normalize() : Vec3.ZERO;
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                            getX() + forward.x * 0.55, getY() + 1.15, getZ() + forward.z * 0.55,
                            3, 0.15, 0.2, 0.15, 0.01);
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            getX() + forward.x * 0.3, getY() + 1.0, getZ() + forward.z * 0.3,
                            2, 0.1, 0.1, 0.1, 0.005);
                }
            }
            case ACTION_RETREAT -> {
                if ((retreatPhase == 1 || isDrinkingPotion) && tickCount % 12 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                            getX(), getY() + 1.0, getZ(),
                            4, 0.25, 0.3, 0.25, 0.01);
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                            getX(), getY() + 0.9, getZ(),
                            2, 0.18, 0.12, 0.18, 0.003);
                }
            }
            default -> {
            }
        }
    }

    // ========================
    //  ICE PLACEMENT
    // ========================

    /**
     * Place scaffold ice. Evicts oldest BEHIND the entity, never beneath.
     */
    private boolean placeScaffoldIce(BlockPos pos) {
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

    private boolean placeTacticalIce(BlockPos pos) {
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
        if (isDrinkingPotion) return;

        boolean building = entityData.get(DATA_BUILDING_ICE);

        switch (currentAction) {
            case ACTION_ATTACK_MELEE ->
                    setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            case ACTION_APPROACH -> {
                if (blockBreaker.isMining()) {
                    // Tool is set by block breaker
                } else if (building) {
                    setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Blocks.PACKED_ICE));
                } else {
                    setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
            case ACTION_FORTIFY, ACTION_TRAP_SET ->
                    setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Blocks.PACKED_ICE));
            case ACTION_RETREAT -> {
                if (retreatPhase == 1) {
                    setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Blocks.PACKED_ICE));
                } else {
                    setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
            default -> setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        entityData.set(DATA_BUILDING_ICE,
                currentAction == ACTION_FORTIFY
                        || currentAction == ACTION_TRAP_SET
                        || (currentAction == ACTION_RETREAT && retreatPhase == 1)
                        || (currentAction == ACTION_APPROACH && !scaffoldIce.isEmpty()));
    }

    // ========================
    //  OBSERVATION HELPERS
    // ========================

    private void scanEntrances(ServerLevel level, BlockPos center) {
        entrancePositions.clear();
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
                        if (nearWall) entrancePositions.add(check.immutable());
                        break;
                    }
                }
            }
        }
    }

    private void scanLightSources(ServerLevel level, BlockPos center) {
        lightSources.clear();
        int radius = 12;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -4, -radius),
                center.offset(radius, 4, radius))) {
            if (isLightSource(level.getBlockState(pos))) {
                lightSources.add(pos.immutable());
            }
        }
    }

    private void findWeakestWall(ServerLevel level, BlockPos playerPos) {
        net.minecraft.core.Direction[] dirs = {
                net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.WEST
        };
        int leastSolid = Integer.MAX_VALUE;
        int bestDir = 0;
        for (int d = 0; d < 4; d++) {
            int solidCount = 0;
            BlockPos.MutableBlockPos probe = playerPos.mutable();
            for (int i = 1; i <= 10; i++) {
                probe.move(dirs[d]);
                if (level.getBlockState(probe).isSolid()) solidCount++;
            }
            if (solidCount < leastSolid) { leastSolid = solidCount; bestDir = d; }
        }
        weakestWallDirection = playerPos.relative(dirs[bestDir], 5);
    }

    /**
     * Called when player places blocks near last observed position.
     * Threshold: 5+ changes within 16 blocks.
     */
    public void onNearbyBlockChange(BlockPos changedPos, int changeCount) {
        if (lastObservedPos != null && changeCount >= 5
                && changedPos.closerToCenterThan(lastObservedPos.getCenter(), 16.0)) {
            observeDirty = true;
        }
        // Notify D* Lite of world changes so it updates costs incrementally
        dstar.onBlockChanged(changedPos, level());
    }

    private boolean isLightSource(BlockState state) {
        if (state.is(ModBlocks.FROST_WARD_TORCH.get()) || state.is(ModBlocks.FROST_WARD_WALL_TORCH.get())) {
            return false;
        }
        return state.getBlock() instanceof BaseTorchBlock
                || state.getBlock() instanceof LanternBlock
                || state.getBlock() instanceof CampfireBlock
                || state.is(Blocks.GLOWSTONE)
                || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.SEA_LANTERN)
                || state.is(Blocks.JACK_O_LANTERN);
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
        double playerRange = roamingAfterTargetLoss ? OBSERVE_REACQUIRE_RANGE : range;
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
    public boolean isMiningBlock() { return blockBreaker.isMining(); }
    public float getMiningProgress() { return blockBreaker.getMiningProgress(); }
    public boolean hasQueuedScaffoldStep() { return scaffoldTarget != null || scaffoldDelay > 0; }
    public boolean isRetreatRecovering() { return currentAction == ACTION_RETREAT && (retreatPhase >= 1 || isDrinkingPotion); }
    public boolean isProbing() {
        return probing;
    }

    public int getSurfaceY() { return surfaceY; }
    public DStarLitePathfinder getDStarPathfinder() { return dstar; }
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
        tag.putInt("CurrentAction", currentAction);
        tag.putBoolean("HasObserved", hasObserved);
        tag.putBoolean("ObserveDirty", observeDirty);
        tag.putBoolean("PlayerUnderground", playerUnderground);
        tag.putInt("RangedHitsReceived", rangedHitsReceived);
        tag.putInt("WallBreakAttempts", wallBreakAttempts);
        tag.putBoolean("AcheroniteEncountered", acheroniteEncountered);
        tag.putInt("HealCooldown", healCooldown);
        tag.putInt("SurfaceY", surfaceY);

        ListTag scaffoldList = new ListTag();
        for (BlockPos pos : scaffoldIce) scaffoldList.add(LongTag.valueOf(pos.asLong()));
        tag.put("ScaffoldIce", scaffoldList);

        ListTag tacticalList = new ListTag();
        for (BlockPos pos : tacticalIce) tacticalList.add(LongTag.valueOf(pos.asLong()));
        tag.put("TacticalIce", tacticalList);

        if (lastKnownPlayerPos != null) tag.putLong("LastKnownPlayerPos", lastKnownPlayerPos.asLong());
        if (lastObservedPos != null) tag.putLong("LastObservedPos", lastObservedPos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTextureVariant(tag.getInt("TextureVariant"));
        despawnTimer = tag.getInt("DespawnTimer");
        currentAction = tag.getInt("CurrentAction");
        // Delay first pathfinding after world load to prevent freeze
        pathRecalcCooldown = 40;
        reevalCooldown = 40;
        hasObserved = tag.getBoolean("HasObserved");
        observeDirty = tag.getBoolean("ObserveDirty");
        playerUnderground = tag.getBoolean("PlayerUnderground");
        rangedHitsReceived = tag.getInt("RangedHitsReceived");
        wallBreakAttempts = tag.getInt("WallBreakAttempts");
        acheroniteEncountered = tag.getBoolean("AcheroniteEncountered");
        healCooldown = tag.getInt("HealCooldown");
        surfaceY = tag.getInt("SurfaceY");
        if (surfaceY == 0) surfaceY = blockPosition().getY(); // migration for existing entities

        scaffoldIce.clear();
        ListTag scaffoldList = tag.getList("ScaffoldIce", Tag.TAG_LONG);
        for (int i = 0; i < scaffoldList.size(); i++) {
            scaffoldIce.add(BlockPos.of(((LongTag) scaffoldList.get(i)).getAsLong()));
        }
        tacticalIce.clear();
        ListTag tacticalList = tag.getList("TacticalIce", Tag.TAG_LONG);
        for (int i = 0; i < tacticalList.size(); i++) {
            tacticalIce.add(BlockPos.of(((LongTag) tacticalList.get(i)).getAsLong()));
        }
        if (tag.contains("LastKnownPlayerPos")) lastKnownPlayerPos = BlockPos.of(tag.getLong("LastKnownPlayerPos"));
        if (tag.contains("LastObservedPos")) lastObservedPos = BlockPos.of(tag.getLong("LastObservedPos"));
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
