package com.frozendawn.entity;

import com.frozendawn.FrozenDawn;
import com.frozendawn.data.PlayerPlacedBlockTracker;
import com.frozendawn.data.ReturnedHearthSavedData;
import com.frozendawn.homo.HearthProtectionPolicy;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.ChunkCatchUpManager;
import net.minecraft.core.BlockPos;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** A post-Maeve terrain predator with no dependency on Frostbitten AI. */
public final class RimeboundEntity extends Monster {
    private static final ResourceLocation SHELL_ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(FrozenDawn.MOD_ID,
                    "rimebound_shell_armor");
    private static final EntityDataAccessor<Integer> DATA_STATE =
            SynchedEntityData.defineId(RimeboundEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE_TICKS =
            SynchedEntityData.defineId(RimeboundEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SHELL =
            SynchedEntityData.defineId(RimeboundEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_LOCKED_POSITION =
            SynchedEntityData.defineId(RimeboundEntity.class, EntityDataSerializers.LONG);

    private final RimeboundBurrowController burrowController =
            new RimeboundBurrowController();
    private final RimeboundTerrainTracker terrainTracker =
            new RimeboundTerrainTracker();
    private final RimeboundRangedController rangedController =
            new RimeboundRangedController();
    private int lanceCooldown;
    private int burrowCooldown;
    private int freezeCooldown;
    private int leapCooldown;
    private int freezeWindup;
    private int ticksWithoutDamage;
    private int deathPresentationTicks;
    private int wakeMovementTicks;

    public RimeboundEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 42.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.ARMOR, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.65D)
                .add(Attributes.FOLLOW_RANGE, 28.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, RimeboundState.STALKING.ordinal());
        builder.define(DATA_STATE_TICKS, 0);
        builder.define(DATA_SHELL, 0);
        builder.define(DATA_LOCKED_POSITION, BlockPos.ZERO.asLong());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.16D, false) {
            @Override
            public boolean canUse() {
                return canRunOrdinaryAi() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return canRunOrdinaryAi() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.72D) {
            @Override
            public boolean canUse() {
                return canRunOrdinaryAi() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F) {
            @Override
            public boolean canUse() {
                return canRunOrdinaryAi() && super.canUse();
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return canRunOrdinaryAi() && super.canUse();
            }
        });
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData);
        setShellIntegrity(RimeboundPolicy.SHELL_MAX_INTEGRITY);
        return result;
    }

    public RimeboundState activityState() {
        return RimeboundState.byOrdinal(entityData.get(DATA_STATE));
    }

    public int stateTicks() {
        return entityData.get(DATA_STATE_TICKS);
    }

    public int shellIntegrity() {
        return entityData.get(DATA_SHELL);
    }

    public int shellVisualStage() {
        int shell = shellIntegrity();
        return shell <= 0 ? 0 : shell <= 3 ? 1 : shell <= 7 ? 2 : 3;
    }

    public BlockPos lockedPosition() {
        return BlockPos.of(entityData.get(DATA_LOCKED_POSITION));
    }

    public void setActivityState(RimeboundState state) {
        entityData.set(DATA_STATE, state.ordinal());
        entityData.set(DATA_STATE_TICKS, 0);
        if (state != RimeboundState.BURROWING) {
            noPhysics = false;
            setInvisible(false);
        }
        if (state == RimeboundState.DORMANT || state == RimeboundState.EMERGING
                || state == RimeboundState.RECOVERY || state == RimeboundState.ARMORED) {
            getNavigation().stop();
        }
        if (state == RimeboundState.DORMANT || state == RimeboundState.EMERGING
                || state == RimeboundState.BURROWING
                || state == RimeboundState.ERUPTING) {
            setPose(Pose.SWIMMING);
        } else {
            setPose(Pose.STANDING);
        }
    }

    public void beginDormant() {
        setActivityState(RimeboundState.DORMANT);
        setTarget(null);
    }

