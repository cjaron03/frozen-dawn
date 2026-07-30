package com.frozendawn.entity;

import com.frozendawn.homo.HeartSuccessorPolicy;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The unfinished, node-bound focal point assembled after the third erasure. */
public final class HeartSuccessorEntity extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> DATA_HEARTH_ID =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_BOUND_NODE =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GENERATION =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_ASSEMBLY_START =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_ANCHOR =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_LAYOUT_SEED =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_FIELD_STRENGTH =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STAGGER_TICKS =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DEATH_TICKS =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HEAL_TARGET_ID =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LINK_TARGET_0 =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LINK_TARGET_1 =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LINK_TARGET_2 =
            SynchedEntityData.defineId(HeartSuccessorEntity.class,
                    EntityDataSerializers.INT);

    private float disruptionDamage;
    private int voiceCooldown = 160;

    public HeartSuccessorEntity(
            EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void registerGoals() {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEARTH_ID, Optional.empty());
        builder.define(DATA_BOUND_NODE, -1);
        builder.define(DATA_GENERATION, 0);
        builder.define(DATA_ASSEMBLY_START, 0L);
        builder.define(DATA_ANCHOR, BlockPos.ZERO.asLong());
        builder.define(DATA_LAYOUT_SEED, 0L);
        builder.define(DATA_FIELD_STRENGTH, 0.0F);
        builder.define(DATA_MODE, HeartSuccessorPolicy.Mode.ASSEMBLING.ordinal());
        builder.define(DATA_STAGGER_TICKS, 0);
        builder.define(DATA_DEATH_TICKS, 0);
        builder.define(DATA_HEAL_TARGET_ID, -1);
        builder.define(DATA_LINK_TARGET_0, -1);
        builder.define(DATA_LINK_TARGET_1, -1);
        builder.define(DATA_LINK_TARGET_2, -1);
    }

    public void configure(
            UUID hearthId, int boundNode, int generation, long assemblyStart,
            BlockPos anchor, long layoutSeed, float fieldStrength) {
        entityData.set(DATA_HEARTH_ID, Optional.ofNullable(hearthId));
        entityData.set(DATA_BOUND_NODE, boundNode);
        entityData.set(DATA_GENERATION, Math.max(0, generation));
        entityData.set(DATA_ASSEMBLY_START, Math.max(0L, assemblyStart));
        entityData.set(DATA_ANCHOR, anchor.asLong());
        entityData.set(DATA_LAYOUT_SEED, layoutSeed);
        entityData.set(DATA_FIELD_STRENGTH,
                net.minecraft.util.Mth.clamp(fieldStrength, 0.0F, 1.0F));
        setPersistenceRequired();
    }

    public Optional<UUID> hearthId() {
        return entityData.get(DATA_HEARTH_ID);
    }

    public int boundNode() {
        return entityData.get(DATA_BOUND_NODE);
    }

    public int generation() {
        return entityData.get(DATA_GENERATION);
    }

    public long assemblyStartGameTime() {
        return entityData.get(DATA_ASSEMBLY_START);
    }

    public BlockPos heartAnchor() {
        return BlockPos.of(entityData.get(DATA_ANCHOR));
    }

    public long layoutSeed() {
        return entityData.get(DATA_LAYOUT_SEED);
    }

    public float fieldStrength() {
        return entityData.get(DATA_FIELD_STRENGTH);
    }

    public HeartSuccessorPolicy.Mode mode() {
        HeartSuccessorPolicy.Mode[] values = HeartSuccessorPolicy.Mode.values();
        int ordinal = entityData.get(DATA_MODE);
        return values[Math.max(0, Math.min(values.length - 1, ordinal))];
    }

    public void setMode(HeartSuccessorPolicy.Mode mode) {
        entityData.set(DATA_MODE, mode.ordinal());
    }

    public int staggerTicks() {
        return entityData.get(DATA_STAGGER_TICKS);
    }

    public void setStaggerTicks(int ticks) {
        entityData.set(DATA_STAGGER_TICKS, Math.max(0, ticks));
    }

    public int deathTicks() {
        return entityData.get(DATA_DEATH_TICKS);
    }

    public boolean isDying() {
        return deathTicks() > 0;
    }

    public float deathProgress(float partialTick) {
        if (!isDying()) {
            return 0.0F;
        }
        return Mth.clamp((deathTicks() + partialTick)
                / HeartSuccessorPolicy.DEATH_TICKS, 0.0F, 1.0F);
    }

    public int healTargetId() {
        return entityData.get(DATA_HEAL_TARGET_ID);
    }

    public void setHealTargetId(int entityId) {
        entityData.set(DATA_HEAL_TARGET_ID, entityId);
    }

    public List<Integer> linkTargetIds() {
        return List.of(
                entityData.get(DATA_LINK_TARGET_0),
                entityData.get(DATA_LINK_TARGET_1),
                entityData.get(DATA_LINK_TARGET_2));
    }

    public void setLinkTargetIds(List<? extends Entity> targets) {
        entityData.set(DATA_LINK_TARGET_0,
                targets.size() > 0 ? targets.get(0).getId() : -1);
        entityData.set(DATA_LINK_TARGET_1,
                targets.size() > 1 ? targets.get(1).getId() : -1);
        entityData.set(DATA_LINK_TARGET_2,
                targets.size() > 2 ? targets.get(2).getId() : -1);
    }

    public void clearLinkTargets() {
        entityData.set(DATA_LINK_TARGET_0, -1);
        entityData.set(DATA_LINK_TARGET_1, -1);
        entityData.set(DATA_LINK_TARGET_2, -1);
    }

    public void steerToward(Vec3 target) {
        if (level().isClientSide() || target == null) {
            return;
        }
        Vec3 delta = target.subtract(position());
        double distance = delta.length();
        if (distance < 0.18D) {
            setDeltaMovement(getDeltaMovement().scale(0.72D));
            return;
        }
        double speed = Mth.clamp(
                distance * 0.032D, 0.045D, 0.19D);
        Vec3 desired = delta.scale(speed / distance);
        Vec3 steering = getDeltaMovement().scale(0.84D)
                .add(desired.scale(0.16D));
        steering = collisionAwareMotion(steering, delta);
        setDeltaMovement(steering);
        hasImpulse = true;
    }

    public void turnToward(Vec3 target) {
        if (target == null) {
            return;
        }
        Vec3 delta = target.subtract(position());
        if (delta.horizontalDistanceSqr() < 0.0001D) {
            return;
        }
        float targetYaw = (float) (Math.atan2(delta.z, delta.x)
                * 180.0D / Math.PI) - 90.0F;
        float bodyYaw = Mth.approachDegrees(getYRot(), targetYaw, 4.0F);
        setYRot(bodyYaw);
        setYBodyRot(Mth.approachDegrees(yBodyRot, targetYaw, 3.0F));
        setYHeadRot(Mth.approachDegrees(getYHeadRot(), targetYaw, 7.5F));
    }

    private Vec3 collisionAwareMotion(Vec3 motion, Vec3 targetDelta) {
        if (pathClear(motion)) {
            return motion;
        }
        Vec3 horizontal = targetDelta.multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.horizontalDistanceSqr() < 0.0001D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            horizontal = horizontal.normalize();
        }
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        Vec3[] detours = {
                new Vec3(motion.x * 0.45D, 0.16D, motion.z * 0.45D),
                side.scale(0.13D).add(0.0D, 0.07D, 0.0D),
                side.scale(-0.13D).add(0.0D, 0.07D, 0.0D),
                new Vec3(0.0D, 0.18D, 0.0D)
        };
        for (Vec3 detour : detours) {
            if (pathClear(detour)) {
                return getDeltaMovement().scale(0.38D)
                        .add(detour.scale(0.62D));
            }
        }
        return Vec3.ZERO;
    }

    private boolean pathClear(Vec3 motion) {
        for (int step = 1; step <= 5; step++) {
            if (!level().noCollision(this,
                    getBoundingBox().move(motion.scale(step)))) {
                return false;
            }
        }
        return true;
    }

    public float assemblyProgress(float partialTick) {
        float elapsed = level().getGameTime() + partialTick - assemblyStartGameTime();
        return net.minecraft.util.Mth.clamp(
                elapsed / HeartSuccessorPolicy.ASSEMBLY_TICKS, 0.0F, 1.0F);
    }

    public void tickStagger() {
        int ticks = staggerTicks();
        if (ticks > 0) {
            setStaggerTicks(ticks - 1);
        }
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = false;
        if (isDying()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
                entityData.set(DATA_DEATH_TICKS,
                        Math.min(HeartSuccessorPolicy.DEATH_TICKS,
                                deathTicks() + 1));
                int ticks = deathTicks();
                if ((ticks & 1) == 0) {
                    double spread = 0.18D + ticks * 0.012D;
                    serverLevel.sendParticles(ticks % 6 == 0
                                    ? ParticleTypes.REVERSE_PORTAL
                                    : ParticleTypes.SCULK_SOUL,
                            getX(), getY() + 1.25D, getZ(),
                            4, spread, 0.7D + spread, spread, 0.025D);
                }
            }
            return;
        }
        if (!level().isClientSide()) {
            setDeltaMovement(getDeltaMovement().scale(0.985D));
        }
        if (!level().isClientSide() && mode() == HeartSuccessorPolicy.Mode.ASSEMBLING
                && tickCount % 5 == 0 && level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY() + 1.3D, getZ(), 3,
                    2.2D, 1.8D, 2.2D, 0.02D);
        }
        if (!level().isClientSide() && mode() != HeartSuccessorPolicy.Mode.ASSEMBLING
                && mode() != HeartSuccessorPolicy.Mode.STAGGERED) {
            tickVoice();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(source.getEntity() instanceof Player) || amount <= 0.0F
                || mode() == HeartSuccessorPolicy.Mode.ASSEMBLING || isDying()) {
            return false;
        }
        if (level().isClientSide()) {
            return true;
        }
        float healthBefore = getHealth();
        boolean lethal = amount + 0.001F >= healthBefore;
        if (lethal) {
            beginDeathSequence();
            return true;
        }
        if (!super.hurt(source, amount)) {
            return false;
        }
        disruptionDamage += amount;
        if (disruptionDamage >= HeartSuccessorPolicy.staggerThreshold(
                generation(), fieldStrength())) {
            disruptionDamage = 0.0F;
            setStaggerTicks(HeartSuccessorPolicy.STAGGER_TICKS);
            setMode(HeartSuccessorPolicy.Mode.STAGGERED);
            setHealTargetId(-1);
            clearLinkTargets();
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL,
                        getX(), getY() + 1.4D, getZ(), 34,
                        0.9D, 1.2D, 0.9D, 0.08D);
                serverLevel.playSound(null, blockPosition(),
                        ModSounds.HEART_SUCCESSOR_STAGGER.get(),
                        SoundSource.HOSTILE, 1.8F, 0.72F);
            }
        }
        return true;
    }

    private void beginDeathSequence() {
        setHealth(1.0F);
        entityData.set(DATA_DEATH_TICKS, 1);
        setMode(HeartSuccessorPolicy.Mode.STAGGERED);
        setStaggerTicks(0);
        setHealTargetId(-1);
        clearLinkTargets();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(),
                    ModSounds.HEART_SUCCESSOR_DEATH.get(),
                    SoundSource.HOSTILE, 2.5F, 0.74F);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL,
                    getX(), getY() + 1.25D, getZ(), 36,
                    0.65D, 1.25D, 0.65D, 0.08D);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance <= 384.0D * 384.0D;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        configure(
                tag.hasUUID("HearthId") ? tag.getUUID("HearthId") : null,
                tag.getInt("BoundNode"),
                tag.getInt("Generation"),
                tag.getLong("AssemblyStart"),
                tag.contains("HeartAnchor")
                        ? BlockPos.of(tag.getLong("HeartAnchor")) : BlockPos.ZERO,
                tag.getLong("LayoutSeed"),
                tag.getFloat("FieldStrength"));
        setMode(readMode(tag.getString("Mode")));
        setStaggerTicks(tag.getInt("StaggerTicks"));
        entityData.set(DATA_DEATH_TICKS,
                Math.max(0, tag.getInt("DeathTicks")));
        disruptionDamage = Math.max(0.0F, tag.getFloat("DisruptionDamage"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        hearthId().ifPresent(id -> tag.putUUID("HearthId", id));
        tag.putInt("BoundNode", boundNode());
        tag.putInt("Generation", generation());
        tag.putLong("AssemblyStart", assemblyStartGameTime());
        tag.putLong("HeartAnchor", heartAnchor().asLong());
        tag.putLong("LayoutSeed", layoutSeed());
        tag.putFloat("FieldStrength", fieldStrength());
        tag.putString("Mode", mode().name());
        tag.putInt("StaggerTicks", staggerTicks());
        tag.putInt("DeathTicks", deathTicks());
        tag.putFloat("DisruptionDamage", disruptionDamage);
    }

    private static HeartSuccessorPolicy.Mode readMode(String name) {
        try {
            return HeartSuccessorPolicy.Mode.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return HeartSuccessorPolicy.Mode.ASSEMBLING;
        }
    }

    private void tickVoice() {
        if (--voiceCooldown > 0) {
            return;
        }
        voiceCooldown = 220 + random.nextInt(260);
        var sound = switch (random.nextInt(5)) {
            case 0 -> ModSounds.HEART_SUCCESSOR_VOICE_WHY.get();
            case 1 -> ModSounds.HEART_SUCCESSOR_VOICE_COLD.get();
            case 2 -> ModSounds.HEART_SUCCESSOR_VOICE_HELLO.get();
            case 3 -> ModSounds.HEART_SUCCESSOR_VOICE_KEVIN.get();
            default -> ModSounds.HEART_SUCCESSOR_VOICE_DONT.get();
        };
        playSound(sound, 2.4F, 0.86F + random.nextFloat() * 0.08F);
    }
}
