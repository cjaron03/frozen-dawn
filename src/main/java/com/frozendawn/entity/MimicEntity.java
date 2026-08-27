package com.frozendawn.entity;

import com.frozendawn.entity.ai.MimicCombatGoal;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.homo.HearthCombatRosterManager;
import com.frozendawn.homo.HearthMemoryManager;
import com.frozendawn.homo.HearthPopulationPolicy;
import com.frozendawn.homo.HearthPopulationRole;
import com.frozendawn.homo.HearthTransmissionManager;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class MimicEntity extends Monster {

    public static final int PHASE_OBSERVATION = 0;
    public static final int PHASE_MIMICRY = 1;
    public static final int PHASE_COMBAT = 2;
    public static final int PHASE_RETREAT = 3;
    public static final int PHASE_BURROW = 4;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_OBSERVATION_TICKS =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_TARGET_YAW =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> DATA_MIMIC_TARGET =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_MIMICRY_TICKS =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BURROW_TICKS =
            SynchedEntityData.defineId(MimicEntity.class, EntityDataSerializers.INT);

    private int despawnTimer = 0;
    private static final int DESPAWN_TIMEOUT = 3600; // 3 minutes

    private int observationDuration; // 200-300 ticks (randomized on spawn)
    private int mimicryTimer = 0;
    public static final int MIMICRY_DURATION = 100; // 5 seconds

    private Vec3 recordedVelocity = Vec3.ZERO;

    private boolean hasLandedFirstHit = false;

    private int retreatTimer = 0;
    private static final int RETREAT_DURATION = 200; // 10 seconds

    private int losTimer = 0; // line-of-sight loss timer
    private static final int LOS_RESET_TICKS = 300; // 15 seconds

    private boolean engaged = false; // whether the mimic has ever entered combat

    private int burrowTimer = 0;
    private BlockPos burrowSurfacePos = null;
    private BlockState burrowCoverBlock = null;

    private int stareSoundCooldown = 0;
    @Nullable
    private UUID hearthPopulationId;
    @Nullable
    private BlockPos hearthPopulationHome;
    private int hearthLostTargetTicks;

    public MimicEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.observationDuration = 400 + level.random.nextInt(201); // 400-600 ticks (20-30 sec)
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_PHASE, PHASE_OBSERVATION);
        builder.define(DATA_OBSERVATION_TICKS, 0);
        builder.define(DATA_TARGET_YAW, 0.0f);
        builder.define(DATA_MIMIC_TARGET, Optional.empty());
        builder.define(DATA_MIMICRY_TICKS, 0);
        builder.define(DATA_BURROW_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MimicCombatGoal(this));
        this.goalSelector.addGoal(5, new RandomStrollGoal(this, 0.8, 60));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 48.0f) {
            @Override
            public boolean canUse() {
                return !isPostMaeveHearthResident() && super.canUse();
            }
        });

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // Only target players when in Phase 1+ (not during observation)
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true, this::canProactivelyTargetPlayer) {
            @Override
            public boolean canUse() {
                return getMimicPhase() >= PHASE_MIMICRY && super.canUse();
            }
        });
    }

    // --- Phase Accessors ---

    public int getMimicPhase() {
        return entityData.get(DATA_PHASE);
    }

    public void setMimicPhase(int phase) {
        entityData.set(DATA_PHASE, phase);
    }

    public int getObservationTicks() {
        return entityData.get(DATA_OBSERVATION_TICKS);
    }

    public float getTargetYaw() {
        return entityData.get(DATA_TARGET_YAW);
    }

    public boolean hasLandedFirstHit() {
        return hasLandedFirstHit;
    }

    public Optional<UUID> getMimicTargetUUID() {
        return entityData.get(DATA_MIMIC_TARGET);
    }

    public int getMimicryTicks() {
        return entityData.get(DATA_MIMICRY_TICKS);
    }

    public int getBurrowTicks() {
        return entityData.get(DATA_BURROW_TICKS);
    }

    int getDespawnTimerInternal() {
        return despawnTimer;
    }

    void setDespawnTimerInternal(int timer) {
        despawnTimer = timer;
    }

    boolean isEngagedInternal() {
        return engaged;
    }

    void setEngagedInternal(boolean value) {
        engaged = value;
    }

    void setMimicTargetUUIDInternal(Optional<UUID> targetUuid) {
        entityData.set(DATA_MIMIC_TARGET, targetUuid);
    }

    void setHasLandedFirstHitInternal(boolean value) {
        hasLandedFirstHit = value;
    }

    void forceTransitionToPhase(int phase) {
        transitionToPhase(phase);
    }

    public void bindToHearthPopulation(UUID hearthId, BlockPos home) {
        hearthPopulationId = hearthId;
        hearthPopulationHome = home.immutable();
        setPersistenceRequired();
        restrictTo(hearthPopulationHome, HearthPopulationPolicy.MIMIC_HOME_RADIUS);
        setTarget(null);
        getNavigation().stop();
        engaged = false;
        despawnTimer = 0;
        transitionToPhase(PHASE_OBSERVATION);
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

    // --- AI Step ---

    @Override
    public void aiStep() {
        enforceHearthEncounterRole();
        clearDeescalatedHearthAggression();
        super.aiStep();

        // Client-side particles
        if (level().isClientSide()) {
            int phase = getMimicPhase();
            if (phase == PHASE_MIMICRY) {
                float progress = getMimicryTicks() / (float) MIMICRY_DURATION;
                float intensity = 1.0f - Math.abs(progress - 0.5f) * 2.0f;
                int count = 1 + (int) (intensity * 4);
                for (int i = 0; i < count; i++) {
                    double px = getX() + (random.nextDouble() - 0.5) * 0.8;
                    double py = getY() + random.nextDouble() * 1.95;
                    double pz = getZ() + (random.nextDouble() - 0.5) * 0.8;
                    if (random.nextFloat() < 0.3f) {
                        level().addParticle(ParticleTypes.LARGE_SMOKE, px, py, pz, 0, 0.05, 0);
                    } else {
                        level().addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.03, 0);
                    }
                }
            } else if (phase == PHASE_RETREAT) {
                // Smoke particles during retreat morph-back (first 60 ticks)
                int rt = entityData.get(DATA_OBSERVATION_TICKS); // retreat doesn't have its own synced tick
                // Use a simpler check: just spawn smoke if we're in retreat phase
                // The server also sends particles, but client-side adds density
                if (random.nextFloat() < 0.4f) {
                    double px = getX() + (random.nextDouble() - 0.5) * 0.8;
                    double py = getY() + random.nextDouble() * 1.95;
                    double pz = getZ() + (random.nextDouble() - 0.5) * 0.8;
                    level().addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0.03, 0);
                }
            } else if (phase == PHASE_BURROW) {
                int bt = getBurrowTicks();
                // Dirt/debris particles — escalate as digging progresses
                if (bt > 10) {
                    BlockState groundBlock = level().getBlockState(blockPosition().below());
                    if (groundBlock.isAir()) {
                        groundBlock = Blocks.DIRT.defaultBlockState();
                    }
                    int count = Math.min(6, 2 + (bt - 10) / 3);
                    for (int i = 0; i < count; i++) {
                        double px = getX() + (random.nextDouble() - 0.5) * 0.6;
                        double py = getY() + random.nextDouble() * 0.3;
                        double pz = getZ() + (random.nextDouble() - 0.5) * 0.6;
                        level().addParticle(
                                new BlockParticleOption(ParticleTypes.BLOCK, groundBlock),
                                px, py, pz,
                                (random.nextDouble() - 0.5) * 0.3, 0.15, (random.nextDouble() - 0.5) * 0.3);
                    }
                }
            }
            return;
        }

        int phase = getMimicPhase();
        long gameTick = level().getGameTime();

        if (isPostMaeveHearthResident()) {
            LivingEntity attacker = getLastHurtByMob();
            if (attacker != null && attacker.isAlive()) {
                setTarget(attacker);
                engaged = true;
                if (phase != PHASE_COMBAT) {
                    transitionToPhase(PHASE_COMBAT);
                }
            } else {
                setTarget(null);
                engaged = false;
                if (phase != PHASE_OBSERVATION) {
                    transitionToPhase(PHASE_OBSERVATION);
                }
                getNavigation().stop();
            }
            despawnTimer = 0;
            return;
        }

        switch (phase) {
            case PHASE_OBSERVATION -> tickObservation();
            case PHASE_MIMICRY -> tickMimicry();
            case PHASE_COMBAT -> tickCombat();
            case PHASE_RETREAT -> tickRetreat();
            case PHASE_BURROW -> tickBurrow();
        }

        // Heater burn: 3 damage/sec within 4 blocks, check every 20 ticks
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

        // Hearth residents persist. Ordinary Mimics retain their custom timer.
        if (isHearthPopulationResident()) {
            despawnTimer = 0;
        } else if (!engaged) {
            despawnTimer++;
            if (despawnTimer >= DESPAWN_TIMEOUT) {
                discard();
            }
        } else {
            despawnTimer = 0;
        }
    }

    private void tickObservation() {
        if (isHearthPopulationResident()) {
            tickHearthObservation();
            return;
        }

        int ticks = getObservationTicks() + 1;
        entityData.set(DATA_OBSERVATION_TICKS, ticks);

        // Stand motionless — zero horizontal movement but preserve gravity
        Vec3 currentMotion = getDeltaMovement();
        setDeltaMovement(0, currentMotion.y, 0);
        getNavigation().stop();

        // Stare sound cooldown
        if (stareSoundCooldown > 0) stareSoundCooldown--;

        // Face nearest player
        Player nearest = level().getNearestPlayer(this, 48.0);
        if (nearest != null) {
            double dx = nearest.getX() - getX();
            double dz = nearest.getZ() - getZ();
            float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            entityData.set(DATA_TARGET_YAW, yaw);
            setYRot(yaw);
            setYHeadRot(yaw);

            // Detect if player's crosshair is on the mimic — Enderman-style AABB raycast
            if (stareSoundCooldown <= 0) {
                if (MimicCombatBehavior.isPlayerLookingAtMe(this, nearest)) {
                    // Play at the PLAYER's position so it feels like an ambient cue
                    level().playSound(null, nearest.getX(), nearest.getY(), nearest.getZ(),
                            ModSounds.MIMIC_STARE.get(), net.minecraft.sounds.SoundSource.AMBIENT, 1.0f, 1.0f);
                    stareSoundCooldown = 100 + random.nextInt(200); // 5-15 sec cooldown
                }
            }

            // If player approaches within 16 blocks, transition to mimicry
            if (nearest.distanceTo(this) <= 16.0) {
                recordedVelocity = nearest.getDeltaMovement();
                entityData.set(DATA_MIMIC_TARGET, Optional.of(nearest.getUUID()));
                transitionToPhase(PHASE_MIMICRY);
                return;
            }
        }

        // If observation timer expires with no player approach, burrow underground
        // Only burrow from initial observation, not after re-entering from combat
        if (!engaged && ticks >= observationDuration) {
            // Check if ground below is solid and breakable
            BlockPos below = blockPosition().below();
            BlockState belowState = level().getBlockState(below);
            if (belowState.getDestroySpeed(level(), below) >= 0 && !belowState.isAir()) {
                transitionToPhase(PHASE_BURROW);
            } else {
                discard(); // Can't burrow here (bedrock, air, etc.)
            }
        }
    }

    private void tickMimicry() {
        mimicryTimer++;
        entityData.set(DATA_MIMICRY_TICKS, mimicryTimer);
        engaged = true;

        // Real-time player movement mirroring — copy the target player's current velocity each tick
        Optional<UUID> targetUUID = getMimicTargetUUID();
        if (targetUUID.isPresent()) {
            Player targetPlayer = level().getPlayerByUUID(targetUUID.get());
            if (targetPlayer != null) {
                Vec3 playerVel = targetPlayer.getDeltaMovement();
                setDeltaMovement(playerVel.x, getDeltaMovement().y, playerVel.z);
                // Mirror the player's facing direction too
                setYRot(targetPlayer.getYRot());
                setYHeadRot(targetPlayer.getYHeadRot());
                setXRot(targetPlayer.getXRot());
            }
        } else {
            // Fallback: use last recorded velocity
            setDeltaMovement(recordedVelocity.x, getDeltaMovement().y, recordedVelocity.z);
        }

        if (mimicryTimer >= MIMICRY_DURATION) {
            transitionToPhase(PHASE_COMBAT);
        }
    }

    private void tickCombat() {
        // Check LOS to target
        LivingEntity target = getTarget();
        if (isHearthPopulationResident() && target == null) {
            hearthLostTargetTicks++;
            if (hearthLostTargetTicks >= 100) {
                transitionToPhase(PHASE_OBSERVATION);
                return;
            }
        } else {
            hearthLostTargetTicks = 0;
        }
        if (target != null) {
            if (hasLineOfSight(target)) {
                losTimer = 0;
            } else {
                losTimer++;
                if (losTimer >= LOS_RESET_TICKS) {
                    transitionToPhase(PHASE_OBSERVATION);
                    return;
                }
            }
        }

        // Check HP for retreat — half health triggers retreat morph
        if (getHealth() < getMaxHealth() * 0.5f) {
            transitionToPhase(PHASE_RETREAT);
        }
    }

    private void tickRetreat() {
        retreatTimer++;

        // First 60 ticks (3 sec): morph back to shadow with smoke
        if (retreatTimer <= 60) {
            // Clear mimic target halfway through to trigger shadow appearance
            if (retreatTimer == 30) {
                entityData.set(DATA_MIMIC_TARGET, Optional.empty());
            }
            // Server-side smoke particles during morph-back
            if (level() instanceof ServerLevel serverLevel && retreatTimer % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        getX(), getY() + 1.0, getZ(), 3, 0.4, 0.8, 0.4, 0.02);
                if (retreatTimer % 6 == 0) {
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                            getX(), getY() + 1.0, getZ(), 1, 0.3, 0.6, 0.3, 0.01);
                }
            }
        }

        // Sprint away from target
        setSprinting(true);
        LivingEntity target = getTarget();
        if (target != null) {
            Vec3 away = position().subtract(target.position()).normalize();
            double retreatX = getX() + away.x * 16;
            double retreatZ = getZ() + away.z * 16;
            getNavigation().moveTo(retreatX, getY(), retreatZ, 1.5);
        }

        // Check if we've reached a dark area (only after morph-back completes)
        if (retreatTimer > 60) {
            int lightLevel = level().getMaxLocalRawBrightness(blockPosition());
            boolean inDarkArea = lightLevel < 7;

            if (inDarkArea || retreatTimer >= RETREAT_DURATION) {
                setSprinting(false);
                transitionToPhase(PHASE_OBSERVATION);
            }
        }
    }

    private void tickBurrow() {
        burrowTimer++;
        entityData.set(DATA_BURROW_TICKS, burrowTimer);

        // Hold position — no horizontal movement
        Vec3 currentMotion = getDeltaMovement();
        setDeltaMovement(0, currentMotion.y, 0);
        getNavigation().stop();

        // Ticks 1-15: look down animation (renderer handles visuals)
        // Tick 16: dig the hole
        if (burrowTimer == 16) {
            BlockPos feetPos = blockPosition();
            BlockPos below1 = feetPos.below();
            BlockPos below2 = feetPos.below(2);

            burrowSurfacePos = below1;
            burrowCoverBlock = level().getBlockState(below1);

            // Break both blocks — entity falls due to gravity
            BlockState below2State = level().getBlockState(below2);
            if (below2State.getDestroySpeed(level(), below2) >= 0 && !below2State.isAir()) {
                level().setBlock(below2, Blocks.AIR.defaultBlockState(), 3);
            }
            level().setBlock(below1, Blocks.AIR.defaultBlockState(), 3);
        }

        // Tick 40: seal the hole
        if (burrowTimer == 40) {
            if (burrowSurfacePos != null && burrowCoverBlock != null) {
                level().setBlock(burrowSurfacePos, burrowCoverBlock, 3);
            }
        }

        if (burrowTimer >= 45) {
            discard();
        }
    }

    private void transitionToPhase(int newPhase) {
        setMimicPhase(newPhase);
        switch (newPhase) {
            case PHASE_OBSERVATION -> {
                entityData.set(DATA_OBSERVATION_TICKS, 0);
                entityData.set(DATA_MIMIC_TARGET, Optional.empty());
                observationDuration = 400 + random.nextInt(201);
                hasLandedFirstHit = false;
                losTimer = 0;
                setTarget(null);
            }
            case PHASE_MIMICRY -> {
                mimicryTimer = 0;
                entityData.set(DATA_MIMICRY_TICKS, 0);
            }
            case PHASE_COMBAT -> {
                hasLandedFirstHit = false;
                losTimer = 0;
            }
            case PHASE_RETREAT -> {
                retreatTimer = 0;
            }
            case PHASE_BURROW -> {
                burrowTimer = 0;
                entityData.set(DATA_BURROW_TICKS, 0);
                burrowSurfacePos = null;
                burrowCoverBlock = null;
            }
        }
    }

    // --- Combat ---

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        MimicCombatBehavior.onSuccessfulAttack(this, target, hit);
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        MimicCombatBehavior.HurtDecision decision = MimicCombatBehavior.beforeHurt(this, source, amount);
        if (decision.cancel()) {
            return false;
        }
        boolean hurt = super.hurt(source, decision.amount());
        if (hurt && isPostMaeveHearthResident()
                && source.getEntity() instanceof LivingEntity attacker) {
            setLastHurtByMob(attacker);
            setTarget(attacker);
            engaged = true;
            transitionToPhase(PHASE_COMBAT);
        }
        if (hurt && isHearthPopulationResident()
                && level() instanceof ServerLevel serverLevel
                && source.getEntity() instanceof ServerPlayer attacker
                && hearthPopulationId != null) {
            HearthMemoryManager.recordHearthEntityAttack(
                    serverLevel, hearthPopulationId, attacker, "mimic resident");
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel serverLevel) {
            if (hearthPopulationId != null && isHearthPopulationResident()) {
                HearthCombatRosterManager.recordResidentDeath(
                        serverLevel, hearthPopulationId, getUUID(),
                        HearthPopulationRole.MIMIC, source);
            }
            // Giant smoke puff on death
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1.0, getZ(), 40, 0.5, 1.0, 0.5, 0.05);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    getX(), getY() + 0.5, getZ(), 60, 0.6, 1.2, 0.6, 0.08);
            serverLevel.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + 1.0, getZ(), 15, 0.4, 0.8, 0.4, 0.02);

            // Grant "not_a_shadow" advancement to killer
            if (source.getEntity() instanceof ServerPlayer killer) {
                WorldTickHandler.grantAdvancement(killer, "not_a_shadow");
            }
            // Also grant to nearby players within 32 blocks
            List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                    ServerPlayer.class, new AABB(blockPosition()).inflate(32.0));
            for (ServerPlayer player : nearbyPlayers) {
                WorldTickHandler.grantAdvancement(player, "not_a_shadow");
            }
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

    // --- Health bar visibility ---

    @Override
    public boolean isCustomNameVisible() {
        // Hide name/health bar during observation phase
        return getMimicPhase() != PHASE_OBSERVATION && super.isCustomNameVisible();
    }

    // --- Sounds ---

    @Override
    public float getVoicePitch() {
        // Deep ghast sounds — Minecraft clamps pitch minimum at 0.5
        return 0.5f + random.nextFloat() * 0.1f;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null; // Always silent — no idle sounds
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MIMIC_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MIMIC_DEATH.get();
    }

    // --- NBT Persistence ---

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        MimicPersistenceState.addAdditionalSaveData(this, tag);
        if (hearthPopulationId != null && hearthPopulationHome != null) {
            tag.putUUID("HearthPopulationId", hearthPopulationId);
            tag.putLong("HearthPopulationHome", hearthPopulationHome.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        MimicPersistenceState.readAdditionalSaveData(this, tag);
        if (tag.hasUUID("HearthPopulationId") && tag.contains("HearthPopulationHome")) {
            hearthPopulationId = tag.getUUID("HearthPopulationId");
            hearthPopulationHome = BlockPos.of(tag.getLong("HearthPopulationHome"));
            setPersistenceRequired();
            restrictTo(hearthPopulationHome, HearthPopulationPolicy.MIMIC_HOME_RADIUS);
            despawnTimer = 0;
        } else {
            hearthPopulationId = null;
            hearthPopulationHome = null;
        }
    }

    // --- Custom despawn (skip vanilla) ---

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
        return !isHearthPopulationResident();
    }

    private void tickHearthObservation() {
        entityData.set(DATA_OBSERVATION_TICKS, 0);
        despawnTimer = 0;
        engaged = false;

        BlockPos home = hearthPopulationHome;
        if (home == null) {
            return;
        }
        double homeDistance = position().distanceToSqr(home.getCenter());
        if (homeDistance > (double) HearthPopulationPolicy.MIMIC_HOME_RADIUS
                * HearthPopulationPolicy.MIMIC_HOME_RADIUS) {
            getNavigation().moveTo(
                    home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.8D);
        } else {
            Vec3 currentMotion = getDeltaMovement();
            setDeltaMovement(0.0D, currentMotion.y, 0.0D);
            getNavigation().stop();
        }

        Player hostile = nearestHearthPlayer(true);
        Player observed = hostile != null ? hostile : nearestHearthPlayer(false);
        if (observed == null) {
            return;
        }
        facePlayer(observed);
        if (observed instanceof ServerPlayer serverPlayer
                && level() instanceof ServerLevel serverLevel
                && hearthPopulationId != null) {
            HearthTransmissionManager.tryStart(
                    serverLevel, this, serverPlayer, hearthPopulationId);
        }
        if (stareSoundCooldown > 0) {
            stareSoundCooldown--;
        } else if (MimicCombatBehavior.isPlayerLookingAtMe(this, observed)) {
            level().playSound(null, observed.getX(), observed.getY(), observed.getZ(),
                    ModSounds.MIMIC_STARE.get(), net.minecraft.sounds.SoundSource.AMBIENT,
                    1.0F, 1.0F);
            stareSoundCooldown = 100 + random.nextInt(200);
        }
        if (hostile != null && hostile.distanceTo(this) <= 16.0D) {
            recordedVelocity = hostile.getDeltaMovement();
            entityData.set(DATA_MIMIC_TARGET, Optional.of(hostile.getUUID()));
            transitionToPhase(PHASE_MIMICRY);
        }
    }

    @Nullable
    private Player nearestHearthPlayer(boolean hostileOnly) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator())
                .filter(player -> distanceToSqr(player) <= 48.0D * 48.0D)
                .filter(player -> !hostileOnly
                        || HearthPopulationPolicy.isHostileRelationship(
                                HearthMemoryManager.relationship(serverLevel, player.getUUID()))
                        && HearthCombatRosterManager.canEngagePlayer(
                                serverLevel, hearthPopulationId, getUUID()))
                .min(java.util.Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private void facePlayer(Player player) {
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        entityData.set(DATA_TARGET_YAW, yaw);
        setYRot(yaw);
        setYHeadRot(yaw);
    }

    private boolean canProactivelyTargetPlayer(LivingEntity candidate) {
        if (!isHearthPopulationResident()) {
            return true;
        }
        return !isPostMaeveHearthResident()
                && candidate instanceof ServerPlayer player
                && level() instanceof ServerLevel serverLevel
                && HearthPopulationPolicy.isHostileRelationship(
                        HearthMemoryManager.relationship(serverLevel, player.getUUID()))
                && HearthCombatRosterManager.canEngagePlayer(
                        serverLevel, hearthPopulationId, getUUID());
    }

    private void clearDeescalatedHearthAggression() {
        if (!isHearthPopulationResident() || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (PostMaeveWorldState.isErased(serverLevel)) {
            return;
        }
        boolean reset = false;
        if (getTarget() instanceof ServerPlayer player
                && !HearthMemoryManager.isPermanentOrsathae(serverLevel, player.getUUID())) {
            setTarget(null);
            reset = true;
        }
        if (getLastHurtByMob() instanceof ServerPlayer player
                && !HearthMemoryManager.isPermanentOrsathae(serverLevel, player.getUUID())) {
            setLastHurtByMob(null);
            reset = true;
        }
        if (reset) {
            engaged = false;
            transitionToPhase(PHASE_OBSERVATION);
            getNavigation().stop();
        }
    }

    private void enforceHearthEncounterRole() {
        if (hearthPopulationId != null && level() instanceof ServerLevel serverLevel
                && !PostMaeveWorldState.isErased(serverLevel)
                && HearthCombatRosterManager.enforcePassiveRole(
                        serverLevel, hearthPopulationId, this)) {
            engaged = false;
            if (getMimicPhase() != PHASE_OBSERVATION) {
                transitionToPhase(PHASE_OBSERVATION);
            }
        }
    }

    private boolean isPostMaeveHearthResident() {
        return isHearthPopulationResident()
                && level() instanceof ServerLevel serverLevel
                && PostMaeveWorldState.isErased(serverLevel);
    }

    public void setHeartScavengerTarget(LivingEntity target) {
        setTarget(target);
        engaged = true;
        if (getMimicPhase() != PHASE_COMBAT) {
            transitionToPhase(PHASE_COMBAT);
        }
    }
}
