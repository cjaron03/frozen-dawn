package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.entity.FrostmiteEntity;
import com.frozendawn.entity.FrostwritheEntity;
import com.frozendawn.entity.FrostwrithePolicy;
import com.frozendawn.entity.FrostwritheState;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.init.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loaded-only arbitration for real Frostmites belonging to a broken colony. */
public final class FrostwritheColonyManager {
    private static final Map<UUID, Long> LAST_DECISION_TICK = new HashMap<>();
    private static final Map<UUID, Long> RALLY_READY_TICK = new HashMap<>();
    private static final Map<UUID, AmbientClusterAttempt> AMBIENT_CLUSTER_ATTEMPTS =
            new HashMap<>();

    private FrostwritheColonyManager() {
    }

    /** Returns true while colony rallying owns this mite's navigation. */
    public static boolean tickMite(FrostmiteEntity mite) {
        if (!(mite.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!mite.hasColony()) return tickAmbientCluster(mite, level);
        long now = level.getGameTime();
        if (now < mite.colonyScatterUntil()) {
            return false;
        }
        if (now > mite.colonyRegroupDeadline()) {
            resolveFailedColony(level, mite.colonyId(), mite.colonyRallyPos());
            return false;
        }
        BlockPos rally = mite.colonyRallyPos();
        if (rally == null || !level.isLoaded(rally)
                || StillpointPolicy.isSuppressed(level, rally)
                || MiteAwayRegistry.isProtected(level, mite.position())
                || MiteAwayRegistry.isProtected(level, rally.getCenter())) {
            return false;
        }
        if (mite.isLatched()) {
            return false;
        }
        mite.setColonyRegrouping(true);
        mite.getNavigation().moveTo(rally.getX() + 0.5D, rally.getY(),
                rally.getZ() + 0.5D, 1.22D);

        if (now % 10L != 0L) return true;
        UUID colonyId = mite.colonyId();
        if (colonyId == null) return true;
        boolean assembledColonyStillExists = !level.getEntitiesOfClass(
                FrostwritheEntity.class, new AABB(rally).inflate(64.0D),
                colony -> colonyId.equals(colony.colonyId())).isEmpty();
        if (assembledColonyStillExists) {
            return false;
        }
        long readyTick = RALLY_READY_TICK.computeIfAbsent(colonyId,
                ignored -> now + 40L + Math.floorMod(colonyId.hashCode(), 21));
        if (now < readyTick) return true;
        List<FrostmiteEntity> members = loadedMembers(level, rally, colonyId);
        FrostmiteEntity leader = members.stream()
                .filter(member -> !member.isLatched())
                .min(Comparator.comparing(entity -> entity.getUUID().toString()))
                .orElse(null);
        if (leader == null || leader != mite) return true;
        // Only the elected leader may claim this tick's decision. Previously,
        // whichever representative ticked first claimed the slot and then
        // deferred to a different leader, deadlocking every regroup forever.
        if (LAST_DECISION_TICK.getOrDefault(colonyId, -1L) == now) return true;
        LAST_DECISION_TICK.put(colonyId, now);

        List<FrostmiteEntity> gathered = members.stream()
                .filter(member -> !member.isLatched()
                        && member.blockPosition().closerToCenterThan(rally.getCenter(), 3.5D))
                .toList();
        int biomass = gathered.stream().mapToInt(FrostmiteEntity::colonyBiomassUnits).sum();
        if (!FrostwrithePolicy.mayReassemble(gathered.size(), biomass,
                level.isLoaded(rally), MiteAwayRegistry.isProtected(level, rally.getCenter()))) {
            return true;
        }

        FrostwritheEntity assembled = ModEntities.FROSTWRITHE.get().create(
                level, null, rally, MobSpawnType.EVENT, true, false);
        if (assembled == null) {
            return true;
        }
        BlockPos assemblyPosition = findAssemblyPosition(level, assembled, rally);
        if (assemblyPosition == null) {
            FrozenDawn.LOGGER.warn(
                    "[Frostwrithe] Colony {} gathered {} representatives with {} biomass at {}, but no terrain-clear assembly point was available",
                    colonyId, gathered.size(), biomass, rally);
            assembled.discard();
            return true;
        }
        assembled.moveTo(assemblyPosition.getX() + 0.5D, assemblyPosition.getY(),
                assemblyPosition.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        // The representatives are intentionally occupying this volume. Only
        // terrain may veto the replacement body; entity collision would make
        // every valid gathered colony reject itself.
        assembled.initializeColony(colonyId, biomass, FrostwritheState.ASSEMBLING);
        if (!level.addFreshEntity(assembled)) {
            assembled.discard();
            return true;
        }
        assembled.playSound(ModSounds.FROSTWRITHE_REGROUP.get(), 1.35F, 0.92F);
        FrozenDawn.LOGGER.info(
                "[Frostwrithe] Colony {} reassembled from {} representatives with {} biomass at {}",
                colonyId, gathered.size(), biomass, assemblyPosition);
        for (FrostmiteEntity member : members) {
            if (gathered.contains(member)) member.discard();
            else member.clearColony();
        }
        LAST_DECISION_TICK.remove(colonyId);
        RALLY_READY_TICK.remove(colonyId);
        return true;
    }

    /** Lets an ordinary post-Maeve Frostmite cluster discover colony behavior. */
    private static boolean tickAmbientCluster(FrostmiteEntity mite, ServerLevel level) {
        if (!FrozenDawnConfig.ENABLE_FROSTWRITHE.get()
                || !PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())
                || mite.isLatched() || !mite.isAlive()) {
            AMBIENT_CLUSTER_ATTEMPTS.remove(mite.getUUID());
            return false;
        }
        long now = level.getGameTime();
        if (Math.floorMod(mite.getId(), 20) != Math.floorMod(now, 20L)) return false;
        if (now % 200L == 0L) {
            AMBIENT_CLUSTER_ATTEMPTS.entrySet().removeIf(
                    entry -> now - entry.getValue().lastSeenTick() > 240L);
        }

        List<FrostmiteEntity> cluster = level.getEntitiesOfClass(
                        FrostmiteEntity.class,
                        mite.getBoundingBox().inflate(FrostwrithePolicy.AMBIENT_CLUSTER_RADIUS),
                        candidate -> candidate.isAlive() && !candidate.hasColony()
                                && !candidate.isLatched()
                                && !MiteAwayRegistry.isProtected(level, candidate.position()))
                .stream()
                .sorted(Comparator.comparing(entity -> entity.getUUID().toString()))
                .limit(FrostwrithePolicy.AMBIENT_CLUSTER_MAX_MITES)
                .toList();
        if (cluster.size() < FrostwrithePolicy.AMBIENT_CLUSTER_MIN_MITES) {
            AMBIENT_CLUSTER_ATTEMPTS.remove(mite.getUUID());
            return false;
        }
        FrostmiteEntity leader = cluster.getFirst();
        if (leader != mite) return false;

        BlockPos rally = clusterCenter(cluster);
        if (!level.isLoaded(rally)
                || StillpointPolicy.isSuppressed(level, rally)
                || MiteAwayRegistry.isProtected(level, rally.getCenter())) {
            AMBIENT_CLUSTER_ATTEMPTS.remove(leader.getUUID());
            return false;
        }
        int cap = FrozenDawnConfig.FROSTWRITHE_NEARBY_CAP.get();
        int assembled = level.getEntitiesOfClass(FrostwritheEntity.class,
                new AABB(rally).inflate(64.0D)).size();
        if (assembled >= cap) {
            AMBIENT_CLUSTER_ATTEMPTS.remove(leader.getUUID());
            return false;
        }

        AmbientClusterAttempt attempt = AMBIENT_CLUSTER_ATTEMPTS.get(leader.getUUID());
        if (attempt == null) {
            long eligibleAt = now + FrostwrithePolicy.AMBIENT_CLUSTER_DWELL_TICKS
                    + level.random.nextInt(
                    FrostwrithePolicy.AMBIENT_CLUSTER_DWELL_VARIANCE + 1);
            AMBIENT_CLUSTER_ATTEMPTS.put(leader.getUUID(),
                    new AmbientClusterAttempt(eligibleAt, now));
            return false;
        }
        AMBIENT_CLUSTER_ATTEMPTS.put(leader.getUUID(),
                new AmbientClusterAttempt(attempt.eligibleAt(), now));
        if (now < attempt.eligibleAt()) return false;
        if (!FrostwrithePolicy.ambientClusterForms(level.random.nextFloat())) {
            AMBIENT_CLUSTER_ATTEMPTS.put(leader.getUUID(),
                    new AmbientClusterAttempt(now
                            + FrostwrithePolicy.AMBIENT_CLUSTER_RETRY_TICKS
                            + level.random.nextInt(61), now));
            return false;
        }

        UUID colonyId = UUID.randomUUID();
        int biomass = FrostwrithePolicy.MAX_BIOMASS;
        for (int index = 0; index < cluster.size(); index++) {
            FrostmiteEntity member = cluster.get(index);
            member.forceColonyRally(colonyId,
                    FrostwrithePolicy.splitBiomass(biomass, cluster.size(), index),
                    rally, now, now + 260L);
            AMBIENT_CLUSTER_ATTEMPTS.remove(member.getUUID());
        }
        FrozenDawn.LOGGER.info(
                "[Frostwrithe] {} ordinary Frostmites began spontaneous convergence at {}",
                cluster.size(), rally);
        level.playSound(null, rally, ModSounds.FROSTWRITHE_ASSEMBLE.get(),
                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.12F);
        return true;
    }

    private static BlockPos clusterCenter(List<FrostmiteEntity> cluster) {
        int x = 0;
        int y = 0;
        int z = 0;
        for (FrostmiteEntity mite : cluster) {
            x += mite.blockPosition().getX();
            y += mite.blockPosition().getY();
            z += mite.blockPosition().getZ();
        }
        return new BlockPos(x / cluster.size(), y / cluster.size(), z / cluster.size());
    }

    private static BlockPos findAssemblyPosition(ServerLevel level,
                                                 FrostwritheEntity assembled,
                                                 BlockPos rally) {
        int[][] offsets = {
                {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };
        int[] verticalOffsets = {0, 1, -1};
        for (int vertical : verticalOffsets) {
            for (int[] offset : offsets) {
                BlockPos candidate = rally.offset(offset[0], vertical, offset[1]);
                if (!level.isLoaded(candidate) || !level.isLoaded(candidate.below())) {
                    continue;
                }
                assembled.moveTo(candidate.getX() + 0.5D, candidate.getY(),
                        candidate.getZ() + 0.5D, 0.0F, 0.0F);
                if (!level.getBlockCollisions(assembled,
                        assembled.getBoundingBox().deflate(0.08D))
                        .iterator().hasNext()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    public static int consumeLooseBiomass(ServerLevel level, FrostwritheEntity colony,
                                         int maximum) {
        UUID colonyId = colony.colonyId();
        if (colonyId == null || maximum <= 0) return 0;
        int consumed = 0;
        for (FrostmiteEntity mite : level.getEntitiesOfClass(FrostmiteEntity.class,
                colony.getBoundingBox().inflate(6.0D), candidate ->
                        colonyId.equals(candidate.colonyId()) && !candidate.isLatched())) {
            int take = Math.min(maximum - consumed, mite.colonyBiomassUnits());
            consumed += take;
            if (take >= mite.colonyBiomassUnits()) {
                mite.discard();
            } else {
                mite.setColonyBiomassUnits(mite.colonyBiomassUnits() - take);
            }
            if (consumed >= maximum) break;
        }
        return consumed;
    }

    /** Debug support: retain gathering time, but remove the seeded idle delay. */
    public static void forceDecisionNow(UUID colonyId, long now) {
        if (colonyId == null) return;
        RALLY_READY_TICK.put(colonyId, now);
        LAST_DECISION_TICK.remove(colonyId);
    }

    public static void resolveFailedColony(ServerLevel level, UUID colonyId,
                                           BlockPos rally) {
        if (colonyId == null) return;
        List<FrostmiteEntity> members = loadedMembers(level, rally, colonyId);
        for (FrostmiteEntity member : members) {
            member.clearColony();
        }
        if (!members.isEmpty()) {
            level.playSound(null, rally, ModSounds.FROSTWRITHE_TERMINAL.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.15F, 0.9F);
        }
        LAST_DECISION_TICK.remove(colonyId);
        RALLY_READY_TICK.remove(colonyId);
    }

    public static List<FrostmiteEntity> loadedMembers(ServerLevel level, BlockPos center,
                                                      UUID colonyId) {
        AABB bounds = new AABB(center).inflate(48.0D);
        return level.getEntitiesOfClass(FrostmiteEntity.class, bounds,
                mite -> colonyId.equals(mite.colonyId()));
    }

    public static void reset() {
        LAST_DECISION_TICK.clear();
        RALLY_READY_TICK.clear();
        AMBIENT_CLUSTER_ATTEMPTS.clear();
    }

    private record AmbientClusterAttempt(long eligibleAt, long lastSeenTick) {
    }
}
