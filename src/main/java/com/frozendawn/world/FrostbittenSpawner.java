package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.config.PostMaeveEvolutionDifficulty;
import com.frozendawn.entity.FrostbittenEntity;
import com.frozendawn.entity.RimeboundBurrowController;
import com.frozendawn.entity.RimeboundEntity;
import com.frozendawn.entity.RimeboundPolicy;
import com.frozendawn.entity.RimeboundState;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;

public class FrostbittenSpawner {

    private FrostbittenSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        boolean postMaeve = PostMaeveWorldState.isUndoneSpawningReleased(
                level.getServer());
        if (!postMaeve && currentPhase < 4) return;
        if (!FrozenDawnConfig.ENABLE_FROSTBITTEN.get()) return;
        if (!postMaeve && currentPhase >= 6 && progress >= 0.92f) return;

        long gameTick = level.getGameTime();
        if (gameTick % 100 != 0) return; // Every 5 seconds

        RandomSource random = level.random;

        // Phase-based spawn chance and density
        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float spawnChance;
        int maxNearby;
        int groupSize;
        if (postMaeve) {
            spawnChance = Math.min(0.8f, 0.22f * mobMult);
            maxNearby = Math.max(2, (int) (6 * mobMult));
            groupSize = Math.max(1, (int) (3 * mobMult));
        } else if (currentPhase == 4) {
            spawnChance = Math.min(0.8f, 0.25f * mobMult);
            maxNearby = Math.max(1, (int) (4 * mobMult));
            groupSize = Math.max(1, (int) (2 * mobMult));
        } else if (currentPhase == 5) {
            spawnChance = Math.min(0.8f, 0.65f * mobMult);
            maxNearby = Math.max(1, (int) (12 * mobMult));
            groupSize = Math.max(1, (int) (4 * mobMult));
        } else {
            spawnChance = Math.min(0.8f, 0.12f * mobMult);
            maxNearby = Math.max(1, (int) (3 * mobMult));
            groupSize = Math.max(1, (int) (2 * mobMult));
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            float localChance = postMaeve
                    ? Math.min(0.95F, spawnChance * BloomGrowthManager.pressureMultiplier(
                    level, player.blockPosition()))
                    : spawnChance;
            if (random.nextFloat() > localChance) continue;

            int nearbyCount = level.getEntitiesOfClass(FrostbittenEntity.class,
                    player.getBoundingBox().inflate(48.0)).size();
            if (nearbyCount >= maxNearby) continue;

            int spawnCount = 1 + random.nextInt(groupSize);
            spawnCount = Math.min(spawnCount, maxNearby - nearbyCount);
            if (spawnCount <= 0) continue;

            // Find one group spawn point, then cluster the group around it
            BlockPos groupCenter = postMaeve
                    ? LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                            level, player, random, 20, 52, 28,
                            LateThreatSpawnHelper.NO_LIGHT_LIMIT)
                    : findSpawnPos(level, player, random);
            if (groupCenter == null) continue;

            if (postMaeve && trySpawnRimeboundEncounter(
                    level, groupCenter, player, random)) {
                continue;
            }

