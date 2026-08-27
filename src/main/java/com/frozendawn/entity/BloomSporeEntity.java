package com.frozendawn.entity;

import com.frozendawn.bloom.BloomSporeManager;
import com.frozendawn.bloom.BloomSporePolicy;
import com.frozendawn.init.ModSounds;
import com.frozendawn.init.ModParticles;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A Bloom-grown body committed to one outward bearing, without intent or aggression. */
public final class BloomSporeEntity extends PathfinderMob {
    private static final EntityDataAccessor<Float> DATA_HEADING_X =
            SynchedEntityData.defineId(BloomSporeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEADING_Z =
            SynchedEntityData.defineId(BloomSporeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLLAPSE_TICKS =
            SynchedEntityData.defineId(BloomSporeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PAUSED =
            SynchedEntityData.defineId(BloomSporeEntity.class, EntityDataSerializers.BOOLEAN);

    private final Map<UUID, Long> contactUntil = new HashMap<>();
    private UUID sourceId = new UUID(0L, 0L);
    private UUID lineageId = new UUID(0L, 0L);
    private BlockPos sourceAnchor = BlockPos.ZERO;
    private double sourceEdgeRadius;
    private int pauseTicks;
    private int nextPauseTicks;
    private int pathRefreshTicks;
    private int trailCooldown;
    private Vec3 lastTrailPosition = Vec3.ZERO;
    private final ArrayDeque<BlockPos> trailRequests = new ArrayDeque<>();
    private int collapsePatchEdits;

    public BloomSporeEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        xpReward = 0;
        nextPauseTicks = 400 + random.nextInt(501);
        trailCooldown = 120 + random.nextInt(81);
        getNavigation().getNodeEvaluator().setCanFloat(true);
        getNavigation().getNodeEvaluator().setCanOpenDoors(false);
        getNavigation().getNodeEvaluator().setCanPassDoors(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEADING_X, 0.0F);
        builder.define(DATA_HEADING_Z, 1.0F);
        builder.define(DATA_COLLAPSE_TICKS, -1);
        builder.define(DATA_PAUSED, false);
    }

    public void bindSource(UUID sourceId, UUID lineageId, BlockPos sourceAnchor,
                           double sourceEdgeRadius, Vec3 heading) {
        this.sourceId = sourceId;
        this.lineageId = lineageId;
        this.sourceAnchor = sourceAnchor.immutable();
        this.sourceEdgeRadius = Math.max(0.0D, sourceEdgeRadius);
        Vec3 flat = new Vec3(heading.x, 0.0D, heading.z).normalize();
        if (flat.lengthSqr() < 0.1D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        entityData.set(DATA_HEADING_X, (float) flat.x);
        entityData.set(DATA_HEADING_Z, (float) flat.z);
        lastTrailPosition = position();
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getLineageId() {
        return lineageId;
    }

    public Vec3 getOutwardHeading() {
        return new Vec3(entityData.get(DATA_HEADING_X), 0.0D,
                entityData.get(DATA_HEADING_Z)).normalize();
    }

    public boolean isRooting() {
        return entityData.get(DATA_COLLAPSE_TICKS) >= 0;
    }

    public int getRootingTicks() {
        return Math.max(0, entityData.get(DATA_COLLAPSE_TICKS));
    }

    public boolean isSignalPaused() {
        return entityData.get(DATA_PAUSED);
    }

    public float rootingProgress(float partialTick) {
        if (!isRooting()) {
            return 0.0F;
        }
        return Mth.clamp((getRootingTicks() + partialTick)
                / BloomSporePolicy.COLLAPSE_TICKS, 0.0F, 1.0F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (isRooting()) {
            tickRooting(server);
            return;
        }

        setYHeadRot(getYRot());
        setXRot(0.0F);
        if (tickCount % 20 == 0) {
            BloomSporeManager.updateSporePosition(
                    server, sourceId, getUUID(), blockPosition());
        }
        if (horizontalDistanceToSource() - sourceEdgeRadius
                >= BloomSporePolicy.ESCAPE_DISTANCE) {
            beginRooting();
            return;
        }

        tickContacts(server);
        tickTrailRequest();
        if (--nextPauseTicks <= 0) {
            pauseTicks = 20 + random.nextInt(21);
            nextPauseTicks = 400 + random.nextInt(501);
            entityData.set(DATA_PAUSED, true);
            getNavigation().stop();
        }
        if (pauseTicks > 0) {
            pauseTicks--;
            setDeltaMovement(0.0D, getDeltaMovement().y, 0.0D);
            if (pauseTicks == 0) {
                entityData.set(DATA_PAUSED, false);
                pathRefreshTicks = 0;
            }
            return;
        }

        if (--pathRefreshTicks <= 0 || getNavigation().isDone()) {
            refreshOutwardPath(server);
            pathRefreshTicks = 14;
        }
        if (getNavigation().isDone() && onGround()) {
            Vec3 heading = getOutwardHeading();
            setDeltaMovement(getDeltaMovement().add(
                    heading.x * 0.045D, 0.0D, heading.z * 0.045D));
        }
        if (random.nextInt(420) == 0) {
            playSound(ModSounds.BLOOM_SPORE_AMBIENT.get(), 0.65F,
                    0.96F + random.nextFloat() * 0.08F);
        }
    }

    private void refreshOutwardPath(ServerLevel level) {
        Vec3 heading = getOutwardHeading();
        Path fallback = null;
        double fallbackProgress = Double.NEGATIVE_INFINITY;
        int[] distances = {18, 14, 10};
        int[] sides = {0, -3, 3};
        for (int distance : distances) {
            for (int side : sides) {
                int x = Mth.floor(getX() + heading.x * distance - heading.z * side);
                int z = Mth.floor(getZ() + heading.z * distance + heading.x * side);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos target = level.getHeightmapPos(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, 0, z));
                Path path = getNavigation().createPath(target, 0);
                if (path == null) {
                    continue;
                }
                if (path.canReach()) {
                    getNavigation().moveTo(path, 1.0D);
                    return;
                }
                BlockPos end = path.getEndNode() == null
                        ? blockPosition() : path.getEndNode().asBlockPos();
                double progress = (end.getX() - getX()) * heading.x
                        + (end.getZ() - getZ()) * heading.z;
                if (progress > fallbackProgress) {
                    fallback = path;
                    fallbackProgress = progress;
                }
            }
        }
        if (fallback != null && fallbackProgress > 1.0D) {
            getNavigation().moveTo(fallback, 1.0D);
        }
    }

    private void tickTrailRequest() {
        if (trailCooldown > 0) {
            trailCooldown--;
        }
        if (!trailRequests.isEmpty() || trailCooldown > 0
                || lastTrailPosition == Vec3.ZERO
                || position().distanceToSqr(lastTrailPosition) < 25.0D) {
            return;
        }
        Vec3 heading = getOutwardHeading();
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            int side = i - (count - 1) / 2;
            trailRequests.addLast(BlockPos.containing(
                    getX() - heading.x * (1.0D + i * 0.7D) - heading.z * side,
                    getY(),
                    getZ() - heading.z * (1.0D + i * 0.7D) + heading.x * side));
        }
        lastTrailPosition = position();
        trailCooldown = 120 + random.nextInt(81);
    }

    @Nullable
    public BlockPos pollTrailRequest() {
        return trailRequests.pollFirst();
    }

    public void onTrailPlaced() {
        if (isRooting()) {
            collapsePatchEdits = Math.min(3, collapsePatchEdits + 1);
        }
    }

    public int getCollapsePatchEdits() {
        return collapsePatchEdits;
    }

    private void tickContacts(ServerLevel level) {
        contactUntil.entrySet().removeIf(entry -> entry.getValue() <= level.getGameTime());
        AABB contact = getBoundingBox().inflate(0.18D, 0.08D, 0.18D);
        for (Player player : level.getEntitiesOfClass(Player.class, contact,
                candidate -> candidate.isAlive() && !candidate.isCreative()
                        && !candidate.isSpectator())) {
            if (contactUntil.containsKey(player.getUUID())) {
                continue;
            }
            player.hurt(level.damageSources().magic(), 2.0F);
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    BloomSporePolicy.CONTACT_SLOW_TICKS, 0));
            contactUntil.put(player.getUUID(), level.getGameTime()
                    + BloomSporePolicy.CONTACT_COOLDOWN_TICKS);
            playSound(ModSounds.BLOOM_SPORE_CONTACT.get(), 0.9F, 0.94F);
            level.sendParticles(ParticleTypes.WAX_ON,
                    player.getX(), player.getY() + 0.9D, player.getZ(),
                    20, 0.3D, 0.55D, 0.3D, 0.025D);
        }
    }

    public boolean beginRooting() {
        if (isRooting() || level().isClientSide()) {
            return false;
        }
        entityData.set(DATA_COLLAPSE_TICKS, 0);
        entityData.set(DATA_PAUSED, false);
        trailRequests.clear();
        collapsePatchEdits = 0;
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        if (level() instanceof ServerLevel server) {
            snapToSupport(server);
            queueImmediateRootPatch();
            emitRootingBurst(server);
        }
        setInvulnerable(true);
        playSound(ModSounds.BLOOM_SPORE_DEATH.get(), 1.6F, 0.94F);
        playSound(ModSounds.BLOOM_SPORE_GROWTH_START.get(), 1.4F, 0.78F);
        return true;
    }

    private void queueImmediateRootPatch() {
        BlockPos center = blockPosition();
        Vec3 heading = getOutwardHeading();
        int sideX = Math.abs(heading.z) >= 0.35D ? (heading.z > 0.0D ? 1 : -1) : 0;
        int sideZ = Math.abs(heading.x) >= 0.35D ? (heading.x > 0.0D ? -1 : 1) : 0;
        BlockPos[] candidates = {
                center,
                center.offset(sideX * 2, 0, sideZ * 2),
                center.offset(-sideX * 2, 0, -sideZ * 2)
        };
        for (int i = 0; i < Math.min(BloomSporePolicy.IMMEDIATE_ROOT_TIPS,
                candidates.length); i++) {
            trailRequests.addLast(candidates[i]);
        }
    }

    private void emitRootingBurst(ServerLevel level) {
        level.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                getX(), getY() + 0.12D, getZ(), 52,
                0.24D, 0.10D, 0.24D, 0.12D);
    }

