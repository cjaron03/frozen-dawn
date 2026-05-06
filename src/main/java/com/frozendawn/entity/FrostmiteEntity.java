package com.frozendawn.entity;

import com.frozendawn.block.MiteAwayBlockEntity;
import com.frozendawn.event.MobFreezeHandler;
import com.frozendawn.init.ModSounds;
import com.frozendawn.world.HeaterRegistry;
import com.frozendawn.world.MiteAwayRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FrostmiteEntity extends Monster {

    private static final Map<UUID, Integer> PLAYER_LATCH_COUNTS = new HashMap<>();

    private static final EntityDataAccessor<Boolean> DATA_LATCHED =
            SynchedEntityData.defineId(FrostmiteEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ON_HEATER =
            SynchedEntityData.defineId(FrostmiteEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int MAX_PLAYER_LATCH = 6;
    private static final int MAX_HEATER_LATCH = 10;
    private static final int HEATER_RADIUS_CAP = 4;
    private static final int HEATER_FUEL_DRAIN_CAP = 10;
    private static final double TARGET_RANGE = 14.0;
    private static final double HEATER_BAIT_RANGE = 18.0;
    private static final double PLAYER_LATCH_RANGE = 0.9;
    private static final double HEATER_LATCH_RANGE = 1.2;
    private static final int RETARGET_INTERVAL = 10;
    private static final int PLAYER_LATCH_DURATION = 140;
    private static final int HEATER_LATCH_DURATION = 200;
    private static final int PLAYER_LATCH_MIN_STICK_TICKS = 40;
    private static final int MITEAWAY_DISENGAGE_DELAY = 30;

    private @Nullable UUID latchedPlayerId;
    private @Nullable BlockPos latchedHeaterPos;
    private @Nullable UUID preferredPlayerId;
    private @Nullable BlockPos preferredHeaterPos;
    private @Nullable BlockPos preferredRepellentPos;
    private int retargetTicks;
    private int latchTicks;
    private float orbitSeed;

    public FrostmiteEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 5.0)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.42)
                .add(Attributes.FOLLOW_RANGE, TARGET_RANGE)
                .add(Attributes.ARMOR, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LATCHED, false);
        builder.define(DATA_ON_HEATER, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData groupData) {
        this.orbitSeed = this.random.nextFloat() * Mth.TWO_PI;
        return super.finalizeSpawn(level, difficulty, spawnType, groupData);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide() && !isLatched() && tickCount % 3 == 0) {
            level().addParticle(ParticleTypes.SNOWFLAKE,
                    getX(), getY(0.45), getZ(),
                    (random.nextDouble() - 0.5) * 0.04,
                    0.01,
                    (random.nextDouble() - 0.5) * 0.04);
        }
    }

    @Override
    protected void customServerAiStep() {
        if (isLatched()) {
            tickLatch();
            return;
        }

        super.customServerAiStep();

        if (--retargetTicks <= 0) {
            retargetTicks = RETARGET_INTERVAL;
            acquirePreferredTargets();
        }

        if (preferredRepellentPos != null) {
            if (MiteAwayRegistry.isProtected(level(), position())) {
                moveAwayFromRepellent(preferredRepellentPos);
                return;
            }
            preferredRepellentPos = null;
        }

        if (preferredHeaterPos != null) {
            moveToHeater(preferredHeaterPos);
            if (distanceToSqr(preferredHeaterPos.getCenter()) <= HEATER_LATCH_RANGE * HEATER_LATCH_RANGE) {
                latchToHeater(preferredHeaterPos);
            }
            return;
        }

        ServerPlayer player = getPreferredPlayer();
        if (player != null) {
            getNavigation().moveTo(player, 1.35);
            if (distanceToSqr(player) <= PLAYER_LATCH_RANGE * PLAYER_LATCH_RANGE) {
                latchToPlayer(player);
            }
            return;
        }

        if (tickCount % 20 == 0) {
            getNavigation().stop();
        }
    }

    private void acquirePreferredTargets() {
        preferredRepellentPos = MiteAwayRegistry.findNearestCoveringBurner(level(), position());
        if (preferredRepellentPos != null) {
            preferredHeaterPos = null;
            preferredPlayerId = null;
            setTarget(null);
            return;
        }

        preferredHeaterPos = findPreferredHeater();
        ServerPlayer player = findPreferredPlayer();
        preferredPlayerId = player != null ? player.getUUID() : null;
        if (preferredHeaterPos != null) {
            setTarget(null);
        } else {
            setTarget(player);
        }
    }

    private void moveToHeater(BlockPos heaterPos) {
        Vec3 target = heaterPos.getCenter().add(0.0, 0.2, 0.0);
        getNavigation().moveTo(target.x, target.y, target.z, 1.25);
    }

    private void moveAwayFromRepellent(BlockPos burnerPos) {
        Vec3 center = burnerPos.getCenter();
        Vec3 direction = new Vec3(getX() - center.x, 0.0, getZ() - center.z);
        if (direction.lengthSqr() < 1.0E-4) {
            float angle = orbitSeed + tickCount * 0.37f;
            direction = new Vec3(Mth.cos(angle), 0.0, Mth.sin(angle));
        }
        Vec3 target = center.add(direction.normalize().scale(MiteAwayBlockEntity.COVERAGE_RADIUS + 3.0));
        getNavigation().moveTo(target.x, getY(), target.z, 1.3);
    }

    private @Nullable ServerPlayer findPreferredPlayer() {
        if (!(level() instanceof ServerLevel serverLevel)) return null;
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPlayerTarget(player)) continue;
            if (MiteAwayRegistry.isProtected(level(), player.position())) continue;
            double dist = distanceToSqr(player);
            if (dist < TARGET_RANGE * TARGET_RANGE && dist < bestDist
                    && countLatchedToPlayer(player) < MAX_PLAYER_LATCH) {
                best = player;
                bestDist = dist;
            }
        }
        return best;
    }

    private @Nullable BlockPos findPreferredHeater() {
        Set<BlockPos> heaters = HeaterRegistry.getHeaters(level());
        if (heaters.isEmpty()) return null;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos heaterPos : heaters) {
            if (!isValidHeaterTarget(heaterPos)) continue;
            if (MiteAwayRegistry.isProtected(level(), heaterPos.getCenter())) continue;
            double dist = distanceToSqr(heaterPos.getCenter());
            if (dist < HEATER_BAIT_RANGE * HEATER_BAIT_RANGE
                    && dist < bestDist
                    && countLatchedToHeater(level(), heaterPos) < MAX_HEATER_LATCH) {
                best = heaterPos.immutable();
                bestDist = dist;
            }
        }
        return best;
    }

    private boolean isValidPlayerTarget(Player player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && MobFreezeHandler.getFullSetTier(player) < 3;
    }

    private boolean isValidHeaterTarget(BlockPos heaterPos) {
        if (!level().isLoaded(heaterPos)) return false;
        if (!HeaterRegistry.getHeaters(level()).contains(heaterPos)) return false;
        BlockEntity be = level().getBlockEntity(heaterPos);
        return be instanceof com.frozendawn.block.ThermalHeaterBlockEntity heater && heater.isLit();
    }

    private void latchToPlayer(ServerPlayer player) {
        if (!isValidPlayerTarget(player) || countLatchedToPlayer(player) >= MAX_PLAYER_LATCH) {
            return;
        }
        clearLatch();
        clearPreferredTargets();
        latchedPlayerId = player.getUUID();
        latchedHeaterPos = null;
        latchTicks = 0;
        entityData.set(DATA_LATCHED, true);
        entityData.set(DATA_ON_HEATER, false);
        PLAYER_LATCH_COUNTS.merge(latchedPlayerId, 1, Integer::sum);
        setNoGravity(true);
        noPhysics = true;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        player.hurt(damageSources().mobAttack(this), 1.0f);
    }

    private void latchToHeater(BlockPos heaterPos) {
        if (!isValidHeaterTarget(heaterPos) || countLatchedToHeater(level(), heaterPos) >= MAX_HEATER_LATCH) {
            return;
        }
        clearLatch();
        clearPreferredTargets();
        latchedPlayerId = null;
        latchedHeaterPos = heaterPos.immutable();
        latchTicks = 0;
        entityData.set(DATA_LATCHED, true);
        entityData.set(DATA_ON_HEATER, true);
        setNoGravity(true);
        noPhysics = true;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
    }

    private void tickLatch() {
        if (latchedPlayerId != null) {
            tickPlayerLatch();
        } else if (latchedHeaterPos != null) {
            tickHeaterLatch();
        } else {
            clearLatch();
        }
    }

    private void tickPlayerLatch() {
        if (!(level() instanceof ServerLevel serverLevel) || latchedPlayerId == null) {
            clearLatch();
            return;
        }

        Player player = serverLevel.getPlayerByUUID(latchedPlayerId);
        if (!(player instanceof ServerPlayer serverPlayer) || !isValidPlayerTarget(serverPlayer)) {
            clearLatch();
            return;
        }

        if (latchTicks >= MITEAWAY_DISENGAGE_DELAY) {
            BlockPos repellent = MiteAwayRegistry.findNearestCoveringBurner(level(), serverPlayer.position());
            if (repellent != null) {
                clearLatch();
                preferredRepellentPos = repellent;
                return;
            }
        }

        BlockPos heater = latchTicks >= PLAYER_LATCH_MIN_STICK_TICKS ? findPreferredHeater() : null;
        if (heater != null && serverPlayer.blockPosition().closerToCenterThan(heater.getCenter(), 4.5)) {
            clearLatch();
            preferredHeaterPos = heater;
            return;
        }

        latchTicks++;
        if (latchTicks > PLAYER_LATCH_DURATION) {
            clearLatch();
            return;
        }

        double angle = orbitSeed + tickCount * 0.4;
        double radius = 0.35;
        double x = serverPlayer.getX() + Math.cos(angle) * radius;
        double y = serverPlayer.getY() + 0.55 + Math.sin(tickCount * 0.18) * 0.08;
        double z = serverPlayer.getZ() + Math.sin(angle) * radius;
        moveTo(x, y, z, getYRot(), getXRot());
        setDeltaMovement(Vec3.ZERO);
    }

    private void tickHeaterLatch() {
        if (latchedHeaterPos == null || !isValidHeaterTarget(latchedHeaterPos)) {
            clearLatch();
            return;
        }

        if (latchTicks >= MITEAWAY_DISENGAGE_DELAY
                && MiteAwayRegistry.isProtected(level(), latchedHeaterPos.getCenter())) {
            clearLatch();
            preferredRepellentPos = latchedHeaterPos;
            return;
        }

        latchTicks++;
        if (latchTicks > HEATER_LATCH_DURATION) {
            clearLatch();
            return;
        }

        Vec3 center = latchedHeaterPos.getCenter();
        double angle = orbitSeed + tickCount * 0.3;
        double radius = 0.45;
        double x = center.x + Math.cos(angle) * radius;
        double y = center.y + 0.15 + Math.sin(tickCount * 0.12) * 0.08;
        double z = center.z + Math.sin(angle) * radius;
        moveTo(x, y, z, getYRot(), getXRot());
        setDeltaMovement(Vec3.ZERO);
    }

    private void clearPreferredTargets() {
        preferredPlayerId = null;
        preferredHeaterPos = null;
        preferredRepellentPos = null;
        setTarget(null);
    }

    private void clearLatch() {
        if (latchedPlayerId != null) {
            PLAYER_LATCH_COUNTS.compute(latchedPlayerId, (id, count) -> {
                if (count == null || count <= 1) return null;
                return count - 1;
            });
        }
        latchedPlayerId = null;
        latchedHeaterPos = null;
        latchTicks = 0;
        entityData.set(DATA_LATCHED, false);
        entityData.set(DATA_ON_HEATER, false);
        setNoGravity(false);
        noPhysics = false;
    }

    public boolean isLatched() {
        return entityData.get(DATA_LATCHED);
    }

    public boolean isLatchedToHeater() {
        return entityData.get(DATA_ON_HEATER) && latchedHeaterPos != null;
    }

    public boolean isLatchedToPlayer(UUID playerId) {
        return isLatched() && latchedPlayerId != null && latchedPlayerId.equals(playerId);
    }

    public boolean isLatchedToHeater(BlockPos heaterPos) {
        return isLatchedToHeater() && latchedHeaterPos != null && latchedHeaterPos.equals(heaterPos);
    }

    private @Nullable ServerPlayer getPreferredPlayer() {
        if (!(level() instanceof ServerLevel serverLevel) || preferredPlayerId == null) return null;
        Player player = serverLevel.getPlayerByUUID(preferredPlayerId);
        return player instanceof ServerPlayer serverPlayer && isValidPlayerTarget(serverPlayer) ? serverPlayer : null;
    }

    public static int countLatchedToPlayer(ServerPlayer player) {
        Integer tracked = PLAYER_LATCH_COUNTS.get(player.getUUID());
        if (tracked != null && tracked > 0) {
            return tracked;
        }
        AABB box = player.getBoundingBox().inflate(6.0);
        return player.level().getEntitiesOfClass(FrostmiteEntity.class, box,
                mite -> mite.isAlive() && mite.isLatchedToPlayer(player.getUUID())).size();
    }

    public static void resetAttachmentTracking() {
        PLAYER_LATCH_COUNTS.clear();
    }

    public static int countLatchedToHeater(Level level, BlockPos heaterPos) {
        AABB box = new AABB(heaterPos).inflate(2.0);
        return level.getEntitiesOfClass(FrostmiteEntity.class, box,
                mite -> mite.isAlive() && mite.isLatchedToHeater(heaterPos)).size();
    }

    public static float getPlayerTemperatureDrain(ServerPlayer player) {
        return 0.0f;
    }

    public static int getHeaterRadiusPenalty(Level level, BlockPos heaterPos) {
        int attached = countLatchedToHeater(level, heaterPos);
        if (attached <= 0) {
            return 0;
        }
        return Math.min(HEATER_RADIUS_CAP, (attached + 1) / 2);
    }

    public static int getHeaterFuelDrain(Level level, BlockPos heaterPos) {
        int attached = countLatchedToHeater(level, heaterPos);
        if (attached <= 0) {
            return 0;
        }
        return Math.min(HEATER_FUEL_DRAIN_CAP, attached);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        clearLatch();
        if (source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void tickDeath() {
        remove(RemovalReason.KILLED);
    }

    @Override
    public void remove(RemovalReason reason) {
        clearLatch();
        super.remove(reason);
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
    protected SoundEvent getAmbientSound() {
        return ModSounds.FROSTMITE_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.FROSTMITE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FROSTMITE_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(ModSounds.FROSTMITE_STEP.get(), 0.07f, 1.0f);
    }
}
