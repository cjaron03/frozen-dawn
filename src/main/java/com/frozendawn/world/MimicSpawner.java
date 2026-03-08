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
        float spawnChance = Math.min(0.8f, 0.005f * mobMult); // Base 0.005 (~33 min average)

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            if (random.nextFloat() > spawnChance) continue;

            // Density cap: max 1 Mimic within 128 blocks
            int nearbyCount = level.getEntitiesOfClass(MimicEntity.class,
                    player.getBoundingBox().inflate(128.0)).size();
            if (nearbyCount >= 1) continue;

            BlockPos spawnPos = findSpawnPos(level, player, random);
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

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 40 + random.nextInt(25); // 40-64 blocks
            int x = (int) (player.getX() + Math.cos(angle) * dist);
            int z = (int) (player.getZ() + Math.sin(angle) * dist);
            int y = (int) player.getY() + random.nextInt(17) - 8;

            BlockPos pos = new BlockPos(x, y, z);

            // Solid block below and 2-high air space
            if (!level.getBlockState(pos.below()).isSolid()) continue;
            if (!level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos.above()).isAir()) continue;

            // Must have sky access or be underground (below Y=60)
            if (!level.canSeeSky(pos) && pos.getY() >= 60) continue;

            // Light level must be <= 7
            int lightLevel = level.getMaxLocalRawBrightness(pos);
            if (lightLevel > 7) continue;

            return pos;
        }
        return null;
    }

    public static void reset() {}
}
