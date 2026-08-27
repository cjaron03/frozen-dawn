package com.frozendawn.entity;

import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.init.ModItems;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import com.frozendawn.init.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import com.frozendawn.network.HearthBoundaryEffectPayload;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A Vaen-less Returned remnant with no Hearth or collective behavior. */
public class UndoneEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_STUMBLE_TICKS =
            SynchedEntityData.defineId(UndoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PAUSED =
            SynchedEntityData.defineId(UndoneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_GRASP_TARGET_ID =
            SynchedEntityData.defineId(UndoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GRASP_STATE =
            SynchedEntityData.defineId(UndoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BLOOM_EMERGENCE_TICKS =
            SynchedEntityData.defineId(UndoneEntity.class, EntityDataSerializers.INT);

    private static final int TARGET_SCAN_INTERVAL = 20;
    private static final double PURSUIT_SPEED = 1.16D;
    private static final int TARGET_STALL_LIMIT = 600;
    private static final int TARGET_EXCLUSION_TICKS = 400;
    private static final int GRASP_PULL_TICKS = 24;
    private static final int GRASP_HOLD_TICKS = 80;
    private static final int GRASP_COOLDOWN = 160;
    private static final double GRASP_RANGE = 8.5D;
    private static final float GRASP_ESCAPE_PER_TICK = 0.040F;
    public static final int BLOOM_EMERGENCE_DURATION = 36;
    private static final String CONTACT_FLAG = "frozendawnUndoneContact";

    private final Map<UUID, Integer> excludedTargets = new HashMap<>();
    private int pauseTicks;
    private int graspTicks;
    private int graspHoldTicks;
    private int graspCooldown;
    private int struggleFreshTicks;
    private float struggleInput;
    private float struggleProgress;
    private int stalledTicks;
    private double lastTargetDistance = Double.MAX_VALUE;
    @Nullable
    private UUID trackedTarget;
    @Nullable
    private UUID graspTarget;

    public UndoneEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 0;
        getNavigation().getNodeEvaluator().setCanOpenDoors(true);
        getNavigation().getNodeEvaluator().setCanPassDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 72.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.36D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    public static AttributeSupplier.Builder createBloomboundAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 96.0D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.33D)
                .add(Attributes.ARMOR, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.75D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STUMBLE_TICKS, 0);
        builder.define(DATA_PAUSED, false);
        builder.define(DATA_GRASP_TARGET_ID, -1);
        builder.define(DATA_GRASP_STATE, 0);
        builder.define(DATA_BLOOM_EMERGENCE_TICKS, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, PURSUIT_SPEED, false) {
            @Override
            public boolean canUse() {
                return !isBloomEmerging() && !isPaused() && !isGrasping()
                        && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !isBloomEmerging() && !isPaused() && !isGrasping()
                        && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.96D) {
            @Override
            public boolean canUse() {
                return !isBloomEmerging() && !isPaused() && !isGrasping()
                        && getTarget() == null && super.canUse();
            }
        });
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !isBloomEmerging() && !isPaused() && super.canUse();
            }
        });
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide()) {
            return;
        }

        if (isBloomEmerging()) {
            tickBloomEmergence();
            return;
        }

        tickSyncedStumble();
        tickExclusions();
        if (graspCooldown > 0) {
            graspCooldown--;
        }

        tickAura();
        if (isGrasping()) {
            tickGrasp();
            return;
        }

        if (pauseTicks > 0) {
            pauseTicks--;
            entityData.set(DATA_PAUSED, true);
            getNavigation().stop();
            setDeltaMovement(new Vec3(0.0D, getDeltaMovement().y, 0.0D));
            if (pauseTicks == 0) {
                entityData.set(DATA_PAUSED, false);
            }
            return;
        }

        if (getTarget() == null && random.nextInt(2400) == 0) {
            pauseTicks = 60 + random.nextInt(41);
            entityData.set(DATA_PAUSED, true);
            setYHeadRot(random.nextFloat() * 360.0F);
            setYBodyRot(getYHeadRot() + 18.0F);
            return;
        }

        if (random.nextInt(700) == 0) {
            entityData.set(DATA_STUMBLE_TICKS, 10 + random.nextInt(9));
        }
        if (random.nextInt(isBloombound() ? 520 : 760) == 0) {
            SoundEvent ambient = isBloombound()
                    ? (random.nextBoolean()
                    ? ModSounds.BLOOMBOUND_UNDONE_AMBIENT_ONE.get()
                    : ModSounds.BLOOMBOUND_UNDONE_AMBIENT_TWO.get())
                    : random.nextInt(14) == 0
                    ? ModSounds.UNDONE_FAILED_WORD.get()
                    : switch (random.nextInt(3)) {
                        case 0 -> ModSounds.UNDONE_AMBIENT_ONE.get();
                        case 1 -> ModSounds.UNDONE_AMBIENT_TWO.get();
                        default -> ModSounds.UNDONE_AMBIENT_THREE.get();
                    };
            playSound(ambient, 0.82F, 0.92F + random.nextFloat() * 0.10F);
        }

        if (isBloombound() && tickCount % 100 == 0 && isTouchingBloom()) {
            heal(1.0F);
        }

        if (tickCount % TARGET_SCAN_INTERVAL == 0) {
            validateOrFindTarget();
            maintainPursuit();
            trackPathProgress();
            notifyFirstCloseContact();
            tryStartRangedGrasp();
        }
    }

    public boolean isPaused() {
        return entityData.get(DATA_PAUSED);
    }

    public int getStumbleTicks() {
        return entityData.get(DATA_STUMBLE_TICKS);
    }

    public boolean isGrasping() {
        return entityData.get(DATA_GRASP_STATE) != 0;
    }

    public int getGraspState() {
        return entityData.get(DATA_GRASP_STATE);
    }

    public int getGraspTargetId() {
        return entityData.get(DATA_GRASP_TARGET_ID);
    }

    public boolean isBloombound() {
        return getType() == ModEntities.BLOOMBOUND_UNDONE.get();
    }

    public boolean isBloomEmerging() {
        return entityData.get(DATA_BLOOM_EMERGENCE_TICKS) > 0;
    }

    public int getBloomEmergenceTicks() {
        return entityData.get(DATA_BLOOM_EMERGENCE_TICKS);
    }

    public void beginBloomEmergence() {
        if (!isBloombound()) {
            return;
        }
        entityData.set(DATA_BLOOM_EMERGENCE_TICKS, BLOOM_EMERGENCE_DURATION);
        setTarget(null);
        getNavigation().stop();
        if (level() instanceof ServerLevel serverLevel) {
            playSound(ModSounds.BLOOM_CRACK.get(), 1.1F, 0.72F);
            serverLevel.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    getX(), getY() + 0.2D, getZ(),
                    30, 0.48D, 0.18D, 0.48D, 0.035D);
        }
    }

    private void tickBloomEmergence() {
        int ticks = getBloomEmergenceTicks();
        entityData.set(DATA_BLOOM_EMERGENCE_TICKS, Math.max(0, ticks - 1));
        setTarget(null);
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        if (level() instanceof ServerLevel serverLevel && ticks % 3 == 0) {
            double height = 0.2D + (1.0D - ticks / (double) BLOOM_EMERGENCE_DURATION) * 1.4D;
            serverLevel.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    getX(), getY() + height, getZ(),
                    4, 0.32D, 0.12D, 0.32D, 0.018D);
        }
    }

    public void applyStruggle(ServerPlayer player, float input) {
        if (player.getId() != getGraspTargetId() || !Float.isFinite(input)) {
            return;
        }
        struggleInput = net.minecraft.util.Mth.clamp(input, 0.0F, 1.0F);
        struggleFreshTicks = 6;
    }

    private void tickSyncedStumble() {
        int ticks = getStumbleTicks();
        if (ticks > 0) {
            entityData.set(DATA_STUMBLE_TICKS, ticks - 1);
        }
    }

    private void tickExclusions() {
        excludedTargets.replaceAll((uuid, ticks) -> ticks - 1);
        excludedTargets.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private void validateOrFindTarget() {
        LivingEntity current = getTarget();
        if (isValidTarget(current) && !excludedTargets.containsKey(current.getUUID())) {
            return;
        }
        setTarget(null);
        AABB search = getBoundingBox().inflate(64.0D, 32.0D, 64.0D);
        var candidates = level().getEntitiesOfClass(
                        LivingEntity.class, search, this::isValidTarget).stream()
                .filter(candidate -> !excludedTargets.containsKey(candidate.getUUID()))
                .sorted(Comparator.comparingDouble(this::distanceToSqr))
                .limit(12)
                .toList();
        LivingEntity fallback = candidates.isEmpty() ? null : candidates.getFirst();
        for (LivingEntity candidate : candidates) {
            Path path = getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                setTarget(candidate);
                getNavigation().moveTo(path, PURSUIT_SPEED);
                return;
            }
        }
        if (fallback != null) {
            setTarget(fallback);
        }
    }

    private void maintainPursuit() {
        LivingEntity target = getTarget();
        if (!isValidTarget(target) || isPaused() || isGrasping()) {
            return;
        }
        Path path = getNavigation().createPath(target, 0);
        if (path != null) {
            getNavigation().moveTo(path, PURSUIT_SPEED);
        }
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        super.setTarget(isSporeTarget(target) || target instanceof ArchivistEntity
                ? null : target);
    }

    private boolean isValidTarget(@Nullable LivingEntity candidate) {
        if (candidate == null || candidate == this || !candidate.isAlive()
                || candidate == getVehicle() || candidate.hasPassenger(this)
                || candidate instanceof ArchivistEntity
                || candidate instanceof BloomSporeEntity
                || candidate instanceof BloomSporeCorpseEntity) {
            return false;
        }
        if (candidate instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return candidate.isAttackable();
    }

    private void trackPathProgress() {
        LivingEntity target = getTarget();
        if (target == null) {
            trackedTarget = null;
            stalledTicks = 0;
            lastTargetDistance = Double.MAX_VALUE;
            return;
        }
        if (!target.getUUID().equals(trackedTarget)) {
            trackedTarget = target.getUUID();
            stalledTicks = 0;
            lastTargetDistance = distanceTo(target);
            return;
        }

        double distance = distanceTo(target);
        if (distance + 0.65D < lastTargetDistance || hasLineOfSight(target)) {
            stalledTicks = 0;
        } else {
            stalledTicks += TARGET_SCAN_INTERVAL;
        }
        lastTargetDistance = distance;
        if (stalledTicks < TARGET_STALL_LIMIT) {
            return;
        }

        excludedTargets.put(target.getUUID(), TARGET_EXCLUSION_TICKS);
        setTarget(null);
        getNavigation().stop();
        trackedTarget = null;
        stalledTicks = 0;
        lastTargetDistance = Double.MAX_VALUE;
        validateOrFindTarget();
    }

    private void notifyFirstCloseContact() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer player : serverLevel.getEntitiesOfClass(
                ServerPlayer.class, getBoundingBox().inflate(18.0D),
                candidate -> !candidate.isCreative() && !candidate.isSpectator())) {
            if (player.getPersistentData().getBoolean(CONTACT_FLAG)) {
                continue;
            }
            player.getPersistentData().putBoolean(CONTACT_FLAG, true);
            PacketDistributor.sendToPlayer(
                    player, HearthBoundaryEffectPayload.undoneContact());
        }
    }

    private void tickAura() {
        if (!(level() instanceof ServerLevel serverLevel) || tickCount % 4 != 0) {
            return;
        }
        double angle = tickCount * (isBloombound() ? 0.17D : 0.23D) + getId() * 0.71D;
        double radius = 0.78D + 0.16D * Math.sin(tickCount * 0.11D);
        double x = getX() + Math.cos(angle) * radius;
        double y = getY() + 0.35D + Math.floorMod(tickCount, 28) / 28.0D * 1.35D;
        double z = getZ() + Math.sin(angle) * radius;
        serverLevel.sendParticles(isBloombound() ? ParticleTypes.GLOW : ParticleTypes.WHITE_ASH,
                x, y, z, 1, 0.03D, 0.04D, 0.03D, 0.0D);
        if (isBloombound() && tickCount % 8 == 0) {
            serverLevel.sendParticles(ParticleTypes.WAX_ON,
                    getX(), getY() + 1.05D, getZ(),
                    2, 0.34D, 0.62D, 0.34D, 0.012D);
        }
        if (tickCount % 8 == 0) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX() + Math.cos(angle + Math.PI) * 0.55D,
                    getY() + 0.8D,
                    getZ() + Math.sin(angle + Math.PI) * 0.55D,
                    1, 0.02D, 0.02D, 0.02D, 0.005D);
        }
        if (tickCount % 16 == 0) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    getX(), getY() + 1.05D, getZ(),
                    1, 0.16D, 0.42D, 0.16D, 0.005D);
        }
    }

    private void tryStartRangedGrasp() {
        LivingEntity target = getTarget();
        if (graspCooldown > 0 || !isValidTarget(target)
                || distanceToSqr(target) > GRASP_RANGE * GRASP_RANGE
                || distanceToSqr(target) < 5.0D
                || !hasLineOfSight(target)) {
            return;
        }
        beginGrasp(target);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isBloomEmerging() || isSporeTarget(target)) {
            setTarget(null);
            getNavigation().stop();
            return false;
        }
        boolean hit = super.doHurtTarget(target);
        if (!hit) {
            return false;
        }
        playSound(isBloombound()
                        ? ModSounds.BLOOMBOUND_UNDONE_ATTACK.get()
                        : ModSounds.UNDONE_ATTACK.get(),
                isBloombound() ? 1.25F : 1.0F,
                0.9F + random.nextFloat() * 0.12F);
        if (graspCooldown <= 0 && target instanceof LivingEntity living) {
            beginGrasp(living);
        }
        return true;
    }

    private static boolean isSporeTarget(@Nullable Entity target) {
        return target instanceof BloomSporeEntity
                || target instanceof BloomSporeCorpseEntity;
    }

    private void beginGrasp(LivingEntity target) {
        if (!(level() instanceof ServerLevel) || isGrasping()
                || !isValidTarget(target)) {
            return;
        }
        graspTarget = target.getUUID();
        graspTicks = 0;
        graspHoldTicks = 0;
        graspCooldown = GRASP_COOLDOWN;
        struggleProgress = 0.0F;
        struggleInput = 0.0F;
        struggleFreshTicks = 0;
        entityData.set(DATA_GRASP_TARGET_ID, target.getId());
        entityData.set(DATA_GRASP_STATE, 1);
        getNavigation().stop();
        playSound(isBloombound()
                        ? ModSounds.BLOOMBOUND_UNDONE_ATTACK.get()
                        : ModSounds.UNDONE_GRASP_CAST.get(),
                1.35F, isBloombound() ? 0.72F : 0.92F);
    }

    private void tickGrasp() {
        if (!(level() instanceof ServerLevel serverLevel) || graspTarget == null
                || !(serverLevel.getEntity(graspTarget) instanceof LivingEntity target)
                || !isValidTarget(target) || distanceToSqr(target) > 196.0D) {
            releaseGrasp(false);
            return;
        }
        if (StillpointPolicy.isSuppressed(serverLevel, target.blockPosition())) {
            releaseGrasp(false);
            return;
        }
        getLookControl().setLookAt(target, 40.0F, 40.0F);
        setTarget(target);
        getNavigation().stop();

        Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).normalize();
        Vec3 anchor = position().add(
                forward.x * 1.12D, 1.15D, forward.z * 1.12D);
        Vec3 targetCenter = target.position().add(0.0D, target.getBbHeight() * 0.52D, 0.0D);
        Vec3 toward = anchor.subtract(targetCenter);
        double distance = toward.length();
        double acceleration = entityData.get(DATA_GRASP_STATE) == 1 ? 0.20D : 0.28D;
        Vec3 correction = distance < 0.01D
                ? Vec3.ZERO : toward.normalize().scale(Math.min(0.48D, distance * acceleration));
        Vec3 motion = target.getDeltaMovement().scale(0.28D).add(correction);
        target.setDeltaMovement(motion);
        target.fallDistance = 0.0F;

        sendGraspParticles(serverLevel, targetCenter, anchor);
        if (entityData.get(DATA_GRASP_STATE) == 1) {
            graspTicks++;
            if (distance <= 1.15D || graspTicks >= GRASP_PULL_TICKS) {
                entityData.set(DATA_GRASP_STATE, 2);
                playSound(isBloombound()
                                ? ModSounds.BLOOM_CORE_PULSE.get()
                                : ModSounds.UNDONE_GRASP_HOLD.get(),
                        1.2F, isBloombound() ? 0.74F : 0.86F);
            }
        } else {
            graspHoldTicks++;
            if (graspHoldTicks % 20 == 0) {
                target.hurt(damageSources().mobAttack(this), 2.0F);
            }
        }

        if (target instanceof ServerPlayer player) {
            tickPlayerStruggle(player);
        } else if (graspHoldTicks >= GRASP_HOLD_TICKS) {
            releaseGrasp(false);
        }
    }

    private void tickPlayerStruggle(ServerPlayer player) {
        if (struggleFreshTicks > 0) {
            struggleFreshTicks--;
            struggleProgress += GRASP_ESCAPE_PER_TICK * struggleInput;
        } else {
            struggleProgress = Math.max(0.0F, struggleProgress - 0.008F);
        }
        if (struggleProgress >= 1.0F || graspHoldTicks >= GRASP_HOLD_TICKS + 40) {
            Vec3 away = player.position().subtract(position()).normalize().scale(0.42D);
            player.setDeltaMovement(away.x, 0.18D, away.z);
            playSound(ModSounds.UNDONE_GRASP_BREAK.get(), 1.1F, 1.08F);
            releaseGrasp(true);
        }
    }

    private void sendGraspParticles(ServerLevel level, Vec3 target, Vec3 anchor) {
        Vec3 path = target.subtract(anchor);
        for (int i = 1; i <= 4; i++) {
            double t = i / 5.0D;
            Vec3 point = anchor.add(path.scale(t));
            level.sendParticles(isBloombound()
                            ? (i % 2 == 0 ? ParticleTypes.GLOW : ParticleTypes.WAX_ON)
                            : (i % 2 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.SNOWFLAKE),
                    point.x, point.y, point.z,
                    1, 0.04D, 0.04D, 0.04D, 0.004D);
        }
        if (tickCount % 6 == 0) {
            level.sendParticles(ParticleTypes.SMOKE,
                    target.x, target.y, target.z,
                    2, 0.24D, 0.42D, 0.24D, 0.01D);
        }
    }

    private void releaseGrasp(boolean escaped) {
        graspTarget = null;
        graspTicks = 0;
        graspHoldTicks = 0;
        struggleProgress = 0.0F;
        struggleInput = 0.0F;
        struggleFreshTicks = 0;
        entityData.set(DATA_GRASP_TARGET_ID, -1);
        entityData.set(DATA_GRASP_STATE, 0);
        if (!escaped) {
            graspCooldown = Math.max(graspCooldown, 160);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isBloomEmerging() || source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        if (source.getEntity() instanceof ArchitectEntity) {
            amount *= 1.5F;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && isGrasping() && amount >= 6.0F) {
            releaseGrasp(false);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide()) {
            releaseGrasp(false);
            playSound(isBloombound()
                            ? ModSounds.BLOOMBOUND_UNDONE_DEATH.get()
                            : ModSounds.UNDONE_DEATH.get(),
                    isBloombound() ? 2.0F : 1.75F,
                    isBloombound() ? 0.76F : 0.92F);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(isBloombound()
                                ? ParticleTypes.WAX_ON : ParticleTypes.ITEM_SNOWBALL,
                        getX(), getY() + 0.9D, getZ(),
                        isBloombound() ? 48 : 28,
                        0.4D, 0.75D, 0.4D, 0.08D);
            }
            if (random.nextInt(8) == 0) {
                spawnAtLocation(new ItemStack(ModItems.TATTERED_CLOTHING_SCRAP.get()));
            }
        }
        super.die(source);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isBloombound()
                ? ModSounds.BLOOMBOUND_UNDONE_HURT.get()
                : ModSounds.UNDONE_HURT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos,
                                 net.minecraft.world.level.block.state.BlockState state) {
        super.playStepSound(pos, state);
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    private boolean isTouchingBloom() {
        BlockPos origin = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1),
                origin.offset(1, 1, 1))) {
            BlockState state = level().getBlockState(pos);
            if (state.is(ModBlocks.BLOOM_MASS.get())
                    || state.is(ModBlocks.BLOOM_CRUST.get())
                    || state.is(ModBlocks.BLOOM_TIP.get())
                    || state.is(ModBlocks.BLOOM_CORE.get())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("GraspCooldown", graspCooldown);
        tag.putInt("PauseTicks", pauseTicks);
        tag.putInt("BloomEmergenceTicks", getBloomEmergenceTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        graspCooldown = Math.max(0, tag.contains("GraspCooldown")
                ? tag.getInt("GraspCooldown") : tag.getInt("GrabCooldown"));
        pauseTicks = Math.max(0, tag.getInt("PauseTicks"));
        entityData.set(DATA_BLOOM_EMERGENCE_TICKS,
                Math.max(0, tag.getInt("BloomEmergenceTicks")));
        entityData.set(DATA_PAUSED, pauseTicks > 0);
        graspTarget = null;
        graspTicks = 0;
        graspHoldTicks = 0;
        struggleProgress = 0.0F;
        struggleInput = 0.0F;
        struggleFreshTicks = 0;
        entityData.set(DATA_GRASP_TARGET_ID, -1);
        entityData.set(DATA_GRASP_STATE, 0);
    }
}
