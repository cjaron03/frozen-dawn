package com.frozendawn.entity;

import com.frozendawn.entity.architect.BreakChoice;
import com.frozendawn.entity.architect.BreakReason;
import com.frozendawn.entity.architect.ArchitectDecisionJournal;
import com.frozendawn.entity.architect.ArchitectWalkBreakPlanner;

import com.frozendawn.entity.architect.ArchitectApproachState;
import com.frozendawn.entity.architect.ArchitectApproachRecovery;
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
import com.frozendawn.maeve.MaeveDirector;
import com.frozendawn.data.PlayerEndStats;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.homo.HearthArchitectPolicy;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthTargetPolicy.Candidate;
import com.frozendawn.homo.HearthMasterArchitectManager;
import com.frozendawn.homo.HearthMasterArchitectPolicy;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthPopulationPolicy;
import com.frozendawn.homo.HearthPopulationRole;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.homo.HeartScavengerWaveManager;
import com.frozendawn.homo.MasterArchitectBossBarPolicy;
import com.frozendawn.homo.MasterArchitectCombatPhase;
import com.frozendawn.homo.MasterArchitectCombatPolicy;
import com.frozendawn.aggregate.AggregateReinforcementManager;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.TowerEncounterController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
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

    private static final EntityDataAccessor<Integer> DATA_PURSUIT_POSE =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MAEVE_HOLD =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RECON_EYES =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RECON_POSE =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_RECON_DISSOLVE =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_RECON_CLOUD_START =
            SynchedEntityData.defineId(ArchitectEntity.class, EntityDataSerializers.LONG);
    private final ArchitectThinkingController thinkingController = new ArchitectThinkingController(this);
    private float thinkingTilt, thinkingTiltOld, thinkingHand, thinkingHandOld;

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
    private final List<BlockPos> mantletIce = new ArrayList<>();
    private static final int MAX_SCAFFOLD_ICE = 64;
    private static final int MAX_TACTICAL_ICE = 12;
    private static final int MASTER_MAX_TACTICAL_ICE = 6;

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
    private boolean masterBossBarProvoked;

    private static final int HEAL_COOLDOWN_TICKS = 1200;
    private static final int DRINK_DURATION = 32;

    // --- Burst Damage Tracking ---
    /** Damage taken in the last BURST_WINDOW ticks. Used to boost retreat scoring. */
    private static final int BURST_WINDOW = 60; // 3 seconds

    // --- Block Breaker ---
    private final ArchitectDecisionJournal decisionJournal = new ArchitectDecisionJournal();
    @Nullable private DStarLitePathfinder.NextStep debugStep;
    private long lastDebugStepTick = Long.MIN_VALUE;
    private String lastCandidateSignature = "";
    private long lastCandidateTick = Long.MIN_VALUE;
    @Nullable private Long debugSeed;
    /** Lab-only target lock: pins {@link #findTarget()} so a nearby player cannot steal the run. */
    @Nullable private UUID debugForcedTargetId;
    @Nullable private UUID lastJournalTargetId;
    record MeleeDebugObservation(long gameTick, UUID target, boolean allowed) { }
    @Nullable private MeleeDebugObservation meleeDebugObservation;

    private final ArchitectBlockBreaker blockBreaker = new ArchitectBlockBreaker(this, this::onApproachBreakAttemptFinished);
    private final ArchitectCommitmentController maeveCommitment = new ArchitectCommitmentController(this, blockBreaker);
    private final ArchitectAttentionController maeveAttention = new ArchitectAttentionController(this);
    private final ArchitectReconnaissanceController maeveReconnaissance = new ArchitectReconnaissanceController(this);
    private long localCombatUntil;
    private UUID lastCombatSubject;
    private int thinkingInterruptedUntil;
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
    private final ArchitectAssessmentCommitment roamingCommitment =
            new ArchitectAssessmentCommitment();
    /** Debug only: last roaming target logged, so the line fires on change, not per tick. */
    @Nullable
    private UUID loggedRoamingTargetId;
    private final ArchitectDecisionEngine decisionEngine = new ArchitectDecisionEngine();
    private final ArchitectFxController fxController = new ArchitectFxController(this, blockBreaker);

    // --- Despawn ---
    private int despawnTimer = 0;
    private static final int DESPAWN_TIMEOUT = 6000;
    private int aggregateReinforcementAttackCooldown;

    // --- Misc ---
    private int peekTicks = 0;
    private int trapCooldown = 0;
    private int pathRecalcCooldown = 0;
    private boolean suppressMasterHurtSound;
    private boolean maeveShieldDamageInProgress;
    private int clientMasterTetherHurtSuppressionTicks;
    private boolean mindReturnDeathDetonatesImmediately;
    private boolean foldedDeathPresentationAlreadyPlayed;
    private boolean mindReturnDeathSoundOnly;
    private boolean mindCopyDefeatReported;
    private DamageSource mindCopyDeathSource;
    private UUID masterDeathKillerId;
    private static final float WALK_MAX_ROTATE = 35.0F;
    static final int UNREACHABLE_BREAK_DELAY_TICKS = 8;
    static final int MELEE_COMMIT_TICKS = 12;
    static final double MELEE_ATTACK_RANGE = 2.8;
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
    /** Journal heartbeat cadence, so a recording never goes silent outside APPROACH. */
    private static final int JOURNAL_HEARTBEAT_TICKS = 20;
    static final int SCAFFOLD_PLACE_TICKS = 12; // ~0.6s pause after placing before stepping up

    public ArchitectEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.moveControl = new ArchitectMoveControl(this, WALK_MAX_ROTATE);
        approachState.dstar.setBreakPermission(pos -> !isOwnedScaffold(pos) || canReclaimScaffold(pos));
        setCustomName(Component.literal("The Architect"));
        setCustomNameVisible(true);
        if (!level.isClientSide && decisionJournal.enabled()) {
            Long seed = Long.getLong("frozendawn.debug.architectSeed");
            if (seed != null) { random.setSeed(seed); debugSeed = seed; }
        }
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

    /**
     * Kept in lockstep with the planner's fall limit. APPROACH reads this too: vanilla
     * {@code createPath} uses it to build open-descent routes, and
     * {@link ArchitectApproachMovementSupport#isSafeWalkingPath} gates each step against it.
     * Raising it here lets the Architect drop into pits the planner would refuse -- verify
     * with the {@code pit_side_steps} lab scenario before changing it.
     */
    @Override
    public int getMaxFallDistance() {
        return DStarLitePathfinder.MAX_SAFE_FALL_DISTANCE;
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
        builder.define(DATA_PURSUIT_POSE, 0);
        builder.define(DATA_MAEVE_HOLD, false);
        builder.define(DATA_RECON_EYES, false);
        builder.define(DATA_RECON_POSE, false);
        builder.define(DATA_RECON_DISSOLVE, 0);
        builder.define(DATA_RECON_CLOUD_START, -1L);
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
        if (isShowingReconnaissancePose()) return false;
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

    void notePursuitRouteChange(String reason, @Nullable BlockPos focus) {
        thinkingController.noteRouteChange(reason, focus);
    }

    public float getThinkingTilt(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, thinkingTiltOld, thinkingTilt);
    }

    public float getThinkingHand(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, thinkingHandOld, thinkingHand);
    }

    public boolean isHoldingMaevePosition() {
        return entityData.get(DATA_MAEVE_HOLD) && isAlive() && !isNoAi()
                && getDeathTicks() == 0 && !isMasterArchitectVisual();
    }

    void setMaeveHolding(boolean holding) {
        entityData.set(DATA_MAEVE_HOLD, holding);
    }
    public boolean hasReconnaissanceEyes() { return entityData.get(DATA_RECON_EYES) && !isMasterArchitectVisual(); }
    void setReconnaissanceEyes(boolean active) {
        entityData.set(DATA_RECON_EYES, active);
        if (!active) {
            entityData.set(DATA_RECON_POSE, false); entityData.set(DATA_RECON_DISSOLVE, 0);
            entityData.set(DATA_RECON_CLOUD_START, -1L);
        }
    }
    public int getReconnaissanceDissolve() {
        return hasReconnaissanceEyes() && isAlive() && !isNoAi() && getDeathTicks() == 0
                ? entityData.get(DATA_RECON_DISSOLVE) : 0;
    }
    void setReconnaissanceDissolve(int form) { entityData.set(DATA_RECON_DISSOLVE, form); }
    void setReconnaissanceCloudStart(long time) { entityData.set(DATA_RECON_CLOUD_START, time); }
    public int getReconnaissanceCloudAge() {
        long start = entityData.get(DATA_RECON_CLOUD_START), age = level().getGameTime() - start;
        return getReconnaissanceDissolve() != 0 && start >= 0 && age >= 0 && age < 400 ? (int) age : -1;
    }

    /** Presentation of an actual noncombat scout task, independent of its fallback utility action. */
    public boolean isShowingReconnaissancePose() {
        return entityData.get(DATA_RECON_POSE) && hasReconnaissanceEyes() && isAlive()
                && !isNoAi() && getDeathTicks() == 0;
    }

    private void updateReconnaissancePose(boolean passive) {
        entityData.set(DATA_RECON_POSE, passive && hasReconnaissanceEyes() && !combatState.isDrinkingPotion);
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

    int getMantletIceCount() { return mantletIce.size(); }

    int getMaxTacticalIce() {
        return isMasterArchitectVisual() ? MASTER_MAX_TACTICAL_ICE : MAX_TACTICAL_ICE;
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
        combatState.retreatStartPosition = null;
        combatState.retreatRunTicks = 0;
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
        if (level().isClientSide()) {
            com.frozendawn.entity.architect.ArchitectReconnaissanceFx.tick(this);
            thinkingTiltOld = thinkingTilt;
            thinkingHandOld = thinkingHand;
            boolean thinking = isHoldingMaevePosition() || isShowingReconnaissancePose();
            int pose = entityData.get(DATA_PURSUIT_POSE);
            boolean allowed = getCurrentAction() == ACTION_APPROACH && !isMiningBlock()
                    && !hasQueuedScaffoldStep() && !isMasterArchitectVisual()
                    && isAlive() && !isNoAi() && getDeathTicks() == 0;
            thinkingTilt = net.minecraft.util.Mth.approach(thinkingTilt,
                    thinking || allowed && pose > 0 ? 1.0F : 0.0F, 0.16F);
            thinkingHand = net.minecraft.util.Mth.approach(thinkingHand,
                    thinking || allowed && pose == 2 ? 1.0F : 0.0F, 0.10F);
        } else if (isNoAi() || tickCount < 40 || !isAlive() || getDeathTicks() > 0
                || isMasterArchitectVisual() || isHearthAssessor() || isHearthPopulationResident()
                || AggregateReinforcementManager.isChild(this)) {
            if (maeveCommitment.shield().active()) maeveCommitment.shield().stop("OBSERVER_UNAVAILABLE");
            entityData.set(DATA_PURSUIT_POSE, 0);
            entityData.set(DATA_MAEVE_HOLD, false);
            updateReconnaissancePose(false);
        } else if (combatState.isDrinkingPotion) {
            entityData.set(DATA_PURSUIT_POSE, 0);
            entityData.set(DATA_MAEVE_HOLD, false);
            updateReconnaissancePose(false);
        }
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
        if (!level().isClientSide() && AggregateReinforcementManager.isChild(this)) {
            super.aiStep();
            tickAggregateReinforcement((ServerLevel) level());
            return;
        }
        // NoAI also governs the custom utility AI. Lab actors use it while prepared.
        if (isNoAi()) {
            getNavigation().stop();
            super.aiStep();
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

        if (maeveAttention.tick()) {
            updateReconnaissancePose(true);
            entityData.set(DATA_PURSUIT_POSE, 0);
            updateHeldItem(); syncRenderState(); return;
        }

        // Defensive: fix surfaceY if it wasn't set (NBT load before positioning)
        if (approachState.surfaceY == 0) approachState.surfaceY = blockPosition().getY();
        ensureAmbientHelmet();

        boolean postMaeveHearthResident = isPostMaeveHearthResident(
                (ServerLevel) level());
        if (postMaeveHearthResident) {
            LivingEntity attacker = getLastHurtByMob();
            if (attacker == null || !attacker.isAlive()) {
                prepareHearthAssessmentMode();
                setTarget(null);
                getNavigation().stop();
                updateHeldItem();
                syncRenderState();
                return;
            }
            setTarget(attacker);
        }

        if (!postMaeveHearthResident
                && isHearthAssessor() && level() instanceof ServerLevel serverLevel
                && hearthAssessmentController.tick(serverLevel)) {
            updateHeldItem();
            syncRenderState();
            return;
        }
        if (!postMaeveHearthResident
                && isHearthPopulationResident() && level() instanceof ServerLevel serverLevel
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

        if (maeveReconnaissance.tick(null)) {
            updateReconnaissancePose(true);
            entityData.set(DATA_PURSUIT_POSE, 0); updateHeldItem(); syncRenderState(); return;
        }

        // --- Target acquisition ---
        // Architect senses through blocks — always knows target position
        LivingEntity target = findTarget();
        if (target instanceof ServerPlayer player && maeveReconnaissance.tick(player)) {
            updateReconnaissancePose(true);
            entityData.set(DATA_PURSUIT_POSE, 0); updateHeldItem(); syncRenderState(); return;
        }
        // Roaming after extraction remains a scout task. A different player's combat
        // must retain its actual tools and animation even while the old subject is avoided.
        updateReconnaissancePose(target == null);
        // Roaming selection deliberately does not mutate vanilla's target field.
        // Supply that local candidate to the same perception validator used by the Post hook.
        if (gameTick % 10 == 0 && target instanceof ServerPlayer player && getTarget() != target) MaeveDirector.observePresence(this, player);
        UUID selectedTargetId = target == null ? null : target.getUUID();
        if (decisionJournal.enabled() && !java.util.Objects.equals(lastJournalTargetId, selectedTargetId)) {
            lastJournalTargetId = selectedTargetId;
            recordDecision("TARGET_CHANGE", null, "target=" + selectedTargetId);
        }

        if (target == null) {
            ArchitectApproachRecovery.resetProgress(approachState);
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

        if (decisionJournal.heartbeatDue(gameTick, JOURNAL_HEARTBEAT_TICKS)) {
            recordDecision("STATE", blockBreaker.getChoice(),
                    "target=" + (target == null ? "none" : target.getName().getString())
                            + " targetPos=" + decisionJournal.relative(target == null ? null : target.blockPosition())
                            + " targetHealth=" + (target == null ? "-" : target.getHealth())
                            + " maeveHold=" + isHoldingMaevePosition()
                            + " reconEyes=" + hasReconnaissanceEyes() + " scoutPose=" + isShowingReconnaissancePose()
                            + " mining=" + blockBreaker.isMining()
                            + " collision=" + horizontalCollision
                            + " locked=" + (debugForcedTargetId != null));
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
            // Validate the retained stance even while ordinary combat execution pauses.
            if (maeveCommitment.shield().active()) maeveCommitment.tick(target);
            combatState.drinkTicks++;
            if (combatState.drinkTicks >= DRINK_DURATION) {
                finishDrinking();
            }
            // Fully commits to drinking — no cancellation. Player can punish this.
            syncRenderState();
            return;
        }

        // --- Keep doors and fence gates open while pushing toward a target ---
        if (getBrainAction() == ACTION_APPROACH
                || getBrainAction() == ACTION_ATTACK_MELEE
                || horizontalCollision) {
            keepNearbyPassagesOpen();
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

        boolean respondingToHit = tickCount < thinkingInterruptedUntil;
        if (!respondingToHit && maeveCommitment.tick(target)) {
            entityData.set(DATA_PURSUIT_POSE, 0);
            updateHeldItem();
            syncRenderState();
            return;
        }

        // --- Utility AI scoring ---
        // Don't re-evaluate while actively mining — commit to the block
        // Only interrupt for critical HP (retreat needed)
        boolean miningLock = blockBreaker.isMining()
                && getHealth() > getMaxHealth() * 0.3f;

        brainState.setReevalCooldown(brainState.getReevalCooldown() - 1);
        brainState.setActionHoldTicks(brainState.getActionHoldTicks() + 1);
        if (respondingToHit && target != null && getHealth() > getMaxHealth() * .3F) {
            transitionToAction(canStartMelee(target) ? ACTION_ATTACK_MELEE : ACTION_APPROACH);
        } else if (brainState.getReevalCooldown() <= 0 && !miningLock) {
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
        if (target != null && getBrainAction() != ACTION_OBSERVE) {
            localCombatUntil = level().getGameTime() + 600;
            if (target instanceof ServerPlayer player
                    && (!player.getUUID().equals(lastCombatSubject) || gameTick % 20 == 0)) {
                MaeveDirector.beginLocalCombat(this, player); lastCombatSubject = player.getUUID();
            }
        }
        long actionStart = System.nanoTime();
        executeAction(target);
        long actionUs = (System.nanoTime() - actionStart) / 1000;
        if (actionUs > SLOW_EXEC_ACTION_LOG_US && LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Architect] executeAction({}) took {}us", getBrainAction(), actionUs);
        }

        entityData.set(DATA_PURSUIT_POSE, thinkingController.tick(target,
                getBrainAction() == ACTION_APPROACH && !blockBreaker.hasTarget()
                        && !blockBreaker.isMining() && approachState.scaffoldTarget == null
                        && approachState.stepOffTarget == null && onGround()
                        && !isInWaterOrBubble() && !combatState.isDrinkingPotion
                        && !isMasterArchitectVisual(),
                !approachState.dstar.isSearchComplete()));

        emitActionTelegraphParticles(target);

        setSprinting(shouldSprintRetreat(target) || approachState.sprintRequested);

        updateHeldItem();
        syncRenderState();
    }

    private void tickAggregateReinforcement(ServerLevel level) {
        if (aggregateReinforcementAttackCooldown > 0) {
            aggregateReinforcementAttackCooldown--;
        }
        LivingEntity target = getTarget();
        if (!(target instanceof Player player) || !player.isAlive()
                || player.isCreative() || player.isSpectator()) {
            target = level.getNearestPlayer(
                    getX(), getY(), getZ(), 48.0D,
                    candidate -> candidate instanceof Player nearby
                            && nearby.isAlive()
                            && !nearby.isCreative()
                            && !nearby.isSpectator());
            if (target instanceof Player nearest
                    && !nearest.isCreative() && !nearest.isSpectator()) {
                setTarget(nearest);
            } else {
                setTarget(null);
                getNavigation().stop();
                transitionToAction(ACTION_OBSERVE);
                syncRenderState();
                return;
            }
        }
        double distance = distanceToSqr(target);
        if (distance > 6.25D) {
            transitionToAction(ACTION_APPROACH);
            getNavigation().moveTo(target, 1.18D);
        } else {
            transitionToAction(ACTION_ATTACK_MELEE);
            getNavigation().stop();
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (aggregateReinforcementAttackCooldown <= 0
                    && getSensing().hasLineOfSight(target)) {
                swing(InteractionHand.MAIN_HAND);
                doHurtTarget(target);
                aggregateReinforcementAttackCooldown = 18;
            }
        }
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

        var beliefBias = target instanceof net.minecraft.server.level.ServerPlayer player
                ? com.frozendawn.maeve.MaeveDirector.utilityBias(this, player)
                : com.frozendawn.maeve.MaeveDirector.UtilityBias.NONE;
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
                        getMaxTacticalIce(),
                        trapCooldown,
                        !observationMemory.entrancePositions().isEmpty(),
                        target != null && isPlayerInsideBase(target),
                        target != null && isNearCorner()
                ),
                random, beliefBias.fortify(), beliefBias.peek(), tacticsController.canFortify(), tacticsController.canPeek()
        );
        int bestAction = decision.bestAction();
        float[] scores = decision.scores();

        if (bestAction != getBrainAction()) {
            if (bestAction == ACTION_FORTIFY) recordDecision("FORTIFY_SELECTED", null, "historicalBias=" + beliefBias.fortify());
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

    void setCommitmentAction(boolean holding) {
        transitionToAction(holding ? ACTION_OBSERVE : ACTION_APPROACH);
    }

    /** Synced vanilla item use drives the actual shield animation on clients. */
    public boolean isUsingMaeveShield() {
        return isUsingItem() && ArchitectShieldController.generated(getUseItem());
    }

    @Override
    protected void hurtCurrentlyUsedShield(float amount) {
        if (maeveCommitment.shield().active() && !damageContainers.isEmpty()) {
            var damage = damageContainers.peek();
            maeveCommitment.shield().blocked(damage.getSource(), damage.getBlockedDamage());
            maeveCommitment.shield().wear(amount);
        } else super.hurtCurrentlyUsedShield(amount);
    }

    @Override
    protected void blockUsingShield(LivingEntity attacker) {
        super.blockUsingShield(attacker);
        maeveCommitment.shield().contact(attacker);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Brace only a fully blocked MACS hit. Keep the transaction scoped through
        // shield break/disable, which can remove the item before vanilla knockback.
        if (maeveShieldDamageInProgress && !damageContainers.isEmpty()) {
            var damage = damageContainers.peek();
            if (damage.getBlockedDamage() > 0 && damage.getNewDamage() <= 0) return;
        }
        super.knockback(strength, x, z);
    }

    /** Erasure releases local execution in the same server-thread transition. */
    public void clearMaevePositioning() {
        maeveCommitment.clear();
    }

    public void beginMaeveDisengagement(UUID player, BlockPos observed, String concern) {
        maeveAttention.begin(player, observed, concern);
    }

    public boolean isMaeveDisengaging() { return maeveAttention.active(); }
    public void clearMaeveAttention() { maeveAttention.clear(); }
    public void clearMaeveReconnaissance() { maeveReconnaissance.clear(); }
    public boolean canBeginMaeveReconnaissance() { return level().getGameTime() >= localCombatUntil; }
    void resumeAfterReconnaissance() {
        observationMemory.setHasObserved(true); observationMemory.setObserveDirty(false);
        transitionToAction(ACTION_APPROACH);
        brainState.setActionHoldTicks(0); brainState.setReevalCooldown(0);
    }

    /** Final damage has already been recorded by Maeve before execution is released. */
    public void onEffectiveCombatDamage(DamageSource source, float damage) {
        if (level().isClientSide() || !(damage > 0) || !Float.isFinite(damage)
                || isMasterArchitectVisual() || isHearthAssessor() || isHearthPopulationResident()
                || AggregateReinforcementManager.isChild(this)) return;
        localCombatUntil = level().getGameTime() + 600;
        if (source.getEntity() instanceof ServerPlayer player) MaeveDirector.beginLocalCombat(this, player);
        boolean interrupted = isHoldingMaevePosition() || hasReconnaissanceEyes()
                || getBrainAction() == ACTION_OBSERVE || entityData.get(DATA_PURSUIT_POSE) != 0;
        boolean committed = MaeveDirector.positionDirective(this) != null;
        boolean shieldStagger = maeveCommitment.shield().staggerAfterDamage(source);
        if (!shieldStagger) MaeveDirector.releaseCommitment(this, isAlive() ? "LOCAL_DEFENSE" : "OWNER_KILLED");
        MaeveDirector.finishMission(this, "LOCAL_DEFENSE", false);
        if (!shieldStagger && (committed || isHoldingMaevePosition())) maeveCommitment.clear();
        if (maeveAttention.active()) maeveAttention.clear();
        thinkingController.interrupt(); entityData.set(DATA_PURSUIT_POSE, 0);
        if (interrupted && !combatState.isDrinkingPotion && isAlive()) {
            thinkingInterruptedUntil = tickCount + 40;
            observationMemory.setHasObserved(true); observationMemory.setObserveDirty(false);
            transitionToAction(ACTION_APPROACH);
            brainState.setActionHoldTicks(0); brainState.setReevalCooldown(0);
            recordDecision("THINKING_INTERRUPTED", null, "effectiveDamage=" + damage + " local defense");
            updateHeldItem(); syncRenderState();
        }
    }

    void cancelMaeveAttentionWork() {
        maeveCommitment.clear(); blockBreaker.clearTarget();
        approachState.scaffoldTarget = null; approachState.scaffoldDelay = 0;
        approachState.dstar.cleanup(); approachState.dstarPrecomputed = false;
        approachState.sprintRequested = false; brainState.setMeleeCommitTicks(0);
        getNavigation().stop(); setTarget(null); observationController.enterRoamModeAfterTargetLoss();
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

    boolean tryApproachProgressRecovery(LivingEntity target) {
        return walkSupport.tryProgressRecovery(target);
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

    boolean canCommitToMelee(LivingEntity target) {
        boolean allowed = ArchitectMeleeEngagement.canCommitToMelee(
                this,
                target,
                hasLineOfSight(target),
                getNavigation(),
                MELEE_COMMIT_HORIZONTAL_RANGE,
                MELEE_COMMIT_VERTICAL_RANGE,
                MELEE_COMMIT_LOS_GRACE_RANGE,
                2.25,
                1.25);
        if (decisionJournal.visual().enabled()) {
            meleeDebugObservation = new MeleeDebugObservation(level().getGameTime(), target.getUUID(), allowed);
        }
        return allowed;
    }

    void clearWalkNavigationState(boolean stopNavigation) {
        walkSupport.clearWalkNavigationState(stopNavigation);
    }

    void keepNearbyPassagesOpen() {
        ArchitectBlockEnvironment.keepNearbyPassagesOpen(this);
    }

    void keepPassageOpenNear(BlockPos center) {
        ArchitectBlockEnvironment.keepPassageOpenNear(this, center);
    }

    boolean isBreakableBlock(BlockPos pos) { return breakRejection(pos) == null; }

    public boolean isOwnedScaffold(BlockPos pos) { return scaffoldIce.contains(pos); }

    /** A planned descent may reclaim a column one block at a time, never open a fall. */
    public boolean canReclaimScaffold(BlockPos pos) {
        if (!level().getBlockState(pos).is(Blocks.PACKED_ICE)) return false;
        BlockPos landing = pos.below();
        var support = level().getBlockState(landing);
        return support.isFaceSturdy(level(), landing, Direction.UP)
                && support.getFluidState().isEmpty()
                && !support.is(Blocks.MAGMA_BLOCK) && !support.is(Blocks.CAMPFIRE)
                && !support.is(Blocks.SOUL_CAMPFIRE) && !support.is(Blocks.CACTUS)
                && ArchitectBlockEnvironment.breakRejection(level(), pos, java.util.List.of()) == null;
    }

    boolean canExecutePlannedBreak(DStarLitePathfinder.NextStep step) {
        BlockPos pos = step.breakTarget();
        if (pos == null) return false;
        if (isBreakableBlock(pos)) return true;
        return "OWN_SCAFFOLD".equals(breakRejection(pos)) && canReclaimScaffold(pos);
    }

    public void onScaffoldReclaimed(BlockPos pos) {
        if (scaffoldIce.remove(pos)) {
            recordDecision("SCAFFOLD_RECLAIM", new BreakChoice(pos, BreakReason.SCAFFOLD), "DESTROYED");
        }
    }


    @Nullable String breakRejection(BlockPos pos) {
        if (getBrainAction() == ACTION_APPROACH && !ArchitectApproachRecovery.canAttemptBreak(approachState, pos)) {
            return approachState.blockedUnstickBreakCandidates.contains(pos) ? "BLACKLISTED" : "RETRY_BUDGET_EXHAUSTED";
        }
        return ArchitectBlockEnvironment.breakRejection(level(), pos, scaffoldIce);
    }

    public ArchitectDecisionJournal decisionJournal() { return decisionJournal; }
    public com.frozendawn.debug.architect.ArchitectDebugSnapshot debugSnapshot(
            com.frozendawn.debug.architect.ArchitectDebugSnapshot.Lab lab, boolean frozen) {
        return ArchitectDebugSnapshotFactory.capture(this, approachState, combatState, blockBreaker, debugStep, meleeDebugObservation, lab, frozen);
    }
    public long successfulBreakCount() { return blockBreaker.successfulBreakCount(); }
    public void startDecisionRecording(@Nullable Long seed) {
        startDecisionRecording(UUID.randomUUID(), seed, net.minecraft.world.level.block.Rotation.NONE);
    }
    public void startDecisionRecording(UUID runId, @Nullable Long seed, net.minecraft.world.level.block.Rotation rotation) {
        lastJournalTargetId = null;
        decisionJournal.start(runId, level().getGameTime(), blockPosition(), successfulBreakCount(), rotation);
        lastDebugStepTick = lastCandidateTick = level().getGameTime();
        lastCandidateSignature = "";
        debugStep = null;
        if (seed != null) { random.setSeed(seed); debugSeed = seed; }
        recordDecision("RECORD_START", null, "seed=" + debugSeed);
    }
    /** Releases the lab target lock so normal targeting resumes. */
    public void clearDebugTargetLock() {
        debugForcedTargetId = null;
    }
    public void recordDecision(String event, @Nullable BreakChoice choice, String detail) {
        if (level().isClientSide) return;
        decisionJournal.visual().event(level().getGameTime(), event, detail,
                choice == null ? null : ArchitectDebugSnapshotFactory.point(choice.pos()),
                choice == null ? "" : choice.reason().name());
        if (!decisionJournal.enabled()) return;
        decisionJournal.append(new ArchitectDecisionJournal.Entry(level().getGameTime(), event,
                actionName(getBrainAction()), blockPosition().immutable(), debugStep == null ? null : debugStep.pos(),
                debugStep == null ? "-" : debugStep.type().name(), approachState.committedWalkWaypoint,
                choice, approachState.approachNoProgressTicks, approachState.blockedUnstickBreakCandidates.size(),
                approachState.unstickReinitAttempts, successfulBreakCount(), detail));
    }
    void recordStep(DStarLitePathfinder.NextStep step) {
        boolean changed = !step.equals(debugStep);
        debugStep = step;
        if (changed || level().getGameTime() - lastDebugStepTick >= 40) {
            lastDebugStepTick = level().getGameTime();
            recordDecision("PLAN_STEP", step.breakChoice(), "searchComplete=" + approachState.dstar.isSearchComplete()
                    + " goal=" + approachState.dstar.debugState().goal());
        }
    }
    @Nullable BreakChoice chooseBreak(String source, java.util.List<BreakChoice> choices,
            java.util.function.Predicate<BlockPos> lastResort) {
        java.util.List<ArchitectWalkBreakPlanner.CandidateDecision> trace = (decisionJournal.enabled() || decisionJournal.visual().enabled())
                ? new java.util.ArrayList<>() : null;
        BreakChoice chosen = ArchitectWalkBreakPlanner.selectChoice(choices,
                approachState.blockedUnstickBreakCandidates, this::breakRejection, lastResort,
                d -> { if (trace != null) trace.add(d); });
        if (trace != null) {
            String signature = source + trace;
            if (!signature.equals(lastCandidateSignature) || level().getGameTime() - lastCandidateTick >= 40) {
                lastCandidateSignature = signature;
                lastCandidateTick = level().getGameTime();
                for (var d : trace) recordDecision("CANDIDATE", d.candidate(), source + ":" + d.outcome());
                recordDecision("BREAK_CHOICE", chosen, source);
            }
        }
        return chosen;
    }
    public String inspectDecisions() {
        return "Architect " + getId() + " " + getUUID() + " action=" + actionName(getBrainAction())
                + " pos=" + blockPosition() + " lastPlannedStep=" + debugStep
                + " waypoint=" + approachState.committedWalkWaypoint + " mining=" + blockBreaker.getChoice()
                + " noProgress=" + approachState.approachNoProgressTicks
                + " exclusions=" + approachState.blockedUnstickBreakCandidates
                + " reinits=" + approachState.unstickReinitAttempts + " destroyed=" + successfulBreakCount()
                + " recording=" + decisionJournal.enabled() + " retained=" + decisionJournal.entries().size()
                + " dropped=" + decisionJournal.dropped() + " seed=" + debugSeed
                + " targetLock=" + debugForcedTargetId;
    }

    // ========================
    //  Lab harness (operator-only, never called by AI)
    // ========================

    /** One line per Architect for {@code /fd debug architect list}. */
    public String labSummary() {
        LivingEntity target = getTarget();
        return "#" + getId() + " " + actionName(getBrainAction()) + " at " + blockPosition()
                + " hp=" + (int) getHealth() + "/" + (int) getMaxHealth()
                + " target=" + (target == null ? "none" : "#" + target.getId() + " " + target.getName().getString())
                + " recording=" + decisionJournal.enabled();
    }

    /**
     * Forces a live APPROACH at {@code target}, matching what game tests do through an NBT
     * round trip. Clears retry suppression and every recovery budget so the run starts clean.
     */
    public void debugForceApproach(LivingEntity target) {
        setPersistenceRequired();
        observationMemory.setHasObserved(true);
        observationMemory.setObserveDirty(false);
        debugResetApproach();
        debugForcedTargetId = target.getUUID();
        setTarget(target);
        brainState.setReevalCooldown(0);
        transitionToAction(ACTION_APPROACH);
        brainState.setActionHoldTicks(0);
        setNoAi(false);
        setNoGravity(false);
        recordDecision("LAB_APPROACH", null, "target=locked_target");
    }

    /** Drops planner state, recovery budgets, the break blacklist and any retry suppression. */
    public void debugResetApproach() {
        debugForcedTargetId = null;
        blockBreaker.clearTarget();
        ArchitectApproachRecovery.resetProgress(approachState);
        approachState.abandonedApproachTarget = null;
        approachState.approachRetryAfterTick = 0;
        approachState.scaffoldTarget = null;
        approachState.scaffoldDelay = 0;
        approachState.dstar.cleanup();
        approachState.dstarPrecomputed = false;
        approachState.sprintRequested = false;
        approachState.unreachableTicks = 0;
        approachState.ceilingBreachPos = null;
        approachState.stepOffStart = null;
        approachState.stepOffTarget = null;
        approachState.stepOffProgress = 0;
        approachState.lastFallbackBreakPos = null;
        approachState.fallbackBreakCooldown = 0;
        approachState.dstarApproachEntryLogged = false;
        approachState.dstarObserveHandoffLogged = false;
        clearWalkNavigationState(true);
        clearCommittedWalk();
        getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0);
        setDeltaMovement(Vec3.ZERO);
        setSpeed(0);
        setJumping(false);
        setTarget(null);
        brainState.setMeleeCommitTicks(0);
        brainState.setReevalCooldown(0);
        pathRecalcCooldown = 0;
        com.frozendawn.entity.architect.ArchitectWalkTracking.resetWalkStuckTracker(approachState);
        com.frozendawn.entity.architect.ArchitectWalkTracking.resetWalkCellHistory(approachState);
        debugStep = null;
        lastCandidateSignature = "";
        recordDecision("LAB_RESET", null, "");
    }

    private void onApproachBreakAttemptFinished(BlockPos pos) {
        if (getBrainAction() != ACTION_APPROACH) {
            return;
        }
        boolean obstructing = ArchitectBlockEnvironment.isPathObstructingState(level(), level().getBlockState(pos), pos);
        if (ArchitectApproachRecovery.finishBreakAttempt(approachState, pos, obstructing)) {
            LOGGER.info("[Architect] APPROACH_BREAK_FAILED entity={} candidate={} excluded={}",
                    getId(), pos, approachState.blockedUnstickBreakCandidates.size());
        }
    }

    public boolean isApproachTargetSuppressed(LivingEntity target) {
        return maeveAttention.suppresses(target.getUUID())
                || ArchitectApproachRecovery.isTargetSuppressed(approachState, target.getUUID(), tickCount);
    }

    public int approachRetryTicksRemaining() {
        return Math.max(0, approachState.approachRetryAfterTick - tickCount);
    }

    /** Dispose only lab-owned actors before restoring their fixture. */
    public void discardLabActor() {
        if (!getTags().contains("fd_lab")) throw new IllegalStateException("Not a lab actor");
        blockBreaker.clearTarget();
        cleanupAllIce();
        approachState.dstar.cleanup();
        discard();
    }

    void abandonApproach(LivingEntity target, String reason) {
        recordDecision("ABANDON", blockBreaker.getChoice(), reason);
        LOGGER.info("[Architect] APPROACH_ABANDON entity={} reason={} target={} pos={} noProgressTicks={} reinits={} retryAfterTicks={}",
                getId(), reason, target.getUUID(), blockPosition(), approachState.approachNoProgressTicks,
                approachState.unstickReinitAttempts, ArchitectApproachRecovery.TARGET_RETRY_COOLDOWN_TICKS);
        blockBreaker.clearTarget();
        ArchitectApproachRecovery.abandon(approachState, target.getUUID(), tickCount);
        approachState.scaffoldTarget = null;
        approachState.scaffoldDelay = 0;
        approachState.dstar.cleanup();
        approachState.dstarPrecomputed = false;
        approachState.sprintRequested = false;
        brainState.setMeleeCommitTicks(0);
        setTarget(null);
        transitionToAction(ACTION_OBSERVE);
        observationController.enterRoamModeAfterTargetLoss();
        observationController.executeRoamAndRuin();
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
        Vec3 supported = com.frozendawn.entity.architect.ArchitectCombatFooting.constrain(this, blended);
        if (supported.distanceToSqr(blended) > 1.0e-8 && tickCount % 20 == 0) {
            recordDecision("COMBAT_FOOTING", null, "limited=" + blended + " allowed=" + supported);
        }
        setDeltaMovement(supported.x, current.y, supported.z);
    }

    // ========================
    //  ACTION TRANSITIONS
    // ========================

    private void onActionChange(int oldAction, int newAction) {
        recordDecision("ACTION_CHANGE", blockBreaker.getChoice(), actionName(oldAction) + "->" + actionName(newAction));
        debugStep = null;
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
        if (oldAction != ACTION_APPROACH) blockBreaker.clearTarget();
        if (oldAction == ACTION_RETREAT) {
            if (combatState.isDrinkingPotion) cancelDrinking();
            ArchitectActionTransitionSupport.onLeaveRetreat(combatState);
        }
        if (newAction == ACTION_RETREAT) {
            maeveCommitment.shield().suspend("RETREAT");
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
            recordDecision("MELEE_HIT", null, "target=" + target.getUUID() + " health=" + living.getHealth());
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
        boolean previousShieldDamage = maeveShieldDamageInProgress;
        maeveShieldDamageInProgress = isUsingMaeveShield();
        try {
            hurt = super.hurt(source, amount);
        } finally {
            maeveShieldDamageInProgress = previousShieldDamage;
            suppressMasterHurtSound = false;
        }
        if (hurt && !level().isClientSide()) {
            recordDecision("DAMAGE", blockBreaker.getChoice(),
                    "type=" + source.getMsgId() + " amount=" + amount + " health=" + getHealth());
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
        if (!level().isClientSide() && maeveCommitment.shield().active()) maeveCommitment.shield().stop("OWNER_KILLED");
        if (isMasterMindCopy()) {
            mindCopyDeathSource = source;
            super.die(source);
            updateMasterBossBarProgress();
            return;
        }
        boolean masterArchitect = isHearthMasterArchitect();
        if (masterArchitect && source.getEntity() instanceof ServerPlayer killer) {
            masterDeathKillerId = killer.getUUID();
        }
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
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel) {
            hearthMasterController.onDeath(
                    serverLevel,
                    source.getEntity() instanceof ServerPlayer killer ? killer : null);
        }
        if (!masterArchitect && !level().isClientSide()
                && source.getEntity() instanceof ServerPlayer killer) {
            WorldTickHandler.grantAdvancement(killer, "disassembled");
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

    boolean isDrinkingPotion() { return combatState.isDrinkingPotion; }

    void startDrinking() {
        maeveCommitment.shield().suspend("DRINKING");
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
            recordDecision("SCAFFOLD_PLACE", new BreakChoice(pos, BreakReason.SCAFFOLD), "placed");
            emitIcePlacementFx(pos);
            return true;
        }
        return false;
    }

    boolean placeTacticalIce(BlockPos pos) {
        BlockPos evicted = tacticalIce.size() >= getMaxTacticalIce() ? tacticalIce.getFirst() : null;
        if (ArchitectIcePlacement.placeTacticalIce(
                level(),
                pos,
                tacticalIce,
                getMaxTacticalIce())) {
            // setBlock is not a player placement event. Update the cached route
            // explicitly so the next approach knows about our own construction.
            approachState.dstar.onLocalBlockChanged(pos, level());
            if (evicted != null) approachState.dstar.onLocalBlockChanged(evicted, level());
            emitIcePlacementFx(pos);
            return true;
        }
        return false;
    }

    /** Bow-defense pillar only; retreat keeps its existing construction path. */
    int placeCoverPillar(BlockPos pos) {
        if (!com.frozendawn.entity.architect.ArchitectCoverGeometry.canPlacePillar(this, position(), pos)) return 0;
        int height = com.frozendawn.entity.architect.ArchitectCoverGeometry.PILLAR_HEIGHT;
        int placed = 0;
        for (int y = 0; y < height; y++) {
            if (!placeTacticalIce(pos.above(y))) break;
            placed++;
        }
        return placed;
    }

    /** Mantlets have their own finite allowance; ordinary pillars and retreat keep theirs. */
    boolean placeMantletIce(BlockPos pos) {
        if (mantletIce.size() >= ArchitectMantletGeometry.PLACEMENTS
                || !ArchitectIcePlacement.placeTacticalIce(level(), pos, mantletIce, ArchitectMantletGeometry.PLACEMENTS)) return false;
        approachState.dstar.onLocalBlockChanged(pos, level());
        emitIcePlacementFx(pos);
        return true;
    }

    /** Retire only this executor's tracked mantlet ice; consumed budget is not refunded. */
    void retireMantletIce(BlockPos pos, net.minecraft.world.level.block.state.BlockState previous) {
        if (!mantletIce.contains(pos) || !level().hasChunkAt(pos) || !level().getBlockState(pos).is(Blocks.PACKED_ICE)) return;
        level().levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(level().getBlockState(pos)));
        level().setBlock(pos, previous != null && previous.is(Blocks.SNOW) ? previous : Blocks.AIR.defaultBlockState(), 3);
        approachState.dstar.onLocalBlockChanged(pos, level());
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
        ArchitectIcePlacement.cleanupAllIce(level(), scaffoldIce, mantletIce);
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
     * Called when a player changes a block near the Architect.
     * Threshold: 5+ changes within 16 blocks of the last observed position, each within
     * NEARBY_CHANGE_WINDOW_TICKS of the previous one. The count is tracked here rather than
     * passed in so callers cannot forget to maintain it.
     */
    public void onNearbyBlockChange(BlockPos changedPos) {
        BlockPos lastObservedPos = observationMemory.getLastObservedPos();
        if (ArchitectObservationSupport.isWithinObserveRadius(lastObservedPos, changedPos)) {
            int changeCount = ArchitectObservationSupport.accumulateNearbyChange(
                    observationMemory.getNearbyChangeCount(),
                    observationMemory.getLastNearbyChangeTick(),
                    tickCount);
            observationMemory.recordNearbyChange(changeCount, tickCount);
            if (ArchitectObservationSupport.shouldMarkObserveDirty(lastObservedPos, changedPos, changeCount)) {
                observationMemory.setObserveDirty(true);
            }
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
        LivingEntity candidate = findTargetIgnoringApproachCooldown();
        return candidate != null && isApproachTargetSuppressed(candidate) ? null : candidate;
    }

    @Nullable
    private LivingEntity findTargetIgnoringApproachCooldown() {
        if (debugForcedTargetId != null && level() instanceof ServerLevel lockLevel) {
            net.minecraft.world.entity.Entity locked = lockLevel.getEntity(debugForcedTargetId);
            if (locked instanceof LivingEntity lockedLiving && lockedLiving.isAlive()) {
                if (maeveAttention.suppresses(lockedLiving.getUUID())) return null;
                return lockedLiving;
            }
            debugForcedTargetId = null; // Locked entity is gone — fall back to normal targeting.
        }
        LivingEntity current = getTarget();
        LivingEntity directAttacker = getLastHurtByMob();
        if ((directAttacker instanceof UndoneEntity
                || directAttacker instanceof UndoneArchitectEntity)
                && directAttacker.isAlive()) {
            return directAttacker;
        }
        if (level() instanceof ServerLevel serverLevel
                && isPostMaeveHearthResident(serverLevel)) {
            return directAttacker != null && directAttacker.isAlive()
                    ? directAttacker : null;
        }
        UUID hearthId = isHearthAssessor()
                ? getHearthAssessorId().orElse(null)
                : getHearthPopulationId().orElse(null);
        if (HeartScavengerWaveManager.isHeartScavenger(current, hearthId)) {
            return current;
        }
        if (isHearthAssessor() && level() instanceof ServerLevel serverLevel) {
            return hearthAssessmentController.findHostileTarget(serverLevel);
        }
        if (isHearthPopulationResident() && level() instanceof ServerLevel serverLevel) {
            return hearthResidentController.findHostileTarget(serverLevel);
        }
        if (isHearthMasterArchitect() && level() instanceof ServerLevel serverLevel) {
            return hearthMasterController.findHostileTarget(serverLevel);
        }
        if (level() instanceof ServerLevel serverLevel) {
            return findRoamingTarget(serverLevel);
        }
        return villagerFallback();
    }

    /**
     * Target selection for the ordinary roaming Architect.
     *
     * <p>Distance changes every tick, so picking the nearest player each tick
     * makes the Architect swap which player it is stalking mid-observe — the
     * approach destination jitters and the remembered player position
     * ping-pongs. This commits to one player instead, and picks the weakest
     * rather than the nearest, so a naturally spawned Architect walks past the
     * armored player to stalk the unarmored one and then stays on them.
     */
    @Nullable
    private LivingEntity findRoamingTarget(ServerLevel serverLevel) {
        double range = brainState.isRoamingAfterTargetLoss()
                ? OBSERVE_REACQUIRE_RANGE
                : getDetectionRange();
        double rangeSquared = range * range;
        UUID previousCommitted = roamingCommitment.committedId();
        UUID previousSuspended = roamingCommitment.suspendedId();

        // Retaliation outranks scoring: whoever just hit us takes the commitment.
        if (getLastHurtByMob() instanceof Player attacker
                && ArchitectTargetingSupport.isTargetablePlayer(attacker)
                && !isApproachTargetSuppressed(attacker)
                && distanceToSqr(attacker) <= rangeSquared) {
            roamingCommitment.commitToTarget(attacker.getUUID());
            logRoamingCommitment(serverLevel, attacker, "RETALIATION",
                    previousCommitted, range, -1);
            return attacker;
        }

        List<ServerPlayer> inRange = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        for (ServerPlayer player : serverLevel.players()) {
            if (ArchitectTargetingSupport.isTargetablePlayer(player)
                    && !isApproachTargetSuppressed(player)
                    && distanceToSqr(player) <= rangeSquared) {
                inRange.add(player);
                candidates.add(new Candidate(player.getUUID(), player.getArmorValue(),
                        distanceToSqr(player)));
            }
        }
        boolean previousStillInRange = previousCommitted != null
                && inRange.stream().anyMatch(p -> p.getUUID().equals(previousCommitted));
        // A recovery cooldown is not a distance departure: forget the old commitment
        // instead of bookmarking it and starving another eligible player. Apply the
        // same eligibility rule to scoring, presence, and remembered retaliation.
        var targetableIds = ArchitectTargetingSupport.targetablePlayerIds(serverLevel);
        targetableIds.removeIf(id -> maeveAttention.suppresses(id) || ArchitectApproachRecovery.isTargetSuppressed(
                approachState, id, tickCount));
        UUID targetId = roamingCommitment.resolve(candidates, targetableIds);
        if (targetId != null) {
            for (ServerPlayer player : inRange) {
                if (player.getUUID().equals(targetId)) {
                    logRoamingCommitment(serverLevel, player,
                            roamingSwitchReason(targetId, previousCommitted,
                                    previousSuspended, previousStillInRange),
                            previousCommitted, range, candidates.size());
                    return player;
                }
            }
        }
        loggedRoamingTargetId = null;
        return villagerFallback();
    }

    /**
     * Debug: why the roaming commitment moved off the previous target.
     *
     * <p>{@code PREV_LEFT_RANGE} and {@code OUTSCORED} are the two that matter:
     * the first means the candidate list dropped the committed player, the
     * second means {@code warrantsRetarget} genuinely fired on an armor gap.
     */
    private static String roamingSwitchReason(UUID targetId, @Nullable UUID previousCommitted,
            @Nullable UUID previousSuspended, boolean previousStillInRange) {
        if (previousCommitted == null) {
            return previousSuspended == null ? "FIRST_PICK" : "REPICK_AFTER_SUSPEND";
        }
        if (targetId.equals(previousCommitted)) {
            return "HELD";
        }
        if (targetId.equals(previousSuspended)) {
            return "BOOKMARK_RECLAIM";
        }
        return previousStillInRange ? "OUTSCORED" : "PREV_LEFT_RANGE";
    }

    /**
     * Debug: one line each time the roaming commitment moves to a different player,
     * so a two-player armor test can read the pick out of the log instead of
     * inferring it from which way the Architect walks.
     *
     * <p>Logs the previous target's current distance and the range actually in
     * force, since those are what decide whether the committed player stayed in
     * the candidate list.
     */
    private void logRoamingCommitment(ServerLevel serverLevel, Player player, String reason,
            @Nullable UUID previousId, double range, int candidateCount) {
        if (player.getUUID().equals(loggedRoamingTargetId)) {
            return;
        }
        loggedRoamingTargetId = player.getUUID();
        Player previous = previousId == null ? null : serverLevel.getPlayerByUUID(previousId);
        LOGGER.info(
                "Roaming Architect {} -> {} | armor={} dist={} | reason={} prev={} prevDist={} range={} candidates={}",
                getUUID().toString().substring(0, 8), player.getName().getString(),
                player.getArmorValue(), String.format("%.1f", distanceTo(player)),
                reason,
                previous == null ? "none" : previous.getName().getString(),
                previous == null ? "n/a" : String.format("%.1f", distanceTo(previous)),
                String.format("%.0f", range),
                candidateCount < 0 ? "n/a" : Integer.toString(candidateCount));
    }

    /** No player in range: fall back to the nearest villager, as before. */
    @Nullable
    private LivingEntity villagerFallback() {
        return ArchitectTargetingSupport.findTarget(
                level(),
                this,
                brainState.isRoamingAfterTargetLoss(),
                getDetectionRange(),
                OBSERVE_REACQUIRE_RANGE,
                this::distanceToSqr,
                candidate -> !isApproachTargetSuppressed(candidate));
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
        if (level() instanceof ServerLevel serverLevel
                && isHearthMasterArchitect()
                && hearthMasterArchitectId != null) {
            HearthMasterArchitectManager.recordDefeat(
                    serverLevel, hearthMasterArchitectId, getUUID(), masterDeathKillerId);
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
        masterBossBarProvoked = provoked;
        if (masterBossEvent == null && !provoked) {
            return;
        }
        ServerBossEvent bossEvent = getOrCreateMasterBossEvent();
        bossEvent.setProgress(masterBossBarProgress());
        bossEvent.setVisible(provoked);
    }

    public boolean isMasterFightActive() {
        return isMasterArchitectVisual() && masterBossBarProvoked;
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
        if (level() instanceof ServerLevel serverLevel
                && isPostMaeveHearthResident(serverLevel)) {
            return null;
        }
        return isMasterArchitectVisual()
                ? ModSounds.MASTER_ARCHITECT_AMBIENT.get()
                : ModSounds.ARCHITECT_AMBIENT.get();
    }

    private boolean isPostMaeveHearthResident(ServerLevel level) {
        return (isHearthAssessor() || isHearthPopulationResident())
                && PostMaeveWorldState.isErased(level);
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
        tag.putLong("LocalCombatUntil", localCombatUntil);
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
        ArchitectPersistence.putBlockPosList(tag, "MantletIce", mantletIce);
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
        if (masterDeathKillerId != null) {
            tag.putUUID("MasterDeathKillerId", masterDeathKillerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        maeveCommitment.shield().clear();
        maeveAttention.clear(); maeveReconnaissance.clear();
        localCombatUntil = Math.min(tag.getLong("LocalCombatUntil"), level().getGameTime() + 600);
        ArchitectPersistence.CoreState coreState = ArchitectPersistence.readCoreState(tag);
        setTextureVariant(coreState.textureVariant());
        despawnTimer = coreState.despawnTimer();
        setBrainAction(coreState.currentAction());
        if (!tag.contains("LocalCombatUntil") && coreState.currentAction() != ACTION_OBSERVE)
            localCombatUntil = level().getGameTime() + 600;
        // Delay first pathfinding after world load to prevent freeze
        pathRecalcCooldown = 40;
        brainState.setReevalCooldown(40);
        brainState.setActionHoldTicks(0);
        ArchitectPersistence.readObservationMemory(tag, observationMemory);
        ArchitectPersistence.readCombatState(tag, combatState);
        ArchitectPersistence.readApproachState(tag, approachState, scaffoldIce, tacticalIce);
        mantletIce.clear();
        var savedMantlet = tag.getList("MantletIce", net.minecraft.nbt.Tag.TAG_LONG);
        for (int i = 0; i < Math.min(savedMantlet.size(), ArchitectMantletGeometry.PLACEMENTS); i++)
            mantletIce.add(BlockPos.of(((net.minecraft.nbt.LongTag) savedMantlet.get(i)).getAsLong()));
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
        masterDeathKillerId = tag.hasUUID("MasterDeathKillerId")
                ? tag.getUUID("MasterDeathKillerId") : null;
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
        if (!level().isClientSide() && getServer() != null) {
            com.frozendawn.maeve.MaeveDirector.releaseCommitment(this, reason == RemovalReason.KILLED ? "OWNER_KILLED" : "OWNER_UNAVAILABLE");
            MaeveDirector.finishMission(this, "OWNER_REMOVED", false);
            maeveCommitment.clear();
        }
        if (masterBossEvent != null) {
            masterBossEvent.removeAllPlayers();
        }
        super.remove(reason);
    }
}