    public void notifyTerrainInteraction(BlockPos interaction) {
        if (activityState() == RimeboundState.DORMANT
                && interaction.closerToCenterThan(position(), 10.0D)) {
            setActivityState(RimeboundState.EMERGING);
            playSound(ModSounds.RIMEBOUND_CONTRACTION.get(), 1.35F, 0.72F);
        }
    }

    public void forceBurrow() {
        if (level() instanceof ServerLevel serverLevel) {
            LivingEntity target = getTarget();
            BlockPos destination = preferredBurrowExit(serverLevel, target);
            if (destination != null && burrowController.buildRoute(
                    serverLevel, blockPosition(), destination)) {
                setActivityState(RimeboundState.BURROWING);
                noPhysics = true;
                setInvisible(true);
                playSound(ModSounds.RIMEBOUND_BURROW.get(), 1.4F, 0.82F);
            }
        }
    }

    public void forceLance() {
        LivingEntity target = getTarget();
        if (target == null && level() instanceof ServerLevel serverLevel) {
            target = serverLevel.getNearestPlayer(this, 28.0D);
            setTarget(target);
        }
        if (target != null) {
            setActivityState(RimeboundState.RANGED_WINDUP);
            playSound(ModSounds.RIMEBOUND_LANCE_WINDUP.get(), 1.25F, 0.9F);
        }
    }

    public void forceFreeze() {
        freezeWindup = 25;
        getNavigation().stop();
        playSound(ModSounds.RIMEBOUND_FREEZE_WINDUP.get(), 1.25F, 0.78F);
    }

    @Override
    protected void customServerAiStep() {
        tickCooldowns();
        ticksWithoutDamage++;
        entityData.set(DATA_STATE_TICKS, stateTicks() + 1);

        if (!(level() instanceof ServerLevel serverLevel)) {
            super.customServerAiStep();
            return;
        }
        terrainTracker.pruneUnloaded(serverLevel);
        tickTerrainControl(serverLevel);
        if (freezeWindup > 0) {
            tickFreezeWindup(serverLevel);
            return;
        }

        switch (activityState()) {
            case DORMANT -> tickDormant(serverLevel);
            case EMERGING -> tickEmerging(serverLevel);
            case BURROWING -> tickBurrowing(serverLevel);
            case ERUPTING -> tickErupting(serverLevel);
            case RANGED_WINDUP -> tickRangedWindup(serverLevel);
            case LEAP_WINDUP -> tickLeapWindup();
            case RECOVERY -> tickRecovery();
            case ARMORED -> tickArmorRebuild(serverLevel);
            case DEAD -> {
            }
            case STALKING -> {
                super.customServerAiStep();
                tickStalking(serverLevel);
            }
        }
    }

    private void tickCooldowns() {
        lanceCooldown = Math.max(0, lanceCooldown - 1);
        burrowCooldown = Math.max(0, burrowCooldown - 1);
        freezeCooldown = Math.max(0, freezeCooldown - 1);
        leapCooldown = Math.max(0, leapCooldown - 1);
    }

