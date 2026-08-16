package com.frozendawn.aggregate;

import com.frozendawn.config.ConfigPresets;
import com.frozendawn.entity.AggregateEntity;
import com.frozendawn.entity.AggregateFragmentEntity;
import com.frozendawn.event.WorldTickHandler;
import com.frozendawn.init.ModBlocks;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Awakening, reload reconciliation, cleanup, and once-only resolution. */
public final class AggregateEncounterManager {
    private static final int ABSENCE_CONFIRMATION_CHECKS = 5;

    private AggregateEncounterManager() {
    }

    public static void awaken(ServerLevel level, AggregateSavedData data,
                              ServerPlayer trigger) {
        if (!data.awakeningEligible()) return;
        BlockPos anchor = data.ossuaryPos().orElse(null);
        if (anchor == null || !level.isLoaded(anchor)) return;
        AggregateEntity aggregate = ModEntities.AGGREGATE.get().create(level);
        if (aggregate == null) return;

        List<ServerPlayer> participants = level.getPlayers(player -> !player.isSpectator()
                && player.distanceToSqr(anchor.getCenter()) <= 96.0D * 96.0D);
        int count = Math.max(1, participants.size());
        ConfigPresets preset = ConfigPresets.detectCurrentPreset();
        AggregateLineage dominant = AggregatePressurePolicy.dominant(
                data.lineagePressure());
        List<AggregateLineage> traits = AggregatePressurePolicy.lockTraits(
                data.lineagePressure(), data.ossuarySeed());
        float maxHealth = AggregateCombatPolicy.awakenedHealth(
                preset, AggregateCombatPolicy.effectiveOverfeed(
                        data.overfeedPressure(), dominant))
                * AggregateCombatPolicy.participantMultiplier(count);
        placeOnLoadedGround(level, aggregate, anchor,
                level.random.nextFloat() * 360.0F);
        aggregate.initialize(maxHealth, traits, dominant);
        AggregatePressureHandler.markIgnored(aggregate);
        if (!level.addFreshEntity(aggregate)) return;
        data.beginFight(aggregate.getUUID(), count, maxHealth, aggregate.blockPosition());
        for (ServerPlayer player : participants) {
            WorldTickHandler.grantAdvancement(player, "convergence");
        }
    }

    public static void reconcile(ServerLevel level, AggregateSavedData data) {
        if (!data.fightStarted() || data.resolved()) return;
        BlockPos anchor = data.ossuaryPos().orElse(null);
        if (anchor == null || !level.isLoaded(anchor)) return;
        BlockPos lastPosition = data.fightPosition().orElse(anchor);
        if (!level.isLoaded(lastPosition)) return;
        AggregateEntity active = data.activeAggregateId()
                .map(level::getEntity)
                .filter(AggregateEntity.class::isInstance)
                .map(AggregateEntity.class::cast)
                .orElse(null);
        if (active != null && (active.isAlive()
                || active.phase() == AggregatePhase.DYING
                || active.phase() == AggregatePhase.DEAD)) {
            data.clearMissingEntityChecks();
            return;
        }
        if (data.noteMissingEntity() < ABSENCE_CONFIRMATION_CHECKS) return;
        if (data.fightHealth() <= 0.0F || data.fightPhase() == AggregatePhase.DYING
                || data.fightPhase() == AggregatePhase.DEAD) {
            resolveAt(level, data, lastPosition, data.activeAggregateId().orElse(null));
            return;
        }
        cleanupTemporary(level, data);

        AggregateEntity restored = ModEntities.AGGREGATE.get().create(level);
        if (restored == null) return;
        restored.moveTo(lastPosition.getX() + 0.5D, lastPosition.getY(),
                lastPosition.getZ() + 0.5D, 0.0F, 0.0F);
        restored.initialize(data.fightMaxHealth(), data.lockedTraits(),
                data.dominantTrait().orElse(null));
        restored.setHealth(Math.max(1.0F, data.fightHealth()));
        restored.restorePhase(data.fightPhase());
        restored.restoreDischargeScars(data.dischargeScars());
        AggregatePressureHandler.markIgnored(restored);
        if (level.addFreshEntity(restored)) {
            data.snapshotFight(restored.getUUID(), restored.blockPosition(), restored.getHealth(),
                    restored.getMaxHealth(), restored.phase());
            data.clearMissingEntityChecks();
        }
    }

    public static void resolve(ServerLevel level, AggregateEntity aggregate) {
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        if (data.resolved()) return;
        resolveAt(level, data, aggregate.blockPosition(), aggregate.getUUID());
    }

