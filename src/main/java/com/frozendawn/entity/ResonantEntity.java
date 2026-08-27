package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.init.ModEffects;
import com.frozendawn.init.ModSounds;
import com.frozendawn.init.ModToolTiers;
import com.frozendawn.world.ResonanceEventManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A post-Maeve Hollow evolution that hunts structural vibration, never sight. */
public final class ResonantEntity extends Monster {
    private static final int DEATH_PRESENTATION_TICKS = 44;
    private static final int DEATH_COLLAPSE_SOUND_TICK = 23;
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(ResonantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICKS =
            SynchedEntityData.defineId(ResonantEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_CONFIDENCE =
            SynchedEntityData.defineId(ResonantEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> DATA_BREACH_OUTSIDE =
            SynchedEntityData.defineId(ResonantEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_GRAB_TARGET =
            SynchedEntityData.defineId(ResonantEntity.class, EntityDataSerializers.INT);

    private BlockPos encounterAnchor;
    private BlockPos lastSignalPosition;
    private BlockPos impreciseSignalPosition;
    private UUID lastSignalSource;
    private long lastSignalTime = Long.MIN_VALUE;
    private long lastProcessedSequence;
    private Direction breachNormal = Direction.NORTH;
    private int quietTicks;
    private int grabCooldown;
    private int pulseCooldown = 360;
    private int pulseWindup;
    private int disorientedDuration = ResonantPolicy.DISORIENTED_TICKS;
    private int grabSwings;
    private boolean swingWasActive;

    public ResonantEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 8;
        noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        // Sight, wandering, and vanilla target acquisition would undermine noise authority.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, ResonantState.LISTENING.ordinal());
        builder.define(DATA_STATE_TICKS, 0);
        builder.define(DATA_CONFIDENCE, 0.0F);
        builder.define(DATA_BREACH_OUTSIDE, BlockPos.ZERO.asLong());
        builder.define(DATA_GRAB_TARGET, -1);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
                                        net.minecraft.world.DifficultyInstance difficulty,
                                        MobSpawnType spawnType,
                                        @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        encounterAnchor = blockPosition();
        setActivityState(ResonantState.LISTENING);
        return result;
    }

    public ResonantState activityState() {
        return ResonantState.byOrdinal(entityData.get(DATA_STATE));
    }

    public int stateTicks() {
        return entityData.get(DATA_STATE_TICKS);
    }

    public float confidence() {
        return entityData.get(DATA_CONFIDENCE);
    }

    public BlockPos breachOutside() {
        return BlockPos.of(entityData.get(DATA_BREACH_OUTSIDE));
    }

    public int grabTargetId() {
        return entityData.get(DATA_GRAB_TARGET);
    }

    public float renderAlpha() {
        return switch (activityState()) {
            case DORMANT, LISTENING -> 0.16F;
            case TRIANGULATING -> Mth.lerp(confidence() / 55.0F, 0.24F, 0.48F);
            case PHASING, STALKING -> 0.62F;
            case BREACHING -> 0.84F;
            case GRABBING -> 0.96F;
            case DISORIENTED -> 0.90F;
        };
    }

    public void setActivityState(ResonantState state) {
        if (activityState() == ResonantState.GRABBING && state != ResonantState.GRABBING) {
            clearGrabTarget();
        }
        entityData.set(DATA_STATE, state.ordinal());
        entityData.set(DATA_STATE_TICKS, 0);
        if (state == ResonantState.DISORIENTED || state == ResonantState.GRABBING) {
            noPhysics = false;
            setNoGravity(false);
        } else {
            noPhysics = true;
            setNoGravity(true);
        }
        setDeltaMovement(Vec3.ZERO);
    }

    public void setConfidence(float confidence) {
        entityData.set(DATA_CONFIDENCE, Mth.clamp(confidence, 0.0F, 100.0F));
    }

    public void forceMarkedTarget(ServerPlayer player) {
        if (!player.isAlive() || player.level() != level()) return;
        setConfidence(100.0F);
        recordSignal(player.blockPosition(), player.getUUID(), level().getGameTime());
        if (!isCommittedState(activityState())) {
            setActivityState(ResonantState.PHASING);
        }
    }

    public void forcePulse() {
        if (!isCommittedState(activityState())) {
            pulseWindup = ResonantPolicy.PULSE_WINDUP_TICKS;
            pulseCooldown = 360 + random.nextInt(121);
            playSound(ModSounds.RESONANT_PULSE_WINDUP.get(), 1.25F, 0.82F);
        }
    }

    public boolean forceBreach(ServerPlayer player) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        setConfidence(100.0F);
        recordSignal(player.blockPosition(), player.getUUID(), serverLevel.getGameTime());
        return beginBreach(serverLevel, player.blockPosition());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level();
        if (encounterAnchor == null) encounterAnchor = blockPosition();
        entityData.set(DATA_STATE_TICKS, stateTicks() + 1);
        if (grabCooldown > 0) grabCooldown--;
        tickPulse(serverLevel);

        if (activityState() == ResonantState.BREACHING) {
            tickBreach(serverLevel);
        } else if (activityState() == ResonantState.GRABBING) {
            tickGrab(serverLevel);
        } else if (activityState() == ResonantState.DISORIENTED) {
            tickDisoriented();
        } else {
            tickListeningAndTravel(serverLevel);
        }
    }

    private void tickListeningAndTravel(ServerLevel level) {
        if (tickCount % ResonantPolicy.QUERY_INTERVAL_TICKS == 0) {
            boolean enclosed = ResonantPhaseController.isDenselyEnclosed(level, blockPosition());
            List<ResonanceEventManager.Event> events = ResonanceEventManager.query(
                    level, position(), ResonantPolicy.sensingRadius(enclosed),
                    lastProcessedSequence);
            if (events.isEmpty()) {
                quietTicks += ResonantPolicy.QUERY_INTERVAL_TICKS;
                setConfidence(ResonantPolicy.decayConfidence(
                        confidence(), ResonantPolicy.QUERY_INTERVAL_TICKS));
            } else {
                quietTicks = 0;
                for (ResonanceEventManager.Event event : events) {
                    boolean repeated = lastSignalPosition != null
                            && lastSignalPosition.distSqr(BlockPos.containing(event.position()))
                            <= 36.0D;
                    int age = (int) Math.max(0L, level.getGameTime() - event.gameTime());
                    setConfidence(confidence() + ResonantPolicy.signalConfidence(
                            event.strength(), event.position().distanceTo(position()), age, repeated));
                    recordSignal(BlockPos.containing(event.position()), event.sourceUuid(),
                            event.gameTime());
                    lastProcessedSequence = Math.max(lastProcessedSequence, event.sequence());
                }
            }
        }

        if (lastSignalPosition == null || confidence() < 25.0F) {
            if (activityState() != ResonantState.LISTENING) {
                setActivityState(ResonantState.LISTENING);
            }
            return;
        }

        double distance = position().distanceTo(lastSignalPosition.getCenter());
        if (confidence() >= 80.0F && distance <= 8.0D
                && beginBreach(level, lastSignalPosition)) {
            return;
        }
        if (confidence() >= 55.0F) {
            if (activityState() != ResonantState.PHASING
                    && activityState() != ResonantState.STALKING) {
                setActivityState(ResonantState.PHASING);
                playSound(ModSounds.RESONANT_PHASE.get(), 0.9F, 0.86F);
            }
            moveToward(level, lastSignalPosition.getCenter(), 0.105D);
        } else {
            if (activityState() != ResonantState.TRIANGULATING) {
                setActivityState(ResonantState.TRIANGULATING);
            }
            BlockPos target = impreciseSignalPosition == null
                    ? lastSignalPosition : impreciseSignalPosition;
            moveToward(level, target.getCenter(), 0.065D);
        }
    }

    private void recordSignal(BlockPos position, UUID source, long gameTime) {
        lastSignalPosition = position.immutable();
        lastSignalSource = source;
        lastSignalTime = gameTime;
        int spread = confidence() >= 55.0F ? 1 : 4;
        impreciseSignalPosition = position.offset(
                random.nextIntBetweenInclusive(-spread, spread),
                random.nextIntBetweenInclusive(-1, 1),
                random.nextIntBetweenInclusive(-spread, spread));
    }

    private void moveToward(ServerLevel level, Vec3 target, double speed) {
        Vec3 delta = target.subtract(position());
        if (delta.lengthSqr() < 0.08D) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 motion = delta.normalize().scale(speed);
        BlockPos next = BlockPos.containing(position().add(motion.scale(3.0D)));
        if (!ResonantPhaseController.canPhaseTo(level, encounterAnchor, next)) {
            setDeltaMovement(Vec3.ZERO);
            setConfidence(Math.min(confidence(), 24.0F));
            return;
        }
        setDeltaMovement(motion);
        setYRot((float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90.0F);
    }

    private boolean beginBreach(ServerLevel level, BlockPos playerPosition) {
        if (isCommittedState(activityState())) return false;
        ResonantPhaseController.BreachCandidate candidate =
                ResonantPhaseController.findBreach(level, blockPosition(), playerPosition);
        if (candidate == null) return false;
        breachNormal = candidate.normal();
        entityData.set(DATA_BREACH_OUTSIDE, candidate.outside().asLong());
        setPos(candidate.inside().getX() + 0.5D, candidate.inside().getY(),
                candidate.inside().getZ() + 0.5D);
        setYRot(breachNormal.toYRot());
        setActivityState(ResonantState.BREACHING);
        playSound(ModSounds.RESONANT_KNOCK.get(), 1.25F, 0.68F);
        return true;
    }

    private void tickBreach(ServerLevel level) {
        BlockPos outside = breachOutside();
        BlockPos wall = outside.relative(breachNormal.getOpposite());
        if (!level.hasChunkAt(wall)) {
            enterDisoriented(ResonantPolicy.BREACH_MISS_RECOVERY_TICKS);
            return;
        }
        if (stateTicks() % 4 == 0) {
            BlockState state = level.getBlockState(wall);
            if (!state.isAir()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        outside.getX() + 0.5D, outside.getY() + 1.0D,
                        outside.getZ() + 0.5D, 6, 0.28D, 0.72D, 0.28D, 0.035D);
            }
            playSound(ModSounds.RESONANT_KNOCK.get(),
                    0.7F + stateTicks() / 30.0F,
                    0.62F + stateTicks() * 0.012F);
        }
        if (stateTicks() < ResonantPolicy.BREACH_WINDUP_TICKS) return;

        Vec3 safe = outside.getCenter().add(0.0D, -0.5D, 0.0D);
        setPos(safe.x, safe.y, safe.z);
        noPhysics = false;
        setNoGravity(false);
        playSound(ModSounds.RESONANT_BREACH.get(), 1.5F, 0.76F);
        level.sendParticles(ParticleTypes.POOF, safe.x, safe.y + 1.0D, safe.z,
                24, 0.55D, 0.9D, 0.55D, 0.08D);

        ServerPlayer target = resolveSignalPlayer(level);
        if (target == null || target.position().distanceToSqr(safe) > 2.5D * 2.5D) {
            target = level.getEntitiesOfClass(ServerPlayer.class,
                    new AABB(outside).inflate(2.5D), player -> !player.isCreative()
                            && !player.isSpectator()).stream()
                    .min(Comparator.comparingDouble(player -> player.distanceToSqr(safe)))
                    .orElse(null);
        }
        if (target == null) {
            enterDisoriented(ResonantPolicy.BREACH_MISS_RECOVERY_TICKS);
            return;
        }
        target.hurt(damageSources().mobAttack(this), 4.0F);
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 30, 0,
                false, false, true));
        target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 140,
                target.getTicksFrozen() + 100));
        Vec3 shove = Vec3.atLowerCornerOf(breachNormal.getNormal()).scale(0.32D);
        target.push(shove.x, 0.14D, shove.z);
        if (grabCooldown <= 0) {
            beginGrab(target);
        } else {
            enterDisoriented(ResonantPolicy.DISORIENTED_TICKS);
        }
    }

    private void beginGrab(ServerPlayer target) {
        entityData.set(DATA_GRAB_TARGET, target.getId());
        grabSwings = 0;
        swingWasActive = false;
        grabCooldown = ResonantPolicy.GRAB_COOLDOWN_TICKS;
        setActivityState(ResonantState.GRABBING);
        playSound(ModSounds.RESONANT_GRAB.get(), 1.45F, 0.72F);
    }

    private void tickGrab(ServerLevel level) {
        if (!(level.getEntity(grabTargetId()) instanceof ServerPlayer player)
                || !player.isAlive() || player.isCreative() || player.isSpectator()) {
            releaseGrab();
            return;
        }
        if (StillpointPolicy.isSuppressed(level, player.blockPosition())) {
            releaseGrab();
            return;
        }
        if (isOnFire() || player.isOnFire()) {
            releaseGrab();
            return;
        }

        Vec3 safe = breachOutside().getCenter().add(0.0D, -0.5D, 0.0D);
        Vec3 pull = safe.subtract(player.position());
        if (pull.lengthSqr() > 0.04D) {
            double strength = Math.min(0.22D, 0.07D + pull.length() * 0.035D);
            Vec3 impulse = pull.normalize().scale(strength);
            player.setDeltaMovement(player.getDeltaMovement().scale(0.42D).add(impulse));
            player.hurtMarked = true;
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                12, 3, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.RESONANT_GRASP,
                12, 0, false, false, false));
        player.setTicksFrozen(Math.min(player.getTicksRequiredToFreeze() + 180,
                player.getTicksFrozen() + 8));
        if (stateTicks() % 20 == 0) {
            player.hurt(damageSources().mobAttack(this), 1.0F);
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    12, 0.38D, 0.72D, 0.38D, 0.025D);
        }

        boolean swinging = player.swinging;
        if (swinging && !swingWasActive) {
            ItemStack weapon = player.getMainHandItem();
            int effort = weapon.getItem() instanceof TieredItem tiered
                    && tiered.getTier() == ModToolTiers.ACHERONITE ? 3 : 1;
            grabSwings += effort;
        }
        swingWasActive = swinging;
        if (grabSwings >= ResonantPolicy.GRAB_ESCAPE_SWINGS
                || stateTicks() >= ResonantPolicy.GRAB_MAX_TICKS) {
            releaseGrab();
        }
    }

    private void releaseGrab() {
        if (level() instanceof ServerLevel level
                && level.getEntity(grabTargetId()) instanceof ServerPlayer player) {
            player.removeEffect(ModEffects.RESONANT_GRASP);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        playSound(ModSounds.RESONANT_RELEASE.get(), 1.25F, 0.9F);
        enterDisoriented(ResonantPolicy.DISORIENTED_TICKS);
    }

    private void clearGrabTarget() {
        entityData.set(DATA_GRAB_TARGET, -1);
    }

    private void enterDisoriented(int duration) {
        disorientedDuration = duration;
        setActivityState(ResonantState.DISORIENTED);
    }

    private void tickDisoriented() {
        setDeltaMovement(getDeltaMovement().multiply(0.72D, 1.0D, 0.72D));
        if (stateTicks() >= disorientedDuration) {
            setConfidence(Math.min(confidence(), 18.0F));
            setActivityState(ResonantState.LISTENING);
        }
    }

    private void tickPulse(ServerLevel level) {
        if (isCommittedState(activityState())) return;
        if (pulseWindup > 0) {
            pulseWindup--;
            if (pulseWindup % 5 == 0) {
                level.sendParticles(ParticleTypes.WHITE_ASH,
                        getX(), getY() + 1.0D, getZ(),
                        8, 1.5D, 0.8D, 1.5D, 0.02D);
            }
            if (pulseWindup == 0) executePulse(level);
            return;
        }
        if (--pulseCooldown <= 0) forcePulse();
    }

    private void executePulse(ServerLevel level) {
        playSound(ModSounds.RESONANT_PULSE.get(), 1.45F, 0.7F);
        int dustChecks = 0;
        for (int x = -6; x <= 6 && dustChecks < 64; x += 2) {
            for (int z = -6; z <= 6 && dustChecks < 64; z += 2) {
                BlockPos ceiling = blockPosition().offset(x, 3, z);
                dustChecks++;
                BlockState state = level.getBlockState(ceiling);
                if (!state.isAir() && state.blocksMotion()) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            ceiling.getX() + 0.5D, ceiling.getY() - 0.05D,
                            ceiling.getZ() + 0.5D, 2, 0.22D, 0.05D, 0.22D, 0.012D);
                }
            }
        }
        int radius = ResonantPolicy.sensingRadius(
                ResonantPhaseController.isDenselyEnclosed(level, blockPosition()));
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                getBoundingBox().inflate(radius), p -> !p.isCreative() && !p.isSpectator())) {
            Vec3 horizontal = player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
            if (ResonantPolicy.pulseReveals(horizontal.lengthSqr() > 0.0016D)) {
                setConfidence(Math.max(confidence(), 82.0F));
                recordSignal(player.blockPosition(), player.getUUID(), level.getGameTime());
            }
        }
    }

    private ServerPlayer resolveSignalPlayer(ServerLevel level) {
        if (lastSignalSource == null) return null;
        return level.getPlayerByUUID(lastSignalSource) instanceof ServerPlayer player
                ? player : null;
    }

    private static boolean isCommittedState(ResonantState state) {
        return state == ResonantState.BREACHING || state == ResonantState.GRABBING
                || state == ResonantState.DISORIENTED;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 1.5F;
            if (activityState() == ResonantState.GRABBING) releaseGrab();
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            boolean acheronite = attacker instanceof Player player
                    && player.getMainHandItem().getItem() instanceof TieredItem tiered
                    && tiered.getTier() == ModToolTiers.ACHERONITE;
            if (!acheronite) amount *= 0.5F;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (activityState() == ResonantState.GRABBING) {
            clearGrabEffects();
        }
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        noPhysics = true;
        setNoGravity(true);
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel level) {
            if (deathTime <= 18 && deathTime % 3 == 0) {
                double cavityY = getY() + 1.75D - deathTime * 0.012D;
                level.sendParticles(ParticleTypes.WHITE_ASH,
                        getX(), cavityY, getZ(),
                        5, 0.3D, 0.46D, 0.3D, 0.012D);
            }
            if (deathTime == DEATH_COLLAPSE_SOUND_TICK) {
                playSound(ModSounds.RESONANT_DEATH_COLLAPSE.get(), 1.55F, 0.82F);
            }
            if (deathTime >= 24 && deathTime % 2 == 0) {
                level.sendParticles(ParticleTypes.ASH,
                        getX(), getY() + 0.35D, getZ(),
                        8, 0.48D, 0.18D, 0.48D, 0.035D);
                level.sendParticles(ParticleTypes.POOF,
                        getX(), getY() + 0.22D, getZ(),
                        4, 0.38D, 0.12D, 0.38D, 0.025D);
            }
            if (deathTime >= DEATH_PRESENTATION_TICKS) {
                level.sendParticles(ParticleTypes.WHITE_ASH,
                        getX(), getY() + 0.2D, getZ(),
                        28, 0.72D, 0.18D, 0.72D, 0.055D);
                remove(RemovalReason.KILLED);
            }
        }
    }

    private void clearGrabEffects() {
        if (level() instanceof ServerLevel level
                && level.getEntity(grabTargetId()) instanceof ServerPlayer player) {
            player.removeEffect(ModEffects.RESONANT_GRASP);
            player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        clearGrabTarget();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.RESONANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.RESONANT_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ResonantState", activityState().ordinal());
        tag.putInt("ResonantStateTicks", stateTicks());
        tag.putFloat("ResonantConfidence", confidence());
        if (encounterAnchor != null) tag.putLong("ResonantAnchor", encounterAnchor.asLong());
        if (lastSignalPosition != null) tag.putLong("ResonantSignal", lastSignalPosition.asLong());
        if (lastSignalSource != null) tag.putUUID("ResonantSource", lastSignalSource);
        tag.putLong("ResonantSignalTime", lastSignalTime);
        tag.putInt("ResonantGrabCooldown", grabCooldown);
        tag.putInt("ResonantPulseCooldown", pulseCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        encounterAnchor = tag.contains("ResonantAnchor")
                ? BlockPos.of(tag.getLong("ResonantAnchor")) : blockPosition();
        lastSignalPosition = tag.contains("ResonantSignal")
                ? BlockPos.of(tag.getLong("ResonantSignal")) : null;
        lastSignalSource = tag.hasUUID("ResonantSource")
                ? tag.getUUID("ResonantSource") : null;
        lastSignalTime = tag.getLong("ResonantSignalTime");
        grabCooldown = Math.max(0, tag.getInt("ResonantGrabCooldown"));
        pulseCooldown = Math.max(20, tag.getInt("ResonantPulseCooldown"));
        ResonantState saved = ResonantState.byOrdinal(tag.getInt("ResonantState"));
        if (saved.isUnsafeAfterReload()) {
            saved = ResonantState.LISTENING;
            setConfidence(Math.min(tag.getFloat("ResonantConfidence"), 20.0F));
        } else {
            setConfidence(tag.getFloat("ResonantConfidence"));
        }
        setActivityState(saved);
        if (saved != ResonantState.LISTENING) {
            entityData.set(DATA_STATE_TICKS,
                    Math.max(0, tag.getInt("ResonantStateTicks")));
        }
    }
}
