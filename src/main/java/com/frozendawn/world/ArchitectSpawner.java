package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

public class ArchitectSpawner {
    private static final float POST_MAEVE_CHANCE = 0.02F;
    private static final float POST_MAEVE_BRUTAL_CHANCE = 0.05F;
    private static final float POST_MAEVE_MAX_LOCAL_CHANCE = 0.075F;

    private ArchitectSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        boolean maeveErased = PostMaeveWorldState.isErased(level);
        boolean postMaeve = PostMaeveWorldState.isUndoneSpawningReleased(
                level.getServer());
        if (maeveErased && !postMaeve) return;
        if (!postMaeve && currentPhase < 6) return;
        if (!FrozenDawnConfig.ENABLE_ARCHITECT.get()) return;

        long gameTick = level.getGameTime();
        if (gameTick % 200 != 0) return;

        RandomSource random = level.random;
        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float baseChance = postMaeve
                ? (BrutalPhase6SpawnCurves.isActive()
                ? POST_MAEVE_BRUTAL_CHANCE : POST_MAEVE_CHANCE)
                : BrutalPhase6SpawnCurves.isActive()
                ? BrutalPhase6SpawnCurves.architectChance(progress) : 0.02F;
        if (baseChance <= 0.0f) return;
        float spawnChance = Math.min(0.8f, baseChance * mobMult);

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            // Suppresses only unresolved scripted tower Architect encounters, not solved comm tower terminals.
            if (!postMaeve && TowerEncounterController.isTowerEncounterNearby(
                    level, player.blockPosition(), 80.0)) continue;
            float localChance = postMaeve
                    ? Math.min(POST_MAEVE_MAX_LOCAL_CHANCE,
                    spawnChance * BloomGrowthManager.pressureMultiplier(
                    level, player.blockPosition()))
                    : spawnChance;
            if (random.nextFloat() > localChance) continue;

            // Density cap: max 1 within 96 blocks
            int nearbyCount = level.getEntitiesOfClass(ArchitectEntity.class,
                    player.getBoundingBox().inflate(96.0)).size();
            if (nearbyCount >= 1) continue;

            BlockPos spawnPos = postMaeve
                    ? LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                            level, player, random, 48, 72, 28,
                            LateThreatSpawnHelper.NO_LIGHT_LIMIT)
                    : findSpawnPos(level, player, random);
            if (spawnPos == null || (postMaeve
                    && LateThreatSpawnHelper.isInsideHearthBoundary(level, spawnPos))) continue;

            ArchitectEntity architect = ModEntities.ARCHITECT.get().create(level, null, spawnPos,
                    MobSpawnType.NATURAL, true, false);
            if (architect != null) {
                // Pre-seed observation data before adding to world
                architect.preSeedObservation(level, player);
                if (!player.isCreative()) {
                    architect.armSpawnObserveCue(player);
                }

                level.addFreshEntity(architect);
                FrozenDawn.LOGGER.info("[Architect] Spawned near {} at phase {} ({}){}",
                        player.getName().getString(), currentPhase,
                        String.format("%.0f blocks away", Math.sqrt(
                                player.distanceToSqr(architect))),
                        postMaeve ? " (post-Maeve)" : "");
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
