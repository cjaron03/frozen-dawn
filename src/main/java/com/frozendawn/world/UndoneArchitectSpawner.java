package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.aggregate.StillpointPolicy;
import com.frozendawn.bloom.BloomGrowthManager;
import com.frozendawn.bloom.BloomGrowthPolicy;
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
    private static final int LOCAL_CAP = 2;

    private UndoneArchitectSpawner() {
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
                    FrozenDawnConfig.UNDONE_ARCHITECT_SPAWN_CHANCE_PER_CHECK.get(),
                    density);
            if (StillpointPolicy.isSuppressed(level, player.blockPosition())) {
                spawnChance *= 0.20D;
            }
            if (!player.isAlive() || player.isSpectator()
                    || hasNearby(level, player.blockPosition())) {
                continue;
            }
            if (!PostMaeveEncounterDirector.rollPlayer(level, player,
                    PostMaeveEncounterType.UNDONE_ARCHITECT, spawnChance)) {
                continue;
            }
            BlockPos pos = LateThreatSpawnHelper.findUnrestrictedHybridSpawn(
                    level, player, level.random, 48, 80, 32,
                    LateThreatSpawnHelper.NO_LIGHT_LIMIT);
            if (pos != null && level.hasChunkAt(pos)) {
                UndoneArchitectEntity spawned = spawn(level, pos);
                if (spawned != null) {
                    PostMaeveEncounterDirector.successPlayer(level, player,
                            PostMaeveEncounterType.UNDONE_ARCHITECT);
                    FrozenDawn.LOGGER.info(
                            "[UndoneArchitect] Naturally spawned near {} density={} chance={}",
                            player.getName().getString(), density, spawnChance);
                } else {
                    PostMaeveEncounterDirector.blockedPlayer(level, player,
                            PostMaeveEncounterType.UNDONE_ARCHITECT,
                            "entity creation or insertion failed");
                }
            } else {
                PostMaeveEncounterDirector.blockedPlayer(level, player,
                        PostMaeveEncounterType.UNDONE_ARCHITECT,
                        "no loaded hybrid spawn position");
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
        if (!level.addFreshEntity(architect)) {
            return null;
        }
        if (BloomGrowthManager.localDensity(level, pos) > 0.02F) {
            architect.beginBloomEmergence();
        }
        return architect;
    }

    private static boolean hasNearby(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(
                UndoneArchitectEntity.class,
                new AABB(pos).inflate(LOCAL_CAP_RADIUS)).size() >= LOCAL_CAP;
    }
}
