package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

public class ArchitectSpawner {

    private ArchitectSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        if (currentPhase < 6) return;
        if (!FrozenDawnConfig.ENABLE_ARCHITECT.get()) return;

        long gameTick = level.getGameTime();
        if (gameTick % 200 != 0) return;

        RandomSource random = level.random;
        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float baseChance = BrutalPhase6SpawnCurves.isActive()
                ? BrutalPhase6SpawnCurves.architectChance(progress)
                : 0.02f;
        if (baseChance <= 0.0f) return;
        float spawnChance = Math.min(0.8f, baseChance * mobMult);

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            // Suppresses only unresolved scripted tower Architect encounters, not solved comm tower terminals.
            if (TowerEncounterController.isTowerEncounterNearby(level, player.blockPosition(), 80.0)) continue;
            if (random.nextFloat() > spawnChance) continue;

            // Density cap: max 1 within 96 blocks
            int nearbyCount = level.getEntitiesOfClass(ArchitectEntity.class,
                    player.getBoundingBox().inflate(96.0)).size();
            if (nearbyCount >= 1) continue;

            BlockPos spawnPos = findSpawnPos(level, player, random);
            if (spawnPos == null) continue;

            ArchitectEntity architect = ModEntities.ARCHITECT.get().create(level, null, spawnPos,
                    MobSpawnType.NATURAL, true, false);
            if (architect != null) {
                // Pre-seed observation data before adding to world
                architect.preSeedObservation(level, player);
                if (!player.isCreative()) {
                    architect.armSpawnObserveCue(player);
                }

                level.addFreshEntity(architect);
                FrozenDawn.LOGGER.info("[Architect] Spawned near {} at phase {} ({})",
                        player.getName().getString(), currentPhase,
                        String.format("%.0f blocks away", Math.sqrt(player.distanceToSqr(architect))));
            }
        }
    }

    /**
     * Spawn 48-64 blocks from player, using a vertical scan plus surface fallback
     * so buried late-phase terrain does not silently eat natural Architect attempts.
     */
    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        return LateThreatSpawnHelper.findHybridSpawn(level, player, random,
                48, 64, 20, LateThreatSpawnHelper.NO_LIGHT_LIMIT);
    }

    public static void reset() {}
}