            int spawned = 0;
            for (int i = 0; i < spawnCount; i++) {
                // First mob at center, rest offset 1-3 blocks nearby
                BlockPos spawnPos;
                if (i == 0) {
                    spawnPos = groupCenter;
                } else {
                    spawnPos = findNearbySpawnPos(
                            level, groupCenter, random, postMaeve);
                    if (spawnPos == null) spawnPos = groupCenter;
                }

                FrostbittenEntity mob = ModEntities.FROSTBITTEN.get().create(level, null, spawnPos,
                        MobSpawnType.NATURAL, true, false);
                if (mob != null) {
                    mob.setEmerging(true);
                    level.addFreshEntity(mob);
                    spawned++;
                }
            }
            if (spawned > 0) {
                FrozenDawn.LOGGER.info("[Frostbitten] Spawned group of {} near {} at phase {}", spawned, player.getName().getString(), currentPhase);
            }
        }
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 24 + random.nextInt(25); // 24-48 blocks
            int x = (int) (player.getX() + Math.cos(angle) * dist);
            int z = (int) (player.getZ() + Math.sin(angle) * dist);

            BlockPos surface = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));

            // Surface only — must be above Y=60 (sea level)
            if (surface.getY() < 60) continue;

            BlockPos below = surface.below();
            BlockState groundState = level.getBlockState(below);
            if (!groundState.isSolidRender(level, below)) continue;

            if (!level.getBlockState(surface).isAir()) continue;
            if (!level.getBlockState(surface.above()).isAir()) continue;

            // Must have sky access or be underground (below Y=60)
            if (!level.canSeeSky(surface) && surface.getY() >= 60) continue;

            return surface;
        }
        return null;
    }

    /** Find a spawn position 1-3 blocks from the group center. */
    private static BlockPos findNearbySpawnPos(
            ServerLevel level, BlockPos center, RandomSource random,
            boolean unrestricted) {
        for (int attempt = 0; attempt < 5; attempt++) {
            int dx = random.nextIntBetweenInclusive(-3, 3);
            int dz = random.nextIntBetweenInclusive(-3, 3);
            BlockPos candidate = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset(dx, 0, dz));

            if (!unrestricted && candidate.getY() < 60) continue;
            if (!level.getBlockState(candidate).isAir()) continue;
            if (!level.getBlockState(candidate.above()).isAir()) continue;
            BlockPos below = candidate.below();
            if (!level.getBlockState(below).isSolidRender(level, below)) continue;

            // Must have sky access or be underground (below Y=60)
            if (!unrestricted && !level.canSeeSky(candidate)
                    && candidate.getY() >= 60) continue;

            return candidate;
        }
        return null;
    }

    public static void reset() {
        RimeboundManager.reset();
    }

    private static boolean trySpawnRimeboundEncounter(
            ServerLevel level, BlockPos groupCenter, ServerPlayer player,
            RandomSource random) {
        if (!FrozenDawnConfig.ENABLE_RIMEBOUND.get()) {
            return false;
        }
        float pressure = BloomGrowthManager.pressureMultiplier(level, groupCenter);
        float chance = RimeboundPolicy.evolutionChance(
                RimeboundManager.ticksSinceErasure(level), pressure,
                FrozenDawnConfig.RIMEBOUND_EVOLUTION_SHARE_MULTIPLIER.get()
                        * PostMaeveEvolutionDifficulty.evolutionMultiplier());
        if (random.nextFloat() >= chance
                || !RimeboundBurrowController.validDormantTerrain(level, groupCenter)) {
            return false;
        }

        int cap = FrozenDawnConfig.RIMEBOUND_NEARBY_CAP.get();
        int nearby = level.getEntitiesOfClass(RimeboundEntity.class,
                new net.minecraft.world.phys.AABB(groupCenter).inflate(64.0D)).size();
        if (nearby >= cap) {
            return false;
        }
        int desired = random.nextFloat() < 0.20F ? 2 : 1;
        int spawned = 0;
        for (int i = 0; i < Math.min(desired, cap - nearby); i++) {
            BlockPos spawn = i == 0 ? groupCenter
                    : findNearbySpawnPos(level, groupCenter, random, true);
            if (spawn == null
                    || !RimeboundBurrowController.validDormantTerrain(level, spawn)) {
                continue;
            }
            RimeboundEntity entity = ModEntities.RIMEBOUND.get().create(
                    level, null, spawn, MobSpawnType.NATURAL, true, false);
            if (entity == null) {
                continue;
            }
            entity.setActivityState(RimeboundState.DORMANT);
            level.addFreshEntity(entity);
            spawned++;
        }
        if (spawned > 0) {
            FrozenDawn.LOGGER.info(
                    "[Rimebound] Evolved {} Frostbitten spawn(s) near {} at age {} ticks",
                    spawned, player.getName().getString(),
                    RimeboundManager.ticksSinceErasure(level));
        }
        return spawned > 0;
    }
}
