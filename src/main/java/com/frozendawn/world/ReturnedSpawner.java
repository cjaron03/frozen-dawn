package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.ReturnedEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

public class ReturnedSpawner {

    private ReturnedSpawner() {}

    public static void tick(ServerLevel level, int currentPhase, float progress) {
        if (currentPhase < 6) return;
        if (!FrozenDawnConfig.ENABLE_RETURNED.get()) return;

        long gameTick = level.getGameTime();
        if (gameTick % 200 != 0) return; // Every 10 seconds

        RandomSource random = level.random;

        float mobMult = (float) FrozenDawnConfig.MOB_SPAWN_MULTIPLIER.get().doubleValue();
        float baseChance = BrutalPhase6SpawnCurves.isActive()
                ? BrutalPhase6SpawnCurves.returnedHunterChance(progress)
                : 0.008f;
        if (baseChance <= 0.0f) return;
        float spawnChance = Math.min(0.8f, baseChance * mobMult);
        int maxReturned = Math.max(1, (int) (2 * mobMult));

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;

            // Base chance 0.008 per check (~20-30 min average), scaled by multiplier
            if (random.nextFloat() > spawnChance) continue;

            // Density cap: max 2 within 96 blocks (scaled by multiplier)
            int nearbyCount = level.getEntitiesOfClass(ReturnedEntity.class,
                    player.getBoundingBox().inflate(96.0)).size();
            if (nearbyCount >= maxReturned) continue;

            BlockPos spawnPos = findSpawnPos(level, player, random);
            if (spawnPos == null) continue;

            ReturnedEntity returned = ModEntities.RETURNED.get().create(level, null, spawnPos,
                    MobSpawnType.NATURAL, true, false);
            if (returned != null) {
                level.addFreshEntity(returned);
                // Play door creak sound on spawn (audible at 32 blocks)
                level.playSound(null, spawnPos, SoundEvents.WOODEN_DOOR_OPEN,
                        SoundSource.HOSTILE, 2.0f, 0.5f);
                FrozenDawn.LOGGER.info("[Returned] Spawned near {} at phase {}",
                        player.getName().getString(), currentPhase);
            }
        }
    }

    private static BlockPos findSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 32 + random.nextInt(17); // 32-48 blocks
            int x = (int) (player.getX() + Math.cos(angle) * dist);
            int z = (int) (player.getZ() + Math.sin(angle) * dist);
            // Any Y level near the player
            int y = (int) player.getY() + random.nextInt(17) - 8; // +/- 8 blocks from player Y

            BlockPos pos = new BlockPos(x, y, z);

            // Need solid block below and 2-high air space
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
