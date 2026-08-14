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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

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
        aggregate.moveTo(anchor.getX() + 0.5D, anchor.getY() + 1.0D,
                anchor.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
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
        if (active != null && active.isAlive()) {
            data.clearMissingEntityChecks();
            return;
        }
        if (data.noteMissingEntity() < ABSENCE_CONFIRMATION_CHECKS) return;
        cleanupTemporary(level, data);

        AggregateEntity restored = ModEntities.AGGREGATE.get().create(level);
        if (restored == null) return;
        restored.moveTo(lastPosition.getX() + 0.5D, lastPosition.getY(),
                lastPosition.getZ() + 0.5D, 0.0F, 0.0F);
        restored.initialize(data.fightMaxHealth(), data.lockedTraits(),
                data.dominantTrait().orElse(null));
        restored.setHealth(Math.max(1.0F, data.fightHealth()));
        restored.restorePhase(data.fightPhase());
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
        cleanupTemporary(level, data);
        AABB area = aggregate.getBoundingBox().inflate(64.0D);
        for (AggregateFragmentEntity fragment : level.getEntitiesOfClass(
                AggregateFragmentEntity.class, area, candidate ->
                        aggregate.getUUID().equals(candidate.ownerId()))) {
            fragment.discard();
        }
        if (data.claimCoreReward()) {
            ItemEntity reward = new ItemEntity(level, aggregate.getX(),
                    aggregate.getY() + 1.0D, aggregate.getZ(),
                    new ItemStack(ModItems.INERT_CONVERGENCE_CORE.get()));
            reward.setExtendedLifetime();
            reward.setInvulnerable(true);
            level.addFreshEntity(reward);
            ExperienceOrb.award(level, aggregate.position(), 150 + level.random.nextInt(51));
        }
        for (ServerPlayer player : level.getPlayers(candidate -> !candidate.isSpectator()
                && candidate.distanceToSqr(aggregate) <= 128.0D * 128.0D)) {
            WorldTickHandler.grantAdvancement(player, "nothing_left_to_become");
        }
        data.resolve();
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
}
