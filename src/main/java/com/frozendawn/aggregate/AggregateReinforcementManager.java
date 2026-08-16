package com.frozendawn.aggregate;

import com.frozendawn.FrozenDawn;
import com.frozendawn.entity.AggregateEntity;
import com.frozendawn.entity.AggregateFragmentEntity;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.MasterArchitectLightningEntity;
import com.frozendawn.entity.RemnantEntity;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.ResonantState;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.RimeboundState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModParticles;
import com.frozendawn.homo.HearthMasterArchitectWeatherManager;
import com.frozendawn.network.MasterArchitectAuraEventPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Owns finite, no-reward bodies physically expelled by the Aggregate. */
@EventBusSubscriber(modid = FrozenDawn.MOD_ID)
public final class AggregateReinforcementManager {
    public static final String CHILD_TAG = "frozendawn:aggregate_reinforcement";
    private static final String FIGHT_TAG = "frozendawn:aggregate_fight_id";
    private static final String RECORD_TAG = "frozendawn:aggregate_reinforcement_id";
    private static final double ARENA_LEASH = 48.0D;

    private AggregateReinforcementManager() {
    }

    public static boolean beginDischarge(
            ServerLevel level, AggregateEntity aggregate, int wave) {
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        if (data.resolved() || data.dischargeSpent(wave)) return false;
        List<AggregateLineage> selected = AggregateDischargePolicy.lineagesForWave(
                aggregate.traits(), aggregate.dominantUpgrade(), wave);
        if (selected.isEmpty()) {
            data.markDischargeSpent(wave);
            return false;
        }

        List<AggregateLineage> expanded = new ArrayList<>();
        int waveBodyCap = AggregateDischargePolicy.bodiesForWave(wave);
        for (AggregateLineage lineage : selected) {
            int bodies = lineage == AggregateLineage.FROSTWRITHE
                    ? AggregateDischargePolicy.frostwritheFragmentCount(
                            aggregate.hasDominantTrait(AggregateLineage.FROSTWRITHE))
                    : AggregateDischargePolicy.substantialBodiesPerLineage();
            for (int copy = 0; copy < bodies; copy++) {
                if (expanded.size() >= waveBodyCap) break;
                if (lineage != AggregateLineage.FROSTWRITHE
                        && data.activeSubstantialReinforcements()
                                + countSubstantial(expanded)
                                >= AggregateDischargePolicy.substantialCap()) {
                    break;
                }
                expanded.add(lineage);
            }
            if (expanded.size() >= waveBodyCap) break;
        }

        List<AggregateLineage> validLineages = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < expanded.size(); i++) {
            BlockPos position = findSpawnPosition(level, aggregate, expanded.get(i), wave, i);
            if (position != null) {
                validLineages.add(expanded.get(i));
                positions.add(position);
            }
        }
        if (positions.isEmpty()) {
            FrozenDawn.LOGGER.warn(
                    "[Aggregate] Convergence discharge wave {} found no loaded landing positions",
                    wave);
            return false;
        }
        return data.reserveDischarge(wave, validLineages, positions, level.getGameTime());
    }

    public static int eject(ServerLevel level, AggregateEntity aggregate, int wave) {
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        int fragmentIndex = 0;
        int spawned = 0;
        for (AggregateSavedData.ReinforcementSnapshot record
                : data.pendingReinforcements(wave)) {
            Entity entity = createBody(level, aggregate, record, fragmentIndex++);
            if (entity == null) {
                data.retireReinforcement(record.id());
                continue;
            }
            emitEjectionPath(level, aggregate, record.lastPosition());
            entity.moveTo(record.lastPosition().getX() + 0.5D,
                    record.lastPosition().getY(), record.lastPosition().getZ() + 0.5D,
                    aggregate.getYRot() + 180.0F, 0.0F);
            initializeBody(level, entity, aggregate, record);
            markChild(entity, record.fightId(), record.id());
            AggregatePressureHandler.markIgnored(entity);
            entity.setDeltaMovement(entity.position().subtract(aggregate.position())
                    .multiply(0.08D, 0.0D, 0.08D).add(0.0D, 0.22D, 0.0D));
            if (entity instanceof Mob mob) mob.setPersistenceRequired();
            if (!level.addFreshEntity(entity)) {
                data.retireReinforcement(record.id());
                continue;
            }
            level.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                    entity.getX(), entity.getY() + 0.8D, entity.getZ(),
                    76, 0.85D, 1.05D, 0.85D, 0.52D);
            level.sendParticles(ParticleTypes.POOF,
                    entity.getX(), entity.getY() + 0.35D, entity.getZ(),
                    24, 0.7D, 0.35D, 0.7D, 0.16D);
            data.activateReinforcement(record.id(), entity.getUUID(), entity.blockPosition());
            spawned++;
        }
        return spawned;
    }

    public static void telegraphLandings(
            ServerLevel level, AggregateEntity aggregate, int wave) {
        AggregateSavedData data = AggregateSavedData.get(level.getServer());
        for (AggregateSavedData.ReinforcementSnapshot record
                : data.pendingReinforcements(wave)) {
            BlockPos target = record.lastPosition();
            level.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                    target.getX() + 0.5D,
                    target.getY() + 0.2D,
                    target.getZ() + 0.5D,
                    28, 0.38D, 0.18D, 0.38D, 0.34D);
        }
    }

    /** Strikes each reserved landing in sequence, with an arena fallback for visual continuity. */
    public static void strikeForWave(
            ServerLevel level, AggregateEntity aggregate, int wave, int strikeIndex) {
        List<AggregateSavedData.ReinforcementSnapshot> pending = AggregateSavedData
                .get(level.getServer()).pendingReinforcements(wave);
        BlockPos target = strikeIndex < pending.size()
                ? pending.get(strikeIndex).lastPosition()
                : fallbackStrikePosition(level, aggregate, wave, strikeIndex);
        float intensity = wave == AggregateDischargePolicy.SECONDARY_WAVE ? 1.65F : 1.48F;
        long seed = aggregate.getUUID().getMostSignificantBits()
                ^ aggregate.getUUID().getLeastSignificantBits()
                ^ (long)wave * 0x9E3779B97F4A7C15L
                ^ (long)strikeIndex * 0xC2B2AE3D27D4EB4FL;
        MasterArchitectLightningEntity.spawn(
                level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                84.0F, intensity, seed);
        HearthMasterArchitectWeatherManager.broadcastAuraEvent(
                level,
                MasterArchitectAuraEventPayload.AGGREGATE_BOLT,
                target.above(84),
                target,
                intensity);
        level.sendParticles(ParticleTypes.FLASH,
                target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D,
                2, 0.2D, 0.3D, 0.2D, 0.0D);
    }

    private static BlockPos fallbackStrikePosition(
            ServerLevel level, AggregateEntity aggregate, int wave, int strikeIndex) {
        double angle = aggregate.getYRot() * Mth.DEG_TO_RAD
                + (Math.PI * 2.0D * strikeIndex
                / AggregateDischargePolicy.bodiesForWave(wave))
                + (wave == AggregateDischargePolicy.SECONDARY_WAVE ? 0.42D : 0.0D);
        int radius = 7 + strikeIndex * 2;
        int x = Mth.floor(aggregate.getX() + Math.cos(angle) * radius);
        int z = Mth.floor(aggregate.getZ() + Math.sin(angle) * radius);
        if (!level.hasChunk(x >> 4, z >> 4)) return aggregate.blockPosition();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    public static void cancel(ServerLevel level, int wave) {
        AggregateSavedData.get(level.getServer()).cancelPendingReinforcements(wave);
    }

    public static void tick(ServerLevel level, AggregateSavedData data) {
        UUID fightId = data.fightId().orElse(null);
        BlockPos anchor = data.ossuaryPos().orElse(null);
        if (fightId == null || anchor == null) return;
        AggregateEntity activeAggregate = data.activeAggregateId()
                .map(level::getEntity)
                .filter(AggregateEntity.class::isInstance)
                .map(AggregateEntity.class::cast).orElse(null);
        if (activeAggregate != null
                && activeAggregate.action() != AggregateAction.CONVERGENCE_DISCHARGE) {
            data.cancelPendingReinforcements(AggregateDischargePolicy.PRIMARY_WAVE);
            data.cancelPendingReinforcements(AggregateDischargePolicy.SECONDARY_WAVE);
        }

        for (AggregateSavedData.ReinforcementSnapshot record : data.reinforcements()) {
            if (record.state() != AggregateReinforcementState.ACTIVE
                    || record.entityId() == null) continue;
            Entity entity = level.getEntity(record.entityId());
            if (entity == null) {
                if (level.isLoaded(record.lastPosition())) {
                    data.markReinforcementDead(record.entityId());
                }
                continue;
            }
            if (!isChildForFight(entity, fightId) || data.resolved()
                    || level.getGameTime() >= record.expiresAt()
                    || entity.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(),
                    anchor.getZ() + 0.5D) > ARENA_LEASH * ARENA_LEASH) {
                entity.discard();
                data.retireReinforcement(record.id());
                continue;
            }
            data.updateReinforcement(record.id(), entity.blockPosition());
            if (entity instanceof Mob mob && entity.isAlive()) {
                ServerPlayer target = nearestCombatPlayer(level, entity, anchor);
                if (target != null) mob.setTarget(target);
            }
        }
    }

    public static void cleanupLoaded(ServerLevel level, AggregateSavedData data) {
        for (AggregateSavedData.ReinforcementSnapshot record : data.reinforcements()) {
            if (record.entityId() == null) continue;
            Entity entity = level.getEntity(record.entityId());
            if (entity != null && isChild(entity)) entity.discard();
        }
        data.retireAllReinforcements();
    }

    public static boolean isChild(Entity entity) {
        return entity.getPersistentData().getBoolean(CHILD_TAG);
    }

    private static boolean isChildForFight(Entity entity, UUID fightId) {
        return isChild(entity) && entity.getPersistentData().hasUUID(FIGHT_TAG)
                && fightId.equals(entity.getPersistentData().getUUID(FIGHT_TAG));
    }

    private static void markChild(Entity entity, UUID fightId, UUID recordId) {
        entity.getPersistentData().putBoolean(CHILD_TAG, true);
        entity.getPersistentData().putUUID(FIGHT_TAG, fightId);
        entity.getPersistentData().putUUID(RECORD_TAG, recordId);
    }

    @Nullable
    private static Entity createBody(ServerLevel level, AggregateEntity aggregate,
                                     AggregateSavedData.ReinforcementSnapshot record,
                                     int fragmentIndex) {
        Entity entity = switch (record.lineage()) {
            case RIMEBOUND -> ModEntities.RIMEBOUND.get().create(level);
            case RESONANT -> ModEntities.RESONANT.get().create(level);
            case REMNANT -> ModEntities.REMNANT.get().create(level);
            case FROSTWRITHE -> createFragment(level, aggregate, fragmentIndex);
            case ARCHITECT -> ModEntities.ARCHITECT.get().create(level);
            case UNDONE -> ModEntities.UNDONE.get().create(level);
            case NORMAL -> ModEntities.FROSTBITTEN.get().create(level);
        };
        return entity;
    }

    private static void initializeBody(
            ServerLevel level, Entity entity, AggregateEntity aggregate,
            AggregateSavedData.ReinforcementSnapshot record) {
        ServerPlayer target = nearestCombatPlayer(level, aggregate,
                aggregate.blockPosition());
        if (entity instanceof RimeboundEntity rimebound) {
            rimebound.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                    MobSpawnType.EVENT, null);
            rimebound.setActivityState(RimeboundState.STALKING);
        } else if (entity instanceof ResonantEntity resonant) {
            resonant.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                    MobSpawnType.EVENT, null);
            resonant.setActivityState(ResonantState.STALKING);
            if (target != null) resonant.forceMarkedTarget(target);
        } else if (entity instanceof RemnantEntity remnant) {
            if (target != null) remnant.exposeWithoutLure(target);
            else remnant.setState(com.frozendawn.entity.RemnantState.HUNTING);
        } else if (entity instanceof ArchitectEntity architect) {
            architect.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                    MobSpawnType.EVENT, null);
        } else if (entity instanceof FrostbittenEntity frostbitten) {
            frostbitten.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()),
                    MobSpawnType.EVENT, null);
        }
        if (entity instanceof LivingEntity living) {
            boolean dominant = aggregate.hasDominantTrait(record.lineage());
            double quality = dominant ? 1.18D : 0.88D;
            if (living.getAttribute(Attributes.MAX_HEALTH) != null) {
                double maximum = living.getAttributeBaseValue(Attributes.MAX_HEALTH) * quality;
                living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximum);
                living.setHealth((float) maximum);
            }
            if (dominant && living.getAttribute(Attributes.ARMOR) != null) {
                living.getAttribute(Attributes.ARMOR).setBaseValue(
                        living.getAttributeBaseValue(Attributes.ARMOR) + 2.0D);
            }
        }
    }

    private static AggregateFragmentEntity createFragment(
            ServerLevel level, AggregateEntity aggregate, int index) {
        AggregateFragmentEntity entity = ModEntities.AGGREGATE_FRAGMENT.get().create(level);
        if (entity != null) entity.initialize(aggregate, index);
        return entity;
    }

    private static void emitEjectionPath(
            ServerLevel level, AggregateEntity aggregate, BlockPos landing) {
        Vec3 start = aggregate.position().add(0.0D, 1.65D, 0.0D);
        Vec3 end = Vec3.atBottomCenterOf(landing)
                .add(0.0D, 0.45D, 0.0D);
        Vec3 delta = end.subtract(start);
        for (int step = 0; step <= 18; step++) {
            double t = step / 18.0D;
            Vec3 point = start.add(delta.scale(t))
                    .add(0.0D, Math.sin(Math.PI * t) * 1.4D, 0.0D);
            Vec3 velocity = delta.normalize().scale(0.26D + t * 0.18D)
                    .add(0.0D, 0.08D - t * 0.12D, 0.0D);
            level.sendParticles(ModParticles.AGGREGATE_EXPULSION.get(),
                    point.x, point.y, point.z, 0,
                    velocity.x, velocity.y, velocity.z, 1.0D);
            if (step % 3 == 0) {
                level.sendParticles(ParticleTypes.WHITE_ASH,
                        point.x, point.y, point.z, 2,
                        0.1D, 0.1D, 0.1D, 0.025D);
            }
        }
    }

    @Nullable
    private static BlockPos findSpawnPosition(
            ServerLevel level, AggregateEntity aggregate, AggregateLineage lineage,
            int wave, int index) {
        UUID fightId = AggregateSavedData.get(level.getServer()).fightId().orElse(aggregate.getUUID());
        RandomSource random = RandomSource.create(fightId.getMostSignificantBits()
                ^ fightId.getLeastSignificantBits() ^ (long) wave * 0x9E3779B97F4A7C15L
                ^ (long) index * 0xC2B2AE3D27D4EB4FL);
        for (int attempt = 0; attempt < 96; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int radius = 5 + random.nextInt(10);
            int x = aggregate.blockPosition().getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = aggregate.blockPosition().getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos horizontal = new BlockPos(x, aggregate.blockPosition().getY(), z);
            if (!level.hasChunkAt(horizontal)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!validSpawn(level, feet)) continue;
            return feet;
        }
        return null;
    }

    private static boolean validSpawn(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet) || !level.getFluidState(feet).isEmpty()
                || !level.getFluidState(feet.below()).isEmpty()
                || !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                || !level.getBlockState(feet.below()).isFaceSturdy(
                level, feet.below(), net.minecraft.core.Direction.UP)) {
            return false;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class,
                new AABB(feet).inflate(0.8D, 1.8D, 0.8D), Entity::isAlive).isEmpty()) {
            return false;
        }
        return true;
    }

    @Nullable
    private static ServerPlayer nearestCombatPlayer(
            ServerLevel level, Entity from, BlockPos anchor) {
        return level.getPlayers(player -> player.isAlive() && !player.isCreative()
                        && !player.isSpectator() && player.distanceToSqr(
                        anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D)
                        <= 96.0D * 96.0D).stream()
                .min(java.util.Comparator.comparingDouble(from::distanceToSqr)).orElse(null);
    }

    private static int countSubstantial(List<AggregateLineage> lineages) {
        return (int) lineages.stream()
                .filter(lineage -> lineage != AggregateLineage.FROSTWRITHE).count();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!isChild(event.getEntity())
                || !(event.getEntity().level() instanceof ServerLevel level)) return;
        AggregateSavedData.get(level.getServer()).markReinforcementDead(
                event.getEntity().getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (isChild(event.getEntity())) event.getDrops().clear();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isChild(event.getEntity())) event.setDroppedExperience(0);
    }
}