    private void tickRooting(ServerLevel level) {
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        int ticks = getRootingTicks() + 1;
        entityData.set(DATA_COLLAPSE_TICKS, ticks);
        if (ticks <= BloomSporePolicy.ROOT_SHOCK_TICKS) {
            emitRootShockRing(level, ticks);
        }
        if (ticks == BloomSporePolicy.COLLAPSE_IMPACT_TICKS) {
            playSound(ModSounds.BLOOM_SPORE_COLLAPSE.get(), 0.95F, 1.05F);
        }
        if (ticks >= BloomSporePolicy.COLLAPSE_IMPACT_TICKS && ticks % 2 == 0) {
            int count = 3 + ticks / BloomSporePolicy.ROOT_PATCH_INTERVAL;
            level.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    getX(), getY() + 0.10D, getZ(), count,
                    0.58D, 0.08D, 0.58D, 0.028D);
        }
        if (ticks % BloomSporePolicy.ROOT_PATCH_INTERVAL == 0
                && collapsePatchEdits + trailRequests.size() < 3) {
            Vec3 heading = getOutwardHeading();
            trailRequests.addLast(blockPosition().offset(
                    Mth.floor(heading.z
                            * ((ticks / BloomSporePolicy.ROOT_PATCH_INTERVAL) % 3 - 1)),
                    0,
                    Mth.floor(-heading.x
                            * ((ticks / BloomSporePolicy.ROOT_PATCH_INTERVAL) % 3 - 1))));
        }
        if (ticks >= BloomSporePolicy.COLLAPSE_TICKS) {
            level.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    getX(), getY() + 0.16D, getZ(), 34,
                    0.85D, 0.14D, 0.85D, 0.045D);
            BloomSporeManager.completeRooting(level, this);
        }
    }

    private void emitRootShockRing(ServerLevel level, int ticks) {
        double radius = 0.20D + ticks * 0.24D;
        double y = getY() + 0.06D;
        for (int spoke = 0; spoke < 12; spoke++) {
            double angle = spoke * Mth.TWO_PI / 12.0D;
            level.sendParticles(ModParticles.BLOOM_SPORE_ROOTING.get(),
                    getX() + Math.cos(angle) * radius,
                    y,
                    getZ() + Math.sin(angle) * radius,
                    2, 0.05D, 0.025D, 0.05D, 0.018D);
        }
    }

    private void snapToSupport(ServerLevel level) {
        int x = Mth.floor(getX());
        int z = Mth.floor(getZ());
        int startY = Math.min(Mth.floor(getY() + 0.2D), level.getMaxBuildHeight() - 1);
        int stopY = Math.max(level.getMinBuildHeight(), startY - 48);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, startY, z);
        for (int y = startY; y >= stopY; y--) {
            cursor.setY(y);
            if (!level.isLoaded(cursor)) {
                break;
            }
            VoxelShape collision = level.getBlockState(cursor)
                    .getCollisionShape(level, cursor);
            if (collision.isEmpty()) {
                continue;
            }
            double supportY = y + collision.max(Direction.Axis.Y);
            if (supportY <= getY() + 0.65D) {
                moveTo(getX(), supportY + 0.02D, getZ(), getYRot(), getXRot());
                setNoGravity(true);
                return;
            }
        }
    }

    private double horizontalDistanceToSource() {
        double dx = getX() - (sourceAnchor.getX() + 0.5D);
        double dz = getZ() - (sourceAnchor.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRooting() || source.is(DamageTypeTags.IS_FREEZING)) {
            return false;
        }
        float previous = getHealth();
        boolean hurt = super.hurt(source, amount);
        if (hurt && isAlive() && getHealth() <= 0.01F) {
            setHealth(1.0F);
            beginRooting();
        } else if (hurt && amount >= previous) {
            setHealth(1.0F);
            beginRooting();
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!isRooting() && !level().isClientSide()) {
            setHealth(1.0F);
            beginRooting();
            return;
        }
        super.die(source);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.BLOOM_SPORE_STEP.get(), 0.48F, 0.96F);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BLOOM_SPORE_CONTACT.get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source,
                                       boolean recentlyHit) {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !isRooting();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putUUID("sourceId", sourceId);
        tag.putUUID("lineageId", lineageId);
        tag.putLong("sourceAnchor", sourceAnchor.asLong());
        tag.putDouble("sourceEdgeRadius", sourceEdgeRadius);
        tag.putFloat("headingX", entityData.get(DATA_HEADING_X));
        tag.putFloat("headingZ", entityData.get(DATA_HEADING_Z));
        tag.putInt("collapseTicks", entityData.get(DATA_COLLAPSE_TICKS));
        tag.putInt("pauseTicks", pauseTicks);
        tag.putInt("nextPauseTicks", nextPauseTicks);
        tag.putInt("trailCooldown", trailCooldown);
        tag.putDouble("lastTrailX", lastTrailPosition.x);
        tag.putDouble("lastTrailY", lastTrailPosition.y);
        tag.putDouble("lastTrailZ", lastTrailPosition.z);
        tag.putInt("collapsePatchEdits", collapsePatchEdits);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        sourceId = tag.hasUUID("sourceId") ? tag.getUUID("sourceId") : new UUID(0L, 0L);
        lineageId = tag.hasUUID("lineageId") ? tag.getUUID("lineageId") : sourceId;
        sourceAnchor = tag.contains("sourceAnchor")
                ? BlockPos.of(tag.getLong("sourceAnchor")) : blockPosition();
        sourceEdgeRadius = Math.max(0.0D, tag.getDouble("sourceEdgeRadius"));
        entityData.set(DATA_HEADING_X, tag.getFloat("headingX"));
        entityData.set(DATA_HEADING_Z, tag.getFloat("headingZ"));
        entityData.set(DATA_COLLAPSE_TICKS, tag.contains("collapseTicks")
                ? tag.getInt("collapseTicks") : -1);
        pauseTicks = Math.max(0, tag.getInt("pauseTicks"));
        nextPauseTicks = Math.max(1, tag.getInt("nextPauseTicks"));
        trailCooldown = Math.max(0, tag.getInt("trailCooldown"));
        lastTrailPosition = new Vec3(tag.getDouble("lastTrailX"),
                tag.getDouble("lastTrailY"), tag.getDouble("lastTrailZ"));
        collapsePatchEdits = Math.max(0, Math.min(3,
                tag.getInt("collapsePatchEdits")));
        entityData.set(DATA_PAUSED, pauseTicks > 0);
        if (isRooting()) {
            setInvulnerable(true);
            if (level() instanceof ServerLevel server) {
                snapToSupport(server);
            }
        }
    }
}