    private void tickDormant(ServerLevel level) {
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        getNavigation().stop();
        if (tickCount % 20 != 0) {
            return;
        }
        boolean movement = level.getEntitiesOfClass(Player.class,
                        getBoundingBox().inflate(10.0D), player -> !player.isSpectator())
                .stream().anyMatch(player -> player.getDeltaMovement().horizontalDistanceSqr()
                        > 0.0025D);
        wakeMovementTicks = movement ? wakeMovementTicks + 20 : 0;
        if (wakeMovementTicks >= 20 || random.nextInt(240) == 0) {
            setActivityState(RimeboundState.EMERGING);
            playSound(ModSounds.RIMEBOUND_CONTRACTION.get(), 1.35F, 0.72F);
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 0.12D, getZ(), 18,
                    0.45D, 0.12D, 0.45D, 0.025D);
        }
    }

    private void tickEmerging(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
        if (stateTicks() == 10 || stateTicks() == 22) {
            playSound(ModSounds.RIMEBOUND_SHELL_CRACK.get(), 1.25F,
                    stateTicks() == 10 ? 0.72F : 0.9F);
        }
        if (stateTicks() % 3 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 0.2D, getZ(), 5,
                    0.42D, 0.18D, 0.42D, 0.03D);
        }
        if (stateTicks() >= RimeboundPolicy.EMERGENCE_TICKS) {
            setActivityState(RimeboundState.STALKING);
        }
    }

    private void tickStalking(ServerLevel level) {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }
        double distance = distanceTo(target);
        if (ticksWithoutDamage >= 200 && shellIntegrity() <= 0
                && isFrozenTerrain(level.getBlockState(blockPosition().below()))) {
            setActivityState(RimeboundState.ARMORED);
            playSound(ModSounds.RIMEBOUND_ARMOR.get(), 1.1F, 0.76F);
            return;
        }
        if (burrowCooldown <= 0 && distance >= 9.0D && random.nextInt(55) == 0) {
            forceBurrow();
            if (activityState() == RimeboundState.BURROWING) {
                burrowCooldown = 180;
                return;
            }
        }
        if (lanceCooldown <= 0 && distance >= 7.0D && distance <= 24.0D
                && hasLineOfSight(target) && random.nextInt(45) == 0) {
            forceLance();
            return;
        }
        if (freezeCooldown <= 0 && distance <= 6.0D && random.nextInt(100) == 0) {
            forceFreeze();
            return;
        }
        if (leapCooldown <= 0 && distance >= 6.0D && distance <= 12.0D
                && hasLineOfSight(target) && random.nextInt(90) == 0) {
            setActivityState(RimeboundState.LEAP_WINDUP);
            getNavigation().stop();
            playSound(ModSounds.RIMEBOUND_LEAP.get(), 1.15F, 0.84F);
        }
    }

    private void tickBurrowing(ServerLevel level) {
        noPhysics = true;
        setInvisible(true);
        setTarget(getTarget());
        BlockPos waypoint = burrowController.currentWaypoint();
        if (waypoint == null || burrowController.complete()) {
            lockEruption(blockPosition());
            return;
        }
        Vec3 target = new Vec3(waypoint.getX() + 0.5D,
                waypoint.getY() - 0.45D, waypoint.getZ() + 0.5D);
        Vec3 delta = target.subtract(position());
        if (delta.lengthSqr() > 0.001D) {
            Vec3 motion = delta.normalize().scale(0.34D);
            setDeltaMovement(motion);
            setPos(getX() + motion.x, target.y, getZ() + motion.z);
        }
        burrowController.advanceIfReached(getX(), getZ());
        terrainTracker.addTrail(waypoint.below(), level.getGameTime());
        if (tickCount % 2 == 0) {
            BlockState surface = level.getBlockState(waypoint.below());
            level.sendParticles(new BlockParticleOption(
                            ParticleTypes.BLOCK, surface),
                    getX(), waypoint.getY() + 0.05D, getZ(), 3,
                    0.25D, 0.05D, 0.25D, 0.025D);
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), waypoint.getY() + 0.08D, getZ(), 2,
                    0.24D, 0.04D, 0.24D, 0.02D);
        }
        if (burrowController.complete()) {
            lockEruption(waypoint);
        }
    }

    private void lockEruption(BlockPos position) {
        entityData.set(DATA_LOCKED_POSITION, position.asLong());
        setPos(position.getX() + 0.5D, position.getY() - 0.45D,
                position.getZ() + 0.5D);
        setActivityState(RimeboundState.ERUPTING);
        setInvisible(true);
        noPhysics = true;
        setDeltaMovement(Vec3.ZERO);
    }

    private void tickErupting(ServerLevel level) {
        BlockPos locked = lockedPosition();
        setPos(locked.getX() + 0.5D, locked.getY() - 0.45D,
                locked.getZ() + 0.5D);
        setDeltaMovement(Vec3.ZERO);
        if (stateTicks() % 2 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    locked.getX() + 0.5D, locked.getY() + 0.08D,
                    locked.getZ() + 0.5D, 5,
                    0.55D, 0.06D, 0.55D, 0.055D);
        }
        if (stateTicks() >= RimeboundPolicy.ERUPTION_TELEGRAPH_TICKS) {
            noPhysics = false;
            setInvisible(false);
            setPos(locked.getX() + 0.5D, locked.getY(), locked.getZ() + 0.5D);
            playSound(ModSounds.RIMEBOUND_ERUPTION.get(), 1.7F, 0.82F);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    getX(), getY() + 0.5D, getZ(), 42,
                    0.75D, 0.6D, 0.75D, 0.16D);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(2.5D), entity -> entity != this)) {
                if (victim.hurt(damageSources().mobAttack(this), 5.0F)) {
                    Vec3 shove = victim.position().subtract(position()).normalize().scale(0.55D);
                    victim.push(shove.x, 0.28D, shove.z);
                    victim.addEffect(new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                    if (victim instanceof ServerPlayer player) {
                        RimeboundEncasement.apply(player, 35.0F);
                    }
                }
            }
            terrainTracker.addBrittle(locked.below(), level.getGameTime());
            setActivityState(RimeboundState.RECOVERY);
        }
    }

    private void tickRangedWindup(ServerLevel level) {
        getNavigation().stop();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            setActivityState(RimeboundState.STALKING);
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (stateTicks() % 4 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getEyeY() - 0.35D, getZ(), 3,
                    0.18D, 0.2D, 0.18D, 0.02D);
        }
        if (stateTicks() >= RimeboundPolicy.LANCE_WINDUP_TICKS) {
            fireLance(level, target);
            lanceCooldown = 100;
            setActivityState(RimeboundState.RECOVERY);
        }
    }

    private void fireLance(ServerLevel level, LivingEntity target) {
        rangedController.fire(level, this, target);
        playSound(ModSounds.RIMEBOUND_LANCE.get(), 1.35F, 0.92F);
    }

    private void tickLeapWindup() {
        getNavigation().stop();
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            setActivityState(RimeboundState.STALKING);
            return;
        }
        if (stateTicks() >= RimeboundPolicy.LEAP_WINDUP_TICKS) {
            Vec3 predicted = target.position().add(target.getDeltaMovement().scale(6.0D));
            Vec3 horizontal = predicted.subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (horizontal.lengthSqr() > 0.01D) {
                Vec3 leap = horizontal.normalize().scale(
                        Mth.clamp(horizontal.length() * 0.10D, 0.72D, 1.05D));
                setDeltaMovement(leap.x, 0.48D, leap.z);
                hasImpulse = true;
            }
            leapCooldown = 160;
            setActivityState(RimeboundState.RECOVERY);
        }
    }

    private void tickRecovery() {
        if (stateTicks() < RimeboundPolicy.LEAP_RECOVERY_TICKS) {
            getNavigation().stop();
        } else {
            setActivityState(RimeboundState.STALKING);
        }
    }

    private void tickArmorRebuild(ServerLevel level) {
        getNavigation().stop();
        if (stateTicks() % 3 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 1.0D, getZ(), 5,
                    0.42D, 0.72D, 0.42D, 0.025D);
        }
        if (stateTicks() >= RimeboundPolicy.ARMOR_REBUILD_TICKS) {
            setShellIntegrity(RimeboundPolicy.SHELL_MAX_INTEGRITY);
            setActivityState(RimeboundState.STALKING);
        }
    }

    private void tickFreezeWindup(ServerLevel level) {
        freezeWindup--;
        getNavigation().stop();
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                getX(), getY() + 0.25D, getZ(), 6,
                1.1D, 0.2D, 1.1D, 0.03D);
        if (freezeWindup == 0) {
            applyFlashFreeze(level);
            freezeCooldown = 400;
        }
    }

    private void applyFlashFreeze(ServerLevel level) {
        int checks = 0;
        int edits = 0;
        BlockPos center = blockPosition();
        int[] verticalOrder = {0, -1, 1, -2, 2};
        for (int radius = 0; radius <= 6 && checks < 64 && edits < 24; radius++) {
            for (int dx = -radius; dx <= radius && checks < 64 && edits < 24; dx++) {
                for (int dz = -radius; dz <= radius && checks < 64 && edits < 24; dz++) {
                    if ((radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius)
                            || dx * dx + dz * dz > 36) {
                        continue;
                    }
                    for (int dy : verticalOrder) {
                        if (checks++ >= 64 || edits >= 24) {
                            break;
                        }
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (level.isLoaded(pos) && tryFlashFreezeEdit(level, pos)) {
                            edits++;
                        }
                    }
                }
            }
        }
        playSound(ModSounds.RIMEBOUND_FREEZE.get(), 1.45F, 0.86F);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(6.0D), entity -> entity != this)) {
            if (victim instanceof ServerPlayer player) {
                RimeboundEncasement.apply(player, 70.0F);
            } else {
                victim.setTicksFrozen(Math.max(victim.getTicksFrozen(),
                        victim.getTicksRequiredToFreeze() + 20));
                victim.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
            }
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                getX(), getY() + 0.4D, getZ(), 50,
                4.0D, 0.45D, 4.0D, 0.08D);
    }

    private boolean tryFlashFreezeEdit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState replacement = null;
        if (state.is(Blocks.WATER)) {
            replacement = Blocks.ICE.defaultBlockState();
        } else if (state.is(Blocks.SNOW)) {
            int layers = Math.min(8, state.getValue(
                    net.minecraft.world.level.block.SnowLayerBlock.LAYERS) + 1);
            replacement = state.setValue(
                    net.minecraft.world.level.block.SnowLayerBlock.LAYERS, layers);
        } else if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            replacement = Blocks.AIR.defaultBlockState();
        }
        if (replacement == null
                || PlayerPlacedBlockTracker.get(level.getServer()).isPlayerPlaced(pos)
                || ChunkCatchUpManager.isBloomOrsaProtected(level, pos)
                || HearthProtectionPolicy.protectedInteriorAt(
                ReturnedHearthSavedData.get(level.getServer()), pos).isPresent()) {
            return false;
        }
        level.setBlock(pos, replacement, 3);
        return true;
    }

    private static boolean isFrozenTerrain(BlockState state) {
        return state.is(BlockTags.ICE)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(ModBlocks.FROZEN_DIRT.get())
                || state.is(ModBlocks.FROZEN_SAND.get())
                || state.is(ModBlocks.FROZEN_COBBLESTONE.get())
                || state.is(ModBlocks.FROZEN_STONE_BRICKS.get())
                || state.is(ModBlocks.FROZEN_OBSIDIAN.get());
    }

    @Nullable
    private BlockPos preferredBurrowExit(ServerLevel level, @Nullable LivingEntity target) {
        BlockPos anchor = rangedController.preferredAnchor(level, this);
        if (anchor != null) {
            return anchor;
        }
        return target == null ? null : target.blockPosition();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            playSound(ModSounds.RIMEBOUND_ATTACK.get(), 1.15F,
                    0.9F + random.nextFloat() * 0.12F);
            playSound(ModSounds.RIMEBOUND_RESONANCE_ATTACK.get(), 1.0F,
                    0.96F + random.nextFloat() * 0.08F);
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        if (activityState() == RimeboundState.DORMANT) {
            setActivityState(RimeboundState.EMERGING);
        }
        ticksWithoutDamage = 0;
        if (source.is(DamageTypeTags.IS_FIRE)) {
            amount *= 2.25F;
            stripShell(true);
            if (random.nextInt(5) == 0) {
                playSound(ModSounds.RIMEBOUND_FIRE_SCREAM.get(), 1.4F, 0.96F);
                playSound(ModSounds.RIMEBOUND_RESONANCE_HURT.get(), 1.0F, 0.9F);
            }
        } else if (shellIntegrity() > 0) {
            boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
            boolean heavyMelee = !projectile && source.getEntity() instanceof LivingEntity
                    && amount >= 6.0F;
            int damage = RimeboundPolicy.shellDamage(amount, projectile, heavyMelee);
            setShellIntegrity(Math.max(0, shellIntegrity() - damage));
            if (projectile) {
                amount *= 0.70F;
            }
            if (shellIntegrity() <= 0) {
                playSound(ModSounds.RIMEBOUND_SHELL_SHATTER.get(), 1.45F, 0.88F);
            } else {
                playSound(ModSounds.RIMEBOUND_SHELL_CRACK.get(), 1.0F, 1.0F);
            }
        }
        return super.hurt(source, amount);
    }

    private void stripShell(boolean particles) {
        if (shellIntegrity() <= 0) {
            return;
        }
        setShellIntegrity(0);
        if (particles && level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    getX(), getY() + 1.0D, getZ(), 26,
                    0.5D, 0.8D, 0.5D, 0.12D);
        }
        playSound(ModSounds.RIMEBOUND_SHELL_SHATTER.get(), 1.5F, 0.72F);
    }

    private boolean canRunOrdinaryAi() {
        return activityState() == RimeboundState.STALKING && freezeWindup <= 0;
    }

    private void setShellIntegrity(int integrity) {
        int clamped = Mth.clamp(integrity, 0,
                RimeboundPolicy.SHELL_MAX_INTEGRITY);
        entityData.set(DATA_SHELL, clamped);
        AttributeInstance armor = getAttribute(Attributes.ARMOR);
        if (armor != null) {
            armor.removeModifier(SHELL_ARMOR_ID);
            if (clamped > 0) {
                armor.addTransientModifier(new AttributeModifier(
                        SHELL_ARMOR_ID, 4.0D,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    private void tickTerrainControl(ServerLevel level) {
        long now = level.getGameTime();
        if (tickCount % 6 == 0) {
            for (BlockPos brittle : terrainTracker.brittlePositions(now)) {
                level.sendParticles(new BlockParticleOption(
                                ParticleTypes.BLOCK, level.getBlockState(brittle)),
                        brittle.getX() + 0.5D, brittle.getY() + 1.01D,
                        brittle.getZ() + 0.5D, 3,
                        0.32D, 0.015D, 0.32D, 0.025D);
            }
        }
        if (tickCount % 2 == 0) {
            for (Player player : level.getEntitiesOfClass(Player.class,
                    getBoundingBox().inflate(24.0D), player -> !player.isSpectator())) {
                BlockPos under = player.blockPosition().below();
                if (terrainTracker.isTrail(under, now) && player.onGround()) {
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x * 1.055D,
                            motion.y, motion.z * 1.055D);
                    if (tickCount % 8 == 0) {
                        level.sendParticles(ParticleTypes.SNOWFLAKE,
                                player.getX(), player.getY() + 0.05D, player.getZ(),
                                2, 0.22D, 0.02D, 0.22D, 0.01D);
                    }
                }
                if (terrainTracker.isBrittle(under, now) && player.onGround()) {
                    collapseBrittle(level, under, player, now);
                }
            }
        }
    }

    private void collapseBrittle(ServerLevel level, BlockPos ground,
                                 Player player, long now) {
        if (!terrainTracker.consumeBrittle(ground, now)) {
            return;
        }
        BlockPos support = ground.below();
        BlockState state = level.getBlockState(ground);
        boolean safe = level.isLoaded(support)
                && level.getBlockState(support).isSolidRender(level, support)
                && state.is(RimeboundBurrowController.BRITTLE_GROUND)
                && !PlayerPlacedBlockTracker.get(level.getServer()).isPlayerPlaced(ground)
                && !ChunkCatchUpManager.isBloomOrsaProtected(level, ground)
                && HearthProtectionPolicy.protectedInteriorAt(
                ReturnedHearthSavedData.get(level.getServer()), ground).isEmpty();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                ground.getX() + 0.5D, ground.getY() + 0.8D,
                ground.getZ() + 0.5D, 18,
                0.48D, 0.24D, 0.48D, 0.08D);
        if (safe) {
            level.setBlock(ground, Blocks.AIR.defaultBlockState(), 3);
        } else {
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 16, 1));
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x * 0.25D, Math.min(motion.y, 0.0D),
                    motion.z * 0.25D);
        }
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public int getTicksFrozen() {
        return 0;
    }

    @Override
    public void die(DamageSource source) {
        if (activityState() == RimeboundState.DEAD) {
            return;
        }
        super.die(source);
        setActivityState(RimeboundState.DEAD);
        deathPresentationTicks = 0;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel serverLevel) {
            rangedController.discardOwned(serverLevel, this);
        }
    }

    @Override
    protected void tickDeath() {
        deathPresentationTicks++;
        entityData.set(DATA_STATE_TICKS, deathPresentationTicks);
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel serverLevel && deathPresentationTicks < 20
                && deathPresentationTicks % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY() + 1.0D, getZ(), 4,
                    0.34D, 0.68D, 0.34D, 0.015D);
        }
        if (deathPresentationTicks >= RimeboundPolicy.DEATH_FREEZE_TICKS) {
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        getX(), getY() + 0.9D, getZ(), 48,
                        0.65D, 0.9D, 0.65D, 0.16D);
                serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                        getX(), getY() + 0.9D, getZ(), 24,
                        0.75D, 1.0D, 0.75D, 0.08D);
            }
            playSound(ModSounds.RIMEBOUND_DEATH.get(), 1.5F, 0.78F);
            playSound(ModSounds.RIMEBOUND_RESONANCE_DEATH.get(), 1.0F, 1.0F);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void playAmbientSound() {
        playSound(ModSounds.RIMEBOUND_AMBIENT.get(), 1.15F,
                0.78F + random.nextFloat() * 0.08F);
        playSound(ModSounds.RIMEBOUND_RESONANCE_AMBIENT.get(), 1.0F,
                0.96F + random.nextFloat() * 0.06F);
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        playSound(ModSounds.RIMEBOUND_HURT.get(), 1.15F,
                0.82F + random.nextFloat() * 0.1F);
        playSound(ModSounds.RIMEBOUND_RESONANCE_HURT.get(), 1.0F,
                0.96F + random.nextFloat() * 0.08F);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RimeboundState", activityState().ordinal());
        tag.putInt("StateTicks", stateTicks());
        tag.putInt("ShellIntegrity", shellIntegrity());
        tag.putLong("LockedPosition", entityData.get(DATA_LOCKED_POSITION));
        tag.putInt("LanceCooldown", lanceCooldown);
        tag.putInt("BurrowCooldown", burrowCooldown);
        tag.putInt("FreezeCooldown", freezeCooldown);
        tag.putInt("LeapCooldown", leapCooldown);
        tag.putInt("TicksWithoutDamage", ticksWithoutDamage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        RimeboundState loaded = RimeboundState.byOrdinal(tag.getInt("RimeboundState"));
        if (loaded.isUnsafeAfterReload()) {
            loaded = RimeboundState.RECOVERY;
        }
        entityData.set(DATA_STATE, loaded.ordinal());
        entityData.set(DATA_STATE_TICKS, loaded == RimeboundState.RECOVERY
                ? 0 : Math.max(0, tag.getInt("StateTicks")));
        setShellIntegrity(tag.getInt("ShellIntegrity"));
        entityData.set(DATA_LOCKED_POSITION, tag.getLong("LockedPosition"));
        lanceCooldown = Math.max(0, tag.getInt("LanceCooldown"));
        burrowCooldown = Math.max(0, tag.getInt("BurrowCooldown"));
        freezeCooldown = Math.max(0, tag.getInt("FreezeCooldown"));
        leapCooldown = Math.max(0, tag.getInt("LeapCooldown"));
        ticksWithoutDamage = Math.max(0, tag.getInt("TicksWithoutDamage"));
        noPhysics = false;
        setInvisible(false);
    }
}
