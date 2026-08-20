package com.frozendawn.entity;

import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Short-lived rendered mass visibly torn from the Aggregate during Convergence. */
public final class AggregateShedChunkEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(
                    AggregateShedChunkEntity.class, EntityDataSerializers.INT);
    private float blastPower = 1.35F;

    public AggregateShedChunkEntity(
            EntityType<? extends AggregateShedChunkEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static void spawn(
            ServerLevel level, AggregateEntity aggregate, int variant, float angleOffset) {
        spawn(level, aggregate, variant, angleOffset, 1.35F);
    }

    public static void spawn(
            ServerLevel level, AggregateEntity aggregate, int variant,
            float angleOffset, float blastPower) {
        AggregateShedChunkEntity chunk = ModEntities.AGGREGATE_SHED_CHUNK.get().create(level);
        if (chunk == null) return;
        double angle = Math.toRadians(aggregate.getYRot() + angleOffset);
        Vec3 outward = new Vec3(Math.sin(angle), 0.0D, -Math.cos(angle));
        chunk.entityData.set(DATA_VARIANT, Math.floorMod(variant, 3));
        chunk.blastPower = Math.clamp(blastPower, 0.8F, 2.0F);
        chunk.moveTo(aggregate.position().add(outward.scale(2.55D)).add(0.0D, 1.9D, 0.0D));
        chunk.setDeltaMovement(outward.scale(0.55D).add(0.0D, 0.68D, 0.0D));
        if (level.addFreshEntity(chunk)) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, chunk.blockState()),
                    chunk.getX(), chunk.getY(), chunk.getZ(), 22,
                    0.35D, 0.35D, 0.35D, 0.16D);
        } else {
            com.frozendawn.FrozenDawn.LOGGER.warn(
                    "[Aggregate] Failed to spawn a shed body chunk at {}", aggregate.blockPosition());
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VARIANT, 0);
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Vec3 motion = getDeltaMovement();
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        setDeltaMovement(motion.multiply(0.94D, 0.92D, 0.94D).add(0.0D, -0.055D, 0.0D));
        if (level() instanceof ServerLevel server) {
            if (tickCount % 2 == 0) {
                server.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                        getX(), getY() + 0.25D, getZ(), 7,
                        0.24D, 0.24D, 0.24D, 0.13D);
            }
            int surfaceY = server.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(getX()), Mth.floor(getZ()));
            if ((tickCount > 6 && motion.y < 0.0D && getY() <= surfaceY + 0.35D)
                    || tickCount >= 60) {
                explode(server, surfaceY);
            }
        }
    }

    private void explode(ServerLevel level, int surfaceY) {
        double impactY = Math.max(getY(), surfaceY + 0.15D);
        level.explode(this, getX(), impactY, getZ(), blastPower,
                Level.ExplosionInteraction.TNT);
        level.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                getX(), impactY, getZ(), 96,
                1.15D, 0.75D, 1.15D, 0.62D);
        level.sendParticles(new BlockParticleOption(
                        ParticleTypes.BLOCK, blockState()),
                getX(), impactY, getZ(), 54,
                1.0D, 0.55D, 1.0D, 0.28D);
        level.sendParticles(ParticleTypes.POOF,
                getX(), impactY, getZ(), 34,
                0.9D, 0.35D, 0.9D, 0.22D);
        level.playSound(null, getX(), impactY, getZ(),
                com.frozendawn.init.ModSounds.AGGREGATE_FRAGMENT_BREAK.get(),
                net.minecraft.sounds.SoundSource.HOSTILE,
                2.4F, 0.68F + random.nextFloat() * 0.14F);
        discard();
    }

    private net.minecraft.world.level.block.state.BlockState blockState() {
        return switch (variant()) {
            case 1 -> com.frozendawn.init.ModBlocks.AGGREGATE_RIB.get().defaultBlockState();
            case 2 -> com.frozendawn.init.ModBlocks.AGGREGATE_RESIDUE.get().defaultBlockState();
            default -> com.frozendawn.init.ModBlocks.AGGREGATE_MASS.get().defaultBlockState();
        };
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
