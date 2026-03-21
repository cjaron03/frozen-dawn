package com.frozendawn.world;

import com.frozendawn.FrozenDawn;
import com.frozendawn.config.FrozenDawnConfig;
import com.frozendawn.data.OrsaStructureState;
import com.frozendawn.entity.ArchitectEntity;
import com.frozendawn.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

public final class TowerEncounterController {

    private static final double TRIGGER_RADIUS_SQ = 60.0 * 60.0;
    private static final double SPAWN_SEARCH_RADIUS = 26.0;

    private TowerEncounterController() {
    }

    public static void tick(ServerLevel level) {
        if (!FrozenDawnConfig.ENABLE_ARCHITECT.get()) {
            return;
        }

        OrsaStructureState state = OrsaStructureState.get(level.getServer());
        for (OrsaStructureState.TowerRecord tower : state.getTowers()) {
            if (!tower.placed() || tower.architectResolved()) {
                continue;
            }

            if (tower.architectTriggered()) {
                continue;
            }

            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) {
                    continue;
                }
                if (player.blockPosition().distSqr(tower.pos()) > TRIGGER_RADIUS_SQ) {
                    continue;
                }
                if (trySpawnTowerArchitect(level, tower, player)) {
                    break;
                }
            }
        }
    }

    public static boolean isTowerEncounterNearby(ServerLevel level, BlockPos pos, double radius) {
        OrsaStructureState state = OrsaStructureState.get(level.getServer());
        double radiusSq = radius * radius;
        for (OrsaStructureState.TowerRecord tower : state.getTowers()) {
            if (!tower.placed() || tower.architectResolved()) {
                continue;
            }
            if (tower.pos().distSqr(pos) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    public static void markResolved(ServerLevel level, long towerId) {
        if (towerId == Long.MIN_VALUE) {
            return;
        }
        OrsaStructureState.get(level.getServer()).setTowerArchitectResolved(towerId, true);
    }

    private static boolean trySpawnTowerArchitect(ServerLevel level, OrsaStructureState.TowerRecord tower, ServerPlayer player) {
        RandomSource random = level.random;
        BlockPos spawnPos = findSpawnPos(level, tower.pos(), player.blockPosition(), random);
        if (spawnPos == null) {
            return false;
        }

        ArchitectEntity architect = ModEntities.ARCHITECT.get().create(level, null, spawnPos,
                MobSpawnType.TRIGGERED, true, false);
        if (architect == null) {
            return false;
        }

        architect.preSeedObservation(level, player);
        if (!player.isCreative()) {
            architect.armSpawnObserveCue(player);
        }
        architect.setTowerEncounter(tower.id());

        level.addFreshEntity(architect);
        OrsaStructureState.get(level.getServer()).setTowerArchitectTriggered(tower.id(), true);
        FrozenDawn.LOGGER.info("[Architect] Tower encounter triggered near {} at tower ({}, {}, {})",
                player.getName().getString(), tower.pos().getX(), tower.pos().getY(), tower.pos().getZ());
        return true;
    }

    private static BlockPos findSpawnPos(ServerLevel level, BlockPos towerPos, BlockPos playerPos, RandomSource random) {
        double awayAngle = Math.atan2(towerPos.getZ() - playerPos.getZ(), towerPos.getX() - playerPos.getX());

        for (int attempt = 0; attempt < 18; attempt++) {
            double angle = awayAngle + (random.nextDouble() - 0.5D) * Math.PI * 0.75D;
            double dist = SPAWN_SEARCH_RADIUS + random.nextInt(12);
            int x = towerPos.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = towerPos.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
                continue;
            }
            return pos;
        }

        return null;
    }
}
