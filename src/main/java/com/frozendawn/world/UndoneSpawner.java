package com.frozendawn.world;

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
    private static final double LOCAL_CAP_RADIUS = 128.0D;

    private UndoneSpawner() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_INTERVAL != 0L
                || !PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator() || !player.isAlive()
                    || level.random.nextDouble()
                            >= FrozenDawnConfig.UNDONE_SPAWN_CHANCE_PER_CHECK.get()) {
                continue;
            }
            if (hasNearbyUndone(level, player.blockPosition())) {
                continue;
            }
            BlockPos spawnPos = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                    level, player, level.random, 40, 72, 28,
                    LateThreatSpawnHelper.NO_LIGHT_LIMIT);
            if (spawnPos == null || !level.hasChunkAt(spawnPos)) {
                continue;
            }
            spawn(level, spawnPos);
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

    private static boolean hasNearbyUndone(ServerLevel level, BlockPos center) {
        AABB bounds = new AABB(center).inflate(LOCAL_CAP_RADIUS);
        return !level.getEntitiesOfClass(UndoneEntity.class, bounds).isEmpty();
    }
}