    @Nullable
    public static UUID releaseCoreFromSky(ServerLevel level, AggregateEntity aggregate) {
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        if (data.resolved() || !data.claimCoreReward()) return null;
        BlockPos corePos = coreFormationPosition(level, aggregate.blockPosition());
        int availableHeight = level.getMaxBuildHeight() - corePos.getY() - 2;
        if (availableHeight < 8) {
            grantCoreAt(level, corePos);
            return null;
        }
        BlockPos start = corePos.above(Math.min(28, availableHeight));
        if (!level.getBlockState(start).canBeReplaced()) {
            grantCoreAt(level, corePos);
            return null;
        }
        FallingBlockEntity core = FallingBlockEntity.fall(level, start,
                ModBlocks.INERT_CONVERGENCE_CORE.get().defaultBlockState());
        core.setDeltaMovement(0.0D, -1.4D, 0.0D);
        core.setHurtsEntities(8.0F, 24);
        core.setGlowingTag(true);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                start.getX() + 0.5D, start.getY() + 0.5D, start.getZ() + 0.5D,
                2, 0.2D, 0.2D, 0.2D, 0.0D);
        for (int y = corePos.getY() + 2; y < start.getY(); y += 2) {
            level.sendParticles(com.frozendawn.init.ModParticles.AGGREGATE_EXPULSION.get(),
                    start.getX() + 0.5D, y + 0.5D, start.getZ() + 0.5D,
                    6, 0.35D, 0.7D, 0.35D, 0.12D);
        }
        ExperienceOrb.award(level, corePos.getCenter(), 150 + level.random.nextInt(51));
        return core.getUUID();
    }

    private static void resolveAt(ServerLevel level, AggregateSavedData data,
                                  BlockPos center, @Nullable UUID ownerId) {
        if (data.resolved()) return;
        AggregateReinforcementManager.cleanupLoaded(level, data);
        cleanupTemporary(level, data);
        AABB area = AABB.ofSize(center.getCenter(), 128.0D, 128.0D, 128.0D);
        for (AggregateFragmentEntity fragment : level.getEntitiesOfClass(
                AggregateFragmentEntity.class, area, candidate ->
                        ownerId != null && ownerId.equals(candidate.ownerId()))) {
            fragment.discard();
        }
        if (data.claimCoreReward()) {
            grantCoreAt(level, coreFormationPosition(level, center));
        }
        Vec3 centerPoint = center.getCenter();
        for (ServerPlayer player : level.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.distanceToSqr(centerPoint) <= 128.0D * 128.0D)) {
            WorldTickHandler.grantAdvancement(player, "nothing_left_to_become");
        }
        data.resolve();
    }

    private static void grantCoreAt(ServerLevel level, BlockPos corePos) {
        BlockState previous = level.getBlockState(corePos);
        boolean placed = previous.canBeReplaced()
                && level.setBlockAndUpdate(corePos,
                ModBlocks.INERT_CONVERGENCE_CORE.get().defaultBlockState());
        if (!placed) {
            ItemEntity reward = new ItemEntity(level,
                    corePos.getX() + 0.5D, corePos.getY() + 0.5D,
                    corePos.getZ() + 0.5D,
                    new ItemStack(ModItems.INERT_CONVERGENCE_CORE.get()));
            reward.setExtendedLifetime();
            reward.setInvulnerable(true);
            level.addFreshEntity(reward);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                corePos.getX() + 0.5D, corePos.getY() + 0.55D,
                corePos.getZ() + 0.5D, 2,
                0.25D, 0.25D, 0.25D, 0.0D);
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        net.minecraft.core.particles.ParticleTypes.BLOCK,
                        ModBlocks.INERT_CONVERGENCE_CORE.get().defaultBlockState()),
                corePos.getX() + 0.5D, corePos.getY() + 0.55D,
                corePos.getZ() + 0.5D, 96,
                1.4D, 0.9D, 1.4D, 0.32D);
        ExperienceOrb.award(level, corePos.getCenter(), 150 + level.random.nextInt(51));
    }

    private static BlockPos coreFormationPosition(ServerLevel level, BlockPos center) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                center.getX(), center.getZ());
        BlockPos surface = new BlockPos(center.getX(), surfaceY, center.getZ());
        for (int offset = 0; offset <= 3; offset++) {
            BlockPos candidate = surface.above(offset);
            if (level.getBlockState(candidate).canBeReplaced()) return candidate;
        }
        return surface.above();
    }

    public static void cleanupTemporary(ServerLevel level, AggregateSavedData data) {
        for (long packed : data.temporaryBlocks()) {
            BlockPos pos = BlockPos.of(packed);
            if (level.isLoaded(pos)
                    && level.getBlockState(pos).is(ModBlocks.AGGREGATE_TEMPORARY_MASS.get())) {
                level.destroyBlock(pos, false);
            }
        }
        data.clearTemporaryBlocks();
    }

    private static void placeOnLoadedGround(ServerLevel level, AggregateEntity aggregate,
                                            BlockPos anchor, float yaw) {
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    BlockPos column = anchor.offset(dx, 0, dz);
                    if (!level.hasChunkAt(column)) continue;
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            column.getX(), column.getZ());
                    aggregate.moveTo(column.getX() + 0.5D, surfaceY,
                            column.getZ() + 0.5D, yaw, 0.0F);
                    if (level.noCollision(aggregate)) return;
                }
            }
        }
        aggregate.moveTo(anchor.getX() + 0.5D, anchor.getY() + 1.0D,
                anchor.getZ() + 0.5D, yaw, 0.0F);
    }
}
