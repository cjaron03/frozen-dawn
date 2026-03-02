package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.HollowEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

public class HollowSpawner {

    private HollowSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        if (currentPhase < 5) return;
        if (!FrozenDawnConfig.ENABLE_HOLLOW.get()) return;
        // Stop spawning in phase 6 late (atmosphere gone — even vapors freeze solid)
        if (currentPhase >= 6 && progress >= 0.85f) return;

        long gameTick = level.getGameTime();
        if (gameTick % 200 != 0) return; // Every 10 seconds

        RandomSource random = level.random;

        // Phase-based spawn chance:
        // Phase 5: 15% — introductory encounters
        // Phase 6 early (0-0.4): ramps 20% → 40%
        // Phase 6 mid (0.4-0.7): peaks at 50%
        // Phase 6 late (0.7-0.85): tapers to 25% then stops at 0.85
        float spawnChance;
        if (currentPhase == 5) {
            spawnChance = 0.15f;
        } else {
            // Phase 6 — ramp/peak/taper based on progress
            if (progress < 0.4f) {
                // Early: ramp 0.20 → 0.40
                spawnChance = 0.20f + (progress / 0.4f) * 0.20f;
            } else if (progress < 0.7f) {
                // Mid: peak at 0.50
                spawnChance = 0.50f;
            } else {
                // Late: taper 0.50 → 0.0 from 0.7 to 0.85
                float taper = (progress - 0.7f) / 0.15f;
                spawnChance = 0.50f * (1.0f - taper);
            }
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            if (random.nextFloat() > spawnChance) continue;

            // Density cap: max 4 within 48 blocks
            int nearbyCount = level.getEntitiesOfClass(HollowEntity.class,
                    player.getBoundingBox().inflate(48.0)).size();
            if (nearbyCount >= 4) continue;

            BlockPos spawnPos = findSpawnPos(level, player, random);
            if (spawnPos == null) continue;

            HollowEntity hollow = ModEntities.HOLLOW.get().create(level, null, spawnPos,
                    MobSpawnType.NATURAL, true, false);
            if (hollow != null) {
                level.addFreshEntity(hollow);
                FrozenDawn.LOGGER.info("[Hollow] Spawned near {} at phase {}", player.getName().getString(), currentPhase);
            }
        }
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
