package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.MimicEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

public class MimicSpawner {

    private MimicSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        if (currentPhase < 6 || progress < 0.5f) return;
        if (!FrozenDawnConfig.ENABLE_MIMIC.get()) return;

        long gameTick = level.getGameTime();
        if (gameTick % 200 != 0) return; // Every 10 seconds

        RandomSource random = level.random;

        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float baseChance = BrutalPhase6SpawnCurves.isActive()
                ? BrutalPhase6SpawnCurves.mimicChance(progress)
                : 0.005f;
        if (baseChance <= 0.0f) return;
        float spawnChance = Math.min(0.8f, baseChance * mobMult); // Base 0.005 (~33 min average)

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            if (random.nextFloat() > spawnChance) continue;

            // Density cap: max 1 Mimic within 128 blocks
            int nearbyCount = level.getEntitiesOfClass(MimicEntity.class,
                    player.getBoundingBox().inflate(128.0)).size();
            if (nearbyCount >= 1) continue;

            BlockPos spawnPos = findSpawnPos(level, player, random, progress);
            if (spawnPos == null) continue;

            MimicEntity mimic = ModEntities.MIMIC.get().create(level, null, spawnPos,
                    MobSpawnType.NATURAL, true, false);
            if (mimic != null) {
                level.addFreshEntity(mimic);
                // No sound on spawn — silent appearance to maintain shadow figure illusion
                FrozenDawn.LOGGER.info("[Mimic] Spawned near {} at phase {} (progress {})",
                        player.getName().getString(), currentPhase, String.format("%.2f", progress));
            }
        }
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random, float progress) {
        int maxLight = BrutalPhase6SpawnCurves.isActive()
                ? BrutalPhase6SpawnCurves.mimicMaxLight(progress)
                : 7;
        return LateThreatSpawnHelper.findHybridSpawn(level, player, random,
                40, 64, 20, maxLight);
    }

    public static void reset() {}
}
