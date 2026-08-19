package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.StillpointPolicy;
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

/** Sparse, player-local post-Maeve encounters without chunk tickets. */
public final class UndoneSpawner {
    private static final int CHECK_INTERVAL = 200;
    private static final int LOCAL_CAP = 4;

    private UndoneSpawner() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_INTERVAL != 0L
                || !PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            float density = BloomGrowthManager.localDensity(
                    level, player.blockPosition());
            double spawnChance = BloomGrowthPolicy.undoneSpawnChance(
                    FrozenDawnConfig.UNDONE_SPAWN_CHANCE_PER_CHECK.get(),
                    density);
            if (StillpointPolicy.isSuppressed(level, player.blockPosition())) {
                spawnChance *= 0.20D;
            }
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            if (hasNearbyUndone(level, player.blockPosition(), density)) {
                continue;
            }
            if (!PostMaeveEncounterDirector.rollPlayer(level, player,
                    PostMaeveEncounterType.UNDONE, spawnChance)) {
                continue;
            }
            BlockPos spawnPos = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                    level, player, level.random, 40, 72, 28,
                    LateThreatSpawnHelper.NO_LIGHT_LIMIT);
            if (spawnPos == null || !level.hasChunkAt(spawnPos)) {
                PostMaeveEncounterDirector.blockedPlayer(level, player,
                        PostMaeveEncounterType.UNDONE,
                        "no loaded hybrid spawn position");
                continue;
            }
            if (spawn(level, spawnPos) != null) {
                PostMaeveEncounterDirector.successPlayer(level, player,
                        PostMaeveEncounterType.UNDONE);
                FrozenDawn.LOGGER.info(
                        "[Undone] Naturally spawned near {} density={} chance={}",
                        player.getName().getString(), density, spawnChance);
            } else {
                PostMaeveEncounterDirector.blockedPlayer(level, player,
                        PostMaeveEncounterType.UNDONE,
                        "entity creation or insertion failed");
            }
        }
    }

    public static UndoneEntity spawn(ServerLevel level, BlockPos pos) {
        UndoneEntity undone = ModEntities.UNDONE.get().create(
                level, null, pos, MobSpawnType.EVENT, true, false);
        if (undone == null) {
            return null;
        }
        undone.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        undone.setPersistenceRequired();
        level.addFreshEntity(undone);
        return undone;
    }

    private static boolean hasNearbyUndone(ServerLevel level, BlockPos center,
                                           float localDensity) {
        AABB bounds = new AABB(center).inflate(
                BloomGrowthPolicy.undoneLocalCapRadius(localDensity));
        return level.getEntitiesOfClass(UndoneEntity.class, bounds,
                entity -> entity.getType() == ModEntities.UNDONE.get()).size()
                >= LOCAL_CAP;
    }
}
