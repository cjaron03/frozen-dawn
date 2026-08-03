package com.frozendawn.world;

import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.entity.UndoneArchitectEntity;
import com.frozendawn.homo.PostMaeveWorldState;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Rare post-Maeve builders, capped separately from the ordinary Undone. */
public final class UndoneArchitectSpawner {
    private static final int CHECK_INTERVAL = 600;
    private static final double LOCAL_CAP_RADIUS = 192.0D;

    private UndoneArchitectSpawner() {
    }

    public static void tick(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_INTERVAL != 0L
                || !PostMaeveWorldState.isUndoneSpawningReleased(level.getServer())) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || level.random.nextDouble()
                    >= FrozenDawnConfig.UNDONE_ARCHITECT_SPAWN_CHANCE_PER_CHECK.get()
                    || hasNearby(level, player.blockPosition())) {
                continue;
            }
            BlockPos pos = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                    level, player, level.random, 48, 80, 32,
                    LateThreatSpawnHelper.NO_LIGHT_LIMIT);
            if (pos != null && level.hasChunkAt(pos)) {
                spawn(level, pos);
            }
        }
    }

    public static UndoneArchitectEntity spawn(ServerLevel level, BlockPos pos) {
        UndoneArchitectEntity architect = ModEntities.UNDONE_ARCHITECT.get().create(
                level, null, pos, MobSpawnType.EVENT, true, false);
        if (architect == null) {
            return null;
        }
        architect.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        architect.setPersistenceRequired();
        level.addFreshEntity(architect);
        return architect;
    }

    private static boolean hasNearby(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(
                UndoneArchitectEntity.class,
                new AABB(pos).inflate(LOCAL_CAP_RADIUS)).isEmpty();
    }
}
