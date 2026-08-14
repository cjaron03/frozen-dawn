package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.config.PostMaeveEvolutionDifficulty;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.entity.ResonantEntity;
import com.frozendawn.entity.ResonantPhaseController;
import com.frozendawn.entity.ResonantPolicy;
import com.frozendawn.entity.ResonantState;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import com.frozendawn.phase.PhaseManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public class HollowSpawner {

    private HollowSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        boolean postMaeve = PostMaeveWorldState.isUndoneSpawningReleased(
                level.getServer());
        if (!postMaeve && currentPhase < 5) return;
        if (!FrozenDawnConfig.ENABLE_HOLLOW.get()) return;
        // Stop spawning in phase 6 late (atmosphere gone — even vapors freeze solid)
        if (!postMaeve && PhaseManager.isVacuumActive(currentPhase, progress)) return;

        long gameTick = level.getGameTime();
        if (gameTick % 200 != 0) return; // Every 10 seconds

        RandomSource random = level.random;

        // Phase-based spawn chance:
        // Phase 5: 15% — introductory encounters
        // Phase 6 early local window (0.0-0.4): ramps 20% → 40%
        // Phase 6 mid local window (0.4-0.7): peaks at 50%
        // Phase 6 late local window (0.7-1.0): tapers to 0% at vacuum onset
        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float spawnChance;
        if (postMaeve) {
            spawnChance = 0.12f;
        } else if (currentPhase == 5) {
            spawnChance = 0.15f;
        } else if (BrutalPhase6SpawnCurves.isActive()) {
            spawnChance = BrutalPhase6SpawnCurves.hollowChance(progress);
        } else {
            float phase6Progress = PhaseManager.getPhase6PreVacuumLocalProgress(progress);

            // Phase 6 — ramp/peak/taper based on pre-vacuum local progress
            if (phase6Progress < PhaseManager.HOLLOW_PHASE6_RAMP_END) {
                // Early: ramp 0.20 → 0.40
                spawnChance = 0.20f + (phase6Progress / PhaseManager.HOLLOW_PHASE6_RAMP_END) * 0.20f;
            } else if (phase6Progress < PhaseManager.HOLLOW_PHASE6_PEAK_END) {
                // Mid: peak at 0.50
                spawnChance = 0.50f;
            } else {
                // Late: taper 0.50 → 0.0 from local progress 0.70 to 1.00 (overall vacuum start at 0.85)
                float taper = (phase6Progress - PhaseManager.HOLLOW_PHASE6_PEAK_END)
                        / (1.0f - PhaseManager.HOLLOW_PHASE6_PEAK_END);
                spawnChance = 0.50f * (1.0f - taper);
            }
        }
        if (spawnChance <= 0.0f) return;
        spawnChance = Math.min(0.8f, spawnChance * mobMult);
        int maxHollow = Math.max(1, (int) ((postMaeve ? 5 : 4) * mobMult));

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            float localChance = postMaeve
                    ? Math.min(0.95F, spawnChance * BloomGrowthManager.pressureMultiplier(
                    level, player.blockPosition()))
                    : spawnChance;
            if (random.nextFloat() > localChance) continue;

            // Density cap: max 4 within 48 blocks (scaled by multiplier)
            int nearbyCount = level.getEntitiesOfClass(HollowEntity.class,
                    player.getBoundingBox().inflate(48.0)).size();
            if (nearbyCount >= maxHollow) continue;

            BlockPos spawnPos = postMaeve
                    ? LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                            level, player, random, 24, 56, 28,
                            LateThreatSpawnHelper.NO_LIGHT_LIMIT)
                    : findSpawnPos(level, player, random);
            if (spawnPos == null) continue;

            if (postMaeve && trySpawnResonant(level, spawnPos, random)) {
                continue;
            }
            HollowEntity hollow = ModEntities.HOLLOW.get().create(
                    level, null, spawnPos, MobSpawnType.NATURAL, true, false);
            if (hollow != null && level.addFreshEntity(hollow)) {
                FrozenDawn.LOGGER.info("[Hollow] Spawned near {} at phase {}",
                        player.getName().getString(), currentPhase);
            }
        }
    }

    private static boolean trySpawnResonant(ServerLevel level, BlockPos encounter,
                                            RandomSource random) {
        if (!FrozenDawnConfig.ENABLE_RESONANT.get()) return false;
        float chance = ResonantPolicy.evolutionChance(
                ResonantManager.ticksSinceErasure(level),
                BloomGrowthManager.pressureMultiplier(level, encounter),
                FrozenDawnConfig.RESONANT_EVOLUTION_SHARE_MULTIPLIER.get()
                        * PostMaeveEvolutionDifficulty.evolutionMultiplier());
        if (random.nextFloat() >= chance) return false;
        int nearby = level.getEntitiesOfClass(ResonantEntity.class,
                new AABB(encounter).inflate(64.0D)).size();
        if (nearby >= FrozenDawnConfig.RESONANT_NEARBY_CAP.get()) return false;
        BlockPos concealed = ResonantPhaseController.findConcealedSpawn(level, encounter);
        if (concealed == null) return false;
        ResonantEntity resonant = ModEntities.RESONANT.get().create(
                level, null, concealed, MobSpawnType.NATURAL, true, false);
        if (resonant == null) return false;
        resonant.setActivityState(ResonantState.LISTENING);
        if (!level.addFreshEntity(resonant)) {
            resonant.discard();
            return false;
        }
        FrozenDawn.LOGGER.info("[Resonant] Hollow evolution concealed at {}", concealed);
        return true;
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 24 + random.nextInt(25); // 24-48 blocks
            int x = (int) (player.getX() + Math.cos(angle) * dist);
            int z = (int) (player.getZ() + Math.sin(angle) * dist);

            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, 0, z));

            // Surface only — above Y=60
            if (surface.getY() < 60) continue;

            // Spawn 1-2 blocks above surface
            BlockPos spawnPos = surface.above(1 + random.nextInt(2));

            // Must have sky access
            if (!level.canSeeSky(surface)) continue;

            // Must be air
            if (!level.getBlockState(spawnPos).isAir()) continue;

            return spawnPos;
        }
        return null;
    }

    public static void reset() {}
}
