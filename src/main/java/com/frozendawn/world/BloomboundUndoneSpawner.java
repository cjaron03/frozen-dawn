package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.bloom.BloomBand;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.bloom.BloomGrowthPolicy;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.UndoneEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Rare Undone encounters physically overtaken by mature Bloom. */
public final class BloomboundUndoneSpawner {
    private static final int CHECK_INTERVAL = 200;
    private static final int LOCAL_CAP = 3;

    private BloomboundUndoneSpawner() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_INTERVAL != 0L
                || !PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())
                || !PostMaeveWorldState.isBloomReleased(level.getServer())) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            float density = BloomGrowthManager.localDensity(
                    level, player.blockPosition());
            double spawnChance = BloomGrowthPolicy.bloomboundSpawnChance(
                    FrozenDawnConfig.BLOOMBOUND_UNDONE_SPAWN_CHANCE_PER_CHECK.get(),
                    density);
            if (player.isSpectator() || !player.isAlive()
                    || level.random.nextDouble() >= spawnChance
                    || !BloomGrowthManager.hasBandNear(
                    level, player.blockPosition(), BloomBand.CORE, 28)
                    || hasNearbyUndone(level, player.blockPosition(), density)) {
                continue;
            }
            BlockPos spawnPos = findCoreSpawn(level, player);
            if (spawnPos != null) {
                if (spawn(level, spawnPos) != null) {
                    FrozenDawn.LOGGER.info(
                            "[Bloombound] Naturally spawned near {} density={} chance={}",
                            player.getName().getString(), density, spawnChance);
                }
            }
        }
    }

    private static BlockPos findCoreSpawn(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 6; attempt++) {
            BlockPos pos = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                    level, player, level.random, 28, 52, 24,
                    LateThreatSpawnHelper.NO_LIGHT_LIMIT);
            if (pos != null && level.hasChunkAt(pos)
                    && BloomGrowthManager.hasBandNear(level, pos, BloomBand.CORE, 8)) {
                return pos;
            }
        }
        return null;
    }

    public static UndoneEntity spawn(ServerLevel level, BlockPos pos) {
        UndoneEntity undone = ModEntities.BLOOMBOUND_UNDONE.get().create(
                level, null, pos, MobSpawnType.EVENT, true, false);
        if (undone == null) {
            return null;
        }
        undone.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        undone.setPersistenceRequired();
        if (!level.addFreshEntity(undone)) {
            return null;
        }
        undone.beginBloomEmergence();
        return undone;
    }

    private static boolean hasNearbyUndone(ServerLevel level, BlockPos center,
                                           float localDensity) {
        return level.getEntitiesOfClass(UndoneEntity.class,
                new AABB(center).inflate(
                        BloomGrowthPolicy.undoneLocalCapRadius(localDensity)),
                entity -> entity.getType()
                        == ModEntities.BLOOMBOUND_UNDONE.get()).size() >= LOCAL_CAP;
    }
}
