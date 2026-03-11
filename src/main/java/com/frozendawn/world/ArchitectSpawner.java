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
        float spawnChance = Math.min(0.8f, 0.02f * mobMult);

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
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
     * Spawn 48-64 blocks from player — outside render distance so the Architect
     * exists and begins OBSERVE before the player can see it.
     */
    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 48 + random.nextInt(17); // 48-64 blocks
            int x = (int) (player.getX() + Math.cos(angle) * dist);
            int z = (int) (player.getZ() + Math.sin(angle) * dist);
            int y = (int) player.getY() + random.nextInt(17) - 8;

            BlockPos pos = new BlockPos(x, y, z);

            if (!level.getBlockState(pos.below()).isSolid()) continue;
            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.above()).isAir()) continue;

            // Must have sky access or be underground (below Y=60)
            if (!level.canSeeSky(pos) && pos.getY() >= 60) continue;

            return pos;
        }
        return null;
    }

    public static void reset() {}
}
